# Write Contracts — WB & Ozon Price Update

**Статус:** code-verified + endpoint-accessibility-tested
**Источники:**
- WB: https://dev.wildberries.ru/openapi/work-with-products (Prices and Discounts)
- Ozon: https://docs.ozon.ru/api/seller/ (Prices & Stocks API)
- Кодовая база: `datapulse-marketplaces` module
**Верификация:** endpoint reachability via real tokens 2026-03-30

Этот документ фиксирует write-контракты маркетплейсов для capability **Price Update**,
используемые в подсистеме ценообразования Datapulse (`PriceUpdateService` → `MarketplacePriceAdapter`).

> **Promo write contracts** (activate/deactivate) задокументированы отдельно: [Promo & Advertising Contracts](promo-advertising-contracts.md).

Confidence levels:
- **confirmed** — подтверждено реальным API-ответом или кодом
- **confirmed-docs** — проверено по документации, но не вызывалось с реальными данными
- **assumed** — выведено логически из документации или кода
- **code-verified** — подтверждено наличием в кодовой базе и YAML-конфиге

---

## 1. WILDBERRIES — PRICE UPDATE (async)

### 1.1 Write Endpoint — Set Prices and Discounts

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | code-verified |
| Path | `/api/v2/upload/task` | code-verified |
| Base URL | `https://discounts-prices-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |
| Token category | Prices and Discounts | confirmed-docs |
| Execution model | **Asynchronous** — returns `uploadId`, requires polling | code-verified |

> **CRITICAL HOST CHANGE:** The configured host `discounts-api.wildberries.ru` **no longer resolves (DNS failure)**. The correct host is `discounts-prices-api.wildberries.ru`. See §4 Findings.

### Request Structure

```json
{
  "data": [
    {
      "nmID": 274849,
      "price": 5000,
      "discount": 10
    }
  ]
}
```

| Field | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `data` | array | yes | Array of price items (batch supported) | code-verified |
| `data[].nmID` | long | yes | WB nomenclature ID (product identifier) | code-verified |
| `data[].price` | int | yes | Base price in RUB (whole units, not kopecks) | code-verified |
| `data[].discount` | int | yes | Seller discount percentage (0–100) | code-verified |

**Implementation note:** The codebase sends `discount: 0` always (`WbUpdatePricesRequest.of(nmId, priceInt)`).
Price is truncated to integer via `BigDecimal.intValueExact()` — fractional prices will throw `ArithmeticException`.

### Response Structure

```json
{
  "data": {
    "id": 12345,
    "alreadyExists": false
  },
  "error": false,
  "errorText": ""
}
```

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `data.id` | Long (nullable) | Upload task ID (used for polling) | code-verified |
| `data.alreadyExists` | boolean | Task already exists with same data | code-verified |
| `error` | boolean | Whether the request failed | code-verified |
| `errorText` | string | Error description | code-verified |

### Error Handling

| Scenario | HTTP Status | Response | Confidence |
|----------|-------------|----------|------------|
| Success | 200 | `error: false`, `data.id` populated | code-verified |
| Invalid token scope | 401 | Unauthorized | confirmed |
| Invalid payload | 400 | `error: true`, `errorText` populated | assumed |
| Rate limited | 429 | — | confirmed-docs |

### Related Endpoints

| Method | Path | Purpose | Confidence |
|--------|------|---------|------------|
| `POST /api/v2/upload/task/size` | Set per-size prices | confirmed-docs |
| `POST /api/v2/upload/task/club-discount` | Set WB Club discounts | confirmed-docs |
| `GET /api/v2/buffer/tasks` | Unprocessed upload state | confirmed-docs |
| `GET /api/v2/buffer/goods/task` | Unprocessed upload details | confirmed-docs |
| `GET /api/v2/history/tasks` | Processed upload state | confirmed-docs |
| `GET /api/v2/history/goods/task` | Processed upload details (polling) | code-verified |

---

### 1.2 Poll Endpoint — Upload Details

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | code-verified |
| Path | `/api/v2/history/goods/task` | code-verified |
| Base URL | `https://discounts-prices-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |

### Query Parameters

| Param | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `uploadID` | string | Upload task ID from write response | code-verified |
| `limit` | int | Max items to return | code-verified |

### Response Structure

```json
{
  "data": {
    "uploadID": 12345,
    "historyGoods": [
      {
        "nmID": 274849,
        "vendorCode": "ART-001",
        "sizeID": 274848,
        "status": "success",
        "errorText": ""
      }
    ]
  },
  "error": false,
  "errorText": ""
}
```

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `data.uploadID` | Long | Upload task identifier | code-verified |
| `data.historyGoods` | array | Per-item processing results | code-verified |
| `data.historyGoods[].nmID` | Long | Product ID | code-verified |
| `data.historyGoods[].vendorCode` | String | Seller article | code-verified |
| `data.historyGoods[].sizeID` | Long | Size-level ID | code-verified |
| `data.historyGoods[].status` | String | Processing status | code-verified |
| `data.historyGoods[].errorText` | String | Error description (empty = success) | code-verified |

### Polling Strategy (implemented)

1. Initial wait: **3 seconds** after write
2. First poll: `GET /api/v2/history/goods/task?uploadID={id}&limit=100`
3. If still pending (empty `historyGoods`): wait **4 more seconds**, poll again
4. Terminal states: `errorText` non-empty → FAILED; `errorText` empty → SUCCESS
5. Total max wait: ~7 seconds

---

### 1.3 WB Reconciliation Read — Verify Price Applied

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | confirmed |
| Path | `/api/v2/list/goods/filter` | confirmed |
| Base URL | `https://discounts-prices-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |

This endpoint (documented in `wb-read-contracts.md` §2 PRICES) serves as the **read-after-write verification** endpoint. After a successful price upload, the system can query current prices via this endpoint to confirm the price was actually applied.

### Reconciliation Read Fields

| Field | Semantics | Confidence |
|-------|-----------|------------|
| `listGoods[].nmID` | Product ID to match | confirmed |
| `listGoods[].sizes[].price` | Current base price (RUB) | confirmed |
| `listGoods[].sizes[].discountedPrice` | Price after discount | confirmed |
| `listGoods[].discount` | Applied seller discount % | confirmed |

### Reconciliation Logic (recommended)

```
1. Write: POST /api/v2/upload/task → uploadId
2. Poll: GET /api/v2/history/goods/task?uploadID={id} → status=success
3. Verify: GET /api/v2/list/goods/filter?limit=1 (filter by nmID)
4. Assert: response.sizes[0].price == sentPrice
5. If mismatch: → RECONCILIATION_FAILED
```

**Current implementation status:** Step 3 (read-after-write verification) is NOT implemented. The system relies on poll result only.

---

## 2. OZON — PRICE UPDATE (synchronous)

### 2.1 Write Endpoint — Update Product Prices

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | code-verified |
| Path | `/v1/product/import/prices` | code-verified |
| Base URL | `https://api-seller.ozon.ru` | confirmed |
| Auth | `Client-Id` + `Api-Key` headers | confirmed |
| Execution model | **Synchronous** — response contains per-item success/failure | code-verified |

### Request Structure

```json
{
  "prices": [
    {
      "offer_id": "ART-001",
      "price": "5000",
      "old_price": "0",
      "currency_code": "RUB",
      "auto_action_enabled": "DISABLED",
      "price_strategy_enabled": "DISABLED"
    }
  ]
}
```

| Field | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `prices` | array | yes | Array of price items (max 1000) | confirmed-docs |
| `prices[].offer_id` | string | yes* | Seller's product identifier | code-verified |
| `prices[].product_id` | int64 | yes* | Ozon internal product ID | confirmed-docs |
| `prices[].price` | string | yes | Sale price (with discount applied), as string | code-verified |
| `prices[].old_price` | string | no | Struck-through price, "0" = no discount | code-verified |
| `prices[].min_price` | string | no | Minimum price for promotions | confirmed-docs |
| `prices[].currency_code` | string | yes | Currency (RUB, BYN, KZT, EUR, USD, CNY) | code-verified |
| `prices[].auto_action_enabled` | string | no | ENABLED / DISABLED / UNKNOWN | code-verified |
| `prices[].price_strategy_enabled` | string | no | ENABLED / DISABLED / UNKNOWN | code-verified |
| `prices[].vat` | string | no | VAT rate: "0", "0.05", "0.07", "0.1", "0.2" | confirmed-docs |
| `prices[].net_price` | string | no | Product cost price (COGS) | confirmed-docs |
| `prices[].min_price_for_auto_actions_enabled` | boolean | no | Consider min_price for auto-promo | confirmed-docs |

*\* If both `offer_id` and `product_id` are provided, `offer_id` takes precedence.*

**Implementation note:** The codebase sends `old_price: "0"` (no discount display), `auto_action_enabled: "DISABLED"`, `price_strategy_enabled: "DISABLED"`. It uses `offer_id` as identifier, falling back to `sourceProductId` if `offerId` is null.

### Response Structure

```json
{
  "result": [
    {
      "product_id": 1179304145,
      "offer_id": "ART-001",
      "updated": true,
      "errors": []
    }
  ]
}
```

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `result` | array | Per-item results | code-verified |
| `result[].product_id` | Long | Ozon product ID | code-verified |
| `result[].offer_id` | String | Seller product ID | code-verified |
| `result[].updated` | boolean | Whether the price was applied | code-verified |
| `result[].errors` | array | Error list (empty = success) | code-verified |
| `result[].errors[].code` | String | Error code | code-verified |
| `result[].errors[].message` | String | Error description | code-verified |

### Rate Limits

| Constraint | Value | Confidence |
|------------|-------|------------|
| Max items per request | 1000 | confirmed-docs |
| Max updates per product per hour | 10 | confirmed-docs |
| API rate limit | Not explicitly documented | assumed |

### Error Handling

| Scenario | HTTP Status | Response | Confidence |
|----------|-------------|----------|------------|
| Success | 200 | `result[].updated: true` | code-verified |
| Per-item rejection | 200 | `result[].updated: false`, `errors` populated | code-verified |
| Empty prices array | 403 | Forbidden | confirmed |
| Invalid auth | 403 | Forbidden | confirmed |
| Invalid request | 400 | `rpcStatus` with code, message, details | confirmed-docs |

### Minimum Price Gap Requirements

| Sale price (`price`) | Min gap to `old_price` | Confidence |
|----------------------|------------------------|------------|
| < 400 RUB | ≥ 20 RUB | confirmed-docs |
| 400–10,000 RUB | ≥ 5% | confirmed-docs |
| > 10,000 RUB | ≥ 500 RUB | confirmed-docs |

---

### 2.2 Ozon Reconciliation Read — Verify Price Applied

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | confirmed |
| Path | `/v5/product/info/prices` | confirmed |
| Base URL | `https://api-seller.ozon.ru` | confirmed |
| Auth | `Client-Id` + `Api-Key` headers | confirmed |

This endpoint (documented in `ozon-read-contracts.md` §2 PRICES) serves as the **read-after-write verification** endpoint. After a synchronous price update, the system can query current prices to confirm the change took effect.

### Reconciliation Read Fields

| Field | Semantics | Confidence |
|-------|-----------|------------|
| `items[].offer_id` | Seller product ID to match | confirmed |
| `items[].price.price` | Current active sale price | confirmed |
| `items[].price.old_price` | Current display old price | confirmed |
| `items[].price.marketing_seller_price` | Price in marketing actions | confirmed |

### Reconciliation Logic (recommended)

```
1. Write: POST /v1/product/import/prices → result[].updated=true
2. Verify: POST /v5/product/info/prices (filter by offer_id)
3. Assert: response.items[].price.price == sentPrice
4. If mismatch: → RECONCILIATION_FAILED
```

**Current implementation status:** Read-after-write verification is NOT implemented. The system relies on the synchronous `updated: true` response only.

---

## 3. ARCHITECTURE MAPPING

### 3.1 Code-to-Contract Mapping

| Architecture Component | Code | Contract Endpoint |
|------------------------|------|-------------------|
| `MarketplacePriceAdapter` | Interface in `datapulse-core` | — |
| `WbPriceCommandAdapter` | `datapulse-marketplaces` | WB §1.1 + §1.2 |
| `OzonPriceCommandAdapter` | `datapulse-marketplaces` | Ozon §2.1 |
| `PriceUpdateService` | `datapulse-core` | Orchestrator |
| `MarketplaceCommandClient` | `datapulse-marketplaces` | HTTP client (WebClient) |
| `PriceUpdateLogEntity` | `datapulse-core` | Internal audit log |

### 3.2 Endpoint Configuration

Source: `datapulse-marketplaces.yml`

| EndpointKey | Configured URL | Status |
|-------------|----------------|--------|
| `CMD_WB_UPDATE_PRICES` | `https://discounts-api.wildberries.ru/api/v2/upload/task` | **BROKEN** — DNS fails |
| `CMD_WB_PRICE_UPLOAD_DETAILS` | `https://discounts-api.wildberries.ru/api/v2/history/goods/task` | **BROKEN** — DNS fails |
| `CMD_OZON_UPDATE_PRICES` | `https://api-seller.ozon.ru/v1/product/import/prices` | OK |

### 3.3 Naming Divergence

| Architecture docs | Codebase | Contract |
|-------------------|----------|----------|
| `PriceWriteGateway` | `MarketplacePriceAdapter` | This document |
| `PriceAction` | `PriceUpdateLogEntity` | Internal state |
| `ReconciliationWorker` | Not implemented | §1.3, §2.2 |

---

## 4. FINDINGS & CRITICAL ISSUES

### F-1: WB Host Migration (CRITICAL)

**Finding:** `discounts-api.wildberries.ru` no longer resolves (DNS failure as of 2026-03-30).
**Correct host:** `discounts-prices-api.wildberries.ru`
**Impact:** WB price updates are **completely broken** in production with the current `datapulse-marketplaces.yml` config.
**Evidence:**
- `Resolve-DnsName discounts-api.wildberries.ru` → DNS error
- `GET https://discounts-prices-api.wildberries.ru/api/v2/list/goods/filter` → 200 OK
- Sandbox host `discounts-prices-api-sandbox.wildberries.ru` → accessible

**Fix required in `datapulse-marketplaces.yml`:**
```yaml
CMD_WB_UPDATE_PRICES:
  url: https://discounts-prices-api.wildberries.ru/api/v2/upload/task
CMD_WB_PRICE_UPLOAD_DETAILS:
  url: https://discounts-prices-api.wildberries.ru/api/v2/history/goods/task
```

### F-2: WB Token Scope for Price Write (HIGH)

**Finding:** Production token returns `401 Unauthorized` for `POST /api/v2/upload/task` on the correct host.
**Probable cause:** The production token (`s=1073823486`) may not include the "Prices and Discounts" write scope. The `GET /api/v2/list/goods/filter` (read) works fine — indicating the read scope is present but write scope may be separate.
**Action:** Verify token scope bits in WB seller cabinet. The write endpoint likely requires explicit "Цены и скидки → Запись" permission.

### F-3: Ozon Empty Prices Array → 403 (LOW)

**Finding:** Submitting `{"prices":[]}` returns `403 Forbidden`, not `400 Bad Request`.
**Impact:** Minor — the adapter always sends at least one item. Edge case only.

### F-4: Reconciliation Not Implemented (MEDIUM)

**Finding:** Neither WB nor Ozon adapters perform read-after-write verification. The system relies on:
- WB: async poll result (`historyGoods[].errorText` empty = success)
- Ozon: synchronous `result[].updated: true`

The architecture (ADR-016) explicitly requires `RECONCILIATION_PENDING` → `RECONCILED` / `RECONCILIATION_FAILED` lifecycle, but the current implementation goes directly to `SUCCESS`.

### F-5: WB Price Truncation Risk (MEDIUM)

**Finding:** `newPrice.intValueExact()` throws `ArithmeticException` for fractional prices.
WB API accepts only integer prices in whole RUB. This is correct API behavior but requires upstream validation to prevent runtime exceptions.

### F-6: Ozon `old_price: "0"` Semantics (LOW)

**Finding:** The codebase always sends `old_price: "0"`, which resets any crossed-out price display.
If the product previously had an `old_price`, this will remove the discount visual. May be intentional but should be documented as a known behavior.

---

## 5. RATE LIMITS

### WB Price Write

| Constraint | Value | Confidence |
|------------|-------|------------|
| Rate limit group | `WB_PRICE_UPDATE` | code-verified |
| Official limit | Not explicitly documented | unknown |
| Community reports | ~5 req/min | assumed |

### Ozon Price Write

| Constraint | Value | Confidence |
|------------|-------|------------|
| Rate limit group | `OZON_PRICE_UPDATE` | code-verified |
| Per-request limit | 1000 items | confirmed-docs |
| Per-product limit | 10 updates/hour | confirmed-docs |
| API-level limit | Not explicitly documented | unknown |

---

## 6. IDEMPOTENCY

### WB

- `alreadyExists: true` in response indicates duplicate upload — provides natural idempotency at task level
- Same `(nmID, price, discount)` combination may or may not trigger `alreadyExists` depending on WB internal dedup window
- **Confidence:** assumed (not tested)

### Ozon

- No explicit idempotency mechanism. Repeated calls with same price succeed with `updated: true`
- Price-per-hour limit (10/product) acts as implicit throttle
- **Confidence:** confirmed-docs

---

## 7. SIGN CONVENTIONS & UNITS

| Provider | Price unit | Currency | Sign | Confidence |
|----------|-----------|----------|------|------------|
| WB | Whole RUB (integer) | RUB only | Positive | confirmed |
| Ozon | String representation of RUB | RUB (or as per account) | Positive | confirmed-docs |

---

## 8. SUMMARY STATUS

| Capability | WB | Ozon | Notes |
|------------|----|----- |-------|
| Price Write | **BROKEN** (host migration) | READY | WB requires YAML fix + token scope verification |
| Async Poll | **BROKEN** (host migration) | N/A (synchronous) | WB-only, same host issue |
| Reconciliation Read | READY (endpoint works) | READY (endpoint works) | Logic not implemented |
| Reconciliation Logic | NOT IMPLEMENTED | NOT IMPLEMENTED | ADR-016 gap |
| Rate Limiting | Configured, limits unknown | Configured, per-product limit documented | — |
| Idempotency | Partial (alreadyExists) | None | — |
