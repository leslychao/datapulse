# 07 Domain Model and Use Cases

## A. Core Domain Concepts

The domain model is organized around **what the business does**, not around how legacy code was structured. Every concept below was validated against the extracted business model (02) and invariants (04).

| Concept | Description | Source |
|---------|-------------|--------|
| **Marketplace Offer** | A product listing on a specific marketplace connection. Central entity that pricing, execution, promotions, and operations all revolve around. | 02-B (Product Hierarchy) |
| **Price Decision** | An immutable record of a pricing engine's output for one offer. Contains the full reasoning chain (policy, strategy, signals, constraints, guards). | 02-B (Pricing Domain), 04-P1 |
| **Price Action** | A command to change a price on a marketplace, with a lifecycle from approval through execution to reconciliation. | 02-B (Execution Domain), 04-E1-E4 |
| **Pricing Run** | A batch execution of the pricing pipeline for a set of offers within a connection. | 02-B (Pricing Domain) |
| **Canonical Data** | Marketplace-agnostic representations of marketplace data (orders, sales, returns, finance, prices, stocks). | 02-B (Canonical Data), 04-D1 |
| **Cost Profile** | Seller-provided COGS per SKU with temporal validity (SCD2). Bridges raw marketplace data to P&L. | 02-B (Canonical Data) |
| **Promo Decision** | Outcome of evaluating one product against one promotional campaign. | 02-B (Promotions Domain) |
| **Workspace** | The primary tenant isolation boundary. All business data is scoped to a workspace. | 02-B (Tenant & Workspace), 04-T1 |

---

## B. Entities

### Entities kept from legacy (verified as domain truth in 02-I)

| Entity | Module | Key Fields | Identity |
|--------|--------|------------|----------|
| Tenant | iam | id, name, createdAt | System-generated ID |
| Workspace | iam | id, tenantId, name, slug | System-generated ID |
| User | iam | id, externalId (Keycloak), email, name | System-generated ID + external IdP ID |
| Member | iam | id, workspaceId, userId, role | System-generated ID |
| Invitation | iam | id, workspaceId, email, role, token, status, expiresAt | System-generated ID + unique token |
| Connection | iam (or marketplace) | id, workspaceId, marketplaceType, name, status, credentialRef | System-generated ID |
| ProductMaster | catalog | id, workspaceId, internalCode, name | System-generated ID + workspace-scoped internal code |
| SellerSku | catalog | id, productMasterId, barcode | System-generated ID |
| MarketplaceOffer | catalog | id, sellerSkuId, connectionId, marketplaceSku, status | System-generated ID + (connectionId, marketplaceSku) natural key |
| CanonicalPrice | catalog | id, offerId, price, discountPrice, discountPct, minPrice, maxPrice | offerId (one-to-one with offer) |
| CanonicalStock | catalog | id, offerId, warehouseId, available, reserved | (offerId, warehouseId) |
| CanonicalOrder | catalog | id, connectionId, externalOrderId, orderDate, status, quantity, price | (connectionId, externalOrderId) natural key |
| CanonicalSale | catalog | id, connectionId, externalSaleId, saleDate, quantity, amount, commission | (connectionId, externalSaleId) natural key |
| CanonicalReturn | catalog | id, connectionId, externalReturnId, returnDate, quantity, amount, reason | (connectionId, externalReturnId) natural key |
| CanonicalFinanceEntry | catalog | id, connectionId, externalEntryId, financeDate, type, amount, currency | (connectionId, externalEntryId) natural key |
| CostProfile | catalog | id, sellerSkuId, cogs, validFrom, validTo | (sellerSkuId, validFrom) |
| PromoCampaign | catalog | id, connectionId, externalCampaignId, name, startDate, endDate, type | (connectionId, externalCampaignId) natural key |
| PromoProduct | catalog | id, campaignId, offerId, status, requiredPrice, requiredDiscount | (campaignId, offerId) |
| PricePolicy | pricing | id, workspaceId, name, strategyType, version, params, constraints, guardConfig, executionMode | System-generated ID |
| PolicyAssignment | pricing | id, policyId, scope (connection/category/offer), scopeId | System-generated ID |
| PricingRun | pricing | id, connectionId, triggerType, status, progress, counters | System-generated ID |
| PriceDecision | pricing | id, runId, offerId, decisionType, targetPrice, explanation, signalSnapshot, policySnapshot, constraintsApplied, guardResults | System-generated ID |
| ManualPriceLock | pricing | id, offerId, lockedBy, lockedAt, expiresAt, reason | System-generated ID |
| PriceAction | execution | id, offerId, connectionId, decisionId, targetPrice, status, scheduledAt, expiresAt | System-generated ID |
| PriceActionAttempt | execution | id, actionId, attemptNumber, requestSummary, responseSummary, status, executedAt | System-generated ID |
| PromoPolicy | promotions | id, workspaceId, name, autoParticipateCategories, autoDeclineCategories, config | System-generated ID |
| PromoEvaluationRun | promotions | id, connectionId, status, progress | System-generated ID |
| PromoDecision | promotions | id, runId, campaignId, offerId, decision, reason | System-generated ID |
| PromoAction | promotions | id, decisionId, campaignId, offerId, actionType, status | System-generated ID |
| SavedView | operations | id, workspaceId, name, filters, columns, sortOrder, isDefault | System-generated ID |
| WorkingQueue | operations | id, workspaceId, name, criteria, itemLimit | System-generated ID |
| QueueItem | operations | id, queueId, offerId, priority, addedAt | System-generated ID |
| Mismatch | operations | id, offerId, type, expected, actual, detectedAt, resolvedAt | System-generated ID |
| AlertRule | alerting | id, workspaceId, type, config, thresholds, isActive | System-generated ID |
| AlertEvent | alerting | id, ruleId, severity, details, status, triggeredAt, resolvedAt | System-generated ID |
| AuditEntry | alerting | id, workspaceId, userId, action, entityType, entityId, details, timestamp | System-generated ID |
| Notification | alerting | id, workspaceId, userId, type, message, isRead, createdAt | System-generated ID |
| JobExecution | ingestion | id, connectionId, eventType, status, startedAt, completedAt, checkpoint | System-generated ID |

### Entities NOT carried from legacy

| Legacy Entity/Concept | Why Dropped |
|----------------------|-------------|
| `BaseEntity` (JPA) | No JPA in new architecture. Timestamps handled per-module. |
| `OutboxEntry` as domain entity | Outbox is infrastructure in `app`, not domain. |
| `IntegrationCallLog` as domain entity | Call logging is infrastructure in `marketplace`. |
| Advertising-related entities | Not implemented. Will be added when feature is needed. |
| `SyncState` as separate entity | Merged into `Connection` or tracked within `JobExecution`. |

### New entity: Connection moved to iam

In legacy, Connection was in `datapulse-integration`. In the new architecture, Connection belongs to `iam` because:
- A connection is workspace-scoped (tenant domain)
- Connection CRUD is an IAM-level operation (create, archive, configure)
- Credential management goes through `marketplace` module

However, Connection's **runtime behavior** (health checks, sync state) is managed by `marketplace` and `ingestion` respectively. `iam` owns the entity; `marketplace` provides health probes; `ingestion` tracks sync progress.

**Alternative considered:** Connection as its own module or in `marketplace`. Rejected because Connection is fundamentally a workspace resource — it belongs with the tenant model.

---

## C. Value Objects

Value objects are immutable types with no identity, used throughout the domain:

| Value Object | Type | Values/Fields |
|-------------|------|---------------|
| MarketplaceType | Enum | WB, OZON |
| ConnectionStatus | Enum | PENDING_VALIDATION, ACTIVE, AUTH_FAILED, DISABLED, ARCHIVED |
| WorkspaceRole | Enum | OWNER, ADMIN, PRICING_MANAGER, OPERATOR, ANALYST, VIEWER |
| StrategyType | Enum | TARGET_MARGIN, PRICE_CORRIDOR |
| ExecutionMode | Enum | RECOMMENDATION, SEMI_AUTO, FULL_AUTO, SIMULATED |
| ActionStatus | Enum | PENDING_APPROVAL, APPROVED, ON_HOLD, REJECTED, SCHEDULED, EXECUTING, RECONCILIATION_PENDING, SUCCEEDED, FAILED, RETRY_SCHEDULED, EXPIRED, CANCELLED, SUPERSEDED |
| DecisionType | Enum | CHANGE, SKIP, HOLD |
| PromoDecisionType | Enum | PARTICIPATE, DECLINE, DEACTIVATE, PENDING_REVIEW |
| ErrorClassification | Enum | RETRIABLE, NON_RETRIABLE, UNCERTAIN |
| TriggerType | Enum | MANUAL, SCHEDULED, POST_SYNC, POLICY_CHANGE |
| RateLimitGroup | Enum | WB_CONTENT, WB_PRICES, WB_STATISTICS, WB_ANALYTICS, WB_MARKETPLACE, OZON_DEFAULT |
| GuardResult | Record | passed: boolean, guardName: String, messageKey: String, args: Map |
| SignalSnapshot | Record (JSONB) | salesVelocity, marginTrend, competitorPrice, stockLevel, etc. |
| PolicySnapshot | Record (JSONB) | strategyType, params, constraints, guardConfig, executionMode |
| ExplanationSummary | String | Human/machine-readable reasoning chain |
| Money | Record | amount: BigDecimal, currency: String |
| DateRange | Record | from: LocalDate, to: LocalDate |

### Value objects removed vs legacy

| Legacy Value Object | Why Removed |
|--------------------|-------------|
| `EtlEventType` | Internal to ingestion. Not a cross-module domain concept. Becomes a package-level enum within ingestion. |
| `AuditEvent` as platform-level class | Replaced by per-module domain events. `alerting` provides a unified `AuditLogger` interface. |

---

## D. Aggregates and Invariants

### Aggregate boundaries

| Aggregate Root | Module | Contains | Transaction Boundary |
|---------------|--------|----------|---------------------|
| **Workspace** | iam | Members, Invitations | Per-operation |
| **Connection** | iam | — (sync state tracked separately) | Per-operation |
| **PricePolicy** | pricing | PolicyAssignments | Per-operation (version bump on logic change) |
| **PricingRun** | pricing | PriceDecisions (batch-committed) | Per-batch |
| **PriceAction** | execution | PriceActionAttempts | CAS per state transition |
| **PromoEvaluationRun** | promotions | PromoDecisions (batch-committed) | Per-batch |
| **PromoAction** | promotions | — | CAS per state transition |
| **AlertRule** | alerting | — | Per-operation |
| **AlertEvent** | alerting | — | Per-operation |
| **WorkingQueue** | operations | QueueItems | Per-operation |

### Invariants by aggregate

**Workspace (iam)**

| ID | Invariant | Enforcement |
|----|-----------|-------------|
| T1 | Exactly one OWNER per workspace | Domain service check on role assignment |
| T3 | Ownership transfer is explicit, not a role change | Dedicated use case with validation |
| — | Invitation token is unique and has expiry | Token generation + expiry check on accept |
| — | User cannot be a member of the same workspace twice | Unique constraint (userId, workspaceId) |

**PricePolicy (pricing)**

| ID | Invariant | Enforcement |
|----|-----------|-------------|
| P2 | Version increments on logic change | Domain service bumps version on update |
| — | Assignment scopes do not overlap for the same policy | Domain service validates scope uniqueness |
| — | Only one active policy per offer (via assignment resolution) | PolicyResolver picks most specific scope |

**PricingRun (pricing)**

| ID | Invariant | Enforcement |
|----|-----------|-------------|
| P1 | Decisions are immutable after creation | INSERT-only repository operation |
| P5 | Blast radius limit in FULL_AUTO | Counter checked per-batch; run paused if exceeded |
| P6 | Guard chain is ordered, short-circuit | GuardChain sorts by order, stops on first block |
| P4 | Automation blocked if data unreliable | AutomationBlockerChecker called before run start |
| P3 | Manual lock prevents automated pricing | ManualLockGuard is first in guard chain |

**PriceAction (execution)**

| ID | Invariant | Enforcement |
|----|-----------|-------------|
| E1 | SUCCEEDED only after reconciliation confirms | State machine: EXECUTING → RECONCILIATION_PENDING → SUCCEEDED (no shortcut) |
| E2 | State transitions are atomic CAS | UPDATE ... WHERE status = :expected |
| E4 | Only one active action per offer | Supersede check on action creation |
| — | Attempts are append-only | INSERT-only for attempts |
| — | Expired/cancelled are terminal | State machine prevents transitions out of terminal states |

**Canonical Data (catalog)**

| ID | Invariant | Enforcement |
|----|-----------|-------------|
| D1 | Marketplace-agnostic field names | Normalizer layer enforces during ingestion |
| D4 | UPSERT idempotency via natural keys | ON CONFLICT DO UPDATE with marketplace external IDs |
| D5 | Every record references source job | jobExecutionId required field |

---

## E. State Machines / Lifecycle Rules

### E1. Price Action State Machine

Preserved from legacy (02-F1) — this is confirmed domain truth (04-E1, E2):

```
PENDING_APPROVAL
    ├── approve → APPROVED
    └── reject  → REJECTED (terminal)

APPROVED
    ├── schedule → SCHEDULED
    └── hold     → ON_HOLD

ON_HOLD
    └── resume   → APPROVED

SCHEDULED
    └── execute  → EXECUTING

EXECUTING
    ├── success        → RECONCILIATION_PENDING
    └── failure        → FAILED
                           └── retry → RETRY_SCHEDULED
                                          └── re-execute → EXECUTING

RECONCILIATION_PENDING
    ├── confirmed      → SUCCEEDED (terminal)
    └── not confirmed  → schedule next reconciliation check
                           └── retries exhausted → FAILED (terminal)

Terminal states: SUCCEEDED, FAILED (final), REJECTED, EXPIRED, CANCELLED, SUPERSEDED
```

**Architectural changes in new system:**
- Reconciliation includes read-after-write (not just task status polling)
- WB async task handling: submit → RECONCILIATION_PENDING → reconciliation check polls task AND verifies price
- No inline blocking poll

### E2. Connection Status Lifecycle

Preserved from legacy (02-F2):

```
PENDING_VALIDATION → ACTIVE             (health check success)
PENDING_VALIDATION → AUTH_FAILED        (health check failure)
ACTIVE             → AUTH_FAILED        (credential failure)
ACTIVE             → DISABLED           (manual disable)
AUTH_FAILED        → ACTIVE             (credential update + successful validation)
DISABLED           → ACTIVE             (manual enable)
Any non-terminal   → ARCHIVED           (terminal)
```

### E3. Pricing Run Status

Preserved from legacy (02-F3), with batch awareness:

```
PENDING → IN_PROGRESS → COMPLETED / FAILED / COMPLETED_WITH_WARNINGS
PENDING → CANCELLED (manual cancel)
IN_PROGRESS → PAUSED (blast radius breaker or manual pause) → resumed → IN_PROGRESS
```

**New: batch progress tracking.** `IN_PROGRESS` state includes `batchesCompleted`, `batchesRemaining` counters. Partial completion is visible to users.

### E4. Alert Event Lifecycle

```
ACTIVE → ACKNOWLEDGED (user acknowledged) → RESOLVED (condition no longer met)
ACTIVE → RESOLVED (auto-resolved when condition clears)
ACTIVE → MUTED (user suppressed temporarily)
```

---

## F. Domain Services

Domain services contain business rules and orchestrate domain operations. They do not depend on infrastructure directly — they call domain port interfaces.

| Service | Module | Responsibility |
|---------|--------|----------------|
| `WorkspaceService` | iam | Create/update workspace, ownership transfer |
| `MemberService` | iam | Add/remove members, change roles |
| `InvitationService` | iam | Create invitation, accept with token, validate expiry |
| `ConnectionService` | iam | Create/update/archive connections |
| `PolicyResolver` | pricing | Resolve the most specific policy for a given offer (connection → category → offer scope) |
| `PricingEngine` | pricing | Core calculation: policy + signals → strategy → constraints → guards → decision. Stateless. |
| `PricingRunOrchestrator` | pricing | Batch orchestration: load batch → call PricingEngine per offer → batch-commit decisions → publish commands |
| `GuardChain` | pricing | Ordered evaluation of guards. Produces guard results for each guard. |
| `ManualLockService` | pricing | Lock/unlock offers for automated pricing |
| `BlastRadiusChecker` | pricing | Check if FULL_AUTO run exceeds change limit |
| `ActionLifecycleService` | execution | State transitions (approve, reject, hold, resume, cancel, expire, supersede) via CAS |
| `ExecutionOrchestrator` | execution | Execute a price action: resolve credentials, call write adapter, schedule reconciliation |
| `ReconciliationService` | execution | Read current price from marketplace, compare with target, update action status |
| `ErrorClassifier` | execution | Classify marketplace API errors into RETRIABLE/NON_RETRIABLE/UNCERTAIN |
| `PromoEvaluator` | promotions | Evaluate products against campaigns using promo policy rules |
| `PromoRunOrchestrator` | promotions | Batch orchestration for promo evaluation |
| `PromoActionService` | promotions | Execute promo participation/declination on marketplace |
| `PnlCalculator` | analytics | Calculate P&L at posting/product/account levels |
| `MaterializationOrchestrator` | analytics | Order and execute CH materializers (dims → facts → marts) |
| `DataQualityChecker` | analytics | Check for residuals, missing data, anomalies |
| `GridQueryService` | operations | Compose grid data from catalog + pricing + execution + promotions + analytics |
| `MismatchDetector` | operations | Compare expected vs actual marketplace state |
| `QueueManager` | operations | Auto-populate and manage working queues |
| `AlertEvaluator` | alerting | Evaluate alert rules against current data state |
| `AutomationBlockerChecker` | alerting | Determine if automation is safe for a given connection |
| `AuditLogger` | alerting | Persist structured audit entries |
| `NotificationFanout` | alerting | Distribute notifications to users by role |

---

## G. Use Cases

Each use case is an application-level method that orchestrates domain logic. Listed by module.

### iam

| Use Case | Actor | Flow |
|----------|-------|------|
| CreateWorkspace | User | Validate tenant ownership → create workspace → set user as OWNER |
| InviteMember | Admin/Owner | Validate role permission → create invitation → send email (async) |
| AcceptInvitation | Invited user | Validate token + expiry → create membership → mark invitation accepted |
| ChangeRole | Admin/Owner | Validate permission hierarchy → update member role → audit |
| TransferOwnership | Owner | Validate new owner is member → swap roles → audit |
| CreateConnection | Admin/Owner | Validate marketplace type → store credential in Vault → create connection → trigger health check |
| ArchiveConnection | Admin/Owner | Validate no active actions → mark ARCHIVED → audit |

### pricing

| Use Case | Actor | Flow |
|----------|-------|------|
| CreatePolicy | Pricing Manager | Validate params → create policy with version 1 → audit |
| UpdatePolicy | Pricing Manager | Validate params → increment version → audit |
| AssignPolicy | Pricing Manager | Validate scope → create assignment → audit |
| TriggerPricingRun | System/Manual | Check automation blocker → create run (PENDING) → publish outbox PRICING_RUN_EXECUTE |
| ExecutePricingRun | System (consumer) | Load run → for each batch: load offers → PricingEngine.calculate() per offer → batch-commit decisions → publish SchedulePriceAction per CHANGE decision → update progress → complete run |
| LockPrice | Operator | Create manual lock → audit |
| UnlockPrice | Operator | Remove manual lock → audit |
| PreviewImpact | User | Run PricingEngine in simulation mode (no persist, no commands) → return preview |

### execution

| Use Case | Actor | Flow |
|----------|-------|------|
| ReceivePriceActionCommand | System (consumer) | Deserialize command → check for existing active action (supersede if needed) → create action (PENDING_APPROVAL or APPROVED based on execution mode) |
| ApproveAction | Operator | CAS: PENDING_APPROVAL → APPROVED → schedule (outbox PRICE_ACTION_EXECUTE) |
| RejectAction | Operator | CAS: PENDING_APPROVAL → REJECTED |
| ExecuteAction | System (consumer) | CAS: SCHEDULED → EXECUTING → resolve credentials → call write adapter → CAS: EXECUTING → RECONCILIATION_PENDING → schedule reconciliation check |
| ReconcileAction | System (scheduled) | Read current price from marketplace → compare with target → CAS to SUCCEEDED or schedule retry |
| ExpireStuckActions | System (scheduled) | Find actions in non-terminal states past timeout → CAS to EXPIRED |

### promotions

| Use Case | Actor | Flow |
|----------|-------|------|
| CreatePromoPolicy | Pricing Manager | Validate config → create policy → audit |
| TriggerPromoEvaluation | System/Manual | Create run → publish outbox PROMO_EVAL_EXECUTE |
| ExecutePromoEvaluation | System (consumer) | For each batch: load campaigns + offers → PromoEvaluator per product-campaign pair → batch-commit decisions → create promo actions |
| ExecutePromoAction | System | Call promo write adapter via MarketplaceClient → update action status |

### analytics

| Use Case | Actor | Flow |
|----------|-------|------|
| QueryPnl | Analyst | Resolve connections for workspace → query PG + CH → calculate P&L → return result (with CH availability flag) |
| QueryReturns | Analyst | Similar to QueryPnl for returns data |
| QueryInventory | Analyst | Similar for inventory/stock data |
| TriggerMaterialization | System (post-ingest) | Execute materializers in order (dims → facts → marts) → log results |

### operations

| Use Case | Actor | Flow |
|----------|-------|------|
| QueryGrid | Operator | Resolve saved view → build dynamic query → execute on PG → optionally enrich from CH → return page |
| GetOfferDetail | Operator | Load offer from catalog → load pricing decisions, actions, promo status → compose response |
| SaveView | Operator | Validate filters/columns → persist view → audit |
| ManageQueue | Operator | Create/update queue → auto-populate based on criteria → return items |
| DetectMismatches | System (scheduled) | Compare canonical prices/stocks/promos with marketplace actuals → create mismatch records → trigger alerts |

### alerting

| Use Case | Actor | Flow |
|----------|-------|------|
| EvaluateAlertRules | System (scheduled) | For each active rule: check condition → create alert event if threshold exceeded → notify users → block automation if critical |
| AcknowledgeAlert | Operator | CAS: ACTIVE → ACKNOWLEDGED → audit |
| ResolveAlert | System/Operator | CAS: ACTIVE/ACKNOWLEDGED → RESOLVED → unblock automation if blocker was active |
| MarkNotificationRead | User | Update notification read status |

### ingestion

| Use Case | Actor | Flow |
|----------|-------|------|
| TriggerSync | System/Manual | Create job execution (PENDING) → publish outbox ETL_SYNC_EXECUTE |
| ExecuteSync | System (consumer) | Acquire job (CAS) → for each event source: call read adapter → store raw in S3 → normalize → batch upsert to catalog → update progress → complete job → trigger materialization |

---

## H. Decision Points

| Decision | Module | Inputs | Output | Rule |
|----------|--------|--------|--------|------|
| Which pricing strategy to apply | pricing | Policy.strategyType | Strategy implementation | Dispatch by strategyType enum |
| Whether to block price change | pricing | Offer state, guard config | GuardResult (PASS/BLOCK) | Ordered guard chain, short-circuit on first BLOCK |
| Whether automation is safe | alerting | Active alert events for connection | Boolean (safe/blocked) | Blocked if any critical alert is active |
| How to classify an API error | execution | HTTP status, response body, exception type | ErrorClassification | Whitelist: 429/500/502/503/504 → RETRIABLE; 400/401/403/404 → NON_RETRIABLE; unknown → UNCERTAIN |
| Which policy applies to an offer | pricing | Offer's connection, category, offer ID + all policy assignments | Single policy | Most specific scope wins: offer > category > connection |
| Whether reconciliation passed | execution | Target price, actual marketplace price | Boolean | Exact match (within tolerance, e.g., ±0.01 RUB for rounding) |
| Whether to participate in promo | promotions | PromoPolicy config, offer category, campaign type | PromoDecisionType | Auto-participate if category in whitelist, auto-decline if in blacklist, otherwise PENDING_REVIEW |
| Whether blast radius is exceeded | pricing | Offers changed in current run, total offers for connection, threshold % | Boolean | changed/total > threshold → PAUSE run |

---

## I. Domain Events or Explicit Alternatives

### Events that trigger side effects (via Spring ApplicationEventPublisher)

These are in-process, non-critical events. If they fail, the primary operation still succeeds.

| Event | Published By | Consumed By | Purpose |
|-------|-------------|-------------|---------|
| `AuditAction` | All modules (via AuditLogger interface) | alerting | Record audit trail |
| `CredentialAccessed` | marketplace | alerting | Track credential usage |
| `MemberRoleChanged` | iam | alerting (audit + notification) | Notify workspace members |
| `ConnectionStatusChanged` | iam | alerting, operations (mismatch) | Trigger alerts on auth failure |
| `AlertTriggered` | alerting | alerting (notification fanout) | Distribute notifications |
| `PricingRunCompleted` | pricing | alerting (if warnings) | Notify on run issues |

### Commands that require reliable delivery (via outbox)

These must survive crashes. At-least-once delivery is mandatory (04-E3).

| Command | Published By | Consumed By | Purpose |
|---------|-------------|-------------|---------|
| `ExecutePricingRun` | pricing | pricing (consumer in app) | Trigger async pricing run execution |
| `SchedulePriceAction` | pricing | execution (consumer in app) | Create price action from pricing decision |
| `ExecutePriceAction` | execution | execution (consumer in app) | Trigger price action execution on marketplace |
| `ScheduleReconciliation` | execution | execution (consumer in app) | Trigger read-after-write verification |
| `ExecuteEtlSync` | ingestion | ingestion (consumer in app) | Trigger ETL sync for a connection |
| `ExecutePromoEvaluation` | promotions | promotions (consumer in app) | Trigger async promo evaluation |
| `ExecutePromoAction` | promotions | promotions (consumer in app) | Execute promo participation on marketplace |

### Clear boundary: when to use what

| Criterion | Domain Event (Spring) | Outbox Command |
|-----------|-----------------------|----------------|
| Must survive application crash | No | **Yes** |
| Failure blocks primary operation | No | No (async in both cases) |
| Cross-process delivery needed | No (in-process only) | **Yes** (RabbitMQ) |
| Examples | Audit, notifications | Price execution, ETL sync, reconciliation |

---

## J. What the New Domain Model Fixes Compared to Legacy

| Legacy Problem | How New Model Fixes It | Reference |
|---------------|------------------------|-----------|
| Canonical model buried in ETL, consumed without contract | `catalog` module with explicit read/write interfaces. Compile-time breakage on contract change. | 03-A4 |
| Three credential resolvers with different key schemas | Single `CredentialService` in `marketplace`. Canonical key names defined once. | 03-A1 |
| PricingRun as single transaction | `PricingRunOrchestrator` processes in batches. Each batch independently committed. Run tracks progress. | 03-A2 |
| No read-after-write verification | `ReconciliationService` reads current price from marketplace after write. Mandatory step, not optional. | 03-A3 |
| Dual *ApiService + *Service pattern | One service per use case. No thin wrapper indirection. | 03-D1 |
| Controller-level entity mapping | Controllers call use-case services that return response DTOs. No entity access in controllers. | 03-F1 |
| GridController imports persistence entity | `operations` depends on read contracts from other modules. No persistence imports across boundaries. | 03-F2 |
| PriceActionFilter in domain package | Filter/query DTOs live in `api/` layer. Domain contains only business types. | 03-E3 |
| Mixed JPA/JDBC without rules | JDBC only. One persistence approach. No dual-model confusion. | 03-B1 |
| PricingModule directly calls ExecutionModule | Commands via outbox. No direct method call across boundary. | 01-J1 |
| TenancyAuditPublisher vs ApplicationEventPublisher | Unified `AuditLogger` interface in alerting. All modules use the same contract. | 03-C2 |
| Platform module as catch-all | `kernel` is minimal. Domain-specific concerns in domain modules. | 03-C1 |
| Inline blocking WB poll | Async reconciliation: submit → RECONCILIATION_PENDING → scheduled check verifies task + price. | 03-E2 |
| Per-service CH degradation logic | Centralized in `analytics`. Other modules get CH data through analytics read interfaces. | 03-E1 |
