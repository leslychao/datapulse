# Модуль: Analytics & P&L

**Фаза:** B — Trust Analytics
**Зависимости:** [ETL Pipeline](etl-pipeline.md)
**Runtime:** datapulse-ingest-worker (materializers), datapulse-api (reads)

---

## Назначение

Правдивая, сверяемая прибыльность по SKU, категории, кабинету, маркетплейсу и периоду. Star schema в ClickHouse, materializers, P&L формула, inventory intelligence, returns & penalties analysis, data quality controls.

## P&L и unit economics

### Обязательные свойства

- Полная формула P&L: revenue − комиссии − логистика − хранение − штрафы − реклама − COGS − возвраты + компенсации − прочие удержания.
- Reconciliation residual: **отдельная диагностическая мера**, НЕ компонент P&L. Показывает расхождение между выплатой и суммой классифицированных компонентов.
- COGS по SCD2: себестоимость привязана к моменту продажи.
- Drill-down от P&L до отдельных финансовых записей с provenance (cross-store: ClickHouse → PostgreSQL → S3).
- Advertising allocation: пропорциональная аллокация рекламных расходов по revenue share.

## Формула P&L

```
P&L = revenue_amount
    − marketplace_commission
    − acquiring_commission
    − logistics_cost
    − storage_cost
    − penalties
    − acceptance_cost
    − marketing_cost
    − other_marketplace_charges
    − advertising_cost (pro-rata allocation)
    − refund_amount
    + compensation
    − COGS (SCD2)
```

### Phase B scope

**Phase B core:** `advertising_cost` = 0 (fallback до подключения рекламных данных). COGS = 0 при отсутствии `cost_profile` (помечено в UI как «себестоимость не задана»). P&L отражает marketplace P&L без рекламных расходов и без COGS для товаров без cost_profile.

**Phase B extended:** после подключения advertising ingestion `advertising_cost` заполняется из `fact_advertising`. Подключение не блокирует Phase B core — реклама добавляется инкрементально. При переключении с 0 на реальные данные, marts пересчитываются ретроактивно (full re-materialization). UI предупреждение «Рекламные расходы не подключены» снимается per marketplace при наличии данных в `fact_advertising`.

### revenue_amount (DD-11 / DD-13)

Seller-facing price (цена, на основе которой рассчитываются комиссии МП), ДО удержаний маркетплейса. **Не** цена, которую заплатил покупатель (МП может субсидировать скидки). Маппинг provider-полей → canonical revenue_amount описан в [mapping-spec.md](../provider-api-specs/mapping-spec.md) §5, §7 (DD-11, DD-13).

### refund_amount — правила агрегации

`refund_amount` агрегируется ТОЛЬКО из возвратных операций, НЕ из spine. Revenue spine включает только продажные операции. Возвратные операции агрегируются отдельно в `refund_amount`. Это предотвращает двойной учёт.

Правила:
- Canonical entries с `entryType` ∈ {RETURN_REVERSAL, ...} → `refund_amount` (а НЕ `revenue_amount`)
- Все прочие measures (commission, logistics, penalties и т.д.) агрегируются одинаково для sales и returns
- Инвариант: `entryType` определяет classification revenue vs refund

Маппинг конкретных provider-полей в canonical entry types описан в [mapping-spec.md](../provider-api-specs/mapping-spec.md) §5, §7.

### Advertising allocation

Рекламные расходы аллоцируются на уровень product × day для включения в P&L.

**Два режима аллокации** в зависимости от гранулярности данных провайдера:

**Mode 1: Direct attribution (WB)**

WB fullstats v3 возвращает расход per product per day (`campaign × date × nmId → sum`). Прямая привязка: `fact_advertising.spend` → `marketplace_offer` через `nmId` = `marketplace_sku`.

```
advertising_cost_per_product_day = SUM(fact_advertising.spend)
  WHERE marketplace_sku = offer.marketplace_sku
    AND ad_date BETWEEN period_start AND period_end
  GROUP BY seller_sku_id, ad_date
```

**Mode 2: Pro-rata by revenue share (Ozon, fallback)**

Ozon Performance API может не предоставлять product-level daily spend в одном отчёте. В этом случае — пропорциональная аллокация по revenue share:

```
advertising_cost_per_product_day =
  campaign_daily_spend × (product_daily_revenue / campaign_daily_revenue)
```

Где:
- `campaign_daily_spend` = `SUM(fact_advertising.spend)` per campaign per day
- `product_daily_revenue` = `SUM(fact_finance.revenue_amount)` per product per day (из `fact_finance` WHERE `attribution_level = 'POSTING'`)
- `campaign_daily_revenue` = сумма revenue по всем продуктам кампании за день

**Edge cases:**
- День без продаж → `campaign_daily_revenue = 0` → advertising cost аллоцируется равномерно по продуктам кампании
- Продукт без revenue но с ad spend (direct mode) → cost полностью на этот продукт
- Данные доступны только по одному маркетплейсу → advertising_cost заполняется только для этого MP, для другого = 0

**Attribution в marts:**

| Mart | Grain | Advertising cost source |
|------|-------|------------------------|
| `mart_posting_pnl` | posting | **Не включается** — реклама не привязана к posting. Posting-level P&L = marketplace P&L |
| `mart_product_pnl` | product × month | `SUM(advertising_cost_per_product_day)` за месяц. Добавляется как отдельная строка `advertising_cost` |

Advertising cost участвует в P&L только на уровне `mart_product_pnl` и выше. `mart_posting_pnl` остаётся чистым от аллоцированных расходов (аналогично COGS — COGS тоже не в posting-level).

### COGS

```
gross_cogs = fact_sales.quantity × fact_product_cost.cost_price
refund_ratio = SUM(refund_amount) / NULLIF(SUM(revenue_amount), 0)
net_cogs = gross_cogs × GREATEST(0, 1 − COALESCE(refund_ratio, 0))
```

| Компонент | Источник | Описание |
|-----------|----------|----------|
| `gross_cogs` | `fact_sales.quantity × fact_product_cost.cost_price` | COGS до учёта возвратов |
| `refund_ratio` | `SUM(refund_amount) / NULLIF(SUM(revenue_amount), 0)` | Доля выручки, реверсированная возвратами. Computed at applicable grain |
| `net_cogs` | `gross_cogs × GREATEST(0, 1 − COALESCE(refund_ratio, 0))` | P&L-grade COGS |

**`cogs_date`** = `finance_date` строки `entry_type = 'SALE_ACCRUAL'` для данного posting. Для multi-entry postings (sale + brand commission + stars + return) — используется **только** дата SALE_ACCRUAL entry, не return/commission entries.

**Revenue-ratio netting (T-4 invariant):** COGS нетируется пропорционально реверсированной выручке. Если posting полностью возвращён → `refund_ratio = 1` → `net_cogs = 0`. Если нет возвратов → `refund_ratio = 0` → `net_cogs = gross_cogs`. Используется единая формула для обоих маркетплейсов.

**Почему revenue-ratio, а не quantity-based netting:**
- Ozon `v1/returns/list` не содержит `posting_number` → `fact_returns` нельзя join'ить к `mart_posting_pnl` по `posting_id`.
- Ozon finance return entries (`ClientReturnAgentOperation`) содержат `posting_number` и exact revenue reversal → `refund_amount / revenue_amount` per posting даёт точную долю возврата.
- WB: sale и return — отдельные postings (разные `srid`). Revenue-ratio работает на уровне product×month.
- На маркетплейсах refund = original sale price × returned_qty. Поэтому `refund_ratio ≡ return_qty / sale_qty` — математически эквивалентно quantity netting.

**Grain-level behaviour:**

| Grain | Ozon | WB |
|-------|------|-----|
| `mart_posting_pnl` | Exact: sale+return share `posting_id` → per-posting refund_ratio точен | Trivial: return postings have `gross_cogs = 0` (no fact_sales) → net_cogs = 0. Sale postings: refund_ratio = 0 → net_cogs = gross_cogs |
| `mart_product_pnl` | Exact: rollup per product×month | Exact: revenue-ratio across all sale/return postings per product×month |

**SCD2 granularity:** day. Изменения cost_profile вступают в силу для всех продаж с `valid_from` и позже. Intra-day precision не поддерживается.

Для standalone operations COGS неприменим.

Таблицы: `cost_profile` (PostgreSQL, canonical) → `fact_product_cost` (ClickHouse, analytics).

### COGS temporal semantics (T-3 invariant)

`finance_date` представляет дату финансового урегулирования маркетплейсом (≈ подтверждение доставки). Для обоих МП это дата revenue recognition.

| Marketplace | Source field → finance_date | Economic meaning | Lag from order |
|-------------|----------------------------|------------------|----------------|
| Ozon | `operation_date` | Financial settlement (delivery confirmation) | Days to weeks (empirical: up to 11 days) |
| WB | `sale_dt` | Delivery acceptance | Days |

COGS SCD2 intentionally использует дату доставки, не дату создания заказа. `posting.order_date` (Ozon) и `order_dt` (WB) доступны в raw layer, но не используются для COGS. Обоснование: cost_profile — ручной ввод, precision ниже ~1 недели иллюзорна; revenue recognition = delivery — стандартная accounting practice.

### Reconciliation residual

`reconciliation_residual` вычисляется в **`mart_posting_pnl`** (не хранится в fact_finance):

```
reconciliation_residual = SUM(net_payout) − Σ(именованные компоненты P&L)
```

(GROUP BY posting_id — posting-level aggregate)

**Инвариант:** residual НЕ входит в P&L формулу. P&L = Σ(именованных компонентов). Residual — диагностическая мера точности классификации. Reconciliation check: `Σ(компонентов) + residual ≈ net_payout` (до вычета COGS и advertising). Advertising cost не участвует в reconciliation — это внешний расход, не часть marketplace payout.

`residual_ratio = |residual| / |net_payout|` — метрика качества маппинга. Ожидаемые baselines:
- WB: ~3-5% (positive, SPP-компенсация от WB продавцу)
- Ozon: ~0% (все компоненты полностью decompose)

Стабильный residual в пределах baseline — норма. Резкое отклонение от baseline — anomaly, требует investigation (новые типы операций МП, ошибки маппинга).

## Star schema (ClickHouse)

### Analytics layer

| Свойство | Описание |
|----------|----------|
| Назначение | Derived facts, marts, projections |
| Хранилище | ClickHouse |
| Engine | `ReplacingMergeTree` с `ver` (timestamp) для upsert-семантики |
| Формат | Star schema: `dim_*` + `fact_*` + `mart_*` |
| Мутабельность | Upsert facts (ReplacingMergeTree, latest version wins; `SELECT ... FINAL` обязателен); marts пересчитываются из facts |
| Материализация | Materializer читает из canonical layer (PostgreSQL), пишет в ClickHouse |
| Ограничение | Не source of truth для decisions; не хранит action lifecycle, retries, reconciliation |

### Dimensions

| Таблица | Содержание |
|---------|------------|
| `dim_product` | Мастер-запись товара: название, бренд, категория, marketplace IDs |
| `dim_warehouse` | Склады: тип (FBO/FBS/seller), название, location |
| `dim_category` | Иерархия категорий |
| `dim_promo_campaign` | Промо-кампании: даты, тип, название (Phase F/G) |
| `dim_advertising_campaign` | Рекламные кампании: название, тип, статус, placement, бюджет (Phase B extended) |

### Facts

**P&L-critical facts:**

| Таблица | Содержание | P&L роль |
|---------|------------|----------|
| `fact_finance` | Consolidated financial fact | Центральный P&L fact |
| `fact_sales` | Продажи: количество, сумма, привязка к order/product | Quantity для COGS |
| `fact_advertising` | Рекламная статистика per campaign/product/day | Direct + pro-rata allocation (Phase B extended) |
| `fact_product_cost` | Себестоимость (SCD2: `valid_from` / `valid_to`) | Unit cost для COGS |

**Operational / analytical facts:**

| Таблица | Содержание | Назначение |
|---------|------------|------------|
| `fact_orders` | Заказы/отправления | Order funnel, conversion |
| `fact_returns` | Возвраты: количество, сумма, причина | Return rate analysis |
| `fact_price_snapshot` | Исторические снимки цен | Ценовая история |
| `fact_inventory_snapshot` | Снимки остатков | Inventory intelligence |
| `fact_supply` | Поставки (WB incomes) | **Phase G** |

**Deferred:** `fact_promo_product`, `dim_promo_campaign` — Phase F/G.

**Eliminated (sanitation):** ~~`fact_commission`~~, ~~`fact_logistics_costs`~~, ~~`fact_marketing_costs`~~, ~~`fact_penalties`~~ — поглощены `fact_finance`.

**Renamed (DD-AD-3):** ~~`fact_advertising_costs`~~ → `fact_advertising` (содержит не только costs, но и views, clicks, orders, conversions).

### Marts

| Таблица | Содержание | Зависимости | Phase |
|---------|------------|-------------|-------|
| `mart_posting_pnl` | P&L по отправке (posting grain) + `reconciliation_residual` | fact_finance (POSTING: measures + refund_ratio), fact_sales (qty), fact_product_cost (COGS) | A/B |
| `mart_product_pnl` | P&L по продукту за период (cash-basis) + account_level_charges row | mart_posting_pnl + fact_finance (PRODUCT, ACCOUNT) | A/B |
| `mart_inventory_analysis` | Inventory intelligence | fact_inventory_snapshot, fact_sales, fact_product_cost | B |
| `mart_returns_analysis` | Returns & penalties | fact_returns, fact_finance, fact_sales | B |
| `mart_promo_product_analysis` | Эффективность промо | **Phase F/G** |

### ClickHouse column-level schemas

Ниже — полные column-level спецификации для всех ClickHouse-таблиц Phase A/B. `fact_finance` описан отдельно в §fact_finance.

#### dim_product

| Column | Type | Source | Nullable | Notes |
|--------|------|--------|----------|-------|
| `product_id` | UInt64 | marketplace_offer.id | No | PK, grain key |
| `connection_id` | UInt32 | marketplace_offer.marketplace_connection_id | No | FK marketplace_connection |
| `source_platform` | LowCardinality(String) | marketplace_connection.marketplace_type | No | `'ozon'` / `'wb'` |
| `seller_sku_id` | UInt64 | seller_sku.id | No | FK seller_sku |
| `product_master_id` | UInt64 | product_master.id | No | FK product_master (cross-marketplace) |
| `sku_code` | String | seller_sku.sku_code | No | Артикул продавца |
| `marketplace_sku` | String | marketplace_offer.marketplace_sku | No | nmID (WB), product_id (Ozon) |
| `product_name` | String | marketplace_offer.name | No | Название товара |
| `brand` | String | product_master.brand | Yes | Бренд |
| `category` | String | marketplace_offer.category | Yes | Категория |
| `status` | LowCardinality(String) | marketplace_offer.status | No | ACTIVE / INACTIVE |
| `ver` | UInt64 | — | No | Materialization timestamp |

```
ENGINE = ReplacingMergeTree(ver)
PARTITION BY source_platform
ORDER BY (connection_id, product_id)
```

#### dim_warehouse

| Column | Type | Source | Nullable | Notes |
|--------|------|--------|----------|-------|
| `warehouse_id` | UInt32 | warehouse.id | No | PK, grain key |
| `external_warehouse_id` | String | warehouse.external_warehouse_id | No | Provider-specific ID |
| `name` | String | warehouse.name | No | Название склада |
| `warehouse_type` | LowCardinality(String) | warehouse.warehouse_type | No | FBO / FBS / SELLER |
| `marketplace_type` | LowCardinality(String) | warehouse.marketplace_type | No | `'ozon'` / `'wb'` |
| `ver` | UInt64 | — | No | Materialization timestamp |

```
ENGINE = ReplacingMergeTree(ver)
ORDER BY (marketplace_type, warehouse_id)
```

#### dim_category

| Column | Type | Source | Nullable | Notes |
|--------|------|--------|----------|-------|
| `category_id` | UInt64 | category.id | No | PK, grain key |
| `connection_id` | UInt32 | category.marketplace_connection_id | No | FK marketplace_connection |
| `external_category_id` | String | category.external_category_id | No | Provider-specific ID |
| `name` | String | category.name | No | Название категории |
| `parent_category_id` | UInt64 | category.parent_category_id | Yes | FK parent (nullable — root) |
| `marketplace_type` | LowCardinality(String) | — | No | `'ozon'` / `'wb'` |
| `ver` | UInt64 | — | No | Materialization timestamp |

```
ENGINE = ReplacingMergeTree(ver)
ORDER BY (marketplace_type, category_id)
```

#### fact_sales

Grain: одна строка per canonical_sale. Содержит quantity для COGS calculation.

| Column | Type | Source | Nullable | Notes |
|--------|------|--------|----------|-------|
| `sale_id` | UInt64 | canonical_sale.id (PK) | No | Grain key |
| `connection_id` | UInt32 | canonical_sale.connection_id | No | FK marketplace_connection |
| `source_platform` | LowCardinality(String) | canonical_sale.source_platform | No | `'ozon'` / `'wb'` |
| `posting_id` | String | canonical_sale.posting_id | Yes | Join key к fact_finance |
| `order_id` | String | canonical_sale.order_id | Yes | |
| `seller_sku_id` | UInt64 | canonical_sale.seller_sku_id | Yes | FK seller_sku |
| `product_id` | UInt64 | canonical_sale.marketplace_offer_id | Yes | FK dim_product |
| `quantity` | Int32 | canonical_sale.quantity | No | Количество (для COGS) |
| `sale_amount` | Decimal(18,2) | canonical_sale.sale_amount | No | Сумма продажи |
| `sale_date` | Date | canonical_sale.sale_date | No | Дата продажи |
| `ver` | UInt64 | — | No | Materialization timestamp |

```
ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(sale_date)
ORDER BY (connection_id, source_platform, sale_id)
```

#### fact_orders

Grain: одна строка per canonical_order.

| Column | Type | Source | Nullable | Notes |
|--------|------|--------|----------|-------|
| `order_id_pk` | UInt64 | canonical_order.id (PK) | No | Grain key |
| `connection_id` | UInt32 | canonical_order.connection_id | No | FK marketplace_connection |
| `source_platform` | LowCardinality(String) | canonical_order.source_platform | No | `'ozon'` / `'wb'` |
| `external_order_id` | String | canonical_order.external_order_id | No | Provider-specific order ID |
| `seller_sku_id` | UInt64 | canonical_order.seller_sku_id | Yes | FK seller_sku |
| `product_id` | UInt64 | canonical_order.marketplace_offer_id | Yes | FK dim_product |
| `quantity` | Int32 | canonical_order.quantity | No | Количество |
| `price_per_unit` | Decimal(18,2) | canonical_order.price_per_unit | No | Цена за единицу |
| `total_amount` | Decimal(18,2) | canonical_order.total_amount | No | Сумма заказа |
| `order_date` | Date | canonical_order.order_date | No | Дата заказа |
| `status` | LowCardinality(String) | canonical_order.status | No | Статус заказа |
| `ver` | UInt64 | — | No | Materialization timestamp |

```
ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(order_date)
ORDER BY (connection_id, source_platform, order_id_pk)
```

#### fact_returns

Grain: одна строка per canonical_return.

| Column | Type | Source | Nullable | Notes |
|--------|------|--------|----------|-------|
| `return_id` | UInt64 | canonical_return.id (PK) | No | Grain key |
| `connection_id` | UInt32 | canonical_return.connection_id | No | FK marketplace_connection |
| `source_platform` | LowCardinality(String) | canonical_return.source_platform | No | `'ozon'` / `'wb'` |
| `external_return_id` | String | canonical_return.external_return_id | No | Provider-specific ID |
| `seller_sku_id` | UInt64 | canonical_return.seller_sku_id | Yes | FK seller_sku |
| `product_id` | UInt64 | canonical_return.marketplace_offer_id | Yes | FK dim_product |
| `quantity` | Int32 | canonical_return.quantity | No | Количество |
| `return_amount` | Decimal(18,2) | canonical_return.return_amount | Yes | Сумма возврата |
| `return_reason` | LowCardinality(String) | canonical_return.return_reason | Yes | Причина возврата |
| `return_date` | Date | canonical_return.return_date | No | Дата возврата |
| `ver` | UInt64 | — | No | Materialization timestamp |

```
ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(return_date)
ORDER BY (connection_id, source_platform, return_id)
```

#### fact_price_snapshot

Grain: одна строка per price observation (marketplace_offer × captured_at).

| Column | Type | Source | Nullable | Notes |
|--------|------|--------|----------|-------|
| `snapshot_id` | UInt64 | auto-generated | No | Grain key (ClickHouse-local sequence) |
| `connection_id` | UInt32 | canonical_price_current.connection_id | No | FK marketplace_connection |
| `source_platform` | LowCardinality(String) | — | No | `'ozon'` / `'wb'` |
| `product_id` | UInt64 | canonical_price_current.marketplace_offer_id | No | FK dim_product |
| `price` | Decimal(18,2) | canonical_price_current.price | No | Базовая цена |
| `discount_price` | Decimal(18,2) | canonical_price_current.discount_price | Yes | Цена со скидкой |
| `currency` | LowCardinality(String) | canonical_price_current.currency | No | RUB |
| `captured_at` | DateTime | materializer timestamp (sync moment) | No | Время снапшота |
| `captured_date` | Date | — | No | `toDate(captured_at)` для partitioning |
| `ver` | UInt64 | — | No | Materialization timestamp |

```
ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(captured_date)
ORDER BY (connection_id, product_id, captured_at)
```

#### fact_inventory_snapshot

Grain: одна строка per stock observation (marketplace_offer × warehouse × captured_at).

| Column | Type | Source | Nullable | Notes |
|--------|------|--------|----------|-------|
| `snapshot_id` | UInt64 | auto-generated | No | Grain key (ClickHouse-local sequence) |
| `connection_id` | UInt32 | canonical_stock_current.connection_id | No | FK marketplace_connection |
| `source_platform` | LowCardinality(String) | — | No | `'ozon'` / `'wb'` |
| `product_id` | UInt64 | canonical_stock_current.marketplace_offer_id | No | FK dim_product |
| `warehouse_id` | UInt32 | canonical_stock_current.warehouse_id | Yes | FK dim_warehouse |
| `available` | Int32 | canonical_stock_current.available | No | Доступный остаток |
| `reserved` | Int32 | canonical_stock_current.reserved | Yes | Зарезервировано |
| `captured_at` | DateTime | materializer timestamp (sync moment) | No | Время снапшота |
| `captured_date` | Date | — | No | `toDate(captured_at)` для partitioning |
| `ver` | UInt64 | — | No | Materialization timestamp |

```
ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(captured_date)
ORDER BY (connection_id, product_id, warehouse_id, captured_at)
```

#### fact_product_cost

Grain: одна строка per cost_profile period (seller_sku × valid_from). SCD2.

| Column | Type | Source | Nullable | Notes |
|--------|------|--------|----------|-------|
| `cost_id` | UInt64 | cost_profile.id (PK) | No | Grain key |
| `seller_sku_id` | UInt64 | cost_profile.seller_sku_id | No | FK seller_sku |
| `cost_price` | Decimal(18,2) | cost_profile.cost_price | No | Себестоимость за единицу |
| `currency` | LowCardinality(String) | cost_profile.currency | No | RUB |
| `valid_from` | Date | cost_profile.valid_from | No | Начало действия |
| `valid_to` | Date | cost_profile.valid_to | Yes | Конец действия (NULL = текущий) |
| `ver` | UInt64 | — | No | Materialization timestamp |

```
ENGINE = ReplacingMergeTree(ver)
ORDER BY (seller_sku_id, valid_from)
```

#### mart_posting_pnl

Grain: одна строка per posting. Materialized из fact_finance + fact_sales + fact_product_cost.

| Column | Type | Source | Nullable | Notes |
|--------|------|--------|----------|-------|
| `posting_id` | String | fact_finance.posting_id | No | Grain key |
| `connection_id` | UInt32 | fact_finance.connection_id | No | FK marketplace_connection |
| `source_platform` | LowCardinality(String) | fact_finance.source_platform | No | `'ozon'` / `'wb'` |
| `order_id` | String | fact_finance.order_id | Yes | Order-level grouping |
| `seller_sku_id` | UInt64 | fact_finance.seller_sku_id | Yes | FK seller_sku |
| `product_id` | UInt64 | dim_product join | Yes | FK dim_product |
| `finance_date` | Date | MIN(fact_finance.finance_date) WHERE entry_type='SALE_ACCRUAL' | No | Дата revenue recognition |
| `revenue_amount` | Decimal(18,2) | SUM(fact_finance.revenue_amount) | No | Выручка |
| `marketplace_commission_amount` | Decimal(18,2) | SUM | No | Комиссия МП |
| `acquiring_commission_amount` | Decimal(18,2) | SUM (включая pro-rata allocation) | No | Эквайринг |
| `logistics_cost_amount` | Decimal(18,2) | SUM | No | Логистика |
| `storage_cost_amount` | Decimal(18,2) | SUM | No | Хранение |
| `penalties_amount` | Decimal(18,2) | SUM | No | Штрафы |
| `marketing_cost_amount` | Decimal(18,2) | SUM | No | Маркетинг |
| `acceptance_cost_amount` | Decimal(18,2) | SUM | No | Приёмка |
| `other_marketplace_charges_amount` | Decimal(18,2) | SUM | No | Прочие |
| `compensation_amount` | Decimal(18,2) | SUM | No | Компенсации |
| `refund_amount` | Decimal(18,2) | SUM | No | Реверс выручки |
| `net_payout` | Decimal(18,2) | SUM(fact_finance.net_payout) | No | Чистая выплата |
| `quantity` | Int32 | fact_sales.quantity (LEFT JOIN) | Yes | Количество для COGS |
| `gross_cogs` | Decimal(18,2) | quantity × cost_price | Yes | COGS до нетирования |
| `refund_ratio` | Decimal(18,4) | refund_amount / NULLIF(revenue_amount, 0) | Yes | Доля возврата |
| `cogs_amount` | Decimal(18,2) | gross_cogs × (1 − refund_ratio) | Yes | P&L-grade COGS |
| `cogs_status` | LowCardinality(String) | computed | No | OK / NO_COST_PROFILE / NO_SALES |
| `reconciliation_residual` | Decimal(18,2) | net_payout − Σ(named components) | No | Диагностический residual |
| `ver` | UInt64 | — | No | Materialization timestamp |

```
ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(finance_date)
ORDER BY (connection_id, source_platform, posting_id)
```

#### mart_product_pnl

Grain: product × month. Содержит rollup из mart_posting_pnl + PRODUCT-level + ACCOUNT-level entries.

| Column | Type | Source | Nullable | Notes |
|--------|------|--------|----------|-------|
| `connection_id` | UInt32 | — | No | FK marketplace_connection |
| `source_platform` | LowCardinality(String) | — | No | `'ozon'` / `'wb'` |
| `seller_sku_id` | UInt64 | — | No | FK seller_sku; 0 = account row |
| `product_id` | UInt64 | — | No | FK dim_product; 0 = account row |
| `period` | UInt32 | toYYYYMM(finance_date) | No | YYYYMM cash-basis period |
| `attribution_level` | LowCardinality(String) | — | No | `PRODUCT` / `ACCOUNT` |
| `revenue_amount` | Decimal(18,2) | SUM | No | |
| `marketplace_commission_amount` | Decimal(18,2) | SUM | No | |
| `acquiring_commission_amount` | Decimal(18,2) | SUM | No | |
| `logistics_cost_amount` | Decimal(18,2) | SUM | No | |
| `storage_cost_amount` | Decimal(18,2) | SUM | No | |
| `penalties_amount` | Decimal(18,2) | SUM | No | |
| `marketing_cost_amount` | Decimal(18,2) | SUM | No | |
| `acceptance_cost_amount` | Decimal(18,2) | SUM | No | |
| `other_marketplace_charges_amount` | Decimal(18,2) | SUM | No | |
| `compensation_amount` | Decimal(18,2) | SUM | No | |
| `refund_amount` | Decimal(18,2) | SUM | No | |
| `net_payout` | Decimal(18,2) | SUM | No | |
| `gross_cogs` | Decimal(18,2) | SUM | Yes | |
| `product_refund_ratio` | Decimal(18,4) | product-month level | Yes | |
| `net_cogs` | Decimal(18,2) | gross_cogs × (1 − product_refund_ratio) | Yes | |
| `cogs_status` | LowCardinality(String) | worst of posting statuses | No | |
| `ver` | UInt64 | — | No | Materialization timestamp |

```
ENGINE = ReplacingMergeTree(ver)
PARTITION BY period
ORDER BY (connection_id, source_platform, seller_sku_id, period)
```

#### mart_inventory_analysis

Grain: product × warehouse × snapshot date. Phase B.

| Column | Type | Source | Nullable | Notes |
|--------|------|--------|----------|-------|
| `connection_id` | UInt32 | — | No | FK marketplace_connection |
| `source_platform` | LowCardinality(String) | — | No | |
| `product_id` | UInt64 | — | No | FK dim_product |
| `seller_sku_id` | UInt64 | — | No | FK seller_sku |
| `warehouse_id` | UInt32 | — | Yes | FK dim_warehouse |
| `analysis_date` | Date | — | No | Дата анализа |
| `available` | Int32 | fact_inventory_snapshot | No | Текущий остаток |
| `reserved` | Int32 | fact_inventory_snapshot | Yes | Зарезервировано |
| `avg_daily_sales_14d` | Decimal(18,2) | fact_sales (14-day avg) | Yes | Скользящее среднее |
| `days_of_cover` | Decimal(18,1) | available / avg_daily_sales_14d | Yes | Дней до stock-out |
| `stock_out_risk` | LowCardinality(String) | threshold-based | No | CRITICAL / WARNING / NORMAL |
| `cost_price` | Decimal(18,2) | fact_product_cost (SCD2 current) | Yes | Себестоимость |
| `frozen_capital` | Decimal(18,2) | excess_qty × cost_price | Yes | Замороженный капитал |
| `recommended_replenishment` | Int32 | computed | Yes | Рекомендация по пополнению |
| `ver` | UInt64 | — | No | Materialization timestamp |

```
ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(analysis_date)
ORDER BY (connection_id, product_id, warehouse_id, analysis_date)
```

#### mart_returns_analysis

Grain: product × month. Phase B.

| Column | Type | Source | Nullable | Notes |
|--------|------|--------|----------|-------|
| `connection_id` | UInt32 | — | No | FK marketplace_connection |
| `source_platform` | LowCardinality(String) | — | No | |
| `product_id` | UInt64 | — | No | FK dim_product |
| `seller_sku_id` | UInt64 | — | No | FK seller_sku |
| `period` | UInt32 | toYYYYMM(return_date) | No | YYYYMM |
| `return_count` | UInt32 | COUNT(fact_returns) | No | Количество возвратов |
| `return_quantity` | Int32 | SUM(fact_returns.quantity) | No | Единиц возвращено |
| `return_amount` | Decimal(18,2) | SUM(fact_returns.return_amount) | No | Сумма возвратов |
| `sale_count` | UInt32 | COUNT(fact_sales) | No | Продаж за период |
| `sale_quantity` | Int32 | SUM(fact_sales.quantity) | No | Единиц продано |
| `return_rate_pct` | Decimal(18,2) | return_quantity / NULLIF(sale_quantity, 0) × 100 | Yes | % возвратов |
| `financial_refund_amount` | Decimal(18,2) | SUM(fact_finance.refund_amount) | No | Финансовый impact |
| `penalties_amount` | Decimal(18,2) | SUM(fact_finance.penalties_amount) | No | Штрафы за период |
| `top_return_reason` | String | MODE(fact_returns.return_reason) | Yes | Самая частая причина |
| `ver` | UInt64 | — | No | Materialization timestamp |

```
ENGINE = ReplacingMergeTree(ver)
PARTITION BY period
ORDER BY (connection_id, source_platform, seller_sku_id, period)
```

### dim_advertising_campaign

Рекламная кампания маркетплейса. Материализуется из WB Advert API (`/api/advert/v2/adverts`) и Ozon Performance API (`/api/client/campaign`).

**Columns:**

| Column | Type | Source | Notes |
|--------|------|--------|-------|
| `connection_id` | UInt32 | marketplace_connection.id | FK |
| `source_platform` | LowCardinality(String) | `'wb'` / `'ozon'` | |
| `campaign_id` | UInt64 | WB: `advertId`; Ozon: campaign `id` | Stable key |
| `name` | String | Campaign name | |
| `campaign_type` | LowCardinality(String) | WB: `type` (9 = unified); Ozon: `advObjectType` | |
| `status` | LowCardinality(String) | WB: `status`; Ozon: `state` | |
| `placement` | Nullable(String) | WB: `"search"` / `"recommendations"` / `"combined"` | WB-specific |
| `daily_budget` | Nullable(Decimal(18,2)) | WB: `dailyBudget`; Ozon: `dailyBudget` | |
| `start_time` | Nullable(DateTime) | Campaign start | |
| `end_time` | Nullable(DateTime) | Campaign end | |
| `created_at` | Nullable(DateTime) | Campaign creation time | |
| `ver` | UInt64 | Materialization timestamp | ReplacingMergeTree |

```sql
ENGINE = ReplacingMergeTree(ver)
PARTITION BY source_platform
ORDER BY (connection_id, source_platform, campaign_id)
```

### fact_advertising

Рекламная статистика per campaign / product / day. Материализуется из WB fullstats v3 и Ozon Performance reports. Pipeline invariant exception: Raw → ClickHouse (без canonical PostgreSQL entity). See DD-AD-1.

**Grain:** `connection_id × campaign_id × ad_date × marketplace_sku` (product-level daily). Для Ozon (если product-level недоступен): `connection_id × campaign_id × ad_date` (campaign-level daily) с `marketplace_sku = NULL`.

**Columns:**

| Column | Type | Source | Notes |
|--------|------|--------|-------|
| `connection_id` | UInt32 | marketplace_connection.id | FK |
| `source_platform` | LowCardinality(String) | `'wb'` / `'ozon'` | |
| `campaign_id` | UInt64 | WB: `advertId`; Ozon: campaign `id` | FK dim_advertising_campaign |
| `ad_date` | Date | WB: `days[].date`; Ozon: report `date` | |
| `marketplace_sku` | Nullable(String) | WB: `nmId` (as string); Ozon: sku (nullable) | Join to marketplace_offer |
| `views` | UInt64 | Impressions | |
| `clicks` | UInt64 | Clicks | |
| `spend` | Decimal(18,2) | Ad spend (RUB). WB: `sum`; Ozon: `moneySpent` | P&L `advertising_cost` source |
| `orders` | UInt32 | Ad-attributed orders | |
| `ordered_units` | UInt32 | Ad-attributed units. WB: `shks` | |
| `ordered_revenue` | Decimal(18,2) | Revenue from ad orders. WB: `sum_price` | |
| `canceled` | UInt32 | Canceled orders. WB: v3 new field | |
| `ctr` | Float32 | Click-through rate (%) | Derived, stored for convenience |
| `cpc` | Decimal(18,2) | Cost per click | Derived, stored for convenience |
| `cr` | Float32 | Conversion rate (%) | Derived, stored for convenience |
| `job_execution_id` | UInt64 | FK to job_execution (provenance) | Data provenance |
| `ver` | UInt64 | Materialization timestamp | ReplacingMergeTree |
| `materialized_at` | DateTime | Materialization time | |

```sql
ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(ad_date)
ORDER BY (connection_id, source_platform, campaign_id, ad_date, marketplace_sku)
```

**Dedup key:** `(connection_id, source_platform, campaign_id, ad_date, marketplace_sku)`. ReplacingMergeTree с `ver` — upsert при re-materialization.

**Data provenance:** `job_execution_id` → `job_item.s3_key` → S3 raw payload. Обеспечивает drill-down без canonical PostgreSQL entity (DD-AD-1).

**Retention:** 12 месяцев (аналогично raw finance). Рекламные данные нужны для P&L ретроактивного пересчёта.

### Attribution taxonomy

Каждая строка `fact_finance` классифицируется по `attribution_level`:

| `attribution_level` | Правило | Mart destination |
|----------------------|---------|------------------|
| `POSTING` | `posting_id IS NOT NULL` OR `order_id IS NOT NULL` | `mart_posting_pnl` (order-linked ops, включая acquiring) |
| `PRODUCT` | `posting_id IS NULL AND order_id IS NULL AND seller_sku_id IS NOT NULL` | `mart_product_pnl` (product row) |
| `ACCOUNT` | `posting_id IS NULL AND order_id IS NULL AND seller_sku_id IS NULL` | `mart_product_pnl` (account_level_charges row) |

`attribution_level` вычисляется materializer-ом при INSERT в fact_finance. Это **exhaustive classification** — каждая строка попадает ровно в одну категорию.

| Mart | Grain | Period key | Source |
|------|-------|------------|--------|
| `mart_posting_pnl` | posting | (no period dimension — lifetime P&L per posting) | fact_finance WHERE attribution_level = 'POSTING' |
| `mart_product_pnl` (product rows) | product × month | `toYYYYMM(finance_date)` per entry (cash-basis) | Rollup mart_posting_pnl + fact_finance WHERE attribution_level = 'PRODUCT' |
| `mart_product_pnl` (account row) | account × month | `toYYYYMM(finance_date)` per entry (cash-basis) | fact_finance WHERE attribution_level = 'ACCOUNT' |

**Period semantics (T-5 invariant):** `mart_product_pnl` использует cash-basis accounting. Period = `toYYYYMM(finance_date)` каждой строки `fact_finance`. Late returns и compensations попадают в период своей `finance_date`, не в период исходной продажи. Cross-period posting splits — ожидаемое поведение (sale в декабре, return в январе). Cash-basis выбран как matching marketplace reporting periods (WB realization report, Ozon finance transactions).

**COGS в mart_product_pnl:**

```sql
product_month_gross_cogs = Σ(mart_posting_pnl.gross_cogs)        -- по cogs_date period
product_month_refund_ratio = Σ(refund_amount) / NULLIF(Σ(revenue_amount), 0)  -- per product×month
product_month_net_cogs = product_month_gross_cogs × GREATEST(0, 1 − COALESCE(product_month_refund_ratio, 0))
```

Posting-level `cogs_amount` уже содержит per-posting netting (exact для Ozon). Product-month level добавляет cross-posting netting (необходимо для WB, где sale и return — разные postings). `gross_cogs` rollup'ится по периоду SALE_ACCRUAL entry's `finance_date`.

### Attribution completeness invariant

```
account_P&L = Σ(product_P&L) + account_level_charges
```

Инвариант доказуемо true, потому что `attribution_level` — exhaustive enum. Каждая строка fact_finance учтена ровно в одном bucket. Reconciliation check:

```
SUM(fact_finance all) = SUM(mart_posting_pnl) + SUM(fact_finance PRODUCT) + SUM(fact_finance ACCOUNT)
```

Divergence > 0 → alert, investigation.

### posting_id granularity per platform

`posting_id` в canonical layer — marketplace-agnostic идентификатор. Однако его **гранулярность** различается:

- **Ozon**: posting = shipment (может содержать несколько SKU)
- **WB**: posting = одна строка финансового отчёта (один SKU)

Cross-platform comparison на posting level — ложная метрика. Корректное сравнение — на уровне `mart_product_pnl` (product × period).

### Standalone ops с SKU-attribution (verified 2026-03-31)

Standalone ops = 43% от net дохода по заказам (Ozon, Jan 2025). Главный cost driver — упаковка/маркировка = 94% standalone debits, привязан к SKU через canonical `seller_sku_id`. Эти операции получают `attribution_level = PRODUCT` и входят в product P&L, не в account_level_charges.

## fact_finance — consolidated financial fact

Центральная финансовая таблица. Материализуется **напрямую** из canonical_finance_entry (без промежуточных component facts, без spine pattern).

### Grain

Одна строка per `canonical_finance_entry`. Строка классифицирована по `entry_type` и может содержать **несколько non-zero measures** из исходной операции (composite row model). Posting-level P&L строится в `mart_posting_pnl` через `GROUP BY posting_id`.

### Columns

**Dimension keys:**

| Column | Type | Source | Nullable | Notes |
|--------|------|--------|----------|-------|
| `connection_id` | UInt32 | canonical_finance_entry.connection_id | No | FK marketplace_connection |
| `source_platform` | LowCardinality(String) | canonical_finance_entry.source_platform | No | `'ozon'` / `'wb'` |
| `entry_id` | UInt64 | canonical_finance_entry.id (PK) | No | Grain key, FK к canonical |
| `posting_id` | String | canonical_finance_entry.posting_id | Yes | NULL для standalone ops |
| `order_id` | String | canonical_finance_entry.order_id | Yes | NULL для standalone ops |
| `seller_sku_id` | UInt64 | canonical_finance_entry.seller_sku_id | Yes | NULL для standalone ops без SKU |
| `warehouse_id` | UInt32 | canonical_finance_entry.warehouse_id | Yes | WB: из `ppvz_office_id`; Ozon и non-warehouse ops: NULL |
| `finance_date` | Date | canonical_finance_entry.entryDate | No | Дата финансовой операции |
| `entry_type` | LowCardinality(String) | canonical_finance_entry.entryType | No | Тип финансовой операции |
| `attribution_level` | LowCardinality(String) | computed by materializer | No | `POSTING` / `PRODUCT` / `ACCOUNT` |

**Measures:**

| Measure | Описание | P&L sign |
|---------|----------|----------|
| `revenue_amount` | Выручка продавца (seller-facing price) | + |
| `marketplace_commission_amount` | Комиссия МП | − |
| `acquiring_commission_amount` | Эквайринг | − |
| `logistics_cost_amount` | Логистика | − |
| `storage_cost_amount` | Хранение | − |
| `penalties_amount` | Штрафы | − |
| `marketing_cost_amount` | Маркетинг (не ads) | − |
| `acceptance_cost_amount` | Приёмка | − |
| `other_marketplace_charges_amount` | Прочие удержания | − |
| `compensation_amount` | Компенсации | + |
| `refund_amount` | Реверс выручки | − |
| `net_payout` | Чистая выплата (per-entry contribution) | (reference) |

Composite row: строка может содержать несколько non-zero measures (например, операция продажи включает revenue + commission + logistics). Маппинг canonical entry fields → fact_finance measures описан в [mapping-spec.md](../provider-api-specs/mapping-spec.md) §7.

`reconciliation_residual` вычисляется в `mart_posting_pnl`, не хранится в fact_finance (см. §Marts).

**Metadata:**

| Column | Type | Notes |
|--------|------|-------|
| `ver` | UInt64 | Materialization timestamp для ReplacingMergeTree |
| `materialized_at` | DateTime | Время материализации |

### Sign convention

Все cost measures в fact_finance хранятся как **positive absolute values**. P&L формула применяет знаки (см. таблицу measures выше). Materializer: `abs(canonical_finance_entry.amount)` для cost measures. В canonical layer обе платформы хранят debit как отрицательный amount (нормализация знаков — ответственность ETL, описана в [etl-pipeline.md](etl-pipeline.md) §Sign conventions).

### Материализация

Sorting key для `ReplacingMergeTree`: `(connection_id, source_platform, entry_id)`. `entry_id` — PK canonical_finance_entry (bigint, гарантированно уникален). `finance_date` **не входит** в sorting key — при изменении даты entry ReplacingMergeTree корректно заменяет строку по `entry_id`, без ghost rows.

```
canonical_finance_entry (PostgreSQL)
→ Materializer: 1:1 map (entry → composite row, populate all applicable measures)
→ Materializer: compute attribution_level (POSTING / PRODUCT / ACCOUNT)
→ INSERT INTO fact_finance (ReplacingMergeTree)
→ SELECT ... FINAL при чтении
```

Posting-level aggregation выполняется в `mart_posting_pnl` через `GROUP BY posting_id`. Materializer не группирует — каждая canonical entry = одна строка fact_finance.

### Partitioning strategy

```sql
PARTITION BY toYYYYMM(finance_date)
ORDER BY (connection_id, source_platform, entry_id)
```

Monthly partitioning по `finance_date` — оптимально для P&L queries (фильтрация по period). `connection_id` не в PARTITION BY (малое количество connections в MVP). ReplacingMergeTree merge работает within partition.

### ClickHouse DDL

```sql
ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(finance_date)
ORDER BY (connection_id, source_platform, entry_id)
```

### entry_type taxonomy

Полный перечень canonical entry types, поддерживаемых normalizer-ом. Маппинг provider-specific полей → entry_type описан в [mapping-spec.md](../provider-api-specs/mapping-spec.md) §5, §7.

| entry_type | P&L measure | Описание | Attribution |
|------------|-------------|----------|-------------|
| `SALE_ACCRUAL` | `revenue_amount` | Выручка от продажи | POSTING |
| `RETURN_REVERSAL` | `refund_amount` | Реверс выручки при возврате | POSTING |
| `MARKETPLACE_COMMISSION` | `marketplace_commission_amount` | Комиссия маркетплейса | POSTING |
| `BRAND_COMMISSION` | `marketplace_commission_amount` | Комиссия за бренд (Ozon) | POSTING |
| `STARS_COMMISSION` | `marketplace_commission_amount` | Комиссия за рейтинг (Ozon) | POSTING |
| `ACQUIRING` | `acquiring_commission_amount` | Эквайринг | POSTING (order-level, allocated pro-rata) |
| `DELIVERY` | `logistics_cost_amount` | Логистика доставки | POSTING |
| `REVERSE_LOGISTICS` | `logistics_cost_amount` | Обратная логистика (возврат) | POSTING |
| `LAST_MILE` | `logistics_cost_amount` | Логистика последней мили | POSTING |
| `STORAGE` | `storage_cost_amount` | Хранение на складе | PRODUCT or ACCOUNT |
| `PENALTY` | `penalties_amount` | Штрафы | POSTING or ACCOUNT |
| `ACCEPTANCE` | `acceptance_cost_amount` | Приёмка товара | POSTING |
| `PACKAGING` | `other_marketplace_charges_amount` | Упаковка/маркировка | PRODUCT |
| `LABELING` | `other_marketplace_charges_amount` | Маркировка | PRODUCT |
| `DISPOSAL` | `other_marketplace_charges_amount` | Утилизация | ACCOUNT |
| `MARKETING` | `marketing_cost_amount` | Маркетинговые услуги МП (не реклама) | ACCOUNT |
| `COMPENSATION` | `compensation_amount` | Компенсации от МП | POSTING or ACCOUNT |
| `OTHER` | `other_marketplace_charges_amount` | Нераспознанные операции | ACCOUNT |

**Exhaustive mapping:** normalizer обязан классифицировать каждую provider-операцию. Нераспознанные типы → `OTHER` + `log.warn`. Периодический review `OTHER` entries для расширения taxonomy.

### Acquiring multi-posting allocation

Acquiring operations хранятся в fact_finance с `posting_id = NULL`, `order_id IS NOT NULL`. Один order может содержать несколько postings. В `mart_posting_pnl` acquiring allocируется **pro-rata по revenue_amount** каждого posting в order:

```
posting_acquiring = order_acquiring × (posting_revenue / order_revenue)
```

Canonical resolution правила (posting_id, order_id, seller_sku_id) описаны в [etl-pipeline.md](etl-pipeline.md) §Canonical finance resolution rules.

### Drill-down path

P&L → отдельные финансовые записи → raw source:

1. **ClickHouse:** `fact_finance` → `entry_id`, `posting_id`
2. **PostgreSQL:** `canonical_finance_entry WHERE id IN (entry_ids)` → `entryType`, `amount`, `job_execution_id`
3. **PostgreSQL:** `job_item WHERE job_execution_id = X` → `s3_key`
4. **S3:** raw payload по `s3_key`

`datapulse-api` реализует этот path через REST endpoints. `fact_finance` хранит `entry_id` (canonical PK) и `posting_id` для корреляции.

## Аналитика остатков (Inventory Intelligence)

### Capabilities

| Capability | Вход | Выход |
|------------|------|-------|
| Days of cover | Доступные остатки, продажи за N дней | Число дней до stock-out |
| Stock-out risk | Days of cover, lead time | Уровень: critical / warning / normal |
| Overstock / frozen capital | Days of cover, себестоимость | Сумма замороженного капитала |
| Replenishment signal | Stock-out risk, velocity | Рекомендуемый объём пополнения |

Все расчёты выполняются отдельно по типам складов (FBO/FBS/seller). Velocity — скользящее среднее продаж (default: 14 дней).

### Алгоритмы

- Days of cover: `available / avg_daily_sales(N)`.
- Stock-out risk: threshold-based — critical если `days_of_cover < lead_time`, warning если `< 2× lead_time`.
- Frozen capital: `excess_qty × cost_price`, где `excess_qty = available − (avg_daily_sales × target_days_of_cover)`.
- Replenishment: `recommended_qty = avg_daily_sales(N) × target_days_of_cover − available`.

## Возвраты и штрафы

### Capabilities

- Агрегация потерь по: категории возврата, SKU, периоду, маркетплейсу.
- Drill-down до evidence (конкретные записи, ссылки на raw source).
- Trend analysis: динамика return rate.
- Breakdown штрафов: по типам.

### fact_returns vs refund_amount

| Артефакт | Что фиксирует | Зачем нужен |
|----------|---------------|-------------|
| `fact_returns` | Операционный факт: что вернули, почему, когда | Return rate analysis, причины, тренды |
| `refund_amount` в `fact_finance` | Финансовый impact | P&L calculation, reconciliation |

## Контроль качества данных

### Controls

| Control | Что проверяется | Threshold (default) | Per-provider | Реакция |
|---------|----------------|--------------------|--------------| --------|
| Stale data | `marketplace_sync_state.last_success_at` | Finance: > 24h, State (catalog/stocks): > 48h | Одинаковый | Alert + block automation |
| Missing sync | Ожидаемый sync пропущен по расписанию | 1 пропуск | Одинаковый | Alert |
| Residual anomaly | `residual_ratio` отклонение от baseline | delta > 2× baseline std | WB baseline ~3-5%, Ozon baseline ~0% | Alert + investigation |
| Spike detection | Period-over-period change по мере | > 3× median за 30 дней | Per measure | Alert |
| Mismatch detection | Расхождения между связанными data domains | Configurable per check | — | Alert |

### Automation blocker

Pricing pipeline (Phase C) **блокируется** для account/marketplace если:
1. **Stale data:** last successful finance sync > 24h, ИЛИ
2. **Residual anomaly:** текущий `residual_ratio` отклоняется от 30-day rolling baseline более чем на 2 стандартных отклонения.

Блокировка = per-account, per-marketplace. Не global. Ozon stale → Ozon pricing blocked, WB unaffected.

**Реализация:** при срабатывании controls создаётся `alert_event` с `blocks_automation = true`. Pricing pipeline проверяет наличие blocking alerts через query к `alert_event` (см. [Audit & Alerting](audit-alerting.md) §Automation blocker integration). Thresholds и calibration определяются здесь (Analytics); evaluation, event lifecycle и automation blocker query — в [Audit & Alerting](audit-alerting.md).

### Operational model

Все thresholds — configurable через `@ConfigurationProperties`. Initial calibration period: первые 30 дней — collecting baselines, alerts в log-only mode (без block).

**Residual calibration (empirically verified 2026-03-31):**

- Minimum sample size: alert активируется только при N > 100 операций за rolling period. Для малых аккаунтов (<100 ops/month) — высокая дисперсия residual, ложные anomalies.
- Provider-default baselines (fallback при недостаточных данных): Ozon ≈ 0%, WB ≈ 4%.
- Tenant-specific baselines — Phase G (требуется достаточная история данных).

## Design decisions

### K-1: revenue_gross → revenue_amount — RESOLVED

Переименовано. Семантика: seller-facing price (до комиссий МП, после скидок). Provider-specific маппинг: mapping-spec.md DD-11, DD-13.

### K-2: P&L формула — RESOLVED

Добавлены `storage_cost_amount`, `acceptance_cost_amount`. Формула до 13 компонентов.

### K-3: Двойное вычитание возвратов — RESOLVED

Revenue spine ТОЛЬКО sales-операции. `refund_amount` агрегируется ОТДЕЛЬНО. Возвратные canonical entries содержат reversals (negative revenue, positive commission refund, new return logistics cost). В fact_finance composite row модели: все measures хранятся с absolute values и корректными знаками. `mart_posting_pnl` GROUP BY posting_id автоматически «гасит» reversals при суммировании.

Детальные return-related rules per provider — [mapping-spec.md](../provider-api-specs/mapping-spec.md) §7 (empirically verified 2026-03-31).

### K-4: seller_discount_amount — RESOLVED (удалён)

Скидки уже учтены в `revenue_amount`. Ни один API не предоставляет отдельное поле.

### G-1: Ozon services classification — RESOLVED

12 service names верифицированы. Маппинг в mapping-spec.md §7.

### G-4: cost_profile vs fact_product_cost — RESOLVED

`cost_profile` (PostgreSQL) → `fact_product_cost` (ClickHouse). `custom_expense_entry` удалён.

### G-5: Acquiring join — RESOLVED (DD-15)

Acquiring операции: `posting_id = NULL`, `order_id IS NOT NULL`. Canonical resolution в [etl-pipeline.md](etl-pipeline.md). mart_posting_pnl allocation — pro-rata по revenue.

### G-6: WB SPP — RESOLVED (DD-14)

SPP — скидка WB для покупателя, оплачиваемая WB. Не компонент P&L продавца.

### N-1: Sorting key и grain — RESOLVED

Grain = canonical_finance_entry (одна composite row per entry). Sorting key: `(connection_id, source_platform, entry_id)`. `entry_id` = PK canonical_finance_entry (bigint, globally unique). `finance_date` исключён из sorting key для предотвращения ghost rows при re-materialization. Posting-level P&L строится в `mart_posting_pnl` через `GROUP BY posting_id`.

### N-2: mart_order_pnl spine — RESOLVED

`mart_posting_pnl` строится из `fact_finance` как единственного source of truth для P&L.

**Dependencies (уточнение):** mart_posting_pnl зависит от:
- `fact_finance` WHERE `attribution_level = 'POSTING'` — P&L measures (source of truth) + `revenue_amount` и `refund_amount` для refund_ratio
- `fact_finance` WHERE `attribution_level = 'POSTING' AND order_id IS NOT NULL AND posting_id IS NULL` — acquiring ops, allocated pro-rata по revenue
- `fact_sales` — gross quantity для COGS (LEFT JOIN по posting_id)
- `fact_product_cost` — unit cost для COGS (LEFT JOIN по seller_sku_id + cogs_date ∈ [valid_from, valid_to))

**`fact_returns` НЕ является зависимостью для COGS.** Ozon `v1/returns/list` не содержит `posting_number` → join невозможен. Вместо quantity-based netting используется revenue-ratio (см. §COGS).

**cogs_date resolution (T-2 invariant):** `cogs_date` = `MIN(finance_date)` WHERE `entry_type = 'SALE_ACCRUAL'` AND `posting_id = X`. Для multi-entry postings (sale + brand commission + stars + return) — используется только дата SALE_ACCRUAL entry.

**Computed measures в mart_posting_pnl:**

```sql
gross_cogs      = fact_sales.quantity × fact_product_cost.cost_price
refund_ratio    = SUM(refund_amount) / NULLIF(SUM(revenue_amount), 0)  -- per posting
cogs_amount     = gross_cogs × GREATEST(0, 1 − COALESCE(refund_ratio, 0))
reconciliation_residual = SUM(net_payout) − Σ(named P&L components)   -- per posting
```

- `cogs_status` = `'OK'` / `'NO_COST_PROFILE'` / `'NO_SALES'`

**Missing data handling:**
- fact_finance без fact_sales → COGS = 0, `cogs_status = 'NO_SALES'`
- fact_sales без fact_finance → строка НЕ создаётся в mart (finance = source of truth)
- fact_product_cost отсутствует → COGS = 0, `cogs_status = 'NO_COST_PROFILE'`
- `revenue_amount = 0` для posting → `refund_ratio = NULL` → COALESCE(NULL, 0) = 0 → `cogs_amount = gross_cogs` (WB return postings: gross_cogs = 0, так что safe)

## Санация P&L-модели (rationale)

### Выявленные и исправленные наросты

1. **Избыточная фрагментация:** fact_commission, fact_logistics_costs и др. — дублировали fact_finance. Удалены.
2. **Spine pattern:** лишняя индирекция. Прямая материализация из canonical_finance_entry.
3. **Naming «order»:** grain = posting, не заказ. Переименовано в `mart_posting_pnl`.
4. **fact_supply:** WB-specific, нет потребителей. Отложено на Phase G.
5. **fact_orders в P&L:** убран из зависимостей P&L-витрин.

### Вердикт

Ядро модели концептуально здорово. Основные искажения — accumulation, не corruption. Очистка выполнена без redesign. Модель масштабируема: новый маркетплейс = новый adapter, без изменения star schema.

### N-3: COGS temporal correctness review — RESOLVED

Architectural review COGS temporal correctness (2026-03-31). Findings and resolutions:

| ID | Finding | Priority | Resolution |
|----|---------|----------|------------|
| T-1 | WB `entryDate` source ambiguity (`rr_dt` vs `sale_dt`) | P0 | Fixed: `entryDate = sale_dt` only. `rr_dt` = report metadata. Updated in mapping-spec §7 |
| T-2 | COGS SCD2 join key under-specified for multi-entry postings | P0 | Fixed: `cogs_date` = SALE_ACCRUAL entry's `finance_date`. Updated in N-2 dependencies |
| T-3 | Cross-marketplace temporal divergence (Ozon operation_date vs WB sale_dt) | P1 | Accepted + documented: both ≈ delivery date, correct for revenue recognition. See COGS temporal semantics |
| T-4 | Gross COGS (no reversal for returns) | P1 | Fixed: revenue-ratio netting `net_cogs = gross_cogs × (1 − refund_ratio)`. Quantity-based netting невозможен: Ozon fact_returns не содержит posting_id |
| T-5 | `mart_product_pnl` period key undefined | P1 | Fixed: cash-basis `toYYYYMM(finance_date)` per entry. Updated attribution table |
| T-6 | Day-granularity SCD2 edge case | P2 | Accepted: standard practice for manual cost entry |
| T-7 | Return COGS temporal consistency across periods | P2 | Solved by revenue-ratio at both posting and product-month levels. Ozon: exact per-posting. WB: cross-posting netting at product-month level |
| T-8 | WB retroactive report corrections | P2 | Accepted: bounded by re-materialization cycle (daily) |

**Verdict:** model sufficient with the above fixes. No redesign needed.

### N-4: Retroactive corrections handling

WB realization reports can be retroactively corrected. Corrected rows are upserted in canonical (dedup by `rrd_id`) and replaced in fact_finance (ReplacingMergeTree by `entry_id`). Marts may be stale until next re-materialization cycle. Phase B: daily full re-materialization bounds staleness to 24h. Selective re-materialization — deferred to Phase G.

## Materialization scheduling

### Incremental (per-sync)

После каждого ETL sync → materializer обрабатывает записи текущего `job_execution_id`:
- canonical → fact tables (INSERT/ReplacingMergeTree)
- fact → mart (re-aggregate affected postings/products)

Latency: минуты после sync completion. Scope: только изменённые записи.

### Full re-materialization (daily)

Scheduled job (cron, configurable, default: 02:00 UTC daily):
1. fact tables: `TRUNCATE` + full re-insert из canonical (PostgreSQL → ClickHouse)
2. marts: пересчёт из facts

Зачем:
- Ретроактивные корректировки провайдеров (WB report corrections)
- Advertising allocation recalc (новые ad spend данные per past dates)
- SCD2 cost_profile changes ретроактивно корректируют COGS
- ReplacingMergeTree гарантирует consistency после `OPTIMIZE TABLE ... FINAL`

### Full re-materialization (on-demand)

| Trigger | Scope | Когда |
|---------|-------|-------|
| Cost profile bulk update | Affected seller_sku → all related marts | После CSV import |
| Advertising data first ingestion | All marts (advertising_cost appears) | Phase B extended |
| Mapping correction | All facts + marts | После hotfix normalizer mapping |

### Configuration

```yaml
datapulse:
  materialization:
    daily-rematerialization-cron: "0 2 * * *"
    incremental-enabled: true
    full-rematerialization-timeout: 2h
    optimize-final-after-full: true
```

## Schema evolution

ClickHouse schema управляется numbered SQL migration scripts (`clickhouse/migrations/V001__*.sql`). При старте `datapulse-ingest-worker` проверяется таблица `schema_version` в ClickHouse, непримёнённые миграции применяются автоматически.

При несовместимых изменениях (sorting key): `CREATE TABLE new` → `INSERT INTO new SELECT ... FROM old` → `RENAME TABLE`. Rollback: re-materialization из canonical layer (PostgreSQL → ClickHouse) — всегда возможна, canonical = source of truth.

## REST API

### P&L

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/analytics/pnl/summary` | Any role | P&L summary per connection. Filters: `?connectionId=...&from=...&to=...`. Response: aggregated P&L components |
| GET | `/api/analytics/pnl/by-product` | Any role | P&L по продуктам. Paginated. Filters: `?connectionId=...&period=YYYYMM&sellerSkuId=...&search=...`. Sort: any P&L column |
| GET | `/api/analytics/pnl/by-posting` | Any role | P&L по отправкам. Paginated. Filters: `?connectionId=...&from=...&to=...&sellerSkuId=...` |
| GET | `/api/analytics/pnl/posting/{postingId}/details` | Any role | Drill-down: все fact_finance entries для posting. Response: `[{ entryType, measures, entryDate }]` |
| GET | `/api/analytics/pnl/trend` | Any role | P&L trend by period (daily/weekly/monthly). Filters: `?connectionId=...&from=...&to=...&granularity=MONTHLY` |

### Inventory intelligence

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/analytics/inventory/overview` | Any role | Inventory overview: total SKUs, stock-out risk distribution, frozen capital. Filter: `?connectionId=...` |
| GET | `/api/analytics/inventory/by-product` | Any role | Per-product inventory analysis. Paginated. Includes days_of_cover, stock_out_risk, frozen_capital |
| GET | `/api/analytics/inventory/stock-history` | Any role | Historical stock levels for product. Filters: `?productId=...&from=...&to=...` |

### Returns & penalties

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/analytics/returns/summary` | Any role | Return rate summary. Filter: `?connectionId=...&period=YYYYMM` |
| GET | `/api/analytics/returns/by-product` | Any role | Per-product return analysis. Paginated. Includes return_rate_pct, top_reason, financial_impact |
| GET | `/api/analytics/returns/trend` | Any role | Return rate trend over time |

### Data quality

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/analytics/data-quality/status` | Any role | Sync freshness per connection/domain. Automation blocker status |
| GET | `/api/analytics/data-quality/reconciliation` | ADMIN, OWNER | Reconciliation residual stats per connection. Baseline vs current |

### Drill-down (provenance)

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/analytics/provenance/entry/{entryId}` | Any role | Canonical finance entry details (PostgreSQL) |
| GET | `/api/analytics/provenance/entry/{entryId}/raw` | ADMIN, OWNER | Raw S3 payload link. Returns presigned S3 URL |

## Связанные модули

- [ETL Pipeline](etl-pipeline.md) — canonical data sources для materialization
- [Pricing](pricing.md) — derived signals для pricing pipeline через signal assembler
- [Promotions](promotions.md) — `dim_promo_campaign`, `fact_promo_product`, `mart_promo_product_analysis`; margin signals для promo evaluation
- [Seller Operations](seller-operations.md) — grid, journals, mismatch monitor
- [Audit & Alerting](audit-alerting.md) — alert event lifecycle, automation blocker query, notification delivery для data quality alerts
