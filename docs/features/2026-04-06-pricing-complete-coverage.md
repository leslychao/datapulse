# Feature: Pricing — Complete Coverage

**Статус:** TBD_READY
**Дата создания:** 2026-04-06
**Автор:** Виталий Ким
**Целевая фаза:** C (completion) → G → G+

---

## Business Context

### Проблема

Модуль ценообразования реализован архитектурно и покрывает pipeline end-to-end, но работает в деградированном режиме:

1. **Сигналы комиссии, логистики, возвратов** не подключены из ClickHouse — стратегия TARGET_MARGIN работает только с ручным вводом cost rate, хотя данные для автоматического расчёта уже лежат в `fact_finance`, `mart_posting_pnl`, `fact_returns`, `fact_sales`.
2. **Нет адаптивных стратегий** — селлеры не могут автоматически реагировать на изменения скорости продаж или уровня остатков ценой.
3. **Нет конкурентного ценообразования** — главная боль селлеров на WB/Ozon; нет даже ручного ввода конкурентных цен.
4. **Нет AI-рекомендаций** — rule-based pipeline работает, но не хватает человекочитаемых советов и проактивных инсайтов.

### Бизнес-ценность

| Уровень | Outcome | Метрика |
|---------|---------|---------|
| Phase C completion | TARGET_MARGIN в полном AUTO-режиме — без ручного ввода cost rates | Доля offers с decision ≠ HOLD из-за missing signal → 0% |
| VELOCITY_ADAPTIVE | Автоматическая реакция на замедление/ускорение продаж | Снижение overstock на 15-20% |
| STOCK_BALANCING | Ценовое управление остатками — распродажа overstock, защита near-stockout | Frozen capital ↓ 20-30% |
| COMPETITOR_ANCHOR | Удержание конкурентоспособности без ручного мониторинга | Сокращение lost sales от завышенной цены |
| AI Pricing | Объяснимые рекомендации для оператора | Время принятия решения по pricing action ↓ 50% |

---

## Scope

### В scope

- Подключение ClickHouse-сигналов: `avgCommissionPct`, `avgLogisticsPerUnit`, `returnRatePct`
- Подключение PostgreSQL-сигналов: `productStatus`, `marketplaceMinPrice`
- Стратегия `VELOCITY_ADAPTIVE` (Phase G)
- Стратегия `STOCK_BALANCING` (Phase G)
- Стратегия `COMPOSITE` (Phase G+)
- Стратегия `COMPETITOR_ANCHOR` с manual upload MVP (Phase G+)
- AI Pricing Advisor, Impact Narrative, Proactive Insights (Phase G)

### Вне scope

- Координация цен между маркетплейсами (потенциально отдельная фича)
- Автоматический scraping конкурентных цен
- SaaS-интеграции для конкурентных данных (только manual upload MVP)
- Seasonal pricing patterns (нет подтверждённого бизнес-кейса)
- A/B price testing

---

## Architectural Impact

### Затронутые модули

| Модуль | Тип изменения | Описание |
|--------|---------------|----------|
| [Pricing](../modules/pricing.md) | Расширение | Новые стратегии, сигналы, guards, competitor model |
| [Analytics & P&L](../modules/analytics-pnl.md) | Чтение | Pricing signal assembly читает из marts |
| [Seller Operations](../modules/seller-operations.md) | Расширение | Competitor match UI, pricing advisor display |
| Frontend | Расширение | Policy forms для новых стратегий, competitor management, AI advisor panel |

### Изменения в data model

- Новые PostgreSQL-таблицы: `competitor_observation`, `competitor_match`
- Новые значения `PolicyType` enum: `VELOCITY_ADAPTIVE`, `STOCK_BALANCING`, `COMPOSITE`, `COMPETITOR_ANCHOR`
- Расширение `PricingSignalSet`: новые поля для velocity, inventory, competitor сигналов
- Новые ClickHouse-запросы в `PricingClickHouseReadRepository`

---

## Детальный дизайн

### Level 1: Phase C Completion — Подключение сигналов

#### 1.1. Текущее состояние

`PricingSignalCollector.collectBatch()` передаёт `null` для 5 из 14 сигналов:

| Сигнал | Текущее значение | Источник данных | Статус |
|--------|------------------|-----------------|--------|
| `avgCommissionPct` | `null` | `mart_posting_pnl` (CH) | НЕ ПОДКЛЮЧЁН |
| `avgLogisticsPerUnit` | `null` | `mart_posting_pnl` (CH) | НЕ ПОДКЛЮЧЁН |
| `returnRatePct` | `null` | `fact_returns` + `fact_sales` (CH) | НЕ ПОДКЛЮЧЁН |
| `productStatus` | `null` | `marketplace_offer.status` (PG) | НЕ ПОДКЛЮЧЁН — данные уже загружаются |
| `marketplaceMinPrice` | `null` | `canonical_price_current.min_price` (PG) | НЕ ПОДКЛЮЧЁН — данные уже загружаются |

#### 1.2. `avgCommissionPct` — средний % комиссии МП

**Определение:** доля комиссии маркетплейса в выручке за rolling window.

```
avg_commission_pct = (|SUM(marketplace_commission_amount)| + |SUM(acquiring_commission_amount)|)
                     / NULLIF(SUM(revenue_amount), 0)
```

**Источник:** `mart_posting_pnl` — содержит `marketplace_commission_amount`, `acquiring_commission_amount`, `revenue_amount` на уровне постинга с `finance_date` и `seller_sku_id`.

**SQL (per-SKU):**

```sql
SELECT
    coalesce(mp.seller_sku_id, 0) AS seller_sku_id,
    (abs(sum(mp.marketplace_commission_amount)) + abs(sum(mp.acquiring_commission_amount)))
        / nullIf(sum(mp.revenue_amount), 0) AS avg_commission_pct,
    count(*) AS transaction_count
FROM mart_posting_pnl AS mp
WHERE mp.connection_id = :connectionId
  AND mp.seller_sku_id IN (:sellerSkuIds)
  AND mp.finance_date >= today() - :lookbackDays
GROUP BY mp.seller_sku_id
HAVING transaction_count >= :minTransactions
SETTINGS final = 1
```

**SQL (per-category fallback):** для SKU, не прошедших `minTransactions` порог.

```sql
SELECT
    dp.category AS category_name,
    (abs(sum(mp.marketplace_commission_amount)) + abs(sum(mp.acquiring_commission_amount)))
        / nullIf(sum(mp.revenue_amount), 0) AS avg_commission_pct
FROM mart_posting_pnl AS mp
INNER JOIN dim_product AS dp
    ON mp.product_id = dp.product_id AND mp.connection_id = dp.connection_id
WHERE mp.connection_id = :connectionId
  AND dp.category IN (:categories)
  AND mp.finance_date >= today() - :lookbackDays
GROUP BY dp.category
SETTINGS final = 1
```

**Cascade fallback (из `TargetMarginParams`):**

```
1. Per-SKU historical (lookbackDays, ≥ minTransactions)
     ↓ недостаточно данных
2. Per-category historical
     ↓ недостаточно данных
3. Manual value из commissionManualPct
     ↓ не задано
4. StrategyResult.skip → decision HOLD
```

**Параметры из `TargetMarginParams`:**
- `commissionSource`: `AUTO` / `MANUAL` / `AUTO_WITH_MANUAL_FALLBACK` (default)
- `commissionLookbackDays`: default 30
- `commissionMinTransactions`: default 5
- `commissionManualPct`: fallback значение

**Метод в `PricingClickHouseReadRepository`:**

```java
Map<Long, CommissionResult> findAvgCommissionPct(
    long connectionId, List<Long> sellerSkuIds, int lookbackDays, int minTransactions);

Map<String, BigDecimal> findCategoryAvgCommissionPct(
    long connectionId, List<String> categories, int lookbackDays);

record CommissionResult(BigDecimal commissionPct, int transactionCount) {}
```

**Изменения в `PricingSignalCollector`:**
- Маппинг `offerId → sellerSkuId` уже есть (через `findOffersByConnection`)
- Вызвать `findAvgCommissionPct()` с батчем `sellerSkuIds`
- Для SKU без результата → вызвать `findCategoryAvgCommissionPct()` для их категорий
- Передать результат в `PricingSignalSet.avgCommissionPct` вместо `null`

**Важно:** cascade fallback до manual выполняется уже в `TargetMarginStrategy.resolveCommission()` — сигнал-коллектор отвечает только за AUTO-уровни (per-SKU, per-category).

#### 1.3. `avgLogisticsPerUnit` — средняя логистика на единицу

**Определение:** средняя стоимость логистики на единицу проданного товара за rolling window.

```
avg_logistics_per_unit = |SUM(logistics_cost_amount)| / NULLIF(SUM(quantity), 0)
```

**Источник:** `mart_posting_pnl` — содержит `logistics_cost_amount` и `quantity` на уровне постинга.

**SQL:**

```sql
SELECT
    coalesce(mp.seller_sku_id, 0) AS seller_sku_id,
    abs(sum(mp.logistics_cost_amount)) / nullIf(sum(mp.quantity), 0) AS avg_logistics_per_unit
FROM mart_posting_pnl AS mp
WHERE mp.connection_id = :connectionId
  AND mp.seller_sku_id IN (:sellerSkuIds)
  AND mp.finance_date >= today() - :lookbackDays
  AND mp.quantity IS NOT NULL
  AND mp.quantity > 0
GROUP BY mp.seller_sku_id
SETTINGS final = 1
```

**Метод в `PricingClickHouseReadRepository`:**

```java
Map<Long, BigDecimal> findAvgLogisticsPerUnit(
    long connectionId, List<Long> sellerSkuIds, int lookbackDays);
```

**Cascade fallback (аналогично commission):**

```
1. Per-SKU historical
     ↓ нет данных
2. Manual value из logisticsManualAmount
     ↓ не задано
3. logisticsPct = 0 (conservative: не увеличивает effective_cost_rate)
```

Per-category fallback для логистики менее полезен (логистика зависит от габаритов, не от категории) — пропускаем.

**Внимание:** `avgLogisticsPerUnit` — это **абсолютная сумма** (рубли), не процент. В `TargetMarginStrategy.resolveLogisticsPct()` она делится на `currentPrice` для получения доли. Это поведение уже реализовано и корректно.

#### 1.4. `returnRatePct` — процент возвратов

**Определение:** доля возвращённых единиц от проданных за rolling window.

```
return_rate_pct = SUM(return_quantity) / NULLIF(SUM(sale_quantity), 0)
```

**Источник:** `fact_returns` + `fact_sales` (CH) — для rolling window. `mart_returns_analysis` агрегирует по month (period YYYYMM), что не даёт точного rolling window.

**SQL:**

```sql
SELECT
    fs.seller_sku_id,
    coalesce(toDecimal64(fr.return_qty, 4) / nullIf(fs.sale_qty, 0), 0) AS return_rate_pct
FROM (
    SELECT seller_sku_id, sum(quantity) AS sale_qty
    FROM fact_sales
    WHERE connection_id = :connectionId
      AND seller_sku_id IN (:sellerSkuIds)
      AND sale_date >= today() - :lookbackDays
    GROUP BY seller_sku_id
) AS fs
LEFT JOIN (
    SELECT seller_sku_id, sum(quantity) AS return_qty
    FROM fact_returns
    WHERE connection_id = :connectionId
      AND seller_sku_id IN (:sellerSkuIds)
      AND return_date >= today() - :lookbackDays
    GROUP BY seller_sku_id
) AS fr ON fs.seller_sku_id = fr.seller_sku_id
SETTINGS final = 1
```

**Метод:**

```java
Map<Long, BigDecimal> findReturnRatePct(
    long connectionId, List<Long> sellerSkuIds, int lookbackDays);
```

**Fallback:** `returnRatePct = null` → `TargetMarginStrategy` использует `BigDecimal.ZERO` (conservative). Поведение уже реализовано.

#### 1.5. `productStatus` — статус товара (PostgreSQL)

**Текущее состояние:** `findOffersByConnection()` в `PricingDataReadRepository` уже извлекает `mo.status`. Возвращает `OfferRow` с полем `status`. Но `PricingSignalCollector` не передаёт его в `PricingSignalSet`.

**Изменение в `PricingSignalCollector`:**
- Из `findOffersByConnection()` получить маппинг `offerId → status`
- Передать `status` в `PricingSignalSet.productStatus` вместо `null`

**Использование:** eligibility check — товар должен быть в статусе `ACTIVE`. Если не `ACTIVE` → decision = SKIP, reason = «Товар неактивен на маркетплейсе».

#### 1.6. `marketplaceMinPrice` — минимальная цена МП (PostgreSQL)

**Текущее состояние:** `canonical_price_current` содержит колонку `min_price`. SQL-запрос `CURRENT_PRICES` извлекает `price` и `discount_price`, но **не** `min_price`.

**Изменение в `PricingDataReadRepository`:**

SQL `CURRENT_PRICES` → добавить `cpc.min_price`:

```sql
SELECT cpc.marketplace_offer_id, cpc.price, cpc.discount_price, cpc.min_price
FROM canonical_price_current cpc
WHERE cpc.marketplace_offer_id IN (:offerIds)
```

Вернуть `Map<Long, BigDecimal>` для min_price (отдельный маппинг или расширение `findCurrentPrices`).

**Изменение в `PricingSignalCollector`:** передать `minPrices.get(offerId)` в `PricingSignalSet.marketplaceMinPrice` вместо `null`.

**Использование:** constraint `marketplace_min_price` в `PricingConstraintResolver.applyMarketplaceMinPrice()` — уже реализован, принимает `signals.marketplaceMinPrice()`. Сейчас всегда `null` → constraint пропускается.

#### 1.7. Маппинг offerId → sellerSkuId

Все ClickHouse-сигналы (commission, logistics, returns) индексируются по `seller_sku_id`, а `PricingSignalCollector` оперирует `offerId`. Маппинг нужен в обоих направлениях.

**Текущее состояние:** `PricingDataReadRepository.findMarketplaceSkus(offerIds)` возвращает `Map<Long, String>` (offerId → marketplace_sku). Аналогично нужен `offerId → sellerSkuId`.

**Решение:** `findOffersByConnection()` возвращает `OfferRow(id, sellerSkuId, categoryId, connectionId, status)` — все маппинги доступны. Передать `Map<Long, Long>` (offerId → sellerSkuId) и `Map<Long, Long>` (offerId → categoryId) в signal collector, использовать для преобразования ключей CH-результатов обратно в offerId.

---

### Level 2: VELOCITY_ADAPTIVE — адаптация к скорости продаж

#### 2.1. Концепция

Стратегия адаптирует цену к динамике продаж:
- Продажи замедляются → снижаем цену (стимулируем спрос)
- Продажи ускоряются → повышаем цену (максимизируем маржу)
- Стабильные продажи → HOLD (цена ок)

#### 2.2. Новые сигналы

Расширить `PricingSignalSet` новыми полями:

```java
// Velocity signals
BigDecimal salesVelocityShort,       // units/day за короткое окно (default 7 дней)
BigDecimal salesVelocityLong,        // units/day за длинное окно (default 30 дней)
BigDecimal daysOfCover               // из mart_inventory_analysis (последний snapshot)
```

#### 2.3. SQL для velocity-сигналов

**Short-window velocity (7 дней):**

```sql
SELECT seller_sku_id,
       toDecimal64(sum(quantity), 2) / :shortWindowDays AS velocity_short
FROM fact_sales
WHERE connection_id = :connectionId
  AND seller_sku_id IN (:sellerSkuIds)
  AND sale_date >= today() - :shortWindowDays
GROUP BY seller_sku_id
SETTINGS final = 1
```

**Long-window velocity (30 дней):**

```sql
SELECT seller_sku_id,
       toDecimal64(sum(quantity), 2) / :longWindowDays AS velocity_long
FROM fact_sales
WHERE connection_id = :connectionId
  AND seller_sku_id IN (:sellerSkuIds)
  AND sale_date >= today() - :longWindowDays
GROUP BY seller_sku_id
SETTINGS final = 1
```

**Days of cover (из mart_inventory_analysis):**

```sql
SELECT
    m.seller_sku_id,
    sum(m.available) AS total_available,
    avg(m.days_of_cover) AS avg_days_of_cover
FROM mart_inventory_analysis AS m
WHERE m.connection_id = :connectionId
  AND m.seller_sku_id IN (:sellerSkuIds)
  AND m.analysis_date = (
      SELECT max(analysis_date) FROM mart_inventory_analysis
      WHERE connection_id = :connectionId
  )
GROUP BY m.seller_sku_id
SETTINGS final = 1
```

**Методы в `PricingClickHouseReadRepository`:**

```java
Map<Long, BigDecimal> findSalesVelocity(
    long connectionId, List<Long> sellerSkuIds, int windowDays);

Map<Long, BigDecimal> findDaysOfCover(
    long connectionId, List<Long> sellerSkuIds);
```

#### 2.4. Стратегия `VelocityAdaptiveStrategy`

**Enum:** добавить `VELOCITY_ADAPTIVE` в `PolicyType`.

**Params record:**

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record VelocityAdaptiveParams(
    BigDecimal decelerationThreshold,     // ratio ниже → снижение (default 0.70)
    BigDecimal accelerationThreshold,     // ratio выше → повышение (default 1.30)
    BigDecimal decelerationDiscountPct,   // макс. снижение за решение (default 0.05)
    BigDecimal accelerationMarkupPct,     // макс. повышение за решение (default 0.03)
    Integer minBaselineSales,             // минимум продаж за long window (default 10 units)
    Integer velocityWindowShortDays,      // короткое окно (default 7)
    Integer velocityWindowLongDays,       // длинное окно (default 30)
    BigDecimal roundingStep,
    TargetMarginParams.RoundingDirection roundingDirection
) {
    public BigDecimal effectiveDecelerationThreshold() {
        return decelerationThreshold != null ? decelerationThreshold : new BigDecimal("0.70");
    }
    public BigDecimal effectiveAccelerationThreshold() {
        return accelerationThreshold != null ? accelerationThreshold : new BigDecimal("1.30");
    }
    public BigDecimal effectiveDecelerationDiscountPct() {
        return decelerationDiscountPct != null ? decelerationDiscountPct : new BigDecimal("0.05");
    }
    public BigDecimal effectiveAccelerationMarkupPct() {
        return accelerationMarkupPct != null ? accelerationMarkupPct : new BigDecimal("0.03");
    }
    public int effectiveMinBaselineSales() {
        return minBaselineSales != null ? minBaselineSales : 10;
    }
    public int effectiveVelocityWindowShortDays() {
        return velocityWindowShortDays != null ? velocityWindowShortDays : 7;
    }
    public int effectiveVelocityWindowLongDays() {
        return velocityWindowLongDays != null ? velocityWindowLongDays : 30;
    }
}
```

**Validation constraints:**

| Параметр | Constraint | Обоснование |
|----------|-----------|-------------|
| `decelerationThreshold` | (0.0, 1.0) | Ratio < 1.0 означает замедление |
| `accelerationThreshold` | (1.0, 5.0) | Ratio > 1.0 означает ускорение |
| `decelerationDiscountPct` | [0.01, 0.30] | Макс. 30% снижение за решение |
| `accelerationMarkupPct` | [0.01, 0.20] | Макс. 20% повышение за решение |
| `minBaselineSales` | [1, 1000] | Минимальная выборка |
| `velocityWindowShortDays` | [3, 14] | 3-14 дней для short window |
| `velocityWindowLongDays` | [14, 90] | 14-90 дней для long window |

**Алгоритм:**

```
1. Check: salesVelocityLong is null OR baseline_units < minBaselineSales
   → StrategyResult.skip("Insufficient sales data", PRICING_VELOCITY_INSUFFICIENT_DATA)

2. velocity_ratio = salesVelocityShort / salesVelocityLong

3. if velocity_ratio < decelerationThreshold:
     adjustment = -decelerationDiscountPct × min(1.0, (decelerationThreshold - velocity_ratio) / decelerationThreshold)
   elif velocity_ratio > accelerationThreshold:
     adjustment = +accelerationMarkupPct × min(1.0, (velocity_ratio - accelerationThreshold) / accelerationThreshold)
   else:
     → StrategyResult.skip("Velocity stable, no change needed", PRICING_VELOCITY_STABLE)

4. raw_target_price = currentPrice × (1 + adjustment)

5. → StrategyResult.success(raw_target_price, explanation)
```

Adjustment пропорционален отклонению от порога (не фиксированный) — мягкая реакция при небольшом отклонении, усиливающаяся при значительном.

**Explanation format:**

```
velocity_short=2.3 u/d (7d), velocity_long=3.5 u/d (30d),
ratio=0.66, threshold_decel=0.70,
adjustment=-4.3%, raw=1 435
```

#### 2.5. Frontend: форма для VELOCITY_ADAPTIVE policy

Policy form расширяется: при выборе `strategyType = VELOCITY_ADAPTIVE`:
- Показать поля: deceleration/acceleration thresholds, discount/markup pcts, min baseline sales, window sizes
- Tooltips с пояснениями по каждому полю (ключи `pricing.form.tooltip.velocity.*`)
- Live preview: «При замедлении продаж до 70% от нормы — снижение до 5%»

---

### Level 3: STOCK_BALANCING — ценовое управление остатками

#### 3.1. Концепция

Стратегия адаптирует цену к уровню остатков:
- Overstock (days_of_cover > порог) → снижаем цену (освободить капитал)
- Near-stockout (days_of_cover < порог) → повышаем цену (растянуть остатки до пополнения)
- Нормальный уровень → HOLD

#### 3.2. Новые сигналы

Используются те же velocity-сигналы из Level 2 (`daysOfCover`, `salesVelocityShort`), плюс:

```java
BigDecimal frozenCapital,            // из mart_inventory_analysis
String stockOutRisk                  // 'CRITICAL' / 'WARNING' / 'NORMAL'
```

#### 3.3. SQL

Те же запросы к `mart_inventory_analysis`, что и в Level 2 (§2.3), с дополнительными полями:

```sql
SELECT
    m.seller_sku_id,
    sum(m.available) AS total_available,
    avg(m.days_of_cover) AS avg_days_of_cover,
    sum(m.frozen_capital) AS frozen_capital,
    anyLast(m.stock_out_risk) AS stock_out_risk
FROM mart_inventory_analysis AS m
WHERE m.connection_id = :connectionId
  AND m.seller_sku_id IN (:sellerSkuIds)
  AND m.analysis_date = (
      SELECT max(analysis_date) FROM mart_inventory_analysis
      WHERE connection_id = :connectionId
  )
GROUP BY m.seller_sku_id
SETTINGS final = 1
```

#### 3.4. Стратегия `StockBalancingStrategy`

**Enum:** добавить `STOCK_BALANCING` в `PolicyType`.

**Params record:**

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockBalancingParams(
    Integer criticalDaysOfCover,        // порог near-stockout (default 7)
    Integer overstockDaysOfCover,       // порог overstock (default 60)
    BigDecimal stockoutMarkupPct,       // повышение при near-stockout (default 0.05)
    BigDecimal overstockDiscountFactor, // скорость снижения при overstock (default 0.10)
    BigDecimal maxDiscountPct,          // максимум снижения (default 0.20)
    Integer leadTimeDays,               // lead time поставки (default 14)
    BigDecimal roundingStep,
    TargetMarginParams.RoundingDirection roundingDirection
) {
    public int effectiveCriticalDaysOfCover() {
        return criticalDaysOfCover != null ? criticalDaysOfCover : 7;
    }
    public int effectiveOverstockDaysOfCover() {
        return overstockDaysOfCover != null ? overstockDaysOfCover : 60;
    }
    public BigDecimal effectiveStockoutMarkupPct() {
        return stockoutMarkupPct != null ? stockoutMarkupPct : new BigDecimal("0.05");
    }
    public BigDecimal effectiveOverstockDiscountFactor() {
        return overstockDiscountFactor != null ? overstockDiscountFactor : new BigDecimal("0.10");
    }
    public BigDecimal effectiveMaxDiscountPct() {
        return maxDiscountPct != null ? maxDiscountPct : new BigDecimal("0.20");
    }
    public int effectiveLeadTimeDays() {
        return leadTimeDays != null ? leadTimeDays : 14;
    }
}
```

**Validation constraints:**

| Параметр | Constraint | Обоснование |
|----------|-----------|-------------|
| `criticalDaysOfCover` | [1, 30] | Меньше 1 не имеет смысла, больше 30 → overlap с normal |
| `overstockDaysOfCover` | [30, 365] | Порог overstock |
| `criticalDaysOfCover < overstockDaysOfCover` | Cross-field | Зоны не пересекаются |
| `stockoutMarkupPct` | [0.01, 0.30] | Макс. 30% повышение |
| `overstockDiscountFactor` | [0.01, 0.50] | Множитель скорости снижения |
| `maxDiscountPct` | [0.01, 0.50] | Абсолютный потолок снижения |
| `leadTimeDays` | [1, 180] | Lead time поставки |

**Алгоритм:**

```
1. Check: daysOfCover is null
   → StrategyResult.skip("No inventory data", PRICING_STOCK_NO_DATA)

2. if daysOfCover < criticalDaysOfCover:
     adjustment = +stockoutMarkupPct
   elif daysOfCover > overstockDaysOfCover:
     overshoot = (daysOfCover - overstockDaysOfCover) / overstockDaysOfCover
     discount = min(overshoot × overstockDiscountFactor, maxDiscountPct)
     adjustment = -discount
   else:
     → StrategyResult.skip("Stock level normal", PRICING_STOCK_NORMAL)

3. raw_target_price = currentPrice × (1 + adjustment)

4. → StrategyResult.success(raw_target_price, explanation)
```

**Explanation format:**

```
days_of_cover=85.0, overstock_threshold=60,
overshoot=41.7%, discount_factor=0.10,
adjustment=-4.2%, raw=4 790
```

---

### Level 4: COMPOSITE — взвешенная комбинация стратегий

#### 4.1. Концепция

Позволяет селлеру комбинировать несколько стратегий с весами. Финальная target price — средневзвешенная raw prices всех компонентов.

#### 4.2. Стратегия `CompositeStrategy`

**Enum:** добавить `COMPOSITE` в `PolicyType`.

**Params record:**

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompositeParams(
    List<ComponentConfig> components,
    BigDecimal roundingStep,
    TargetMarginParams.RoundingDirection roundingDirection
) {
    public record ComponentConfig(
        PolicyType strategyType,
        BigDecimal weight,
        String strategyParams    // JSON-строка — params для вложенной стратегии
    ) {}
}
```

**Validation:**

| Правило | Описание |
|---------|----------|
| `components` не пустой | Минимум 1 компонент |
| Каждый `weight` > 0 | Нулевые и отрицательные запрещены |
| `strategyType ≠ COMPOSITE` | Вложенный COMPOSITE запрещён (no recursion) |
| `strategyType ≠ MANUAL_OVERRIDE` | MANUAL_OVERRIDE — не расчётная стратегия |
| Каждый `strategyParams` валиден | Десериализуется в typed record, проходит Jakarta Validation |

**Алгоритм:**

```
1. Для каждого component:
     strategy = registry.get(component.strategyType)
     policySnapshot = build from component.strategyParams
     result_i = strategy.calculate(signals, policySnapshot)

2. Отфильтровать компоненты с result.rawTargetPrice = null (SKIP/HOLD):
     if все компоненты SKIP → StrategyResult.skip("All component strategies skipped")

3. Перенормировать веса оставшихся:
     effective_weight_i = weight_i / SUM(weights of successful components)

4. weighted_target = Σ(rawTargetPrice_i × effective_weight_i)

5. → StrategyResult.success(weighted_target, explanation)
```

**Explanation format:**

```
COMPOSITE (2/3 components):
  TARGET_MARGIN w=0.60 → 3 890 (target_margin=25%, ...)
  VELOCITY_ADAPTIVE w=0.40 → 3 750 (ratio=0.66, adj=-4.3%)
  STOCK_BALANCING SKIPPED (no inventory data)
weighted_raw=3 834
```

**Зависимости:** Level 4 требует реализации хотя бы 2 стратегий из Levels 1-3.

---

### Level 5: COMPETITOR_ANCHOR — конкурентное ценообразование

#### 5.1. Концепция (Manual Upload MVP)

Селлер загружает данные о ценах конкурентов вручную (через UI). Стратегия привязывает цену к конкурентному ориентиру с margin floor.

**Scope MVP:** ручной ввод/CSV upload. Без SaaS-интеграций, без scraping.

#### 5.2. Новые PostgreSQL-таблицы

**Liquibase migration: `XXXX-competitor-tables.sql`**

```sql
--liquibase formatted sql

--changeset datapulse:XXXX-competitor-tables

CREATE TABLE competitor_match (
    id                      bigserial   PRIMARY KEY,
    workspace_id            bigint      NOT NULL,
    marketplace_offer_id    bigint      NOT NULL,
    competitor_name         varchar(255),
    competitor_listing_url  varchar(1000),
    match_method            varchar(20) NOT NULL DEFAULT 'MANUAL',
    trust_level             varchar(20) NOT NULL DEFAULT 'TRUSTED',
    matched_by              bigint,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_cm_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_cm_offer     FOREIGN KEY (marketplace_offer_id) REFERENCES marketplace_offer (id),
    CONSTRAINT fk_cm_user      FOREIGN KEY (matched_by) REFERENCES app_user (id)
);

CREATE INDEX idx_cm_offer ON competitor_match (marketplace_offer_id);
CREATE INDEX idx_cm_workspace ON competitor_match (workspace_id);

CREATE TABLE competitor_observation (
    id                    bigserial   PRIMARY KEY,
    competitor_match_id   bigint      NOT NULL,
    competitor_price      decimal     NOT NULL,
    currency              varchar(3)  NOT NULL DEFAULT 'RUB',
    observed_at           timestamptz NOT NULL,
    created_at            timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_co_match FOREIGN KEY (competitor_match_id) REFERENCES competitor_match (id)
);

CREATE INDEX idx_co_match      ON competitor_observation (competitor_match_id);
CREATE INDEX idx_co_observed_at ON competitor_observation (observed_at DESC);

--rollback DROP TABLE competitor_observation; DROP TABLE competitor_match;
```

**Модели:** `match_method` = `MANUAL` | `AUTO`, `trust_level` = `TRUSTED` | `CANDIDATE` | `REJECTED`.

#### 5.3. Новые сигналы

Расширить `PricingSignalSet`:

```java
BigDecimal competitorPrice,          // медиана из последних observations (или min)
String competitorTrustLevel,         // TRUSTED / CANDIDATE
OffsetDateTime competitorFreshnessAt // observed_at последнего observation
```

**SQL для сборки competitor signal (PostgreSQL):**

```sql
SELECT
    cm.marketplace_offer_id,
    cm.trust_level,
    co.competitor_price,
    co.observed_at
FROM competitor_match cm
JOIN LATERAL (
    SELECT co.competitor_price, co.observed_at
    FROM competitor_observation co
    WHERE co.competitor_match_id = cm.id
    ORDER BY co.observed_at DESC
    LIMIT 1
) co ON true
WHERE cm.marketplace_offer_id IN (:offerIds)
  AND cm.trust_level IN ('TRUSTED', 'CANDIDATE')
```

Если несколько competitor_match для одного offer — берём минимальную цену среди TRUSTED.

#### 5.4. Новые guards

| Guard | `guardName()` | Блокирует когда | `order()` |
|-------|---------------|-----------------|-----------|
| `CompetitorFreshnessGuard` | `competitor_freshness_guard` | Данные о конкурентах старше `competitorFreshnessHours` (default 72) | 25 |
| `CompetitorTrustGuard` | `competitor_trust_guard` | `trust_level = CANDIDATE` и `requireTrustedMatch = true` | 26 |

Конфигурация в `guard_config` JSONB:

```json
{
  "competitor_freshness_guard_enabled": true,
  "competitor_freshness_hours": 72,
  "competitor_trust_guard_enabled": false
}
```

Расширить `GuardConfig` record соответствующими полями.

#### 5.5. Стратегия `CompetitorAnchorStrategy`

**Enum:** добавить `COMPETITOR_ANCHOR` в `PolicyType`.

**Params record:**

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompetitorAnchorParams(
    BigDecimal positionFactor,           // 1.0=match, 0.95=на 5% дешевле (default 1.0)
    BigDecimal minMarginPct,             // margin floor (default 0.10)
    CompetitorPriceAggregation aggregation, // MIN / MEDIAN / AVG (default MIN)
    Boolean useMarginFloor,              // использовать margin floor (default true)
    BigDecimal roundingStep,
    TargetMarginParams.RoundingDirection roundingDirection
) {
    public enum CompetitorPriceAggregation {
        MIN, MEDIAN, AVG
    }
    public BigDecimal effectivePositionFactor() {
        return positionFactor != null ? positionFactor : BigDecimal.ONE;
    }
    public BigDecimal effectiveMinMarginPct() {
        return minMarginPct != null ? minMarginPct : new BigDecimal("0.10");
    }
    public CompetitorPriceAggregation effectiveAggregation() {
        return aggregation != null ? aggregation : CompetitorPriceAggregation.MIN;
    }
    public boolean effectiveUseMarginFloor() {
        return useMarginFloor == null || useMarginFloor;
    }
}
```

**Алгоритм:**

```
1. Check: competitorPrice is null
   → StrategyResult.skip("No competitor data", PRICING_COMPETITOR_MISSING)

2. anchor_price = competitorPrice × positionFactor

3. if useMarginFloor AND cogs is not null:
     effective_cost_rate = commission + logistics + returns + ads (из signals)
     denominator = 1 - minMarginPct - effective_cost_rate
     if denominator > 0:
       margin_floor_price = cogs / denominator
     else:
       margin_floor_price = cogs × 2  // fallback: 100% markup
     target_price = MAX(anchor_price, margin_floor_price)
   else:
     target_price = anchor_price

4. → StrategyResult.success(target_price, explanation)
```

**Explanation format:**

```
competitor_price=3 500, position_factor=0.95,
anchor=3 325, margin_floor=2 890 (min_margin=10%),
target=max(3 325, 2 890)=3 325, raw=3 325
```

#### 5.6. REST API для competitor management

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/workspaces/{wsId}/competitors/matches` | Any role | Список matche'й. Filter: `?marketplaceOfferId=...` |
| POST | `/api/workspaces/{wsId}/competitors/matches` | PRICING_MANAGER, ADMIN, OWNER | Создать match. Body: `{ marketplaceOfferId, competitorName, competitorListingUrl }` |
| DELETE | `/api/workspaces/{wsId}/competitors/matches/{matchId}` | PRICING_MANAGER, ADMIN, OWNER | Удалить match |
| POST | `/api/workspaces/{wsId}/competitors/matches/{matchId}/observations` | PRICING_MANAGER, ADMIN, OWNER | Добавить наблюдение цены. Body: `{ competitorPrice, observedAt? }` |
| GET | `/api/workspaces/{wsId}/competitors/matches/{matchId}/observations` | Any role | История наблюдений |
| POST | `/api/workspaces/{wsId}/competitors/bulk-upload` | PRICING_MANAGER, ADMIN, OWNER | CSV upload. Body: multipart. Format: `sku_code,competitor_name,competitor_price` |

#### 5.7. Frontend: competitor management

- Новая страница в разделе Pricing: «Конкуренты» (tab в pricing layout)
- Таблица matche'й: SKU, конкурент, последняя цена, свежесть, trust level
- Inline add observation: кнопка «Обновить цену» → input поле
- CSV upload: drag-and-drop зона + file picker
- В detail panel товара: секция «Конкурентные цены» с историей

---

### Level 6: AI Pricing Features (Phase G)

#### 6.1. Pricing Advisor (F4 из ai-llm-insights.md)

**Что:** LLM-генерированный совет в Detail Panel рядом с rule-based pricing explanation. On-demand, кэш 24h.

**Вход LLM:**
- Последний `price_decision` с `explanation_summary`, `signal_snapshot`, `constraints_applied`, `guards_evaluated`
- P&L данные из `mart_product_pnl` (последний период)
- Inventory данные из `mart_inventory_analysis`
- Competitor data (если есть)

**System prompt (template):**

```
Ты — pricing advisor для селлеров маркетплейсов.
Проанализируй данные и дай короткую рекомендацию (2-3 предложения).
Формат: [Рекомендация] + [Обоснование] + [Риски].
Не принимай решений, только советуй. Будь конкретен — цифры, проценты.
```

**API:**

| Method | Path | Описание |
|--------|------|----------|
| POST | `/api/workspaces/{wsId}/pricing/advisor/{offerId}` | Генерация совета. Response: `{ advice, generatedAt, cachedUntil }` |

**Кэширование:** Redis/Caffeine, ключ = `offerId:lastDecisionId`, TTL 24h.

**Frontend:** в Detail Panel → секция «AI-совет» (collapsible, lazy-load on expand).

#### 6.2. Impact Simulation Narrative (F9)

**Что:** при выполнении Impact Preview — LLM генерирует текстовое описание результата.

**Вход LLM:** Impact preview result (summary + top-5 biggest changes + top-3 blocked by guards).

**Выход:** 3-5 предложений:
> «Политика затронет 245 товаров. Средняя цена снизится на 8.2%, что при текущей velocity должно увеличить оборот примерно на 10-12%. 3 товара заблокированы из-за нулевых остатков. Минимальная маржа после изменения — 14.2% (товар XYZ-001), что выше порога 10%.»

**API:** включается как поле `narrative` в response `POST /api/pricing/policies/{policyId}/preview` (optional, генерируется async).

#### 6.3. Proactive Price Insights (F5)

**Что:** автоматические инсайты, генерируемые по расписанию (daily) или после ETL sync.

**Типы инсайтов:**

| Тип | Вход | Пример |
|-----|------|--------|
| Price increase candidate | margin > threshold AND velocity growing | «5 товаров с маржой > 40% и растущими продажами — кандидаты на повышение цены» |
| Overstock liquidation | days_of_cover > 90 AND frozen_capital > X | «12 товаров с запасом > 90 дней, замороженный капитал 450K ₽ — рассмотрите снижение цены» |
| High DRR alert | ad_cost_ratio > 30% | «3 товара с DRR > 30% — рекламные расходы съедают маржу» |
| Competitor undercut | competitor_price < current_price × 0.9 | «Конкурент снизил цену на 15% по 8 товарам» |

**Scheduling:** `@Scheduled` daily → генерация через LLM → сохранение в `pricing_insight` таблицу → notification.

**API:**

| Method | Path | Описание |
|--------|------|----------|
| GET | `/api/workspaces/{wsId}/pricing/insights` | Список инсайтов. Filter: `?type=...&from=...&acknowledged=false` |
| POST | `/api/workspaces/{wsId}/pricing/insights/{id}/acknowledge` | Пометить как прочитанный |

---

## Technical Breakdown (TBD)

**Статус:** готов

### Предусловия

- [x] Phase C (Pricing) pipeline: eligibility, signal assembly, strategy, constraints, guards, decision, explanation, action scheduling
- [x] Phase B (Analytics): `mart_posting_pnl`, `mart_product_pnl`, `mart_returns_analysis`, `mart_inventory_analysis` материализуются
- [x] Phase D (Execution): action lifecycle, executor worker
- [ ] Phase G (AI): vLLM инфраструктура для levels 6.1-6.3

### Задачи

| # | Задача | Level | Зависимости | Оценка | Приоритет |
|---|--------|-------|-------------|--------|-----------|
| 1 | CH: `findAvgCommissionPct` + category fallback в `PricingClickHouseReadRepository` | L1 | — | S | must |
| 2 | CH: `findAvgLogisticsPerUnit` в `PricingClickHouseReadRepository` | L1 | — | S | must |
| 3 | CH: `findReturnRatePct` в `PricingClickHouseReadRepository` | L1 | — | S | must |
| 4 | PG: добавить `min_price` в `findCurrentPrices` SQL | L1 | — | XS | must |
| 5 | PG: wire `productStatus` и `marketplaceMinPrice` в signal collector | L1 | #4 | XS | must |
| 6 | Wire CH signals: commission, logistics, returns в `PricingSignalCollector.collectBatch()` | L1 | #1, #2, #3 | M | must |
| 7 | Mapping `offerId ↔ sellerSkuId` в signal collector | L1 | — | S | must |
| 8 | Unit-тесты: CH queries + signal collector с real signals | L1 | #6 | M | must |
| 9 | Расширить `PricingSignalSet`: velocity/inventory signals | L2 | — | S | must |
| 10 | CH: `findSalesVelocity` + `findDaysOfCover` в `PricingClickHouseReadRepository` | L2 | — | S | must |
| 11 | Wire velocity/inventory signals в `PricingSignalCollector` | L2 | #9, #10 | S | must |
| 12 | `VelocityAdaptiveParams` record + validation | L2 | — | S | must |
| 13 | `VelocityAdaptiveStrategy` — `@Component implements PricingStrategy` | L2 | #9, #12 | M | must |
| 14 | Unit-тесты: `VelocityAdaptiveStrategy` | L2 | #13 | M | must |
| 15 | Frontend: policy form для VELOCITY_ADAPTIVE | L2 | #13 | M | must |
| 16 | `StockBalancingParams` record + validation | L3 | — | S | must |
| 17 | `StockBalancingStrategy` — `@Component implements PricingStrategy` | L3 | #9, #16 | M | must |
| 18 | Unit-тесты: `StockBalancingStrategy` | L3 | #17 | M | must |
| 19 | Frontend: policy form для STOCK_BALANCING | L3 | #17 | M | must |
| 20 | `CompositeParams` record + validation (no recursion check) | L4 | — | S | must |
| 21 | `CompositeStrategy` — delegates to registry, weighted average | L4 | #20 | M | should |
| 22 | Unit-тесты: `CompositeStrategy` | L4 | #21 | M | should |
| 23 | Frontend: policy form для COMPOSITE (multi-strategy selector) | L4 | #21 | L | should |
| 24 | Liquibase: `competitor_match` + `competitor_observation` tables | L5 | — | S | should |
| 25 | JPA entities + repositories для competitor tables | L5 | #24 | S | should |
| 26 | `CompetitorAnchorParams` record + validation | L5 | — | S | should |
| 27 | `CompetitorAnchorStrategy` — `@Component implements PricingStrategy` | L5 | #26 | M | should |
| 28 | Competitor signal assembly в `PricingSignalCollector` | L5 | #25 | M | should |
| 29 | `CompetitorFreshnessGuard` + `CompetitorTrustGuard` | L5 | — | S | should |
| 30 | Competitor REST API: matches CRUD + observations + CSV upload | L5 | #25 | L | should |
| 31 | Unit-тесты: `CompetitorAnchorStrategy` + guards | L5 | #27, #29 | M | should |
| 32 | Frontend: competitor management page (matches, observations, upload) | L5 | #30 | L | should |
| 33 | Frontend: policy form для COMPETITOR_ANCHOR | L5 | #27 | M | should |
| 34 | `PolicyType` enum: добавить `VELOCITY_ADAPTIVE`, `STOCK_BALANCING`, `COMPOSITE`, `COMPETITOR_ANCHOR` | L2-L5 | — | XS | must |
| 35 | `MessageCodes`: добавить ключи для новых стратегий и guards | L2-L5 | — | S | must |
| 36 | `ru.json`: добавить переводы для новых ключей, strategy labels, form tooltips | L2-L5 | #35 | M | must |
| 37 | AI: Pricing Advisor endpoint + LLM prompt + cache | L6 | vLLM infra | L | could |
| 38 | AI: Impact Simulation Narrative (extension of preview response) | L6 | #37, vLLM infra | M | could |
| 39 | AI: Proactive Price Insights — scheduler + insight types + API | L6 | #37, vLLM infra | L | could |
| 40 | Frontend: AI Advisor panel в Detail Panel | L6 | #37 | M | could |
| 41 | Frontend: Insights page в Pricing section | L6 | #39 | M | could |
| 42 | Обновить `docs/modules/pricing.md`: стратегии, сигналы, guards, competitor model | all | last | M | must |

### Порядок реализации

```
Phase 1 — Level 1 (Phase C completion, 1-2 дня):
  #4 + #7 (PG signals prep) → #5 (wire PG signals)
  #1 + #2 + #3 (CH queries, параллельно) → #6 (wire CH signals)
  #34 (PolicyType enum — нужен для L2+)
  #8 (тесты)

Phase 2 — Levels 2-3 (адаптивные стратегии, 1-2 недели):
  #9 (расширить SignalSet) → #10 (CH queries) → #11 (wire)
  #12 → #13 (VelocityAdaptive) → #14 (тесты) → #15 (frontend)
  #16 → #17 (StockBalancing) → #18 (тесты) → #19 (frontend)
  #35 + #36 (i18n)

Phase 3 — Level 4 (COMPOSITE, 3-4 дня):
  #20 → #21 → #22 → #23

Phase 4 — Level 5 (COMPETITOR_ANCHOR, 2-3 недели):
  #24 → #25 → #26 → #27 + #28 + #29 → #30 → #31 → #32 + #33

Phase 5 — Level 6 (AI, 2-3 недели, требует vLLM):
  #37 → #38 + #39 → #40 + #41

Финал:
  #42 (документация)
```

### Риски реализации

| Риск | Вероятность | Impact | Митигация |
|------|-------------|--------|-----------|
| CH-запросы для signals тяжёлые при >10K offers | LOW | Pricing run замедляется | Batch queries с `IN (:sellerSkuIds)` — уже паттерн. `SETTINGS final = 1` + partition pruning по `finance_date`. При необходимости — pre-computed materialized view |
| Velocity signals шумные (сезонность, промо-всплески) | MED | Ложные срабатывания стратегии | `minBaselineSales` порог + длинный baseline window (30 дней) сглаживают шум. Guard `max_price_change` ограничивает амплитуду |
| Competitor data стареет (manual upload) | HIGH | Стратегия работает на stale данных | `CompetitorFreshnessGuard` блокирует при данных старше 72h. Alert при stale competitor data |
| COMPOSITE strategy: debugging сложных взвешенных решений | MED | Трудно объяснить пользователю | Detailed explanation с per-component breakdown. UI показывает вклад каждой стратегии |
| LLM infrastructure не готова к Phase G | LOW | AI features блокированы | Levels 1-5 не зависят от LLM. AI features — отдельная фаза |

### Definition of Done (per Level)

**Level 1:**
- [ ] CH queries для commission, logistics, returns возвращают корректные значения
- [ ] `PricingSignalCollector` передаёт все 14 сигналов (нет `null` для подключённых)
- [ ] TARGET_MARGIN в AUTO mode: commission и logistics рассчитываются автоматически
- [ ] Cascade fallback работает: per-SKU → per-category → manual → SKIP
- [ ] Тесты: `PricingSignalCollectorTest` обновлён с mock CH responses
- [ ] Тесты: `TargetMarginStrategyTest` покрывает AUTO/MANUAL/FALLBACK scenarios

**Level 2-3:**
- [ ] `VelocityAdaptiveStrategy` и `StockBalancingStrategy` зарегистрированы в `PricingStrategyRegistry`
- [ ] Policy CRUD: create/update с новыми `strategyType` работает
- [ ] Pricing run: offers с новыми стратегиями проходят полный pipeline
- [ ] Frontend: формы создания policy для новых стратегий
- [ ] Тесты: per-strategy unit-тесты (deceleration, acceleration, stockout, overstock, edge cases)

**Level 4:**
- [ ] `CompositeStrategy` делегирует в registry, weighted average корректен
- [ ] Skip-компоненты: веса перенормируются
- [ ] Validation: COMPOSITE не содержит COMPOSITE (no recursion)
- [ ] Frontend: multi-strategy selector с весами

**Level 5:**
- [ ] Competitor tables мигрированы
- [ ] CRUD для matches + observations работает
- [ ] CSV upload парсит и создаёт matches + observations
- [ ] `CompetitorAnchorStrategy` рассчитывает anchor price с margin floor
- [ ] Guards: freshness и trust блокируют при stale/untrusted data
- [ ] Frontend: competitor management page

**Level 6:**
- [ ] Pricing Advisor генерирует совет через LLM
- [ ] Кэш 24h работает
- [ ] Impact narrative генерируется при preview
- [ ] Proactive insights: daily scheduler + 4 типа инсайтов
- [ ] Frontend: advisor panel + insights page

---

## References

- [Pricing module](../modules/pricing.md) — полная архитектура pipeline, стратегий, guards
- [Analytics & P&L](../modules/analytics-pnl.md) — star schema, marts, materializers
- [AI/LLM Insights](2026-03-31-ai-llm-insights.md) — F4 (Pricing Advisor), F5 (Proactive), F9 (Impact Narrative)
- [Project Vision](../project-vision-and-scope.md) — capability 5 (Pricing decisioning), Phase C/G
- Existing code:
  - `PricingSignalCollector.java` — signal assembly
  - `PricingClickHouseReadRepository.java` — CH queries
  - `PricingDataReadRepository.java` — PG queries
  - `PricingStrategy.java` — strategy interface
  - `PricingStrategyRegistry.java` — auto-discovery registry
  - `TargetMarginStrategy.java` — reference implementation
  - `PricingConstraintResolver.java` — constraint pipeline
  - `PricingGuard.java` — guard interface
  - `PricingGuardChain.java` — guard pipeline
