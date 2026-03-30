# Целевая data-архитектура v2 — инженерное ядро, каноническая модель, минимальный scope

**Статус:** draft
**Дата:** 2026-03-30
**Контекст:** Синтез всей текущей архитектурной документации. Отделение инженерного ядра от исторических компромиссов, извлечение канонической аналитической модели, проектирование минимально достаточной целевой архитектуры под Phase A/B.

---

## Часть 1. Инженерное ядро — что правильно и почему

Текущая архитектура содержит девять фундаментальных инженерных решений, которые являются **доменно обоснованными**, а не историческими компромиссами. Каждое из них решает конкретную проблему multi-marketplace аналитики и должно быть перенесено в реализацию as-is.

### 1.1. Четырёхслойный pipeline

```
API маркетплейсов → Raw (S3) → Normalized (in-process) → Canonical (PostgreSQL) → Analytics (ClickHouse)
```

**Почему ядро:** каждый слой отвечает на свой вопрос. Raw — «что пришло?». Normalized — «что это значит в терминах провайдера?». Canonical — «что это значит в бизнес-терминах?». Analytics — «как это агрегировать для решений?». Пропуск любого слоя — архитектурная ошибка. Паттерн соответствует medallion architecture (bronze → silver → gold) и data vault.

**Инвариант:** пропуск стадий запрещён. Бизнес-логика работает только с canonical и analytics. Raw обеспечивает replay без повторных API-вызовов.

### 1.2. Canonical State vs Canonical Flow

| Категория | Семантика | Хранилище | Примеры |
|-----------|-----------|-----------|---------|
| **State** | Текущее состояние сущностей; читается напрямую для decisions | PostgreSQL (read + write) | Каталог, цены, остатки, себестоимость |
| **Flow** | Поток событий; пишется в PostgreSQL, аналитика из ClickHouse | PostgreSQL (write) → ClickHouse (read) | Заказы, продажи, возвраты, финансы |

**Почему ядро:** разделение доменно обосновано. State поддерживает operational reads (pricing pipeline читает текущую цену). Flow поддерживает аналитику (P&L за период). Оба нуждаются в PostgreSQL как source of truth, но паттерн чтения принципиально разный.

### 1.3. Трёхуровневая каталожная иерархия

```
product_master (внутренний товар, cross-marketplace)
  └── seller_sku (артикул продавца; один product_master → N SKU)
        └── marketplace_offer (предложение на конкретном маркетплейсе)
              ├── WB: nmID, vendorCode
              └── Ozon: product_id, offer_id, sku
```

**Почему ядро:** обеспечивает cross-marketplace P&L по товару. Себестоимость привязана к `seller_sku` (cost одинакова вне зависимости от маркетплейса). `marketplace_offer` хранит marketplace-specific identifiers. Без `product_master` невозможно ответить на вопрос «сколько я заработал на товаре X суммарно по WB и Ozon».

### 1.4. fact_finance как единственный консолидированный финансовый факт

**Почему ядро:** один запрос — один P&L. Все 13 финансовых компонентов (revenue, commission, acquiring, logistics, storage, penalties, acceptance, marketing, other charges, refund, compensation, net_payout, residual) — measures одной таблицы. Материализуется напрямую из `canonical_finance_entry`, без промежуточных component facts, без spine pattern.

### 1.5. SCD2 для себестоимости

`cost_profile` с `valid_from` / `valid_to`, привязка COGS к моменту продажи через `sale_ts`. Стандартный паттерн — продажа в январе учитывает январскую себестоимость, даже если в марте она изменилась.

### 1.6. Reconciliation residual как явная метрика

```
reconciliation_residual = net_payout − Σ(все компоненты P&L)
```

Признаёт, что маппинг провайдерских данных неполный (новые типы операций, SPP-компенсация WB). Отслеживает расхождения вместо того, чтобы их скрывать. Стабильный residual — норма. Резкое изменение — anomaly.

### 1.7. Знаковая нормализация на границе adapter

WB (все значения положительные, семантика по имени поля) и Ozon (signed values) нормализуются к единой конвенции (`positive = credit, negative = debit`) при ingestion. Canonical layer не знает о provider-specific конвенциях.

### 1.8. Streaming processing для large payloads

Temp file → S3 → streaming JSON parse с batch processing (500 records). Memory footprint: O(batch_size), не O(payload_size). WB finance 300 MB → 1.5 MB в heap.

### 1.9. DB-first + Transactional Outbox

Критическое состояние фиксируется в PostgreSQL до внешних действий. Retry truth — в PostgreSQL. RabbitMQ — только transport/delay через DLX/TTL. Потеря in-flight message не приводит к потере business state.

---

## Часть 2. Исторические компромиссы — что удалено и почему

Текущая документация уже прошла цикл архитектурной санации (`pnl-architecture-sanitation.md`). Ниже — итоговый реестр компромиссов с финальным статусом.

### 2.1. Устранённые компромиссы (уже отражены в документации)

| Компромисс | Суть проблемы | Решение | Документ |
|------------|---------------|---------|----------|
| Компонентные fact-таблицы | `fact_commission`, `fact_logistics_costs`, `fact_marketing_costs`, `fact_penalties` дублировали measures в `fact_finance` | Удалены. Данные — measures в `fact_finance` | pnl-sanitation §4.1 |
| Spine pattern | Ложная индирекция: fact_finance собирался из component facts, хотя все данные из одного источника | Удалён. Прямая материализация из `canonical_finance_entry` | pnl-sanitation §4.2 |
| Нейминг `mart_order_pnl` | Grain = posting, не покупательский заказ | Переименован в `mart_posting_pnl` | pnl-sanitation §4.3 |
| Зависимость P&L от fact_orders | P&L строился на финансовых данных, не на заказах | Убрана. P&L → fact_finance | pnl-sanitation §4.6 |
| `seller_discount_amount` | Нет источника данных; скидки уже учтены в revenue | Measure удалён | data-architecture K-4 |
| `revenue_gross` naming | Не является "gross" в бухгалтерском смысле | Переименован в `revenue_amount` | data-architecture K-1 |

### 2.2. Принятые компромиссы (осознанно оставлены)

| Компромисс | Суть | Почему принят | Severity |
|------------|------|---------------|----------|
| Mixed grain в fact_finance | Order-linked (posting) и standalone (operation) в одной таблице | Standalone операций мало (~30/мес). Разделение на две таблицы — overengineering | Низкая |
| WB sales из finance, Ozon из postings | Разные ETL events для одного факта | Обусловлено различием provider API. Grain одинаковый, timing разный | Низкая |
| fact_supply без потребителей | WB-only, не участвует в P&L | Отложен на Phase G. API контракт задокументирован | Не применимо |
| P&L без рекламных расходов | advertising_cost = 0 в Phase A/B/C | Phase G добавит ads ingestion. UI предупреждение. Не дефект — phased delivery | R-13 |

### 2.3. Что запрещено переносить в реализацию

1. **Компонентные fact-таблицы** (fact_commission и др.) — поглощены fact_finance
2. **Spine pattern** для fact_finance — прямая материализация из canonical_finance_entry
3. **Зависимость P&L-витрин от fact_orders** — P&L = fact_finance
4. **fact_supply в Phase A/B** — нет потребителей
5. **dim_promo_campaign, fact_promo_product в Phase A/B** — promo pipeline не реализован
6. **mart_promo_product_analysis** — Phase F/G

---

## Часть 3. Каноническая аналитическая модель

Каноническая модель — единственная абстракция, с которой работает бизнес-логика. Она стирает различия между маркетплейсами и обеспечивает добавление нового маркетплейса без изменения аналитики, pricing и UI.

### 3.1. Канонические сущности (PostgreSQL)

#### State-сущности (текущее состояние)

| Сущность | Таблицы | Grain | Назначение |
|----------|---------|-------|------------|
| **CanonicalOffer** | `product_master` + `seller_sku` + `marketplace_offer` | marketplace_offer | Товарное предложение на конкретном маркетплейсе. Абстракция над WB (nmID, vendorCode) и Ozon (product_id, offer_id) |
| **CanonicalPriceSnapshot** | `canonical_price_snapshot` | marketplace_offer × captured_at | Текущая цена. Pricing pipeline читает напрямую |
| **CanonicalStockSnapshot** | `canonical_stock_snapshot` | marketplace_offer × warehouse × captured_at | Текущие остатки по складам |
| **CostProfile** | `cost_profile` | seller_sku × validity_period (SCD2) | Себестоимость единицы. Привязана к seller_sku (cross-marketplace) |

#### Flow-сущности (поток событий)

| Сущность | Таблица | Grain | Назначение |
|----------|---------|-------|------------|
| **CanonicalOrder** | `canonical_order` | posting_id | Заказ/отправление |
| **CanonicalSale** | `canonical_sale` | posting_id × product | Факт продажи с количеством (необходим для COGS) |
| **CanonicalReturn** | `canonical_return` | return_id | Факт возврата с причиной |
| **CanonicalFinanceEntry** | `canonical_finance_entry` | operation_id | Атомарная финансовая операция. Source of truth для fact_finance |

### 3.2. Провайдерские маппинги (сводка)

| Каноническая сущность | WB source | Ozon source | Ключ связи |
|-----------------------|-----------|-------------|------------|
| CanonicalOffer | `/content/v2/get/cards/list` | `/v3/product/info/list` + `/v4/product/info/attributes` (brand) | vendorCode = offer_id → seller_sku |
| CanonicalPriceSnapshot | `/api/v2/list/goods/filter` | `/v5/product/info/prices` | nmID / product_id |
| CanonicalStockSnapshot | warehouse stocks endpoint | `/v4/product/info/stocks` | nmID / offer_id |
| CanonicalOrder | `/api/v1/supplier/orders` | `/v3/posting/fbo/list` | srid / posting_number |
| CanonicalSale | `reportDetailByPeriod` (doc_type=Продажа) | delivered postings (composite) | srid / posting_number |
| CanonicalReturn | `reportDetailByPeriod` (doc_type=Возврат) + `goods-return` | `/v1/returns/list` | srid / return_id |
| CanonicalFinanceEntry | `reportDetailByPeriod` | `/v3/finance/transaction/list` | rrd_id / operation_id |

### 3.3. Ключи связывания (join keys)

```
WB:
  nmID ↔ nm_id (finance) — marketplace product ID
  vendorCode = supplierArticle = sa_name — seller's SKU
  srid — links orders ↔ sales ↔ finance rows

Ozon:
  product_id — marketplace product ID
  offer_id — seller's SKU
  sku — Ozon system SKU (used in finance items[])
  posting_number — links orders ↔ sales ↔ returns ↔ finance
  operation_id — unique finance operation ID

Acquiring join (Ozon DD-15):
  Strip last "-N" from posting_number → order_number
  "0151413710-0012-1" → "0151413710-0012"

SKU lookup (Ozon finance):
  items[].sku → catalog sources[].sku → product_id → offer_id
```

### 3.4. Star schema (ClickHouse)

#### Dimensions

| Dimension | Grain | Source | Phase |
|-----------|-------|--------|-------|
| **dim_product** | (account_id, source_platform, product_id) | product_master + seller_sku + marketplace_offer | A |
| **dim_category** | (category_id) | Provider category APIs | A |
| **dim_warehouse** | (warehouse_id, source_platform) | WB `/api/v3/offices`, Ozon warehouse IDs | A |

#### Facts — P&L-critical

| Fact | Grain | Назначение в P&L | Source | Phase |
|------|-------|-------------------|--------|-------|
| **fact_finance** | (account_id, source_platform, operation_id, finance_date) | Все финансовые компоненты | canonical_finance_entry → direct materialization | A/B |
| **fact_sales** | (account_id, source_platform, posting_id, product_id) | Quantity для COGS | canonical_sale | A/B |
| **fact_product_cost** | (product_id, valid_from, valid_to) | Unit cost для COGS | cost_profile (SCD2) | A/B |

#### Facts — operational/analytical

| Fact | Grain | Назначение | Phase |
|------|-------|------------|-------|
| **fact_orders** | (account_id, source_platform, posting_id, order_date) | Order funnel, conversion | A/B |
| **fact_returns** | (account_id, source_platform, return_id) | Return rate, причины | A/B |
| **fact_price_snapshot** | (account_id, product_id, captured_at) | Ценовая история | A/B |
| **fact_inventory_snapshot** | (account_id, product_id, warehouse_id, captured_at) | Inventory intelligence | B |
| **fact_advertising_costs** | (account_id, source_platform, campaign_id, product_id, date) | Ad allocation | **G** |

#### Marts

| Mart | Grain | Зависимости | Phase |
|------|-------|-------------|-------|
| **mart_posting_pnl** | (account_id, source_platform, posting_id, date) | fact_finance, fact_sales, fact_product_cost | B |
| **mart_product_pnl** | (account_id, source_platform, product_id, period) | mart_posting_pnl + fact_finance (standalone ops) | B |
| **mart_inventory_analysis** | (account_id, product_id, warehouse_id) | fact_inventory_snapshot, fact_sales, fact_product_cost | B |
| **mart_returns_analysis** | (account_id, product_id) | fact_returns, fact_finance (penalties), fact_sales | B |

### 3.5. Формула P&L

```
posting_pnl = revenue_amount
            − marketplace_commission
            − acquiring_commission
            − logistics_cost
            − storage_cost
            − penalties
            − acceptance_cost
            − marketing_cost
            − other_marketplace_charges
            − refund_amount
            + compensation
            − COGS (qty × unit_cost, SCD2)

product_pnl = Σ(posting_pnl for product)
            + product-level ops (packaging, disposal via items[].sku)

account_pnl = Σ(product_pnl)
            + account_level_charges (storage, subscriptions, compensation without SKU)
```

**Source of truth:** fact_finance (финансовые компоненты) + fact_sales × fact_product_cost (COGS).

**Advertising:** = 0 в Phase A/B/C. Phase G добавит fact_advertising_costs с pro-rata allocation.

### 3.6. fact_finance — measures (полный перечень)

| Measure | Ozon source | WB source |
|---------|-------------|-----------|
| `revenue_amount` | `accruals_for_sale` из `OperationAgentDeliveredToCustomer` | `retail_price_withdisc_rub` из строк `doc_type_name = 'Продажа'` |
| `marketplace_commission_amount` | `sale_commission` + `MarketplaceServiceBrandCommission` | `ppvz_sales_commission` |
| `acquiring_commission_amount` | `MarketplaceRedistributionOfAcquiringOperation` (join по order_number) | `acquiring_fee` |
| `logistics_cost_amount` | Σ logistics services (8 service names) | `delivery_rub` + `rebill_logistic_cost` |
| `storage_cost_amount` | `OperationMarketplaceServiceStorage` | `storage_fee` |
| `penalties_amount` | `DisposalReason*` operations | `penalty` + `deduction` |
| `marketing_cost_amount` | `MarketplaceSaleReviewsOperation` | — |
| `acceptance_cost_amount` | — | `acceptance` |
| `other_marketplace_charges_amount` | `StarsMembership` + `OperationElectronicServiceStencil` | — |
| `compensation_amount` | `MarketplaceSellerCompensationOperation` + `AccrualInternalClaim` + `AccrualWithoutDocs` | `additional_payment` |
| `refund_amount` | `ClientReturnAgentOperation` → accruals_for_sale < 0 | Строки `doc_type_name = 'Возврат'` |
| `net_payout` | Σ `amount` по операциям | `ppvz_for_pay` |
| `reconciliation_residual` | net_payout − Σ(компоненты) | net_payout − Σ(компоненты) |
| `product_id` (nullable) | items[].sku → catalog lookup | nm_id (всегда заполнен) |

---

## Часть 4. Минимально достаточная целевая архитектура (Phase A/B)

### 4.1. Scope Phase A (Foundation)

**Цель:** tenancy, интеграция, canonical truth.

```
Tenancy & Access
  └── tenant → workspace → app_user → workspace_member → workspace_invitation

Marketplace Integration
  └── marketplace_connection → secret_reference → marketplace_sync_state

Data Pipeline (9 ETL events)
  └── WAREHOUSE_DICT ─────┐
      CATEGORY_DICT ──────┤
                          ├→ PRODUCT_DICT ──→ SALES_FACT ──→ FACT_FINANCE
                          │       │
                          │       ├→ PRICE_SNAPSHOT
                          │       └→ INVENTORY_FACT
                          │
                          └→ (ADVERTISING_FACT — Phase G)

Raw Layer
  └── S3 + job_item (index)

Canonical Layer (PostgreSQL)
  └── State: product_master, seller_sku, marketplace_offer, canonical_price_snapshot,
             canonical_stock_snapshot, cost_profile
  └── Flow: canonical_order, canonical_sale, canonical_return, canonical_finance_entry

Analytics Layer (ClickHouse) — initial dims only
  └── dim_product, dim_category, dim_warehouse
```

#### Phase A deliverables (конкретный перечень)

| Deliverable | Что создаётся | Зависимости |
|-------------|---------------|-------------|
| Tenancy tables | `tenant`, `workspace`, `app_user`, `workspace_member`, `workspace_invitation` | — |
| Integration tables | `marketplace_connection`, `secret_reference`, `marketplace_sync_state`, `integration_call_log` | Tenancy |
| Execution tables | `job_execution`, `job_item`, `outbox_event` | Integration |
| Raw layer | S3 bucket + streaming write/read, `job_item` as index | MinIO |
| Catalog canonical | `product_master`, `seller_sku`, `marketplace_offer` | — |
| Cost canonical | `cost_profile` (SCD2) | Catalog |
| State canonical | `canonical_price_snapshot`, `canonical_stock_snapshot` | Catalog |
| Flow canonical | `canonical_order`, `canonical_sale`, `canonical_return`, `canonical_finance_entry` | Catalog |
| Dimensions | `dim_product`, `dim_category`, `dim_warehouse` | Canonical catalog |
| ETL pipeline | 7 events: WAREHOUSE_DICT, CATEGORY_DICT, PRODUCT_DICT, PRICE_SNAPSHOT, SALES_FACT, INVENTORY_FACT, FACT_FINANCE | All above |
| Adapters | WB adapter (catalog, prices, orders, sales, finance, offices), Ozon adapter (catalog, prices, stocks, orders, postings, returns, finance, categories) | Provider contracts |

### 4.2. Scope Phase B (Trust Analytics)

**Цель:** правдивая аналитика.

```
Analytics Layer (ClickHouse) — facts + marts
  ├── Facts:
  │   ├── fact_finance (direct materialization from canonical_finance_entry)
  │   ├── fact_sales
  │   ├── fact_orders
  │   ├── fact_returns
  │   ├── fact_product_cost (from cost_profile SCD2)
  │   ├── fact_price_snapshot
  │   └── fact_inventory_snapshot
  │
  └── Marts:
      ├── mart_posting_pnl (P&L per posting)
      ├── mart_product_pnl (P&L per product per period)
      ├── mart_inventory_analysis (days of cover, stock-out risk)
      └── mart_returns_analysis (return rate, penalty breakdown)
```

#### Phase B deliverables

| Deliverable | Что создаётся | Зависимости |
|-------------|---------------|-------------|
| fact_finance | ReplacingMergeTree, sorting key: (account_id, source_platform, operation_id, finance_date) | canonical_finance_entry |
| fact_sales | Из canonical_sale | canonical_sale |
| fact_orders | Из canonical_order | canonical_order |
| fact_returns | Из canonical_return | canonical_return |
| fact_product_cost | Из cost_profile (SCD2) | cost_profile |
| fact_price_snapshot | Из canonical_price_snapshot | canonical_price_snapshot |
| fact_inventory_snapshot | Из canonical_stock_snapshot | canonical_stock_snapshot |
| mart_posting_pnl | fact_finance + fact_sales (qty) + fact_product_cost (COGS) | All P&L facts |
| mart_product_pnl | mart_posting_pnl + fact_finance (standalone ops) | mart_posting_pnl |
| mart_inventory_analysis | fact_inventory_snapshot + fact_sales + fact_product_cost | Inventory facts |
| mart_returns_analysis | fact_returns + fact_finance (penalties) + fact_sales | Returns/finance facts |
| Materializers | Per-domain materializers: canonical → ClickHouse | All canonical tables |
| Anomaly controls | Stale data detection, missing sync, residual tracking | marketplace_sync_state, fact_finance |

### 4.3. Что НЕ входит в Phase A/B

| Что | Почему | Phase |
|-----|--------|-------|
| fact_advertising_costs | Ads API не подключены | G |
| fact_supply (WB incomes) | Нет потребителей; API deprecated June 2026 | G |
| dim_promo_campaign, fact_promo_product | Promo pipeline не реализован | F |
| mart_promo_product_analysis | Нет data dependency | F/G |
| Pricing pipeline | Требует confirmed P&L truth | C |
| Execution lifecycle | Требует manual approval flow | D |
| Seller Operations UI | Требует working analytics | E |
| Simulated execution | Требует parity tests | F |

### 4.4. Граф зависимостей (Phase A/B)

```
                    ┌─────────────────────────────────────────┐
                    │  INFRASTRUCTURE                         │
                    │  PostgreSQL, ClickHouse, MinIO, RabbitMQ│
                    └──────────────┬──────────────────────────┘
                                   │
          ┌────────────────────────┼────────────────────────┐
          │                        │                        │
    ┌─────▼──────┐         ┌──────▼───────┐         ┌──────▼──────┐
    │  Tenancy   │         │  Raw Layer   │         │  Analytics  │
    │  & Access  │         │  (S3+index)  │         │  (ClickHouse)│
    └─────┬──────┘         └──────┬───────┘         └──────▲──────┘
          │                       │                        │
    ┌─────▼──────┐         ┌──────▼───────┐         ┌──────┤
    │ Integration│         │  Normalized  │         │  Materializers
    │ (connections│         │  (in-process)│         │      │
    │  sync state)│        └──────┬───────┘         └──────┤
    └─────┬──────┘                │                        │
          │                ┌──────▼───────┐                │
          └───────────────►│  Canonical   ├────────────────┘
                           │  (PostgreSQL)│
                           │  State + Flow│
                           └──────────────┘
```

### 4.5. Runtime entrypoints (Phase A/B)

| Entrypoint | Ответственность в Phase A/B |
|------------|----------------------------|
| `datapulse-api` | REST API: tenancy CRUD, connection management, sync triggers, canonical reads, analytics reads |
| `datapulse-ingest-worker` | Data pipeline: fetch → raw → normalize → canonicalize → materialize |

`datapulse-pricing-worker` и `datapulse-executor-worker` — Phase C/D.

### 4.6. Потоки данных (Phase A/B)

#### Ingestion flow (per ETL event)

```
1. Scheduler / manual trigger
   → INSERT job_execution (PostgreSQL)
   → INSERT outbox_event (ETL_STEP_EXECUTE)

2. Outbox poller → RabbitMQ → ingest-worker

3. Worker: fetch
   → HTTP request to marketplace API
   → streaming write to temp file (64 KB chunks, SHA-256, cursor extraction)
   → S3 putObject (from temp file)
   → INSERT job_item (s3_key, sha256, byte_size, status=CAPTURED)
   → delete temp file
   → if more pages: INSERT outbox_event (next page)

4. Worker: normalize + canonicalize
   → S3 getObject → streaming JSON parse (batch=500)
   → per batch:
       → deserialize to NormalizedDTO
       → sign normalization (WB debit fields × -1)
       → timestamp normalization
       → resolve canonical offer (sellerSku → marketplace_offer)
       → UPSERT canonical tables (IS DISTINCT FROM, no-churn)

5. Worker: materialize (canonical → ClickHouse)
   → read canonical tables
   → transform to star schema (dim/fact)
   → INSERT into ClickHouse (ReplacingMergeTree)

6. Update job_execution status, marketplace_sync_state
```

#### Materialization flow (canonical → analytics)

```
canonical_finance_entry (PostgreSQL)
  → Materializer: group by posting_number, aggregate measures
  → INSERT INTO fact_finance (ClickHouse)

canonical_sale → fact_sales
canonical_order → fact_orders
canonical_return → fact_returns
cost_profile → fact_product_cost
canonical_price_snapshot → fact_price_snapshot
canonical_stock_snapshot → fact_inventory_snapshot

fact_finance + fact_sales + fact_product_cost → mart_posting_pnl
mart_posting_pnl + fact_finance (standalone) → mart_product_pnl
fact_inventory_snapshot + fact_sales + fact_product_cost → mart_inventory_analysis
fact_returns + fact_finance + fact_sales → mart_returns_analysis
```

### 4.7. Ключевые таблицы PostgreSQL (Phase A/B)

| Группа | Таблицы | Count |
|--------|---------|-------|
| Tenancy | `tenant`, `workspace`, `app_user`, `workspace_member`, `workspace_invitation` | 5 |
| Integration | `marketplace_connection`, `secret_reference`, `marketplace_sync_state`, `integration_call_log` | 4 |
| Catalog | `product_master`, `seller_sku`, `marketplace_offer`, `cost_profile` | 4 |
| Canonical State | `canonical_price_snapshot`, `canonical_stock_snapshot` | 2 |
| Canonical Flow | `canonical_order`, `canonical_sale`, `canonical_return`, `canonical_finance_entry` | 4 |
| Execution | `job_execution`, `job_item`, `outbox_event` | 3 |
| Audit | `audit_log` | 1 |
| **Total** | | **23** |

### 4.8. Ключевые таблицы ClickHouse (Phase A/B)

| Группа | Таблицы | Count |
|--------|---------|-------|
| Dimensions | `dim_product`, `dim_category`, `dim_warehouse` | 3 |
| Facts | `fact_finance`, `fact_sales`, `fact_orders`, `fact_returns`, `fact_product_cost`, `fact_price_snapshot`, `fact_inventory_snapshot` | 7 |
| Marts | `mart_posting_pnl`, `mart_product_pnl`, `mart_inventory_analysis`, `mart_returns_analysis` | 4 |
| **Total** | | **14** |

---

## Часть 5. Инварианты и guard rails

### 5.1. Архитектурные инварианты (нарушение = build failure)

| # | Инвариант |
|---|-----------|
| 1 | Pipeline строго последовательный: Raw → Normalized → Canonical → Analytics. Пропуск стадий запрещён |
| 2 | Бизнес-логика работает только с canonical и analytics. Прямой доступ к raw/normalized — запрещён |
| 3 | PostgreSQL — единственный source of truth для business state |
| 4 | ClickHouse — read-only для бизнес-логики; не хранит action lifecycle, retries, reconciliation |
| 5 | Provider DTO не протекают за границу adapter. Canonical layer = marketplace-agnostic |
| 6 | Каждая каноническая запись прослеживаема до raw source через job_item.s3_key |
| 7 | fact_finance материализуется напрямую из canonical_finance_entry, без промежуточных facts |
| 8 | UPSERT с `IS DISTINCT FROM` — no-churn при неизменённых данных |

### 5.2. Data quality controls (Phase B)

| Control | Что проверяет | Реакция |
|---------|---------------|---------|
| Stale data | `marketplace_sync_state.last_success_at` > threshold | Alert + block automation |
| Missing sync | Ожидаемый sync не произошёл по расписанию | Alert |
| Residual spike | `reconciliation_residual` > threshold (резкое изменение) | Alert + investigation |
| Spike detection | Аномальные всплески в финансовых метриках | Alert |

### 5.3. Phasing rules

| Правило | Обоснование |
|---------|-------------|
| Phase B не начинается без confirmed canonical truth (Phase A) | Mart на некорректных данных — хуже, чем отсутствие mart |
| Phase C не начинается без confirmed P&L truth (Phase B) | Pricing на неверных сигналах — хуже, чем отсутствие pricing |
| Phase D не начинается без manual approval flow (Phase C) | Auto-execution без approval — опасно |
| fact_advertising_costs = 0 до Phase G | P&L корректен структурно, но неполон. UI предупреждение |

---

## Часть 6. Что готово для реализации

### 6.1. Provider contract readiness

| Capability | WB | Ozon | Blocker |
|------------|-----|------|---------|
| Catalog | READY (sandbox) | READY | — |
| Prices | READY (sandbox) | READY | — |
| Stocks | PARTIAL (no warehouse data in sandbox) | READY | WB: verify on production |
| Orders | READY (sandbox) | READY | — |
| Sales | READY (sandbox) | READY (composite) | — |
| Returns | READY | READY | — |
| Finance | READY (sandbox + docs) | READY | — |
| Warehouses | READY (sandbox + production) | READY | — |
| Categories | — (WB: subjectName in catalog) | READY (v1/description-category/tree) | — |

### 6.2. Design decisions — все resolved

Все 22 design decisions (DD-1 через DD-16 + K-1 через K-5 + G-1 через G-7 + N-1 через N-3) resolved и задокументированы. Нет открытых архитектурных вопросов для Phase A/B.

### 6.3. Риски, требующие внимания при реализации

| Риск | Impact | Митигация |
|------|--------|-----------|
| R-01: Ломающие изменения API | Pipeline ломается | Capability-based adapter boundary; `@JsonIgnoreProperties(ignoreUnknown = true)` |
| R-02: Rate limiting | Устаревание данных | Token-bucket; DLX retry; lane isolation |
| R-04: Корректность P&L | Некорректные финансы | Sign conventions verified; reconciliation residual; golden datasets |
| R-10: Timestamp formats | Ошибки парсинга | Dual-format parser (WB DD-9); custom format parser (Ozon DD-6) |

---

## Связанные документы

- [Видение и границы](project-vision-and-scope.md) — scope, фазы, constraints
- [Целевая архитектура](target-architecture.md) — модули, bounded contexts, store roles
- [Архитектура данных](data-architecture.md) — полная модель данных, формула P&L, join keys
- [S3 Raw Layer](s3-raw-layer-architecture.md) — streaming write/read, memory footprint
- [Санация P&L](pnl-architecture-sanitation.md) — удалённые компромиссы, resolved questions
- [Pricing](pricing-architecture-analysis.md) — Phase C architecture
- [Execution](execution-and-reconciliation.md) — Phase D lifecycle
- [Mapping Spec](provider-contracts/mapping-spec.md) — field-level provider mappings
- [Risk Register](risk-register.md) — risks and mitigations
