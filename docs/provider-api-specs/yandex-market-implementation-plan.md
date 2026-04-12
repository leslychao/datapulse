# Yandex Market — Implementation Plan

**Status:** Draft
**Date:** 2026-04-12
**Based on:** yandex-read-contracts.md, mapping-spec.md (Yandex sections), write-contracts.md (§3), empirical-verification-log.md (Session 6)

---

## 1. Цель интеграции и MVP scope

### Что значит "поддержка Яндекс.Маркета" для продукта

Datapulse — это multi-marketplace аналитическая платформа. Добавление Яндекс.Маркета расширяет охват до трёх крупнейших российских маркетплейсов (WB, Ozon, YM). Для пользователя это означает: один дашборд для P&L, остатков, ценообразования и возвратов по всем трём площадкам.

### Бизнес-сценарии MVP

Полный цикл: **read → canonical → analytics → pricing → execution**.

1. **Каталог** — загрузка товарного ассортимента с ценами, брендами, категориями в canonical model
2. **Остатки** — мониторинг FBY/FBS stocks по складам
3. **Заказы** — загрузка заказов с детализацией по товарам и статусам
4. **Возвраты** — загрузка возвратов с причинами
5. **Финансы** — P&L через async reports (services cost, goods realization)
6. **Справочники** — склады, категории
7. **Ценообразование** — чтение текущих цен + запись новых цен через `offer-prices/updates`
8. **Аналитика** — материализация в ClickHouse star schema, P&L marts

### Что НЕ входит в MVP

- Stock management (PUT stocks) — FBY управляется Яндексом, FBS/DBS — фаза 2
- Order processing (accept, ship, cancel) — не scope Datapulse
- Promotions write (участие в акциях) — read-only в MVP
- Advertising bids write (PUT bids) — read-only в MVP
- Content management (создание/обновление карточек)
- Webhooks / push-уведомления
- OAuth token flow — только Api-Key auth

---

## 2. Подтверждённые источники данных

| # | API endpoint | Доменная задача | Уровень | Обяз. | Readiness | Rate limit |
|---|---|---|---|---|---|---|
| 1 | `GET /v2/campaigns` | Discovery магазинов (businessId/campaignId) | Global | Must | **READY** (empirical 200 OK) | 1K/h |
| 2 | `POST /v2/businesses/{id}/offer-mappings` | Каталог + цены + бренды | Business | Must | READY (docs) | 100/min |
| 3 | `POST /v2/businesses/{id}/offer-prices` | Цены (альтернативный read) | Business | Nice | READY (docs) | Shares w/ #2 |
| 4 | `POST /v2/campaigns/{id}/offers/stocks` | Остатки по складам | Campaign | Must | READY (docs) | 100K items/min |
| 5 | `POST /v1/businesses/{id}/orders` | Заказы (v1, business-level) | Business | Must | READY (docs) | 10K/h |
| 6 | `GET /v2/campaigns/{id}/returns` | Возвраты | Campaign | Must | PARTIAL (docs URL 404) | Unknown |
| 7 | `POST /v2/reports/united-marketplace-services/generate` | Финансовый отчёт (services) | Business | Must | READY (docs) | Unknown |
| 8 | `POST /v2/reports/goods-realization/generate` | Отчёт реализации | Business | Must | READY (docs) | Unknown |
| 9 | `GET /v2/reports/info/{reportId}` | Poll статуса отчёта | Global | Must | READY (docs) | Unknown |
| 10 | `GET /v2/warehouses` | Склады Яндекса (FBY) | Global | Must | READY (docs) | 100/h |
| 11 | `GET /v2/businesses/{id}/warehouses` | Склады продавца (FBS) | Business | Must | READY (docs) | Unknown |
| 12 | `POST /v2/categories/tree` | Дерево категорий | Global | Nice | READY (docs) | Unknown |
| 13 | `POST /v2/businesses/{id}/promos` | Список акций (read) | Business | Nice | READY (docs) | Unknown |
| 14 | `POST /v2/businesses/{id}/bids/info` | Текущие ставки (read) | Business | Nice | **READY** (empirical) | 500/min |
| 15 | `POST /v2/tariffs/calculate` | Калькулятор тарифов | Global | Nice | READY (docs) | Unknown |
| 16 | `POST /v2/businesses/{id}/offer-prices/updates` | **Запись цен** | Business | Must | READY (docs) | 10K items/min |

Полный контракт каждого endpoint: [yandex-read-contracts.md](yandex-read-contracts.md), [write-contracts.md](write-contracts.md) §3.

---

## 3. Минимально достаточный scope

### Must have (MVP)

| Capability | Endpoints | Canonical target | Зависимости |
|---|---|---|---|
| Campaign discovery | #1 | `connection_metadata` | — |
| Catalog + prices | #2 | `product_master`, `seller_sku`, `marketplace_offer`, `canonical_price_current` | Campaign discovery |
| Stocks | #4 | `canonical_stock` | Campaign discovery (fan-out per campaign) |
| Orders | #5 | `canonical_order` | Catalog (join по offerId) |
| Returns | #6 | `canonical_return` | Campaign discovery (fan-out), orders |
| Finance reports | #7, #8, #9 | `canonical_finance_entry` | New async capture pattern |
| Warehouses | #10, #11 | `dim_warehouse` | — |
| Price write | #16 | `price_action` | Catalog (offerId resolution) |

### Nice to have (Phase 2+)

| Capability | Endpoints | Зачем |
|---|---|---|
| Category tree | #12 | Обогащение категорийной иерархии |
| Promos read | #13 | `dim_promo_campaign`, `fact_promo_product` |
| Bids read | #14 | Advertising analytics |
| Tariff calculator | #15 | Предиктивный P&L (расчёт комиссий до продажи) |

---

## 4. Схема внедрения в текущую архитектуру

### 4.1 MarketplaceType.YANDEX — добавление значения в enum

**Файл:** `datapulse-integration/.../domain/MarketplaceType.java`

Текущие значения: `WB`, `OZON`. Добавить `YANDEX`.

Последствия:
- `EventSourceRegistry` автоматически начнёт резолвить `(YANDEX, eventType)` пары
- `RateLimitGroup` — нужны новые группы (§4.2)
- `PriceReadAdapter` / `PriceWriteAdapter` — маппинг по `marketplace()` (§4.5)
- `connection.marketplace_type` — новый enum value в БД
- `marketplace_sync_state` — новые строки при создании connection
- `source_platform = 'yandex'` в canonical tables и ClickHouse

### 4.2 datapulse-integration

#### YandexHealthProbe

Реализация `MarketplaceHealthProbe` для Яндекса.

**Endpoint для health check:** `POST /v2/businesses/{businessId}/offer-mappings` с `{"limit": 1}` (минимальный запрос).

Альтернатива: `GET /v2/campaigns` — единственный endpoint, работающий даже для disabled business. Но он не проверяет доступ к данным, только валидность токена.

**Решение:** двухэтапная проверка:
1. `GET /v2/campaigns` — проверка валидности токена + discovery businessId/campaignId
2. `POST /v2/businesses/{businessId}/offer-mappings` с `limit=1` — проверка доступа к данным

Если шаг 2 возвращает 403 `API_DISABLED` — connection status = `AUTH_FAILED` с message key `integration.connection.yandex.business_disabled`.

#### CredentialKeys / CredentialMapper для YANDEX

Яндекс использует один credential: Api-Key token.

```
secret_type = 'YANDEX'
secret fields:
  api_key: "ACMA:..."
```

В отличие от Ozon (два поля: `client_id` + `api_key`), Яндекс — единственное поле.
В отличие от WB (один JWT token), Яндекс — opaque string с фиксированным форматом `ACMA:base64:hex`.

#### YANDEX rate limit groups

Новые значения в `RateLimitGroup` enum:

| Group | initialRate (req/s) | burst | Обоснование |
|---|---|---|---|
| `YANDEX_DEFAULT` | 1.5 | 4 | Параллелизм ≤4 (community-reported), запас для 100 req/min |
| `YANDEX_CATALOG` | 1.5 | 4 | 100 req/min (600 w/ subscription) |
| `YANDEX_ORDERS` | 2.5 | 5 | 10K req/h ≈ 2.8 req/s |
| `YANDEX_BIDS` | 8.0 | 10 | 500 req/min ≈ 8.3 req/s |
| `YANDEX_PRICE_UPDATE` | 2.0 | 5 | 10K items/min, batches of 500 → ~20 req/min |
| `YANDEX_REPORTS` | 0.5 | 2 | Conservative — rate limit unknown |
| `YANDEX_WAREHOUSES` | 0.02 | 1 | 100 req/h ≈ 0.03 req/s |

Все с `MarketplaceType.YANDEX`.

**HTTP 420 handling:** `MarketplaceRateLimiter.onThrottle()` должен реагировать и на 420, не только на 429. Проверить текущую реализацию — если привязана к `WebClientResponseException.TooManyRequests` (429), расширить на 420.

#### Connection setup: auto-discover campaigns

При создании / валидации connection для `YANDEX`:
1. `GET /v2/campaigns` → список `(campaignId, placementType, businessId)`
2. Сохранить campaigns в `marketplace_connection.metadata` (JSONB):

```json
{
  "businessId": 67890,
  "campaigns": [
    {"campaignId": 12345, "placementType": "FBS"},
    {"campaignId": 12346, "placementType": "FBY"}
  ]
}
```

**Refresh:** при каждом sync — вызывать `GET /v2/campaigns` и обновлять metadata (campaigns могут добавляться/удаляться).

### 4.3 datapulse-etl — adapter/yandex/

#### Структура пакетов

```
adapter/yandex/
├── YandexApiCaller.java          — WebClient, base URL, Api-Key header
├── YandexNormalizer.java          — Provider DTO → Normalized model
├── YandexCatalogReadAdapter.java  — offer-mappings (catalog + prices)
├── YandexPriceReadAdapter.java    — offer-prices (dedicated price read)
├── YandexStocksReadAdapter.java   — stocks per campaign (fan-out)
├── YandexOrdersReadAdapter.java   — orders v1 (business-level)
├── YandexReturnsReadAdapter.java  — returns per campaign (fan-out)
├── YandexWarehousesReadAdapter.java — warehouses (Yandex + seller)
├── YandexCategoryTreeReadAdapter.java — categories tree
├── YandexFinanceReportReadAdapter.java — async report (generate/poll/download)
├── YandexPromoReadAdapter.java    — promos list + promo offers
├── YandexBidsReadAdapter.java     — bids/info (sales boost)
└── dto/
    ├── YandexCampaign.java
    ├── YandexCampaignsResponse.java
    ├── YandexOfferMapping.java
    ├── YandexOffer.java
    ├── YandexMapping.java
    ├── YandexOfferMappingsResponse.java
    ├── YandexPriceItem.java
    ├── YandexStockItem.java
    ├── YandexStockWarehouse.java
    ├── YandexStocksResponse.java
    ├── YandexOrder.java
    ├── YandexOrderItem.java
    ├── YandexOrdersResponse.java
    ├── YandexReturnItem.java
    ├── YandexReturnsResponse.java
    ├── YandexWarehouse.java
    ├── YandexWarehousesResponse.java
    ├── YandexReportGenerateResponse.java
    ├── YandexReportStatusResponse.java
    ├── YandexServicesReportRow.java
    ├── YandexRealizationReportRow.java
    ├── YandexPromo.java
    ├── YandexPromosResponse.java
    ├── YandexBidItem.java
    └── YandexBidsResponse.java
```

#### YandexApiCaller

Аналог `WbApiCaller` / `OzonApiCaller`. Основные отличия:

- **Auth header:** `Api-Key` (не `Authorization` как у WB, не `Client-Id + Api-Key` как у Ozon)
- **Base URL:** `https://api.partner.market.yandex.ru` (единый хост для всех endpoints, в отличие от WB с 6+ хостами)
- **Response envelope:** `{"status": "OK", "result": {...}}` — unwrap `result` перед возвратом
- **Error handling:** 420 = rate limit (не 429), 403 `API_DISABLED` = disabled business
- **Rate limit group:** передаётся per-call (разные endpoints — разные группы)

```java
@Service
@RequiredArgsConstructor
public class YandexApiCaller {

  private final WebClient.Builder webClientBuilder;
  private final IntegrationProperties properties;
  private final MarketplaceRateLimiter rateLimiter;

  public Flux<DataBuffer> post(
      String path,
      long connectionId,
      RateLimitGroup group,
      String apiKey,
      Object body) {
    // rateLimiter.acquire → webClient.post() → path → header("Api-Key", apiKey) → body
  }

  public Flux<DataBuffer> get(
      String path,
      long connectionId,
      RateLimitGroup group,
      String apiKey) {
    // rateLimiter.acquire → webClient.get() → path → header("Api-Key", apiKey)
  }
}
```

#### YandexNormalizer

Маппинг provider DTO → Normalized model. Один класс с методами:

- `List<NormalizedCatalogItem> normalizeCatalog(List<YandexOfferMapping> items)`
- `List<NormalizedPriceItem> normalizePrices(List<YandexOfferMapping> items)`
- `List<NormalizedStockItem> normalizeStocks(List<YandexStockWarehouse> warehouses)`
- `List<NormalizedOrderItem> normalizeOrders(List<YandexOrder> orders)`
- `List<NormalizedReturnItem> normalizeReturns(List<YandexReturnItem> returns)`
- `List<NormalizedFinanceItem> normalizeServicesReport(List<YandexServicesReportRow> rows)` — TBD
- `List<NormalizedFinanceItem> normalizeRealizationReport(List<YandexRealizationReportRow> rows)` — TBD

Полный маппинг полей: [mapping-spec.md](mapping-spec.md) (секции Yandex → Normalized*).

#### КРИТИЧЕСКИ: YandexFinanceReportReadAdapter — async report pattern

Новый паттерн, отсутствующий у WB и Ozon (они используют синхронные paginated API для финансов).

**Flow:**
1. `POST /v2/reports/{type}/generate` → `reportId`
2. Poll: `GET /v2/reports/info/{reportId}` → status `PENDING` / `GENERATING` / `DONE` / `FAILED`
3. При `DONE`: download file по URL из `result.file`
4. Parse CSV/JSON с `@JsonIgnoreProperties(ignoreUnknown = true)` — структура может меняться (F-6)

**Polling strategy:**
- Initial wait: 5 секунд
- Poll interval: 5 секунд
- Max attempts: 60 (= 5 минут max wait)
- Backoff: fixed (async reports обычно генерируются за секунды-минуты)

**Два типа отчётов:**
- `united-marketplace-services` — по дате начисления (`dateFrom`/`dateTo`) или по месяцу (`year`/`month`)
- `goods-realization` — только по месяцу (`year`/`month`)

**Реализация:** отдельный utility class `AsyncReportCapture` (или метод в `YandexFinanceReportReadAdapter`) — не в `CursorPagedCapture` / `OffsetPagedCapture`, так как паттерн принципиально другой.

### 4.4 datapulse-etl — domain/source/yandex/ (10 EventSource)

По аналогии с WB и Ozon — 10 реализаций `EventSource`, по одной на каждый `EtlEventType`:

| # | EtlEventType | Source class | Adapter dependency | Особенности |
|---|---|---|---|---|
| 1 | `CATEGORY_DICT` | `YandexCategoryDictSource` | `YandexCategoryTreeReadAdapter` | POST /v2/categories/tree. Если endpoint не критичен — stub (return success, 0 records), как `WbCategoryDictSource` |
| 2 | `WAREHOUSE_DICT` | `YandexWarehouseDictSource` | `YandexWarehousesReadAdapter` | Два вызова: FBY warehouses (GET /v2/warehouses) + seller warehouses (GET /v2/businesses/{id}/warehouses) |
| 3 | `PRODUCT_DICT` | `YandexProductDictSource` | `YandexCatalogReadAdapter` | POST offer-mappings → cursor pagination. Также нормализует prices из той же response (dual output) |
| 4 | `PRICE_SNAPSHOT` | `YandexPriceSnapshotSource` | `YandexCatalogReadAdapter` или `YandexPriceReadAdapter` | Цены встроены в offer-mappings. Можно переиспользовать данные из PRODUCT_DICT или вызвать offer-prices отдельно |
| 5 | `INVENTORY_FACT` | `YandexInventoryFactSource` | `YandexStocksReadAdapter` | **Campaign-level fan-out:** для каждого campaignId из connection metadata вызвать stocks endpoint |
| 6 | `SUPPLY_FACT` | `YandexSupplyFactSource` | — | **Stub/no-op.** Яндекс не имеет supply endpoint, аналогичного WB. Return `List.of(SubSourceResult.success("YandexSupplyFact", 0, 0))` |
| 7 | `SALES_FACT` | `YandexSalesFactSource` | `YandexOrdersReadAdapter`, `YandexReturnsReadAdapter` | Orders (business-level) + returns (campaign-level fan-out). Нет отдельного sales endpoint как у WB |
| 8 | `FACT_FINANCE` | `YandexFinanceFactSource` | `YandexFinanceReportReadAdapter` | **Async reports.** Генерация двух отчётов: services cost + goods realization. Новый capture pattern |
| 9 | `PROMO_SYNC` | `YandexPromoSyncSource` | `YandexPromoReadAdapter` | Promos list + promo offers. Business-level |
| 10 | `ADVERTISING_FACT` | `YandexAdvertisingFactSource` | `YandexBidsReadAdapter` | Bids read (POST bids/info). Stub в MVP — full implementation в Phase 2 |

**Регистрация:** каждый class = `@Component implements EventSource`. Spring auto-discovery через `List<EventSource>` injection в `EventSourceRegistry`. Резолвится по ключу `(MarketplaceType.YANDEX, EtlEventType.*)`.

### 4.5 datapulse-execution — adapter/yandex/

Новый пакет `adapter/yandex/` в `datapulse-execution`:

```
adapter/yandex/
├── YandexPriceReadAdapter.java    — implements PriceReadAdapter
├── YandexPriceWriteAdapter.java   — implements PriceWriteAdapter
└── dto/
    ├── YandexUpdatePricesRequest.java
    └── YandexUpdatePricesResponse.java
```

#### YandexPriceReadAdapter

`marketplace()` → `YANDEX`

`readCurrentPrice(connectionId, offerId, credentials)` → вызов `POST /v2/businesses/{businessId}/offer-prices` с фильтром по `offerId`, извлечение `price.value`.

**businessId resolution:** из `connection.metadata.businessId` (discovered при setup).

#### YandexPriceWriteAdapter

`marketplace()` → `YANDEX`

`setPrice(connectionId, offerId, targetPrice, credentials)` → вызов `POST /v2/businesses/{businessId}/offer-prices/updates`:

```json
{
  "offers": [{
    "offerId": "SKU-001",
    "price": { "value": 5000, "currencyId": "RUR" }
  }]
}
```

**Особенности vs WB/Ozon:**
- **Synchronous** (как Ozon) — response `{"status": "OK"}` = accepted
- **Blanket response** — нет per-item результата (F-7). Batch = atomic (assumed)
- **Quarantine risk** — после write проверять `POST /v2/businesses/{id}/price-quarantine`
- **Rate limit 420** — не 429
- **Price = decimal** (не integer как у WB)
- **Max 500 items per request**

**Reconciliation:**
1. Write → `status: OK`
2. Wait 30-60 секунд (Яндекс: «данные обновляются не мгновенно»)
3. Check quarantine → если quarantined → `RECONCILIATION_PENDING`
4. Read `offer-prices` → verify price applied
5. Mismatch → `RECONCILIATION_FAILED`

### 4.6 Canonical model — минимальные расширения

#### source_platform

Строковое значение `'yandex'` в canonical tables (PostgreSQL) и ClickHouse. Аналогично `'wb'` и `'ozon'`.

Затронутые таблицы: все canonical и fact tables, где есть `source_platform` / `marketplace_type`.

#### delivery_schema mapping (DD-25)

| Yandex `programType` / `placementType` | → canonical `delivery_schema` |
|---|---|
| `FBY` | `FBY` (аналог FBO у WB/Ozon) |
| `FBS` | `FBS` |
| `DBS` | `DBS` (новое значение, нет у WB/Ozon) |
| `EXPRESS` | `EXPRESS` (новое значение, нет у WB/Ozon) |

**Расширение enum:** `delivery_schema` в ClickHouse — добавить `DBS`, `EXPRESS` (для WB/Ozon эти значения не используются, но enum должен поддерживать).

#### campaign_id хранение

**Решение (DD-22):** campaigns хранятся в `marketplace_connection.metadata` JSONB. Отдельной таблицы `yandex_campaign` не создаём в MVP — это overengineering при текущем масштабе.

Для campaign-level данных (stocks, returns) в canonical entities добавить:
- `canonical_stock.source_campaign_id` (string, nullable) — для привязки к campaignId
- `canonical_return.source_campaign_id` (string, nullable)

Для WB/Ozon эти поля = NULL (single-level hierarchy).

---

## 5. Данные и модели

### 5.1 Новые reference data

| Reference | Source | Canonical target | Периодичность |
|---|---|---|---|
| Yandex fulfillment warehouses | `GET /v2/warehouses` | `dim_warehouse` | При sync (редко меняется) |
| Seller warehouses | `GET /v2/businesses/{id}/warehouses` | `dim_warehouse` | При sync |
| Category tree | `POST /v2/categories/tree` | `dim_category` (если используется) | При sync |
| Campaigns | `GET /v2/campaigns` | `connection.metadata` | При каждом sync |

### 5.2 Расширения canonical entities

**Минимальные аддитивные изменения:**

| Entity | Новое поле | Тип | Назначение |
|---|---|---|---|
| `canonical_stock` | `source_campaign_id` | `varchar(50)` nullable | campaignId для Yandex (WB/Ozon = NULL) |
| `canonical_return` | `source_campaign_id` | `varchar(50)` nullable | campaignId для Yandex (WB/Ozon = NULL) |
| `delivery_schema` enum | `DBS`, `EXPRESS` | — | Новые модели Яндекса |

**Не расширяем:**
- `canonical_order` — `programType` маппится в существующий `delivery_schema`
- `canonical_finance_entry` — структура универсальна, Яндекс report rows маппятся в неё
- `product_master`, `seller_sku`, `marketplace_offer` — всё покрывается текущими полями

### 5.3 Финансы через async reports — AsyncReportCapture

Новый utility для ETL pipeline, используемый только Яндексом:

```
domain/
└── capture/
    └── AsyncReportCapture.java
```

**Ответственность:**
1. Вызов generate endpoint → получение reportId
2. Polling status endpoint с backoff
3. Download file по URL (streaming → temp file)
4. Парсинг CSV/JSON

**Контракт:**
```java
public class AsyncReportCapture {

  public <T> List<T> captureReport(
      String generatePath,
      Object requestBody,
      Class<T> rowType,
      CaptureContext context) { ... }
}
```

**Не является новым EventSource** — это utility, вызываемый из `YandexFinanceFactSource`.

---

## 6. ETL / Ingestion сценарии

### 6.1 Initial load vs incremental

| Domain | Initial load | Incremental |
|---|---|---|
| Catalog | Full scan: POST offer-mappings, empty body, cursor pagination. Все товары | Same — no `updatedSince` filter. Full rescan каждый sync |
| Prices | Embedded in catalog response (basicPrice) | Same as catalog |
| Stocks | Full scan per campaign. POST stocks, cursor pagination | Same — snapshot каждый sync |
| Orders | POST orders с `dates.creationDateFrom` = epoch, `dateTo` = now. Max 30-day window → sliding window | `dates.updateDateFrom` = last sync checkpoint |
| Returns | GET returns, `fromDate` = epoch. Cursor pagination | `fromDate` = last sync checkpoint |
| Finance | Generate report for historical months | Generate report for current/previous month |
| Warehouses | GET all | Same (reference data, rarely changes) |

**Orders — 30-day window constraint:** max date range per request = 30 days. Initial load с историей > 30 дней → серия запросов с скользящим окном (месяц за месяцем). Заказы старше 30 дней (delivered/cancelled) не возвращаются основным endpoint → нужны finance reports для полной истории.

### 6.2 Async report jobs для финансов

**Pipeline (в рамках FACT_FINANCE event):**

1. Определить период: для initial load — по месяцам от `datapulse.etl.ingest.full-fact-lookback-days` до текущего; для incremental — текущий + предыдущий месяц
2. Для каждого месяца:
   a. Generate `united-marketplace-services` report
   b. Generate `goods-realization` report
   c. Poll обоих до `DONE`
   d. Download + parse
   e. Normalize → canonical_finance_entry
3. Batch upsert в PostgreSQL

**Параллелизм:** generate requests для разных месяцев можно отправлять параллельно (разные reportId). Polling — sequential per report.

**Checkpoint:** `job_execution.checkpoint` = `{"lastReportMonth": "2025-03"}` — месяц последнего успешно загруженного отчёта.

### 6.3 Дедупликация, идемпотентность

- **Catalog / stocks:** UPSERT по `(connection_id, seller_sku)` / `(connection_id, seller_sku, warehouse_id)` — идемпотентно
- **Orders:** UPSERT по `(connection_id, external_order_id)` — Яндекс `order.id` = уникальный across marketplace
- **Returns:** UPSERT по `(connection_id, external_return_id)` — Яндекс `return.id`
- **Finance reports:** UPSERT по `(connection_id, external_operation_id)` — нужен стабильный ID из отчёта. `ORDER_ID` + `SHOP_SKU` + column context = composite key. TBD после получения реальных отчётов

### 6.4 Retry-sensitive места

| Concern | Strategy |
|---|---|
| **4-parallel limit** (community-reported) | `YANDEX_DEFAULT` burst = 4. Все Yandex groups share connection-level concurrency |
| **420 rate limit** | `MarketplaceRateLimiter.onThrottle()` — extend to handle 420 alongside 429 |
| **Report generation FAILED** | Retry generate call (max 3 attempts). If FAILED persists — skip month, log warning |
| **Report download timeout** | Download URL has limited TTL (assumed). Retry poll → get fresh URL |
| **Campaign-level fan-out** | Sequential per campaign (avoid parallel burst across campaigns for same connection) |

---

## 7. Финансовая часть

### 7.1 United Marketplace Services Report для P&L

**Назначение:** комиссии и сервисные сборы (аналог Ozon services в `finance/transaction/list`).

**Ключевые колонки (confirmed-docs):** `ORDER_ID`, `YOUR_SKU`, `SHOP_SKU`, `OFFER_NAME`, `ORDER_CREATION_DATE_TIME`, `PLACEMENT_MODEL`, `PARTNER_ID`, `INN`.

**Что покрывает:** marketplace commission, logistics costs, storage fees, return handling fees — детали per-column TBD до получения реальных данных.

**Запрос:** по дате начисления (`dateFrom`/`dateTo`) или по дате акта (`year`/`month`).

### 7.2 Goods Realization Report для revenue

**Назначение:** факт реализации товаров — что было передано в доставку, доставлено, оплачено.

**Ключевые колонки (confirmed-docs):** `ORDER_ID`, `YOUR_ORDER_ID`, `YOUR_SKU`, `SHOP_SKU`, `ORDER_CREATION_DATE`, `TRANSFERRED_TO_DELIVERY_DATE`, `DELIVERY_DATE`, `TRANSFERRED_TO_DELIVERY_COUNT`, `PRICE_WITH_VAT_AND_NO_DISCOUNT`, `VAT`.

**Запрос:** только по месяцу (`year` + `month`).

### 7.3 Маппинг report columns → canonical_finance_entry

**Status: TBD (DD-23).** Полный маппинг заблокирован до:
1. Получения реальных отчётов с активного аккаунта
2. Верификации sign convention (DD-26)
3. Инвентаризации всех колонок (report structure may change)

**Предварительная модель:**
- Revenue: из `goods-realization` report → `PRICE_WITH_VAT_AND_NO_DISCOUNT` × `TRANSFERRED_TO_DELIVERY_COUNT`
- Commissions: из `united-marketplace-services` report → service-specific columns
- Attribution: `ORDER_ID` + `YOUR_SKU` → order-level + product-level

### 7.4 Sign convention (DD-26)

**Status: UNKNOWN.** Не верифицировано эмпирически.

**Предположение (assumed):** колонки отчёта разделяют credits и debits по имени колонки (как WB), НЕ по знаку (как Ozon). Это предположение основано на табличной структуре CSV-отчётов.

**Риск:** если convention окажется signed (как Ozon) — нужен перемаппинг всех finance columns. Это не блокирует реализацию адаптеров, но блокирует корректность P&L.

**Митигация:**
1. Первая загрузка — логировать raw report rows в S3 для ручной сверки
2. Маппинг конфигурируемый (не хардкод знака)
3. Data quality alert на аномальные суммы (отрицательная выручка, положительные расходы)

### 7.5 Gaps и компромиссы vs WB/Ozon streaming finance

| Aspect | WB | Ozon | Yandex |
|---|---|---|---|
| Finance API | GET synchronous (streaming) | POST synchronous (paginated) | **Async reports only** |
| Granularity | Per-transaction row | Per-operation | **Per-report-row (TBD)** |
| Real-time | ~24h delay | Near real-time | **Hours-days (report generation)** |
| Sign convention | All positive, name = direction | Signed (pos=credit, neg=debit) | **Unknown** |
| Reconciliation residual | ~3-5% | ~0% | **Unknown** |

**Ключевой gap:** Яндекс не предоставляет real-time транзакционные данные. P&L для Яндекса будет обновляться с задержкой (часы-дни vs минуты у WB/Ozon). Это acceptable для MVP — финансовая аналитика по определению не real-time.

---

## 8. Риски

### R-YM-1: Контракты верифицированы только по документации (HIGH)

**Описание:** Disabled business account заблокировал все endpoints кроме `GET /v2/campaigns`. Все field-level данные основаны на official docs (`confirmed-docs`), не на реальных ответах API.

**Митигация:**
1. `@JsonIgnoreProperties(ignoreUnknown = true)` на всех Yandex DTO
2. Логирование первых N response body каждого endpoint при первом подключении реального аккаунта (→ S3)
3. Contract tests с WireMock (16 стабов уже созданы: `infra/wiremock/mappings/yandex-*.json`)
4. Alert при неизвестных полях (custom deserializer или post-parse validation)
5. Data quality checks с порогами на NULL-rate per field

### R-YM-2: Finance sign convention unknown (HIGH)

**Описание:** Не определено, как интерпретировать суммы в финансовых отчётах. Блокирует корректность P&L.

**Митигация:**
1. Реализовать finance adapter с configurable sign mapping
2. При первой загрузке реальных данных — ручная сверка с кабинетом Яндекс.Маркета
3. Не показывать Яндекс P&L в UI до верификации sign convention

### R-YM-3: Async report structure may change without notice (MEDIUM)

**Описание:** Яндекс явно заявляет (F-6): «Структура и содержание отчетов могут изменяться без предварительного уведомления.»

**Митигация:**
1. Lenient parser с `@JsonIgnoreProperties(ignoreUnknown = true)`
2. Validation: проверять наличие обязательных колонок после парсинга
3. Alert при исчезновении ожидаемых колонок
4. Версионирование парсера: при обнаружении изменений — обновить маппинг, задокументировать

### R-YM-4: Rate limits снижаются после 18.05.2026 (MEDIUM)

**Описание:** Текущие лимиты временно повышены. После deadline: campaigns 5000→1000/h, catalog 600→100/min, bids 1000→500/min.

**Митигация:**
1. Rate limit groups настроены на POST-deadline значения (conservative)
2. Мониторинг 420 responses — если растёт, снизить rate
3. AIMD в `MarketplaceRateLimiter` адаптирует rate автоматически

### R-YM-5: Отсутствие sandbox (MEDIUM)

**Описание:** Яндекс Market не имеет отдельного sandbox-окружения (F-7). Тестирование — только production API + WireMock.

**Митигация:**
1. WireMock stubs для всех 16 endpoints (уже созданы)
2. Integration tests через WireMock
3. `application-local.yml` — Yandex URLs → WireMock

### R-YM-6: Campaign fan-out complexity (LOW)

**Описание:** Stocks и returns — campaign-level endpoints. Нужен fan-out per campaignId, что добавляет сложность по сравнению с WB/Ozon (single-level).

**Митигация:**
1. Campaigns cached в connection metadata, refresh при каждом sync
2. Sequential fan-out (не parallel) — избежать burst rate limit
3. SubSourceResult per campaign — отслеживать partial failures

### R-YM-7: Orders retention — 30 days (LOW)

**Описание:** Основной orders endpoint не возвращает заказы, доставленные/отменённые >30 дней назад.

**Митигация:**
1. При initial load — скользящее окно по 30 дней
2. Для полной финансовой истории — finance reports (которые не ограничены 30 днями)
3. Incremental sync с checkpoint `updateDateFrom` — покрывает текущие заказы

---

## 9. Пошаговый roadmap

### Этап 1: Discovery / Contracts — DONE

**Цель:** понять API, задокументировать контракты, верифицировать эмпирически где возможно.

**Результат:**
- `yandex-read-contracts.md` — полный справочник read endpoints
- `write-contracts.md` §3 — price write contract
- `mapping-spec.md` — Yandex → Normalized маппинги
- `empirical-verification-log.md` Session 6 — эмпирическая верификация
- WireMock stubs (16 файлов) — готовы
- Этот план

**Критерий готовности:** ✅ Выполнен.

### Этап 2: Integration module

**Цель:** YANDEX как полноценный marketplace type в системе.

**Результат:**
- `MarketplaceType.YANDEX` в enum
- `YANDEX_*` rate limit groups в `RateLimitGroup` enum
- `YandexHealthProbe` — health check implementation
- Credential schema: `secret_type = 'YANDEX'`, single field `api_key`
- Connection setup: auto-discover campaigns, save to metadata
- `application-local.yml`: Yandex base URL → WireMock
- Liquibase migration: `marketplace_type` enum extension
- 420 HTTP handling in `MarketplaceRateLimiter`

**Зависимости:** нет (первый этап реализации)

**Критерий готовности:**
- [ ] `POST /api/connections` с `marketplace_type: YANDEX` создаёт connection
- [ ] Health probe валидирует токен (через WireMock)
- [ ] Rate limit groups зарегистрированы и работают
- [ ] Integration tests проходят

### Этап 3: ETL adapters + normalizer

**Цель:** загрузка данных с API Яндекса в normalized model.

**Результат:**
- `YandexApiCaller` — HTTP client
- `YandexNormalizer` — маппинг provider DTO → normalized
- Read adapters: Catalog, Prices, Stocks, Orders, Returns, Warehouses, CategoryTree
- `YandexFinanceReportReadAdapter` + `AsyncReportCapture` — async report pipeline
- Provider DTOs в `adapter/yandex/dto/`
- Unit tests для normalizer

**Зависимости:** Этап 2 (MarketplaceType, rate limits, URL routing)

**Критерий готовности:**
- [ ] Каждый adapter загружает данные из WireMock stubs
- [ ] Normalizer тесты покрывают все маппинги из mapping-spec.md
- [ ] AsyncReportCapture: generate → poll → download → parse работает с WireMock
- [ ] Campaign fan-out для stocks/returns работает

### Этап 4: ETL sources (10 EventSource)

**Цель:** интеграция adapters в ETL pipeline через EventSource pattern.

**Результат:**
- 10 классов в `domain/source/yandex/` (таблица из §4.4)
- Регистрация в `EventSourceRegistry` (автоматическая через `@Component`)
- `YandexSupplyFactSource` — stub (no-op)
- `YandexAdvertisingFactSource` — stub в MVP

**Зависимости:** Этап 3 (adapters ready)

**Критерий готовности:**
- [ ] `EventSourceRegistry.resolve(YANDEX, eventType)` возвращает source для всех 10 event types
- [ ] Full sync job для YANDEX connection проходит (с WireMock)
- [ ] Sub-source results логируются корректно
- [ ] DAG зависимости соблюдаются (CATEGORY_DICT/WAREHOUSE_DICT → PRODUCT_DICT → parallel facts)

### Этап 5: Canonical mapping + materialization

**Цель:** normalized data → canonical PostgreSQL → ClickHouse star schema.

**Результат:**
- Canonical upsert для всех Yandex domains (catalog, prices, stocks, orders, returns)
- `source_platform = 'yandex'` в canonical/ClickHouse
- `delivery_schema` enum extension (DBS, EXPRESS)
- Liquibase migration: `source_campaign_id` columns
- Materialization в ClickHouse: `fact_sales`, `fact_finance`, `fact_price_snapshot`, etc.
- Finance mapping: **partial** — маппинг TBD до real data, но pipeline operational

**Зависимости:** Этап 4 (sources produce normalized data)

**Критерий готовности:**
- [ ] Canonical tables populated with Yandex data (from WireMock)
- [ ] ClickHouse materialization includes Yandex data
- [ ] `delivery_schema` DBS/EXPRESS visible in analytics
- [ ] Data quality checks pass (NULL rates within thresholds)

### Этап 6: Execution adapters (price write)

**Цель:** ценообразование работает для Яндекс.Маркета.

**Результат:**
- `YandexPriceReadAdapter` — read current prices
- `YandexPriceWriteAdapter` — write prices
- Quarantine check logic
- Registration in `LivePriceActionGateway` (auto via `marketplace()`)
- WireMock stubs для price update (success/error/rate-limit — уже есть)

**Зависимости:** Этап 2 (rate limits), Этап 3 (API caller)

**Критерий готовности:**
- [ ] Price action для YANDEX проходит lifecycle: PENDING → EXECUTING → SUCCEEDED
- [ ] Rate limit 420 → retry → success
- [ ] Validation error (price=0) → FAILED
- [ ] Reconciliation read after write

### Этап 7: Validation + rollout

**Цель:** подключение реального аккаунта, верификация контрактов, запуск в production.

**Результат:**
- Подключение активного Яндекс аккаунта
- Верификация всех field-level контрактов на реальных данных
- Finance sign convention — подтверждение или коррекция
- Raw response logging (первые 5 вызовов каждого endpoint → S3)
- Data quality review: reconciliation residual, NULL rates, P&L sanity
- Обновление `yandex-read-contracts.md` confidence levels (confirmed-docs → confirmed)

**Зависимости:** Этапы 2–6 (полная реализация), активный Яндекс аккаунт

**Критерий готовности:**
- [ ] Реальные данные загружены в canonical
- [ ] P&L для Яндекса отображается в UI (или маркирован как unverified)
- [ ] Все confidence levels = confirmed или confirmed с known limitations
- [ ] Finance sign convention verified и задокументирована
- [ ] Alerting настроен на Yandex-specific anomalies

---

## 10. Validation strategy

### Contract tests (WireMock)

16 WireMock stubs уже созданы в `infra/wiremock/`. Для каждого adapter — integration test с WireMock:

```java
@SpringJUnitConfig
@Testcontainers
class YandexCatalogReadAdapterTest {
  // WireMock stub: yandex-offer-mappings.json
  // Verify: response parsed correctly, all fields mapped
}
```

### Sample reconciliation cases

При подключении реального аккаунта:
1. **Catalog:** сравнить количество товаров из Datapulse vs кабинет Яндекс.Маркета
2. **Stocks:** сравнить остатки по 5 SKU с кабинетом
3. **Orders:** сравнить заказы за последние 7 дней — count + total amount
4. **Finance:** сравнить P&L за последний закрытый месяц с кабинетом (если sign convention verified)

### First-connection validation mode

При первом подключении реального YM-аккаунта:
1. **Raw response logging:** сохранять response body первых 5 вызовов каждого endpoint в S3 (`s3://datapulse-raw/{connection_id}/yandex-validation/{endpoint}/{timestamp}.json`)
2. **Schema validation:** после парсинга — проверять наличие всех mandatory fields из mapping-spec
3. **Field coverage report:** для каждого DTO — процент non-null полей vs ожидаемых
4. **Alert:** если > 20% полей NULL для обязательного маппинга — data quality warning

### Data quality checks

| Check | Threshold | Action |
|---|---|---|
| NULL rate per mandatory field | > 5% | Warning alert |
| Zero-price products | > 10% of catalog | Warning alert |
| Orders with unknown status | Any | Error alert |
| Finance report empty for month with orders | — | Error alert |
| Reconciliation residual (finance - orders) | > 10% | Warning alert (calibrate after baseline) |

---

## 11. Observability

### Логирование

| Компонент | Уровень | Формат |
|---|---|---|
| `YandexApiCaller` requests | DEBUG | `"Yandex API call: method={}, path={}, connectionId={}, status={}, elapsed={}ms"` |
| `YandexApiCaller` errors | ERROR | `"Yandex API error: path={}, status={}, errorCode={}, message={}"` |
| Rate limit 420 | WARN | `"Yandex rate limited: path={}, connectionId={}, group={}"` |
| Report generation | INFO | `"Yandex report: type={}, reportId={}, status={}"` |
| Campaign discovery | INFO | `"Yandex campaigns discovered: connectionId={}, count={}, types={}"` |
| Normalizer skips | DEBUG | `"Yandex normalizer skip: field={}, reason={}, offerId={}"` |

### Метрики

| Метрика | Тип | Labels |
|---|---|---|
| `datapulse_yandex_api_calls_total` | counter | `endpoint`, `status`, `connection_id` |
| `datapulse_yandex_api_latency_seconds` | histogram | `endpoint` |
| `datapulse_yandex_rate_limit_hits_total` | counter | `endpoint`, `group` |
| `datapulse_yandex_report_generation_seconds` | histogram | `report_type` |
| `datapulse_yandex_report_failures_total` | counter | `report_type` |
| `datapulse_yandex_sync_records_total` | counter | `domain` (catalog/stocks/orders/...) |

### Retry visibility

- Каждый retry логируется с attempt number и причиной
- 420 retries отдельно от 500 retries (разные root causes)
- Report poll retries — логировать каждый 10-й poll (не каждый — шум)

### Data completeness

- После каждого sync — сравнить количество записей per domain с предыдущим sync
- Резкое падение (> 50% drop) — alert
- Новые NULL-only поля — alert (признак schema change)

### Alerting

| Alert | Условие | Severity |
|---|---|---|
| Yandex sync failed | Job status = FAILED | HIGH |
| Yandex rate limit sustained | > 10 hits per 5 min | MEDIUM |
| Yandex report generation failed | 3 consecutive failures | HIGH |
| Yandex schema drift detected | Unknown fields in response | LOW |
| Yandex data quality degraded | NULL rate > threshold | MEDIUM |

---

## 12. Финальный вывод

### Достаточен ли уровень изученности для начала разработки?

**Да, с оговорками.**

Yandex Market Partner API задокументирован на высоком уровне. Официальная документация содержит типизированные схемы, примеры ответов, исчерпывающие enum-ы. 16 WireMock stubs созданы. Маппинг в canonical model определён (mapping-spec.md).

### Что ещё не хватает

| Gap | Severity | Когда закрыть |
|---|---|---|
| Эмпирическая верификация data-level | HIGH | Этап 7 (подключение реального аккаунта) |
| Finance sign convention | HIGH | Этап 7 (первый реальный отчёт) |
| Finance report full column inventory | MEDIUM | Этап 7 |
| Returns endpoint docs gap (URL 404) | LOW | Этап 7 (verify on real data) |
| Rate limits for undocumented endpoints | LOW | Runtime AIMD adaptation |

### Можно ли идти в implementation сразу?

**Да.** Этапы 2–6 можно реализовать на основе документации + WireMock, не дожидаясь реального аккаунта.

**Порядок:**
1. Этап 2 (Integration) — можно начинать немедленно
2. Этап 3 (ETL adapters) — параллельно с этапом 2 (после MarketplaceType.YANDEX)
3. Этап 6 (Execution) — параллельно с этапами 3–4 (зависит только от API caller и rate limits)
4. Этапы 4–5 (Sources + Canonical) — после этапа 3
5. Этап 7 (Validation) — после подключения реального аккаунта

**Единственный hard blocker:** finance sign convention (DD-26) блокирует **корректность P&L**, но не блокирует реализацию pipeline. Pipeline можно собрать, а маппинг скорректировать при получении реальных данных.
