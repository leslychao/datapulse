# 03 Legacy Problems and Noise

This document separates real architectural defects from accidental complexity, historical layering, and stylistic issues. For each problem, we identify location, impact, severity, and whether it must be actively avoided in a rewrite.

---

## A. Real Architectural Defects

These are problems that create runtime risk, data integrity issues, or make the system fundamentally harder to evolve.

### A1. Credential Key Naming Inconsistency (CRITICAL)

**Location:** Three different credential resolvers across three modules.

| Module | Resolver | WB Key | Ozon Client ID Key | Ozon API Key |
|--------|----------|--------|--------------------|--------------| 
| integration | `CredentialMapper` | `apiToken` | `clientId` | `apiKey` |
| execution | `ExecutionCredentialResolver` | `token` | `client_id` | `api_key` |
| promotions | `PromoCredentialResolver` | — | `client-id` | `api-key` |

**Problem:** Three different modules read from the same Vault path but expect different key names for the same credential. This works only if the Vault secret contains ALL key variants simultaneously, or if each module writes/reads from different paths. If any path is shared, credential resolution will silently fail at runtime.

**Why it matters:** Silent credential resolution failure means write operations (price changes, promo participation) could fail with cryptic 401 errors from marketplace APIs. Debugging requires tracing through three different credential resolvers.

**Severity:** Must-fix now. This is a **runtime correctness bug risk**, not a style issue.

**Rewrite verdict:** Must not be carried into rewrite. Single canonical credential schema with one resolver.

---

### A2. Single-Transaction Pricing Run (HIGH)

**Location:** `PricingRunService.executeRun()` — entire method annotated `@Transactional`.

**Problem:** The full pricing pipeline — load offers, resolve policies, collect signals (including ClickHouse queries), calculate strategies, apply constraints, run guards, save decisions, schedule actions — runs in one PostgreSQL transaction. For connections with thousands of offers, this creates:
- Long-running transaction holding row locks
- Risk of transaction timeout
- All-or-nothing semantics: if the last offer fails, all decisions are rolled back
- ClickHouse queries (which can be slow) execute inside a PG transaction, extending lock hold time

**Why it matters:** As catalog sizes grow, this will become a bottleneck. A single slow ClickHouse query or a transient failure on the last offer wipes out the entire run's work.

**Severity:** Important. Not a bug today (system works for current catalog sizes), but a scalability ceiling.

**Rewrite verdict:** Must not carry this pattern. Pricing runs should process in batches with partial progress tracking.

---

### A3. No Read-After-Write Verification in Price Write Adapters (HIGH)

**Location:** `WbPriceWriteAdapter`, `OzonPriceWriteAdapter`.

**Problem:** After writing a price change to a marketplace API, neither adapter performs a read-back to verify the price was actually applied. The reconciliation check exists as a separate scheduled step, but there's a gap:
- WB adapter polls for task completion status, but doesn't verify the actual price
- Ozon adapter parses the response for errors, but trusts the success response without verification

**Why it matters:** Marketplace APIs can report success but not apply the change (known behavior for Ozon partial failures). The system enters RECONCILIATION_PENDING trusting the write succeeded, then may discover it didn't on the next reconciliation check.

**Severity:** Important. Documented as a known risk in `write-contracts.md`.

**Rewrite verdict:** Must design reconciliation as a first-class concern, not an afterthought. Consider read-after-write as the default, with configurable delay.

---

### A4. Canonical Model as Shared Kernel Without Explicit Contract (MEDIUM-HIGH)

**Location:** `datapulse-etl/persistence/canonical/` — entities used by ETL, pricing, seller-ops, analytics, promotions.

**Problem:** The canonical entities (product, offer, price, stock, order, sale, return, finance) are physically located in `datapulse-etl` but consumed by nearly every other module. There is no explicit interface/contract between ETL (the writer) and consumers (the readers). Changes to canonical schema require updating:
- ETL normalizers and upsert repositories
- Pricing signal collectors
- Analytics materializers
- Seller-ops grid queries
- Promo product queries

**Why it matters:** This creates a hidden coupling web. A seemingly simple schema change (e.g., adding a field to `canonical_price_current`) triggers changes across 5+ modules. There's no compile-time or contract-level protection against breaking consumers.

**Severity:** Important. This is a maintainability and evolution problem.

**Rewrite verdict:** The canonical model should be an explicit shared contract (possibly its own module) with versioned schema and clear producer/consumer interfaces.

---

### A5. Mixed Blocking/Non-Blocking HTTP Client Usage (MEDIUM)

**Location:**
- ETL adapters: reactive (`WebClient` flux, `retryWhen`, backpressure)
- Execution adapters: blocking (`.block()` on `WebClient`)
- Promo adapters: blocking (`.block()` with timeout)

**Problem:** The same `WebClient` infrastructure is used in fundamentally different ways. ETL uses reactive streams for efficient large-data ingestion. Execution and promo adapters call `.block()`, defeating the purpose of reactive programming and tying up threads during API calls.

**Why it matters:** The blocking calls in execution/promo adapters can exhaust thread pools under high concurrency (many simultaneous price changes). The inconsistency also makes the system harder to reason about — developers must remember which adapter style is used where.

**Severity:** Important for scaling execution throughput. Not a correctness issue.

**Rewrite verdict:** Choose one HTTP client model and apply consistently, or explicitly document when each is appropriate.

---

### A6. Outbox Consumers in Composition Root (MEDIUM)

**Location:** `datapulse-api` module — `EtlSyncConsumer`, `PriceActionExecuteConsumer`, and other RabbitMQ consumers.

**Problem:** Outbox event consumers contain error handling, retry logic, and business-relevant routing. They live in `datapulse-api` (the composition root) rather than in their respective domain modules. This means:
- Business logic for error classification and retry decisions is separated from the domain it belongs to
- The composition root has business knowledge it shouldn't need

**Why it matters:** Maintainability — when fixing execution retry logic, you need to look in both `datapulse-execution` (domain) and `datapulse-api` (consumer).

**Severity:** Medium. Not a runtime risk, but an organizational problem.

**Rewrite verdict:** Optional to fix immediately, but consumers should be co-located with their domain logic.

---

## B. Accidental Complexity

These are areas where the solution is more complex than the problem requires.

### B1. Dual Persistence Strategy Without Clear Rules

**Location:** All modules — mix of JPA (`JpaRepository`) and JDBC (`NamedParameterJdbcTemplate`, raw `JdbcTemplate`).

**Problem:** No documented rule for when to use JPA vs JDBC. Current state:
- Tenancy: JPA only
- Integration: JPA for entities, JDBC for call log queries
- ETL canonical: JDBC only (upsert repositories)
- Pricing: JPA for writes, JDBC for reads
- Execution: JPA for writes, JDBC for queries
- Seller-ops: JDBC only
- Analytics: JDBC only

**Why it matters:** Developers must learn two persistence models. Mapping between JPA entities and JDBC rows creates duplicate representations. Some modules have both `*Entity.java` (JPA) and `*Row.java` (JDBC) for the same data.

**Accidental because:** The dual strategy evolved organically — JPA was used initially, JDBC was added for performance-sensitive reads. There was no intentional CQRS design.

**Rewrite verdict:** Choose one persistence approach per responsibility (e.g., JDBC for all reads, JPA for simple writes, or drop JPA entirely). Make the choice explicit.

---

### B2. Rate Limiter Complexity

**Location:** `datapulse-integration/domain/ratelimit/` — `MarketplaceRateLimiter`, `AimdRateController`, `OzonProductRateLimiter`, `RateLimitProperties`.

**Problem:** Three separate rate limiting mechanisms:
1. Redis token bucket per `(connection_id, rate_limit_group)` — global API rate limits
2. AIMD (Additive Increase, Multiplicative Decrease) controller — dynamic adjustment based on 429 responses
3. Redis ZSET sliding window per Ozon product — per-product update frequency limit

Plus in-memory fallback when Redis is unavailable (at 50% capacity).

**Why it matters:** This is a lot of infrastructure for a problem that could potentially be solved more simply. The AIMD controller adds dynamic behavior on top of static limits, making the effective rate hard to predict or debug.

**Accidental because:** Each mechanism was added incrementally as new rate limit constraints were discovered. The overall design was not planned holistically.

**Rewrite verdict:** Simplify if possible. The per-product Ozon limit is a genuine business constraint. The AIMD may be over-engineering for known API limits.

---

### B3. `AimdRateController` Naming Confusion

**Location:** `datapulse-integration/domain/ratelimit/AimdRateController.java`

**Problem:** Named `*Controller` but is a `@Component`, not a `@RestController`. This violates the project's own naming convention where `*Controller` means HTTP endpoint.

**Why it matters:** Confusing for developers. Grep for controllers returns a non-REST class.

**Accidental because:** Name was likely chosen from the rate-limiting algorithm domain ("controller" in control theory), not from Spring conventions.

**Rewrite verdict:** Rename. Trivial fix, but symptomatic of naming inconsistency.

---

## C. Historical Layering

These are abstraction layers that exist because of incremental evolution, not current architectural need.

### C1. Platform Module as Catch-All Infrastructure

**Location:** `datapulse-platform` — contains audit, config, ETL hooks, observability, outbox, persistence, security, storage.

**Problem:** The platform module has become a catch-all for anything that is "shared infrastructure." It contains:
- `BaseEntity` (legitimate shared infrastructure)
- Security context and auth (legitimate)
- Outbox service (could be its own module)
- ETL hooks (`PostIngestMaterializationHook`) — specific to ETL domain
- Audit event (`AuditEvent`) — specific to audit domain
- Storage abstractions — specific to ETL

**Why it matters:** The platform module has grown to include domain-specific concerns disguised as infrastructure. ETL hooks in platform create a dependency from platform back to ETL concepts.

**Severity:** Medium. It works, but blurs module boundaries.

**Rewrite verdict:** Platform should contain only truly cross-cutting infrastructure. Domain-specific hooks should live in their respective modules.

---

### C2. TenancyAuditPublisher vs ApplicationEventPublisher

**Location:** `datapulse-tenancy-iam` uses its own `TenancyAuditPublisher`. Other modules use Spring `ApplicationEventPublisher`.

**Problem:** Two different event publishing mechanisms for the same purpose (audit logging). Tenancy has its own wrapper, while other modules publish `AuditEvent` directly via Spring's publisher.

**Why it matters:** Inconsistency. When reading the code, you must track two different patterns for the same concern.

**Severity:** Low. Both work correctly.

**Rewrite verdict:** Unify event publishing mechanism.

---

### C3. Multiple Domain Event Mechanisms

**Location:** Across all modules.

**Problem:** Three patterns for async side effects:
1. Spring `ApplicationEventPublisher` + `@EventListener` — most modules
2. Spring `ApplicationEventPublisher` + `@TransactionalEventListener` — post-commit effects
3. Outbox + RabbitMQ — reliable delivery for critical commands

The boundaries between "use spring events" and "use outbox" are not formally documented. Critical actions (price execution, ETL sync) use outbox. Less critical actions (notifications, audit) use spring events.

**Why it matters:** A developer adding a new side effect must decide which mechanism to use without clear guidelines.

**Severity:** Low-medium. The current allocation is reasonable but implicit.

**Rewrite verdict:** Document clear rules: outbox for cross-process/critical, spring events for in-process/non-critical. Or unify on one mechanism.

---

## D. Unnecessary Abstractions

### D1. Separate `*ApiService` Classes in Pricing and Promotions

**Location:** `PricingRunApiService` (alongside `PricingRunService`), `PromoEvaluationApiService` (alongside `PromoEvaluationService`).

**Problem:** These `*ApiService` classes are thin wrappers that do little more than create a run entity, save it, and publish an outbox event. They exist to separate "API-facing" operations from "execution" operations, but the separation adds an indirection layer without significant business value.

**Why it matters:** Adds cognitive overhead — "which service do I call?" — for minimal separation benefit.

**Severity:** Low. They work, just add indirection.

**Rewrite verdict:** May not carry into rewrite if run creation and execution can be unified cleanly.

---

### D2. Advertising Stub Adapters

**Location:** `WbAdvertisingFactSource`, `OzonAdvertisingFactSource` in ETL.

**Problem:** These event sources exist in the codebase as stubs — they are registered in the EventSourceRegistry but do not make real HTTP calls to advertising APIs. The infrastructure (classes, registry entries) exists for a feature that isn't implemented.

**Why it matters:** Dead code creates confusion about what the system actually does.

**Severity:** Low. Stubs don't cause runtime issues.

**Rewrite verdict:** Do not carry stubs into rewrite. Implement advertising when the feature is actually needed.

---

## E. Workarounds and Compromise Logic

### E1. ClickHouse Circuit Breaker as Business Logic

**Location:** `GridService`, `OfferService`, `QueueAutoPopulationService` — all contain logic for CH degradation.

**Problem:** Each service that queries ClickHouse implements its own fallback logic for CH unavailability. This is a workaround for ClickHouse's lower reliability compared to PostgreSQL. The grid returns a `X-Sort-Fallback` header when CH-based sorting is unavailable.

**Why it matters:** Business logic (what to show users) is tangled with infrastructure resilience (CH availability). Each service implements fallback differently.

**Severity:** Medium. It's a pragmatic solution, but the scattered implementation makes it fragile.

**Rewrite verdict:** Centralize CH degradation handling. Make it a cross-cutting concern, not per-service logic.

---

### E2. WB Price Write Async Polling

**Location:** `WbPriceWriteAdapter`.

**Problem:** WB price API is asynchronous (upload task → poll for completion). The adapter implements an inline polling loop, sleeping and checking until the task completes. This blocks the thread during the entire poll wait.

**Why it matters:** Thread-blocking poll in a write adapter limits concurrency. If many price actions execute simultaneously, threads can be exhausted.

**Severity:** Medium. Works for current volume, will not scale.

**Rewrite verdict:** Implement async/callback-based polling or separate the poll into a scheduled check.

---

### E3. `PriceActionFilter` in Domain Package but Used by Controller

**Location:** `datapulse-execution/domain/PriceActionFilter.java` — used by `PriceActionController`.

**Problem:** This filter DTO is in `domain/` package but serves as a query parameter binding object for the controller. It should be in `api/` package per project conventions.

**Why it matters:** Minor package structure violation. The domain package shouldn't contain API-specific DTOs.

**Severity:** Low. Style issue.

**Rewrite verdict:** Not architecturally significant. Fix during rewrite naturally.

---

## F. Duplication and Responsibility Leaks

### F1. Controller-Level Entity Mapping

**Location:** `WorkspaceController`, `MemberController`, `InvitationController`, `PriceActionController` — map JPA entities to response DTOs inline in the controller.

**Problem:** Controllers directly access entity fields and construct response DTOs, bypassing the mapper layer. This leaks persistence concerns into the API layer.

**Why it matters:** When entity structure changes, controllers must be updated alongside mappers and repositories.

**Severity:** Low-medium. Not a runtime risk, but a maintenance burden.

**Rewrite verdict:** All mapping should be in dedicated mappers or services, not controllers.

---

### F2. GridController Imports Persistence Entity

**Location:** `GridController` imports `SavedViewEntity` from `persistence/` package.

**Problem:** Direct import of a JPA entity in the controller layer violates the layered architecture rule (api → domain → persistence, never api → persistence).

**Why it matters:** Creates a shortcut dependency that bypasses the domain layer.

**Severity:** Low. Single instance, not systemic.

**Rewrite verdict:** Must not carry this pattern.

---

### F3. Duplicated Workspace Connection Resolution

**Location:** Multiple analytics services (`PnlQueryService`, `ReturnsAnalysisService`, `InventoryAnalysisService`) each inject `WorkspaceConnectionRepository` to resolve connection IDs for the current workspace.

**Problem:** Same "get connections for workspace" logic repeated in 3+ services.

**Why it matters:** Minor DRY violation.

**Severity:** Low.

**Rewrite verdict:** Extract shared workspace context resolution.

---

## G. Stylistic Issues That Are Not Architecture

These items are cosmetic or conventional, not architecturally significant.

| Issue | Location | Note |
|-------|----------|------|
| Some controllers use inline entity mapping, others use MapStruct | Various | Inconsistency, not defect |
| Mix of `record` and `class` for DTOs | Various | Historical — records introduced later |
| Some services have `@Slf4j` on domain services, others don't | Various | Inconsistent but harmless |
| `@Transactional(readOnly = true)` missing on some read-only service methods | Various | Should be there but absence doesn't cause bugs |
| Naming: `*SummaryResponse` vs `*ListResponse` vs `*Response` for list endpoints | Various | Inconsistent naming convention |

**Rewrite verdict:** Do not list as architectural requirements. Define consistent conventions in the new codebase and apply uniformly.

---

## H. What Must Not Be Carried into Rewrite

| Problem | Ref | Reason |
|---------|-----|--------|
| Credential key naming inconsistency | A1 | Runtime bug risk |
| Single-transaction pricing run | A2 | Scalability ceiling |
| Missing read-after-write verification | A3 | Data integrity gap |
| Canonical model without explicit contract | A4 | Hidden coupling web |
| Dual JPA/JDBC without rules | B1 | Accidental complexity |
| Controller-level entity mapping | F1 | Layer violation |
| Stub adapters as shipped code | D2 | Dead code |
| Per-service CH degradation logic | E1 | Scattered resilience |
| Thread-blocking write adapter polling | E2 | Concurrency limit |

---

## I. Confidence / Uncertainties

| Finding | Confidence |
|---------|-----------|
| Credential key naming inconsistency | [CONFIRMED] — read three resolvers in full |
| Single-transaction pricing run | [CONFIRMED] — `@Transactional` on `executeRun()` |
| No read-after-write in write adapters | [CONFIRMED] — read both write adapters in full |
| Canonical model used across 5+ modules | [CONFIRMED] — traced imports and queries |
| Controller entity mapping violations | [CONFIRMED] — read affected controllers |
| Advertising stubs | [HIGH CONFIDENCE] — source files exist but real HTTP calls not found |
| Thread pool exhaustion risk from blocking calls | [UNCERTAIN] — theoretical risk, not measured under load |
| CH circuit breaker inconsistency across services | [HIGH CONFIDENCE] — read GridService, OfferService; other services sampled |
| Completeness of this defect list | [UNCERTAIN] — there may be additional defects not yet discovered in less-explored areas (e.g., analytics materialization pipeline, notification fan-out logic) |
