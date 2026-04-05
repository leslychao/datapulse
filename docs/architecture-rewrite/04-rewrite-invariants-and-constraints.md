# 04 Rewrite Invariants and Constraints

This document catalogs everything that **must not be lost** during the rewrite. These are the non-negotiable requirements, external contracts, data semantics, and guarantees that the new system must preserve — regardless of how differently it may be architected.

---

## A. Non-Negotiable Business Invariants

These are rules that must always hold true. Violating any of these would break the product's core value proposition.

### Pricing Invariants

| ID | Invariant | Source | Confidence |
|----|-----------|--------|-----------|
| P1 | **A price decision is immutable once created.** Its signal snapshot, policy snapshot, constraint results, and guard evaluations are never modified after persistence. | `PriceDecisionEntity` has no update operations. Decision is INSERT-only with full JSONB snapshots. | [CONFIRMED] |
| P2 | **Every price decision records the exact policy version that produced it.** If a policy is updated, the version increments, and new decisions use the new version. Old decisions retain the old version reference. | `PricePolicyEntity.version` incremented in `PricePolicyService` on logic change. `PriceDecisionEntity` stores `policyVersion` + `policySnapshot`. | [CONFIRMED] |
| P3 | **A manual price lock prevents all automated pricing.** If an operator locks a price for a specific offer, no pricing strategy, no policy, and no scheduled run can override that price until the lock expires or is manually removed. | `ManualLockGuard` checks lock repository and returns BLOCK if active lock exists. Guard runs before any price change. | [CONFIRMED] |
| P4 | **Automation is blocked when data is unreliable.** If stale data alerts or missing sync alerts are active for a connection, automated pricing runs for that connection are blocked. Manual pricing is still allowed. | `AutomationBlockerChecker` in pricing module checks alert state before run. | [CONFIRMED] |
| P5 | **Blast radius limits constrain FULL_AUTO mode.** In fully automated pricing, no single run may change prices for more than a configured percentage of a connection's offers. If the threshold is exceeded, the run pauses for manual review. | `BlastRadiusBreaker` in `PricingRunService`. Run status transitions to PAUSED. | [CONFIRMED] |
| P6 | **Guard chain evaluation is ordered and short-circuit.** Guards execute in a defined priority order. The first guard that returns BLOCK terminates the chain. All guard evaluations (pass or block) are recorded in the decision. | `PricingGuardChain` sorts guards by `order()`, iterates, stops on first block. | [CONFIRMED] |

### Execution Invariants

| ID | Invariant | Source | Confidence |
|----|-----------|--------|-----------|
| E1 | **A price action reaches SUCCEEDED only after reconciliation confirms the marketplace applied the target price.** There is no shortcut from EXECUTING to SUCCEEDED without a reconciliation check. | `ActionService` transitions to RECONCILIATION_PENDING after execution. `ReconciliationService` performs the confirmation. No direct path to SUCCEEDED from EXECUTING. | [CONFIRMED] |
| E2 | **State transitions are atomic (CAS).** A price action can only move to a new state from a specific expected current state. Concurrent conflicting transitions are rejected at the database level. | `PriceActionCasRepository` uses `UPDATE ... WHERE status = :expected` and checks affected rows. | [CONFIRMED] |
| E3 | **At-least-once delivery for critical commands.** Price action execution, ETL sync triggers, and reconciliation checks are scheduled via transactional outbox. If the application crashes after scheduling, the outbox poller will redeliver. | `OutboxService` writes within the same PG transaction as the domain command. `OutboxPoller` re-publishes pending events. | [CONFIRMED] |
| E4 | **Supersede policy for conflicting actions.** When a new price action targets the same offer while an older, non-terminal action exists, the older action may be superseded (terminal state). Only one active price action per offer at a time. | `ActionService.createAction()` checks for existing active actions. | [CONFIRMED] |

### Data Invariants

| ID | Invariant | Source | Confidence |
|----|-----------|--------|-----------|
| D1 | **Canonical model is marketplace-agnostic.** Canonical entities use normalized field names, not marketplace-specific terminology. The normalization boundary is the ETL normalizer layer. | All canonical entities use generic names (`price`, `quantity`, `status`), not WB/Ozon-specific. | [CONFIRMED] |
| D2 | **PostgreSQL is the authoritative source of truth for business state.** ClickHouse is a derived analytical store. If PG and CH disagree, PG wins. Business commands never depend on CH data for correctness (CH is used for signals and enrichment, with fallback when unavailable). | Architecture documentation + circuit breaker patterns in services. | [CONFIRMED] |
| D3 | **Raw data in S3 is immutable.** Once a raw API response is stored in S3, it is never modified. It may be deleted by retention policy after its TTL expires. Provenance links from canonical data to raw data must remain valid until retention expiry. | `RetentionService` manages deletion; raw data written once by ETL adapters. | [CONFIRMED] |
| D4 | **Canonical ingestion is idempotent via UPSERT.** Re-ingesting the same data (same job, same raw response) produces the same canonical state. Upsert keys are based on marketplace external IDs. | `*UpsertRepository` classes use `ON CONFLICT ... DO UPDATE` SQL. | [CONFIRMED] |
| D5 | **Every canonical record references its source job execution.** The `job_execution_id` field enables tracing any canonical value back to the specific ingestion job, and from there to the raw S3 object. | All canonical entity classes have `jobExecutionId` field. | [CONFIRMED] |

### Tenancy Invariants

| ID | Invariant | Source | Confidence |
|----|-----------|--------|-----------|
| T1 | **Workspace isolation.** All business data is scoped to a workspace. No query returns data from another workspace. Cross-workspace data access is not possible through the API. | Workspace context filter + `@PreAuthorize` checks on all endpoints. Connection-scoped data inherits workspace isolation. | [CONFIRMED] |
| T2 | **Role-based access control.** Users can only perform operations permitted by their workspace role. Critical operations (credential management, policy activation, automation mode changes) require elevated roles (ADMIN/OWNER/PRICING_MANAGER). | `@PreAuthorize` annotations on controller methods. | [CONFIRMED] |
| T3 | **Single owner per workspace.** Ownership transfer is an explicit operation, not a role assignment. | `WorkspaceService` + `MemberService` enforce this. | [CONFIRMED] |

---

## B. Required External Contracts

These are interfaces with systems outside our control that must be maintained exactly as they are.

### B1. Wildberries API Contracts

| Contract | Current Implementation | Must Preserve |
|----------|----------------------|---------------|
| Product catalog read | `POST /content/v2/get/cards/list` — cursor-based (updatedAt + nmID) | Pagination cursor mechanism. Field mapping to canonical model. |
| Price read | `GET/POST /api/v2/list/goods/filter` — per-size pricing | Size-level price granularity. Discount hierarchy (discount/club). |
| Price write | `POST /api/v2/upload/task` + poll `GET /api/v2/history/goods/task` | Async write model with task polling. |
| Stocks read | `POST /api/analytics/v1/stocks-report/wb-warehouses` | Warehouse-level stock breakdown. Note: no `reserved` field — canonical sets WB reserved = 0. |
| Orders/Sales/Finance read | Statistics API endpoints | Date-range cursor, incremental ingestion. |
| Auth | Bearer token in `Authorization` header | Token format and scoping (read vs write). |
| Rate limits | Per-endpoint limits (documented + discovered via 429) | Rate limit group assignments. AIMD or similar adaptive mechanism for unknown limits. |
| Base URLs | Content, Prices, Statistics, Analytics, Marketplace, Promo — separate subdomains | Correct URL routing per API domain. Note: `discounts-prices-api` not `discounts-api`. |

### B2. Ozon Seller API Contracts

| Contract | Current Implementation | Must Preserve |
|----------|----------------------|---------------|
| Product catalog read | `POST /v3/product/list` (cursor: `last_id` base64) + `/v3/product/info/list` (batch by ID) | Cursor pagination. Cannot detect end by small response size. |
| Attributes/Brand | `POST /v4/product/info/attributes` — brand is attribute_id 85 | Attribute-based data extraction for canonical fields. |
| Price read | `POST /v5/product/info/prices` | Nested `price` object. Number types (not strings). |
| Price write | `POST /v1/product/import/prices` | Synchronous write. String fields in request. Per-product rate limit (10/hour). |
| Promo read | `GET /v1/actions` + `POST /v1/actions/products` + `/candidates` | Campaign-level then product-level fetching. |
| Promo write | `POST /v1/actions/products/activate` / `deactivate` | Participation management per product per campaign. |
| Auth | `Client-Id` + `Api-Key` headers | Two-header authentication. |
| Ozon Performance (advertising) | OAuth2 on separate host | Separate auth flow for advertising data. |

### B3. Infrastructure Contracts

| System | Contract | Must Preserve |
|--------|----------|---------------|
| PostgreSQL | Primary data store. All business state. Liquibase migrations. | Schema evolution process. Transaction semantics. |
| ClickHouse | Analytical read model. Star schema with `ReplacingMergeTree` + `FINAL`. | Eventually-consistent analytical data. Degradation tolerance. |
| Vault | KV v2 secrets. Versioned. Cached with Caffeine. | Credential lifecycle: store → rotate → read → cache. |
| Redis | Token bucket for rate limiting. Sliding window for per-product limits. | Graceful degradation when Redis unavailable. |
| RabbitMQ | Outbox event delivery. At-least-once semantics. | Reliable event delivery for critical commands. |
| S3/MinIO | Raw data archive. Immutable objects. Retention-managed. | Provenance chain integrity. |
| Keycloak | OAuth2 IdP. User identity. External user IDs. | SSO flow. Token validation. |

---

## C. Required Data Semantics

These are rules about how data must be interpreted, regardless of storage format.

| Semantic | Rule | Source |
|----------|------|--------|
| **Sign conventions** | WB: commissions and logistics are positive amounts to subtract. Ozon: commissions are negative amounts (already subtracted). Canonical model normalizes to consistent sign convention. | `mapping-spec.md` + ETL normalizers |
| **Timestamps** | Ozon: Moscow timezone for financial dates. WB: dates as provided by API. Canonical stores in UTC or with explicit offset. | `mapping-spec.md` |
| **Currency** | All monetary amounts include explicit currency code. Default: RUB for RU marketplaces. | Canonical entities have `currency` field |
| **P&L attribution** | WB advertising: direct attribution by campaign keywords to products. Ozon advertising: pro-rata allocation based on spend share. | Analytics documentation |
| **COGS matching** | Cost profile matched to finance entry using `finance_date` against `validFrom/validTo` range. If no matching profile, COGS = 0 with data quality flag. | SCD2 pattern in `cost_profile` table |
| **Canonical price hierarchy** | For WB: `price` is base before discount, `discountPrice` is effective buyer price. For Ozon: `price.price` is the current selling price. | `mapping-spec.md` + pricing data read repository |
| **Stock semantics** | `available` = quantity available for sale. `reserved` = quantity reserved (WB: always 0, not provided). Per-warehouse breakdown. | Canonical stock entity + WB adapter notes |

---

## D. Required Lifecycle and Workflow Guarantees

| Guarantee | Description | Confidence |
|-----------|-------------|-----------|
| **Price action cannot skip states** | Every action must traverse the state machine in order. No shortcut from PENDING_APPROVAL to SUCCEEDED. | [CONFIRMED] |
| **Failed actions can be retried** | A failed action can transition to RETRY_SCHEDULED and then re-execute. Retry count and backoff are tracked. | [CONFIRMED] |
| **Reconciliation is mandatory** | Actions that successfully called the marketplace API must wait for reconciliation before being marked SUCCEEDED. | [CONFIRMED] |
| **Expiration handles abandoned actions** | Actions that remain in non-terminal state beyond a timeout are expired by `ExpirationJob` / `StuckStateDetector`. | [CONFIRMED] |
| **Promo sync updates canonical data** | ETL promo sync writes to canonical promo tables. Promo evaluation reads from canonical. These are separate steps, not atomic. Canonical is source of truth for campaign data. | [CONFIRMED] |
| **Invitation flow** | Create invitation → send email (async, optional if SMTP not configured) → user accepts with token → membership created → invitation marked accepted. Token has expiry. | [CONFIRMED] |
| **Cost profile versioning** | Multiple cost profile versions per SKU. Most recent valid profile at a given date wins. Bulk import replaces per-SKU. | [CONFIRMED] |

---

## E. Required Transaction and Consistency Guarantees

| Guarantee | Description | How Currently Implemented | Confidence |
|-----------|-------------|--------------------------|-----------|
| **Outbox atomicity** | Domain command and outbox event are persisted in the same database transaction. The outbox event is published to RabbitMQ by a separate poller. | `OutboxService` writes within caller's `@Transactional`. `OutboxPoller` reads and publishes separately. | [CONFIRMED] |
| **CAS for state transitions** | Price action state transitions must be atomic and prevent concurrent conflicting transitions. | `UPDATE ... WHERE id = :id AND status = :expected` with affected row count check. | [CONFIRMED] |
| **Canonical UPSERT atomicity** | Each canonical entity upsert is atomic (INSERT ON CONFLICT UPDATE). Batch upserts for performance. | JDBC batch with `ON CONFLICT` clauses. | [CONFIRMED] |
| **Workspace data isolation** | All data access queries must filter by workspace scope. This is enforced at query level, not at database level (no row-level security). | WHERE clauses in repositories + workspace context filter. | [CONFIRMED] |
| **Materialization order** | Analytics materialization must process dimensions before facts, and facts before marts. | `MaterializationService` sorts materializers by `phase` and `order`. | [CONFIRMED] |

---

## F. Security, Audit, and Boundary Constraints

| Constraint | Description | Confidence |
|-----------|-------------|-----------|
| **All business actions are audited** | Create, update, delete, approve, reject, lock, unlock, rotate credentials — all produce audit log entries. | [HIGH CONFIDENCE] — most services publish `AuditEvent` or use `TenancyAuditPublisher` |
| **Credential access is tracked** | Every time marketplace credentials are read from Vault (for ETL sync, price execution, promo execution, health check), a `CredentialAccessedEvent` is published with the purpose. | [CONFIRMED] |
| **Pricing decisions are explainable** | Every price decision stores enough context (signal snapshot, policy snapshot, guard results, constraint results) to fully reconstruct why the decision was made. | [CONFIRMED] |
| **External API calls are logged** | Marketplace API calls are logged in `integration_call_log` with endpoint, method, status, duration, and correlation ID. | [CONFIRMED] |
| **Role-based operation restrictions** | Destructive or sensitive operations (archive connection, activate FULL_AUTO, acknowledge alerts, view audit log) require specific roles. | [CONFIRMED] — `@PreAuthorize` annotations |
| **No direct cross-workspace data access** | There is no API endpoint or service method that accepts data from one workspace and applies it to another. | [HIGH CONFIDENCE] — workspace context is always resolved from auth + path parameter |

---

## G. Internal Compatibility That May Be Broken

These are internal implementation details that do NOT need to be preserved:

| What Can Change | Why |
|----------------|-----|
| Module/package structure | Internal organization, not external contract |
| Class names and hierarchy | Implementation detail |
| Database table names and column names | Internal schema (no external consumers) |
| JPA vs JDBC choice | Persistence technology |
| MapStruct vs manual mapping | Implementation detail |
| Spring Framework conventions (annotations, injection style) | Framework choice |
| Strategy+Registry pattern | Design pattern choice |
| Outbox + RabbitMQ | Any mechanism that provides at-least-once delivery |
| Redis for rate limiting | Any mechanism that enforces marketplace rate limits |
| ClickHouse as analytical store | Any analytical store that meets query performance requirements |
| ETL EventSource structure | Any pipeline structure that achieves the same canonical result |
| Number of Maven modules | Internal decomposition |
| REST API URL structure | Can be redesigned (though breaking frontend requires coordinated change) |

---

## H. External Compatibility That Must Be Preserved

| What Must Be Preserved | Why | Confidence |
|------------------------|-----|-----------|
| Marketplace API integration patterns (auth, pagination, rate limits) | External systems we don't control | [CONFIRMED] |
| Vault secret storage format | Operational infrastructure contract | [HIGH CONFIDENCE] |
| PostgreSQL as primary database | Operational infrastructure (migration effort otherwise extreme) | [CONFIRMED] |
| OAuth2/Keycloak authentication flow | User-facing authentication | [CONFIRMED] |
| Frontend API contract (REST endpoints, request/response shapes) | Frontend depends on it — unless frontend is also rewritten | [HIGH CONFIDENCE] |
| WebSocket notification delivery (STOMP) | Frontend real-time features | [HIGH CONFIDENCE] |
| Data already in production database | Migration must preserve existing business data | [CONFIRMED] |
| S3 raw data objects | Provenance chain to existing data | [CONFIRMED] |

---

## I. Confirmed Constraints vs Historical Assumptions

### Confirmed Constraints (backed by evidence)

| Constraint | Evidence |
|-----------|---------|
| PostgreSQL single source of truth | Architecture docs + code: no business writes to CH, all mutations go through PG |
| Reconciliation before SUCCEEDED | `ActionService` code: no path from EXECUTING to SUCCEEDED without reconciliation |
| Canonical model marketplace-agnostic | All canonical entity fields use generic names; marketplace-specific data stays in adapters |
| Workspace isolation for all data | Repository queries include workspace/connection filtering; `WorkspaceContextFilter` sets context |
| Rate limiting required for marketplace APIs | 429 responses documented; rate limit infrastructure exists; marketplace docs specify limits |

### Historical Assumptions (may or may not hold in rewrite)

| Assumption | Origin | May Not Hold Because |
|-----------|--------|---------------------|
| "Modular monolith is sufficient" | Initial architecture decision | Scaling requirements may demand different deployment |
| "ClickHouse is necessary for analytics" | Performance requirement for star schema queries | Advances in PG analytics (columnar, partitioning) may make CH optional |
| "RabbitMQ is the right message broker" | Infrastructure choice | Other brokers (Kafka, NATS) may better fit the use case |
| "Vault is needed for credential storage" | Security architecture | Simpler approaches (encrypted PG columns) may suffice for current scale |
| "Redis is needed for rate limiting" | Performance/scalability decision | In-memory rate limiting may suffice at current scale |
| "ETL runs as scheduled jobs in the monolith" | Deployment simplicity | Separate ETL workers may improve resilience |

---

## J. Confidence / Uncertainties

| Finding | Confidence |
|---------|-----------|
| Pricing invariants (P1-P6) | [CONFIRMED] — read all pricing service and guard code |
| Execution invariants (E1-E4) | [CONFIRMED] — read ActionService, CAS repository, outbox implementation |
| Data invariants (D1-D5) | [CONFIRMED] — verified against schema, entities, and repository code |
| Tenancy invariants (T1-T3) | [CONFIRMED] — read security filters, PreAuthorize annotations |
| External API contracts completeness | [HIGH CONFIDENCE] — based on adapter code + provider-api-specs docs, but undiscovered endpoints possible |
| Sign convention normalization correctness | [HIGH CONFIDENCE] — documented in mapping-spec, not line-by-line verified in all normalizers |
| P&L formula accuracy | [HIGH CONFIDENCE] — based on docs + analytics query repos, but advertising allocation not fully implemented |
| Historical assumptions validity | [UNCERTAIN] — these need explicit validation during rewrite design phase |
| Frontend API surface completeness | [UNCERTAIN] — 43 controllers cataloged, but edge cases in query parameters may be missed |
