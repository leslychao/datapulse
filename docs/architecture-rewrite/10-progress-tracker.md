# 10 Progress Tracker

---

## Current Phase

**Phase 2: Architecture Design**

Status: **COMPLETE** — all design documents created and filled.

---

## Completed Documents

### Phase 1: Diagnostic (Extraction & Analysis)

| Document | Status | Last Updated | Summary |
|----------|--------|-------------|---------|
| `00-rules-and-process.md` | ✅ Complete | 2026-04-05 | Governing rules, principles, document hierarchy, analysis methodology |
| `01-current-system-analysis.md` | ✅ Complete | 2026-04-05 | System overview, 12 modules, 5 main execution flows, entry points, data ownership, transaction boundaries, dependency map, where business logic lives, 5 initial architectural observations |
| `02-business-model-extracted.md` | ✅ Complete | 2026-04-05 | 7 core capabilities, 20+ entities, value objects, aggregate boundaries, 16 invariants, 4 state machines, 10 use cases, business rules for pricing/guards/P&L, domain truth vs implementation artifacts |
| `03-legacy-problems-and-noise.md` | ✅ Complete | 2026-04-05 | 6 architectural defects (1 critical, 3 high, 2 medium), 3 accidental complexity items, 3 historical layering issues, 2 unnecessary abstractions, 3 workarounds, 3 duplication/leak issues, what must not be carried |
| `04-rewrite-invariants-and-constraints.md` | ✅ Complete | 2026-04-05 | 16 non-negotiable invariants, external contracts for WB/Ozon/infra, data semantics, lifecycle guarantees, transaction guarantees, security constraints, breakable vs preserved compatibility |

### Phase 2: Architecture Design

| Document | Status | Last Updated | Summary |
|----------|--------|-------------|---------|
| `05-target-architecture.md` | ✅ Complete | 2026-04-05 | 8 architectural goals, design principles, 11-module structure (kernel, marketplace, iam, catalog, ingestion, analytics, pricing, execution, promotions, operations, alerting + app), domain core, integration edges, transaction boundaries, error handling, observability, what's dropped from legacy |
| `06-module-boundaries-and-dependencies.md` | ✅ Complete | 2026-04-05 | 12 modules with detailed responsibilities, allowed/forbidden dependency graph, integration patterns, domain/application/infrastructure separation, transaction ownership per module, 7 erosion prevention rules |
| `07-domain-model-and-use-cases.md` | ✅ Complete | 2026-04-05 | 35+ entities, 16 value objects, 10 aggregate boundaries with invariants, 4 state machines, 26 domain services, 30+ use cases across all modules, 8 decision points, domain events vs outbox commands, fixes vs legacy |
| `08-rewrite-strategy.md` | ✅ Complete | 2026-04-05 | Controlled big-bang with parallel staging validation, 5 preconditions, 4 safety nets, transition rules (schema, frontend, in-flight data, Vault), decommissioning rules, 8 risks with mitigations, decision criteria per step |
| `09-implementation-plan.md` | ✅ Complete | 2026-04-05 | 8 ordered steps with concrete artifacts/dependencies/done criteria, foundation → IAM → catalog/ingestion → analytics → alerting → pricing → execution → promotions/operations. Total estimate: 50-70 dev-days. |
| `10-progress-tracker.md` | ✅ Current | 2026-04-05 | This file |

---

## In Progress

None — Phase 2 design is complete.

---

## Pending (Future Phases)

| Phase | Description | Prerequisites |
|-------|-------------|---------------|
| Phase 3: Precondition Resolution | Resolve C1-C5 preconditions from 08. Confirm invariants with stakeholder. Inspect Vault credential schema. Extract REST API contract. | Phase 2 reviewed by stakeholder |
| Phase 4: Implementation | Build the new system per 09-implementation-plan.md (8 steps, 50-70 dev-days) | Phase 3 complete. Preconditions met. |
| Phase 5: Validation & Cutover | Parallel staging validation, golden-file pricing tests, acceptance tests, cutover | Phase 4 complete for all 8 steps |
| Phase 6: Stabilization & Cleanup | Monitor production, delete legacy code, clean up database schema | Phase 5 cutover successful |

---

## Key Architectural Decisions

| Decision | Rationale | Reference |
|----------|-----------|-----------|
| **12 modules** (not fewer, not more) | Each module has one clear reason to exist. Fewer → catch-all modules. More → fragmentation. | 06-B |
| **JDBC only, no JPA** | Eliminates dual-model confusion (03-B1). All persistence is explicit. | 05-B |
| **Catalog as explicit shared contract** | Fixes legacy's uncontracted shared kernel (03-A4). Compile-time breakage on contract change. | 05-D, 06-B4 |
| **Pricing → Execution via outbox, not direct call** | Decouples bidirectional conceptual dependency (01-J1). Modules independently evolvable. | 05-D, 06-D |
| **Single CredentialService** | Eliminates credential key naming inconsistency (03-A1). Physically impossible to diverge. | 05-F, 06-B2 |
| **Batched pricing runs** | Fixes single-transaction scalability ceiling (03-A2). Per-batch commits with progress tracking. | 05-H |
| **Reconciliation with read-after-write** | Fixes missing verification (03-A3). Mandatory step in execution flow. | 05-I |
| **Controlled big-bang rewrite** | Strangler-fig impractical for monolith with shared DB. Incremental schema migration provides safety. | 08-A, 08-B |
| **CH degradation centralized in analytics** | Fixes per-service scatter (03-E1). Single circuit breaker, clean read interfaces. | 05-D, 06-B6 |

---

## Key Risks

| Risk | Severity | Status | Mitigation |
|------|----------|--------|------------|
| Pricing engine produces different results than legacy | High | Mitigated by plan | Golden-file tests (09-F) |
| Schema migration breaks production data | Critical | Mitigated by plan | Additive migrations + checksums (08-D2, D3) |
| Rewrite takes too long | Medium | Mitigated by plan | Sliced implementation, 50-70 day estimate (09-A) |
| Credential key schema unclear | High | **Unresolved** | Requires runtime Vault inspection (08-C5) |
| Invariants list incomplete | Medium | Mitigated by plan | Stakeholder review of 04-A (08-C4) |

---

## Open Questions / Uncertainties

| Question | Impact | How to Resolve | Status |
|----------|--------|---------------|--------|
| Actual Vault secret key structure? | Critical — determines CredentialService design | Inspect staging/prod Vault | **Unresolved** |
| Confirm all 16 invariants with stakeholder? | High — ensures rewrite preserves correct rules | Stakeholder review of 04-A | **Unresolved** |
| Is advertising pipeline in scope for rewrite? | Medium — affects step count and effort | Product decision | **Unresolved** |
| Frontend rewrite coordinated or deferred? | Medium — affects API compatibility approach | Team decision | **Assumed coordinated** (08-E2) |
| Production data snapshot available for testing? | Medium — affects golden-file test feasibility | Ops team | **Assumed available** |
| Actual pricing run sizes in production? | Medium — validates batching design | Production metrics | **Unresolved** |

---

## Corrections to Phase 1 Documents

No corrections needed. Phase 1 documents were used as source of truth; no contradictions found during Phase 2 design.

---

## Next Recommended Step

**Review Phase 2 design documents (05-09) with stakeholder.** Key review points:

1. **Module structure (06)** — confirm 12-module breakdown makes sense for the team
2. **Catalog as shared contract (05-D, 06-B4)** — this is the biggest structural change from legacy
3. **Pricing → Execution decoupling via outbox (06-D)** — confirm this level of decoupling is desired
4. **JDBC-only decision (05-B)** — confirm team is comfortable dropping JPA entirely
5. **Implementation order (09-A)** — confirm starting with Foundation + IAM + Marketplace
6. **Effort estimate (09-I)** — 50-70 dev-days — align with team capacity and timeline

After review, resolve open questions (especially Vault credential schema) and proceed to Phase 3 (Precondition Resolution) → Phase 4 (Implementation).
