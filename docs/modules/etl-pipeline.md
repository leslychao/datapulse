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
| Реклама | Кампании, статистика (показы, клики, расход) |

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
| Идемпотентность | SHA-256 от serialized payload; `ON CONFLICT DO NOTHING` |
| Dedup key | `(request_id, source_id, record_key)` |
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
| Provenance | Каждая запись прослеживаема до raw source |

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
s3://{bucket}/raw/{account_id}/{event}/{source_id}/{request_id}/page-{N}.json
```

| Компонент | Назначение |
|-----------|------------|
| `raw/` | Prefix для raw layer |
| `{account_id}` | Tenant isolation + partition для retention |
| `{event}` | Тип ETL event |
| `{source_id}` | Конкретный source (класс адаптера) |
| `{request_id}` | UUID конкретного ETL run |
| `page-{N}.json` | Номер страницы пагинации |

### Read path: S3 → Normalization

Стратегия write-then-read: сначала пишем в S3, потом читаем оттуда для нормализации. Соответствует принципу DB-first. При crash — retry из S3 без повторного API call.

Streaming JSON parse через Jackson streaming API (batch=500 records, memory ~1.5 MB).

### Cursor extraction

Три семейства pagination определяют стратегию cursor extraction:

| Семейство | Endpoints | Стратегия | Overhead |
|-----------|-----------|-----------|----------|
| 1: Externally-paged | ~11 endpoints (WB Orders/Sales/Returns/Incomes/Offices, WB Prices/Stocks offset, Ozon Orders/Returns/Catalog info) | `NoCursorExtractor` — нет extraction | 0 |
| 2: Metadata cursor | ~5 endpoints (WB Catalog, Ozon Catalog list, Ozon Stocks/Prices/Finance) | `JsonPathCursorExtractor` — парсит temp file post-write | < 1 ms |
| 3: Data-derived cursor | 1 endpoint (WB Finance) | `TailFieldExtractor` — читает tail 32 KB из temp file | < 1 ms |

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
| `status` | VARCHAR(32) | CAPTURED → PROCESSED → ARCHIVED |
| `captured_at` | TIMESTAMPTZ | Время захвата |

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
| `CanonicalOffer` | State | Товарное предложение | sellerSku, marketplaceSku, name, brand, category, status |
| `CanonicalPriceSnapshot` | State | Снимок цены | price, discountPrice, currency, capturedAt |
| `CanonicalStockSnapshot` | State | Снимок остатков | available, reserved, warehouseId |
| `CanonicalOrder` | Flow | Заказ/отправление | externalOrderId, quantity, pricePerUnit, status |
| `CanonicalSale` | Flow | Продажа | saleAmount, commission |
| `CanonicalReturn` | Flow | Возврат | returnAmount, returnReason, returnDate |
| `CanonicalFinanceEntry` | Flow | Финансовая операция | entryType, amount (нормализованный знак), entryDate |

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
| Категории | `CATEGORY_DICT` | `dim_category` | — |
| Склады | `WAREHOUSE_DICT` | `dim_warehouse` | — |
| Товары | `PRODUCT_DICT` | `dim_product` | `product_master`, `seller_sku`, `marketplace_offer` |
| Цены | `PRICE_SNAPSHOT` | `fact_price_snapshot` | `canonical_price_snapshot` |
| Продажи (Ozon) | `SALES_FACT` | `fact_orders`, `fact_sales`, `fact_returns`, dim backfill | `canonical_order`, `canonical_sale`, `canonical_return` |
| Продажи (WB) | `SALES_FACT` | `fact_orders`, dim_product backfill | `canonical_order` |
| Остатки | `INVENTORY_FACT` | `fact_inventory_snapshot` | `canonical_stock_snapshot` |
| Финансы (Ozon) | `FACT_FINANCE` | `fact_finance` | `canonical_finance_entry` |
| Финансы (WB) | `FACT_FINANCE` | `fact_sales`, `fact_returns`, `fact_finance` | `canonical_sale`, `canonical_return`, `canonical_finance_entry` |
| Реклама | `ADVERTISING_FACT` | `fact_advertising_costs` | — |
| Промо | `PROMO_SYNC` | `dim_promo_campaign`, `fact_promo_product` | — |

### Platform-specific правила

**WB sales/returns:** заполняются в обработчике `FACT_FINANCE`, не в `SALES_FACT`. WB `SALES_FACT` выполняет только dim_product backfill. Граф зависимостей (`FACT_FINANCE` depends on `SALES_FACT`) гарантирует порядок.

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
posting_number связывает orders ↔ sales ↔ returns ↔ finance

ACQUIRING (DD-15):
  acquiring.posting_number = order_number (без суффикса -N)
  Join: strip "-N" от posting_number → order_number

STANDALONE (storage, disposal, compensation):
  posting_number = "" → ключ = operation_id
  Аллокация на заказ — pro-rata

Важно: finance items[] содержит только sku + name, без offer_id.
Lookup: items[].sku → catalog sources[].sku → product_id → offer_id
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
| **Ozon** | `SALES_FACT` | Ozon returns — часть sales-домена |
| **WB** | `FACT_FINANCE` | WB returns извлекаются из строк финансового отчёта |

## Ingestion flow (per ETL event)

```
1. Scheduler / manual trigger → INSERT job_execution → INSERT outbox_event
2. Outbox poller → RabbitMQ → ingest-worker
3. Worker: fetch → HTTP → streaming write to temp file → S3 putObject → INSERT job_item
4. Worker: normalize → S3 getObject → streaming JSON parse (batch=500) → UPSERT canonical
5. Worker: materialize → canonical → ClickHouse (ReplacingMergeTree)
6. Update job_execution status, marketplace_sync_state
```

## Data provenance

Каждая каноническая запись прослеживаема до raw source:

1. Raw record хранится в S3; `job_item` — индекс raw layer в PostgreSQL.
2. Materialization привязывает canonical record к `job_item_id` / `job_execution_id`.
3. Fact/mart records содержат `account_id`, `source_platform`, привязку к canonical entities.
4. Audit log фиксирует materialization events.

## Модель данных: ETL-specific таблицы

| Таблица | Назначение |
|---------|------------|
| `job_execution` | ETL run: connection, event type, status, timing |
| `job_item` | Index raw payload в S3: s3_key, sha256, byte_size, status |
| `outbox_event` (ETL part) | Outbox для ETL step dispatch: ETL_STEP_EXECUTE, ETL_STEP_RETRY |

## Design decisions

### G-2: Ozon categories API — RESOLVED

`POST /v1/description-category/tree` с `{"language":"DEFAULT"}`. Возвращает иерархию: `description_category_id` + `category_name` + `children[]`.

### G-3: fact_supply (WB incomes) — RESOLVED

WB old incomes API `/api/v1/supplier/incomes` deprecated (June 2026). **Phase A/B**: использовать endpoint. **Post-deprecation**: FBS через `/api/v3/supplies`; FBO — manual import.

### G-7: dim_warehouse для WB — RESOLVED

Найден dedicated endpoint `GET /api/v3/offices` — полный список складов WB (Production: 225 offices). Join keys: finance `ppvz_office_id` → `offices.id`.

## Конфигурация MinIO

### Docker Compose

```yaml
minio:
  image: minio/minio:latest
  command: server /data --console-address ":9001"
  ports:
    - "9000:9000"
    - "9001:9001"
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
| Версионирование | SCD2: `valid_from`, `valid_to`. При обновлении: закрыть текущую запись, создать новую. |
| При отсутствии | Pricing: eligibility SKIP («Себестоимость не задана»). P&L: COGS = 0 (explicit, помечено в UI). |
| Validation | `cost_price > 0`, `currency = RUB`. |
| API | CRUD через datapulse-api: `POST /api/cost-profiles`, `PUT`, bulk import CSV. |
| Permission | ADMIN, PRICING_MANAGER. |

## Связанные модули

- [Integration](integration.md) — marketplace connections, credentials, rate limits
- [Analytics & P&L](analytics-pnl.md) — materialization targets (facts, dims, marts)
- [Pricing](pricing.md) — читает canonical state для decision-grade data
- Детальные контракты: [Provider API Specs](../provider-api-specs/)
