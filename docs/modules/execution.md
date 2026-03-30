# Модуль: Execution

**Фаза:** D — Execution, F — Simulation
**Зависимости:** [Pricing](pricing.md)
**Runtime:** datapulse-executor-worker, datapulse-api (manual interventions)

---

## Назначение

Lifecycle внешних действий (price changes) с гарантиями доставки и подтверждения результата. Включает retry, reconciliation, симулированное исполнение.

## Action lifecycle

### State machine

```
PENDING_APPROVAL → APPROVED → SCHEDULED → EXECUTING → RECONCILIATION_PENDING → SUCCEEDED
    ↓ ↓              ↕           ↓                  ↘ FAILED
  EXPIRED          ON_HOLD    CANCELLED            ↗ RETRY_SCHEDULED ──↗
  SUPERSEDED         ↓
                  CANCELLED
```

### Состояния

| State | Описание | Переходы |
|-------|----------|----------|
| `PENDING_APPROVAL` | Action intent создан pricing pipeline; ожидает approval | → APPROVED / EXPIRED / CANCELLED / SUPERSEDED |
| `APPROVED` | Одобрен к исполнению | → SCHEDULED / ON_HOLD / CANCELLED |
| `ON_HOLD` | Приостановлено оператором | → APPROVED / CANCELLED |
| `SCHEDULED` | В очереди на исполнение; outbox message создан | → EXECUTING / CANCELLED |
| `EXECUTING` | Worker взял action; внешний вызов в процессе | → RECONCILIATION_PENDING / SUCCEEDED / RETRY_SCHEDULED / FAILED |
| `RECONCILIATION_PENDING` | Provider response получен, результат не подтверждён | → SUCCEEDED / FAILED |
| `RETRY_SCHEDULED` | Retriable failure; следующая попытка запланирована | → EXECUTING |
| `SUCCEEDED` | Подтверждённый финальный эффект | Terminal |
| `FAILED` | Терминальная ошибка | Terminal |
| `EXPIRED` | Approval timeout | Terminal |
| `CANCELLED` | Отменено оператором | Terminal |
| `SUPERSEDED` | Заменён более свежим decision | Terminal |

### SUCCEEDED = confirmed only

SUCCEEDED присваивается **только** когда фактический эффект подтверждён (price change verified by re-read). Ложный success — architectural violation.

Uncertain provider outcomes → `RECONCILIATION_PENDING`, не `SUCCEEDED`.

## DB-first + Transactional Outbox

### Инвариант

Критическое состояние фиксируется в PostgreSQL **до** внешнего вызова. Потеря in-flight message в RabbitMQ не приводит к потере business state.

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
  type          (ETL_STEP_EXECUTE, ETL_STEP_RETRY, ACTION_EXECUTE, ACTION_RETRY, RECONCILIATION_CHECK)
  payload_json
  exchange_name
  routing_key
  correlation_id
  ttl_millis    (for *_RETRY types only)
  status        (PENDING | SENT | ERROR)
  created_at, sent_at, last_error
```

### RabbitMQ topology

```
Exchanges (direct):
  action.execution       ← основная диспетчеризация
  action.execution.wait  ← delayed retry (DLX target)

Queues:
  action.execution       ← executor worker
  action.execution.wait  ← TTL expiration → DLX → action.execution
```

Consumer config: `AcknowledgeMode.AUTO`, `prefetchCount=1`, `defaultRequeueRejected=false`.

### Consumer error handling

`defaultRequeueRejected=false`. При unhandled exception в consumer:

1. Message consumed (ACK), не requeue'd.
2. `outbox_event.status` → ERROR, `last_error` записан.
3. `log.error` с `correlation_id`.
4. Alert: «poison pill detected».

Retriable business errors (HTTP 429, 503, timeout) обрабатываются через outbox retry (CAS → RETRY_SCHEDULED → INSERT outbox с TTL). Non-retriable ошибки (corrupt payload, NPE, validation) → message consumed, investigation через `outbox_event.last_error`.

**Обоснование:** DB-first retry через outbox надёжнее, чем RabbitMQ requeue. Requeue poison pill → infinite loop → очередь заблокирована. Consumed poison pill → logged + alerted → остальные messages обрабатываются.

### Outbox poller concurrency

При горизонтальном масштабировании worker'ов несколько outbox poller'ов работают корректно: `FOR UPDATE SKIP LOCKED` обеспечивает single-winner semantics. Дополнительная координация (leader election) не требуется для ожидаемого масштаба (≤ 3 инстанса на entrypoint).

## Retry

### Параметры

| Параметр | Default | Описание |
|----------|---------|----------|
| `max_attempts` | 3 | Макс. попыток |
| `min_backoff` | 5s | Мин. задержка |
| `max_backoff` | 5m | Макс. задержка |
| `backoff_multiplier` | 2× | Exponential growth |

### Retry flow

```
Retriable failure → TransactionTemplate {
  if attempt >= max_attempts → mark FAILED (terminal)
  else → CAS: EXECUTING → RETRY_SCHEDULED
       → set next_attempt_at = now + backoff
       → INSERT outbox RETRY_EXECUTE with TTL
}
```

Retry truth (attempt count, next_attempt_at, error history) — в PostgreSQL, не в RabbitMQ.

### Классификация ошибок

| Тип | Действие | Примеры |
|-----|----------|---------|
| Retriable (rate limit) | Backoff + retry | HTTP 429 |
| Retriable (transient) | Backoff + retry | HTTP 503, timeout |
| Non-retriable | FAILED немедленно | HTTP 400, validation error |

## Reconciliation

### Flow

```
Action executed → provider response →
  if confirms immediate success → SUCCEEDED
  if uncertain → RECONCILIATION_PENDING →
    → reconciliation worker re-reads current state →
    → compares expected vs actual →
    → SUCCEEDED (match) / FAILED (mismatch or timeout)
```

### Правила

| Правило | Описание |
|---------|----------|
| Re-read verification | Проверяет фактическое состояние через read API |
| Expected vs actual | Сравнивается intent (target price) с фактическим значением |
| Timeout escalation | Reconciliation timeout → alerting / manual investigation |
| Residual tracking | Расхождение expected vs actual фиксируется |

### Критерии SUCCEEDED

1. Provider write call получил positive response.
2. Reconciliation re-read подтвердил, что target price применена.
3. Фактическое значение соответствует intent в пределах tolerance.

### Reconciliation — triggering и timing

**Этап 1: Immediate reconciliation (в рамках execution attempt)**

```
Provider call completed →
  WB: poll upload details (3s + 4s, max 2 polls)
  Ozon: synchronous response

  if confirmed → SUCCEEDED
  if WB poll still empty → RECONCILIATION_PENDING
  if Ozon updated:false → FAILED (non-retriable)
```

**Этап 2: Deferred reconciliation (через outbox)**

```
CAS: EXECUTING → RECONCILIATION_PENDING
→ INSERT outbox_event {
    type: RECONCILIATION_CHECK,
    payload_json: { action_id, marketplace_offer_id, expected_price, attempt: 1 },
    ttl_millis: 30000
  }
→ outbox poller → RabbitMQ (action.reconciliation.wait, DLX after TTL)
→ reconciliation consumer:
    1. Read current price from marketplace (GET prices endpoint)
    2. Compare actual vs expected
    3. if match → CAS: RECONCILIATION_PENDING → SUCCEEDED
    4. if mismatch AND attempt < max_reconciliation_attempts:
       → INSERT outbox_event RECONCILIATION_CHECK with increased TTL (60s, 120s)
    5. if mismatch AND attempt >= max → CAS: RECONCILIATION_PENDING → FAILED
       → Alert: «reconciliation failed, manual investigation»
```

**Параметры:**

| Параметр | Default | Описание |
|----------|---------|----------|
| `reconciliation_initial_delay` | 30s | Задержка до первой проверки |
| `reconciliation_backoff_multiplier` | 2× | Множитель задержки |
| `max_reconciliation_attempts` | 3 | Макс. проверок |
| `reconciliation_timeout` | 10min | Абсолютный таймаут (safety net) |

**Safety net:** Scheduled job (каждые 5 мин) проверяет actions в RECONCILIATION_PENDING дольше `reconciliation_timeout` → CAS → FAILED + alert.

**RabbitMQ topology (reconciliation):**

```
Exchanges (direct):
  action.reconciliation       ← reconciliation dispatch
  action.reconciliation.wait  ← delayed reconciliation (DLX)

Queues:
  action.reconciliation       ← reconciliation consumer
  action.reconciliation.wait  ← TTL → DLX → action.reconciliation
```

## CAS (Compare-And-Swap) guards

Все переходы через CAS SQL:

```sql
UPDATE price_action_attempt
SET status = :newStatus, updated_at = NOW()
WHERE id = :id AND status = :expectedStatus
```

| Свойство | Описание |
|----------|----------|
| Single-winner | Один worker может claim; concurrent → один succeeds |
| Transition validation | Невалидный переход → silent skip |
| No optimistic locking | CAS вместо version-based; DB-level guarantee |
| Audit trail | Каждый CAS transition с timestamp |

### Таблица переходов

| Transition | Когда |
|------------|-------|
| `PENDING_APPROVAL → APPROVED` | Manual или auto-approval |
| `PENDING_APPROVAL → EXPIRED` | Approval timeout (scheduled job) |
| `PENDING_APPROVAL → SUPERSEDED` | Более свежий action на тот же offer |
| `APPROVED → SCHEDULED` | Action scheduled |
| `APPROVED → ON_HOLD` | Manual hold |
| `SCHEDULED → EXECUTING` | Worker claim |
| `EXECUTING → RECONCILIATION_PENDING` | Uncertain response |
| `EXECUTING → SUCCEEDED` | Confirmed immediate |
| `EXECUTING → RETRY_SCHEDULED` | Retriable failure |
| `EXECUTING → FAILED` | Non-retriable / max attempts |
| `RETRY_SCHEDULED → EXECUTING` | Retry due |
| `RECONCILIATION_PENDING → SUCCEEDED/FAILED` | Reconciliation result |

## Supersede policy

При создании нового `price_action` для `marketplace_offer_id`, если существует `price_action` в статусе `PENDING_APPROVAL` для того же `marketplace_offer_id` → CAS: `PENDING_APPROVAL → SUPERSEDED` (terminal).

`SUPERSEDED` означает «заменён более свежим решением». Не является ошибкой.

**DB constraint:** partial unique index гарантирует не более одного active action на `marketplace_offer`:

```sql
CREATE UNIQUE INDEX idx_price_action_active_offer
ON price_action (marketplace_offer_id)
WHERE status NOT IN ('SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED', 'SUPERSEDED');
```

## Expiration mechanism

Scheduled job (interval: 1 час). Переводит actions из `PENDING_APPROVAL` в `EXPIRED`, если `created_at + approval_timeout_hours < NOW()`.

Оба механизма — defense in depth: supersede при нормальной работе, expiration — fallback при отсутствии свежих pricing runs.

## Идемпотентность

| Уровень | Механизм |
|---------|----------|
| Action creation | Same decision не создаёт дубль |
| Attempt execution | CAS claim → только один worker |
| Provider call | Idempotency key (где поддерживается) |
| Outbox delivery | At-least-once + idempotent consumer |

| Provider | Механизм |
|----------|----------|
| WB Price Write | Last-write-wins; reconciliation re-read validates |
| Ozon Price Write | Last-write-wins; reconciliation re-read validates |

## Manual intervention

| Точка вмешательства | Описание |
|---------------------|----------|
| Manual approval | Оператор одобряет/отклоняет pending action |
| Manual hold | Оператор приостанавливает execution |
| Manual retry | Оператор перезапускает failed action |
| Manual reconciliation | Оператор вручную помечает succeeded/failed |

### Failed action alerting

Failed actions → alert: action ID, target entity, failure reason, attempt history. Видим в Seller Operations → working queue «failed actions».

## Симулированное исполнение (Phase F)

### Flow

```
Decision → Explanation → Action Scheduling → Simulated Gateway
 → Shadow-state update → Simulated reconciliation → Simulated SUCCEEDED/FAILED
```

### Правила

| Правило | Описание |
|---------|----------|
| Full pipeline parity | Simulated mode проходит все шаги live pipeline |
| No real writes | Simulated gateway не вызывает реальный API |
| Shadow-state isolation | Хранится отдельно; не мутирует каноническую модель |
| Parity tests | Automated tests проверяют идентичность simulated и live |
| Execution mode tracking | `execution_mode = SIMULATED / LIVE` |

## Границы транзакций

| Операция | Scope |
|----------|-------|
| Action creation (from pricing) | `@Transactional` — decision + action + outbox |
| Worker CAS claim | Standalone JDBC update |
| Provider call | Без транзакции (I/O-bound) |
| Outcome recording | `TransactionTemplate` — CAS + optional retry outbox |
| Reconciliation check | Без транзакции (после commit) |

## Модель данных

| Таблица | Ответственность |
|---------|-----------------|
| `price_action` | Action intent: target entity, target price, status |
| `price_action_attempt` | Попытка: attempt number, timing, outcome, error |
| `outbox_event` | Outbox для reliable delivery |
| `simulated_offer_state` | Shadow-state для симуляции |
| `manual_price_lock` | Operator-placed locks |

## Связанные модули

- [Pricing](pricing.md) — создаёт actions из decisions
- [Integration](integration.md) — write-адаптеры для provider API
- [Seller Operations](seller-operations.md) — failed action queues, price journal
- [Tenancy & IAM](tenancy-iam.md) — approval/hold определяются ролями
