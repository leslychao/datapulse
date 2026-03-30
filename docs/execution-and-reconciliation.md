# Datapulse — Исполнение и сверка

## Назначение

Модель исполнения внешних действий (price changes) и подтверждения их результатов. Execution и reconciliation — отдельный operational lifecycle, не часть pricing decision logic.

## Action lifecycle

### State machine

```
PENDING_APPROVAL → APPROVED → SCHEDULED → EXECUTING → RECONCILIATION_PENDING → SUCCEEDED
    ↓                ↕           ↓                  ↘ FAILED
  EXPIRED         ON_HOLD    CANCELLED            ↗ RETRY_SCHEDULED ──↗
                    ↓
                 CANCELLED
```

### Состояния

| State | Описание | Переходы |
|-------|----------|----------|
| `PENDING_APPROVAL` | Action intent создан pricing pipeline; ожидает approval | → APPROVED (manual/auto) / EXPIRED / CANCELLED |
| `APPROVED` | Одобрен к исполнению | → SCHEDULED / ON_HOLD / CANCELLED |
| `ON_HOLD` | Приостановлено оператором (manual price lock) | → APPROVED (resume) / CANCELLED |
| `SCHEDULED` | В очереди на исполнение; outbox message создан | → EXECUTING (worker claim) / CANCELLED |
| `EXECUTING` | Worker взял action; внешний вызов в процессе | → RECONCILIATION_PENDING / SUCCEEDED / RETRY_SCHEDULED / FAILED |
| `RECONCILIATION_PENDING` | Provider response получен, результат не подтверждён | → SUCCEEDED / FAILED |
| `RETRY_SCHEDULED` | Retriable failure; следующая попытка запланирована | → EXECUTING (retry due) |
| `SUCCEEDED` | Подтверждённый финальный эффект | Terminal |
| `FAILED` | Терминальная ошибка; max attempts или non-retriable | Terminal |
| `EXPIRED` | Approval timeout; action не был одобрен вовремя | Terminal |
| `CANCELLED` | Отменено оператором до начала исполнения | Terminal |

### Критическое правило: SUCCEEDED = confirmed only

SUCCEEDED присваивается **только** когда фактический эффект подтверждён (price change verified by re-read). Ложный success — architectural violation.

Uncertain provider outcomes (HTTP 2xx без гарантии применения, timeout без ответа) → `RECONCILIATION_PENDING`, не `SUCCEEDED`.

## DB-first + Transactional Outbox

### Инвариант

Критическое состояние (action intent, attempt, outcome) фиксируется в PostgreSQL **до** внешнего вызова. Потеря in-flight message в RabbitMQ не приводит к потере business state.

### Outbox pattern

```
Action approved → INSERT outbox_event (PENDING) → Outbox poller (1s)
 → claims batch (SELECT ... FOR UPDATE SKIP LOCKED)
 → publishes to RabbitMQ → marks SENT
 → on failure: marks ERROR, retry on next poll
```

### Outbox table

```
outbox_event:
  id            BIGSERIAL
  type          (see Outbox event types below)
  payload_json  (serialized execution context)
  exchange_name
  routing_key
  correlation_id
  ttl_millis    (for *_RETRY types only)
  status        (PENDING | SENT | ERROR)
  created_at
  sent_at
  last_error
```

### RabbitMQ topology

```
Exchanges (direct):
  action.execution       ← основная диспетчеризация
  action.execution.wait  ← delayed retry (DLX target)

Queues:
  action.execution       ← executor worker слушает здесь
  action.execution.wait  ← TTL expiration → DLX → action.execution
```

Consumer config: `AcknowledgeMode.AUTO`, `prefetchCount=1`, `defaultRequeueRejected=true`. ACK только после durable DB outcome commit.

### Outbox event types

| Type | Exchange | Назначение |
|------|----------|------------|
| `ETL_STEP_EXECUTE` | `etl.execution` | Диспетчеризация ETL-шага (fetch / normalize / materialize) |
| `ETL_STEP_RETRY` | `etl.execution.wait` (с TTL) | Retry failed ETL-шага после backoff |
| `ACTION_EXECUTE` | `action.execution` | Исполнение price action |
| `ACTION_RETRY` | `action.execution.wait` (с TTL) | Retry failed action после backoff |
| `RECONCILIATION_CHECK` | `action.execution` | Проверка результата reconciliation |

Каждый outbox event привязан к `exchange_name` и `routing_key`. TTL используется только для `*_RETRY` типов — определяет задержку через DLX/TTL механизм.

## Retry

### Параметры

| Параметр | Default | Описание |
|----------|---------|----------|
| `max_attempts` | 3 | Максимальное количество попыток |
| `min_backoff` | 5s | Минимальная задержка |
| `max_backoff` | 5m | Максимальная задержка |
| `backoff_multiplier` | 2× | Exponential growth factor |

### Retry flow

```
Retriable failure → TransactionTemplate {
  if attempt >= max_attempts → mark FAILED (terminal)
  else → CAS: EXECUTING → RETRY_SCHEDULED
       → set next_attempt_at = now + backoff
       → INSERT outbox RETRY_EXECUTE with TTL
} → if terminal: completion check
```

### Retry truth

Retry state (attempt count, next_attempt_at, error history) хранится в PostgreSQL. RabbitMQ обеспечивает только delay через DLX/TTL:

1. Worker inserts `RETRY_EXECUTE` outbox row с TTL.
2. Outbox publisher sends to wait exchange с message TTL.
3. Message expires → DLX redirects to main execution queue.
4. Worker picks up → checks `next_attempt_at` (guard) → proceeds если retry is due.

### Классификация ошибок

| Тип | Действие | Примеры |
|-----|----------|---------|
| Retriable (rate limit) | Backoff + retry | HTTP 429, provider throttling |
| Retriable (transient) | Backoff + retry | HTTP 503, connection timeout |
| Non-retriable | FAILED немедленно | HTTP 400, validation error, business rule violation |

## Reconciliation

### Назначение

Подтверждение, что action intent привёл к ожидаемому результату.

### Flow

```
Action executed → provider response →
  if provider confirms immediate success → SUCCEEDED
  if provider response uncertain → RECONCILIATION_PENDING →
    → reconciliation worker re-reads current state from provider →
    → compares expected vs actual →
    → SUCCEEDED (match) / FAILED (mismatch or timeout)
```

### Правила

| Правило | Описание |
|---------|----------|
| Re-read verification | Проверяет фактическое состояние через read API (не доверяет write response) |
| Expected vs actual | Сравнивается intent (target price) с фактическим значением |
| Timeout escalation | Reconciliation timeout → escalation to alerting / manual investigation |
| Residual tracking | Расхождение между expected и actual outcome фиксируется |

### Критерии SUCCEEDED

Action считается SUCCEEDED только при выполнении всех условий:

1. Provider write call получил positive response.
2. Reconciliation re-read подтвердил, что target price применена.
3. Фактическое значение соответствует intent в пределах допустимого tolerance.

## CAS (Compare-And-Swap) guards

Все переходы состояний реализуются через CAS SQL:

```sql
UPDATE price_action_attempt
SET status = :newStatus, updated_at = NOW()
WHERE id = :id AND status = :expectedStatus
```

### Свойства

| Свойство | Описание |
|----------|----------|
| Single-winner | Только один worker может claim action; concurrent attempts → один succeeds |
| Transition validation | Невалидный переход (unexpected current status) → silent skip |
| No optimistic locking | CAS вместо version-based optimistic locking; DB-level guarantee |
| Audit trail | Каждый CAS transition фиксируется с timestamp |

### Таблица переходов

| Transition | Когда |
|------------|-------|
| `PENDING_APPROVAL → APPROVED` | Manual approval или auto-approval policy |
| `PENDING_APPROVAL → EXPIRED` | Approval timeout (configurable) |
| `PENDING_APPROVAL → CANCELLED` | Manual cancellation |
| `APPROVED → SCHEDULED` | Action scheduled for execution |
| `APPROVED → ON_HOLD` | Manual hold (operator) |
| `APPROVED → CANCELLED` | Manual cancellation |
| `ON_HOLD → APPROVED` | Resume after hold (operator) |
| `ON_HOLD → CANCELLED` | Manual cancellation during hold |
| `SCHEDULED → EXECUTING` | Worker claim |
| `SCHEDULED → CANCELLED` | Manual cancellation (до worker claim) |
| `EXECUTING → RECONCILIATION_PENDING` | Uncertain provider response |
| `EXECUTING → SUCCEEDED` | Confirmed immediate success |
| `EXECUTING → RETRY_SCHEDULED` | Retriable failure |
| `EXECUTING → FAILED` | Non-retriable failure или max attempts |
| `RETRY_SCHEDULED → EXECUTING` | Retry due |
| `RECONCILIATION_PENDING → SUCCEEDED` | Reconciliation confirmed |
| `RECONCILIATION_PENDING → FAILED` | Reconciliation failed или timeout |

## Идемпотентность

### Action idempotency

| Уровень | Механизм |
|---------|----------|
| Action creation | Pricing decision → action intent; same decision не создаёт дубль |
| Attempt execution | CAS claim → только один worker; retry of same attempt → skip |
| Provider call | Idempotency key (где поддерживается провайдером) |
| Outbox delivery | At-least-once delivery + idempotent consumer |

### Provider idempotency

| Provider | Механизм |
|----------|----------|
| WB Price Write | Last-write-wins; reconciliation re-read validates |
| Ozon Price Write | Last-write-wins; reconciliation re-read validates |

## Manual intervention

| Точка вмешательства | Описание |
|---------------------|----------|
| Manual approval | Оператор одобряет/отклоняет pending action |
| Manual hold | Оператор приостанавливает execution (manual price lock) |
| Manual retry | Оператор перезапускает failed action |
| Manual reconciliation | Оператор вручную помечает action как succeeded/failed |

### Failed action alerting

Failed actions (terminal FAILED) генерируют alert:

- Alert содержит: action ID, target entity, failure reason, attempt history.
- Alert видим в Seller Operations → working queue «failed actions».
- Оператор может investigate, retry или dismiss.

## Симулированное исполнение

### Flow

```
Decision → Explanation → Action Scheduling → Simulated Gateway
 → Shadow-state update → Simulated reconciliation → Simulated SUCCEEDED/FAILED
```

### Правила

| Правило | Описание |
|---------|----------|
| Full pipeline parity | Simulated mode проходит все шаги live pipeline |
| No real writes | Simulated gateway не вызывает реальный provider API |
| Shadow-state isolation | Simulated state хранится отдельно; не мутирует каноническую модель |
| Parity tests | Automated tests проверяют идентичность simulated и live pipelines |
| Execution mode tracking | Каждый action помечен `execution_mode = SIMULATED / LIVE` |

## Границы транзакций

| Операция | Scope | Примечания |
|----------|-------|------------|
| Action creation (from pricing) | `@Transactional` | Decision + action + outbox в одной транзакции |
| Worker CAS claim | Standalone JDBC update | Не в TransactionTemplate (быстрый single statement) |
| Provider call | Без транзакции | I/O-bound, potentially long-running |
| Outcome recording | `TransactionTemplate` | CAS state update + optional retry outbox |
| Reconciliation check | Без транзакции | После commit outcome transaction |

## Модель данных (ключевые таблицы)

| Таблица | Ответственность |
|---------|-----------------|
| `price_action` | Action intent: target entity, target price, pricing decision reference, current status |
| `price_action_attempt` | Отдельная попытка: attempt number, started_at, completed_at, outcome, error |
| `outbox_event` | Outbox для reliable delivery: payload, exchange, routing key, status |
| `simulated_offer_state` | Shadow-state для симуляции: simulated prices per offer |
| `manual_price_lock` | Manual hold: operator-placed locks, блокирующие automated price changes |

## Связанные документы

- [Архитектура данных](data-architecture.md) — scope Phase A/B, runtime entrypoints
- [Функциональные возможности](functional-capabilities.md) — pricing pipeline, controlled execution
- [Нефункциональная архитектура](non-functional-architecture.md) — resilience requirements
- [Архитектура данных](data-architecture.md) — action state в модели данных
