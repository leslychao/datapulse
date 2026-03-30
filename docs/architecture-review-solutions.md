# Datapulse — Решения по gaps архитектурного анализа

**Дата:** 2026-03-30
**Входной документ:** `architecture-review-2026-03-30.md`

Для каждого gap — глубокий анализ корневой причины, оценка вариантов и рекомендуемое решение.

---

## D-1. Outbox poller single-instance

### Корневая причина

Outbox poller — это `@Scheduled` метод с фиксированным `fixedDelay = 1000ms`, который выполняет:

```
SELECT id, payload_json, exchange_name, routing_key
FROM outbox_event
WHERE status = 'PENDING'
ORDER BY id
LIMIT :batchSize
FOR UPDATE SKIP LOCKED
```

`SKIP LOCKED` гарантирует, что два конкурирующих poller'а не возьмут одну строку. Но оба тратят connection и CPU на poll'ing одной таблицы.

### Почему это не блокер

При текущем масштабе (единицы аккаунтов, outbox ≤ сотни строк в час) — overhead от двух poller'ов = 1 extra SQL query/sec. PostgreSQL обрабатывает это без заметного impact.

`SKIP LOCKED` — это **правильный** механизм для single-DB multi-consumer. Leader election (через Redis, ZooKeeper, или `SELECT ... FOR UPDATE` на lock-таблице) добавляет инфраструктурную зависимость и failure mode без решения реальной проблемы.

### Решение

**Оставить как есть.** Добавить в `execution.md` одно предложение:

> При горизонтальном масштабировании worker'ов несколько outbox poller'ов работают корректно: `FOR UPDATE SKIP LOCKED` обеспечивает single-winner semantics. Дополнительная координация (leader election) не требуется для ожидаемого масштаба (≤ 3 инстанса на entrypoint).

**Почему не leader election:** добавляет Redis/ZooKeeper как critical dependency для outbox delivery. Outbox — reliability-critical path. Чем меньше зависимостей, тем надёжнее. `SKIP LOCKED` работает без дополнительной инфраструктуры.

---

## D-2. Poison pill loop (RabbitMQ consumer)

### Глубокий анализ

Текущая конфигурация:

```
AcknowledgeMode.AUTO
prefetchCount=1
defaultRequeueRejected=true
```

Цепочка событий при poison pill:

```
1. Outbox poller → publish message → RabbitMQ
2. Consumer receives message
3. Handler throws unchecked exception (NPE, ClassCastException, corrupt JSON)
4. Spring AMQP: AUTO mode → basic.reject(requeue=true)
5. RabbitMQ requeues message → go to step 2
6. Loop forever. prefetchCount=1 → queue blocked.
```

Это **не** теоретический сценарий. Достаточно одного `outbox_event` с невалидным `payload_json` (например, encoding issue при INSERT, или code change в consumer incompatible со старым payload format).

### Оценка вариантов

**Вариант A: DLQ на уровне RabbitMQ (x-dead-letter-exchange + x-death count)**

```
Queue etl.execution:
  x-dead-letter-exchange: etl.execution.dlq-exchange
  x-delivery-limit: 3
```

После 3 reject'ов → message перемещается в DLQ. Consumer code не меняется.

Плюсы: RabbitMQ-native, нулевые изменения в Java-коде.
Минусы: `x-delivery-limit` — quorum queue feature (RabbitMQ 3.12+). Classic queue не поддерживает. Нужно мигрировать на quorum queues или использовать другой механизм.

**Вариант B: `defaultRequeueRejected=false` + retry в consumer code**

```java
@RabbitListener(queues = "etl.execution")
public void handle(EtlStepMessage message) {
    try {
        processStep(message);
    } catch (RetriableException e) {
        // Уже обработано: CAS → RETRY_SCHEDULED → outbox → RabbitMQ delayed retry
        // Message не requeue'd — outbox создаст новый message
    } catch (Exception e) {
        log.error("Poison pill detected: messageId={}, error={}", message.getId(), e.getMessage(), e);
        markOutboxError(message.getCorrelationId(), e);
        // Message ACK'd (consumed), не requeue'd
        // outbox_event.status = ERROR → poller пропустит
        // Alert: manual investigation
    }
}
```

Плюсы: работает с classic queues, poison pill consumed и logged, остальные messages обрабатываются.
Минусы: нужен catch-all в каждом consumer.

**Вариант C: Spring Retry + DLQ**

```java
@Bean
SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(...) {
    factory.setDefaultRequeueRejected(false);
    factory.setAdviceChain(RetryInterceptorBuilder.stateless()
        .maxAttempts(3)
        .backOffOptions(1000, 2.0, 5000)
        .recoverer(new RejectAndDontRequeueRecoverer())
        .build());
}
```

Плюсы: стандартный Spring паттерн, retry с backoff в consumer, после max attempts — reject без requeue.
Минусы: retry происходит в consumer thread (блокирует thread на backoff), не через outbox delayed retry.

### Решение

**Вариант B** — `defaultRequeueRejected=false` + explicit error handling в consumer.

Обоснование:
1. Система **уже** имеет retry mechanism через outbox (CAS → RETRY_SCHEDULED → INSERT outbox с TTL). RabbitMQ retry не нужен — outbox retry надёжнее (DB-first).
2. Poison pill = non-retriable error. Consumer должен поглотить message, залогировать, пометить outbox_event как ERROR, и продолжить обработку следующих messages.
3. Не требует quorum queues.
4. Соответствует DB-first philosophy: retry truth в PostgreSQL, не в broker.

Добавить в `execution.md` и `etl-pipeline.md`:

```
### Consumer error handling

defaultRequeueRejected=false. При unhandled exception:
1. Message consumed (ACK), не requeue'd.
2. outbox_event.status → ERROR, last_error записан.
3. log.error с correlation_id.
4. Alert: «poison pill detected».

Retriable errors обрабатываются через outbox retry (CAS → RETRY_SCHEDULED).
Non-retriable errors → message consumed, investigation via outbox_event.last_error.
```

---

## D-3. Reconciliation timing и triggering

### Глубокий анализ

Текущее описание reconciliation в `execution.md`:

```
Action executed → provider response →
  if confirms immediate success → SUCCEEDED
  if uncertain → RECONCILIATION_PENDING →
    → reconciliation worker re-reads current state →
    → compares expected vs actual →
    → SUCCEEDED (match) / FAILED (mismatch or timeout)
```

Проблема: между «RECONCILIATION_PENDING» и «reconciliation worker re-reads» — провал. Кто инициирует re-read? Когда? Сколько раз? Что если re-read тоже uncertain?

Outbox event type `RECONCILIATION_CHECK` существует в schema — значит авторы планировали outbox-driven reconciliation. Но flow creation этого event'а не описан.

### Контекст: когда возникает RECONCILIATION_PENDING

Два сценария:

1. **WB Price Write (async).** `POST /api/v2/upload/task` возвращает `uploadId`. Poll `GET /api/v2/history/goods/task` — может показать пустой `historyGoods` (ещё не обработан). Текущий polling: wait 3s → poll → wait 4s → poll. Total: 7s. Если после двух poll'ов всё ещё пусто → uncertain → RECONCILIATION_PENDING.

2. **Ozon Price Write (sync).** `POST /v1/product/import/prices` возвращает `updated: true`. Но `updated: true` = «принято к обработке», не «применено на витрине». Для строгой reconciliation нужен re-read через `/v5/product/info/prices`.

### Решение

Reconciliation — двухэтапный процесс:

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
    ttl_millis: 30000  // первая проверка через 30 секунд
  }
→ outbox poller → RabbitMQ (action.reconciliation.wait queue, DLX after TTL)
→ reconciliation consumer:
    1. Read current price from marketplace (GET prices endpoint)
    2. Compare actual vs expected
    3. if match → CAS: RECONCILIATION_PENDING → SUCCEEDED
    4. if mismatch AND attempt < max_reconciliation_attempts (default: 3):
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

**RabbitMQ topology (дополнение):**

```
Exchanges (direct):
  action.reconciliation       ← reconciliation dispatch
  action.reconciliation.wait  ← delayed reconciliation (DLX)

Queues:
  action.reconciliation       ← reconciliation consumer
  action.reconciliation.wait  ← TTL → DLX → action.reconciliation
```

Добавить в `execution.md` как секцию «Reconciliation — triggering и timing».

---

## D-4. Pricing reads из двух stores без snapshot isolation

### Глубокий анализ

Signal assembler при batch pricing run:

```
1. PostgreSQL reads: current_price, cogs, product_status, stocks, locks, freshness
2. ClickHouse reads: avg_commission, avg_logistics, return_rate, ad_cost
3. Per-SKU: combine signals → strategy → decision
```

Между reads 1 и 2 — нет транзакционной границы. Между reads и ETL writes — нет координации.

Но проблема **переоценена** в ревью. Вот почему:

**ClickHouse signals — historical aggregates за 30 дней.** `avg_commission_pct` = среднее за `commission_lookback_days` (default: 30). ETL обновляет `fact_finance` 1-4 раза в день. Одна дополнительная строка в 30-дневном окне меняет avg на доли процента. Pricing decision не чувствителен к такой дельте.

**PostgreSQL state — point-in-time.** `current_price` из `canonical_price_snapshot` — это snapshot, не streaming. Если ETL обновил цену, а pricing worker прочитал старую — decision будет с `current_price` до обновления. Это **correct** behavior: pricing должен работать с committed data, не с in-flight.

**Реальный risk:** не inconsistency между stores, а **concurrent ETL + pricing run на одном connection**. ETL обновляет canonical_price_snapshot для SKU A и SKU B в разных UPSERT batches. Pricing worker читает обновлённый SKU A но старый SKU B. Для batch pricing (все SKU одного connection) — это data skew.

### Решение

**Инвариант: pricing run запускается только после завершения ETL sync для данного connection.**

Механизм (уже почти задокументирован в «Post-sync trigger»):

```
ETL sync completed (job_execution.status = COMPLETED)
→ UPDATE marketplace_sync_state SET last_success_at = NOW()
→ Emit event: SyncCompleted(connectionId)
→ Pricing run triggered for connectionId
```

Pricing run при запуске проверяет:

```sql
SELECT 1 FROM job_execution
WHERE marketplace_connection_id = :connectionId
  AND status = 'IN_PROGRESS'
LIMIT 1
```

Если есть active ETL → pricing run **отложен** (не отклонён — запланирован after sync completion).

Добавить в `pricing.md` → секция «Pricing run → Триггеры → Post-sync»:

> **Инвариант:** pricing run для connection X не запускается, пока для того же connection X есть ETL job_execution в статусе IN_PROGRESS. Post-sync trigger создаёт pricing run только после успешного завершения ETL sync. Manual и scheduled триггеры проверяют отсутствие активного sync перед запуском.

Этого достаточно. Cross-store snapshot isolation **не нужна** — ClickHouse signals = 30-day aggregates, нечувствительны к одному обновлению.

---

## D-5. ETL canonical UPSERT vs ClickHouse materialization boundary

### Глубокий анализ

```
Step 4: UPSERT canonical → PostgreSQL  (committed)
Step 5: INSERT facts    → ClickHouse   (may fail)
Step 6: UPDATE marketplace_sync_state  (not reached)
```

При failure step 5:
- PostgreSQL canonical: **updated** ✓
- ClickHouse facts: **not updated** ✗
- marketplace_sync_state: **not updated** → stale data guard blocks pricing ✓

Система **безопасна**: stale data guard — естественный circuit breaker.

При retry (outbox redelivery):
- Step 4: UPSERT + `IS DISTINCT FROM` = no-churn ✓
- Step 5: ClickHouse ReplacingMergeTree = idempotent insert ✓
- Step 6: updates marketplace_sync_state ✓

### Реальный gap

Не сам failure mode, а **отсутствие observability** для этого состояния. Оператор видит «stale data» alert, но не знает **почему** — ClickHouse down? API маркетплейса не ответил? Network issue?

### Решение

**Нет изменений в архитектуре.** Добавить в `runbook.md`:

```markdown
### FM-9: ClickHouse materialization failure

**Симптомы:** Stale data alert. Canonical data в PostgreSQL свежая
(canonical_price_snapshot.captured_at < threshold), но ClickHouse facts не обновлены.
job_execution в статусе FAILED или IN_PROGRESS дольше ожидаемого.

**Диагностика:**
1. `SELECT status, error_details FROM job_execution WHERE status IN ('FAILED', 'IN_PROGRESS') ORDER BY started_at DESC LIMIT 10`
2. Проверить доступность ClickHouse: `SELECT 1 FROM system.one`
3. Проверить логи ingest-worker на ClickHouse connection errors

**Восстановление:**
1. Восстановить доступность ClickHouse
2. ETL автоматически retry через outbox
3. При завершении sync — marketplace_sync_state обновится → stale data guard снимется

**Влияние:** Canonical truth (PostgreSQL) не пострадала. Pricing заблокирован
stale data guard (correct behavior). Analytics screens показывают устаревшие данные.
```

---

## D-6. price_action grain: marketplace_offer, не product

### Глубокий анализ

Иерархия:

```
product_master (cross-marketplace)
  └── seller_sku (артикул продавца)
        └── marketplace_offer (конкретное предложение на WB / Ozon)
```

Price policy assignment: per connection / category / SKU. Pricing decision: per marketplace_offer_id. Это **by design** и **correct**: цена на WB и Ozon для одного товара — разная (разные комиссии, логистика, конкуренция).

Gap: `seller-operations.md` не определяет grain operational grid.

### Решение

Добавить в `seller-operations.md` → секция «Operational Grid»:

```markdown
### Grain

Каждая строка operational grid = один `marketplace_offer` (конкретное предложение
конкретного товара на конкретном маркетплейсе через конкретное подключение).

Один seller_sku с предложениями на WB и Ozon → **две строки** в гриде.
Столбец «Маркетплейс» + «Подключение» — обязательные visible columns.

Группировка по seller_sku доступна через saved view с group-by.
```

---

## D-7. Approval timeout — механизм expiration

### Глубокий анализ

Без expiration:

```
t=0h:  pricing run → decision CHANGE → action PENDING_APPROVAL (target: 1000₽)
t=24h: ETL sync → canonical_price changed → pricing run → decision CHANGE → action PENDING_APPROVAL (target: 1050₽)
t=48h: ещё один action PENDING_APPROVAL (target: 980₽)
...
t=72h: оператор открывает UI → 3 pending actions на один SKU
```

Оператор одобряет самый старый (target: 1000₽), хотя данные уже изменились дважды. На маркетплейс пишется устаревшая цена.

### Оценка вариантов

**Вариант A: Scheduled job**

```
@Scheduled(fixedDelay = 3600000) // каждый час
void expireStaleActions() {
    int expired = jdbcTemplate.update("""
        UPDATE price_action
        SET status = 'EXPIRED', updated_at = NOW()
        WHERE status = 'PENDING_APPROVAL'
          AND created_at < NOW() - (
            SELECT approval_timeout_hours * INTERVAL '1 hour'
            FROM price_policy WHERE id = price_action.price_policy_id
          )
        """);
    if (expired > 0) log.info("Expired {} stale actions", expired);
}
```

Плюсы: простой, надёжный, CAS не нужен (PENDING_APPROVAL → EXPIRED — единственный автоматический transition из этого статуса).
Минусы: до 1 часа задержки между actual expiration и status change.

**Вариант B: Outbox event с TTL при создании action**

```
action PENDING_APPROVAL created →
INSERT outbox_event {
  type: APPROVAL_EXPIRY_CHECK,
  ttl_millis: approval_timeout_hours * 3600000,
  payload: { action_id }
}
```

Плюсы: точное timing.
Минусы: 72-часовой TTL в RabbitMQ = message висит в wait queue 3 дня. При рестарте RabbitMQ — messages могут потеряться (non-durable wait queue) или создать spike при recovery.

**Вариант C: Lazy check + scheduled cleanup**

При каждом UI-запросе списка pending actions — filter out expired. Scheduled job — раз в час — bulk update status для гигиены.

### Решение

**Вариант A + дополнение: supersede при создании нового action.**

```
При Action Scheduling (pricing.md → step 8):
1. Проверить: есть ли non-terminal price_action для того же marketplace_offer_id?
2. Если есть в PENDING_APPROVAL → CAS: PENDING_APPROVAL → SUPERSEDED
3. Создать новый action PENDING_APPROVAL

SUPERSEDED — новый terminal status. Означает: «заменён более свежим decision».
```

Это решает **обе** проблемы:
- Expiration: scheduled job (1h interval) для actions, которые не были superseded.
- Staleness: supersede автоматически при появлении свежего decision.

Добавить в `execution.md`:

```markdown
### Supersede policy

При создании нового price_action для marketplace_offer_id, если существует
price_action в статусе PENDING_APPROVAL для того же marketplace_offer_id:
→ CAS: PENDING_APPROVAL → SUPERSEDED (terminal)

SUPERSEDED означает «заменён более свежим решением». Не является ошибкой.

### Expiration mechanism

Scheduled job (interval: 1 час). Переводит actions из PENDING_APPROVAL в EXPIRED,
если created_at + approval_timeout_hours < NOW().

Оба механизма — defense in depth: supersede при нормальной работе,
expiration — fallback при отсутствии свежих pricing runs.
```

State machine update:

```
PENDING_APPROVAL → APPROVED / EXPIRED / CANCELLED / SUPERSEDED
```

---

## D-8. WB Price Write BROKEN — risk register gap

### Решение

Добавить в `risk-register.md`:

```markdown
### R-16: WB Price Write недоступен

| Параметр    | Значение |
|-------------|----------|
| Риск        | WB Price Write endpoint недоступен: DNS migration + token scope |
| Вероятность | Подтверждён (2026-03-30) |
| Влияние     | Высокое — Phase D Execution для WB невозможен |
| Митигация   | F-1: обновить host на `discounts-prices-api.wildberries.ru`. F-2: получить production токен с write scope |
| Detection   | DNS resolution failure; HTTP 401 на write endpoint |
| Blocking    | Phase D (Execution) для WB |
| Not blocking | Phase A-C; Ozon Execution |
```

---

## D-9. R-06 устарел

### Решение

Обновить `risk-register.md` → R-06:

```markdown
### R-06: WB Returns endpoint — RESOLVED (2026-03-30)

| Параметр    | Значение |
|-------------|----------|
| Статус      | **RESOLVED** |
| Причина     | Root cause: формат даты `dateFrom`/`dateTo` — date-only (`YYYY-MM-DD`), не datetime |
| Дата        | 2026-03-30 |
| Влияние     | Нет — endpoint работает с корректными параметрами |
```

---

## D-10. Workspace isolation — DB-level enforcement

### Глубокий анализ

Текущая модель: application-level isolation через `@PreAuthorize` + `accessService.canRead(#connectionId)`. Это проверяет принадлежность connection к workspace пользователя **на уровне API endpoint**.

Но внутри сервисного слоя — JDBC queries по canonical_order, fact_finance, price_decision — содержат ли они `WHERE workspace_id = :wsId`? Это convention, не enforcement.

### Оценка вариантов

**Вариант A: RLS (Row-Level Security)**

```sql
ALTER TABLE canonical_order ENABLE ROW LEVEL SECURITY;
CREATE POLICY workspace_isolation ON canonical_order
  USING (workspace_id = current_setting('app.workspace_id')::bigint);
```

Плюсы: database-level enforcement, невозможно обойти.
Минусы: требует `SET LOCAL app.workspace_id` в каждом request (через Hibernate filter или connection interceptor). Добавляет complexity в connection management. Не работает для cross-workspace admin queries (если понадобятся).

**Вариант B: Convention + ArchUnit enforcement**

```java
@ArchTest
void allRepositoryMethodsTakeWorkspaceId(JavaClasses classes) {
    methods().that().areDeclaredInClassesThat().areAnnotatedWith(Repository.class)
        .and().arePublic()
        .should().haveRawParameterTypes(new DescribedPredicate<>("include workspaceId") {
            public boolean test(List<JavaClass> params) {
                return params.stream().anyMatch(p -> p.getSimpleName().equals("Long") || ...);
            }
        });
}
```

Плюсы: compile-time CI check, нет runtime overhead, нет DB-level complexity.
Минусы: проверяет сигнатуру, не SQL. Можно обойти через transitive call.

**Вариант C: Request-scoped context + base repository method**

```java
@Component
@RequestScope
public class WorkspaceContext {
    private Long workspaceId;
    // set by Spring Security filter from JWT claims
}

// Base repository pattern:
public abstract class WorkspaceAwareRepository {
    protected NamedParameterJdbcTemplate jdbc;
    protected WorkspaceContext context;

    protected MapSqlParameterSource params() {
        return new MapSqlParameterSource("workspaceId", context.getWorkspaceId());
    }
}
```

### Решение

**Вариант C** — request-scoped context + base repository pattern.

Обоснование:
1. Coding style уже определяет: «Получение текущего пользователя в сервисах: через request-scoped context-бин». `WorkspaceContext` — расширение этого паттерна.
2. Каждый repository query **обязан** использовать `context.getWorkspaceId()` в WHERE. Base class делает это default.
3. ArchUnit тест (вариант B) — дополнительный safety net в CI.
4. RLS — overengineering для единиц-десятков workspace'ов и одного разработчика.

Добавить в `tenancy-iam.md`:

```markdown
### Workspace isolation enforcement

1. `WorkspaceContext` — request-scoped бин, устанавливается из JWT claims в security filter.
2. Все repository queries обязаны фильтровать по `workspace_id` из `WorkspaceContext`.
3. Worker'ы (без HTTP request): `WorkspaceContext` устанавливается из `job_execution.workspace_id` перед обработкой.
4. CI: ArchUnit тест проверяет, что все public repository methods принимают workspaceId или используют WorkspaceContext.
5. RLS — не используется (может быть добавлен позже без архитектурных изменений).
```

---

## D-11. strategy_params / guard_config JSONB validation

### Глубокий анализ

`strategy_params` для TARGET_MARGIN:

```json
{
  "target_margin_pct": 0.25,
  "commission_source": "AUTO_WITH_MANUAL_FALLBACK",
  "commission_manual_pct": 0.15,
  "commission_lookback_days": 30,
  "commission_min_transactions": 5,
  "logistics_source": "AUTO",
  "rounding_step": 10,
  "rounding_direction": "FLOOR"
}
```

Формула: `target_price = COGS / (1 − target_margin_pct − effective_cost_rate)`.

Если `target_margin_pct = 0.85` и `effective_cost_rate = 0.20`:
```
denominator = 1 − 0.85 − 0.20 = −0.05
target_price = 500 / (−0.05) = −10000
```

Guard'ы: `min_price` может поймать если задан. `min_margin` не поймает — strategy уже вернула nonsensical price.

### Решение

**Java-level validation при десериализации strategy_params.**

Coding style уже определяет: records с Jakarta Validation constraints для request-объектов. `strategy_params` десериализуется в record при загрузке policy.

```java
public record TargetMarginParams(
    @NotNull @DecimalMin("0.01") @DecimalMax("0.80")
    BigDecimal targetMarginPct,

    @NotNull
    CommissionSource commissionSource,

    @DecimalMin("0.01") @DecimalMax("0.50")
    BigDecimal commissionManualPct,  // nullable

    @Min(7) @Max(365)
    int commissionLookbackDays,

    @Min(1) @Max(100)
    int commissionMinTransactions,

    @NotNull
    LogisticsSource logisticsSource,

    @DecimalMin("0")
    BigDecimal logisticsManualAmount,  // nullable

    boolean includeReturnAdjustment,
    boolean includeAdCost,

    @NotNull @DecimalMin("1") @DecimalMax("100")
    BigDecimal roundingStep,

    @NotNull
    RoundingDirection roundingDirection
) {}
```

Ключевые constraints:
- `target_margin_pct` ≤ 0.80 — **hard ceiling**. 80% margin при любом effective_cost_rate > 0 даёт отрицательный знаменатель.
- `commission_manual_pct` ≤ 0.50 — комиссия маркетплейса не бывает > 50%.

**Дополнительно: runtime guard в strategy evaluator:**

```java
BigDecimal denominator = BigDecimal.ONE
    .subtract(params.targetMarginPct())
    .subtract(effectiveCostRate);

if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
    return Decision.skip("Невозможно рассчитать цену: target margin + cost rate ≥ 100%");
}
```

Это defense-in-depth: validation ловит при создании policy, runtime guard — при execution (на случай если cost rates изменились после создания policy).

Добавить в `pricing.md`:

```markdown
### Validation contract для strategy_params

strategy_params десериализуется в typed record с Jakarta Validation constraints
при создании/обновлении price_policy. Невалидные params → 400 Bad Request.

#### TARGET_MARGIN constraints

| Параметр | Constraint | Обоснование |
|----------|-----------|-------------|
| target_margin_pct | [0.01, 0.80] | > 0.80 при любых cost rates → отрицательный знаменатель |
| commission_manual_pct | [0.01, 0.50] | Комиссия МП не превышает 50% |
| commission_lookback_days | [7, 365] | < 7 — недостаточно данных; > 365 — irrelevant |
| rounding_step | [1, 100] | Шаг округления в рублях |

#### Runtime safety guard

Pricing strategy evaluator проверяет `denominator > 0` перед делением.
При denominator ≤ 0 → decision = SKIP с reason
«target margin + effective cost rate ≥ 100%».
```

---

## E-1. Concurrent pricing decisions на один SKU

### Глубокий анализ

Сценарий:

```
t=0s: Manual pricing run starts, reads canonical state
t=1s: Post-sync pricing run starts, reads same state
t=2s: Manual run: decision CHANGE, target=1000₽, action PENDING_APPROVAL (id=101)
t=3s: Post-sync run: decision CHANGE, target=1000₽, action PENDING_APPROVAL (id=102)
→ Два идентичных pending actions на один SKU
```

Или хуже: между t=0 и t=1 прошёл ETL sync, и два run'а читают **разные** данные → два action'а с разным target_price.

### Решение

**Supersede policy (уже описана в D-7) решает этот gap.**

При Action Scheduling: если существует non-terminal action для того же marketplace_offer_id → supersede старый.

Дополнительно: **partial index** для быстрой проверки:

```sql
CREATE UNIQUE INDEX idx_price_action_active_offer
ON price_action (marketplace_offer_id)
WHERE status NOT IN ('SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED', 'SUPERSEDED');
```

Unique partial index гарантирует: **не более одного active action на marketplace_offer** на уровне БД. При INSERT duplicate → PostgreSQL rejects → application catches и supersedes existing.

---

## E-2. Ozon per-product rate limit (10 updates/hour)

### Решение

**Adapter-level rate limiter**, не execution worker. Обоснование:

1. Rate limit = provider constraint. Adapter — anti-corruption boundary для provider constraints.
2. Token-bucket rate limiter уже задокументирован в `integration.md` для read endpoints. Write endpoints — аналогично.

```
OzonPriceCommandAdapter:
  rate_limiter: per_product_id, 10 per hour
  on rate limit exceeded: RetriableException → CAS RETRY_SCHEDULED → backoff
```

Добавить в `integration.md` → Rate limiting → Ozon:

```markdown
| API Group       | Limit              | Scope        | Источник      |
|-----------------|--------------------|--------------|-----------    |
| Price Write     | 10 updates/hour    | per product  | confirmed-docs|
```

---

## E-3. ClickHouse schema evolution

### Решение

**Numbered SQL migration scripts** (аналог Flyway для ClickHouse).

```
clickhouse/migrations/
  V001__initial_dims.sql
  V002__initial_facts.sql
  V003__initial_marts.sql
  V004__add_advertising_fact.sql
```

Execution: при старте ingest-worker проверяет таблицу `schema_version` в ClickHouse, применяет непримёнённые миграции.

Для non-backward-compatible changes (изменение sorting key):

```sql
-- V010__recreate_fact_finance_new_sorting_key.sql
CREATE TABLE fact_finance_v2 (...) ENGINE = ReplacingMergeTree(ver)
ORDER BY (account_id, source_platform, operation_id, finance_date, new_column);

INSERT INTO fact_finance_v2 SELECT ... FROM fact_finance;
RENAME TABLE fact_finance TO fact_finance_old, fact_finance_v2 TO fact_finance;
-- DROP TABLE fact_finance_old после валидации
```

Re-materialization из canonical: always possible (canonical = PostgreSQL source of truth).

Добавить в `analytics-pnl.md`:

```markdown
### Schema evolution

ClickHouse schema управляется numbered SQL migration scripts.
При несовместимых изменениях (sorting key): CREATE new → INSERT ... SELECT → RENAME.
Rollback: re-materialization из canonical layer (PostgreSQL → ClickHouse).
```

---

## E-4. cost_profile lifecycle

### Решение

Добавить в `etl-pipeline.md`:

```markdown
### cost_profile lifecycle

| Аспект | Описание |
|--------|----------|
| Источник | Ручной ввод (Phase A/B). Bulk CSV import (Phase E). |
| Grain | Per seller_sku. Не per marketplace_offer. |
| Версионирование | SCD2: valid_from, valid_to. При обновлении: закрыть текущую запись, создать новую. |
| При отсутствии | Pricing: eligibility SKIP («Себестоимость не задана»). P&L: COGS = 0 (explicit, помечено в UI). |
| Validation | cost_price > 0, currency = RUB. |
| API | CRUD через datapulse-api: POST /api/cost-profiles, PUT, bulk import CSV. |
| Permission | ADMIN, PRICING_MANAGER. |
```

---

## E-5. End-to-end latency budget

### Решение

Добавить в `non-functional-architecture.md`:

```markdown
### Target latency budget (Phase C)

| Сегмент | Target | Обоснование |
|---------|--------|-------------|
| API sync → raw (S3) | 1-10 мин | Зависит от rate limits МП |
| Raw → canonical | 1-5 мин | Batch 500, streaming parse |
| Canonical → ClickHouse | 1-3 мин | Batch INSERT |
| ClickHouse → pricing run | < 1 мин | Post-sync trigger |
| Pricing run (1000 SKU) | < 30 сек | Batch signal assembly |
| Action → execution | 1-7 сек | Outbox poll (1s) + provider call |
| Execution → reconciliation | 30s-10 мин | Deferred reconciliation |
| **Total: data change → confirmed price update** | **~15-30 мин** | При штатной работе |

Это **не SLA** — это target для engineering decisions. SLA определяется позже
на основе эмпирических данных после Phase A-B.
```

---

## Сводка изменений по документам

| Документ | Изменения |
|----------|-----------|
| `execution.md` | Consumer error handling (D-2); reconciliation timing (D-3); supersede policy + expiration (D-7, E-1) |
| `pricing.md` | Pricing run инвариант (D-4); strategy_params validation (D-11) |
| `etl-pipeline.md` | cost_profile lifecycle (E-4) |
| `seller-operations.md` | Grid grain = marketplace_offer (D-6) |
| `tenancy-iam.md` | Workspace isolation enforcement (D-10) |
| `analytics-pnl.md` | ClickHouse schema evolution (E-3) |
| `risk-register.md` | R-06 RESOLVED (D-9); R-16 WB Price Write (D-8) |
| `non-functional-architecture.md` | Latency budget (E-5) |
| `runbook.md` | FM-9 ClickHouse failure (D-5) |
