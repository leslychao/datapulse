# Разрешение открытых вопросов P&L-санации

**Статус:** resolved
**Зависит от:** pnl-architecture-sanitation.md, data-architecture.md

---

## Контекст

Санация P&L-модели выявила 7 открытых вопросов. Каждый из них проанализирован ниже с учётом реальных данных провайдеров, текущего grain fact_finance и потребностей pricing pipeline.

**Критическое наблюдение перед анализом:** fact_finance имеет grain = `operation_id` (одна строка = одна финансовая операция). Это означает:
- WB: каждая строка reportDetailByPeriod = одна операция = один товар (1 unit). `operation_id` = `rrd_id`.
- Ozon: каждая операция (sale, brand_commission, stars, acquiring, storage) = одна строка. Одна продажа (posting) порождает 3-5 строк fact_finance.
- P&L по posting: `GROUP BY posting_id, SUM(measures)`.
- Standalone операции (storage, disposal): `posting_id = ''`, ключ = `operation_id`.

Этот grain **уже обеспечивает** тот уровень детализации, который давали компонентные факты (fact_commission, fact_logistics_costs и т.д.). Удаление компонентных фактов ничего не теряет.

---

## Q1: Quantity в fact_finance

### Анализ

**Зачем quantity нужен?** Для расчёта COGS = Σ(quantity × unit_cost) в mart_posting_pnl.

**Что происходит при добавлении quantity в fact_finance:**

| Маркетплейс | Операция | Что было бы quantity | Проблема |
|-------------|----------|---------------------|----------|
| WB | Каждая строка reportDetailByPeriod | 1 (всегда) | Тривиально, но не добавляет ценности |
| Ozon | OperationAgentDeliveredToCustomer (sale) | Σ(products[].quantity) по всем товарам в posting | Это TOTAL по posting, не per-product. Бесполезно для per-product COGS |
| Ozon | MarketplaceServiceBrandCommission | Не применимо (нет concept of quantity) | Семантически бессмысленно |
| Ozon | OperationMarketplaceServiceStorage | Не применимо | Семантически бессмысленно |

**Корневая проблема:** COGS требует per-product granularity (quantity_productA × cost_productA + quantity_productB × cost_productB). fact_finance оперирует на уровне операций, а не товаров. Одна Ozon sale-операция может покрывать несколько товаров в posting, и revenue_amount = accruals_for_sale — это сумма по всем товарам. Разбить её на per-product внутри fact_finance невозможно без дополнительных данных из posting API.

**Альтернатива:** fact_sales уже имеет grain = (posting × product) с quantity per product. Это ИМЕННО тот grain, который нужен для COGS.

### Решение: **Не добавлять quantity в fact_finance. Оставить fact_sales для COGS.**

**Обоснование:**
1. quantity семантически бессмысленно для большинства типов операций в fact_finance (brand_commission, acquiring, storage, disposal).
2. Даже для sale-операций, Ozon quantity = total по posting, не per-product — бесполезно для COGS.
3. fact_sales УЖЕ решает задачу: grain = posting × product, quantity per product.
4. COGS join: `fact_sales.quantity × fact_product_cost.unit_cost WHERE fact_product_cost.valid_from <= sale_ts < fact_product_cost.valid_to` — чистый, тестируемый.
5. Добавление quantity в fact_finance = ложная прямизна, усложняющая модель без выигрыша.

**Следствие:** mart_posting_pnl зависит от fact_finance + fact_sales + fact_product_cost + fact_advertising_costs. fact_sales необходим ИМЕННО для COGS.

**Исправление в data-architecture.md:** в разделе «Marts» зависимости mart_order_pnl (→ mart_posting_pnl) должны включать fact_sales:
```
mart_posting_pnl | fact_finance (primary), fact_sales (qty for COGS), fact_product_cost (COGS), fact_advertising_costs (pro-rata)
```

---

## Q2: Detail-level drill-down

### Анализ

**Что обеспечивали компонентные факты?**
- fact_commission: per-operation commission detail
- fact_logistics_costs: per-operation logistics detail
- fact_marketing_costs: per-operation marketing detail
- fact_penalties: per-operation penalty detail

**Что обеспечивает fact_finance после удаления компонентных фактов?**

fact_finance с grain = operation_id хранит КАЖДУЮ финансовую операцию как отдельную строку. Запрос:

```sql
SELECT operation_id, posting_id, finance_date,
       logistics_cost_amount, marketplace_commission_amount, penalties_amount
FROM fact_finance FINAL
WHERE posting_id = '87621408-0010-1'
  AND account_id = :accountId
```

Возвращает:
| operation_id | posting_id | logistics_cost_amount | marketplace_commission_amount |
|---|---|---|---|
| 10001 | 87621408-0010-1 | -71.45 | -35.95 |
| 10002 | 87621408-0010-1 | 0 | -0.79 |
| 10003 | 87621408-0010-1 | 0 | 0 |

Это РОВНО тот уровень детализации, который давали component facts. Ничего не потеряно.

**Какой drill-down НЕ обеспечивает fact_finance?**

Breakdown logistics-cost внутри одной операции: Ozon sale-операция содержит `services[]` (DirectFlowLogistic: -63₽, DelivToCustomer: -8.45₽). fact_finance хранит SUM(-71.45₽), а не per-service. Это service-level детализация.

**Нужна ли service-level детализация?**

Два сценария:
1. **«Почему логистика по этому posting стоила 71₽?»** — forensic / support scenario. Редкий. Допустимо ходить в canonical_finance_entry (PostgreSQL), где хранится полный operation payload.
2. **«Какие типы логистических сервисов стоят дороже всего в целом?»** — analytical scenario. Средне-частый. Если нужен — требует fact в ClickHouse.

По эмпирическим данным (Ozon, Jan 2025): DirectFlowLogistic = -119,908₽, DelivToCustomer = -14,557₽, ReturnFlowLogistic = -6,146₽. Первый доминирует. Разбивка по сервисам полезна для понимания структуры логистических затрат.

### Решение: **Не создавать fact_finance_detail сейчас. Использовать canonical_finance_entry (PostgreSQL) для forensic drill-down. Добавить fact_finance_detail в Phase G если появится потребность в analytical breakdown по типам сервисов.**

**Обоснование:**
1. fact_finance с grain=operation уже даёт per-operation detail — ровно то, что давали component facts.
2. Service-level breakdown (DirectFlowLogistic vs DelivToCustomer) — это forensic/diagnostic, не P&L calculation.
3. canonical_finance_entry в PostgreSQL хранит полные данные для drill-down по конкретному posting.
4. Для Phase B (Trust Analytics) этого достаточно. Если в Phase G потребуется analytics по типам сервисов — добавить fact_finance_detail (одна таблица вместо 4 component facts).

---

## Q3: fact_supply

### Анализ

**Кто использует fact_supply?**

mart_inventory_analysis зависимости: fact_inventory_snapshot, fact_sales, fact_product_cost. **fact_supply НЕ в списке зависимостей.**

Inventory intelligence алгоритмы (из functional-capabilities.md):
- Days of cover: `available / avg_daily_sales(N)` — не использует supply data
- Stock-out risk: threshold на days_of_cover — не использует supply data
- Frozen capital: `excess_qty × cost_price` — не использует supply data
- Replenishment: `avg_daily_sales(N) × target_days_of_cover − available` — не использует supply data

Lead time (время доставки поставки) упоминается как input для stock-out risk, но описан как **configurable** параметр (селлер вводит вручную), а не как вычисляемый из исторических поставок.

fact_supply материализуется из WB SUPPLY_FACT event. Ozon не имеет аналога.

### Решение: **Убрать fact_supply. Не реализовывать SUPPLY_FACT ETL event на Phase B.**

**Обоснование:**
1. Ни одна витрина не зависит от fact_supply.
2. Lead time — configurable параметр, не computed metric.
3. fact_supply — WB-only, нет аналога на Ozon → нарушает marketplace-agnostic принцип.
4. Если в Phase G потребуется analytics по поставкам / расчёт lead time из истории — добавить тогда.
5. Убирается WB SUPPLY_FACT из ETL графа → упрощение pipeline.

---

## Q4: Standalone operations в fact_finance

### Анализ

**Какие операции standalone (posting_id = '')?**

Ozon (из эмпирических данных, Jan 2025):
| Тип операции | Количество | Сумма | Может быть привязан к товару? |
|---|---|---|---|
| OperationMarketplaceServiceStorage | 22 | period-level | Нет — начисление за период |
| OperationElectronicServiceStencil (упаковка) | 134 | -300₽+ | **Да — items[] содержит sku** (но posting_number = '') |
| DisposalReasonFailedToPickupOnTime | 3 | per-item | **Да — items[] содержит sku** |
| DisposalReasonDamagedPackaging | 1 | per-item | **Да — items[] содержит sku** |
| MarketplaceSaleReviewsOperation | 5 | per-action | Нет — не товароспецифично |
| AccrualInternalClaim | 1 | compensation | Нет |
| AccrualWithoutDocs | 1 | compensation | Нет |
| MarketplaceSellerCompensationOperation | 1 | compensation | Нет |

WB: **Нет standalone операций.** Все суммы (storage_fee, penalty, acceptance) — поля ВНУТРИ каждой строки reportDetailByPeriod, привязанной к конкретному товару/srid. WB allocates всё per item by design.

**Ключевое наблюдение:** часть standalone Ozon операций (упаковка, утилизация) ИМЕЕТ items[] с sku, хотя posting_number пуст. Их МОЖНО привязать к товару через items[].sku lookup.

**Варианты для Ozon standalone операций:**

| Вариант | Storage (нет sku) | Packaging/disposal (есть sku) | Compensation (нет sku) |
|---|---|---|---|
| **(a) Unallocated line** | Account-level | Account-level | Account-level |
| **(b) Pro-rata by revenue** | По выручке | По выручке | По выручке |
| **(c) Smart allocation** | Account-level | По sku из items[] | Account-level |

### Решение: **Вариант (c) — smart allocation.**

Правила:
1. **Операции с items[].sku** (packaging, disposal): привязывать к товару через sku → product lookup при материализации. В fact_finance: `product_id` заполняется из items[].sku. В mart_product_pnl: попадает в P&L конкретного товара.
2. **Операции без привязки к товару** (storage, compensation, reviews): `product_id = NULL`. В mart_product_pnl: отдельная строка «Account-level charges» (не аллоцируется на товары).
3. **Итоговая формула:** `account_P&L = Σ(product_P&L) + account_level_charges`.

**Обоснование:**
1. Packaging и disposal — per-item расходы, Ozon их знает (items[] заполнен). Скрывать эту информацию в «нераспределённые» — потеря данных.
2. Storage — period-level charge, не привязан к конкретным товарам. Pro-rata аллокация по revenue — ложная precision (storage зависит от объёма/веса, не от выручки). Честнее показать как account overhead.
3. Compensations — непредсказуемы, не привязаны к товарам.
4. Seller видит: «P&L по товару X = 1000₽» + «Расходы кабинета (хранение, подписки) = -500₽» = «Общий P&L кабинета = X₽».

**Следствие для fact_finance:** добавить поле `product_id` (nullable). Заполняется из:
- WB: `nm_id` из строки reportDetailByPeriod (всегда заполнен)
- Ozon order-linked: через `items[].sku → catalog lookup`
- Ozon standalone с items[]: через `items[].sku → catalog lookup`
- Ozon standalone без items[]: NULL

---

## Q5: mart_promo_product_analysis

### Анализ

Delivery phases (project-vision-and-scope.md):
- **Phase B (Trust Analytics):** P&L, returns/penalties, inventory intelligence
- **Phase E (Seller Operations):** Promo Journal, operational layer
- **Phase G (Intelligence):** Advertising analytics, scenario modelling

Promo analysis не входит в Phase B scope. Data foundation (fact_promo_product, dim_promo_campaign) загружается при ingestion (Phase A), но аналитическая витрина — Phase E capability (Promo Journal).

### Решение: **Отложить mart_promo_product_analysis до Phase E.**

**Что сохранить:** fact_promo_product, dim_promo_campaign (загружаются в PROMO_SYNC event). Это data foundation — готова для использования когда потребуется.

**Что не делать:** Не строить витрину, не проектировать promo effectiveness метрики на Phase B.

---

## Q6: Advertising data readiness

### Анализ

Provider capability matrix:
- WB Advertising: **NEEDS MIGRATION** (v2 POST → v3 GET; endpoint disabled)
- Ozon Advertising: **STUB** (требует отдельной OAuth2 регистрации на api-performance.ozon.ru)

Delivery phases:
- Phase B: P&L без advertising → advertising_cost = 0 в формуле
- Phase G: Advertising analytics → fact_advertising_costs заполняется

P&L формула корректно обрабатывает отсутствие advertising data: `advertising_cost = 0` → term просто не влияет на результат.

### Решение: **Ожидаемо и корректно. Архитектурных изменений не требуется.**

**Что фиксируем:**
1. P&L на Phase B работает без advertising data. Это by design, не limitation.
2. fact_advertising_costs существует в schema, но пуста до Phase G.
3. mart_posting_pnl корректно обрабатывает пустые advertising data (COALESCE в join, 0 default).
4. Pricing signal `ad_cost_ratio`: при отсутствии данных → fallback = 0% (из cascading fallback chain в pricing-architecture-analysis.md).

---

## Q7: WB SPP delta

### Анализ

**Что такое SPP?** WB Special Price — программа лояльности WB. Покупатель видит цену со скидкой SPP, WB компенсирует продавцу разницу. Продавец получает revenue на основе pre-SPP цены (retail_price_withdisc_rub).

**Откуда delta?** Данные из official docs example:
- revenue_amount (retail_price_withdisc_rub) = 399.68₽
- marketplace_commission (ppvz_sales_commission) = 23.74₽
- acquiring (acquiring_fee) = 14.89₽
- net_payout (ppvz_for_pay) = 376.99₽
- Expected payout = 399.68 − 23.74 − 14.89 − 0 (logistics, penalties, storage...) = 361.05₽
- Actual payout = 376.99₽
- **Residual = +15.94₽** (продавец получил БОЛЬШЕ, чем ожидалось)

Причина: WB internally рассчитывает payout через ppvz_vw (доля продавца = 22.25₽ в примере), а не через retail_price_withdisc_rub. SPP-компенсация заложена внутрь ppvz_for_pay. Наша модель не декомпозирует ppvz_vw.

**Ozon аналог:** Ozon marketing subsidy — для FBS с маркетингом Ozon, customer_price < seller price. Ozon субсидирует разницу. accruals_for_sale = seller-facing price (не buyer price). Аналогичный эффект, аналогичная residual.

**Варианты:**

| Вариант | Плюсы | Минусы |
|---|---|---|
| **(a) Отдельный measure spp_compensation_amount** | Прозрачность, селлер видит SPP эффект | WB-specific; ломает marketplace-agnostic модель; Ozon marketing subsidy потребует аналогичный measure |
| **(b) Оставить в reconciliation_residual** | Чистая модель; marketplace-agnostic; residual = «то, что мы не декомпозируем» | Селлер может не понимать, почему residual ≠ 0 |
| **(c) Split residual на known + unexplained** | Лучшее из обоих | Complexity; «known» — marketplace-specific knowledge |

### Решение: **Вариант (b) — оставить в reconciliation_residual. Документировать expected behavior.**

**Обоснование:**

1. **Marketplace-agnostic принцип.** fact_finance не должен содержать WB-specific measures. Если добавить spp_compensation_amount, при каждом новом marketplace adjustment (WB меняет SPP формулу, Ozon добавит новый subsidy) модель потребует новых колонок.

2. **reconciliation_residual ОЖИДАЕМО ≠ 0.** Это by design. Residual — не ошибка, а «часть payout, которую наша модель не декомпозирует до individual components». SPP — одна из причин.

3. **Оба маркетплейса имеют аналогичный эффект.** WB SPP и Ozon marketing subsidy — обе marketplace absorbs discount, seller gets higher payout than computed. Обе попадают в residual. Symmetry подтверждает, что это NOT WB-specific workaround, а fundamental property модели.

4. **Anomaly monitoring.** Если WB residual стабильно = +3-5% от revenue → это SPP, всё нормально. Если вдруг +15% или −5% → аномалия, alert. Мониторинг residual trend работает одинаково для «explained» и «unexplained» deltas.

5. **UI решение, не data model решение.** Объяснение «ваш residual включает SPP-компенсацию WB» — это tooltip/documentation в UI, не отдельная колонка в schema.

**Следствие:** Добавить в docs description для reconciliation_residual:
> reconciliation_residual типично включает marketplace-specific корректировки: WB SPP-компенсацию (positive residual ~3-5% от revenue), Ozon marketing subsidy (аналогично). Стабильный residual — норма. Резкое изменение residual — anomaly.

---

## Сводка решений

| # | Вопрос | Решение | Impact на архитектуру |
|---|--------|---------|----------------------|
| Q1 | Quantity в fact_finance | **Не добавлять. Оставить fact_sales для COGS** | fact_sales остаётся; зависимость mart_posting_pnl += fact_sales |
| Q2 | Detail drill-down | **fact_finance (operation grain) достаточен. Forensic → canonical_finance_entry PG** | Компонентные facts удаляются без потери. Новых таблиц не нужно |
| Q3 | fact_supply | **Убрать. Нет потребителей** | Убрать fact_supply, SUPPLY_FACT event |
| Q4 | Standalone allocation | **Smart: items[].sku → product, остальное → account-level** | Добавить product_id (nullable) в fact_finance |
| Q5 | mart_promo_product_analysis | **Отложить до Phase E** | Убрать из Phase B scope. Оставить fact_promo_product, dim_promo_campaign |
| Q6 | Advertising readiness | **Expected. P&L на Phase B без advertising = by design** | Без изменений |
| Q7 | WB SPP delta | **Оставить в reconciliation_residual. Документировать** | Обновить описание residual в docs |

## Что зафиксировано для fact_finance

По итогам решений, финальная структура fact_finance:

| Аспект | Значение |
|--------|----------|
| Grain | operation_id (одна финансовая операция = одна строка) |
| Sorting key | `(account_id, source_platform, operation_id, finance_date)` |
| Grouping field | `posting_id` (для P&L по posting: GROUP BY posting_id) |
| Product attribution | `product_id` (nullable): WB — всегда заполнен; Ozon order-linked — через items[].sku; Ozon standalone с items[] — через items[].sku; без items[] — NULL |
| Spine pattern | **Убран.** Материализация напрямую из canonical_finance_entry |
| Component facts | **Убраны.** fact_commission, fact_logistics_costs, fact_marketing_costs, fact_penalties — НЕ создаются |

## Что зафиксировано для mart_posting_pnl (ex mart_order_pnl)

| Аспект | Значение |
|--------|----------|
| Grain | (account_id, source_platform, posting_id, date) |
| Зависимости | fact_finance (primary), fact_sales (qty for COGS), fact_product_cost (COGS SCD2), fact_advertising_costs (pro-rata) |
| COGS | `SUM(fact_sales.quantity × fact_product_cost.unit_cost)` с SCD2 match по sale_ts |
| Advertising | Daily ad spend × (posting revenue / product-day total revenue) |
| Standalone operations | Не попадают в mart_posting_pnl (posting_id = ''). Попадают в mart_product_pnl как account_level_charges |

## Что зафиксировано для mart_product_pnl

| Аспект | Значение |
|--------|----------|
| Grain | (account_id, source_platform, product_id, period) |
| Attributable P&L | Агрегация из mart_posting_pnl + fact_finance WHERE product_id = X для standalone с sku |
| Account-level charges | Standalone операции без product_id → отдельная строка «Account-level charges» |
| Итоговая формула | `account_P&L = Σ(product_P&L) + account_level_charges` |

---

## Обновлённый план действий

После разрешения открытых вопросов, шаги из санации уточняются:

1. **Переименование** mart_order_pnl → mart_posting_pnl
2. **Добавление product_id** (nullable) в fact_finance schema
3. **Прямая материализация** fact_finance из canonical_finance_entry (без spine, без компонентных фактов)
4. **Перенаправление pricing signal queries** на fact_finance
5. **Добавление fact_sales** в зависимости mart_posting_pnl
6. **Удаление:** fact_commission, fact_logistics_costs, fact_marketing_costs, fact_penalties, fact_supply, SUPPLY_FACT event
7. **Отложить:** mart_promo_product_analysis (Phase E)
8. **Документирование** reconciliation_residual (SPP, Ozon subsidy)
9. **Обновление** data-architecture.md, mapping-spec.md, pricing-architecture-analysis.md

**Следующий шаг:** обновить data-architecture.md с применением всех принятых решений.
