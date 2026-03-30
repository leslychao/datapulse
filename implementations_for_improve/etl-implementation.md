# DataPulse ETL Flow — Implementation Description

Детальное описание реализации ETL-подсистемы. Обзор общей архитектуры проекта см. в [docs/project-architecture.md](project-architecture.md).

## 1. High-Level Architecture

```
REST API (POST /api/etl/run | POST /api/etl/scenario)
  └─ EtlOrchestratorService / EtlScenarioOrchestratorService
       └─ EtlRunBootstrapService
            ├─ EventExecutionPlanResolver  (plan)
            ├─ INSERT etl_execution        (persist)
            ├─ INSERT etl_source_execution_state[]
            └─ INSERT outbox_message[]     (dispatch)
                 └─ Outbox Poller (Spring Integration, fixedDelay 1s)
                      └─ RabbitMQ (exchange: etl.execution)
                           └─ EtlWorkerService.process()
                                ├─ Guards (execution terminal? source terminal? retry due? CAS claim)
                                ├─ EtlSourcePipelineService.execute()
                                │    ├─ EventSource.fetchSnapshotChunks()
                                │    │    └─ Marketplace Adapter (HTTP → JSON file on disk)
                                │    ├─ SnapshotIteratorFactory (Gson streaming)
                                │    └─ RawBatchInsertJdbcRepository (SHA-256 idempotent, batch 500)
                                ├─ SourceStepProgressionService (unlock successor / fail lane)
                                └─ EtlCompletionService.checkAndFinalize()
                                     ├─ EtlMaterializationServiceImpl (JSONB → dim_*/fact_*)
                                     ├─ MartRefreshService (mart_order_pnl, mart_product_pnl)
                                     ├─ RawRetentionService (cleanup old raw data)
                                     └─ EtlScenarioProgressService (scenario FSM → EtlScenarioTerminalEvent)
```

## 2. Two Orchestration Levels

### 2.1 Single-Event Execution

**Entry:** `POST /api/etl/run` → `EtlRunController` → `EtlOrchestratorService.orchestrate(EtlRunRequest)`

- Resolves `MarketplaceEvent` from string
- Resolves date range via `EtlDateRangeResolver` (modes: `NONE`, `RANGE`, `LAST_DAYS`)
- Delegates to `EtlRunBootstrapService.bootstrap()`
- Returns `requestId`

**Module:** `datapulse-application` (controller) → `datapulse-etl` (orchestration)

### 2.2 Multi-Event Scenario

**Entry:** `POST /api/etl/scenario` → `EtlScenarioController` → `EtlScenarioOrchestratorService.startScenario(EtlScenarioRunRequest)`

- Creates `etl_scenario_execution` (status `NEW`)
- Creates `etl_scenario_item_state` per event (`BLOCKED` if dependency also in scenario, else `READY`)
- CAS `NEW → IN_PROGRESS`
- Dispatches `READY` items via `EtlScenarioDispatchService` → each triggers `bootstrap()`
- **Constraint:** partial unique index — max one active scenario per account

**Scenario state machine:**
```
NEW → IN_PROGRESS → COMPLETED
                  └→ FAILED
```

**Scenario-level events:** `EtlScenarioTerminalEvent` published via `ApplicationEventPublisher` when scenario reaches terminal state → consumed by `SyncNotificationListener` in `datapulse-core` for user notifications.

## 3. Bootstrap — Planning and Dispatch

### 3.1 Execution Plan

`EventExecutionPlanResolver.resolve(event, activeMarketplaces)` → `EventExecutionPlan`

**Lane concept:**
- One lane = one marketplace within one event
- Within a lane: linear chain of `EventSource` beans sorted by `@EtlSourceMeta(order)`
- Different marketplaces execute in **parallel** (separate lanes)
- Within a lane: **strictly sequential** (predecessor → successor)

**Plan construction:**
1. `EtlSourceRegistry` collects all `EventSource` beans annotated with `@EtlSourceMeta`
2. Indexes by `MarketplaceEvent`, sorts by `order`
3. `EventExecutionPlanResolver` splits by marketplace, builds `MarketplaceLanePlan` per marketplace
4. Each step gets `predecessorSourceId` = previous step's `sourceId` (null for root)
5. `sourceId` = simple class name of the `EventSource` bean

**Output:** `EventExecutionPlan { lanes: [MarketplaceLanePlan { steps: [SourceStepPlan] }] }`

### 3.2 Persistence and Outbox

`EtlRunBootstrapService.bootstrap()` (runs inside caller's `@Transactional`):

1. **INSERT** `etl_execution` with status `NEW`
2. If plan has **no steps**: CAS `NEW → COMPLETED`, return `immediatelyCompleted = true`
3. **Batch INSERT** `etl_source_execution_state`:
   - Lane roots → `READY`
   - Non-roots → `BLOCKED` (with `predecessor_source_id`)
4. **CAS** `NEW → IN_PROGRESS`
5. **Batch INSERT** `outbox_message` (type `EXECUTION_PUBLISH`) for each lane root

**Atomicity:** Bootstrap does not declare `@Transactional` itself — relies on caller's transaction (`EtlOrchestratorService` and `EtlScenarioOrchestratorService` are both `@Transactional`).

## 4. Outbox Pattern — Reliable Messaging

### 4.1 Outbox Table

```
outbox_message:
  id BIGSERIAL
  type          (EXECUTION_PUBLISH | WAIT_PUBLISH)
  payload_json  (serialized EtlSourceExecution)
  exchange_name
  routing_key
  correlation_id
  ttl_millis    (for WAIT_PUBLISH only)
  status        (PENDING | SENT | ERROR)
  created_at
  sent_at
  last_error
```

### 4.2 Polling Flow (Spring Integration DSL)

```
OutboxPollingFlowConfig:
  IntegrationFlow:
    fixedDelay poller (1s, configurable)
    → OutboxMessageRepository.claimBatch(batchSize=50)
      (SELECT ... WHERE status IN ('PENDING','ERROR') ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED)
    → splitter
    → OutboxMessageMapper → AMQP Message with headers

OutboxPublishFlowConfig:
    → Amqp.outboundAdapter (exchange + routing key from message headers)
    → OutboxPublishAdvice:
        success → markSent(id, now)
        failure → markError(id, error) — swallowed, retry on next poll
```

### 4.3 RabbitMQ Topology

```
Exchanges (direct):
  etl.execution       ← main work
  etl.execution.wait  ← delayed retry

Queues:
  etl.execution       ← worker listens here
  etl.execution.wait  ← DLX config → etl.execution (TTL expiration reroutes to main)
```

**Retry mechanism:** Worker inserts `WAIT_PUBLISH` outbox row → published to wait exchange with TTL → message expires → DLX redirects to main execution queue → worker picks up again.

**Consumer config:** `AcknowledgeMode.AUTO`, `prefetchCount=1`, `defaultRequeueRejected=true`.

## 5. Worker — Step Processing

`EtlWorkerService.process(EtlSourceExecution)` — entry point from Rabbit inbound flow.

### 5.1 Guards

```
1. Execution missing or terminal?        → skip
2. Source row missing or terminal?        → skip
3. nextAttemptAt not yet reached?         → skip (retry not due)
4. CAS casStart fails?                    → skip (another worker claimed it)
   (READY | RETRY_SCHEDULED → IN_PROGRESS)
```

All guards return silently — no exception, message is ACKed (AUTO mode).

### 5.2 Pipeline Execution

```
pipelineService.execute(execution)  ← runs OUTSIDE TransactionTemplate (long-running I/O)
```

### 5.3 Outcome Handling

| Outcome | Action |
|---------|--------|
| **Success** | `TransactionTemplate { progressionService.onStepCompleted() }` → `completionService.checkAndFinalize()` |
| **TooManyRequestsBackoffRequired** | `TransactionTemplate { if attempt >= maxAttempts → onStepFailedTerminal; else casRetryScheduled + outbox WAIT_PUBLISH }` → if terminal: `checkAndFinalize()` |
| **RateLimitBackoffRequired** | Same as above |
| **Other exception** | `TransactionTemplate { onStepFailedTerminal(errorMessage) }` → `checkAndFinalize()` |

### 5.4 Transaction Boundaries

- **CAS start** — standalone JDBC update (not in TransactionTemplate)
- **Pipeline execution** — no transaction (I/O-bound, long-running)
- **Step progression** (success/fail) — inside TransactionTemplate
- **Completion check** — after progression transaction commits (separate call)

## 6. Ingest Pipeline

`EtlSourcePipelineService.execute(EtlSourceExecution)`:

### 6.1 Data Flow

```
1. EtlSourceRegistry.getSource(event, sourceId) → RegisteredSource
2. RawTableSchemaJdbcRepository.ensureRawTableExists(rawTable) — auto DDL
3. EventSource.fetchSnapshotChunks(accountId, event, dateFrom, dateTo)
4. For each chunk:
   a. SnapshotJsonLayoutRegistry → JsonArrayLocator (path to array in JSON)
   b. SnapshotIteratorFactory.createIterator(file, elementType, locator)
      → BufferedReader → Gson JsonReader → streaming deserialization
   c. Per element:
      - Jackson objectMapper.writeValueAsString(record) → payload string
      - SHA-256(payload UTF-8 bytes) → recordKey
      - Build RawEtlSnapshotEntity
   d. Flush every 500 records → RawBatchInsertJdbcRepository.saveBatchIdempotent()
```

### 6.2 EventSource Interface

```java
public interface EventSource {
    List<Snapshot<?>> fetchSnapshots(long accountId, MarketplaceEvent event,
                                     LocalDate dateFrom, LocalDate dateTo);
    default List<RawSnapshotChunk> fetchSnapshotChunks(...) {
        // maps each snapshot to RawSnapshotChunk(null, snapshot)
    }
}
```

**Registration:** `@EtlSourceMeta(events, marketplace, rawTableName, order)` annotation → `EtlSourceRegistry` indexes by event, sorts by order.

**sourceId** = simple class name of the EventSource bean (e.g., `OzonPostingsFbsEventSource`).

### 6.3 EventSource Implementations

| Domain | Event | Ozon | WB |
|--------|-------|------|----|
| Warehouses | `WAREHOUSE_DICT` | `OzonClustersEventSource`, `OzonWarehouseFbsEventSource` | `WbOfficeFbsEventSource`, `WbWarehouseFbwEventSource`, `WbWarehouseSellerEventSource` |
| Categories | `CATEGORY_DICT` | `OzonCategoryEventSource` | `WbCategoryParentEventSource`, `WbSubjectEventSource` |
| Products | `PRODUCT_DICT` | `OzonProductsEventSource`, `OzonProductInfoEventSource` (order=1) | `WbProductsEventSource` |
| Sales | `SALES_FACT` | `OzonReturnsEventSource`, `OzonPostingsFboEventSource`, `OzonPostingsFbsEventSource` | `WbSupplierSalesEventSource` |
| Inventory | `INVENTORY_FACT` | `OzonAnalyticsStocksEventSource`, `OzonProductInfoStocksEventSource` | `WbStocksEventSource` (order=0), `WbIncomesEventSource` (order=1) |
| Advertising | `ADVERTISING_FACT` | `OzonAdvertisingEventSource` | `WbAdvertisingEventSource` |
| Finance | `FACT_FINANCE` | `OzonFinanceTransactionsEventSource` | `WbSalesReportDetailByPeriodEventSource` |
| Promos | `PROMO_SYNC` | `OzonPromoEventSource` (0), `OzonPromoActionProductsEventSource` (1) | `WbPromoEventSource` (0), `WbPromoNomenclaturesEventSource` (1) |

Sources with overridden `fetchSnapshotChunks` (fan-out with `rowSourceId`): `WbPromoNomenclaturesEventSource`, `OzonPromoActionProductsEventSource`.

### 6.4 Marketplace Download

```
EventSource bean → OzonAdapter / WbAdapter → AbstractMarketplaceAdapter:

1. Compute path: baseDir / {marketplace} / {accountId} / {endpointTag} / {endpoint}[_{partition}].json
2. Per-path ReentrantLock (SnapshotLockRegistry) — no concurrent writes to same file
3. If file exists → cache hit (return Snapshot without download)
4. Else → MarketplaceStreamingDownloadService.download():
     HTTP GET/POST with rate limiting (token bucket)
     → FileStreamingService: DataBuffer flux → .part temp → atomic move
5. Return Snapshot(elementType, filePath, nextToken)
```

**Pagination:** EventSource loops while `snapshot.nextToken() != null`.

**File cleanup:** `FileCleanupService` (`@Scheduled`, configurable `maxAge` default 30m, `interval` default 5m) — walks `baseDir`, deletes files older than `maxAge`.

**Credentials:** `DefaultCredentialsProvider` → `MarketplaceCredentialsVaultServiceImpl` → HashiCorp Vault at `datapulse/accounts/{accountId}/{marketplace}`.

### 6.5 Idempotency

```sql
INSERT INTO {raw_table}
  (request_id, account_id, event, source_id, record_key, payload, created_at)
VALUES (...)
ON CONFLICT (request_id, source_id, record_key) DO NOTHING
```

- `record_key` = SHA-256 of Jackson-serialized JSON payload
- Dedup key: `(request_id, source_id, record_key)` — per-request, not global
- Same request retried: duplicates silently ignored
- New request: same business data inserted again (raw layer is per-request)
- SHA-256 uses `ThreadLocal<MessageDigest>` for efficiency

### 6.6 Raw Table DDL (Dynamic)

`RawTableSchemaJdbcRepository`:
- Only tables with `raw_` prefix get auto-DDL
- `to_regclass()` fast existence check
- Schema: `BIGSERIAL id`, standard columns, `UNIQUE (request_id, source_id, record_key)`
- Indexes: `(request_id)`, `(account_id, request_id)`

**Constants:** `RawTableNames` — final class with `public static final String` for all raw table names.

## 7. Step Progression

`SourceStepProgressionService`:

### 7.1 On Success

```
onStepCompleted(requestId, event, sourceId):
  1. CAS casComplete: IN_PROGRESS → COMPLETED
  2. unlockSuccessorIfNeeded():
     a. Find successor: WHERE predecessor_source_id = sourceId
     b. Verify parent execution still IN_PROGRESS
     c. CAS casUnlockSuccessor: BLOCKED → READY
     d. Insert EXECUTION_PUBLISH outbox via OutboxFactory
```

### 7.2 On Terminal Failure

```
onStepFailedTerminal(requestId, event, sourceId, error):
  1. CAS casFailTerminal: IN_PROGRESS → FAILED_TERMINAL (error truncated to 4000 chars)
  2. markLaneRemainingSkipped: all BLOCKED in same lane_key → SKIPPED_UPSTREAM_FAILED
```

Completion check is NOT triggered here — it is called by the worker after the progression transaction commits.

## 8. Completion — Finalization

`EtlCompletionService.checkAndFinalize(requestId)`:

```
1. CompletionResult = sourceRepository.getCompletionState(requestId)
   allTerminal() = (completed + failed + skipped == total) AND total > 0

2. If NOT allTerminal → return (wait for remaining steps)

3. If ANY FAILED_TERMINAL:
   CAS IN_PROGRESS → FAILED
   → scenarioProgressService.onExecutionTerminal(FAILED)

4. If ALL SUCCESS:
   a. CAS IN_PROGRESS → MATERIALIZING (single-winner semantics)
   b. executeMaterializationWithRetry(requestId)
      - Up to maxRetryAttempts with backoff
   c. rawRetentionService.cleanupAfterExecution(requestId)
   d. CAS MATERIALIZING → COMPLETED
   e. → scenarioProgressService.onExecutionTerminal(COMPLETED)
   f. On exception: CAS MATERIALIZING → FAILED → onExecutionTerminal(FAILED)
```

**Execution status state machine:**
```
NEW → IN_PROGRESS → MATERIALIZING → COMPLETED
                  ↘ FAILED       ↗
```

No `@Transactional` on `checkAndFinalize` — each CAS is standalone; materialization runs outside any transaction scope.

## 9. Materialization

### 9.1 Orchestration

`EtlMaterializationServiceImpl.executeMaterialization(requestId)`:

1. Load `EtlExecutionEntity` → parse event, get accountId
2. Load all source states → filter `COMPLETED` → group by `MarketplaceType`
3. For each marketplace: `handlerRegistry.findFor(event, marketplace)` → `handler.materialize(context)`
4. After all marketplaces: `martRefreshService.refreshAfterEvent(accountId, event, requestId)`

**Fails fast:** if any handler throws, remaining marketplaces and mart refresh are skipped. No per-handler try/catch.

### 9.2 MaterializationHandler Contract

```java
public interface MaterializationHandler {
    MarketplaceEvent supportedEvent();
    MarketplaceType marketplace();
    void materialize(MaterializationContext context);
}
```

`MaterializationHandlerRegistry` collects all handler beans, indexes by `(event, marketplace)`, fails fast on duplicate registrations.

### 9.3 Materialization Handlers

| Domain | Event | Handler actions |
|--------|-------|-----------------|
| **Category** | `CATEGORY_DICT` | Recursive CTE on raw JSONB → UPSERT `dim_category` |
| **Warehouse** | `WAREHOUSE_DICT` | UNION ALL from multiple raw tables with priority → UPSERT `dim_warehouse` |
| **Product** | `PRODUCT_DICT` | JOIN raw_products + raw_product_info → UPSERT `dim_product` |
| **Sales Ozon** | `SALES_FACT` | FBS/FBO postings → `fact_sales`, returns → `fact_returns`, + dim backfill |
| **Sales WB** | `SALES_FACT` | **Only dim_product backfill** — WB sales/returns loaded in FACT_FINANCE handler |
| **Inventory** | `INVENTORY_FACT` | → `fact_inventory_snapshot` + `fact_supply` (WB), dim backfill |
| **Advertising** | `ADVERTISING_FACT` | → `fact_advertising_costs` |
| **Finance Ozon** | `FACT_FINANCE` | → `fact_commission`, `fact_logistics_costs`, `fact_marketing_costs`, `fact_penalties`, `fact_finance` |
| **Finance WB** | `FACT_FINANCE` | → `fact_sales` + `fact_returns` (!) + commission/logistics/marketing/penalties + `fact_finance` |
| **Promo** | `PROMO_SYNC` | → `dim_promo_campaign`, `fact_promo_product` |

**WB sales/returns note:** WB `fact_sales` and `fact_returns` are populated in the `FACT_FINANCE` handler, not in `SALES_FACT`. The `SALES_FACT` handler for WB only does dim_product backfill. The dependency graph (`FACT_FINANCE` depends on `SALES_FACT`) ensures correct ordering.

### 9.4 fact_finance — Central Consolidated Fact

`FinanceFactJdbcRepository` — single `UPSERT_TEMPLATE` with platform-specific `SELECT`:

**WB flow:**
```
raw_wb_sales_report_detail
  → wb_raw_numbered (row_number per srid+nm_id+day for dedup)
  → daily_finance (cash_flow, revenue, discount, refund, compensation)
  → spine (UNION keys from daily_finance + fact_commission + fact_logistics + fact_penalties + fact_marketing)
  → final SELECT with COALESCE from all sources
  → UPSERT fact_finance ON CONFLICT (account_id, source_platform, order_id, finance_date)
```

**Ozon flow:**
```
raw_ozon_finance_transactions
  → daily_finance (group by posting_number + date)
  → spine + component fact joins
  → UPSERT fact_finance
```

**Measures:** `revenue_gross`, `seller_discount_amount`, `marketplace_commission_amount`, `acquiring_commission_amount`, `logistics_cost_amount`, `penalties_amount`, `marketing_cost_amount`, `other_marketplace_charges_amount`, `compensation_amount`, `refund_amount`, `net_payout`, `reconciliation_residual`.

### 9.5 Mart Refresh

`MartRefreshService.refreshAfterEvent()`:

| Event | Marts refreshed |
|-------|-----------------|
| `SALES_FACT`, `ADVERTISING_FACT`, `FACT_FINANCE` | `mart_order_pnl` + `mart_product_pnl` |
| `PROMO_SYNC`, `SALES_FACT`, `FACT_FINANCE` | `mart_promo_product_analysis` |
| Others (category, warehouse, product, inventory) | None |

**mart_order_pnl formula:**
```
pnl = revenue_gross
    - seller_discount - commission - acquiring
    - logistics - penalties - other_charges - marketing
    - advertising (pro-rata allocation by product revenue share)
    - refund + compensation
    - cogs (SCD2 product cost from fact_product_cost)
```

**Advertising allocation:** daily ad spend × (line's sale_amount / product-day daily_revenue).

**COGS:** `fact_sales × fact_product_cost` using SCD2 `valid_from/valid_to` relative to `sale_ts`.

**mart_product_pnl:** Aggregates by `(account_id, source_platform, dim_product_id)`, allocating order-level costs proportionally by product revenue share within order.

Both marts use `ON CONFLICT ... DO UPDATE ... WHERE ... IS DISTINCT FROM ...` to avoid churn on unchanged rows.

## 10. Scenario FSM

`EtlScenarioProgressService`:

```
onExecutionTerminal(requestId, executionStatus):
  @Transactional(REQUIRES_NEW):
    1. Lookup scenario item by execution_request_id
    2. SELECT FOR UPDATE scenario row (pessimistic lock for serialization)
    3. If scenario already terminal → ignore

    SUCCESS → handleItemCompleted:
      a. Mark item COMPLETED
      b. advanceScenario():
         - All items done? → CAS scenario COMPLETED + publish EtlScenarioTerminalEvent
         - Else: find BLOCKED items whose dependencies are ALL completed → dispatch

    FAILURE → handleItemFailed:
      a. Mark item FAILED_TERMINAL
      b. Mark remaining BLOCKED items SKIPPED_UPSTREAM_FAILED
      c. CAS scenario FAILED + publish EtlScenarioTerminalEvent
```

### Event Dependency Graph

```
WAREHOUSE_DICT ──────────────────┐
                                 ├→ PRODUCT_DICT ──→ SALES_FACT ──→ FACT_FINANCE
CATEGORY_DICT ───────────────────┘       │
                                         ├→ INVENTORY_FACT
                                         ├→ ADVERTISING_FACT
                                         └→ PROMO_SYNC
```

Defined via `MarketplaceEvent.dependencies()`:

| Event | Dependencies |
|-------|--------------|
| `WAREHOUSE_DICT`, `CATEGORY_DICT` | none |
| `PRODUCT_DICT` | `CATEGORY_DICT`, `WAREHOUSE_DICT` |
| `SALES_FACT` | `PRODUCT_DICT` |
| `INVENTORY_FACT` | `PRODUCT_DICT`, `WAREHOUSE_DICT` |
| `ADVERTISING_FACT` | `PRODUCT_DICT` |
| `FACT_FINANCE` | `SALES_FACT` |
| `PROMO_SYNC` | `PRODUCT_DICT` |

## 11. CAS (Compare-And-Swap) — Complete Registry

### Execution Level (`EtlExecutionRepository`)

| Transition | When |
|------------|------|
| `NEW → IN_PROGRESS` | Bootstrap |
| `IN_PROGRESS → MATERIALIZING` | Completion (all success) |
| `MATERIALIZING → COMPLETED` | After materialization |
| `MATERIALIZING → FAILED` | Materialization error |
| `IN_PROGRESS → FAILED` | Any source failed |

SQL: `UPDATE etl_execution SET status = ?, ended_at = CASE WHEN ? IN ('COMPLETED','FAILED') THEN NOW() ELSE ended_at END WHERE request_id = ? AND status = ?`

### Source Level (`SourceExecutionStateRepository`)

| Method | Transition | When |
|--------|------------|------|
| `casStart` | `READY / RETRY_SCHEDULED → IN_PROGRESS` | Worker claim |
| `casComplete` | `IN_PROGRESS → COMPLETED` | Step success |
| `casFailTerminal` | `IN_PROGRESS → FAILED_TERMINAL` | Step failure |
| `casRetryScheduled` | `IN_PROGRESS → RETRY_SCHEDULED` | Rate limit backoff |
| `casUnlockSuccessor` | `BLOCKED → READY` | Predecessor completed |
| `markLaneRemainingSkipped` | `BLOCKED → SKIPPED_UPSTREAM_FAILED` | Predecessor failed |

### Scenario Level

| Repository | Transition |
|------------|------------|
| `EtlScenarioExecutionRepository` | `NEW → IN_PROGRESS`, `IN_PROGRESS → COMPLETED/FAILED` |
| `EtlScenarioItemStateRepository` | `READY/BLOCKED → RUNNING`, `RUNNING → COMPLETED/FAILED` |

## 12. Status Enums

All in `io.datapulse.etl.model`:

**EtlExecutionStatus:** `NEW`, `IN_PROGRESS`, `MATERIALIZING`, `COMPLETED`, `FAILED`
- `isTerminal()`: COMPLETED or FAILED

**SourceExecutionStatus:** `BLOCKED`, `READY`, `IN_PROGRESS`, `RETRY_SCHEDULED`, `COMPLETED`, `FAILED_TERMINAL`, `SKIPPED_UPSTREAM_FAILED`
- `isTerminal()`: COMPLETED, FAILED_TERMINAL, SKIPPED_UPSTREAM_FAILED
- `isStartable()`: READY or RETRY_SCHEDULED

**EtlScenarioStatus:** `NEW`, `IN_PROGRESS`, `COMPLETED`, `FAILED`

**EtlScenarioItemStatus:** `READY`, `BLOCKED`, `RUNNING`, `COMPLETED`, `FAILED_TERMINAL`, `SKIPPED_UPSTREAM_FAILED`

**OutboxMessageStatus:** `PENDING`, `SENT`, `ERROR`

**OutboxMessageType:** `EXECUTION_PUBLISH`, `WAIT_PUBLISH`

## 13. Raw Retention

`RawRetentionService.cleanupAfterExecution(requestId)`:
- Called after successful materialization
- Guarded by `RawRetentionProperties.isEnabled()`
- For each raw table used by completed sources: keeps `keepCount` (default 3) most recent request_ids per (account_id, table), deletes older
- SQL: CTE with `DENSE_RANK() OVER (ORDER BY MIN(created_at) DESC)` → DELETE rows with rank > keepCount
- Best-effort: exceptions caught and logged, never thrown

## 14. File Management

**Download caching:** `AbstractMarketplaceAdapter` — per-path `ReentrantLock`, file exists = cache hit (no TTL check at adapter level).

**Cleanup:** `FileCleanupService` (`@Scheduled`) — walks `storage.baseDir`, deletes files where `lastModifiedTime < now - maxAge`.

**Config:**
```yaml
marketplace:
  storage:
    base-dir: /var/datapulse/snapshots
    cleanup:
      max-age: 30m
      interval: 5m
```

## 15. Persistence Model

### ETL Tables (JDBC-only, no JPA)

| Table | Module | Repository |
|-------|--------|------------|
| `etl_execution` | etl | `EtlExecutionRepository` |
| `etl_source_execution_state` | etl | `SourceExecutionStateRepository` |
| `outbox_message` | etl | `OutboxMessageRepository` |
| `etl_scenario_execution` | etl | `EtlScenarioExecutionRepository` |
| `etl_scenario_item_state` | etl | `EtlScenarioItemStateRepository` |
| `raw_*` (dynamic) | etl | `RawBatchInsertJdbcRepository`, `RawTableSchemaJdbcRepository` |

All ETL "entities" are plain POJOs with Lombok `@Builder`, not JPA `@Entity`. Persistence is through `JdbcTemplate`.

### Analytical Tables (JDBC materialization in etl, read in core)

| Table type | Examples | Write (etl) | Read (core) |
|------------|----------|-------------|-------------|
| Dimensions | `dim_product`, `dim_warehouse`, `dim_category` | `Dim*JdbcRepository` | JPA entities |
| Facts | `fact_sales`, `fact_returns`, `fact_finance`, `fact_commission`, etc. | `*FactJdbcRepository` | — |
| Marts | `mart_order_pnl`, `mart_product_pnl` | `*MartJdbcRepository` | `*ReadJdbcRepository` in core |

### Liquibase Migrations (in datapulse-core)

- `0020-create-etl-execution-schema.xml` — `etl_execution`, `etl_source_execution_state`, `outbox_message`
- `0021-create-etl-scenario-schema.xml` — `etl_scenario_execution`, `etl_scenario_item_state`
- `0022-add-etl-lane-columns.xml` — lane columns + status migration
- `0037-add-etl-scenario-active-unique-index.xml` — partial unique (one active scenario per account)

## 16. Configuration

### Properties Classes

| Class | Prefix | Key Parameters |
|-------|--------|----------------|
| `EtlAmqpProperties` | `datapulse.etl.amqp` | Exchange/queue/routing-key names |
| `EtlOutboxProperties` | `datapulse.etl.outbox` | `batch-size` (50), `poll-delay` (1s) |
| `EtlWorkerProperties` | `datapulse.etl.worker` | `allowed-skew` (5s) |
| `EtlRetryProperties` | `datapulse.etl.retry` | `max-attempts` (3), `min-backoff` (5s), `max-backoff` (5m) |
| `RawRetentionProperties` | `datapulse.etl.raw-retention` | `enabled` (true), `keep-count` (3) |
| `EtlMaterializationProperties` | `datapulse.etl.materialization` | `max-retry-attempts`, `retry-backoff` |

Registered via `EtlPropertiesConfig` (`@EnableConfigurationProperties`).

### RabbitMQ Topology (datapulse-etl.yml)

```yaml
datapulse.etl.amqp:
  exchanges:
    execution: etl.execution
    execution-wait: etl.execution.wait
  queues:
    execution: etl.execution
    execution-wait: etl.execution.wait
  routing-keys:
    execution: etl.execution
    execution-wait: etl.execution.wait
```

## 17. Full E2E Lifecycle Example

```
1. POST /api/etl/run {accountId=42, event="FACT_FINANCE", dateMode="LAST_DAYS", lastDays=30}

2. EtlOrchestratorService (@Transactional):
   - MarketplaceEvent.fromString("FACT_FINANCE")
   - EtlDateRangeResolver → EtlDateRange(today-30, today)
   - bootstrap(42, FACT_FINANCE, dates)

3. EtlRunBootstrapService:
   - AccountConnectionService → activeMarketplaces = {OZON, WILDBERRIES}
   - EventExecutionPlanResolver:
       Lane OZON:  [OzonFinanceTransactionsEventSource]
       Lane WB:    [WbSalesReportDetailByPeriodEventSource]
   - INSERT etl_execution (requestId="abc-123", status=NEW)
   - INSERT 2 source rows (both lane roots → READY)
   - CAS NEW → IN_PROGRESS
   - INSERT 2 outbox_message (EXECUTION_PUBLISH)

4. Outbox poller (1s) → claims messages → publishes to RabbitMQ

5. Worker picks up OzonFinanceTransactionsEventSource:
   - Guards pass, CAS READY → IN_PROGRESS
   - Pipeline:
     - OzonAdapter.doPost(...) → downloads JSON to disk
     - Gson streams JSON → Jackson serializes → SHA-256 → batch INSERT raw_ozon_finance_transactions
   - onStepCompleted: CAS IN_PROGRESS → COMPLETED, no successor in lane
   - checkAndFinalize: not allTerminal (WB still running)

6. Worker picks up WbSalesReportDetailByPeriodEventSource:
   - Same pipeline → raw_wb_sales_report_detail
   - onStepCompleted → checkAndFinalize: allTerminal, all SUCCESS

7. EtlCompletionService:
   - CAS IN_PROGRESS → MATERIALIZING
   - Materialization:
     - FinanceFactOzonMaterializationHandler → dim_warehouse, fact_commission,
       fact_logistics, fact_marketing, fact_penalties, fact_finance
     - FinanceFactWildberriesMaterializationHandler → dim_product, dim_warehouse,
       fact_sales, fact_returns, fact_commission, fact_logistics, fact_marketing,
       fact_penalties, fact_finance
   - MartRefreshService → refresh mart_order_pnl + mart_product_pnl + mart_promo_analysis
   - RawRetentionService → cleanup old raw requests
   - CAS MATERIALIZING → COMPLETED

8. If part of scenario:
   - EtlScenarioProgressService → advance or complete scenario
   - EtlScenarioTerminalEvent → SyncNotificationListener → user notification
```

## 18. Module Boundaries

```
datapulse-application (REST controllers, Security, WebSocket)
 ├── datapulse-etl (ETL orchestration, worker, materialization, JDBC write)
 │   ├── datapulse-marketplaces (HTTP adapters, rate limiting, file download)
 │   │   └── datapulse-domain (DTOs, enums, exceptions)
 │   └── datapulse-core (JPA entities, business services, JDBC read for marts)
 │       └── datapulse-domain
 └── datapulse-domain
```

Dependencies flow strictly top-down. No reverse dependencies.

## 19. Key Design Patterns

| Pattern | Implementation |
|---------|---------------|
| **Transactional Outbox** | `outbox_message` table + Spring Integration poller + AMQP publish + advice |
| **Compare-And-Swap** | SQL `UPDATE ... WHERE status = ?` for all state transitions |
| **Lane-based parallelism** | Per-marketplace parallel lanes, sequential steps within lane |
| **Idempotent ingest** | SHA-256 record_key + `ON CONFLICT DO NOTHING` |
| **UPSERT materialization** | `INSERT ... SELECT ... ON CONFLICT DO UPDATE` for all dim/fact/mart |
| **Registry pattern** | `EtlSourceRegistry`, `MaterializationHandlerRegistry` |
| **SCD2** | `fact_product_cost` with `valid_from/valid_to` for COGS calculation |
| **Star schema** | `dim_*` (dimensions) + `fact_*` (facts) + `mart_*` (aggregates) |
| **Best-effort cleanup** | `RawRetentionService`, `FileCleanupService` — never throw |
| **Pessimistic locking** | `SELECT ... FOR UPDATE` for scenario progression serialization |
| **DLX retry** | Wait queue with TTL + dead-letter exchange back to main queue |

## 20. Test Coverage

| Area | Tests | Type |
|------|-------|------|
| `EtlWorkerServiceTest` | 3 | Unit (Mockito) |
| `EtlSourcePipelineServiceTest` | 3 | Unit |
| `EtlMaterializationServiceImplTest` | 4 | Unit |
| `EtlCompletionServiceTest` | 7 | Unit |
| `FinanceFactJdbcRepositoryTest` | 9 | Integration (Testcontainers PostgreSQL) |
| `ReconciliationInvariantTest` | 9 | Integration (Testcontainers) |
| Snapshot iteration tests | 5 | Unit |
| EventSource tests | 2 | Unit |
| Marketplace adapter/config tests | 9 | Unit |
