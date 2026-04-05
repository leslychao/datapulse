# 02 Business Model Extracted

This document extracts the **real business model** from the Datapulse codebase — independent of the current implementation structure. The goal is to capture what the system does for its users, what rules govern business behavior, and what invariants must always hold.

---

## A. Core Business Capabilities

Datapulse provides the following business capabilities to marketplace sellers:

### 1. Marketplace Data Consolidation
Automatically ingest product catalog, pricing, inventory, orders, sales, returns, financial transactions, and promotional campaigns from Wildberries and Ozon into a unified canonical model. Provide provenance back to raw API responses.

### 2. Financial Analytics (P&L)
Calculate profit and loss at posting, product, and account levels by combining marketplace financial data with seller-provided cost profiles. Attribute advertising spend. Detect data quality issues.

### 3. Algorithmic Pricing
Apply configurable pricing strategies (target margin, price corridor) with constraint limits and safety guards. Produce explainable pricing decisions with full audit trail. Support multiple execution modes from recommendation-only to fully automated.

### 4. Price Action Execution
Execute approved price changes on marketplace APIs with retry logic, reconciliation verification, and conflict management. Ensure price changes are confirmed by marketplace before marking as successful.

### 5. Promotional Campaign Management
Evaluate promotional campaigns against configurable policies to decide product participation. Execute participation/declination actions on marketplace APIs.

### 6. Operational Monitoring
Provide a unified operational grid across all products, connections, and marketplaces. Detect mismatches between expected and actual marketplace state. Manage working queues for operational review.

### 7. Safety & Audit
Maintain complete audit trail of all business actions. Alert on data quality issues. Block automation when data is unreliable. Notify users of events requiring attention.

---

## B. Core Entities

These are the business entities that exist in the domain, described independently of their database representation.

### Tenant & Workspace
- **Tenant** — a billing/ownership unit. Has one owner user. Can have multiple workspaces.
- **Workspace** — the primary isolation boundary. All business data is scoped to a workspace. Has members with roles.
- **User** — authenticated person, can be a member of multiple workspaces with different roles.
- **Member** — a user's membership in a workspace with a specific role (OWNER, ADMIN, PRICING_MANAGER, OPERATOR, ANALYST, VIEWER).

### Product Hierarchy
- **Product Master** — a seller's product, identified by an internal code. Workspace-scoped. One product can be sold on multiple marketplaces.
- **Seller SKU** — a specific stock-keeping unit under a product master. Has barcode.
- **Marketplace Offer** — a product listing on a specific marketplace connection. Has marketplace-specific IDs (`marketplaceSku`, `marketplaceSkuAlt`), status, category, images.

### Marketplace Connection
- **Connection** — represents a seller's account on a marketplace (WB or Ozon). Holds a reference to encrypted credentials in Vault. Has health status. Workspace-scoped.
- **Sync State** — per-connection, per-data-domain tracking of when data was last synced and what the sync cursor is.

### Canonical Data (Marketplace-Agnostic)
- **Canonical Price** — current price snapshot for a marketplace offer (price, discountPrice, discountPct, min/max).
- **Canonical Stock** — current stock level per offer per warehouse (available, reserved).
- **Canonical Order** — a marketplace order with quantity, price, status, fulfillment type.
- **Canonical Sale** — a confirmed sale (posting-level) with commission.
- **Canonical Return** — a returned item with reason and amount.
- **Canonical Finance Entry** — a detailed financial transaction from marketplace (sales, commission, logistics, penalties, advertising, etc.).
- **Cost Profile** — seller-provided cost-of-goods (COGS) per SKU, with temporal validity (SCD2 pattern via validFrom/validTo).

### Pricing Domain
- **Price Policy** — a named configuration for pricing: strategy type, strategy parameters, constraint limits (min/max price, max change %), guard configuration, execution mode. Versioned.
- **Policy Assignment** — binds a policy to a scope: entire connection, specific category, or specific offer.
- **Pricing Run** — an execution of the pricing pipeline for a connection. Tracks trigger type, status, counters.
- **Price Decision** — the outcome of pricing for one offer in one run. Contains full explanation (strategy result, constraints applied, guards evaluated, signal snapshot, policy snapshot).
- **Manual Price Lock** — prevents automated pricing for a specific offer until unlocked or expired.

### Execution Domain
- **Price Action** — a command to change a price on a marketplace. Has a lifecycle (state machine), target price, connection reference, decision reference. Can be deferred if a conflicting action exists.
- **Price Action Attempt** — one try at executing a price action. Records provider request/response summaries.
- **Reconciliation** — verification that the marketplace actually applied the price change.

### Promotions Domain
- **Promo Campaign** — a synced representation of a marketplace promotion (dates, type, mechanic, products).
- **Promo Product** — a product's participation status in a campaign (with prices, discounts, stock requirements).
- **Promo Policy** — rules for automatic promo evaluation (auto-participate categories, auto-decline categories, configuration).
- **Promo Evaluation Run** — an execution of promo evaluation for a connection.
- **Promo Decision** — the outcome for one product in one campaign (PARTICIPATE, DECLINE, DEACTIVATE, PENDING_REVIEW).
- **Promo Action** — a command to participate/decline/deactivate in a promo on the marketplace.

### Operational Entities
- **Saved View** — a named set of grid filters and visible columns. Workspace-scoped.
- **Working Queue** — a prioritized list of offers for operational review, with auto-population criteria.
- **Mismatch** — a detected discrepancy between expected and actual marketplace state (price, promo, finance, stock).

### Audit & Alerting
- **Audit Log Entry** — a record of a business action (who, what, when, where, details).
- **Alert Rule** — a configured check (stale data, missing sync, residual anomaly, spike, mismatch) with thresholds.
- **Alert Event** — an instance of a triggered alert with severity, details, resolution status. Can block automation.
- **Notification** — a user-facing message derived from audit or alert events. Fan-out by role.

---

## C. Value Objects

Value objects are immutable concepts with no identity of their own:

| Value Object | Description | Key Fields |
|-------------|-------------|------------|
| `MarketplaceType` | WB or OZON | Enum |
| `ConnectionStatus` | PENDING_VALIDATION, ACTIVE, AUTH_FAILED, DISABLED, ARCHIVED | Enum |
| `WorkspaceRole` | OWNER, ADMIN, PRICING_MANAGER, OPERATOR, ANALYST, VIEWER | Enum |
| `EtlEventType` | PRODUCT_DICT, PRICE_SNAPSHOT, SALES_FACT, etc. | Enum |
| `PolicyType` / `StrategyType` | TARGET_MARGIN, PRICE_CORRIDOR | Enum |
| `ExecutionMode` | RECOMMENDATION, SEMI_AUTO, FULL_AUTO, SIMULATED | Enum |
| `ActionStatus` | Full state machine (see section F) | Enum |
| `DecisionType` | CHANGE, SKIP, HOLD | Enum |
| `GuardResult` | PASS or BLOCK with reason key + params | Value |
| `SignalSnapshot` | Pricing signals at the moment of decision (JSONB) | Map |
| `PolicySnapshot` | Policy configuration at the moment of decision (JSONB) | Map |
| `ExplanationSummary` | Human/machine-readable summary of how a decision was made | String |
| `ErrorClassification` | RETRIABLE, UNCERTAIN, NON_RETRIABLE | Enum |
| `RateLimitGroup` | Logical grouping for API rate limits (WB_STATISTICS, OZON_DEFAULT, etc.) | Enum |

---

## D. Aggregates

In DDD terms, the following aggregate boundaries are evident from the code:

| Aggregate Root | Contains | Invariants |
|---------------|----------|------------|
| **Workspace** | Members, Invitations | Role uniqueness per user, owner transfer rules |
| **Connection** | SyncStates, CredentialReferences | One active credential per type, status transitions |
| **PricePolicy** | Assignments | Version bumps on logic change, assignment scope rules |
| **PricingRun** | PriceDecisions | Run status consistency, counter accuracy |
| **PriceAction** | Attempts, StateTransitions, DeferredAction | State machine invariants, CAS transitions |
| **PromoPolicy** | Assignments | Assignment scope rules |
| **PromoEvaluationRun** | Evaluations, Decisions, Actions | Run status, counter accuracy |
| **PromoAction** | Attempts | State machine, retry limits |
| **AlertRule** | — | Configuration validation |
| **AlertEvent** | — | Status transitions (active → acknowledged → resolved) |
| **WorkingQueue** | QueueItems | Item limit per queue, assignment rules |

**Note:** Not all of these are modeled as explicit aggregates in the code. Most are flat JPA entities with scalar FK references. The aggregate boundaries listed here are **inferred from business invariants**, not from code structure. [HIGH CONFIDENCE]

---

## E. Invariants

### Pricing Invariants

1. **Decision immutability:** Once a `price_decision` is saved, its content (signal snapshot, policy snapshot, constraints, guards) is never modified. It is a point-in-time record. [CONFIRMED]
2. **Policy version tracking:** Each decision records which policy version produced it. If policy logic changes, the version increments. [CONFIRMED]
3. **Guard chain ordering:** Guards execute in a defined order. First blocking guard terminates the chain. [CONFIRMED]
4. **Manual lock supremacy:** If a manual price lock is active for an offer, no automated pricing can change its price (ManualLockGuard blocks). [CONFIRMED]
5. **Blast radius limit:** In FULL_AUTO mode, no more than a configured percentage of offers can have prices changed in a single run. [CONFIRMED]
6. **Automation blocker:** If data quality alerts are active (stale data, missing sync), automated pricing is blocked for the affected connection. [CONFIRMED]

### Execution Invariants

7. **SUCCEEDED requires reconciliation:** A price action reaches SUCCEEDED state **only** when reconciliation confirms the marketplace applied the target price. No shortcut. [CONFIRMED]
8. **CAS state transitions:** State transitions are atomic compare-and-swap operations. Concurrent transitions to the same state are prevented at the database level. [CONFIRMED]
9. **Supersede policy:** When a new action targets the same offer while an older action is still pending, the older action may be superseded (moved to SUPERSEDED terminal state). [CONFIRMED]
10. **At-least-once delivery:** Outbox pattern guarantees price action execution is attempted at least once, even if the application crashes after scheduling. [CONFIRMED]

### Data Invariants

11. **Canonical model is marketplace-agnostic:** Canonical entities (`canonical_order`, `canonical_sale`, etc.) do not contain marketplace-specific field names. Normalization happens in the ETL layer. [CONFIRMED]
12. **PostgreSQL is the source of truth:** ClickHouse data is derived. If there is a conflict, PG wins. [CONFIRMED]
13. **Raw data provenance:** Every canonical entity references the `job_execution_id` that created it, enabling trace-back to raw S3 data. [CONFIRMED]
14. **UPSERT idempotency:** Canonical ingestion uses UPSERT with natural keys (marketplace-specific external IDs), making re-ingestion safe. [CONFIRMED]

### Tenancy Invariants

15. **Workspace isolation:** All business data queries include workspace-level filtering (directly or via connection_id → workspace_id). No cross-workspace data leakage. [CONFIRMED]
16. **Owner uniqueness:** Each workspace has exactly one OWNER. Ownership transfer is an explicit operation. [CONFIRMED]

---

## F. Lifecycle and State Transitions

### F1. Price Action State Machine

```
                                    ┌─────────────────────────┐
                                    │    PENDING_APPROVAL      │
                                    └────┬──────────┬──────────┘
                                         │          │
                                    approve      reject
                                         │          │
                                         ▼          ▼
                                    ┌─────────┐  ┌──────────┐
                        ┌──────────│ APPROVED │  │ REJECTED │ (terminal)
                        │          └────┬─────┘  └──────────┘
                        │               │
                     hold          schedule
                        │               │
                        ▼               ▼
                   ┌──────────┐   ┌───────────┐
                   │ ON_HOLD  │   │ SCHEDULED │
                   └────┬─────┘   └─────┬─────┘
                        │               │
                     resume          execute
                        │               │
                        ▼               ▼
                   ┌──────────┐   ┌───────────┐
                   │ APPROVED │   │ EXECUTING │
                   └──────────┘   └─────┬─────┘
                                        │
                              ┌─────────┴─────────┐
                              │                   │
                           success             failure
                              │                   │
                              ▼                   ▼
                   ┌───────────────────┐   ┌─────────┐
                   │ RECONCILIATION_   │   │ FAILED  │ (may retry)
                   │ PENDING           │   └────┬────┘
                   └────────┬──────────┘        │
                            │              retry │
                   ┌────────┴────────┐          │
                   │                │           ▼
               confirmed       not confirmed  ┌──────────────┐
                   │                │          │RETRY_SCHEDULED│
                   ▼                ▼          └───────┬──────┘
              ┌───────────┐  ┌──────────────┐          │
              │ SUCCEEDED │  │schedule next │     re-execute
              │ (terminal)│  │   check      │          │
              └───────────┘  └──────────────┘          ▼
                                                  ┌───────────┐
                                                  │ EXECUTING │
                                                  └───────────┘

Additional terminal states: EXPIRED, CANCELLED, SUPERSEDED
```

### F2. Connection Status Lifecycle

```
PENDING_VALIDATION → ACTIVE (health check success)
PENDING_VALIDATION → AUTH_FAILED (health check failure)
ACTIVE → AUTH_FAILED (credential rotation failure / health degradation)
ACTIVE → DISABLED (manual disable)
AUTH_FAILED → ACTIVE (credential update + successful validation)
DISABLED → ACTIVE (manual enable)
Any non-terminal → ARCHIVED (manual archive, terminal)
```

### F3. Pricing Run Status

```
PENDING → IN_PROGRESS → COMPLETED / FAILED / COMPLETED_WITH_WARNINGS
PENDING → CANCELLED (manual cancel before execution)
IN_PROGRESS → PAUSED (blast radius breaker) → COMPLETED (manual resume)
```

### F4. Promo Decision Types

```
PARTICIPATE — join the promotion
DECLINE — do not join
DEACTIVATE — leave a previously joined promotion
PENDING_REVIEW — needs manual operator decision
```

---

## G. Main Use Cases

### G1. Seller Onboarding
Actor: Seller. Creates tenant → creates workspace → invites team → creates marketplace connections → system validates credentials → triggers initial sync → data flows in.

### G2. Data Synchronization
Actor: System (scheduled). For each active connection, runs ETL event sources for all domains → ingests raw data → normalizes → upserts canonical → materializes to ClickHouse.

### G3. P&L Review
Actor: Analyst. Opens P&L dashboard → selects connection(s) and period → views aggregated summary → drills into posting-level detail → traces to raw API response (provenance).

### G4. Configure Pricing Policy
Actor: Pricing Manager. Creates a price policy with strategy (target margin or corridor), sets constraints (min/max price, max change %), configures guards, selects execution mode → assigns policy to connection/category/SKU scope.

### G5. Pricing Run
Actor: System (triggered by sync completion, schedule, or manual). Runs pricing pipeline → produces decisions for each offer → schedules price actions according to execution mode.

### G6. Review & Approve Price Actions
Actor: Operator. Views pending price actions in grid → reviews explanation → approves/rejects/holds individual or bulk actions.

### G7. Price Execution & Reconciliation
Actor: System. Executes approved price actions on marketplace API → waits for reconciliation check → verifies price was applied → marks succeeded or retries.

### G8. Promo Evaluation
Actor: System (triggered manually or on sync). Evaluates each product against promo campaigns using promo policy → produces participation decisions → creates promo actions.

### G9. Mismatch Detection
Actor: System (scheduled). Compares expected prices/stocks/promo state with actual marketplace data → creates mismatch records → triggers alerts if thresholds exceeded.

### G10. Operational Grid Work
Actor: Operator. Browses unified product grid → filters/sorts → opens offer detail → reviews pricing history, promo history, action history → locks/unlocks prices → processes working queue items.

---

## H. Business Rules and Decisions

### Pricing Strategy Rules

| Strategy | Rule |
|----------|------|
| **Target Margin** | `targetPrice = COGS / (1 - targetMarginPct - commissionPct - logisticsCostPct)`. Adjusted by signal-based factors. |
| **Price Corridor** | `targetPrice = currentPrice * (1 + corridorAdjustmentPct)`. Bounded by corridor limits. |

### Constraint Rules

| Constraint | Rule |
|-----------|------|
| Min Price | `targetPrice >= policy.minPrice` |
| Max Price | `targetPrice <= policy.maxPrice` |
| Max Change % | `abs(targetPrice - currentPrice) / currentPrice <= policy.maxPriceChangePct` |
| Min Margin | `targetPrice` must result in margin >= `policy.minMarginPct` |

### Guard Rules (Ordered Chain)

| Guard | Order | Blocks When |
|-------|-------|-------------|
| ManualLockGuard | 10 | Active manual lock on offer |
| StaleDataGuard | 20 | Sync data older than threshold |
| StockOutGuard | 30 | Zero or near-zero stock |
| PromoGuard | 40 | Offer currently in active promotion |
| FrequencyGuard | 50 | Price was changed too recently |
| VolatilityGuard | 60 | Price change exceeds volatility threshold |
| MarginGuard | 70 | Resulting margin below critical threshold |

### P&L Calculation Rules

- Revenue = sum of sale amounts from `canonical_sale`
- Commission = marketplace commission fees from `canonical_finance_entry`
- Logistics = shipping/handling fees
- Advertising = WB direct attribution by campaign; Ozon pro-rata allocation
- COGS = from `cost_profile` with SCD2 matching on `finance_date` (latest valid profile at transaction date)
- Residual = unattributed finance entries (diagnostic measure, should be small)

---

## I. Domain Truth vs Implementation Artifacts

### This IS real business logic (must survive rewrite)

| What | Why |
|------|-----|
| Pricing strategy calculations | Core value proposition |
| Guard chain with ordered evaluation | Safety mechanism |
| Price action state machine with CAS | Execution reliability |
| Reconciliation requirement before SUCCEEDED | Data integrity guarantee |
| Canonical model normalization rules | Marketplace-agnostic data quality |
| P&L formula with COGS SCD2 | Financial accuracy |
| Policy versioning and decision snapshots | Explainability and audit |
| Workspace-level isolation | Multi-tenancy security |
| Outbox for reliable async delivery | At-least-once guarantee |
| Automation blocker on stale data | Safety mechanism |

### This is NOT business logic (implementation artifact)

| What | Why it exists | Can be dropped |
|------|---------------|----------------|
| Strategy+Registry pattern | Clean extensibility | Yes — pattern choice is implementation |
| EventSource per marketplace per event type | Code organization for ETL | Yes — organization is implementation |
| CAS via raw SQL queries | Performance choice vs JPA optimistic locking | Yes — any concurrency control that provides same guarantees |
| Separate PG + CH with materialization | Performance architecture | Maybe — if single DB can meet requirements |
| Outbox + RabbitMQ | Reliability pattern | Yes — any at-least-once mechanism |
| MapStruct mappers | Convenience | Yes — any mapping approach |
| Dual JPA + JDBC persistence | Historical evolution | Yes — one consistent approach |
| Rate limiter in Redis | Performance/scalability | Yes — any rate limiting that meets marketplace limits |

---

## J. Confidence / Uncertainties

| Finding | Confidence |
|---------|-----------|
| Core entities and their relationships | [CONFIRMED] — verified against DB schema and entity code |
| Pricing strategy formulas | [HIGH CONFIDENCE] — read strategy implementations, but edge cases in parameter handling not fully traced |
| Guard chain order and behavior | [CONFIRMED] — read `PricingGuardChain` and all 7 guard implementations |
| Price action state machine | [CONFIRMED] — read `ActionService` + CAS repository |
| P&L calculation approach | [HIGH CONFIDENCE] — based on documentation + analytics repository queries, but exact allocation formulas not line-by-line verified |
| COGS SCD2 matching logic | [HIGH CONFIDENCE] — documented in analytics module, code in `PnlReadRepository` |
| Promo evaluation rules | [HIGH CONFIDENCE] — read `PromoEvaluationService`, but policy resolution details partially traced |
| Mismatch detection rules | [HIGH CONFIDENCE] — read `MismatchMonitorService`, 4 types of checks confirmed |
| Completeness of invariants list | [UNCERTAIN] — there may be additional implicit invariants not yet identified |
| Advertising attribution rules | [UNCERTAIN] — advertising adapters are stubs, documented rules may not be fully implemented |
