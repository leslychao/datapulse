# Wildberries — Provider Read Contracts

**Статус:** sandbox-verified + cross-checked with official docs (6/7 capabilities verified, returns unblocked)
**Источник:** https://dev.wildberries.ru / https://openapi.wildberries.ru
**Верификация:** sandbox API (content-api-sandbox, discounts-prices-api-sandbox, marketplace-api-sandbox, statistics-api-sandbox) + official docs

Этот документ фиксирует read-контракты Wildberries API для каждой capability,
релевантной ingestion pipeline Datapulse.

Каждое семантическое свойство классифицировано:
- **confirmed** — подтверждено реальным API-ответом (sandbox или production)
- **confirmed-docs** — проверено по документации, но не получено в реальном ответе
- **assumed** — выведено из документации или ответа, но не подтверждено явно
- **unknown** — не удалось подтвердить, требует дополнительного исследования

**Sandbox URLs:**
- Content: `https://content-api-sandbox.wildberries.ru`
- Prices: `https://discounts-prices-api-sandbox.wildberries.ru`
- Marketplace: `https://marketplace-api-sandbox.wildberries.ru`
- Statistics: `https://statistics-api-sandbox.wildberries.ru`

---

## 1. CATALOG

### Endpoint

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | confirmed |
| Path | `/content/v2/get/cards/list` | confirmed |
| Base URL | `https://content-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |
| Token category | Content | confirmed |

### Response Structure — SANDBOX VERIFIED

```json
{
  "cards": [{ "nmID": 274849, "vendorCode": "...", "brand": "...", ... }],
  "cursor": { "updatedAt": "2025-12-16T17:40:20Z", "nmID": 274849, "total": 1 }
}
```

### Pagination

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Type | Cursor-based | confirmed |
| Cursor fields | `cursor.updatedAt`, `cursor.nmID` | confirmed |
| Limit | `cursor.limit` (max 100) | confirmed |
| Termination | `cursor.total < limit` | confirmed |
| Sort | By `updatedAt` ascending | confirmed |

### Identifier Semantics

| Provider field | Semantics | Confidence |
|----------------|-----------|------------|
| `nmID` | WB internal product ID (nomenclature). Primary identifier. | confirmed |
| `vendorCode` | Seller's own article / SKU | confirmed |
| `imtID` | Internal media/template ID | confirmed |
| `nmUUID` | Product UUID | confirmed |
| `subjectID` | Category/subject ID | confirmed |
| `sizes[].chrtID` | Size-level characteristic ID | confirmed |
| `sizes[].skus[]` | Barcodes per size (array of strings) | confirmed |

### Join Key Semantics

| Связь | Join key | Confidence |
|-------|----------|------------|
| Catalog → Prices | `nmID` = prices `nmId` | confirmed-docs |
| Catalog → Stocks | `sizes[].skus[]` (barcode) or `nmID` | assumed |
| Catalog → Sales/Orders | `nmId` in statistics reports | confirmed-docs |
| Catalog → Finance | `nm_id` in reportDetailByPeriod | confirmed-docs |

### Timestamp Semantics

| Field | Semantics | Confidence |
|-------|-----------|------------|
| `createdAt` | Card creation time (ISO 8601, UTC). Example: "2025-12-16T17:40:20.827Z" | confirmed |
| `updatedAt` | Last card modification time (ISO 8601, UTC) | confirmed |

### Key Response Fields — SANDBOX VERIFIED

| Field | Type | Semantics | Sample | Confidence |
|-------|------|-----------|--------|------------|
| `nmID` | long | WB product ID | 274849 | confirmed |
| `vendorCode` | string | Seller article | "Артикул_2" | confirmed |
| `brand` | string | Brand name | "Бренд" | confirmed |
| `title` | string | Product title | "Подзаголовок товара" | confirmed |
| `subjectName` | string | Category name | "Купальники" | confirmed |
| `subjectID` | int | Category ID | 105 | confirmed |
| `description` | string | Product description | — | confirmed |
| `dimensions` | object | `{length, width, height, weightBrutto, isValid}` | — | confirmed |
| `sizes` | array | `[{chrtID, techSize, wbSize, skus[]}]` | — | confirmed |
| `sizes[].techSize` | string | Technical size | "S" | confirmed |
| `sizes[].wbSize` | string | WB display size | "42" | confirmed |
| `characteristics` | array | `[{id, name, value[]}]` | — | confirmed |
| `needKiz` | boolean | Requires marking code | false | confirmed |
| `imtID` | long | Media template ID | 274847 | confirmed |
| `nmUUID` | string | Product UUID | "ff722b33-..." | confirmed |

### Data Freshness

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Freshness | Near real-time (card updates reflected quickly) | assumed |
| Lag | Minutes to low hours | assumed |

### Known Limitations

- Max 100 items per page (confirmed)
- No filtering by status (active/inactive) in this endpoint (assumed)
- Text search accepts single article/barcode only (confirmed-docs)

### Rate Limits

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Rate limit | Not explicitly documented for this endpoint | unknown |

---

## 2. PRICES

### Endpoint

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` / `POST` | confirmed |
| Path | `/api/v2/list/goods/filter` | confirmed |
| Base URL | `https://discounts-prices-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |
| Token category | Content (Prices and Discounts) | confirmed |

### Response Structure — SANDBOX VERIFIED

```json
{
  "data": {
    "listGoods": [{
      "nmID": 274849,
      "vendorCode": "...",
      "sizes": [{ "sizeID": 274848, "price": 5000, "discountedPrice": 5000, "clubDiscountedPrice": 5000, "techSizeName": "S" }],
      "currencyIsoCode4217": "RUB",
      "discount": 0, "clubDiscount": 0, "editableSizePrice": false
    }]
  },
  "error": false,
  "errorText": ""
}
```

### Pagination

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Type | Offset-based (`offset` parameter) | confirmed |
| Limit | `limit` parameter | confirmed |
| POST variant | Allows filtering by multiple articles in one request | confirmed-docs |

### Identifier Semantics

| Provider field | Semantics | Confidence |
|----------------|-----------|------------|
| `nmID` | WB product ID (nomenclature) | confirmed |
| `sizes[].sizeID` | Size-level ID | confirmed |
| `sizes[].techSizeName` | Technical size name (e.g. "S") | confirmed |

### Amount Semantics — SANDBOX VERIFIED

| Field | Type | Semantics | Sample | Confidence |
|-------|------|-----------|--------|------------|
| `sizes[].price` | number | Base price per size (RUB, whole units) | 5000 | confirmed |
| `sizes[].discountedPrice` | number | Price after seller discount | 5000 | confirmed |
| `sizes[].clubDiscountedPrice` | number | Price for WB Club members | 5000 | confirmed |
| `discount` | number | Seller discount percentage | 0 | confirmed |
| `clubDiscount` | number | WB Club discount percentage | 0 | confirmed |
| `editableSizePrice` | boolean | Whether per-size price editing is enabled | false | confirmed |
| `currencyIsoCode4217` | string | Currency ISO code | "RUB" | confirmed |

**PRICES ARE PER-SIZE**: Each `sizes[]` element has its own `price`, `discountedPrice`,
`clubDiscountedPrice`. When `editableSizePrice` = false, all sizes share the same price.

**PRICE HIERARCHY**: `price` → apply `discount` → `discountedPrice` → apply `clubDiscount` → `clubDiscountedPrice`

### Amount Field Semantics

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Currency | RUB (explicit via `currencyIsoCode4217`) | confirmed |
| Unit | Rubles (whole), not kopecks | confirmed |
| Sign | Positive values only | confirmed |

### Timestamp Semantics

| Field | Semantics | Confidence |
|-------|-----------|------------|
| (none) | No timestamps in price response | assumed |

### Data Freshness

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Freshness | Near real-time (reflects current prices) | assumed |

### Known Limitations

- 403 errors reported by community for some token configurations (confirmed-docs via community)
- Exact interaction between seller discount and WB markup is opaque (confirmed as limitation)
- No historical price data — only current snapshot (confirmed-docs)
- Response uses `data.listGoods[]` wrapper (confirmed)

### Rate Limits

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Rate limit | Not explicitly documented | unknown |

---

## 3. STOCKS

> **Updated 2026-03-31** — fields verified via official documentation (dev.wildberries.ru).
> Previous version had Assumed/Unknown field names. Now Confirmed-docs.

### Endpoint

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/api/analytics/v1/stocks-report/wb-warehouses` | confirmed |
| Base URL | `https://seller-analytics-api.wildberries.ru` | confirmed |
| Auth | API Key (Personal or Service token) | confirmed-docs |
| Token category | Analytics | confirmed-docs |

**NOTE**: Endpoint `/api/v1/analytics/stocks-report` returns 404 (deprecated).
The correct path is `/api/analytics/v1/stocks-report/wb-warehouses`.
Returns empty string for account without stock data.
Old `GET /api/v1/supplier/stocks` is deprecated (disabled June 2026).

### Response Structure — CONFIRMED from Official Docs

```json
{
  "data": {
    "items": [
      {
        "nmId": 47254354,
        "chrtId": 91663228,
        "warehouseId": 507,
        "warehouseName": "Коледино",
        "regionName": "Центральный",
        "quantity": 43,
        "inWayToClient": 14,
        "inWayFromClient": 11
      }
    ]
  }
}
```

### Pagination

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Type | Offset-based (`offset` + `limit` in request body) | confirmed-docs |
| Max rows | 250,000 per response | confirmed-docs |
| Sort | Ascending by WB article (nmID) | confirmed-docs |
| Request body fields | `nmIds[]`, `chrtIds[]`, `limit`, `offset` | confirmed-docs |

### Identifier Semantics

| Provider field | Type | Semantics | Confidence |
|----------------|------|-----------|------------|
| `nmId` | long | WB product nomenclature ID (same as catalog `nmID`) | confirmed-docs |
| `chrtId` | long | Size-level characteristic ID (same as catalog `sizes[].chrtID`) | confirmed-docs |
| `warehouseId` | int | WB warehouse numeric identifier | confirmed-docs |
| `warehouseName` | string | Warehouse name (e.g. "Коледино") | confirmed-docs |

### Granularity

**1 row = 1 size × 1 warehouse.** Data is at `(nmId, chrtId, warehouseId)` grain.
For product-level stock, must aggregate: `SUM(quantity) GROUP BY nmId, warehouseId`.

### Quantity Fields — CONFIRMED from Official Docs

| Field | Type | Semantics | Sample | Confidence |
|-------|------|-----------|--------|------------|
| `quantity` | int | Available stock quantity at warehouse | 43 | confirmed-docs |
| `inWayToClient` | int | Units in transit to customer | 14 | confirmed-docs |
| `inWayFromClient` | int | Units in transit from customer (returns in transit) | 11 | confirmed-docs |

**CRITICAL:** No `reserved` field exists in this endpoint. `reserved` in canonical DDL
must be set to `0` for WB. `inWayToClient` and `inWayFromClient` are supplementary
transit metrics, not "reserved" stock.

### Additional Fields

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `regionName` | string | Shipping region (e.g. "Центральный") | confirmed-docs |

### Join Key Semantics

| Связь | Join key | Confidence |
|-------|----------|------------|
| Stocks → Catalog | `nmId` = catalog `nmID` | confirmed-docs |
| Stocks → Catalog (size) | `chrtId` = catalog `sizes[].chrtID` | confirmed-docs |
| Stocks → Warehouse | `warehouseId` → `warehouse.external_warehouse_id` | confirmed-docs |

**NOTE:** No `vendorCode`/`barcode` in stocks response. Join to catalog is via `nmId` only.
Resolved to `seller_sku` via: `nmId` → `marketplace_offer.marketplace_sku` → `seller_sku`.

### Timestamp Semantics

| Field | Semantics | Confidence |
|-------|-----------|------------|
| (none) | No timestamps in stocks response | confirmed-docs |
| Data freshness | Snapshot updated every 30 minutes | confirmed-docs |

### Data Freshness

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Freshness | Updated once every 30 minutes | confirmed-docs |
| Report nature | Snapshot of current state | confirmed-docs |

### Known Limitations

- 3 requests per minute, burst 1 per 20 seconds (confirmed-docs)
- Max 250,000 rows per response (confirmed-docs)
- Ability to filter by specific `nmIds[]` and `chrtIds[]` (confirmed-docs)
- Old `GET /api/v1/supplier/stocks` endpoint deprecated (confirmed-docs)
- Old `/api/v1/analytics/stocks-report` returns 404 (confirmed)
- Returns empty string for accounts without stock (confirmed)
- No `reserved` field — only `quantity`, `inWayToClient`, `inWayFromClient` (confirmed-docs)
- Grain is per-size (chrtId), NOT per-product — aggregation needed (confirmed-docs)
- No `vendorCode`/`barcode` in response — join via `nmId` only (confirmed-docs)

### Rate Limits

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Rate limit | 3 requests per minute | confirmed-docs |
| Burst | 1 request per 20 seconds | confirmed-docs |
| Token types | Personal, Service | confirmed-docs |

---

## 4. ORDERS

### Endpoint

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | confirmed |
| Path | `/api/v1/supplier/orders` | confirmed |
| Base URL | `https://statistics-api.wildberries.ru` | confirmed |
| Auth | API Key | confirmed |
| Token category | Statistics | confirmed |

**SANDBOX VERIFIED** — full order data with test data.

### Pagination

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Type | Date-range (`dateFrom` parameter) + `flag` parameter | confirmed |
| `flag=0` | Return data updated since `dateFrom` | confirmed-docs |
| `flag=1` | Return data created since `dateFrom` | confirmed-docs |

### Identifier Semantics

| Provider field | Type | Semantics | Confidence |
|----------------|------|-----------|------------|
| `srid` | string | Unique shipment/row identifier | confirmed |
| `gNumber` | string | Order group number | confirmed |
| `nmId` | long | WB product nomenclature ID | confirmed |
| `supplierArticle` | string | Seller's article (vendorCode) | confirmed |
| `barcode` | string | Product barcode | confirmed |

### Amount Fields — SANDBOX VERIFIED

| Field | Type | Semantics | Sample | Confidence |
|-------|------|-----------|--------|------------|
| `totalPrice` | number | Gross price before discount | 2230 | confirmed |
| `discountPercent` | number | Discount percentage applied | 60 | confirmed |
| `priceWithDisc` | number | Price after discount | 2230 | confirmed |
| `spp` | number | SPP discount amount | 0 | confirmed |
| `finishedPrice` | number | Final price | 0 | confirmed |
| Currency | — | RUB (implicit) | — | confirmed |
| Unit | — | Rubles (whole, not kopecks) | — | confirmed |

### Status Semantics — SANDBOX VERIFIED

| Field | Type | Semantics | Sample | Confidence |
|-------|------|-----------|--------|------------|
| `isCancel` | boolean | Whether order was cancelled | true/false | confirmed |
| `cancelDate` | string | Cancellation timestamp (ISO 8601 or "0001-01-01T00:00:00" if not cancelled) | "2025-12-01T07:54:35Z" | confirmed |
| `isSupply` | boolean | Whether supply-related | false | confirmed |
| `isRealization` | boolean | Whether realization-related | true | confirmed |

### Timestamp Semantics — SANDBOX VERIFIED

| Field | Semantics | Sample | Confidence |
|-------|-----------|--------|------------|
| `date` | Order date (ISO 8601 UTC) | "2025-12-01T07:51:35Z" | confirmed |
| `lastChangeDate` | Last status change (ISO 8601 UTC) | "2025-12-01T12:54:35Z" | confirmed |
| Timezone | UTC (Z suffix) | — | confirmed |

### Data Freshness

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Freshness | Data appears with delay | assumed |
| Lag | Not explicitly documented | unknown |

### Known Limitations

- 1 request per minute (confirmed-docs)
- Statistics token required (confirmed)
- Row = 1 unit of goods (1 order can have multiple rows) (confirmed-docs)
- Historical data availability window not documented (unknown)
- Returns empty/null for accounts without orders (confirmed)

### Rate Limits

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Rate limit | 1 request per minute | confirmed-docs |

---

## 5. SALES

### Endpoint

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | confirmed |
| Path | `/api/v1/supplier/sales` | confirmed |
| Base URL | `https://statistics-api.wildberries.ru` | confirmed |
| Auth | API Key | confirmed |
| Token category | Statistics | confirmed |

**SANDBOX VERIFIED** — full sales data with test data.

### Pagination

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Type | Date-range (`dateFrom`) + `flag` parameter | confirmed |
| Same semantics as Orders | `flag=0` updated since, `flag=1` created since | confirmed-docs |

### Identifier Semantics — SANDBOX VERIFIED

| Provider field | Type | Semantics | Sample | Confidence |
|----------------|------|-----------|--------|------------|
| `saleID` | string | Sale identifier (`S` = sale, `R` = return/storno) | "S3207347857" | confirmed |
| `srid` | string | Unique shipment/row identifier | "27427813070712635.2.2" | confirmed |
| `gNumber` | string | Order group number | "1374066348108264155" | confirmed |
| `nmId` | long | WB product nomenclature ID | 115689477 | confirmed |
| `supplierArticle` | string | Seller's article | "BGC03067" | confirmed |
| `barcode` | string | Product barcode | "BGC03067" | confirmed |

### Sign Semantics — SANDBOX VERIFIED

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Sale prefix | `saleID` starts with `S` = sale | confirmed |
| Return/storno prefix | `saleID` starts with `R` = return/storno | confirmed-docs |
| Amount sign | Returns likely have negative `forPay` / `finishedPrice` | assumed |

### Amount Fields — SANDBOX VERIFIED

| Field | Type | Semantics | Sample | Confidence |
|-------|------|-----------|--------|------------|
| `totalPrice` | number | Gross price before discount | 20736 | confirmed |
| `discountPercent` | number | Discount percentage | 75 | confirmed |
| `spp` | number | WB customer discount (SPP) | 0 | confirmed |
| `forPay` | number | Amount to be paid to seller | 4182.96 | confirmed |
| `finishedPrice` | number | Final price after all discounts | 4183.96 | confirmed |
| `priceWithDisc` | number | Price after seller discount (before SPP) | 4669 | confirmed |
| Currency | — | RUB (implicit) | — | confirmed |
| Unit | — | Rubles (with decimal kopecks) | — | confirmed |

**PRICE HIERARCHY**: `totalPrice` → apply `discountPercent` → `priceWithDisc` → apply `spp` → `finishedPrice` → minus commission → `forPay`

### Timestamp Semantics — SANDBOX VERIFIED

| Field | Semantics | Sample | Confidence |
|-------|-----------|--------|------------|
| `date` | Sale/return date (ISO 8601 UTC) | "2025-11-30T18:41:20Z" | confirmed |
| `lastChangeDate` | Last status change (ISO 8601 UTC) | "2025-12-01T12:54:20Z" | confirmed |
| Timezone | UTC (Z suffix) | — | confirmed |

### Data Freshness

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Freshness | Data appears with delay (hours to day) | assumed |
| Row granularity | 1 row = 1 unit sold | confirmed-docs |

### Known Limitations

- 1 request per minute (confirmed-docs)
- Statistics token required (confirmed)
- Returns mixed with sales via `saleID` prefix semantics (confirmed-docs)
- Exact meaning of `spp` computation not fully transparent (assumed)
- Returns empty/null for accounts without sales (confirmed)

### Rate Limits

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Rate limit | 1 request per minute | confirmed-docs |

---

## 6. RETURNS

> **Previously BLOCKED** — resolved 2026-03-30. Root cause: incorrect date format (datetime instead of date-only).

### Endpoint

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | confirmed |
| Path | `/api/v1/analytics/goods-return` | confirmed |
| Base URL | `https://seller-analytics-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |
| Token category | Analytics | confirmed-docs |

### Request Parameters

| Param | Type | Required | Format | Example | Confidence |
|-------|------|----------|--------|---------|------------|
| `dateFrom` | string | yes | `YYYY-MM-DD` (date-only!) | `2024-08-13` | confirmed |
| `dateTo` | string | yes | `YYYY-MM-DD` (date-only!) | `2024-08-27` | confirmed |

> **CRITICAL:** Parameters must be **date-only** format (`2024-08-13`), NOT datetime (`2024-08-13T00:00:00Z`).
> Datetime format causes `400 Bad Request`. This was the root cause of all previous 400 errors.

Max query window: **31 days** per request (confirmed-docs).

### Response Structure — CONFIRMED from Official Docs

```json
{
  "report": [
    {
      "barcode": "1680063403480",
      "brand": "dub",
      "completedDt": "2025-03-31T11:33:53",
      "dstOfficeAddress": "Жуковский Улица Маяковского 19",
      "dstOfficeId": 310105,
      "expiredDt": "2025-03-31T11:33:53",
      "isStatusActive": 0,
      "nmId": 12862181,
      "orderDt": "2024-08-26",
      "orderId": 2034240826,
      "readyToReturnDt": "2025-01-31T08:33:50",
      "reason": "Цвет",
      "returnType": "Возврат заблокированного товара",
      "shkId": 23411783472,
      "srid": "ad3817664d3046c5a8d55054d8be96d6",
      "status": "В пути в пвз",
      "stickerId": "33811984302",
      "subjectName": "Багажные бирки",
      "techSize": "0"
    }
  ]
}
```

### Identifier Semantics

| Provider field | Type | Semantics | Confidence |
|----------------|------|-----------|------------|
| `nmId` | long | Product nomenclature ID | confirmed-docs |
| `barcode` | string | Product barcode | confirmed-docs |
| `srid` | string | Unique shipment/row identifier | confirmed-docs |
| `orderId` | long | Assembly order number (may be 0 for defect returns) | confirmed-docs |
| `shkId` | long | Unique barcode ID | confirmed-docs |
| `stickerId` | string | WB sticker ID | confirmed-docs |
| `dstOfficeId` | long | Destination office ID | confirmed-docs |

### Status Semantics

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `status` | string | Current return status (e.g., "В пути в пвз") | confirmed-docs |
| `isStatusActive` | int | Whether the return status is currently active (0/1) | confirmed-docs |
| `returnType` | string | Return type classification (e.g., "Возврат заблокированного товара", "Возврат брака") | confirmed-docs |
| `reason` | string | Return reason text (e.g., "Цвет") | confirmed-docs |

### Timestamp Semantics

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `orderDt` | string | Order date (date-only: `YYYY-MM-DD`) | confirmed-docs |
| `completedDt` | string | Return completion datetime (ISO 8601 without tz) | confirmed-docs |
| `readyToReturnDt` | string | Date/time when item became ready for return | confirmed-docs |
| `expiredDt` | string | Return expiration datetime | confirmed-docs |

### Additional Fields

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `brand` | string | Product brand name | confirmed-docs |
| `subjectName` | string | Product subject/category name | confirmed-docs |
| `techSize` | string | Technical size (e.g., "0" for one-size) | confirmed-docs |
| `dstOfficeAddress` | string | Destination office address | confirmed-docs |

### Amount Fields

No monetary amount fields are present in this endpoint. Financial impact of returns must be derived from:
- Finance report (`reportDetailByPeriod`) with `supplier_oper_name` = return operations
- Sales endpoint (`/api/v1/supplier/sales`) where `saleID` starts with `R`

### Alternative Sources

Returns are also partially visible through:
- Sales endpoint (`/api/v1/supplier/sales`) where `saleID` starts with `R` — includes `forPay`/`finishedPrice`
- Finance report (`reportDetailByPeriod`) with `supplier_oper_name` = return operations — includes full financial breakdown

### Data Freshness

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Freshness | Not explicitly documented | assumed |

### Known Limitations

- `orderId` returns 0 for some return types (e.g., defect returns) — community-confirmed issue
- No monetary fields — financial impact must come from finance report
- `srid` in returns maps to `srid` in sales/finance for cross-referencing
- Max 31 days per query
- Empty `report:[]` for accounts without returns (confirmed via production token)

### Rate Limits

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Rate limit | 1 request per minute, burst 10 | confirmed-docs |

---

## 7. FINANCES

### Endpoint

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | confirmed |
| Path | `/api/v5/supplier/reportDetailByPeriod` | confirmed |
| Base URL | `https://statistics-api.wildberries.ru` | confirmed |
| Auth | API Key | confirmed |
| Token category | Statistics | confirmed |

Returns empty string for accounts without finance data (confirmed).

### Pagination

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Type | Date-range (`dateFrom`, `dateTo`) + `rrdid` cursor | confirmed-docs |
| Max period | None enforced by API; official docs specify no explicit limit | confirmed-docs |
| Cursor | `rrdid` — pass last row's `rrd_id` for next page, start with 0 | confirmed-docs |
| Limit | `limit` param, default 100000, max 100000 | confirmed-docs |
| Period | `period` param: `"weekly"` (default) or `"daily"` | confirmed-docs |
| Termination | HTTP 204 No Content = no more data | confirmed-docs |

### Identifier Semantics

| Provider field | Semantics | Confidence |
|----------------|-----------|------------|
| `realizationreport_id` | Realization report ID | confirmed-docs+sandbox |
| `rrd_id` | Unique row ID within report; used as cursor for pagination | confirmed-docs+sandbox |
| `srid` | Unique row/shipment identifier | confirmed-docs+sandbox |
| `nm_id` | Product nomenclature ID | confirmed-docs+sandbox |
| `sa_name` | Seller article (sa = supplier article) | confirmed-docs+sandbox |
| `barcode` | Product barcode | confirmed-docs+sandbox |
| `gi_id` | Delivery number | confirmed-docs |
| `shk_id` | Unique barcode ID | confirmed-docs |
| `assembly_id` | Assembly task ID | confirmed-docs+sandbox |
| `order_uid` | Order unique ID (new v5 field) | confirmed-docs |
| `sticker_id` | WB sticker ID | confirmed-docs |

### Operation Type Semantics

| Field | Semantics | Confidence |
|-------|-----------|------------|
| `doc_type_name` | Document type: "Продажа", "Возврат" etc. | confirmed-docs+sandbox |
| `supplier_oper_name` | Operation name: "Продажа", "Логистика", "Возврат" etc. | confirmed-docs+sandbox |
| `bonus_type_name` | Penalty/bonus description when applicable | confirmed-docs |

**doc_type_name known values** (official WB docs, 2026-03-30):
- `"Продажа"` — sale (credit to seller)
- `"Возврат"` — return (debit from seller)
- (empty) — for logistics, storage, deductions, penalties

**supplier_oper_name full list** (official WB docs seller.wildberries.ru, updated 2026-03-30):

| supplier_oper_name | Semantics | Confidence |
|---|---|---|
| Продажа | Buyer purchased item | confirmed-docs |
| Возврат | Buyer returned purchased item | confirmed-docs |
| Логистика | Delivery (warehouse→PVZ, PVZ→warehouse, warehouse→seller) | confirmed-docs |
| Хранение | Weekly storage fee at WB warehouses | confirmed-docs |
| Обработка товара | Supply acceptance processing at warehouses | confirmed-docs |
| Штраф | Penalty/fine (details in `bonus_type_name`) | confirmed-docs |
| Удержания | Deductions: tariff constructor fees, Jam subscription, WB Promotion, other services | confirmed-docs |
| Компенсация ущерба | Compensation for lost/substituted items (not seller's fault) | confirmed-docs |
| Добровольная компенсация при возврате | Compensation for damaged items (with defect or incomplete set) | confirmed-docs |
| Коррекция продаж | Correction of previously accrued sale amounts | confirmed-docs |
| Коррекция логистики | Correction of previously accrued logistics amounts | confirmed-docs |
| Коррекция эквайринга | Correction of previously accrued acquiring amounts | confirmed-docs |
| Возмещение издержек по перевозке/по складским операциям с товаром | Reimbursement of third-party transport/warehouse costs | confirmed-docs |
| Возмещение за выдачу и возврат товаров на ПВЗ | PVZ issuance/return service costs | confirmed-docs |
| Услуга платной доставки | Paid delivery service for DBS/EDBS models | confirmed-docs |
| Бронирование товара через самовывоз | Click & Collect (C&C) reservation | confirmed-docs |
| Стоимость участия в программе лояльности | Cashback commission set by seller | confirmed-docs |
| Сумма, удержанная за начисленные баллы программы лояльности | Buyer cashback deduction | confirmed-docs |
| Компенсация скидки по программе лояльности | Cashback spent by buyer on item payment | confirmed-docs |
| Разовое изменение срока перечисления денежных средств | "Withdraw now" service fee | confirmed-docs |

**NOTE:** `supplier_oper_name` values can be combined with various `doc_type_name` values. For example, "Добровольная компенсация при возврате" appears with both "Продажа" (credit: WB compensates) and "Возврат" (debit: item returned to warehouse). Corrections (Коррекция *) can adjust amounts in either direction.

### Amount Fields — CONFIRMED from Official Docs + Sandbox

| Field | Type | Semantics | Official sample | Sandbox verified | Confidence |
|-------|------|-----------|-----------------|------------------|------------|
| `ppvz_for_pay` | number | Amount payable to seller (CREDIT) | 376.99 | ✅ (0 in test) | confirmed-docs+sandbox |
| `ppvz_vw` | number | Revenue (seller's share) | 22.25 | ✅ (0 in test) | confirmed-docs+sandbox |
| `ppvz_vw_nds` | number | VAT on revenue | 4.45 | ✅ (0 in test) | confirmed-docs+sandbox |
| `ppvz_sales_commission` | number | WB sales commission (DEBIT) | 23.74 | ✅ (0 in test) | confirmed-docs+sandbox |
| `delivery_rub` | number | Logistics cost in rubles (DEBIT) | 0 | ✅ **20** | confirmed-docs+sandbox |
| `penalty` | number | Fines/penalties (DEBIT) | 231.35 | ✅ (0 in test) | confirmed-docs+sandbox |
| `storage_fee` | number | Warehouse storage fees (DEBIT) | 12647.29 | absent in sandbox | confirmed-docs |
| `deduction` | number | Withholdings for violations (DEBIT) | 6354 | absent in sandbox | confirmed-docs |
| `additional_payment` | number | Additional charges/withholdings | 0 | ✅ (0) | confirmed-docs+sandbox |
| `rebill_logistic_cost` | number | Re-billed logistics charges (DEBIT) | 1.349 | ✅ **1.349** (exact) | confirmed-docs+sandbox |
| `acquiring_fee` | number | Acquiring commission (DEBIT) | 14.89 | ✅ (0 in test) | confirmed-docs+sandbox |
| `acquiring_percent` | number | Acquiring commission percentage | 4.06 | absent in sandbox | confirmed-docs |
| `acceptance` | number | Acceptance fees (DEBIT) | 865 | absent in sandbox | confirmed-docs |
| `retail_price` | number | Retail price | 1249 | ✅ (0 in test) | confirmed-docs+sandbox |
| `retail_amount` | number | Retail amount | 367 | ✅ (0 in test) | confirmed-docs+sandbox |
| `retail_price_withdisc_rub` | number | Retail price with discount in RUB | 399.68 | ✅ (0 in test) | confirmed-docs+sandbox |
| `dlv_prc` | number | Delivery percentage | 1.8 | ✅ **1.8** (exact) | confirmed-docs+sandbox |

#### New fields in v5 docs NOT present in sandbox

| Field | Type | Semantics | Official sample | Confidence |
|-------|------|-----------|-----------------|------------|
| `currency_name` | string | Currency name ("руб") | "руб" | confirmed-docs |
| `report_type` | number | Report type | 1 | confirmed-docs |
| `kiz` | string | KIZ marking code | "0102900..." | confirmed-docs |
| `cashback_amount` | number | Cashback amount | 0 | confirmed-docs |
| `cashback_discount` | number | Cashback discount | 0 | confirmed-docs |
| `cashback_commission_change` | number | Cashback commission delta | 0 | confirmed-docs |
| `order_uid` | string | Order unique ID | "id375f..." | confirmed-docs |
| `payment_schedule` | number | Payment schedule flag | 0 | confirmed-docs |
| `delivery_method` | string | Delivery method ("FBS, (МГТ)") | "FBS, (МГТ)" | confirmed-docs |
| `seller_promo_id` | number | Seller promo campaign ID | 14350 | confirmed-docs |
| `seller_promo_discount` | number | Seller promo discount | 3 | confirmed-docs |
| `loyalty_id` | number | Loyalty program ID | 0 | confirmed-docs |
| `loyalty_discount` | number | Loyalty discount | 0 | confirmed-docs |
| `uuid_promocode` | string | Promocode UUID | "" | confirmed-docs |
| `sale_price_promocode_discount_prc` | number | Promo discount percent | 0 | confirmed-docs |

#### Fields confirmed in both official docs AND sandbox (updated 2026-03-31)

| Field | Type | Semantics | Official sample | Sandbox value | Confidence |
|-------|------|-----------|-----------------|---------------|------------|
| `installment_cofinancing_amount` | number | Installment co-financing amount | 0 | 0 | confirmed-docs+sandbox |
| `wibes_wb_discount_percent` | number | WB discount percentage (WIBES program) | 1 | 0 | confirmed-docs+sandbox |
| `is_legal_entity` | boolean | Legal entity flag | false | false | confirmed-docs+sandbox |
| `trbx_id` | string | Transport box ID | "WB-TRBX-1234567" | "" | confirmed-docs+sandbox |
| `srv_dbs` | boolean | DBS/FBS delivery marker | true | true | confirmed-docs+sandbox |
| `dlv_prc` | number | Delivery percentage | 1.8 | 1.8 | confirmed-docs+sandbox |
| `fix_tariff_date_from` | string | Fixed tariff start (date-only) | — | "2024-10-23" | confirmed-sandbox |
| `fix_tariff_date_to` | string | Fixed tariff end (date-only) | — | "2024-11-18" | confirmed-sandbox |

#### Sandbox-only fields

| Field | Type | Semantics | Sandbox value | Notes |
|-------|------|-----------|---------------|-------|
| `rid` | number | Unknown (0 in all rows) | 0 | Present in sandbox only, absent from official docs |

### Sign Convention — CONFIRMED from Official Sample + Sandbox

| Свойство | Значение | Confidence |
|----------|----------|------------|
| All values | **POSITIVE absolute amounts** | confirmed-docs+sandbox |
| Credit/debit | Determined by **field name**, not by sign | confirmed-docs+sandbox |
| CREDIT fields | `ppvz_for_pay`, `ppvz_vw`, `additional_payment` (when positive) | confirmed-docs |
| DEBIT fields | `ppvz_sales_commission`, `acquiring_fee`, `delivery_rub`, `penalty`, `storage_fee`, `deduction`, `rebill_logistic_cost`, `acceptance` | confirmed-docs |
| Net calculation | Revenue - Costs = `ppvz_for_pay` - (commission + acquiring + logistics + penalty + storage + ...) | confirmed-docs |
| Sandbox evidence | `delivery_rub = 20` (positive for debit), `rebill_logistic_cost = 1.349` (positive for debit) | confirmed-sandbox |

**CONFIRMED from official API documentation sample response + sandbox empirical data.**
WB uses ALL POSITIVE values — the sign convention is implicit in field names.
This is fundamentally different from Ozon, where negative values = costs.

### Currency and Units

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Currency | RUB | confirmed-docs |
| `currency_name` | "руб" (official docs sample) | confirmed-docs |
| Unit | Rubles (with kopecks as decimals) | confirmed-docs |
| Fields ending in `_rub` | Explicitly in rubles | confirmed-docs |

### Timestamp Semantics — verified 2026-03-31

| Field | Format in Official Docs | Format in Sandbox (2026-03-31) | Confidence |
|-------|------------------------|-------------------------------|------------|
| `rr_dt` | `"2022-10-20"` (date-only) | `"2026-01-02T06:54:29Z"` (full ISO 8601) | **⚠️ DISCREPANCY** |
| `date_from`, `date_to` | `"2022-10-17"` (date-only) | `"2025-12-31T23:54:29Z"` (full ISO 8601) | **⚠️ DISCREPANCY** |
| `create_dt` | `"2022-10-24"` (date-only) | `"2026-01-01T22:54:29Z"` (full ISO 8601) | **⚠️ DISCREPANCY** |
| `order_dt` | `"2022-10-13T00:00:00Z"` (ISO 8601) | `"2026-01-02T06:54:29Z"` (ISO 8601) | confirmed-docs+sandbox |
| `sale_dt` | `"2022-10-20T00:00:00Z"` (ISO 8601) | `"2026-01-02T06:54:29Z"` (ISO 8601) | confirmed-docs+sandbox |
| `fix_tariff_date_from/to` | N/A | `"2024-10-23"` / `"2024-11-18"` (date-only) | confirmed-sandbox |

**⚠️ CRITICAL: `rr_dt`, `date_from`, `date_to`, `create_dt` format discrepancy.**
Official docs show date-only strings. Sandbox returns full ISO 8601 datetime.
Official docs specify `dateFrom` input format as RFC3339 (accepts both date and datetime).
**Implementation MUST parse both formats** (date-only and full ISO 8601 datetime).

### sale_dt Nullability — sandbox-verified 2026-03-31

| Свойство | Значение | Confidence |
|----------|----------|------------|
| `sale_dt` populated for logistics entries | YES — always filled | confirmed-sandbox |
| `sale_dt` populated for storage/penalty entries | Unknown — sandbox doesn't generate these | **unknown** |
| Fallback strategy | If `sale_dt` is null → use `rr_dt` as fallback for `entryDate` | design decision (DD-17) |

Sandbox verification: all 100+ records (logistics type, `delivery_rub: 20`, `quantity: 0`)
had `sale_dt` populated with ISO 8601 datetime. Storage/penalty entry types not present in sandbox.

### Data Freshness

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Freshness | Reports formed periodically (daily or weekly based on `period` param) | confirmed-docs |
| Daily mode | `period=daily` — daily reports | confirmed-docs |
| Weekly mode | `period=weekly` — weekly reports (default) | confirmed-docs |
| Maintenance window | Monday 00:00–16:00 Moscow time | confirmed-docs |
| Data availability | From January 29, 2024 onward | confirmed-docs |

### Known Limitations

- Report structure is flat — one row per operation per item (confirmed-docs)
- Sign convention CONFIRMED: all values positive, field name determines credit/debit (confirmed-docs+sandbox)
- 1 request per 1 minute (confirmed-docs)
- Data only from 2024-01-29 onward (confirmed-docs)
- Returns empty string for accounts without finance data (confirmed)
- Sandbox response is a SUBSET of production fields — newer fields (cashback, loyalty, delivery_method, kiz, seller_promo) absent in sandbox
- `storage_fee`, `deduction`, `acceptance` fields present in docs but absent from sandbox response
- `rid` field present in sandbox but absent from official docs (ignored)
- **Timestamp format discrepancy**: `rr_dt`, `date_from`, `date_to`, `create_dt` — sandbox returns ISO 8601 datetime, official docs show date-only. Parser must handle both (DD-9)
- `site_country` format discrepancy: sandbox returns ISO code (`"RU"`), official docs show text (`"Россия"`)
- **sale_dt nullability**: Always filled in sandbox (incl. logistics entries). Unknown for storage/penalty entries. Fallback: `rr_dt` (DD-17)
- **v5 new fields confirmed in sandbox (2026-03-31)**: `is_legal_entity`, `trbx_id`, `installment_cofinancing_amount`, `wibes_wb_discount_percent`, `dlv_prc`, `fix_tariff_date_from/to`

### Rate Limits

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Rate limit | 1 request per 1 minute per seller account | confirmed-docs |
| Burst | 1 request | confirmed-docs |

---

## Summary: Contract Readiness per Capability

| Capability | Readiness | Rationale |
|------------|-----------|-----------|
| CATALOG | **READY** | All fields sandbox-verified: nmID, vendorCode, brand, title, sizes, dimensions, timestamps |
| PRICES | **READY** | All fields sandbox-verified: per-size pricing, discountedPrice, clubDiscountedPrice, currency explicit |
| STOCKS | PARTIAL | No warehouses in sandbox; endpoint confirmed but field names unverified with data |
| ORDERS | **READY** | Sandbox-verified: totalPrice, discountPercent, priceWithDisc, isCancel, cancelDate, srid |
| SALES | **READY** | Sandbox-verified: forPay, finishedPrice, priceWithDisc, saleID prefix, spp |
| RETURNS | **READY** | Unblocked: date-only format required; response schema confirmed from official docs; no return data in test account |
| FINANCES | **READY** | Sandbox-verified + official docs: sign convention confirmed (all positive), delivery_rub=20 in sandbox |

### Empirical Verification Status

| What | Status |
|------|--------|
| Endpoint accessibility | 6/7 verified (5 sandbox + returns via production), 1 partial (stocks) |
| Field-level data verification | **6/7 verified** (catalog, prices, orders, sales, finance + returns schema from official docs) |
| Sign convention verification | **CONFIRMED** empirically in sandbox (delivery_rub=20, positive for debit) |
| Official docs cross-check | **DONE** — official sample compared field-by-field with sandbox response |
| Sandbox URLs | content-api-sandbox, discounts-prices-api-sandbox, marketplace-api-sandbox, statistics-api-sandbox |
| Missing sandbox | No analytics-api-sandbox (returns, goods-return endpoint) |

### Sandbox vs Official Docs Discrepancies

| Area | Sandbox | Official Docs | Impact |
|------|---------|---------------|--------|
| `rr_dt` format | ISO 8601 datetime | date-only string | **Parser must handle both** |
| `date_from/to/create_dt` format | ISO 8601 datetime | date-only string | **Parser must handle both** |
| `site_country` | ISO code ("RU") | Text ("Россия") | Minor, use as opaque string |
| New fields | Absent (15+ fields) | Present | Sandbox generates legacy data |
| `storage_fee`, `deduction`, `acceptance` | Absent from response | Present in sample | Sandbox simplified; parse as optional |
| `rid` field | Present (value 0) | Absent | Sandbox-only, ignore |

### Mapping Blockers

1. ~~Returns~~ — **RESOLVED**: endpoint works with date-only format `YYYY-MM-DD`; previous 400 was caused by datetime format
2. ~~Finance sign convention~~ — **RESOLVED**: confirmed empirically via sandbox + official docs
3. ~~Price markup~~ — **PARTIALLY RESOLVED**: `price` → `discountedPrice` → `clubDiscountedPrice` hierarchy confirmed; WB internal markup between `finishedPrice` and `forPay` still opaque
4. **Stock field names**: No warehouses in sandbox; endpoint accessible but empty
5. **⚠️ Finance timestamp format**: `rr_dt` can be date-only OR full ISO 8601 — adapter MUST parse both

### Required Actions

1. ~~Field-level verification~~ — **DONE** for 5/7 capabilities via sandbox
2. ~~Finance sign convention~~ — **DONE** (sandbox + official docs)
3. **Stocks**: Create warehouse in sandbox via test API, then verify stocks fields
4. ~~Returns~~ — **DONE**: endpoint accessible, response schema confirmed from official docs; date-only query format required
5. **Finance adapter**: Implement flexible date/datetime parser for `rr_dt`, `date_from`, `date_to`, `create_dt`
6. **Finance adapter**: Handle optional fields that may be absent (15 new v5 fields)

---

## 8. SUPPLY / INCOMES

### Endpoint

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | confirmed |
| Path | `/api/v1/supplier/incomes` | confirmed |
| Base URL | `https://statistics-api.wildberries.ru` | confirmed |
| Sandbox URL | `https://statistics-api-sandbox.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |
| Token category | Statistics | confirmed |
| Status | **DEPRECATED** — отключается June 2026 | confirmed-docs |

**SANDBOX VERIFIED** — 120 rows of incomes data with test data.

### Request Parameters

| Param | Type | Required | Format | Confidence |
|-------|------|----------|--------|------------|
| `dateFrom` | string | yes | RFC3339 datetime | confirmed |

### Response Structure — SANDBOX VERIFIED

```json
{
  "incomeId": 2031852,
  "number": "",
  "date": "2025-11-29T09:54:24Z",
  "lastChangeDate": "2025-11-29T12:54:24Z",
  "supplierArticle": "Ch-02r-1",
  "techSize": "38-51, 25 см",
  "barcode": "2034224082630",
  "quantity": 5,
  "totalPrice": 0,
  "dateClose": "2025-11-29T09:54:24Z",
  "warehouseName": "Краснодар 2",
  "nmId": 73508154,
  "status": "Принято"
}
```

### Identifier Semantics — SANDBOX VERIFIED

| Provider field | Type | Semantics | Confidence |
|----------------|------|-----------|------------|
| `incomeId` | long | Unique supply/income ID | confirmed |
| `nmId` | long | Product nomenclature ID | confirmed |
| `supplierArticle` | string | Seller article (vendorCode) | confirmed |
| `barcode` | string | Product barcode | confirmed |

### Amount & Quantity Fields — SANDBOX VERIFIED

| Field | Type | Semantics | Sample | Confidence |
|-------|------|-----------|--------|------------|
| `quantity` | int | Number of units in supply | 5 | confirmed |
| `totalPrice` | number | Total price of supply | 0 (test) | confirmed |

### Status Semantics — SANDBOX VERIFIED

| Field | Type | Semantics | Sample | Confidence |
|-------|------|-----------|--------|------------|
| `status` | string | Income status | "Принято" | confirmed |

### Warehouse Fields — SANDBOX VERIFIED

| Field | Type | Semantics | Sample | Confidence |
|-------|------|-----------|--------|------------|
| `warehouseName` | string | WB warehouse name | "Краснодар 2" | confirmed |

Unique warehouses in sandbox: Санкт-Петербург, Казань, Коледино, Екатеринбург, Краснодар 2.

### Timestamp Semantics — SANDBOX VERIFIED

| Field | Semantics | Format | Confidence |
|-------|-----------|--------|------------|
| `date` | Income date | ISO 8601 UTC | confirmed |
| `lastChangeDate` | Last change date | ISO 8601 UTC | confirmed |
| `dateClose` | Closing date | ISO 8601 UTC | confirmed |

### NormalizedSupplyItem Mapping

| WB field | Normalized field | Confidence | Notes |
|----------|------------------|------------|-------|
| `incomeId` | `externalSupplyId` | C | Unique supply ID |
| `supplierArticle` | `sellerSku` | C | Seller article |
| `nmId` | `marketplaceSku` | C | WB product ID |
| `barcode` | `barcode` | C | Product barcode |
| `quantity` | `quantity` | C | Units supplied |
| `totalPrice` | `totalPrice` | C | Supply total (0 in sandbox) |
| `warehouseName` | `warehouseName` | C | Destination warehouse |
| `status` | `status` | C | Supply status |
| `date` | `supplyDate` | C | Income date |
| `dateClose` | `closedDate` | C | Closing date |

### Known Limitations

- **DEPRECATED** — endpoint shuts down June 2026
- No replacement for FBO incomes after deprecation
- WB Marketplace Supplies API (`/api/v3/supplies`) covers FBS supplies only
- `totalPrice` = 0 in sandbox (test data), should have real values in production
- `number` field empty in sandbox

### Rate Limits

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Rate limit | 1 request per minute | confirmed-docs |

### Post-Deprecation Strategy

After June 2026 when `/api/v1/supplier/incomes` is disabled:
1. **FBS supplies**: use `/api/v3/supplies` (Marketplace API)
2. **FBO incomes**: no API replacement. Options:
   - Manual import via CSV/Excel upload in UI
   - Derive from inventory delta analysis (stock changes over time)
   - Request from WB if they provide a new endpoint

---

## 9. WB WAREHOUSES / OFFICES

### Endpoint — WB Offices (WB-owned warehouses)

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | confirmed |
| Path | `/api/v3/offices` | confirmed |
| Base URL | `https://marketplace-api.wildberries.ru` | confirmed |
| Sandbox URL | `https://marketplace-api-sandbox.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |
| Token category | Marketplace | confirmed |

**SANDBOX VERIFIED** — 108 offices (sandbox), 225 offices (production).

### Response Structure — VERIFIED (sandbox + production)

```json
{
  "id": 10236,
  "name": "Коледино-2",
  "address": "ул. Пример, 1, город, область",
  "city": "Коледино",
  "longitude": 37.92211,
  "latitude": 55.74251,
  "cargoType": 1,
  "deliveryType": 1,
  "selected": true
}
```

### Field Semantics — VERIFIED (confirmed 2026-03-31, production 225 offices)

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `id` | long | WB office/warehouse ID | confirmed |
| `name` | string | Office name (e.g. "Коледино-2", "Коледино Плюс") | confirmed |
| `address` | string | Physical address | confirmed |
| `city` | string | City name | confirmed |
| `federalDistrict` | string | Federal district name (e.g. "Сибирский федеральный округ") | confirmed |
| `longitude` | number | GPS longitude | confirmed |
| `latitude` | number | GPS latitude | confirmed |
| `cargoType` | int | Cargo type (1=mgt, 2=sgt, 3=kgt) | confirmed |
| `deliveryType` | int | Delivery type (1=fbs, 2=dbs, 3=dbw, 5=cc) | confirmed |
| `selected` | boolean | Whether selected by seller | confirmed |

### Join Keys for dim_warehouse

| Source | WB offices field | Confidence |
|--------|-----------------|------------|
| Finance report `office_name` | `name` (text match) | confirmed-docs |
| Finance report `ppvz_office_id` | `id` (exact match) | confirmed-docs |
| Sales/Orders `warehouseName` | `name` (text match) | confirmed |
| Incomes `warehouseName` | `name` (text match) | confirmed |

### Endpoint — Seller Warehouses (FBS)

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | confirmed |
| Path | `/api/v3/warehouses` | confirmed |
| Base URL | `https://marketplace-api.wildberries.ru` | confirmed |

Returns seller's own FBS warehouses. Empty for sellers without FBS setup.

### dim_warehouse Strategy

1. **Primary source**: `GET /api/v3/offices` → full list of WB warehouses (WAREHOUSE_DICT event)
2. **Enrichment**: seller warehouses from `/api/v3/warehouses` for FBS
3. **Join**: `ppvz_office_id` (finance) → `offices.id`; `office_name` (finance) → `offices.name`

### Known Limitations

- 225 offices in production, 108 in sandbox (includes international CC points)
- `cargoType` and `deliveryType` enums not fully documented
- `ppvz_office_id` in finance may = 0 for some operations (backfill from `office_name`)

### Rate Limits

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Rate limit | Not explicitly documented | unknown |

---

## Rate Limits Summary (updated 2026-03-31)

| # | Capability | Endpoint | Rate limit | Token category | Confidence |
|---|------------|----------|-----------|----------------|------------|
| 1 | Catalog | `/content/v2/get/cards/list` | Not documented | Content | unknown |
| 2 | Prices | `/api/v2/list/goods/filter` | Not documented | Prices | unknown |
| 3 | Stocks | `/api/analytics/v1/stocks-report/wb-warehouses` | 3 req/min, burst 1/20s | Analytics | confirmed-docs |
| 4 | Orders | `/api/v1/supplier/orders` | 1 req/min | Statistics | confirmed-docs |
| 5 | Sales | `/api/v1/supplier/sales` | 1 req/min | Statistics | confirmed-docs |
| 6 | Returns | `/api/v1/analytics/goods-return` | 1 req/min, burst 10 | Analytics | confirmed-docs |
| 7 | Finance | `/api/v5/supplier/reportDetailByPeriod` | 1 req/min | Statistics | confirmed-docs |
| 8 | Incomes | `/api/v1/supplier/incomes` | 1 req/min | Statistics | confirmed-docs |
| 9 | Offices | `/api/v3/offices` | Not documented | Marketplace | unknown |

### WB Token Type Policy (effective 2026-03-30)

С 30 марта 2026 WB ввёл дифференциацию rate limits по типу токена:

| Token type | Scope | Rate limits |
|------------|-------|-------------|
| **Personal** | Proprietary software on own servers | Standard (full) limits |
| **Service** | Cloud services from Business Solutions Catalog | Standard limits per service |
| **Basic** | Auxiliary, limited data access | **Reduced** limits |
| **Test** | Sandbox only | **Reduced** limits |

**Datapulse использует Personal token** → стандартные rate limits.
Service tokens имеют **независимые** лимиты per individual service.

**Рекомендация для реализации:**
- Для endpoints с unknown rate limit: adaptive rate limiting (exponential backoff при HTTP 429)
- Стартовый safe interval: 1 request per 10 seconds для Content/Prices
- Мониторинг: логировать 429 responses, автоматически увеличивать interval
