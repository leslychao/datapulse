# Yandex Market Integration — Implementation Prompts

**Инструкция:** открывай новый Cursor chat для каждого блока (Chat 1, Chat 2, ...).
В начале чата скопируй весь блок целиком (от заголовка `## Chat N` до следующего `## Chat`).
После завершения блока переходи к следующему чату.

**Правила для каждого чата:**
- Agent mode (не Ask mode)
- Перед каждым промптом внутри блока скажи: "Продолжай, промпт N"
- Если что-то сломалось — исправь в этом же чате, не переходи дальше
- После последнего промпта в блоке: проверь что проект компилируется (`mvn compile` backend)

**Сквозные ссылки (читать перед каждым чатом):**
- `docs/provider-api-specs/yandex-market-implementation-plan.md` — общий план (источник правды)
- `docs/provider-api-specs/yandex-read-contracts.md` — read API контракты
- `docs/provider-api-specs/write-contracts.md` §3 — price write контракт
- `docs/provider-api-specs/mapping-spec.md` — Yandex → Normalized маппинги (секции "Yandex →")

---

## Chat 1 — Integration module: enum + rate limits + credentials + health + config (промпты 1–3)

### Контекст для чата

Добавляю Яндекс.Маркет как третий маркетплейс в Datapulse (рядом с WB и Ozon).

Первый шаг — интеграционный модуль: MarketplaceType enum, rate limit groups, credential schema, health probe, connection setup.

Ключевые документы:
- `docs/provider-api-specs/yandex-market-implementation-plan.md` §4.1–§4.2
- `docs/provider-api-specs/yandex-read-contracts.md` §Authentication, §Rate Limits Summary
- `backend/datapulse-integration/src/main/java/io/datapulse/integration/domain/MarketplaceType.java` — текущий enum (WB, OZON)
- `backend/datapulse-integration/src/main/java/io/datapulse/integration/domain/ratelimit/RateLimitGroup.java` — rate limit groups
- `backend/datapulse-integration/src/main/java/io/datapulse/integration/domain/ratelimit/MarketplaceRateLimiter.java` — rate limiter (проверить 420 handling)
- `backend/datapulse-integration/src/main/java/io/datapulse/integration/domain/MarketplaceHealthProbe.java` — health probe interface
- `backend/datapulse-integration/src/main/java/io/datapulse/integration/domain/WbHealthProbe.java` — WB health probe для reference
- `backend/datapulse-integration/src/main/java/io/datapulse/integration/config/IntegrationProperties.java` — integration config

### Промпт 1 — MarketplaceType.YANDEX + RateLimitGroup + HTTP 420

1. Добавь `YANDEX` в `MarketplaceType` enum (`datapulse-integration/.../domain/MarketplaceType.java`).

2. Добавь rate limit groups в `RateLimitGroup` enum. Посмотри формат существующих entries (initialRate, burst, marketplaceType):

   | Group | initialRate (req/s) | burst | Обоснование |
   |---|---|---|---|
   | `YANDEX_DEFAULT` | 1.5 | 4 | Параллелизм ≤4 (community-reported), базовый для 100 req/min |
   | `YANDEX_CATALOG` | 1.5 | 4 | 100 req/min (600 w/ subscription до 18.05.2026) |
   | `YANDEX_ORDERS` | 2.5 | 5 | 10K req/h ≈ 2.8 req/s |
   | `YANDEX_BIDS` | 8.0 | 10 | 500 req/min ≈ 8.3 req/s |
   | `YANDEX_PRICE_UPDATE` | 2.0 | 5 | 10K items/min, batches of 500 |
   | `YANDEX_REPORTS` | 0.5 | 2 | Conservative — rate limit unknown |
   | `YANDEX_WAREHOUSES` | 0.02 | 1 | 100 req/h ≈ 0.03 req/s |

   Все с `MarketplaceType.YANDEX`.

3. **HTTP 420 handling:** Яндекс возвращает **420** (не 429) при rate limit exceeded. Проверь `MarketplaceRateLimiter` — если `onThrottle()` реагирует только на 429 (через `WebClientResponseException.TooManyRequests`), расширь:
   - В месте, где ловится HTTP status для throttling, добавь проверку на status == 420
   - Это может быть в `YandexApiCaller` (создаём в Chat 2) — ловить 420 и вызывать `rateLimiter.onThrottle()`
   - Или в общем обработчике — найди где именно сейчас перехватывается 429 и добавь 420 рядом

### Промпт 2 — Credentials + Health Probe + Connection Metadata

1. **Credential schema для YANDEX.** Яндекс использует один credential: Api-Key token (opaque string, формат `ACMA:base64:hex`). Найди как определены credential keys для WB (`Authorization` header) и Ozon (`Client-Id` + `Api-Key`). Добавь аналогичную запись для YANDEX:
   - Secret type: `YANDEX`
   - Единственное поле: `api_key`
   - Auth header: `Api-Key: {value}` (НЕ `Authorization` как у WB!)

   Найди `CredentialKeys` или аналогичную конфигурацию и добавь YANDEX mapping.

2. **YandexHealthProbe** — создай реализацию `MarketplaceHealthProbe` в integration module (рядом с `WbHealthProbe`, `OzonHealthProbe`):
   - `marketplace()` → `YANDEX`
   - Двухэтапная проверка:
     a. `GET /v2/campaigns` — проверка валидности токена. Этот endpoint работает даже для disabled business (единственный!). Из response извлечь businessId и список campaigns
     b. Если campaigns пустой → connection status = `AUTH_FAILED`, message = `integration.connection.yandex.business_disabled`
     c. Если campaigns не пустой → `ACTIVE`
   - Сохранить discovered data в connection metadata (см. пункт 3)
   - API Key передаётся в header `Api-Key` (НЕ `Authorization`)

3. **Connection metadata (campaign discovery).** При validate/health check для YANDEX connection — сохранить обнаруженные campaigns в `marketplace_connection.metadata` (JSONB). Структура:
   ```json
   {
     "businessId": 67890,
     "campaigns": [
       {"campaignId": 12345, "placementType": "FBS"},
       {"campaignId": 12346, "placementType": "FBY"}
     ]
   }
   ```
   Найди где WB/Ozon health probes сохраняют результаты (если сохраняют) и используй тот же механизм. Если metadata ещё не используется — предложи минимальный способ хранения.

4. Добавь MessageCodes:
   ```java
   public static final String INTEGRATION_YANDEX_BUSINESS_DISABLED = "integration.connection.yandex.business_disabled";
   public static final String INTEGRATION_YANDEX_TOKEN_INVALID = "integration.connection.yandex.token_invalid";
   ```

5. Добавь переводы в `frontend/src/locale/ru.json`:
   ```json
   "integration.connection.yandex.business_disabled": "Яндекс.Маркет: бизнес-аккаунт отключён (нет активных магазинов)",
   "integration.connection.yandex.token_invalid": "Яндекс.Маркет: невалидный Api-Key токен"
   ```

### Промпт 3 — IntegrationProperties + application-local.yml + Liquibase

1. **IntegrationProperties** — добавь секцию для Yandex. Посмотри как настроены `wildberries.*-base-url` и `ozon.seller-base-url` в `IntegrationProperties`:
   ```
   datapulse.integration.yandex.base-url=https://api.partner.market.yandex.ru
   datapulse.integration.yandex.write-base-url=https://api.partner.market.yandex.ru
   ```
   У Яндекса единый хост для всех endpoints (в отличие от WB с 6+ хостами).

2. **application-local.yml** — добавь local override (WireMock):
   ```yaml
   datapulse:
     integration:
       yandex:
         base-url: http://${WIREMOCK_HOST:localhost}:${WIREMOCK_PORT:9091}
         write-base-url: http://${WIREMOCK_HOST:localhost}:${WIREMOCK_PORT:9091}
   ```

3. **Liquibase migration** — создай миграцию для расширения canonical model (план §4.6):

   Файл: `backend/datapulse-api/src/main/resources/db/changelog/changes/NNNN-yandex-canonical-extensions.sql` (используй следующий номер после последней миграции):

   ```sql
   --liquibase formatted sql

   --changeset datapulse:NNNN-yandex-canonical-extensions

   ALTER TABLE canonical_stock ADD COLUMN IF NOT EXISTS source_campaign_id varchar(50);
   ALTER TABLE canonical_return ADD COLUMN IF NOT EXISTS source_campaign_id varchar(50);

   --rollback ALTER TABLE canonical_return DROP COLUMN IF EXISTS source_campaign_id;
   --rollback ALTER TABLE canonical_stock DROP COLUMN IF EXISTS source_campaign_id;
   ```

   Добавь include в `db.changelog-master.yaml`.

   **Примечание:** Если таблицы `canonical_stock` / `canonical_return` имеют другие имена в реальной БД — посмотри реальные имена и скорректируй.

**Чеклист после Chat 1:**
- [ ] `MarketplaceType.YANDEX` добавлен
- [ ] 7 rate limit groups для YANDEX добавлены
- [ ] HTTP 420 handling подготовлен (или задокументирован для Chat 2)
- [ ] Credential schema для YANDEX настроена
- [ ] `YandexHealthProbe` создан с campaign discovery
- [ ] Connection metadata: businessId + campaigns сохраняются
- [ ] `IntegrationProperties` содержит yandex base URLs
- [ ] `application-local.yml` содержит WireMock override для Yandex
- [ ] Liquibase migration для canonical extensions создана
- [ ] MessageCodes + ru.json дополнены
- [ ] `mvn compile` проходит

---

## Chat 2 — ETL infrastructure: YandexApiCaller + DTOs + Normalizer (промпты 4–6)

### Контекст для чата

Chat 1 добавил MarketplaceType.YANDEX, rate limit groups, credentials, health probe, config.

Сейчас: инфраструктура для ETL — HTTP client, provider DTOs, normalizer.

Reference:
- `docs/provider-api-specs/yandex-read-contracts.md` — response structures для каждого endpoint
- `docs/provider-api-specs/mapping-spec.md` — Yandex → Normalized маппинги
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/wb/WbApiCaller.java` — WB API caller для reference
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/ozon/OzonApiCaller.java` — Ozon API caller для reference
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/wb/WbNormalizer.java` — WB normalizer для reference
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/ozon/OzonNormalizer.java` — Ozon normalizer для reference

### Промпт 4 — YandexApiCaller

Создай `YandexApiCaller` в `io.datapulse.etl.adapter.yandex` (пакет `adapter/yandex/`):

По аналогии с `WbApiCaller` и `OzonApiCaller`, но с отличиями:

1. **Auth header:** `Api-Key` (не `Authorization` как у WB, не `Client-Id + Api-Key` как у Ozon)
2. **Base URL:** единый — из `IntegrationProperties` (`datapulse.integration.yandex.base-url`)
3. **Response envelope:** Яндекс оборачивает все ответы в `{"status": "OK", "result": {...}}`. ApiCaller должен:
   - Проверить `status == "OK"`
   - Вернуть содержимое `result` (не весь response)
   - При `status == "ERROR"` → бросить исключение с error code из `errors[0].code`
4. **HTTP 420 handling:** при получении status 420 → вызвать `rateLimiter.onThrottle()` и пробросить retryable exception (аналогично 429 для WB/Ozon)
5. **Methods:**
   - `Flux<DataBuffer> get(String path, long connectionId, RateLimitGroup group, String apiKey)` — для GET endpoints
   - `Flux<DataBuffer> post(String path, long connectionId, RateLimitGroup group, String apiKey, Object body)` — для POST endpoints
   - `Flux<DataBuffer> postNoBody(String path, long connectionId, RateLimitGroup group, String apiKey)` — для POST без body

Посмотри `WbApiCaller` для точного паттерна (WebClient, rateLimiter.acquire, streaming response, error handling) и адаптируй.

### Промпт 5 — Provider DTOs

Создай provider DTOs в `io.datapulse.etl.adapter.yandex.dto`. Все DTO — records с `@JsonIgnoreProperties(ignoreUnknown = true)` (Яндекс может менять структуру отчётов без уведомления, F-6).

Группируй по домену. Для каждого — поля из `yandex-read-contracts.md`:

**Campaigns:**
- `YandexCampaignsResponse(List<YandexCampaign> campaigns, YandexPaging paging)`
- `YandexCampaign(long id, YandexBusiness business, String placementType, String apiAvailability, String domain)`
- `YandexBusiness(long id, String name)`
- `YandexPaging(String nextPageToken)` — общий для всех endpoints

**Catalog (offer-mappings):**
- `YandexOfferMappingsResponse(YandexPaging paging, List<YandexOfferMapping> offerMappings)`
- `YandexOfferMapping(YandexOffer offer, YandexMapping mapping)`
- `YandexOffer(String offerId, String name, String vendor, List<String> barcodes, String vendorCode, String description, YandexWeightDimensions weightDimensions, YandexBasicPrice basicPrice, YandexBasicPrice purchasePrice, String cardStatus, List<YandexOfferCampaign> campaigns, List<YandexSellingProgram> sellingPrograms, boolean archived, String groupId)`
- `YandexBasicPrice(BigDecimal value, String currencyId)`
- `YandexWeightDimensions(BigDecimal length, BigDecimal width, BigDecimal height, BigDecimal weight)`
- `YandexMapping(Long marketSku, String marketSkuName, String marketModelName, Integer marketCategoryId, String marketCategoryName)`
- `YandexOfferCampaign(long campaignId, String status)`
- `YandexSellingProgram(String sellingProgram, String status)`

**Stocks:**
- `YandexStocksResponse(YandexPaging paging, List<YandexStockWarehouse> warehouses)`
- `YandexStockWarehouse(long warehouseId, List<YandexStockOffer> offers)`
- `YandexStockOffer(String offerId, List<YandexStockEntry> stocks, String updatedAt, YandexTurnover turnoverSummary)`
- `YandexStockEntry(String type, int count)`
- `YandexTurnover(String turnover, BigDecimal turnoverDays)`

**Orders:**
- `YandexOrdersResponse(YandexPaging paging, List<YandexOrder> orders)`
- `YandexOrder(long id, String status, String substatus, String creationDate, String updatedAt, String paymentType, String programType, long campaignId, List<YandexOrderItem> items, YandexDelivery delivery)`
- `YandexOrderItem(String offerId, String offerName, Long marketSku, int count, List<YandexOrderPrice> prices)`
- `YandexOrderPrice(String type, BigDecimal costPerItem, BigDecimal total)`
- `YandexDelivery(String deliveryPartnerType, String type, List<YandexShipment> shipments, YandexRegion region)`
- `YandexShipment(String shipmentDate)`
- `YandexRegion(int id, String name)`

**Returns:**
- `YandexReturnsResponse(YandexPaging paging, List<YandexReturn> returns)`
- `YandexReturn(long id, long orderId, String creationDate, String updateDate, String returnStatus, String returnType, List<YandexReturnItem> items)`
- `YandexReturnItem(Long marketSku, String shopSku, int count, String decisionType, YandexReturnReason returnReason)`
- `YandexReturnReason(String type, String description)`

**Warehouses:**
- `YandexWarehousesResponse(List<YandexWarehouse> warehouses)`
- `YandexWarehouse(long id, String name, YandexAddress address)`
- `YandexAddress(String city, String street, String number, YandexGps gps)`
- `YandexGps(BigDecimal latitude, BigDecimal longitude)`

**Reports:**
- `YandexReportGenerateResponse(String reportId, int estimatedGenerationTime)`
- `YandexReportStatusResponse(String status, String file, String generationRequestedAt, String generationFinishedAt)`
- `YandexServicesReportRow(...)` — TBD, full columns after empirical verification. Stub с основными: `Long orderId, String yourSku, String shopSku, String offerName, String orderCreationDateTime, String placementModel, Long partnerId`
- `YandexRealizationReportRow(...)` — TBD. Stub: `Long orderId, String yourOrderId, String yourSku, String shopSku, String orderCreationDate, String deliveryDate, Integer transferredToDeliveryCount, BigDecimal priceWithVatAndNoDiscount, String vat`

**Promos:**
- `YandexPromosResponse(List<YandexPromo> promos)`
- `YandexPromo(String promoId, String name, String status, List<String> channels, String startDate, String endDate, String mechanicsType, String participationType)`

**Bids:**
- `YandexBidsResponse(YandexPaging paging, List<YandexBidItem> bids)`
- `YandexBidItem(String sku, int bid)`

**Prices (offer-prices):**
- `YandexOfferPricesResponse(YandexPaging paging, List<YandexOfferPriceItem> offers)`
- `YandexOfferPriceItem(String offerId, YandexBasicPrice price, String updatedAt)`

### Промпт 6 — YandexNormalizer

Создай `YandexNormalizer` в `io.datapulse.etl.adapter.yandex`:
- @Service (без @RequiredArgsConstructor — stateless utility с маппинг-методами)

Маппинги из `mapping-spec.md` (секции "Yandex → Normalized*"):

1. `List<NormalizedCatalogItem> normalizeCatalog(List<YandexOfferMapping> items)`:
   - `sellerSku` = `offer.offerId`
   - `marketplaceSku` = `String.valueOf(mapping.marketSku)` (long → string)
   - `name` = `offer.name`
   - `brand` = `offer.vendor` (прямо в offer-mappings, не нужен secondary API как у Ozon)
   - `category` = `mapping.marketCategoryName`
   - `barcode` = `offer.barcodes` (первый элемент или null)
   - `vendorCode` = `offer.vendorCode`
   - `status` = derive: `offer.archived` → "ARCHIVED", else по `offer.cardStatus`
   - `weight` = `offer.weightDimensions.weight`
   - `length/width/height` = from `weightDimensions`
   - Посмотри `WbNormalizer.normalizeCatalog()` для типа `NormalizedCatalogItem` и адаптируй

2. `List<NormalizedPriceItem> normalizePrices(List<YandexOfferMapping> items)`:
   - `sellerSku` = `offer.offerId`
   - `price` = `offer.basicPrice.value`
   - `discountPrice` = null (Яндекс не имеет marketing_seller_price эквивалента)
   - `currency` = normalize `offer.basicPrice.currencyId`: "RUR" → "RUB"
   - `purchasePrice` = `offer.purchasePrice.value` (informational)

3. `List<NormalizedStockItem> normalizeStocks(List<YandexStockWarehouse> warehouses)`:
   - Развернуть иерархию: warehouse → offers → stock entries
   - `sellerSku` = `offer.offerId`
   - `warehouseId` = `String.valueOf(warehouse.warehouseId)`
   - `available` = stock entry where type == "AVAILABLE" → count
   - `reserved` = stock entry where type == "FREEZE" → count
   - `total` = stock entry where type == "FIT" → count
   - `updatedAt` = `offer.updatedAt` (parse ISO 8601)

4. `List<NormalizedOrderItem> normalizeOrders(List<YandexOrder> orders)`:
   - Развернуть: order → items
   - `externalOrderId` = `String.valueOf(order.id)`
   - `sellerSku` = `item.offerId`
   - `quantity` = `item.count`
   - `pricePerUnit` = find price where type == "BUYER" → `costPerItem`
   - `totalAmount` = find price where type == "BUYER" → `total`
   - `currency` = "RUB" (implicit)
   - `orderDate` = `order.creationDate` (parse "YYYY-MM-DD" → LocalDate)
   - `status` = `order.status`
   - `fulfillmentType` = `order.programType` (FBY/FBS/DBS/EXPRESS → delivery_schema, DD-25)
   - `region` = `order.delivery.region.name`

5. `List<NormalizedReturnItem> normalizeReturns(List<YandexReturn> returns)`:
   - Развернуть: return → items
   - `externalReturnId` = `String.valueOf(ret.id)`
   - `externalOrderId` = `String.valueOf(ret.orderId)`
   - `sellerSku` = `item.shopSku`
   - `quantity` = `item.count`
   - `returnDate` = `ret.creationDate` (parse "YYYY-MM-DD" → LocalDate)
   - `reason` = `item.returnReason.type`
   - `status` = `ret.returnStatus`

Посмотри `WbNormalizer` и `OzonNormalizer` для точных типов `NormalizedCatalogItem`, `NormalizedPriceItem`, etc. и адаптируй.

**Чеклист после Chat 2:**
- [ ] `YandexApiCaller` — GET/POST с Api-Key auth, response envelope unwrap, 420 handling
- [ ] ~40 provider DTO records в `adapter/yandex/dto/`
- [ ] `YandexNormalizer` — 5 маппинг-методов (catalog, prices, stocks, orders, returns)
- [ ] `mvn compile` проходит

---

## Chat 3 — ETL read adapters: dict & snapshot domains (промпты 7–9)

### Контекст для чата

Chat 1: integration module (enum, rate limits, credentials, health probe, config).
Chat 2: YandexApiCaller, provider DTOs, YandexNormalizer.

Сейчас: read adapters для справочных и snapshot доменов (catalog, prices, warehouses, categories, stocks).

Reference:
- `docs/provider-api-specs/yandex-read-contracts.md` §1–§4 (Campaigns, Catalog, Prices, Stocks)
- `docs/provider-api-specs/yandex-read-contracts.md` §8 (Warehouses)
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/wb/WbCatalogReadAdapter.java` — WB catalog adapter для reference
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/util/` — CursorPagedCapture, StreamingPageCapture и другие utilities

### Промпт 7 — YandexCatalogReadAdapter + YandexPriceReadAdapter

В `io.datapulse.etl.adapter.yandex`:

1. `YandexCatalogReadAdapter` — @Service, @RequiredArgsConstructor:
   - Inject: `YandexApiCaller`, `IntegrationProperties`, пагинация utility (посмотри `CursorPagedCapture` или аналог)
   - Method: `void captureAllPages(CaptureContext context, String apiKey, long businessId)`
   - Endpoint: `POST /v2/businesses/{businessId}/offer-mappings`
   - Body: пустой `{}` (все товары) или `{"archived": false}` для фильтрации
   - Pagination: cursor-based — `pageToken` query param + `paging.nextPageToken` in response
   - Max page size: 100
   - Rate limit group: `YANDEX_CATALOG`
   - Termination: `nextPageToken` absent or empty
   - Output: streaming pages to S3/temp (по паттерну WbCatalogReadAdapter)

2. `YandexPriceReadAdapter` — @Service, @RequiredArgsConstructor:
   - Method: `void captureAllPages(CaptureContext context, String apiKey, long businessId)`
   - Endpoint: `POST /v2/businesses/{businessId}/offer-prices`
   - Pagination: cursor-based, same pattern
   - Rate limit group: `YANDEX_CATALOG` (shares with offer-mappings)
   - **Альтернатива:** цены можно получить из offer-mappings (offer.basicPrice). Если решим переиспользовать данные из catalog — этот adapter не нужен как отдельный. Создай его, но отметь комментарием что может быть заменён dual-output из catalog

### Промпт 8 — YandexStocksReadAdapter + YandexWarehousesReadAdapter + YandexCategoryTreeReadAdapter

1. `YandexStocksReadAdapter` — @Service, @RequiredArgsConstructor:
   - Method: `void captureAllPages(CaptureContext context, String apiKey, List<Long> campaignIds)`
   - **КРИТИЧЕСКИ: Campaign-level fan-out.** Endpoint `POST /v2/campaigns/{campaignId}/offers/stocks` — требует campaignId, НЕ businessId.
   - Adapter принимает список campaignIds (из connection.metadata.campaigns), вызывает endpoint per campaign
   - Body: пустой `{}` (все товары)
   - Pagination: cursor-based, max page size 200
   - Rate limit: `YANDEX_DEFAULT` (rate limit считается в items, не requests — 100K items/min)
   - Для каждого campaign: capture pages → stream to context

2. `YandexWarehousesReadAdapter` — @Service, @RequiredArgsConstructor:
   - Method: `void capture(CaptureContext context, String apiKey, long businessId)`
   - **Два вызова:**
     a. `GET /v2/warehouses` — Yandex fulfillment warehouses (global, не business-level, но заблокирован для disabled business)
     b. `GET /v2/businesses/{businessId}/warehouses` — seller warehouses
   - Без пагинации (reference data, мало записей)
   - Rate limit: `YANDEX_WAREHOUSES`
   - Объединить результаты в одну коллекцию

3. `YandexCategoryTreeReadAdapter` — @Service, @RequiredArgsConstructor:
   - Method: `void capture(CaptureContext context, String apiKey)`
   - Endpoint: `POST /v2/categories/tree`
   - Body: пустой `{}`
   - Без пагинации (дерево целиком)
   - Rate limit: `YANDEX_DEFAULT`

### Промпт 9 — YandexPromoReadAdapter + YandexBidsReadAdapter

1. `YandexPromoReadAdapter` — @Service, @RequiredArgsConstructor:
   - Method: `void captureAllPages(CaptureContext context, String apiKey, long businessId)`
   - Endpoint: `POST /v2/businesses/{businessId}/promos`
   - Pagination: cursor-based
   - Rate limit: `YANDEX_DEFAULT`

2. `YandexBidsReadAdapter` — @Service, @RequiredArgsConstructor:
   - Method: `void captureAllPages(CaptureContext context, String apiKey, long businessId)`
   - Endpoint: `POST /v2/businesses/{businessId}/bids/info`
   - **КРИТИЧЕСКИ (F-2):** Read endpoint = `POST /bids/info`, НЕ `POST /bids` (который принимает только PUT для записи)
   - Body: пустой `{}` (все SKU с ставками)
   - Pagination: cursor-based, max page size 500
   - Rate limit: `YANDEX_BIDS`

**Чеклист после Chat 3:**
- [ ] `YandexCatalogReadAdapter` — cursor-paginated capture
- [ ] `YandexPriceReadAdapter` — cursor-paginated capture (or note as optional)
- [ ] `YandexStocksReadAdapter` — campaign-level fan-out + cursor pagination
- [ ] `YandexWarehousesReadAdapter` — two GET calls merged
- [ ] `YandexCategoryTreeReadAdapter` — single POST
- [ ] `YandexPromoReadAdapter` — cursor-paginated
- [ ] `YandexBidsReadAdapter` — POST bids/info (не bids!)
- [ ] `mvn compile` проходит

---

## Chat 4 — ETL read adapters: facts + async finance (промпты 10–12)

### Контекст для чата

Chats 1–3: integration module, API caller, DTOs, normalizer, dict/snapshot read adapters.

Сейчас: fact-domain adapters (orders, returns) и **async finance report** adapter — новый паттерн, отсутствующий у WB/Ozon.

Reference:
- `docs/provider-api-specs/yandex-read-contracts.md` §5 (Orders), §6 (Returns), §7 (Finance Reports)
- `docs/provider-api-specs/yandex-market-implementation-plan.md` §4.3 (AsyncReportCapture), §6.2

### Промпт 10 — YandexOrdersReadAdapter + YandexReturnsReadAdapter

1. `YandexOrdersReadAdapter` — @Service, @RequiredArgsConstructor, @Slf4j:
   - Method: `void captureAllPages(CaptureContext context, String apiKey, long businessId, LocalDate dateFrom, LocalDate dateTo)`
   - Endpoint: `POST /v1/businesses/{businessId}/orders` (v1, business-level!)
   - **КРИТИЧЕСКИ (F-3):** НЕ `GET /v2/campaigns/{id}/orders` — этот deprecated!
   - Request body:
     ```json
     {
       "dates": {
         "creationDateFrom": "2025-01-01",
         "creationDateTo": "2025-01-31"
       },
       "fake": false
     }
     ```
   - **Max 30-day window:** если dateFrom→dateTo > 30 дней, нужно разбить на chunks по 30 дней (sliding window)
   - Pagination: cursor-based, max page size 50
   - Rate limit: `YANDEX_ORDERS`

2. `YandexReturnsReadAdapter` — @Service, @RequiredArgsConstructor, @Slf4j:
   - Method: `void captureAllPages(CaptureContext context, String apiKey, List<Long> campaignIds, LocalDate fromDate, LocalDate toDate)`
   - **Campaign-level fan-out:** endpoint `GET /v2/campaigns/{campaignId}/returns` — per campaign
   - Query params: `fromDate`, `toDate`, `limit`, `pageToken`
   - Pagination: cursor-based
   - Rate limit: `YANDEX_DEFAULT`
   - **Предупреждение:** docs URL для returns вернул 404 при проверке — контракт реконструирован по API probing. Может потребоваться корректировка при подключении реального аккаунта.

### Промпт 11 — AsyncReportCapture utility

Создай `AsyncReportCapture` в `io.datapulse.etl.adapter.yandex` (или в `io.datapulse.etl.domain.capture/` если хочешь сделать его общим):

- @Slf4j, не @Component (utility, инжектируется вручную или через конструктор)
- Inject: `YandexApiCaller`

**Контракт:**
```java
public <T> List<T> captureReport(
    String generatePath,
    Object requestBody,
    String apiKey,
    long connectionId,
    Class<T> rowType) { ... }
```

**Шаги:**

1. **Generate:** `POST {generatePath}` с requestBody → parse response как `YandexReportGenerateResponse` → extract `reportId`

2. **Poll:** цикл с backoff:
   - `GET /v2/reports/info/{reportId}` → parse `YandexReportStatusResponse`
   - Если `status == "PENDING"` или `"GENERATING"` → wait и poll снова
   - Если `status == "DONE"` → extract download URL из `result.file`
   - Если `status == "FAILED"` → throw exception
   - Polling strategy: initial wait 5s, poll interval 5s, max 60 attempts (5 min total)
   - Rate limit: `YANDEX_REPORTS`

3. **Download:** GET download URL → streaming response → temp file
   - Download URL — внешний (не api.partner.market.yandex.ru), поэтому прямой WebClient GET без Api-Key header
   - Parse JSON/CSV. Для MVP — JSON (если отчёт генерируется в JSON формате)

4. **Parse:** ObjectMapper с `@JsonIgnoreProperties(ignoreUnknown = true)` → `List<T>`
   - Lenient: неизвестные поля игнорируются (Яндекс может менять структуру, F-6)
   - Валидация: после парсинга проверить, что обязательные поля (ORDER_ID, YOUR_SKU) не null

5. **Error handling:**
   - Report generation failure → log.error + throw
   - Poll timeout (60 attempts) → log.error + throw
   - Download failure → retry 1 раз (URL может быть expired) → throw
   - Parse failure → log.error с первыми 500 chars raw response → throw

### Промпт 12 — YandexFinanceReportReadAdapter

Создай `YandexFinanceReportReadAdapter` — @Service, @RequiredArgsConstructor, @Slf4j:

Inject: `AsyncReportCapture` (или `YandexApiCaller` + внутренний capture), `IntegrationProperties`

**Два типа отчётов:**

1. Method `List<YandexServicesReportRow> captureServicesReport(String apiKey, long connectionId, long businessId, LocalDate dateFrom, LocalDate dateTo)`:
   - Generate path: `/v2/reports/united-marketplace-services/generate`
   - Body: `{"businessId": businessId, "dateFrom": "YYYY-MM-DD", "dateTo": "YYYY-MM-DD"}`
   - Returns parsed rows

2. Method `List<YandexRealizationReportRow> captureRealizationReport(String apiKey, long connectionId, long businessId, int year, int month)`:
   - Generate path: `/v2/reports/goods-realization/generate`
   - Body: `{"businessId": businessId, "year": year, "month": month}`
   - Returns parsed rows

3. Method `void captureFinanceForPeriod(CaptureContext context, String apiKey, long businessId, LocalDate from, LocalDate to)`:
   - Orchestrates both reports for the given period
   - Для initial load: вызывается per month (из EventSource)
   - Для incremental: текущий + предыдущий месяц
   - Нормализация через `YandexNormalizer` (добавь finance-related normalize methods если ещё не созданы — stub, TBD until real data)

**Чеклист после Chat 4:**
- [ ] `YandexOrdersReadAdapter` — 30-day sliding window + cursor pagination
- [ ] `YandexReturnsReadAdapter` — campaign fan-out + cursor pagination
- [ ] `AsyncReportCapture` — generate → poll → download → parse pipeline
- [ ] `YandexFinanceReportReadAdapter` — two report types + orchestration
- [ ] `mvn compile` проходит

---

## Chat 5 — ETL EventSources: 10 Yandex sources (промпты 13–15)

### Контекст для чата

Chats 1–4: integration module, API caller, DTOs, normalizer, все read adapters (7 штук + async finance).

Сейчас: 10 EventSource реализаций, по аналогии с WB и Ozon (10 per marketplace).

Reference:
- `docs/provider-api-specs/yandex-market-implementation-plan.md` §4.4 — таблица 10 sources
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/EventSource.java` — interface
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/source/wb/WbProductDictSource.java` — WB example
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/source/ozon/OzonProductDictSource.java` — Ozon example
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/source/wb/WbCategoryDictSource.java` — stub example (no-op)

### Промпт 13 — Dict sources: Category + Warehouse + Product

Создай пакет `io.datapulse.etl.domain.source.yandex` и три EventSource:

1. `YandexCategoryDictSource` — @Component:
   - `marketplace()` → `YANDEX`
   - `eventType()` → `CATEGORY_DICT`
   - `execute(IngestContext)`:
     - Extract apiKey from context credentials
     - Call `YandexCategoryTreeReadAdapter.capture(...)` — если endpoint доступен
     - Если categories не критичны для MVP — **stub:** return `List.of(SubSourceResult.success("YandexCategoryDict", 0, 0))` как `WbCategoryDictSource`
     - Normalize + batch upsert в canonical dim_category (если таблица используется)

2. `YandexWarehouseDictSource` — @Component:
   - `marketplace()` → `YANDEX`
   - `eventType()` → `WAREHOUSE_DICT`
   - `execute(IngestContext)`:
     - Extract apiKey + businessId from context
     - Call `YandexWarehousesReadAdapter.capture(context, apiKey, businessId)`
     - Normalize → upsert dim_warehouse
     - Return SubSourceResult с количеством записей

3. `YandexProductDictSource` — @Component:
   - `marketplace()` → `YANDEX`
   - `eventType()` → `PRODUCT_DICT`
   - `execute(IngestContext)`:
     - Extract apiKey + businessId from context
     - Call `YandexCatalogReadAdapter.captureAllPages(context, apiKey, businessId)`
     - Process pages: normalize catalog → batch upsert (product_master, seller_sku, marketplace_offer)
     - **Dual output:** также normalize prices из тех же данных → upsert canonical_price_current (если PRICE_SNAPSHOT source решит переиспользовать эти данные)
     - Посмотри `WbProductDictSource` для паттерна SubSourceRunner.processPages

**Извлечение businessId из IngestContext:** businessId хранится в connection.metadata JSONB (сохранён health probe в Chat 1). Найди как IngestContext предоставляет доступ к connection metadata и используй тот же механизм. Если прямого доступа нет — добавь helper method для извлечения Yandex-specific metadata.

### Промпт 14 — Snapshot + Inventory sources: Price + Inventory + Supply(stub)

1. `YandexPriceSnapshotSource` — @Component:
   - `eventType()` → `PRICE_SNAPSHOT`
   - `execute(IngestContext)`:
     - **Вариант A (рекомендуемый):** данные уже получены в PRODUCT_DICT (цены встроены в offer-mappings). Если dual-output реализован — этот source может быть no-op (return success)
     - **Вариант B:** отдельный вызов `YandexPriceReadAdapter` (offer-prices endpoint) или повторный `YandexCatalogReadAdapter`
     - Выбери вариант A если dual-output из PRODUCT_DICT реализован, иначе B

2. `YandexInventoryFactSource` — @Component:
   - `eventType()` → `INVENTORY_FACT`
   - `execute(IngestContext)`:
     - Extract apiKey + campaignIds from context
     - **Campaign fan-out:** для каждого campaignId из metadata.campaigns:
       - Call `YandexStocksReadAdapter.captureAllPages(context, apiKey, List.of(campaignId))`
       - Normalize → batch upsert canonical_stock (с source_campaign_id = campaignId)
     - Merge SubSourceResult per campaign

3. `YandexSupplyFactSource` — @Component:
   - `eventType()` → `SUPPLY_FACT`
   - **Stub/no-op.** Яндекс не имеет supply endpoint аналогичного WB.
   - `execute()`: return `List.of(SubSourceResult.success("YandexSupplyFact", 0, 0))`

### Промпт 15 — Fact sources: Sales + Finance + Promo + Advertising

1. `YandexSalesFactSource` — @Component:
   - `eventType()` → `SALES_FACT`
   - `execute(IngestContext)`:
     - **Orders (business-level):** call `YandexOrdersReadAdapter` с dateFrom/dateTo из context
     - Normalize orders → upsert canonical_order
     - **Returns (campaign fan-out):** call `YandexReturnsReadAdapter` per campaign
     - Normalize returns → upsert canonical_return (с source_campaign_id)
     - Return combined SubSourceResult

2. `YandexFinanceFactSource` — @Component:
   - `eventType()` → `FACT_FINANCE`
   - `execute(IngestContext)`:
     - Call `YandexFinanceReportReadAdapter.captureFinanceForPeriod(...)`
     - **Async reports:** generate → poll → download → parse (всё внутри adapter)
     - Normalize finance rows → upsert canonical_finance_entry
     - **Note:** finance normalize methods могут быть stub (TBD until real data, DD-23)
     - Return SubSourceResult

3. `YandexPromoSyncSource` — @Component:
   - `eventType()` → `PROMO_SYNC`
   - `execute(IngestContext)`:
     - Call `YandexPromoReadAdapter.captureAllPages(...)`
     - Normalize promos → upsert dim_promo_campaign
     - Return SubSourceResult

4. `YandexAdvertisingFactSource` — @Component:
   - `eventType()` → `ADVERTISING_FACT`
   - **Stub в MVP.** Full implementation = Phase 2.
   - `execute()`: call `YandexBidsReadAdapter` для сбора текущих ставок (read-only). Или no-op если bids data не нужны сейчас.
   - Return SubSourceResult

**Чеклист после Chat 5:**
- [ ] 10 EventSource классов в `domain/source/yandex/`
- [ ] `EventSourceRegistry.resolve(YANDEX, eventType)` возвращает source для всех 10 types
- [ ] Stubs: Supply (no-op), Advertising (minimal), PriceSnapshot (optional dual-output)
- [ ] Campaign fan-out в Inventory и SalesFact (returns)
- [ ] businessId extraction из connection metadata
- [ ] `mvn compile` проходит

---

## Chat 6 — Execution adapters: price write + reconciliation (промпты 16–18)

### Контекст для чата

Chats 1–5: полный ETL pipeline для Yandex (integration, adapters, normalizer, 10 sources).

Сейчас: execution layer — write price adapter для ценообразования.

Reference:
- `docs/provider-api-specs/write-contracts.md` §3 — Yandex price write contract
- `docs/provider-api-specs/yandex-market-implementation-plan.md` §4.5
- `backend/datapulse-execution/src/main/java/io/datapulse/execution/domain/PriceReadAdapter.java` — interface
- `backend/datapulse-execution/src/main/java/io/datapulse/execution/domain/PriceWriteAdapter.java` — interface
- `backend/datapulse-execution/src/main/java/io/datapulse/execution/adapter/wb/WbPriceWriteAdapter.java` — WB implementation
- `backend/datapulse-execution/src/main/java/io/datapulse/execution/adapter/ozon/OzonPriceWriteAdapter.java` — Ozon implementation

### Промпт 16 — YandexPriceWriteAdapter

Создай `io.datapulse.execution.adapter.yandex.YandexPriceWriteAdapter` — @Component, implements PriceWriteAdapter:

1. `marketplace()` → `YANDEX`

2. `setPrice(connectionId, offerId, targetPrice, credentials)`:
   - **businessId resolution:** extract from connection metadata (same mechanism as ETL)
   - Endpoint: `POST /v2/businesses/{businessId}/offer-prices/updates`
   - Header: `Api-Key: {credentials.get("api_key")}`
   - Body:
     ```json
     {
       "offers": [{
         "offerId": "<offerId>",
         "price": {
           "value": <targetPrice as BigDecimal>,
           "currencyId": "RUR"
         }
       }]
     }
     ```
   - **Price unit:** BigDecimal (рубли с копейками, decimal). НЕ integer как у WB.
   - **Rate limit:** `YANDEX_PRICE_UPDATE`
   - **Success:** response `{"status": "OK"}` → return success
   - **Blanket response (F-7):** нет per-item результата, только общий OK/ERROR

3. **Error classification:**
   - 400 → non-retryable (fix payload)
   - 401, 403 → non-retryable (auth)
   - 420 → retryable (rate limit) — **NOT 429!**
   - 423 → retryable (store locked)
   - 500 → retryable (server error)

4. DTOs в `adapter/yandex/dto/`:
   - `YandexUpdatePricesRequest(List<YandexPriceOffer> offers)`
   - `YandexPriceOffer(String offerId, YandexPriceValue price)`
   - `YandexPriceValue(BigDecimal value, String currencyId)`
   - `YandexUpdatePricesResponse(String status)` — или just parse status field

### Промпт 17 — YandexPriceReadAdapter

Создай `io.datapulse.execution.adapter.yandex.YandexPriceReadAdapter` — @Component, implements PriceReadAdapter:

1. `marketplace()` → `YANDEX`

2. `readCurrentPrice(connectionId, offerId, credentials)`:
   - **businessId resolution:** from connection metadata
   - Endpoint: `POST /v2/businesses/{businessId}/offer-prices`
   - Body: filter by offerId (если endpoint поддерживает фильтр) или full scan + filter locally
   - Rate limit: `YANDEX_CATALOG`
   - Return: current price value

### Промпт 18 — Quarantine check + reconciliation notes

1. **Price quarantine** — после write, Яндекс может «поставить цену на карантин» (подозрительное изменение). Добавь awareness:
   - Quarantine check endpoint: `POST /v2/businesses/{businessId}/price-quarantine`
   - Quarantine confirmation: `POST /v2/businesses/{businessId}/price-quarantine/confirm`
   - Для MVP: **не реализовывать автоматический quarantine check.** Документировать как TODO:
     ```java
     // TODO: Check price quarantine status after write
     // POST /v2/businesses/{businessId}/price-quarantine
     // If quarantined → RECONCILIATION_PENDING until quarantine resolves
     ```

2. **Reconciliation flow (MVP):**
   - Write → `status: OK` → immediately mark as SUCCEEDED (skip reconciliation)
   - TODO Phase 2: wait 30-60s → read back via offer-prices → compare → quarantine check
   - Документировать delay: Яндекс: «данные обновляются не мгновенно» — need to wait

3. **Registration:** PriceReadAdapter и PriceWriteAdapter регистрируются автоматически через `marketplace()` в `LivePriceActionGateway` (verify: найди как WB/Ozon adapters resolved by marketplace type).

**Чеклист после Chat 6:**
- [ ] `YandexPriceWriteAdapter` — POST offer-prices/updates, decimal price, 420 handling
- [ ] `YandexPriceReadAdapter` — POST offer-prices (read current)
- [ ] Execution DTOs
- [ ] Error classification documented
- [ ] Quarantine awareness (TODO for Phase 2)
- [ ] Adapter auto-registered by marketplace type
- [ ] `mvn compile` проходит

---

## Chat 7 — WireMock integration + application-local.yml + tests (промпты 19–22)

### Контекст для чата

Chats 1–6: полная реализация Yandex integration + ETL + execution.

Сейчас: WireMock integration для локальной разработки, unit и integration тесты.

Reference:
- `infra/wiremock/mappings/yandex-*.json` — 16 WireMock стабов (уже созданы)
- `infra/wiremock/__files/yandex-*.json` — response bodies
- `infra/docker-compose.yml` — WireMock container (port 9091)
- `backend/datapulse-etl/src/test/java/io/datapulse/etl/adapter/wb/WbNormalizerTest.java` — normalizer test для reference

### Промпт 19 — application-local.yml финализация

Проверь и дополни `application-local.yml`:

1. Yandex base URLs должны указывать на WireMock:
   ```yaml
   datapulse:
     integration:
       yandex:
         base-url: http://${WIREMOCK_HOST:localhost}:${WIREMOCK_PORT:9091}
         write-base-url: http://${WIREMOCK_HOST:localhost}:${WIREMOCK_PORT:9091}
   ```

2. Добавь Yandex sandbox credentials для auto-init (по аналогии с WB/Ozon):
   - Api-Key: `ACMA:Wd1CyQdDkhs1ewX1I0hKFKRGmYGbCLS5QRgLbrH7:c48c5547` (из provider-api-verification.mdc)
   - Проверь `LocalSandboxCredentialInitializer` (или аналог) — нужно ли добавить YANDEX credentials init

3. Проверь что WireMock stubs покрывают все endpoints, используемые адаптерами. Существующие 16 стабов:
   - campaigns, offer-mappings, offer-prices, stocks, orders, returns, warehouses, category-tree, promos, report-generate, report-status-done, price-update (success/validation-error/rate-limit/server-error), promo-update
   - Если какой-то adapter использует endpoint без стаба — создай стаб

### Промпт 20 — Unit tests: YandexNormalizer

Создай `YandexNormalizerTest` в `backend/datapulse-etl/src/test/java/io/datapulse/etl/adapter/yandex/`:

1. `@Nested NormalizeCatalog`:
   - `should_mapAllFields_when_fullOfferMapping` — полный маппинг из WireMock fixture `yandex-offer-mappings.json`
   - `should_handleNullMapping_when_noMarketSku` — offer без mapping (marketSku = null)
   - `should_deriveArchived_when_archivedTrue`
   - `should_normalizeVendorAsBrand`

2. `@Nested NormalizeStocks`:
   - `should_flattenWarehouseOfferHierarchy` — из WireMock fixture `yandex-stocks.json`
   - `should_mapStockTypes_correctly` — FIT→total, AVAILABLE→available, FREEZE→reserved
   - `should_handleEmptyStocks`

3. `@Nested NormalizeOrders`:
   - `should_extractBuyerPrice_from_pricesArray` — filter by type=="BUYER"
   - `should_mapProgramType_to_fulfillmentType` — FBY/FBS/DBS/EXPRESS
   - `should_handleMultipleItems`

4. `@Nested NormalizeReturns`:
   - `should_mapReturnFields` — from WireMock fixture
   - `should_mapShopSku_as_sellerSku`

AssertJ assertions. Посмотри `WbNormalizerTest` для паттерна загрузки fixture data.

### Промпт 21 — Integration test: EventSource with WireMock

Создай integration test для одного EventSource (например `YandexProductDictSource`):

1. `YandexProductDictSourceIntegrationTest`:
   - @SpringJUnitConfig + WireMock (посмотри как WB/Ozon adapter tests настраивают WireMock — может быть @WireMockTest или manual WireMockServer)
   - @TestConfiguration с необходимыми mock-beans (connection repository, credential resolver)
   - Setup: WireMock stub for offer-mappings → return fixture data
   - Test: `should_captureAndNormalize_when_offerMappingsAvailable`
     - Execute source
     - Verify: SubSourceResult success, correct record count
     - Verify: canonical repos called with correct data

2. Если WireMock integration tests слишком тяжелы для текущей инфраструктуры — создай lighter test:
   - Mock `YandexApiCaller` → return pre-built DTOs
   - Verify normalizer + upsert called correctly

### Промпт 22 — Execution adapter test + final compilation

1. `YandexPriceWriteAdapterTest`:
   - WireMock stub: POST `/v2/businesses/67890/offer-prices/updates` → 200 `{"status": "OK"}`
   - Test: `should_writePrice_successfully` — verify request body format (offerId, value, currencyId=RUR)
   - WireMock stub: same path → 420 rate limit response
   - Test: `should_classifyAsRetryable_when_420` — verify retryable error
   - WireMock stub: → 400 validation error
   - Test: `should_classifyAsNonRetryable_when_400`

2. **Final compilation check:**
   - `mvn compile` — all backend modules
   - Проверь что нет unused imports, missing dependencies
   - Если есть circular dependencies между datapulse-etl и datapulse-execution (из-за shared metadata access) — resolve через interface в common module или event

**Чеклист после Chat 7:**
- [ ] `application-local.yml` полностью настроен для Yandex + WireMock
- [ ] Yandex credentials в local init (если applicable)
- [ ] `YandexNormalizerTest` — ~12 test cases (catalog, stocks, orders, returns)
- [ ] Integration test: EventSource with WireMock (1 source)
- [ ] `YandexPriceWriteAdapterTest` — 3 test cases (success, 420, 400)
- [ ] `mvn compile` проходит для всех модулей
- [ ] Нет circular dependencies

---

## Chat 8 — Stabilization + documentation (промпты 23–25)

### Контекст для чата

Chats 1–7: полная реализация + тесты. Финальная стабилизация.

### Промпт 23 — Cross-module verification

Проверь корректность всех cross-module связей:

1. **EventSourceRegistry:** запусти мысленную проверку — `EventSourceRegistry.resolve(YANDEX, eventType)` должен возвращать source для всех 10 EtlEventType. Проверь что все 10 source классов аннотированы `@Component` и находятся в component scan scope.

2. **PriceWriteAdapter / PriceReadAdapter:** проверь что `LivePriceActionGateway` (или аналог) корректно резолвит Yandex adapters по `marketplace() == YANDEX`. Найди Map-based resolution и убедись что YANDEX key попадёт туда.

3. **Rate limits:** проверь что все adapters используют правильные `RateLimitGroup`:
   - Catalog, Prices → YANDEX_CATALOG
   - Orders → YANDEX_ORDERS
   - Bids → YANDEX_BIDS
   - Price write → YANDEX_PRICE_UPDATE
   - Reports → YANDEX_REPORTS
   - Warehouses → YANDEX_WAREHOUSES
   - Stocks, Returns, Promos, Categories → YANDEX_DEFAULT

4. **Connection metadata:** проверь что businessId и campaignIds доступны в IngestContext для Yandex connections. Если нужен helper — убедись что он работает.

5. **Credential resolution:** проверь что ETL и Execution получают api_key для Yandex connection корректно.

### Промпт 24 — Data flow smoke test

Мысленный end-to-end walkthrough (не запуск, а code review):

1. User создаёт connection `marketplace_type=YANDEX`, вводит api_key
2. `YandexHealthProbe` → `GET /v2/campaigns` → discovers campaigns → saves metadata
3. ETL sync triggered → `EventRunner` resolves 10 Yandex sources
4. DAG execution: CATEGORY_DICT/WAREHOUSE_DICT → PRODUCT_DICT → parallel (PRICE_SNAPSHOT, INVENTORY_FACT, SALES_FACT, ...) → FACT_FINANCE
5. Each source: extract credentials → call adapter → normalize → upsert canonical
6. Post-ingest: materialize to ClickHouse → P&L marts
7. Pricing run → reads canonical prices → decides → creates price action
8. `YandexPriceWriteAdapter` → POST offer-prices/updates → "OK"

Проверь каждый шаг на наличие всех необходимых классов. Если обнаружишь gap — исправь.

### Промпт 25 — Documentation updates

1. Обнови `docs/provider-api-specs/yandex-market-implementation-plan.md`:
   - Измени Status на "Implementation complete (MVP)"
   - В секции Roadmap отметь этапы 2–6 как DONE
   - Добавь секцию "Implementation Notes" с ссылками на ключевые пакеты:
     - `io.datapulse.etl.adapter.yandex` — API caller, DTOs, normalizer, read adapters
     - `io.datapulse.etl.domain.source.yandex` — 10 EventSource implementations
     - `io.datapulse.execution.adapter.yandex` — price read/write adapters
     - Rate limit groups: `YANDEX_*` in `RateLimitGroup`

2. Обнови `docs/modules/integration.md`:
   - В матрице capabilities добавь колонку YANDEX
   - В секции health probes упомяни `YandexHealthProbe`

3. Обнови `docs/modules/etl-pipeline.md`:
   - В таблице sub-sources добавь Yandex endpoints
   - В таблице EventSource упомяни 10 Yandex sources

4. Обнови `docs/data-model.md` (если есть секция с canonical tables):
   - Новые поля: `canonical_stock.source_campaign_id`, `canonical_return.source_campaign_id`
   - delivery_schema: DBS, EXPRESS

**Чеклист после Chat 8:**
- [ ] Все cross-module связи проверены (EventSourceRegistry, PriceAdapters, rate limits)
- [ ] End-to-end data flow code reviewed
- [ ] No gaps in class chain
- [ ] `yandex-market-implementation-plan.md` обновлён со статусом
- [ ] `integration.md`, `etl-pipeline.md`, `data-model.md` обновлены
- [ ] `mvn compile` проходит
- [ ] Всё готово к подключению реального аккаунта (Этап 7)

---

## Chat 9 — Frontend: generic marketplace support + Yandex (промпты 26–30)

### Контекст для чата

Chats 1–8: полный backend Yandex integration (ETL, execution, tests, docs).

Сейчас: фронтенд. Текущий UI захардкожен под WB и Ozon в ~10 файлах. Задача — сделать UI generic по маркетплейсу (data-driven, не if/else), и одновременно добавить Яндекс.

**Проблемные места (grep по `'WB'|'OZON'|Wildberries`):**
- `core/models/connection.model.ts` — union type, credential interfaces, CreateConnectionRequest
- `shared/components/marketplace-badge.component.ts` — Record с захардкоженными стилями
- `features/settings/connections/connections-page.component.ts` — marketplaces array, credential form, display name, isFormValid
- `features/settings/connection-detail/connection-detail-page.component.ts` — display name, credential rotation form, isRotationValid
- `features/onboarding/step-connection.component.ts` — marketplace cards, credential forms
- `features/grid/components/grid-toolbar.component.ts` — marketplace filter options
- `features/advertising/campaigns-page.component.ts` — AG Grid cellRenderer badge classes

Reference:
- `frontend/src/app/core/models/connection.model.ts`
- `frontend/src/app/shared/components/marketplace-badge.component.ts`
- `frontend/src/app/features/settings/connections/connections-page.component.ts`
- `frontend/src/app/features/settings/connection-detail/connection-detail-page.component.ts`
- `frontend/src/app/features/onboarding/step-connection.component.ts`
- `frontend/src/app/features/grid/components/grid-toolbar.component.ts`
- `frontend/src/app/features/advertising/campaigns-page.component.ts`
- `frontend/src/locale/ru.json`

### Промпт 26 — Models: MarketplaceType + credentials + marketplace registry

Цель: централизовать всю marketplace-specific конфигурацию в одном месте (data-driven), чтобы добавление нового маркетплейса = одна строка в registry.

1. **`connection.model.ts`** — расширить union type:
   ```typescript
   export type MarketplaceType = 'WB' | 'OZON' | 'YANDEX';
   ```

2. **Добавить `YandexCredentials`:**
   ```typescript
   export interface YandexCredentials {
     apiKey: string;
   }
   ```

3. **Расширить `CreateConnectionRequest` и `UpdateCredentialsRequest`:**
   ```typescript
   export interface CreateConnectionRequest {
     marketplaceType: MarketplaceType;
     name: string;
     credentials: WbCredentials | OzonCredentials | YandexCredentials;
   }

   export interface UpdateCredentialsRequest {
     credentials: WbCredentials | OzonCredentials | YandexCredentials;
   }
   ```

4. **Создать marketplace registry** — новый файл `frontend/src/app/core/models/marketplace-registry.ts`:
   ```typescript
   import { MarketplaceType } from './connection.model';

   export interface MarketplaceConfig {
     type: MarketplaceType;
     label: string;
     shortLabel: string;
     badgeBg: string;
     badgeText: string;
     credentialFields: CredentialFieldDef[];
   }

   export interface CredentialFieldDef {
     key: string;
     labelKey: string;       // i18n key
     inputType: 'text' | 'textarea';
     placeholder?: string;
   }

   export const MARKETPLACE_REGISTRY: MarketplaceConfig[] = [
     {
       type: 'WB',
       label: 'Wildberries',
       shortLabel: 'WB',
       badgeBg: '#7B2FBE',
       badgeText: '#FFFFFF',
       credentialFields: [
         { key: 'apiToken', labelKey: 'settings.connections.wb_token_label', inputType: 'textarea' },
       ],
     },
     {
       type: 'OZON',
       label: 'Ozon',
       shortLabel: 'Ozon',
       badgeBg: '#005BFF',
       badgeText: '#FFFFFF',
       credentialFields: [
         { key: 'clientId', labelKey: 'settings.connections.ozon_client_id_label', inputType: 'text' },
         { key: 'apiKey', labelKey: 'settings.connections.ozon_api_key_label', inputType: 'text' },
       ],
     },
     {
       type: 'YANDEX',
       label: 'Яндекс.Маркет',
       shortLabel: 'YM',
       badgeBg: '#FFCC00',
       badgeText: '#000000',
       credentialFields: [
         { key: 'apiKey', labelKey: 'settings.connections.yandex_api_key_label', inputType: 'text', placeholder: 'ACMA:...' },
       ],
     },
   ];

   export function getMarketplaceConfig(type: MarketplaceType): MarketplaceConfig {
     return MARKETPLACE_REGISTRY.find(m => m.type === type)!;
   }

   export function getMarketplaceLabel(type: MarketplaceType): string {
     return getMarketplaceConfig(type).label;
   }
   ```

5. Добавь re-export в `core/models/index.ts`:
   ```typescript
   export * from './marketplace-registry';
   ```

### Промпт 27 — Marketplace badge + grid toolbar (generic)

1. **`marketplace-badge.component.ts`** — сделать data-driven:

   Заменить захардкоженный `STYLES: Record<MarketplaceType, ...>` на использование registry:

   ```typescript
   import { getMarketplaceConfig } from '@core/models';
   ```

   Вместо:
   ```typescript
   const STYLES: Record<MarketplaceType, { bg: string; text: string; label: string }> = {
     WB: { ... },
     OZON: { ... },
   };
   ```

   Использовать:
   ```typescript
   protected readonly config = computed(() => {
     const mc = getMarketplaceConfig(this.type());
     return { bg: mc.badgeBg, text: mc.badgeText, label: mc.shortLabel };
   });
   ```

   Теперь badge автоматически работает для YANDEX (и любого будущего маркетплейса).

2. **`grid-toolbar.component.ts`** — marketplace filter options:

   Заменить захардкоженный массив:
   ```typescript
   options: [
     { value: 'WB', label: 'Wildberries' },
     { value: 'OZON', label: 'Ozon' },
   ],
   ```
   На:
   ```typescript
   import { MARKETPLACE_REGISTRY } from '@core/models';
   // ...
   options: MARKETPLACE_REGISTRY.map(m => ({ value: m.type, label: m.label })),
   ```

3. **`advertising/campaigns-page.component.ts`** — AG Grid cellRenderer:

   Заменить if/else по WB/OZON на generic lookup:
   ```typescript
   import { getMarketplaceConfig } from '@core/models';
   // ...
   cellRenderer: (p: { value: string }) => {
     const mc = getMarketplaceConfig(p.value as MarketplaceType);
     return `<span class="rounded-[var(--radius-sm)] px-1.5 py-0.5 text-[11px] font-medium"
       style="background:${mc.badgeBg}22;color:${mc.badgeBg}">${mc.shortLabel}</span>`;
   },
   ```

### Промпт 28 — Connections page: generic create form

**`connections-page.component.ts`** — полный рефакторинг формы создания:

1. **Marketplace selector** — из registry:
   ```typescript
   import { MARKETPLACE_REGISTRY, MarketplaceConfig, getMarketplaceConfig } from '@core/models';

   readonly marketplaces = MARKETPLACE_REGISTRY;
   ```

2. **Display name** — заменить тернарник:
   ```
   // БЫЛО:
   {{ selectedMarketplace() === 'WB' ? 'Wildberries' : 'Ozon' }}

   // СТАЛО:
   {{ selectedConfig()?.label }}
   ```
   Где:
   ```typescript
   readonly selectedConfig = computed(() =>
     this.selectedMarketplace() ? getMarketplaceConfig(this.selectedMarketplace()!) : null
   );
   ```

3. **Credential form** — заменить `@if (=== 'WB')` / `@if (=== 'OZON')` на data-driven loop:
   ```html
   @if (selectedConfig(); as config) {
     @for (field of config.credentialFields; track field.key) {
       <div>
         <label class="mb-1 block text-sm text-[var(--text-secondary)]">{{ field.labelKey | translate }}</label>
         @if (field.inputType === 'textarea') {
           <textarea
             [(ngModel)]="credentialValues()[field.key]"
             [name]="field.key"
             [placeholder]="field.placeholder ?? ''"
             rows="3"
             class="...input styles..."
           ></textarea>
         } @else {
           <input
             type="text"
             [(ngModel)]="credentialValues()[field.key]"
             [name]="field.key"
             [placeholder]="field.placeholder ?? ''"
             class="...input styles..."
           />
         }
       </div>
     }
   }
   ```

   **credentialValues** — signal `Record<string, string>`:
   ```typescript
   readonly credentialValues = signal<Record<string, string>>({});
   ```

   При смене marketplace — reset:
   ```typescript
   onSelectMarketplace(type: MarketplaceType): void {
     this.selectedMarketplace.set(type);
     this.credentialValues.set({});
   }
   ```

4. **isFormValid** — generic:
   ```typescript
   isFormValid(): boolean {
     if (!this.formName.trim()) return false;
     const config = this.selectedConfig();
     if (!config) return false;
     return config.credentialFields.every(f => !!this.credentialValues()[f.key]?.trim());
   }
   ```

5. **submitCreate** — generic credentials assembly:
   ```typescript
   submitCreate(): void {
     if (!this.isFormValid()) return;
     this.createMutation.mutate({
       marketplaceType: this.selectedMarketplace()!,
       name: this.formName.trim(),
       credentials: Object.fromEntries(
         Object.entries(this.credentialValues()).map(([k, v]) => [k, v.trim()])
       ),
     });
   }
   ```

### Промпт 29 — Connection detail + onboarding (generic)

1. **`connection-detail-page.component.ts`**:

   a. Display name — заменить тернарник:
   ```
   // БЫЛО:
   {{ conn.marketplaceType === 'WB' ? 'Wildberries' : 'Ozon' }}

   // СТАЛО:
   {{ marketplaceLabel(conn.marketplaceType) }}
   ```
   Где `marketplaceLabel` = `getMarketplaceLabel` из registry.

   b. Credential rotation form — аналогичный рефакторинг как в промпте 28:
   - Заменить `@if (conn.marketplaceType === 'WB')` / `@if (conn.marketplaceType === 'OZON')` на data-driven loop по `credentialFields`
   - `isRotationValid()` → generic: все credential fields заполнены
   - Rotation mutation credentials assembly → generic `Object.fromEntries(...)` вместо тернарника

2. **`step-connection.component.ts`** (onboarding):

   a. Marketplace cards — заменить захардкоженные кнопки WB/OZON на loop по registry:
   ```html
   <div class="flex gap-4">
     @for (mp of marketplaces; track mp.type) {
       <button
         (click)="onSelectMarketplace(mp.type)"
         class="flex h-[140px] w-[200px] cursor-pointer flex-col items-center justify-center gap-3 rounded-[var(--radius-lg)] border-2 transition-all"
         [class]="selectedMarketplace() === mp.type
           ? 'border-[var(--accent-primary)] bg-[var(--accent-subtle)] shadow-[var(--shadow-sm)]'
           : 'border-[var(--border-default)] bg-[var(--bg-primary)] hover:border-[var(--accent-primary)] hover:shadow-[var(--shadow-sm)]'"
       >
         <span class="text-2xl font-bold" [style.color]="mp.badgeBg">{{ mp.shortLabel }}</span>
         <span class="text-sm text-[var(--text-secondary)]">{{ mp.label }}</span>
       </button>
     }
   </div>
   ```

   b. Credential form — аналогичный рефакторинг (data-driven loop по `credentialFields` из selected marketplace config)

   c. `onSelectMarketplace()` — вместо if/else с wbForm/ozonForm: динамически строить FormGroup на основе `credentialFields`:
   ```typescript
   onSelectMarketplace(type: MarketplaceType): void {
     const config = getMarketplaceConfig(type);
     const formFields: Record<string, FormControl> = {
       name: new FormControl('', Validators.required),
     };
     config.credentialFields.forEach(f => {
       formFields[f.key] = new FormControl('', Validators.required);
     });
     this.form.set(new FormGroup(formFields));
     this.selectedMarketplace.set(type);
   }
   ```

   d. `submitCreate()` — generic: собрать credentials из FormGroup по `credentialFields` keys

### Промпт 30 — i18n + `ng build` verification

1. Добавь переводы в `frontend/src/locale/ru.json`:
   ```json
   "settings.connections.yandex_api_key_label": "Api-Key токен Яндекс.Маркета",
   "onboarding.connection.yandex_api_key_label": "Api-Key",
   "marketplace.wb": "Wildberries",
   "marketplace.ozon": "Ozon",
   "marketplace.yandex": "Яндекс.Маркет"
   ```

2. Проверь, что во всех ~30 файлах из grep больше нет захардкоженных `=== 'WB'` / `=== 'OZON'` в display logic. Допустимые исключения:
   - Тесты (`.spec.ts`) — fixture data с конкретными значениями OK
   - AG Grid cellRenderers, если уже заменены на registry lookup
   - Backend-driven данные (фильтры, где WB/OZON приходят с сервера)

3. Запусти `ng build` и исправь все ошибки:
   - TypeScript ошибки от расширения union type (`MarketplaceType` теперь 3 значения → exhaustive checks могут сломаться)
   - Любые `Record<MarketplaceType, ...>` без YANDEX ключа = ошибка компиляции

4. Проверь визуально badge для YANDEX (жёлтый фон `#FFCC00`, чёрный текст `#000000`, label "YM"). Если не нравится цвет — скорректируй. Референс: логотип Яндекс.Маркета = жёлтый/красный.

**Чеклист после Chat 9:**
- [ ] `MarketplaceType` = `'WB' | 'OZON' | 'YANDEX'`
- [ ] `YandexCredentials` interface
- [ ] `marketplace-registry.ts` — centralized config (labels, colors, credential fields)
- [ ] `marketplace-badge.component.ts` — data-driven from registry
- [ ] `grid-toolbar.component.ts` — filter options from registry
- [ ] `advertising/campaigns-page.component.ts` — generic badge renderer
- [ ] `connections-page.component.ts` — generic create form (no if/else per marketplace)
- [ ] `connection-detail-page.component.ts` — generic display name + credential rotation
- [ ] `step-connection.component.ts` — generic marketplace cards + credential form
- [ ] i18n: Yandex labels
- [ ] Zero hardcoded `=== 'WB'` / `=== 'OZON'` в display logic (кроме тестов)
- [ ] `ng build` проходит

---

## Summary

| Chat | Промпты | Тема | Ожидаемое время |
|------|---------|------|-----------------|
| 1 | 1–3 | Integration module: enum + rate limits + credentials + health + config | 20–25 min |
| 2 | 4–6 | ETL infrastructure: YandexApiCaller + ~40 DTOs + Normalizer | 20–25 min |
| 3 | 7–9 | ETL read adapters: dict & snapshot (Catalog, Prices, Stocks, Warehouses, Categories, Promos, Bids) | 20–25 min |
| 4 | 10–12 | ETL read adapters: facts + async finance (Orders, Returns, AsyncReportCapture, Finance) | 20–25 min |
| 5 | 13–15 | ETL EventSources: 10 Yandex sources | 20–25 min |
| 6 | 16–18 | Execution adapters: price write + read + quarantine | 15–20 min |
| 7 | 19–22 | WireMock + tests (normalizer, integration, adapter) | 25–30 min |
| 8 | 23–25 | Stabilization + documentation | 15–20 min |
| 9 | 26–30 | Frontend: generic marketplace support + Yandex | 25–30 min |

**Total: 9 чатов, 30 промптов, ~3–3.5 часа чистого времени**

### Что НЕ включено (Phase 2 / Этап 7):
- Подключение реального аккаунта Яндекс.Маркета
- Верификация finance sign convention (DD-26)
- Полноценный finance normalizer (stub → real mapping)
- Price quarantine auto-check
- Reconciliation read-after-write
- Stock write adapter
- Advertising bids write
