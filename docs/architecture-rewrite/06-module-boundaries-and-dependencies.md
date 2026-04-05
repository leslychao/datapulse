# 06 Module Boundaries and Dependencies

## A. Module List

| # | Module | Type | Legacy Counterpart |
|---|--------|------|--------------------|
| 1 | `kernel` | Infrastructure | `datapulse-common` + parts of `datapulse-platform` |
| 2 | `marketplace` | Infrastructure | `datapulse-integration` (reorganized) |
| 3 | `iam` | Domain | `datapulse-tenancy-iam` |
| 4 | `catalog` | Domain (shared contract) | NEW — extracted from `datapulse-etl` canonical entities |
| 5 | `ingestion` | Domain | `datapulse-etl` (minus canonical model ownership) |
| 6 | `analytics` | Domain | `datapulse-analytics-pnl` |
| 7 | `pricing` | Domain | `datapulse-pricing` |
| 8 | `execution` | Domain | `datapulse-execution` |
| 9 | `promotions` | Domain | `datapulse-promotions` |
| 10 | `operations` | Domain (read model) | `datapulse-seller-operations` |
| 11 | `alerting` | Domain | `datapulse-audit-alerting` |
| 12 | `app` | Composition root | `datapulse-api` |

**Count:** 12 modules (same as legacy, but with cleaner responsibilities and one new module replacing a catch-all).

---

## B. Responsibility of Each Module

### 1. kernel

**Exists because:** every module needs shared error codes, base exception hierarchy, and fundamental value objects (MarketplaceType, Currency). Without a minimal shared foundation, these types would be duplicated or scattered.

**Owns:**
- `MessageCodes` — i18n message key constants
- Base exception hierarchy — `AppException`, `BadRequestException`, `NotFoundException`
- Fundamental value objects — `MarketplaceType`, `Currency`, `WorkspaceRole`
- Workspace context interface — `WorkspaceContext` (current workspace ID, user ID, role — implemented in `app`)

**Does NOT own:**
- Any persistence code, Spring configuration, security logic, HTTP client code, or outbox mechanism. These belong to their respective modules.

**Why simpler would not work:** Without `kernel`, modules would either duplicate error codes and value objects, or create circular dependencies by sharing types from one domain module to another.

**Why more complex is unnecessary:** Legacy's `datapulse-platform` proved that putting "shared infrastructure" in one module leads to catch-all growth. `kernel` stays minimal by containing only types that genuinely cross all module boundaries.

---

### 2. marketplace

**Exists because:** the credential key naming disaster (03-A1) proved that distributed marketplace connectivity is fragile. One module must own the full lifecycle: credentials, HTTP calls, rate limiting, logging, health.

**Owns:**
- `CredentialService` — single canonical key schema for WB and Ozon credentials. Reads from Vault, caches with Caffeine.
- `MarketplaceHttpClient` — configured WebClient instances per marketplace. Retry, timeout, error classification built in.
- `RateLimiter` — endpoint-level token bucket (Redis) + per-product frequency limit (Ozon, Redis). No AIMD.
- Health probes — per-connection health check against marketplace APIs.
- Call logging — structured log of every external HTTP call (endpoint, method, status, latency, correlation ID).
- `CredentialAccessedEvent` — emitted on every credential read with purpose tag.

**Exposes (interfaces for consumers):**
- `MarketplaceClient` interface — `get(path, connectionId, rateGroup)`, `post(path, body, connectionId, rateGroup)`. Consumers don't configure HTTP, retry, or auth themselves.
- `CredentialService` interface — `resolve(connectionId, marketplace)` → canonical credential map.
- `HealthProbe` interface — `check(connectionId)` → health status.

**Why simpler would not work:** Splitting marketplace connectivity across ingestion, execution, and promotions (legacy approach) led to three credential resolvers. The whole point of this module is preventing that fragmentation.

**Why more complex is unnecessary:** One module with clear interfaces is sufficient. No need for per-marketplace sub-modules — the marketplace-specific logic is in the adapters that call `MarketplaceClient`.

---

### 3. iam

**Exists because:** tenant/workspace/user management is a self-contained bounded context with its own lifecycle, invariants, and access control rules.

**Owns:**
- Tenants, workspaces, members, users
- Invitations (create → send email → accept with token → membership)
- Role management (OWNER, ADMIN, PRICING_MANAGER, OPERATOR, ANALYST, VIEWER)
- Ownership transfer
- Access checking (can user X do operation Y on workspace Z)

**Dependencies:** `kernel` only.

**Why simpler would not work:** IAM invariants (owner uniqueness, workspace isolation, role hierarchy) are complex enough to warrant their own module. Merging with another domain module would dilute responsibility.

**Why more complex is unnecessary:** No need for separate auth module — authentication is a cross-cutting concern handled by `app` (Keycloak integration, security filters). IAM handles authorization and identity management.

---

### 4. catalog

**Exists because:** the canonical model is the most heavily shared contract in the system. Legacy buried it in ETL (03-A4), creating hidden coupling. Extracting it as an explicit module forces consumers to depend on interfaces, not implementation.

**Owns:**
- Product hierarchy definition — ProductMaster, SellerSku, MarketplaceOffer
- Canonical data contracts — CanonicalPrice, CanonicalStock, CanonicalOrder, CanonicalSale, CanonicalReturn, CanonicalFinanceEntry
- Promo canonical contracts — PromoCampaign, PromoProduct
- Cost profile definition — CostProfile (SCD2)
- **Read contracts** — `CatalogReadService` interface with methods like `findOffersByConnection(connectionId)`, `findCurrentPrice(offerId)`, etc.
- **Write contracts** — `CatalogWriteService` interface with methods like `upsertOffers(batch)`, `upsertPrices(batch)`, etc.
- Repository interfaces — `OfferRepository`, `PriceRepository`, `StockRepository`, etc. (interface only, implementation provided by module that implements them)

**How it works:**
- `catalog` defines the contracts (interfaces + domain classes)
- `ingestion` module provides the write-path implementations (normalizers → `CatalogWriteService` implementation)
- Consumers (`pricing`, `operations`, `analytics`, `promotions`) depend on `catalog` read contracts
- Repository implementations (JDBC) are in the consuming module or in `catalog` itself for shared read queries

**Why simpler would not work:** Without an explicit module, the canonical model would be owned by one module (ETL) but consumed by five others — repeating the legacy coupling problem. The whole point is making the contract explicit.

**Why more complex is unnecessary:** `catalog` is a contract module, not a full domain with use cases. It defines what the data looks like and provides read/write interfaces. It does not contain ETL logic, pricing logic, or analytics logic.

---

### 5. ingestion

**Exists because:** data ingestion from marketplace APIs is a complex pipeline with marketplace-specific adapters, normalizers, S3 storage, and materialization triggers. It's the largest body of code in the system.

**Owns:**
- Ingestion job lifecycle — scheduling, tracking, progress (CAS-based)
- Read adapters (per marketplace, per data domain) — call marketplace APIs via `MarketplaceClient`
- Normalizers — transform raw marketplace responses into canonical model writes
- S3 raw data storage — immutable archive with provenance metadata
- Canonical upserts — implement `CatalogWriteService` to write normalized data
- ClickHouse materialization trigger — after canonical upsert, trigger async materialization
- Sync state tracking — per-connection, per-domain cursor management

**Dependencies:** `kernel`, `catalog` (write contracts), `marketplace` (HTTP client + credentials).

**Why simpler would not work:** Ingestion involves 19+ adapter/normalizer pairs (10 WB + 9 Ozon), each with marketplace-specific pagination, field mapping, and error handling. This volume of code needs its own module.

**Why more complex is unnecessary:** No need for separate WB-ingestion and Ozon-ingestion modules. The marketplace-specific code is organized as packages within ingestion, not separate modules. Package-level boundaries are sufficient at current scale (2 marketplaces).

---

### 6. analytics

**Exists because:** P&L calculation, returns/inventory analysis, and data quality checks form a coherent analytical domain with their own query patterns and ClickHouse dependencies.

**Owns:**
- P&L calculation — posting, product, account levels
- Returns analysis, inventory analysis
- Data quality checks — residual monitoring, provenance queries
- ClickHouse materializers — dim/fact/mart table loading
- Materialization orchestration — ordered loading (dims → facts → marts)
- **CH degradation handling** — centralized circuit breaker for all CH queries. Exposes `AnalyticsAvailability` interface that other modules check.

**Dependencies:** `kernel`, `catalog` (read contracts), `marketplace` (for connection context).

**Key architectural change:** ClickHouse degradation is handled entirely within this module. Other modules that need CH-enriched data (e.g., `operations` for grid enrichment, `pricing` for sales velocity signals) request it through `analytics` read interfaces, which internally handle CH availability.

**Why simpler would not work:** P&L formulas, materialization orchestration, and data quality checks are complex enough to warrant isolation. Merging with `operations` would create a module with two different concerns (analytics + operational UI backend).

**Why more complex is unnecessary:** Splitting analytics into sub-modules (P&L, quality, materialization) would fragment a cohesive domain. Internal packages are sufficient.

---

### 7. pricing

**Exists because:** algorithmic pricing is the most architecturally significant business flow in the system. It involves policy resolution, strategy calculation, constraint enforcement, guard chains, explainable decisions, and execution mode awareness.

**Owns:**
- Price policies — creation, versioning, assignment to scopes
- Pricing strategies — TargetMargin, PriceCorridor (and future strategies)
- Constraints — min/max price, max change %, min margin
- Guard chain — ordered evaluation of safety guards
- Pricing runs — batched execution, progress tracking
- Price decisions — immutable, with full explainability snapshots
- Manual price locks — lock/unlock per offer
- Blast radius — FULL_AUTO mode safety limit
- Impact preview / simulation

**Key architectural change from legacy:**
1. **Batched runs:** Offers processed in configurable batches with per-batch commits (fixes 03-A2)
2. **No direct execution call:** After decisions, pricing publishes `SchedulePriceAction` commands via outbox. Execution consumes independently (fixes 01-J1)
3. **Automation blocker:** Calls `alerting` module's `AutomationBlockerChecker` interface before run start (clean cross-module contract vs. legacy's tight coupling)

**Dependencies:** `kernel`, `catalog` (read contracts for offer data, current prices, stocks), `alerting` (automation blocker interface), `analytics` (pricing signals from CH — sales velocity, margin trends — via analytics read interface).

**Why simpler would not work:** The pricing pipeline (policy → strategy → constraints → guards → decisions → batch commit → command publish) is genuinely complex. Merging with execution would create a monolithic pricing-execution module that is harder to reason about and test.

**Why more complex is unnecessary:** No need for separate modules for strategies, guards, or policies. They are internal packages within pricing.

---

### 8. execution

**Exists because:** price action execution has its own lifecycle (state machine), reliability requirements (at-least-once, reconciliation), and marketplace I/O patterns that are distinct from pricing decisions.

**Owns:**
- Price action state machine — full lifecycle with CAS transitions
- Execution attempts — per-action attempt tracking
- Write adapters (per marketplace) — call marketplace APIs via `MarketplaceClient` to set prices
- Reconciliation — read-after-write verification (mandatory)
- Retry — configurable retry with backoff
- Supersede policy — conflict detection when new action targets same offer
- Expiration — stuck/abandoned action detection
- Error classification — determines if failure is retriable/non-retriable/uncertain

**Key architectural changes from legacy:**
1. **Read-after-write verification:** After successful write, schedules a delayed read-back to confirm marketplace applied the price. This is the default, not an optional separate job (fixes 03-A3).
2. **No inline blocking poll:** WB async write is handled by: submit task → immediately move to RECONCILIATION_PENDING → reconciliation check polls for task completion and then verifies actual price (fixes 03-E2).
3. **Consumes commands:** Receives `SchedulePriceAction` from outbox, does not expose a callable `createAction()` service.

**Dependencies:** `kernel`, `catalog` (read contracts — current price for reconciliation comparison), `marketplace` (HTTP client for write + read-after-write).

**Why simpler would not work:** The action state machine (10+ states), reconciliation, retry logic, and marketplace-specific write protocols require dedicated focus. Merging with pricing would create a 1000+ line monolithic domain.

**Why more complex is unnecessary:** No need for separate reconciliation module. Reconciliation is an integral part of the execution flow.

---

### 9. promotions

**Exists because:** promotional campaign management has its own policies, evaluation logic, decision types, and marketplace APIs that are distinct from pricing.

**Owns:**
- Promo policies — auto-participate/decline rules, configuration
- Promo evaluation runs — batched evaluation per connection
- Promo decisions — PARTICIPATE, DECLINE, DEACTIVATE, PENDING_REVIEW
- Promo actions — participation command execution on marketplace
- Promo write adapters — via `MarketplaceClient` (currently Ozon only, WB when available)

**Dependencies:** `kernel`, `catalog` (read contracts — promo campaigns, promo products, offers), `marketplace` (HTTP client for promo write).

**Why simpler would not work:** Promo evaluation has its own policy model, decision types, and marketplace API interactions. Merging with pricing would confuse two related but distinct decision engines.

**Why more complex is unnecessary:** Promo lifecycle is simpler than pricing (no guard chain, simpler state machine). One module is sufficient.

---

### 10. operations

**Exists because:** the operational grid and related features (views, queues, journals, mismatches) are a read-model layer that aggregates data from multiple domain modules. It's the primary UI backend for day-to-day seller operations.

**Owns:**
- Grid — unified product/offer view with filters, sorts, pagination (PG + optional CH enrichment)
- Offer detail — aggregated view of one offer across all domains
- Search — product/offer search
- Saved views — user-configured filter presets
- Working queues — prioritized review lists with auto-population
- Mismatch monitor — detect discrepancies between expected and actual marketplace state
- Price/promo journals — historical timeline of price and promo changes

**Dependencies:** `kernel`, `catalog` (read contracts), `pricing` (read contracts — policies, decisions, locks), `execution` (read contracts — actions, attempts), `promotions` (read contracts — campaigns, decisions), `analytics` (CH-enriched data for grid columns), `alerting` (mismatch → alert triggering).

**Key architectural change:** `operations` explicitly depends on read contracts from other modules. It does not import entities or persistence classes from other modules. This prevents the legacy problem where `GridController` imported `SavedViewEntity` from persistence (03-F2).

**Why simpler would not work:** The grid alone requires joining data from catalog, pricing, execution, promotions, and analytics. This cross-module aggregation needs a dedicated module that understands how to compose these views.

**Why more complex is unnecessary:** All operation sub-features (grid, views, queues, journals, mismatches) share the same data sources and belong to the same user context. Splitting them would fragment a cohesive operational domain.

---

### 11. alerting

**Exists because:** audit logging, alert rules, alert events, notifications, and the automation blocker form a safety and observability domain that is consumed by multiple modules.

**Owns:**
- Audit log — structured recording of all business actions
- Alert rules — configurable checks (stale data, missing sync, anomalies, spikes, mismatches)
- Alert events — triggered instances with severity, details, resolution status
- Alert checkers — scheduled evaluation of alert rules
- Notifications — user-facing messages, fan-out by role
- Automation blocker — `AutomationBlockerChecker` interface implementation

**Provides interfaces:**
- `AutomationBlockerChecker` — consumed by `pricing` to determine if automated runs are safe
- `AuditLogger` — consumed by all modules to record audit events

**Dependencies:** `kernel`, `catalog` (read contracts — for data freshness checks).

**Why simpler would not work:** Audit, alerting, and automation blocking are genuinely interconnected (alerts can block automation; audit produces events that trigger alerts). Separating them would create artificial boundaries.

**Why more complex is unnecessary:** One module with clear internal packages (audit, alerting, notifications) is sufficient.

---

### 12. app

**Exists because:** every Spring Boot application needs a composition root that wires modules together, runs database migrations, configures security, and hosts long-running infrastructure (outbox poller, message consumers, schedulers).

**Owns:**
- Spring Boot main class
- Liquibase migrations
- Security configuration (Keycloak, filters, CORS)
- Outbox poller — reads pending outbox entries, publishes to message queue
- Message consumers — thin delegators that deserialize messages and call domain module use cases
- WebSocket configuration
- Global error handler
- Scheduler infrastructure (ShedLock, thread pool configuration)
- `WorkspaceContext` implementation (request-scoped bean)

**Key rule:** `app` is pure wiring. If a consumer makes a business decision (retry classification, error handling), it delegates to a domain service in the owning module (fixing legacy 03-A6).

---

## C. Allowed Dependencies

```
kernel ← (no deps)

marketplace ← kernel

iam ← kernel

catalog ← kernel

ingestion ← kernel, catalog, marketplace

analytics ← kernel, catalog, marketplace

pricing ← kernel, catalog, alerting, analytics

execution ← kernel, catalog, marketplace

promotions ← kernel, catalog, marketplace

operations ← kernel, catalog, pricing, execution, promotions, analytics, alerting

alerting ← kernel, catalog

app ← ALL modules
```

### Dependency graph (simplified, arrows = "depends on")

```
                    kernel
                   ╱  │  ╲
                  ╱   │   ╲
          marketplace iam  catalog ← ─ ─ ─ ─ ─ ─ ─ ┐
             │              │ │ │                     │
             │    ┌─────────┘ │ └──────────┐         │
             │    │           │            │         │
          ingestion      analytics     alerting      │
             │                │            │         │
             │                │            │         │
             │         ┌──────┴────┐       │         │
             │         │           │       │         │
          pricing ─────┤      execution    │         │
             │         │           │       │         │
             │         │           │       │         │
         promotions    │           │       │         │
             │         │           │       │         │
             └────┬────┴───────────┴───────┘         │
                  │                                   │
              operations ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
                  │
                 app (depends on everything)
```

---

## D. Forbidden Dependencies

| From | To | Why Forbidden |
|------|----|---------------|
| `kernel` | Any domain module | Foundation must not know about business domains |
| `marketplace` | Any domain module except `kernel` | Infrastructure must not know about business logic |
| `iam` | `pricing`, `execution`, `promotions`, etc. | IAM is self-contained, must not depend on business features |
| `catalog` | `ingestion`, `pricing`, `analytics`, etc. | Catalog defines contracts; it must not know who implements or consumes them |
| `pricing` | `execution` | **Critical.** This was the legacy tight coupling (01-J1). Communication is via outbox commands only. |
| `execution` | `pricing` | No reverse dependency either. Execution reads catalog, not pricing decisions. |
| `alerting` | `pricing`, `execution`, `promotions` | Alerting provides interfaces for others to consume, does not depend on them |
| `analytics` | `pricing`, `execution`, `promotions` | Analytics reads catalog and CH. Does not depend on domain-specific modules. |
| `ingestion` | `pricing`, `execution`, `promotions`, `operations` | Ingestion writes to catalog. Does not know about downstream consumers. |
| Any module | `app` | Composition root is the leaf. No module depends on the application shell. |

### How forbidden dependencies are enforced

1. **Maven module boundaries** — forbidden dependencies simply do not exist in `pom.xml`.
2. **ArchUnit tests** — automated tests that verify layer constraints and forbidden imports on every build.
3. **Code review** — dependency additions require justification against this document.

---

## E. Integration Boundaries

### Module ↔ Module integration patterns

| Pattern | When Used | Example |
|---------|-----------|---------|
| **Direct method call** (via interface) | Synchronous read contract | `pricing` calls `catalog.findOffersByConnection()` |
| **Outbox command** | Critical async command that must survive crashes | `pricing` publishes `SchedulePriceAction` → `execution` consumes |
| **Domain event** (Spring ApplicationEvent) | Non-critical in-process side effect | `iam` publishes `MemberRoleChanged` → `alerting` logs audit entry |
| **Shared interface** | Cross-module capability contract | `alerting` provides `AutomationBlockerChecker` → `pricing` calls it |

### Module ↔ External system integration

| External System | Module That Owns Integration | Pattern |
|----------------|------------------------------|---------|
| Wildberries API | `marketplace` (HTTP) + `ingestion` (read adapters) + `execution` (write adapters) | `marketplace` provides `MarketplaceClient`. Adapters in domain modules call it. |
| Ozon Seller API | Same as WB | Same pattern |
| PostgreSQL | Each module owns its own tables | JDBC repositories within each module |
| ClickHouse | `analytics` (materializers + query) + `ingestion` (materialization trigger) | Analytics owns CH schema. Ingestion triggers materialization. |
| S3/MinIO | `ingestion` | Direct S3 client in ingestion module |
| Vault | `marketplace` | Vault client in marketplace module |
| Redis | `marketplace` (rate limiting) | Redis client in marketplace module |
| RabbitMQ | `app` (outbox poller + consumers) | App wires outbox → RabbitMQ. Consumers delegate to domain modules. |
| Keycloak | `app` (security config) | OAuth2 resource server configuration |

---

## F. Domain vs Application vs Infrastructure Separation

Each domain module follows this internal structure:

```
<module>/
├── api/              ← REST controllers + request/response DTOs + mappers
├── domain/           ← Business logic, domain services, value objects, interfaces
│   ├── model/        ← Domain classes (not JPA entities)
│   ├── service/      ← Domain services with business rules
│   ├── port/         ← Interfaces for outbound dependencies (repositories, external services)
│   └── event/        ← Domain events
├── infrastructure/   ← Implementations of domain ports
│   ├── persistence/  ← JDBC repositories implementing domain port interfaces
│   ├── adapter/      ← Marketplace-specific adapters (in ingestion, execution, promotions)
│   └── config/       ← Module-level Spring configuration
└── scheduling/       ← @Scheduled jobs (thin: try-catch + delegate to domain service)
```

### Layer rules

| Layer | May Depend On | May NOT Depend On |
|-------|---------------|-------------------|
| `api/` | `domain/` | `infrastructure/` |
| `domain/` | Only `kernel`, other module domain contracts | `api/`, `infrastructure/`, Spring annotations (except @Service, @Transactional) |
| `infrastructure/` | `domain/` (implements its interfaces) | `api/` |
| `scheduling/` | `domain/` | `api/`, `infrastructure/` (except for Spring scheduling annotations) |

**Key differences from legacy:**
- `domain/port/` makes outbound dependency interfaces explicit (legacy mixed domain and infrastructure)
- No JPA entities — `infrastructure/persistence/` contains JDBC implementations only
- `infrastructure/adapter/` replaces legacy's top-level `adapter/` package with clearer separation from domain

---

## G. Transaction Ownership by Module

| Module | Transaction Pattern | Scope |
|--------|-------------------|-------|
| `iam` | Per-operation `@Transactional` | Single entity mutations (create workspace, add member, accept invitation) |
| `catalog` | Batch upsert transactions | Per-batch during ingestion. Read operations are `readOnly`. |
| `ingestion` | Per-source-batch | Each event source processes in bounded batches. Job status via CAS (no transaction). |
| `analytics` | Per-materializer | Each CH materializer runs independently. PG reads are `readOnly`. |
| `pricing` | Per-batch within a run | Configurable batch size (e.g., 500 offers). Run progress tracked outside transaction. |
| `execution` | CAS per state transition | No long transactions. Each state change is a single UPDATE with WHERE condition. |
| `promotions` | Per-batch within evaluation | Same pattern as pricing. |
| `operations` | Read-only | No write transactions. All data comes from other modules' read contracts. Exception: saved views and queue management — per-operation. |
| `alerting` | Per-event | Each audit entry, alert event, or notification is persisted in its own transaction. |
| `marketplace` | None (stateless) | No transactions. Credential cache refresh is atomic read from Vault. |
| `app` | Outbox poller batch | Reads pending outbox entries in batch, publishes, marks delivered. |

---

## H. Rules That Prevent Architectural Erosion

### H1. No shared persistence classes

Modules do not share JPA entities, Row classes, or repository implementations. If module A needs to read module B's data, it calls module B's domain-level read interface. The only exception is `catalog`, which explicitly provides shared read contracts.

### H2. No transitive domain dependencies

If `operations` depends on `pricing`, it depends on `pricing`'s read contract interface, not on `pricing`'s internal domain model. If `pricing` refactors its internal classes, `operations` is not affected.

### H3. ArchUnit boundary tests

Automated tests on every build verify:
- No forbidden imports between modules
- `domain/` does not import from `infrastructure/`
- `api/` does not import from `infrastructure/`
- Controllers do not import repository classes
- No cross-module entity/Row class imports

### H4. Outbox for cross-module commands

If module A needs to trigger a side effect in module B that must be reliable (survive crashes), it must go through outbox. Direct method calls across module boundaries are allowed only for synchronous read queries.

### H5. One credential resolver

The `marketplace` module provides the single `CredentialService`. No other module may read from Vault directly or implement its own credential resolution. This is enforced by Maven dependency: only `marketplace` has Vault client dependency.

### H6. New module requires justification

Adding a new module requires documenting:
1. What concrete problem it solves that existing modules cannot
2. Its dependencies (must not violate the dependency graph)
3. Its transaction ownership
4. Its API surface (what it exposes to other modules)

### H7. Catalog contract changes require consumer review

Any change to `catalog` read or write interfaces requires reviewing all consuming modules for compatibility. This is partially enforced by compile-time breakage and fully enforced by code review.
