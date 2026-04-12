# Promo & Advertising — Provider Read & Write Contracts

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

### 1.4 Upload Products to Promotion (WRITE) — PARTIALLY VERIFIED (2026-03-31)

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | confirmed |
| Path | `/api/v1/calendar/promotions/upload` | confirmed |
| Base URL | `https://dp-calendar-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) — **requires Write-scoped token** | confirmed |
| Status | Endpoint accessible, **token scope blocks full testing** | — |

### Open Questions (see [promotions.md P-4](../modules/promotions.md))

| # | Вопрос | Статус |
|---|--------|--------|
| 1 | Точный формат запроса (JSON body vs multipart) | partially confirmed — JSON body, path confirmed |
| 2 | Поведение при невалидных SKU (rejection structure) | not confirmed (need write token) |
| 3 | Ограничения по timing (можно ли менять участие после `startDateTime`) | not confirmed |
| 4 | Rate limits для write endpoint (same bucket as reads?) | not confirmed |
| 5 | Requires separate Promotion-scoped token or same API key | **confirmed: needs Write scope** (401: "read-only token scope not allowed for this route") |

### Assumed Request Structure

```json
{
  "promotionId": 2235,
  "nomenclatures": [
    {
      "nmId": 987654,
      "actionPrice": 499
    }
  ]
}
```

> **NOTE:** Path is confirmed (`POST /api/v1/calendar/promotions/upload`). Request body format still speculative — needs verification with write-scoped token. All alternative paths (nomenclatures/upload, nomenclatures/join, activate, products) return 404.

**Verification evidence (2026-03-31):**
- `POST /upload` → 401: `{"title":"unauthorized","detail":"read-only token scope not allowed for this route","origin":"s2sauth-calendar"}`
- Alternative paths → 404
- Official docs (dev.wildberries.ru) confirm: `POST /api/v1/calendar/promotions/upload` = "Add Product to the Promotion"

### Remaining Blocker

WB promo write body format cannot be fully tested until:
1. ~~API documentation path~~ — **CONFIRMED**: `POST /api/v1/calendar/promotions/upload`
2. Write-scoped token is provisioned (current token has read-only scope)

---

## 2. WB ADVERTISING (Advert API)

> **Bidding-specific contracts** (change bids, min bids, bid recommendations, campaign management)
> описаны в отдельном документе: [wb-advertising-bidding-contracts.md](wb-advertising-bidding-contracts.md).

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
| Method | `GET` | confirmed |
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

### v3 Endpoint Verification (2026-03-31)

- `GET /adv/v3/fullstats?ids=1&beginDate=2026-03-01&endDate=2026-03-30` → 200 OK, returns `null` (no matching campaigns — expected for account without ads)
- `POST /adv/v2/fullstats` (old) → 404 (confirmed dead)
- `GET /api/advert/v2/adverts` (campaigns list) → 200 OK, `{"adverts":[]}` (empty — no campaigns)

**Conclusion:** v3 GET endpoint is fully accessible. Returns `null` for non-existent campaign IDs (not an error). v2 POST is confirmed dead.

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

### 3.4 Activate Products in Action (WRITE)

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/v1/actions/products/activate` | confirmed-docs |
| Base URL | `https://api-seller.ozon.ru` | confirmed-docs |
| Auth | `Client-Id` + `Api-Key` headers | confirmed-docs |
| Rate limit group | `OZON_PROMO` (20/60s) | assumed (same bucket as reads) |

### Request Body

```json
{
  "action_id": 1977747,
  "products": [
    {
      "product_id": 12345,
      "action_price": 499.0,
      "stock": 100
    }
  ]
}
```

| Field | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `action_id` | long | yes | Target promotion ID | confirmed-docs |
| `products` | array | yes | Products to activate | confirmed-docs |
| `products[].product_id` | long | yes | Ozon product ID | confirmed-docs |
| `products[].action_price` | double | yes | Price for promo (must be ≤ `max_action_price`) | confirmed-docs |
| `products[].stock` | int | no | Stock quantity | confirmed-docs |

### Response Structure — confirmed-docs

```json
{
  "result": {
    "product_ids": [12345],
    "rejected": [
      {
        "product_id": 99999,
        "reason": "Product not eligible for this action"
      }
    ]
  }
}
```

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `result.product_ids` | long[] | Successfully activated products | confirmed-docs |
| `result.rejected` | array | Rejected products with reasons | confirmed-docs |
| `result.rejected[].product_id` | long | Rejected product ID | confirmed-docs |
| `result.rejected[].reason` | string | Rejection reason (human-readable) | confirmed-docs |

### Idempotency

Activating an already-participating product is a **no-op**: it appears in `product_ids` without error. This makes the endpoint retry-safe.

### Constraints

- `action_price` must be ≤ `max_action_price` (from candidates/products response); otherwise rejected.
- Products must be in eligible/candidate set; otherwise rejected with reason.
- After `freeze_date` — activate may be rejected (unverified; depends on Ozon internal rules).

---

### 3.5 Deactivate Products from Action (WRITE)

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/v1/actions/products/deactivate` | confirmed-docs |
| Base URL | `https://api-seller.ozon.ru` | confirmed-docs |
| Auth | `Client-Id` + `Api-Key` headers | confirmed-docs |
| Rate limit group | `OZON_PROMO` (20/60s) | assumed |

### Request Body

```json
{
  "action_id": 1977747,
  "product_ids": [12345, 67890]
}
```

| Field | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `action_id` | long | yes | Target promotion ID | confirmed-docs |
| `product_ids` | long[] | yes | Products to remove from promo | confirmed-docs |

### Response Structure — confirmed-docs

```json
{
  "result": {
    "product_ids": [12345],
    "rejected": [
      {
        "product_id": 67890,
        "reason": "Product is not participating in this action"
      }
    ]
  }
}
```

Same structure as activate response.

### Idempotency

Deactivating a non-participating product → **rejected** with reason (not a no-op). Retry-safe only when product was actually participating.

### Constraints

- After `freeze_date` — deactivate may be rejected.
- Banned products cannot be deactivated (already removed by Ozon).

---

### 3.6 Write Contracts — Reconciliation Strategy

After activate/deactivate, `canonical_promo_product.participation_status` is updated optimistically based on API response:
- Activate success → `PARTICIPATING`
- Deactivate success → `REMOVED`

Authoritative reconciliation occurs at next `PROMO_SYNC`: ETL re-reads products from §3.2 and candidates from §3.3, applying the conditional UPSERT logic described in [Promotions → Write boundary](../modules/promotions.md#write-boundary-canonical_promo_product).

---

## 4. OZON ADVERTISING (Performance API)

> **Bidding-specific contracts** (set bids, recommended bids, promo products, batch enable/disable)
> описаны в отдельном документе: [ozon-advertising-bidding-contracts.md](ozon-advertising-bidding-contracts.md).

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Status | **READ IMPLEMENTED** — OAuth2 token + campaigns + stats adapters working | code-verified |
| Base URL | `https://api-performance.ozon.ru` | confirmed |
| Auth | **OAuth2 `client_credentials`** (NOT `Client-Id` + `Api-Key`) | confirmed |
| Rate limit | 100,000 requests/day per Performance account | confirmed-docs |
| EndpointKey | `FACT_OZON_ADVERTISING_DAILY` (defined but not in YAML config) | code-verified |

> **HOST MIGRATION (Jan 2025):** `performance.ozon.ru` → `api-performance.ozon.ru`. **Verified 2026-03-31:** old host returns 404, new host is accessible.

### Current State

OAuth2 token exchange and read adapters are **fully implemented**:
- `OzonPerformanceTokenService` — token exchange + caching (25 min TTL)
- `OzonAdvertisingReadAdapter` — campaigns list + SKU-level statistics
- `OzonAdvertisingFactSource` — ETL event source (skips if no Performance credentials)

Requires only real Performance API credentials to activate.

**Verification (2026-03-31):**
- Token endpoint `POST /api/client/token` → 401 `{"error":"invalid_client","error_description":"Client authentication failed"}` (endpoint exists and responds correctly for invalid credentials)
- Old host `performance.ozon.ru` → 404 (confirmed dead)
- **Conclusion:** OAuth2 infrastructure is accessible. Only real Performance API credentials are needed (not a code blocker).

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

**Error response for invalid credentials (verified 2026-03-31):**
```json
{"error":"invalid_client","error_description":"Client authentication failed"}
```

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

## 5. YANDEX PROMOTIONS (Business API)

> **Phase E/F scope, not MVP.** Yandex promo integration is planned for later phases.
> Contracts documented here for completeness and forward planning.

### 5.1 List Promotions

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/v2/businesses/{businessId}/promos` | confirmed |
| Base URL | `https://api.partner.market.yandex.ru` | confirmed |
| Auth | `Api-Key` header | confirmed |
| Scope | `pricing`, `pricing:read-only`, `promotion`, `promotion:read-only`, `all-methods` | confirmed-docs |
| Level | **Business-level** | confirmed-docs |

### Request Body

```json
{
  "participation": "PARTICIPATING_NOW",
  "mechanics": "DIRECT_DISCOUNT"
}
```

| Field | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `participation` | string | no | Filter: `PARTICIPATING_NOW`, `PARTICIPATED` (last year). Default: all eligible + participating | confirmed-docs |
| `mechanics` | string | no | Filter by mechanics type | confirmed-docs |

### Mechanics Type Enum

| Value | Semantics | Confidence |
|-------|-----------|------------|
| `DIRECT_DISCOUNT` | Direct price discount | confirmed-docs |
| `BLUE_FLASH` | Flash sale | confirmed-docs |
| `MARKET_PROMOCODE` | Promo code discount | confirmed-docs |

### Response Structure

```json
{
  "status": "OK",
  "result": {
    "promos": [
      {
        "id": "promo-123",
        "name": "Весенняя распродажа",
        "period": {
          "dateTimeFrom": "2025-03-01T00:00:00Z",
          "dateTimeTo": "2025-03-31T23:59:59Z"
        },
        "participating": true,
        "assortmentInfo": {
          "activeOffers": 15,
          "potentialOffers": 50
        },
        "mechanicsInfo": {
          "type": "DIRECT_DISCOUNT"
        },
        "channels": ["MARKET"],
        "constraints": {
          "discountPercent": { "min": 10, "max": 95 }
        }
      }
    ]
  }
}
```
*(synthetic, based on official contract)*

### Response Fields

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `id` | string | Unique promotion ID | confirmed-docs |
| `name` | string | Promo name | confirmed-docs |
| `period.dateTimeFrom` | string | Start datetime (ISO 8601) | confirmed-docs |
| `period.dateTimeTo` | string | End datetime (ISO 8601) | confirmed-docs |
| `participating` | boolean | Whether seller currently participates | confirmed-docs |
| `assortmentInfo.activeOffers` | int | Offers currently in promo | confirmed-docs |
| `assortmentInfo.potentialOffers` | int | Eligible offers | confirmed-docs |
| `mechanicsInfo.type` | string | Mechanics type enum | confirmed-docs |
| `channels` | string[] | Promo channels (e.g., `MARKET`) | confirmed-docs |
| `constraints.discountPercent.min` | number | Minimum required discount % | confirmed-docs |
| `constraints.discountPercent.max` | number | Maximum allowed discount % | confirmed-docs |
| `bestsellerInfo` | object | Bestseller promo info (if applicable) | confirmed-docs |

### Pagination

No pagination — returns all matching promos in a single response.

### Rate Limits

| Property | Value | Confidence |
|----------|-------|------------|
| Rate limit | 1,000 req/hour | confirmed-docs |

---

### 5.2 Promo Offers (Products in Promo)

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/v2/businesses/{businessId}/promos/offers` | confirmed |
| Base URL | `https://api.partner.market.yandex.ru` | confirmed |
| Auth | `Api-Key` header | confirmed |
| Scope | `pricing`, `pricing:read-only`, `promotion`, `promotion:read-only`, `all-methods` | confirmed-docs |
| Level | **Business-level** | confirmed-docs |

### Request Body

```json
{
  "promoId": "promo-123",
  "statusType": "PARTICIPATING"
}
```

| Field | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `promoId` | string | yes | Target promotion ID | confirmed-docs |
| `statusType` | string | no | Filter: `PARTICIPATING`, `READY_FOR_PARTICIPATING` | confirmed-docs |

### Response Fields

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `offers[].offerId` | string | Seller's SKU | confirmed-docs |
| `offers[].participationStatus` | string | `PARTICIPATING`, `READY_FOR_PARTICIPATING` | confirmed-docs |
| `offers[].currentPrice` | object | Current price | confirmed-docs |
| `offers[].maxPromoPrice` | number | Max allowed promo price | confirmed-docs |

### Pagination

| Property | Value | Confidence |
|----------|-------|------------|
| Type | Cursor-based (`pageToken` + `limit`) | confirmed-docs |
| Max page size | 500 | confirmed-docs |

---

### 5.3 Update Promo Offers (WRITE)

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/v2/businesses/{businessId}/promos/offers/update` | confirmed-docs |
| Level | **Business-level** | confirmed-docs |

### Request Body

```json
{
  "promoId": "promo-123",
  "offers": [
    {
      "offerId": "SKU-001",
      "params": {
        "discountParams": {
          "promoPrice": 450
        }
      }
    }
  ]
}
```

| Field | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `promoId` | string | yes | Target promotion ID | confirmed-docs |
| `offers[].offerId` | string | yes | Seller's SKU | confirmed-docs |
| `offers[].params.discountParams.promoPrice` | number | yes | Promo price (must be ≤ `maxPromoPrice`) | confirmed-docs |

### Constraints

- `promoPrice` must be ≤ `maxPromoPrice` from promo offers response
- Discount must meet `constraints.discountPercent.min` requirement from promo listing

---

### 5.4 Delete Promo Offers (WRITE)

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/v2/businesses/{businessId}/promos/offers/delete` | confirmed-docs |
| Level | **Business-level** | confirmed-docs |

### Request Body

```json
{
  "promoId": "promo-123",
  "offerIds": ["SKU-001", "SKU-002"]
}
```

| Field | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `promoId` | string | yes | Target promotion ID | confirmed-docs |
| `offerIds` | string[] | yes | SKUs to remove from promo | confirmed-docs |

---

### 5.5 Canonical Mapping

| Yandex field | → Canonical target | Confidence |
|---|---|---|
| `id` (promos) | `dim_promo_campaign.external_campaign_id` | C-docs |
| `name` | `dim_promo_campaign.name` | C-docs |
| `period.dateTimeFrom` | `dim_promo_campaign.start_date` | C-docs |
| `period.dateTimeTo` | `dim_promo_campaign.end_date` | C-docs |
| `mechanicsInfo.type` | `dim_promo_campaign.promo_type` | C-docs |
| `participating` | `dim_promo_campaign.is_participating` | C-docs |
| promo offers `offerId` | `fact_promo_product.offer_id` → lookup → `marketplace_offer_id` | C-docs |
| promo offers `maxPromoPrice` | `fact_promo_product.max_promo_price` | C-docs |
| promo offers `participationStatus` | `fact_promo_product.participation_status` | C-docs |

> **Phase E/F scope, not MVP.** Promo sync for Yandex will be implemented when the promo module is extended to support Yandex mechanics.

---

## 6. YANDEX SALES BOOST / ADVERTISING (Business API)

> **Phase E/F scope, not MVP.** Yandex Sales Boost (bidding) integration is planned for later phases.
> Read contracts documented here; write contracts in [wb-advertising-bidding-contracts.md](wb-advertising-bidding-contracts.md) pattern.

### 6.1 Read Current Bids

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | confirmed |
| Path | `/v2/businesses/{businessId}/bids/info` | confirmed (F-2: empirically discovered) |
| Base URL | `https://api.partner.market.yandex.ru` | confirmed |
| Auth | `Api-Key` header | confirmed |
| Scope | `pricing`, `pricing:read-only`, `promotion`, `promotion:read-only`, `all-methods` | confirmed-docs |
| Level | **Business-level** | confirmed-docs |

> **CRITICAL (F-2):** Read endpoint is `POST /bids/info`, NOT `POST /bids`. The `/bids` path only accepts `PUT` (write).

### Request Body

```json
{
  "skus": ["SKU-001", "SKU-002"]
}
```

Empty `skus` (or omitted) returns all SKUs with bids, paginated.

| Field | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `skus` | string[] | no | Filter by seller SKUs. Max 500 | confirmed-docs |

### Response Structure

```json
{
  "status": "OK",
  "result": {
    "bids": [
      { "sku": "SKU-001", "bid": 570 }
    ],
    "paging": { "nextPageToken": "eyJ..." }
  }
}
```

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `bids[].sku` | string | Seller's SKU (= `offerId` from catalog) | confirmed-docs |
| `bids[].bid` | integer | Bid value (0–9999) | confirmed-docs |

### Bid Semantics

| Property | Value | Confidence |
|----------|-------|------------|
| Encoding | Percent of item cost × 100 (e.g., 570 = 5.7%) | confirmed-docs |
| Min effective | 50 (0.5%) | confirmed-docs |
| Max | 9999 (99.99%) | confirmed-docs |
| 0 | No bid set | confirmed-docs |

### Pagination

| Property | Value | Confidence |
|----------|-------|------------|
| Type | Cursor-based (`pageToken` + `limit`) | confirmed-docs |
| Default page size | 250 | confirmed-docs |
| Max page size | 500 | confirmed-docs |
| When `skus` specified | Single page, ignores `pageToken`/`limit` | confirmed-docs |

### Rate Limits

| Property | Value | Confidence |
|----------|-------|------------|
| Rate limit | 500 req/min (1,000 with subscription) | confirmed-docs |
| Until 18.05.2026 | 1,000 req/min (old limit) | confirmed-docs |

### Known Limitation

Only returns bids set via API — bids set in Yandex Partner cabinet are NOT returned (confirmed-docs).

---

### 6.2 Bid Recommendations

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/v2/businesses/{businessId}/bids/recommendations` | confirmed-docs |
| Level | **Business-level** | confirmed-docs |

Returns recommended bid values per SKU, including `minBid`, `maxBid`, `currentBid`.

### Request Body

```json
{
  "skus": ["SKU-001", "SKU-002"]
}
```

### Response Fields

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `recommendations[].sku` | string | Seller's SKU | confirmed-docs |
| `recommendations[].minBid` | integer | Minimum effective bid | confirmed-docs |
| `recommendations[].maxBid` | integer | Maximum recommended bid | confirmed-docs |
| `recommendations[].currentBid` | integer | Current bid value | confirmed-docs |

---

### 6.3 Set Bids (WRITE)

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `PUT` | confirmed (empirically discovered) |
| Path | `/v2/businesses/{businessId}/bids` | confirmed |
| Level | **Business-level** | confirmed-docs |
| Scope | `pricing`, `promotion`, `all-methods` | confirmed-docs |

### Request Body

```json
{
  "bids": [
    { "sku": "SKU-001", "bid": 570 }
  ]
}
```

| Field | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `bids[].sku` | string | yes | Seller's SKU | confirmed-docs |
| `bids[].bid` | integer | yes | Bid value (0–9999). Set to 0 to remove bid | confirmed-docs |

### Response

```json
{
  "status": "OK"
}
```

### Idempotency

Last-write-wins. Setting same bid value is a no-op (accepted without error).

---

### 6.4 Canonical Mapping

| Yandex field | → Canonical target | Confidence |
|---|---|---|
| `bids[].sku` | `marketplace_offer` via `offerId` join | C-docs |
| `bids[].bid` | `fact_advertising.bid_value` (if modeled) | A |
| recommendations `minBid` / `maxBid` | Used by autobidding algorithm, not persisted | A |

**Join key:** `bids[].sku` = catalog `offer.offerId` = `marketplace_offer.external_sku` or via `seller_sku.sku_code` lookup.

> **Phase E/F scope, not MVP.** Yandex Sales Boost bidding will be integrated as part of the autobidding module.
> Existing WB/Ozon bidding patterns (see dedicated bidding contracts docs) serve as implementation reference.

---

## 7. ETL PIPELINE MAPPING

### Event Sources

| EventSource | Pipeline | Marketplace | Raw table | Order |
|-------------|----------|-------------|-----------|-------|
| `WbPromoEventSource` | `PROMO_SYNC` | WB | `raw_wb_promotions` | 0 |
| `WbPromoNomenclaturesEventSource` | `PROMO_SYNC` | WB | `raw_wb_promotion_nomenclatures` | 1 |
| `OzonPromoEventSource` | `PROMO_SYNC` | OZON | `raw_ozon_actions` | 0 |
| `OzonPromoActionProductsEventSource` | `PROMO_SYNC` | OZON | `raw_ozon_action_products` | 1 |
| `WbAdvertisingEventSource` | `ADVERTISING_FACT` | WB | `raw_wb_advertising_fullstats` | 0 |
| `OzonAdvertisingEventSource` | `ADVERTISING_FACT` | OZON | (stub — empty) | 0 |
| `YandexPromoEventSource` | `PROMO_SYNC` | YANDEX_MARKET | `raw_yandex_promos` | 0 |
| `YandexPromoOffersEventSource` | `PROMO_SYNC` | YANDEX_MARKET | `raw_yandex_promo_offers` | 1 |
| `YandexBidsEventSource` | `ADVERTISING_FACT` | YANDEX_MARKET | `raw_yandex_bids` | 0 |

### Materialization Handlers

| Handler | Target tables |
|---------|--------------|
| `PromoWbMaterializationHandler` | `dim_promo_campaign` + `fact_promo_product` |
| `PromoOzonMaterializationHandler` | `dim_promo_campaign` + `fact_promo_product` |
| `AdvertisingFactWildberriesMaterializationHandler` | `dim_advertising_campaign` + `fact_advertising` |
| `AdvertisingFactOzonMaterializationHandler` | `dim_advertising_campaign` + `fact_advertising` |
| `PromoYandexMaterializationHandler` | `dim_promo_campaign` + `fact_promo_product` |
| `AdvertisingFactYandexMaterializationHandler` | `dim_advertising_campaign` + `fact_advertising` |

> **Naming decision (DD-AD-3):** Target table is `fact_advertising` (not `fact_advertising_costs`),
> because fullstats contains not only costs but also views, clicks, orders, conversions.
> All documents updated to use `fact_advertising` consistently.

---

## 8. FINDINGS & CRITICAL ISSUES

### F-1: WB Advertising Campaigns Endpoint Migration (CRITICAL — blocks Phase B)

**Finding:** `GET /adv/v1/promotion/adverts` returns **404** — deprecated and removed.
**New endpoint:** `GET /api/advert/v2/adverts`
**Impact:** WB advertising campaign discovery is **completely broken**.
**Fix applied:** YAML config updated to new URL.
**Remaining work:**
- Response wraps in `{"adverts":[...]}` instead of root array — `WbAdvertisingAdvertIdsExtractor` needs adaptation
- Expand DTO to extract dimension fields (name, type, status, dates, placement) for `dim_advertising_campaign`
- Verify status filter codes `[7, 9, 11]` are still valid in v2

### F-2: WB Advertising Full Stats v2→v3 Migration (CRITICAL — blocks Phase B) — ENDPOINT VERIFIED

**Finding:** `POST /adv/v2/fullstats` was **disabled Sept 30, 2025** — returns 404.
**New endpoint:** `GET /adv/v3/fullstats` — **VERIFIED (2026-03-31):** returns 200 OK (null for no-data, expected).
**Impact:** WB advertising statistics ingestion is **completely broken** with v2 adapter.
**Fix applied:** YAML config updated to new URL.
**BREAKING:** HTTP method changed from POST to GET. Adapter code migration required:
1. Change from POST with JSON body `[{id, dates}]` to GET with query params `ids`, `beginDate`, `endDate`
2. Batch campaign IDs (max 50 per request)
3. Flatten hierarchical response (campaign → days → apps → nms) into flat rows
4. Handle `canceled` field
5. Skip current-day queries (returns 0 for some metrics)
6. New grain: product-level daily breakdown (nmId available in response)

### F-3: Ozon Advertising — OAuth2 Implemented, Credentials Needed (MEDIUM) — UPDATED 2026-04-12

**Finding:** OAuth2 infrastructure is **fully implemented** in code:
- `OzonPerformanceTokenService` — token exchange + Caffeine cache (25 min TTL)
- `OzonAdvertisingReadAdapter` — campaigns + SKU-level statistics capture
- `CredentialResolver.resolvePerformanceCredentials()` — vault integration
- `OzonPerformanceCampaignDto`, `OzonPerformanceStatDto` — DTOs ready

**Verification (2026-03-31):** Token endpoint `POST /api/client/token` returns proper 401 for invalid creds (endpoint confirmed accessible). Old host `performance.ozon.ru` returns 404 (migration confirmed).
**Impact:** Code is ready, but no data is being ingested because real credentials are not provisioned.
**Remaining work:**
1. **Obtain real Performance API credentials** from seller.ozon.ru → Settings → API Keys → Performance API
2. Empirical verification of campaign/stats response schemas with real credentials
3. For autobidding: implement bidding write adapters (see [ozon-advertising-bidding-contracts.md](ozon-advertising-bidding-contracts.md))

### F-4: DTO Expansion Required (MEDIUM — blocks Phase B)

**Finding:** Existing WB advertising DTO extracts only `advertId` from campaigns endpoint. For `dim_advertising_campaign`, need: name, type, status, placement, dates, budget.
**Impact:** Cannot build advertising campaign dimension without expanded DTO.
**Remaining work:** Expand `WbAdvertisingCampaignDto` and `WbAdvertisingAdvertIdsExtractor`.

### F-5: Fullstats Response Flattening (MEDIUM — blocks Phase B)

**Finding:** WB v3 fullstats returns hierarchical JSON (campaign → days → apps → nms). Current materializer expects flat structure.
**Impact:** Materializer must flatten hierarchy into per-product daily rows for `fact_advertising`.
**Remaining work:** New response parser/flattener for hierarchical v3 format.

---

## 9. SUMMARY STATUS

| Capability | WB | Ozon | Yandex | Phase | Status |
|------------|-----|------|--------|-------|--------|
| Promo list | **READY** (verified) | **READY** (verified) | **READY** (docs) | F | All three documented |
| Promo details | READY (code-verified) | N/A | N/A | F | — |
| Promo products | READY (code-verified) | READY (code-verified) | READY (docs) | F | Need data verification |
| Promo candidates | N/A | READY (code-verified) | N/A | F | — |
| Promo write (update/delete) | N/A | N/A | READY (docs) | E/F | — |
| Ad campaigns (dim) | **NEEDS WORK** (F-1, F-4) | **READY** (code implemented, needs credentials) | N/A | **B** | WB: DTO expansion; Ozon: get real credentials |
| Ad stats (fact) | **READY** (v3 endpoint verified) | **READY** (code implemented, needs credentials) | N/A | **B** | v3 GET confirmed; Ozon: code ready, needs credentials |
| Bids read | N/A | N/A | **READY** (endpoint confirmed) | E/F | F-2: bids/info confirmed |
| **Bidding write (ставки)** | **DOCUMENTED** | **DOCUMENTED** | **DOCUMENTED** (PUT /bids) | **Bidding MVP** | See dedicated bidding contracts docs |
| **Bid recommendations** | **DOCUMENTED** | **DOCUMENTED** | **DOCUMENTED** | **Bidding MVP** | See dedicated bidding contracts docs |

## 10. DESIGN DECISIONS

### DD-AD-1: No canonical entity for advertising — RESOLVED

Advertising data flows Raw → ClickHouse directly, bypassing canonical PostgreSQL layer. This is a documented exception to pipeline invariant №1 (Raw → Normalized → Canonical → Analytics).

**Rationale:** Advertising data is used exclusively for analytics (P&L allocation, pricing signal `ad_cost_ratio`). No business decision flow reads advertising state from PostgreSQL. No action lifecycle depends on advertising canonical truth.

**Data provenance:** Maintained through `job_execution_id` stored in `fact_advertising` (ClickHouse column). Drill-down: `fact_advertising.job_execution_id` → `job_item.s3_key` → S3 raw payload.

### DD-AD-2: `dim_advertising_campaign` — RESOLVED

Create a separate dimension table for advertising campaigns. Campaigns have attributes (name, type, status, placement, dates, budget) needed for filtering and group-by in analytics.

### DD-AD-3: Table naming `fact_advertising` — RESOLVED

Renamed from `fact_advertising_costs` to `fact_advertising`. Fullstats contains not only costs (spend) but also views, clicks, orders, conversions. Limiting to "costs" is artificially narrow.
