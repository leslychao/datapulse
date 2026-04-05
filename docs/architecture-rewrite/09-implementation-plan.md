# 09 Implementation Plan

## A. Implementation Order

The rewrite is organized into **8 ordered steps**. Each step produces a working, testable increment. Steps are ordered by dependency: foundation first, then features that depend on it.

```
Step 1: Foundation (kernel + app skeleton + DB migrations)
    │
Step 2: IAM + Marketplace
    │
Step 3: Catalog + Ingestion
    │
Step 4: Analytics
    │
Step 5: Alerting
    │
Step 6: Pricing
    │
Step 7: Execution
    │
Step 8: Promotions + Operations
```

**Rationale for order:**
- Steps 1-2 provide the infrastructure and tenant model that everything else needs
- Step 3 provides the canonical data that pricing, analytics, operations, and promotions all read
- Step 4 provides analytical data that pricing needs for signals and operations needs for grid enrichment
- Step 5 provides automation blocker that pricing depends on
- Steps 6-7 are the core business flow (pricing → execution) — pricing before execution because execution consumes pricing's output
- Step 8 completes the feature set — promotions and operations are least dependent on architectural correctness (they build on top of everything else)

---

## B. Foundation Work

### Step 1: Foundation

**Goal:** Establish project structure, shared kernel, database schema migrations, CI, and a running application that starts and serves health checks.

**Inputs:**
- 05-target-architecture.md (module structure)
- 06-module-boundaries-and-dependencies.md (dependency rules)
- Legacy Liquibase migrations (existing schema as baseline)

**Artifacts:**

| Artifact | Description |
|----------|-------------|
| Maven multi-module project | 12 modules with correct dependency graph, no JPA dependency |
| `kernel` module | MessageCodes, base exceptions, MarketplaceType, Currency, WorkspaceRole, WorkspaceContext interface |
| `app` module skeleton | Spring Boot main class, security config (Keycloak), global error handler, health endpoint |
| Liquibase baseline | `db.changelog-master.yaml` that applies existing production schema (baseline) + new additive migrations |
| CI pipeline | Build, test, schema migration check |
| ArchUnit tests | Boundary checks for all 12 modules (forbidden dependencies from 06-D) |
| `application.yml` | Cleaned configuration structure — no legacy properties, proper module-prefixed keys |

**Dependencies:** None (this is the root)

**Done criteria:**
- [ ] `mvn clean verify` passes with all 12 empty modules
- [ ] Application starts with test PostgreSQL (Testcontainers)
- [ ] ArchUnit tests enforce all forbidden dependencies from 06-D
- [ ] Health endpoint returns 200
- [ ] Liquibase applies baseline + first additive migration

**Estimated effort:** 2-3 days

---

## C. First Rewrite Slice

### Step 2: IAM + Marketplace

**Goal:** Users can authenticate, manage workspaces and members, create marketplace connections with unified credential management.

**Inputs:**
- Legacy `datapulse-tenancy-iam` (business logic extraction)
- Legacy `datapulse-integration` (credential + rate limit logic extraction)
- 07-domain-model-and-use-cases.md (IAM use cases)

**Artifacts:**

| Artifact | Description |
|----------|-------------|
| `iam` module | Workspace CRUD, member management, invitations, role checks, connection CRUD |
| `marketplace` module | CredentialService (single resolver, canonical key schema), MarketplaceHttpClient, RateLimiter (token bucket + per-product), health probes, call logging |
| JDBC repositories | All persistence via NamedParameterJdbcTemplate, no JPA |
| REST controllers | Workspace, member, invitation, connection endpoints |
| Integration tests | Workspace isolation, role checks, credential resolution, rate limiting |

**Key decisions in this step:**
- Canonical credential key schema finalized (resolves 03-A1)
- Connection entity placement confirmed (iam module)
- Access checking approach standardized

**Dependencies:** Step 1 (foundation)

**Done criteria:**
- [ ] Can create workspace, invite member, accept invitation via REST API
- [ ] Can create marketplace connection with credentials stored in Vault
- [ ] CredentialService resolves credentials with canonical key names
- [ ] Rate limiter works with Redis (+ graceful in-memory fallback)
- [ ] Health probe checks marketplace API reachability
- [ ] Workspace isolation tests pass
- [ ] No JPA imports anywhere

**Estimated effort:** 5-7 days

---

## D. Next Slices

### Step 3: Catalog + Ingestion

**Goal:** ETL pipeline ingests marketplace data into canonical model. Catalog module provides read/write contracts. S3 raw data archival works.

**Inputs:**
- Legacy `datapulse-etl` (all 19 event sources, normalizers, upsert repos)
- 07-domain-model-and-use-cases.md (catalog entities, ingestion use cases)
- `docs/provider-api-specs/` (API contracts)

**Artifacts:**

| Artifact | Description |
|----------|-------------|
| `catalog` module | Domain classes for all canonical entities. Read interfaces (CatalogReadService). Write interfaces (CatalogWriteService). JDBC read repositories. |
| `ingestion` module | Job execution lifecycle (CAS). All read adapters (10 WB + 9 Ozon). Normalizers. CatalogWriteService implementation (JDBC upsert repos). S3 raw storage. Sync state tracking. ClickHouse materialization trigger. |
| Outbox integration | ETL_SYNC_EXECUTE command via outbox → consumer in app |
| Integration tests | Full ingest cycle: API mock → S3 → normalize → canonical upsert → verify catalog read |

**Key decisions in this step:**
- Catalog read interface design (what queries are supported — affects all consumers)
- Batch size for canonical upserts
- S3 path scheme for raw data
- CH materialization trigger mechanism (event or direct call)

**Dependencies:** Step 2 (marketplace module for HTTP client + credentials)

**Done criteria:**
- [ ] Full ETL cycle for WB products, prices, stocks, orders, sales, returns, finance
- [ ] Full ETL cycle for Ozon products, prices, stocks, orders, sales, returns, finance
- [ ] Raw data stored in S3 with provenance metadata
- [ ] Canonical entities readable through CatalogReadService
- [ ] Upserts are idempotent (re-ingest same data = same result)
- [ ] Job tracking with CAS state transitions
- [ ] Batch upserts with bounded transaction size
- [ ] ClickHouse materialization triggered after canonical upsert

**Estimated effort:** 10-14 days (largest step — 19 adapter/normalizer pairs)

---

### Step 4: Analytics

**Goal:** P&L calculation works. ClickHouse materialization pipeline produces star schema. CH degradation is centralized.

**Inputs:**
- Legacy `datapulse-analytics-pnl` (P&L queries, materializers, data quality)
- 07-domain-model-and-use-cases.md (analytics use cases)

**Artifacts:**

| Artifact | Description |
|----------|-------------|
| `analytics` module | PnlCalculator, materializers (dims → facts → marts), data quality checkers, returns/inventory analysis |
| CH degradation | Centralized AnalyticsAvailability interface. Circuit breaker wrapping all CH queries. |
| JDBC repositories | PG-based P&L queries + CH-based analytical queries |
| REST controllers | P&L, returns, inventory, data quality endpoints |
| Integration tests | P&L golden-file tests. Materialization order verification. CH degradation fallback. |

**Dependencies:** Step 3 (catalog read contracts + CH materialization trigger)

**Done criteria:**
- [ ] Materialization pipeline: dims → facts → marts in correct order
- [ ] P&L calculation at posting, product, account levels
- [ ] COGS matching via SCD2 cost profiles
- [ ] CH degradation: system operates without CH (degraded analytics, no crash)
- [ ] Data quality checks detect residuals and anomalies
- [ ] Golden-file P&L tests pass against known data

**Estimated effort:** 5-7 days

---

### Step 5: Alerting

**Goal:** Audit logging, alert rules, alert events, notifications, and automation blocker work.

**Inputs:**
- Legacy `datapulse-audit-alerting` (audit, alerts, notifications, automation blocker)
- 07-domain-model-and-use-cases.md (alerting use cases)

**Artifacts:**

| Artifact | Description |
|----------|-------------|
| `alerting` module | AuditLogger interface + implementation. AlertEvaluator (rule checking). AlertEvent lifecycle. NotificationFanout. AutomationBlockerChecker. |
| AuditLogger integration | All previous modules (iam, ingestion) emit audit events via AuditLogger interface |
| Scheduled jobs | Alert rule evaluation (ShedLock-protected) |
| REST controllers | Alert rules, alert events, notifications, audit log query endpoints |
| Integration tests | Alert triggering, automation blocker, notification fanout |

**Dependencies:** Step 3 (catalog for data freshness checks), Step 1 (kernel for event types)

**Done criteria:**
- [ ] Audit events recorded for all IAM and ingestion operations
- [ ] Alert rules can be configured and evaluated
- [ ] Stale data alert correctly blocks automation (via AutomationBlockerChecker)
- [ ] Notifications distributed to users by role
- [ ] Alert event lifecycle works (ACTIVE → ACKNOWLEDGED → RESOLVED)

**Estimated effort:** 4-5 days

---

### Step 6: Pricing

**Goal:** Full pricing pipeline works — policies, strategies, constraints, guards, batched runs, decisions with explainability, manual locks. Price change commands published via outbox.

**Inputs:**
- Legacy `datapulse-pricing` (policies, strategies, constraints, guards, run service)
- 07-domain-model-and-use-cases.md (pricing use cases, domain services, invariants)
- 04-rewrite-invariants-and-constraints.md (pricing invariants P1-P6)

**Artifacts:**

| Artifact | Description |
|----------|-------------|
| `pricing` module | PricePolicy CRUD. PolicyResolver. PricingEngine (strategy → constraints → guards → decision). PricingRunOrchestrator (batched). ManualLockService. BlastRadiusChecker. |
| Strategies | TargetMarginStrategy, PriceCorridorStrategy |
| Guards | ManualLockGuard, StaleDataGuard, StockOutGuard, PromoGuard, FrequencyGuard, VolatilityGuard, MarginGuard |
| Outbox integration | SchedulePriceAction command published per CHANGE decision |
| Pricing signals | PricingSignalCollector reads from catalog + analytics (with CH degradation handling) |
| REST controllers | Policy CRUD, pricing run trigger, decision query, manual lock, impact preview |
| Integration tests | Full pricing pipeline. Guard chain ordering. Blast radius. Automation blocker. Decision immutability. Policy versioning. |

**Key validation:** Golden-file tests — capture legacy pricing decisions with their input snapshots, replay through new PricingEngine, verify identical output.

**Dependencies:** Step 3 (catalog for offer data), Step 4 (analytics for signals), Step 5 (alerting for automation blocker)

**Done criteria:**
- [ ] Create, update, assign pricing policies
- [ ] Pricing run processes offers in configurable batches
- [ ] Each batch committed independently (no single-transaction run)
- [ ] Decisions contain immutable policy/signal/constraint/guard snapshots
- [ ] Guard chain evaluates in order, short-circuits on first block
- [ ] ManualLock, BlastRadius, AutomationBlocker all work
- [ ] SchedulePriceAction commands published via outbox for CHANGE decisions
- [ ] Golden-file pricing tests pass (same inputs → same decisions as legacy)
- [ ] All 6 pricing invariants (04-P1 through P6) covered by tests

**Estimated effort:** 7-10 days

---

### Step 7: Execution

**Goal:** Price actions are consumed from outbox, executed on marketplace APIs, reconciled with read-after-write verification. Full state machine with CAS transitions.

**Inputs:**
- Legacy `datapulse-execution` (action service, CAS repository, write adapters, reconciliation)
- 07-domain-model-and-use-cases.md (execution use cases, state machine)
- 04-rewrite-invariants-and-constraints.md (execution invariants E1-E4)

**Artifacts:**

| Artifact | Description |
|----------|-------------|
| `execution` module | ActionLifecycleService (CAS transitions). ExecutionOrchestrator. ReconciliationService (with read-after-write). ErrorClassifier. WB and Ozon write adapters. Expiration/stuck detection. |
| Outbox consumers | SchedulePriceAction consumer (creates action). ExecutePriceAction consumer (executes on marketplace). ScheduleReconciliation consumer (triggers verification). |
| Reconciliation | Read current price from marketplace → compare → SUCCEEDED or retry. WB: also checks async task status. |
| REST controllers | Action query, approve/reject/hold/resume, cancel endpoints |
| Integration tests | Full execution cycle with marketplace API mocks. State machine transitions. CAS atomicity. Reconciliation match/mismatch. Retry. Expiration. Supersede. |

**Dependencies:** Step 6 (pricing publishes SchedulePriceAction), Step 2 (marketplace for write adapters + credentials)

**Done criteria:**
- [ ] SchedulePriceAction consumed → action created with correct initial status
- [ ] Approve/reject/hold/resume via CAS
- [ ] Execute: credentials resolved → write adapter called → RECONCILIATION_PENDING
- [ ] Reconciliation: read-after-write verification (new — not in legacy)
- [ ] WB async handling: no inline blocking poll (submit → reconciliation check)
- [ ] Retry with backoff for RETRIABLE errors
- [ ] Expiration for stuck actions
- [ ] Supersede for conflicting actions
- [ ] All 4 execution invariants (04-E1 through E4) covered by tests

**Estimated effort:** 7-10 days

---

### Step 8: Promotions + Operations

**Goal:** Complete feature set. Promo evaluation and participation. Operational grid, views, queues, journals, mismatches.

**Inputs:**
- Legacy `datapulse-promotions`, `datapulse-seller-operations`
- 07-domain-model-and-use-cases.md (promotions + operations use cases)

**Artifacts:**

| Artifact | Description |
|----------|-------------|
| `promotions` module | PromoPolicy CRUD. PromoEvaluator. PromoRunOrchestrator (batched). PromoDecisions. PromoActions (via marketplace module). |
| `operations` module | GridQueryService (PG + CH enrichment). OfferDetail (composite read). SavedViews. WorkingQueues. MismatchDetector. Price/promo journals. |
| Grid | Dynamic SQL query builder with catalog + pricing + execution + promotions joins. CH enrichment via analytics read interface. Graceful degradation. |
| REST controllers | Grid, offer detail, search, views, queues, mismatch, journals, promo endpoints |
| Integration tests | Grid with full data. Mismatch detection. Queue auto-population. Promo evaluation cycle. |

**Dependencies:** All previous steps (operations reads from everything)

**Done criteria:**
- [ ] Promo evaluation with batched processing
- [ ] Promo actions execute on Ozon marketplace API
- [ ] Grid query returns unified view across all domains
- [ ] Grid degrades gracefully when CH unavailable
- [ ] Saved views persist and apply correctly
- [ ] Working queues auto-populate based on criteria
- [ ] Mismatches detected and alert events triggered
- [ ] Price/promo journals show historical changes

**Estimated effort:** 10-14 days

---

## E. Contracts Stabilization

### REST API contracts

Each step defines its REST endpoints. After Step 2, the API shape is documented in OpenAPI spec. Subsequent steps add endpoints without changing existing ones.

**API versioning:** No versioning for v1 (this is a greenfield rewrite). If incompatible changes are needed in the future, introduce `/v2/` prefix.

### Module read contracts

`catalog` read interfaces are defined in Step 3 and consumed by Steps 4-8. Any change to catalog read interfaces during Steps 4-8 requires reviewing all existing consumers.

**Process:** Catalog interface changes require a PR comment tagging the step that introduced the consumer. ArchUnit + compile-time breakage provide automated protection.

### Database schema contracts

Liquibase migrations are additive during the rewrite. Each step may add tables, columns, and indexes. No step may remove or rename existing production columns until after cutover stabilization.

---

## F. Tests as Behavioral Safety Net

### Test strategy by type

| Test Type | What It Covers | When Run | Step |
|-----------|---------------|----------|------|
| **Unit tests** | Domain services, value objects, strategies, guards | Every build (CI) | All steps |
| **Integration tests** | JDBC repos, API endpoints, outbox flow, CAS transitions | Every build (CI) | All steps |
| **Golden-file tests** | Pricing engine produces expected decisions for known inputs | Step 6+ | Steps 6-7 |
| **Schema migration tests** | Liquibase applies cleanly to prod-size database | Pre-cutover | Step 1 ongoing |
| **Acceptance tests** | Full user scenarios (create workspace → ingest → price → execute) | Pre-cutover | Step 7+ |
| **ArchUnit tests** | Module boundaries, layer rules, forbidden dependencies | Every build | Step 1 |

### Golden-file test process

1. **Capture:** Export N (e.g., 1000) production price decisions with their full input snapshots
2. **Transform:** Convert to test fixtures (JSON files in `src/test/resources/fixtures/pricing/`)
3. **Replay:** Feed each input snapshot into new PricingEngine
4. **Compare:** Assert decision type, target price, constraint results, guard results match
5. **Document differences:** Any difference is either an intentional fix or a regression

### ArchUnit test examples

```
// No module imports persistence classes from another module
noClasses().that().resideInAPackage("..pricing..")
    .should().dependOnClassesThat().resideInAPackage("..catalog.infrastructure..")

// Controllers never import repositories
noClasses().that().areAnnotatedWith(RestController.class)
    .should().dependOnClassesThat().areAnnotatedWith(Repository.class)

// Domain layer does not depend on infrastructure
noClasses().that().resideInAPackage("..domain..")
    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
```

---

## G. Deletion and Cleanup Plan

### During rewrite (parallel development)

- Legacy code remains untouched in its current branch/location
- New code is built in separate modules (or separate directory if same repo)
- No deletion of legacy code until cutover

### After cutover (stabilization period: 2 weeks)

- Monitor new system in production
- Address any issues found
- Keep legacy code available for reference/rollback

### After stabilization (cleanup)

| Deletion Target | When | Prerequisite |
|----------------|------|--------------|
| Legacy Maven modules (all 12) | After stabilization | New system confirmed stable for 2 weeks |
| JPA entity classes | With module deletion | Automatic |
| Old Liquibase changelog files | Never delete | Keep in `db/changelog/archive/` for history |
| Unused database columns (old schema) | 4 weeks after cutover | Verified no read/write references |
| Unused database tables | 4 weeks after cutover | Verified no references |
| Old Vault key names | 4 weeks after cutover | Verified new key schema is sole consumer |
| `.cursor/rules/` legacy rules | After cleanup | Update to reflect new architecture |
| `docs/modules/` legacy docs | After cleanup | Replace with updated architecture docs |

---

## H. Done Criteria Per Step

### Universal criteria (every step must satisfy)

- [ ] All new code compiles without warnings
- [ ] All tests (unit + integration) pass
- [ ] ArchUnit boundary tests pass
- [ ] No JPA imports in any module
- [ ] No `@SuppressWarnings("unchecked")` without documented justification
- [ ] Liquibase migrations apply cleanly on fresh DB and on existing staging DB
- [ ] Module follows internal structure from 06-F (api / domain / infrastructure / scheduling)
- [ ] All mutating operations emit audit events
- [ ] Workspace isolation enforced (WHERE clause + access check) on all data access

### Step-specific criteria

Listed in each step's section above (C, D).

---

## I. Recommended Starting Point

**Start with Step 1 (Foundation) + Step 2 (IAM + Marketplace).**

Why:
1. **Foundation** (Step 1) takes 2-3 days and produces the project skeleton, CI, and ArchUnit boundary enforcement. Everything else depends on it.
2. **IAM + Marketplace** (Step 2) resolves the highest-severity legacy defect (03-A1: credential key inconsistency) and establishes the tenant model that every other module depends on.
3. After Steps 1-2, the team has a running application with auth, workspace management, and unified marketplace connectivity — a solid base for all subsequent features.
4. Steps 1-2 combined take ~7-10 days and produce immediate, tangible architectural improvement.

**The most critical validation milestone** is after Step 7 (Execution), when the full pricing-to-execution flow works end-to-end. This is the moment when the new system can be tested against production data for correctness.

**Total estimated effort for all 8 steps:** 50-70 developer-days (10-14 weeks for one developer, 5-7 weeks for two).
