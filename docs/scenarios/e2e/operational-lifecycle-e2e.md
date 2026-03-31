# Operational Lifecycle E2E Scenarios

## Бизнес-контекст

Ежедневный рабочий цикл продавца / оператора: от обзора ситуации через принятие решений до проверки результатов.

## Сценарии

### E2E-OL-01: Daily operator workflow

- **Business goal:** Оператор начинает рабочий день и обрабатывает все требующие внимания элементы.
- **Участвующие модули:** Tenancy (login) → Seller Ops (grid, queues, alerts) → Pricing (review decisions) → Execution (approve/reject) → Audit (history).
- **Основной поток:**
  1. **Login:** Operator authenticates (Keycloak) → workspace selected → JWT with roles.
  2. **Dashboard/Grid:** Open operational grid → see overview: total items, pending actions, active alerts, mismatches.
  3. **Alerts review:** Check active alerts → acknowledge critical ones → investigate stale data or residual anomalies.
  4. **Pending actions:** Open working queue → review PENDING_APPROVAL price actions → for each: review old price, new price, margin, explanation → approve or reject.
  5. **Mismatch review:** Open mismatch monitor → review price discrepancies → decide: accept, re-price, or investigate.
  6. **Promo review:** Check PENDING_REVIEW promo decisions → approve participation or decline.
  7. **End of day:** Verify: all queues processed, alerts addressed, no stuck actions.
- **Ключевые зависимости:** All modules operational. Data fresh. UI responsive.
- **Failure paths:**
  - Login failure (Keycloak down) → cannot access system. Mitigation: Keycloak HA (not Phase A).
  - Grid slow (large dataset + complex filters) → degraded UX. Mitigation: pagination, index optimization.
  - Approval made on stale data → check at execution time mitigates.
- **Почему обязательный:** Основной user journey. Если этот flow сломан, система непригодна для ежедневной работы.

### E2E-OL-02: Investigation flow — from alert to root cause

- **Business goal:** Расследование проблемы от alert до первопричины.
- **Участвующие модули:** Audit & Alerting → Seller Ops → Analytics → ETL → Integration.
- **Основной поток:**
  1. **Alert received:** WebSocket notification → operator clicks alert.
  2. **Alert details:** Type (STALE_DATA / RESIDUAL_ANOMALY / MISMATCH / ACTION_FAILED), affected connection, severity.
  3. **Drill-down path (varies by type):**
     - **STALE_DATA:** Check sync state → last sync time → connection health → call log → identify provider issue.
     - **RESIDUAL_ANOMALY:** P&L breakdown → identify discrepant component → data provenance: mart → fact → canonical → raw → provider response.
     - **MISMATCH:** Price journal → last action → execution log → reconciliation result.
     - **ACTION_FAILED:** Execution attempt log → provider response → error code → root cause.
  4. **Resolution:** Fix root cause (update credentials, re-sync, manual reconciliation, etc.) → resolve alert.
- **Ключевые зависимости:** Data provenance chain intact. Call log available. Raw data preserved (retention).
- **Failure paths:**
  - Provenance chain broken (missing job_execution_id, expired raw) → investigation dead-end.
  - Root cause external (provider bug, undocumented behavior) → cannot fix, must document and accept.
- **Почему обязательный:** Debugging capability. Без investigation flow оператор не может решить проблемы, только наблюдать их.

### E2E-OL-03: Onboarding — new workspace and first marketplace

- **Business goal:** Новый клиент настраивает workspace и подключает первый маркетплейс.
- **Участвующие модули:** Tenancy → Integration → ETL → Analytics → Pricing → Seller Ops.
- **Основной поток:**
  1. **Tenancy:** User registers → create workspace → becomes OWNER.
  2. **Integration:** Create first marketplace connection (WB or Ozon) → credentials validated → ACTIVE.
  3. **ETL:** First FULL_SYNC runs → catalog, prices, stocks, orders, finance loaded.
  4. **Analytics:** Materialization → ClickHouse populated → P&L computed (limited history).
  5. **Pricing:** OWNER sets up first price_policy → assigns to offers → first pricing run → recommendations generated (RECOMMENDATION mode).
  6. **Seller Ops:** Grid shows data. User explores, learns system.
  7. **Scaling:** Invite team members (ANALYST, OPERATOR) → configure saved views and queues.
  8. **Confidence building:** Review recommendations for a week → switch to SEMI_AUTO → start approving → eventually FULL_AUTO.
- **Ключевые зависимости:** All modules operational. Smooth onboarding UX. Clear error messages.
- **Failure paths:**
  - Invalid credentials → AUTH_FAILED → user must fix and retry.
  - Large catalog → long first sync → user waits (progress indicator needed).
  - Missing COGS → P&L incomplete → user must import cost profiles.
  - No policy → no pricing decisions → user must configure.
- **Почему обязательный:** User acquisition flow. Determines first impression and time-to-value.

### E2E-OL-04: Multi-marketplace partial failure — lane isolation

- **Business goal:** Сбой одного маркетплейса не влияет на работу другого.
- **Участвующие модули:** Integration (lane isolation) → ETL (partial sync) → Audit & Alerting (selective alert) → Pricing (selective block) → Seller Ops (mixed freshness).
- **Основной поток:**
  1. **Integration:** WB connection → AUTH_FAILED (credentials expired). Ozon connection → ACTIVE.
  2. **ETL:** WB syncs paused (no new data). Ozon syncs continue normally → `ETL_SYNC_COMPLETED` events for Ozon only.
  3. **Audit & Alerting:** STALE_DATA alert for WB connection (blocks_automation = true). No alert for Ozon.
  4. **Pricing:** Pricing run → check blocking alerts → WB connection blocked (stale data) → skip WB offers. Ozon offers → full pipeline → decisions + actions.
  5. **Seller Ops:** Grid shows mixed state: WB offers with stale indicator + no recent decisions. Ozon offers with fresh data + active pricing.
  6. **Resolution:** User updates WB credentials → AUTH_FAILED → ACTIVE → syncs resume → stale data alert AUTO_RESOLVED → WB pricing unblocked.
- **Ключевые зависимости:** Per-connection alert scoping. Per-connection pricing block. Lane isolation in ETL.
- **Failure paths:**
  - Shared infrastructure failure (PostgreSQL, Vault) → both marketplaces affected (не lane-specific).
  - WB stale data persists → WB offers never repriced → operator must investigate.
  - Ozon rate limited simultaneously → both degraded but independently.
- **Почему обязательный:** Validates lane isolation end-to-end. Real-world scenario: маркетплейсы ломаются независимо.
