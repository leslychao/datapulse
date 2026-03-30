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
