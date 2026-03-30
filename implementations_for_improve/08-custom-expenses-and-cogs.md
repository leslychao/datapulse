# Custom Expenses & COGS — Implementation Plan

## Обзор

Пользовательские расходы позволяют продавцам учитывать себестоимость товаров и операционные расходы, которые интегрируются в PnL-витрины. COGS (Cost of Goods Sold) рассчитывается через `cost_per_unit` в записях расходов категории `PRODUCT`.

## Модель данных

### Категории — `CustomExpenseCategoryEntity`

**Таблица `custom_expense_category`:**
| Колонка | Тип | Назначение |
|---------|-----|-----------|
| id | BIGSERIAL | PK |
| account_id | BIGINT | Аккаунт |
| name | VARCHAR | Название категории |
| expense_scope | VARCHAR | `PRODUCT` или `ACCOUNT` |
| is_active | BOOLEAN | Активна (default: true) |

**`ExpenseScope`:**
- `PRODUCT` — расходы привязаны к конкретному товару, поддерживают `cost_per_unit`
- `ACCOUNT` — общие расходы аккаунта (аренда, зарплата и т.д.)

### Записи — `CustomExpenseEntryEntity`

**Таблица `custom_expense_entry`:**
| Колонка | Тип | Назначение |
|---------|-----|-----------|
| id | BIGSERIAL | PK |
| account_id | BIGINT | Аккаунт |
| category_id | BIGINT FK | Категория |
| amount | DECIMAL | Сумма расхода |
| currency | VARCHAR | Валюта (default: RUB) |
| expense_month | DATE | Месяц расхода (first day of month) |
| dim_product_id | BIGINT | Товар (только для PRODUCT scope) |
| cost_per_unit | DECIMAL | Себестоимость за единицу (только для PRODUCT scope) |
| note | VARCHAR | Комментарий |

## REST API — `CustomExpenseController`

**Base path:** `/api/accounts/{accountId}/custom-expenses`

### Categories
| Endpoint | Auth | Описание |
|----------|------|----------|
| `GET /categories` | canRead | Список категорий |
| `POST /categories` | canWrite | Создание (201) |
| `PUT /categories/{categoryId}` | canWrite | Обновление |
| `DELETE /categories/{categoryId}` | canWrite | Удаление (204) |

### Entries
| Endpoint | Auth | Описание |
|----------|------|----------|
| `GET /entries` | canRead | Список записей |
| `POST /entries` | canWrite | Создание (201) |
| `PUT /entries/{entryId}` | canWrite | Обновление |
| `DELETE /entries/{entryId}` | canWrite | Удаление (204) |

### Summary
| Endpoint | Auth | Описание |
|----------|------|----------|
| `GET /summary` | canRead | Агрегация по периоду |

## Бизнес-правила — `CustomExpenseService`

**Валидации категорий:**
- Уникальность `(accountId, name)` — `existsByAccountIdAndName()`
- При обновлении: exclude текущей категории
- Удаление запрещено если есть entries: `existsByCategoryId()`

**Валидации записей (`validateEntryScope`):**
- `ACCOUNT` scope: `dimProductId` и `costPerUnit` должны быть `null`
- `PRODUCT` scope: `dimProductId` обязателен

**Уникальность записей:**
- Unique key: `(accountId, categoryId, expenseMonth, dimProductId)`
- `expenseMonth` нормализуется к первому дню месяца: `withDayOfMonth(1)`

## COGS в витринах

### Механизм расчёта в `OrderPnlMartJdbcRepository`

```sql
-- 1. Выбор актуальной себестоимости по месяцам
expense_unit_costs AS (
    SELECT DISTINCT ON (ce.account_id, ce.dim_product_id, ce.expense_month)
        ce.account_id, ce.dim_product_id, ce.expense_month, ce.cost_per_unit
    FROM custom_expense_entry ce
    JOIN custom_expense_category cc ON cc.id = ce.category_id
    WHERE cc.expense_scope = 'PRODUCT'
      AND cc.is_active = true
      AND ce.cost_per_unit IS NOT NULL
    ORDER BY ce.account_id, ce.dim_product_id, ce.expense_month, ce.updated_at DESC
),

-- 2. Построение SCD-ranges через lead()
expense_cost_ranges AS (
    SELECT
        account_id, dim_product_id,
        expense_month AS valid_from,
        lead(expense_month) OVER (
            PARTITION BY account_id, dim_product_id ORDER BY expense_month
        ) AS valid_to,
        cost_per_unit
    FROM expense_unit_costs
),

-- 3. Join с продажами и расчёт COGS
cogs_by_order AS (
    SELECT
        s.account_id, s.source_platform, s.order_id,
        SUM(s.quantity * COALESCE(er.cost_per_unit, 0)) AS cogs_amount
    FROM fact_sales s
    LEFT JOIN expense_cost_ranges er
        ON er.account_id = s.account_id
       AND er.dim_product_id = s.dim_product_id
       AND date_trunc('month', s.sale_date)::date >= er.valid_from
       AND (er.valid_to IS NULL OR date_trunc('month', s.sale_date)::date < er.valid_to)
    GROUP BY s.account_id, s.source_platform, s.order_id
)
```

### SCD-подобная логика
- `valid_from` = `expense_month` текущей записи
- `valid_to` = `expense_month` следующей записи (через `lead()`)
- `valid_to IS NULL` → последняя запись, действует бессрочно
- Продажа привязывается к cost_per_unit через месяц продажи

### Агрегация в PnL Summary
`CustomExpenseAggregationRepository.aggregateByPeriod(accountId, monthFrom, monthTo)`:
- Сводка по категориям за период
- Общая сумма расходов
- Используется в `OrderPnlQueryService.summaryOrderPnl()` для корректировки PnL

## Ключевые файлы

| Файл | Модуль | Роль |
|------|--------|------|
| `CustomExpenseController.java` | application | REST API |
| `CustomExpenseService.java` | core | CRUD + validations |
| `CustomExpenseCategoryEntity.java` | core | Category entity |
| `CustomExpenseEntryEntity.java` | core | Entry entity |
| `CustomExpenseAggregationRepository.java` | core | Period aggregation JDBC |
| `OrderPnlMartJdbcRepository.java` | etl | COGS calculation in mart SQL |
| `ProductPnlMartJdbcRepository.java` | etl | COGS in product mart SQL |
