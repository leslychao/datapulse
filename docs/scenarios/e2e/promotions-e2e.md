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
