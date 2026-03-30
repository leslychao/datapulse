# Pricing — Module Scenarios

## Роль модуля

Pricing отвечает за принятие ценовых решений: 8-стадийный pipeline от eligibility check до action scheduling. Включает стратегии (TARGET_MARGIN, PRICE_CORRIDOR), policy management, signal assembly, constraint resolution, guard pipeline, и генерацию объяснений.

**Pipeline (8 стадий):** Eligibility → Signal Assembly → Strategy Evaluation → Constraint Resolution → Guard Pipeline → Decision → Explanation → Action Scheduling.

**Стратегии:** TARGET_MARGIN, PRICE_CORRIDOR.

**Режимы исполнения:** RECOMMENDATION, SEMI_AUTO, FULL_AUTO, SIMULATED.

## Сценарии

### PRC-01: Pricing run — happy path (TARGET_MARGIN)

- **Назначение:** Стандартный расчёт рекомендованной цены на основе целевой маржи.
- **Trigger:** `PRICES_SYNC_COMPLETED` или `STOCKS_SYNC_COMPLETED` outbox event → pricing run per connection.
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

### PRC-06: Guard hit — active action exists

- **Назначение:** Уже есть active (non-terminal) price action для этого offer.
- **Trigger:** Guard pipeline: `active_action_guard` checks `price_action`.
- **Main path:** Active action found → guard blocks → no new decision → explanation: `active_action_exists`.
- **Dependencies:** `price_action` partial unique index.
- **Failure risks:** Stuck action → permanently blocks new pricing. Mitigation: stuck-state detector (EXE-15).
- **Uniqueness:** Cross-module guard (Pricing checks Execution state). Другая recovery: resolve existing action.

### PRC-07: Guard hit — promo active

- **Назначение:** Товар участвует в промо-акции, ценовое решение заблокировано.
- **Trigger:** Guard pipeline: `promo_guard` checks `promo_product` status.
- **Main path:** Active promo found → guard blocks → explanation: `promo_active, promo_id=X`.
- **Dependencies:** Promotions module: `canonical_promo_product`.
- **Failure risks:** Stale promo data → false block (promo ended but data not synced). Mitigation: promo sync freshness check.
- **Uniqueness:** Cross-module guard (Pricing ↔ Promotions). Другой resolution: promo ends → next run succeeds.

### PRC-08: Guard hit — stock guard (low inventory)

- **Назначение:** Запас товара слишком низкий для автоматического ценообразования.
- **Trigger:** Guard pipeline: `stock_guard` checks `canonical_stock_snapshot`.
- **Main path:** Stock < threshold → guard blocks → explanation: `low_stock, available=X, threshold=Y`.
- **Dependencies:** `canonical_stock_snapshot`. Threshold in `price_policy`.
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
- **Trigger:** POST manual price lock (Operator/Pricing Manager). Lock stored in `canonical_offer`.
- **Main path:** Pricing run: eligibility check → locked offer → skip → no decision.
- **Dependencies:** `canonical_offer.price_locked` flag. Lock expiry (optional).
- **Failure risks:** Lock forgotten (no expiry) → offer never repriced. Mitigation: lock duration alert.
- **Uniqueness:** User-initiated override — другой actor, другой persistence (lock flag, не policy).

### PRC-11: Price policy versioning

- **Назначение:** Обновление policy создаёт новую версию, не мутирует существующую.
- **Trigger:** `PUT /api/price-policies/{id}` (PRICING_MANAGER/ADMIN).
- **Main path:** Increment version → new version active → existing actions continue with old version → next pricing run uses new version.
- **Dependencies:** `price_policy.version`, `price_policy.effective_from`.
- **Failure risks:** Race: pricing run reads policy mid-update → version check ensures consistency.
- **Uniqueness:** Versioned write — другая persistence semantics (append, не mutate).

### PRC-12: Policy assignment (bulk)

- **Назначение:** Назначение policy на группу offers (by filter criteria).
- **Trigger:** `POST /api/policy-assignments` (PRICING_MANAGER/ADMIN).
- **Main path:** Evaluate filter → UPSERT `policy_assignment` for matching offers → trigger pricing run.
- **Dependencies:** Canonical offers. Filter criteria (brand, category, price range, etc.).
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
- **Trigger:** Manual trigger с `execution_mode = SIMULATED`.
- **Main path:** Full pricing pipeline → decision → action (`execution_mode=SIMULATED`) → simulated write → shadow state.
- **Dependencies:** Simulation infrastructure (EXE-16).
- **Failure risks:** Simulation results diverge from reality (different state, timing).
- **Uniqueness:** Другой execution mode. Результаты не влияют на реальные цены.

## Индекс сценариев

| ID | Кратко |
|----|--------|
| PRC-01 | Happy path, TARGET_MARGIN |
| PRC-02 | PRICE_CORRIDOR |
| PRC-03 | Eligibility: no policy |
| PRC-04 | Eligibility: missing COGS |
| PRC-05 | Guard: stale data |
| PRC-06 | Guard: active action |
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

## Связанные документы

- Модуль: `docs/modules/pricing.md`
- Execution (actions, simulation): `docs/modules/execution.md`
- Promotions (promo guard): `docs/modules/promotions.md`
