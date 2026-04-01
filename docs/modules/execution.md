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
    ↓ ↓ ↓            ↕         ↓ ↓            ↓ ↓               ↘ FAILED
  EXPIRED        ON_HOLD   CANCELLED      CANCELLED           ↗ RETRY_SCHEDULED ──↗
  SUPERSEDED       ↓                                              ↓
  CANCELLED     CANCELLED                                      CANCELLED
```

### Состояния

| State | Описание | Переходы |
|-------|----------|----------|
| `PENDING_APPROVAL` | Action intent создан pricing pipeline; ожидает approval | → APPROVED / EXPIRED / CANCELLED / SUPERSEDED |
| `APPROVED` | Одобрен к исполнению | → SCHEDULED / ON_HOLD / CANCELLED / SUPERSEDED |
| `ON_HOLD` | Приостановлено оператором | → APPROVED / CANCELLED / SUPERSEDED |
| `SCHEDULED` | В очереди на исполнение; outbox message создан | → EXECUTING / CANCELLED / SUPERSEDED |
| `EXECUTING` | Worker взял action; внешний вызов в процессе | → RECONCILIATION_PENDING / SUCCEEDED / RETRY_SCHEDULED / FAILED |
| `RECONCILIATION_PENDING` | Provider response получен, результат не подтверждён | → SUCCEEDED / FAILED / CANCELLED |
| `RETRY_SCHEDULED` | Retriable failure; следующая попытка запланирована | → EXECUTING / CANCELLED |
| `SUCCEEDED` | Подтверждённый финальный эффект | Terminal |
| `FAILED` | Терминальная ошибка | Terminal |
| `EXPIRED` | Approval timeout | Terminal |
| `CANCELLED` | Отменено оператором | Terminal |
| `SUPERSEDED` | Заменён более свежим decision | Terminal |

**Расширенные cancel/supersede переходы:**

- `SCHEDULED → CANCELLED` — manual cancel до начала execution; provider call не выполнялся, безопасно.
- `RETRY_SCHEDULED → CANCELLED` — manual cancel ожидающего retry; provider call предыдущей попытки завершён, безопасно.
- `RECONCILIATION_PENDING → CANCELLED` — manual cancel в процессе reconciliation. Означает «прекратить проверку — оператор разберётся вручную». **Важно:** write мог быть уже применён провайдером; причина `cancel_reason` обязательна.
- `APPROVED / ON_HOLD / SCHEDULED → SUPERSEDED` — новое pricing decision supersede'ит pre-execution action. Provider call не выполнялся, безопасно.
- `EXECUTING` не поддерживает cancel/supersede — in-flight вызов провайдера должен завершиться, чтобы узнать результат.

### SUCCEEDED = confirmed only

SUCCEEDED присваивается **только** когда фактический эффект подтверждён (price change verified by re-read). Ложный success — architectural violation.

Uncertain provider outcomes → `RECONCILIATION_PENDING`, не `SUCCEEDED`.

При manual reconciliation (оператор вручную помечает SUCCEEDED) — `reconciliation_source = MANUAL` и `manual_override_reason` обязательны. Это единственное исключение из автоматической верификации; факт ручного подтверждения фиксируется для аудита.

## DB-first + Transactional Outbox

### Инвариант

Критическое состояние фиксируется в PostgreSQL **до** внешнего вызова. Потеря in-flight message в RabbitMQ не приводит к потере business state.

### Outbox pattern

```
Action approved → INSERT outbox_event (PENDING) → Outbox poller (1s)
 → claims batch (SELECT ... FOR UPDATE SKIP LOCKED)
 → publishes to RabbitMQ → marks PUBLISHED
 → on failure: marks FAILED, retry on next poll
```

### Outbox table

Авторитетная DDL `outbox_event` определена в [Data Model](../data-model.md) §outbox_event — unified event types. Execution использует общий outbox с event types `PRICE_ACTION_EXECUTE`, `PRICE_ACTION_RETRY`, `PROMO_ACTION_EXECUTE`.

### RabbitMQ topology

```
Exchanges (direct):
  price.execution       ← основная диспетчеризация
  price.execution.wait  ← delayed retry (DLX target)

Queues:
  price.execution       ← executor worker
  price.execution.wait  ← TTL expiration → DLX → price.execution
```

Consumer config: `AcknowledgeMode.AUTO`, `prefetchCount=1`, `defaultRequeueRejected=false`.

### Consumer error handling

`defaultRequeueRejected=false`. При unhandled exception в consumer:

1. Message consumed (ACK), не requeue'd.
2. `outbox_event.status` → FAILED, `last_error` записан.
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
| Retriable (transient) | Backoff + retry | HTTP 503, connect timeout (запрос не отправлен) |
| Uncertain (request sent, no response) | → RECONCILIATION_PENDING | Read timeout, socket timeout после отправки запроса |
| Non-retriable | FAILED немедленно | HTTP 400, validation error |

**Разделение timeout'ов (критически важно):**

- **Connect timeout** (соединение не установлено) — запрос гарантированно не достиг провайдера → безопасно повторить → `RETRY_SCHEDULED`.
- **Read timeout** (запрос отправлен, ответ не получен) — запрос мог быть обработан провайдером → неопределённость → `RECONCILIATION_PENDING`. Retry в этом случае рискует двойной записью.

**Ozon: классификация по error code.**

Ozon возвращает HTTP 200 с `result[].updated: false` + `errors[]` при per-item rejection. Классификация:

| `errors[].code` | Тип | Действие |
|-----------------|-----|----------|
| Rate-limit related (per-product 10/hour) | Retriable | Backoff 10 min + retry |
| `PRODUCT_NOT_FOUND`, validation errors | Non-retriable | FAILED |
| Неизвестный код | Non-retriable | FAILED (safe default) |

### Rate limit координация с ETL

Executor-worker использует **те же** Redis-based token bucket-ы, что и ingest-worker (ETL). Ключ: `rate:{connection_id}:{rate_limit_group}`. Перед каждым marketplace API call executor **обязан** запросить токен из bucket соответствующей rate limit group (`WB_PRICE_UPDATE`, `OZON_PRICE_UPDATE`, `WB_PROMO`, `OZON_PROMO`).

Если токен недоступен — consumer вызывает `rateLimiter.acquire(connectionId, group).get(timeout)` и блокируется до получения токена (CompletableFuture-based, поддерживает cancel при graceful shutdown). Это критично: без координации executor и ingest-worker суммарно превысят лимит маркетплейса.

При `prefetchCount=1` — consumer не берёт следующий message из очереди, пока текущий не завершится (включая ожидание токена). Это означает **head-of-line blocking**: action для быстрого rate limit group (OZON_PRICE_UPDATE) ждёт в очереди, пока обрабатывается action для медленного group (WB_PRICE_UPDATE).

**Phase A limitation (допустимо):** при текущих объёмах (десятки actions/час) head-of-line blocking не критичен. Rate limits маркетплейсов и так медленные — параллельная обработка не даст ускорения, если бюджет один. При масштабировании (Phase G) — рассмотреть разделение очередей per rate limit group для изоляции.

**Ozon per-product rate limit:** перед формированием batch executor проверяет per-product sliding window counter (Redis sorted set, ключ: `product_rate:{connection_id}:{product_id}`). Products, исчерпавшие 10 updates/hour, исключаются из batch и откладываются. Детали: [Integration §Per-entity rate limiting](integration.md#per-entity-rate-limiting-ozon).

**Backoff при 429 vs rate limiter wait:**
- `wait_ms` от token bucket — нормальная задержка, не ошибка. Consumer остаётся EXECUTING, блокирован на `acquire().get()`.
- HTTP 429 от маркетплейса — ошибка. Классифицируется как `RETRIABLE_RATE_LIMIT`, action переходит в RETRY_SCHEDULED через outbox. Если `Retry-After` header присутствует — используется как delay; иначе `min_backoff × multiplier^attempt`.

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

**Evidence hierarchy:**

| Provider | Primary evidence (write confirmation) | Secondary evidence (read-after-write) |
|----------|--------------------------------------|--------------------------------------|
| WB | Upload poll result (`historyGoods[].errorText` empty = success) | Read prices API (`/api/v2/list/goods/filter`) |
| Ozon | Synchronous response (`result[].updated: true`) | Read prices API (`/v5/product/info/prices`) |

Primary evidence определяет, был ли write принят провайдером. Secondary evidence — read-after-write verification, используется для дополнительного подтверждения. При расхождении (primary = success, secondary = mismatch) → пометить `SUCCEEDED` + алерт «reconciliation read mismatch after confirmed write» для расследования. Доверяем write path больше, чем read path (read API может быть stale из-за eventual consistency).

**Этап 1: Immediate reconciliation (в рамках execution attempt)**

```
Provider call completed →
  WB: poll upload details (3s + 4s, max 2 polls)
  Ozon: synchronous response

  if WB poll errorText empty → SUCCEEDED (primary evidence)
  if WB poll still empty → RECONCILIATION_PENDING
  if WB poll errorText non-empty → FAILED (non-retriable)
  if Ozon updated:true → SUCCEEDED (primary evidence)
  if Ozon updated:false → classify by error code (see §Классификация ошибок)
```

**Этап 2: Deferred reconciliation (через outbox)**

```
CAS: EXECUTING → RECONCILIATION_PENDING
→ INSERT outbox_event {
    type: RECONCILIATION_CHECK,
    payload_json: { action_id, marketplace_offer_id, expected_price, attempt: 1 },
    ttl_millis: 30000
  }
→ outbox poller → RabbitMQ (price.reconciliation.wait, DLX after TTL)
→ reconciliation consumer:
    1. Read current price from marketplace (GET prices endpoint)
    2. Save evidence: UPDATE price_action_attempt SET
         reconciliation_source = 'DEFERRED',
         reconciliation_read_at = NOW(),
         reconciliation_snapshot = {raw read response},
         actual_price = {parsed price},
         price_match = (actual_price matches target_price within tolerance)
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

### Stuck-state detector (unified safety net)

Единый scheduled job (каждые 5 мин) обнаруживает actions, застрявшие в не-терминальных состояниях:

```sql
SELECT * FROM price_action
WHERE status IN ('EXECUTING', 'RETRY_SCHEDULED', 'RECONCILIATION_PENDING', 'SCHEDULED')
  AND updated_at + state_ttl(status) < NOW()
```

| Застрявшее состояние | Default TTL | Эскалация |
|---------------------|-------------|-----------|
| `EXECUTING` | 5 min | → `RECONCILIATION_PENDING` (write мог быть применён) + alert |
| `RETRY_SCHEDULED` | `next_attempt_at` + 5 min | → `FAILED` + alert |
| `RECONCILIATION_PENDING` | 10 min (`reconciliation_timeout`) | → `FAILED` + alert |
| `SCHEDULED` | 5 min | → `FAILED` + alert (outbox delivery failure) |

**Обоснование:** Worker crash, pod eviction, poison pill после деплоя, зависший provider call — любой из этих сценариев может оставить action в не-терминальном состоянии без пути восстановления. Без safety net partial unique index блокирует все будущие action'ы для оффера навсегда.

`EXECUTING` эскалируется в `RECONCILIATION_PENDING` (не `FAILED`), т.к. provider call мог быть выполнен успешно до crash'а — reconciliation проверит фактическое состояние.

**RabbitMQ topology (reconciliation):**

```
Exchanges (direct):
  price.reconciliation       ← reconciliation dispatch
  price.reconciliation.wait  ← delayed reconciliation (DLX)

Queues:
  price.reconciliation       ← reconciliation consumer
  price.reconciliation.wait  ← TTL → DLX → price.reconciliation
```

## CAS (Compare-And-Swap) guards

Все переходы через CAS SQL на таблице `price_action`:

```sql
UPDATE price_action
SET status = :newStatus, updated_at = NOW()
WHERE id = :id AND status = :expectedStatus
```

`price_action_attempt` не участвует в CAS lifecycle-переходах; attempt-записи отслеживают per-attempt outcome (timing, provider response, error). Полная схема — см. [§Модель данных](#модель-данных).

| Свойство | Описание |
|----------|----------|
| Single-winner | Один worker может claim; concurrent → один succeeds |
| Conflict detection | CAS return 0 rows → `log.warn` с action_id, attempted transition, current status, actor |
| API conflict response | API-инициированные переходы (manual operations) при CAS conflict → HTTP 409 с текущим статусом |
| Audit trail | Каждая CAS-попытка (успех и провал) записывается в `audit_log` |
| No optimistic locking | CAS вместо version-based; DB-level guarantee |

### Таблица переходов

| Transition | Когда | Инициатор |
|------------|-------|-----------|
| `PENDING_APPROVAL → APPROVED` | Manual или auto-approval | system / operator |
| `PENDING_APPROVAL → EXPIRED` | Approval timeout (scheduled job) | system |
| `PENDING_APPROVAL → SUPERSEDED` | Более свежий action на тот же offer | system |
| `PENDING_APPROVAL → CANCELLED` | Manual cancel | operator |
| `APPROVED → SCHEDULED` | Action scheduled | system |
| `APPROVED → ON_HOLD` | Manual hold | operator |
| `APPROVED → CANCELLED` | Manual cancel | operator |
| `APPROVED → SUPERSEDED` | Более свежий action (pre-execution) | system |
| `ON_HOLD → APPROVED` | Manual resume | operator |
| `ON_HOLD → CANCELLED` | Manual cancel | operator |
| `ON_HOLD → SUPERSEDED` | Более свежий action (pre-execution) | system |
| `SCHEDULED → EXECUTING` | Worker claim | system |
| `SCHEDULED → CANCELLED` | Manual cancel | operator |
| `SCHEDULED → SUPERSEDED` | Более свежий action (pre-execution) | system |
| `EXECUTING → RECONCILIATION_PENDING` | Uncertain response / read timeout / stuck-state escalation | system |
| `EXECUTING → SUCCEEDED` | Confirmed immediate (primary evidence) | system |
| `EXECUTING → RETRY_SCHEDULED` | Retriable failure (connect timeout, 429, 503) | system |
| `EXECUTING → FAILED` | Non-retriable / max attempts | system |
| `RETRY_SCHEDULED → EXECUTING` | Retry due | system |
| `RETRY_SCHEDULED → CANCELLED` | Manual cancel (предотвращает retry) | operator |
| `RECONCILIATION_PENDING → SUCCEEDED` | Reconciliation match (auto или manual) | system / operator |
| `RECONCILIATION_PENDING → FAILED` | Reconciliation mismatch / timeout | system / operator |
| `RECONCILIATION_PENDING → CANCELLED` | Manual cancel (прекращение проверки) | operator |

### APPROVED → SCHEDULED transition

Переход `APPROVED → SCHEDULED` происходит **в той же транзакции**, что и approval:

```
@Transactional:
  1. CAS: PENDING_APPROVAL → APPROVED (или auto-approval при FULL_AUTO)
  2. INSERT outbox_event (type: PRICE_ACTION_EXECUTE, aggregate_id: price_action.id)
  3. CAS: APPROVED → SCHEDULED
```

Для FULL_AUTO: pricing pipeline создаёт action сразу в APPROVED, затем в той же транзакции переводит в SCHEDULED + INSERT outbox_event.

Для SEMI_AUTO: при manual approval оператором — аналогичная транзакция (approve + schedule + outbox).

**Инвариант:** action НЕ остаётся в APPROVED без outbox event. Переход APPROVED → SCHEDULED всегда сопровождается INSERT outbox_event.

## Supersede policy

`SUPERSEDED` означает «заменён более свежим решением». Не является ошибкой.

### Immediate supersede (pre-execution)

При создании нового `price_action` для `marketplace_offer_id`, если существует active action для того же offer (в том же `execution_mode`) в pre-execution состоянии → CAS → `SUPERSEDED`:

| Текущий статус | Supersede безопасен? | Обоснование |
|---------------|---------------------|-------------|
| `PENDING_APPROVAL` | Да | Provider call не выполнялся |
| `APPROVED` | Да | Provider call не выполнялся |
| `ON_HOLD` | Да | Provider call не выполнялся |
| `SCHEDULED` | Да | Outbox event создан, но provider call не выполнялся. Consumer при получении сообщения попытается CAS `SCHEDULED → EXECUTING` — он провалится (action уже `SUPERSEDED`), silent skip |

### Deferred supersede (in-flight)

Для in-flight состояний (`EXECUTING`, `RETRY_SCHEDULED`, `RECONCILIATION_PENDING`) немедленный supersede невозможен — provider call мог быть выполнен. Вместо этого используется **deferred action queue**:

```
Новый price_action для offer X, текущий active action A в EXECUTING/RETRY_SCHEDULED/RECONCILIATION_PENDING:
→ INSERT/UPSERT deferred_action (marketplace_offer_id, price_decision_id, execution_mode)
→ A завершается (SUCCEEDED / FAILED / CANCELLED)
→ Scheduled job: проверяет deferred_action WHERE marketplace_offer_id has no active action
→ Создаёт price_action из deferred_action (если decision ещё актуален)
→ Удаляет deferred_action
```

**Правила deferred queue:**

- Одна `deferred_action` на `(marketplace_offer_id, execution_mode)` — более свежая перезаписывает предыдущую (UPSERT).
- TTL: `deferred_action` автоматически expire'ится через `approval_timeout_hours`.
- Scheduled job проверки: каждые 30 секунд (совмещается с outbox poller cycle).

### DB constraint

Partial unique indexes гарантируют не более одного active action на `marketplace_offer` per execution_mode (см. [Simulation scope](#simulation-scope)):

```sql
CREATE UNIQUE INDEX idx_price_action_active_offer_live
ON price_action (marketplace_offer_id)
WHERE execution_mode = 'LIVE'
  AND status NOT IN ('SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED', 'SUPERSEDED');

CREATE UNIQUE INDEX idx_price_action_active_offer_simulated
ON price_action (marketplace_offer_id)
WHERE execution_mode = 'SIMULATED'
  AND status NOT IN ('SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED', 'SUPERSEDED');
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

| Точка вмешательства | Описание | Допустимые состояния | Обязательные поля |
|---------------------|----------|---------------------|-------------------|
| Manual approval | Оператор одобряет pending action | `PENDING_APPROVAL` | `actor_user_id` |
| Manual reject | Оператор отклоняет pending action | `PENDING_APPROVAL` | `actor_user_id`, `cancel_reason` |
| Manual hold | Оператор приостанавливает execution | `APPROVED` | `actor_user_id`, `hold_reason` |
| Manual resume | Оператор возобновляет после hold | `ON_HOLD` | `actor_user_id` |
| Manual cancel | Оператор отменяет action | `PENDING_APPROVAL`, `APPROVED`, `ON_HOLD`, `SCHEDULED`, `RETRY_SCHEDULED`, `RECONCILIATION_PENDING` | `actor_user_id`, `cancel_reason` |
| Manual retry | Оператор перезапускает failed action | `FAILED` | `actor_user_id`, `retry_reason` |
| Manual reconciliation | Оператор вручную помечает succeeded/failed | `RECONCILIATION_PENDING` | `actor_user_id`, `manual_override_reason`, `reconciliation_source = MANUAL` |

### Аудит ручных операций

Каждое ручное действие **обязательно** записывается в `audit_log` (схема и механизм записи — [Audit & Alerting](audit-alerting.md) §Audit). Action types: `action.approve`, `action.hold`, `action.cancel`, `action.manual_override` и т.д.

При CAS conflict (concurrent transition) — запись с `outcome = CAS_CONFLICT` всё равно создаётся. API возвращает HTTP 409 с текущим статусом action'а.

### Failed action alerting

Failed actions → alert: action ID, target entity, failure reason, attempt history. Видим в Seller Operations → working queue «failed actions».

## Симулированное исполнение (Phase F)

### Назначение

Полное прохождение pricing → execution pipeline без реальных записей в маркетплейс. Позволяет:
- Безопасно тестировать стратегии ценообразования до включения SEMI_AUTO / FULL_AUTO
- Оценить масштаб и направление ценовых изменений, которые были бы выполнены
- Калибровать guards и constraints на реальных данных без risk
- Валидировать работу pipeline end-to-end перед production launch

### Архитектурные инварианты

| Инвариант | Описание |
|-----------|----------|
| Full pipeline parity | Simulated mode проходит **все** шаги live pipeline: eligibility → signals → strategy → constraints → guards → decision → explanation → action scheduling → execution → reconciliation |
| No real writes | Simulated gateway не вызывает реальный marketplace API. Единственная точка подмены |
| Shadow-state isolation | Результат симуляции пишется в `simulated_offer_state`, не в `canonical_price_current` и не в marketplace. Каноническая модель не мутируется |
| Execution mode tracking | `price_action.execution_mode = SIMULATED / LIVE`. Все queries фильтруют по execution_mode |
| Parity guarantee | Simulated и live gateway реализуют один интерфейс. Automated tests верифицируют что при одинаковых входах оба пути проходят идентичные шаги pipeline (кроме gateway call) |

### Flow

```
Pricing Pipeline (unchanged):
  Eligibility → Signal Assembly → Strategy → Constraints → Guards → Decision → Explanation

Action Scheduling:
  Decision = CHANGE, policy execution_mode = SIMULATED
  → price_action (execution_mode = SIMULATED, status = APPROVED)

Execution:
  Worker claim → CAS: SCHEDULED → EXECUTING
  → Gateway dispatch (by execution_mode):
      LIVE      → LivePriceActionGateway → real marketplace API call
      SIMULATED → SimulatedPriceActionGateway → shadow-state update

Reconciliation:
  LIVE      → real re-read → compare expected vs actual
  SIMULATED → deterministic SUCCEEDED (shadow-state always matches intent)
```

### Gateway architecture

```
PriceActionGateway (interface)
├── LivePriceActionGateway      → WebClient → marketplace API → real outcome
└── SimulatedPriceActionGateway → UPDATE simulated_offer_state → synthetic outcome
```

Gateway selection: executor worker resolves gateway implementation по `price_action.execution_mode`. Strategy pattern, не `@Profile` (оба gateway-я активны одновременно — в одном runtime могут быть и live и simulated actions).

### SimulatedPriceActionGateway — поведение

| Аспект | Поведение |
|--------|-----------|
| Write call | Не выполняется. No HTTP request |
| Outcome | Deterministic `SUCCEEDED` с synthetic response |
| Shadow-state | `INSERT/UPDATE simulated_offer_state` с target_price |
| Latency simulation | Optional configurable delay (default: 0ms). Включается для нагрузочного тестирования |
| Error simulation | **Не симулирует** provider errors. Simulated mode тестирует **logic correctness** pipeline, не error handling. Error handling тестируется unit/integration tests |

**Обоснование deterministic SUCCEEDED:** цель симуляции — ответить на вопрос «какие ценовые решения были бы приняты и какие actions созданы?», а не «что произойдёт если marketplace API вернёт ошибку?». Стохастическая симуляция ошибок маскировала бы ценность pipeline output.

### Simulation execution contract (fast-forward)

Симуляция — это **fast-forward** через execution pipeline. Explicit отличия от live mode:

| Аспект | Live | Simulated |
|--------|------|-----------|
| Provider call | Реальный HTTP → marketplace API | Нет. Shadow-state update |
| Retry | Реальные retry с backoff через outbox | Не происходит (gateway всегда success) |
| Reconciliation | Re-read через provider API, backoff, timeout | Не происходит (deterministic SUCCEEDED) |
| Stuck-state detector | Проверяет EXECUTING/RETRY_SCHEDULED/RECONCILIATION_PENDING | Проверяет только EXECUTING (остальные не возникают) |
| Supersede | Полная логика (immediate + deferred) | Полная логика (idempotent — shared index per execution_mode) |
| Hold/Cancel | Полная поддержка | Полная поддержка (оператор может hold/cancel simulated actions) |
| Timing | Реальные задержки (30s–10min reconciliation) | Мгновенно (0ms default delay) |
| State machine transitions | PENDING_APPROVAL → ... → RECONCILIATION_PENDING → SUCCEEDED/FAILED | APPROVED → SCHEDULED → EXECUTING → SUCCEEDED |
| Audit | Полный (audit_log, attempt records) | Полный (идентичный формат, execution_mode = SIMULATED) |

**Parity scope:** parity тесты верифицируют, что при одинаковых входных данных pricing pipeline (eligibility → decision → explanation) даёт идентичные результаты для SIMULATED и LIVE. Execution pipeline различается by design (no real writes). Parity тесты НЕ покрывают retry/reconciliation timing — это live-only поведение.

### simulated_offer_state — shadow-state model

```
simulated_offer_state:
  id                          BIGSERIAL PK
  workspace_id                BIGINT FK → workspace
  marketplace_offer_id        BIGINT FK → marketplace_offer
  simulated_price             DECIMAL NOT NULL
  simulated_at                TIMESTAMPTZ NOT NULL
  price_action_id             BIGINT FK → price_action
  previous_simulated_price    DECIMAL (nullable)
  canonical_price_at_simulation DECIMAL NOT NULL
  price_delta                 DECIMAL
  price_delta_pct             DECIMAL
  created_at                  TIMESTAMPTZ
```

| Колонка | Назначение |
|---------|-----------|
| `simulated_price` | Цена, которая была бы установлена |
| `canonical_price_at_simulation` | Фактическая цена на момент симуляции (для сравнения) |
| `previous_simulated_price` | Предыдущая симулированная цена (для tracking trajectory) |
| `price_delta` / `price_delta_pct` | Разница между simulated и canonical (абсолютная и %) |

**Upsert semantics:** `ON CONFLICT (workspace_id, marketplace_offer_id) DO UPDATE` — одна строка per offer, последняя симуляция wins.

### Simulation scope

| Scope | Механизм |
|-------|----------|
| Per-policy | `price_policy.execution_mode = SIMULATED` → actions с `execution_mode = SIMULATED`. Остальные значения (SEMI_AUTO, FULL_AUTO) → `execution_mode = LIVE` |
| Per-connection | Все policies на connection в SIMULATED mode → все actions simulated |
| Mixed | Одни policies в LIVE mode (SEMI_AUTO/FULL_AUTO), другие в SIMULATED — допускается. Partial unique index `idx_price_action_active_offer` фильтрует по execution_mode, предотвращая конфликт live vs simulated active actions на один offer |

**Partial unique index (расширение):**

```sql
CREATE UNIQUE INDEX idx_price_action_active_offer_live
ON price_action (marketplace_offer_id)
WHERE execution_mode = 'LIVE'
  AND status NOT IN ('SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED', 'SUPERSEDED');

CREATE UNIQUE INDEX idx_price_action_active_offer_simulated
ON price_action (marketplace_offer_id)
WHERE execution_mode = 'SIMULATED'
  AND status NOT IN ('SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED', 'SUPERSEDED');
```

Два отдельных индекса: live и simulated actions не блокируют друг друга, но в пределах одного mode — не более одного active action per offer.

### Shadow-state lifecycle

| Событие | Действие |
|---------|----------|
| Simulated action SUCCEEDED | UPSERT `simulated_offer_state` с новой ценой |
| Policy переключена на LIVE | Shadow-state **не удаляется** (historical reference) |
| Manual reset | Оператор может очистить shadow-state для connection (bulk DELETE) |
| Retention | Бессрочно (lightweight — одна строка per offer) |

### Simulated vs Live comparison

Доступно через Seller Operations UI и REST API:

| Метрика | Вычисление |
|---------|-----------|
| Simulated actions count | `COUNT(price_action) WHERE execution_mode = 'SIMULATED' AND status = 'SUCCEEDED'` |
| Average price delta | `AVG(simulated_offer_state.price_delta_pct)` |
| Direction distribution | Сколько actions повысили / понизили / не изменили цену |
| Coverage | Доля offers с simulated state от total offers в connection |
| Estimated margin impact | `Σ(margin_at_simulated_price − margin_at_current_price)` через signal assembler |

Comparison report строится on-demand (не materialized view) — lightweight query по `simulated_offer_state` JOIN `canonical_price_current`.

### Transition: Simulated → Live

Workflow перехода от симуляции к реальному исполнению:

```
1. Policy execution_mode = SIMULATED → pricing runs создают simulated actions (auto-approved)
2. Оператор анализирует results (comparison metrics, price journal)
3. Оператор меняет policy execution_mode на SEMI_AUTO
4. Следующий pricing run создаёт LIVE actions с PENDING_APPROVAL
5. Оператор одобряет первые actions вручную
6. После N успешных cycles → переход на FULL_AUTO (safety gate)
```

**Прогрессия:** SIMULATED → SEMI_AUTO → FULL_AUTO. Каждый шаг повышает уровень автоматизации и доверия.

**Safety:** переключение на SEMI_AUTO/FULL_AUTO не изменяет существующие simulated actions. Они остаются в терминальном состоянии (SUCCEEDED) для reference. Новые pricing runs создают LIVE actions.

### Outbox integration для simulated actions

Simulated actions **проходят через outbox** (полный паритет с live):

```
Simulated action APPROVED → INSERT outbox_event (type: PRICE_ACTION_EXECUTE) → RabbitMQ
→ executor worker → resolves SimulatedPriceActionGateway → shadow-state update → SUCCEEDED
```

**Обоснование:** outbox parity гарантирует, что timing, ordering и concurrency behaviour идентичны live mode. Без outbox simulated actions выполнялись бы synchronously — скрывая потенциальные race conditions.

**Альтернатива (Phase G optimization):** synchronous simulated execution bypass outbox для performance. Допускается только после валидации parity tests.

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
| `price_action` | Action intent: target entity, target price, status, lifecycle |
| `price_action_attempt` | Попытка: timing, provider request/response, reconciliation read, outcome |
| `deferred_action` | Отложенные action'ы, ожидающие завершения in-flight action'а |
| `outbox_event` | Outbox для reliable delivery |
| `simulated_offer_state` | Shadow-state для симуляции |

### price_action — расширенная схема

```
price_action:
  id                        BIGSERIAL PK
  workspace_id              BIGINT FK → workspace
  marketplace_offer_id      BIGINT FK → marketplace_offer
  price_decision_id         BIGINT FK → price_decision
  execution_mode            ENUM (LIVE, SIMULATED)
  status                    ENUM (см. state machine)
  target_price              DECIMAL NOT NULL
  current_price_at_creation DECIMAL NOT NULL
  approved_by_user_id       BIGINT FK → app_user (nullable; NULL для auto-approval)
  approved_at               TIMESTAMPTZ (nullable)
  hold_reason               TEXT (nullable)
  cancel_reason             TEXT (nullable — обязателен при RECONCILIATION_PENDING → CANCELLED)
  superseded_by_action_id   BIGINT FK → price_action (nullable)
  reconciliation_source     ENUM (AUTO, MANUAL) (nullable; заполняется при SUCCEEDED)
  manual_override_reason    TEXT (nullable; обязателен при reconciliation_source = MANUAL)
  attempt_count             INT DEFAULT 0
  max_attempts              INT NOT NULL
  approval_timeout_hours    INT NOT NULL                          -- snapshot from price_policy at creation time
  next_attempt_at           TIMESTAMPTZ (nullable)
  created_at                TIMESTAMPTZ
  updated_at                TIMESTAMPTZ
```

### price_action_attempt — расширенная схема

```
price_action_attempt:
  id                        BIGSERIAL PK
  price_action_id           BIGINT FK → price_action
  attempt_number            INT NOT NULL
  started_at                TIMESTAMPTZ NOT NULL
  completed_at              TIMESTAMPTZ (nullable)
  outcome                   ENUM (SUCCESS, RETRIABLE_FAILURE, NON_RETRIABLE_FAILURE, UNCERTAIN)
  error_classification      ENUM (RETRIABLE_RATE_LIMIT, RETRIABLE_TRANSIENT, UNCERTAIN_TIMEOUT, NON_RETRIABLE, PROVIDER_ERROR) (nullable)
  error_message             TEXT (nullable)
  actor_user_id             BIGINT FK → app_user (nullable; NULL для system, заполнен для manual)
  provider_request_summary  JSONB (nullable)
  provider_response_summary JSONB (nullable)
  reconciliation_source     ENUM (IMMEDIATE, DEFERRED, MANUAL) (nullable)
  reconciliation_read_at    TIMESTAMPTZ (nullable)
  reconciliation_snapshot   JSONB (nullable)
  actual_price              DECIMAL (nullable)
  price_match               BOOLEAN (nullable)
  created_at                TIMESTAMPTZ
```

**provider_request_summary** — ключевые поля: endpoint, target_price, offer_id, marketplace_type.

**provider_response_summary** — ключевые поля: http_status, upload_id (WB), updated (Ozon), error_code, error_text.

**Reconciliation evidence fields:**

| Поле | Заполняется когда | Содержание |
|------|-------------------|------------|
| `reconciliation_source` | Reconciliation завершена | `IMMEDIATE` (write response ok), `DEFERRED` (read-after-write), `MANUAL` (оператор) |
| `reconciliation_read_at` | DEFERRED / MANUAL | Timestamp re-read вызова |
| `reconciliation_snapshot` | DEFERRED | Raw JSON ответа от read API маркетплейса (для аудита) |
| `actual_price` | Reconciliation прочитала цену | Фактическая цена из read response |
| `price_match` | Reconciliation завершена | `actual_price` совпадает с `target_price` (tolerance) |

### deferred_action

```
deferred_action:
  id                        BIGSERIAL PK
  workspace_id              BIGINT FK → workspace
  marketplace_offer_id      BIGINT FK → marketplace_offer
  price_decision_id         BIGINT FK → price_decision
  execution_mode            ENUM (LIVE, SIMULATED)
  deferred_reason           TEXT NOT NULL
  expires_at                TIMESTAMPTZ NOT NULL
  created_at                TIMESTAMPTZ
  UNIQUE (marketplace_offer_id, execution_mode)
```

## REST API

### Actions

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/actions` | Any role | Paginated. Filters: `?connectionId=...&marketplaceOfferId=...&status=...&executionMode=...&from=...&to=...` |
| GET | `/api/actions/{actionId}` | Any role | Детали action: all fields + attempt history |
| GET | `/api/actions/{actionId}/attempts` | Any role | Список attempts с provider request/response summaries |

### Manual interventions

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/actions/{actionId}/approve` | PRICING_MANAGER, ADMIN, OWNER | Approve: PENDING_APPROVAL → APPROVED. Bulk: `POST /api/actions/bulk-approve` Body: `{ actionIds: [...] }` |
| POST | `/api/actions/{actionId}/reject` | PRICING_MANAGER, ADMIN, OWNER | Reject: PENDING_APPROVAL → CANCELLED. Body: `{ cancelReason }` |
| POST | `/api/actions/{actionId}/hold` | OPERATOR, PRICING_MANAGER, ADMIN, OWNER | Hold: APPROVED → ON_HOLD. Body: `{ holdReason }` |
| POST | `/api/actions/{actionId}/resume` | OPERATOR, PRICING_MANAGER, ADMIN, OWNER | Resume: ON_HOLD → APPROVED |
| POST | `/api/actions/{actionId}/cancel` | OPERATOR, PRICING_MANAGER, ADMIN, OWNER | Cancel. Body: `{ cancelReason }`. Допустимые состояния: PENDING_APPROVAL, APPROVED, ON_HOLD, SCHEDULED, RETRY_SCHEDULED, RECONCILIATION_PENDING |
| POST | `/api/actions/{actionId}/retry` | PRICING_MANAGER, ADMIN, OWNER | Retry failed: FAILED → создаёт новый action (re-run). Body: `{ retryReason }` |
| POST | `/api/actions/{actionId}/reconcile` | ADMIN, OWNER | Manual reconciliation. Body: `{ outcome: "SUCCEEDED" / "FAILED", manualOverrideReason }` |

### Simulation

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/simulation/comparison` | Any role | Simulated vs live comparison per connection. Filter: `?connectionId=...` |
| GET | `/api/simulation/preview` | Any role | Decision-level simulation preview: simulated prices for a specific price decision. Filter: `?decisionId=...` |
| DELETE | `/api/simulation/shadow-state` | PRICING_MANAGER, ADMIN, OWNER | Reset shadow-state for connection. Body: `{ connectionId }` |

## Связанные модули

- [Pricing](pricing.md) — создаёт actions из decisions
- [Promotions](promotions.md) — promo actions исполняются тем же `datapulse-executor-worker` (отдельная queue `promo.execution`); shared outbox infrastructure, отдельный упрощённый lifecycle
- [Integration](integration.md) — write-адаптеры для provider API
- [Seller Operations](seller-operations.md) — failed action queues, price journal
- [Tenancy & IAM](tenancy-iam.md) — approval/hold определяются ролями
- [Audit & Alerting](audit-alerting.md) — audit записи для CAS-переходов и manual operations; alert events (action failed, stuck state, reconciliation failed, poison pill)
