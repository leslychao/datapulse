# Ozon — Provider Read Contracts

**Статус:** empirically verified (brand via attributes API resolved)
**Источник:** [https://docs.ozon.ru/api/seller/](https://docs.ozon.ru/api/seller/)
**Верификация:** реальные API-запросы 2026-03-29 + v4/attributes verification (см. samples/empirical-verification-log.md)

Этот документ фиксирует read-контракты Ozon Seller API для каждой capability,
релевантной ingestion pipeline Datapulse.

Каждое семантическое свойство классифицировано:

- **confirmed** — проверено по официальной документации И подтверждено реальным API-ответом
- **confirmed-docs** — проверено по документации, но не получено в реальном ответе (аккаунт без соотв. данных)
- **assumed** — выведено из документации или ответа, но не подтверждено явно
- **unknown** — не удалось подтвердить, требует дополнительного исследования

---

## 1. CATALOG

### Endpoint


| Свойство  | Значение                        | Confidence |
| --------- | ------------------------------- | ---------- |
| Method    | `POST`                          | confirmed  |
| List path | `/v3/product/list`              | confirmed  |
| Info path | `/v3/product/info/list`         | confirmed  |
| Base URL  | `https://api-seller.ozon.ru`    | confirmed  |
| Auth      | `Client-Id` + `Api-Key` headers | confirmed  |


**BREAKING**: `/v2/product/list` и `/v2/product/info` возвращают **404**.
Необходимо использовать v3 endpoints.

### Pagination


| Свойство                | Значение                                          | Confidence |
| ----------------------- | ------------------------------------------------- | ---------- |
| `/v3/product/list`      | Cursor (`last_id` + `limit`), `total` in response | confirmed  |
| `last_id` format        | Base64-encoded cursor string                      | confirmed  |
| Terminal page           | `last_id` may **repeat** the value sent in the request (often with empty `items`); clients must stop when cursor is missing/empty or does not advance — **do not** infer end-of-list from small HTTP body size (risks truncating valid pages) | confirmed (2026-04-03; `OzonCursorPaging` + list adapters) |
| `/v3/product/info/list` | Batch by `product_id` list                        | confirmed  |
| Typical flow            | List IDs first, then fetch info in batches        | confirmed  |


### Identifier Semantics


| Provider field            | Semantics                                                 | Confidence |
| ------------------------- | --------------------------------------------------------- | ---------- |
| `product_id` / `id`       | Ozon internal product ID (long)                           | confirmed  |
| `offer_id`                | Seller's own article / SKU (string)                       | confirmed  |
| `sources[].sku`           | Ozon system-level SKU (per shipment type)                 | confirmed  |
| `barcodes[]`              | Product barcodes (array of strings, e.g. "OZN1595285688") | confirmed  |
| `description_category_id` | Ozon category ID (replaces deprecated `category_id`)      | confirmed  |
| `type_id`                 | Product type ID                                           | confirmed  |


### Join Key Semantics


| Связь             | Join key                                   | Confidence |
| ----------------- | ------------------------------------------ | ---------- |
| Catalog → Prices  | `product_id` / `offer_id`                  | confirmed  |
| Catalog → Stocks  | `product_id` / `offer_id`                  | confirmed  |
| Catalog → Orders  | `offer_id` / `sku` in posting products     | confirmed  |
| Catalog → Finance | `sku` in transaction items (NOT offer_id!) | confirmed  |


### Timestamp Semantics


| Field        | Semantics                                                                                                                                                                                                      | Confidence |
| ------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| `created_at` | Product creation time (ISO 8601 UTC with nanoseconds)                                                                                                                                                          | confirmed  |
| `updated_at` | Product last update time (ISO 8601 UTC with nanoseconds). **Correction (2026-03-31):** IS present in v3/product/info/list response (previously documented as absent). Example: `"2025-06-26T08:17:43.409484Z"` | confirmed  |


### Key Response Fields (v3/product/info/list)


| Field                     | Type     | Semantics                                           | Confidence |
| ------------------------- | -------- | --------------------------------------------------- | ---------- |
| `id`                      | long     | Ozon product ID (alias for `product_id`)            | confirmed  |
| `offer_id`                | string   | Seller SKU                                          | confirmed  |
| `name`                    | string   | Product name                                        | confirmed  |
| `barcodes`                | string[] | Product barcodes (array)                            | confirmed  |
| `description_category_id` | long     | Category ID (replaces `category_id`)                | confirmed  |
| `type_id`                 | long     | Product type ID                                     | confirmed  |
| `created_at`              | string   | Creation timestamp (ISO 8601 UTC)                   | confirmed  |
| `images`                  | string[] | Image URLs                                          | confirmed  |
| `currency_code`           | string   | Currency ("RUB")                                    | confirmed  |
| `price`                   | string   | Current price (STRING, e.g. "273.00")               | confirmed  |
| `old_price`               | string   | Previous price (STRING)                             | confirmed  |
| `min_price`               | string   | Minimum allowed price (STRING)                      | confirmed  |
| `is_archived`             | boolean  | Whether product is archived                         | confirmed  |
| `is_autoarchived`         | boolean  | Whether product was auto-archived                   | confirmed  |
| `sources[]`               | array    | SKU sources with shipment types                     | confirmed  |
| `commissions[]`           | array    | Commission rates per sale schema (FBO/FBS/RFBS/FBP) | confirmed  |
| `model_info`              | object   | Model info with `model_id` and `count`              | confirmed  |
| `volume_weight`           | number   | Volume weight                                       | confirmed  |
| `stocks`                  | object   | Summary stock info: `{ coming, present, reserved }` (NOT an array) | confirmed  |


**Поля, отсутствующие в v3 product/info (были в v2 доках):**

- `brand` — НЕТ в product/info/list. **НАЙДЕН через `POST /v4/product/info/attributes`**:
  - attribute_id = 85 (name = "Бренд", type = String, is_required = true)
  - path: `result[].attributes[?(@.id==85)].values[0].value`
  - пример: `"BOROFONE"` (confirmed 2026-03-29)
- `visible` — заменено на `is_archived` + `is_autoarchived`
- `status` (complex object) — заменено на `is_archived` / `is_autoarchived`
- `category_id` — заменено на `description_category_id`
- `vat` — НЕТ в product/info/list v3

### Data Freshness


| Свойство  | Значение                               | Confidence |
| --------- | -------------------------------------- | ---------- |
| Freshness | Near real-time (current product state) | assumed    |


### Category Tree API — VERIFIED (2026-03-30)

Для заполнения `dim_category` используется отдельный endpoint.


| Свойство     | Значение                        | Confidence |
| ------------ | ------------------------------- | ---------- |
| Method       | `POST`                          | confirmed  |
| Path         | `/v1/description-category/tree` | confirmed  |
| Request body | `{"language":"DEFAULT"}`        | confirmed  |


Ответ содержит вложенную иерархию:


| Field                     | Type    | Semantics                                                         | Confidence |
| ------------------------- | ------- | ----------------------------------------------------------------- | ---------- |
| `description_category_id` | long    | Category ID (matches `description_category_id` from product/info) | confirmed  |
| `category_name`           | string  | Category display name (UTF-8)                                     | confirmed  |
| `disabled`                | boolean | Whether category is disabled                                      | confirmed  |
| `children[]`              | array   | Nested subcategories (recursive)                                  | confirmed  |
| `children[].type_name`    | string  | Product type name (leaf level)                                    | confirmed  |
| `children[].type_id`      | long    | Product type ID (matches `type_id` from product/info)             | confirmed  |


Join: `product.description_category_id` → `tree.description_category_id`, `product.type_id` → `tree.type_id`

### Known Limitations

- `/v2/product/list` и `/v2/product/info` deprecated (404) — must use v3 (confirmed)
- `brand` не является полем v3/product/info; доступен через `POST /v4/product/info/attributes` (attribute_id=85) (confirmed)
- `updated_at` IS present in v3/product/info/list response (confirmed 2026-03-31, previously documented as absent)
- Prices in product/info are STRINGS (confirmed)

### Rate Limits


| Свойство   | Значение                               | Confidence |
| ---------- | -------------------------------------- | ---------- |
| Rate limit | Not explicitly documented per-endpoint | unknown    |


---

## 2. PRICES

### Endpoint


| Свойство              | Значение                                      | Confidence     |
| --------------------- | --------------------------------------------- | -------------- |
| Method                | `POST`                                        | confirmed      |
| Path                  | `/v5/product/info/prices`                     | confirmed      |
| Previous (deprecated) | `/v4/product/info/prices` (disabled Feb 2025) | confirmed-docs |
| Base URL              | `https://api-seller.ozon.ru`                  | confirmed      |
| Auth                  | `Client-Id` + `Api-Key` headers               | confirmed      |


### Pagination


| Свойство | Значение                                      | Confidence |
| -------- | --------------------------------------------- | ---------- |
| Type     | Filter-based (`filter.visibility` + `limit`)  | confirmed  |
| Cursor   | Response likely contains cursor for next page | assumed    |


### Identifier Semantics


| Provider field | Semantics                 | Confidence |
| -------------- | ------------------------- | ---------- |
| `product_id`   | Ozon product ID (long)    | confirmed  |
| `offer_id`     | Seller's article (string) | confirmed  |


### Response Structure

**IMPORTANT**: Prices are inside a NESTED `price` object, not at top level.


| Field               | Type   | Semantics                                 | Confidence |
| ------------------- | ------ | ----------------------------------------- | ---------- |
| `acquiring`         | number | Acquiring fee amount                      | confirmed  |
| `offer_id`          | string | Seller article                            | confirmed  |
| `product_id`        | long   | Ozon product ID                           | confirmed  |
| `volume_weight`     | number | Volume weight                             | confirmed  |
| `commissions`       | object | Detailed commission breakdown per FBO/FBS | confirmed  |
| `marketing_actions` | object | Active marketing actions                  | confirmed  |
| `price`             | object | **Nested price object** (see below)       | confirmed  |
| `price_indexes`     | object | Price competitiveness indexes             | confirmed  |


### Price Object Fields (inside `price`)


| Field                                   | Type    | Semantics                                   | Confidence |
| --------------------------------------- | ------- | ------------------------------------------- | ---------- |
| `price`                                 | number  | Current selling price (NUMBER, not string!) | confirmed  |
| `old_price`                             | number  | Crossed-out price                           | confirmed  |
| `min_price`                             | number  | Seller's minimum price                      | confirmed  |
| `marketing_seller_price`                | number  | Seller price in marketing action            | confirmed  |
| `currency_code`                         | string  | Currency ("RUB")                            | confirmed  |
| `vat`                                   | number  | VAT rate                                    | confirmed  |
| `auto_action_enabled`                   | boolean | Auto-pricing flag                           | confirmed  |
| `retail_price`                          | number  | Retail price                                | confirmed  |
| `net_price`                             | number  | Net price                                   | confirmed  |
| `auto_add_to_ozon_actions_list_enabled` | boolean | Auto-add to actions flag                    | confirmed  |


### Commission Fields (inside `commissions`)


| Field                          | Type   | Semantics                     | Confidence |
| ------------------------------ | ------ | ----------------------------- | ---------- |
| `sales_percent_fbo`            | number | FBO commission %              | confirmed  |
| `sales_percent_fbs`            | number | FBS commission %              | confirmed  |
| `sales_percent_rfbs`           | number | RFBS commission %             | confirmed  |
| `sales_percent_fbp`            | number | FBP commission %              | confirmed  |
| `fbo_deliv_to_customer_amount` | number | FBO delivery cost to customer | confirmed  |
| `fbo_direct_flow_trans_`*      | number | FBO logistics costs (min/max) | confirmed  |
| `fbo_return_flow_amount`       | number | FBO return logistics cost     | confirmed  |
| `fbs_*`                        | number | Analogous FBS costs           | confirmed  |


### Amount Field Semantics


| Свойство | Значение                           | Confidence |
| -------- | ---------------------------------- | ---------- |
| Currency | Explicit via `price.currency_code` | confirmed  |
| Unit     | Rubles (number, not string!)       | confirmed  |
| Sign     | Positive only                      | confirmed  |


**Поля, НЕ найденные в реальном v5 ответе:**

- `premium_price` — not present
- `recommended_price` — not present
- `min_ozon_price` — not present

### Timestamp Semantics

No timestamps in price response. (confirmed)

### Data Freshness


| Свойство  | Значение                        | Confidence |
| --------- | ------------------------------- | ---------- |
| Freshness | Near real-time (current prices) | assumed    |


### Known Limitations

- v4 endpoint disabled since Feb 2025 (confirmed-docs)
- Price fields in v5 are NUMBERS (unlike product/info where they are strings) (confirmed)
- Multiple price concepts — design decision needed for "canonical price" (confirmed)

### Rate Limits


| Свойство   | Значение                  | Confidence |
| ---------- | ------------------------- | ---------- |
| Rate limit | Not explicitly documented | unknown    |


---

## 3. STOCKS

### Endpoint


| Свойство | Значение                        | Confidence |
| -------- | ------------------------------- | ---------- |
| Method   | `POST`                          | confirmed  |
| Path     | `/v4/product/info/stocks`       | confirmed  |
| Base URL | `https://api-seller.ozon.ru`    | confirmed  |
| Auth     | `Client-Id` + `Api-Key` headers | confirmed  |


### Pagination


| Свойство      | Значение                                  | Confidence |
| ------------- | ----------------------------------------- | ---------- |
| Type          | Cursor-based (`cursor` field in response) | confirmed  |
| Cursor format | Base64-encoded string                     | confirmed  |
| `total`       | Total count in response                   | confirmed  |
| Limit         | `limit` parameter in filter               | confirmed  |


### Identifier Semantics


| Provider field | Semantics                                | Confidence |
| -------------- | ---------------------------------------- | ---------- |
| `product_id`   | Ozon product ID (long)                   | confirmed  |
| `offer_id`     | Seller's article (string)                | confirmed  |
| `stocks[].sku` | Ozon system SKU (long, FBO/FBS specific) | confirmed  |


### Quantity Fields


| Field                    | Type   | Semantics                                        | Confidence |
| ------------------------ | ------ | ------------------------------------------------ | ---------- |
| `stocks[].present`       | int    | Currently in warehouse                           | confirmed  |
| `stocks[].reserved`      | int    | Reserved for orders                              | confirmed  |
| `stocks[].type`          | string | Warehouse type ("fbo" / "fbs")                   | confirmed  |
| `stocks[].shipment_type` | string | Package type enum (e.g. "SHIPMENT_TYPE_GENERAL") | confirmed  |
| `stocks[].warehouse_ids` | long[] | Warehouse IDs (array, may be empty)              | confirmed  |


### Warehouse Semantics


| Свойство                  | Значение                                                    | Confidence |
| ------------------------- | ----------------------------------------------------------- | ---------- |
| `warehouse_ids`           | IS present as array field (was documented as absent)        | confirmed  |
| `warehouse_ids` emptiness | Often empty in practice                                     | confirmed  |
| FBO vs FBS distinction    | Via `type` field ("fbo" / "fbs")                            | confirmed  |
| Individual warehouse      | Via `warehouse_ids[]` when populated, or via `type` + `sku` | confirmed  |


**UPDATE**: Previous documentation stated `warehouse_id` was not present in v4 response.
Empirical verification confirms `warehouse_ids` IS present as an array field,
but it may be empty. Warehouse granularity falls back to `type` (FBO/FBS) when IDs are empty.

### Timestamp Semantics


| Field  | Semantics                        | Confidence |
| ------ | -------------------------------- | ---------- |
| (none) | No timestamps in stocks response | confirmed  |


### Data Freshness


| Свойство  | Значение                | Confidence |
| --------- | ----------------------- | ---------- |
| Freshness | Near real-time snapshot | assumed    |


### Known Limitations

- `warehouse_ids` exists but often empty — fallback to `type` needed (confirmed)
- Different FBO/FBS SKUs for same product (confirmed)
- No stock update timestamps in response (confirmed)
- Multiple stock entries per product (one per type + shipment_type combo) (confirmed)

### Rate Limits


| Свойство   | Значение                           | Confidence |
| ---------- | ---------------------------------- | ---------- |
| Rate limit | Not explicitly documented for read | unknown    |


---

## 4. ORDERS (Postings)

### Endpoint


| Свойство | Значение                        | Confidence     |
| -------- | ------------------------------- | -------------- |
| Method   | `POST`                          | confirmed      |
| Path FBO | `/v2/posting/fbo/list`          | confirmed      |
| Path FBS | `/v3/posting/fbs/list`          | confirmed-docs |
| Base URL | `https://api-seller.ozon.ru`    | confirmed      |
| Auth     | `Client-Id` + `Api-Key` headers | confirmed      |


FBS endpoint **VERIFIED** (2026-03-31) — same structure as FBO with additional FBS-specific fields.
FBO endpoint fully verified.

> **Date range limit:** `PERIOD_IS_TOO_LONG` error if date range exceeds ~3 months. Use 1-month windows.

### Pagination


| Свойство      | Значение                                           | Confidence |
| ------------- | -------------------------------------------------- | ---------- |
| Type          | Offset-based (`offset` + `limit`)                  | confirmed  |
| FBO filter    | `since`, `to` (date range, ISO 8601 UTC), `dir`    | confirmed  |
| `with` params | `analytics_data`, `financial_data` (boolean flags) | confirmed  |
| Loop guard    | Observed (2026-04-03): successive pages may return **byte-identical** JSON while `offset` increases — treat as end of pagination (same pattern as non-advancing `last_id` on cursor endpoints) | confirmed |
| Datapulse safety cap | Ingest adapters stop after **5000** offset pages per run (`OzonOffsetPaging.MAX_OFFSET_PAGES_PER_RUN`) in addition to duplicate-body and small-response heuristics — avoids unbounded capture if API misbehaves | assumed (client policy) |


### Identifier Semantics


| Provider field        | Type   | Semantics                      | Confidence |
| --------------------- | ------ | ------------------------------ | ---------- |
| `order_id`            | long   | Parent order ID                | confirmed  |
| `order_number`        | string | Human-readable order number    | confirmed  |
| `posting_number`      | string | Unique posting/shipment number | confirmed  |
| `products[].sku`      | long   | Ozon system product SKU        | confirmed  |
| `products[].offer_id` | string | Seller's article               | confirmed  |


### Amount Fields


| Field                              | Type    | Semantics                       | Confidence |
| ---------------------------------- | ------- | ------------------------------- | ---------- |
| `products[].price`                 | string  | Product price (STRING "103.00") | confirmed  |
| `products[].currency_code`         | string  | Currency ("RUB")                | confirmed  |
| `products[].quantity`              | int     | Quantity in this posting        | confirmed  |
| `products[].is_marketplace_buyout` | boolean | Whether marketplace buyout      | confirmed  |


### Financial Data (inside `financial_data.products[]`)


| Field                    | Type     | Semantics                           | Confidence |
| ------------------------ | -------- | ----------------------------------- | ---------- |
| `commission_amount`      | number   | Commission amount (NUMBER)          | confirmed  |
| `commission_percent`     | number   | Commission percentage               | confirmed  |
| `payout`                 | number   | Seller payout amount                | confirmed  |
| `product_id`             | long     | Product SKU (not product_id!)       | confirmed  |
| `old_price`              | number   | Original price (NUMBER)             | confirmed  |
| `price`                  | number   | Current price (NUMBER, not string!) | confirmed  |
| `total_discount_value`   | number   | Total discount amount               | confirmed  |
| `total_discount_percent` | number   | Total discount percentage           | confirmed  |
| `actions`                | string[] | Applied promotions                  | confirmed  |
| `currency_code`          | string   | Currency                            | confirmed  |
| `cluster_from`           | string   | Source cluster                      | confirmed  |
| `cluster_to`             | string   | Destination cluster                 | confirmed  |


### Status Semantics


| Status               | Semantics                                  | Confidence     |
| -------------------- | ------------------------------------------ | -------------- |
| `awaiting_packaging` | Waiting for seller to pack                 | confirmed      |
| `delivering`         | In transit                                 | confirmed      |
| `delivered`          | Delivered to customer                      | confirmed      |
| `cancelled`          | Cancelled                                  | confirmed      |
| `substatus`          | Sub-status detail (e.g. "posting_received", "posting_canceled") | confirmed |


### FBS-specific Fields (verified 2026-03-31)

| Field | Type | Semantics | Confidence |
| ----- | ---- | --------- | ---------- |
| `delivery_method.id` | long | Delivery method ID | confirmed |
| `delivery_method.name` | string | Delivery method name | confirmed |
| `delivery_method.warehouse_id` | long | Source warehouse ID | confirmed |
| `delivery_method.warehouse` | string | Warehouse name | confirmed |
| `delivery_method.tpl_provider_id` | long | TPL provider ID | confirmed |
| `delivery_method.tpl_provider` | string | TPL provider name (e.g. "Доставка Ozon") | confirmed |
| `tracking_number` | string | Tracking number (may be empty) | confirmed |
| `tpl_integration_type` | string | Integration type (e.g. "ozon") | confirmed |
| `shipment_date` | string | Shipment date (ISO 8601 UTC) | confirmed |
| `delivering_date` | string | Delivery date (ISO 8601 UTC, null for cancelled) | confirmed |
| `cancellation.cancel_reason_id` | int | Cancellation reason ID (0 = not cancelled, 402 = seller cancel) | confirmed |
| `cancellation.cancel_reason` | string | Cancellation reason text | confirmed |
| `cancellation.cancellation_type` | string | Who cancelled ("seller", etc.) | confirmed |
| `cancellation.cancelled_after_ship` | boolean | Whether cancelled after shipment | confirmed |
| `cancellation.affect_cancellation_rating` | boolean | Impacts seller rating | confirmed |
| `financial_data.products[].customer_price` | number | **Buyer-paid price** (different from seller price!) | confirmed |
| `financial_data.products[].customer_currency_code` | string | Customer currency code | confirmed |

**CRITICAL (FBS customer_price):** FBS postings include `customer_price` which can be SIGNIFICANTLY lower than seller `price`.
Example: `price: 293`, `customer_price: 105.71` → Ozon absorbs 187.29 RUB as marketing subsidy.
This confirms DD-11: `accruals_for_sale` = seller-facing price, NOT buyer-paid price.

**FBS date range limit:** `PERIOD_IS_TOO_LONG` error if date range exceeds ~3 months. Use 1-month windows.

### Analytics Data (inside `analytics_data`)


| Field                     | Type    | Semantics                     | Confidence |
| ------------------------- | ------- | ----------------------------- | ---------- |
| `city`                    | string  | Customer city                 | confirmed  |
| `delivery_type`           | string  | Delivery method ("PVZ", etc.) | confirmed  |
| `is_premium`              | boolean | Premium delivery flag         | confirmed  |
| `payment_type_group_name` | string  | Payment method name           | confirmed  |
| `warehouse_id`            | long    | Source warehouse ID (LONG!)   | confirmed  |
| `warehouse_name`          | string  | Warehouse name                | confirmed  |
| `is_legal`                | boolean | Legal entity flag             | confirmed  |


### Timestamp Semantics


| Field           | Format                     | Semantics             | Confidence |
| --------------- | -------------------------- | --------------------- | ---------- |
| `created_at`    | ISO 8601 UTC (nanoseconds) | Posting creation time | confirmed  |
| `in_process_at` | ISO 8601 UTC (nanoseconds) | Processing start time | confirmed  |
| Timezone        | UTC                        | All timestamps in UTC | confirmed  |


### Data Freshness


| Свойство  | Значение                        | Confidence |
| --------- | ------------------------------- | ---------- |
| Freshness | Near real-time for new postings | confirmed  |


### Known Limitations

- FBS and FBO are separate endpoints (confirmed)
- Must combine both for full order picture (confirmed)
- `financial_data` available even for non-delivered postings (confirmed — verified for awaiting_packaging)
- `products[].price` is STRING, but `financial_data.products[].price` is NUMBER (confirmed)
- `cancel_reason_id` present (0 = not cancelled, 402 = seller cancel) (confirmed)
- **FBS verified (2026-03-31):** Same core structure as FBO; additional fields: `delivery_method`, `tracking_number`, `shipment_date`, `delivering_date`, `cancellation` (detailed), `customer_price`
- **FBS customer_price:** Present in FBS financial_data (NOT in FBO). Buyer pays less than seller price when Ozon marketing applies.
- **FBS date range limit:** `PERIOD_IS_TOO_LONG` for >3 months. v2/posting/fbs/list returns 404 (deprecated).

### Rate Limits


| Свойство   | Значение                               | Confidence |
| ---------- | -------------------------------------- | ---------- |
| Rate limit | Not explicitly documented per-endpoint | unknown    |


---

## 5. SALES

### Endpoint

Ozon does not have a dedicated "sales" endpoint. Sales data is derived from:


| Source               | Path                                                                    | Confidence |
| -------------------- | ----------------------------------------------------------------------- | ---------- |
| Postings (delivered) | `/v2/posting/fbo/list` + `/v3/posting/fbs/list` with `status=delivered` | confirmed  |
| Finance transactions | `/v3/finance/transaction/list` with `OperationAgentDeliveredToCustomer` | confirmed  |


### Semantics


| Свойство                 | Значение                                                   | Confidence |
| ------------------------ | ---------------------------------------------------------- | ---------- |
| Sale = delivered posting | A sale is a posting that reached `delivered` status        | confirmed  |
| Sale financial data      | In `financial_data` of posting AND in finance transactions | confirmed  |
| No separate sale ID      | Use `posting_number` as sale identifier                    | confirmed  |
| Finance sale operation   | `OperationAgentDeliveredToCustomer` operation_type         | confirmed  |


### Amount Fields (via posting financial_data)


| Field                                          | Type   | Semantics              | Confidence |
| ---------------------------------------------- | ------ | ---------------------- | ---------- |
| `products[].price`                             | string | Unit price (string)    | confirmed  |
| `financial_data.products[].commission_amount`  | number | Commission per item    | confirmed  |
| `financial_data.products[].commission_percent` | number | Commission %           | confirmed  |
| `financial_data.products[].payout`             | number | Seller payout per item | confirmed  |
| `financial_data.products[].currency_code`      | string | Currency               | confirmed  |


### Amount Fields (via finance transactions)


| Field               | Type   | Semantics                                  | Confidence |
| ------------------- | ------ | ------------------------------------------ | ---------- |
| `accruals_for_sale` | number | Gross sale revenue (POSITIVE)              | confirmed  |
| `sale_commission`   | number | Commission (NEGATIVE)                      | confirmed  |
| `amount`            | number | Net amount after all deductions (POSITIVE) | confirmed  |
| `services[].price`  | number | Logistics/service costs (NEGATIVE)         | confirmed  |


### Data Freshness


| Свойство       | Значение                               | Confidence |
| -------------- | -------------------------------------- | ---------- |
| Posting status | Near real-time                         | assumed    |
| Financial data | Finance transactions appear with delay | confirmed  |


### Known Limitations

- No dedicated sales endpoint — composite capability (confirmed)
- Financial details per item may differ between posting and finance endpoints (confirmed)
- Commission breakdown varies between FBO and FBS (assumed)

---

## 6. RETURNS

### Endpoint


| Свойство              | Значение                                                                 | Confidence     |
| --------------------- | ------------------------------------------------------------------------ | -------------- |
| Method                | `POST`                                                                   | confirmed      |
| Path                  | `/v1/returns/list`                                                       | confirmed      |
| Previous (deprecated) | `/v3/returns/company/fbs`, `/v3/returns/company/fbo` (disabled Feb 2025) | confirmed-docs |
| Base URL              | `https://api-seller.ozon.ru`                                             | confirmed      |
| Auth                  | `Client-Id` + `Api-Key` headers                                          | confirmed      |


### Pagination


| Свойство  | Значение                                                                                          | Confidence |
| --------- | ------------------------------------------------------------------------------------------------- | ---------- |
| Type      | Cursor-based (`last_id` + `limit`, max 500)                                                       | confirmed  |
| Filter    | One of: `logistic_return_date`, `storage_tariffication_start_date`, `visual_status_change_moment` | confirmed  |
| Note      | Only one time-based filter per request; `last_free_waiting_day` is NOT valid for v1                | confirmed  |


### Response Structure


| Field       | Type  | Semantics               | Confidence |
| ----------- | ----- | ----------------------- | ---------- |
| `returns[]` | array | Array of return objects | confirmed  |


### Identifier Semantics


| Provider field     | Type   | Semantics                                   | Confidence |
| ------------------ | ------ | ------------------------------------------- | ---------- |
| `id`               | long   | Unique return identifier (NOT `return_id`!) | confirmed  |
| `company_id`       | long   | Seller company ID                           | confirmed  |
| `order_id`         | long   | Original order ID                           | confirmed  |
| `order_number`     | string | Order number                                | confirmed  |
| `product.sku`      | long   | Ozon product SKU                            | confirmed  |
| `product.offer_id` | string | Seller's article                            | confirmed  |


### Status Semantics


| Field                        | Type   | Semantics                                       | Confidence |
| ---------------------------- | ------ | ----------------------------------------------- | ---------- |
| `visual.status.sys_name`     | string | Machine-readable status (e.g. "ReturnedToOzon") | confirmed  |
| `visual.status.display_name` | string | Human-readable status                           | confirmed  |
| `visual.status.id`           | int    | Status ID                                       | confirmed  |
| `return_reason_name`         | string | Human-readable return reason                    | confirmed  |
| `type`                       | string | Return type (e.g. "Cancellation")               | confirmed  |
| `schema`                     | string | Fulfillment schema ("Fbo" / "Fbs")              | confirmed  |


### Amount Fields — VERIFIED


| Field                              | Type   | Semantics                                                | Confidence |
| ---------------------------------- | ------ | -------------------------------------------------------- | ---------- |
| `product.price`                    | object | Price with `currency_code` (string) and `price` (number) | confirmed  |
| `product.price_without_commission` | object | Price without commission (same structure)                | confirmed  |
| `product.commission_percent`       | number | Commission percentage                                    | confirmed  |
| `product.commission`               | object | Commission with `currency_code` and `price`              | confirmed  |
| `product.quantity`                 | int    | Return quantity                                          | confirmed  |


**UPDATE**: Previous documentation assumed financial amounts were NOT in returns endpoint.
Empirical verification confirms `product.price`, `product.price_without_commission`,
`product.commission_percent`, and `product.commission` ARE all present.

### Storage Fields


| Field                     | Type   | Semantics                           | Confidence |
| ------------------------- | ------ | ----------------------------------- | ---------- |
| `storage.sum`             | object | Storage cost {currency_code, price} | confirmed  |
| `storage.days`            | int    | Storage days                        | confirmed  |
| `storage.utilization_sum` | object | Utilization cost                    | confirmed  |


### Place Fields


| Field          | Semantics                            | Confidence |
| -------------- | ------------------------------------ | ---------- |
| `place`        | Current location {id, name, address} | confirmed  |
| `target_place` | Destination {id, name, address}      | confirmed  |


### Timestamp Semantics


| Field                                         | Format                  | Semantics                        | Confidence |
| --------------------------------------------- | ----------------------- | -------------------------------- | ---------- |
| `logistic.return_date`                        | ISO 8601 UTC            | Return initiation date           | confirmed  |
| `logistic.final_moment`                       | ISO 8601 UTC            | Final processing moment          | confirmed  |
| `logistic.technical_return_moment`            | ISO 8601 UTC (nullable) | Technical return moment          | confirmed  |
| `logistic.cancelled_with_compensation_moment` | ISO 8601 UTC (nullable) | Compensation cancellation moment | confirmed  |
| `visual.change_moment`                        | ISO 8601 UTC            | Last status change               | confirmed  |


### Data Freshness


| Свойство  | Значение                                               | Confidence |
| --------- | ------------------------------------------------------ | ---------- |
| Freshness | Historical data available (verified returns from 2024) | confirmed  |


### Known Limitations

- Old v3 endpoints deprecated (confirmed)
- Return identifier is `id`, not `return_id` (confirmed)
- Financial data IS present in returns response (corrects previous assumption)
- `product.commission` may have empty `currency_code` when commission is 0 (confirmed)
- `storage.sum.currency_code` may be empty when no storage charges (confirmed)

### Rate Limits


| Свойство   | Значение                  | Confidence |
| ---------- | ------------------------- | ---------- |
| Rate limit | Not explicitly documented | unknown    |


---

## 7. FINANCES

### Endpoint


| Свойство | Значение                        | Confidence |
| -------- | ------------------------------- | ---------- |
| Method   | `POST`                          | confirmed  |
| Path     | `/v3/finance/transaction/list`  | confirmed  |
| Base URL | `https://api-seller.ozon.ru`    | confirmed  |
| Auth     | `Client-Id` + `Api-Key` headers | confirmed  |


### Pagination


| Свойство      | Значение                              | Confidence |
| ------------- | ------------------------------------- | ---------- |
| Type          | Page-based (`page` + `page_size`)     | confirmed  |
| Response      | `page_count`, `row_count` in `result` | confirmed  |
| Max page size | At least 100 works                    | confirmed  |


### Filter Parameters


| Parameter                 | Semantics                                   | Confidence     |
| ------------------------- | ------------------------------------------- | -------------- |
| `filter.date.from`        | Start date (ISO 8601)                       | confirmed      |
| `filter.date.to`          | End date (ISO 8601)                         | confirmed      |
| `filter.operation_type`   | Filter by operation type (array)            | confirmed      |
| `filter.posting_number`   | Filter by specific posting                  | confirmed-docs |
| `filter.transaction_type` | Transaction type filter ("all", etc.)       | confirmed      |
| Max period                | **1 month** per request (error if exceeded) | confirmed      |


### Identifier Semantics


| Provider field            | Type   | Semantics                                     | Confidence |
| ------------------------- | ------ | --------------------------------------------- | ---------- |
| `operation_id`            | long   | Unique operation/transaction ID               | confirmed  |
| `posting.posting_number`  | string | Associated posting number                     | confirmed  |
| `posting.warehouse_id`    | long   | Source warehouse (0 for non-order operations) | confirmed  |
| `posting.delivery_schema` | string | Delivery schema ("FBO", empty for non-order)  | confirmed  |
| `items[].sku`             | long   | Product SKU                                   | confirmed  |
| `items[].name`            | string | Product name                                  | confirmed  |


**NOTE**: `items[]` does NOT contain `offer_id`. Only `sku` and `name`.
Join to catalog requires `sku` → `product_id` → `offer_id` lookup.

### Operation Type Semantics — VERIFIED (updated 2026-03-31, cross-verified Jan 2025 + Feb 2026)

**Core operations (order-linked):**


| `operation_type`                          | `type` (category) | Semantics                                     | Count (Jan 2025) | Count (Feb 2026) | Confidence |
| ----------------------------------------- | ----------------- | --------------------------------------------- | ---------------- | ---------------- | ---------- |
| `OperationAgentDeliveredToCustomer`       | orders            | Sale delivery to customer                     | 1854             | 182              | confirmed  |
| `ClientReturnAgentOperation`              | returns           | Customer return (reverses sale: accruals < 0) | 27               | 1                | confirmed  |
| `OperationAgentStornoDeliveredToCustomer` | returns           | Storno/correction of delivery                 | 26               | —                | confirmed  |
| `OperationItemReturn`                     | returns           | Item return logistics cost                    | 163              | 28               | confirmed  |
| `OperationReturnGoodsFBSofRMS`            | returns           | FBS return processing with logistics services | 1                | 16               | confirmed  |


**Fee operations (order-linked, separate from sale):**


| `operation_type`                                | `type` (category) | Semantics                                       | Count (Jan 2025) | Count (Feb 2026) | Confidence |
| ----------------------------------------------- | ----------------- | ----------------------------------------------- | ---------------- | ---------------- | ---------- |
| `MarketplaceRedistributionOfAcquiringOperation` | other             | Acquiring fee redistribution                    | 1648             | 190              | confirmed  |
| `MarketplaceServiceBrandCommission`             | services          | Brand commission (separate operation per sale!) | 1847             | 105              | confirmed  |
| `StarsMembership`                               | services          | Stars loyalty program fee (per sale)            | 1854             | **0** (absent)   | confirmed  |
| `MarketplaceServiceItemCrossdocking`            | services          | Crossdocking logistics service                  | 2                | —                | confirmed  |


**Standalone operations (no order link):**


| `operation_type`                         | `type` (category) | Semantics                                | Count (Jan 2025) | Count (Feb 2026) | Confidence |
| ---------------------------------------- | ----------------- | ---------------------------------------- | ---------------- | ---------------- | ---------- |
| `OperationElectronicServiceStencil`      | services          | Electronic packaging/stencil service     | 134              | —                | confirmed  |
| `OperationMarketplaceServiceStorage`     | services          | Storage service                          | 22               | 29               | confirmed  |
| `MarketplaceSaleReviewsOperation`        | services          | Purchased reviews for promotion          | 5                | —                | confirmed  |
| `DisposalReasonFailedToPickupOnTime`     | services          | Disposal: not picked up in time          | 3                | —                | confirmed  |
| `DisposalReasonDamagedPackaging`         | services          | Disposal: damaged packaging              | 1                | —                | confirmed  |
| `AccrualInternalClaim`                   | compensation      | Ozon internal claim compensation         | 1                | —                | confirmed  |
| `AccrualWithoutDocs`                     | compensation      | Accrual without documents (compensation) | 1                | —                | confirmed  |
| `MarketplaceSellerCompensationOperation` | compensation      | Marketplace seller compensation          | 1                | —                | confirmed  |


**New operations (discovered Feb 2026):**


| `operation_type`                     | `type` (category) | Semantics                            | Count (Feb 2026) | Amount sample | Confidence |
| ------------------------------------ | ----------------- | ------------------------------------ | ---------------- | ------------- | ---------- |
| `OperationMarketplaceCostPerClick`   | services          | CPC advertising charge               | 23               | -3.1          | confirmed  |
| `OperationPromotionWithCostPerOrder` | services          | Promotion cost-per-order             | 7                | -49.9         | confirmed  |
| `DefectRateCancellation`             | services          | Defect rate penalty (posting-linked) | 4                | -150          | confirmed  |
| `OperationPointsForReviews`          | services          | Points for reviews (standalone)      | 2                | -585.6        | confirmed  |
| `DefectFineShipmentDelayRated`       | services          | Shipment delay fine (posting-linked) | 1                | -50           | confirmed  |
| `DefectFineCancellation`             | services          | Cancellation fine (posting-linked)   | 1                | -150          | confirmed  |


**New operations (discovered Apr 2026):**


| `operation_type`                                            | `type` (category) | Semantics                                     | Confidence |
| ----------------------------------------------------------- | ----------------- | --------------------------------------------- | ---------- |
| `OperationGettingToTheTop`                                  | services          | Promotion "getting to the top" charge         | confirmed  |
| `OperationMarketplaceServiceSupplyInboundCargoShortage`     | services          | Supply inbound cargo shortage penalty         | confirmed  |
| `DefectRateDetailed`                                        | services          | Detailed defect rate penalty                  | confirmed  |
| `TemporaryStorage`                                          | services          | Temporary warehouse storage                   | confirmed  |

**New service names (discovered Apr 2026):**

| `services[].name`                                           | Measure column  | Semantics                     | Confidence |
| ----------------------------------------------------------- | --------------- | ----------------------------- | ---------- |
| `MarketplaceServiceItemRedistributionLastMilePVZ`           | LOGISTICS       | Last mile redistribution PVZ  | confirmed  |


**27 operation types verified from real data (Jan 2025 + Feb 2026 + Apr 2026).**

**Additional 21 types from official Ozon OpenAPI enum (C-docs, not yet observed in our data):**

| `operation_type` | `type` (category) | Semantics | Source |
|--|--|--|--|
| `OperationAgentDeliveredToCustomerCanceled` | returns | Delivery cancellation accrual | Official enum |
| `OperationClaim` | compensation | Claim accrual | Official enum |
| `OperationCorrectionSeller` | other | Mutual settlement | Official enum |
| `OperationDefectiveWriteOff` | compensation | Warehouse damaged goods compensation | Official enum |
| `OperationLackWriteOff` | compensation | Warehouse lost goods compensation | Official enum |
| `OperationSetOff` | other | Offset with counterparties | Official enum |
| `OperationMarketplaceCrossDockServiceWriteOff` | services | Cross-dock delivery service | Official enum |
| `ReturnAgentOperationRFBS` | returns | rFBS delivery return transfer | Official enum |
| `MarketplaceSellerReexposureDeliveryReturnOperation` | transferDelivery | Buyer delivery transfer | Official enum |
| `MarketplaceSellerShippingCompensationReturnOperation` | transferDelivery | Shipping fee compensation transfer | Official enum |
| `MarketplaceMarketingActionCostOperation` | services | Product promotion services | Official enum |
| `OperationMarketplaceServicePremiumCashback` | services | Premium promotion service | Official enum |
| `MarketplaceServicePremiumPromotion` | services | Premium promotion fixed commission | Official enum |
| `MarketplaceServicePremiumCashbackIndividualPoints` | services | Seller bonus promotion | Official enum |
| `OperationSubscriptionPremium` | services | Premium subscription | Official enum |
| `MarketplaceReturnStorageServiceAtThePickupPointFbsItem` | services | FBS short-term return storage at PVZ | Official enum |
| `MarketplaceReturnStorageServiceInTheWarehouseFbsItem` | services | FBS long-term return storage in warehouse | Official enum |
| `MarketplaceServiceItemDeliveryKGT` | services | Oversized item logistics | Official enum |
| `OperationMarketplaceWithHoldingForUndeliverableGoods` | services | Delivery hold for undeliverable items | Official enum |
| `OperationElectronicServicesPromotionInSearch` | services | Search promotion service | Official enum |
| `OperationMarketplaceServiceItemElectronicServicesBrandShelf` | services | Brand shelf service | Official enum |

**Total: 48 operation types known (27 empirical + 21 from official enum).** Mapping to FinanceEntryType and fact_finance measures — see mapping-spec.md §7.

**NOTE:** 11 of our 23 empirical types are NOT in the official enum (added after Sept 2024 enum update). This confirms Ozon adds types without updating the public enum. Adapter MUST default unmapped types to OTHER with logging.

**Observation (Feb 2026):** `StarsMembership` completely absent (was 24% of ops in Jan 2025). Possible Ozon program change. `OperationPointsForReviews` may replace `MarketplaceSaleReviewsOperation`. Adapter MUST handle dynamic operation type evolution.

**New types characteristics:**

- `OperationMarketplaceCostPerClick`: `posting_number` = campaign/ad ID (numeric, e.g. "20460416"), NOT posting format. `items=[]`, `services=[]`, `warehouse_id=0`.
- `OperationPromotionWithCostPerOrder`: `posting_number` = promotion ID (numeric). Same empty structure.
- `DefectRateCancellation/DefectFineShipmentDelayRated/DefectFineCancellation`: `posting_number` = posting format (e.g. "0116722727-0104-3"), `warehouse_id` populated. Posting-linked defect/delay fines.
- `OperationPointsForReviews`: standalone (`posting_number=""`). Points-based review costs.

**CRITICAL: One posting → multiple operations.** A single delivery (posting "87621408-0010-1")
generates 3 separate operations: `OperationAgentDeliveredToCustomer` (sale), `MarketplaceServiceBrandCommission` (-0.79),
`StarsMembership` (-0.79). All share the same `posting.posting_number`. P&L aggregation must join ALL
operations by `posting_number`.

**NOTE**: Standalone operations (storage, disposal, reviews, compensation, CPC, promotions) have `posting_number = ""`
or numeric campaign/promo ID (NOT posting format), `warehouse_id = 0`. These are period-level charges, not order-level.
Sorting key uses `operation_id` (unique per operation).

**CRITICAL (DD-15 updated)**: Acquiring operations use **DUAL FORMAT** for `posting_number`:

- 57% use 2-part order_number format (e.g. "93284743-0263")
- 43% use 3-part full posting_number format (e.g. "39222582-0174-1")
Join strategy: exact match first, then strip-suffix match. See mapping-spec.md DD-15 for details.

### Amount Fields — VERIFIED


| Field                    | Type   | Semantics                                                          | Confidence |
| ------------------------ | ------ | ------------------------------------------------------------------ | ---------- |
| `amount`                 | number | Net transaction amount                                             | confirmed  |
| `accruals_for_sale`      | number | Gross sale revenue (positive for sales, negative for returns)      | confirmed  |
| `sale_commission`        | number | Sales commission (negative for sales, positive for return refunds) | confirmed  |
| `delivery_charge`        | number | Delivery cost (always 0 in observed data, costs in services)       | confirmed  |
| `return_delivery_charge` | number | Return delivery cost (always 0 in observed data)                   | confirmed  |
| `services[].name`        | string | Service type machine-readable name                                 | confirmed  |
| `services[].price`       | number | Service cost (negative = cost to seller)                           | confirmed  |


### Sign Convention — CONFIRMED


| Scenario                                 | `accruals_for_sale` | `sale_commission` | `services[].price` | `amount`           |
| ---------------------------------------- | ------------------- | ----------------- | ------------------ | ------------------ |
| Sale (OperationAgentDeliveredToCustomer) | POSITIVE (157)      | NEGATIVE (-35.95) | NEGATIVE (-71.45)  | POSITIVE (49.60)   |
| Return (ClientReturnAgentOperation)      | NEGATIVE (-211)     | POSITIVE (48.32)  | 0                  | NEGATIVE (-162.68) |
| Cost operation                           | 0                   | 0                 | NEGATIVE           | NEGATIVE           |


**Formula**: `amount` = `accruals_for_sale` + `sale_commission` + Σ(`services[].price`)
**Convention**: Positive = credit to seller, Negative = debit from seller

**Verified math**:

- Sale: 157 + (-35.95) + (-8.45) + (-63) = 49.60 ✓
- Return: -211 + 48.32 + 0 = -162.68 ✓

### Service Types Observed — COMPLETE (2026-03-30, real data)

**Logistics services (forward):**


| Service name                                      | Semantics                         | Count (Jan) | Total (Jan, RUB) | Confidence |
| ------------------------------------------------- | --------------------------------- | ----------- | ---------------- | ---------- |
| `MarketplaceServiceItemDirectFlowLogistic`        | Warehouse-to-sortcenter logistics | 1917        | -119,908         | confirmed  |
| `MarketplaceServiceItemDelivToCustomer`           | Last-mile delivery to customer    | 1838        | -14,557          | confirmed  |
| `MarketplaceServiceItemDropoffPVZ`                | Dropoff at PVZ                    | 7           | -105             | confirmed  |
| `MarketplaceServiceItemRedistributionDropOffApvz` | Redistribution via dropoff        | 7           | -105             | confirmed  |


**Logistics services (return flow):**


| Service name                                       | Semantics                          | Count (Jan) | Total (Jan, RUB) | Confidence |
| -------------------------------------------------- | ---------------------------------- | ----------- | ---------------- | ---------- |
| `MarketplaceServiceItemReturnFlowLogistic`         | Return logistics (main)            | 100         | -6,146           | confirmed  |
| `MarketplaceServiceItemReturnNotDelivToCustomer`   | Return: not delivered (no charge)  | 64          | 0                | confirmed  |
| `MarketplaceServiceItemReturnAfterDelivToCustomer` | Return: after delivery (no charge) | 36          | 0                | confirmed  |
| `MarketplaceServiceItemRedistributionReturnsPVZ`   | Return redistribution via PVZ      | 60          | -900             | confirmed  |


**Fee services:**


| Service name                                    | Semantics                                  | Count (Jan) | Total (Jan, RUB) | Confidence |
| ----------------------------------------------- | ------------------------------------------ | ----------- | ---------------- | ---------- |
| `MarketplaceRedistributionOfAcquiringOperation` | Acquiring fee (as service in operation)    | 1648        | -2,864           | confirmed  |
| `MarketplaceServiceBrandCommission`             | Brand commission (as service in operation) | 1847        | -1,398           | confirmed  |
| `ItemAgentServiceStarsMembership`               | Stars membership (as service in operation) | 1854        | -1,466           | confirmed  |


**Disposal services:**


| Service name                             | Semantics       | Count (Jan) | Total (Jan, RUB) | Confidence |
| ---------------------------------------- | --------------- | ----------- | ---------------- | ---------- |
| `MarketplaceServiceItemDisposalDetailed` | Disposal charge | 4           | -300             | confirmed  |


**Additional services (observed Apr 2026):**


| Service name                                              | Semantics                                      | Confidence |
| --------------------------------------------------------- | ---------------------------------------------- | ---------- |
| `MarketplaceServiceItemRedistributionLastMileCourier`     | Last-mile courier redistribution (logistics)   | confirmed  |


**Additional operation types (observed Apr 2026):**


| `operation_type`            | Semantics                   | Measure column | Confidence |
| --------------------------- | --------------------------- | -------------- | ---------- |
| `InsuranceServiceSellerItem`| Seller insurance service fee | OTHER          | confirmed  |


**Key insight**: Forward logistics dominates costs (-119,908 RUB from `DirectFlowLogistic` vs -14,557 from `DelivToCustomer`). Return services are mostly zero-cost except `ReturnFlowLogistic` (-6,146) and `RedistributionReturnsPVZ` (-900).

### Currency and Units


| Свойство | Значение                                          | Confidence |
| -------- | ------------------------------------------------- | ---------- |
| Currency | RUB (no explicit `currency_code` per transaction) | confirmed  |
| Unit     | Rubles with kopecks (decimal numbers)             | confirmed  |


### Timestamp Semantics


| Field                | Format                                                                                                                                                                                         | Semantics                | Confidence |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------ | ---------- |
| `operation_date`     | "YYYY-MM-DD HH:MM:SS" (NOT ISO 8601!)                                                                                                                                                          | Financial operation date | confirmed  |
| `posting.order_date` | "YYYY-MM-DD HH:MM:SS" (NOT ISO 8601!)                                                                                                                                                          | Original order date      | confirmed  |
| Timezone             | **Moscow (UTC+3)** — empirically confirmed 2026-03-31 (cross-reference `posting.created_at` UTC vs `finance.posting.order_date`: constant +3h offset, 7 data points; see mapping-spec.md DD-6) | confirmed                |            |


**CRITICAL**: Finance timestamps use custom format "YYYY-MM-DD HH:MM:SS",
NOT ISO 8601. Timezone is Moscow (UTC+3). Parser must handle this format explicitly
and apply `ZoneOffset.ofHours(3)` to produce `OffsetDateTime`.

### Data Freshness


| Свойство       | Значение                                           | Confidence     |
| -------------- | -------------------------------------------------- | -------------- |
| Freshness      | Aligns with "Accruals" section in personal account | confirmed-docs |
| Delay          | Financial operations appear with processing delay  | confirmed      |
| Current period | May not have all operations until period is closed | assumed        |


### Known Limitations

- Max 1-month query window (confirmed by error message)
- Financial data appears with processing delay (confirmed)
- No explicit currency field per transaction — RUB confirmed by context (confirmed)
- `items[]` has `sku` + `name` only — no `offer_id` (confirmed)
- `delivery_charge` and `return_delivery_charge` always 0 in observed data; actual costs are in `services[]` (confirmed)
- **23 operation types** observed across 2 months (Jan 2025 + Feb 2026); new types appear over time — adapter MUST handle unmapped types
- Finance timestamps use custom format "YYYY-MM-DD HH:MM:SS", not ISO 8601 (confirmed)
- One posting generates multiple finance operations (sale + brand_commission + stars); all share same `posting_number` (confirmed)
- Standalone charges (storage, disposal, reviews, compensation, CPC, promotions) have empty `posting_number` or numeric campaign/promo ID (NOT posting format) — need `operation_id` as key (confirmed)
- `StarsMembership` completely absent from Feb 2026 data (was 24% of ops in Jan 2025) — possible Ozon program change (confirmed)
- `filter.posting_number` parameter confirmed working — allows fetching all operations for a specific posting (confirmed)
- SKU lookup chain verified (2026-03-31): `items[].sku` → `catalog sources[].sku` → `product_id` → `offer_id` (confirmed)

### Rate Limits


| Свойство   | Значение                  | Confidence |
| ---------- | ------------------------- | ---------- |
| Rate limit | Not explicitly documented | unknown    |


---

## Rate Limits Summary (2026-03-31)

Ozon Seller API **не документирует** per-endpoint rate limits в открытой документации.

| # | Capability | Endpoint | Rate limit | Confidence |
|---|------------|----------|-----------|------------|
| 1 | Catalog | `/v3/product/list`, `/v3/product/info/list` | unknown | unknown |
| 2 | Prices | `/v5/product/info/prices` | unknown | unknown |
| 3 | Stocks | `/v4/product/info/stocks` | unknown | unknown |
| 4 | Orders FBO | `/v2/posting/fbo/list` | unknown | unknown |
| 5 | Orders FBS | `/v3/posting/fbs/list` | unknown | unknown |
| 6 | Returns | `/v1/returns/list` | unknown | unknown |
| 7 | Finance | `/v3/finance/transaction/list` | unknown | unknown |
| 8 | Categories | `/v1/description-category/tree` | unknown | unknown |
| 9 | Attributes | `/v4/product/info/attributes` | unknown | unknown |

**Что известно из сообщества (Habr, форумы, 2025-2026):**
- Ozon применяет rate limits, но конкретные значения не публикует
- Типичное поведение при превышении: HTTP 429 Too Many Requests
- Некоторые продавцы сообщают о лимитах ~60 req/min для data endpoints
- Seller API product manager (Habr) подтвердил наличие лимитов без disclosure конкретных цифр

**Реализация rate limiting:** см. [Integration §Rate limiting](../modules/integration.md#rate-limiting). Стартовый conservative rate для `OZON_DEFAULT`: 30 req/min.

---

## Summary: Contract Readiness per Capability


| Capability | Readiness         | Rationale                                                                                                                                   |
| ---------- | ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| CATALOG    | **READY**         | v3 endpoint verified; `brand` available via v4/attributes (id=85); `updated_at` IS present (confirmed 2026-03-31)                           |
| PRICES     | READY             | v5 endpoint fully verified; prices as numbers; currency explicit                                                                            |
| STOCKS     | READY             | v4 endpoint verified; `warehouse_ids` exists (may be empty); `present`/`reserved` confirmed                                                 |
| ORDERS     | **READY**         | FBO verified; **FBS verified (2026-03-31)** — same structure + customer_price, cancellation, delivery_method fields |
| SALES      | READY (composite) | Composite: delivered postings + finance `OperationAgentDeliveredToCustomer`; design confirmed                                               |
| RETURNS    | READY             | v1 endpoint verified; financial amounts PRESENT (price, commission); dates confirmed                                                        |
| FINANCES   | READY             | v3 endpoint verified; sign convention CONFIRMED; **23 operation types mapped** (Jan 2025 + Feb 2026); DD-15 acquiring dual format confirmed |


### Resolved Blockers

1. ~~Finance sign convention~~ — **CONFIRMED**: positive = credit, negative = debit
2. ~~Finance currency~~ — **CONFIRMED**: RUB (verified from context and amounts)
3. ~~Stocks warehouse_id~~ — **PARTIALLY RESOLVED**: `warehouse_ids[]` exists but often empty; `analytics_data.warehouse_id` in postings is populated
4. ~~Multiple price concepts~~ — **RESOLVED by design**: `price.price` is canonical, `price.marketing_seller_price` for marketing
5. ~~FBO vs FBS merge~~ — **DESIGN DECISION**: separate ingestion, unified via `posting_number`
6. ~~Returns financial data~~ — **CONFIRMED PRESENT** in returns endpoint

### Remaining Gaps

1. ~~Brand~~ — **RESOLVED**: available via `POST /v4/product/info/attributes` (attribute_id=85, "Бренд", required field)
2. ~~FBS~~ — **RESOLVED (2026-03-31)**: FBS endpoint verified. Same core contract as FBO, additional fields: `delivery_method`, `customer_price`, `cancellation`, `shipment_date`. Date range limit: ~3 months.
3. **Rate limits** — not documented for any endpoint
4. ~~Finance timezone~~ — **RESOLVED**: Moscow (UTC+3), empirically confirmed 2026-03-31 (7 data points, constant +3h offset)
5. ~~Acquiring join~~ — **RESOLVED (DD-15)**: acquiring uses `order_number` format (without -N suffix), can be joined to posting
6. ~~Storage attribution~~ — **RESOLVED (DD-16)**: storage is daily aggregate, no per-order attribution; pro-rata allocation needed
7. ~~Categories API~~ — **RESOLVED**: `POST /v1/description-category/tree` returns full hierarchy with `description_category_id` + `category_name`

