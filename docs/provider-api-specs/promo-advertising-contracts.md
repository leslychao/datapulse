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

### Query Parameters

| Param | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `status` | int | no | Campaign status filter (see below) | confirmed-docs |
| `type` | int | no | Campaign type filter | confirmed-docs |
| `order` | string | no | Sort order | confirmed-docs |
| `direction` | string | no | Sort direction | confirmed-docs |

Status codes (v2): `7` = completed, `9` = active, `11` = paused.

> **NOTE:** With the v2 migration, status codes may have changed. Needs empirical verification
> with an account that has advertising campaigns.

### Response Structure — VERIFIED

```json
{
  "adverts": []
}
```

Empty array for account without advertising campaigns (confirmed 2026-03-31). Response wraps in `{"adverts": [...]}`.

### Response Fields

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `advertId` | Long | Campaign ID (stable key) | code-verified |
| `type` | Integer | Campaign type (9 = unified, as of Oct 2025) | confirmed-docs |
| `status` | Integer | Campaign status | confirmed-docs |
| `name` | String | Campaign name | confirmed-docs |
| `dailyBudget` | Integer | Daily budget (kopecks) | confirmed-docs |
| `createTime` | String | Campaign creation time (ISO 8601) | confirmed-docs |
| `changeTime` | String | Last change time (ISO 8601) | confirmed-docs |
| `startTime` | String | Campaign start time (ISO 8601) | confirmed-docs |
| `endTime` | String | Campaign end time (ISO 8601) | confirmed-docs |
| `placement` | String | Placement: `"search"`, `"recommendations"`, `"combined"` | confirmed-docs |
| `bidType` | String | Bid type: `"manual"`, `"unified"` | confirmed-docs |

> **NOTE:** The existing codebase extracts only `advertId`. DTO needs expansion
> to capture dimension fields (name, type, status, dates, placement) for `dim_advertising_campaign`.
> The `WbAdvertisingAdvertIdsExtractor` handles root-level JSON parsing.

### Architectural requirement

For `dim_advertising_campaign` materialization, the following fields must be extracted:
`advertId`, `name`, `type`, `status`, `placement`, `dailyBudget`, `createTime`, `startTime`, `endTime`.

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

### v3 Response Structure — confirmed-docs

Hierarchical: campaign → days → apps → nms (product-level breakdown).

```json
[
  {
    "advertId": 12345678,
    "days": [
      {
        "date": "2026-03-15",
        "apps": [
          {
            "appType": 32,
            "nms": [
              {
                "nmId": 987654,
                "name": "Product name",
                "views": 1500,
                "clicks": 42,
                "ctr": 2.8,
                "cpc": 5.71,
                "sum": 240.0,
                "atbs": 8,
                "orders": 3,
                "cr": 7.14,
                "shks": 3,
                "sum_price": 4500.0,
                "canceled": 0
              }
            ]
          }
        ]
      }
    ]
  }
]
```

### Response Fields (v3 — full hierarchy)

**Campaign level:**

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `advertId` | Long | Campaign ID | confirmed-docs |

**Day level (`days[]`):**

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `date` | String | Date (`YYYY-MM-DD`) | confirmed-docs |

**App level (`days[].apps[]`):**

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `appType` | Integer | Application type | confirmed-docs |

**Product level (`days[].apps[].nms[]`) — target grain for `fact_advertising`:**

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `nmId` | Long | Product nomenclature ID (join key to `marketplace_offer.marketplace_sku`) | confirmed-docs |
| `name` | String | Product name | confirmed-docs |
| `views` | Long | Impressions count | confirmed-docs |
| `clicks` | Long | Clicks count | confirmed-docs |
| `ctr` | Double | Click-through rate (%) | confirmed-docs |
| `cpc` | Double | Cost per click (RUB) | confirmed-docs |
| `sum` | Double | Spend amount (RUB) | confirmed-docs |
| `atbs` | Long | Add-to-basket count | confirmed-docs |
| `orders` | Long | Orders count | confirmed-docs |
| `cr` | Double | Conversion rate (click → order, %) | confirmed-docs |
| `shks` | Long | Units ordered | confirmed-docs |
| `sum_price` | Double | Ordered revenue (RUB) | confirmed-docs |
| `canceled` | Long | Canceled orders count (v3 new field) | confirmed-docs |

### Grain & join keys

WB fullstats provides **product-level daily breakdown** — grain: `campaign × date × nmId`.
Join to catalog: `nmId` = `marketplace_offer.marketplace_sku` (WB nmID).
This enables direct product-level ad cost attribution (no pro-rata needed for WB).

### v3 Changes vs v2

| Aspect | v2 (deprecated) | v3 (current) | Confidence |
|--------|-----------------|--------------|------------|
| HTTP method | POST | GET | confirmed-docs |
| Request | JSON body `[{id, dates}]` | Query params `ids`, `beginDate`, `endDate` | confirmed-docs |
| Max campaign IDs | — | 50 per request | confirmed-docs |
| New field | — | `canceled` (cancellations in units) | confirmed-docs |
| Known issues | — | Returns 0 for some metrics on current day | confirmed-docs |
| Data sync | — | Every 3 minutes | confirmed-docs |

### **BREAKING:** Adapter Code Migration Required

The `WbAdapter.downloadAdvertisingFullStats()` currently sends a **POST body** to the fullstats endpoint. The v3 endpoint is **GET-only**. This requires:
1. Changing the adapter from `doPost` to `doGet` with query parameters
2. Mapping `campaignIds` → `ids` query param (max 50 per request, batch if more)
3. Mapping `dates` → `beginDate`/`endDate` query params
4. Flattening hierarchical response (campaign → days → apps → nms) into flat rows for materialization
5. Handling the `canceled` field
6. Skipping current-day queries (known issue: returns 0 for some metrics)

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
| Status | **NOT IMPLEMENTED** — requires OAuth2 infrastructure | code-verified |
| Base URL | `https://api-performance.ozon.ru` | confirmed-docs |
| Auth | **OAuth2 `client_credentials`** (NOT `Client-Id` + `Api-Key`) | confirmed-docs |
| Rate limit | 100,000 requests/day per Performance account | confirmed-docs |
| EndpointKey | `FACT_OZON_ADVERTISING_DAILY` (defined but not in YAML config) | code-verified |

> **HOST MIGRATION (Jan 2025):** `performance.ozon.ru` → `api-performance.ozon.ru`.

### Current State

`OzonAdvertisingEventSource` is a **no-op stub** that returns an empty list. Requires:
1. Separate OAuth2 credentials (client_id + client_secret) from seller.ozon.ru → Settings → Performance API
2. Token exchange implementation
3. Async report flow (request UUID → poll → download)

### 4.1 Token Exchange

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/api/client/token` | confirmed-docs |
| Base URL | `https://api-performance.ozon.ru` | confirmed-docs |

**Request:**

```json
{
  "client_id": "...",
  "client_secret": "...",
  "grant_type": "client_credentials"
}
```

**Response:**

```json
{
  "access_token": "...",
  "expires_in": 1800,
  "token_type": "Bearer"
}
```

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `access_token` | String | Bearer token for subsequent requests | confirmed-docs |
| `expires_in` | Integer | Token TTL in seconds (**30 minutes**) | confirmed-docs |
| `token_type` | String | Always `"Bearer"` | confirmed-docs |

**Token lifecycle:** No refresh token. Re-request with same credentials before expiry. Cache token with TTL = `expires_in − buffer` (e.g., 25 minutes).

### 4.2 List Campaigns

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | confirmed-docs |
| Path | `/api/client/campaign` | confirmed-docs |
| Auth | `Authorization: Bearer {access_token}` | confirmed-docs |

**Query Parameters:**

| Param | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `advObjectType` | string | no | Campaign type filter (e.g., `SKU`) | confirmed-docs |
| `state` | string | no | State filter (e.g., `CAMPAIGN_STATE_RUNNING`) | confirmed-docs |

**Response:**

```json
{
  "list": [
    {
      "id": 12345678,
      ...
    }
  ]
}
```

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `list` | array | List of campaigns | confirmed-docs |
| `id` | Long | Campaign ID (stable key) | confirmed-docs |

> Full campaign fields (title, state, dailyBudget, advObjectType, placement, dates) — **need empirical verification** with an account that has Performance API access.

### 4.3 Campaign Statistics (Async Report Flow)

Ozon Performance API uses an **async report pattern**: request → UUID → poll → download.

**Step 1: Request report**

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/api/client/statistics/json` | confirmed-docs |
| Auth | `Authorization: Bearer {access_token}` | confirmed-docs |

**Request:**

```json
{
  "campaigns": [12345678],
  "dateFrom": "2026-03-01",
  "dateTo": "2026-03-30",
  "groupBy": "DATE"
}
```

| Param | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `campaigns` | Long[] | Campaign IDs (up to 10) | confirmed-docs |
| `dateFrom` | String | Start date (`YYYY-MM-DD`) | confirmed-docs |
| `dateTo` | String | End date (`YYYY-MM-DD`) | confirmed-docs |
| `groupBy` | String | Grouping: `DATE`, `NO_GROUP_BY`, `START_OF_WEEK`, `START_OF_MONTH` | confirmed-docs |

**Response:**

```json
{
  "UUID": "abc123-..."
}
```

**Step 2: Download report**

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | confirmed-docs |
| Path | `/api/client/statistics/report?UUID={uuid}` | confirmed-docs |
| Auth | `Authorization: Bearer {access_token}` | confirmed-docs |
| Polling | Report may not be immediately available; retry with backoff | confirmed-docs |

**Response (report body):**

```json
{
  "report": {
    "rows": [
      {
        "date": "2026-03-15",
        "views": 1500,
        "clicks": 42,
        "ctr": "2.80",
        "moneySpent": "240.00",
        ...
      }
    ]
  }
}
```

### Report fields (confirmed-docs from third-party integrations)

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `date` | String | Report date | confirmed-docs |
| `views` | Long | Impressions count | confirmed-docs |
| `clicks` | Long | Clicks count | confirmed-docs |
| `ctr` | String | Click-through rate (%) | confirmed-docs |
| `moneySpent` | String | Spend amount (RUB, string with decimals) | confirmed-docs |
| `orders` | Long | Orders count | assumed |
| `revenue` | Double | Revenue from ad-attributed orders | assumed |

> **IMPORTANT:** The exact report schema depends on the report type. Five report types available:
> 1. Statistics on all campaigns
> 2. Statistics on product (SKU) campaigns  
> 3. Statistics on media campaigns
> 4. Statistics on products in search promotion
> 5. Report on orders in search promotion
>
> For Datapulse Phase B: **type 2 (product campaigns)** is the primary target — provides SKU-level spend attribution.
> Full field inventory requires **empirical verification** with real Performance API credentials.

### Grain & join keys

Ozon Performance API statistics grain: `campaign × date` (when `groupBy = DATE`).
Product-level breakdown (sku-level): available through report types 2 and 4. Join key: campaign products from `/api/client/campaign` → product mapping.

**Key difference from WB:** Ozon ad stats may not provide per-product daily spend in a single report. Product-level attribution may require cross-referencing campaign product list with campaign daily spend. Pro-rata allocation by campaign product count or separate product-level report.

### Architectural notes for implementation

1. **Token caching:** Cache `access_token` with TTL = 25 min (5 min buffer before 30 min expiry). Store in Redis or in-memory per-connection.
2. **Async polling:** Request UUID → wait 5s → poll → exponential backoff up to 5 min. Large accounts may take up to 8 hours.
3. **Credentials storage:** `secret_reference` with `secret_type = OZON_PERFORMANCE_OAUTH2`. Vault path stores `client_id` + `client_secret`.
4. **Connection model:** Extend existing `marketplace_connection` (Ozon) with optional Performance API credentials. Not a separate connection.

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
| `AdvertisingFactWildberriesMaterializationHandler` | `dim_advertising_campaign` + `fact_advertising` |
| `AdvertisingFactOzonMaterializationHandler` | `dim_advertising_campaign` + `fact_advertising` |

> **Naming decision (DD-AD-3):** Target table is `fact_advertising` (not `fact_advertising_costs`),
> because fullstats contains not only costs but also views, clicks, orders, conversions.
> All documents updated to use `fact_advertising` consistently.

---

## 6. FINDINGS & CRITICAL ISSUES

### F-1: WB Advertising Campaigns Endpoint Migration (CRITICAL — blocks Phase B)

**Finding:** `GET /adv/v1/promotion/adverts` returns **404** — deprecated and removed.
**New endpoint:** `GET /api/advert/v2/adverts`
**Impact:** WB advertising campaign discovery is **completely broken**.
**Fix applied:** YAML config updated to new URL.
**Remaining work:**
- Response wraps in `{"adverts":[...]}` instead of root array — `WbAdvertisingAdvertIdsExtractor` needs adaptation
- Expand DTO to extract dimension fields (name, type, status, dates, placement) for `dim_advertising_campaign`
- Verify status filter codes `[7, 9, 11]` are still valid in v2

### F-2: WB Advertising Full Stats v2→v3 Migration (CRITICAL — blocks Phase B)

**Finding:** `POST /adv/v2/fullstats` was **disabled Sept 30, 2025** — returns 404.
**New endpoint:** `GET /adv/v3/fullstats`
**Impact:** WB advertising statistics ingestion is **completely broken**.
**Fix applied:** YAML config updated to new URL.
**BREAKING:** HTTP method changed from POST to GET. Adapter code migration required:
1. Change from POST with JSON body `[{id, dates}]` to GET with query params `ids`, `beginDate`, `endDate`
2. Batch campaign IDs (max 50 per request)
3. Flatten hierarchical response (campaign → days → apps → nms) into flat rows
4. Handle `canceled` field
5. Skip current-day queries (returns 0 for some metrics)
6. New grain: product-level daily breakdown (nmId available in response)

### F-3: Ozon Advertising — OAuth2 Not Implemented (HIGH — blocks Phase B extended)

**Finding:** The Ozon Performance API requires OAuth2 client_credentials flow on a separate host (`api-performance.ozon.ru`).
**Impact:** No Ozon advertising data is being ingested.
**Remaining work:**
1. OAuth2 token exchange implementation (§4.1)
2. Token caching (30 min TTL) in Redis or in-memory
3. `secret_reference` extension: `secret_type = OZON_PERFORMANCE_OAUTH2`
4. Async report flow: request UUID → poll → download (§4.3)
5. Campaign list adapter (§4.2)
6. Empirical verification of report schemas with real credentials

### F-4: DTO Expansion Required (MEDIUM — blocks Phase B)

**Finding:** Existing WB advertising DTO extracts only `advertId` from campaigns endpoint. For `dim_advertising_campaign`, need: name, type, status, placement, dates, budget.
**Impact:** Cannot build advertising campaign dimension without expanded DTO.
**Remaining work:** Expand `WbAdvertisingCampaignDto` and `WbAdvertisingAdvertIdsExtractor`.

### F-5: Fullstats Response Flattening (MEDIUM — blocks Phase B)

**Finding:** WB v3 fullstats returns hierarchical JSON (campaign → days → apps → nms). Current materializer expects flat structure.
**Impact:** Materializer must flatten hierarchy into per-product daily rows for `fact_advertising`.
**Remaining work:** New response parser/flattener for hierarchical v3 format.

---

## 7. SUMMARY STATUS

| Capability | WB | Ozon | Phase | Status |
|------------|-----|------|-------|--------|
| Promo list | **READY** (verified) | **READY** (verified) | F | Both working |
| Promo details | READY (code-verified) | N/A | F | — |
| Promo products | READY (code-verified) | READY (code-verified) | F | Need data verification |
| Promo candidates | N/A | READY (code-verified) | F | — |
| Ad campaigns (dim) | **NEEDS WORK** (F-1, F-4) | **NEEDS WORK** (F-3) | **B** | DTO expansion + OAuth2 |
| Ad stats (fact) | **NEEDS WORK** (F-2, F-5) | **NEEDS WORK** (F-3) | **B** | POST→GET migration + async flow |

## 8. DESIGN DECISIONS

### DD-AD-1: No canonical entity for advertising — RESOLVED

Advertising data flows Raw → ClickHouse directly, bypassing canonical PostgreSQL layer. This is a documented exception to pipeline invariant №1 (Raw → Normalized → Canonical → Analytics).

**Rationale:** Advertising data is used exclusively for analytics (P&L allocation, pricing signal `ad_cost_ratio`). No business decision flow reads advertising state from PostgreSQL. No action lifecycle depends on advertising canonical truth.

**Data provenance:** Maintained through `job_execution_id` stored in `fact_advertising` (ClickHouse column). Drill-down: `fact_advertising.job_execution_id` → `job_item.s3_key` → S3 raw payload.

### DD-AD-2: `dim_advertising_campaign` — RESOLVED

Create a separate dimension table for advertising campaigns. Campaigns have attributes (name, type, status, placement, dates, budget) needed for filtering and group-by in analytics.

### DD-AD-3: Table naming `fact_advertising` — RESOLVED

Renamed from `fact_advertising_costs` to `fact_advertising`. Fullstats contains not only costs (spend) but also views, clicks, orders, conversions. Limiting to "costs" is artificially narrow.
