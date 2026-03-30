# Интеграция с маркетплейсами — Implementation Plan

## Обзор

Модуль `datapulse-marketplaces` — anti-corruption layer, изолирующий доменную модель от деталей API Ozon и Wildberries. Включает стриминг загрузку данных, rate limiting, retry, управление credentials и command-операции (обновление цен).

## Архитектура

```
EventSource (ETL)
 → OzonAdapter / WbAdapter
   → AbstractMarketplaceAdapter
     → EndpointsResolver (key → URI)
     → HttpHeaderProvider (credentials → headers)
     → MarketplaceStreamingDownloadService
       → MarketplaceRateLimiter (token bucket)
       → HttpStreamingClient (WebClient Flux<DataBuffer>)
       → MarketplaceRetryService (reactor Retry)
       → FileStreamingService (Flux → file)
     → Snapshot<R> (elementType + file path + nextCursor)
```

## Компоненты

### 1. AbstractMarketplaceAdapter

**Base class** для Ozon/WB адаптеров.

**API:**
- `doGet(accountId, key, elementType)` → `Snapshot<R>`
- `doGet(accountId, key, queryParams, elementType)` → `Snapshot<R>`
- `doPost(accountId, key, body, elementType)` → `Snapshot<R>`
- `doPostPartitioned(accountId, key, body, partitionKey, elementType)` → `Snapshot<R>`

**Механика `execute()`:**
1. Resolve `EndpointAuthScope` → определение `authAccountId` (для мульти-аккаунтных endpoints)
2. `resolveEndpoint(key, method, params)` → `EndpointRef` с URI
3. `headerProvider.build(marketplace, authAccountId)` → HTTP headers
4. `planPath(targetAccountId, endpoint, partitionKey)` → file path для кеша
5. **SnapshotLockRegistry** — per-path `ReentrantLock` для предотвращения concurrent downloads
6. Проверка файлового кеша: если файл существует → reuse snapshot
7. Download через `MarketplaceStreamingDownloadService`

**Файловая структура кеша:**
```
{baseDir}/{marketplace}/{accountId}/{endpointTag}/{endpointTag}[_{partitionKey}].json
```

### 2. OzonAdapter

**27 методов** для различных Ozon API endpoints:
- Warehouses: `downloadFbsWarehouses`, `downloadFboWarehouses`
- Categories: `downloadCategoryTree`
- Products: `downloadProductsPage`, `downloadProductInfoListBatch`, `downloadProductInfoPricesPage`
- Sales: `downloadPostingsFbsPage`, `downloadPostingsFboPage`, `downloadFinanceTransactionsPage`
- Returns: `downloadReturnsPage`
- Inventory: `downloadProductInfoStocksPage`, `downloadAnalyticsStocks`
- Promotions: `downloadActions`, `downloadActionProducts`, `downloadActionCandidates`

**Пагинация:**
- Cursor-based: `OzonCursorExtractor`, `OzonLastIdExtractor`
- Offset-based: `OzonHasNextExtractor`, `OzonResultSizeExtractor`, `OzonPageCountExtractor`
- `Snapshot.nextToken` — передача курсора следующей страницы

**Partition key:** для каждого page/cursor генерируется уникальный `partitionKey` через `CursorLimitPartitionKeyGenerator`.

### 3. WbAdapter

**Endpoints:**
- Warehouses: `downloadFbwWarehouses`, `downloadFbsOffices`, `downloadSellerWarehouses`
- Categories: `downloadParentCategories`, `downloadSubjects`
- Tariffs: `downloadTariffsCommission`
- Products: `downloadProductCards` (cursor-based)
- Sales: `downloadSalesReportDetailByPeriodPage` (rrdid cursor), `downloadSupplierSalesPage`
- Inventory: `downloadStocksReportPage` (offset-based), `downloadIncomesPage`
- Advertising: `downloadPromotionAdverts`, `downloadAdvertisingFullStats`
- Promotions: `downloadPromotions`, `downloadPromotionDetails`, `downloadPromotionNomenclatures`

**WB-specific cursor:** `updatedAt|nmID` format для product cards.

### 4. Endpoint Registry — `EndpointKey` + `EndpointsResolver`

**`EndpointKey` enum** — 57 значений:
- `DICT_*` — справочники
- `FACT_*` — факты (sales, inventory, advertising)
- `CMD_*` — команды (price updates)
- `PROMO_*` — промо-акции

**`EndpointsResolver`:**
1. `MarketplaceProperties.get(type).endpointConfig(key)` → URL из YAML
2. `UriComponentsBuilder.fromHttpUrl(url)` → URI
3. Query params: array/iterable values expanded as multi-value params

### 5. HTTP Headers — `HttpHeaderProvider`

**Strategy pattern per marketplace:**

| Marketplace | Headers |
|-------------|---------|
| Ozon | `Client-Id: {clientId}`, `Api-Key: {apiKey}`, `Content-Type: application/json` |
| Wildberries | `Authorization: {token}`, `Content-Type: application/json` |

**Credentials:** `CredentialsProvider.resolve(accountId, type)` → `OzonCredentials` / `WbCredentials`

### 6. Streaming Download — `MarketplaceStreamingDownloadService`

**Паттерн:**
1. `Flux.defer()` — lazy evaluation (rate limit check per attempt)
2. `rateLimiter.ensurePermit(marketplace, endpoint, accountId)`
3. `httpStreamingClient.getAsDataBufferFlux()` / `postAsDataBufferFlux()` — reactive streaming
4. `retryService.withRetries(source, marketplace, endpoint)` — retry wrapper
5. `fileStreamingService.writeToPermanentFile(retried, targetFile)` — write Flux to file

### 7. Rate Limiting — `MarketplaceRateLimiter`

**Token bucket algorithm** (per-account, per-group):

**Конфигурация** (`MarketplaceRateLimitsProperties`):
```yaml
marketplace:
  rate-limits:
    providers:
      OZON:
        groups:
          default: { limit: 10, period: 1s }
          finance: { limit: 5, period: 1s, burst: 3 }
      WILDBERRIES:
        groups:
          default: { limit: 10, period: 1s }
```

**Bucket key:** `{marketplace}:{group}:{accountId}`
**CAS loop:** lock-free compare-and-swap через `AtomicReference<BucketState>`
**Refill:** `tokensPerNano = limit / period.toNanos()`
**На исчерпание:** `RateLimitBackoffRequiredException(marketplace, endpoint, retryAfterSeconds, message)`

### 8. Retry — `MarketplaceRetryService`

**`MarketplaceRetryPolicy` interface** (Strategy per marketplace):
- `BaseRetryPolicy` — default
- `WbRetryPolicy` — WB-specific 429 handling

**`retryFor(marketplace, endpoint)`:**
1. `effectiveRetryPolicy(endpoint)` из YAML config
2. Policy builds reactor `Retry` spec

### 9. Command Client — `MarketplaceCommandClient`

**Для RPC-операций** (не bulk ETL):
- `post(marketplace, endpoint, accountId, body, responseType)` — synchronous with `block(30s)`
- `get(marketplace, endpoint, accountId, queryParams, responseType)`
- Rate limit check перед каждым вызовом
- Используется для обновления цен: `OzonPriceCommandAdapter`, `WbPriceCommandAdapter`

### 10. Auth Account Resolution — `AuthAccountIdResolver`

**`EndpointAuthScope`** определяет, чьи credentials использовать:
- `TARGET` — credentials target account
- `SYSTEM` — credentials system account (для shared endpoints)
- `SANDBOX` — sandbox credentials

## Ключевые файлы

| Файл | Модуль | Роль |
|------|--------|------|
| `AbstractMarketplaceAdapter.java` | marketplaces | Base adapter with caching |
| `OzonAdapter.java` | marketplaces | Ozon API methods (540 строк) |
| `WbAdapter.java` | marketplaces | WB API methods (444 строки) |
| `MarketplaceStreamingDownloadService.java` | marketplaces | Streaming download |
| `MarketplaceRateLimiter.java` | marketplaces | Token bucket rate limiter |
| `MarketplaceRetryService.java` | marketplaces | Retry policy dispatch |
| `EndpointKey.java` | marketplaces | Endpoint enum (57 values) |
| `EndpointsResolver.java` | marketplaces | Key → URI resolution |
| `HttpHeaderProvider.java` | marketplaces | Strategy-based headers |
| `MarketplaceCommandClient.java` | marketplaces | Sync RPC client |
| `MarketplaceRateLimitsProperties.java` | marketplaces | YAML-driven rate limits |
