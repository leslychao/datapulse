# Datapulse — Архитектура данных

## Data pipeline

Данные проходят через строго последовательный pipeline. Пропуск стадий запрещён.

```
API маркетплейсов → Raw → Normalized → Canonical → Analytics
```

### Raw layer

| Свойство | Описание |
|----------|----------|
| Назначение | Immutable source-faithful хранилище; replay; forensic traceability |
| Хранилище | S3-compatible |
| Формат | Исходный JSON payload от API маркетплейса |
| Мутабельность | Immutable — записи не обновляются и не удаляются (кроме retention cleanup) |
| Идемпотентность | SHA-256 от serialized payload; `ON CONFLICT DO NOTHING` |
| Dedup key | `(request_id, source_id, record_key)` |
| Retention | Configurable; default: `keep_count = 3` последних снапшотов per (account, table) |

### Normalized layer

| Свойство | Описание |
|----------|----------|
| Назначение | Типизированное представление данных провайдера; ещё не бизнес-модель |
| Хранилище | In-process (десериализованные provider DTO) |
| Ответственность | Парсинг JSON, type coercion, нормализация timestamp, нормализация знаков |
| Mapping version | Привязка к версии контракта провайдера |

### Canonical layer

Каноническая модель — единая, унифицированная структура данных, в которую приводятся данные из всех маркетплейсов. WB и Ozon имеют разные API, разные поля, разные форматы. Canonical layer стирает эти различия: например, товар WB (`nmID`, `vendorCode`) и товар Ozon (`product_id`, `offer_id`) становятся единой сущностью `CanonicalOffer`. Вся бизнес-логика (P&L, pricing, inventory) работает исключительно с канонической моделью — это обеспечивает добавление новых маркетплейсов без изменения бизнес-логики.

| Свойство | Описание |
|----------|----------|
| Назначение | Marketplace-agnostic каноническая модель — основа для business computations |
| Хранилище | PostgreSQL (авторитетный) |
| Мутабельность | UPSERT с `IS DISTINCT FROM` (no-churn on unchanged rows) |
| Decision-grade | Current state (цены, остатки, каталог, cost) — из canonical (PostgreSQL). Derived signals (velocity, return rate, ad spend) — из analytics (ClickHouse) через signal assembler |
| Provenance | Каждая запись прослеживаема до raw source |

#### Canonical State vs Canonical Flow

Canonical layer делится на две категории по характеру данных:

| Категория | Назначение | Хранилище | Примеры |
|-----------|------------|-----------|---------|
| **Canonical State** | Текущее состояние сущностей; pricing pipeline читает напрямую | PostgreSQL (read + write) | Каталог, текущие цены, текущие остатки, себестоимость |
| **Canonical Flow** | Транзакционный поток событий; пишется в PostgreSQL, аналитические агрегаты читаются из ClickHouse | PostgreSQL (write) → ClickHouse (read) | Заказы, продажи, возвраты, финансовые операции |

Pricing pipeline читает current state напрямую из canonical (PostgreSQL). Derived signals (velocity, return rate, advertising spend) вычисляются из analytics layer (ClickHouse) через dedicated signal assembler.

### Analytics layer

| Свойство | Описание |
|----------|----------|
| Назначение | Derived facts, marts, projections |
| Хранилище | ClickHouse |
| Engine | `ReplacingMergeTree` с `ver` (timestamp) для upsert-семантики через дедупликацию по sorting key |
| Формат | Star schema: `dim_*` + `fact_*` + `mart_*` |
| Мутабельность | Append-only facts; marts пересчитываются из facts |
| Материализация | Materializer читает из canonical layer (PostgreSQL), вычисляет, пишет напрямую в ClickHouse |
| Ограничение | Не source of truth для decisions; не хранит action lifecycle, retries, reconciliation |

## Канонические сущности

| Сущность | Категория | Назначение | Ключевые поля |
|----------|-----------|------------|---------------|
| `CanonicalOffer` | State | Товарное предложение | sellerSku, marketplaceSku, name, brand, category, status |
| `CanonicalPriceSnapshot` | State | Снимок цены | price, discountPrice, currency, capturedAt |
| `CanonicalStockSnapshot` | State | Снимок остатков | available, reserved, warehouseId |
| `CanonicalOrder` | Flow | Заказ/отправление | externalOrderId, quantity, pricePerUnit, status |
| `CanonicalSale` | Flow | Продажа | saleAmount, commission |
| `CanonicalReturn` | Flow | Возврат | returnAmount, returnReason, returnDate |
| `CanonicalFinanceEntry` | Flow | Финансовая операция | entryType, amount (нормализованный знак), entryDate |

## Связи каталожных сущностей

```
product_master (внутренний товар селлера, cross-marketplace)
  └── seller_sku (артикул продавца; один product_master → N SKU: размеры, цвета)
        └── marketplace_offer (конкретное предложение на конкретном маркетплейсе)
              ├── WB: nmID, vendorCode
              └── Ozon: product_id, offer_id, sku
```

| Таблица | Уровень | Связь | Marketplace IDs |
|---------|---------|-------|-----------------|
| `product_master` | Товар (cross-marketplace) | workspace_id (FK) | — |
| `seller_sku` | Артикул продавца | product_master_id (FK) | vendorCode (WB), offer_id (Ozon) |
| `marketplace_offer` | Предложение на маркетплейсе | seller_sku_id (FK), marketplace_connection_id (FK) | nmID (WB), product_id (Ozon), sku (Ozon) |
| `cost_profile` | Себестоимость (SCD2) | seller_sku_id (FK) | — |

`CanonicalOffer` — это доменная абстракция поверх трёх таблиц. `product_master` обеспечивает cross-marketplace identity для P&L по товару. `marketplace_offer` хранит marketplace-specific данные и связь с конкретным подключением. Tenant isolation обеспечивается через `product_master.workspace_id`; остальные таблицы привязаны транзитивно.

## Star schema (analytics)

### Dimensions

| Таблица | Содержание |
|---------|------------|
| `dim_product` | Мастер-запись товара: название, бренд, категория, marketplace IDs |
| `dim_warehouse` | Склады: тип (FBO/FBS/seller), название, location |
| `dim_category` | Иерархия категорий |
| `dim_promo_campaign` | Промо-кампании: даты, тип, название |

Отдельные dimension-таблицы для аккаунтов и маркетплейсов не создаются. `account_id` и `source_platform` хранятся как денормализованные поля в фактовых и димовых таблицах.

### Facts

**P&L-critical facts:**

| Таблица | Содержание | P&L роль |
|---------|------------|----------|
| `fact_finance` | Consolidated financial fact (см. раздел ниже) | Центральный P&L fact |
| `fact_sales` | Продажи: количество, сумма, привязка к order/product | Quantity для COGS |
| `fact_advertising_costs` | Рекламные затраты per campaign/day | Pro-rata allocation (Phase G) |
| `fact_product_cost` | Себестоимость (SCD2: `valid_from` / `valid_to`) | Unit cost для COGS |

**Operational / analytical facts:**

| Таблица | Содержание | Назначение |
|---------|------------|------------|
| `fact_orders` | Заказы/отправления: количество, сумма, статус | Order funnel, conversion |
| `fact_returns` | Возвраты: количество, сумма, причина | Return rate analysis |
| `fact_price_snapshot` | Исторические снимки цен | Ценовая история |
| `fact_inventory_snapshot` | Снимки остатков | Inventory intelligence |
| `fact_supply` | Поставки (WB incomes) | Phase G: lead time, reorder point. **НЕ реализуется в Phase A/B** (sanitation Q3). Контракт задокументирован в wb-read-contracts.md §8 |

**Deferred to Phase F/G:**

| Таблица | Phase | Причина |
|---------|-------|---------|
| `fact_promo_product` | Phase F | Promo ingestion pipeline не реализован |
| `dim_promo_campaign` | Phase F | Promo ingestion pipeline не реализован |

**Eliminated (sanitation 2026-03-30, pnl-architecture-sanitation.md §4):**

| Таблица | Причина удаления | Данные доступны в |
|---------|-----------------|-------------------|
| ~~`fact_commission`~~ | Дублирует fact_finance.marketplace_commission_amount | fact_finance |
| ~~`fact_logistics_costs`~~ | Дублирует fact_finance.logistics_cost_amount | fact_finance |
| ~~`fact_marketing_costs`~~ | Дублирует fact_finance.marketing_cost_amount | fact_finance |
| ~~`fact_penalties`~~ | Дублирует fact_finance.penalties_amount | fact_finance |

Detail drill-down (breakdown по типам сервисов) обеспечивается через canonical_finance_entry (PostgreSQL), не через отдельные fact-таблицы.

### Marts

| Таблица | Содержание | Зависимости | Phase |
|---------|------------|-------------|-------|
| `mart_posting_pnl` | P&L по отправке (posting grain, НЕ order grain) | fact_finance, fact_sales (qty), fact_product_cost (COGS), fact_advertising_costs (Phase G) | A/B |
| `mart_product_pnl` | P&L по продукту за период (с allocation standalone costs) | mart_posting_pnl + fact_finance (standalone ops, pro-rata by revenue) | A/B |
| `mart_inventory_analysis` | Inventory intelligence: days of cover, stock-out risk | fact_inventory_snapshot, fact_sales, fact_product_cost, [fact_supply (Phase G: lead time)] | B |
| `mart_returns_analysis` | Returns & penalties: return rate, penalty breakdown | fact_returns, fact_finance (penalties_amount), fact_sales | B |
| `mart_promo_product_analysis` | Эффективность промо | fact_promo_product, fact_sales, fact_finance | **Phase F/G** |

**Переименование:** `mart_order_pnl` → `mart_posting_pnl`. Grain = posting (отправка), а не покупательский заказ. Один заказ может содержать несколько postings.

**Smart allocation для standalone ops (sanitation Q4):**

| Mart | Grain | Standalone ops |
|------|-------|----------------|
| `mart_posting_pnl` | posting × date | Только order-linked ops. Account-level charges НЕ включаются |
| `mart_product_pnl` | product × period | Attributable P&L (posting + items[].sku ops) + account_level_charges (отдельная строка, НЕ allocated) |

Операции с items[].sku (packaging, disposal) → product-level attribution. Операции без SKU (storage, subscriptions, compensation) → account-level charges (не распределяются по товарам — storage зависит от объёма/веса, не от выручки).

Формула: `account_P&L = Σ(product_P&L) + account_level_charges`

fact_finance: добавлено поле `product_id` (nullable). WB: всегда заполнен (nm_id). Ozon: через items[].sku lookup; NULL для операций без SKU.

## fact_finance — consolidated financial fact

Центральная финансовая таблица. Материализуется **напрямую** из canonical_finance_entry (без промежуточных component facts, без spine pattern — sanitation §4.1, §4.2).

### Measures

| Measure | Описание | Ozon source | WB source |
|---------|----------|-------------|-----------|
| `revenue_amount` | Выручка продавца (seller-facing price × qty, после скидок, до комиссий МП; DD-13 resolved) | `accruals_for_sale` из `OperationAgentDeliveredToCustomer` (positive, DD-11) | `retail_price_withdisc_rub` из строк `doc_type_name = 'Продажа'` (DD-13) |
| `marketplace_commission_amount` | Комиссия маркетплейса (включая brand commission) | `sale_commission` + `MarketplaceServiceBrandCommission` | `ppvz_sales_commission` |
| `acquiring_commission_amount` | Комиссия за эквайринг | `MarketplaceRedistributionOfAcquiringOperation` (join по order_number, DD-15) | `acquiring_fee` |
| `logistics_cost_amount` | Логистические затраты (доставка, обратная логистика, перемещения) | Σ logistics services (см. mapping-spec) | `delivery_rub` + `rebill_logistic_cost` |
| `storage_cost_amount` | Складское хранение | `OperationMarketplaceServiceStorage` | `storage_fee` |
| `penalties_amount` | Штрафы, утилизация | `DisposalReason*` operations | `penalty` + `deduction` |
| `marketing_cost_amount` | Маркетинговые затраты (покупка отзывов и т.п.; НЕ рекламные кампании) | `MarketplaceSaleReviewsOperation` | — |
| `acceptance_cost_amount` | Плата за приёмку | — | `acceptance` |
| `other_marketplace_charges_amount` | Прочие удержания (подписки, упаковка и т.п.) | `StarsMembership` + `OperationElectronicServiceStencil` | — |
| `compensation_amount` | Компенсации от маркетплейса | `MarketplaceSellerCompensationOperation` + `AccrualInternalClaim` + `AccrualWithoutDocs` | `additional_payment` |
| `refund_amount` | Реверс выручки по возвратам (accruals_for_sale < 0) | `ClientReturnAgentOperation` → accruals_for_sale | Строки `doc_type_name = 'Возврат'` |
| `net_payout` | Чистая выплата | Σ `amount` по всем операциям posting | `ppvz_for_pay` |
| `reconciliation_residual` | `net_payout − Σ(компоненты)` | — | — |

**Удалённые measures:**
- `seller_discount_amount` — удалена. Скидки уже учтены в `revenue_amount` (цена после скидок). Ни Ozon, ни WB не предоставляют отдельное поле "сумма скидки продавца" в финансовых отчётах. Данные о скидках доступны в postings (Ozon `total_discount_value`) и orders (WB `discountPercent`), но не в finance API.

**Переименованные measures:**
- `revenue_gross` → `revenue_amount`. Семантика: seller-facing price (цена, на основе которой МП рассчитывает комиссии), после скидок, до удержаний маркетплейса. **Не** является ни "gross" в бухгалтерском смысле, ни ценой, которую заплатил покупатель (МП может субсидировать скидки). См. DD-11, DD-13, DD-14.

**Добавленные measures:**
- `storage_cost_amount` — выделено из `other_marketplace_charges` в отдельный measure, т.к. хранение — значимая статья расходов.
- `acceptance_cost_amount` — WB-специфичная плата за приёмку товара.

### Материализация

Materialization выполняется непосредственно в ClickHouse. Sorting key для `ReplacingMergeTree`: `(account_id, source_platform, operation_id, finance_date)`. Дедупликация — по `ver` (timestamp вставки).

`operation_id` — каноническое имя для уникального идентификатора финансовой операции:
- Ozon: `operation_id` (long, уникален для каждой операции)
- WB: `rrd_id` (long, уникален для каждой строки отчёта)

`posting_id` — дополнительное поле для группировки:
- Ozon: `posting_number` для order-linked операций (sale, brand, stars); для acquiring — `order_number` формат (без суффикса -1, см. DD-15); для standalone (storage, disposal, compensation) — пустая строка
- WB: `srid` для привязки к заказу

**Почему `operation_id` вместо `posting_id` в sorting key:** Использование `posting_id` приводит к коллизиям — несколько безордерных операций одного дня (storage, compensation) имеют одинаковый пустой `posting_id` и сливаются при `FINAL`-чтении. `operation_id` гарантирует уникальность каждой финансовой записи.

```
canonical_finance_entry (PostgreSQL)
→ Materializer: group by posting_number, aggregate measures
→ INSERT INTO fact_finance (ReplacingMergeTree, sorting key: account_id, source_platform, operation_id, finance_date)
→ SELECT ... FINAL при чтении для актуальных данных
```

**Прямая материализация (без spine, без промежуточных facts).** Materializer читает canonical_finance_entry, агрегирует по posting и раскладывает по measure-колонкам.

**Ozon: одна продажа → несколько финансовых операций.** Posting "87621408-0010-1" порождает 3 операции: sale + brand_commission + stars_membership. Materializer агрегирует их по `posting_number`.

**Detail drill-down:** canonical_finance_entry (PostgreSQL) хранит каждую операцию с entryType и amount. API-запрос по posting_id → полный breakdown по сервисам. fact_finance_detail в ClickHouse не создаётся (Phase A/B). Рассмотреть как Phase G extension при необходимости analytical aggregation по типам сервисов.

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

### revenue_amount — DD-11 / DD-13

`revenue_amount` = seller-facing price (цена, на основе которой рассчитываются комиссии МП), ДО удержаний маркетплейса.

**Это НЕ цена, которую заплатил покупатель** (МП может субсидировать скидки из своих средств).
Это цена, от которой рассчитывается выручка продавца — основа для P&L.

| Провайдер | Источник | Значение | DD |
|-----------|----------|----------|----|
| **Ozon** | `accruals_for_sale` из `OperationAgentDeliveredToCustomer` | seller-facing price × qty. FBO: совпадает с buyer price. FBS с маркетингом Ozon: может быть > buyer price (Ozon субсидирует разницу, DD-11) | DD-11 |
| **WB** | `retail_price_withdisc_rub` из строк `doc_type_name = 'Продажа'` | Розничная цена с учётом скидки продавца, ДО SPP (DD-14). WB компенсирует SPP из своих средств | DD-13 |

**Отвергнутые кандидаты для WB:**
- `ppvz_vw` — доля продавца ПОСЛЕ split-а с WB markup; не является gross revenue
- `ppvz_for_pay` — net payout после всех удержаний; это итог, а не начало P&L
- `retail_price` — розничная цена до скидки продавца; слишком "gross"
- `retail_amount` — семантика неясна, не используется

### refund_amount — правила агрегации

`refund_amount` агрегируется ТОЛЬКО из возвратных операций, НЕ из spine. Revenue spine включает только продажные операции (`OperationAgentDeliveredToCustomer` / `doc_type_name = 'Продажа'`). Возвратные операции (`ClientReturnAgentOperation` / `doc_type_name = 'Возврат'`) агрегируются отдельно в `refund_amount`. Это предотвращает двойной учёт.

### Advertising allocation

Daily ad spend × (line's sale_amount / product-day daily_revenue). Пропорциональная аллокация по revenue share.

### COGS

`fact_sales × cost_profile` с SCD2 `valid_from` / `valid_to` привязкой к `sale_ts`. Себестоимость определяется по моменту продажи.

Таблицы: `cost_profile` (PostgreSQL, canonical) → `fact_product_cost` (ClickHouse, analytics). COGS join выполняется в ClickHouse.

### Reconciliation residual

`reconciliation_residual = net_payout − Σ(все компоненты P&L)`

Residual отслеживается явно. Anomaly controls мониторят residual > threshold.

reconciliation_residual типично включает marketplace-specific корректировки: WB SPP-компенсацию (positive residual ~3-5% от revenue), Ozon marketing subsidy (аналогично). Стабильный residual — норма. Резкое изменение residual — anomaly. Отдельный measure `spp_compensation_amount` НЕ вводится — SPP не является P&L-компонентом продавца (sanitation Q7).

## Домен возвратов

### Определение

Возврат — факт обратного движения товара от покупателя (или отмены до получения). В каноническую модель попадает как `CanonicalReturn` (Flow-сущность), в аналитику — как `fact_returns`.

### Источники данных по провайдерам

Возвраты — единственный домен, где источники данных между маркетплейсами **принципиально различаются**.

| Провайдер | Источник | Что доступно | Что отсутствует |
|-----------|----------|--------------|-----------------|
| **Ozon** | Выделенный endpoint `v1/returns/list` | ID возврата, SKU, количество, цена, комиссия, причина, даты, статус, схема (FBO/FBS), хранение | — |
| **WB** | Финансовый отчёт (`reportDetailByPeriod`), строки с `doc_type_name = 'Возврат'` | Финансовые суммы, привязка к заказу через `srid` | Причина возврата, статус, даты lifecycle |
| **WB** (дополнительный) | Analytics endpoint `goods-return` | Причина, тип возврата, статусы, даты | **Денежные суммы** (endpoint не содержит amount fields) |

**Архитектурное следствие:** для WB полная картина возврата требует объединения данных из двух источников — финансового отчёта (суммы) и analytics endpoint (причины, статусы). Canonical layer принимает данные из того источника, который доступен в рамках текущего ETL event.

### Структура CanonicalReturn

| Поле | Назначение | Ozon | WB |
|------|------------|------|----|
| `externalReturnId` | Идентификатор возврата у провайдера | `id` (long) | `srid` (из финансового отчёта) |
| `sellerSku` | Артикул продавца | `product.offer_id` | `supplierArticle` |
| `quantity` | Количество единиц | `product.quantity` | 1 (1 строка = 1 единица) |
| `returnAmount` | Сумма возврата | `product.price.price` | Из финансового отчёта (ppvz_for_pay или аналог) |
| `returnReason` | Причина возврата | `return_reason_name` | Из `goods-return` endpoint (`returnType`) |
| `returnDate` | Дата возврата | `logistic.return_date` (ISO 8601 UTC) | Из финансового отчёта (`rr_dt`) |
| `currency` | Валюта | `product.price.currency_code` | "RUB" (implicit) |

### Валюация возвратов

Определение `returnAmount` — нетривиальная задача:

- **Ozon:** endpoint `v1/returns/list` содержит `product.price.price` — сумма доступна напрямую.
- **WB:** dedicated endpoint `goods-return` **не содержит денежных полей**. Сумма возврата определяется из финансового отчёта, где строки с `doc_type_name = 'Возврат'` содержат полный финансовый breakdown (revenue reversal, commission refund, logistics).

### Возвраты в ETL pipeline

Возвраты заполняются **разными ETL events** в зависимости от провайдера:

| Провайдер | ETL Event | Причина |
|-----------|-----------|---------|
| **Ozon** | `SALES_FACT` | Ozon returns — часть sales-домена (отдельный endpoint, но одна фаза ingestion) |
| **WB** | `FACT_FINANCE` | WB returns извлекаются из строк финансового отчёта |

Граф зависимостей гарантирует корректный порядок: `FACT_FINANCE` зависит от `SALES_FACT`, поэтому к моменту обработки WB returns каталожные данные уже доступны.

### fact_returns vs refund_amount (fact_finance)

Эти два артефакта **дополняют друг друга**, не дублируют:

| Артефакт | Что фиксирует | Зачем нужен |
|----------|---------------|-------------|
| `fact_returns` | Операционный факт: что вернули, почему, когда, сколько штук | Return rate analysis, причины возвратов, тренды по SKU/категории |
| `refund_amount` в `fact_finance` | Финансовый impact: сколько денег вернулось покупателю | P&L calculation, reconciliation |

`mart_returns_analysis` строится на `fact_returns` + `fact_finance` (penalties_amount) + `fact_sales` и отвечает на вопросы: return rate trends, penalty breakdown, потери по SKU/категории.

### Join keys для возвратов

| Провайдер | Возврат → Заказ/Продажа | Возврат → Финансы | Возврат → Каталог |
|-----------|------------------------|-------------------|-------------------|
| **Ozon** | `posting_number` | `posting_number` → finance `items[].sku` | `product.offer_id` → `seller_sku` |
| **WB** | `srid` | `srid` (одна строка финансового отчёта = один факт) | `nm_id` → `marketplace_offer` |

### Причины возвратов

Таксономия причин **не унифицируется** между маркетплейсами в canonical layer — `returnReason` хранит оригинальное значение провайдера.

| Провайдер | Примеры значений | Источник |
|-----------|------------------|----------|
| **Ozon** | `return_reason_name` (свободный текст от покупателя) + `type` (классификация: "Cancellation" и др.) | `v1/returns/list` |
| **WB** | `returnType` ("Возврат заблокированного товара", "Возврат брака") + `reason` ("Цвет", "Размер") | `goods-return` endpoint |

Нормализация причин (mapping провайдерских значений в единую таксономию) — потенциальное расширение для `mart_returns_analysis`, но не часть текущей канонической модели.

## Sign conventions

### Ozon

| Правило | Описание |
|---------|----------|
| Конвенция | `amount > 0` = кредит продавцу; `amount < 0` = дебет |
| В каноническую модель | Знак сохраняется as-is |
| Формула | `amount = accruals_for_sale + sale_commission + Σ(services[].price)` |

### Wildberries

| Правило | Описание |
|---------|----------|
| Конвенция | Все значения положительные; имя поля определяет credit/debit |
| Credit fields | `ppvz_for_pay`, `ppvz_vw`, `additional_payment` |
| Debit fields | `ppvz_sales_commission`, `acquiring_fee`, `delivery_rub`, `penalty`, `storage_fee`, `deduction`, `rebill_logistic_cost`, `acceptance` |
| В каноническую модель | Debit-поля умножаются на −1 при нормализации |

Нормализация знаков выполняется на уровне adapter/materializer. Canonical layer получает унифицированные знаковые значения.

## Форматы timestamp

| Провайдер | Формат | Особенности |
|-----------|--------|-------------|
| Ozon (финансы) | `yyyy-MM-dd HH:mm:ss` | Не ISO 8601; timezone — Moscow |
| WB (финансы) | Dual-format | date-only или ISO 8601; parser обязан поддерживать оба |
| Ozon (прочие) | ISO 8601 | Стандартный формат |

## Join keys

### Wildberries

```
nmID (каталог) ↔ nmId (цены) ↔ nmId (заказы/продажи) ↔ nm_id (финансы)
vendorCode = supplierArticle = seller's SKU
srid связывает orders ↔ sales ↔ finance rows
```

### Ozon

```
product_id (каталог) ↔ product_id (цены/остатки)
offer_id = seller's SKU (каталог, цены, остатки, постинги)
sku ↔ sku (остатки ↔ постинги ↔ finance items)
posting_number связывает orders ↔ sales ↔ returns ↔ finance (sale/brand/stars операции)

ACQUIRING (DD-15):
  acquiring.posting_number = order_number (без суффикса -N)
  Join: strip "-N" от posting_number → order_number
  Пример: posting "0151413710-0012-1" → acquiring "0151413710-0012"

STANDALONE (storage, disposal, compensation):
  posting_number = "" → ключ = operation_id
  Аллокация на заказ — pro-rata

Важно: finance items[] содержит только sku + name, без offer_id.
Lookup: items[].sku → catalog sources[].sku → product_id → offer_id
```

## Идентификаторы

| Поле | Описание |
|------|----------|
| `posting_id` (Datapulse) | Order-level группировка. Ozon: `posting_number`, WB: `srid` |
| `operation_id` (Datapulse) | Уникальный ID финансовой операции. Ozon: `operation_id` (long), WB: `rrd_id` (long) |
| `order_number` (Ozon) | Номер заказа без суффикса shipping. Используется acquiring операциями (DD-15) |
| WB primary keys | `nmID`, `vendorCode`, `srid`, `rrd_id` |
| Ozon primary keys | `product_id`, `offer_id`, `sku`, `posting_number`, `operation_id` |

## Источники истины

| Данные | Source of truth | Запрещено |
|--------|----------------|-----------|
| Business state (tenancy, decisions, actions) | PostgreSQL | ClickHouse, Redis, RabbitMQ, S3 |
| Canonical State (каталог, цены, остатки) | PostgreSQL (canonical state tables) | Raw layer, normalized DTO |
| Canonical Flow (заказы, продажи, возвраты, финансы) | PostgreSQL (canonical flow tables); аналитические агрегаты — ClickHouse | Raw JSON, provider DTO |
| Входы pricing decisions: current state | Canonical State (PostgreSQL) | Raw/normalized data |
| Входы pricing decisions: derived signals | Analytics (ClickHouse) через signal assembler | Direct ClickHouse reads из pricing pipeline |
| Action lifecycle state | PostgreSQL (actions, attempts, reconciliation) | RabbitMQ, Redis |
| Retry truth | PostgreSQL (attempt count, next_attempt_at) | RabbitMQ TTL, in-memory |
| Historical analytics | ClickHouse (facts, marts) | — |
| Raw evidence / replay | S3-compatible | — |

## Data provenance

Каждая каноническая запись прослеживаема до raw source:

1. Raw record хранится в S3; `job_item` служит индексом raw layer в PostgreSQL (S3 key, request_id, record_key, status).
2. Materialization привязывает canonical record к `job_item_id` / `job_execution_id` source execution.
3. Fact/mart records содержат `account_id`, `source_platform`, привязку к canonical entities.
4. Audit log фиксирует materialization events.

Отдельная таблица `raw_record` не создаётся — `job_item` выполняет роль raw layer index.

## Граф зависимостей ETL events

```
WAREHOUSE_DICT ──────────────────┐
                                 ├→ PRODUCT_DICT ──→ SALES_FACT ──→ FACT_FINANCE
CATEGORY_DICT ───────────────────┘       │
                                         ├→ PRICE_SNAPSHOT
                                         ├→ INVENTORY_FACT
                                         ├→ ADVERTISING_FACT
                                         └→ PROMO_SYNC
```

| Event | Зависимости |
|-------|-------------|
| `WAREHOUSE_DICT`, `CATEGORY_DICT` | Нет |
| `PRODUCT_DICT` | `CATEGORY_DICT`, `WAREHOUSE_DICT` |
| `PRICE_SNAPSHOT` | `PRODUCT_DICT` |
| `SALES_FACT` | `PRODUCT_DICT` |
| `INVENTORY_FACT` | `PRODUCT_DICT`, `WAREHOUSE_DICT` |
| `ADVERTISING_FACT` | `PRODUCT_DICT` |
| `FACT_FINANCE` | `SALES_FACT` |
| `PROMO_SYNC` | `PRODUCT_DICT` |

## Материализация по доменам

| Domain | Event | Target tables (ClickHouse) | Target tables (PostgreSQL canonical) |
|--------|-------|---------------------------|--------------------------------------|
| Категории | `CATEGORY_DICT` | `dim_category` (recursive CTE → UPSERT) | — |
| Склады | `WAREHOUSE_DICT` | `dim_warehouse` (UNION ALL with priority → UPSERT) | — |
| Товары | `PRODUCT_DICT` | `dim_product` (JOIN raw tables → UPSERT) | `product_master`, `seller_sku`, `marketplace_offer` |
| Цены | `PRICE_SNAPSHOT` | `fact_price_snapshot` | `canonical_price_snapshot` |
| Продажи (Ozon) | `SALES_FACT` | `fact_orders`, `fact_sales`, `fact_returns`, dim backfill | `canonical_order`, `canonical_sale`, `canonical_return` |
| Продажи (WB) | `SALES_FACT` | `fact_orders`, dim_product backfill | `canonical_order` |
| Остатки | `INVENTORY_FACT` | `fact_inventory_snapshot` | `canonical_stock_snapshot` |
| Поставки (WB) | `SUPPLY_FACT` | `fact_supply` | — |
| Реклама | `ADVERTISING_FACT` | `fact_advertising_costs` | — |
| Финансы (Ozon) | `FACT_FINANCE` | `fact_finance` (direct materialization) | `canonical_finance_entry` |
| Финансы (WB) | `FACT_FINANCE` | `fact_sales`, `fact_returns`, `fact_finance` (direct materialization) | `canonical_sale`, `canonical_return`, `canonical_finance_entry` |
| Промо | `PROMO_SYNC` | `dim_promo_campaign`, `fact_promo_product` | — |

### Platform-specific правила

**WB sales/returns:** заполняются в обработчике `FACT_FINANCE`, не в `SALES_FACT`. WB `SALES_FACT` выполняет только dim_product backfill. Граф зависимостей (`FACT_FINANCE` depends on `SALES_FACT`) гарантирует порядок.

**Ozon brand:** отсутствует в стандартном product/info; получается через отдельный `POST /v4/product/info/attributes` (attr_id=85).

**Ozon finance SKU lookup:** `finance items[]` содержит только `sku` + `name`, без `offer_id`. Lookup: `items[].sku → catalog sources[].sku → product_id → offer_id`.

## Ключевые таблицы PostgreSQL

| Группа | Таблицы |
|--------|---------|
| Tenancy | `tenant`, `workspace`, `app_user`, `workspace_member`, `workspace_invitation` |
| Integration | `marketplace_connection`, `secret_reference`, `marketplace_sync_state`, `integration_call_log` |
| Catalog | `product_master`, `seller_sku`, `marketplace_offer`, `cost_profile` |
| Canonical State | `canonical_price_snapshot`, `canonical_stock_snapshot` |
| Canonical Flow | `canonical_order`, `canonical_sale`, `canonical_return`, `canonical_finance_entry` |
| Pricing | `price_policy`, `price_policy_assignment`, `price_decision`, `price_action`, `manual_price_lock` |
| Execution | `job_execution`, `job_item`, `outbox_event`, `price_action_attempt`, `simulated_offer_state` |
| Operations | `saved_view`, `working_queue_definition`, `working_queue_assignment` |
| Audit | `alert_rule`, `alert_event`, `audit_log` |

### Tenancy

| Таблица | Назначение | Ключевые поля |
|---------|------------|---------------|
| `tenant` | Юрлицо / организация; контейнер для workspace-ов | name, slug, status, owner_user_id |
| `workspace` | Операционное пространство (бренд, команда); **граница изоляции данных** | tenant_id (FK), name, slug, status, owner_user_id |
| `app_user` | Глобальный пользователь системы | email (unique), name, status |
| `workspace_member` | Членство пользователя в workspace с ролью | workspace_id (FK), user_id (FK), role (enum), status; unique (workspace_id, user_id) |
| `workspace_invitation` | Приглашение пользователя в workspace | workspace_id (FK), email, role, status, token_hash, expires_at, invited_by_user_id |

Роли (enum в `workspace_member.role`): `OWNER`, `ADMIN`, `PRICING_MANAGER`, `OPERATOR`, `ANALYST`, `VIEWER`. Owner обязан иметь запись в `workspace_member`; `workspace.owner_user_id` — denormalized pointer для удобства. `PRICING_MANAGER` — роль для управления ценообразованием (policy config, approval, auto-execution) без полного admin-доступа.

Tenant — организационный контейнер (юрлицо, биллинг), не граница данных. Workspace — граница изоляции данных. Все бизнес-сущности привязаны к workspace напрямую или транзитивно.

### Integration

| Таблица | Назначение | Ключевые поля |
|---------|------------|---------------|
| `marketplace_connection` | Подключение к кабинету маркетплейса | workspace_id (FK), marketplace_type (enum), name, status, secret_reference_id (FK), external_account_id, last_check_at, last_success_at, last_error_at, last_error_code |
| `secret_reference` | Ссылка на секрет в Vault (generic, не только для маркетплейсов) | workspace_id (FK), provider, vault_path, vault_key, vault_version, secret_type (enum), status |
| `marketplace_sync_state` | Состояние синхронизации per connection/domain | marketplace_connection_id (FK), data_domain, last_sync_at, last_success_at, next_scheduled_at, status |
| `integration_call_log` | Журнал вызовов к API маркетплейсов (observability) | marketplace_connection_id (FK), endpoint, http_status, duration_ms, correlation_id |

`marketplace_connection` — бизнес-сущность (что подключено, каков health). `secret_reference` — инфраструктурный concern (как достать credentials из Vault). Разделение предотвращает попадание секретов в основную таблицу.

### Canonical State / Canonical Flow

| Таблица | Категория | Назначение |
|---------|-----------|------------|
| `canonical_price_snapshot` | State | Текущая цена по marketplace_offer; pricing pipeline читает напрямую |
| `canonical_stock_snapshot` | State | Текущие остатки по marketplace_offer и складу; pricing pipeline читает напрямую |
| `canonical_order` | Flow | Заказ/отправление; пишется в PostgreSQL, аналитика — из ClickHouse |
| `canonical_sale` | Flow | Продажа; пишется в PostgreSQL, аналитика — из ClickHouse |
| `canonical_return` | Flow | Возврат; пишется в PostgreSQL, аналитика — из ClickHouse |
| `canonical_finance_entry` | Flow | Финансовая операция; пишется в PostgreSQL, аналитика — из ClickHouse |

### Operations

| Таблица | Назначение |
|---------|------------|
| `saved_view` | Персональные пресеты фильтров и сортировок для operational grid |
| `working_queue_definition` | Правила очереди: filter criteria, название, тип |
| `working_queue_assignment` | Назначение элемента в очереди оператору: entity_type, entity_id, assigned_to_user_id, status |

## Design decisions — resolved issues (2026-03-30)

### K-1: revenue_gross → revenue_amount — RESOLVED

**Проблема:** Нет определённого маппинга для WB revenue_gross.
**Решение:** Переименовано в `revenue_amount`. Семантика: seller-facing price (до комиссий МП, после скидок).
- WB: `retail_price_withdisc_rub` (DD-13)
- Ozon: `accruals_for_sale` (DD-11)
Подробный анализ отвергнутых кандидатов — в секции "revenue_amount" выше.

### K-2: P&L формула — RESOLVED

**Проблема:** Формула не покрывала storage_fee, deduction, acceptance.
**Решение:** Добавлены отдельные measures: `storage_cost_amount`, `acceptance_cost_amount`. `deduction` → `penalties_amount`. Формула P&L обновлена до 13 компонентов.

### K-3: Двойное вычитание возвратов — RESOLVED

**Проблема:** Если spine включает sales+returns, refund_amount вычитается дважды.
**Решение:** Revenue spine включает ТОЛЬКО sales-операции (`OperationAgentDeliveredToCustomer` / `doc_type_name = 'Продажа'`). `refund_amount` агрегируется ОТДЕЛЬНО из возвратных операций. Правило задокументировано в секции "refund_amount — правила агрегации".

### K-4: seller_discount_amount — RESOLVED

**Проблема:** Нет источника данных для seller_discount_amount.
**Решение:** Measure удалён. Скидки уже учтены в `revenue_amount`. Ни Ozon, ни WB не предоставляют отдельное поле "сумма скидки продавца" в финансовых отчётах.

### K-5: WB CanonicalSale из finance — RESOLVED

**Проблема:** Маппинг из WB finance report → CanonicalSale не документирован.
**Решение:** Полный маппинг добавлен в mapping-spec.md секция 5 "WB → CanonicalSale (from reportDetailByPeriod)". DD-12 фиксирует, что P&L строится из finance, а не из sales endpoint.

### G-1: Ozon services classification — RESOLVED

**Проблема:** Нет таблицы маппинга service name → fact_finance measure.
**Решение:** 12 service names верифицированы по реальным данным (Jan 2025, 7590 ops) и задокументированы в mapping-spec.md секция 7 "Ozon services[].name → fact_finance measure classification".

### G-2: Ozon categories API — RESOLVED

**Проблема:** API endpoint для дерева категорий не документирован.
**Решение:** `POST /v1/description-category/tree` с `{"language":"DEFAULT"}`. Возвращает иерархию: `description_category_id` + `category_name` + `children[]`. Join: `product.description_category_id` → `tree.description_category_id`. Задокументирован в ozon-read-contracts.md.

### G-3: fact_supply (WB incomes) — RESOLVED (2026-03-30)

**Проблема:** WB old incomes API `/api/v1/supplier/incomes` deprecated (отключается June 2026).
**Статус:** Endpoint **РАБОТАЕТ** — verified на sandbox (120 rows test data).

**Решение:**
1. **Phase A/B (до June 2026)**: использовать `GET /api/v1/supplier/incomes` — контракт полностью задокументирован в `wb-read-contracts.md §8`
2. **Post-deprecation (after June 2026)**:
   - FBS: `GET /api/v3/supplies` (Marketplace API)
   - FBO: нет replacement → manual import или inventory delta analysis
3. **Sandbox data confirms fields**: `incomeId`, `supplierArticle`, `nmId`, `quantity`, `totalPrice`, `warehouseName`, `status`, `date`, `dateClose`
4. **SUPPLY_FACT event**: добавлен в pipeline → `fact_supply`

**Mapping**: `NormalizedSupplyItem` → `fact_supply` задокументирован в `wb-read-contracts.md §8`

### G-4: Naming — cost_profile vs fact_product_cost — RESOLVED

**Проблема:** Три разных имени для одной концепции COGS.
**Решение:** Единый поток данных:
1. `cost_profile` (PostgreSQL, canonical state) — SCD2 таблица себестоимости, привязана к `seller_sku`
2. `fact_product_cost` (ClickHouse, analytics) — материализация из `cost_profile` для COGS join
3. `custom_expense_entry` — **не существует** как отдельная таблица. Имелась в виду `cost_profile`. Имя `custom_expense_entry` удалено из всех документов.

### G-5: Ozon acquiring join — RESOLVED (DD-15)

**Проблема:** Acquiring транзакции не содержат `posting_number` → нельзя аллоцировать на заказ.
**Решение:** Acquiring операции ИМЕЮТ `posting_number`, но в формате `order_number` (без суффикса `-N`). Верифицировано эмпирически: sale posting "0151413710-0012-1" → acquiring posting "0151413710-0012". Join: strip последний `-N` суффикс от posting_number. Детали — DD-15 в mapping-spec.md.

### G-6: WB SPP — RESOLVED (DD-14)

**Проблема:** SPP не учтён в P&L формуле.
**Решение:** SPP — скидка WB для покупателя, оплачиваемая WB. Продавец получает на основе pre-SPP цены (`retail_price_withdisc_rub`). SPP не уменьшает revenue продавца → не нужен как отдельный компонент P&L. Детали — DD-14 в mapping-spec.md.

### G-7: dim_warehouse для WB — RESOLVED (2026-03-30)

**Проблема:** Ранее считалось, что нет source API для списка складов WB.
**Статус:** WB Marketplace API имеет `/api/v3/warehouses` (seller warehouses для FBS), но не имеет endpoint для списка WB FBO складов. FBO склады можно извлечь из:
- Finance report: `office_name` + `ppvz_office_id` + `ppvz_office_name`
- Statistics data: warehouse identifiers в orders/sales
**Решение:** Найден dedicated endpoint `GET /api/v3/offices` (Marketplace API).

1. **Primary source**: `GET /api/v3/offices` → полный список складов WB
   - Sandbox: 108 offices (включая международные CC)
   - Production: 225 offices (реальные склады: Коледино-2, Коледино Плюс, Краснодар и др.)
   - Fields: `id`, `name`, `address`, `city`, `longitude`, `latitude`, `cargoType`, `deliveryType`, `selected`
2. **Seller warehouses** (FBS): `GET /api/v3/warehouses` — склады продавца
3. **Join keys**:
   - Finance report `ppvz_office_id` → `offices.id` (exact match)
   - Finance report `office_name` → `offices.name` (text match)
   - Sales/Incomes `warehouseName` → `offices.name` (text match)
4. **WAREHOUSE_DICT event**: заполняется из `/api/v3/offices` → `dim_warehouse`

Контракт полностью задокументирован в `wb-read-contracts.md §9`.

### N-1: Sorting key для безордерных операций — RESOLVED

**Проблема:** С sorting key `(account_id, source_platform, order_id, finance_date)` безордерные операции одного дня сливаются.
**Решение:** Sorting key изменён на `(account_id, source_platform, operation_id, finance_date)`. `operation_id` уникален для каждой финансовой записи (Ozon: `operation_id`, WB: `rrd_id`). `posting_id` — дополнительное поле для группировки, не часть sorting key.

### N-2: mart_order_pnl spine — RESOLVED

**Проблема:** Dual-driver spine (fact_finance + fact_sales) может привести к двойному подсчёту.
**Решение:** `mart_order_pnl` строится из `fact_finance` как единственного source of truth для P&L. `fact_sales` используется ТОЛЬКО для операционных метрик (кол-во, timing), НЕ для revenue. Revenue берётся из `fact_finance.revenue_amount`. Dual-driver устранён.

### N-3: CanonicalSale.saleAmount vs accruals_for_sale — RESOLVED

**Проблема:** Для Ozon posting price=103 vs accruals_for_sale=157 → разные значения.
**Решение:** Эти значения для РАЗНЫХ товаров/количеств. Для ОДНОГО товара: `financial_data.products[].price` = `accruals_for_sale` при qty=1 (верифицировано: posting price=99, accruals_for_sale=99). При qty>1: `accruals_for_sale` = `price × quantity`. Discrepancy в примере была вызвана сравнением разных postings. `CanonicalSale.saleAmount` = `financial_data.products[].price` (из posting) для per-item, `accruals_for_sale` (из finance) для per-posting total.

## Связанные документы

- [Целевая архитектура](target-architecture.md) — store responsibilities, architecture principles
- [Функциональные возможности](functional-capabilities.md) — P&L, inventory intelligence
- [Матрица возможностей провайдеров](provider-capability-matrix.md) — покрытие по data domains
- [Исполнение и сверка](execution-and-reconciliation.md) — action state в модели данных
- [Архитектура ценообразования](pricing-architecture-analysis.md) — pricing pipeline, strategies, policy model
