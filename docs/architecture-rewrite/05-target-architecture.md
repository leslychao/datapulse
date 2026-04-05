# 05 Target Architecture

## A. Architectural Goals

1. **Business logic is explicit and central.** Domain rules (pricing formulas, guard chains, state machines, P&L formulas) are expressed in plain domain code, not scattered across SQL, adapters, controllers, or framework annotations.

2. **Every module has one reason to exist.** No catch-all "platform" or "common" modules that accumulate unrelated concerns. Each module solves a specific, nameable business or infrastructure problem.

3. **The canonical model is a first-class shared contract.** The product catalog and marketplace data (offers, prices, stocks, orders, sales, returns, finance) are owned by a dedicated `catalog` module, not buried inside ETL. All consumers depend on explicit read interfaces.

4. **Marketplace connectivity is unified.** One module owns credentials, rate limiting, HTTP client configuration, and health probes. No more three different credential resolvers with three different key name conventions.

5. **Pricing and execution are decoupled via commands.** Pricing produces decisions and publishes price-change commands. Execution consumes them. No direct method call from pricing into execution. This eliminates the bidirectional conceptual dependency identified in legacy (01-J1).

6. **Transactions are right-sized.** No monolithic transactions spanning thousands of entities. Pricing runs process in batches. Execution uses CAS for state transitions. ETL upserts per-batch. Each operation's transaction scope is documented.

7. **Infrastructure is on the edges.** Domain services never import HTTP clients, S3 clients, or ClickHouse drivers. Infrastructure adapters implement domain-defined interfaces and are injected at the boundary.

8. **All legacy defects from 03 are structurally prevented.** The architecture makes it impossible (or at least difficult) to recreate the credential inconsistency, the monolithic transaction, the missing reconciliation, and the uncontracted shared kernel.

---

## B. Core Design Principles

| Principle | What It Means Concretely |
|-----------|-------------------------|
| **Domain-centric** | Business rules live in domain services and value objects. Not in SQL, not in controllers, not in infrastructure. |
| **Explicit contracts at module boundaries** | Modules communicate through defined interfaces (Java interfaces or record-based DTOs), never by sharing JPA entities or persistence classes across boundaries. |
| **One persistence model** | JDBC with explicit repositories throughout. No JPA. Read models (`*Row`) and write models (domain classes) are separate by design, not by accident. |
| **Outbox for all critical side effects** | Anything that must survive a crash (price execution, ETL sync trigger, reconciliation check, promo execution) goes through the transactional outbox. In-process events for non-critical side effects (audit logging, notifications). |
| **Batch by default** | Long-running operations (pricing runs, ETL ingestion, promo evaluation) process data in bounded batches with per-batch commits. No all-or-nothing transactions spanning the entire dataset. |
| **Centralized marketplace I/O** | All HTTP communication with Wildberries and Ozon APIs goes through the `marketplace` module. Credentials, rate limits, retry, logging — all in one place. |
| **CH is optional enrichment, not a dependency** | ClickHouse is used for analytics and grid enrichment. If CH is unavailable, the system continues operating with degraded analytics. No business command depends on CH data for correctness. |
| **Audit by design** | Every mutation-producing use case emits an audit event as part of its contract, not as an afterthought or optional listener. |

---

## C. High-Level Structure

```
┌──────────────────────────────────────────────────────────────────────┐
│                            app (composition root)                     │
│  Wiring, startup, migrations, outbox poller, message consumers,       │
│  global error handler, WebSocket config                               │
└───────────┬──────────────────────────────────────────────────────────┘
            │ depends on all modules
            │
┌───────────┴──────────────────────────────────────────────────────────┐
│                         Domain Modules                                │
│                                                                       │
│  ┌─────────┐  ┌───────────┐  ┌─────────┐  ┌────────────┐            │
│  │   iam   │  │  catalog  │  │ pricing │  │ execution  │            │
│  └─────────┘  └───────────┘  └─────────┘  └────────────┘            │
│                                                                       │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌──────────┐       │
│  │ ingestion  │  │ promotions │  │ operations │  │ alerting │       │
│  └────────────┘  └────────────┘  └────────────┘  └──────────┘       │
│                                                                       │
│  ┌────────────┐                                                       │
│  │ analytics  │                                                       │
│  └────────────┘                                                       │
└──────────────────────────────────────────────────────────────────────┘
            │
┌───────────┴──────────────────────────────────────────────────────────┐
│                      Infrastructure Modules                           │
│                                                                       │
│  ┌─────────────┐  ┌────────┐                                         │
│  │ marketplace │  │ kernel │                                         │
│  └─────────────┘  └────────┘                                         │
└──────────────────────────────────────────────────────────────────────┘
```

**11 modules total** (vs 12 in legacy). The reduction comes from eliminating the catch-all `platform` module and extracting the canonical model from ETL into `catalog`.

---

## D. Domain Core

The domain core consists of the modules that express business rules and own business state. They have no direct dependency on HTTP frameworks, database drivers, or external APIs.

### catalog — The Shared Data Contract

Owns the canonical product/financial model. This is the entity that legacy had buried inside `datapulse-etl` and that 5+ modules consumed without a contract.

**What it defines:**
- Product hierarchy interfaces: ProductMaster, SellerSku, MarketplaceOffer
- Canonical data interfaces: Price, Stock, Order, Sale, Return, FinanceEntry
- Promo campaign/product canonical representations
- Cost profile with SCD2 semantics
- Read contracts (interfaces) that other modules depend on
- Write contracts (interfaces) that ingestion implements

**What it does NOT contain:**
- ETL pipeline logic (that's in `ingestion`)
- SQL queries (those are in repository implementations within each module)
- ClickHouse schemas (that's in `analytics`)

### pricing — Decision Engine

Owns pricing policies, strategies, constraints, guards, runs, and decisions.

**Key architectural change from legacy:** Pricing does not directly call execution. Instead, after producing decisions, it publishes `SchedulePriceAction` commands via outbox. Execution consumes these commands independently.

**Batch processing:** Pricing runs process offers in configurable batches (e.g., 500 offers per batch-commit). Each batch is committed independently. Run progress is tracked at batch level.

### execution — Action Lifecycle

Owns the price action state machine, execution attempts, reconciliation, and simulation.

**Key architectural change from legacy:** 
- Reconciliation includes mandatory read-after-write verification (fixes legacy A3)
- WB async poll is handled as a scheduled reconciliation check, not an inline blocking loop (fixes legacy E2)
- Consumes `SchedulePriceAction` commands via outbox, does not expose `createAction()` as a callable service

### promotions — Promo Decision Engine

Owns promo policies, evaluation, decisions, and participation actions. Structurally parallel to pricing + execution, but simpler (fewer guards, simpler lifecycle).

### iam — Identity and Access

Owns tenants, workspaces, users, members, roles, invitations. Self-contained. No dependency on any other domain module.

### ingestion — Data Pipeline

Owns ETL orchestration: job tracking, marketplace-specific read adapters, normalizers, canonical upserts, S3 raw storage, ClickHouse materialization trigger.

**Key architectural change from legacy:** Writes to `catalog` module's write contracts. Does not own the canonical model definition.

### analytics — Financial Intelligence

Owns P&L calculations, returns/inventory analysis, data quality checks, provenance. Reads from `catalog` read contracts and ClickHouse analytical tables.

**Key architectural change from legacy:** CH degradation handling is centralized here, not scattered per-service.

### operations — Operational Read Model

Owns the operational grid, offer detail, search, saved views, working queues, mismatch detection, price/promo journals. This is a **read model** module — it does not own any business entities with lifecycles.

**Key architectural change from legacy:** Explicitly positioned as a projection/read-model. Reads from catalog, pricing, execution, promotions via their read contracts. Does not directly import entities from other modules.

### alerting — Safety and Notifications

Owns audit log, alert rules, alert events, notifications, and the automation blocker check. Provides the `AutomationBlockerChecker` interface that pricing consumes.

---

## E. Application / Use Case Layer

Each domain module exposes its use cases as **application services** — thin orchestration methods that:
1. Validate input
2. Load aggregate/entity from repository
3. Call domain logic
4. Persist result
5. Emit events or outbox entries
6. Return result

Application services do not contain business rules themselves. They coordinate between domain objects and infrastructure.

**Naming convention:** Use cases are named by what they do, not by pattern. `TriggerPricingRun`, `ApproveAction`, `EvaluatePromo`, `CreateConnection` — not `PricingRunApiService` or `PricingRunService` (legacy's confusing dual-service pattern from 03-D1 is eliminated).

**Controllers are thin:** Controllers deserialize HTTP, call the use case, serialize the response. No mapping logic, no entity access, no business decisions.

---

## F. Integration and Infrastructure Edges

### marketplace — Unified Marketplace Gateway

This module replaces legacy's scattered marketplace connectivity:

| Concern | Legacy (Broken) | New (Unified) |
|---------|-----------------|---------------|
| Credentials | 3 resolvers with 3 key schemas | 1 `CredentialService` with 1 canonical key schema |
| Rate limiting | 3 mechanisms (token bucket + AIMD + per-product) | 2 mechanisms: endpoint-level bucket + per-product limit. AIMD removed (known limits are sufficient). |
| HTTP client | Mix of reactive and blocking | One consistent approach per use case: streaming for large reads (ETL), request-response for writes |
| Health probes | In integration module | In marketplace module |
| Call logging | In integration module | In marketplace module |

**Who calls it:**
- `ingestion` — for read adapters (catalog sync)
- `execution` — for price write + read-after-write
- `promotions` — for promo write
- `iam` — indirectly, for connection health checks

### kernel — Minimal Shared Foundation

Contains only:
- Error codes and message keys (`MessageCodes`)
- Base exception hierarchy
- Shared value objects (MarketplaceType, Currency)
- Workspace context contract (interface, not implementation)

**What was removed vs legacy `platform` + `common`:**
- No `BaseEntity` (JDBC-only, no JPA)
- No outbox service (lives in `app`)
- No ETL hooks (live in `ingestion`)
- No security implementation (lives in `app`)
- No WebClient config (lives in `marketplace`)

### app — Composition Root

Wires everything together:
- Spring Boot application class
- Outbox poller + message consumers (each consumer delegates to the owning module's use case)
- Security configuration (filters, Keycloak integration)
- WebSocket configuration
- Liquibase migrations
- Global error handler

**Key rule:** `app` contains no business logic. If a consumer needs to make a business decision (e.g., retry classification), it delegates to a domain service in the owning module.

---

## G. Data Ownership and Source of Truth

Unchanged from legacy diagnostic (01-E), because these are confirmed invariants (04-D1, D2):

| Store | Role | Modules That Write | Modules That Read |
|-------|------|-------------------|-------------------|
| **PostgreSQL** | Source of truth for all business state | All modules | All modules |
| **ClickHouse** | Derived analytical data (star schema) | ingestion (materialization) | analytics, operations (enrichment) |
| **S3/MinIO** | Immutable raw API responses | ingestion | analytics (provenance) |
| **Vault** | Encrypted marketplace credentials | iam (via marketplace module on connection create/rotate) | marketplace (on credential resolve) |
| **Redis** | Ephemeral rate limit state | marketplace | marketplace |

---

## H. Transaction Boundaries

### Pricing Run — Batched

```
for each batch of N offers:
    BEGIN TRANSACTION
        load batch offers
        for each offer:
            resolve policy → calculate → constrain → guard → create decision
        batch-insert decisions
        update run progress counters
    COMMIT
    publish outbox entries for any CHANGE decisions (separate tx per batch)
```

Each batch is an independent transaction. If batch K fails, batches 1..K-1 are already committed. The run tracks progress (offers_processed, offers_remaining) and can be resumed.

### Price Action State Transitions — CAS (No Transaction)

Same pattern as legacy — this works well and is preserved:
```sql
UPDATE price_action SET status = :new WHERE id = :id AND status = :expected
```

### ETL Ingestion — Per-Source Batch

Each event source processes its data in batches of configurable size. Each batch upsert is an independent transaction. Job progress tracked via CAS on job_execution status.

### Outbox — Atomic with Domain Write

Domain command + outbox entry written in the same transaction. Outbox poller reads and publishes in a separate transaction. This is the same proven pattern from legacy (04-E).

### Promo Evaluation — Batched

Same batch pattern as pricing runs.

---

## I. Error Handling and Operational Model

### Error Classification

All marketplace API errors are classified at the boundary (`marketplace` module):

| Classification | Meaning | Action |
|---------------|---------|--------|
| RETRIABLE | Temporary failure (429, 500, 502, 503, 504, timeout) | Retry with backoff |
| NON_RETRIABLE | Permanent failure (400, 401, 403, 404) | Fail immediately, alert |
| UNCERTAIN | Ambiguous response (partial success, network error after send) | Schedule reconciliation check |

### Reconciliation as First-Class Flow

For price writes:
1. Write adapter sends price change to marketplace
2. Immediately schedule a **read-after-write verification** (delay: configurable, e.g., 30 seconds for Ozon sync API, 2 minutes for WB async task)
3. Verification reads current price from marketplace and compares with target
4. If match → SUCCEEDED. If no match and retries remain → schedule next check. If retries exhausted → FAILED + alert.

This replaces legacy's inline blocking poll (WB) and absent verification (Ozon).

### Automation Blocker

`alerting` module provides `AutomationBlockerChecker` interface. `pricing` module calls it before each run. If blocker is active, automated runs are rejected (manual runs still allowed).

The checker evaluates: active stale-data alerts, active missing-sync alerts, active critical-severity alerts. This is the same business rule as legacy (04-P4), implemented as a clean cross-module contract.

---

## J. Observability and Control Points

| Concern | Implementation |
|---------|---------------|
| **Audit log** | Every mutating use case emits structured audit event. Alerting module persists and queries them. |
| **API call log** | `marketplace` module logs all external HTTP calls with endpoint, status, latency, correlation ID. |
| **Credential access tracking** | `marketplace` module emits `CredentialAccessed` event with purpose on every secret read. |
| **Pricing explainability** | Each price decision stores immutable snapshots of policy, signals, constraints, guard results. |
| **Metrics** | Per-module metrics: pricing run duration/batch count, action execution latency, ETL ingest volume, API error rates. |
| **Health checks** | Per-connection marketplace health (via `marketplace` module). Data freshness (via `alerting` module). |
| **Structured logging** | Correlation ID propagated through all flows. Key-value format for machine parsing. |

---

## K. What Is Intentionally Not Carried Over from Legacy

| Legacy Artifact | Why Dropped | Reference |
|----------------|-------------|-----------|
| JPA entities and `BaseEntity` | JDBC-only architecture eliminates dual-model confusion (03-B1) | 03-B1 |
| `datapulse-platform` catch-all module | Replaced by focused `kernel` + responsibilities distributed to owning modules (03-C1) | 03-C1 |
| Three credential resolvers with different key names | Single `CredentialService` in `marketplace` module (03-A1) | 03-A1 |
| Single-transaction pricing run | Batch-commit with progress tracking (03-A2) | 03-A2 |
| Direct pricing → execution method call | Command via outbox (01-J1) | 01-J1 |
| `*ApiService` + `*Service` dual pattern | One use-case service per operation (03-D1) | 03-D1 |
| Advertising stub adapters | Not implemented until feature is needed (03-D2) | 03-D2 |
| Inline blocking WB poll loop | Async reconciliation check (03-E2) | 03-E2 |
| Per-service CH degradation logic | Centralized in analytics module (03-E1) | 03-E1 |
| AIMD rate controller | Static rate limits with known marketplace documentation are sufficient (03-B2) | 03-B2 |
| `TenancyAuditPublisher` wrapper | Unified audit event mechanism (03-C2) | 03-C2 |
| Mixed reactive/blocking HTTP | Consistent model per use case (03-A5) | 03-A5 |
| Strategy+Registry pattern for ETL sources | Simple dispatch (marketplace type + event type → handler function). Pattern is implementation, not business rule (02-I). | 02-I |
| MapStruct mappers | Manual mapping or a single consistent mapper approach. MapStruct adds build complexity for marginal benefit. | 02-I |

---

## L. Why This Architecture Is Better for the Current Scope

| Problem in Legacy | How New Architecture Prevents It |
|-------------------|----------------------------------|
| Credential key chaos (3 resolvers, 3 schemas) | Single `CredentialService` in `marketplace` — physically impossible to have divergent key names |
| Canonical model buried in ETL, consumed by 5+ modules without contract | `catalog` module defines explicit read/write interfaces — compile-time breakage if contract changes |
| Single-transaction pricing run (scalability ceiling) | Batch-commit is the default. Single-batch transactions are bounded by design. |
| No read-after-write verification | Reconciliation is a mandatory step in the execution flow, not optional |
| Mixed JPA/JDBC without rules | JDBC-only. One model. No confusion. |
| Platform module catches everything | `kernel` is minimal (error codes + value objects). Domain-specific concerns live in domain modules. |
| Pricing directly calls execution | Commands via outbox. Modules are independently deployable in the future if needed. |
| Outbox consumers contain business logic in composition root | Consumers in `app` are thin delegators. Business logic (retry classification, error handling) lives in domain modules. |
| CH degradation scattered across services | Centralized degradation handling in `analytics` module, exposed as clean read interfaces |
