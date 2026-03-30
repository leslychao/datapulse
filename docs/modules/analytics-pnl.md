# Модуль: Analytics & P&L

**Фаза:** B — Trust Analytics
**Зависимости:** [ETL Pipeline](etl-pipeline.md)
**Runtime:** datapulse-ingest-worker (materializers), datapulse-api (reads)

---

## Назначение

Правдивая, сверяемая прибыльность по SKU, категории, кабинету, маркетплейсу и периоду. Star schema в ClickHouse, materializers, P&L формула, inventory intelligence, returns & penalties analysis, data quality controls.

## P&L и unit economics

### Обязательные свойства

- Полная формула P&L: revenue − комиссии − логистика − хранение − штрафы − реклама − COGS − возвраты + компенсации − прочие удержания + reconciliation_residual.
- Reconciliation residual: явно отслеживается как мера расхождения между выплатой и суммой компонентов.
- COGS по SCD2: себестоимость привязана к моменту продажи.
- Drill-down от P&L до отдельных финансовых записей с provenance.
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

### revenue_amount (DD-11 / DD-13)

Seller-facing price (цена, на основе которой рассчитываются комиссии МП), ДО удержаний маркетплейса. **Не** цена, которую заплатил покупатель (МП может субсидировать скидки).

| Провайдер | Источник | Значение |
|-----------|----------|----------|
| **Ozon** | `accruals_for_sale` из `OperationAgentDeliveredToCustomer` | seller-facing price × qty |
| **WB** | `retail_price_withdisc_rub` из строк `doc_type_name = 'Продажа'` | Розничная цена с учётом скидки продавца, ДО SPP |

### refund_amount — правила агрегации

`refund_amount` агрегируется ТОЛЬКО из возвратных операций, НЕ из spine. Revenue spine включает только продажные операции. Возвратные операции агрегируются отдельно в `refund_amount`. Это предотвращает двойной учёт.

### Advertising allocation

Daily ad spend × (line's sale_amount / product-day daily_revenue). Пропорциональная аллокация по revenue share.

### COGS

`fact_sales × cost_profile` с SCD2 `valid_from` / `valid_to` привязкой к `sale_ts`. Себестоимость определяется по моменту продажи.

Таблицы: `cost_profile` (PostgreSQL, canonical) → `fact_product_cost` (ClickHouse, analytics).

### Reconciliation residual

`reconciliation_residual = net_payout − Σ(все компоненты P&L)`

Residual типично включает marketplace-specific корректировки: WB SPP-компенсацию (positive residual ~3-5%), Ozon marketing subsidy. Стабильный residual — норма. Резкое изменение residual — anomaly.

## Star schema (ClickHouse)

### Analytics layer

| Свойство | Описание |
|----------|----------|
| Назначение | Derived facts, marts, projections |
| Хранилище | ClickHouse |
| Engine | `ReplacingMergeTree` с `ver` (timestamp) для upsert-семантики |
| Формат | Star schema: `dim_*` + `fact_*` + `mart_*` |
| Мутабельность | Append-only facts; marts пересчитываются из facts |
| Материализация | Materializer читает из canonical layer (PostgreSQL), пишет в ClickHouse |
| Ограничение | Не source of truth для decisions; не хранит action lifecycle, retries, reconciliation |

### Dimensions

| Таблица | Содержание |
|---------|------------|
| `dim_product` | Мастер-запись товара: название, бренд, категория, marketplace IDs |
| `dim_warehouse` | Склады: тип (FBO/FBS/seller), название, location |
| `dim_category` | Иерархия категорий |
| `dim_promo_campaign` | Промо-кампании: даты, тип, название (Phase F/G) |

### Facts

**P&L-critical facts:**

| Таблица | Содержание | P&L роль |
|---------|------------|----------|
| `fact_finance` | Consolidated financial fact | Центральный P&L fact |
| `fact_sales` | Продажи: количество, сумма, привязка к order/product | Quantity для COGS |
| `fact_advertising_costs` | Рекламные затраты per campaign/day | Pro-rata allocation (Phase G) |
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

### Marts

| Таблица | Содержание | Зависимости | Phase |
|---------|------------|-------------|-------|
| `mart_posting_pnl` | P&L по отправке (posting grain) | fact_finance, fact_sales (qty), fact_product_cost (COGS) | A/B |
| `mart_product_pnl` | P&L по продукту за период | mart_posting_pnl + fact_finance (standalone ops) | A/B |
| `mart_inventory_analysis` | Inventory intelligence | fact_inventory_snapshot, fact_sales, fact_product_cost | B |
| `mart_returns_analysis` | Returns & penalties | fact_returns, fact_finance, fact_sales | B |
| `mart_promo_product_analysis` | Эффективность промо | **Phase F/G** |

### Smart allocation для standalone ops

| Mart | Grain | Standalone ops |
|------|-------|----------------|
| `mart_posting_pnl` | posting × date | Только order-linked ops |
| `mart_product_pnl` | product × period | Attributable P&L + account_level_charges (отдельная строка, НЕ allocated) |

Формула: `account_P&L = Σ(product_P&L) + account_level_charges`

## fact_finance — consolidated financial fact

Центральная финансовая таблица. Материализуется **напрямую** из canonical_finance_entry (без промежуточных component facts, без spine pattern).

### Measures

| Measure | Описание | Ozon source | WB source |
|---------|----------|-------------|-----------|
| `revenue_amount` | Выручка продавца | `accruals_for_sale` | `retail_price_withdisc_rub` |
| `marketplace_commission_amount` | Комиссия МП | `sale_commission` + brand | `ppvz_sales_commission` |
| `acquiring_commission_amount` | Эквайринг | `MarketplaceRedistributionOfAcquiringOperation` | `acquiring_fee` |
| `logistics_cost_amount` | Логистика | Σ logistics services | `delivery_rub` + `rebill_logistic_cost` |
| `storage_cost_amount` | Хранение | `OperationMarketplaceServiceStorage` | `storage_fee` |
| `penalties_amount` | Штрафы | `DisposalReason*` | `penalty` + `deduction` |
| `marketing_cost_amount` | Маркетинг (не ads) | `MarketplaceSaleReviewsOperation` | — |
| `acceptance_cost_amount` | Приёмка | — | `acceptance` |
| `other_marketplace_charges_amount` | Прочие удержания | `StarsMembership` + `ElectronicServiceStencil` | — |
| `compensation_amount` | Компенсации | `SellerCompensationOperation` + claims | `additional_payment` |
| `refund_amount` | Реверс выручки | `ClientReturnAgentOperation` → accruals_for_sale | Строки `doc_type_name = 'Возврат'` |
| `net_payout` | Чистая выплата | Σ amount | `ppvz_for_pay` |
| `reconciliation_residual` | net_payout − Σ(компоненты) | — | — |

### Материализация

Sorting key для `ReplacingMergeTree`: `(account_id, source_platform, operation_id, finance_date)`. `operation_id` — каноническое имя (Ozon: `operation_id`, WB: `rrd_id`).

```
canonical_finance_entry (PostgreSQL)
→ Materializer: group by posting, aggregate measures
→ INSERT INTO fact_finance (ReplacingMergeTree)
→ SELECT ... FINAL при чтении
```

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

| Control | Описание | Реакция |
|---------|----------|---------|
| Stale data detection | `marketplace_sync_state.last_success_at` > threshold | Alert + block automation |
| Missing sync detection | Ожидаемый sync не произошёл по расписанию | Alert |
| Spike detection | Аномальные всплески в финансовых/inventory метриках | Alert |
| Mismatch detection | Расхождения между связанными data domains | Alert |
| Residual tracking | `reconciliation_residual` > threshold | Alert + investigation |
| Automation blocker | Блокировка автоматического ценообразования при broken truth | Block |

## Design decisions

### K-1: revenue_gross → revenue_amount — RESOLVED

Переименовано. Семантика: seller-facing price (до комиссий МП, после скидок). WB: `retail_price_withdisc_rub`. Ozon: `accruals_for_sale`.

### K-2: P&L формула — RESOLVED

Добавлены `storage_cost_amount`, `acceptance_cost_amount`. Формула до 13 компонентов.

### K-3: Двойное вычитание возвратов — RESOLVED

Revenue spine ТОЛЬКО sales-операции. `refund_amount` агрегируется ОТДЕЛЬНО.

### K-4: seller_discount_amount — RESOLVED (удалён)

Скидки уже учтены в `revenue_amount`. Ни один API не предоставляет отдельное поле.

### G-1: Ozon services classification — RESOLVED

12 service names верифицированы. Маппинг в mapping-spec.md §7.

### G-4: cost_profile vs fact_product_cost — RESOLVED

`cost_profile` (PostgreSQL) → `fact_product_cost` (ClickHouse). `custom_expense_entry` удалён.

### G-5: Ozon acquiring join — RESOLVED (DD-15)

Acquiring операции join по `order_number` (strip `-N` suffix). Верифицировано эмпирически.

### G-6: WB SPP — RESOLVED (DD-14)

SPP — скидка WB для покупателя, оплачиваемая WB. Не компонент P&L продавца.

### N-1: Sorting key — RESOLVED

`(account_id, source_platform, operation_id, finance_date)` вместо `posting_id` в sorting key.

### N-2: mart_order_pnl spine — RESOLVED

`mart_posting_pnl` строится из `fact_finance` как единственного source of truth для P&L.

## Санация P&L-модели (rationale)

### Выявленные и исправленные наросты

1. **Избыточная фрагментация:** fact_commission, fact_logistics_costs и др. — дублировали fact_finance. Удалены.
2. **Spine pattern:** лишняя индирекция. Прямая материализация из canonical_finance_entry.
3. **Naming «order»:** grain = posting, не заказ. Переименовано в `mart_posting_pnl`.
4. **fact_supply:** WB-specific, нет потребителей. Отложено на Phase G.
5. **fact_orders в P&L:** убран из зависимостей P&L-витрин.

### Вердикт

Ядро модели концептуально здорово. Основные искажения — accumulation, не corruption. Очистка выполнена без redesign. Модель масштабируема: новый маркетплейс = новый adapter, без изменения star schema.

## Связанные модули

- [ETL Pipeline](etl-pipeline.md) — canonical data sources для materialization
- [Pricing](pricing.md) — derived signals для pricing pipeline через signal assembler
- [Seller Operations](seller-operations.md) — grid, journals, mismatch monitor
