# Промты для реализации Pricing Complete Coverage

> Каждый промт — для отдельного чата. Копируй промт целиком в новый чат.
> Выполняй строго последовательно: Chat 1 → Chat 2 → ... → Chat 8.
> Каждый чат ссылается на `docs/features/2026-04-06-pricing-complete-coverage.md` как источник истины.

---

## Chat 1: Level 1 — ClickHouse signals (commission, logistics, returns)

```
Реализуй Level 1 (§1.1–§1.4, §1.7) из docs/features/2026-04-06-pricing-complete-coverage.md — подключение ClickHouse-сигналов в pricing pipeline.

Что нужно сделать (задачи #1, #2, #3, #7 из TBD):

1. В `PricingClickHouseReadRepository` добавь 3 batch-метода:
   - `findAvgCommissionPct(connectionId, sellerSkuIds, lookbackDays, minTransactions)` → `Map<Long, CommissionResult>`
   - `findCategoryAvgCommissionPct(connectionId, categories, lookbackDays)` → `Map<String, BigDecimal>`
   - `findAvgLogisticsPerUnit(connectionId, sellerSkuIds, lookbackDays)` → `Map<Long, BigDecimal>`
   - `findReturnRatePct(connectionId, sellerSkuIds, lookbackDays)` → `Map<Long, BigDecimal>`
   SQL-запросы — точно по документу (§1.2, §1.3, §1.4). Все запросы с `SETTINGS final = 1`.

2. В `PricingSignalCollector.collectBatch()`:
   - Построй маппинг `offerId → sellerSkuId` из данных `findOffersByConnection()` (§1.7)
   - Вызови все 3 CH-метода батчем
   - Для commission: если SKU не прошёл minTransactions → вызови category fallback
   - Передай результаты в `PricingSignalSet` вместо null

3. Для category fallback commission: тебе нужен маппинг `sellerSkuId → category`. Его можно получить из `dim_product` через join в основном запросе, или отдельным запросом. Выбери оптимальный вариант — главное, чтобы fallback cascade работал: per-SKU → per-category → (далее уже стратегия резолвит manual fallback).

4. Напиши unit-тесты для новых CH-методов (mock JdbcTemplate) и для обновлённого `PricingSignalCollector` (#8).

Не трогай: стратегии, guards, constraint resolver, frontend. Только signal assembly layer.

Ожидаемый результат: после этого чата `PricingSignalSet` для каждого offer будет содержать реальные значения `avgCommissionPct`, `avgLogisticsPerUnit`, `returnRatePct` из ClickHouse (или null если данных нет).
```

---

## Chat 2: Level 1 — PostgreSQL signals (status, min_price)

```
Реализуй оставшуюся часть Level 1 (§1.5, §1.6) из docs/features/2026-04-06-pricing-complete-coverage.md — подключение PostgreSQL-сигналов.

Что нужно сделать (задачи #4, #5 из TBD):

1. В `PricingDataReadRepository`:
   - В SQL-запрос `CURRENT_PRICES` добавь поле `cpc.min_price` (§1.6)
   - Обнови ResultSet-маппинг: верни `min_price` в результате (можно расширить существующий маппинг или добавить отдельный `findMinPrices`)

2. В `PricingSignalCollector.collectBatch()`:
   - `productStatus`: из `findOffersByConnection()` уже возвращается `status` — передай его в `PricingSignalSet.productStatus` вместо `null` (§1.5)
   - `marketplaceMinPrice`: из обновлённого `findCurrentPrices()` возьми `min_price` — передай в `PricingSignalSet.marketplaceMinPrice` вместо `null` (§1.6)

3. Обнови существующие тесты `PricingSignalCollectorTest` — они должны проверять, что `productStatus` и `marketplaceMinPrice` передаются корректно.

Не трогай: ClickHouse queries (сделаны в Chat 1), стратегии, guards, frontend.

Ожидаемый результат: все 14 полей `PricingSignalSet` заполняются реальными данными. Phase C — 100% complete. TARGET_MARGIN работает в полном AUTO-режиме.
```

---

## Chat 3: Level 2 — VelocityAdaptiveStrategy

```
Реализуй Level 2 (§2.1–§2.5) из docs/features/2026-04-06-pricing-complete-coverage.md — стратегия VELOCITY_ADAPTIVE.

Что нужно сделать (задачи #9, #10, #11, #12, #13, #14, #34, #35 из TBD):

1. Расширь `PolicyType` enum: добавь `VELOCITY_ADAPTIVE` (#34)

2. Расширь `PricingSignalSet` (#9): добавь поля `salesVelocityShort`, `salesVelocityLong`, `daysOfCover` (типы `BigDecimal`). Обнови все места, где создаётся `PricingSignalSet` — передавай `null` для новых полей как default.

3. В `PricingClickHouseReadRepository` (#10): добавь методы `findSalesVelocity(connectionId, sellerSkuIds, windowDays)` и `findDaysOfCover(connectionId, sellerSkuIds)`. SQL — точно по документу (§2.3).

4. В `PricingSignalCollector` (#11): вызови новые CH-методы, wire результаты в `PricingSignalSet`.

5. Создай `VelocityAdaptiveParams` record (#12) — точно по документу (§2.4). С `@JsonIgnoreProperties(ignoreUnknown = true)`, дефолтами через `effective*()` методы.

6. Создай `VelocityAdaptiveStrategy` (#13) — `@Component implements PricingStrategy`. Алгоритм — точно по документу (§2.4). Пропорциональный adjustment. Explanation format — по документу.

7. Добавь `MessageCodes` (#35) для новых reason keys:
   - `pricing.velocity.insufficient_data`
   - `pricing.velocity.stable`
   И переводы в `ru.json`.

8. Unit-тесты (#14): покрой scenarios: deceleration, acceleration, stable (HOLD), insufficient data (SKIP), edge cases (velocityLong = 0, null signals).

Не трогай: frontend (будет в отдельном чате), STOCK_BALANCING, COMPOSITE, COMPETITOR — только VELOCITY_ADAPTIVE.

Ожидаемый результат: можно создать policy с `strategyType = VELOCITY_ADAPTIVE`, pricing run для offer с такой policy корректно рассчитывает target price.
```

---

## Chat 4: Level 3 — StockBalancingStrategy

```
Реализуй Level 3 (§3.1–§3.4) из docs/features/2026-04-06-pricing-complete-coverage.md — стратегия STOCK_BALANCING.

Что нужно сделать (задачи #16, #17, #18 из TBD):

1. Расширь `PolicyType` enum: добавь `STOCK_BALANCING`

2. Расширь `PricingSignalSet`: добавь `frozenCapital` (BigDecimal) и `stockOutRisk` (String). Обнови все места создания — передавай null по умолчанию.

3. Обнови CH-запрос `findDaysOfCover` (из Chat 3): добавь поля `frozen_capital` и `stock_out_risk` (§3.3). Верни расширенную модель.

4. Создай `StockBalancingParams` record (#16) — точно по документу (§3.4). С validation constraints и `effective*()` дефолтами.

5. Создай `StockBalancingStrategy` (#17) — `@Component implements PricingStrategy`. Алгоритм — по документу (§3.4). Зоны: critical (near-stockout) → markup, normal → HOLD, overstock → progressive discount. Explanation format — по документу.

6. Добавь `MessageCodes`:
   - `pricing.stock.no_data`
   - `pricing.stock.normal`
   И переводы в `ru.json`.

7. Unit-тесты (#18): покрой: near-stockout markup, overstock discount (progressive), normal level (HOLD), no inventory data (SKIP), extreme overstock (clamp at maxDiscountPct).

Не трогай: frontend, COMPOSITE, COMPETITOR. Velocity-сигналы и CH-запросы из Chat 3 переиспользуй.

Ожидаемый результат: policy с `strategyType = STOCK_BALANCING` работает через полный pricing pipeline.
```

---

## Chat 5: Level 4 — CompositeStrategy

```
Реализуй Level 4 (§4.1–§4.2) из docs/features/2026-04-06-pricing-complete-coverage.md — стратегия COMPOSITE.

Что нужно сделать (задачи #20, #21, #22 из TBD):

1. Расширь `PolicyType` enum: добавь `COMPOSITE`

2. Создай `CompositeParams` record (#20) — по документу (§4.2). Вложенный `ComponentConfig(strategyType, weight, strategyParams)`. Validation:
   - components не пустой
   - каждый weight > 0
   - strategyType ≠ COMPOSITE (no recursion)
   - strategyType ≠ MANUAL_OVERRIDE
   - каждый strategyParams валиден для своего strategyType

3. Создай `CompositeStrategy` (#21) — `@Component implements PricingStrategy`. Инжектирует `PricingStrategyRegistry`. Алгоритм — по документу (§4.2):
   - Для каждого компонента: resolve strategy, build PolicySnapshot из component.strategyParams, вызвать calculate()
   - Отфильтровать SKIP-компоненты
   - Перенормировать веса
   - weighted average
   - Explanation — per-component breakdown

4. Unit-тесты (#22):
   - 2 component'а, оба success → weighted average
   - 3 component'а, один SKIP → перенормировка весов
   - Все SKIP → CompositeStrategy.skip()
   - Один компонент → дегенератный случай (weight=1.0)
   - COMPOSITE в components → validation error
   - Edge case: один компонент с weight=0 → validation error

Не трогай: frontend, COMPETITOR, AI features.

Ожидаемый результат: policy с `strategyType = COMPOSITE` с несколькими вложенными стратегиями корректно рассчитывает weighted target price.
```

---

## Chat 6: Level 5 — CompetitorAnchorStrategy (backend)

```
Реализуй Level 5 backend (§5.1–§5.5) из docs/features/2026-04-06-pricing-complete-coverage.md — конкурентное ценообразование, backend часть.

Что нужно сделать (задачи #24, #25, #26, #27, #28, #29, #30, #31 из TBD):

1. Liquibase миграция (#24): создай `XXXX-competitor-tables.sql` с таблицами `competitor_match` и `competitor_observation`. DDL — точно по документу (§5.2). Добавь в `db.changelog-master.yaml`.

2. JPA entities + repositories (#25):
   - `CompetitorMatchEntity` → `persistence/` пакет в `datapulse-pricing`
   - `CompetitorObservationEntity` → `persistence/`
   - `CompetitorMatchRepository extends JpaRepository`
   - `CompetitorObservationRepository extends JpaRepository`

3. Расширь `PolicyType` enum: добавь `COMPETITOR_ANCHOR`

4. Расширь `PricingSignalSet`: добавь `competitorPrice` (BigDecimal), `competitorTrustLevel` (String), `competitorFreshnessAt` (OffsetDateTime). Обнови все места создания.

5. Competitor signal assembly в `PricingSignalCollector` (#28): SQL из §5.3 (в PG read repository). Для каждого offer: если есть TRUSTED match → взять минимальную цену. Wire в PricingSignalSet.

6. Создай `CompetitorAnchorParams` record (#26) — по §5.5.

7. Создай `CompetitorAnchorStrategy` (#27) — `@Component implements PricingStrategy`. Алгоритм по §5.5: anchor_price = competitor × positionFactor, margin floor с effective_cost_rate.

8. Создай guards (#29):
   - `CompetitorFreshnessGuard` — блокирует если `competitorFreshnessAt` старше N часов
   - `CompetitorTrustGuard` — блокирует если trust_level = CANDIDATE и requireTrustedMatch = true
   Расширь `GuardConfig` record новыми полями.

9. REST API для competitor management (#30) — по §5.6:
   - `CompetitorController` в `api/` пакете
   - `CompetitorService` в `domain/` — CRUD + CSV upload parsing
   - Endpoints: matches CRUD, observations CRUD, bulk CSV upload
   - CSV format: `sku_code,competitor_name,competitor_price`

10. Добавь `MessageCodes` для:
    - `pricing.competitor.missing`
    - `pricing.competitor.stale`
    - `pricing.competitor.untrusted`
    И переводы в `ru.json`.

11. Unit-тесты (#31): стратегия (anchor, margin floor, no data), guards (freshness, trust), CSV parsing.

Не трогай: frontend (будет в Chat 7).

Ожидаемый результат: полный backend для конкурентного ценообразования — от ввода данных до pricing decision.
```

---

## Chat 7: Frontend — формы для новых стратегий + competitor management

```
Реализуй frontend часть для Levels 2-5 из docs/features/2026-04-06-pricing-complete-coverage.md.

Что нужно сделать (задачи #15, #19, #23, #32, #33, #36 из TBD):

1. Policy form для VELOCITY_ADAPTIVE (#15):
   - При выборе strategyType = VELOCITY_ADAPTIVE в form создания/редактирования policy → показать поля:
     - Deceleration threshold (0-100%, default 70%)
     - Acceleration threshold (100-500%, default 130%)
     - Max deceleration discount (1-30%, default 5%)
     - Max acceleration markup (1-20%, default 3%)
     - Min baseline sales (1-1000, default 10)
     - Short window (3-14 дней, default 7)
     - Long window (14-90 дней, default 30)
   - Tooltips с пояснениями
   - Validation: constraints из документа (§2.4)

2. Policy form для STOCK_BALANCING (#19):
   - Поля:
     - Critical days of cover (1-30, default 7) — порог near-stockout
     - Overstock days of cover (30-365, default 60)
     - Stockout markup (1-30%, default 5%)
     - Overstock discount factor (1-50%, default 10%)
     - Max discount (1-50%, default 20%)
     - Lead time (1-180, default 14)
   - Cross-field validation: critical < overstock

3. Policy form для COMPOSITE (#23):
   - Multi-strategy selector: dropdown для выбора стратегии + input для веса
   - Кнопка «Добавить стратегию» → добавить ещё один компонент
   - Для каждого компонента — вложенная форма params (те же формы из п.1-2 + existing TARGET_MARGIN)
   - Validation: COMPOSITE и MANUAL_OVERRIDE в dropdown не доступны

4. Policy form для COMPETITOR_ANCHOR (#33):
   - Поля:
     - Position factor (0.5-2.0, default 1.0)
     - Min margin floor (1-50%, default 10%)
     - Aggregation (MIN/MEDIAN/AVG radio buttons, default MIN)
     - Use margin floor (checkbox, default true)

5. Competitor management page (#32):
   - Новый tab «Конкуренты» в pricing section (routing: `/pricing/competitors`)
   - AG Grid: columns = SKU, Product name, Competitor name, Last price, Observed at, Trust level
   - Toolbar: «Добавить match» button → modal, «Загрузить CSV» button → file upload
   - Inline «Обновить цену» button per row → input field для новой цены
   - Detail panel per match: история observations (таблица)

6. Добавь i18n ключи в `ru.json` (#36):
   - Labels для всех полей форм: `pricing.form.velocity.*`, `pricing.form.stock.*`, `pricing.form.composite.*`, `pricing.form.competitor.*`
   - Tooltips: `pricing.form.tooltip.velocity.*`, etc.
   - Strategy names: `pricing.strategy.velocity_adaptive`, `pricing.strategy.stock_balancing`, etc.
   - Competitor page: `pricing.competitors.title`, `pricing.competitors.add_match`, etc.

7. API service: `CompetitorApiService` в `core/api/` — HTTP transport для competitor endpoints.

Не трогай: backend (сделан в Chats 1-6), AI features, Draft Mode.

Ожидаемый результат: все 4 новые стратегии можно создать через UI, competitor management page работает.
```

---

## Chat 8: Level 6 — AI Pricing Features + документация

```
Реализуй Level 6 (§6.1–§6.3) из docs/features/2026-04-06-pricing-complete-coverage.md — AI pricing features.

Предусловие: vLLM инфраструктура доступна (если нет — реализуй только backend skeleton с TODO для LLM-вызова, чтобы frontend мог работать с mock response).

Что нужно сделать (задачи #37, #38, #39, #40, #41, #42 из TBD):

1. Pricing Advisor (#37):
   - `PricingAdvisorService` в `domain/` модуля `datapulse-pricing`
   - Метод: `generateAdvice(offerId)` → собрать context (last decision, P&L, inventory, competitor) → LLM prompt → cache result
   - System prompt — по §6.1
   - Cache: Caffeine, ключ = `offerId:lastDecisionId`, TTL 24h
   - REST: `POST /api/workspaces/{wsId}/pricing/advisor/{offerId}` → `{ advice, generatedAt, cachedUntil }`
   - Если LLM не доступен: вернуть `{ advice: null, error: "llm.unavailable" }`

2. Impact Simulation Narrative (#38):
   - Расширь response `POST /api/pricing/policies/{policyId}/preview`: добавь optional поле `narrative: string`
   - Генерация: async, не блокирует основной response. Добавить поле `narrativeStatus: PENDING | READY | UNAVAILABLE`
   - Prompt: top-5 biggest changes + summary → 3-5 предложений (§6.2)

3. Proactive Price Insights (#39):
   - Liquibase: таблица `pricing_insight` (id, workspace_id, insight_type, title, body, severity, acknowledged, created_at)
   - `PricingInsightScheduler` в `scheduling/`: daily cron → для каждого workspace → detect 4 типа инсайтов (§6.3) → LLM verbalization → INSERT
   - REST: `GET /api/workspaces/{wsId}/pricing/insights` + `POST .../acknowledge`
   - Notification: `applicationEventPublisher.publishEvent(new InsightCreatedEvent(...))`

4. Frontend: AI Advisor panel (#40):
   - В Detail Panel товара → новая секция «AI-совет» (collapsible)
   - Lazy load: при expand → POST advisor endpoint
   - Показать: текст совета, дату генерации
   - Loading state + error state

5. Frontend: Insights page (#41):
   - Новый tab «Инсайты» в pricing section
   - Список карточек: severity icon + title + body + date + «Прочитано» кнопка
   - Filter: по типу, по дате, acknowledged/unacknowledged
   - Badge на tab: count of unacknowledged insights

6. Обновить документацию (#42):
   - Обнови `docs/modules/pricing.md`:
     - Секция «Strategies»: добавь VELOCITY_ADAPTIVE, STOCK_BALANCING, COMPOSITE, COMPETITOR_ANCHOR
     - Секция «Signal Assembly»: обнови список сигналов — все 14+ подключены
     - Секция «Guards»: добавь CompetitorFreshnessGuard, CompetitorTrustGuard
     - Новая секция «Competitor Price Model»: описание competitor_match, competitor_observation
     - Новая секция «AI Pricing Features»: advisor, narrative, insights
   - Обнови `docs/data-model.md` если нужно (новые таблицы)

7. Добавь i18n ключи:
   - `pricing.advisor.title`, `pricing.advisor.loading`, `pricing.advisor.unavailable`
   - `pricing.insights.title`, `pricing.insights.types.*`, `pricing.insights.acknowledge`

Ожидаемый результат: pricing module на 100% — все стратегии, сигналы, guards, competitor model, AI features реализованы. Документация актуальна.
```

---

## Зависимости между чатами

```
Chat 1 (CH signals) ─────→ Chat 3 (VELOCITY) ──→ Chat 4 (STOCK) ──→ Chat 5 (COMPOSITE)
        ↓                                                                     ↓
Chat 2 (PG signals) ─────→ Chat 6 (COMPETITOR backend) ──→ Chat 7 (Frontend all)
                                                                              ↓
                                                                    Chat 8 (AI + docs)
```

- Chat 1 и Chat 2 можно делать параллельно
- Chat 3 зависит от Chat 1 (velocity queries используют тот же паттерн)
- Chat 4 зависит от Chat 3 (использует те же сигналы)
- Chat 5 зависит от Chat 3+4 (нужны реализованные стратегии для вложенных компонентов)
- Chat 6 может идти параллельно с Chat 3-5
- Chat 7 зависит от Chats 3-6 (backend должен быть готов)
- Chat 8 — последний (AI + финальная документация)
