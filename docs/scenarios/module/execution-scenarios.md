# Execution — Module Scenarios

## Роль модуля

Execution отвечает за жизненный цикл `price_action` — от создания до подтверждённого результата. Управляет state machine (11 состояний, 34 перехода), transactional outbox, CAS guards, retry с backoff, reconciliation, и simulation mode.

## Сценарии

### EXE-01: Создание price_action (happy path)

- **Назначение:** Pricing pipeline создаёт action на исполнение после принятия решения.
- **Trigger:** `PRICING_DECISION_MADE` outbox event.
- **Main path:** Event consumed → validate decision → INSERT `price_action` (status=PENDING_APPROVAL или APPROVED, зависит от execution mode) → INSERT `outbox_event` → ACK.
- **Dependencies:** `pricing_decision` существует и валиден. `marketplace_offer_id` уникален среди active actions (partial unique index).
- **Failure risks:** Duplicate event delivery → idempotent check по `decision_id`. Unique constraint violation → action уже создан.
- **Uniqueness:** Единственная точка входа в state machine Execution. Отдельный контракт (event-driven, не REST).

### EXE-02: Approval flow (SEMI_AUTO mode)

- **Назначение:** Ручное подтверждение price action перед исполнением.
- **Trigger:** User action: POST approve/reject.
- **Main path:** PENDING_APPROVAL → APPROVED (approve) или REJECTED (reject). CAS guard: `UPDATE WHERE status = 'PENDING_APPROVAL'`.
- **Dependencies:** User role: PRICING_MANAGER или ADMIN. Action существует и в PENDING_APPROVAL.
- **Failure risks:** Concurrent approve+reject → CAS обеспечивает single winner. Action expired while user deciding → EXPIRED (timeout job).
- **Uniqueness:** User-initiated state transition (в отличие от system-driven). Другой actor, другой audit requirement.

### EXE-03: Approval timeout (PENDING_APPROVAL → EXPIRED)

- **Назначение:** Автоматическое истечение actions, не подтверждённых вовремя.
- **Trigger:** Scheduled job (hourly).
- **Main path:** SELECT actions WHERE status = PENDING_APPROVAL AND created_at + timeout < now() → CAS UPDATE → EXPIRED.
- **Dependencies:** `price_policy.approval_timeout_hours` (default: 72h).
- **Failure risks:** Clock skew → minimal risk (hourly granularity). Concurrent approval + timeout → CAS single winner.
- **Uniqueness:** Scheduler-driven, not user-driven. Terminal state, no recovery.

### EXE-04: Scheduling (APPROVED → SCHEDULED)

- **Назначение:** Запланировать action на исполнение в определённое время или немедленно.
- **Trigger:** Approval event или auto-approval (FULL_AUTO mode).
- **Main path:** APPROVED → SCHEDULED (если scheduled_at в будущем) или APPROVED → EXECUTING (если immediate). CAS guard.
- **Dependencies:** `scheduled_at` field. Scheduler job для deferred execution.
- **Failure risks:** Schedule в прошлое → execute immediately. Scheduler lag → delayed execution (bounded by poll interval).
- **Uniqueness:** Временной scheduling — отдельная ветка state machine.

### EXE-05: Provider write — immediate success

- **Назначение:** Отправка price change на маркетплейс, провайдер подтвердил.
- **Trigger:** Executor worker picks up SCHEDULED/EXECUTING action.
- **Main path:** EXECUTING → call provider write API → HTTP 200 → INSERT `price_action_attempt` (success) → RECONCILIATION_PENDING (для immediate reconciliation) или SUCCEEDED (если reconciliation не требуется).
- **Dependencies:** Integration module (write adapter, credentials from Vault). Rate limiter.
- **Failure risks:** Vault unavailable → attempt fails → retry. Rate limited → backoff → retry.
- **Uniqueness:** Happy path write — baseline для всех failure variants.

### EXE-06: Provider write — transient failure (5xx / timeout)

- **Назначение:** Провайдер временно недоступен.
- **Trigger:** HTTP 5xx или connection timeout при write.
- **Main path:** EXECUTING → attempt failed → INSERT `price_action_attempt` (error) → increment attempt_count → check max_attempts → RETRY_SCHEDULED (backoff) → EXECUTING (retry).
- **Dependencies:** Retry policy: max_attempts, min/max backoff, exponential growth. PostgreSQL — retry truth.
- **Failure risks:** Retry exhaustion → FAILED (terminal). Backoff too aggressive → provider still down.
- **Uniqueness:** Retry loop — отдельный failure/recovery path с собственной state machine веткой (RETRY_SCHEDULED).

### EXE-07: Provider write — terminal failure (4xx, business rejection)

- **Назначение:** Провайдер отклонил запрос (невалидная цена, товар не существует).
- **Trigger:** HTTP 4xx (кроме 429).
- **Main path:** EXECUTING → attempt failed → FAILED (terminal, no retry). Error details в `price_action_attempt`.
- **Dependencies:** Provider error code parsing.
- **Failure risks:** Misclassification: transient error classified as terminal → lost action. Defensive: only known terminal codes → FAILED; unknown → retry.
- **Uniqueness:** Terminal failure path — другой business outcome (no retry), другой recovery (manual investigation).

### EXE-08: Provider write — uncertain result

- **Назначение:** Не удаётся определить, применилась ли цена (timeout после send, ambiguous response).
- **Trigger:** Timeout после отправки запроса. Или provider response неоднозначен.
- **Main path:** EXECUTING → RECONCILIATION_PENDING. Не retry (side effect мог произойти).
- **Dependencies:** Reconciliation mechanism (immediate или deferred).
- **Failure risks:** Dangerous: если классифицировать как failure и retry → double write. Поэтому uncertain = RECONCILIATION_PENDING, не FAILED.
- **Uniqueness:** Фундаментально отличается от success и failure — третья ветка с другой семантикой (may or may not have happened).

### EXE-09: Immediate reconciliation

- **Назначение:** Проверить фактическое состояние цены на маркетплейсе сразу после write.
- **Trigger:** Action переходит в RECONCILIATION_PENDING.
- **Main path:** Read current price from provider → compare with expected → match → SUCCEEDED. Не match → FAILED или RETRY_SCHEDULED.
- **Dependencies:** Integration read adapter. Price comparison logic (с учётом rounding, marketplace processing delays).
- **Failure risks:** Provider read fails → deferred reconciliation. Price не обновилась из-за marketplace processing delay → false negative.
- **Uniqueness:** Immediate verification — другой trigger (right after write), другой timing.

### EXE-10: Deferred reconciliation

- **Назначение:** Отложенная проверка для случаев, где immediate reconciliation неприменим или failed.
- **Trigger:** Scheduled job обрабатывает RECONCILIATION_PENDING actions с deferred strategy.
- **Main path:** Batch: SELECT RECONCILIATION_PENDING → для каждого: read provider price → compare → SUCCEEDED/FAILED.
- **Dependencies:** `deferred_action` table. Configurable delay (default: 15 min). Max reconciliation attempts.
- **Failure risks:** Price changed by someone else between write and check → false positive reconciliation failure. Stale marketplace data → false negative.
- **Uniqueness:** Batch processing, delayed timing, отдельная таблица (`deferred_action`), другой scheduled trigger.

### EXE-11: Manual reconciliation

- **Назначение:** Оператор вручную разрешает RECONCILIATION_PENDING action.
- **Trigger:** User action: POST force-succeed или force-fail.
- **Main path:** RECONCILIATION_PENDING → SUCCEEDED или FAILED (manual override). CAS guard. Audit log entry.
- **Dependencies:** User role: PRICING_MANAGER или ADMIN. Reason field обязателен.
- **Failure risks:** Incorrect manual decision (user marks SUCCEEDED but price didn't change).
- **Uniqueness:** User-initiated terminal transition. Другой actor, другой audit trail (reason required).

### EXE-12: Cancel action

- **Назначение:** Отмена action до его исполнения.
- **Trigger:** User action: POST cancel. Или system: supersede.
- **Main path:** PENDING_APPROVAL/APPROVED/SCHEDULED → CANCELLED. CAS guard.
- **Dependencies:** Action must be in pre-execution state.
- **Failure risks:** Race: cancel + execute concurrent → CAS single winner.
- **Uniqueness:** User-driven cancellation — отдельный terminal path.

### EXE-13: Supersede (new action replaces existing)

- **Назначение:** Новое pricing decision для того же offer отменяет предыдущий active action.
- **Trigger:** New `price_action` INSERT для `marketplace_offer_id` с existing active action.
- **Main path:** Immediate policy: CAS old action → SUPERSEDED. Deferred policy: old action continues, new becomes active after old completes/fails.
- **Dependencies:** Partial unique index (one active action per offer). Supersede policy configuration.
- **Failure risks:** CAS conflict on supersede → retry. Old action already EXECUTING → deferred supersede (не прерывать mid-write).
- **Uniqueness:** Два policy (immediate/deferred), конфликт двух actions — уникальная reconciliation semantics.

### EXE-14: Hold / Resume

- **Назначение:** Приостановка и возобновление action (оператор хочет придержать).
- **Trigger:** User action: POST hold / POST resume.
- **Main path:** APPROVED/SCHEDULED → ON_HOLD (hold). ON_HOLD → APPROVED/SCHEDULED (resume). CAS guard.
- **Dependencies:** User role. ON_HOLD timer (optional: auto-resume after X hours).
- **Failure risks:** Action expires while on hold. Held action's underlying data becomes stale.
- **Uniqueness:** Bidirectional transition (hold ↔ resume) — уникальная ветка state machine, не terminal.

### EXE-15: Stuck-state detection

- **Назначение:** Обнаружение actions, застрявших в non-terminal state без прогресса.
- **Trigger:** Scheduled job (every 10 min).
- **Main path:** SELECT actions WHERE status IN (EXECUTING, RECONCILIATION_PENDING) AND updated_at < now() - threshold → RECONCILIATION_PENDING или FAILED.
- **Dependencies:** Threshold configuration per state. Alert emission.
- **Failure risks:** Aggressive threshold → false positives (action still processing). Too lenient → genuine stuck not detected.
- **Uniqueness:** Scheduled safety net — обнаружение аномалий, не нормальный flow.

### EXE-16: Simulation mode (SIMULATED execution)

- **Назначение:** Полный pricing+execution pipeline без реальной записи на маркетплейс.
- **Trigger:** `execution_mode = SIMULATED` в pricing run.
- **Main path:** Та же state machine, но вместо provider write → запись в `simulated_offer_state` (shadow table). Reconciliation: сравнение с shadow state (always match).
- **Dependencies:** `SimulatedPriceActionGateway`. Отдельный partial unique index для simulated actions.
- **Failure risks:** Simulation drift: shadow state diverges from real marketplace state over time.
- **Uniqueness:** Другой gateway (simulated vs real), другой persistence target, другой partial unique index. Изолирован от live pipeline.

### EXE-17: Outbox publisher — event delivery

- **Назначение:** Доставка outbox events из PostgreSQL в RabbitMQ.
- **Trigger:** Outbox publisher polling (configurable interval).
- **Main path:** SELECT pending events → publish to RabbitMQ → UPDATE status = SENT → ACK.
- **Dependencies:** RabbitMQ availability. Idempotent consumers.
- **Failure risks:** RabbitMQ down → events accumulate in outbox (bounded table growth). Publisher crash after publish but before DB update → duplicate delivery (idempotent consumer handles).
- **Uniqueness:** Infrastructure concern, не business logic. Отдельный failure path (messaging infrastructure vs provider).

### EXE-18: DLX retry (dead-letter exchange)

- **Назначение:** RabbitMQ-level retry для messages, которые consumer не смог обработать.
- **Trigger:** Consumer NACK / processing exception.
- **Main path:** Message → DLX → TTL delay → re-queue → retry. After max DLX retries → dead-letter permanently → alert.
- **Dependencies:** RabbitMQ DLX configuration. Max retry count. Dead-letter monitoring.
- **Failure risks:** Poison message (always fails) → retry exhaustion → dead-letter → manual investigation.
- **Uniqueness:** RabbitMQ-level retry (отличается от application-level retry в EXE-06).
