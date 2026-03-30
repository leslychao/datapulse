# Promotions — Module Scenarios

## Роль модуля

Promotions управляет жизненным циклом промо-акций: discovery (через ETL sync), evaluation (margin & stock checks), participation decisions, execution (activate/deactivate на маркетплейсе), и координация с Pricing (promo guard).

**ClickHouse:** аналитика по промо и сравнение simulated vs actual (см. PRO-12).

## Сценарии

### PRO-01: Promo discovery (ETL sync)

- **Назначение:** Обнаружение новых промо-акций от маркетплейса.
- **Trigger:** `ETL_SYNC_COMPLETED` event (pricing worker проверяет `PROMO ∈ completed_domains`).
- **Main path:** ETL синхронизирует promo data → `canonical_promo_campaign`, `canonical_promo_product` → новые кампании и продукты обнаружены → `promo_campaign` records created/updated.
- **Dependencies:** ETL Pipeline (`PROMO_SYNC` domain). Provider promo API (Ozon: documented; WB: partially documented).
- **Failure risks:** Promo API changes → missed campaigns. Late discovery → insufficient time for evaluation.
- **Uniqueness:** Data-driven discovery (ETL), не user-initiated.

### PRO-02: Promo evaluation — happy path (margin check passes)

- **Назначение:** Автоматическая оценка промо: участие выгодно.
- **Trigger:** New `promo_product` discovered. Evaluation policy assigned.
- **Main path:** Fetch current P&L signals → compute expected margin with promo discount → margin ≥ threshold → create `promo_decision` (`PARTICIPATE`).
- **Dependencies:** P&L data (Analytics). `promo_policy` with margin threshold. COGS available.
- **Failure risks:** Stale P&L → incorrect margin calculation. Missing COGS → cannot evaluate.
- **Uniqueness:** Automated margin-based evaluation — algorithmic decision.

### PRO-03: Promo evaluation — margin check fails

- **Назначение:** Промо невыгодно — маржа ниже порога.
- **Trigger:** Same as PRO-02.
- **Main path:** `computed_margin < threshold` → create `promo_decision` (`DECLINE`) → explanation: `margin_below_threshold`, expected=X%, threshold=Y%.
- **Dependencies:** Same as PRO-02.
- **Failure risks:** Aggressive threshold → decline too many promos. Operator may override.
- **Uniqueness:** Другой business outcome (`DECLINE` vs `PARTICIPATE`). Другое объяснение.

### PRO-04: Promo evaluation — stock check fails

- **Назначение:** Недостаточно остатков для участия в промо.
- **Trigger:** Same evaluation trigger.
- **Main path:** `stock < required_minimum` for promo duration → `promo_decision` (`DECLINE`) → explanation: `insufficient_stock`.
- **Dependencies:** `canonical_stock_snapshot`. Expected promo duration.
- **Failure risks:** Stock data stale → incorrect assessment.
- **Uniqueness:** Другой signal source (stocks, не P&L). Другая причина отказа.

### PRO-05: Manual promo review (`PENDING_REVIEW`)

- **Назначение:** Промо требует ручного решения (маржа на границе, новый товар, нет данных для авто-решения).
- **Trigger:** Evaluation inconclusive (margin near threshold, missing signals) → `promo_decision` = `PENDING_REVIEW`.
- **Main path:** Decision visible in UI → operator reviews → approve (`PARTICIPATE`) or reject (`DECLINE`).
- **Dependencies:** User role: `OPERATOR` / `PRICING_MANAGER` / `ADMIN`.
- **Failure risks:** Review not done in time → promo window closes.
- **Uniqueness:** User-initiated decision — другой actor, другой audit trail (reason for override).

### PRO-06: Promo execution — Ozon activate

- **Назначение:** Активация участия в промо на Ozon.
- **Trigger:** `promo_decision` = `PARTICIPATE` → `promo_action` created.
- **Main path:** `promo_action` (`PENDING_APPROVAL` → `APPROVED` → `EXECUTING`) → call Ozon promo activate API → success → `SUCCEEDED`. SEMI_AUTO: `PENDING_APPROVAL` → operator approves → `APPROVED` → `EXECUTING`. FULL_AUTO: `PENDING_APPROVAL` → auto-approved → `APPROVED` → `EXECUTING`.
- **Dependencies:** Ozon Seller API promo endpoint. Connection `ACTIVE`. Rate limiter.
- **Failure risks:** API call fails → retry. Rate limited → backoff.
- **Uniqueness:** Write to provider — отличается от evaluation (read-only). Ozon-specific endpoint.

### PRO-07: Promo execution — Ozon deactivate

- **Назначение:** Отказ от участия в промо на Ozon.
- **Trigger:** `promo_decision` = `DECLINE` (after previously `PARTICIPATE`) или manual withdrawal.
- **Main path:** `promo_action` (deactivate) → call Ozon promo deactivate API → success → `SUCCEEDED`.
- **Dependencies:** Same as PRO-06. Active participation exists on marketplace.
- **Failure risks:** Deactivation rejected by marketplace (promo already started, cannot withdraw).
- **Uniqueness:** Reverse action — другой API endpoint, другой business meaning (выход из промо).

### PRO-08: WB promo execution (TBD)

- **Назначение:** Активация/деактивация промо на Wildberries.
- **Trigger:** `promo_decision` for WB connection.
- **Main path:** TBD — WB promo write API не задокументирован.
- **Dependencies:** WB promo API documentation (blocker).
- **Failure risks:** Blocker: cannot implement until API documented.
- **Uniqueness:** Provider-specific blocker — architectural gap. Documented in `risk-register`.

### PRO-09: Promo ↔ Pricing coordination (promo guard)

- **Назначение:** Pricing pipeline не создаёт ценовых actions для товаров в активных промо.
- **Trigger:** Pricing run: guard pipeline.
- **Main path:** `promo_guard` checks `canonical_promo_product` → active participation → pricing skipped for this offer → explanation: `promo_active`.
- **Dependencies:** `canonical_promo_product` freshness. Promo sync timing.
- **Failure risks:** Stale promo data → pricing overrides promo price. Promo ended but data not synced → unnecessary block.
- **Uniqueness:** Cross-module coordination — единственная точка, где Promotions влияет на Pricing.

### PRO-10: Promo changes after decision

- **Назначение:** Маркетплейс изменил условия промо после нашего решения.
- **Trigger:** Next `PROMO_SYNC` обнаруживает изменения в `promo_campaign` / `promo_product`.
- **Main path:** Changed conditions → re-evaluate → new `promo_decision` (may differ from original). If participation active → may need deactivation.
- **Dependencies:** Change detection in canonical UPSERT (`IS DISTINCT FROM`).
- **Failure risks:** Change not detected (only metadata changed, not business-relevant fields).
- **Uniqueness:** Re-evaluation scenario — другой trigger (data change, не initial discovery).

### PRO-11: Promo ended / expired

- **Назначение:** Промо-акция завершилась.
- **Trigger:** `PROMO_SYNC`: campaign `end_date` passed OR provider reports campaign ended.
- **Main path:** `promo_campaign.status` → `ENDED`. `promo_product` → cleanup. Pricing promo guard lifts → next pricing run can create actions for these offers.
- **Dependencies:** Promo sync freshness.
- **Failure risks:** Stale data → promo still blocks pricing after actual end.
- **Uniqueness:** Lifecycle completion — другой terminal state.

### PRO-12: Simulated promo participation

- **Назначение:** Оценка влияния промо без реального участия.
- **Trigger:** Simulation mode (Phase F).
- **Main path:** Full evaluation → simulated decision → shadow `promo_action` (не отправляется на маркетплейс) → ClickHouse analytics comparison (simulated vs actual).
- **Dependencies:** Simulation infrastructure. Historical promo data for comparison.
- **Failure risks:** Simulation divergence from reality.
- **Uniqueness:** Другой execution path (shadow, не real). Другой persistence target (в т.ч. CH для аналитики).

### PRO-13: Temporal constraint — freeze_at deadline (EXPIRED)

- **Назначение:** Акция достигла момента заморозки — изменение участия больше невозможно.
- **Trigger:** `PROMO_SYNC` обнаруживает `freeze_at` прошёл для акции с pending decisions.
- **Main path:** `canonical_promo_campaign.freeze_at < now()` → status → FROZEN. Pending `promo_decision` (PENDING_REVIEW) → auto-expired. Active `promo_action` в PENDING_APPROVAL → EXPIRED. Evaluation pipeline пропускает FROZEN campaigns.
- **Dependencies:** `freeze_at` field в `canonical_promo_campaign`. Ozon-specific (WB может не иметь `freeze_at`).
- **Failure risks:** `freeze_at` не синхронизирован вовремя → Datapulse пытается activate после заморозки → marketplace rejects. Mitigation: safety buffer перед `freeze_at`.
- **Uniqueness:** Temporal deadline — другой trigger (time-based, не data-based). Ozon-specific constraint.
