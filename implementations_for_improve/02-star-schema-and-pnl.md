# Star Schema & PnL Analytics — Implementation Plan

## Обзор

Аналитическая модель DataPulse построена по принципу star schema: нормализованные измерения (`dim_*`), факты (`fact_*`) и агрегированные витрины (`mart_*`). PnL рассчитывается на двух уровнях: по заказам (`mart_order_pnl`) и по продуктам (`mart_product_pnl`).

## Архитектура данных

```
API маркетплейсов (Ozon / WB)
 → raw_* таблицы (JSONB, идемпотентность по SHA-256)
 → MaterializationHandlers (SQL: JSONB → upsert в dim_* / fact_*)
 → fact_finance (консолидация финансовых компонентов)
 → mart_order_pnl (PnL по заказам)
 → mart_product_pnl (PnL по продуктам)
```

## Измерения (dim_*)

| Таблица | Natural Key | Миграция | Особенности |
|---------|------------|----------|-------------|
| `dim_product` | `(account_id, source_platform, source_product_id)` | `0007-create-dim-product.xml` | JPA-сущность `DimProductEntity` в core |
| `dim_warehouse` | `(account_id, source_platform, source_warehouse_id)` | `0003-create-dim-warehouses.xml` | — |
| `dim_category` | tree structure | `0004-create-dim-category.xml` | Ozon + WB category trees |
| `dim_subject_wb` | WB subjects | `0009-create-dim-subject-wb.xml` | Связка с categories |
| `dim_tariff_wb` | FK → `dim_category_id` | `0005-create-dim-tariff-wb.xml` | SCD2: `valid_from` / `valid_to` |
| `dim_tariff_ozon` | FK → `product_id` | `0006-create-dim-tariff-ozon.xml` | SCD2: `valid_from` / `valid_to` |
| `dim_promo_campaign` | campaign identifiers | `0038-create-promo-module.xml` | Промо-кампании |

## Факты (fact_*)

| Таблица | Grain | Миграция | Назначение |
|---------|-------|----------|-----------|
| `fact_sales` | `(account_id, source_platform, source_event_id)` | `0008` | Продажи: quantity, unit_price, sale_amount, discount_amount |
| `fact_returns` | `(account_id, source_platform, source_event_id)` | `0011` | Возвраты |
| `fact_commission` | по операции/заказу | `0013` | Комиссии: `commission_type` (SALE, ACQUIRING...) |
| `fact_logistics_costs` | по операции/заказу | `0012` | Логистика: `logistics_type` |
| `fact_penalties` | по заказу | `0016` | Штрафы |
| `fact_marketing_costs` | по заказу | `0017` | Маркетинг (marketplace-initiated) |
| `fact_advertising_costs` | `(account_id, source_platform, campaign_id, advertising_date, source_product_id)` | `0028` | Рекламные затраты (seller CPM/CPC) |
| `fact_finance` | `(account_id, source_platform, order_id, finance_date)` | `0015` | Консолидированный финансовый факт |
| `fact_inventory_snapshot` | по дате/продукту/складу | `0010` | Снимки остатков |
| `fact_supply` | по поставке | `0046` | Поставки (WB incomes) |
| `fact_promo_product` | по кампании/продукту | `0038` | Промо-товары |

## Витрина `mart_order_pnl`

### Grain
Один ряд на заказ `(account_id, source_platform, order_id)`.

### Dual-driver spine
Spine строится как UNION заказов из `fact_finance` и `fact_sales`. Это позволяет видеть:
- Заказы с финансовыми данными, но без продаж (финансовые корректировки)
- Заказы с продажами, но без финансов (ещё не рассчитанные)

### Формула PnL
```
pnl_amount = revenue_gross
  - seller_discount_amount
  - marketplace_commission_amount
  - acquiring_commission_amount
  - logistics_cost_amount
  - penalties_amount
  - other_marketplace_charges_amount
  - marketing_cost_amount
  - advertising_cost_amount
  - refund_amount
  + compensation_amount
  - cogs_amount
```

### COGS (себестоимость)
Рассчитывается через `custom_expense_entry.cost_per_unit`:
1. CTE `expense_unit_costs` — выбор последней записи расхода для `(account_id, dim_product_id, expense_month)` по `updated_at`
2. CTE `expense_cost_ranges` — построение SCD-ranges через `lead(expense_month)` → `valid_from` / `valid_to`
3. CTE `cogs_by_order` — join `fact_sales` → `expense_cost_ranges` по `dim_product_id` и месяцу продажи → `sum(quantity * cost_per_unit)`

### Advertising allocation
Pro-rata по доле выручки продукта за день:
1. `ad_daily_product` — суммарные рекламные расходы за день по продукту
2. `sales_daily_product` — суммарная выручка продукта за день
3. `advertising_by_order` — аллокация: `daily_spend * sale_amount / daily_revenue`

### UPSERT стратегия
```sql
ON CONFLICT (account_id, source_platform, order_id) DO UPDATE
SET ... = excluded....
WHERE (...) IS DISTINCT FROM (...)
```
`IS DISTINCT FROM` предотвращает бессмысленные UPDATE без изменения данных.

## Витрина `mart_product_pnl`

### Grain
Один ряд на продукт `(account_id, source_platform, dim_product_id)`.

### Allocation strategy
Прямые метрики (из fact-таблиц): revenue, COGS, advertising.
Аллоцированные (pro-rata из `mart_order_pnl`): commission, logistics, penalties, marketing, compensation, refund.

Формула аллокации:
```
revenue_share = product_sale_amount_in_order / order_revenue_gross
allocated_cost = order_cost * revenue_share
```

### Margin
```
margin_percent = pnl_amount * 100.0 / revenue (если revenue ≠ 0)
```

## REST API

### `OrderPnlController` — `/api/accounts/{accountId}/order-pnl`

| Endpoint | Метод | Описание |
|----------|-------|----------|
| `GET /` | searchOrderPnl | Пагинированный список заказов с фильтрами |
| `GET /summary` | summaryOrderPnl | Агрегированная сводка + custom expenses overlay |
| `GET /trend` | trendOrderPnl | Временной ряд (дневные точки) |
| `GET /breakdown` | breakdownOrderPnl | Группировка по маркетплейсам |
| `GET /{orderId}/detail` | findOrderDetail | Детали заказа + cost components |

### Server-side sort
Whitelist `SORTABLE_COLUMNS` маппит DTO-поля в SQL-колонки:
```java
Map.entry("revenueGross", "revenue_gross"),
Map.entry("pnlAmount", "pnl_amount"),
Map.entry("lastFinanceDate", "last_finance_date"), ...
```
`buildOrderByClause(Sort)` — safe sort clause с fallback на default order. Предотвращает SQL injection.

### Order Detail
Помимо `mart_order_pnl` данных, включает cost components из fact-таблиц:
- `fact_commission` → COMMISSION items
- `fact_logistics_costs` → LOGISTICS items
- `fact_penalties` → PENALTY / OTHER_MARKETPLACE_CHARGE items
- `fact_marketing_costs` → MARKETING items

### Summary с Custom Expenses
`OrderPnlQueryService.summaryOrderPnl()` обогащает base summary данными из `CustomExpenseAggregationRepository`:
```java
BigDecimal adjustedPnl = baseSummary.totalPnl().subtract(expenses.totalAmount());
return baseSummary.withExpenseOverlay(expenses, adjustedPnl);
```

## Ключевые файлы

| Файл | Модуль | Роль |
|------|--------|------|
| `OrderPnlMartJdbcRepository.java` | etl | Mart UPSERT SQL (370 строк) |
| `ProductPnlMartJdbcRepository.java` | etl | Product mart UPSERT SQL (340 строк) |
| `OrderPnlReadJdbcRepository.java` | core | Read repo with server-side sort |
| `ProductPnlReadJdbcRepository.java` | core | Product PnL read |
| `OrderPnlController.java` | application | REST endpoints |
| `OrderPnlQueryService.java` | core | Business logic + expense overlay |
| `ConsolidatedPnlController.java` | application | Multi-account PnL summary |
