# Promo & Advertising — Provider Read Contracts

**Статус:** endpoint-verified + code-verified
**Источники:**
- WB Promotions: https://dev.wildberries.ru/openapi/promotion (dp-calendar-api)
- WB Advertising: https://dev.wildberries.ru/openapi/promotion (advert-api)
- Ozon Promos: https://docs.ozon.ru/api/seller/ (Actions API)
- Ozon Advertising: https://docs.ozon.ru/api/performance/ (Performance API — OAuth2, separate host)
- Кодовая база: `datapulse-marketplaces` + `datapulse-etl` modules
**Верификация:** real API calls 2026-03-30

Confidence levels:
- **confirmed** — подтверждено реальным API-ответом
- **confirmed-docs** — проверено по документации, но нет данных в аккаунте
- **code-verified** — подтверждено наличием в кодовой базе
- **assumed** — выведено логически

---

## 1. WB PROMOTIONS (Calendar API)

### 1.1 List Promotions

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | confirmed |
| Path | `/api/v1/calendar/promotions` | confirmed |
| Base URL | `https://dp-calendar-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |
| EndpointKey | `PROMO_WB_PROMOTIONS` | code-verified |
| Rate limit group | `WB_PROMO` (10/6s, burst 5) | code-verified |

### Query Parameters

| Param | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `startDateTime` | string (ISO 8601) | yes | Start of calendar window | code-verified |
| `endDateTime` | string (ISO 8601) | yes | End of calendar window | code-verified |
| `allPromo` | boolean | yes | Include all promos (true) | code-verified |
| `limit` | int | no | Items per page (default 1000) | code-verified |
| `offset` | int | no | Pagination offset | code-verified |

Default lookback: 365 days. Default lookahead: 180 days.

### Response Structure — VERIFIED

```json
{
  "data": {
    "promotions": [
      {
        "id": 2235,
        "name": "Неделя цветов (модели и обувь): товары-герои",
        "startDateTime": "2026-03-29T21:00:00Z",
        "endDateTime": "2026-04-12T20:59:59Z",
        "type": "regular"
      }
    ]
  }
}
```

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `data.promotions` | array | List of promotions | confirmed |
| `id` | long | Promotion ID (stable key) | confirmed |
| `name` | string | Promo campaign name | confirmed |
| `startDateTime` | string | Start datetime (ISO 8601 UTC) | confirmed |
| `endDateTime` | string | End datetime (ISO 8601 UTC) | confirmed |
| `type` | string | Promo type (e.g., "regular") | confirmed |

Additional fields from DTO (not verified with data):

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `description` | string | Campaign description | code-verified |
| `inAction` | boolean | Whether seller participates | code-verified |
| `participationPercentage` | int | Participation rate | code-verified |
| `advantages` | string[] | Benefits of participation | code-verified |
| `conditions` | string[] | Participation conditions | code-verified |

### Pagination

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Type | Offset-based | code-verified |
| Default limit | 1000 | code-verified |
| Termination | Page size < limit | code-verified |

---

### 1.2 Promotion Details

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | code-verified |
| Path | `/api/v1/calendar/promotions/details` | code-verified |
| Base URL | `https://dp-calendar-api.wildberries.ru` | code-verified |
| EndpointKey | `PROMO_WB_PROMOTION_DETAILS` | code-verified |
| Rate limit group | `WB_PROMO` | code-verified |

### Response Fields (from DTO)

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `id` | long | Promotion ID | code-verified |
| `name` | string | Campaign name | code-verified |
| `startDateTime` | string | Start datetime | code-verified |
| `endDateTime` | string | End datetime | code-verified |
| `type` | string | Promo type | code-verified |
| `description` | string | Description | code-verified |
| `inAction` | boolean | Participation flag | code-verified |
| `ranging` | object[] | Ranging/ranking data | code-verified |

---

### 1.3 Promotion Nomenclatures (Products in Promo)

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | code-verified |
| Path | `/api/v1/calendar/promotions/nomenclatures` | code-verified |
| Base URL | `https://dp-calendar-api.wildberries.ru` | code-verified |
| EndpointKey | `PROMO_WB_PROMOTION_NOMENCLATURES` | code-verified |
| Rate limit group | `WB_PROMO_NOMENCLATURES` (separate bucket) | code-verified |

### Query Parameters

| Param | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `promotionID` | long | yes | Target promotion ID | code-verified |
| `inAction` | boolean | yes | Filter by participation status | code-verified |

### Response Fields (from DTO)

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `nmID` / `nmId` | long | Product nomenclature ID | code-verified |
| `vendorCode` | string | Seller article | code-verified |
| `actionPrice` / `planPrice` | double | Promo price | code-verified |
| `price` | double | Regular price | code-verified |
| `inAction` | boolean | Whether product is in the promo | code-verified |

---

## 2. WB ADVERTISING (Advert API)

### 2.1 List Campaigns

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | confirmed |
| Path | `/api/advert/v2/adverts` | confirmed |
| Base URL | `https://advert-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |
| EndpointKey | `FACT_WB_ADVERTISING_CAMPAIGNS` | code-verified |
| Rate limit group | `WB_ADVERT` (5/60s) | code-verified |

> **CRITICAL MIGRATION:** The old endpoint `/adv/v1/promotion/adverts` was **deprecated and removed** (returns 404).
> New endpoint: `/api/advert/v2/adverts`. YAML config has been updated.

### Response Structure — VERIFIED

```json
{
  "adverts": []
}
```

Empty array for account without advertising campaigns. Response wraps in `{"adverts": [...]}`.

### Response Fields (from DTO — minimal extraction)

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `advertId` | Long | Campaign ID | code-verified |

> **NOTE:** The codebase extracts only `advertId` from this endpoint. Full campaign details
> (name, type, status, dates, budget) are available but not mapped in the DTO.
> The `WbAdvertisingAdvertIdsExtractor` handles root-level JSON array parsing.

### Code Behavior

The adapter calls this endpoint with `status` query params `[7, 9, 11]` (completed, active, paused).
With the v2 migration, the status filtering mechanism may have changed — needs verification.

---

### 2.2 Full Stats

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | confirmed-docs |
| Path | `/adv/v3/fullstats` | confirmed |
| Base URL | `https://advert-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |
| EndpointKey | `FACT_WB_ADVERTISING_FULLSTATS` | code-verified |
| Rate limit group | `WB_ADVERT` | code-verified |

> **CRITICAL MIGRATION:** The old endpoint `POST /adv/v2/fullstats` was **deprecated** (disabled Sept 30, 2025).
> New endpoint: `GET /adv/v3/fullstats`. HTTP method changed from POST to GET.
> YAML config has been updated. **Adapter code needs migration** from POST to GET.

### v3 Query Parameters (NEW — replaces POST body)

| Param | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `ids` | int[] | yes | Campaign IDs | confirmed-docs |
| `beginDate` | string | yes | Start date (`YYYY-MM-DD`) | confirmed-docs |
| `endDate` | string | yes | End date (`YYYY-MM-DD`) | confirmed-docs |

### v2 Request Body (OLD — adapter still uses this!)

```json
[
  { "id": 12345, "dates": ["2026-03-29"] }
]
```

### Response Fields (from DTO — minimal extraction)

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `advertId` | Long | Campaign ID | code-verified |
| `type` | Integer | Campaign type | code-verified |
| `status` | Integer | Campaign status | code-verified |
| (nested stats) | JSONB | Daily/product breakdown (stored as JSONB, flattened during materialization) | code-verified |

### v3 Changes vs v2

| Aspect | v2 (deprecated) | v3 (current) | Confidence |
|--------|-----------------|--------------|------------|
| HTTP method | POST | GET | confirmed-docs |
| Request | JSON body `[{id, dates}]` | Query params `ids`, `beginDate`, `endDate` | confirmed-docs |
| New field | — | `canceled` (cancellations in units) | confirmed-docs |
| Known issues | — | Returns 0 for some metrics on current day | confirmed-docs |

### **BREAKING:** Adapter Code Migration Required

The `WbAdapter.downloadAdvertisingFullStats()` currently sends a **POST body** to the fullstats endpoint. The v3 endpoint is **GET-only**. This requires:
1. Changing the adapter from `doPost` to `doGet` with query parameters
2. Mapping `campaignIds` → `ids` query param
3. Mapping `dates` → `beginDate`/`endDate` query params
4. Handling the changed response structure

---

## 3. OZON PROMOTIONS (Actions API)

### 3.1 List Actions (Promotions)

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | confirmed |
| Path | `/v1/actions` | confirmed |
| Base URL | `https://api-seller.ozon.ru` | confirmed |
| Auth | `Client-Id` + `Api-Key` headers | confirmed |
| EndpointKey | `PROMO_OZON_ACTIONS` | code-verified |
| Rate limit group | `OZON_PROMO` (20/60s) | code-verified |

### Response Structure — VERIFIED

```json
{
  "result": [
    {
      "id": 1977747,
      "title": "Электронный ассортимент...",
      "action_type": "...",
      "date_start": "2025-03-19T21:00:44Z",
      "date_end": "2026-12-31T20:59:59Z",
      "potential_products_count": 23,
      "participating_products_count": 323,
      "is_participating": true,
      "description": "...",
      "freeze_date": "...",
      "is_voucher_action": false,
      "banned_products_count": 0,
      "with_targeting": false,
      "order_amount": 0,
      "discount_type": "...",
      "discount_value": 0.0
    }
  ]
}
```

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `id` | long | Action ID (stable key) | confirmed |
| `title` | string | Action name | confirmed |
| `action_type` | string | Action type | confirmed |
| `description` | string | Description (HTML) | confirmed |
| `date_start` | string | Start datetime (ISO 8601 UTC) | confirmed |
| `date_end` | string | End datetime (ISO 8601 UTC) | confirmed |
| `freeze_date` | string | Freeze date (after which promo is locked) | confirmed |
| `potential_products_count` | int | Products eligible | confirmed |
| `participating_products_count` | int | Products currently in promo | confirmed |
| `is_participating` | boolean | Whether seller participates | confirmed |
| `is_voucher_action` | boolean | Voucher-based promo flag | confirmed |
| `banned_products_count` | int | Products banned from promo | confirmed |
| `with_targeting` | boolean | Has targeting | confirmed |
| `order_amount` | long | Order amount threshold | confirmed |
| `discount_type` | string | Discount calculation type | confirmed |
| `discount_value` | double | Discount value | confirmed |

---

### 3.2 Action Products (Products in Promo)

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | code-verified |
| Path | `/v1/actions/products` | code-verified |
| Base URL | `https://api-seller.ozon.ru` | code-verified |
| Auth | `Client-Id` + `Api-Key` headers | code-verified |
| EndpointKey | `PROMO_OZON_ACTION_PRODUCTS` | code-verified |
| Rate limit group | `OZON_PROMO` | code-verified |

### Request Body

```json
{
  "action_id": 1977747,
  "offset": 0,
  "limit": 1000
}
```

### Response Fields (from DTO)

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `id` (→ `productId`) | long | Product ID | code-verified |
| `price` | double | Current regular price | code-verified |
| `action_price` | double | Promo price | code-verified |
| `max_action_price` | double | Maximum promo price allowed | code-verified |
| `add_mode` | string | How product was added | code-verified |
| `stock` | int | Available stock | code-verified |
| `min_stock` | int | Minimum required stock | code-verified |

### Pagination

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Type | Offset-based | code-verified |
| Default limit | 1000 | code-verified |
| Termination | Page size < limit (via `OzonResultSizeExtractor`) | code-verified |

---

### 3.3 Action Candidates (Eligible Products)

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | code-verified |
| Path | `/v1/actions/candidates` | code-verified |
| Base URL | `https://api-seller.ozon.ru` | code-verified |
| Auth | `Client-Id` + `Api-Key` headers | code-verified |
| EndpointKey | `PROMO_OZON_ACTION_CANDIDATES` | code-verified |
| Rate limit group | `OZON_PROMO` | code-verified |

### Request Body

```json
{
  "action_id": 1977747,
  "offset": 0,
  "limit": 1000
}
```

### Response Fields (from DTO — same structure as Action Products)

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `id` (→ `productId`) | long | Product ID | code-verified |
| `price` | double | Current price | code-verified |
| `action_price` | double | Proposed promo price | code-verified |
| `max_action_price` | double | Maximum promo price allowed | code-verified |
| `add_mode` | string | How product was added | code-verified |
| `stock` | int | Available stock | code-verified |
| `min_stock` | int | Minimum required stock | code-verified |

---

## 4. OZON ADVERTISING (Performance API)

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Status | **STUB — NOT IMPLEMENTED** | code-verified |
| Base URL | `https://api-performance.ozon.ru` (separate from Seller API) | confirmed-docs |
| Auth | **OAuth2 `client_credentials`** (NOT `Client-Id` + `Api-Key`) | confirmed-docs |
| EndpointKey | `FACT_OZON_ADVERTISING_DAILY` (defined but not in YAML config) | code-verified |

### Current State

`OzonAdvertisingEventSource` is a **no-op stub** that returns an empty list. The Ozon Performance API requires:
1. Separate OAuth2 registration (client_id + client_secret)
2. Token exchange via `https://api-performance.ozon.ru/api/client/token`
3. Different auth mechanism from Seller API

### DTO (prepared for future implementation)

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `date` | string | Report date | code-verified |
| `campaign_id` | Long | Campaign ID | code-verified |
| `views` | Long | Impression count | code-verified |
| `clicks` | Long | Click count | code-verified |
| `expense` | Double | Spend amount | code-verified |

---

## 5. ETL PIPELINE MAPPING

### Event Sources

| EventSource | Pipeline | Marketplace | Raw table | Order |
|-------------|----------|-------------|-----------|-------|
| `WbPromoEventSource` | `PROMO_SYNC` | WB | `raw_wb_promotions` | 0 |
| `WbPromoNomenclaturesEventSource` | `PROMO_SYNC` | WB | `raw_wb_promotion_nomenclatures` | 1 |
| `OzonPromoEventSource` | `PROMO_SYNC` | OZON | `raw_ozon_actions` | 0 |
| `OzonPromoActionProductsEventSource` | `PROMO_SYNC` | OZON | `raw_ozon_action_products` | 1 |
| `WbAdvertisingEventSource` | `ADVERTISING_FACT` | WB | `raw_wb_advertising_fullstats` | 0 |
| `OzonAdvertisingEventSource` | `ADVERTISING_FACT` | OZON | (stub — empty) | 0 |

### Materialization Handlers

| Handler | Target tables |
|---------|--------------|
| `PromoWbMaterializationHandler` | `dim_promo_campaign` + `fact_promo_product` |
| `PromoOzonMaterializationHandler` | `dim_promo_campaign` + `fact_promo_product` |
| `AdvertisingFactWildberriesMaterializationHandler` | `fact_advertising` |
| `AdvertisingFactOzonMaterializationHandler` | `fact_advertising` |

---

## 6. FINDINGS & CRITICAL ISSUES

### F-1: WB Advertising Campaigns Endpoint Migration (CRITICAL)

**Finding:** `GET /adv/v1/promotion/adverts` returns **404** — deprecated and removed.
**New endpoint:** `GET /api/advert/v2/adverts`
**Impact:** WB advertising campaign discovery is **completely broken**.
**Fix applied:** YAML config updated to new URL.
**Additional risk:** The v2 endpoint may return a different response structure (wraps in `{"adverts":[...]}` instead of root array). The `WbAdvertisingAdvertIdsExtractor` may need adaptation.

### F-2: WB Advertising Full Stats v2→v3 Migration (CRITICAL)

**Finding:** `POST /adv/v2/fullstats` was **disabled Sept 30, 2025** — returns 404.
**New endpoint:** `GET /adv/v3/fullstats`
**Impact:** WB advertising statistics ingestion is **completely broken**.
**Fix applied:** YAML config updated to new URL.
**BREAKING:** HTTP method changed from POST to GET. The `WbAdapter.downloadAdvertisingFullStats()` method sends a POST body — this **will not work** with the v3 GET endpoint. Adapter code migration required:
- Change from POST with JSON body `[{id, dates}]` to GET with query params `ids`, `beginDate`, `endDate`
- Handle new `canceled` field in response
- Known v3 issue: returns 0 for some metrics on current-day queries

### F-3: Ozon Advertising — OAuth2 Not Implemented (MEDIUM)

**Finding:** The Ozon Performance API requires OAuth2 client_credentials flow on a separate host (`api-performance.ozon.ru`). The event source is a stub returning empty data.
**Impact:** No Ozon advertising data is being ingested.
**Status:** Intentional — requires separate OAuth2 credentials setup.

---

## 7. SUMMARY STATUS

| Capability | WB | Ozon | Status |
|------------|-----|------|--------|
| Promo list | **READY** (verified) | **READY** (verified) | Both working |
| Promo details | READY (code-verified) | N/A | — |
| Promo products | READY (code-verified) | READY (code-verified) | Need data verification |
| Promo candidates | N/A | READY (code-verified) | — |
| Advertising campaigns | **BROKEN** → fixed URL, needs code review | STUB | v1→v2 migration |
| Advertising stats | **BROKEN** → fixed URL, **needs code migration** | STUB | v2→v3 POST→GET |
