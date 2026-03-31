# Promotions E2E Scenarios

## Бизнес-контекст

Полный цикл промо-акции: от обнаружения на маркетплейсе через оценку и решение до активации и координации с ценообразованием.

## Сценарии

### E2E-PR-01: Promo discovery → evaluation → participation → pricing coordination

- **Business goal:** Промо-акция обнаружена, оценена, решение принято, участие активировано, pricing учитывает промо.
- **Участвующие модули:** ETL (promo sync) → Promotions → Integration (write) → Pricing → Seller Ops → Audit.
- **Основной поток:**
  1. **ETL:** `PROMO_SYNC` → `canonical_promo_campaign` / `canonical_promo_product` created → `ETL_SYNC_COMPLETED (PROMO ∈ completed_domains)` event.
  2. **Promotions:** `ETL_SYNC_COMPLETED (PROMO ∈ completed_domains)` consumed → new `promo_product` detected → evaluation triggered → margin check (P&L signals from Analytics) → margin OK + stock OK → `promo_decision` = `PARTICIPATE`.
  3. **Promotions:** `promo_action` created → `EXECUTING` → call Ozon promo activate API → `SUCCEEDED`.
  4. **Pricing:** Next pricing run for this offer → `promo_guard` detects active promo → pricing skipped → explanation: `"promo_active"`.
  5. **Seller Ops:** Offer marked as "in promo" in grid. Promo journal shows decision and action.
  6. **Audit:** Promo decision and action logged.
- **Ключевые зависимости:** Promo API available (Ozon). P&L data for margin evaluation. Stock data. Pricing promo guard.
- **Failure paths:**
  - Evaluation fails (no COGS / stale P&L) → `PENDING_REVIEW` → manual decision.
  - Activate API fails → retry → `FAILED` → alert.
  - Promo guard stale data → pricing runs despite promo → price conflict.
  - WB promo → write API TBD → participation not possible (blocker).
- **Почему обязательный:** Полный promo lifecycle. Координация с Pricing — критична для предотвращения price conflicts.

### E2E-PR-02: Promo ends → pricing resumes

- **Business goal:** После окончания промо, pricing автоматически возобновляется для затронутых offers.
- **Участвующие модули:** ETL (promo sync) → Promotions → Pricing → Seller Ops.
- **Основной поток:**
  1. **ETL:** `PROMO_SYNC` → `promo_campaign.end_date` passed / status = `ENDED` → canonical update.
  2. **Promotions:** `promo_product` status → `ENDED`. No active participation.
  3. **Pricing:** Next pricing run → `promo_guard` checks → no active promo → guard passes → pricing resumes for this offer → new decision generated.
  4. **Seller Ops:** Offer no longer marked as "in promo". Price journal shows new decision after promo.
- **Ключевые зависимости:** Promo sync freshness (timely detection of promo end). Promo guard data freshness.
- **Failure paths:**
  - Promo ended but sync delayed → promo guard still blocks → pricing gap.
  - Marketplace extends promo without notification → our data says ended, promo still active → price conflict.
- **Почему обязательный:** Ensures continuity: promo block is temporary, не permanent.

### E2E-PR-03: Promo conditions changed → re-evaluation → possible withdrawal

- **Business goal:** Маркетплейс изменил условия промо после нашего решения участвовать.
- **Участвующие модули:** ETL → Promotions → Integration (write) → Pricing → Audit.
- **Основной поток:**
  1. **ETL:** `PROMO_SYNC` → canonical update detects changed conditions (discount increased, duration extended).
  2. **Promotions:** Change detected (`IS DISTINCT FROM`) → re-evaluation triggered → new margin check.
  3. **If margin now below threshold:** New `promo_decision` = `DECLINE` → `promo_action` (deactivate) → Ozon deactivate API → `SUCCEEDED`. Promo guard lifted → pricing resumes.
  4. **If margin still OK:** No action needed. Existing participation continues.
  5. **Audit:** Decision change logged with reason.
- **Ключевые зависимости:** Change detection in canonical UPSERT. Re-evaluation policy. Deactivation API available.
- **Failure paths:**
  - Deactivation rejected by marketplace (too late, promo already started) → forced participation → accept.
  - Rapid condition changes → re-evaluation oscillation. Mitigation: cooldown period.
- **Почему обязательный:** Prevents participation in loss-making promos due to changed conditions.

### E2E-PR-04: Simulated promo participation → comparison → transition to live

- **Business goal:** Оператор оценивает, как повлияло бы участие в промо, не участвуя реально. Затем переходит на live-режим.
- **Участвующие модули:** ETL (promo sync) → Promotions (simulated) → Analytics (ClickHouse) → Seller Ops (UI) → Promotions (live) → Integration (write) → Pricing.
- **Основной поток:**
  1. **Setup:** `promo_policy.participation_mode = SIMULATED` для connection.
  2. **ETL:** `PROMO_SYNC` → new promo campaigns discovered → `canonical_promo_campaign` / `canonical_promo_product` created.
  3. **Promotions (simulated):** `PROMO_EVALUATION_EXECUTE` → evaluation batch → margin & stock checks → decisions created as usual → `promo_action` created with `execution_mode = SIMULATED`, `status = APPROVED`. No marketplace API call. Action transitions directly to `SUCCEEDED` (simulated).
  4. **Analytics:** `promo_action` (simulated) materialized в ClickHouse → `fact_promo_product` с `execution_mode = SIMULATED`. `mart_promo_product_analysis` available for comparison.
  5. **Seller Ops (UI):** Оператор видит dashboard: simulated decisions рядом с actual marketplace results. Comparison: «если бы участвовали → ожидаемая маржа X%, фактическая маржа без промо Y%». Товары с positive uplift highlighted.
  6. **Transition:** Оператор убеждён → обновляет `promo_policy.participation_mode` с `SIMULATED` на `SEMI_AUTO` или `FULL_AUTO`.
  7. **Promotions (live):** Policy change triggers re-evaluation (PRO-17) → new evaluation batch → теперь `promo_action` создаётся с `execution_mode = LIVE` → outbox → marketplace API → activate.
  8. **Pricing:** `promo_guard` обнаруживает активное участие → pricing skipped.
- **Ключевые зависимости:** `SIMULATED` в `participation_mode` enum. `execution_mode` propagation from policy to action. ClickHouse simulated-vs-actual analytics. Policy change re-evaluation trigger (PRO-17).
- **Failure paths:**
  - Simulated actions accumulated, transition delayed → stale simulations no longer relevant (promo ended). Mitigation: UI shows only active campaigns.
  - Transition to live during campaign `freeze_at` → marketplace rejects activate → FAILED. Mitigation: UI warning if `freeze_at` approaching.
  - Simulated margin diverges from reality (COGS changed, marketplace commission changed between simulation and live). Mitigation: re-evaluation at transition time uses fresh signals.
- **Почему обязательный:** Единственный E2E path для `SIMULATED` mode — ключевая business capability Phase F. Демонстрирует: discovery → evaluation → shadow execution → analytics comparison → confidence building → live transition. Аналог E2E-PE-04 (simulated pricing).
