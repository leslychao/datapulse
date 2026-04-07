# Модуль: ETL Pipeline

**Фаза:** A — Foundation
**Зависимости:** [Tenancy & IAM](tenancy-iam.md), [Integration](integration.md)
**Runtime:** datapulse-ingest-worker

---

## Назначение

Загрузка данных из API Wildberries и Ozon, прохождение через четырёхступенчатый pipeline (Raw → Normalized → Canonical → Analytics), и сохранение в canonical model. Поддержка нескольких кабинетов одного продавца с изоляцией сбоев по маркетплейсам.

## Data domains

| Domain | Содержание |
|--------|------------|
| Каталог | Товары, SKU, бренды, категории, статусы |
| Цены | Текущие цены, скидки, ценовые иерархии |
| Остатки | По складам (FBO/FBS/seller), доступные и зарезервированные |
| Заказы | Отправления FBO/FBS, статусы |
| Продажи | Фактические продажи с привязкой к заказам |
| Возвраты | Возвраты, невыкупы, причины |
| Финансы | Транзакции, комиссии, логистика, компенсации, штрафы |
| Промо | Акции маркетплейсов, участие товаров |
| Реклама | Кампании, статистика (показы, клики, расход). Phase B extended |
| Поставки | Поставки на склады маркетплейсов (FBO/FBS). **Stub (Phase B).** WB only, см. G-3 |

## Обязательные свойства

- Идемпотентная загрузка (дедупликация по SHA-256 record key).
- Rate limit handling с retry и backoff.
- Lane isolation: сбой одного маркетплейса не блокирует другой.
- Сохранение исходных payload для traceability (raw layer).
- Валидация credentials перед началом синхронизации.
- Строго последовательный pipeline: raw → normalized → canonical → analytics.

## Pipeline layers

```
API маркетплейсов → Raw (S3) → Normalized (in-process) → Canonical (PostgreSQL) → Analytics (ClickHouse)
```

### Raw layer

| Свойство | Описание |
|----------|----------|
| Назначение | Immutable source-faithful хранилище; replay; forensic traceability |
| Хранилище | S3-compatible (MinIO) |
| Формат | Исходный JSON payload от API маркетплейса |
| Мутабельность | Immutable — записи не обновляются и не удаляются (кроме retention cleanup) |
| Идемпотентность | `content_sha256` хранится в `job_item` для content-addressable tracking. Raw layer сам по себе append-only (каждый sync создаёт новые `job_item` записи). Дедупликация данных — на canonical layer (UPSERT с `IS DISTINCT FROM`) |
| Dedup key (canonical) | Зависит от entity: `(connection_id, external_order_id)` для orders, `(marketplace_offer_id)` для prices, и т.д. (см. §UPSERT keys) |
| Гранулярность | Одна страница API = один S3 object |
| Index | `job_item` в PostgreSQL (без payload) — связь raw → execution context |
| Retention | Finance: 12 мес (audit); state: keep_count=3; flow: 6 мес |

### Normalized layer

| Свойство | Описание |
|----------|----------|
| Назначение | Типизированное представление данных провайдера; ещё не бизнес-модель |
| Хранилище | In-process (десериализованные provider DTO) |
| Ответственность | Парсинг JSON, type coercion, нормализация timestamp, нормализация знаков |
| Mapping version | Привязка к версии контракта провайдера |

### Canonical layer

Каноническая модель — единая, унифицированная структура данных, в которую приводятся данные из всех маркетплейсов. Вся бизнес-логика (P&L, pricing, inventory) работает исключительно с канонической моделью.

| Свойство | Описание |
|----------|----------|
| Назначение | Marketplace-agnostic каноническая модель — основа для business computations |
| Хранилище | PostgreSQL (авторитетный) |
| Мутабельность | UPSERT с `IS DISTINCT FROM` (no-churn on unchanged rows) |
| Decision-grade | Current state — из canonical (PostgreSQL). Derived signals — из analytics (ClickHouse) через signal assembler |
| Provenance | Каждая запись содержит `job_execution_id` (FK) → drill-down до raw source через `job_item` |

#### Canonical State vs Canonical Flow

| Категория | Назначение | Хранилище | Примеры |
|-----------|------------|-----------|---------|
| **Canonical State** | Текущее состояние сущностей; pricing pipeline читает напрямую | PostgreSQL (read + write) | Каталог, текущие цены, текущие остатки, себестоимость |
| **Canonical Flow** | Транзакционный поток событий; пишется в PostgreSQL, аналитические агрегаты читаются из ClickHouse | PostgreSQL (write) → ClickHouse (read) | Заказы, продажи, возвраты, финансовые операции |

## S3 Raw Layer — Implementation Spec

### Write path: API → S3

HTTP response → temp file (disk, streaming 64 KB chunks) → SHA-256 digest → S3 putObject → INSERT job_item → delete temp file.

**Memory footprint записи: 64 KB** вне зависимости от размера response (WB finance до 300 MB).

Temp file выбран вместо streaming напрямую в S3 (`putObject` требует Content-Length) и вместо multipart upload (сложнее, нельзя re-read при ошибке).

#### S3 key structure

```
s3://{bucket}/raw/{connection_id}/{event}/{source_id}/{request_id}/page-{N}.json
```

| Компонент | Назначение |
|-----------|------------|
| `raw/` | Prefix для raw layer |
| `{connection_id}` | `marketplace_connection.id` — isolation per marketplace cabinet |
| `{event}` | Тип ETL event |
| `{source_id}` | Конкретный source (класс адаптера) |
| `{request_id}` | UUID конкретного ETL run |
| `page-{N}.json` | Номер страницы пагинации |

### Read path: S3 → Normalization

Стратегия write-then-read: сначала пишем в S3, потом читаем оттуда для нормализации. Соответствует принципу DB-first. При crash — retry из S3 без повторного API call.

Streaming JSON parse через Jackson streaming API (batch=500 records, memory ~1.5 MB).

### Cursor extraction

Четыре семейства cursor extraction определяют, как из ответа API извлекается маркер следующей страницы:

| Семейство | Endpoints | Стратегия | Overhead |
|-----------|-----------|-----------|----------|
| 1: No cursor (offset-based) | WB Prices/Stocks/Promo offset, Ozon FBO/FBS/Promo offset | `NoCursorExtractor` — пагинация через арифметический offset | 0 |
| 2: String cursor (last_id) | Ozon Product List, Ozon Prices, Ozon Stocks, Ozon Attributes, Ozon Returns | `JsonPathCursorExtractor` — парсит temp file post-write | < 1 ms |
| 3: Composite cursor (WB Catalog) | WB Catalog (`/content/v2/get/cards/list`) | `WbCatalogCursorExtractor` — извлекает `updatedAt`, `nmID`, `total` | < 1 ms |
| 4: Data-derived cursor (WB Finance) | WB Finance (`reportDetailByPeriod`) | `TailFieldExtractor` — читает tail 32 KB, последний `rrd_id` | < 1 ms |

### Pagination orchestration

Два marketplace-agnostic класса в `adapter/util/` централизуют pagination loop:

| Класс | Паттерн | Safety guards | Используется |
|-------|---------|---------------|--------------|
| `OffsetPagedCapture` | offset/limit | SHA-256 duplicate detection, max page cap, small-page threshold | WB Prices, WB Stocks, WB Promo, Ozon FBO, Ozon FBS, Ozon Promo products/candidates |
| `CursorPagedCapture` | opaque string cursor | Non-advancing cursor detection (same cursor = stop), null/empty = stop | Ozon Product List, Ozon Prices, Ozon Stocks, Ozon Attributes, Ozon Returns |

Два адаптера используют собственный pagination loop из-за уникальных termination conditions:

| Адаптер | Причина собственного loop |
|---------|--------------------------|
| `WbCatalogReadAdapter` | Составной cursor (`updatedAt` + `nmID`), termination по `cursor.total < limit` |
| `WbFinanceReadAdapter` | `rrdid` cursor + HTTP 204 empty response termination + `byteSize < 10` filter |

Post-write extraction (Вариант B) — единый write path для всех endpoints, cursor extraction — отдельная фаза.

### Retention

| Тип данных | Retention | Обоснование |
|------------|-----------|-------------|
| Raw finance (FACT_FINANCE) | 12 месяцев | Audit, reconciliation investigation |
| Raw state snapshots | keep_count=3 | Достаточно для replay; state перезаписывается |
| Raw flow (orders/sales) | 6 месяцев | Forensics; replay при изменении маппинга |

### Memory footprint по провайдерам

| Provider/Domain | Размер page | Heap memory (write) | Peak memory (read, batch=500) |
|-----------------|-------------|---------------------|-------------------------------|
| WB Finance | 200-300 MB | **64 KB** | **1.5 MB** |
| WB Catalog | 50-200 KB | **64 KB** | **0.5-1 MB** |
| Ozon Finance | 50-100 KB | **64 KB** | **0.5-1.5 MB** |
| Ozon Catalog | 100-500 KB | **64 KB** | **1-2.5 MB** |

### Индексная таблица `job_item`

| Поле | Тип | Назначение |
|------|-----|------------|
| `id` | BIGSERIAL | PK |
| `job_execution_id` | BIGINT FK | Привязка к ETL run |
| `request_id` | VARCHAR(64) | UUID ETL run |
| `source_id` | VARCHAR(128) | Класс source |
| `page_number` | INT | Номер страницы |
| `s3_key` | VARCHAR(512) | Полный S3 key |
| `record_count` | INT | Записей на странице |
| `content_sha256` | VARCHAR(64) | SHA-256 для дедупликации |
| `byte_size` | BIGINT | Размер payload |
| `status` | VARCHAR(32) | Lifecycle status (см. ниже) |
| `captured_at` | TIMESTAMPTZ | Время захвата |
| `processed_at` | TIMESTAMPTZ | Время завершения normalization (nullable) |

### job_item status lifecycle

```
CAPTURED → PROCESSED → EXPIRED
         → FAILED
```

| Переход | Условие | Guard |
|---------|---------|-------|
| → CAPTURED | S3 putObject успешен | INSERT (initial status) |
| CAPTURED → PROCESSED | Normalization + canonical UPSERT завершены | `UPDATE ... WHERE id = ? AND status = 'CAPTURED'` |
| CAPTURED → FAILED | Normalization failed (parsing error, validation error) | `UPDATE ... WHERE id = ? AND status = 'CAPTURED'` |
| PROCESSED → EXPIRED | Retention cleanup: S3 object удалён | Scheduled job: `WHERE status = 'PROCESSED' AND captured_at < retention_threshold` |

**FAILED items:** не блокируют processing остальных items в `job_execution`. При наличии FAILED items → `job_execution` завершается как `COMPLETED_WITH_ERRORS`. FAILED items логируются с `error_details` для forensic investigation. Recovery: re-run sync (новый job_execution) перезагрузит данные.

### Риски raw layer

| ID | Риск | Severity | Митигация |
|----|------|----------|-----------|
| R-S3-01 | MinIO недоступен | HIGH | Health check; fallback buffer to local temp |
| R-S3-02 | Объём хранилища | MEDIUM | Retention policies; S3 lifecycle; мониторинг |
| R-CAP-01 | DataBuffer memory leak (WebClient) | HIGH | `try-finally` с `DataBufferUtils.release()` |
| R-CAP-03 | HTTP response truncation | HIGH | WebClient signal completion; JSON validation |
| R-CAP-05 | Cursor extraction failure | MEDIUM | Mark CAPTURED + alert; forensic из S3 |
| R-CAP-07 | Concurrent temp file disk pressure | MEDIUM | Pre-check disk space; семафор на source type |

## Канонические сущности

| Сущность | Категория | Назначение | Ключевые поля |
|----------|-----------|------------|---------------|
| `CanonicalOffer` (product_master, seller_sku, marketplace_offer) | State | Товарное предложение | sellerSku, marketplaceSku, name, brand, category, status |
| `CanonicalPriceCurrent` | State | Текущая цена (latest per offer) | price, discountPrice, currency, capturedAt |
| `CanonicalStockCurrent` | State | Текущие остатки (latest per offer × warehouse) | available, reserved, warehouseId |
| `Category` | Dict | Категория маркетплейса | name, parentCategoryId, externalCategoryId |
| `Warehouse` | Dict | Склад | name, warehouseType (FBO/FBS/SELLER), externalWarehouseId |
| `CanonicalOrder` | Flow | Заказ/отправление | externalOrderId, quantity, pricePerUnit, status |
| `CanonicalSale` | Flow | Продажа | saleAmount, commission |
| `CanonicalReturn` | Flow | Возврат | returnAmount, returnReason, returnDate |
| `CanonicalFinanceEntry` | Flow | Финансовая операция | id (PK), connection_id (FK marketplace_connection), source_platform, posting_id, order_id, seller_sku_id, entryType, amount (нормализованный знак), entryDate, job_execution_id (FK) |
| `CanonicalPromoCampaign` | State | Акция маркетплейса | external_promo_id, promo_name, promo_type, status, date_from, date_to |
| `CanonicalPromoProduct` | State | Участие товара в акции | participation_status, required_price, current_price |
| `cost_profile` | State (SCD2) | Себестоимость SKU (ручной ввод) | seller_sku_id, cost_price, valid_from, valid_to |

### Canonical DDL

```sql
product_master:
  id                      BIGSERIAL PK
  workspace_id            BIGINT FK → workspace              NOT NULL
  external_code           VARCHAR(120) NOT NULL               -- cross-marketplace product code (seller's internal code)
  name                    VARCHAR(500)
  brand                   VARCHAR(255)
  job_execution_id        BIGINT FK → job_execution           NOT NULL
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (workspace_id, external_code)

seller_sku:
  id                      BIGSERIAL PK
  product_master_id       BIGINT FK → product_master          NOT NULL
  sku_code                VARCHAR(120) NOT NULL               -- seller's article (vendorCode WB, offer_id Ozon)
  barcode                 VARCHAR(120)
  job_execution_id        BIGINT FK → job_execution           NOT NULL
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (product_master_id, sku_code)

marketplace_offer:
  id                          BIGSERIAL PK
  seller_sku_id               BIGINT FK → seller_sku             NOT NULL
  marketplace_connection_id   BIGINT FK → marketplace_connection NOT NULL
  marketplace_sku             VARCHAR(120) NOT NULL              -- marketplace-specific ID (nmID WB, product_id Ozon)
  marketplace_sku_alt         VARCHAR(120)                       -- secondary ID (sku Ozon, imtID WB)
  name                        VARCHAR(500)
  category_id                 BIGINT FK → category               (nullable)
  status                      VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE, ARCHIVED, BLOCKED
  url                         VARCHAR(1000)
  image_url                   VARCHAR(1000)
  job_execution_id            BIGINT FK → job_execution          NOT NULL
  created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (seller_sku_id, marketplace_connection_id, marketplace_sku)

canonical_price_current:
  id                      BIGSERIAL PK
  marketplace_offer_id    BIGINT FK → marketplace_offer       NOT NULL UNIQUE
  price                   DECIMAL NOT NULL                    -- base price (before discount)
  discount_price          DECIMAL                             -- final price after discount (nullable — no discount)
  discount_pct            DECIMAL                             -- discount percentage (nullable)
  currency                VARCHAR(3) NOT NULL DEFAULT 'RUB'
  min_price               DECIMAL                             -- marketplace min price constraint (nullable)
  max_price               DECIMAL                             -- marketplace max price constraint (nullable)
  job_execution_id        BIGINT FK → job_execution           NOT NULL
  captured_at             TIMESTAMPTZ NOT NULL                -- when price was fetched from marketplace
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()

canonical_stock_current:
  id                          BIGSERIAL PK
  marketplace_offer_id        BIGINT FK → marketplace_offer   NOT NULL
  warehouse_id                BIGINT FK → warehouse           NOT NULL
  available                   INT NOT NULL DEFAULT 0
  reserved                    INT NOT NULL DEFAULT 0
  job_execution_id            BIGINT FK → job_execution       NOT NULL
  captured_at                 TIMESTAMPTZ NOT NULL
  updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (marketplace_offer_id, warehouse_id)

canonical_order:
  id                      BIGSERIAL PK
  connection_id           BIGINT FK → marketplace_connection  NOT NULL
  source_platform         VARCHAR(10) NOT NULL                -- 'ozon' / 'wb'
  external_order_id       VARCHAR(120) NOT NULL
  marketplace_offer_id    BIGINT FK → marketplace_offer       (nullable — SKU lookup miss)
  order_date              TIMESTAMPTZ NOT NULL
  quantity                INT NOT NULL
  price_per_unit          DECIMAL NOT NULL
  total_amount            DECIMAL                             -- quantity × price_per_unit (computed at normalization)
  currency                VARCHAR(3) NOT NULL DEFAULT 'RUB'
  status                  VARCHAR(30) NOT NULL                -- PENDING, DELIVERED, CANCELLED, RETURNED
  fulfillment_type        VARCHAR(10)                         -- FBO, FBS
  region                  VARCHAR(255)
  job_execution_id        BIGINT FK → job_execution           NOT NULL
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (connection_id, external_order_id)

canonical_sale:
  id                      BIGSERIAL PK
  connection_id           BIGINT FK → marketplace_connection  NOT NULL
  source_platform         VARCHAR(10) NOT NULL                -- 'ozon' / 'wb'
  external_sale_id        VARCHAR(120) NOT NULL
  canonical_order_id      BIGINT FK → canonical_order         (nullable)
  marketplace_offer_id    BIGINT FK → marketplace_offer       (nullable)
  posting_id              VARCHAR(120)                        -- posting_number (Ozon) / srid (WB); join key к fact_finance
  seller_sku_id           BIGINT FK → seller_sku              (nullable — SKU lookup miss)
  sale_date               TIMESTAMPTZ NOT NULL
  sale_amount             DECIMAL NOT NULL
  commission              DECIMAL
  quantity                INT NOT NULL DEFAULT 1
  currency                VARCHAR(3) NOT NULL DEFAULT 'RUB'
  job_execution_id        BIGINT FK → job_execution           NOT NULL
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (connection_id, external_sale_id)

canonical_return:
  id                      BIGSERIAL PK
  connection_id           BIGINT FK → marketplace_connection  NOT NULL
  source_platform         VARCHAR(10) NOT NULL                -- 'ozon' / 'wb'
  external_return_id      VARCHAR(120) NOT NULL
  canonical_order_id      BIGINT FK → canonical_order         (nullable)
  marketplace_offer_id    BIGINT FK → marketplace_offer       (nullable)
  seller_sku_id           BIGINT FK → seller_sku              (nullable — SKU lookup miss)
  return_date             TIMESTAMPTZ NOT NULL
  return_amount           DECIMAL NOT NULL
  fulfillment_type        VARCHAR(10)                         -- 'FBW' (WB), NULL (Ozon — unified API, no delivery_schema)
  return_reason           VARCHAR(255)                        -- provider-specific reason (nullable — WB no reason)
  quantity                INT NOT NULL DEFAULT 1
  status                  VARCHAR(30)                         -- PENDING, COMPLETED (nullable)
  currency                VARCHAR(3) NOT NULL DEFAULT 'RUB'
  job_execution_id        BIGINT FK → job_execution           NOT NULL
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (connection_id, external_return_id)

canonical_finance_entry:
  id                      BIGSERIAL PK
  connection_id           BIGINT FK → marketplace_connection  NOT NULL
  source_platform         VARCHAR(10) NOT NULL                -- 'ozon' / 'wb'
  external_entry_id       VARCHAR(120) NOT NULL               -- rrd_id (WB), operation_id (Ozon)
  entry_type              VARCHAR(60) NOT NULL                -- SALE_ACCRUAL, RETURN_REVERSAL, MARKETPLACE_COMMISSION, DELIVERY, STORAGE, PENALTY, etc. (full taxonomy in analytics-pnl.md §entry_type taxonomy)
  posting_id              VARCHAR(120)                        -- posting_number / srid (nullable — standalone ops)
  order_id                VARCHAR(120)                        -- order group ID (nullable)
  seller_sku_id           BIGINT FK → seller_sku              (nullable — SKU lookup miss)
  warehouse_id            BIGINT FK → warehouse               (nullable — populated from WB ppvz_office_id; NULL for Ozon and non-warehouse ops)

  -- Per-measure columns (composite row model, DD-8 in mapping-spec).
  -- Signs: positive = credit to seller, negative = debit from seller.
  -- WB: one reportDetailByPeriod row → all applicable measures populated from separate fields.
  -- Ozon: one operation → accruals_for_sale, sale_commission, services[] decomposed into measures.
  -- Non-applicable measures = 0.
  revenue_amount                    DECIMAL NOT NULL DEFAULT 0   -- seller-facing price (WB: retail_price_withdisc_rub; Ozon: accruals_for_sale). Positive for sales
  marketplace_commission_amount     DECIMAL NOT NULL DEFAULT 0   -- marketplace commission. Negative for sales, positive for return refunds
  acquiring_commission_amount       DECIMAL NOT NULL DEFAULT 0   -- acquiring fee. Negative
  logistics_cost_amount             DECIMAL NOT NULL DEFAULT 0   -- delivery, reverse logistics, last mile. Negative
  storage_cost_amount               DECIMAL NOT NULL DEFAULT 0   -- storage fees. Negative
  penalties_amount                  DECIMAL NOT NULL DEFAULT 0   -- penalties, deductions. Negative
  acceptance_cost_amount            DECIMAL NOT NULL DEFAULT 0   -- acceptance fees. Negative
  marketing_cost_amount             DECIMAL NOT NULL DEFAULT 0   -- marketplace marketing services (not ads). Negative
  other_marketplace_charges_amount  DECIMAL NOT NULL DEFAULT 0   -- packaging, labeling, disposal, other. Negative
  compensation_amount               DECIMAL NOT NULL DEFAULT 0   -- marketplace compensations. Positive
  refund_amount                     DECIMAL NOT NULL DEFAULT 0   -- revenue reversal on returns. Negative (debit to seller)
  net_payout                        DECIMAL                      -- seller payout (ppvz_for_pay WB, operation.amount Ozon)

  currency                VARCHAR(3) NOT NULL DEFAULT 'RUB'
  entry_date              TIMESTAMPTZ NOT NULL                -- operation date from provider
  attribution_level       VARCHAR(10) NOT NULL                -- POSTING, PRODUCT, ACCOUNT (computed by normalizer at INSERT based on posting_id/order_id/seller_sku_id presence)
  job_execution_id        BIGINT FK → job_execution           NOT NULL
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (connection_id, source_platform, external_entry_id)

cost_profile:
  id                      BIGSERIAL PK
  seller_sku_id           BIGINT FK → seller_sku              NOT NULL
  cost_price              DECIMAL NOT NULL                    -- per-unit cost
  currency                VARCHAR(3) NOT NULL DEFAULT 'RUB'
  valid_from              DATE NOT NULL
  valid_to                DATE                                -- NULL = current version
  updated_by_user_id      BIGINT FK → app_user               NOT NULL
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (seller_sku_id, valid_from)
```

### Canonical UPSERT keys

Каждая каноническая сущность использует UPSERT с `IS DISTINCT FROM` (no-churn). Ниже — уникальные бизнес-ключи для `ON CONFLICT`.

| Сущность | UPSERT business key | Стратегия |
|----------|---------------------|-----------|
| `category` | `(marketplace_connection_id, external_category_id)` | UPSERT |
| `warehouse` | `(marketplace_connection_id, external_warehouse_id)` | UPSERT |
| `product_master` | `(workspace_id, external_code)` | UPSERT |
| `seller_sku` | `(product_master_id, sku_code)` | UPSERT |
| `marketplace_offer` | `(seller_sku_id, marketplace_connection_id, marketplace_sku)` | UPSERT |
| `canonical_price_current` | `(marketplace_offer_id)` | UPSERT (latest state) |
| `canonical_stock_current` | `(marketplace_offer_id, warehouse_id)` | UPSERT (latest state) |
| `canonical_order` | `(connection_id, external_order_id)` | UPSERT |
| `canonical_sale` | `(connection_id, external_sale_id)` | UPSERT |
| `canonical_return` | `(connection_id, external_return_id)` | UPSERT |
| `canonical_finance_entry` | `(connection_id, source_platform, external_entry_id)` | UPSERT |
| `canonical_promo_campaign` | `(connection_id, external_promo_id)` | UPSERT |
| `canonical_promo_product` | `(canonical_promo_campaign_id, marketplace_offer_id)` | UPSERT |
| `cost_profile` | `(seller_sku_id, valid_from)` | SCD2 |

`external_*_id` — provider-specific идентификатор, уникальный в рамках connection. Для WB finance: `rrd_id`; для Ozon finance: `operation_id`.

**Finance corrections и идемпотентность:** провайдеры WB и Ozon не мутируют существующие finance entries — корректировки создаются как **новые записи** с новыми `rrd_id` (WB) / `operation_id` (Ozon) и противоположным знаком или специальным `entry_type`. Поэтому UPSERT по `(connection_id, source_platform, external_entry_id)` с `IS DISTINCT FROM` корректно обрабатывает все сценарии:
- **Повторная загрузка** (overlap window) → `IS DISTINCT FROM` отсекает unchanged rows (no-churn)
- **Новая корректировка** → новый `external_entry_id` → INSERT (не конфликтует с исходной записью)
- **Гипотетическая мутация провайдером** (не документирована, но возможна) → UPSERT обновит row, `IS DISTINCT FROM` сработает только если данные изменились

**Naming convention для marketplace identifier:** dictionary tables (`category`, `warehouse`) используют поле `marketplace_type`, flow entities (`canonical_order`, `canonical_sale`, `canonical_return`, `canonical_finance_entry`, `canonical_promo_campaign`) — `source_platform`. Оба содержат одинаковые значения (`'ozon'` / `'wb'`). Разделение намеренное: `marketplace_type` — атрибут справочника (к какому маркетплейсу относится), `source_platform` — denormalized provenance field (откуда пришла запись).

### Связи каталожных сущностей

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

**`product_master.external_code` mapping (Phase A):** `external_code` = `seller_sku.sku_code` (= vendorCode WB, offer_id Ozon). Это даёт 1:1 связь product_master ↔ seller_sku на старте. Кросс-маркетплейсное объединение товаров (когда один физический товар продаётся на WB и Ozon под разными артикулами) — scope Phase C: ручной merge через UI или auto-match по barcode/name.

### Dictionary tables: category, warehouse

```
category:
  id                          BIGSERIAL PK
  marketplace_connection_id   BIGINT FK → marketplace_connection     NOT NULL
  external_category_id        VARCHAR(120) NOT NULL                  -- provider-specific ID
  name                        VARCHAR(500) NOT NULL
  parent_category_id          BIGINT FK → category                  (nullable — root categories)
  marketplace_type            VARCHAR(10) NOT NULL                   -- 'ozon' / 'wb'
  job_execution_id            BIGINT FK → job_execution              NOT NULL
  created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (marketplace_connection_id, external_category_id)
```

```
warehouse:
  id                          BIGSERIAL PK
  marketplace_connection_id   BIGINT FK → marketplace_connection     NOT NULL
  external_warehouse_id       VARCHAR(120) NOT NULL                  -- provider-specific ID
  name                        VARCHAR(500) NOT NULL
  warehouse_type              VARCHAR(20) NOT NULL                   -- FBO, FBS, SELLER
  marketplace_type            VARCHAR(10) NOT NULL                   -- 'ozon' / 'wb'
  job_execution_id            BIGINT FK → job_execution              NOT NULL
  created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (marketplace_connection_id, external_warehouse_id)
```

**Назначение в PostgreSQL:**
- `category` — используется для pricing policy assignments (scope_type = CATEGORY), фильтрации в Seller Operations grid.
- `warehouse` — FK для `canonical_stock_current.warehouse_id`, фильтрация остатков по типам складов.
- Оба справочника — materialized в ClickHouse как `dim_category` / `dim_warehouse` для аналитики.

## Data domain → ETL event mapping

`marketplace_sync_state.data_domain` определяет, какие данные загружаются. ETL dependency graph определяет порядок обработки. Один ETL event может обслуживать несколько data domains, и одна fetch-операция может порождать записи в нескольких canonical таблицах.

| data_domain | ETL event(s) | Canonical targets | Провайдер-специфика |
|-------------|-------------|-------------------|---------------------|
| `CATALOG` | `CATEGORY_DICT`, `WAREHOUSE_DICT`, `PRODUCT_DICT` | category, warehouse, product_master, seller_sku, marketplace_offer | — |
| `PRICES` | `PRICE_SNAPSHOT` | canonical_price_current | — |
| `STOCKS` | `INVENTORY_FACT` | canonical_stock_current | — |
| `ORDERS` | `SALES_FACT` | canonical_order | WB: orders endpoint; Ozon: postings endpoint |
| `SALES` | `SALES_FACT` | canonical_sale | WB: statistics sales endpoint; Ozon: через postings (FBO/FBS) |
| `RETURNS` | `SALES_FACT` | canonical_return | WB: analytics goods-return endpoint; Ozon: через returns/list |
| `FINANCE` | `FACT_FINANCE` | canonical_finance_entry | WB: reportDetailByPeriod; Ozon: finance/transaction/list |
| `PROMO` | `PROMO_SYNC` | canonical_promo_campaign, canonical_promo_product | WB: `WbPromoSyncSource` → calendar/promotions + nomenclatures; Ozon: `OzonPromoSyncSource` → actions + products/candidates |
| `ADVERTISING` | `ADVERTISING_FACT` | `canonical_advertising_campaign` (DD-AD-1 revised: partial canonical) | **Stub (Phase A).** `WbAdvertisingFactSource` / `OzonAdvertisingFactSource` registered as no-op stubs. Full spec: [Advertising](advertising.md) §ETL Pipeline |
| `SUPPLY` | `SUPPLY_FACT` | — (no canonical entity, see G-3) | **Stub (Phase B).** WB only. `WbSupplyFactSource` registered as no-op stub. Pending `/api/v1/supplier/incomes` deprecation June 2026 |

**Примечание**: WB sales и returns извлекаются из отдельных statistics/analytics endpoints (`/api/v1/supplier/sales` и `/api/v1/analytics/goods-return`) в рамках `SALES_FACT`, а не из `FACT_FINANCE`. `FACT_FINANCE` создаёт только `canonical_finance_entry`. Граф зависимостей (`FACT_FINANCE` depends on `SALES_FACT`) обеспечивает, что canonical orders/sales/returns доступны для SKU resolution при финансовой нормализации.

## Граф зависимостей ETL events

```
WAREHOUSE_DICT ──────────────────┐
                                 ├→ PRODUCT_DICT ──→ SALES_FACT ──→ FACT_FINANCE
CATEGORY_DICT ───────────────────┘       │
                                         ├→ PRICE_SNAPSHOT
                                         ├→ INVENTORY_FACT
                                         ├→ ADVERTISING_FACT
                                         ├→ PROMO_SYNC
                                         └→ SUPPLY_FACT (stub)
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
| `SUPPLY_FACT` | `PRODUCT_DICT`, `WAREHOUSE_DICT`. **Stub (no-op)** — см. G-3 |

## Материализация по доменам

| Domain | Event | Target tables (ClickHouse) | Target tables (PostgreSQL canonical) |
|--------|-------|---------------------------|--------------------------------------|
| Категории | `CATEGORY_DICT` | `dim_category` | `category` |
| Склады | `WAREHOUSE_DICT` | `dim_warehouse` | `warehouse` |
| Товары | `PRODUCT_DICT` | `dim_product` | `product_master`, `seller_sku`, `marketplace_offer` |
| Цены | `PRICE_SNAPSHOT` | `fact_price_snapshot` | `canonical_price_current` |
| Продажи (Ozon) | `SALES_FACT` | `fact_orders`, `fact_sales`, `fact_returns`, dim backfill | `canonical_order`, `canonical_sale`, `canonical_return` |
| Продажи (WB) | `SALES_FACT` | `fact_orders`, `fact_sales`, `fact_returns` | `canonical_order`, `canonical_sale`, `canonical_return` |
| Остатки | `INVENTORY_FACT` | `fact_inventory_snapshot` | `canonical_stock_current` |
| Финансы (Ozon) | `FACT_FINANCE` | `fact_finance` | `canonical_finance_entry` |
| Финансы (WB) | `FACT_FINANCE` | `fact_finance` | `canonical_finance_entry` |
| Реклама | `ADVERTISING_FACT` | `dim_advertising_campaign`, `fact_advertising` | `canonical_advertising_campaign` (DD-AD-1 revised: partial canonical — campaigns in PG, facts directly to CH). **Stub (Phase A).** Full spec: [Advertising](advertising.md) |
| Промо | `PROMO_SYNC` | `dim_promo_campaign`, `fact_promo_product` | `canonical_promo_campaign`, `canonical_promo_product`. Implemented |
| Поставки | `SUPPLY_FACT` | — | — (no canonical entity, see G-3). **Stub (Phase B).** WB only |

### Pipeline invariant exception: Advertising (DD-AD-1 revised)

> **Implementation status:** `ADVERTISING_FACT` EventSource зарегистрирован как **no-op stub** (`WbAdvertisingFactSource`, `OzonAdvertisingFactSource`). Полная спецификация реализации — [Advertising](advertising.md) §ETL Pipeline.

Advertising data (`ADVERTISING_FACT`) использует **частичный canonical слой**:

- **Кампании** (справочник) → PostgreSQL `canonical_advertising_campaign` (UPSERT) + ClickHouse `dim_advertising_campaign` (материализация). Это аналог промо-паттерна: `canonical_promo_campaign` (PG) → `dim_promo_campaign` (CH).
- **Факты** (статистика) → ClickHouse `fact_advertising` напрямую, без canonical в PostgreSQL. Исключение из инварианта «Raw → Canonical → Analytics» — рекламная статистика не является бизнес-состоянием.

**Обоснование partial canonical:** кампании — бизнес-состояние: алерты join-ят кампании с остатками в PostgreSQL, дашборд требует pagination/sorting, рекомендации join-ят с ценами/маржей. Факты — чистая аналитика с большим объёмом, агрегации выполняются в ClickHouse.

**Data provenance (facts):** обеспечивается через `job_execution_id` в `fact_advertising` (ClickHouse column). Drill-down: `fact_advertising.job_execution_id` → `job_item.s3_key` → S3 raw payload.

**Dedup:** Campaigns: PostgreSQL UPSERT с `ON CONFLICT (connection_id, external_campaign_id) DO UPDATE`. Facts: ReplacingMergeTree с `ver`. Dedup key: `(connection_id, source_platform, campaign_id, ad_date, marketplace_sku)`.

### Platform-specific правила

**WB sales/returns:** заполняются в обработчике `SALES_FACT` из отдельных statistics/analytics endpoints. `WbSalesFactSource` содержит три последовательных sub-source: (a) orders (`GET /api/v1/supplier/orders`) → `canonical_order`, (b) sales (`GET /api/v1/supplier/sales`) → `canonical_sale`, (c) returns (`GET /api/v1/analytics/goods-return`) → `canonical_return`. `FACT_FINANCE` (`WbFinanceFactSource`) создаёт **только** `canonical_finance_entry` из `reportDetailByPeriod`. Граф зависимостей (`FACT_FINANCE` depends on `SALES_FACT`) обеспечивает, что dim_product и canonical orders/sales/returns доступны для SKU resolution при финансовой нормализации.

**Graceful degradation:** зависимость `FACT_FINANCE → SALES_FACT` — мягкая (soft dependency). Если `SALES_FACT` упал, `FACT_FINANCE` может выполниться при условии, что `PRODUCT_DICT` успешен (основной источник dim_product). Отсутствие sales/returns от SALES_FACT не блокирует финансовую загрузку — finance entries сохраняются с SKU lookup по каталогу. Worker при failure SALES_FACT: логирует warning, продолжает с FACT_FINANCE. Job получает `COMPLETED_WITH_ERRORS`.

**Ozon brand:** отсутствует в стандартном product/info; получается через отдельный `POST /v4/product/info/attributes` (attr_id=85).

**Ozon finance SKU lookup:** `finance items[]` содержит только `sku` + `name`, без `offer_id`. Lookup: `items[].sku → catalog sources[].sku → product_id → offer_id`.

## Sign conventions

### Ozon

| Правило | Описание |
|---------|----------|
| Конвенция | `amount > 0` = кредит продавцу; `amount < 0` = дебет |
| В каноническую модель | Знак сохраняется as-is |

### Wildberries

| Правило | Описание |
|---------|----------|
| Конвенция | Все значения положительные; имя поля определяет credit/debit |
| Credit fields | `ppvz_for_pay`, `ppvz_vw`, `additional_payment` |
| Debit fields | `ppvz_sales_commission`, `acquiring_fee`, `delivery_rub`, `penalty`, `storage_fee`, `deduction`, `rebill_logistic_cost`, `acceptance` |
| В каноническую модель | Debit-поля умножаются на −1 при нормализации |

## Форматы timestamp

| Провайдер | Формат | Особенности |
|-----------|--------|-------------|
| Ozon (финансы) | `yyyy-MM-dd HH:mm:ss` | Не ISO 8601; timezone — **Moscow (UTC+3), empirically confirmed 2026-03-31** |
| WB (финансы) | Dual-format | date-only или ISO 8601; parser обязан поддерживать оба |
| Ozon (прочие) | ISO 8601 | Стандартный формат |

### Timezone normalization per domain

Все canonical timestamps хранятся как `TIMESTAMPTZ` (UTC). ClickHouse `Date` / `DateTime` используют Moscow TZ для бизнес-дат. Ниже — полная таблица нормализации:

| Domain | Провайдер | Source timestamp | Source TZ | Canonical conversion | Notes |
|--------|-----------|-----------------|-----------|---------------------|-------|
| Заказы | WB | `lastChangeDate` (ISO 8601) | UTC | `TIMESTAMPTZ` as-is | WB orders timestamps — UTC |
| Заказы | Ozon | `in_process_at`, `shipment_date` (ISO 8601) | UTC | `TIMESTAMPTZ` as-is | |
| Продажи | WB | `sale_dt` from finance (ISO 8601 / date-only) | UTC (ISO) / Moscow (date-only) | Dual-format parser: date-only → Moscow midnight → UTC (DD-9) | |
| Продажи | Ozon | `created_at` from posting (ISO 8601) | UTC | `TIMESTAMPTZ` as-is | |
| Возвраты | WB | `dt` from goods-return (ISO 8601) | UTC | `TIMESTAMPTZ` as-is | `completedDt` — аналогично, UTC |
| Возвраты | Ozon | `returned_moment` (ISO 8601) | UTC | `TIMESTAMPTZ` as-is | |
| Финансы | WB | `rr_dt` (dual-format) | UTC (ISO) / Moscow (date-only) | Dual-format parser (DD-9) | |
| Финансы | Ozon | `operation_date` (`yyyy-MM-dd HH:mm:ss`) | **Moscow (UTC+3)** | Parse as Moscow → convert to UTC | Empirically confirmed |
| Цены | WB / Ozon | `captured_at` (ingestion time) | UTC | `TIMESTAMPTZ` as-is | Set by ETL, not provider |
| Остатки | WB / Ozon | `captured_at` (ingestion time) | UTC | `TIMESTAMPTZ` as-is | Set by ETL, not provider |
| Каталог | WB | `updateAt` from cards (ISO 8601) | UTC | `TIMESTAMPTZ` as-is | |
| Каталог | Ozon | `created_at`, `updated_at` (ISO 8601) | UTC | `TIMESTAMPTZ` as-is | |
| Промо | WB / Ozon | `startDateTime`, `endDateTime` (ISO 8601) | UTC / Moscow | WB: Moscow → UTC; Ozon: UTC as-is | |

**ClickHouse materialization:** все `Date`-колонки в ClickHouse fact/dim tables хранят дату в **Moscow TZ** (`toDate(canonical_timestamp, 'Europe/Moscow')`). Reason: бизнес-отчёты ориентированы на московское время (рабочий день продавца).

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
posting_number связывает orders ↔ sales ↔ returns ↔ finance

ACQUIRING (DD-15, updated 2026-03-31):
  DUAL FORMAT: 57% order_number (2-part), 43% full posting_number (3-part)
  Join: exact match first → then strip-suffix match
  Пример: sale "39222582-0174-1" → acq "39222582-0174-1" (exact) ИЛИ acq "93284743-0263" (2-part)

STANDALONE (storage, disposal, compensation, CPC, promotions):
  posting_number = "" или numeric campaign/promo ID → ключ = operation_id
  Аллокация на заказ — pro-rata

Важно: finance items[] содержит только sku + name, без offer_id.
Lookup: items[].sku → catalog sources[].sku → product_id → offer_id
(Verified 2026-03-31: chain works end-to-end, sku=1595285688 → product_id=1074782997 → offer_id)
```

## Домен возвратов

Возвраты — единственный домен, где источники данных между маркетплейсами **принципиально различаются**.

| Провайдер | Источник | Что доступно | Что отсутствует |
|-----------|----------|--------------|-----------------|
| **Ozon** | `v1/returns/list` | ID, SKU, количество, цена, комиссия, причина, даты, статус, схема | — |
| **WB** | Finance report (`reportDetailByPeriod`), строки `doc_type_name = 'Возврат'` | Финансовые суммы, привязка к заказу через `srid` | Причина, статус, даты lifecycle |
| **WB** (доп.) | Analytics endpoint `goods-return` | Причина, тип, статусы, даты | Денежные суммы |

Для WB полная картина возврата требует объединения данных из двух источников.

### Возвраты по ETL events

| Провайдер | ETL Event | Причина |
|-----------|-----------|---------|
| **Ozon** | `SALES_FACT` | Ozon returns — часть sales-домена (через `POST /v1/returns/list`) |
| **WB** | `SALES_FACT` | WB returns загружаются через analytics endpoint `GET /api/v1/analytics/goods-return`. Финансовые суммы по возвратам — в `canonical_finance_entry` (через `FACT_FINANCE`) |

## Ingestion flow

### Sync scheduling

| Trigger | Описание | Scope |
|---------|----------|-------|
| Scheduled | Uniform interval per connection (current implementation: every 6 hours for all domains). **Target design:** configurable per-domain cron (4x/day finance, 2x/day catalog/prices/stocks) | Per marketplace_connection |
| Manual | Оператор запускает через UI / REST API | Per marketplace_connection |
| System (after connection creation) | Первичная полная загрузка при создании подключения | Per marketplace_connection |

**Scheduling model:** `marketplace_sync_state` хранит `next_scheduled_at` per connection. Spring `@Scheduled` job (configurable poll interval, default: 1 min) сканирует `WHERE next_scheduled_at <= now() AND enabled = true` → INSERT `job_execution` → update `next_scheduled_at`. **Current implementation:** `next_scheduled_at = now() + 6 hours` (uniform for all domains). **Target design:** per-domain cron expressions для разной частоты по типам данных. **Distributed lock:** `@SchedulerLock` (ShedLock) на scheduler job — обязателен для кластера. `lockAtMostFor = PT5M`.

**Sync scope per run:**

| Sync type | `event_type` в job_execution | Domains included | Когда |
|-----------|------------------------------|------------------|-------|
| `FULL_SYNC` | `FULL_SYNC` | Все узлы DAG (`DagDefinition`) | Initial load при активации подключения |
| `INCREMENTAL` | `INCREMENTAL` | Все узлы DAG (см. ниже про time-window) | Scheduled sync |
| `MANUAL_SYNC` | `MANUAL_SYNC` | Все узлы DAG, либо подмножество из `params.domains` + транзитивные hard-deps | Ручной запуск из UI/API |

**`job_execution.params` (JSONB):** источник правды для опций run (не только outbox payload). Примеры: `domains` — массив имён `EtlEventType` для частичного manual sync; `sourceJobId` + `trigger` — при retry из API. Пустой/NULL `params` — полный DAG для данного `event_type`.

**Incremental strategy per domain (target design):**

> **Current implementation:** окно fact-capture задаётся в `IngestSyncContextBuilder` и прокидывается в `IngestContext` (`wbFactDateFrom` / `wbFactDateTo`, `ozonFactSince` / `ozonFactTo`). **FULL_SYNC** и **MANUAL_SYNC:** старт = `now - full-fact-lookback-days` (default **365**, `datapulse.etl.ingest.full-fact-lookback-days`). **INCREMENTAL:** если у подключения есть `marketplace_sync_state.last_success_at`, старт = `max(last_success_at - fact-sync-overlap, now - incremental-fact-lookback-days)` (`fact-sync-overlap` default **PT1H**, cap **30** дней через `incremental-fact-lookback-days`); если успешной синхронизации ещё не было — тот же широкий интервал, что и FULL (365d по умолчанию). Часы: `java.time.Clock` (bean `ingestClock`). UPSERT с `IS DISTINCT FROM` сохраняет идемпотентность при overlap. `rrdid`-based cursor для WB finance и Ozon month chunking — **по-прежнему не реализованы** (см. ниже).

| Domain | Incremental strategy (target) | Cursor / pagination | Current impl |
|--------|------------------------------|---------------------|--------------|
| Каталог | Full scan (catalog small, no incremental API) | Offset-based / cursor pagination | Implemented |
| Цены | Full scan per connection (no incremental API) | Offset-based pagination | Implemented |
| Остатки | Full scan per connection (real-time state, no delta) | Offset-based pagination | Implemented |
| Заказы | Date-range: `updated_since = last_success_at - overlap_buffer` | Date filter + offset pagination | `IngestContext` fact window (см. выше) |
| Продажи | Date-range: `date_from = last_success_at - overlap_buffer` | Date filter + pagination | `IngestContext` fact window |
| Возвраты | Date-range: `last_change_date >= last_success_at - overlap_buffer` | Date filter + pagination | `IngestContext` fact window |
| Финансы (Ozon) | Date-range: `date >= last_success_at - overlap_buffer` | Cursor-based pagination | `IngestContext` fact window |
| Финансы (WB) | Date-range: `dateFrom = last_rrd_id based` | `rrdid`-based cursor (WB-specific) | `IngestContext` fact window (`dateFrom`/`dateTo` по календарю) |
| Промо | Full scan (promo list small per connection) | Offset-based pagination | Implemented (full scan per sync) |
| Реклама | Date-range: `date_from = last_success_at - overlap_buffer` | Date-based | Stub (Phase B) |

**`overlap_buffer` (target design):** configurable per domain (default: 2 hours). Overlapping window гарантирует, что late-arriving records не потеряются. UPSERT с `IS DISTINCT FROM` обеспечивает идемпотентность при повторной загрузке.

**Ozon finance date-range chunking (target design, not yet implemented):** API `/v3/finance/transaction/list` имеет ограничение — max 1 месяц между `date.from` и `date.to`. Если `last_success_at` старше 1 месяца (длительный outage, первая загрузка), Ozon-адаптер автоматически разбивает интервал на месячные чанки и выполняет N последовательных запросов. Каждый чанк обрабатывается с cursor-based pagination до исчерпания. Результаты всех чанков сохраняются в рамках одного `job_execution`.

**WB finance `rrdid`-based cursor (target design, not yet implemented):** для WB финансов `overlap_buffer` не применяется. Курсор `rrdid` (монотонно возрастающий ID записи) сохраняется в `marketplace_sync_state`. Каждый sync начинается с `rrdid = last_rrdid`. UPSERT по `external_entry_id` (= WB `rrd_id`) обеспечивает идемпотентность, включая ретроактивные корректировки.

**Deduplication:** Raw layer — append-only (каждый sync создаёт новые `job_item` записи; `content_sha256` хранится для content tracking и forensic comparison). Canonical layer — UPSERT с `IS DISTINCT FROM` обеспечивает no-churn для unchanged records и идемпотентность при overlap window.

### Trigger и dispatch

Один sync run = один `job_execution` = один outbox message = один RabbitMQ message. `job_execution` привязан к одному `connection_id` (= один кабинет одного маркетплейса).

**Разделение ответственности:**
- **Scheduler и outbox** работают на уровне connection целиком. Они не знают про отдельные ETL events (CATEGORY_DICT, PRODUCT_DICT и т.д.).
- **Worker** — единственный компонент, который знает про граф зависимостей events. Получив задачу «синхронизируй connection #5», worker сам раскладывает её на events и определяет порядок выполнения.

```
1. Scheduler / manual trigger → INSERT job_execution (PENDING) → INSERT outbox_event
2. Outbox poller → RabbitMQ → ingest-worker picks up message
3. Worker: CAS job_execution PENDING → IN_PROGRESS
```

Далее в worker: выполнение DAG, затем фаза завершения (§Completion) — при успешном исходе ingest **`IN_PROGRESS` → `MATERIALIZING` → терминальный статус**, outbox `ETL_SYNC_COMPLETED` только после финального CAS (см. §job_execution lifecycle).

Параллелизм между маркетплейсами — естественный: разные connections порождают разные `job_execution`, разные RabbitMQ messages, которые обрабатываются разными worker-ами (или одним — по очереди). Lane isolation работает автоматически: сбой WB API не влияет на Ozon sync, т.к. это полностью независимые job_execution.

### Processing (per ETL event, inside worker)

Worker обходит dependency graph (§Граф зависимостей) с **level-based параллелизмом**: events на одном уровне графа выполняются параллельно, между уровнями — barrier (ожидание завершения всех events текущего уровня).

**Execution levels:**

```
Level 0:  CATEGORY_DICT ║ WAREHOUSE_DICT              ← параллельно
              ──────── barrier: ждём оба ─────────
Level 1:            PRODUCT_DICT                      ← один event
              ──────── barrier ────────────────────
Level 2:  PRICE_SNAPSHOT ║ INVENTORY_FACT ║ SALES_FACT ║ ADVERTISING_FACT ║ PROMO_SYNC ║ SUPPLY_FACT
                                                        ← до 6 events параллельно (SUPPLY_FACT — stub)
              ──────── barrier: ждём SALES_FACT ──────
Level 3:            FACT_FINANCE                      ← один event (soft dep на SALES_FACT)
```

**Реализация:** DAG executor вычисляет topological levels. Events одного уровня запускаются через `CompletableFuture` / virtual threads. Barrier — `CompletableFuture.allOf()`. Результат каждого event (success / partial / failed) передаётся на следующий уровень для проверки hard/soft зависимостей.

**Shared rate limiter:** все параллельные events одного connection разделяют token bucket-ы из Integration модуля (Redis-based, ключ: `rate:{connection_id}:{rate_limit_group}`). Один bucket per (connection, rate_limit_group) — разные event types, использующие endpoints из одной rate limit group, конкурируют за токены. Этот же bucket разделяется с executor-worker (price/promo writes). Детали алгоритма, гранулярность, adaptive rate limiting и fallback — см. [Integration §Rate limiting](integration.md#rate-limiting).

**Logging:** каждый параллельный event устанавливает MDC (Mapped Diagnostic Context) с `event_type`, чтобы логи не перемешивались и можно было фильтровать по event.

**Error propagation между уровнями:**

| Ситуация | Поведение |
|----------|-----------|
| Hard dependency failed (PRODUCT_DICT failed → PRICE_SNAPSHOT) | Зависимый event **пропускается**. Нет каталога = невозможен SKU resolution |
| Soft dependency failed (SALES_FACT failed → FACT_FINANCE) | Зависимый event **выполняется** с warning. Результат может быть менее обогащённым |
| Один event на уровне failed, другие OK | Barrier дожидается всех. Успешные events на следующем уровне выполняются, если их hard dependencies OK |

**Per-event timeout:** каждый ETL event имеет configurable timeout (`datapulse.etl.event-timeout`, default: **30 минут**). Если event не завершился за это время, executor прерывает его `CompletableFuture` и маркирует event как `FAILED` с `error_type = TIMEOUT`. Уже обработанные pages сохранены (raw + canonical). Общий job timeout (default: 2 часа, `datapulse.etl.stale-job-threshold`) служит страховкой для зависших workers — stale detector переводит такие jobs в `STALE` (см. G-10).

**Страницы внутри event:** обрабатываются строго последовательно (page за page). Причина — cursor-based pagination (следующая page зависит от курсора предыдущей), rate limits, memory management (streaming по одной page).

Для каждого event:

```
4. Fetch: HTTP → streaming write to temp file → S3 putObject → INSERT job_item (CAPTURED)
5. Normalize: S3 getObject → streaming JSON parse (batch=500) → UPSERT canonical (с job_execution_id)
6. Materialize: canonical WHERE job_execution_id = current → ClickHouse facts (ReplacingMergeTree) → re-aggregate affected marts (mart_posting_pnl, mart_product_pnl, etc.)
7. Update job_item.status → PROCESSED
```

### Sub-source structure внутри event

Один ETL event может включать вызовы к **нескольким API endpoints** (sub-sources). Каждый sub-source — это отдельный `source_id` в `job_item`. Sub-sources внутри event выполняются последовательно; между ними существуют hard/soft зависимости.

| ETL Event | Sub-sources (WB) | Sub-sources (Ozon) | Зависимости |
|-----------|-------------------|---------------------|-------------|
| `CATEGORY_DICT` | — (WB: categories из catalog) | `POST /v1/description-category/tree` | — |
| `WAREHOUSE_DICT` | `GET /api/v3/offices` | — (Ozon: warehouses из stocks) | — |
| `PRODUCT_DICT` | `GET /content/v2/get/cards/list` | 1. `POST /v3/product/list` (IDs) 2. `POST /v3/product/info/list` (full info) 3. `POST /v4/product/info/attributes` (бренды, **soft**) | Ozon: (2) зависит от product_id из (1) — **hard**; (3) — **soft** |
| `PRICE_SNAPSHOT` | `GET /api/v2/list/goods/filter` | `POST /v5/product/info/prices` | — |
| `INVENTORY_FACT` | `POST /api/analytics/v1/stocks-report/wb-warehouses` | `POST /v4/product/info/stocks` | — |
| `SALES_FACT` | 1. `GET /api/v1/supplier/orders` 2. `GET /api/v1/supplier/sales` 3. `GET /api/v1/analytics/goods-return` | 1. `POST /v2/posting/fbo/list` 2. `POST /v3/posting/fbs/list` 3. `POST /v1/returns/list` (Ozon returns) | WB: (1), (2), (3) **последовательно**; Ozon: (1), (2), (3) **независимы** |
| `FACT_FINANCE` | `GET /api/v5/supplier/reportDetailByPeriod` | `POST /v3/finance/transaction/list` | — |
| `PROMO_SYNC` | 1. `GET /api/v1/calendar/promotions` 2. `GET /api/v1/calendar/promotions/nomenclatures` (per promo) | 1. `GET /v1/actions` 2. `POST /v1/actions/products` + `POST /v1/actions/candidates` (per action) | (2) зависит от promo_id из (1) — **hard**. Implemented: `WbPromoSyncSource`, `OzonPromoSyncSource` |
| `SUPPLY_FACT` | `GET /api/v1/supplier/incomes` (deprecated June 2026) | — (Ozon: нет аналога) | **Stub (no-op).** Post-deprecation: FBS → `/api/v3/supplies` (см. G-3) |

**Error handling на уровне sub-sources:**

| Ситуация | Тип зависимости | Поведение |
|----------|-----------------|-----------|
| Primary sub-source failed (Ozon каталог: product/info/list) | — | Event → `FAILED`. Без основных данных sub-source-обогащение бессмысленно |
| Enrichment sub-source failed (Ozon бренды: attributes) | **Soft** | Event → `COMPLETED_WITH_ERRORS`. Основные данные сохранены, обогащение пропущено (brand = NULL) |
| Один из независимых sub-sources failed (Ozon: FBO OK, FBS failed) | **Independent** | Event → `COMPLETED_WITH_ERRORS`. Успешные sub-sources сохранены, failed — логируются |

### Pagination recovery при потере страницы

Поведение при потере страницы зависит от типа pagination:

| Семейство | Потеря page N | Recovery | Обоснование |
|-----------|---------------|----------|-------------|
| Cursor-based (WB Finance `rrdid`, Ozon Finance cursor) | Event **останавливается** на page N. Pages 1..N-1 сохранены | Следующий sync — cursor начнётся с `last_rrdid` (WB) или `last_success_at` (Ozon) | Cursor page N+1 зависит от ответа page N — продолжение невозможно |
| Metadata cursor (WB Catalog `cursor`, Ozon Catalog/Prices/Stocks) | Event **останавливается** на page N | Аналогично cursor-based | `cursor` из response body не получен — следующая страница неизвестна |
| Offset-based (WB Orders/Sales/Returns, Ozon Orders/Returns) | Page N пропускается, worker **продолжает** с page N+1 | Пропущенные записи подхватит следующий sync через `overlap_buffer` | Offset вычисляется арифметически, не зависит от предыдущего ответа |

**Invariant:** независимо от типа pagination, все **уже обработанные** pages (1..N-1) сохранены в raw layer (S3) и canonical layer (PostgreSQL). Потеря страницы не откатывает предыдущую работу.

### Pagination consistency для full-scan domains

State-based full scans (prices, stocks) используют offset-based pagination. Если underlying data изменяется между page fetches (товар добавлен / удалён / цена изменилась), возможны:

| Сценарий | Последствие | Mitigation |
|----------|-------------|------------|
| Товар добавлен между pages | Может быть пропущен (offset shift) | UPSERT следующего full scan подхватит его |
| Товар удалён между pages | Может быть получен дважды или пропущен | Canonical хранит last-known state; orphaned records не вредят (eventual cleanup через full re-sync) |
| Данные изменились (цена) | Получена старая или новая версия — non-deterministic | UPSERT `IS DISTINCT FROM` обновит при следующем sync |

**Design rationale:** full scan domains (prices, stocks) синхронизируются каждые 15–60 минут. Transient inconsistencies между pages **не накапливаются** — каждый новый sync пересканирует полный dataset и UPSERT приводит canonical state в соответствие. Это **eventually consistent** по design, не дефект. Гарантия strong consistency для одного snapshot невозможна без transactional API на стороне провайдера (которого нет).

### Completion

```
8. Определяем исход ingest (результаты DAG) и retriable-порог:
   a. Retriable failure + retry_count < max_job_retries →
      CAS IN_PROGRESS → RETRY_SCHEDULED,
      UPDATE checkpoint,
      INSERT outbox_event (ETL_SYNC_RETRY, delay = backoff)
      → DLX доставит message обратно, worker возобновит с checkpoint (goto step 1)
   b. Все events failed/skipped или retry исчерпан при retriable → CAS IN_PROGRESS → FAILED
      (sync state → ERROR, без MATERIALIZING и без ETL_SYNC_COMPLETED)
9. Иначе (есть хотя бы один успешный домен): CAS IN_PROGRESS → MATERIALIZING,
   сохранить checkpoint + error_details по доменам
   (опционально в одной TX с шагом 9: `ETL_POST_INGEST_MATERIALIZE` → очередь `etl.sync`, см. `post-ingest-materialization-mode`)
10. Post-ingest materialization (ClickHouse / marts): SYNC — вне первой транзакции в ingest worker;
    ASYNC_OUTBOX — в `EtlSyncConsumer` после доставки `ETL_POST_INGEST_MATERIALIZE`
11. CAS MATERIALIZING → COMPLETED или COMPLETED_WITH_ERRORS
    (если материализация не fullySucceeded — терминальный статус COMPLETED_WITH_ERRORS)
12. Только при шаге 11 и статусе COMPLETED / COMPLETED_WITH_ERRORS:
    Update marketplace_sync_state (IDLE, last_success_at),
    INSERT outbox_event (ETL_SYNC_COMPLETED) → downstream consumers
    См. §job_execution lifecycle — инварианты и `IngestJobCompletionCoordinator`.
```

### Post-sync outbox events

После **финального** успешного терминала (`MATERIALIZING` → `COMPLETED` или `COMPLETED_WITH_ERRORS`, шаг 11–12 выше) ETL pipeline публикует outbox event для downstream consumers. **Не** публикуется при `RETRY_SCHEDULED` (step 8c) и не публикуется при `FAILED` (step 8d) — downstream ждёт финального результата:

| Event type | Payload | Consumer | Действие |
|------------|---------|----------|----------|
| `ETL_SYNC_COMPLETED` | `{ workspaceId, connectionId, jobExecutionId, syncScope, completedDomains[], failedDomains[] }` (+ прочие поля по мере эволюции) | `datapulse-pricing-worker` | Запускает pricing run (если FINANCE ∈ completedDomains и есть active price_policies) |
| `ETL_SYNC_COMPLETED` | (тот же payload) | `datapulse-pricing-worker` | Запускает promo evaluation (если PROMO ∈ completed_domains и есть active promo_policies). См. [Promotions](promotions.md) §Post-sync promo evaluation trigger |
| `ETL_SYNC_COMPLETED` | (тот же payload) | `datapulse-api` | Обновляет UI через WebSocket (sync status badge) |
| `ETL_PROMO_CAMPAIGN_STALE` | `{ connection_id, campaign_ids: [...] }` | `datapulse-pricing-worker` | Expires pending promo_actions для stale campaigns. См. [Promotions](promotions.md) §Stale campaign detection |

**sync_scope** — перечень data domains, включённых в sync run (CATALOG, PRICES, STOCKS, ORDERS, SALES, RETURNS, FINANCE, PROMO).

**completed_domains** — домены, успешно обработанные. **failed_domains** — домены, завершившиеся ошибкой (при COMPLETED_WITH_ERRORS).

**Routing:** outbox poller публикует event в RabbitMQ **topic exchange** `datapulse.etl.events` с routing key `etl.sync.completed.{connection_id}`. Pricing worker и API — отдельные queues, каждая с binding `etl.sync.completed.*` (получают все ETL completion events). Topic exchange выбран вместо fanout для будущей гибкости маршрутизации (фильтрация по connection или domain).

**Идемпотентность consumer:** pricing worker проверяет `job_execution_id` — если pricing run для данного `job_execution_id` уже создан, повторный event игнорируется.

### Materialization scope

> **Implementation status (Phase A):** ClickHouse materializer реализован как **stub** — `ClickHouseMaterializer.materialize()` логирует вызов, но не выполняет записи в ClickHouse. Начальная миграция ClickHouse (`0001-initial.sql`) пуста (`SELECT 1`). Полная реализация materialization pipeline (создание dim/fact таблиц, batch INSERT, ReplacingMergeTree, job-scoped delta, daily re-materialization) запланирована на **Phase B (Trust Analytics)**.

**Target design (Phase B):** Materializer обрабатывает **только записи текущего `job_execution_id`** (job-scoped materialization). Canonical → ClickHouse delta определяется по `job_execution_id`, не по timestamp diff. Full re-materialization (Phase B: daily) — отдельный scheduled job.

### Consumer error handling

ETL consumer config: `AcknowledgeMode.AUTO, prefetchCount=1, defaultRequeueRejected=false`.

При unhandled exception message отбрасывается (не requeue). Состояние зафиксировано в PostgreSQL (DB-first): `job_execution` содержит `error_details` и `checkpoint`. Recovery: для retriable ошибок — DLX auto-retry (Level 2, §Retry model); для non-retriable или при исчерпании retry — новый sync run (Level 3). Poison pill не блокирует consumer.

### Retry model — три уровня

Retry и recovery в ETL pipeline работают на трёх уровнях. Паттерн аналогичен Execution module (§Retry в [execution.md](execution.md)): DLX для отложенного retry, PostgreSQL как source of truth для состояния retry.

**Level 1 — HTTP retry (внутри worker, код-уровень)**

При вызове API маркетплейса worker использует Reactor `Retry.backoff()`:

```
Retry.backoff(3, Duration.ofSeconds(1))
    .maxBackoff(Duration.ofSeconds(10))
    .jitter(0.5)
    .filter(ex -> isRetryable(ex))
```

| Параметр | Значение | Назначение |
|----------|----------|------------|
| maxAttempts | 3 | Количество повторных попыток |
| initialBackoff | 1 сек | Начальная задержка между попытками |
| maxBackoff | 10 сек | Максимальная задержка (exponential capped) |
| jitter | 0.5 | Случайный разброс ±50% от задержки |

Retryable: HTTP 429, 500, 502, 503, 504, connection timeout.
Не retryable: HTTP 4xx (кроме 429), parsing errors, validation errors, credentials errors (401/403).

Worker **блокируется** на время retry — не переходит к следующей странице или event, пока текущий HTTP-вызов не завершится (успех после retry или окончательный fail).

**Level 2 — DLX job retry (отложенный retry с checkpoint)**

Если HTTP retry (Level 1) исчерпан на retriable ошибке, worker не завершает job как FAILED сразу. Вместо этого — checkpoint + DLX retry:

```
HTTP retry exhausted (retriable error)
  → Worker фиксирует checkpoint в job_execution (per-event progress)
  → retry_count < max_job_retries?
    → ДА: TransactionTemplate {
        CAS: IN_PROGRESS → RETRY_SCHEDULED
        UPDATE checkpoint (retry_count += 1, last_retry_at = now())
        INSERT outbox_event (ETL_SYNC_RETRY, delay = backoff)
      }
    → НЕТ: CAS: IN_PROGRESS → FAILED (терминально, retry исчерпан)
```

DLX доставляет сообщение обратно в основную очередь через configurable delay. Worker берёт message, читает checkpoint из PostgreSQL, возобновляет с места ошибки (skip completed events, resume failed events from cursor).

| Параметр | Значение | Назначение |
|----------|----------|------------|
| `max_job_retries` | 3 | Макс. DLX retry per job_execution |
| `min_backoff` | 5 мин | Начальная задержка DLX |
| `max_backoff` | 20 мин | Макс. задержка DLX |
| `backoff_multiplier` | 2× | 5 мин → 10 мин → 20 мин |

**RabbitMQ topology (DLX):**

```
Exchanges (direct):
  etl.sync              ← основная диспетчеризация (existing)
  etl.sync.wait         ← delayed retry (DLX target)

Queues:
  etl.sync              ← ingest-worker (existing)
  etl.sync.wait         ← per-message TTL expiration → DLX → etl.sync
```

**TTL strategy:** `OutboxEventPublisher` ставит per-message TTL (`expiration`) из `delay_ms` в payload. Queue-level TTL = `max_backoff` (20 мин) — safety cap на случай отсутствия per-message TTL. RabbitMQ берёт минимум из двух.

Consumer config: `AcknowledgeMode.AUTO`, `prefetchCount=1`, `defaultRequeueRejected=false` (unchanged).

**Классификация ошибок для DLX retry:**

| Ситуация | DLX retry? | Обоснование |
|----------|------------|-------------|
| HTTP 429 / 500 / 503 / 504 (API маркетплейса) | **Да** | Transient, маркетплейс восстановится |
| Connection timeout | **Да** | Network transient |
| ClickHouse недоступен (materialization) | **Да** | Infrastructure transient |
| S3/MinIO недоступен | **Да** | Infrastructure transient |
| Parse error (невалидный JSON) | **Нет** → COMPLETED_WITH_ERRORS | Повторный запрос вернёт тот же JSON |
| Validation error (bad data) | **Нет** → COMPLETED_WITH_ERRORS | Данные провайдера некорректны |
| Credentials invalid (401/403) | **Нет** → FAILED | Нужно обновить токен вручную |
| Read timeout (request sent, no response) | **Да** | Запрос мог не дойти; UPSERT обеспечит идемпотентность |

**Checkpoint structure:**

```json
{
  "events": {
    "CATEGORY_DICT":  { "status": "COMPLETED" },
    "WAREHOUSE_DICT": { "status": "COMPLETED" },
    "PRODUCT_DICT":   { "status": "COMPLETED" },
    "PRICE_SNAPSHOT": { "status": "COMPLETED" },
    "SALES_FACT":     {
      "status": "FAILED",
      "last_cursor": "offset=2000",
      "error_type": "API_ERROR",
      "error": "429 Too Many Requests after 3 HTTP retries"
    },
    "FACT_FINANCE":   { "status": "SKIPPED", "reason": "soft_dep SALES_FACT failed" }
  },
  "retry_count": 1,
  "last_retry_at": "2026-03-31T10:05:00Z"
}
```

`checkpoint.events[].status`: `COMPLETED` / `FAILED` / `SKIPPED`.
`checkpoint.events[].last_cursor`: cursor/offset для resume (cursor-based: rrdid, metadata cursor; offset-based: offset). Nullable — если cursor не получен, event re-runs from start.

Если у одного `EtlEventType` несколько sub-sources (например Ozon `SALES_FACT`: FBO, FBS, returns) и в checkpoint нужно хранить **несколько** независимых токенов, `last_cursor` — JSON-строка вида `{"o":{"OzonFboOrdersReadAdapter":"1000","OzonFbsOrdersReadAdapter":"0"}}` (ключ `o` = map `sourceId` → токен). Один sub-source с токеном по-прежнему может хранить plain string (например `"5000"`). `IngestContext.resumeSubSourceCursor(event, sourceId)` разбирает оба формата. В raw capture на `CaptureResult` задаётся `listRequestOffset` (числовой `offset` списков Ozon) и/или `listResumeKey` (opaque: `last_id`, номер страницы finance, индекс батча product info, строковый `last_id` returns); при падении на normalize/UPSERT DLX retry продолжает с того же запроса. Парсинг в адаптерах — через `EtlSubSourceResume` (`nonNegativeLong`, `lastIdOrEmpty`, `ozonFinanceStartPage`, `ozonProductInfoStartBatchIndex`).

**Worker resume logic:**

```
Worker получает message:
  → Читает job_execution из PostgreSQL
  → checkpoint IS NULL? (первый attempt)
    → Стандартный flow: все events с уровня 0
  → checkpoint EXISTS? (DLX retry)
    → Для каждого event в графе зависимостей:
      if checkpoint[event].status == "COMPLETED" → SKIP
      if checkpoint[event].status == "FAILED" →
        if last_cursor exists → resume from cursor
        else → re-run event from start (UPSERT handles dedup)
      if checkpoint[event].status == "SKIPPED" →
        re-evaluate dependencies: если dependency теперь OK → run
```

Для cursor-based pagination (WB finance `rrdid`): `last_cursor` = rrdid последней успешно обработанной записи → worker возобновляет с этого rrdid. Уже обработанные pages не перезагружаются.

Для offset-based pagination: `last_cursor` = offset последней успешной page → worker продолжает с next offset. UPSERT обеспечивает идемпотентность при overlap.

**Три типа retry — сравнение:**

| Механизм | Тот же job_execution? | Checkpoint resume? | Задержка | Триггер |
|----------|----------------------|-------------------|----------|---------|
| DLX auto-retry (Level 2) | **Да**, тот же ID | Да, с места ошибки | 5-30 мин | Автоматически при retriable failure |
| Manual Retry (кнопка UI) | **Нет**, новый ID | Нет, с нуля | Немедленно | Оператор через `POST /api/jobs/{jobId}/retry` |
| Scheduled sync (cron) | **Нет**, новый ID | Нет, с нуля | Часы | По расписанию |

**Level 3 — Recovery (новый sync run)**

Если DLX retry (Level 2) исчерпан, job завершается как FAILED. Дальнейшее recovery — через **новый `job_execution`**:

| Триггер recovery | Когда |
|------------------|-------|
| Следующий scheduled sync | По расписанию (cron), автоматически |
| Manual retry (`POST /api/jobs/{jobId}/retry`) | Оператор нажимает Retry в UI |
| System recovery after stale detection | Stale detector перевёл job в STALE → следующий scheduled sync создаёт новый job |

Новый sync проходит полный dependency graph с нуля (без checkpoint). Благодаря UPSERT с `IS DISTINCT FROM` и `overlap_buffer` (default: 2 часа), уже загруженные данные не дублируются, а пропущенные — подтягиваются.

## Data provenance

Каждая каноническая запись прослеживаема до raw source:

1. Raw record хранится в S3; `job_item` — индекс raw layer в PostgreSQL.
2. Каждая canonical entity содержит `job_execution_id` (FK) → через `job_item` → `s3_key` → raw payload.
3. Fact/mart records содержат `connection_id`, `source_platform`, `entry_id` (FK к canonical).
4. Drill-down path: `fact_finance.entry_id` → `canonical_finance_entry` → `job_execution_id` → `job_item.s3_key` → S3.

## Модель данных: ETL-specific таблицы

| Таблица | Назначение |
|---------|------------|
| `job_execution` | ETL run: connection, event type, status, timing, error_details |
| `job_item` | Index raw payload в S3: s3_key, sha256, byte_size, status |
| `outbox_event` (shared) | Outbox для ETL step dispatch: `ETL_SYNC_EXECUTE`, `ETL_SYNC_RETRY`, `ETL_POST_INGEST_MATERIALIZE`, `ETL_SYNC_COMPLETED`, `ETL_PROMO_CAMPAIGN_STALE`. Авторитетная DDL — [Data Model](../data-model.md) §outbox_event |

### job_execution lifecycle

```
PENDING → IN_PROGRESS → MATERIALIZING → COMPLETED
                                      → COMPLETED_WITH_ERRORS
         IN_PROGRESS → FAILED  (все домены failed / исчерпан retry по retriable)
         IN_PROGRESS → RETRY_SCHEDULED → IN_PROGRESS → ... (цикл до max_job_retries)
         STALE (set by timeout job)
```

**Инварианты (реализация `IngestOrchestrator` + `IngestJobCompletionCoordinator` + `PostIngestMaterializationMessageHandler`):**

1. Событие outbox **`ETL_SYNC_COMPLETED`** и обновление sync state в **IDLE** выполняются **только** в одной транзакции с финальным CAS **`MATERIALIZING` → `COMPLETED` или `COMPLETED_WITH_ERRORS`** — не раньше материализации и не при `FAILED`.
2. Переход **`IN_PROGRESS` → `MATERIALIZING`** фиксирует checkpoint и `error_details` по результатам DAG. По умолчанию (`datapulse.etl.ingest.post-ingest-materialization-mode=SYNC`) post-ingest materialization (ClickHouse / marts) вызывается **вне** первой транзакции синхронно в ingest worker; финальный CAS и fan-out — вторая транзакция. В режиме **`ASYNC_OUTBOX`** в **той же** транзакции с `IN_PROGRESS` → `MATERIALIZING` пишется outbox **`ETL_POST_INGEST_MATERIALIZE`** (exchange/routing как у `ETL_SYNC_EXECUTE` → очередь `etl.sync`); материализация и финальный CAS выполняет `PostIngestMaterializationMessageHandler` из `EtlSyncConsumer`. Идемпотентность: если job уже не в `MATERIALIZING`, обработчик no-op (повторная доставка сообщения).
3. Payload `ETL_SYNC_COMPLETED` всегда содержит **`workspaceId`** (из контекста инжеста), **`connectionId`**, **`jobExecutionId`** для downstream consumers.

| Переход | Условие | Guard |
|---------|---------|-------|
| PENDING → IN_PROGRESS | Worker picks up message (first attempt) | CAS: `UPDATE ... WHERE id = ? AND status = 'PENDING'` |
| IN_PROGRESS (resume) | В БД уже `IN_PROGRESS` и выполняется **одно из**: **(a)** RabbitMQ `redelivered=true` (предыдущий consumer не ack — crash, обрыв канала); **(b)** `redelivered=false`, но `started_at` старше `datapulse.etl.ingest.in-progress-orphan-reclaim-threshold` (дефолт `PT15M`) — смягчает редкий случай дубликата/квирка брокера без `redelivered` + `AcknowledgeMode.AUTO` (иначе no-op + ack → «мёртвый» job до stale-detector). `PT0S` отключает ветку (b), остаётся только redelivery | `UPDATE ... SET started_at = now() WHERE id = ? AND status = 'IN_PROGRESS'` — обновление lease + разрешение снова выполнить DAG (checkpoint / идемпотентность canonical) |
| IN_PROGRESS → MATERIALIZING | Ingest не полностью failed и не ушёл в retry; DAG завершён | CAS: `WHERE status = 'IN_PROGRESS'` |
| MATERIALIZING → COMPLETED | Все events успешны + материализация успешна | CAS: `WHERE status = 'MATERIALIZING'` |
| MATERIALIZING → COMPLETED_WITH_ERRORS | Часть events failed/partial **или** материализация с ошибками | CAS: `WHERE status = 'MATERIALIZING'` |
| IN_PROGRESS → FAILED | Все domains failed/skipped (без успешного пути в MATERIALIZING) или иной терминальный failed-путь | CAS: `WHERE status = 'IN_PROGRESS'` |
| IN_PROGRESS → RETRY_SCHEDULED | Retriable failure + retry_count < max_job_retries | CAS: `WHERE status = 'IN_PROGRESS'` |
| RETRY_SCHEDULED → IN_PROGRESS | Worker picks up retry message from DLX | CAS: `UPDATE ... WHERE id = ? AND status = 'RETRY_SCHEDULED'` |
| IN_PROGRESS → STALE | `started_at < now() - stale_threshold` | Scheduled stale job detector |
| RETRY_SCHEDULED → STALE | `checkpoint->>'last_retry_at' < now() - interval '1 hour'` | Scheduled stale job detector |
| MATERIALIZING → STALE | Фаза post-ingest / ClickHouse дольше `datapulse.etl.ingest.materializing-stale-threshold` (дефолт 1h); учёт по `job_execution.materializing_at`, иначе fallback `started_at` для старых строк | `StaleJobDetector` → `markStaleMaterializing` |

`STALE` — терминальный статус. Recovery path: `StaleJobDetector` помечает зависший job как STALE → `IngestResultReporter.reconcileAllConnectionsStuckInSyncingWithoutActiveJob()` сбрасывает `marketplace_sync_state.status` SYNCING → IDLE для connections без активных jobs → следующий scheduled sync создаёт новый `job_execution` (concurrency guard пропускает STALE jobs). `stale_threshold`: configurable (default: 2 часа для IN_PROGRESS, 1 час для RETRY_SCHEDULED, 1 час для MATERIALIZING).

**Eager reconcile per connection:** перед проверкой «есть ли активный job» для данного `connection_id` (ручной sync, `SyncTriggeredListener`, активация подключения, scheduled sync, API retry) выполняется тот же набор порогов, что и у `StaleJobDetector`, но scoped `WHERE connection_id = ?` (`ConnectionStaleJobReconciler`). Так `job.active.exists` / skip в listener не блокируют новый dispatch до следующего тика детектора (до 15 минут зазора). Это не заменяет orphan-reclaim по Rabbit: там нужна доставка сообщения; здесь — путь «пользователь/планировщик инициирует новый job».

Почти все переходы защищены CAS (optimistic lock). Исключение: **resume** — тот же guarded `UPDATE` по `id` + `status = IN_PROGRESS`, когда либо `redelivered=true`, либо сработал orphan-порог по `started_at` (`in-progress-orphan-reclaim-threshold`, см. строку выше). При `AcknowledgeMode.AUTO` сообщение ack-ится после успешного return из listener: без resume redelivery после crash дала бы no-op, ack и «вечный» `IN_PROGRESS` до stale-detector; без orphan-порога при `redelivered=false` та же логика для редкого дубликата. Если CAS / acquire не прошёл и **не** было основания для resume (нет `redelivered` и job не старше порога), worker всё равно ack-ит сообщение (duplicate / гонка) — защита от двойного параллельного ingest на одном job по-прежнему через статус в БД.

**Retry — тот же job_execution.** DLX retry не создаёт новый `job_execution`. Это продолжение того же run: ID сохраняется, checkpoint накапливает прогресс, provenance цельная (`canonical.job_execution_id` одинаков для всех attempt-ов).

#### job_execution fields

| Поле | Тип | Назначение |
|------|-----|------------|
| `id` | BIGSERIAL | PK |
| `connection_id` | BIGINT FK | marketplace_connection |
| `event_type` | VARCHAR(64) | Тип sync (`FULL_SYNC`, `INCREMENTAL`, `MANUAL_SYNC`, …) |
| `status` | VARCHAR(32) | PENDING / IN_PROGRESS / **MATERIALIZING** / COMPLETED / COMPLETED_WITH_ERRORS / RETRY_SCHEDULED / FAILED / STALE |
| `started_at` | TIMESTAMPTZ | Время начала (первого attempt) |
| `completed_at` | TIMESTAMPTZ | Время завершения (финального attempt) |
| `materializing_at` | TIMESTAMPTZ | Время входа в `MATERIALIZING`; `NULL` в остальных статусах; stale по фазе ClickHouse |
| `error_details` | JSONB | Структурированные детали ошибок (nullable) |
| `checkpoint` | JSONB | Per-event progress для DLX retry resume (nullable — NULL при первом attempt). См. §Checkpoint structure |
| `params` | JSONB | Опции run: `domains[]`, `sourceJobId`, `trigger`, … (nullable) |
| `created_at` | TIMESTAMPTZ | Время создания записи |

**`error_details` JSON structure:**

```json
{
  "failed_domains": ["FINANCE", "SALES"],
  "errors": [
    {
      "domain": "FINANCE",
      "event": "FACT_FINANCE",
      "error_type": "API_ERROR",
      "message": "429 Too Many Requests after 3 retries",
      "page": 5,
      "records_processed": 2400,
      "records_skipped": 0
    },
    {
      "domain": "SALES",
      "event": "SALES_FACT",
      "error_type": "PARSE_ERROR",
      "message": "Unexpected field format in record",
      "records_processed": 180,
      "records_skipped": 3,
      "skipped_records": [
        { "record_key": "srid_12345", "reason": "Invalid date format" }
      ]
    }
  ],
  "completed_domains": ["CATALOG", "PRICES", "STOCKS"]
}
```

`error_type` enum: `API_ERROR` (provider HTTP error), `PARSE_ERROR` (parsing/validation), `TIMEOUT` (page/job timeout), `INFRA_ERROR` (DB, S3, network).

## Design decisions

### G-2: Ozon categories API — RESOLVED

`POST /v1/description-category/tree` с `{"language":"DEFAULT"}`. Возвращает иерархию: `description_category_id` + `category_name` + `children[]`.

### G-3: fact_supply (WB incomes) — MIGRATION PLAN

WB old incomes API `GET /api/v1/supplier/incomes` **deprecated June 2026**. Данные о поставках на склады WB полезны для inventory tracking, но не являются частью core P&L flow.

**Current state (Phase A/B):**
- Endpoint доступен, данные описаны в `wb-read-contracts.md` §Incomes capability
- `NormalizedSupplyItem` определён в `mapping-spec.md` §8
- Canonical entity **не определена** — данные не попадают в canonical layer
- ETL event **не зарегистрирован** в dependency graph

**Migration timeline:**

| Фаза | Срок | Действие |
|------|------|----------|
| **Phase A** (сейчас) | До June 2026 | `SUPPLY_FACT` зарегистрирован как **stub** (no-op). Приоритет ниже core domains. Endpoint доступен, но не интегрирован |
| **Phase B.1** | June 2026 (deprecation) | Endpoint отключается WB. FBS-поставки мигрируют на `/api/v3/supplies` |
| **Phase B.2** | Post-deprecation | FBO-поставки — manual CSV import или расчёт через inventory delta (stock snapshots) |

**FBS alternative: `POST /api/v3/supplies`**
- Возвращает список поставок FBS (сборочные задания)
- Пагинация: cursor-based (`next` token)
- Ключевые поля: `id` (supply ID), `name`, `createdAt`, `closedAt`, `scanDt`, `done` (boolean), `orders[]`
- **Read contract не детализирован** — требуется верификация response structure перед реализацией (см. `provider-api-verification.mdc`)

**FBO: нет API-альтернативы**
- FBO incomes (приёмки на склады WB) — данные доступны только через личный кабинет WB (CSV export)
- Альтернатива: расчёт FBO incomes через delta inventory snapshots (`canonical_stock_current` diff между sync-ами)
- Точность inventory delta ниже, чем прямой API — приемлемо для аналитических целей

**Impact assessment:**
- **P&L**: нулевой impact — supply data не участвует в P&L расчётах
- **Inventory analytics**: LOW impact — stock levels доступны через `INVENTORY_FACT`; supply history теряется, но компенсируется snapshot delta
- **Downstream consumers**: нет canonical entity → нет потребителей → нет breaking changes

**Blocker / risk:**
- **R-14**: если `/api/v3/supplies` не покрывает нужные FBS-данные — потеря FBS supply history после deprecation
- **Mitigation**: верифицировать `/api/v3/supplies` response до May 2026, зафиксировать в `wb-read-contracts.md`

### G-7: dim_warehouse для WB — RESOLVED

Найден dedicated endpoint `GET /api/v3/offices` — полный список складов WB (Production: 225 offices). Join keys: finance `ppvz_office_id` → `offices.id`.

### G-8: Retention cleanup for job_item — RESOLVED

При удалении S3 objects по retention policy, `job_item.status` обновляется до `EXPIRED`. Cleanup job (scheduled) сканирует `job_item WHERE status = 'PROCESSED' AND captured_at < retention_threshold` и помечает записи. Provenance drill-down для expired записей возвращает "raw source expired".

### G-9: Timezone conversion for ClickHouse materialization — RESOLVED

Materializer конвертирует `canonical_finance_entry.entryDate` (TIMESTAMPTZ) → `fact_finance.finance_date` (Date) с использованием Moscow timezone (UTC+3) как business timezone. Обоснование: оба маркетплейса (WB, Ozon) оперируют в Moscow TZ для финансовой отчётности.

### G-10: Stale job detection — RESOLVED

Scheduled job (configurable interval, default: 15 min) сканирует зависшие jobs по двум условиям:
1. `status = 'IN_PROGRESS' AND started_at < now() - interval '2 hours'` — зависший worker.
2. `status = 'RETRY_SCHEDULED' AND (checkpoint->>'last_retry_at')::timestamptz < now() - interval '1 hour'` — DLX retry message потерялось или не было доставлено.

Оба переводятся в STALE. Threshold настраивается через `datapulse.etl.stale-job-threshold` (IN_PROGRESS) и `datapulse.etl.stale-retry-threshold` (RETRY_SCHEDULED, default: 1 час).

### G-11: canonical_price/stock split — current vs history — RESOLVED

**Проблема:** Исходный дизайн `canonical_price_snapshot` и `canonical_stock_snapshot` включал `captured_at` в UPSERT key. Каждый sync создавал новую строку вместо обновления → бесконечный рост PostgreSQL. Для таблиц, которые описывают **текущее состояние**, это неправильно.

**Решение:** Split на два слоя:

| Слой | Таблица | Хранилище | UPSERT key | Назначение |
|------|---------|-----------|------------|------------|
| Current state | `canonical_price_current` | PostgreSQL | `(marketplace_offer_id)` | Последняя известная цена. Используется pricing pipeline |
| Current state | `canonical_stock_current` | PostgreSQL | `(marketplace_offer_id, warehouse_id)` | Последний известный остаток. Используется pricing pipeline |
| History | `fact_price_snapshot` | ClickHouse | `(connection_id, product_id, captured_at)` | Ценовая история для аналитики |
| History | `fact_inventory_snapshot` | ClickHouse | `(connection_id, product_id, warehouse_id, captured_at)` | Историческая динамика остатков |

**Materializer flow:** каждый PRICE_SNAPSHOT sync → UPSERT `canonical_price_current` (PostgreSQL, latest state) + INSERT `fact_price_snapshot` (ClickHouse, append-only history). Аналогично для INVENTORY_FACT.

**Pricing pipeline reads:** `canonical_price_current` (not ClickHouse) — decision-grade current price per offer. ClickHouse `fact_price_snapshot` используется signal assembler для исторических сигналов (trend, volatility).

**Bounded growth:** PostgreSQL содержит максимум 1 строку per marketplace_offer (prices) и 1 строку per marketplace_offer × warehouse (stocks). Исторические снимки — только в ClickHouse.

### G-12: workspace_id denormalization — RESOLVED

**Вопрос:** Нужно ли денормализовать `workspace_id` в canonical entities (`canonical_finance_entry`, `canonical_order`, `canonical_price_current`, etc.), или достаточно `connection_id → marketplace_connection.workspace_id` JOIN?

**Решение: НЕ денормализовать.**

Обоснование:
1. **`connection_id` — уже tenant-isolation key.** Все canonical queries фильтруются по `connection_id`, не по `workspace_id`. Workspace owns connections; connection owns canonical data.
2. **JOIN стоимость минимальна.** `marketplace_connection` — маленькая таблица (десятки строк). JOIN по PK = index lookup.
3. **Денормализация создаёт inconsistency risk.** При переносе connection между workspaces (Phase G, если потребуется) — все canonical records потребуют bulk UPDATE.
4. **Pricing, Execution, Promotions** — уже используют `connection_id` как primary filter. `workspace_id` нужен только на верхнем уровне (UI routing, API authorization).

**Исключения (workspace_id денормализован):**
- `job_execution` — содержит `workspace_id` опосредованно через `connection_id`.
- `price_policy`, `price_decision`, `price_action` — содержат `workspace_id` напрямую, потому что это бизнес-сущности уровня workspace (policy → workspace).
- `audit_log`, `alert_rule`, `alert_event` — содержат `workspace_id` для прямого query без JOIN.

### G-13: canonical_order grain mismatch — DOCUMENTED LIMITATION (2026-03-31)

**Проблема:** `canonical_order` имеет разную гранулярность для WB и Ozon:

| Провайдер | `external_order_id` | Grain | `quantity` |
|-----------|---------------------|-------|------------|
| WB | `srid` (per-unit row) | 1 row = 1 unit | Всегда 1 |
| Ozon | `posting_number` (per-posting) | 1 row = 1 posting (может содержать N products) | Может быть >1 |

**Последствия:**
- `canonical_order` **не является consistent grain** для cross-platform сравнений
- Для Ozon multi-product postings: `marketplace_offer_id = NULL` (невозможно указать на один конкретный product), `quantity = SUM по всем products в posting`, `total_amount = SUM по всем products`
- WB заказ с 3 unit'ами → 3 canonical_order rows; Ozon posting с 3 products → 1 row

**Решение: оставить как есть.**

Обоснование:
1. `canonical_order` = **operational view**, не P&L grain. Для финансов используется `fact_finance`
2. Per-product детализация Ozon доступна через `canonical_sale` (per-product rows с `posting_id` для join к `canonical_order`)
3. Cross-platform количественное сравнение orders возможно через aggregation (`SUM(quantity) GROUP BY connection_id, order_date`)
4. Нормализация Ozon postings в per-product rows потребует N:1 split (сложнее, чем текущий подход)
5. UPSERT key `(connection_id, external_order_id)` работает корректно для обоих провайдеров

**Guardrail:** Аналитические запросы по `canonical_order` должны использовать `SUM(quantity)`, а не `COUNT(*)` для подсчёта единиц товара. Per-product drill-down для Ozon — через `canonical_sale`.

## Конфигурация MinIO

### Docker Compose

```yaml
minio:
  image: minio/minio:latest
  command: server /data --console-address ":9001"
  ports:
    - "9010:9000"   # API (mapped to avoid conflicts; matches NFA Docker Compose)
    - "9011:9001"   # Console
  environment:
    MINIO_ROOT_USER: datapulse
    MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
  volumes:
    - minio-data:/data
```

### Bucket strategy

Один bucket `datapulse-raw` с prefix-based isolation: `raw/`, `evidence/` (Phase C), `exports/` (Phase E).

## cost_profile lifecycle

| Аспект | Описание |
|--------|----------|
| Источник | Ручной ввод (Phase A/B). Bulk CSV import (Phase E). |
| Grain | Per `seller_sku`. Не per `marketplace_offer`. |
| Версионирование | SCD2: `valid_from`, `valid_to` (`NULL` = текущая версия). При создании новой версии: (1) UPDATE текущую запись `SET valid_to = new.valid_from - 1 day WHERE seller_sku_id = ? AND valid_to IS NULL`, (2) INSERT новую запись `(seller_sku_id, cost_price, valid_from, valid_to = NULL)`. При повторном INSERT с тем же `valid_from`: UPSERT — обновить `cost_price` если отличается, иначе no-op. |
| При отсутствии | Pricing: eligibility SKIP («Себестоимость не задана»). P&L: COGS = 0 (explicit, помечено в UI). |
| Validation | `cost_price > 0`, `currency = RUB`. |
| API | Full CRUD через datapulse-api: `GET`, `POST`, `PUT /{id}`, `DELETE /{id}`, `bulk-import` (CSV), `bulk-update`, `export` (CSV), `{sellerSkuId}/history`. |
| Permission | ADMIN, PRICING_MANAGER. |

## Canonical finance resolution rules

Normalizer разрешает provider-specific идентификаторы в каноничные поля `canonical_finance_entry`. Все provider-specific поля остаются в raw/normalized layer — за границу canonical они **не протекают**.

### posting_id resolution

| Provider | Source field | → canonical posting_id | Notes |
|----------|-------------|------------------------|-------|
| Ozon (order-linked) | `posting.posting_number` | As-is (с суффиксом -N) | e.g. "87621408-0010-1" |
| Ozon (acquiring) | — | **NULL** | Acquiring привязывается через order_id |
| Ozon (standalone) | `posting_number` = "" or non-posting format | **NULL** | Standalone operation. Posting format: `^\d+-\d+-\d+$` (e.g. "87621408-0010-1"). Numeric-only strings (e.g. CPC campaign ID "20460416") и пустые строки → NULL |
| WB | `srid` | As-is | Unique row identifier |

### order_id resolution

| Provider | Source field | → canonical order_id | Notes |
|----------|-------------|----------------------|-------|
| Ozon (order-linked) | `posting.posting_number` | Strip `-N` suffix (DD-15) | e.g. "87621408-0010-1" → "87621408-0010" |
| Ozon (acquiring) | `posting.posting_number` | As-is (уже без -N) | e.g. "87621408-0010" |
| Ozon (standalone) | `posting_number` = "" or non-posting format | **NULL** | Same format check as posting_id resolution |
| WB | `gNumber` | As-is | Order group number |

### seller_sku_id resolution

| Provider | Source field | Resolution | Notes |
|----------|-------------|------------|-------|
| Ozon (order-linked) | `items[].sku` | Lookup: `sku → catalog sources[].sku → product_id → offer_id → seller_sku.id` | |
| Ozon (standalone with items) | `items[].sku` | Same lookup | e.g. packaging/labeling operations |
| Ozon (standalone without items) | — | **NULL** | e.g. storage, reviews |
| WB | `sa_name` (= supplierArticle) | Lookup: `vendorCode → seller_sku.id` | |

**SKU resolution fallback:** если lookup не находит соответствие (товар ещё не загружен через PRODUCT_DICT), `seller_sku_id = NULL`. Запись логируется как warning (`log.warn "SKU lookup miss: sku={}, connection_id={}"`). Attribution fallback: entry без `seller_sku_id` и без `posting_id`/`order_id` получает `ACCOUNT`. Entry с `posting_id` или `order_id`, но без `seller_sku_id` получает `POSTING` (allocation в mart).

### net_payout per entry

| Provider | Source | → canonical net_payout | Notes |
|----------|--------|------------------------|-------|
| Ozon | `operation.amount` | As-is (signed) | Net contribution per operation |
| WB | `ppvz_for_pay` | As-is | Per-row seller payout |

Posting-level net_payout (Σ across all operations) вычисляется в `mart_posting_pnl`, не в canonical layer.

### attribution_level (computed by normalizer at INSERT)

Normalizer вычисляет `attribution_level` при INSERT в `canonical_finance_entry` (PostgreSQL). Materializer **копирует** значение as-is в `fact_finance` (ClickHouse). Правило:

```
IF posting_id IS NOT NULL OR order_id IS NOT NULL  → POSTING
ELIF seller_sku_id IS NOT NULL                     → PRODUCT
ELSE                                               → ACCOUNT
```

Acquiring operations (`posting_id IS NULL`, `order_id IS NOT NULL`): получают `POSTING` потому что привязаны к order (→ к posting через order_id join в mart, allocation pro-rata по revenue).

**Acquiring SKU enrichment:** normalizer проставляет `seller_sku_id` если order содержит один SKU. Для multi-SKU orders `seller_sku_id = NULL`, allocation выполняется в mart pro-rata по revenue.

## Promo canonical entities

Промо-данные загружаются через `PROMO_SYNC` event и сохраняются в canonical layer (PostgreSQL) для operational use в модуле [Promotions](promotions.md), а также материализуются в ClickHouse для аналитики.

### canonical_promo_campaign

```
canonical_promo_campaign:
  id                       BIGSERIAL PK
  connection_id            BIGINT FK → marketplace_connection       NOT NULL
  external_promo_id        VARCHAR(120) NOT NULL                    -- provider-specific promo ID
  source_platform          VARCHAR(10) NOT NULL                     -- 'ozon' / 'wb'
  promo_name               VARCHAR(500) NOT NULL                    -- название акции
  promo_type               VARCHAR(60) NOT NULL                     -- тип акции (provider-specific taxonomy)
  status                   VARCHAR(30) NOT NULL                     -- UPCOMING, ACTIVE, FROZEN, ENDED, CANCELLED
  date_from                TIMESTAMPTZ                              -- начало акции (nullable — если не объявлено)
  date_to                  TIMESTAMPTZ                              -- конец акции (nullable — бессрочные)
  freeze_at                TIMESTAMPTZ                              -- Ozon-specific: момент заморозки изменений участия (nullable)
  participation_deadline   TIMESTAMPTZ                              -- дедлайн подачи заявки (nullable)
  description              TEXT                                     -- описание условий (nullable)
  mechanic                 VARCHAR(60)                              -- механика: DISCOUNT, SPP, CASHBACK, BUNDLE, etc.
  is_participating         BOOLEAN                                  -- summary flag: есть ли participating products (обновляется sync + Datapulse actions)
  raw_payload              JSONB                                    -- полный provider payload для forensics
  job_execution_id         BIGINT FK → job_execution                NOT NULL
  synced_at                TIMESTAMPTZ                              -- время последнего sync
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (connection_id, external_promo_id)
```

### canonical_promo_product

```
canonical_promo_product:
  id                            BIGSERIAL PK
  canonical_promo_campaign_id   BIGINT FK → canonical_promo_campaign  NOT NULL
  marketplace_offer_id          BIGINT FK → marketplace_offer         NOT NULL
  participation_status          VARCHAR(30) NOT NULL                   -- ELIGIBLE, PARTICIPATING, DECLINED, REMOVED, BANNED, AUTO_DECLINED
  required_price                DECIMAL                                -- цена участия (Ozon: action_price; WB: actionPrice/planPrice; nullable)
  current_price                 DECIMAL                                -- текущая regular цена товара на момент sync
  max_promo_price               DECIMAL                                -- макс. допустимая цена участия (Ozon: max_action_price; nullable)
  max_discount_pct              DECIMAL                                -- макс. скидка (nullable — не все акции задают)
  min_stock_required            INT                                    -- мин. остаток для участия (Ozon: min_stock; nullable)
  stock_available               INT                                    -- текущий остаток на момент sync (nullable)
  add_mode                      VARCHAR(60)                            -- Ozon: способ добавления в акцию (nullable)
  participation_decision_source VARCHAR(20)                            -- MANUAL, AUTO, SYSTEM (обновляется Promotions при decision)
  job_execution_id              BIGINT FK → job_execution              NOT NULL
  synced_at                     TIMESTAMPTZ                            -- время последнего sync
  created_at                    TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at                    TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (canonical_promo_campaign_id, marketplace_offer_id)
```

**Маппинг из provider API:**
- WB: `/api/v1/calendar/promotions` → `canonical_promo_campaign`; `/api/v1/calendar/promotions/nomenclatures` → `canonical_promo_product`
- Ozon: `/v1/actions` → `canonical_promo_campaign`; `/v1/actions/products` → `canonical_promo_product`

Детальные маппинг-правила: [promo-advertising-contracts.md](../provider-api-specs/promo-advertising-contracts.md).

### Stale campaign detection (post-sync cleanup)

После каждого `PROMO_SYNC` ETL проверяет кампании, которые не возвращаются в ответах провайдера (маркетплейс удалил акцию). Признак: `synced_at` не обновлялся > 48 часов при активном sync'е для connection.

```sql
UPDATE canonical_promo_campaign
SET status = 'ENDED', updated_at = NOW()
WHERE connection_id = :connectionId
  AND status IN ('UPCOMING', 'ACTIVE')
  AND synced_at < NOW() - INTERVAL '48 hours'
RETURNING id
```

Для каждой stale campaign ETL публикует outbox event `ETL_PROMO_CAMPAIGN_STALE` (fanout exchange `datapulse.etl.events`). Payload: `{ connection_id, campaign_ids: [...] }`.

Consumers:
- [Promotions](promotions.md) — expires PENDING_APPROVAL / APPROVED `promo_action` для stale campaigns
- [Audit & Alerting](audit-alerting.md) — alert `PROMO_CAMPAIGN_STALE`

**Порог 48 часов:** выбран с запасом — стандартный интервал PROMO_SYNC: 4–6 часов. Если кампания не появилась в 8+ sync'ах подряд — с высокой вероятностью удалена провайдером. Порог конфигурируется через `datapulse.etl.promo.stale-threshold`.

## REST API (ETL monitoring)

ETL jobs запускаются через Integration API (`POST /api/connections/{id}/sync`). Мониторинг — через ETL-specific endpoints.

### Job executions

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/connections/{connectionId}/jobs` | Any role | Paginated список job executions. Filters: `?status=...&from=...&to=...`. Sort: `created_at DESC` |
| GET | `/api/jobs/{jobId}` | Any role | Детали job execution: status, timing, error_details, domains processed |
| GET | `/api/jobs/{jobId}/items` | ADMIN, OWNER | Job items (raw layer index): `[{ sourceId, pageNumber, s3Key, status, recordCount, byteSize, capturedAt, processedAt }]` |
| POST | `/api/jobs/{jobId}/retry` | ADMIN, OWNER | Retry failed job — создаёт новый `INCREMENTAL` job_execution для того же `connection_id`. Scope: полный dependency graph (не только failed domains), т.к. incremental strategy + UPSERT идемпотентность обеспечивают корректность при повторной загрузке. Доступен только для FAILED / COMPLETED_WITH_ERRORS. Concurrency guard: если уже есть PENDING/IN_PROGRESS job для connection — возвращает 409 Conflict |

### Cost profiles

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/cost-profiles` | Any role | Список cost profiles (current versions). Filter: `?sellerSkuId=...&search=...`. Paginated |
| POST | `/api/cost-profiles` | ADMIN, PRICING_MANAGER | Создать/обновить cost profile. Body: `{ sellerSkuId, costPrice, currency, validFrom }`. SCD2: закрывает предыдущую версию |
| PUT | `/api/cost-profiles/{id}` | ADMIN, PRICING_MANAGER | Обновить существующий cost profile. Body: `{ costPrice, currency, validFrom }`. SCD2: закрывает предыдущую версию и создаёт новую |
| DELETE | `/api/cost-profiles/{id}` | ADMIN, PRICING_MANAGER | Удалить cost profile. Returns 204 No Content |
| POST | `/api/cost-profiles/bulk-import` | ADMIN, PRICING_MANAGER | CSV import. Body: multipart file. Format: `sku_code,cost_price,currency,valid_from`. Response: `{ imported, skipped, errors[] }` |
| POST | `/api/cost-profiles/bulk-update` | ADMIN, PRICING_MANAGER | Массовое обновление cost profiles. Body: `{ items: [{ sellerSkuId, costPrice, currency, validFrom }] }`. Response: `{ updated, skipped, errors[] }` |
| GET | `/api/cost-profiles/export` | ADMIN, PRICING_MANAGER | Экспорт cost profiles в CSV. Returns `application/octet-stream` с `Content-Disposition: attachment` |
| GET | `/api/cost-profiles/{sellerSkuId}/history` | Any role | SCD2 history для SKU: все версии с valid_from/valid_to |

## Error handling contract

### Normalization errors

При ошибке normalization конкретного record (parsing, type coercion, validation):

1. Record пропускается (не блокирует остальные в batch)
2. Error logged: `log.warn "Normalization failed: source={}, page={}, record={}, error={}", source_id, page, recordIndex, errorMessage`
3. `job_item.status` → `FAILED` (если весь page не parseable) или остаётся `CAPTURED` с partial processing
4. `job_execution.error_details` JSONB обновляется: `{ "normalization_errors": [{ "source", "page", "record_index", "field", "error" }] }`
5. При наличии errors → `job_execution` завершается как `COMPLETED_WITH_ERRORS`

### Error categories

| Категория | Пример | Поведение |
|-----------|--------|-----------|
| Parse error | Невалидный JSON, unexpected structure | Skip page → `job_item` FAILED |
| Type coercion error | String вместо number, invalid date format | Skip record, continue batch |
| Validation error | Negative quantity, missing required field | Skip record, log warning |
| SKU lookup miss | `seller_sku_id` not found for finance entry | Record saved with `seller_sku_id = NULL`, log warning |
| Referential integrity | FK target not found (offer, order) | Record saved with nullable FK = NULL, log warning |

### Alerting

При `FAILED` job_execution (в т.ч. после исчерпания DLX retry) или при `error_count > threshold` в `COMPLETED_WITH_ERRORS` → `ETL_JOB_ALERT` event → notification to workspace admins (WebSocket + alert_event record). Alert содержит `retry_count` и `checkpoint` summary, чтобы оператор мог оценить, стоит ли делать manual retry.

## Bulk operations и массовые операции

### Full sync (bulk ingestion)

Full sync загружает весь каталог/ценовой snapshot/остатки целиком. Это самая тяжёлая операция по нагрузке.

| Аспект | Поведение |
|--------|-----------|
| Триггер | Первичная синхронизация при создании connection. Ручной trigger через API. Scheduled full re-sync (configurable, default: weekly) |
| Pagination | Adapter вычитывает все страницы последовательно (cursor-based для WB, offset/limit для Ozon). Каждая страница → отдельный `job_item` в raw layer |
| Memory management | Streaming: page загружается → сохраняется в S3 → нормализуется → записывается в canonical → memory freed. Не держать весь dataset в памяти |
| Canonical write | Batch UPSERT с `IS DISTINCT FROM` (no-churn). Batch size: configurable (default: 500 records per batch) |
| Timeout | Per-page timeout (configurable). Общий job timeout = `max_pages × page_timeout + buffer`. Default job timeout: 2 часа |
| Failure mid-sync | Processed pages сохранены (raw + canonical). Оставшиеся pages → `FAILED`. Job → `COMPLETED_WITH_ERRORS`. Следующий incremental sync подхватит пропущенное |
| Concurrency | Один sync per connection одновременно (не per domain — один `job_execution` обрабатывает все domains в dependency graph). Enforced через `job_execution` status check при создании нового job: `NOT EXISTS (SELECT 1 FROM job_execution WHERE connection_id = ? AND status IN ('PENDING', 'IN_PROGRESS', 'RETRY_SCHEDULED'))`. `RETRY_SCHEDULED` блокирует новый sync — job ещё в процессе, DLX retry вернёт его в работу |

### Cost profile bulk import

CSV import для cost profiles (COGS) — отдельный поток, не связанный с marketplace sync.

| Аспект | Поведение |
|--------|-----------|
| Endpoint | `POST /api/cost-profiles/bulk-import` (multipart file) |
| Validation | Row-level: пропустить невалидные строки, продолжить. Report: `{ imported, skipped, errors[] }` |
| Atomicity | Всё или ничего не подходит (partial import полезнее). Каждая строка — отдельный UPSERT. SCD2: закрытие предыдущей версии в той же транзакции |
| Limits | Max rows per file: 10 000. Max file size: 5 MB. Превышение → 400 Bad Request |
| Duplicate handling | Повторный import с теми же данными → no-op (SCD2: `valid_from` совпадает, UPSERT не создаёт новую версию если данные не изменились) |

### Materialization (ClickHouse bulk write)

ETL materializer записывает данные из canonical layer (PostgreSQL) в ClickHouse.

| Аспект | Поведение |
|--------|-----------|
| Механизм | Batch INSERT через JDBC ClickHouse driver. Batch size: configurable (default: 5000 rows) |
| Idempotency | `ReplacingMergeTree` в ClickHouse обеспечивает upsert-семантику. Повторная materialization безопасна |
| Partial failure | При ошибке INSERT одного batch → log error, continue с остальными. Job → `COMPLETED_WITH_ERRORS` |
| Full re-materialization | Daily scheduled (ночной window). Перезаписывает все данные. Гарантирует eventual consistency CH ↔ PG |
| Concurrent access | ClickHouse INSERT не блокирует SELECT. Читатели (seller-operations, analytics) видят consistent snapshot благодаря `FINAL` keyword в критических запросах |

## Implementation Status (Phase A → Phase B gap analysis)

Summary of gaps between this document (target design) and the current codebase.

### Fully implemented

| Area | Status |
|------|--------|
| Raw → Normalized → Canonical pipeline (core flow) | Implemented |
| EventSource Strategy + Registry pattern | Implemented |
| DAG execution (levels, hard/soft/independent deps) | Implemented |
| S3 raw storage (MinIO, SHA-256, streaming) | Implemented |
| CursorExtractor (JsonPath, NoCursor, TailField) | Implemented |
| WB adapters (7 EventSources, 10 ReadAdapters) | Implemented |
| Ozon adapters (7 EventSources, 13 ReadAdapters) | Implemented |
| Normalized model (9 records) | Implemented |
| Canonical persistence (JDBC batch upsert, IS DISTINCT FROM) | Implemented |
| Scheduling (SyncScheduler → SyncDispatcher, StaleJobDetector, ShedLock) | Implemented |
| Job monitoring API (JobController, CostProfileController) | Implemented |
| Retention (RetentionService, RetentionScheduler) | Implemented |
| Cost profile SCD2 (CRUD + bulk CSV import) | Implemented |

### Implemented with simplifications (Phase A)

| Area | Current (Phase A) | Target (Phase B) |
|------|-------------------|------------------|
| Date-range strategy | Hardcoded `now() - 7 days` for all date-range domains | `last_success_at - overlap_buffer` per domain |
| Scheduling interval | Configurable via `datapulse.etl.ingest.sync-interval` (default 6h), uniform for all domains per connection | Per-domain cron (4x/day finance, 2x/day catalog) |
| WB finance cursor | Same N-day window (default 30d) | `rrdid`-based monotonic cursor |
| Ozon finance chunking | Same N-day window (default 30d) | Automatic monthly chunk splitting for long gaps |
| ClickHouse materializer | Stub (logs calls, no actual writes) | Full batch INSERT via ClickHouse JDBC |
| Ozon sale amount source | `products[].price` (STRING, parsed to BigDecimal) | `financial_data.products[].price` (NUMBER, native BigDecimal). Requires DTO expansion: `OzonFinancialData.products[]` not modeled yet |
| Ozon sale commission | Always `null` — `financial_data.products[].commission_amount` not modeled in DTO | Expand `OzonFinancialData` with per-product financial breakdown, populate `canonical_sale.commission` |

### Implemented (recent)

| Area | Notes |
|------|-------|
| `PROMO_SYNC` EventSource | `WbPromoSyncSource` + `OzonPromoSyncSource`. Read adapters, normalizers, canonical upsert, FK resolution |
| `ADVERTISING_FACT` EventSource | Registered as no-op stubs (`WbAdvertisingFactSource`, `OzonAdvertisingFactSource`). Full spec: [Advertising](advertising.md) §ETL Pipeline |
| Ingest orchestration decomposition | `IngestOrchestrator` → thin shell; `IngestJobAcquisitionService` (CAS/reclaim), `IngestSyncContextBuilder` (context), `IngestJobCompletionCoordinator` (retry/fail/materialize), `SyncDispatcher` (scheduled dispatch with `@Transactional` via AOP proxy) |
| Async post-ingest materialization | `PostIngestMaterializationMessageHandler` processes `ETL_POST_INGEST_MATERIALIZE` via `etl.sync` queue; CAS failure reconciles sync state |
| `IngestResultReporter` consolidation | Unified sync state transitions (SYNCING/IDLE/ERROR), configurable `syncInterval` (replaces hardcoded 6h), injectable `Clock` for testability |
| `SyncHealth.SYNCING` | Backend enum + frontend type; `ConnectionSyncHealthService` correctly maps SYNCING (was erroneously mapped to STALE) |
| Eager reconcile | `ConnectionStaleJobReconciler` for per-connection staleness before dispatch; reconciles sync state after stale detection |
| Poison pill recovery | `EtlSyncConsumer` catch block attempts `reconcileSyncingWhenNoActiveJob` with best-effort `connectionId` extraction |
| Post-sync outbox events | `IngestResultReporter.recordSuccessfulTerminalSync()` writes `ETL_SYNC_COMPLETED` to outbox (with sync state IDLE) in one transactional step. Consumer routing via RabbitMQ |
| Stale campaign detection | `StaleCampaignDetector` marks campaigns with `synced_at` older than threshold as ENDED and publishes `ETL_PROMO_CAMPAIGN_STALE` outbox event |

### Not yet implemented

| Area | Notes |
|------|-------|
| `ADVERTISING_FACT` full implementation | v2→v3 migration, Ozon OAuth2 token service, credential resolution. Full spec: [Advertising](advertising.md) §ETL Pipeline |
| ClickHouse materialization | `ClickHouseMaterializer` is a stub. ClickHouse schema (`0001-initial.sql`) created but not populated |

## Связанные модули

- [Integration](integration.md) — marketplace connections, credentials, rate limits
- [Analytics & P&L](analytics-pnl.md) — materialization targets (facts, dims, marts)
- [Pricing](pricing.md) — читает canonical state для decision-grade data; post-sync trigger ([§Post-sync outbox events](#post-sync-outbox-events))
- [Promotions](promotions.md) — `PROMO_SYNC` обеспечивает promo discovery; ETL materializer пишет в canonical `canonical_promo_campaign` / `canonical_promo_product` (PostgreSQL) и `dim_promo_campaign` / `fact_promo_product` (ClickHouse)
- Детальные контракты: [Provider API Specs](../provider-api-specs/)
