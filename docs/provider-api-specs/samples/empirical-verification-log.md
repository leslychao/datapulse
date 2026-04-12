# Empirical Verification Log

**Дата:** 2026-03-29
**Метод:** Прямые API-запросы к WB и Ozon через PowerShell Invoke-RestMethod

---

## WB — Результаты запросов

### WB CATALOG (POST /content/v2/get/cards/list)
- **Статус:** 200 OK
- **Данные:** Пустой ответ (аккаунт без товаров)
- **Подтвержденная структура:**
```json
{
  "cards": [],
  "cursor": { "nmID": 0, "total": 0 }
}
```

### WB PRICES (GET /api/v2/list/goods/filter?limit=3&offset=0)
- **Статус:** 200 OK
- **Данные:** Пустой ответ
- **Подтвержденная структура:**
```json
{
  "data": { "listGoods": [] },
  "error": false,
  "errorText": ""
}
```

### WB STOCKS (POST /api/analytics/v1/stocks-report/wb-warehouses)
- **Статус:** 200 OK, пустая строка
- Старый endpoint /api/v1/analytics/stocks-report → 404

### WB ORDERS (GET /api/v1/supplier/orders?dateFrom=2024-01-01)
- **Статус:** 200 OK, пустой ответ (null)

### WB SALES (GET /api/v1/supplier/sales?dateFrom=2024-01-01)
- **Статус:** 200 OK, пустой ответ (null)

### WB RETURNS (GET /api/v1/analytics/goods-return)
- **Статус:** 400 Bad Request

### WB FINANCE (GET /api/v5/supplier/reportDetailByPeriod?dateFrom=2024-06-01&dateTo=2024-07-01)
- **Статус:** 200 OK, пустая строка

### WB Sandbox Token
- Все statistics endpoints → 401 Unauthorized (sandbox token `acc:2` не имеет доступа к statistics API)

**Вывод по WB:** Аккаунт пустой (нет товаров, продаж, финансов). Подтверждены response wrappers для catalog и prices. Полевая верификация невозможна без данных.

---

## Ozon — Результаты запросов

### Ozon CATALOG

#### v2/product/list → 404 (DEPRECATED!)
#### v3/product/list → 200 OK
```json
{
  "result": {
    "items": [
      {
        "product_id": 1074782997,
        "offer_id": "588889995_плетёный",
        "has_fbo_stocks": true,
        "has_fbs_stocks": false,
        "archived": false,
        "is_discounted": false,
        "quants": [
          { "quant_code": "588889995_плетёный_kvant9", "quant_size": 9 }
        ]
      }
    ],
    "total": 337,
    "last_id": "WzEwNzQ5MTQ4MzgsMTA3NDkxNDgzOF0="
  }
}
```

#### v2/product/info → 404 (DEPRECATED!)
#### v3/product/info/list → 200 OK
```json
{
  "items": [
    {
      "id": 1074782997,
      "name": "...",
      "offer_id": "588889995_плетёный",
      "is_archived": false,
      "is_autoarchived": false,
      "barcodes": ["OZN1595285688"],
      "description_category_id": 17028634,
      "type_id": 971081965,
      "created_at": "2024-06-04T09:35:44.934722Z",
      "images": ["https://cdn1.ozone.ru/..."],
      "currency_code": "RUB",
      "min_price": "273.00",
      "old_price": "399.00",
      "price": "273.00",
      "sources": [
        {
          "sku": 1595285688,
          "source": "sds",
          "created_at": "2024-06-04T09:40:40.362951Z",
          "shipment_type": "SHIPMENT_TYPE_GENERAL",
          "quant_code": ""
        }
      ],
      "model_info": { "model_id": 387017114, "count": 5 },
      "commissions": [
        { "delivery_amount": 25, "percent": 19.1, "return_amount": 19.32, "sale_schema": "FBO", "value": 52.14 },
        { "delivery_amount": 25, "percent": 19.1, "return_amount": 19.32, "sale_schema": "FBS", "value": 52.14 }
      ],
      "is_prepayment_allowed": true,
      "volume_weight": 0.1,
      "stocks": { "has_stock": true, "stocks": [{ "present": 20, "reserved": 0, "sku": 1595285688 }] }
    }
  ]
}
```
**Не обнаруженные поля:** `brand`, `visible`, `status` (object), `category_id`, `vat`, `updated_at`
**Новые/изменённые поля:** `is_archived`, `is_autoarchived`, `barcodes[]` (array!), `description_category_id`, `type_id`, `sources[]`, `model_info`, `commissions[]`

### Ozon PRICES (POST /v5/product/info/prices) → 200 OK
```json
{
  "items": [
    {
      "acquiring": 2.73,
      "offer_id": "588889995_плетёный",
      "product_id": 1074782997,
      "volume_weight": 0.1,
      "commissions": {
        "fbo_deliv_to_customer_amount": 25,
        "fbo_direct_flow_trans_max_amount": 45.7,
        "fbo_direct_flow_trans_min_amount": 19.32,
        "fbo_return_flow_amount": 19.32,
        "fbs_deliv_to_customer_amount": 25,
        "fbs_direct_flow_trans_min_amount": 19.32,
        "fbs_direct_flow_trans_max_amount": 19.32,
        "fbs_first_mile_max_amount": 20,
        "fbs_first_mile_min_amount": 10,
        "fbs_return_flow_amount": 19.32,
        "sales_percent_fbo": 19.1,
        "sales_percent_fbs": 19.1,
        "sales_percent_rfbs": 44.1,
        "sales_percent_fbp": 44.1
      },
      "marketing_actions": { "actions": [...], "ozon_actions_exist": false },
      "price": {
        "auto_action_enabled": false,
        "currency_code": "RUB",
        "marketing_seller_price": 273,
        "min_price": 273,
        "old_price": 399,
        "price": 273,
        "retail_price": 0,
        "vat": 0,
        "auto_add_to_ozon_actions_list_enabled": true,
        "net_price": 0
      },
      "price_indexes": { "external_index_data": { "min_price": 191, "min_price_currency": "RUB" } }
    }
  ]
}
```
**Ключевые находки:**
- `price` — это ВЛОЖЕННЫЙ ОБЪЕКТ, а не поле верхнего уровня
- Поля цен внутри `price` — ЧИСЛА (не строки как в product/info!)
- `currency_code` внутри `price` объекта
- Нет `premium_price`, `recommended_price`, `min_ozon_price` в реальном ответе (были в доках)

### Ozon STOCKS (POST /v4/product/info/stocks) → 200 OK
```json
{
  "items": [
    {
      "product_id": 1074782997,
      "offer_id": "588889995_плетёный",
      "stocks": [
        { "type": "fbo", "present": 20, "reserved": 0, "sku": 1595285688, "shipment_type": "SHIPMENT_TYPE_GENERAL", "warehouse_ids": [] },
        { "type": "fbs", "present": 0, "reserved": 0, "sku": 1595285688, "shipment_type": "SHIPMENT_TYPE_GENERAL", "warehouse_ids": [] }
      ]
    }
  ],
  "total": 337,
  "cursor": "WzEwNzQ5MTQ4MzgsMTA3NDkxNDgzOF0="
}
```
**Ключевые находки:**
- `warehouse_ids` ПРИСУТСТВУЕТ как массив (может быть пустым)
- Pagination: cursor-based (base64 encoded), + `total`

### Ozon ORDERS FBO (POST /v2/posting/fbo/list) → 200 OK
```json
{
  "result": [
    {
      "order_id": 34821854683,
      "order_number": "50852427-0132",
      "posting_number": "50852427-0132-1",
      "status": "awaiting_packaging",
      "cancel_reason_id": 0,
      "created_at": "2026-03-28T19:11:19.066032Z",
      "in_process_at": "2026-03-28T19:11:29.240343Z",
      "products": [
        {
          "sku": 1595500037,
          "name": "...",
          "quantity": 1,
          "offer_id": "588889999_серебряный",
          "price": "103.00",
          "digital_codes": [],
          "currency_code": "RUB",
          "is_marketplace_buyout": false
        }
      ],
      "analytics_data": {
        "city": "...",
        "delivery_type": "PVZ",
        "is_premium": false,
        "payment_type_group_name": "Ozon Карта",
        "warehouse_id": 1020000115166000,
        "warehouse_name": "...",
        "is_legal": false,
        "client_delivery_date_begin": null,
        "client_delivery_date_end": null
      },
      "financial_data": {
        "products": [
          {
            "commission_amount": 0,
            "commission_percent": 0,
            "payout": 0,
            "product_id": 1595500037,
            "old_price": 104,
            "price": 103,
            "total_discount_value": 1,
            "total_discount_percent": 1,
            "actions": ["..."],
            "currency_code": "RUB"
          }
        ],
        "cluster_from": "...",
        "cluster_to": "..."
      },
      "additional_data": [],
      "legal_info": { "company_name": "", "inn": "", "kpp": "" },
      "substatus": "posting_packing"
    }
  ]
}
```
**Ключевые находки:**
- FBO ответ — `result[]` (массив напрямую)
- `analytics_data.warehouse_id` — LONG (есть!)
- `products[].price` — STRING "103.00"
- `financial_data.products[].price` — NUMBER 103 (другой тип!)
- `financial_data` доступна для статусов до delivered

### Ozon FBS (POST /v3/posting/fbs/list) → 400 Bad Request
- Аккаунт не имеет FBS настройки, запрос не принимается

### Ozon RETURNS (POST /v1/returns/list) → 200 OK
```json
{
  "returns": [
    {
      "id": 1000016684,
      "company_id": 1943980,
      "return_reason_name": "...",
      "type": "Cancellation",
      "schema": "Fbo",
      "order_id": 24223824654,
      "order_number": "43495982-0103",
      "place": { "id": 1020000115449000, "name": "...", "address": "..." },
      "target_place": { "id": 16583460360000, "name": "...", "address": "..." },
      "storage": {
        "sum": { "currency_code": "", "price": 0 },
        "tariffication_first_date": null,
        "tariffication_start_date": null,
        "arrived_moment": null,
        "days": 0,
        "utilization_sum": { "currency_code": "", "price": 0 },
        "utilization_forecast_date": null
      },
      "product": {
        "sku": 1613210328,
        "offer_id": "588890001_Earphone_BT_FaceChange",
        "name": "...",
        "price": { "currency_code": "RUB", "price": 1140 },
        "price_without_commission": { "currency_code": "RUB", "price": 1140 },
        "commission_percent": 0,
        "commission": { "currency_code": "", "price": 0 },
        "quantity": 1
      },
      "logistic": {
        "technical_return_moment": null,
        "final_moment": "2024-07-14T22:06:27.340Z",
        "cancelled_with_compensation_moment": null,
        "return_date": "2024-07-13T01:47:09.440Z",
        "barcode": "%101%26819284654"
      },
      "visual": {
        "status": { "id": 34, "display_name": "На складе Ozon", "sys_name": "ReturnedToOzon" },
        "change_moment": "2024-07-14T22:06:27.340Z"
      },
      "exemplars": [{ "id": 1019625469589708, "exemplar_id": 0 }]
    }
  ]
}
```
**Ключевые находки:**
- `id` — return identifier (NOT `return_id`)
- `product.price` — ЕСТЬ! {currency_code, price}
- `product.price_without_commission` — ЕСТЬ!
- `product.commission` — ЕСТЬ! {currency_code, price}
- `logistic.return_date` — ISO 8601 UTC, CONFIRMED
- `visual.status.sys_name` — machine-readable status

### Ozon FINANCE (POST /v3/finance/transaction/list) → 200 OK

#### Sale operation (OperationAgentDeliveredToCustomer):
```json
{
  "operation_id": 28589489037,
  "operation_type": "OperationAgentDeliveredToCustomer",
  "operation_date": "2025-01-02 00:00:00",
  "operation_type_name": "Доставка покупателю",
  "delivery_charge": 0,
  "return_delivery_charge": 0,
  "accruals_for_sale": 157,
  "sale_commission": -35.95,
  "amount": 49.6,
  "type": "orders",
  "posting": {
    "delivery_schema": "FBO",
    "order_date": "2024-12-22 16:02:55",
    "posting_number": "87621408-0010-1",
    "warehouse_id": 23128509046000
  },
  "items": [{ "name": "...", "sku": 1595500037 }],
  "services": [
    { "name": "MarketplaceServiceItemDelivToCustomer", "price": -8.45 },
    { "name": "MarketplaceServiceItemDirectFlowLogistic", "price": -63 }
  ]
}
```
**Верификация формулы:** 157 + (-35.95) + (-8.45) + (-63) = 49.60 ✓

#### Return operation (ClientReturnAgentOperation):
- `accruals_for_sale`: -211 (NEGATIVE = reversal)
- `sale_commission`: 48.32 (POSITIVE = refund of commission)
- `amount`: -162.68 (NEGATIVE = net debit)
- Формула: -211 + 48.32 = -162.68 ✓

#### Все обнаруженные operation_type (январь 2025):
| operation_type | count | sample_amount | type_cat |
|---|---|---|---|
| ClientReturnAgentOperation | 5 | -162.68 | returns |
| MarketplaceRedistributionOfAcquiringOperation | 405 | -0.88 | other |
| MarketplaceServiceBrandCommission | 349 | -0.79 | services |
| MarketplaceServiceItemCrossdocking | 1 | -300 | services |
| OperationAgentDeliveredToCustomer | 349 | 49.6 | orders |
| OperationAgentStornoDeliveredToCustomer | 4 | 9.4 | returns |
| OperationElectronicServiceStencil | 15 | -146.1 | services |
| OperationItemReturn | 23 | -63 | returns |
| OperationMarketplaceServiceStorage | 3 | -75.09 | services |
| StarsMembership | 346 | -0.79 | services |

#### ПОДТВЕРЖДЁННАЯ SIGN CONVENTION:
- **Positive** = credit to seller (revenue, cost refunds)
- **Negative** = debit from seller (costs, reversed revenue)
- `amount` = `accruals_for_sale` + `sale_commission` + Σ(`services[].price`)

#### ПОДТВЕРЖДЁННЫЙ TIMESTAMP FORMAT:
- `operation_date`: "YYYY-MM-DD HH:MM:SS" (NOT ISO 8601!)
- `posting.order_date`: "YYYY-MM-DD HH:MM:SS" (NOT ISO 8601!)

#### ПОДТВЕРЖДЁННЫЕ ОГРАНИЧЕНИЯ:
- Max 1 month per request (confirmed by error: "only one month allowed")
- Pagination: page-based (page + page_size), response has page_count + row_count
- `items[]` has `name` + `sku`, but NOT `offer_id`

---

## Session 2: 2026-03-29 (additional verification)

### OZON: Brand via Attributes API

**Endpoint:** `POST /v4/product/info/attributes`
**Request:** `{"filter":{"product_id":[1074782997],"visibility":"ALL"},"limit":1}`
**Result:** 200 OK

Brand found at `result[].attributes[]` where `id == 85`:
```json
{
  "id": 85,
  "complex_id": 0,
  "values": [{ "dictionary_value_id": 971163125, "value": "BOROFONE" }]
}
```

**Attribute metadata** (from `POST /v1/description-category/attribute`):
- id = 85, name = "Бренд", type = String, is_required = true

**Conclusion:** Ozon brand IS available via separate attributes endpoint. Requires secondary API call per product batch.

### OZON: FBS Endpoint

**Endpoint:** `POST /v3/posting/fbs/list`
**Result:** 400 Bad Request (all request formats tested)
**Endpoint v2:** `POST /v2/posting/fbs/list` — 404 Not Found

**Conclusion:** FBS endpoint returns 400 for FBO-only accounts. This is an account limitation, not a contract error. FBS contract follows the same structure as FBO per official docs.

### WB: Finance Sign Convention (from official docs)

**Source:** https://dev.wildberries.ru/openapi/financial-reports-and-accounting
**Endpoint:** `GET /api/v5/supplier/reportDetailByPeriod`

Official sample response confirms:
- ALL values are POSITIVE absolute amounts
- Field name determines credit/debit:
  - CREDIT: `ppvz_for_pay` (376.99), `ppvz_vw` (22.25), `additional_payment` (0)
  - DEBIT: `ppvz_sales_commission` (23.74), `acquiring_fee` (14.89), `penalty` (231.35), `storage_fee` (12647.29), `deduction` (6354), `acceptance` (865), `rebill_logistic_cost` (1.349)

Additional fields discovered from official sample:
- `acceptance` — acceptance fees
- `retail_price_withdisc_rub` — retail price with discount
- `installment_cofinancing_amount` — installment cofinancing
- `cashback_amount`, `cashback_discount`, `cashback_commission_change`
- `seller_promo_id`, `seller_promo_discount`
- `loyalty_id`, `loyalty_discount`
- `delivery_method` — e.g. "FBS, (МГТ)"
- `kiz` — product marking code
- `trbx_id` — transport box ID
- `order_uid` — order UID

Timestamps confirmed:
- `order_dt`: ISO 8601 UTC (e.g. "2022-10-13T00:00:00Z")
- `sale_dt`: ISO 8601 UTC (e.g. "2022-10-20T00:00:00Z")
- `rr_dt`: date-only (e.g. "2022-10-20")

### WB: Returns Endpoint

**Endpoint:** `GET /api/v1/analytics/goods-return`
**Result:** 400 Bad Request (GET with dateFrom/dateTo query params)
**POST attempt:** 405 Method Not Allowed (confirmed GET-only)

**Conclusion:** Likely requires Analytics-scoped token (current token is Statistics scope, `acc: 3`). Returns endpoint remains BLOCKED until correct token scope is used.

---

## Session 3: 2026-03-29 (WB Sandbox verification)

**Key discovery:** WB sandbox requires SANDBOX-specific URLs, NOT production URLs with sandbox token.

| API | Sandbox URL | Result |
|-----|------------|--------|
| Content | `https://content-api-sandbox.wildberries.ru` | 200 ✅ |
| Prices | `https://discounts-prices-api-sandbox.wildberries.ru` | 200 ✅ |
| Marketplace | `https://marketplace-api-sandbox.wildberries.ru` | 200 ✅ |
| Statistics | `https://statistics-api-sandbox.wildberries.ru` | 200 ✅ |
| Analytics | no sandbox URL exists | N/A |

### WB CATALOG (sandbox)

**Endpoint:** `POST https://content-api-sandbox.wildberries.ru/content/v2/get/cards/list`
**Result:** 200 OK — 1 test product card

Key verified fields:
- `nmID`: 274849 (number)
- `vendorCode`: string
- `brand`: string — **CONFIRMED present in catalog response**
- `title`, `description`: string
- `subjectID`: 105 (number), `subjectName`: string
- `dimensions`: `{length: 12, width: 7, height: 5, weightBrutto: 1.242, isValid: false}`
- `sizes[]`: `[{chrtID: 274848, techSize: "S", wbSize: "42", skus: ["88005553535"]}]`
- `characteristics[]`: `[{id: 14177449, name: "Цвет", value: ["Оранжевый"]}]`
- `createdAt`: "2025-12-16T17:40:20.827Z" (ISO 8601 UTC)
- `updatedAt`: "2025-12-16T17:40:20.827Z" (ISO 8601 UTC)
- `imtID`: 274847, `nmUUID`: "ff722b33-...", `needKiz`: false

### WB PRICES (sandbox)

**Endpoint:** `GET https://discounts-prices-api-sandbox.wildberries.ru/api/v2/list/goods/filter?limit=5`
**Result:** 200 OK

Key verified fields:
- `data.listGoods[0].nmID`: 274849
- `data.listGoods[0].vendorCode`: string
- `data.listGoods[0].sizes[]`: per-size pricing!
  - `sizeID`: 274848, `price`: 5000, `discountedPrice`: 5000, `clubDiscountedPrice`: 5000, `techSizeName`: "S"
- `currencyIsoCode4217`: "RUB" — currency explicit
- `discount`: 0, `clubDiscount`: 0, `editableSizePrice`: false
- `error`: false, `errorText`: ""

### WB ORDERS (sandbox, via statistics)

**Endpoint:** `GET https://statistics-api-sandbox.wildberries.ru/api/v1/supplier/orders?dateFrom=2025-12-01`
**Result:** 200 OK — multiple test orders

Key verified fields:
- `date`: "2025-12-01T07:51:35Z" (ISO 8601 UTC)
- `lastChangeDate`: "2025-12-01T12:54:35Z"
- `supplierArticle`, `barcode`, `nmId`: confirmed
- `totalPrice`: 2230, `discountPercent`: 60, `priceWithDisc`: 2230
- `spp`: 0, `finishedPrice`: 0
- `isCancel`: true/false, `cancelDate`: "2025-12-01T07:54:35Z" or "0001-01-01T00:00:00"
- `warehouseName`, `warehouseType`, `countryName`, `regionName`
- `isSupply`: false, `isRealization`: true
- `brand`: "wildberries", `category`, `subject`
- `gNumber`, `srid`, `sticker`

### WB SALES (sandbox, via statistics)

**Endpoint:** `GET https://statistics-api-sandbox.wildberries.ru/api/v1/supplier/sales?dateFrom=2025-12-01`
**Result:** 200 OK — multiple test sales

Key verified fields:
- `date`: "2025-11-30T18:41:20Z", `lastChangeDate`: "2025-12-01T12:54:20Z"
- `totalPrice`: 20736, `discountPercent`: 75, `spp`: 0
- `forPay`: 4182.96 — **amount to seller CONFIRMED**
- `finishedPrice`: 4183.96
- `priceWithDisc`: 4669
- `saleID`: "S3207347857" — **S prefix confirmed**
- `srid`: "27427813070712635.2.2"
- `gNumber`: "1374066348108264155"
- `incomeID`, `isSupply`, `isRealization`
- `warehouseName`, `warehouseType`
- `brand`, `category`, `subject`

### WB FINANCE (sandbox, via statistics)

**Endpoint:** `GET https://statistics-api-sandbox.wildberries.ru/api/v5/supplier/reportDetailByPeriod?dateFrom=2025-12-01&dateTo=2025-12-31`
**Result:** 200 OK — multiple test finance rows

Key verified fields:
- `supplier_oper_name`: "Логистика" — operation type confirmed
- `delivery_rub`: 20 — **POSITIVE for debit (logistics cost), confirming DD-7**
- `rebill_logistic_cost`: 1.349 — positive for debit
- `ppvz_sales_commission`: 0, `ppvz_for_pay`: 0 (test data has minimal values)
- `order_dt`: "2025-12-30T22:54:20Z" — ISO 8601 UTC
- `sale_dt`: "2025-12-30T22:54:20Z" — ISO 8601 UTC
- `rr_dt`: "2025-12-30T22:54:20Z" — full ISO 8601 (not date-only as in official sample)
- `srid`, `nm_id`, `brand_name`, `sa_name`, `barcode` — all present
- `installment_cofinancing_amount`: 0, `wibes_wb_discount_percent`: 0
- `assembly_id`, `is_legal_entity`, `trbx_id`, `site_country`, `srv_dbs` — all present

### WB STOCKS (sandbox)

**Endpoint:** `GET https://marketplace-api-sandbox.wildberries.ru/api/v3/warehouses`
**Result:** Empty array (no warehouses in sandbox)

**Stocks POST:** 400 Bad Request (no warehouses to query)

**Conclusion:** Need to create test warehouses via sandbox API first.

### SUMMARY: Sandbox Impact

| Capability | Before sandbox | After sandbox |
|------------|---------------|---------------|
| CATALOG | PARTIAL (docs) | **READY** (field-level verified) |
| PRICES | PARTIAL (docs) | **READY** (per-size hierarchy verified) |
| ORDERS | PARTIAL (docs) | **READY** (all fields verified) |
| SALES | PARTIAL (docs) | **READY** (forPay/finishedPrice verified) |
| FINANCES | PARTIAL (docs) | **READY** (sign convention empirically confirmed) |
| STOCKS | PARTIAL | PARTIAL (no warehouses in sandbox) |
| RETURNS | BLOCKED | BLOCKED (no analytics sandbox) |

---

## Session 4: WB Finance v5 cross-verification (2026-03-29)

### Goal

Пользователь предоставил полный official sample v5 endpoint
`GET /api/v5/supplier/reportDetailByPeriod`. Cross-check sandbox response
против official docs для выявления расхождений.

### Sandbox query

```
GET https://statistics-api-sandbox.wildberries.ru/api/v5/supplier/reportDetailByPeriod
  ?dateFrom=2025-11-01&dateTo=2025-12-31&limit=10&period=daily
```

### Key discrepancies found

#### 1. Timestamp format discrepancy

| Field | Official docs sample | Sandbox response |
|-------|---------------------|------------------|
| `rr_dt` | `"2022-10-20"` (date-only) | `"2025-12-30T22:54:20Z"` (ISO 8601) |
| `date_from` | `"2022-10-17"` (date-only) | `"2025-12-29T23:54:20Z"` (ISO 8601) |
| `date_to` | `"2022-10-23"` (date-only) | `"2025-12-30T22:54:20Z"` (ISO 8601) |
| `create_dt` | `"2022-10-24"` (date-only) | `"2025-12-30T22:54:20Z"` (ISO 8601) |
| `order_dt` | `"2022-10-13T00:00:00Z"` (ISO 8601) | `"2025-12-30T22:54:20Z"` (ISO 8601) |
| `sale_dt` | `"2022-10-20T00:00:00Z"` (ISO 8601) | `"2025-12-30T22:54:20Z"` (ISO 8601) |

**Impact:** Adapter MUST parse both date-only and full ISO 8601 datetime.
Official docs `dateFrom` input format says RFC3339 (accepts both date and datetime).
See DD-9 in mapping-spec.

#### 2. site_country format discrepancy

| Official docs | Sandbox |
|--------------|---------|
| `"Россия"` (text name) | `"RU"` (ISO code) |

**Impact:** Minor. Treat as opaque string.

#### 3. Missing fields in sandbox

15+ fields from official v5 docs sample are absent from sandbox response:
`currency_name`, `report_type`, `kiz`, `cashback_amount`, `cashback_discount`,
`cashback_commission_change`, `order_uid`, `payment_schedule`, `delivery_method`,
`seller_promo_id`, `seller_promo_discount`, `loyalty_id`, `loyalty_discount`,
`uuid_promocode`, `sale_price_promocode_discount_prc`, `acquiring_percent`.

Also absent: `storage_fee`, `deduction`, `acceptance` (present in docs sample).

**Impact:** Sandbox generates simplified test data. These fields are assumed
present in production. See DD-10 in mapping-spec.

#### 4. Sandbox-only field

`rid` (number, value 0) — present in sandbox, absent from official docs. Ignore.

#### 5. Sign convention CONFIRMED

Sandbox data shows `delivery_rub = 20` (positive value for debit/cost),
`rebill_logistic_cost = 1.349` (positive value for debit/cost).
This confirms DD-7: all positive, field name determines credit/debit.

### Sandbox response sample (first row, abbreviated)

```json
{
  "realizationreport_id": 42678565,
  "date_from": "2025-12-29T23:54:20Z",
  "date_to": "2025-12-30T22:54:20Z",
  "create_dt": "2025-12-30T22:54:20Z",
  "rrd_id": 12465777465,
  "nm_id": 40280886,
  "brand_name": "wildberries",
  "sa_name": "Ch-02b",
  "barcode": "3128026374004",
  "doc_type_name": "Продажа",
  "supplier_oper_name": "Логистика",
  "order_dt": "2025-12-30T22:54:20Z",
  "sale_dt": "2025-12-30T22:54:20Z",
  "rr_dt": "2025-12-30T22:54:20Z",
  "delivery_amount": 1,
  "delivery_rub": 20,
  "rebill_logistic_cost": 1.349,
  "dlv_prc": 1.8,
  "assembly_id": 2816993144,
  "srid": "57062242571278071.2.0",
  "srv_dbs": true,
  "site_country": "RU",
  "is_legal_entity": false
}
```

Note: most amount fields are 0 in sandbox test data (ppvz_for_pay, penalty, etc.)
Only `delivery_rub` and `rebill_logistic_cost` have non-zero values.

### Conclusion

Cross-verification выявила 2 важных расхождения:
1. **Timestamp format** — критическое для парсера, нужен dual-format (DD-9)
2. **Optional v5 fields** — нужен `@JsonIgnoreProperties(ignoreUnknown = true)` (DD-10)

Sign convention DD-7 дополнительно подтверждена: `delivery_rub=20`, `rebill_logistic_cost=1.349`.

---

## Session 5: 2026-03-31 (P&L verification — Ozon production, WB production)

### Goal

Верификация 5 архитектурных гипотез: end-to-end P&L walkthrough, standalone ops ratio, WB v5 optional fields, Ozon timezone, partial return scenario.

### 5.1 Ozon end-to-end walkthrough — posting `87621408-0010-1`

**Запрос:** filter by posting_number="87621408-0010-1", Jan 2025

**Операции по posting_number (3 ops):**

| operation_type | amount | accruals_for_sale | sale_commission | services |
|----------------|--------|-------------------|-----------------|----------|
| OperationAgentDeliveredToCustomer | 49.60 | 157.00 | -35.95 | DelivToCustomer=-8.45, DirectFlowLogistic=-63.00 |
| MarketplaceServiceBrandCommission | -0.79 | 0 | 0 | BrandCommission=-0.79 |
| StarsMembership | -0.79 | 0 | 0 | StarsMembership=-0.79 |

**Acquiring по order_number "87621408-0010" (1 op):**

| operation_type | amount |
|----------------|--------|
| MarketplaceRedistributionOfAcquiringOperation | -2.16 |

**P&L decompose:**
- revenue=157, commission=36.74, acquiring=2.16, logistics=71.45, stars=0.79
- net_payout = 49.60 + (-0.79) + (-0.79) + (-2.16) = 45.86
- residual = 45.86 − (157 − 36.74 − 2.16 − 71.45 − 0.79) = **0.00** ✓

### 5.2 Standalone operations reconciliation — Ozon Jan 2025

**Запрос:** all operations for Jan 2025, page-by-page aggregation (7590 ops total)

| Category | Ops | Credits | Debits | Net |
|----------|-----|---------|--------|-----|
| Order-linked | 7426 | +94,272.56 | -22,151.24 | +72,121.32 |
| Standalone | 164 | +617.85 | -31,640.22 | -31,022.37 |
| TOTAL | 7590 | +94,890.41 | -53,791.46 | +41,098.95 |

**Standalone breakdown:**

| operation_type | count | sum (RUB) |
|---------------|-------|-----------|
| OperationElectronicServiceStencil | 134 | -29,282.70 |
| MarketplaceSaleReviewsOperation | 5 | -1,872.00 |
| OperationMarketplaceServiceStorage | 22 | -485.52 |
| MarketplaceSellerCompensationOperation | 1 | +241.97 |
| AccrualWithoutDocs | 1 | +241.97 |
| AccrualInternalClaim | 1 | +133.91 |

**Key metrics:**
- |standalone_net| / |order_net| = **43.01%**
- |standalone_abs| / |all_abs| = **21.70%**
- Dominant: OperationElectronicServiceStencil = 94% of standalone debits

**Finding:** `OperationElectronicServiceStencil` operations have populated `items[].sku` field despite empty `posting_number`. SKU-level attribution is possible.

### 5.3 WB v5 optional fields — production check

**Запрос:** `GET /api/v5/supplier/reportDetailByPeriod?dateFrom=2025-12-01&dateTo=2026-01-01&limit=3`
**Token:** real-account (acc:3)
**Результат:** Empty string (аккаунт без финансовых данных за период)

**Вывод:** Production verification невозможна без аккаунта с реальными продажами.
Official docs sample confirms `seller_promo_discount=3` (non-zero). Other fields = 0 in sample.

### 5.4 Ozon finance timezone — EMPIRICAL CONFIRMATION (DD-17)

**Метод:** Cross-reference `posting.created_at` (UTC ISO 8601, from FBO list API) vs `finance.posting.order_date` (no-tz, from finance API).

**Delivered postings Jan 4, 2025:**

| posting_number | created_at (UTC ISO) | finance.order_date | Delta |
|----------------|---------------------|--------------------|-------|
| 0120282816-0009-1 | 2025-01-04T**01:25:58**Z | 2025-01-04 **04:25:58** | **+3h** |
| 78354499-0042-1 | 2025-01-04T**01:32:31**Z | 2025-01-04 **04:32:31** | **+3h** |
| 30173635-0399-1 | 2025-01-04T**01:43:21**Z | 2025-01-04 **04:43:21** | **+3h** |
| 48651423-0289-2 | 2025-01-04T**03:10:38**Z | 2025-01-04 **06:10:38** | **+3h** |
| 40777246-0378-2 | 2025-01-04T**04:18:03**Z | 2025-01-04 **07:18:03** | **+3h** |

**Cross-check near midnight UTC (Jan 1):**

| posting_number | created_at (UTC ISO) | finance.order_date | Delta |
|----------------|---------------------|--------------------|-------|
| 47721757-0306-1 | 2025-01-01T**17:06:39**Z | 2025-01-01 **20:06:39** | **+3h** |
| 0125499819-0100-1 | 2025-01-01T**17:11:53**Z | 2025-01-01 **20:11:52** | **+3h** (1s trunc) |
| 88434374-0047-1 | 2025-01-01T**17:11:50**Z | 2025-01-01 **20:11:50** | **+3h** |
| 28717514-0219-2 | 2025-01-01T**17:09:33**Z | 2025-01-01 **20:09:33** | **+3h** |
| 58347216-0441-1 | 2025-01-01T**17:23:02**Z | 2025-01-01 **20:23:02** | **+3h** |

**Conclusion:** Constant +3h offset across all 10 data points = **Moscow timezone (UTC+3) confirmed**.
Sub-second rounding (1s in one case) due to millisecond truncation.

### 5.5 Partial return — posting `0108601676-0168-1`

**Запрос:** all operations for posting 0108601676-0168-1 (Dec-Feb window)

**Timeline (6 ops by posting_number + 2 by order_number):**

| Date | Operation | amount | accruals | commission | services |
|------|-----------|--------|----------|------------|----------|
| Dec 31 | OperationAgentDeliveredToCustomer | +89.16 | +211 | -48.32 | DelivToCustomer=-10.52, DirectFlow=-63.00 |
| Dec 31 | MarketplaceServiceBrandCommission | -1.06 | 0 | 0 | Brand=-1.06 |
| Dec 31 | StarsMembership | -1.06 | 0 | 0 | Stars=-1.06 |
| Jan 2 | ClientReturnAgentOperation | -162.68 | **-211** | **+48.32** | — |
| Jan 2 | OperationAgentStornoDeliveredToCustomer | +9.40 | 0 | 0 | DelivToCustomer=**+9.40** |
| Jan 3 | OperationItemReturn | -78.00 | 0 | 0 | ReturnAfterDeliv=0, ReturnFlow=-63, RedistPVZ=-15 |

**Acquiring (order_number 0108601676-0168):**
- Charge: MarketplaceRedistributionOfAcquiringOperation = -2.63
- Reversal: MarketplaceRedistributionOfAcquiringOperation = **+2.63**

**P&L validation:**
- Sum of all amounts: 89.16 - 1.06 - 1.06 - 162.68 + 9.40 - 78.00 - 2.63 + 2.63 = **-144.24**
- P&L components: 211 - 211 - 1.06 - 0 - 142.12 - 1.06 = **-144.24**
- Residual = **0.00** ✓

**Key observations:**
1. Return = full reversal of `accruals_for_sale` (-211) and `sale_commission` (+48.32)
2. `OperationAgentStornoDeliveredToCustomer` = partial logistics credit (+9.40 = DelivToCustomer refund)
3. `OperationItemReturn` = NEW return logistics cost (-78 total: ReturnFlow + RedistPVZ)
4. Acquiring: charge reversed on return (net = 0)
5. Multi-day spread: sale Dec 31, return Jan 2, return logistics Jan 3

### 5.6 Second return — posting `0186178620-0017-1`

Same pattern confirmed:

| Date | Operation | amount |
|------|-----------|--------|
| Jan 2 | Sale | +7.58 |
| Jan 2 | Stars | -0.50 |
| Jan 2 | Brand | -0.50 |
| Jan 2 | Return (ClientReturn) | -76.33 |
| Jan 2 | Storno | +4.85 |
| Jan 7 | Return logistics | -78.00 |
| (order) | Acquiring charge | -1.36 |
| (order) | Acquiring reversal | +1.36 |

Sum: 7.58 - 0.50 - 0.50 - 76.33 + 4.85 - 78.00 - 1.36 + 1.36 = **-142.90**

### Summary of Session 5

| Verification | Result | Impact |
|-------------|--------|--------|
| End-to-end Ozon P&L | ✅ Residual=0 | Model correct |
| Standalone ops ratio | ⚠️ 43% of net | BLOCKER: needs SKU-attribution, UI visibility |
| WB v5 optional fields | ⏳ No production data | Deferred, monitor on first ingestion |
| Ozon timezone | ✅ Moscow (UTC+3) confirmed | DD-17 created in mapping-spec |
| Partial return | ✅ Residual=0, rules documented | Return measures rules added to analytics-pnl K-3 |

---

## Session 6: 2026-04-12 (Yandex Market Partner API — empirical verification)

### Goal

Полная эмпирическая верификация Yandex Market Partner API: auth, каталог, цены, остатки, заказы, возвраты, отчёты, склады, категории, акции, ставки, тарифы. Проверка error contract, endpoint discovery, расхождений с документацией.

### Constraint

Токен привязан к бизнесу с **disabled partners** (нет активных магазинов/кампаний). Это блокирует все business-level и campaign-level endpoints (403 API_DISABLED). Тем не менее, сессия верифицирует: auth mechanism, error response contract, endpoint URL корректность, HTTP method discovery, official docs cross-check.

---

### 6.1 Auth & Business Discovery

**Endpoint:** `POST /v2/auth/token`
**Result:** 403

```json
{
  "status": "ERROR",
  "errors": [{
    "code": "API_DISABLED",
    "message": "API for business <REDACTED_ID> disabled because it has only disabled partners."
  }]
}
```

**Ключевые находки:**
- Токен валиден — API распознаёт его и возвращает `businessId` в сообщении об ошибке
- `businessId` успешно извлечён из error response
- Auth mechanism: `Api-Key: ACMA:...` header — **подтверждён**

### 6.2 Campaigns (магазины)

**Endpoint:** `GET /v2/campaigns`
**Result:** 200 OK

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

**Ключевые находки:**
- Единственный endpoint, вернувший 200 OK — работает даже для disabled business
- **Dual pagination** в ответе: legacy `pager` (page-based) + modern `paging` (cursor-based `nextPageToken`)
- Подтверждена структура из docs: `campaigns[].id` (campaignId), `campaigns[].business.id` (businessId), `campaigns[].placementType`, `campaigns[].apiAvailability`
- Пустой массив — нет активных магазинов, что объясняет 403 на всех остальных endpoints
- **Rate limit** из docs: 1000 req/hour (до 18.05.2026 — 5000 req/hour)

### 6.3 Offer Mappings (каталог)

**Endpoint:** `POST /v2/businesses/{businessId}/offer-mappings`
**Result:** 403 API_DISABLED

**Из official docs подтверждено:**
- Request body: `{"offerIds": [...], "cardStatuses": [...], "categoryIds": [...], "vendorNames": [...], "tags": [...], "archived": bool}`
- Pagination: cursor-based (`pageToken` + `limit`, max 100)
- Response wrapper: `{"status": "OK", "result": {"paging": {"nextPageToken": "..."}, "offerMappings": [{offer: {...}, mapping: {...}}]}}`
- Offer fields: `offerId` (seller SKU), `name`, `vendor`, `barcodes[]`, `vendorCode`, `weightDimensions`, `basicPrice`, `purchasePrice`, `cardStatus`, `campaigns[]`, `sellingPrograms[]`, `archived`, `groupId`
- Mapping fields: `marketSku` (Yandex SKU), `marketSkuName`, `marketModelName`, `marketCategoryId`, `marketCategoryName`
- **Rate limit**: 100 req/min (600 with subscription)
- Supports `language` query param: `RU` (default), `UZ`

### 6.4 Offer Prices

**Endpoint:** `POST /v2/businesses/{businessId}/offer-prices`
**Result:** 403 API_DISABLED

**Из official docs:** Цены доступны как часть offer-mappings через `offer.basicPrice`, `offer.purchasePrice`. Отдельный endpoint для получения цен — подтверждён URL.

### 6.5 Stocks (остатки)

**Endpoint:** `POST /v2/campaigns/{campaignId}/offers/stocks`
**Result:** 403 FORBIDDEN (fake campaignId — "Access denied")

**Из official docs подтверждено:**
- Campaign-level endpoint (требует `campaignId`, НЕ `businessId`)
- Request body: `{"offerIds": [...], "warehouseIds": [...], "withTurnover": bool}`
- Response: items with `offerId`, `stocks[].warehouseId`, `stocks[].count`, `stocks[].type` (FIT/QUARANTINE/etc.)
- Для FBY: можно получить `turnover` (оборачиваемость) через `withTurnover: true`
- **Rate limit**: 100,000 items/min (НЕ запросов — считаются товары)

### 6.6 Orders (заказы)

**Endpoint:** `POST /v1/businesses/{businessId}/orders`
**Result:** 403 API_DISABLED

**Критическая находка из docs:**
- `GET /v2/campaigns/{campaignId}/orders` — **DEPRECATED!**
- Рекомендованная замена: `POST /v1/businesses/{businessId}/orders` (business-level)
- Альтернатива: `POST /v2/campaigns/{campaignId}/stats/orders` (campaign-level stats)
- Не возвращает заказы, доставленные/отменённые >30 дней назад (для старых — stats endpoint)
- Поддерживает `fake: true` для тестовых заказов
- **Rate limit**: 10,000 req/hour (до 18.05.2026 — 100,000)

### 6.7 Returns (возвраты)

**Endpoint:** `GET /v2/campaigns/{campaignId}/returns?limit=3`
**Result:** 403 FORBIDDEN (fake campaignId)

**Из docs:** campaign-level endpoint, требует `campaignId`.

### 6.8 Report: United Marketplace Services

**Endpoint:** `POST /v2/reports/united-marketplace-services/generate`
**Result:** 403 API_DISABLED

**Из official docs подтверждено:**
- Async: generate → poll `GET /v2/reports/info/{reportId}` → download
- Два типа отчётов: по дате начисления (`dateFrom`+`dateTo`) или по дате акта (`year`+`month`)
- Scope: `finance-and-accounting` или `all-methods`
- **Структура может меняться без предупреждения** (explicit disclaimer from Yandex!)
- Колонки включают: ORDER_ID, SHOP_SKU, ORDER_CREATION_DATE_TIME, PLACEMENT_MODEL (FBY/FBS/etc.), INN, PARTNER_ID

### 6.9 Report: Goods Realization

**Endpoint:** `POST /v2/reports/goods-realization/generate`
**Result:** 403 API_DISABLED

**Из official docs подтверждено:**
- Request: `{"businessId": ..., "year": YYYY, "month": M}` — помесячная генерация
- Доступен для FBY, FBS, Express, DBS
- Scope: `finance-and-accounting` или `all-methods`
- Колонки: ORDER_ID, YOUR_ORDER_ID, YOUR_SKU, SHOP_SKU, ORDER_CREATION_DATE, TRANSFERRED_TO_DELIVERY_DATE, DELIVERY_DATE, TRANSFERRED_TO_DELIVERY_COUNT
- **Структура может меняться без предупреждения**

### 6.10 Warehouses (Yandex)

**Endpoint:** `GET /v2/warehouses`
**Result:** 403 API_DISABLED

### 6.11 Warehouses (seller)

**Endpoint:** `GET /v2/businesses/{businessId}/warehouses`
**Result:** 403 API_DISABLED

### 6.12 Categories Tree

**Endpoint:** `POST /v2/categories/tree`
**Result:** 403 API_DISABLED

**Находка:** даже «справочные» endpoints, не привязанные к конкретному бизнесу, блокируются для disabled business. Это отличается от WB, где справочники доступны без привязки к аккаунту.

### 6.13 Promos (акции)

**Endpoint:** `POST /v2/businesses/{businessId}/promos`
**Result:** 403 API_DISABLED

### 6.14 Bids (ставки буста) — ENDPOINT DISCOVERY

**Attempted endpoints:**

| Method | URL | HTTP Status | Error Code |
|--------|-----|-------------|------------|
| POST | `/v2/businesses/{id}/bids` | 405 | METHOD_NOT_ALLOWED |
| GET | `/v2/businesses/{id}/bids` | 405 | METHOD_NOT_ALLOWED |
| PUT | `/v2/businesses/{id}/bids` | 403 | API_DISABLED |
| POST | `/v2/businesses/{id}/bids/info/search` | 404 | NOT_FOUND |
| POST | `/v2/businesses/{id}/bids/info` | 403 | API_DISABLED |

**Вывод — правильные endpoints:**
- **Read bids:** `POST /v2/businesses/{businessId}/bids/info` ✅
- **Write bids:** `PUT /v2/businesses/{businessId}/bids` ✅

**Из official docs подтверждено:**
- Bid = integer, 0–9999 (процент от стоимости × 100, например 570 = 5.7%)
- Request body (read): `{"skus": ["sku1", "sku2"]}` — если пустой, возвращает все ставки
- Response: `{"status": "OK", "result": {"bids": [{"sku": "...", "bid": 570}], "paging": {"nextPageToken": "..."}}}`
- Pagination: cursor-based, max 500 items
- Scopes: `pricing`, `pricing:read-only`, `promotion`, `promotion:read-only`, `all-methods`
- **Rate limit**: 500 req/min (1000 with subscription)

### 6.15 Tariffs Calculator

**Endpoint:** `POST /v2/tariffs/calculate`
**Result:** 403 API_DISABLED

---

### Error Response Contract — VERIFIED

| HTTP Status | Error Code | Message Pattern | Trigger |
|-------------|------------|-----------------|---------|
| 401 | `UNAUTHORIZED` | `Credentials are not specified` | No Api-Key header |
| 401 | `UNAUTHORIZED` | `Api-Key token prefix invalid` | Malformed token |
| 403 | `API_DISABLED` | `API for business {id} disabled because it has only disabled partners.` | Valid token, disabled business |
| 403 | `FORBIDDEN` | `Access denied` | Valid token, wrong campaignId |
| 404 | `NOT_FOUND` | `Resource not found` | Non-existent endpoint |
| 405 | `METHOD_NOT_ALLOWED` | `Request method '{METHOD}' not supported` | Wrong HTTP method |

**Общая структура ответов:**
- Success: `{"status": "OK", "result": {...}}`
- Error: `{"status": "ERROR", "errors": [{"code": "...", "message": "..."}]}`
- Campaigns (legacy): includes `pager` (page-based) alongside `paging` (cursor-based)

---

### Key Findings & Discrepancies

#### F-1: Disabled Business Blocks ALL Endpoints
Даже «справочные» endpoints (categories, warehouses) блокируются для disabled business. В отличие от WB/Ozon, Yandex Market не разделяет «аккаунтные» и «глобальные» API. Единственное исключение — `GET /v2/campaigns` (возвращает пустой массив с 200).

#### F-2: Bids Endpoint URL Mismatch
Пользовательская спецификация указала `POST /v2/businesses/{businessId}/bids` для чтения ставок. Реальный read endpoint — `POST /v2/businesses/{businessId}/bids/info`. Endpoint `/bids` принимает только PUT (write). Это критическое расхождение для адаптера.

#### F-3: Orders Endpoint Deprecated
`GET /v2/campaigns/{campaignId}/orders` помечен как **Deprecated** в официальной документации. Рекомендуемая замена — `POST /v1/businesses/{businessId}/orders`. Это меняет уровень запроса с campaign на business.

#### F-4: Dual Pagination in Campaigns
Endpoint `/v2/campaigns` возвращает оба формата пагинации: legacy `pager` (page-based) и modern `paging` (cursor-based). Адаптер должен использовать `paging.nextPageToken`.

#### F-5: Rate Limit Code = 420 (Not 429)
В отличие от стандартного 429 Too Many Requests, Yandex Market возвращает **420 Enhance Your Calm** (420 Method Failure в документации). Retry logic должна обрабатывать оба кода.

#### F-6: Report Structure Disclaimer
Yandex Market явно заявляет: «Структура и содержание отчетов могут изменяться без предварительного уведомления». Парсер отчётов должен быть lenient (`@JsonIgnoreProperties(ignoreUnknown = true)`).

#### F-7: No Sandbox
У Yandex Market нет отдельного sandbox-окружения (в отличие от WB). Тестирование — только через production API с реальным аккаунтом. Для интеграционных тестов — только WireMock.

---

### МАТРИЦА ДОСТУПНОСТИ ПРОВЕРКИ

| # | Endpoint | R/W | Official docs | Empirical prod | Sandbox | Реальные данные | WireMock нужен | Уверенность в контракте |
|---|----------|-----|---------------|----------------|---------|-----------------|----------------|-------------------------|
| 1 | `POST /v2/auth/token` | R | ✅ Есть | ✅ 403 (businessId discovered) | ❌ Нет | ❌ Disabled biz | Нет (auth only) | 🟡 Medium |
| 2 | `GET /v2/campaigns` | R | ✅ Есть, sample | ✅ 200 OK (empty) | ❌ Нет | ❌ Нет кампаний | ✅ Да | 🟢 High |
| 3 | `POST /businesses/{id}/offer-mappings` | R | ✅ Есть, полный sample | ❌ 403 | ❌ Нет | ❌ Нет | ✅ Да | 🟡 Medium |
| 4 | `POST /businesses/{id}/offer-prices` | R | ⚠️ Цены в offer-mappings | ❌ 403 | ❌ Нет | ❌ Нет | ✅ Да | 🟡 Medium |
| 5 | `POST /campaigns/{id}/offers/stocks` | R | ✅ Есть, полный sample | ❌ 403 (FORBIDDEN) | ❌ Нет | ❌ Нет | ✅ Да | 🟡 Medium |
| 6 | `POST /v1/businesses/{id}/orders` | R | ✅ Есть (v1) | ❌ 403 | ❌ Нет | ❌ Нет | ✅ Да | 🟡 Medium |
| 7 | `GET /campaigns/{id}/returns` | R | ⚠️ Docs URL 404 | ❌ 403 (FORBIDDEN) | ❌ Нет | ❌ Нет | ✅ Да | 🔴 Low |
| 8 | `POST /reports/united-mktplace-services/generate` | R | ✅ Есть, column spec | ❌ 403 | ❌ Нет | ❌ Нет | ✅ Да | 🟡 Medium |
| 9 | `GET /reports/info/{reportId}` | R | ✅ Есть | ❌ No reportId | ❌ Нет | ❌ Нет | ✅ Да | 🟡 Medium |
| 10 | `POST /reports/goods-realization/generate` | R | ✅ Есть, column spec | ❌ 403 | ❌ Нет | ❌ Нет | ✅ Да | 🟡 Medium |
| 11 | `GET /v2/warehouses` | R | ✅ Есть | ❌ 403 | ❌ Нет | ❌ Нет | ✅ Да | 🟡 Medium |
| 12 | `GET /businesses/{id}/warehouses` | R | ✅ Есть | ❌ 403 | ❌ Нет | ❌ Нет | ✅ Да | 🟡 Medium |
| 13 | `POST /categories/tree` | R | ✅ Есть | ❌ 403 | ❌ Нет | ❌ Нет | ✅ Да | 🟡 Medium |
| 14 | `POST /businesses/{id}/promos` | R | ✅ Есть | ❌ 403 | ❌ Нет | ❌ Нет | ✅ Да | 🟡 Medium |
| 15 | `POST /businesses/{id}/bids/info` | R | ✅ Есть, full spec | ❌ 403 | ❌ Нет | ❌ Нет | ✅ Да | 🟢 High |
| 16 | `POST /tariffs/calculate` | R | ✅ Есть | ❌ 403 | ❌ Нет | ❌ Нет | ✅ Да | 🟡 Medium |

**Легенда уверенности:**
- 🟢 **High** — подтверждено эмпирически + docs (URL/method verified via probing, response structure from docs)
- 🟡 **Medium** — docs-only (URL и body structure из документации, не проверено на реальных данных)
- 🔴 **Low** — ни docs, ни empirical не подтвердили полный контракт

---

### Summary of Session 6

| Verification | Result | Impact |
|-------------|--------|--------|
| Auth mechanism (Api-Key header) | ✅ Confirmed | Token valid, businessId discoverable |
| Error response contract | ✅ 6 error codes verified | Adapter error handling fully spec'd |
| Campaigns structure | ✅ 200 OK (empty) | Dual pagination confirmed |
| Bids endpoint discovery | ✅ Read=POST bids/info, Write=PUT bids | F-2: original spec was wrong |
| Orders deprecation | ⚠️ GET orders DEPRECATED | F-3: use POST /v1/businesses/{id}/orders |
| Report structure disclaimer | ⚠️ May change without notice | F-6: parser must be lenient |
| All business-level endpoints | ❌ 403 API_DISABLED | Need active account for data verification |
| No sandbox | ❌ Confirmed | F-7: WireMock required for all tests |
| Data-level verification | ❌ Not possible | BLOCKER: disabled business, no real data |
