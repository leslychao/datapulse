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
- **Dependencies:** `canonical_stock_current`. Expected promo duration.
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
- **Main path:** `promo_action` created → call Ozon promo activate API → success → `SUCCEEDED`. SEMI_AUTO: action created as `PENDING_APPROVAL` → operator approves → `APPROVED` → `EXECUTING`. FULL_AUTO: action created directly as `APPROVED` → `EXECUTING` (skip `PENDING_APPROVAL`).
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

### PRO-14: MARGINAL evaluation → safety downgrade to PENDING_APPROVAL

- **Назначение:** При пограничной марже даже FULL_AUTO не активирует участие автоматически.
- **Trigger:** Evaluation result = `MARGINAL` (0 < margin < min_margin_pct) + `participation_mode = FULL_AUTO`.
- **Main path:** Evaluation → margin выше 0, но ниже threshold → `MARGINAL` → decision = `PENDING_REVIEW`. Action created as `PENDING_APPROVAL` (не APPROVED, несмотря на FULL_AUTO) → оператор принимает решение вручную.
- **Dependencies:** `promo_policy.min_margin_pct`. P&L signals для margin computation. Evaluation → Decision mapping table.
- **Failure risks:** Operator не заметил pending review → promo window closes → missed opportunity. Threshold слишком строгий → слишком много MARGINAL → operator overload.
- **Uniqueness:** Safety downgrade — единственный случай, когда FULL_AUTO **не** создаёт action в APPROVED. Аналог PRC-21 (safety gate): система защищает от пограничных решений.

### PRO-15: INSUFFICIENT_DATA evaluation (missing signals)

- **Назначение:** Невозможно оценить промо из-за отсутствия критических данных.
- **Trigger:** Evaluation: COGS не задан (cost_profile отсутствует) или critical signal missing (CH unavailable, no sales history).
- **Main path:** Signal assembly → COGS = NULL или CH signals unavailable → `evaluation_result = INSUFFICIENT_DATA` → decision = `PENDING_REVIEW` → explanation: `insufficient_data, missing=[cogs]` или `missing=[commission_rate, logistics_cost]`.
- **Dependencies:** `cost_profile` (SCD2). ClickHouse signals (avg_commission_pct, avg_logistics_per_unit). `fact_sales` (velocity).
- **Failure risks:** Mass INSUFFICIENT_DATA (COGS не импортирован для большинства SKU) → все промо уходят в PENDING_REVIEW → operator overload. Recovery: bulk import cost_profiles (ETL-15) → re-evaluate.
- **Uniqueness:** Другая причина отказа от auto-decision (missing data, не маржа и не остатки). Другой recovery path (импорт данных, не ожидание sync).

### PRO-16: Promo reconciliation via PROMO_SYNC

- **Назначение:** Верификация фактического participation status после выполнения promo_action.
- **Trigger:** Следующий `PROMO_SYNC` после promo_action SUCCEEDED.
- **Main path:** `promo_action` SUCCEEDED (activate) → следующий PROMO_SYNC → `canonical_promo_product.participation_status` обновлён из marketplace → compare: expected = PARTICIPATING, actual = PARTICIPATING → OK. Или actual = ELIGIBLE/BANNED → расхождение → alert в Promo Journal + `alert_event` (MISMATCH type).
- **Dependencies:** PROMO_SYNC freshness. `canonical_promo_product.participation_status`. Previous promo_action outcome.
- **Failure risks:** PROMO_SYNC delayed → reconciliation delayed. Marketplace processed activate but status not yet reflected in API → temporary mismatch (false positive). Marketplace rejected activate silently → genuine mismatch.
- **Uniqueness:** Reconciliation через sync (не через отдельный re-read как в Execution). Другой timing (next sync cycle, не immediate/deferred), другой mechanism (canonical UPSERT detects change).

### PRO-17: Policy change triggers re-evaluation

- **Назначение:** Изменение `promo_policy` вызывает повторную оценку активных промо-кампаний.
- **Trigger:** UPDATE `promo_policy` → `version` инкрементирован (изменились поля: `participation_mode`, `min_margin_pct`, `max_promo_discount_pct`, `auto_participate_categories`, `auto_decline_categories`).
- **Main path:** Policy updated → event `PROMO_POLICY_CHANGED` → pricing-worker проверяет: есть ли активные кампании (`status = ACTIVE`) для connections, привязанных к этой policy? → Да → `PROMO_EVALUATION_EXECUTE` outbox event → evaluation batch для всех promo_products в активных кампаниях → новые `promo_decision` создаются с новой `policy_version` и `policy_snapshot`.
- **Dependencies:** `promo_policy` version tracking. Connection → policy binding. Active `canonical_promo_campaign` с `status = ACTIVE`.
- **Failure risks:** Large number of active campaigns → evaluation batch too large → timeout. Mitigation: chunked processing per campaign. Policy changed during evaluation → stale snapshot → inconsistency. Mitigation: `policy_version` captured at start.
- **Uniqueness:** Другой trigger (policy change, не ETL sync). Другой actor (admin/manager, не system). Может привести к reversal: ранее `PARTICIPATE` → теперь `DECLINE` (новый порог выше) → deactivate action needed.

### PRO-18: Manual participate / decline outside evaluation cycle

- **Назначение:** Оператор вручную принимает решение об участии/отказе для конкретного товара, минуя evaluation pipeline.
- **Trigger:** UI action: оператор нажимает «Участвовать» / «Отказаться» для конкретного `promo_product`.
- **Main path (participate):** Оператор выбирает товар → указывает target_promo_price (опционально; default = marketplace-suggested) → API `POST /api/promo/products/{promoProductId}/participate` → создаётся `promo_decision (PARTICIPATE, decision_source = MANUAL)` + `promo_action (APPROVED, action_type = ACTIVATE)` → outbox → execution → marketplace API.
- **Main path (decline):** Аналогично, но `POST /api/promo/products/{promoProductId}/decline` → `promo_decision (DECLINE, decision_source = MANUAL)`. Если товар уже PARTICIPATING → `promo_action (APPROVED, action_type = DEACTIVATE)`.
- **Dependencies:** `canonical_promo_product` в состоянии, допускающем ручное решение (ELIGIBLE, PENDING_REVIEW, или уже PARTICIPATING для decline). `promo_campaign.status = ACTIVE` (не ENDED/FROZEN).
- **Failure risks:** Оператор участвует в промо с отрицательной маржой (system allows — manual override). Mitigation: UI warning с расчётной маржой. Campaign FROZEN после нажатия → marketplace rejects → FAILED + alert.
- **Uniqueness:** Bypass evaluation — единственный путь, где decision создаётся без evaluation record. Аналог PRC-05 (manual override) в Pricing. Audit trail: `decision_source = MANUAL` + operator user_id.
