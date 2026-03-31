# Execution — Module Scenarios

## Роль модуля

Execution отвечает за жизненный цикл `price_action` — от создания до подтверждённого результата. Управляет state machine (11 состояний, 34 перехода), transactional outbox, CAS guards, retry с backoff, reconciliation, и simulation mode.

## Сценарии

### EXE-01: Создание price_action (happy path)

- **Назначение:** Pricing pipeline создаёт action на исполнение после принятия решения.
- **Trigger:** Pricing pipeline создаёт action в той же транзакции, что и decision (decision + action + outbox INSERT в одном `@Transactional`).
- **Main path:** Validate decision → INSERT `price_action` (status=PENDING_APPROVAL или APPROVED, зависит от execution mode) → INSERT `outbox_event` — всё в одной транзакции с decision.
- **Dependencies:** `pricing_decision` существует и валиден. `marketplace_offer_id` уникален среди active actions (partial unique index).
- **Failure risks:** Duplicate event delivery → idempotent check по `decision_id`. Unique constraint violation → action уже создан.
- **Uniqueness:** Единственная точка входа в state machine Execution. Отдельный контракт (event-driven, не REST).

### EXE-02: Approval flow (SEMI_AUTO mode)

- **Назначение:** Ручное подтверждение или отклонение price action перед исполнением.
- **Trigger:** User action: POST approve/reject.
- **Main path:** `PENDING_APPROVAL → APPROVED` (approve) или `PENDING_APPROVAL → CANCELLED` (reject, с обязательным `cancel_reason`). CAS guard: `UPDATE WHERE status = 'PENDING_APPROVAL'`.
- **Dependencies:** User role: PRICING_MANAGER или ADMIN. Action существует и в PENDING_APPROVAL. `cancel_reason` обязателен при reject.
- **Failure risks:** Concurrent approve+reject → CAS обеспечивает single winner. Action expired while user deciding → EXPIRED (timeout job).
- **Uniqueness:** User-initiated state transition (в отличие от system-driven). Reject → CANCELLED (не отдельный REJECTED status — используем единый terminal state).

### EXE-03: Approval timeout (PENDING_APPROVAL → EXPIRED)

- **Назначение:** Автоматическое истечение actions, не подтверждённых вовремя.
- **Trigger:** Scheduled job (hourly).
- **Main path:** SELECT actions WHERE status = PENDING_APPROVAL AND created_at + timeout < now() → CAS UPDATE → EXPIRED.
- **Dependencies:** `price_policy.approval_timeout_hours` (default: 72h).
- **Failure risks:** Clock skew → minimal risk (hourly granularity). Concurrent approval + timeout → CAS single winner.
- **Uniqueness:** Scheduler-driven, not user-driven. Terminal state, no recovery.

### EXE-04: Scheduling (APPROVED → SCHEDULED)

- **Назначение:** Запланировать action на исполнение — всегда через SCHEDULED, даже для immediate execution.
- **Trigger:** Approval event или auto-approval (FULL_AUTO mode).
- **Main path:** APPROVED → SCHEDULED + INSERT `outbox_event` (в одной транзакции). CAS guard. Outbox poller delivers message to RabbitMQ for worker claim. Worker: SCHEDULED → EXECUTING.
- **Dependencies:** `next_attempt_at` field. Outbox poller (delivers to RabbitMQ).
- **Failure risks:** `next_attempt_at` в прошлом → execute immediately (outbox poller picks up on next cycle). Outbox poller lag → delayed execution (bounded by poll interval).
- **Uniqueness:** Всегда проходит через SCHEDULED (нет прямого APPROVED → EXECUTING). Единообразный flow для immediate и deferred execution.

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
- **Trigger:** `outbox_event` (type: `RECONCILIATION_CHECK`) с TTL — outbox poller доставляет в RabbitMQ после истечения TTL.
- **Main path:** Consumer получает RECONCILIATION_CHECK → read provider price → compare → SUCCEEDED/FAILED. При неудаче — INSERT новый `outbox_event` с увеличенным TTL (progressive backoff: 30s → 60s → 120s), max 3 попытки.
- **Dependencies:** `outbox_event` (type: `RECONCILIATION_CHECK`) с TTL. Initial delay: 30s (via outbox TTL), max 3 reconciliation attempts with backoff multiplier 2×.
- **Failure risks:** Price changed by someone else between write and check → false positive reconciliation failure. Stale marketplace data → false negative. Все попытки исчерпаны → FAILED.
- **Uniqueness:** Работает через outbox с progressive backoff, не через `deferred_action` table (та таблица — для deferred supersede). Event-driven, не batch polling.

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
- **Trigger:** Scheduled job (every 5 min / каждые 5 мин).
- **Main path:** SELECT actions WHERE status IN (EXECUTING, RETRY_SCHEDULED, RECONCILIATION_PENDING, SCHEDULED) AND updated_at < now() - threshold → RECONCILIATION_PENDING или FAILED.
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
- **Main path:** SELECT pending events → publish to RabbitMQ → UPDATE status = PUBLISHED → ACK.
- **Dependencies:** RabbitMQ availability. Idempotent consumers.
- **Failure risks:** RabbitMQ down → events accumulate in outbox (bounded table growth). Publisher crash after publish but before DB update → duplicate delivery (idempotent consumer handles).
- **Uniqueness:** Infrastructure concern, не business logic. Отдельный failure path (messaging infrastructure vs provider).

### EXE-18: Outbox-based DB-first retry (обработка ошибок consumer)

- **Назначение:** Обработка ошибок при consume outbox messages — DB-first подход вместо RabbitMQ-level retry.
- **Trigger:** Consumer throws unhandled exception при обработке outbox message.
- **Main path:** `defaultRequeueRejected=false` → message consumed (ACK), не requeue'd. `outbox_event.status` → FAILED, `last_error` записан. `log.error` с `correlation_id`. Alert: «poison pill detected». Retriable business errors (HTTP 429, 503, timeout) обрабатываются через CAS → RETRY_SCHEDULED → INSERT `outbox_event` с TTL. Non-retriable ошибки → message consumed, investigation через `outbox_event.last_error`.
- **Dependencies:** Outbox table. Error classification (retriable vs non-retriable).
- **Failure risks:** Poison message (corrupt payload, NPE) → consumed + alerted, не блокирует очередь. Outbox poller lag → delayed retry.
- **Uniqueness:** DB-first retry (PostgreSQL — truth для retry state), не RabbitMQ-level retry. Предотвращает infinite requeue loop от poison pills.

### EXE-19: Cancel from RETRY_SCHEDULED

- **Назначение:** Оператор отменяет action, ожидающий retry.
- **Trigger:** User action: POST cancel на action в RETRY_SCHEDULED.
- **Main path:** RETRY_SCHEDULED → CANCELLED. CAS guard. Provider call предыдущей попытки завершён — cancel безопасен.
- **Dependencies:** User role: OPERATOR+. `cancel_reason` обязателен.
- **Failure risks:** Concurrent retry + cancel → CAS single winner.
- **Uniqueness:** Cancel из non-obvious pre-execution state.

### EXE-20: Cancel from RECONCILIATION_PENDING

- **Назначение:** Оператор прекращает reconciliation и берёт ответственность на себя.
- **Trigger:** User action: POST cancel на action в RECONCILIATION_PENDING.
- **Main path:** RECONCILIATION_PENDING → CANCELLED. CAS guard. `cancel_reason` обязателен (write мог быть уже применён провайдером).
- **Dependencies:** User role: PRICING_MANAGER / ADMIN / OWNER. Повышенные требования к роли из-за risk.
- **Failure risks:** Цена могла уже измениться на маркетплейсе — оператор должен проверить вручную.
- **Uniqueness:** High-risk cancel — write side effect мог произойти.

### EXE-21: Manual retry of failed action

- **Назначение:** Оператор перезапускает failed action.
- **Trigger:** `POST /api/actions/{actionId}/retry` (PRICING_MANAGER, ADMIN, OWNER). Body: `{ retryReason }`.
- **Main path:** FAILED → создаётся **новый** `price_action` (re-run с тем же decision). Оригинальный action остаётся FAILED для audit trail.
- **Dependencies:** Оригинальный action в FAILED. Decision ещё актуален.
- **Failure risks:** Underlying issue не устранена → новый action тоже FAILED. Decision устарела → новый action на базе устаревших данных.
- **Uniqueness:** Не CAS-переход на существующем action, а создание нового. Audit trail сохраняется.

### EXE-22: Bulk approve

- **Назначение:** Оператор одобряет несколько pending actions за один запрос.
- **Trigger:** `POST /api/actions/bulk-approve` Body: `{ actionIds: [...] }`.
- **Main path:** Per-action: CAS PENDING_APPROVAL → APPROVED → SCHEDULED + INSERT outbox. Failures per action не прерывают batch.
- **Dependencies:** User role: PRICING_MANAGER, ADMIN, OWNER. Все actions в PENDING_APPROVAL.
- **Failure risks:** Partial success (часть actions уже expired/cancelled). Response содержит per-action result.
- **Uniqueness:** Batch operation — другой performance profile, другой response contract.

### EXE-23: Deferred supersede — scheduled job consumes deferred_action

- **Назначение:** Полный lifecycle deferred supersede: от INSERT deferred_action до создания нового action.
- **Trigger:** Active action для offer завершается (SUCCEEDED / FAILED / CANCELLED), deferred_action существует.
- **Main path:** Pricing run → новый decision для offer X, но active action A в EXECUTING → INSERT/UPSERT `deferred_action` (marketplace_offer_id, price_decision_id, execution_mode). Action A завершается → scheduled job (каждые 30 сек) → `SELECT deferred_action WHERE marketplace_offer_id has no active action` → decision ещё актуален? → создаёт новый `price_action` → DELETE `deferred_action`.
- **Dependencies:** `deferred_action` table. Scheduled job (30s interval, совмещён с outbox poller cycle). Decision freshness check.
- **Failure risks:** Decision устарела к моменту consume → deferred_action удаляется без создания action. TTL expiration (`approval_timeout_hours`) → deferred_action expire → не создаётся action. Более свежий deferred_action перезаписывает предыдущий (UPSERT).
- **Uniqueness:** Отложенный scheduled flow — другой trigger (completion active action + scheduled job), другой persistence (`deferred_action` table), другой timing (не immediate).

### EXE-24: WB-specific reconciliation (poll-based)

- **Назначение:** Reconciliation для Wildberries через poll upload details.
- **Trigger:** Action EXECUTING → provider write → WB upload API call completed.
- **Main path:** Provider call → WB returns upload task ID → poll upload details (delay 3s, then 4s, max 2 polls) → `historyGoods[].errorText` empty → SUCCEEDED (primary evidence). `errorText` non-empty → FAILED (non-retriable). Poll still empty (processing) → RECONCILIATION_PENDING → deferred reconciliation через read prices API (`/api/v2/list/goods/filter`).
- **Dependencies:** WB upload API. WB poll endpoint. Timing: 3s + 4s poll intervals.
- **Failure risks:** WB poll endpoint down → RECONCILIATION_PENDING (fallback to deferred). WB processing slow (> 7s) → RECONCILIATION_PENDING. `errorText` parsing error → defensive RECONCILIATION_PENDING.
- **Uniqueness:** Provider-specific reconciliation — другой evidence format (poll-based, не synchronous), другой timing (3s + 4s), другой failure path (poll timeout ≠ read timeout).

### EXE-25: Ozon partial rejection (HTTP 200 + errors[])

- **Назначение:** Обработка Ozon response: HTTP 200, но per-item rejection.
- **Trigger:** Action EXECUTING → provider write → Ozon returns HTTP 200 с `result[].updated: false` + `errors[]`.
- **Main path:** Parse response → check `result[].updated` → false → classify by `errors[].code`: rate-limit related (per-product 10/hour) → retriable, backoff 10 min → RETRY_SCHEDULED. `PRODUCT_NOT_FOUND`, validation errors → non-retriable → FAILED. Неизвестный код → non-retriable → FAILED (safe default). `result[].updated: true` → SUCCEEDED (primary evidence).
- **Dependencies:** Ozon Seller API price update endpoint. Error code classification map.
- **Failure risks:** Новый error code не в classification map → FAILED (safe default, но может быть retriable). Per-product rate limit (10/hour) → long backoff (10 min) между attempts для одного offer.
- **Uniqueness:** HTTP 200 ≠ success — фундаментально другой detection path. Не 4xx (EXE-07), не 5xx (EXE-06). Per-item classification внутри successful HTTP response.

---

## Индекс сценариев

| ID | Название | Краткое описание |
|----|----------|------------------|
| EXE-01 | Создание price_action | Happy path создания action (в одной транзакции с decision) |
| EXE-02 | Approval flow | Approval / reject (CANCELLED) |
| EXE-03 | Approval timeout | PENDING_APPROVAL → EXPIRED по таймауту |
| EXE-04 | Scheduling | APPROVED → SCHEDULED (всегда через SCHEDULED) |
| EXE-05 | Provider write — success | Immediate success write на маркетплейс |
| EXE-06 | Provider write — transient failure | 5xx / timeout → retry с backoff |
| EXE-07 | Provider write — terminal failure | 4xx → FAILED (no retry) |
| EXE-08 | Provider write — uncertain | Неопределённый результат → RECONCILIATION_PENDING |
| EXE-09 | Immediate reconciliation | Проверка цены сразу после write |
| EXE-10 | Deferred reconciliation | Отложенная проверка через outbox с TTL и backoff |
| EXE-11 | Manual reconciliation | Ручное разрешение RECONCILIATION_PENDING |
| EXE-12 | Cancel action | Отмена до исполнения |
| EXE-13 | Supersede | Новый action заменяет existing |
| EXE-14 | Hold / Resume | Приостановка и возобновление |
| EXE-15 | Stuck-state detection | Обнаружение застрявших actions (каждые 5 мин) |
| EXE-16 | Simulation mode | SIMULATED execution без реальной записи |
| EXE-17 | Outbox publisher | Доставка outbox events в RabbitMQ |
| EXE-18 | DB-first outbox retry | Обработка ошибок consumer (DB-first, не DLX) |
| EXE-19 | Cancel from RETRY_SCHEDULED | Отмена action в ожидании retry |
| EXE-20 | Cancel from RECONCILIATION_PENDING | High-risk cancel после возможного write |
| EXE-21 | Manual retry of failed action | Перезапуск failed action (новый action) |
| EXE-22 | Bulk approve | Массовое одобрение pending actions |
| EXE-23 | Deferred supersede lifecycle | Scheduled job consumes deferred_action после завершения active action |
| EXE-24 | WB reconciliation (poll) | Poll-based reconciliation для Wildberries |
| EXE-25 | Ozon partial rejection | HTTP 200 + per-item errors[] classification |
