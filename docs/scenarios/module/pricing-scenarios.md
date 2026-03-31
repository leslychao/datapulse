# Pricing — Module Scenarios

## Роль модуля

Pricing отвечает за принятие ценовых решений: 8-стадийный pipeline от eligibility check до action scheduling. Включает стратегии (TARGET_MARGIN, PRICE_CORRIDOR), policy management, signal assembly, constraint resolution, guard pipeline, и генерацию объяснений.

**Pipeline (8 стадий):** Eligibility → Signal Assembly → Strategy Evaluation → Constraint Resolution → Guard Pipeline → Decision → Explanation → Action Scheduling.

**Стратегии:** TARGET_MARGIN, PRICE_CORRIDOR.

**Режимы исполнения (`price_policy.execution_mode`):** RECOMMENDATION, SEMI_AUTO, FULL_AUTO, SIMULATED. При SIMULATED → `price_decision.execution_mode = SIMULATED`, остальные → LIVE.

## Сценарии

### PRC-01: Pricing run — happy path (TARGET_MARGIN)

- **Назначение:** Стандартный расчёт рекомендованной цены на основе целевой маржи.
- **Trigger:** `ETL_SYNC_COMPLETED` outbox event → pricing worker проверяет FINANCE ∈ completed_domains → pricing run per connection.
- **Main path:** Eligibility check → assemble signals (COGS, current price, commission, logistics from CH) → evaluate TARGET_MARGIN strategy → apply constraints (min/max) → guard pipeline (all pass) → create decision → generate explanation → schedule action.
- **Dependencies:** Canonical data fresh. ClickHouse signals available. `price_policy` assigned to offer. COGS (`cost_profile`) exists.
- **Failure risks:** Missing COGS → ineligible. Stale CH data → guard blocks.
- **Uniqueness:** Baseline happy path для самой распространённой стратегии.

### PRC-02: Pricing run — PRICE_CORRIDOR strategy

- **Назначение:** Расчёт цены в рамках ценового коридора (min/max bounds).
- **Trigger:** Тот же, что PRC-01.
- **Main path:** Signal assembly → evaluate PRICE_CORRIDOR → price clamped to `[floor, ceiling]` → constraints → guards → decision.
- **Dependencies:** `price_policy` с `strategy = PRICE_CORRIDOR`, floor/ceiling configured.
- **Failure risks:** Corridor bounds conflict with marketplace constraints → decision: use tighter bound.
- **Uniqueness:** Другая strategy evaluation logic (bounds-based vs margin-based).

### PRC-03: Eligibility failure — no policy assigned

- **Назначение:** Offer не имеет назначенной pricing policy.
- **Trigger:** Pricing run reaches eligibility check.
- **Main path:** No policy assignment found → offer skipped → log debug `no_policy` → no decision created.
- **Dependencies:** `policy_assignment` table.
- **Failure risks:** Entire marketplace has no policies → all skipped → zero decisions → silent but expected.
- **Uniqueness:** Early exit — никакая другая стадия pipeline не выполняется.

### PRC-04: Eligibility failure — missing COGS

- **Назначение:** Offer имеет policy, но COGS (cost price) не задан.
- **Trigger:** Signal assembly: `cost_profile` lookup returns empty.
- **Main path:** Offer ineligible for TARGET_MARGIN (COGS required). Log warning. No decision.
- **Dependencies:** `cost_profile` SCD2. `seller_sku` mapping.
- **Failure risks:** Mass COGS missing → large portion of catalog ineligible → alert needed.
- **Uniqueness:** Другая причина ineligibility (missing input data, не missing policy).

### PRC-05: Guard hit — stale data

- **Назначение:** Guard обнаружил, что данные устарели.
- **Trigger:** Guard pipeline: `stale_data_guard` checks data freshness.
- **Main path:** Last sync > threshold → guard blocks decision → decision NOT created → explanation: `data_stale` → alert if automation was blocked.
- **Dependencies:** `marketplace_sync_state.last_success_at`. Freshness threshold (configurable per policy).
- **Failure risks:** Overly aggressive threshold → false positives (blocking too often). Too lenient → pricing on stale data.
- **Uniqueness:** Guard-specific block — другой failure reason, другой recovery (wait for fresh sync).

### PRC-06: Action Scheduling: active action conflict

- **Назначение:** Уже есть active (non-terminal) price action для этого offer — конфликт обнаруживается на стадии Action Scheduling.
- **Trigger:** Action Scheduling stage: конфликт с partial unique index (active action уже существует для этого offer).
- **Main path:** Active action found for same offer → if pre-execution (PENDING_APPROVAL, APPROVED, ON_HOLD, SCHEDULED): immediate supersede → SUPERSEDED, new action created. If in-flight (EXECUTING, RETRY_SCHEDULED, RECONCILIATION_PENDING): `deferred_action` created, decision saved. Unique constraint race condition → decision saved with skip_reason «active action in progress».
- **Dependencies:** `price_action` partial unique index. Supersede policy.
- **Failure risks:** Unique constraint race condition → per-offer catch, batch continues. In-flight action stuck → stuck-state detector eventually resolves.
- **Uniqueness:** Handled at Action Scheduling (not Guard Pipeline). Per-offer conflict resolution, не batch interruption.

### PRC-07: Guard hit — promo active

- **Назначение:** Товар участвует в промо-акции, ценовое решение заблокировано.
- **Trigger:** Guard pipeline: `promo_guard` checks `promo_product` status.
- **Main path:** Active promo found → guard blocks → explanation: `promo_active, promo_id=X`.
- **Dependencies:** Promotions module: `canonical_promo_product`.
- **Failure risks:** Stale promo data → false block (promo ended but data not synced). Mitigation: promo sync freshness check.
- **Uniqueness:** Cross-module guard (Pricing ↔ Promotions). Другой resolution: promo ends → next run succeeds.

### PRC-08: Guard hit — stock guard (low inventory)

- **Назначение:** Запас товара слишком низкий для автоматического ценообразования.
- **Trigger:** Guard pipeline: `stock_guard` checks `canonical_stock_current`.
- **Main path:** Stock < threshold → guard blocks → explanation: `low_stock, available=X, threshold=Y`.
- **Dependencies:** `canonical_stock_current`. Threshold in `price_policy`.
- **Failure risks:** Stock data stale → false block/allow.
- **Uniqueness:** Другой signal source (stocks), другой threshold logic.

### PRC-09: Constraint resolution — marketplace-enforced limits

- **Назначение:** Маркетплейс имеет свои ограничения на цену (min/max).
- **Trigger:** Constraint resolution stage.
- **Main path:** Strategy output → clamp to `[marketplace_min, marketplace_max]` → if clamped, explanation notes `price_clamped_by_marketplace`.
- **Dependencies:** `canonical_offer.min_price`, `canonical_offer.max_price` (from marketplace sync).
- **Failure risks:** Marketplace constraints not synced → stale limits → incorrect clamping.
- **Uniqueness:** External constraint (маркетплейс, не policy). Другой source of truth.

### PRC-10: Manual price lock

- **Назначение:** Оператор вручную фиксирует цену — pricing pipeline пропускает offer.
- **Trigger:** POST manual price lock (Operator/Pricing Manager). Lock stored in dedicated `manual_price_lock` table (не флаг на canonical_offer). Lock has own lifecycle: creation, optional expiration (`expires_at`), manual unlock.
- **Main path:** Pricing run: eligibility check → locked offer → skip → no decision.
- **Dependencies:** `manual_price_lock` table. Lock expiry scheduled job (hourly). Lock expiry (optional).
- **Failure risks:** Lock forgotten (no expiry) → offer never repriced. Mitigation: lock duration alert.
- **Uniqueness:** User-initiated override — другой actor, другой persistence (dedicated lock table, не policy).

### PRC-11: Price policy versioning

- **Назначение:** Обновление policy создаёт новую версию, не мутирует существующую.
- **Trigger:** `PUT /api/price-policies/{id}` (PRICING_MANAGER/ADMIN).
- **Main path:** Atomic `UPDATE ... SET version = version + 1` → existing actions continue with old version (snapshot in `price_decision.policy_snapshot`) → next pricing run uses new version.
- **Dependencies:** `price_policy.version` (атомарный инкремент при UPDATE pricing-logic полей).
- **Failure risks:** Race: pricing run reads policy mid-update → version check ensures consistency.
- **Uniqueness:** Versioned write — другая persistence semantics (append, не mutate).

### PRC-12: Policy assignment (bulk)

- **Назначение:** Назначение policy на группу offers (by filter criteria).
- **Trigger:** `POST /api/policy-assignments` (PRICING_MANAGER/ADMIN).
- **Main path:** UPSERT `price_policy_assignment` with `scope_type` (CONNECTION, CATEGORY, или SKU) → trigger pricing run.
- **Dependencies:** Canonical offers. `scope_type` ENUM (CONNECTION, CATEGORY, SKU). `category_id` или `marketplace_offer_id` в зависимости от scope.
- **Failure risks:** Large filter → many assignments → large pricing run → performance.
- **Uniqueness:** Bulk write с dynamic filter — другой input (filter, не individual offer).

### PRC-13: Decision for RECOMMENDATION mode

- **Назначение:** Decision создан, но action не создаётся — только рекомендация.
- **Trigger:** `price_policy.execution_mode = RECOMMENDATION`.
- **Main path:** Full pipeline → decision created → explanation generated → NO action scheduled. Decision visible in UI.
- **Dependencies:** Same as PRC-01, but no Execution module involved.
- **Failure risks:** Recommendation ignored (user doesn't act). But no financial risk.
- **Uniqueness:** Другой business outcome (рекомендация, не действие). Execution module не задействован.

### PRC-14: Decision for FULL_AUTO mode

- **Назначение:** Decision автоматически переходит в execution без approval.
- **Trigger:** `price_policy.execution_mode = FULL_AUTO`.
- **Main path:** Full pipeline → decision → action created с status = APPROVED (skip PENDING_APPROVAL) → scheduling → execution.
- **Dependencies:** All guards must pass (more critical in FULL_AUTO since no human review).
- **Failure risks:** Incorrect decision → auto-executed → financial impact. Guards are the safety net.
- **Uniqueness:** Другой state machine path (skip approval). Higher risk profile.

### PRC-15: Pricing run with stale analytics

- **Назначение:** ClickHouse data stale, но canonical data fresh.
- **Trigger:** CH materialization delayed/failed. Pricing run triggered.
- **Main path:** Signal assembly: CH signals unavailable → `stale_data_guard` blocks → no decision. OR if guard threshold not exceeded: decision made with partial signals → explanation notes `partial_signals`.
- **Dependencies:** CH freshness. Guard threshold configuration.
- **Failure risks:** Decision on partial data → inaccurate pricing.
- **Uniqueness:** Degraded mode — частичные данные. Другой failure path (CH down, не provider down).

### PRC-16: Pricing simulation run

- **Назначение:** Simulated pricing: полный pipeline + simulated execution.
- **Trigger:** Pricing run (manual или post-sync) для connection, где policy имеет `execution_mode = SIMULATED`.
- **Main path:** Full pricing pipeline → decision (`execution_mode=SIMULATED`) → action (APPROVED, `execution_mode=SIMULATED`) → SimulatedPriceActionGateway → shadow state.
- **Dependencies:** Simulation infrastructure (EXE-16).
- **Failure risks:** Simulation results diverge from reality (different state, timing).
- **Uniqueness:** Другой execution mode. Результаты не влияют на реальные цены.

### PRC-17: Guard hit — frequency (too recent price change)

- **Назначение:** Частота ценовых изменений ограничена.
- **Trigger:** Guard pipeline: `frequency_guard` проверяет `price_decision` history.
- **Main path:** Последнее изменение цены < N часов назад (default: 24h) → guard blocks → explanation: `frequency_guard, last_change=X hours ago, threshold=Y hours`.
- **Dependencies:** `price_decision` history. `guard_config.frequency_guard_hours`.
- **Failure risks:** Aggressive threshold → задержка реакции на рыночные изменения. Configurable per policy.
- **Uniqueness:** Temporal guard — ограничивает частоту, не абсолютное значение.

### PRC-18: Guard hit — volatility (too many reversals)

- **Назначение:** Предотвращение осциллирующих ценовых изменений.
- **Trigger:** Guard pipeline: `volatility_guard` проверяет direction reversals.
- **Main path:** > N разворотов направления цены за период (default: 3 reversals / 7 days) → guard blocks → explanation: `volatility_guard, reversals=X, threshold=Y per Z days`.
- **Dependencies:** `price_decision` direction history. `guard_config.volatility_guard_reversals`, `volatility_guard_period_days`.
- **Failure risks:** Legitimate volatile market → unnecessary block. Mitigation: configurable, disableable.
- **Uniqueness:** Pattern-based guard (direction history), не point-in-time check.

### PRC-19: Guard hit — margin floor

- **Назначение:** Вычисленная цена даёт маржу ниже минимального порога.
- **Trigger:** Guard pipeline: `margin_guard` проверяет expected margin.
- **Main path:** Computed margin at target_price < `min_margin_pct` → guard blocks → explanation: `margin_guard, expected_margin=X%, min_margin=Y%`.
- **Dependencies:** COGS, effective_cost_rate, target_price. `price_policy.min_margin_pct`.
- **Failure risks:** Incorrect COGS → false block. Missing COGS → handled earlier at eligibility (PRC-04).
- **Uniqueness:** Post-strategy guard (проверяет result стратегии), не pre-strategy eligibility.

### PRC-20: Impact preview (dry-run)

- **Назначение:** Предпросмотр эффекта pricing policy перед активацией.
- **Trigger:** `POST /api/pricing/policies/{policyId}/preview` (PRICING_MANAGER/ADMIN/OWNER).
- **Main path:** Resolve offers в scope policy → dry-run pricing pipeline (eligibility → signals → strategy → constraints → guards) → НЕ создавать decisions/actions → return aggregated summary + per-offer breakdown (total_offers, eligible_count, change_count, skip_count, avg_price_change_pct, max_price_change_pct).
- **Dependencies:** Same as pricing run, но synchronous execution (не через outbox/worker). Timeout: 30s. Scope > 10 000 offers → async preview via polling.
- **Failure risks:** Data changed between preview и реальный pricing run → preview ≠ reality. Preview ≠ гарантия.
- **Uniqueness:** Read-only (никаких side effects). Synchronous API (не outbox). Другой execution path.

### PRC-21: Safety gate для FULL_AUTO

- **Назначение:** Проверка условий перед переключением policy на FULL_AUTO.
- **Trigger:** PUT policy `execution_mode = FULL_AUTO`.
- **Main path:** Система проверяет 5 условий: (1) Policy была в SEMI_AUTO минимум N дней (default: 7), (2) Не было FAILED actions за последние N дней, (3) Stale data guard НЕ отключён, (4) Manual lock guard НЕ отключён, (5) Pricing manager явно подтверждает (UI confirmation).
- **Dependencies:** price_action history. price_policy history. guard_config.
- **Failure risks:** Conditions met but underlying data quality poor → false confidence. Safety gate — necessary but not sufficient.
- **Uniqueness:** Meta-check на policy transition, не на individual pricing decision.

### PRC-22: Commission source cascading fallback

- **Назначение:** Каскадный fallback для определения commission rate в стратегии TARGET_MARGIN.
- **Trigger:** Signal assembly stage: commission_source = `AUTO_WITH_MANUAL_FALLBACK` (default).
- **Main path:** Step 1: Historical per-SKU (`lookback_days`, ≥ `min_transactions`) → достаточно данных → use. Step 2: Недостаточно → historical per-category → use. Step 3: Недостаточно → `commission_manual_pct` из strategy_params → use. Step 4: Не задан → decision = SKIP, skip_reason = `no_commission_data`.
- **Dependencies:** `fact_finance` (ClickHouse) для historical computation. `price_policy.strategy_params.commission_manual_pct`. `min_transactions` threshold.
- **Failure risks:** New SKU + new category → cascade до manual → если manual не задан → SKIP для всех новых товаров. User confusion: «почему нет решений?» → explanation should clarify fallback chain.
- **Uniqueness:** 4-step cascade — каждый шаг другой data source, другой fallback trigger. Финальный SKIP — неочевидный business outcome.

### PRC-23: Policy assignment conflict resolution (specificity + priority)

- **Назначение:** Разрешение конфликтов при нескольких policy assignments для одного offer.
- **Trigger:** Eligibility stage: resolve effective policy для marketplace_offer.
- **Main path:** Offer имеет category_id = 42 и marketplace_connection_id = 1. Policy A assigned на CONNECTION (scope_type = CONNECTION, специфичность 1). Policy B assigned на CATEGORY 42 (scope_type = CATEGORY, специфичность 2). → Policy B wins (более специфичная). При равной специфичности: policy.priority (выше = важнее). При равном priority: наименьший id (first created wins).
- **Dependencies:** `price_policy_assignment` table. `marketplace_offer.category_id`. Resolution algorithm.
- **Failure risks:** SKU-level assignment забыта при bulk assignment → SKU policy overrides category/connection policy. Unexpected behavior if user assigns conflicting policies.
- **Uniqueness:** Multi-level resolution — другой algorithm (specificity + priority + id tiebreak), другой failure mode (policy shadowing).

### PRC-24: Price no-change (target = current)

- **Назначение:** Pipeline отработал, но вычисленная цена совпадает с текущей.
- **Trigger:** Strategy evaluation: computed target_price = canonical_price_current.price (после rounding и constraints).
- **Main path:** Full pipeline → strategy evaluates → constraint resolution → target_price = current_price → decision_type = SKIP, skip_reason = `no_change` → explanation generated → NO action created. Log debug.
- **Dependencies:** canonical_price_current. Strategy computation. Rounding config.
- **Failure risks:** Rounding creates false no-change (price differs by < rounding_step). Legitimate: если данные не изменились — решение не нужно.
- **Uniqueness:** Другой business outcome (SKIP, не guard block и не eligibility failure). Предотвращает создание бессмысленных actions в FULL_AUTO.

---

## Индекс сценариев

| ID | Кратко |
|----|--------|
| PRC-01 | Happy path, TARGET_MARGIN |
| PRC-02 | PRICE_CORRIDOR |
| PRC-03 | Eligibility: no policy |
| PRC-04 | Eligibility: missing COGS |
| PRC-05 | Guard: stale data |
| PRC-06 | Action Scheduling: active action conflict |
| PRC-07 | Guard: promo active |
| PRC-08 | Guard: low stock |
| PRC-09 | Marketplace constraints |
| PRC-10 | Manual price lock |
| PRC-11 | Policy versioning |
| PRC-12 | Bulk policy assignment |
| PRC-13 | RECOMMENDATION mode |
| PRC-14 | FULL_AUTO mode |
| PRC-15 | Stale / partial CH analytics |
| PRC-16 | SIMULATED run |
| PRC-17 | Guard: frequency (too recent change) |
| PRC-18 | Guard: volatility (reversals) |
| PRC-19 | Guard: margin floor |
| PRC-20 | Impact preview (dry-run) |
| PRC-21 | Safety gate для FULL_AUTO |
| PRC-22 | Commission source cascade |
| PRC-23 | Policy assignment conflicts |
| PRC-24 | Price no-change (target = current) |

## Связанные документы

- Модуль: `docs/modules/pricing.md`
- Execution (actions, simulation): `docs/modules/execution.md`
- Promotions (promo guard): `docs/modules/promotions.md`
