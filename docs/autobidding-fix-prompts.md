# Autobidding — Промпты для фикса всех найденных проблем

**Источник:** аудит `docs/modules/autobidding.md` + сопоставление с кодовой базой
**Дата аудита:** 2026-04-12
**Порядок исполнения:** строго по порядку чатов (P0 → P1 → P2 → тесты → документация)

---

## Как пользоваться этим документом

1. Каждый раздел — **один отдельный чат** (отдельная сессия агента).
2. Промпт скопировать целиком и вставить в новый чат.
3. Выполнять **строго по порядку** — некоторые чаты зависят от результатов предыдущих.
4. После каждого чата — **убедиться, что проект собирается** (`./gradlew build`).
5. После каждого чата — **сделать коммит** с описанием из заголовка чата.

---

# P0 — КРИТИЧЕСКИЕ БАГИ

---

## Чат 1. P0: PAUSE и RESUME решения не создают bid actions

```
## Задача

В автобиддинге есть критический баг: когда система принимает решение PAUSE (остановить рекламу товара) или RESUME (возобновить), это решение записывается в bid_decision, но **bid action НЕ создаётся** — ставка на маркетплейсе не меняется. Товар с нулевым остатком продолжает рекламироваться.

## Что сломано

Файл: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/BiddingActionScheduler.java`

Строки 27-31:
```java
private static final List<BidDecisionType> ACTIONABLE_TYPES = List.of(
    BidDecisionType.BID_UP,
    BidDecisionType.BID_DOWN,
    BidDecisionType.SET_MINIMUM,
    BidDecisionType.EMERGENCY_CUT);
```

PAUSE и RESUME **не входят** в ACTIONABLE_TYPES. Фильтр на строке 49 отбрасывает эти решения, и для них никогда не создаётся BidActionEntity.

## Что нужно сделать

### 1. Добавить PAUSE и RESUME в ACTIONABLE_TYPES

В `BiddingActionScheduler.java` добавь PAUSE и RESUME в список:

```java
private static final List<BidDecisionType> ACTIONABLE_TYPES = List.of(
    BidDecisionType.BID_UP,
    BidDecisionType.BID_DOWN,
    BidDecisionType.PAUSE,
    BidDecisionType.RESUME,
    BidDecisionType.SET_MINIMUM,
    BidDecisionType.EMERGENCY_CUT);
```

### 2. Обработка targetBid для PAUSE

Для PAUSE решений targetBid в BiddingStrategyResult = null. При создании action нужно установить targetBid:
- Для PAUSE: targetBid = 0 (или minBid маркетплейса, если 0 не поддерживается)
- Для RESUME: targetBid уже задан в BiddingResumeEvaluator (last.getCurrentBid())

В методе `createAction()` добавь логику:

```java
if (decision.getDecisionType() == BidDecisionType.PAUSE) {
  action.setTargetBid(0); // маркетплейс-адаптеры обработают корректно
} else {
  action.setTargetBid(decision.getTargetBid());
}
```

### 3. Marketplace gateway-адаптеры должны уметь обрабатывать PAUSE

Проверь, что WbBidCommandAdapter, OzonBidCommandAdapter, YandexBidCommandAdapter корректно обработают bid = 0:
- WB: ставка 0 = товар не участвует в аукционе (проверить контракт в `docs/provider-api-specs/wb-advertising-bidding-contracts.md`)
- Ozon: аналогично проверить `docs/provider-api-specs/ozon-advertising-bidding-contracts.md`
- Yandex: проверить `docs/provider-api-specs/yandex-read-contracts.md`

Если API не поддерживает ставку 0, нужно устанавливать минимально допустимую (minBid из signals) — это де-факто пауза.

### 4. Тесты

Напиши unit-тесты в `BiddingActionSchedulerTest.java`:
- `should_createAction_when_decisionType_isPAUSE` — PAUSE decision создаёт action с targetBid=0
- `should_createAction_when_decisionType_isRESUME` — RESUME decision создаёт action с targetBid из decision
- `should_supersede_existingActions_when_newPAUSE` — PAUSE supersede-ит старые pre-exec actions

### Контекст

- Бизнес-спек: `docs/modules/autobidding.md`, разделы 14.4 (PAUSE), 14.5 (RESUME), 16.2 (bid action lifecycle)
- BidDecisionType enum: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/BidDecisionType.java`
- BidActionEntity: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/persistence/BidActionEntity.java`
- Marketplace adapters: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/adapter/wb/WbBidCommandAdapter.java`, аналогично ozon/, yandex/
- Provider API specs: `docs/provider-api-specs/wb-advertising-bidding-contracts.md`, `ozon-advertising-bidding-contracts.md`

Не ломай существующие тесты. Проект должен собираться после правки.
```

---

## Чат 2. P0: hasMinimumData практически всегда возвращает true

```
## Задача

В автобиддинге метод `hasMinimumData()` в `BiddingSignalCollector` слишком слабый — он почти всегда возвращает true, из-за чего система принимает решения на пустых или статистически незначимых данных.

## Что сломано

Файл: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/BiddingSignalCollector.java`

Строки 93-96:
```java
public boolean hasMinimumData(BiddingSignalSet signals) {
    return signals.campaignStatus() != null
        || signals.currentBid() != null;
}
```

Достаточно, чтобы был **либо** campaign status, **либо** current bid — это почти всегда true. Документ (`docs/modules/autobidding.md`, раздел 13.3) требует:
- Рекламная статистика за >= lookback_days дней
- Минимум N кликов или N заказов (зависит от стратегии)
- Исключение: LAUNCH в фазе разгона

## Что нужно сделать

### 1. Изменить сигнатуру hasMinimumData

Метод должен принимать контекст стратегии (тип + конфиг), чтобы проверять strategy-specific пороги:

```java
public boolean hasMinimumData(
    BiddingSignalSet signals,
    BiddingStrategyType strategyType,
    JsonNode config) {
```

### 2. Реализовать реальные проверки

Логика:
1. Если `signals.campaignStatus() == null` — false (нет информации о кампании)
2. Если рекламные метрики отсутствуют (`drrPct == null && impressions == 0 && clicks == 0 && adOrders == 0 && adSpend == null`) — false (нет рекламной статистики вообще)
3. Strategy-specific checks:
   - **ECONOMY_HOLD**: нужен `drrPct != null` (без DRR стратегия не может работать)
   - **GROWTH**: нужен `clicks >= min_clicks_for_signal` из конфига (default 20) или `adOrders >= 1`
   - **POSITION_HOLD**: нужен `impressions > 0`
   - **LAUNCH**: **всегда true** (фаза разгона не требует накопленной статистики)
   - **LIQUIDATION**: нужен `stockDays != null` (без inventory данных стратегия не может работать)
   - **MINIMAL_PRESENCE**: нужен `currentBid != null || minBid != null`
4. Общее: `currentBid != null` ИЛИ `minBid != null` — без текущей ставки или минимума невозможно рассчитать новую

### 3. Обновить вызов в BiddingRunService

Файл: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/BiddingRunService.java`

Строка 109 — передать strategyType и configNode:
```java
if (!signalCollector.hasMinimumData(signals, policy.getStrategyType(), configNode)) {
```

### 4. Тесты

В `BiddingSignalCollectorTest.java` (создать, если нет):
- `should_returnFalse_when_noCampaignStatus_and_noCurrentBid`
- `should_returnFalse_when_economyHold_and_noDrrPct`
- `should_returnFalse_when_growth_and_insufficientClicks`
- `should_returnTrue_when_launch_strategy` (всегда true)
- `should_returnTrue_when_allSignalsPresent`

### Контекст

- BiddingSignalSet record: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/BiddingSignalSet.java`
- Strategy configs: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/strategy/config/`
- Бизнес-правило: `docs/modules/autobidding.md`, раздел 13.3

Не ломай существующие тесты. Проект должен собираться.
```

---

## Чат 3. P0: maxAggregateDailySpend сохраняется, но не проверяется в runtime

```
## Задача

Workspace-level настройка `maxAggregateDailySpend` (максимальный совокупный рекламный расход workspace в день) принимается через API, сохраняется в БД, отображается в UI — но **нигде не проверяется** при принятии решений. Пользователь ожидает защиту, но её нет.

## Что сломано

1. `WorkspaceBiddingSettingsEntity` имеет поле `maxAggregateDailySpend` (BigDecimal)
2. API позволяет его задать через `PUT /api/workspaces/{id}/bidding/settings`
3. UI показывает и позволяет редактировать
4. **Но ни `BiddingRunService`, ни один guard не проверяют это значение**

Проверь сам: в `BiddingRunService.java` нет ни одного обращения к `settingsService` кроме `isBiddingEnabled`. В guard chain нет guard-а для aggregate spend.

## Что нужно сделать

### 1. Создать AggregateSpendLimitGuard

Путь: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/guard/AggregateSpendLimitGuard.java`

Логика:
- Order: 38 (после DrrCeilingGuard=35, перед DailySpendLimitGuard=40)
- Блокирует только BID_UP (не блокирует BID_DOWN, PAUSE)
- Проверяет: сегодняшний совокупный расход по всем товарам workspace + прогнозируемое увеличение от текущего BID_UP
- Если `actualDailySpend + estimatedIncrease > maxAggregateDailySpend` → block

Для получения текущего дневного расхода используй:
- Либо сумму `adSpend` из ClickHouse за сегодня (если данные достаточно свежие)
- Либо упрощённый вариант: `SUM(target_bid - previous_bid)` по уже APPROVED/SCHEDULED bid_actions за сегодня + текущий target_bid из context

### 2. Зависимости guard-а

Guard инжектирует:
- `WorkspaceBiddingSettingsService` — для получения `maxAggregateDailySpend`
- `BidActionRepository` — для подсчёта уже запланированных увеличений за сегодня
  (или `BiddingClickHouseReadRepository` — для фактического spend)

### 3. Guard должен пропускать, если лимит не задан

Если `maxAggregateDailySpend == null` → ALLOW (лимит не установлен, нет ограничения).

### 4. Добавить message code

В `MessageCodes.java` добавь:
```java
public static final String BIDDING_GUARD_AGGREGATE_SPEND_LIMIT =
    "bidding.guard.aggregate_spend_limit";
```

В `frontend/src/locale/ru.json` добавь:
```json
"bidding.guard.aggregate_spend_limit": "Совокупный дневной расход workspace приближается к лимиту {{limit}} ₽"
```

### 5. Тесты

- `should_block_when_spendExceedsLimit`
- `should_allow_when_noLimitConfigured`
- `should_allow_when_spendBelowLimit`
- `should_allow_when_decisionType_isBidDown`

### Контекст

- WorkspaceBiddingSettingsEntity: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/persistence/WorkspaceBiddingSettingsEntity.java`
- WorkspaceBiddingSettingsService: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/WorkspaceBiddingSettingsService.java`
- Существующие guards для паттерна: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/guard/DailySpendLimitGuard.java`
- BiddingGuard interface: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/guard/BiddingGuard.java`
- BiddingGuardResult: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/BiddingGuardResult.java`
- Guard chain: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/guard/BiddingGuardChain.java`
```

---

## Чат 4. P0: StaleAdvertisingDataGuard проверяет суррогаты вместо реальной свежести данных

```
## Задача

StaleAdvertisingDataGuard должен блокировать принятие решений, когда рекламные данные из ETL устарели (последняя синхронизация > N часов назад). Вместо этого он проверяет суррогатные показатели, что приводит к ложным срабатываниям и пропускам.

## Что сломано

Файл: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/guard/StaleAdvertisingDataGuard.java`

Текущая логика:
1. `impressions==0 && clicks==0 && adOrders==0` → block. Это **не** staleness, это **отсутствие активности**. У товара на MINIMAL_PRESENCE может быть 0 кликов при свежих данных.
2. `hoursSinceLastChange > threshold` → block. Это часы с последнего **bid decision**, а не с последней **синхронизации данных**. Если товар долго на HOLD (данные свежие, просто ничего менять не надо), guard ошибочно блокирует.

По документу (`docs/modules/autobidding.md`, раздел 12.6) нужно проверять:
- `advertising_data_freshness` из `marketplace_sync_state` (PostgreSQL) — когда ETL последний раз загрузил рекламные данные для данного connection

## Что нужно сделать

### 1. Добавить поле advertisingDataFreshnessHours в BiddingSignalSet

Файл: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/BiddingSignalSet.java`

Добавь поле в record:
```java
Integer advertisingDataFreshnessHours
```

Это количество часов с момента последней успешной синхронизации рекламных данных для connection, к которому привязан товар.

### 2. Заполнить сигнал в BiddingSignalCollector

Файл: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/BiddingSignalCollector.java`

В методе `collect()` добавь запрос к `marketplace_sync_state`:
- Таблица `marketplace_sync_state` в PostgreSQL хранит `last_success_at` для каждого connection + event type
- Найди запись для connectionId с event type, связанным с рекламой (ADVERTISING_FACT или аналогичный)
- Вычисли `EXTRACT(EPOCH FROM (now() - last_success_at)) / 3600` = часы с последней синхронизации

Добавь метод в `BiddingDataReadRepository`:
```java
Optional<Integer> findAdvertisingDataFreshnessHours(long connectionId);
```

SQL:
```sql
SELECT EXTRACT(EPOCH FROM (now() - last_success_at)) / 3600
FROM marketplace_sync_state
WHERE connection_id = :connectionId
  AND event_type IN ('WB_ADVERTISING_FACT', 'OZON_ADVERTISING_FACT', 'YANDEX_ADVERTISING_FACT')
ORDER BY last_success_at DESC
LIMIT 1
```

Точные значения event_type проверь в enum `EtlEventType` (в модуле datapulse-etl) или в таблице `marketplace_sync_state`.

### 3. Переписать StaleAdvertisingDataGuard

Новая логика:
```java
@Override
public BiddingGuardResult evaluate(BiddingGuardContext context) {
  BiddingSignalSet signals = context.signals();
  int thresholdHours = biddingProperties.getStaleDataThresholdHours();

  // Primary check: actual data freshness from ETL sync
  if (signals.advertisingDataFreshnessHours() != null
      && signals.advertisingDataFreshnessHours() > thresholdHours) {
    return BiddingGuardResult.block(guardName(),
        MessageCodes.BIDDING_GUARD_STALE_DATA,
        Map.of("hours", thresholdHours,
               "actualHours", signals.advertisingDataFreshnessHours()));
  }

  // Fallback: if freshness unknown, check presence of any metrics
  if (signals.advertisingDataFreshnessHours() == null
      && signals.drrPct() == null
      && signals.adSpend() == null) {
    return BiddingGuardResult.block(guardName(),
        MessageCodes.BIDDING_GUARD_STALE_DATA,
        Map.of("hours", thresholdHours));
  }

  return BiddingGuardResult.allow(guardName());
}
```

Убери проверку `hoursSinceLastChange` — она не относится к свежести данных (это частота изменений ставки, покрывается FrequencyGuard).
Убери проверку `impressions==0 && clicks==0` — это отсутствие активности, не staleness.

### 4. Обновить BiddingSignalSet usage

Все конструкторы BiddingSignalSet должны включить новое поле. Проверь все места создания BiddingSignalSet (основное в `BiddingSignalCollector.collect()`, тестовые в `TestSignals.java`).

### 5. Тесты

Обнови `StaleAdvertisingDataGuardTest.java`:
- `should_block_when_advertisingDataOlderThanThreshold` — freshnessHours=72, threshold=48 → BLOCK
- `should_allow_when_advertisingDataFresh` — freshnessHours=12, threshold=48 → ALLOW
- `should_block_when_freshnessUnknown_and_noMetrics` — freshnessHours=null, drrPct=null, adSpend=null → BLOCK
- `should_allow_when_freshnessUnknown_but_metricsPresent` — freshnessHours=null, drrPct=10% → ALLOW
- Удали старые тесты, проверяющие impressions==0 как staleness

### Контекст

- marketplace_sync_state — проверь структуру таблицы: `rg "marketplace_sync_state" backend/datapulse-etl/`
- TestSignals helper: `backend/datapulse-bidding/src/test/java/io/datapulse/bidding/domain/guard/TestSignals.java`
- BiddingProperties: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/config/BiddingProperties.java`
```

---

## Чат 5. P0: CSV export решений игнорирует dateFrom/dateTo

```
## Задача

Endpoint экспорта решений в CSV принимает параметры `dateFrom` и `dateTo`, но **не передаёт их** в query — фильтрация по датам не работает.

## Что сломано

Файл: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/api/BidDecisionController.java`

Строки 61-65: принимает `dateFrom` и `dateTo` как `@RequestParam`
Строка 87-88: вызывает `listDecisions(workspaceId, bidPolicyId, null, null, pageable)` — **dateFrom и dateTo не передаются**.

## Что нужно сделать

### 1. Расширить BidDecisionQueryService.listDecisions

Добавь параметры `LocalDate dateFrom, LocalDate dateTo` в метод `listDecisions` (или создай отдельный перегруженный метод для экспорта).

В репозитории — добавь фильтрацию:
```sql
AND (:dateFrom IS NULL OR bd.created_at >= :dateFrom::date)
AND (:dateTo IS NULL OR bd.created_at < (:dateTo::date + interval '1 day'))
```

Или через Spring Data Specification/Criteria, если `listDecisions` уже использует JPA dynamic queries.

### 2. Парсинг дат в контроллере

```java
LocalDate from = dateFrom != null ? LocalDate.parse(dateFrom) : null;
LocalDate to = dateTo != null ? LocalDate.parse(dateTo) : null;
```

Передать в вызов:
```java
batch = decisionQueryService.listDecisions(
    workspaceId, bidPolicyId, null, null, from, to, pageable);
```

### 3. Не сломать существующий listDecisions

Если `listDecisions` используется в других местах (GET list endpoint), добавь overload или параметры по умолчанию. Проверь `BidDecisionController.listDecisions()` на строке 37 — этот endpoint тоже может выиграть от dateFrom/dateTo фильтра.

### 4. Добавь decisionType фильтр (заодно)

Раз уж ты трогаешь этот query, добавь и фильтр по `decisionType` в list endpoint:
```java
@RequestParam(value = "decisionType", required = false) String decisionType
```

Это P1 issue, но лучше сделать сейчас, пока руки в этом файле.

### 5. Тесты

- Integration или unit тест на экспорт с dateRange — проверить, что возвращаются только решения в диапазоне.

### Контекст

- BidDecisionQueryService: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/BidDecisionQueryService.java`
- BidDecisionRepository: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/persistence/BidDecisionRepository.java`
- Frontend вызов: `frontend/src/app/core/api/bidding-api.service.ts` — метод `exportDecisionsCsv`
```

---

# P1 — STRONGLY RECOMMENDED

---

## Чат 6. P1: DRR_CRITICAL и GUARD_BLOCK никогда не auto-resume + RESUME counter

```
## Задача

Два связанных бага:

### Баг 1: DRR_CRITICAL и GUARD_BLOCK auto-resume

В `BiddingResumeEvaluator` товары, поставленные на PAUSE с причиной DRR_CRITICAL или GUARD_BLOCK, **никогда** автоматически не возвращаются в работу.

Файл: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/BiddingResumeEvaluator.java`
Строки 61-62:
```java
case DRR_CRITICAL -> false;
case GUARD_BLOCK -> false;
```

Это значит, что товар с критическим DRR (>150% целевого), которому PAUSE поставлен автоматически, **навсегда застрянет** в PAUSE, даже если DRR вернулся в норму. Пользователь не уведомляется и не видит проблемы.

**Исправление DRR_CRITICAL:**
- Проверять текущий DRR из signals: если `drrPct` вернулся ниже целевого порога → resume
- Целевой DRR надо извлечь из конфига стратегии (он записан в `bid_decision.signal_snapshot` или в `bid_policy.config`)
- Упрощённый вариант: если `drrPct != null && drrPct < configuredCeiling * 0.8` → resume (с запасом 20%)
- Для доступа к конфигу стратегии, `evaluateResume` нужен доступ к `BidPolicyEntity` через `bidPolicyId` из последнего decision

**Исправление GUARD_BLOCK:**
- Это generic "заблокировано guard-ом". Нет достаточной информации для auto-resume.
- Решение: при GUARD_BLOCK → **не auto-resume**, но добавить в BiddingResumeEvaluator возможность ре-evaluate guards для этого товара.
- Или: на каждом run для PAUSE товаров — прогонять guard chain, если все guards прошли → RESUME.
- Или минимальный вариант: оставить `false`, но добавить логику в `BiddingRunService`: если товар в PAUSE дольше N дней → проверить заново.

Я рекомендую минимальный вариант: сделай auto-resume для DRR_CRITICAL (проверяя drrPct), а для GUARD_BLOCK оставь false (generic, рискованно).

### Баг 2: RESUME counter считается как HOLD

Файл: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/BiddingRunService.java`
Строки 120-124: при RESUME результате инкрементируется `totalHold++`.

Нужно:
1. Добавить `totalResume` в `BiddingRunEntity`
2. Инкрементировать `totalResume` при RESUME
3. Добавить поле в `BiddingRunCompletedEvent`
4. Добавить поле в `BiddingRunSummaryResponse` и `BiddingRunDetailResponse` (DTO)
5. Миграция: `ALTER TABLE bidding_run ADD COLUMN total_resume int NOT NULL DEFAULT 0;`

## Что нужно сделать

### Файлы для правки

1. `BiddingResumeEvaluator.java` — добавить DRR_CRITICAL auto-resume
2. `BiddingRunService.java` — добавить totalResume counter
3. `BiddingRunEntity.java` — добавить поле totalResume
4. `BiddingRunCompletedEvent.java` — добавить totalResume
5. `BiddingRunSummaryResponse.java` — добавить totalResume
6. Создать миграцию `changes/0039-bidding-run-total-resume.sql`
7. Frontend: `bidding-run-detail-page.component.ts` — показать totalResume
8. `ru.json` — ключ `bidding.runs.total_resume`

### Тесты

- `BiddingResumeEvaluatorTest.java` (создать): 
  - `should_resume_when_drrCritical_and_drrNormalized`
  - `should_notResume_when_drrCritical_and_drrStillHigh`
  - `should_notResume_when_guardBlock`
```

---

## Чат 7. P1: Workspace access check + campaign UNKNOWN + resolveProduct efficiency

```
## Задача

Три связанных бага в security и reliability:

### Баг 1: getDecision не проверяет workspace ownership

Файл: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/api/BidDecisionController.java`
Строка 55: `decisionQueryService.getDecision(id)` ищет по ID, не проверяя workspaceId.

Пользователь workspace A может прочитать decision из workspace B, зная ID. Это information disclosure.

**Исправление:**
В `BidDecisionQueryService.getDecision()` добавь параметр `workspaceId` и проверяй:
```java
public BidDecisionEntity getDecision(long id, long workspaceId) {
  var entity = decisionRepository.findById(id)
      .orElseThrow(() -> NotFoundException.of(MessageCodes.BIDDING_DECISION_NOT_FOUND, id));
  if (entity.getWorkspaceId() != workspaceId) {
    throw NotFoundException.of(MessageCodes.BIDDING_DECISION_NOT_FOUND, id);
  }
  return entity;
}
```

Обнови вызов в контроллере:
```java
BidDecisionEntity entity = decisionQueryService.getDecision(id, workspaceId);
```

**Проверь аналогично:** `BidActionController`, `BiddingRunController` — все getById должны проверять workspaceId.

### Баг 2: campaign info = UNKNOWN → action failure

Файл: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/BiddingActionScheduler.java`
Строки 115-121: если campaign info не найдена, ставится `UNKNOWN`. Потом `BidActionExecutor` пытается resolve gateway по `UNKNOWN` → NPE или runtime error.

**Исправление:**
Если campaign info не найдена — **не создавай action**, залогируй warning и пропусти decision:
```java
if (campaign == null) {
  log.warn("Skipping action: no campaign info for offer: marketplaceOfferId={}, decisionId={}",
      decision.getMarketplaceOfferId(), decision.getId());
  return null; // caller checks for null
}
```

Обнови caller в `scheduleActions`:
```java
BidActionEntity action = createAction(decision);
if (action == null) {
  continue;
}
```

### Баг 3: resolveProduct делает full query per-decision

Строки 135-145: `resolveProduct` вызывает `findEligibleProducts(workspaceId, bidPolicyId)` для **каждого** decision. При 200 actionable decisions из 500 eligible → 200 SQL-запросов по 500 строк.

**Исправление:**
Кэшируй eligible products один раз в начале `scheduleActions`:
```java
@Transactional
public void scheduleActions(long biddingRunId) {
  List<BidDecisionEntity> decisions = decisionRepository.findByBiddingRunId(biddingRunId);
  // ...
  // Pre-load eligible products once
  Map<Long, EligibleProductRow> productMap = loadProductMap(actionable);
  // ...
  // In createAction: use productMap.get(decision.getMarketplaceOfferId())
}

private Map<Long, EligibleProductRow> loadProductMap(List<BidDecisionEntity> decisions) {
  if (decisions.isEmpty()) return Map.of();
  // All decisions in a run share the same policy
  BidDecisionEntity first = decisions.get(0);
  return readRepository.findEligibleProducts(first.getWorkspaceId(), first.getBidPolicyId())
      .stream()
      .collect(Collectors.toMap(EligibleProductRow::marketplaceOfferId, p -> p));
}
```

## Тесты

- Security: `should_throwNotFound_when_decisionBelongsToOtherWorkspace`
- Action creation: `should_skipAction_when_noCampaignInfo`
- Performance: verify single SQL call per scheduleActions invocation (можно mock verify)
```

---

## Чат 8. P1: Priority field на bid_policy + conflict resolution

```
## Задача

Документ (`docs/modules/autobidding.md`, раздел 8.2) описывает разрешение конфликтов при назначении стратегий: "специфичность (SKU > категория > кампания) → приоритет стратегии → первая созданная". Но поля `priority` на `bid_policy` **нет**.

## Что нужно сделать

### 1. Миграция

Создай `changes/0040-bid-policy-priority.sql`:
```sql
--liquibase formatted sql

--changeset datapulse:0040-bid-policy-priority
ALTER TABLE bid_policy ADD COLUMN priority int NOT NULL DEFAULT 0;
COMMENT ON COLUMN bid_policy.priority IS 'Higher value = higher priority. Used for conflict resolution when multiple policies match the same offer.';

--rollback ALTER TABLE bid_policy DROP COLUMN priority;
```

### 2. BidPolicyEntity

Добавь поле:
```java
@Column(name = "priority", nullable = false)
private int priority;
```

### 3. API

В `CreateBidPolicyRequest` и `UpdateBidPolicyRequest` добавь необязательное поле:
```java
@Min(0) @Max(100) Integer priority
```

Default = 0. В `BidPolicyService.createPolicy()` и `updatePolicy()` — установить.

В response DTO (`BidPolicySummaryResponse`, `BidPolicyDetailResponse`) — добавить.

### 4. Conflict resolution в BiddingDataReadRepository

Если товар попадает под несколько assignments (SKU + campaign), нужно выбрать одну стратегию. В `findEligibleProducts` или в отдельном методе — добавь ORDER BY:
1. `assignment_scope DESC` (SKU=3 > CATEGORY=2 > CAMPAIGN=1)
2. `bp.priority DESC`
3. `bp.created_at ASC`
И DISTINCT ON или GROUP BY для marketplace_offer_id.

### 5. Frontend

В форме стратегии (`bid-policy-form-page.component.ts`) — добавь поле "Приоритет" (number input, 0-100).
В списке стратегий — колонка "Приоритет".

В `ru.json`:
```json
"bidding.policies.priority": "Приоритет",
"bidding.policies.priority_hint": "Чем выше число, тем выше приоритет при конфликте назначений (0-100)"
```

### 6. Тесты

- `should_resolveHigherPriority_when_multipleAssignmentsMatch`
- `should_resolveSkuOverCampaign_regardless_ofPriority`
```

---

## Чат 9. P1: Endpoint для resume/cancel PAUSED run (blast radius)

```
## Задача

Когда bidding run останавливается из-за blast radius (слишком много BID_UP), run переходит в статус PAUSED. Но **нет REST endpoint** для resume или cancel этого run. Пользователь видит PAUSED в UI, но не может ничего сделать.

## Что нужно сделать

### 1. Добавить методы в BiddingRunApiService

```java
@Transactional
public void resumeRun(long runId, long workspaceId) {
  BiddingRunEntity run = requireRun(runId, workspaceId);
  if (run.getStatus() != BiddingRunStatus.PAUSED) {
    throw BadRequestException.of(MessageCodes.BIDDING_RUN_NOT_PAUSED);
  }
  run.setStatus(BiddingRunStatus.COMPLETED);
  run.setCompletedAt(OffsetDateTime.now());
  runRepository.save(run);
  actionScheduler.scheduleActions(run.getId());
  log.info("Bidding run resumed: runId={}", runId);
}

@Transactional
public void cancelRun(long runId, long workspaceId) {
  BiddingRunEntity run = requireRun(runId, workspaceId);
  if (run.getStatus() != BiddingRunStatus.PAUSED) {
    throw BadRequestException.of(MessageCodes.BIDDING_RUN_NOT_PAUSED);
  }
  run.setStatus(BiddingRunStatus.CANCELLED);
  run.setCompletedAt(OffsetDateTime.now());
  runRepository.save(run);
  log.info("Bidding run cancelled: runId={}", runId);
}
```

### 2. Добавить CANCELLED в BiddingRunStatus

```java
public enum BiddingRunStatus {
  RUNNING, COMPLETED, PAUSED, FAILED, CANCELLED
}
```

### 3. REST endpoints

В `BiddingRunController`:
```java
@PostMapping("/{runId}/resume")
@ResponseStatus(HttpStatus.NO_CONTENT)
@PreAuthorize("@workspaceAccessService.canWrite(#workspaceId)")
public void resumeRun(
    @PathVariable("workspaceId") long workspaceId,
    @PathVariable("runId") long runId) {
  runApiService.resumeRun(runId, workspaceId);
}

@PostMapping("/{runId}/cancel")
@ResponseStatus(HttpStatus.NO_CONTENT)
@PreAuthorize("@workspaceAccessService.canWrite(#workspaceId)")
public void cancelRun(
    @PathVariable("workspaceId") long workspaceId,
    @PathVariable("runId") long runId) {
  runApiService.cancelRun(runId, workspaceId);
}
```

### 4. MessageCodes

```java
public static final String BIDDING_RUN_NOT_PAUSED = "bidding.run.not_paused";
```

### 5. Frontend

В `BiddingApiService` — добавь `resumeRun(runId)`, `cancelRun(runId)`.
В `BiddingRunDetailPageComponent` — добавь кнопки "Возобновить" и "Отменить" (видимы только при status=PAUSED).

В `ru.json`:
```json
"bidding.runs.resume": "Возобновить",
"bidding.runs.cancel": "Отменить",
"bidding.run.not_paused": "Прогон не находится в статусе PAUSED",
"bidding.runs.resume_confirm": "Возобновить прогон? Решения будут применены.",
"bidding.runs.cancel_confirm": "Отменить прогон? Решения не будут применены."
```

### 6. WebSocket event

Добавь `BIDDING_RUN_RESUMED` и `BIDDING_RUN_CANCELLED` в WebSocket topics.

### 7. Тесты

- `should_resumePausedRun_and_scheduleActions`
- `should_cancelPausedRun`
- `should_fail_when_resumeNonPausedRun`
```

---

## Чат 10. P1: Дедупликация i18n ключей в ru.json

```
## Задача

В `frontend/src/locale/ru.json` обнаружены дублирующиеся ключи в блоке `bidding.*`. В JSON при дублировании побеждает последнее значение — часть переводов может быть потеряна.

## Что нужно сделать

1. Найди все дублирующиеся ключи в `frontend/src/locale/ru.json`:
   ```bash
   cat frontend/src/locale/ru.json | python3 -c "
   import json, sys, collections
   d = json.loads(sys.stdin.read())
   # JSON doesn't raise on dupes, need raw parse
   " 
   ```
   Или вручную: поищи `rg '"bidding.detail.' frontend/src/locale/ru.json` — найдёшь дубли.

2. Для каждого дубля определи правильное значение (обычно более полный и точный перевод).

3. Удали дублирующуюся запись (оставь одну).

4. Проверь, что frontend собирается: `cd frontend && ng build`.

5. Поищи аналогичные дубли в других блоках (pricing, grid, etc.) — для полноты.

Это чисто механическая задача. Не меняй значения переводов, только удали дубли.
```

---

# P1 — ТЕСТЫ

---

## Чат 11. P1: Unit-тесты для всех 6 стратегий

```
## Задача

Сейчас покрыты тестами только 2 из 6 стратегий: EconomyHoldStrategy и MinimalPresenceStrategy. Нужно добавить тесты для оставшихся 4.

## Что нужно сделать

### 1. GrowthStrategyTest

Файл: `backend/datapulse-bidding/src/test/java/io/datapulse/bidding/domain/strategy/GrowthStrategyTest.java`

Тесты:
- `should_bidUp_when_cpoBelowTarget_and_enoughClicks` — CPO < target, clicks >= min → BID_UP
- `should_bidDown_when_cpoAboveMax` — CPO > max_cpo → BID_DOWN
- `should_hold_when_cpoNearTarget` — CPO ≈ target → HOLD
- `should_hold_when_insufficientClicks` — clicks < min_clicks_for_signal → HOLD
- `should_respectBidStepPct` — BID_UP не превышает bid_step_pct от текущей ставки
- `should_respectMaxBid` — целевая ставка ≤ max_bid из конфига

### 2. PositionHoldStrategyTest

- `should_bidUp_when_impressionsBelowTarget`
- `should_bidDown_when_impressionsAboveTarget`
- `should_hold_when_impressionsWithinTolerance`
- `should_respectDrrCeiling` — не поднимать ставку если DRR > ceiling

### 3. LaunchStrategyTest

- `should_hold_during_launchPeriod` — в первые N дней не снижать
- `should_suggestTransition_when_launchComplete` — по завершении периода, suggestedTransition != null
- `should_extend_when_insufficientClicks` — если кликов < min, продлить
- `should_respectCeilingDrr_even_duringLaunch` — потолок DRR даже в разгоне

### 4. LiquidationStrategyTest

- `should_bidUp_when_stockHigh_and_drrBelowMax`
- `should_bidDown_when_drrAboveMax`
- `should_pause_when_stockZero`
- `should_exit_when_daysOfCoverBelowThreshold` — перевод в обычный режим

### Паттерн

Используй существующий `TestSignals.java` для создания тестовых сигналов. Изучи `EconomyHoldStrategyTest` и `MinimalPresenceStrategyTest` как паттерн.

Конфиг для каждой стратегии — создавай через `ObjectMapper`:
```java
ObjectMapper mapper = new ObjectMapper();
JsonNode config = mapper.readTree("""
    {
      "targetDrrPct": 10,
      "bidStepPct": 10,
      ...
    }
    """);
```

### Контекст

- Стратегии: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/strategy/`
- Конфиги: `backend/datapulse-bidding/src/main/java/io/datapulse/bidding/domain/strategy/config/`
- Существующие тесты: `backend/datapulse-bidding/src/test/java/io/datapulse/bidding/domain/strategy/`
- TestSignals: `backend/datapulse-bidding/src/test/java/io/datapulse/bidding/domain/guard/TestSignals.java`
```

---

## Чат 12. P1: Unit-тесты для BiddingActionScheduler, BiddingResumeEvaluator, missing guards

```
## Задача

Не хватает тестов для ключевых доменных сервисов и 3 guards.

## Что нужно сделать

### 1. BiddingActionSchedulerTest

Файл: `backend/datapulse-bidding/src/test/java/io/datapulse/bidding/domain/BiddingActionSchedulerTest.java`

Тесты:
- `should_createActions_forAllActionableDecisions` — BID_UP, BID_DOWN, PAUSE, RESUME → actions created
- `should_skipNonActionableDecisions` — HOLD → no action
- `should_supersedePreviousPreExecActions` — old PENDING_APPROVAL → SUPERSEDED
- `should_setStatusApproved_when_fullAuto`
- `should_setStatusPendingApproval_when_semiAuto`
- `should_setStatusOnHold_when_recommendation`
- `should_publishOutboxEvent_when_approved`
- `should_skipAction_when_noCampaignInfo` (после фикса из чата 7)

### 2. BiddingResumeEvaluatorTest

Файл: `backend/datapulse-bidding/src/test/java/io/datapulse/bidding/domain/BiddingResumeEvaluatorTest.java`

Тесты:
- `should_returnNull_when_noLastDecision`
- `should_returnNull_when_lastDecisionNotPause`
- `should_resume_when_stockOutResolved` — STOCK_OUT + stockDays > 0 → RESUME
- `should_resume_when_negativeMarginResolved` — NEGATIVE_MARGIN + margin > 0 → RESUME
- `should_resume_when_drrCriticalResolved` (после фикса из чата 6)
- `should_notResume_when_guardBlock` — GUARD_BLOCK → null (not resumable)
- `should_resume_via_legacyFallback_stock` — нет pauseReasonCode, explanation содержит "stock"

### 3. DailySpendLimitGuardTest

Файл: `backend/datapulse-bidding/src/test/java/io/datapulse/bidding/domain/guard/DailySpendLimitGuardTest.java`

- `should_block_when_spendExceedsMaxDailySpend`
- `should_allow_when_spendBelowLimit`
- `should_allow_when_noMaxDailySpendConfigured`
- `should_allow_when_decisionType_isBidDown`

### 4. VolatilityGuardTest

Файл: `backend/datapulse-bidding/src/test/java/io/datapulse/bidding/domain/guard/VolatilityGuardTest.java`

- `should_block_when_tooManyReversals`
- `should_allow_when_fewReversals`
- `should_allow_when_noHistory`

### 5. PriceCompetitivenessGuardTest

- `should_block_when_priceAboveCompetitor_and_drrHigh`
- `should_allow_when_noCompetitorData`
- `should_allow_when_priceCompetitive`

### Контекст

- Используй `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`
- Существующие guard тесты — паттерн для новых
- TestSignals.java для создания сигналов
```

---

# P2 — OPTIONAL IMPROVEMENTS

---

## Чат 13. P2: Обновление документации autobidding.md

```
## Задача

После всех фиксов нужно обновить `docs/modules/autobidding.md`, чтобы он отражал фактическое состояние.

## Что нужно обновить

### Раздел 22 — Текущий scope

1. Перенеси из "Реализовано частично" в "Реализовано полностью":
   - Priority field на bid_policy (после чата 8)
   - CSV export decisions (после чата 5)

2. Добавь в раздел 22.1 (реализовано полностью):
   - PAUSE/RESUME actions (чат 1)
   - Aggregate spend limit guard (чат 3)
   - Run resume/cancel (чат 9)

3. Обнови guard list: упомяни AggregateSpendLimitGuard (order 38)

4. Документируй SET_MINIMUM и EMERGENCY_CUT decision types — они есть в коде, но не описаны в документе:
   - SET_MINIMUM — стратегия устанавливает минимально допустимую ставку
   - EMERGENCY_CUT — экстренное снижение при критическом состоянии

### Раздел 16.3 — Lifecycle bidding run

Добавь CANCELLED состояние:
```
PAUSED → IN_PROGRESS (resume)
       → CANCELLED
```

### Новый раздел: Known limitations

Добавь:
- Yandex Market ETL для рекламной статистики — stub, данные не нормализуются в ClickHouse. Автобиддинг для Yandex работает без рекламных метрик (все решения = HOLD)
- Category-level assignments — DDL поддерживает, UI не реализован

### Раздел 15.2 — Отключаемые guards

Добавь:
| **Aggregate spend limit guard** | Совокупный дневной расход workspace > max | Включён (если лимит задан) | 38 |

### Контекст

Не трогай контент, который не связан с выполненными фиксами. Сохрани стиль и формат документа.
```

---

## Чат 14. P2: Индикация устаревших данных в UI + Yandex warning

```
## Задача

Две UI-доработки для прозрачности:

### 1. Индикация "данные устарели" в UI

Если рекламные данные для workspace/connection устарели (ETL sync упал), пользователь не видит этого в UI автобиддинга. Решения принимаются на старых данных, но UI показывает их как нормальные.

**Что нужно:**
- На дашборде автобиддинга (BiddingDashboardPage) — показать предупреждение, если хотя бы одно подключение имеет рекламные данные старше 48 часов
- В detail page решения — если в signal_snapshot видно, что advertisingDataFreshnessHours > 48 → показать badge "Данные устарели"
- В гриде — если товар получил HOLD из-за StaleDataGuard → показать tooltip с объяснением

**Backend:**
- Добавь в `BiddingDashboardResponse` поле `staleConnections: List<StaleConnectionInfo>` (connectionId, name, lastSyncAt, hoursAgo)
- В `BiddingDashboardService` — запрос к `marketplace_sync_state`

**Frontend:**
- В `BiddingDashboardPageComponent` — блок warning с иконкой, если `staleConnections.length > 0`
- Текст: "Для {{count}} подключений рекламные данные устарели. Последняя синхронизация: {{lastSync}}"

### 2. Warning для Yandex Market

Если workspace имеет Yandex Market подключение, и пользователь пытается назначить bid-стратегию на товары Yandex — показать warning: "Для Yandex Market рекламная статистика пока не загружается. Автобиддинг будет работать с ограниченными данными."

**Frontend:**
- В `BidPolicyAssignmentsSectionComponent` — при добавлении assignment, если оффер связан с Yandex → показать warning toast
- В `BiddingSettingsPageComponent` — если есть Yandex connection → показать info блок

**i18n:**
```json
"bidding.dashboard.stale_data_warning": "Для {{count}} подключений рекламные данные устарели",
"bidding.yandex_limited_warning": "Для Yandex Market рекламная статистика пока не загружается. Автобиддинг будет работать с ограниченными данными."
```
```

---

## Чат 15. P2: Category assignment + Exclude list

```
## Задача

Две функциональных доработки, описанные в документе, но не реализованные.

### 1. Назначение на категорию (category assignment)

DDL уже поддерживает `category_id` в `bid_policy_assignment` (миграция 0034). Нужно реализовать сервисную логику и UI.

**Backend:**
- В `BidPolicyAssignmentService` добавь `assignToCategory(bidPolicyId, categoryId)`
- В `BiddingDataReadRepository.findEligibleProducts` — учитывай category assignments: товары из категории, если нет более специфичного SKU assignment
- В `AssignmentScope` enum — должен быть `CATEGORY` (проверь, есть ли уже)

**API:**
- В `CreateAssignmentRequest` — поле `categoryId` (nullable)
- Validation: ровно одно из (marketplaceOfferId, campaignExternalId, categoryId) должно быть заполнено

**Frontend:**
- В `BidPolicyAssignmentsSectionComponent` — добавь тип "Категория" в выбор scope
- Autocomplete/select для выбора категории (из существующего справочника категорий)

### 2. Exclude list для assignments

Документ (раздел 11.2): "Исключения (exclude list) — SKU, явно исключённые из стратегии"

**Backend:**
- Новая таблица или JSONB поле: `bid_policy_exclusion` (bid_policy_id, marketplace_offer_id)
- Или JSONB массив `excluded_offer_ids` на `bid_policy_assignment`
- В `findEligibleProducts` — LEFT JOIN exclusions, WHERE exclusion IS NULL
- CRUD для exclusions

**API:**
- `POST .../policies/{id}/exclusions` — добавить исключение
- `DELETE .../policies/{id}/exclusions/{offerId}` — удалить
- `GET .../policies/{id}/exclusions` — список

**Frontend:**
- В карточке стратегии — секция "Исключения" с таблицей и кнопками add/remove
- В гриде — bulk action "Исключить из стратегии"

**i18n:**
```json
"bidding.assignments.scope_category": "Категория",
"bidding.exclusions.title": "Исключения",
"bidding.exclusions.add": "Добавить исключение",
"bidding.exclusions.remove": "Удалить исключение",
"bidding.exclusions.empty": "Нет исключений"
```

Это P2, реализуй в минимальном виде — можно начать только с exclude list (он проще и ценнее).
```

---

## Чат 16. P2: Отчёт "Эффективность автобиддинга" (до/после)

```
## Задача

Документ (раздел 20.1) описывает must-have отчёт: средний ДРР до автобиддинга vs после, CPO до vs после, ROAS до vs после. Сейчас этого нет.

## Что нужно сделать

### Концепция

"До" = метрики за N дней перед первым bidding run для товара.
"После" = метрики за последние N дней.

### Backend

1. Создай `BiddingEffectivenessService`:
```java
public BiddingEffectivenessReport getEffectivenessReport(
    long workspaceId, int periodDays, Long bidPolicyId);
```

2. Логика:
   - Для каждого товара под автобиддингом найди дату первого bid_decision
   - "Baseline" = avg DRR за [first_decision - periodDays, first_decision]
   - "Current" = avg DRR за [now - periodDays, now]
   - Агрегируй по стратегии, маркетплейсу, категории

3. SQL: это ClickHouse query к `mart_advertising_product` с GROUP BY period

4. Response DTO:
```java
public record BiddingEffectivenessReport(
    BigDecimal avgDrrBefore,
    BigDecimal avgDrrAfter,
    BigDecimal avgCpoBefore,
    BigDecimal avgCpoAfter,
    BigDecimal avgRoasBefore,
    BigDecimal avgRoasAfter,
    int totalProductsAnalyzed,
    List<StrategyBreakdown> byStrategy
) {}
```

### API

```
GET /api/workspaces/{id}/bidding/effectiveness?periodDays=30&bidPolicyId=5
```

### Frontend

Новая вкладка "Эффективность" в bidding layout (или секция на дашборде):
- Карточки: DRR до/после с процентом изменения
- CPO до/после
- ROAS до/после
- Таблица по стратегиям

Это P2 — реализуй в минимальном виде (один SQL + один endpoint + одна карточка в UI).
```

---

# ФИНАЛЬНАЯ ПРОВЕРКА

---

## Чат 17. Финальная верификация и smoke test

```
## Задача

Все P0 и P1 фиксы должны быть применены. Нужно сделать финальную верификацию.

## Чеклист

### Build
1. `./gradlew clean build` — зелёный
2. `cd frontend && ng build` — зелёный
3. Все тесты проходят

### P0 верификация (по каждому)

1. **PAUSE/RESUME actions:**
   - В `BiddingActionScheduler.ACTIONABLE_TYPES` есть PAUSE и RESUME
   - В `createAction()` для PAUSE targetBid = 0
   - Тесты проходят

2. **hasMinimumData:**
   - Проверяет strategy-specific условия
   - LAUNCH strategy → всегда true
   - ECONOMY_HOLD без drrPct → false
   - Тесты проходят

3. **maxAggregateDailySpend:**
   - AggregateSpendLimitGuard существует и зарегистрирован в guard chain
   - Блокирует BID_UP при превышении workspace лимита
   - Пропускает если лимит не задан (null)
   - Тесты проходят

4. **StaleAdvertisingDataGuard:**
   - Проверяет advertisingDataFreshnessHours из marketplace_sync_state
   - Не блокирует товары с 0 кликов при свежих данных
   - BiddingSignalSet содержит поле advertisingDataFreshnessHours
   - Тесты проходят

5. **CSV export:**
   - dateFrom/dateTo передаются в query
   - Фильтрация работает

### P1 верификация

6. DRR_CRITICAL auto-resume работает
7. totalResume counter в bidding_run — миграция применяется, поле заполняется
8. Priority на bid_policy — миграция, API, UI
9. Workspace access check на getDecision, getAction — workspaceId проверяется
10. Campaign UNKNOWN → skip action (не create)
11. resolveProduct — один SQL на scheduleActions
12. Run resume/cancel — endpoints работают
13. i18n дубли удалены
14. Тесты для всех 6 стратегий
15. Тесты для scheduler, evaluator, missing guards

### Документация

16. autobidding.md обновлён (раздел 22, guards, lifecycle, limitations)

### Финал

Если всё зелёное — сделай коммит:
```
fix(bidding): complete audit remediation — P0/P1 fixes

- PAUSE/RESUME decisions now create bid actions
- hasMinimumData validates strategy-specific minimum data
- maxAggregateDailySpend enforced via AggregateSpendLimitGuard
- StaleAdvertisingDataGuard checks actual ETL sync freshness
- CSV export respects dateFrom/dateTo filters
- DRR_CRITICAL auto-resume implemented
- totalResume counter added to bidding_run
- Priority field on bid_policy for conflict resolution
- Workspace access checks on getDecision/getAction by ID
- Campaign UNKNOWN skips action creation instead of failing
- resolveProduct cached per-run for performance
- Run resume/cancel endpoints added
- i18n duplicates removed
- Unit tests: all 6 strategies, 3 missing guards, scheduler, evaluator
- Documentation updated
```
```

---

*Конец документа. Всего 17 чатов: 5 P0 + 7 P1 + 4 P2 + 1 финальная верификация.*
