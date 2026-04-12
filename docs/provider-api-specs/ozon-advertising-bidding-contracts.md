# Ozon — Performance API Bidding Contracts (Write + Read)

**Статус:** confirmed-docs + web-verified + code-verified
**Источники:**
- Ozon Performance API: https://docs.ozon.ru/global/en/api/perfomance-api/
- Ozon Developer News: https://dev.ozon.ru/news/279-Performance-API-novye-metody-dlia-upravleniia-tovarami/
- Web search cross-verification: 2026-04-12
- Кодовая база: `datapulse-etl` module (read adapters + token service existing)
**Верификация:** official Ozon docs (web search), existing code review, token endpoint confirmed 2026-03-31

Этот документ фиксирует **полный** функциональный контур Ozon Performance API, необходимый
для реализации autobidding: чтение кампаний, чтение/установка ставок, рекомендованные ставки,
статистика, управление товарами в продвижении.

> **Связанный документ:** Контракты для read-only ingestion (campaigns, stats, token)
> описаны в [promo-advertising-contracts.md](promo-advertising-contracts.md) §4.
> Здесь описаны **bidding-specific** endpoints и дополнения к существующим read-контрактам.

Confidence levels:
- **confirmed** — подтверждено реальным API-ответом
- **confirmed-docs** — проверено по официальной документации Ozon
- **web-verified** — подтверждено через web search + community sources
- **code-verified** — подтверждено наличием в кодовой базе Datapulse
- **assumed** — выведено логически, не подтверждено

---

## 1. ОБЩИЕ СВЕДЕНИЯ

### 1.1 Base URL и авторизация

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Base URL | `https://api-performance.ozon.ru` | confirmed |
| Old URL (deprecated) | `https://performance.ozon.ru` (returns 404) | confirmed |
| Auth | **OAuth2 `client_credentials`** → Bearer token | confirmed |
| Auth endpoint | `POST /api/client/token` | confirmed |
| Token TTL | **30 minutes** | confirmed-docs |
| Daily request limit | **100,000 requests/day** per Performance account | confirmed-docs |

**ВАЖНО:** Performance API использует **отдельную** систему авторизации от Seller API.
Seller API использует `Client-Id` + `Api-Key` headers, Performance API — OAuth2 Bearer token.

### 1.2 Token Exchange (существующая реализация)

> Полное описание в [promo-advertising-contracts.md §4.1](promo-advertising-contracts.md#41-token-exchange).

Текущая реализация (code-verified): `OzonPerformanceTokenService`
- Кеш токена: Caffeine, TTL = 25 min (5 min buffer)
- `POST /api/client/token` с `grant_type=client_credentials` (form-encoded)
- Evict при 401 из API-вызова
- Endpoint verified 2026-03-31: возвращает 401 для невалидных credentials (ожидаемо)

### 1.3 Credentials

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Credential type | `client_id` + `client_secret` | confirmed-docs |
| Where to obtain | seller.ozon.ru → Settings → API Keys → Performance API | confirmed-docs |
| Storage in Datapulse | `secret_reference` с `secret_type = OZON_PERFORMANCE_OAUTH2` | code-verified |
| Vault parsing | `CredentialMapper.parseOzonPerformanceCredentials()` | code-verified |

### 1.4 Единицы измерения

| Величина | Единица | Confidence |
|----------|---------|------------|
| Ставки (bids) | **Рубли** (в отличие от WB, где копейки) | web-verified |
| Статистика (spend) | Рубли (string с десятичными) | confirmed-docs |

**АРХИТЕКТУРНО ВАЖНО:** WB использует копейки, Ozon — рубли. Canonical bid model должна нормализовать единицы.

### 1.5 Типы кампаний (advObjectType)

| Значение | Описание | Relevance for bidding | Confidence |
|----------|----------|----------------------|------------|
| `SKU` | Продвижение конкретных товаров | **Основной тип для autobidding** | confirmed-docs |
| `BANNER` | Баннерная реклама | Out of scope | confirmed-docs |
| `VIDEO` | Видеореклама | Out of scope | confirmed-docs |

### 1.6 Состояния кампаний (state)

| Значение | Описание | Bidding-операции | Confidence |
|----------|----------|------------------|------------|
| `CAMPAIGN_STATE_RUNNING` | Активна | Можно управлять ставками | confirmed-docs |
| `CAMPAIGN_STATE_PLANNED` | Запланирована | Можно управлять ставками | assumed |
| `CAMPAIGN_STATE_STOPPED` | Остановлена | Только чтение | confirmed-docs |
| `CAMPAIGN_STATE_INACTIVE` | Неактивна | Только чтение | confirmed-docs |
| `CAMPAIGN_STATE_ARCHIVED` | Архивирована | Только чтение | assumed |
| `CAMPAIGN_STATE_MODERATION` | На модерации | Только чтение | assumed |

> **UNRESOLVED:** Полный список возможных значений `state` и их влияние на write-операции требует эмпирической верификации.

---

## 2. BIDDING WRITE ENDPOINTS

### 2.1 Установка ставок для товаров в продвижении — `POST /api/client/campaign/products/set-bids`

Основной endpoint для управления ставками на уровне товара. Это **главный** endpoint для Ozon autobidding.

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | web-verified |
| Path | `/api/client/campaign/products/set-bids` | web-verified |
| Base URL | `https://api-performance.ozon.ru` | confirmed |
| Auth | `Authorization: Bearer {access_token}` | confirmed |

> **Альтернативный path (legacy):** `POST /api/client/campaign/search/promo/bids` (`ExternalCampaign_SetSearchPromoBidsV2`).
> Рекомендуется использовать более новый endpoint. Оба могут работать — выбор определяется эмпирической верификацией.

#### Request Body

```json
{
  "sku_bids": [
    {
      "sku": 123456789,
      "bid": 15.50
    }
  ]
}
```

| Field | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `sku_bids` | array | yes | Массив ставок по товарам | web-verified |
| `sku_bids[].sku` | long | yes | Ozon SKU ID товара | web-verified |
| `sku_bids[].bid` | decimal | yes | Ставка в рублях | web-verified |

#### Response

| HTTP Status | Описание | Confidence |
|-------------|----------|------------|
| 200 | Ставки применены | web-verified |
| 400 | Invalid request | assumed |
| 401 | Unauthorized (невалидный или expired токен) | confirmed |

> **UNRESOLVED:** Точная структура response body (per-item success/failure, error reasons) требует эмпирической верификации.

#### Constraints

- Товар должен быть включён в продвижение в поиске (если нет — сначала `BatchEnableProducts`).
- Если продвижение для товара не включено, `SetSearchPromoBidsV2` автоматически включает его.

#### Бизнес-сценарий

Autobidding использует этот endpoint для установки/изменения ставки товара в кампании Ozon:
1. Стратегия `ECONOMY_HOLD`: корректировка ставки для поддержания целевого ДРР
2. Стратегия `MINIMAL_PRESENCE`: установка минимальной конкурентной ставки

---

### 2.2 Включение продвижения для товаров — `POST /api/client/campaign/products/batch-enable`

Массовое включение товаров в продвижение в поиске.

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | web-verified |
| Path | `/api/client/campaign/products/batch-enable` | web-verified |
| Base URL | `https://api-performance.ozon.ru` | confirmed |
| Auth | `Authorization: Bearer {access_token}` | confirmed |
| gRPC method | `ExternalCampaign_BatchEnableProducts` | web-verified |

#### Request Body

```json
{
  "sku_list": [123456789, 234567890]
}
```

| Field | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `sku_list` | long[] | yes | Массив SKU для включения | web-verified |

#### Бизнес-сценарий

**Out of scope for MVP.** Может понадобиться, если autobidding должен добавлять товары в продвижение.

---

### 2.3 Отключение продвижения для товаров — `POST /api/client/campaign/products/batch-disable`

Массовое отключение товаров из продвижения в поиске.

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | web-verified |
| Path | `/api/client/campaign/products/batch-disable` | web-verified |
| Base URL | `https://api-performance.ozon.ru` | confirmed |
| Auth | `Authorization: Bearer {access_token}` | confirmed |
| gRPC method | `ExternalCampaign_BatchDisableProducts` | web-verified |

#### Request Body

```json
{
  "sku_list": [123456789, 234567890]
}
```

#### Бизнес-сценарий

**Out of scope for MVP.** Может быть нужно при деактивации autobidding.

---

### 2.4 Удаление товаров из продвижения — `POST /api/client/campaign/search/promo/delete`

Удаление товаров из кампании по продвижению в поиске.

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | web-verified |
| Path | `/api/client/campaign/search/promo/delete` (estimated) | web-verified |
| gRPC method | `ExternalCampaign_DeleteSearchPromoBidsV2` | web-verified |

> **UNRESOLVED:** Точный REST path требует эмпирической верификации. gRPC method name подтверждён.

#### Бизнес-сценарий

**Out of scope for MVP.**

---

## 3. BIDDING READ ENDPOINTS

### 3.1 Список товаров в продвижении — `POST /api/client/campaign/search/promo/products`

Возвращает список всех товаров, участвующих в продвижении в поиске, с текущими ставками.

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | web-verified |
| Path | `/api/client/campaign/search/promo/products` | web-verified |
| Base URL | `https://api-performance.ozon.ru` | confirmed |
| Auth | `Authorization: Bearer {access_token}` | confirmed |
| gRPC method | `ExternalCampaign_ListSearchPromoProductsV2` | web-verified |

#### Response Fields (expected)

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `products` | array | Список товаров в продвижении | web-verified |
| `products[].sku` | long | Ozon SKU ID | web-verified |
| `products[].bid` | decimal | Текущая ставка в рублях | web-verified |
| `products[].enabled` | boolean | Включено ли продвижение | assumed |

> **UNRESOLVED:** Точная структура response требует эмпирической верификации с реальными credentials.

#### Бизнес-сценарий

Signal assembly: чтение текущих ставок (сигнал `current_bid`) перед принятием решения.
Reconciliation: проверка, что ставка была применена после write.

---

### 3.2 Рекомендованные ставки — `POST /api/client/campaign/products/recommended-bids`

Возвращает рекомендованную ставку для SKU. Рассчитывается как медиана ставок в категории на нижнем уровне.

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | web-verified |
| Path | `/api/client/campaign/products/recommended-bids` | web-verified |
| Base URL | `https://api-performance.ozon.ru` | confirmed |
| Auth | `Authorization: Bearer {access_token}` | confirmed |
| gRPC method | `ExternalCampaign_GetProductsRecommendedBids` | web-verified |

#### Request Body (expected)

```json
{
  "sku_list": [123456789, 234567890]
}
```

| Field | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `sku_list` | long[] | yes | Список SKU для получения рекомендаций | web-verified |

#### Response Fields (expected)

| Field | Type | Unit | Semantics | Confidence |
|-------|------|------|-----------|------------|
| `recommendations` | array | — | Рекомендации по товарам | web-verified |
| `recommendations[].sku` | long | — | Ozon SKU ID | web-verified |
| `recommendations[].recommended_bid` | decimal | rubles | Рекомендуемая ставка (медиана по категории) | web-verified |

> **UNRESOLVED:** Точная структура response (наличие min_bid, competitive_bid и т.д.) требует эмпирической верификации.

#### Бизнес-сценарий

Signal assembly: сигнал `recommended_bid` для принятия решения о целевой ставке.
Guard: проверка, что рассчитанная ставка находится в разумных пределах.

---

## 4. CAMPAIGN READ ENDPOINTS

### 4.1 Список кампаний — `GET /api/client/campaign`

> Полное описание в [promo-advertising-contracts.md §4.2](promo-advertising-contracts.md#42-list-campaigns).

Текущая реализация (code-verified): `OzonAdvertisingReadAdapter.captureCampaigns()`

Дополнительные поля, критичные для autobidding:

| Field | Type | Semantics | Code-verified | Confidence |
|-------|------|-----------|---------------|------------|
| `id` | long | Campaign ID | ✅ `OzonPerformanceCampaignDto.id` | confirmed-docs |
| `title` | string | Название кампании | ✅ `OzonPerformanceCampaignDto.title` | confirmed-docs |
| `state` | string | Статус (CAMPAIGN_STATE_*) | ✅ `OzonPerformanceCampaignDto.state` | confirmed-docs |
| `dailyBudget` | decimal | Дневной бюджет | ✅ `OzonPerformanceCampaignDto.dailyBudget` | confirmed-docs |
| `advObjectType` | string | Тип объекта (SKU/BANNER/VIDEO) | ✅ `OzonPerformanceCampaignDto.advObjectType` | confirmed-docs |
| `createdAt` | string | Дата создания | ✅ `OzonPerformanceCampaignDto.createdAt` | confirmed-docs |
| `endedAt` | string | Дата окончания | ✅ `OzonPerformanceCampaignDto.endedAt` | confirmed-docs |

**Текущий статус (code-verified):** DTO реализован, все необходимые поля присутствуют. Расширение не требуется.

---

### 4.2 Статистика кампаний — `GET /api/client/statistics/campaign/product`

> Описано в [promo-advertising-contracts.md §4.3](promo-advertising-contracts.md#43-campaign-statistics-async-report-flow).

Текущая реализация (code-verified): `OzonAdvertisingReadAdapter.captureStatistics()`

Grain: campaign × date × SKU (based on `OzonPerformanceStatDto`).

| Field | Code mapping | Confidence |
|-------|-------------|------------|
| `campaign_id` | `OzonPerformanceStatDto.campaignId` | code-verified |
| `date` | `OzonPerformanceStatDto.date` | code-verified |
| `sku` | `OzonPerformanceStatDto.sku` | code-verified |
| `views` | `OzonPerformanceStatDto.views` | code-verified |
| `clicks` | `OzonPerformanceStatDto.clicks` | code-verified |
| `spend` | `OzonPerformanceStatDto.spend` | code-verified |
| `orders` | `OzonPerformanceStatDto.orders` | code-verified |
| `revenue` | `OzonPerformanceStatDto.revenue` | code-verified |

**Текущий статус:** Adapter реализован, DTO имеет все необходимые поля для signal assembly.

**IMPORTANT:** Этот endpoint использует **синхронный** формат (GET с query params), в отличие от
async report flow (`POST /api/client/statistics/json` → UUID → poll → download), описанного в
promo-advertising-contracts.md §4.3. Существующий код использует синхронный формат.

---

## 5. RATE LIMIT SUMMARY

| Endpoint | Method | Daily Limit | Per-request limit | RateLimitGroup |
|----------|--------|-------------|-------------------|----------------|
| All Performance API | — | 100,000/day | — | — |
| `/api/client/token` | POST | — | — | (no limiter in code) |
| `/api/client/campaign` | GET | — | — | `OZON_PERFORMANCE` (existing, 60/60s) |
| `/api/client/statistics/campaign/product` | GET | — | — | `OZON_PERFORMANCE` (existing) |
| `/api/client/campaign/products/set-bids` | POST | — | — | `OZON_PERFORMANCE_BIDS` (new) |
| `/api/client/campaign/products/recommended-bids` | POST | — | — | `OZON_PERFORMANCE_BIDS` (new) |
| `/api/client/campaign/search/promo/products` | POST | — | — | `OZON_PERFORMANCE` (existing) |
| `/api/client/campaign/products/batch-enable` | POST | — | — | `OZON_PERFORMANCE_BIDS` (new) |
| `/api/client/campaign/products/batch-disable` | POST | — | — | `OZON_PERFORMANCE_BIDS` (new) |

> **NOTE:** Ozon документирует только общий дневной лимит (100k/day), не per-endpoint rate limits.
> Рекомендация: использовать conservative rate limit (1 req/s) для write endpoints,
> с exponential backoff при получении 429.

---

## 6. RECONCILIATION STRATEGY

Для Ozon autobidding write operations:

1. **Write:** `POST /api/client/campaign/products/set-bids` → success response.
2. **Primary evidence:** 200 OK → `SUCCEEDED` with primary confirmation.
3. **Secondary verification:** `POST /api/client/campaign/search/promo/products` → проверить, что ставка для SKU = заданному значению.
4. **Reconciliation window:** 30s–60s (Ozon может применять изменения с задержкой).

**Особенности Ozon vs WB:**
- WB ставки применяются **синхронно** (ответ зеркалирует применённые значения).
- Ozon может применять изменения **асинхронно** (требуется verification read).

---

## 7. IDEMPOTENCY

- `set-bids`: Повторная установка той же ставки — безопасна (no-op если значение не изменилось).
- `batch-enable`: Включение уже включённого товара — безопасно (no-op).
- `batch-disable`: Отключение уже отключённого товара — **UNRESOLVED** (может быть ошибка или no-op).

**Confidence:** assumed — эмпирическая верификация не проводилась.

---

## 8. IMPORTANT CAVEATS

### 8.1 Модель «оплата за заказы»

Ozon активно продвигает модель «оплата за заказы» (pay-per-order), в которой ставки управляются
алгоритмом Ozon, а не продавцом. Для кампаний с этой моделью **direct bid management через API
может быть недоступен**.

**Architectural guard:** При реализации autobidding для Ozon необходимо проверять тип кампании
и модель оплаты перед попыткой изменить ставку. Guard `OzonPaymentModelGuard`:
- Если кампания использует модель pay-per-order → SKIP (не управлять ставкой).
- Если кампания использует CPC/CPM → proceed с bid management.

### 8.2 Два формата статистики

Ozon Performance API предоставляет два формата получения статистики:
1. **Синхронный** (`GET /api/client/statistics/campaign/product`) — используется в текущем коде.
2. **Асинхронный** (`POST /api/client/statistics/json` → UUID → poll → download) — для больших объёмов.

Для autobidding signal assembly рекомендуется использовать **существующий синхронный** endpoint,
так как он уже реализован и достаточен для получения продуктовой статистики.

### 8.3 Связь SKU и Product ID

Ozon использует несколько идентификаторов товара:
- `product_id` — внутренний Ozon ID (используется в Seller API)
- `sku` — SKU в рамках кампании (используется в Performance API)
- `offer_id` — артикул продавца

Для bidding необходим **SKU**, а не `product_id`. Маппинг SKU ↔ product_id уже реализован
через `canonical_advertising_campaign` и `marketplace_offer`.

---

## 9. ARCHITECTURAL REQUIREMENTS FOR IMPLEMENTATION

### 9.1 Новый adapter для bidding

```
datapulse-bidding/
  adapter/
    ozon/
      OzonBidCommandAdapter.java          — set-bids, batch-enable/disable
      OzonBidReadAdapter.java             — promo products, recommended bids
      OzonPerformanceAuthService.java     — reuse OzonPerformanceTokenService
      dto/
        OzonSetBidsRequest.java
        OzonSetBidsResponse.java
        OzonRecommendedBidsRequest.java
        OzonRecommendedBidsResponse.java
        OzonPromoProductsResponse.java
```

### 9.2 Новые RateLimitGroup

Добавить в `RateLimitGroup`:
- `OZON_PERFORMANCE_BIDS` — 1/s (conservative) для write endpoints

### 9.3 Reuse existing infrastructure

- `OzonPerformanceTokenService` — уже реализован, можно инжектировать.
- `OzonPerformanceCredentials` — маппинг из vault уже реализован.
- `IntegrationProperties.getOzon().getPerformanceBaseUrl()` — URL уже конфигурируется.

---

## 10. UNRESOLVED GAPS

| # | Gap | Impact | Possible resolution |
|---|-----|--------|---------------------|
| U-1 | Точная response structure для `set-bids` | Невозможно реализовать error handling без знания формата ошибок | Эмпирическая верификация с реальным API |
| U-2 | Точная response structure для `recommended-bids` | Невозможно реализовать signal assembly parser | Эмпирическая верификация |
| U-3 | Точная response structure для `search/promo/products` | Невозможно реализовать reconciliation read | Эмпирическая верификация |
| U-4 | Точные REST paths для всех gRPC methods | gRPC method names подтверждены, REST paths — выведены | Эмпирическая верификация |
| U-5 | Per-endpoint rate limits | Ozon документирует только дневной лимит 100k | Мониторинг 429 в production |
| U-6 | Список допустимых `state` для write-операций | Не документировано, какие state блокируют set-bids | Эмпирическая верификация |
| U-7 | Модель pay-per-order — как определить через API | Нужно знать, какое поле campaign содержит payment model | Эмпирическая верификация |
| U-8 | Performance API credentials | Реальные credentials не получены | Получить в кабинете Ozon seller |

---

## 11. FUNCTIONAL COVERAGE MATRIX

| Use Case | Endpoint | gRPC Method | Status | Phase |
|----------|----------|-------------|--------|-------|
| Получить OAuth2 токен | `POST /api/client/token` | — | ✅ Реализован | A (existing) |
| Прочитать список кампаний | `GET /api/client/campaign` | — | ✅ Реализован (ETL) | A (existing) |
| Прочитать статистику (SKU-level) | `GET /api/client/statistics/campaign/product` | — | ✅ Реализован (ETL) | A (existing) |
| Установить ставку товара | `POST .../products/set-bids` | `SetSearchPromoBidsV2` | 📋 Задокументирован | MVP |
| Получить рекомендованные ставки | `POST .../products/recommended-bids` | `GetProductsRecommendedBids` | 📋 Задокументирован | MVP |
| Прочитать товары в продвижении | `POST .../search/promo/products` | `ListSearchPromoProductsV2` | 📋 Задокументирован | MVP |
| Включить продвижение товаров | `POST .../products/batch-enable` | `BatchEnableProducts` | 📋 Задокументирован | Post-MVP |
| Отключить продвижение товаров | `POST .../products/batch-disable` | `BatchDisableProducts` | 📋 Задокументирован | Post-MVP |
| Удалить товары из продвижения | `POST .../search/promo/delete` | `DeleteSearchPromoBidsV2` | 📋 Задокументирован | Out of scope |
| Async report statistics | `POST /api/client/statistics/json` + `GET .../report?UUID=` | — | 📋 Задокументирован (alt) | Alternative to existing |

---

## 12. CROSS-REFERENCE: WB vs OZON BIDDING API

| Capability | WB Endpoint | Ozon Endpoint | Key Difference |
|-----------|-------------|---------------|----------------|
| Изменить ставку товара | `PATCH /api/advert/v1/bids` | `POST .../products/set-bids` | WB: kopecks, Ozon: rubles |
| Минимальные ставки | `POST /api/advert/v1/bids/min` | N/A (via recommended-bids) | WB has dedicated min endpoint |
| Рекомендованные ставки | `GET /api/advert/v0/bids/recommendations` | `POST .../products/recommended-bids` | WB: multi-level, Ozon: median |
| Текущие ставки | `POST /adv/v0/normquery/get-bids` | `POST .../search/promo/products` | WB: keyword-level, Ozon: product |
| Авторизация | API Key header | OAuth2 Bearer token | Different auth flow |
| Rate limits | Per-endpoint (5/s, 20/min etc.) | Global daily (100k/day) | Different throttling model |
| Ставки единицы | Копейки (integer) | Рубли (decimal) | Normalization required |
| Campaign statuses | Integer codes (4, 7, 9, 11) | String enums (CAMPAIGN_STATE_*) | Different type systems |
