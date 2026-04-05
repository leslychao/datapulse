# 08 Rewrite Strategy

## A. Rewrite Style

**Controlled big-bang with parallel operation.**

The system is a modular monolith with a single deployment unit and a single database. This makes strangler-fig (gradually routing traffic from old to new) impractical — you can't run two monoliths against the same database schema with different schemas, and the frontend is tightly coupled to the current REST API.

Instead, the strategy is:

1. **Build the new system as a separate codebase** (or a separate set of modules within the same repository)
2. **Migrate database schema** via Liquibase migrations that evolve the current schema toward the target (additive changes first, then cleanup)
3. **Cut over** when the new system passes acceptance criteria on staging with production data
4. **Run legacy and new in parallel on staging** for validation, but only one system is live in production at any time

This is "big-bang" in the sense that there is a single cut-over moment, but "controlled" because:
- The database schema evolves incrementally, not replaced in one shot
- The new system is validated against production data on staging before cutover
- Rollback is possible (old code can run against the incrementally-changed schema)
- The build is sliced into ordered implementation steps with clear done criteria

---

## B. Big-Bang vs Controlled Replacement

### Why not strangler-fig

| Strangler-fig requirement | Our situation | Verdict |
|--------------------------|---------------|---------|
| Old and new systems can run side-by-side | Single monolith, single DB | Not feasible without significant proxy/routing infrastructure |
| Traffic can be gradually redirected | Frontend depends on specific REST API shapes | Would require maintaining two API versions |
| Data consistency between old and new | Shared PG database with evolving schema | Risk of data corruption if both systems write concurrently |
| Independent deployment of old and new | Same deployment unit | Requires splitting the monolith first, which is itself a rewrite |

### Why not pure big-bang

| Pure big-bang risk | Mitigation |
|-------------------|------------|
| Schema migration fails | Incremental schema migration (additive first). Rollback-safe Liquibase changesets. |
| New system has bugs in production | Parallel staging validation with production data snapshot. Comprehensive acceptance tests. |
| Loss of in-flight data (pending actions, running jobs) | Drain queue before cutover. Ensure all pending actions reach terminal state. |
| Frontend incompatibility | Frontend is part of the rewrite — coordinated release. |

### Decision: Controlled big-bang

- Schema evolution is **incremental** (Liquibase additive migrations)
- New system build is **sliced** (see 09-implementation-plan.md)
- Validation is **parallel** (staging with production data)
- Cutover is **single event** (deploy new, decommission old)
- Rollback is **possible** for a limited window (schema migrations are additive/backward-compatible during transition)

---

## C. Preconditions

Before starting the rewrite:

### C1. Production data snapshot for testing

A sanitized snapshot of production PostgreSQL and ClickHouse databases must be available for:
- Schema migration testing
- Data migration verification
- Acceptance testing on realistic data volumes

**Status:** Can be created from staging environment. No blocker.

### C2. Comprehensive API contract documentation

Current REST API contract (all 43 controllers) must be documented:
- Request/response shapes
- Query parameters
- Status codes
- Auth requirements

This serves as the acceptance criteria for the new system's API layer.

**Status:** Partially available from code. Needs extraction (can be automated via OpenAPI/Swagger generation from existing code).

### C3. Marketplace API specifications confirmed

`docs/provider-api-specs/` must be up to date for all endpoints used by the system.

**Status:** Mostly complete. Gaps flagged in 04-B (advertising endpoints).

### C4. Business invariants confirmed by stakeholder

The 16 invariants in 04-A must be explicitly confirmed by the product/tech lead before the rewrite encodes them permanently in new architecture.

**Status:** Documented in 04. Awaiting explicit confirmation.

### C5. Credential key schema decided

The actual Vault secret structure must be inspected (03-A1 open question). The canonical key schema for the new `CredentialService` must be agreed upon before implementation.

**Status:** Blocked until runtime inspection of Vault secrets.

---

## D. Safety Nets

### D1. Acceptance test suite

Before cutover, the new system must pass a comprehensive acceptance test suite covering:

| Category | What's Tested | How |
|----------|---------------|-----|
| API compatibility | All REST endpoints return expected shapes | Integration tests against test database |
| Pricing pipeline | Decisions match expected results for known inputs | Golden-file tests: known offer state → expected decision |
| State machine | All valid state transitions work; invalid transitions are rejected | Unit tests on ActionLifecycleService |
| ETL ingestion | Raw data → canonical upsert produces expected canonical state | Integration tests with fixture API responses |
| Reconciliation | Read-after-write correctly detects match/mismatch | Integration tests with marketplace API mocks |
| P&L calculation | Known inputs produce expected P&L numbers | Golden-file tests against analytics service |
| Workspace isolation | Cross-workspace data access is impossible | Security integration tests |
| Outbox reliability | Commands survive simulated crash | Integration tests with transaction rollback + outbox poller |

### D2. Schema migration rollback

Every Liquibase migration must be:
- **Additive** during the transition period (ADD COLUMN, CREATE TABLE, CREATE INDEX — never DROP during transition)
- **Backward-compatible** (old code can still run against the new schema)
- **Rollback-tested** on staging before production application

Destructive changes (DROP COLUMN, DROP TABLE) happen only **after** the old system is decommissioned and the new system is confirmed stable.

### D3. Data validation checksums

After schema migration:
- Row counts per table must match
- Key business metrics (total offers, total actions, total decisions) must be verifiable
- P&L aggregate for known periods must match pre-migration values

### D4. Feature flags for incremental activation

Critical features can be gated behind feature flags during initial rollout:
- FULL_AUTO pricing mode (most risky — real money at stake)
- Promo auto-participation
- Automated mismatch detection

Flags allow running the new system in "read-only" or "recommendation-only" mode initially.

---

## E. Transition Rules

### E1. Database schema transition

```
Phase 1: Additive migrations
    - Add new columns, tables, indexes alongside existing ones
    - New code reads from new columns, writes to both old and new
    - Old code continues to read from old columns

Phase 2: Data backfill
    - Populate new columns/tables from existing data
    - Verify data integrity with checksums

Phase 3: Switch to new code
    - Deploy new system
    - New code reads/writes new schema exclusively
    - Old columns still exist but are unused

Phase 4: Cleanup (after stabilization)
    - Drop old columns, old tables, old indexes
    - Remove backward-compatibility code
```

### E2. Frontend transition

The Angular frontend is part of the rewrite. Two approaches, depending on scope:

**Option A: Coordinated release (recommended)**
- Rewrite frontend alongside backend
- Deploy both on cutover day
- No API compatibility layer needed

**Option B: API compatibility layer (if frontend rewrite is deferred)**
- New backend exposes endpoints matching current API shapes
- Frontend continues to work unchanged
- Gradually migrate frontend to new API shapes

**Decision:** Option A is preferred because the frontend is relatively small (Angular 19 SPA) and the current REST API has inconsistencies that should not be preserved.

### E3. In-flight data during cutover

Before cutover:
1. **Stop scheduled jobs** (ETL sync, pricing runs, alert checks, mismatch detection)
2. **Drain outbox** — wait for all pending outbox entries to be consumed
3. **Wait for active actions** — ensure no price actions are in EXECUTING state (wait for RECONCILIATION_PENDING or terminal)
4. **Snapshot check** — verify no pending work in any queue

After cutover:
1. **Resume scheduled jobs** in new system
2. **Outbox poller** picks up any entries created during transition

### E4. Vault credential migration

If credential key schema changes (03-A1):
1. Update Vault secrets to include canonical key names (additive — keep old keys)
2. Deploy new system with new key names
3. After stabilization, remove old key names from Vault

---

## F. Legacy Decommissioning Rules

### What to delete without hesitation

| Artifact | Why Safe to Delete |
|----------|-------------------|
| All JPA entity classes | Replaced by JDBC-only persistence |
| `BaseEntity` class | No JPA, no shared entity base |
| `datapulse-platform` module | Replaced by `kernel` + responsibilities distributed |
| `datapulse-common` module | Replaced by `kernel` |
| `*ApiService` wrapper classes | Merged into single use-case services |
| MapStruct mapper classes | Manual or alternative mapping |
| Advertising stub adapters | Dead code, never made real API calls |
| `TenancyAuditPublisher` | Unified audit mechanism |
| `AimdRateController` | AIMD removed — static rate limits sufficient |
| Duplicate credential resolvers | Single `CredentialService` |
| Old migration files | Kept in history but not executed by new system |

### What to delete only after stabilization

| Artifact | Why Wait |
|----------|----------|
| Old database columns | Backward compatibility during transition |
| Old Vault key names | Old system may need to read during rollback window |
| Old REST API endpoints (if Option B) | Frontend may still reference them |

### What to preserve regardless

| Artifact | Why Preserve |
|----------|-------------|
| Liquibase changelog history | Audit trail of schema evolution |
| S3 raw data objects | Provenance chain to existing canonical data |
| Production database data | Business continuity — all existing offers, actions, decisions, audit logs |
| Vault secrets (content) | Marketplace credentials — just normalize key names |

---

## G. Risks and How to Control Them

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Schema migration breaks production data | Low | Critical | Additive migrations + staging validation + checksums + rollback plan |
| New pricing engine produces different results than old | Medium | High | Golden-file tests: run new engine on known inputs, compare with legacy decisions |
| Marketplace API changes during rewrite | Low | Medium | Provider API specs pinned. Monitor marketplace changelog. |
| Rewrite takes too long, old system rots | Medium | High | Sliced implementation plan. Each slice delivers working value. Ship slice by slice, don't wait for "everything." |
| Team unfamiliarity with new patterns | Medium | Medium | Architecture documents (this set). Code review. Internal training on JDBC-only, outbox, batch patterns. |
| Cutover day failures | Low | Critical | Feature flags. Rollback plan. Staged rollout (internal users first). |
| Data loss during cutover | Low | Critical | Drain queues. Verify no in-flight work. Checksum validation. |
| New reconciliation flow creates false positives | Medium | Medium | Configurable tolerance for price comparison (±0.01 RUB). Logging before auto-fail. |

### Highest-priority risk: pricing engine correctness

The pricing engine is the core business value. A rewrite must produce **identical results** for the same inputs. This is validated by:

1. **Capture existing decisions:** Export a set of production price decisions with their full input snapshots (signals, policy, constraints)
2. **Replay through new engine:** Feed the same inputs into the new PricingEngine
3. **Compare outputs:** Decision type, target price, guard results must match exactly
4. **Resolve differences:** Any difference is either a bug fix (intentional) or a regression (fix it)

---

## H. Decision Criteria for Each Rewrite Step

Before completing each implementation step (see 09-implementation-plan.md), verify:

| Criterion | How to Verify |
|-----------|---------------|
| **Compiles and starts** | CI build passes. Application starts with test configuration. |
| **Tests pass** | All unit and integration tests for the step are green. |
| **API contract preserved** | Endpoints return expected shapes (or intentional improvements documented). |
| **Invariants hold** | Relevant invariants from 04-A are covered by tests. |
| **No legacy regression** | Golden-file tests for pricing/P&L match (where applicable). |
| **Schema migration reversible** | Rollback liquibase migration succeeds on staging. |
| **Performance acceptable** | Key operations (grid query, pricing run, ETL ingest) complete within acceptable latency on production-size data. |
| **Security maintained** | Workspace isolation test passes. Auth/RBAC test passes. |
| **Documentation updated** | This document set and progress tracker reflect current state. |
