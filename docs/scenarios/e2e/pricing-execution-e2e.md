# Pricing → Execution E2E Scenarios

## Бизнес-контекст

Главный бизнес-поток системы: от принятия ценового решения до подтверждённого изменения цены на маркетплейсе. Проходит через Pricing → Execution → Integration → Provider.

## Сценарии

### E2E-PE-01: Full auto pricing — from sync to confirmed price change

- **Business goal:** Полностью автоматическое ценообразование: новые данные → решение → исполнение → подтверждение.
- **Участвующие модули:** ETL → Pricing → Execution → Integration → Audit & Alerting.
- **Основной поток:**
  1. **ETL:** `ETL_SYNC_COMPLETED (FINANCE ∈ completed_domains)` → outbox event.
  2. **Pricing:** Event consumed → pricing run → eligibility (policy assigned, COGS available) → signal assembly (canonical + CH) → TARGET_MARGIN strategy → constraints → guards (all pass) → decision → action created (`execution_mode=FULL_AUTO`, `status=APPROVED`).
  3. **Execution:** Action picked up → `SCHEDULED` → `EXECUTING` → call provider write API via Integration → HTTP 200 → `RECONCILIATION_PENDING` → immediate reconciliation (read current price) → price matches → `SUCCEEDED`.
  4. **Audit:** Decision logged. Action attempts logged. Price change recorded in price journal.
  5. **Seller Ops:** Grid updated (new current price visible).
- **Ключевые зависимости:** Fresh data (ETL completed). COGS available. ClickHouse signals fresh. Connection ACTIVE. Provider API available.
- **Failure paths:**
  - Guard blocks (stale data, active action, promo active) → no action created.
  - Provider write fails (5xx) → retry loop → eventual success or `FAILED`.
  - Reconciliation fails → deferred reconciliation or manual.
  - Provider accepted but price not changed (business rejection) → `FAILED`.
- **Почему обязательный:** Это главный revenue-driving flow. Если он не работает, система бессмысленна.

### E2E-PE-02: Semi-auto pricing — with approval step

- **Business goal:** Ценообразование с ручным одобрением перед исполнением.
- **Участвующие модули:** ETL → Pricing → Execution → Seller Ops (UI) → Integration → Audit.
- **Основной поток:**
  1. **Pricing:** Decision → action (`execution_mode=SEMI_AUTO`, `status=PENDING_APPROVAL`).
  2. **Seller Ops:** Pricing Manager видит pending action в UI → reviews price, margin, explanation.
  3. **Execution:** User approves → `APPROVED` → `SCHEDULED` → `EXECUTING` → provider write → `SUCCEEDED`.
  4. Или: User rejects → `REJECTED` (terminal). Explanation logged.
- **Ключевые зависимости:** User available for approval. Approval timeout (72h default).
- **Failure paths:**
  - No approval within timeout → `EXPIRED`.
  - Approve but underlying data changed → execution с устаревшим решением. Mitigation: re-check guard at execution time.
  - Concurrent approve + reject → CAS single winner.
- **Почему обязательный:** Основной safe-mode flow. Пользователи начинают с `SEMI_AUTO` перед переходом на `FULL_AUTO`.

### E2E-PE-03: Supersede — new decision replaces pending action

- **Business goal:** Новые данные делают старое решение неактуальным → замена action.
- **Участвующие модули:** ETL → Pricing → Execution.
- **Основной поток:**
  1. **Pricing run #1:** Decision → action #1 (`PENDING_APPROVAL` or `APPROVED`).
  2. **ETL:** New `PRICES_SYNC` (updated data).
  3. **Pricing run #2:** New decision → action #2 for same offer → supersede policy.
  4. **Execution:** Immediate supersede: action #1 → `SUPERSEDED`, action #2 becomes active. Или deferred: action #1 continues, #2 queued.
- **Ключевые зависимости:** Partial unique index (one active per offer). Supersede policy config.
- **Failure paths:**
  - Action #1 already `EXECUTING` → deferred supersede (не прерывать mid-write).
  - CAS conflict → retry supersede.
- **Почему обязательный:** В реальной системе данные меняются между решениями. Supersede — критический для предотвращения устаревших price changes.

### E2E-PE-04: Provider write with uncertain result → reconciliation chain

- **Business goal:** Обработка неопределённого результата записи.
- **Участвующие модули:** Execution → Integration → Audit & Alerting → Seller Ops.
- **Основной поток:**
  1. **Execution:** `EXECUTING` → provider write → timeout after send.
  2. **Execution:** `RECONCILIATION_PENDING` (не retry, т.к. side effect мог произойти).
  3. **Execution:** Immediate reconciliation attempt → read current price → match → `SUCCEEDED`. Или не match → deferred reconciliation.
  4. **Deferred:** Scheduled job → read again after delay → match → `SUCCEEDED`. Или still не match → `FAILED` или escalate.
  5. **Audit:** Alert if reconciliation exhausted. Mismatch monitor updated.
  6. **Seller Ops:** Manual reconciliation option available.
- **Ключевые зависимости:** Reconciliation read API. Timing (marketplace processing delay).
- **Failure paths:**
  - Reconciliation read also fails → remains `RECONCILIATION_PENDING` → stuck-state detector → alert.
  - Price changed by external actor → false reconciliation result.
- **Почему обязательный:** Uncertain outcome — самый опасный edge case. Неправильная обработка → double write или потеря confirmed change.

### E2E-PE-05: Pricing simulation end-to-end

- **Business goal:** Тестирование pricing strategy без реального воздействия на маркетплейс.
- **Участвующие модули:** Pricing → Execution (simulated) → Analytics (comparison).
- **Основной поток:**
  1. **Pricing:** Manual trigger (`execution_mode=SIMULATED`) → full pipeline → decision → action (`SIMULATED`).
  2. **Execution:** `SimulatedPriceActionGateway` → write to `simulated_offer_state` (shadow table). Reconciliation: compare with shadow state → `SUCCEEDED`.
  3. **Analytics:** Compare simulated decisions vs actual marketplace state. Calculate hypothetical P&L impact.
- **Ключевые зависимости:** Simulation infrastructure. Shadow state isolation (separate partial unique index).
- **Failure paths:**
  - Shadow state diverges from reality → simulation results unreliable.
  - Simulation affects live pipeline → isolation violation (design invariant: must not happen).
- **Почему обязательный:** Позволяет безопасно тестировать strategies. Предшествует переводу на `FULL_AUTO`.
