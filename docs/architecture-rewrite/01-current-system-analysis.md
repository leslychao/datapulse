# 01 Current System Analysis

## A. System Overview

**Datapulse** is a multi-tenant SaaS platform for e-commerce sellers on **Wildberries** and **Ozon** marketplaces. It provides:
- Automated data ingestion from marketplace APIs
- Canonical product/financial data model
- P&L analytics with drill-down to posting level
- Algorithmic pricing with policy-based strategies, constraints, and guard chains
- Price action execution with reconciliation
- Promotional campaign evaluation and participation management
- Operational grid for day-to-day seller operations
- Audit trail, alerting, and automation safety gates

**Technology stack:**
- Backend: Java 17, Spring Boot 3.4, modular monolith (12 Maven modules)
- Primary database: PostgreSQL (business state, canonical model)
- Analytical database: ClickHouse (star schema, P&L marts)
- Object storage: MinIO/S3 (raw marketplace API responses)
- Message broker: RabbitMQ (outbox event delivery)
- Secret management: HashiCorp Vault (marketplace credentials)
- Cache/rate limiting: Redis (token bucket, per-product rate limits)
- Frontend: Angular 19, TanStack Query, NgRx Signal Store, AG Grid, Tailwind CSS 4
- Auth: OAuth2 / Keycloak (external IdP)

**Deployment model:** Single monolith Spring Boot application (`datapulse-api`) that aggregates all modules. No separate microservices. Scheduled jobs run within the same process, protected by ShedLock for distributed locking.

---

## B. Functional Zones

The system is organized into 12 Maven modules. Each module corresponds roughly to a bounded context, though boundaries are not perfectly clean (see section J for observations).

| Module | Bounded Context | Core Responsibility |
|--------|----------------|---------------------|
| `datapulse-common` | Cross-cutting | Error codes (`MessageCodes`), base exceptions, shared enums |
| `datapulse-platform` | Infrastructure | `BaseEntity`, security context, audit event, outbox, WebClient config, observability |
| `datapulse-tenancy-iam` | Tenancy & Identity | Tenants, workspaces, users, members, roles, invitations |
| `datapulse-integration` | Marketplace Connectivity | Connections, credentials (Vault), health probes, rate limiting, HTTP logging |
| `datapulse-etl` | Data Pipeline | Ingest jobs, event sources (WB/Ozon), normalization, canonical model, S3 raw storage, ClickHouse materialization, cost profiles |
| `datapulse-analytics-pnl` | Analytics | P&L queries, returns analysis, inventory, data quality, provenance, materialization orchestration |
| `datapulse-pricing` | Pricing | Price policies, assignments, strategies, constraints, guards, pricing runs, decisions, manual locks, bulk manual pricing, impact preview |
| `datapulse-execution` | Action Execution | Price action lifecycle (state machine), outbox-driven execution, retry, reconciliation, simulation |
| `datapulse-promotions` | Promotions | Promo policies, evaluation runs, decisions, participation actions, promo execution (Ozon live + simulated) |
| `datapulse-seller-operations` | Operations UI Backend | Grid (PG+CH), offer detail, search, saved views, working queues, mismatch monitor, price/promo journals |
| `datapulse-audit-alerting` | Audit & Alerting | Audit log, alert rules, alert events, alert checkers, notifications, automation blocker |
| `datapulse-api` | Composition Root | Spring Boot app, global error handler, outbox poller, RabbitMQ consumers, WebSocket config, Liquibase migrations |

---

## C. Main Execution Flows

### C1. Data Ingestion Flow (ETL)

```
Marketplace API → ReadAdapter (WB/Ozon) → Raw bytes → S3 storage
    → Normalizer (in-process) → Canonical UPSERT (PostgreSQL)
    → Post-ingest materialization → ClickHouse star schema
```

- **Trigger:** Scheduled (`SyncScheduler`, configurable poll interval) or manual (`ConnectionController.triggerSync`)
- **Orchestration:** `EventSourceRegistry` resolves `EventSource` by `(MarketplaceType, EtlEventType)`. Each source calls its `ReadAdapter`, normalizes, and upserts.
- **Job tracking:** `job_execution` table (JDBC, not JPA) tracks status with CAS transitions (`IngestJobAcquisitionService`). Individual items tracked in `job_item`.
- **Materialization:** Two-phase: first canonical UPSERT into PG, then `ClickHouseMaterializer` loads data into CH fact/dim/mart tables. `MaterializationService` orchestrates materializers in dependency order (phase + order).
- **Outbox integration:** Sync triggers go through outbox (`ETL_SYNC_EXECUTE`), consumed by `EtlSyncConsumer` in `datapulse-api`.

**Key observation:** ETL is the largest module by code volume. It contains 19 `EventSource` implementations (10 WB + 9 Ozon), each with its own adapter, normalizer chain, and canonical upsert logic. [CONFIRMED]

### C2. Pricing Flow

```
Trigger (manual/scheduled/post-sync/policy-change)
    → PricingRunApiService creates run + outbox PRICING_RUN_EXECUTE
    → PricingRunService.executeRun():
        1. Load eligible offers (PricingDataReadRepository - JDBC)
        2. Resolve policies per offer (PolicyResolver)
        3. Collect signals (PricingSignalCollector - PG + CH)
        4. Execute strategy (PricingStrategyRegistry → TargetMarginStrategy / PriceCorridorStrategy)
        5. Apply constraints (PricingConstraintResolver - min/max/change limits)
        6. Run guard chain (PricingGuardChain - margin, promo, volatility, frequency, stock, stale data, manual lock)
        7. Save decisions (PriceDecisionRepository)
        8. Schedule actions (PricingActionScheduler → ActionService)
    → Complete run
```

- **Single transaction:** The entire `executeRun()` runs inside one `@Transactional`. This is a potential concern for large product catalogs. [CONFIRMED]
- **Safety gates:** `BlastRadiusBreaker` limits % of offers changed in FULL_AUTO mode. `FullAutoSafetyGate` additional check. `AutomationBlockerChecker` can block pricing if stale data or alerts are active.
- **Decision snapshot:** Each `price_decision` stores a full snapshot of policy, signals, constraints, and guards — enabling full audit trail and explainability. [CONFIRMED]

### C3. Price Action Execution Flow

```
ActionService.createAction() → PriceAction (PENDING_APPROVAL or APPROVED)
    → Approval (manual or auto based on execution mode)
    → SCHEDULED → outbox PRICE_ACTION_EXECUTE
    → PriceActionExecuteConsumer → ExecutionGateway:
        1. Acquire rate limit
        2. Resolve credentials from Vault
        3. Call WriteAdapter (WB async poll / Ozon sync)
        4. Record attempt
    → RECONCILIATION_PENDING
    → ReconciliationService:
        1. Read current price from marketplace
        2. Compare with target
        3. SUCCEEDED if match, retry/fail if not
```

- **State machine:** `PENDING_APPROVAL → APPROVED → SCHEDULED → EXECUTING → RECONCILIATION_PENDING → SUCCEEDED/FAILED`. Additional terminal states: `EXPIRED`, `CANCELLED`, `SUPERSEDED`. [CONFIRMED]
- **CAS transitions:** All state changes use compare-and-swap queries in `PriceActionCasRepository`. No optimistic locking via JPA `@Version`. [CONFIRMED]
- **Outbox pattern:** Actions are scheduled via outbox entries, consumed by RabbitMQ consumers in `datapulse-api`. This provides at-least-once delivery guarantee. [CONFIRMED]

### C4. Promo Evaluation Flow

```
Trigger (manual/scheduled)
    → PromoEvaluationRunApiService creates run
    → PromoEvaluationService.evaluate():
        1. Load eligible products per campaign
        2. Resolve promo policy
        3. Assemble signals
        4. Produce evaluation + decision (PARTICIPATE/DECLINE/DEACTIVATE/PENDING_REVIEW)
        5. Create promo actions
    → PromoActionService.execute() (live Ozon API or simulated)
```

- **Simpler lifecycle** than pricing: fewer guards, simpler state machine for promo actions. [CONFIRMED]
- **Ozon-only write:** Promo write is implemented only for Ozon (`OzonPromoWriteAdapter`). WB promo write adapter does not exist yet. [CONFIRMED]
- **Manual override:** Users can manually participate/decline/deactivate via `PromoProductController`. [CONFIRMED]

### C5. Operational Grid Flow

```
GridController.list()
    → GridService:
        1. Query PostgreSQL (canonical + pricing + execution joins)
        2. Optionally enrich from ClickHouse (sales velocity, stock metrics)
        3. Merge results
    → Response with X-Sort-Fallback header if CH unavailable
```

- **Dual-source:** The grid reads from both PG and CH. If ClickHouse is unavailable, the grid degrades gracefully (no CH enrichment columns, fallback sort). [CONFIRMED]
- **Export:** Streaming CSV via `GridExportService` with timeout and batching. [CONFIRMED]

---

## D. Entry Points and Decision Points

### Entry Points

| Type | Entry Point | Module |
|------|-------------|--------|
| REST API | 43 `@RestController` classes across all modules | All |
| Scheduled jobs | ~15 `@Scheduled` methods (ShedLock protected) | etl, integration, pricing, execution, promotions, analytics, seller-ops, audit, tenancy |
| RabbitMQ consumers | Outbox event consumers in `datapulse-api` | api |
| WebSocket | STOMP over WebSocket for real-time notifications | api |
| Domain events | Spring `ApplicationEventPublisher` + `@EventListener` / `@TransactionalEventListener` | Cross-module |

### Key Decision Points

| Decision | Where | How |
|----------|-------|-----|
| Which pricing strategy to apply | `PricingStrategyRegistry` | Strategy+Registry pattern by `PolicyType` |
| Whether to block a price change | `PricingGuardChain` | Ordered chain of 7 guards |
| Whether automation is safe | `AutomationBlockerChecker` | Checks stale data, active alerts |
| How to classify an execution error | `ErrorClassifier` in execution module | Classifies as RETRIABLE / UNCERTAIN / NON_RETRIABLE |
| Which EventSource to run for ETL | `EventSourceRegistry` | Key: `(MarketplaceType, EtlEventType)` |
| How to evaluate promo participation | `PromoPolicyResolver` | Policy-based with signal assembly |
| Whether to degrade CH features | Individual services (`GridService`, `PnlQueryService`) | Circuit breaker + fallback logic |

---

## E. Data Ownership and Source of Truth

### PostgreSQL — Operational Source of Truth

PostgreSQL is the single source of truth for all business state:
- Tenant/workspace/user management
- Marketplace connections and credential references
- Canonical product catalog (`product_master` → `seller_sku` → `marketplace_offer`)
- Canonical financial data (orders, sales, returns, finance entries)
- Canonical prices and stocks (current snapshots)
- Pricing policies, runs, decisions
- Price actions and their state machine
- Promo policies, evaluations, decisions, actions
- Audit log, alert rules, alert events, notifications
- Saved views, working queues

### ClickHouse — Analytical Read Model

ClickHouse contains **derived** analytical data:
- Star schema: `dim_product`, `dim_warehouse`, `dim_category`, `dim_time`
- Facts: `fact_sale`, `fact_return`, `fact_finance_entry`, `fact_stock_snapshot`, `fact_advertising`
- Marts: `mart_pnl_posting`, `mart_pnl_product`, `mart_pnl_account`

ClickHouse is **never** the source of truth for commands or mutations. It is read-only from the business logic perspective. [CONFIRMED]

### S3 (MinIO) — Raw Data Archive

S3 stores immutable raw API responses from marketplace APIs. Purpose:
- Audit trail and provenance (trace any canonical value back to raw API response)
- Replayability (re-normalize if canonical schema changes)
- Retention management (finance data retained longer than operational data)

### Vault — Credential Source of Truth

HashiCorp Vault stores encrypted marketplace API credentials. Application caches them with Caffeine, but Vault is the authoritative source.

### Redis — Ephemeral State Only

Redis is used exclusively for:
- Rate limit token buckets (per connection × rate limit group)
- Per-product rate limiting for Ozon (sliding window, 10 updates/hour)
- Distributed cache (non-authoritative)

Redis is NOT a source of truth for any business data. If Redis is unavailable, rate limiters degrade to in-memory with reduced capacity. [CONFIRMED]

---

## F. Integrations

### Marketplace APIs (External)

| Provider | Purpose | Modules Using | Auth |
|----------|---------|---------------|------|
| **Wildberries** | Product catalog, prices, orders, sales, finance, returns, stocks, warehouses, promo calendar | etl, execution, integration | API token (Bearer) |
| **Ozon Seller API** | Product catalog, prices, orders, sales, finance, returns, stocks, warehouses, promo actions | etl, execution, promotions, integration | Client-Id + Api-Key headers |
| **Ozon Performance API** | Advertising data (OAuth2) | etl (stub currently) | OAuth2 |

### Infrastructure Services (Internal)

| Service | Purpose | Modules Using |
|---------|---------|---------------|
| PostgreSQL | Primary data store | All |
| ClickHouse | Analytical queries | analytics, seller-ops, pricing (signals) |
| RabbitMQ | Async event delivery (outbox) | api (consumers), platform (outbox) |
| Redis | Rate limiting, ephemeral cache | integration, execution |
| Vault | Credential storage | integration |
| MinIO/S3 | Raw data archive | etl |
| Keycloak | OAuth2 IdP | platform (security) |
| SMTP | Invitation emails | tenancy |

---

## G. Transaction Boundaries

### Large Transaction: PricingRunService.executeRun()

The entire pricing run (load offers → resolve policies → collect signals → calculate → guard → save decisions → schedule actions) runs in **a single `@Transactional`**. For a connection with thousands of offers, this creates a long-running transaction holding locks on `pricing_run`, `price_decision`, and potentially `price_action` rows. [CONFIRMED]

### CAS-based Transitions (No Transaction Required)

Price action state transitions use CAS (compare-and-swap) SQL queries:
```sql
UPDATE price_action SET status = :newStatus WHERE id = :id AND status = :expectedStatus
```
This pattern avoids the need for long transactions on the action lifecycle. The service checks the affected row count to determine success. [CONFIRMED]

### Per-Entity Transactions in ETL

ETL canonical upserts happen per-entity-type within a job execution. Each `EventSource.execute()` typically handles its own batch upserts. The `job_execution` status is updated via CAS, not within the same transaction as the data upserts. [HIGH CONFIDENCE]

### TransactionTemplate in Seller Operations

`QueueAutoPopulationService` uses programmatic `TransactionTemplate` for per-queue isolation, preventing one queue's failure from affecting others. [CONFIRMED]

### Event Listeners

- `@TransactionalEventListener(phase = AFTER_COMMIT)` used for post-commit side effects (notifications, email)
- `@EventListener` used for in-transaction reactions
- `@Async` + `@Transactional(REQUIRES_NEW)` used for listeners needing their own transaction in a separate thread

---

## H. Dependency Map

### Module Dependency Graph (Maven)

```
datapulse-common ← (no deps)
datapulse-platform ← datapulse-common
datapulse-tenancy-iam ← datapulse-platform
datapulse-integration ← datapulse-platform
datapulse-etl ← datapulse-common, datapulse-platform, datapulse-integration
datapulse-analytics-pnl ← datapulse-platform, datapulse-etl (for canonical entities), datapulse-integration
datapulse-pricing ← datapulse-platform, datapulse-etl, datapulse-integration, datapulse-execution, datapulse-audit-alerting
datapulse-execution ← datapulse-platform, datapulse-integration
datapulse-promotions ← datapulse-platform, datapulse-etl, datapulse-integration, datapulse-execution
datapulse-seller-operations ← datapulse-platform, datapulse-etl, datapulse-pricing, datapulse-execution, datapulse-integration
datapulse-audit-alerting ← datapulse-platform
datapulse-api ← ALL modules (composition root)
```

### Cross-Module Runtime Dependencies (Beyond Maven)

| From | To | How | Concern |
|------|----|-----|---------|
| `pricing` → `execution` | `ActionService` | Direct method call (pricing schedules actions) | Tight coupling |
| `pricing` → `audit-alerting` | `AutomationBlockerChecker` | Direct method call | Module boundary leak |
| `seller-ops` → `pricing` | `ManualPriceLockService` | Direct method call (offer lock/unlock) | Cross-module dependency |
| `promotions` → `execution` | Shares `AuditEvent` via platform | Event publishing | Loose coupling (OK) |
| `seller-ops` → `integration` | `MismatchMonitorService` → `AlertTriggeredEvent` | Event publishing | Loose coupling (OK) |
| `etl` → `integration` | `CredentialResolver` | Direct method call | Expected dependency |

---

## I. Where Business Logic Actually Lives

### In Domain Services (Expected Location) — ~70%

Most business logic correctly resides in `domain/` packages:
- `PricingRunService` — pricing pipeline orchestration
- `ActionService` — price action lifecycle
- `PromoEvaluationService` — promo evaluation
- `ConnectionService` — connection lifecycle
- `MismatchMonitorService` — mismatch detection

### In Controllers (Unexpected Location) — ~5%

Several controllers contain logic that should be in services:
- `WorkspaceController`, `MemberController`, `InvitationController` — map JPA entities to response DTOs directly in the controller
- `PriceActionController` — maps `Row` and `Entity` objects to responses inline
- `GridController` — imports `SavedViewEntity` from persistence layer and resolves view filters inline

### In Adapters (Expected for Provider-Specific Logic) — ~10%

Marketplace-specific logic (pagination, field mapping, error classification) correctly lives in adapters. However, some adapters contain business decisions:
- `OzonPriceWriteAdapter` — Ozon per-product rate limiting decision (10/hour) embedded in adapter, not in domain
- `WbPriceWriteAdapter` — async poll loop logic embedded in adapter

### In Repository Implementations (JDBC Queries as Logic) — ~15%

Complex JDBC repositories contain significant SQL-level business logic:
- `PricingDataReadRepository` — multi-join query assembling pricing signals
- `GridPostgresReadRepository` — dynamic SQL construction with filter/sort/join logic
- Various `*QueryRepository` — pagination, filtering, and aggregation logic

This is common in JDBC-heavy systems and is not inherently wrong, but it means business rules are expressed in SQL rather than Java, making them harder to test in isolation. [CONFIRMED]

---

## J. Initial Architectural Observations

### J1. The module boundary between pricing and execution is load-bearing but tight

Pricing directly calls `ActionService.createAction()` to schedule price changes. The execution module is both a downstream consumer of pricing decisions AND a dependency of pricing (for conflict detection, deferred actions). This creates a bidirectional conceptual dependency, even if the Maven dependency is one-directional. [CONFIRMED]

### J2. The canonical model serves double duty

The canonical entities in `datapulse-etl` are used by:
1. ETL for writing (normalizers → upsert repositories)
2. Pricing for reading (signals, policy resolution)
3. Seller-ops for reading (grid, offer detail)
4. Analytics for reading (materialization source)
5. Promotions for reading (campaign products)

This means the canonical model is a **shared kernel** across most modules, but it's physically owned by `datapulse-etl`. Changes to canonical schema affect nearly everything. [CONFIRMED]

### J3. Credential key naming is inconsistent across modules

Different modules use different key names when accessing the same Vault secret:
- `CredentialMapper` (integration) stores: `apiToken` (WB), `clientId` / `apiKey` (Ozon)
- Execution adapters expect: `token` (WB), `client_id` / `api_key` (Ozon)
- Promo `PromoCredentialResolver` expects: `client-id` / `api-key` (Ozon)

This inconsistency is a runtime bug risk — if credential resolvers in different modules produce incompatible key maps. [CONFIRMED — high severity]

### J4. datapulse-api is a composition root with business logic

`datapulse-api` should be pure wiring (configuration, consumers, error handler). However, it also contains:
- Outbox poller with scheduling logic
- RabbitMQ consumer classes with retry/error handling
- WebSocket configuration with authentication logic

This is borderline acceptable for a composition root, but some of this logic (especially consumer error handling) is business-relevant. [HIGH CONFIDENCE]

### J5. JPA and JDBC coexist without clear rules

Some modules use JPA entities for write and JDBC for read (pricing, execution, seller-ops). Others use JPA for everything (tenancy). The canonical model uses exclusively JDBC. There is no documented rule for when to use which approach, leading to inconsistency. [CONFIRMED]

---

## K. Confidence / Uncertainties

| Finding | Confidence | Note |
|---------|-----------|------|
| Module structure and responsibilities | [CONFIRMED] | Verified by reading all module POMs and service classes |
| Pricing pipeline flow | [CONFIRMED] | Read `PricingRunService` in full |
| Price action state machine | [CONFIRMED] | Read `ActionService` and CAS repository |
| Credential key inconsistency | [CONFIRMED] | Read three different credential resolvers |
| Single-transaction pricing run | [CONFIRMED] | `@Transactional` on `executeRun()` |
| ClickHouse as read-only from business logic | [CONFIRMED] | No write operations outside materialization |
| Full extent of controller-level entity mapping | [HIGH CONFIDENCE] | Sampled several controllers, may have missed edge cases |
| Completeness of cross-module runtime dependencies | [HIGH CONFIDENCE] | Based on constructor injection analysis |
| Advertising adapter status (stub) | [UNCERTAIN] | Source classes exist but actual HTTP calls not confirmed in all paths |
| Performance impact of large pricing transactions | [UNCERTAIN] | Confirmed single transaction, but actual lock contention not measured |
