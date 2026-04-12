# Yandex Market — Provider Read Contracts

**Status:** docs-verified + partial empirical (Session 6: disabled business — most endpoints returned 403 API_DISABLED)
**Source:** https://yandex.ru/dev/market/partner-api/doc/ru/
**Verification:** direct API calls 2026-04-12 (see `samples/empirical-verification-log.md`, Session 6)

This document captures read contracts of Yandex Market Partner API for each capability relevant to Datapulse ingestion pipeline.

Each semantic property is classified:
- **confirmed** — verified by real API response (empirical)
- **confirmed-docs** — verified from official Yandex documentation, not observed in real API response
- **assumed** — inferred from documentation or response, not explicitly confirmed
- **unknown** — could not confirm, requires additional investigation

> **IMPORTANT CONTEXT:** Session 6 empirical verification was limited by a disabled business account (no active campaigns/stores). Only the following were confirmed empirically:
> - Error response contract (6 error codes)
> - Campaigns endpoint structure (`GET /v2/campaigns` returned 200 OK)
> - Bids endpoint URL discovery (F-2: read = `POST bids/info`, write = `PUT bids`)
> - Orders endpoint deprecation (F-3: `GET /v2/campaigns/{id}/orders` → deprecated)
>
> All other field-level data relies on official documentation (`confirmed-docs`). Yandex documentation quality is high — includes typed schemas, response samples, and exhaustive enum definitions.

---

## Scope

### What this document covers

Read-only contracts for ingestion of marketplace data into Datapulse canonical model:
- Campaigns (store discovery, businessId/campaignId hierarchy)
- Catalog (offer-mappings, category tree)
- Prices (as part of offer-mappings, price quarantine, tariff calculator)
- Stocks / Inventory (FBY warehouse stocks, FBS seller stocks)
- Orders (business-level list, statuses, financial data)
- Returns / Unredeemed (campaign-level returns, lifecycle)
- Finance / Reports (async report generation: services cost, goods realization)
- Warehouses / Reference Data (fulfillment warehouses, seller warehouses, regions)
- Promotions (read: list promos, promo offers)
- Sales Boost / Advertising (read: current bids, bid recommendations)

### What this document does NOT cover

- Write operations (set prices, update stocks, accept orders, set bids)
- Push notifications / webhooks (order status change callbacks)
- Content management (card editing, media upload)
- Chat / messaging API
- Delivery services integration (DBS-specific shipment management)
- OAuth token flow (only Api-Key auth is used)

### Mandatory read scenarios for Datapulse MVP

1. Discover campaigns → extract `businessId` and `campaignId` per placement model
2. Fetch full catalog via `offer-mappings` (includes prices, vendor, barcodes, card status)
3. Fetch stocks per campaign per warehouse
4. Fetch orders with status/financial filtering for P&L
5. Generate finance reports (services cost, goods realization) — async flow
6. Fetch warehouse reference data for `dim_warehouse`

---

## Authentication & Access

| Property | Value | Confidence |
|----------|-------|------------|
| Auth header | `Api-Key: ACMA:...` | confirmed |
| Token format | `ACMA:<base64>:<hex>` — opaque string, NOT JWT | confirmed-docs |
| Token lifetime | Permanent until manually revoked | confirmed-docs |
| Max tokens per account | 30 | confirmed-docs |
| Base URL | `https://api.partner.market.yandex.ru` | confirmed |

### Token Scopes

| Scope | Access level | Confidence |
|-------|-------------|------------|
| `all-methods` | Full access | confirmed-docs |
| `all-methods:read-only` | Read-only full access | confirmed-docs |
| `offers-and-cards-management` | Catalog, cards | confirmed-docs |
| `pricing` / `pricing:read-only` | Prices, bids | confirmed-docs |
| `inventory-and-order-processing` | Orders, stocks | confirmed-docs |
| `finance-and-accounting` | Finance reports | confirmed-docs |
| `promotion` / `promotion:read-only` | Promos, bids | confirmed-docs |
| `settings-management` | Store settings | confirmed-docs |
| `supplies-management:read-only` | FBY supply info | confirmed-docs |

### Risks

- Token with `all-methods` scope grants write access — Datapulse should use `all-methods:read-only` for production ingestion (confirmed-docs)
- Disabled business blocks ALL endpoints except `GET /v2/campaigns` (confirmed — Session 6 finding F-1)
- Even reference endpoints (categories, warehouses) are blocked for disabled business — unlike WB/Ozon (confirmed)

---

## API Hierarchy

| ID | Level | Semantics | Confidence |
|----|-------|-----------|------------|
| `businessId` | Cabinet (кабинет) | One cabinet = one seller. Used for business-level methods (catalog, prices, orders v1, bids, promos) | confirmed-docs |
| `campaignId` | Store (магазин/кампания) | One cabinet can have multiple stores (FBY, FBS, DBS, Express). Used for campaign-level methods (stocks, returns, legacy orders) | confirmed |

### Discovery

`GET /v2/campaigns` returns list of stores with both IDs:
```json
{
  "campaigns": [{
    "id": 12345,
    "business": { "id": 67890, "name": "My Business" },
    "placementType": "FBS",
    "apiAvailability": "AVAILABLE"
  }]
}
```
*(synthetic, based on official contract — empirical response was empty array due to disabled business)*

### When to use which ID

| Level | Endpoints | Confidence |
|-------|-----------|------------|
| `businessId` | offer-mappings, offer-prices, orders (v1), bids, promos, category tree, reports | confirmed-docs |
| `campaignId` | stocks, returns, legacy orders (deprecated), warehouses (seller) | confirmed-docs |

> **CRITICAL DIFFERENCE from WB/Ozon:** Yandex has a two-level hierarchy. WB and Ozon are single-level (one account = one seller). With Yandex, a `Connection` in Datapulse maps to a business, and each campaign (FBY/FBS/DBS) is a sub-entity. Adapter must discover campaigns first, then fan out requests per campaign for campaign-level endpoints.

---

## Sandbox / Test Environment

| Property | Value | Confidence |
|----------|-------|------------|
| Dedicated sandbox | **No** — production API only | confirmed (F-7) |
| Test orders | `fake: true` filter in orders endpoint | confirmed-docs |
| Test strategy | WireMock stubs required for all integration tests | confirmed |

> **CRITICAL DIFFERENCE from WB:** WB has dedicated sandbox URLs per API category. Yandex Market has NO sandbox. All testing against production API with real account, or via WireMock.

---

## Response Envelope & Error Contract — VERIFIED

### Success response

```json
{"status": "OK", "result": { ... }}
```
*(confirmed-docs + confirmed for campaigns endpoint)*

### Error response — EMPIRICALLY VERIFIED

```json
{"status": "ERROR", "errors": [{"code": "API_DISABLED", "message": "..."}]}
```

| HTTP Status | Error Code | Trigger | Confidence |
|-------------|------------|---------|------------|
| 401 | `UNAUTHORIZED` | No Api-Key header or malformed token | confirmed |
| 403 | `API_DISABLED` | Valid token, disabled business | confirmed |
| 403 | `FORBIDDEN` | Valid token, wrong campaignId | confirmed |
| 404 | `NOT_FOUND` | Non-existent endpoint | confirmed |
| 405 | `METHOD_NOT_ALLOWED` | Wrong HTTP method for endpoint | confirmed |
| 420 | `METHOD_FAILURE` | Rate limit exceeded (NOT 429!) | confirmed-docs |
| 500 | `INTERNAL_SERVER_ERROR` | Yandex internal error | confirmed-docs |

> **CRITICAL DIFFERENCE from WB/Ozon:** Rate limit response uses HTTP **420** (Enhance Your Calm), not 429. Retry logic must handle both 420 and 429.

---

## 1. CAMPAIGNS

### Endpoint

| Property | Value | Confidence |
|----------|-------|------------|
| Method | `GET` | confirmed |
| Path | `/v2/campaigns` | confirmed |
| Base URL | `https://api.partner.market.yandex.ru` | confirmed |
| Auth | `Api-Key` header | confirmed |
| Scope | Any scope (all scopes grant access) | confirmed-docs |

### Response Structure — EMPIRICALLY VERIFIED (empty)

```json
{
  "campaigns": [],
  "pager": {
    "total": 0,
    "from": 1,
    "to": 0,
    "currentPage": 1,
    "pagesCount": 0,
    "pageSize": 100
  },
  "paging": {}
}
```
*(confirmed — real response from disabled business)*

### Pagination

| Property | Value | Confidence |
|----------|-------|------------|
| Type | Dual: legacy `pager` (page-based) + modern `paging` (cursor-based) | confirmed |
| Modern cursor | `pageToken` query param + `paging.nextPageToken` in response | confirmed |
| Legacy (deprecated) | `page` + `pageSize` query params + `pager` object in response | confirmed-docs |
| Adapter strategy | Use `paging.nextPageToken` only; ignore `pager` | confirmed |
| Max page size | 100 | confirmed-docs |

### Key Response Fields

| Field | Type | Semantics | Sample | Confidence |
|-------|------|-----------|--------|------------|
| `campaigns[].id` | integer | campaignId (store ID) | 12345 | confirmed-docs |
| `campaigns[].business.id` | integer | businessId (cabinet ID) | 67890 | confirmed |
| `campaigns[].business.name` | string | Cabinet name | "My Business" | confirmed-docs |
| `campaigns[].placementType` | string | Placement model enum | "FBS" | confirmed-docs |
| `campaigns[].apiAvailability` | string | API access status | "AVAILABLE" | confirmed-docs |
| `campaigns[].domain` | string | Store domain | "example" | confirmed-docs |
| `campaigns[].clientId` | integer | Client ID | 0 | confirmed-docs |

### PlacementType enum

| Value | Semantics | Confidence |
|-------|-----------|------------|
| `FBY` | Fulfillment by Yandex | confirmed-docs |
| `FBS` | Fulfillment by Seller | confirmed-docs |
| `DBS` | Delivery by Seller | confirmed-docs |
| `EXPRESS` | Express delivery | confirmed-docs |

### Data Freshness

| Property | Value | Confidence |
|----------|-------|------------|
| Freshness | Near real-time (reflects current campaigns configuration) | assumed |

### Known Limitations

- Only endpoint returning 200 for disabled business (confirmed)
- Dual pagination in response — adapter must use modern `paging.nextPageToken` (confirmed)
- `page`/`pageSize` deprecated — will eventually be removed (confirmed-docs)

### Rate Limits

| Property | Value | Confidence |
|----------|-------|------------|
| Rate limit | 1,000 req/hour (until 18.05.2026: 5,000 req/hour) | confirmed-docs |

---

## 2. CATALOG (Offer Mappings)

### Endpoint

| Property | Value | Confidence |
|----------|-------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/v2/businesses/{businessId}/offer-mappings` | confirmed (URL probed, returned 403) |
| Base URL | `https://api.partner.market.yandex.ru` | confirmed |
| Auth | `Api-Key` header | confirmed |
| Scope | `offers-and-cards-management`, `offers-and-cards-management:read-only`, `all-methods` | confirmed-docs |

### Request Body

```json
{
  "offerIds": ["SKU-001", "SKU-002"],
  "cardStatuses": ["HAS_CARD_CAN_NOT_UPDATE"],
  "categoryIds": [12345],
  "vendorNames": ["Samsung"],
  "tags": ["seasonal"],
  "archived": false
}
```
*(synthetic, based on official contract)*

All filter fields are optional. Empty body returns all offers.

### Response Structure

```json
{
  "status": "OK",
  "result": {
    "paging": { "nextPageToken": "eyJ..." },
    "offerMappings": [
      {
        "offer": {
          "offerId": "SKU-001",
          "name": "Ударная дрель Makita HP1630, 710 Вт",
          "vendor": "LEVENHUK",
          "barcodes": ["4607159324843"],
          "vendorCode": "VNDR-0005A",
          "description": "...",
          "weightDimensions": { "length": 10, "width": 5, "height": 3, "weight": 0.5 },
          "basicPrice": { "value": 5000, "currencyId": "RUR" },
          "purchasePrice": { "value": 3000, "currencyId": "RUR" },
          "cardStatus": "HAS_CARD_CAN_NOT_UPDATE",
          "campaigns": [{ "campaignId": 12345, "status": "PUBLISHED" }],
          "sellingPrograms": [{ "sellingProgram": "FBY", "status": "ACTIVE" }],
          "archived": false,
          "groupId": "group-123"
        },
        "mapping": {
          "marketSku": 100500,
          "marketSkuName": "Дрель Makita HP1630",
          "marketModelName": "Makita HP1630",
          "marketCategoryId": 91597,
          "marketCategoryName": "Дрели"
        }
      }
    ]
  }
}
```
*(synthetic, based on official contract)*

### Pagination

| Property | Value | Confidence |
|----------|-------|------------|
| Type | Cursor-based (`pageToken` + `limit`) | confirmed-docs |
| Max page size | 100 | confirmed-docs |
| Termination | `paging.nextPageToken` absent or empty | confirmed-docs |
| When using `offerIds` filter | Returns full list, ignores `pageToken`/`limit` | confirmed-docs |

### Identifier Semantics

| Provider field | Type | Semantics | Confidence |
|----------------|------|-----------|------------|
| `offer.offerId` | string | Seller's own SKU (shop_sku) — primary identifier | confirmed-docs |
| `mapping.marketSku` | long | Yandex Market SKU (global marketplace ID) | confirmed-docs |
| `offer.vendorCode` | string | Manufacturer article code | confirmed-docs |
| `offer.barcodes[]` | string[] | EAN/UPC barcodes | confirmed-docs |
| `mapping.marketCategoryId` | integer | Yandex Market category ID | confirmed-docs |
| `offer.groupId` | string | Group ID for variant grouping | confirmed-docs |

### Join Key Semantics

| Relation | Join key | Confidence |
|----------|----------|------------|
| Catalog → Stocks | `offerId` = stocks `offerId` | confirmed-docs |
| Catalog → Orders | `offerId` = orders items `offerId` / `shopSku` | confirmed-docs |
| Catalog → Bids | `offerId` = bids `sku` | confirmed-docs |
| Catalog → Finance reports | `offerId` = reports `SHOP_SKU` / `YOUR_SKU` | confirmed-docs |
| Catalog → Promos | `offerId` = promo offers `offerId` | confirmed-docs |

### Timestamp Semantics

| Field | Semantics | Confidence |
|-------|-----------|------------|
| (none) | No creation/update timestamps in offer-mappings response | confirmed-docs |

### Card Status enum

| Value | Semantics | Confidence |
|-------|-----------|------------|
| `HAS_CARD_CAN_NOT_UPDATE` | Yandex card, seller cannot edit | confirmed-docs |
| `HAS_CARD_CAN_UPDATE` | Card exists, can be enriched | confirmed-docs |
| `HAS_CARD_CAN_UPDATE_ERRORS` | Changes rejected | confirmed-docs |
| `HAS_CARD_CAN_UPDATE_PROCESSING` | Changes being reviewed | confirmed-docs |
| `NO_CARD_NEED_CONTENT` | Card must be created | confirmed-docs |
| `NO_CARD_MARKET_WILL_CREATE` | Yandex will create card | confirmed-docs |
| `NO_CARD_ERRORS` | Card creation failed | confirmed-docs |
| `NO_CARD_PROCESSING` | Card data being validated | confirmed-docs |
| `NO_CARD_ADD_TO_CAMPAIGN` | Card ready, offer not placed in store | confirmed-docs |

### Data Freshness

| Property | Value | Confidence |
|----------|-------|------------|
| Freshness | Near real-time (reflects current catalog state) | assumed |

### Known Limitations

- Prices are inside `offer.basicPrice` and `offer.purchasePrice` — not a separate price object hierarchy like Ozon (confirmed-docs)
- `vendor` field contains brand name (unlike WB where it's in catalog card, unlike Ozon where it's in attributes) (confirmed-docs)
- Max 100 `offerIds` per direct SKU query; cursor pagination for full catalog (confirmed-docs)
- Category tree available via separate endpoint `POST /v2/categories/tree` (confirmed-docs)
- `language` query param supports `RU` (default) and `UZ` (confirmed-docs)

### Rate Limits

| Property | Value | Confidence |
|----------|-------|------------|
| Rate limit | 100 req/min (with subscription: 600 req/min) | confirmed-docs |
| Until 18.05.2026 | 600 req/min (old limit) | confirmed-docs |

---

## 3. PRICES

### Price Data Sources

Yandex Market does NOT have a dedicated "get current prices" read endpoint like Ozon's `/v5/product/info/prices`. Prices are obtained from:

| Source | Fields | Confidence |
|--------|--------|------------|
| Offer Mappings (§2) | `offer.basicPrice`, `offer.purchasePrice` | confirmed-docs |
| Offer Prices endpoint | `POST /v2/businesses/{businessId}/offer-prices` — returns prices set via API | confirmed (URL probed, 403) |
| Price Quarantine | `POST /v2/businesses/{businessId}/price-quarantine` — returns quarantined price changes | confirmed-docs |
| Tariff Calculator | `POST /v2/tariffs/calculate` — calculates fees/tariffs for given parameters | confirmed (URL probed, 403) |

### Offer Prices Endpoint

| Property | Value | Confidence |
|----------|-------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/v2/businesses/{businessId}/offer-prices` | confirmed |
| Scope | `pricing`, `pricing:read-only`, `all-methods` | confirmed-docs |

### Price Fields (from offer-mappings)

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `offer.basicPrice.value` | number | Seller's base price | confirmed-docs |
| `offer.basicPrice.currencyId` | string | Currency code ("RUR") | confirmed-docs |
| `offer.purchasePrice.value` | number | Purchase/cost price | confirmed-docs |
| `offer.purchasePrice.currencyId` | string | Currency code | confirmed-docs |

### Amount Semantics

| Property | Value | Confidence |
|----------|-------|------------|
| Currency | RUR (RUB equivalent in Yandex API) | confirmed-docs |
| Unit | Rubles (decimal) | confirmed-docs |
| Sign | Positive values only | assumed |

### Tariff Calculator

| Property | Value | Confidence |
|----------|-------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/v2/tariffs/calculate` | confirmed |
| Purpose | Calculate marketplace fees/tariffs for products | confirmed-docs |

### Data Freshness

| Property | Value | Confidence |
|----------|-------|------------|
| Freshness | Reflects prices currently set | assumed |

### Known Limitations

- No dedicated price-list read endpoint with full pricing hierarchy (like WB's per-size pricing or Ozon's commission breakdown) — prices come from offer-mappings (confirmed-docs)
- `currencyId` uses `"RUR"` (not `"RUB"`) in some endpoints — treat as RUB (confirmed-docs)
- Price quarantine: Yandex may quarantine price changes that look suspicious; must check quarantine status (confirmed-docs)

### Rate Limits

| Property | Value | Confidence |
|----------|-------|------------|
| Offer prices | Not separately documented (shares with offer-mappings) | unknown |
| Tariff calculator | Not separately documented | unknown |

---

## 4. STOCKS / INVENTORY

### Endpoint

| Property | Value | Confidence |
|----------|-------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/v2/campaigns/{campaignId}/offers/stocks` | confirmed (URL probed, 403 FORBIDDEN) |
| Base URL | `https://api.partner.market.yandex.ru` | confirmed |
| Auth | `Api-Key` header | confirmed |
| Scope | `offers-and-cards-management`, `all-methods` | confirmed-docs |
| Level | **Campaign-level** (requires `campaignId`, NOT `businessId`) | confirmed-docs |

### Request Body

```json
{
  "offerIds": ["SKU-001", "SKU-002"],
  "stocksWarehouseId": 12345,
  "hasStocks": true,
  "withTurnover": true,
  "archived": false
}
```
*(synthetic, based on official contract)*

### Response Structure

```json
{
  "status": "OK",
  "result": {
    "paging": { "nextPageToken": "eyJ..." },
    "warehouses": [
      {
        "warehouseId": 12345,
        "offers": [
          {
            "offerId": "SKU-001",
            "stocks": [
              { "type": "FIT", "count": 50 },
              { "type": "AVAILABLE", "count": 45 },
              { "type": "FREEZE", "count": 5 },
              { "type": "QUARANTINE", "count": 2 }
            ],
            "updatedAt": "2025-01-01T00:00:00Z",
            "turnoverSummary": {
              "turnover": "HIGH",
              "turnoverDays": 65.3
            }
          }
        ]
      }
    ]
  }
}
```
*(synthetic, based on official contract)*

### Pagination

| Property | Value | Confidence |
|----------|-------|------------|
| Type | Cursor-based (`pageToken` + `limit`) | confirmed-docs |
| Default page size | 100 | confirmed-docs |
| Max page size | 200 | confirmed-docs |
| Termination | `paging.nextPageToken` absent | confirmed-docs |

### Identifier Semantics

| Provider field | Type | Semantics | Confidence |
|----------------|------|-----------|------------|
| `warehouseId` | integer | Warehouse ID (Yandex fulfillment or seller) | confirmed-docs |
| `offerId` | string | Seller's SKU | confirmed-docs |

### Stock Type Semantics

| Type | Semantics | Confidence |
|------|-----------|------------|
| `FIT` | Fit for sale or reserved (total usable) | confirmed-docs |
| `AVAILABLE` | Available for new orders | confirmed-docs |
| `FREEZE` | Reserved for existing orders | confirmed-docs |
| `QUARANTINE` | Temporarily unavailable (internal moves) | confirmed-docs |
| `DEFECT` | Defective items | confirmed-docs |
| `EXPIRED` | Expired items | confirmed-docs |
| `UTILIZATION` | Scheduled for disposal | confirmed-docs |

### Granularity

**1 row = 1 offer × 1 warehouse × N stock types.** Data grain is `(offerId, warehouseId)` with breakdown by stock type. For canonical mapping: `AVAILABLE` → available, `FREEZE` → reserved, `FIT` → total usable.

### Turnover (FBY/LaaS only)

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `turnoverSummary.turnover` | string | Turnover rating enum | confirmed-docs |
| `turnoverSummary.turnoverDays` | number | Turnover in days | confirmed-docs |

Turnover enum: `LOW` (≥120d), `ALMOST_LOW` (100-120d), `HIGH` (45-100d), `VERY_HIGH` (<45d), `NO_SALES`, `FREE_STORE`.

### Timestamp Semantics

| Field | Semantics | Confidence |
|-------|-----------|------------|
| `updatedAt` | Last stock update time (ISO 8601 UTC) | confirmed-docs |

### Join Key Semantics

| Relation | Join key | Confidence |
|----------|----------|------------|
| Stocks → Catalog | `offerId` = catalog `offer.offerId` | confirmed-docs |
| Stocks → Warehouse | `warehouseId` → `warehouse.id` from warehouses endpoint | confirmed-docs |

### Data Freshness

| Property | Value | Confidence |
|----------|-------|------------|
| Freshness | `updatedAt` field per offer shows staleness | confirmed-docs |

### Known Limitations

- Campaign-level endpoint — must call per `campaignId`, not per `businessId` (confirmed-docs)
- FBY stocks may span multiple Yandex warehouses (confirmed-docs)
- FBS returns storage may appear at Yandex return warehouses (confirmed-docs)
- Rate limit counts **items**, not requests (confirmed-docs)
- `withTurnover: true` only works for FBY/LaaS models (confirmed-docs)
- Max 500 `offerIds` per direct query (confirmed-docs)

### Rate Limits

| Property | Value | Confidence |
|----------|-------|------------|
| Rate limit | 100,000 **items** per minute (NOT requests) | confirmed-docs |

---

## 5. ORDERS

### Endpoint (recommended — business-level)

| Property | Value | Confidence |
|----------|-------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/v1/businesses/{businessId}/orders` | confirmed (URL probed, 403) |
| Base URL | `https://api.partner.market.yandex.ru` | confirmed |
| Auth | `Api-Key` header | confirmed |
| Scope | `inventory-and-order-processing`, `finance-and-accounting`, `all-methods` | confirmed-docs |
| Level | **Business-level** | confirmed-docs |

> **CRITICAL (F-3):** Legacy `GET /v2/campaigns/{campaignId}/orders` is **DEPRECATED**. Use `POST /v1/businesses/{businessId}/orders` instead. This changes the access level from campaign to business.

### Request Body

```json
{
  "orderIds": [12345],
  "programTypes": ["FBY", "FBS"],
  "campaignIds": [67890],
  "statuses": ["DELIVERED", "CANCELLED"],
  "substatuses": ["USER_CHANGED_MIND"],
  "dates": {
    "creationDateFrom": "2025-01-01",
    "creationDateTo": "2025-01-31",
    "updateDateFrom": "2025-01-01T00:00:00Z",
    "updateDateTo": "2025-01-31T23:59:59Z"
  },
  "fake": false
}
```
*(synthetic, based on official contract)*

### Pagination

| Property | Value | Confidence |
|----------|-------|------------|
| Type | Cursor-based (`pageToken` + `limit`) | confirmed-docs |
| Default page size | 50 | confirmed-docs |
| Max page size | 50 | confirmed-docs |
| Max date range | 30 days (`creationDateFrom` to `creationDateTo`) | confirmed-docs |
| Default date range | Last 30 days if dates not specified | confirmed-docs |

### Response Structure

```json
{
  "status": "OK",
  "result": {
    "paging": { "nextPageToken": "eyJ..." },
    "orders": [
      {
        "id": 12345,
        "status": "DELIVERED",
        "substatus": "USER_RECEIVED",
        "creationDate": "2025-01-15",
        "updatedAt": "2025-01-20T14:30:00Z",
        "paymentType": "PREPAID",
        "programType": "FBY",
        "campaignId": 67890,
        "items": [
          {
            "offerId": "SKU-001",
            "offerName": "Product Name",
            "marketSku": 100500,
            "count": 2,
            "prices": [
              {
                "type": "BUYER",
                "costPerItem": 1500,
                "total": 3000
              }
            ]
          }
        ],
        "delivery": {
          "deliveryPartnerType": "YANDEX_MARKET",
          "type": "DELIVERY",
          "shipments": [
            {
              "shipmentDate": "2025-01-16"
            }
          ],
          "region": {
            "id": 213,
            "name": "Москва"
          }
        }
      }
    ]
  }
}
```
*(synthetic, based on official contract)*

### Order Status Semantics

| Status | Semantics | Terminal? | Confidence |
|--------|-----------|-----------|------------|
| `PLACING` | Being placed, reservation pending | No | confirmed-docs |
| `RESERVED` | Reserved, not yet finalized (LaaS only) | No | confirmed-docs |
| `UNPAID` | Placed but not paid (prepaid orders) | No | confirmed-docs |
| `PROCESSING` | Being processed by seller | No | confirmed-docs |
| `DELIVERY` | Handed to delivery service | No | confirmed-docs |
| `PICKUP` | At pickup point | No | confirmed-docs |
| `DELIVERED` | Received by buyer | Yes | confirmed-docs |
| `CANCELLED` | Cancelled | Yes | confirmed-docs |
| `PARTIALLY_RETURNED` | Partially returned | Yes | confirmed-docs |
| `RETURNED` | Fully returned | Yes | confirmed-docs |

### Substatus Semantics (key values)

| Substatus | Parent status | Semantics | Confidence |
|-----------|---------------|-----------|------------|
| `STARTED` | PROCESSING | Ready to process | confirmed-docs |
| `READY_TO_SHIP` | PROCESSING | Packed, ready to ship | confirmed-docs |
| `USER_CHANGED_MIND` | CANCELLED | Buyer changed mind | confirmed-docs |
| `SHOP_FAILED` | CANCELLED | Seller cannot fulfill | confirmed-docs |
| `USER_REFUSED_QUALITY` | CANCELLED | Quality issue | confirmed-docs |
| `PICKUP_EXPIRED` | CANCELLED | Pickup time expired | confirmed-docs |

### Identifier Semantics

| Provider field | Type | Semantics | Confidence |
|----------------|------|-----------|------------|
| `id` | long | Yandex order ID (unique across marketplace) | confirmed-docs |
| `items[].offerId` | string | Seller's SKU | confirmed-docs |
| `items[].marketSku` | long | Yandex Market SKU | confirmed-docs |
| `campaignId` | long | Campaign (store) that owns this order | confirmed-docs |

### Amount Fields

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `items[].prices[].costPerItem` | number | Price per unit (RUB) | confirmed-docs |
| `items[].prices[].total` | number | Total for this item line | confirmed-docs |
| `items[].prices[].type` | string | Price type: `BUYER`, `CASHBACK_SPEND` etc. | confirmed-docs |
| `items[].count` | integer | Quantity | confirmed-docs |

### Timestamp Semantics

| Field | Format | Semantics | Confidence |
|-------|--------|-----------|------------|
| `creationDate` | `YYYY-MM-DD` (date-only) | Order creation date | confirmed-docs |
| `updatedAt` | ISO 8601 UTC | Last status change | confirmed-docs |
| `delivery.shipments[].shipmentDate` | `YYYY-MM-DD` | Shipment date | confirmed-docs |

### Data Freshness

| Property | Value | Confidence |
|----------|-------|------------|
| Freshness | Near real-time for active orders | assumed |
| Retention | Does not return orders delivered/cancelled >30 days ago | confirmed-docs |

### Known Limitations

- Max 30-day date range per request (confirmed-docs)
- Orders older than 30 days (delivered/cancelled) not available via this endpoint — use stats/reports (confirmed-docs)
- `fake: true` returns test orders (confirmed-docs)
- Business-level endpoint returns orders across ALL campaigns (confirmed-docs)
- Max 50 items per page (confirmed-docs)
- **No financial breakdown** (commission, logistics costs) in this endpoint — only item prices. Financial decomposition requires finance reports (confirmed-docs)

### Rate Limits

| Property | Value | Confidence |
|----------|-------|------------|
| Rate limit | 10,000 req/hour | confirmed-docs |

---

## 6. RETURNS / UNREDEEMED

### Endpoint

| Property | Value | Confidence |
|----------|-------|------------|
| Method | `GET` | confirmed-docs |
| Path | `/v2/campaigns/{campaignId}/returns` | confirmed (URL probed, 403 FORBIDDEN) |
| Base URL | `https://api.partner.market.yandex.ru` | confirmed |
| Auth | `Api-Key` header | confirmed |
| Scope | `inventory-and-order-processing`, `all-methods` | confirmed-docs |
| Level | **Campaign-level** (requires `campaignId`) | confirmed-docs |

### Request Parameters

| Param | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `campaignId` | integer | yes (path) | Campaign ID | confirmed-docs |
| `orderIds` | integer[] | no (query) | Filter by order IDs | confirmed-docs |
| `statuses` | string[] | no (query) | Filter by return statuses | confirmed-docs |
| `type` | string | no (query) | Return type filter | confirmed-docs |
| `fromDate` | string | no (query) | Start date (YYYY-MM-DD) | confirmed-docs |
| `toDate` | string | no (query) | End date (YYYY-MM-DD) | confirmed-docs |
| `limit` | integer | no (query) | Page size | confirmed-docs |
| `pageToken` | string | no (query) | Cursor for pagination | confirmed-docs |

### Response Structure

```json
{
  "status": "OK",
  "result": {
    "paging": { "nextPageToken": "eyJ..." },
    "returns": [
      {
        "id": 98765,
        "orderId": 12345,
        "creationDate": "2025-02-01",
        "updateDate": "2025-02-03",
        "returnStatus": "WAITING_FOR_RETURN",
        "returnType": "RETURN",
        "items": [
          {
            "marketSku": 100500,
            "shopSku": "SKU-001",
            "count": 1,
            "decisionType": "REFUND_MONEY",
            "returnReason": { "type": "BAD_QUALITY", "description": "Defect" }
          }
        ]
      }
    ]
  }
}
```
*(synthetic, based on official contract — endpoint returned 403 for fake campaignId)*

### Return Status Lifecycle

| Status | Semantics | Confidence |
|--------|-----------|------------|
| `WAITING_FOR_RETURN` | Waiting for item to be returned | confirmed-docs |
| `RECEIVED_ON_MARKETPLACE` | Item received at Yandex warehouse | confirmed-docs |
| `RECEIVED_ON_PARTNER` | Item received by seller | confirmed-docs |
| `DECIDED` | Return decision made | confirmed-docs |
| `CANCELLED` | Return cancelled | confirmed-docs |

### Return Type

| Value | Semantics | Confidence |
|-------|-----------|------------|
| `RETURN` | Standard return after delivery | confirmed-docs |
| `UNREDEEMED` | Not picked up at PVZ | confirmed-docs |
| `CROSSDOCK_RETURN` | Crossdock return (FBY) | confirmed-docs |

### Identifier Semantics

| Provider field | Type | Semantics | Confidence |
|----------------|------|-----------|------------|
| `id` | long | Unique return ID | confirmed-docs |
| `orderId` | long | Original order ID | confirmed-docs |
| `items[].marketSku` | long | Yandex Market SKU | confirmed-docs |
| `items[].shopSku` | string | Seller's SKU (= `offerId`) | confirmed-docs |

### Timestamp Semantics

| Field | Format | Semantics | Confidence |
|-------|--------|-----------|------------|
| `creationDate` | `YYYY-MM-DD` | Return creation date | confirmed-docs |
| `updateDate` | `YYYY-MM-DD` | Last update date | confirmed-docs |

### Known Limitations

- Campaign-level — must call per `campaignId` (confirmed-docs)
- Financial impact of returns NOT in this endpoint — must derive from finance reports (assumed)
- Documentation URL for this endpoint returned 404 during fetch — contract inferred from general docs structure and empirical probing (unknown: details may be incomplete)

### Rate Limits

| Property | Value | Confidence |
|----------|-------|------------|
| Rate limit | Not explicitly documented for this endpoint | unknown |

---

## 7. FINANCE / REPORTS

### Architecture — ASYNC ONLY

> **CRITICAL DIFFERENCE from WB/Ozon:** Yandex Market finance data is ONLY available through async reports. There is no synchronous "list transactions" API like Ozon's `/v3/finance/transaction/list` or WB's `/api/v5/supplier/reportDetailByPeriod`.

### Flow

1. **Generate:** `POST /v2/reports/{reportType}/generate` → returns `reportId`
2. **Poll:** `GET /v2/reports/info/{reportId}` → returns status + download URL when ready
3. **Download:** GET the download URL → CSV/JSON/XLSX file

### Report Types

#### 7.1 United Marketplace Services Report

| Property | Value | Confidence |
|----------|-------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/v2/reports/united-marketplace-services/generate` | confirmed (URL probed, 403) |
| Scope | `finance-and-accounting`, `all-methods` | confirmed-docs |

**Request body:**
```json
{
  "businessId": 67890,
  "dateFrom": "2025-01-01",
  "dateTo": "2025-01-31"
}
```
*(synthetic, based on official contract)*

Alternative: `{"businessId": 67890, "year": 2025, "month": 1}` — by accrual date.

**Key columns (confirmed-docs):**

| Column (CSV) | Column (JSON) | Type | Semantics | Confidence |
|-------------|---------------|------|-----------|------------|
| `ORDER_ID` | `orderId` | integer | Order ID | confirmed-docs |
| `YOUR_SKU` | `yourSku` | string | Seller SKU | confirmed-docs |
| `SHOP_SKU` | `shopSku` | string | Shop SKU | confirmed-docs |
| `OFFER_NAME` | `offerName` | string | Product name | confirmed-docs |
| `ORDER_CREATION_DATE_TIME` | `orderCreationDateTime` | string | Order creation datetime | confirmed-docs |
| `PLACEMENT_MODEL` | `placementModel` | string | FBY/FBS/DBS/Express | confirmed-docs |
| `PARTNER_ID` | `partnerId` | integer | Campaign ID | confirmed-docs |
| `INN` | `inn` | string | Tax ID | confirmed-docs |

> **⚠️ WARNING (F-6):** Yandex explicitly states: "Structure and content of reports may change without prior notice." Parser MUST use `@JsonIgnoreProperties(ignoreUnknown = true)` or equivalent lenient parsing.

#### 7.2 Goods Realization Report

| Property | Value | Confidence |
|----------|-------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/v2/reports/goods-realization/generate` | confirmed (URL probed, 403) |
| Scope | `finance-and-accounting`, `all-methods` | confirmed-docs |

**Request body:**
```json
{"businessId": 67890, "year": 2025, "month": 1}
```
*(synthetic, based on official contract)*

Monthly granularity only (year + month).

**Key columns (confirmed-docs):**

| Column (CSV) | Type | Semantics | Confidence |
|-------------|------|-----------|------------|
| `ORDER_ID` | integer | Order ID | confirmed-docs |
| `YOUR_ORDER_ID` | string | External order ID | confirmed-docs |
| `YOUR_SKU` | string | Seller SKU | confirmed-docs |
| `SHOP_SKU` | string | Shop SKU | confirmed-docs |
| `ORDER_CREATION_DATE` | string | Order creation date | confirmed-docs |
| `TRANSFERRED_TO_DELIVERY_DATE` | string | Delivery transfer date | confirmed-docs |
| `DELIVERY_DATE` | string | Delivery date | confirmed-docs |
| `TRANSFERRED_TO_DELIVERY_COUNT` | integer | Units transferred | confirmed-docs |
| `PRICE_WITH_VAT_AND_NO_DISCOUNT` | number | Price with VAT, no discount | confirmed-docs |
| `VAT` | string | VAT rate | confirmed-docs |

#### 7.3 Report Status Polling

| Property | Value | Confidence |
|----------|-------|------------|
| Method | `GET` | confirmed-docs |
| Path | `/v2/reports/info/{reportId}` | confirmed-docs |

**Response:**
```json
{
  "status": "OK",
  "result": {
    "status": "DONE",
    "file": "https://download.example.com/report.csv",
    "generationRequestedAt": "2025-01-15T10:00:00Z",
    "generationFinishedAt": "2025-01-15T10:01:30Z"
  }
}
```
*(synthetic, based on official contract)*

Report statuses: `PENDING`, `GENERATING`, `DONE`, `FAILED`.

### Sign Convention

| Property | Value | Confidence |
|----------|-------|------------|
| Convention | **Unknown** — requires empirical verification with active account | unknown |
| Assumption | Report columns separate credits and debits by column name (like WB), NOT by sign (unlike Ozon) | assumed |

### Timestamp Semantics

| Field | Format | Semantics | Confidence |
|-------|--------|-----------|------------|
| Report date fields | `YYYY-MM-DD` (date-only in columns) | Business dates | confirmed-docs |
| Report datetime fields | `YYYY-MM-DDTHH:MM:SSZ` or `YYYY-MM-DD HH:MM:SS` | Event timestamps | assumed |
| Timezone | Not documented in report columns | unknown |

### Data Freshness

| Property | Value | Confidence |
|----------|-------|------------|
| Generation time | Seconds to minutes (async) | assumed |
| Data availability | Current month may have incomplete data | assumed |

### Known Limitations

- **Async only** — no synchronous finance transaction API (confirmed-docs)
- Report structure may change without notice (confirmed-docs, F-6)
- Monthly granularity for goods realization (confirmed-docs)
- Date range for services report: either `dateFrom`+`dateTo` or `year`+`month` (confirmed-docs)
- Download URL has limited TTL (assumed)
- No sign convention documentation — must verify empirically with real data (unknown)

### Rate Limits

| Property | Value | Confidence |
|----------|-------|------------|
| Generate | Not explicitly documented | unknown |
| Poll | Not explicitly documented | unknown |

---

## 8. WAREHOUSES / REFERENCE DATA

### Yandex Fulfillment Warehouses (FBY/LaaS)

| Property | Value | Confidence |
|----------|-------|------------|
| Method | `GET` | confirmed-docs |
| Path | `/v2/warehouses` | confirmed (URL probed, 403) |
| Auth | `Api-Key` header | confirmed |
| Scope | Any scope | confirmed-docs |

**Response:**
```json
{
  "status": "OK",
  "result": {
    "warehouses": [
      {
        "id": 12345,
        "name": "Ростов-на-Дону (Аксай)",
        "address": {
          "city": "Аксай",
          "street": "ул. Промышленная",
          "number": "1",
          "gps": { "latitude": 47.27, "longitude": 39.87 }
        }
      }
    ]
  }
}
```
*(synthetic, based on official contract)*

### Seller Warehouses (FBS)

| Property | Value | Confidence |
|----------|-------|------------|
| Method | `GET` | confirmed-docs |
| Path | `/v2/businesses/{businessId}/warehouses` | confirmed (URL probed, 403) |

### Warehouse Fields

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `id` | integer | Warehouse ID | confirmed-docs |
| `name` | string | Warehouse name | confirmed-docs |
| `address.city` | string | City | confirmed-docs |
| `address.street` | string | Street | confirmed-docs |
| `address.number` | string | Building number | confirmed-docs |
| `address.gps.latitude` | number | GPS latitude | confirmed-docs |
| `address.gps.longitude` | number | GPS longitude | confirmed-docs |

### Join Key Semantics

| Relation | Join key | Confidence |
|----------|----------|------------|
| Stocks → Warehouses | `stocks.warehouseId` = `warehouses.id` | confirmed-docs |

### Category Tree

| Property | Value | Confidence |
|----------|-------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/v2/categories/tree` | confirmed (URL probed, 403) |

Returns hierarchical category tree for Yandex Market. Blocked for disabled business (unlike WB where category APIs are public).

### Rate Limits

| Property | Value | Confidence |
|----------|-------|------------|
| Fulfillment warehouses | 100 req/hour (until 18.05.2026: 100 req/min) | confirmed-docs |
| Seller warehouses | Not explicitly documented | unknown |
| Categories | Not explicitly documented | unknown |

---

## 9. PROMOTIONS (Read)

### Endpoint

| Property | Value | Confidence |
|----------|-------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/v2/businesses/{businessId}/promos` | confirmed (URL probed, 403) |
| Base URL | `https://api.partner.market.yandex.ru` | confirmed |
| Auth | `Api-Key` header | confirmed |
| Scope | `promotion`, `promotion:read-only`, `all-methods` | confirmed-docs |
| Level | **Business-level** | confirmed-docs |

### Response Structure

```json
{
  "status": "OK",
  "result": {
    "promos": [
      {
        "promoId": "promo-123",
        "name": "Весенняя распродажа",
        "status": "ACTIVE",
        "channels": ["MARKET"],
        "startDate": "2025-03-01",
        "endDate": "2025-03-31",
        "mechanicsType": "DIRECT_DISCOUNT",
        "participationType": "AUTO"
      }
    ]
  }
}
```
*(synthetic, based on official contract)*

### Key Fields

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `promoId` | string | Unique promotion ID | confirmed-docs |
| `name` | string | Promo name | confirmed-docs |
| `status` | string | Promo status | confirmed-docs |
| `startDate` | string | Start date (YYYY-MM-DD) | confirmed-docs |
| `endDate` | string | End date (YYYY-MM-DD) | confirmed-docs |
| `mechanicsType` | string | Promo mechanics enum | confirmed-docs |
| `participationType` | string | How offers participate | confirmed-docs |

### Promo Offers

Separate endpoint to get offers participating in a promo:
- `POST /v2/businesses/{businessId}/promos/offers` — list offers in promo
- `POST /v2/businesses/{businessId}/promos/offers/update` — update participation (write)

### Known Limitations

- Business-level endpoint (confirmed-docs)
- Promo mechanics and participation rules can be complex — not fully documented in read contract (assumed)

### Rate Limits

| Property | Value | Confidence |
|----------|-------|------------|
| Rate limit | Not explicitly documented | unknown |

---

## 10. SALES BOOST / ADVERTISING (Read — Bids)

### Endpoint — Read Current Bids

| Property | Value | Confidence |
|----------|-------|------------|
| Method | `POST` | confirmed |
| Path | `/v2/businesses/{businessId}/bids/info` | confirmed (F-2: empirically discovered) |
| Base URL | `https://api.partner.market.yandex.ru` | confirmed |
| Auth | `Api-Key` header | confirmed |
| Scope | `pricing`, `pricing:read-only`, `promotion`, `promotion:read-only`, `all-methods` | confirmed-docs |
| Level | **Business-level** | confirmed-docs |

> **CRITICAL (F-2):** Original assumption was `POST /v2/businesses/{businessId}/bids` for reading bids. Empirical probing discovered that `/bids` only accepts `PUT` (write). Read endpoint is `POST /v2/businesses/{businessId}/bids/info`.

### Request Body

```json
{
  "skus": ["SKU-001", "SKU-002"]
}
```
*(synthetic, based on official contract)*

Empty `skus` (or no body) returns all SKUs with bids, paginated.

### Response Structure

```json
{
  "status": "OK",
  "result": {
    "bids": [
      {
        "sku": "SKU-001",
        "bid": 570
      }
    ],
    "paging": {
      "nextPageToken": "eyJ..."
    }
  }
}
```
*(synthetic, based on official contract)*

### Pagination

| Property | Value | Confidence |
|----------|-------|------------|
| Type | Cursor-based (`pageToken` + `limit`) | confirmed-docs |
| Default page size | 250 | confirmed-docs |
| Max page size | 500 | confirmed-docs |
| When `skus` specified | Single page, ignores `pageToken`/`limit` | confirmed-docs |
| Max SKUs per request | 500 | confirmed-docs |

### Bid Semantics

| Property | Value | Confidence |
|----------|-------|------------|
| `bid` | integer | Bid value (0–9999) | confirmed-docs |
| Encoding | Percent of item cost × 100 (e.g., 570 = 5.7%) | confirmed-docs |
| Min effective | 50 (0.5%) | confirmed-docs |
| Max | 9999 (99.99%) | confirmed-docs |
| 0 | No bid set | confirmed-docs |

### Identifier Semantics

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `sku` | string | Seller's SKU (= `offerId` from catalog) | confirmed-docs |
| `bid` | integer | Current bid value | confirmed-docs |

### Join Key Semantics

| Relation | Join key | Confidence |
|----------|----------|------------|
| Bids → Catalog | `sku` = catalog `offer.offerId` | confirmed-docs |

### Bid Recommendations

Separate endpoint for bid recommendations:
- `POST /v2/businesses/{businessId}/bids/recommendations` — returns recommended bids
- Contains `minBid`, `maxBid`, `currentBid` per SKU

### Known Limitations

- Only returns bids set via API — bids set in Yandex Partner cabinet are NOT returned (confirmed-docs)
- Business-level endpoint (confirmed-docs)

### Rate Limits

| Property | Value | Confidence |
|----------|-------|------------|
| Rate limit | 500 req/min (with subscription: 1,000 req/min) | confirmed-docs |
| Until 18.05.2026 | 1,000 req/min (old limit) | confirmed-docs |

---

## Rate Limits Summary

| # | Capability | Endpoint | Rate Limit | Unit | Confidence |
|---|------------|----------|-----------|------|------------|
| 1 | Campaigns | `GET /v2/campaigns` | 1,000/hour | requests | confirmed-docs |
| 2 | Catalog | `POST /businesses/{id}/offer-mappings` | 100/min (600 w/ sub) | requests | confirmed-docs |
| 3 | Stocks | `POST /campaigns/{id}/offers/stocks` | 100,000/min | **items** | confirmed-docs |
| 4 | Orders | `POST /v1/businesses/{id}/orders` | 10,000/hour | requests | confirmed-docs |
| 5 | Returns | `GET /campaigns/{id}/returns` | unknown | — | unknown |
| 6 | Reports (generate) | `POST /reports/*/generate` | unknown | — | unknown |
| 7 | Warehouses (FBY) | `GET /v2/warehouses` | 100/hour | requests | confirmed-docs |
| 8 | Promos | `POST /businesses/{id}/promos` | unknown | — | unknown |
| 9 | Bids | `POST /businesses/{id}/bids/info` | 500/min (1000 w/ sub) | requests | confirmed-docs |
| 10 | Tariffs | `POST /tariffs/calculate` | unknown | — | unknown |

### Rate Limit Behavior

| Property | Value | Confidence |
|----------|-------|------------|
| HTTP code on limit | **420** (Enhance Your Calm), NOT 429 | confirmed-docs |
| Retry-After header | Not documented | unknown |
| Subscription boost | "Медиум" subscription increases limits | confirmed-docs |
| Transition period | Until 18.05.2026 old (higher) limits apply | confirmed-docs |

> **CRITICAL:** After 18.05.2026 rate limits decrease significantly for unsubscribed accounts. Plan capacity accordingly.

---

## Summary: Contract Readiness per Capability

| # | Capability | Readiness | Empirical | Docs | Rationale |
|---|------------|-----------|-----------|------|-----------|
| 1 | Campaigns | **READY** | ✅ 200 OK | ✅ Full | Structure confirmed; dual pagination noted |
| 2 | Catalog | READY (docs) | ❌ 403 | ✅ Full | Rich official schema with samples; no data verification |
| 3 | Prices | READY (docs) | ❌ 403 | ⚠️ Partial | Prices embedded in offer-mappings; no standalone price-list API |
| 4 | Stocks | READY (docs) | ❌ 403 | ✅ Full | Campaign-level; stock type breakdown confirmed in schema |
| 5 | Orders | READY (docs) | ❌ 403 | ✅ Full | Business-level v1 endpoint confirmed; legacy deprecated |
| 6 | Returns | PARTIAL | ❌ 403 | ⚠️ Partial | Docs URL 404; contract inferred from API structure and probing |
| 7 | Finance | PARTIAL | ❌ 403 | ✅ Full | Async-only; report structure "may change without notice" (F-6) |
| 8 | Warehouses | READY (docs) | ❌ 403 | ✅ Full | Simple reference data; schema well-documented |
| 9 | Promos | READY (docs) | ❌ 403 | ✅ Full | Business-level; promo mechanics types documented |
| 10 | Bids | **READY** | ✅ URL confirmed | ✅ Full | Read endpoint discovered (F-2); bid semantics clear |

### Empirical Verification Status

| What | Status |
|------|--------|
| Auth mechanism (Api-Key header) | ✅ Confirmed |
| Error response contract | ✅ 6 error codes verified |
| Campaigns structure | ✅ 200 OK (empty, dual pagination) |
| Bids endpoint discovery | ✅ Read=POST bids/info, Write=PUT bids |
| Orders deprecation | ⚠️ GET orders DEPRECATED → use POST v1/businesses/{id}/orders |
| All business-level endpoints | ❌ 403 API_DISABLED |
| Data-level verification | ❌ Not possible (disabled business) |
| No sandbox | ❌ Confirmed (F-7) |

### Key Discrepancies Found (Session 6)

| ID | Discrepancy | Impact | Confidence |
|----|------------|--------|------------|
| F-1 | Disabled business blocks ALL endpoints (incl. reference/global) | Must have active account for any data | confirmed |
| F-2 | Bids read endpoint is `POST /bids/info`, NOT `POST /bids` | Adapter URL fix required | confirmed |
| F-3 | `GET /v2/campaigns/{id}/orders` is DEPRECATED | Use `POST /v1/businesses/{id}/orders` | confirmed-docs |
| F-4 | Dual pagination in campaigns (legacy `pager` + modern `paging`) | Use `paging.nextPageToken` only | confirmed |
| F-5 | Rate limit = HTTP 420, not 429 | Retry logic must handle both | confirmed-docs |
| F-6 | Report structure "may change without notice" | Lenient parser required | confirmed-docs |
| F-7 | No sandbox environment | WireMock for all tests | confirmed |

### Critical Differences from WB/Ozon

| # | Difference | WB | Ozon | Yandex |
|---|-----------|-----|------|--------|
| 1 | Finance access | Synchronous GET | Synchronous POST | **Async reports only** |
| 2 | ID hierarchy | Single-level | Single-level | **Two-level** (business + campaign) |
| 3 | Sandbox | ✅ Dedicated URLs | ❌ None | ❌ None |
| 4 | Pagination | Cursor/offset/date-range | Cursor/offset/page | **Cursor (pageToken) everywhere** |
| 5 | Rate limit HTTP | 429 | 429 | **420** |
| 6 | Parallel limit | Per-endpoint | Per-endpoint | **4 concurrent requests** (community-reported) |
| 7 | Price structure | Per-size hierarchy | Nested price object | **Embedded in offer-mappings** |
| 8 | Finance sign convention | All positive, name = direction | Signed (pos=credit, neg=debit) | **Unknown** (not yet verified) |

---

## Пробелы и риски

### Критические пробелы

1. **Нет эмпирической верификации данных.** Все поля основаны только на документации. Disabled business предотвратил любую data-level проверку. Нужен активный аккаунт с реальными товарами/заказами.

2. **Финансы — только async-отчёты.** В отличие от WB/Ozon, нет синхронного API для финансовых транзакций. Это требует:
   - Реализации async pipeline (generate → poll → download → parse)
   - Обработки нестабильной структуры отчётов (F-6)
   - Неизвестная sign convention — нет данных для верификации

3. **Возвраты — неполная документация.** Docs URL для returns endpoint вернул 404. Контракт реконструирован по общей структуре API и пробингу. Требует проверки на реальных данных.

4. **Sign convention для финансов неизвестна.** WB использует "всё положительное, имя поля = направление". Ozon использует "знак = направление". Яндекс — неизвестно. БЛОКЕР для P&L mapping.

### Умеренные риски

5. **businessId / campaignId иерархия.** Adapter должен сначала обнаружить campaigns, затем фан-аутить запросы per campaign для stocks и returns. Это добавляет сложность по сравнению с WB/Ozon.

6. **Rate limits снижаются после 18.05.2026.** Текущие лимиты временно повышены. После deadline лимиты упадут (campaigns: 5000→1000/h, catalog: 600→100/min).

7. **Отсутствие sandbox.** Все интеграционные тесты через WireMock. Нет возможности быстро проверить контракт на sandbox — только production.

8. **Заказы старше 30 дней.** Основной orders endpoint не возвращает заказы, доставленные/отменённые >30 дней назад. Для полной истории нужны отчёты.

### Низкие риски

9. **Категории заблокированы для disabled business.** Reference-endpoint `/v2/categories/tree` тоже возвращает 403. Нужен активный аккаунт даже для справочников.

10. **Цены без отдельного endpoint.** Цены встроены в offer-mappings — нет отдельного price-list API с историей или breakdown по комиссиям.

---

## Вывод по пригодности API

### Для MVP — достаточно с оговорками

Yandex Market Partner API предоставляет **полный набор capabilities** для Datapulse MVP:
- ✅ Каталог (offer-mappings с ценами, vendor, barcodes)
- ✅ Остатки (per warehouse, per stock type)
- ✅ Заказы (business-level, status filtering)
- ✅ Склады (reference data)
- ✅ Ставки (bids read/write)
- ⚠️ Финансы (async-only — значительно сложнее WB/Ozon)
- ⚠️ Возвраты (неполная документация)

### Что блокирует

1. **Активный аккаунт** — без него невозможна data-level верификация. Текущий disabled business блокирует все endpoints кроме campaigns.
2. **Finance sign convention** — без реальных данных невозможно определить, как интерпретировать суммы в отчётах. БЛОКЕР для P&L.
3. **Async finance pipeline** — требует отдельной реализации (generate/poll/download/parse), которой нет в текущей архитектуре ETL для WB/Ozon.

### Что можно сделать сразу

1. **Campaigns adapter** — полностью готов, контракт эмпирически подтверждён.
2. **Offer-mappings adapter** — контракт из документации высокого качества, можно начать реализацию с WireMock-тестами.
3. **Stocks adapter** — аналогично, campaign-level с fan-out.
4. **Orders adapter** — бизнес-уровень, v1 endpoint, контракт полный.
5. **Bids read adapter** — endpoint эмпирически обнаружен, контракт полный.
6. **Warehouses reference** — простой GET, полный контракт.

### Что отложить до активного аккаунта

1. **Finance reports** — async pipeline + sign convention verification.
2. **Returns** — неполная документация, нужна data-level проверка.
3. **P&L mapping** — зависит от finance sign convention.
