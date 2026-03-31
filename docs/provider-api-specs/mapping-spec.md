# Mapping Specification — Provider → Normalized → Canonical

**Статус:** sandbox + official docs cross-verified (Ozon fully, WB 5/7 via sandbox + docs)
**Зависит от:** wb-read-contracts.md, ozon-read-contracts.md, write-contracts.md, promo-advertising-contracts.md

Этот документ определяет маппинг полей от provider payloads к normalized records
и далее к canonical entities. Каждое поле имеет confidence-классификацию.

Confidence levels:
- **C** = confirmed — маппинг проверен по документации И верифицирован реальным API-ответом
- **C-docs** = confirmed-docs — маппинг проверен по документации, но не верифицирован на данных
- **A** = assumed — маппинг выведен логически, но не верифицирован
- **U** = unknown — маппинг заблокирован, нельзя использовать для business truth

---

## Design Decisions (установлены на основе эмпирической верификации)

### DD-1: Ozon Canonical Price

**Решение:** `CanonicalPriceCurrent.price` = `price.price` из v5/product/info/prices.
`discount_price` = `price.marketing_seller_price`.

**Обоснование:** В реальном ответе v5 `price` — это вложенный объект с числовыми полями.
`price.price` = текущая активная цена продажи (273).
`price.marketing_seller_price` = цена в маркетинговых акциях (273, но может отличаться).
`price.old_price` = зачёркнутая цена (399).

### DD-2: Ozon Warehouse Strategy

**Решение:** `warehouseId` = `stocks[].warehouse_ids[0]` если не пуст, иначе `stocks[].type` ("fbo"/"fbs").
Для заказов: `analytics_data.warehouse_id` (long) — основной источник.

**Обоснование:** v4 stocks endpoint имеет `warehouse_ids[]` (массив, часто пустой).
Postings имеют `analytics_data.warehouse_id` (long, заполнен).

### DD-3: Ozon Sales = Composite Capability

**Решение:** Ozon SALES = delivered postings + finance `OperationAgentDeliveredToCustomer`.
Не существует отдельного sales endpoint.

**Обоснование:** Эмпирически подтверждено — v3/finance/transaction/list содержит
`OperationAgentDeliveredToCustomer` с полной финансовой разбивкой.

### DD-4: Ozon Finance Sign Convention

**Решение:**
- Positive = credit to seller (revenue, cost refunds)
- Negative = debit from seller (costs, reversed revenue)
- `amount` = `accruals_for_sale` + `sale_commission` + Σ(`services[].price`)

**Обоснование:** Математически верифицировано:
- Продажа: 157 + (-35.95) + (-8.45) + (-63) = 49.60 ✓
- Возврат: -211 + 48.32 = -162.68 ✓

### DD-5: Ozon v2 → v3 Migration

**Решение:** Все Ozon endpoints используют v3/v4/v5 версии.
v2/product/list, v2/product/info → 404 (deprecated).

**Обоснование:** Эмпирически подтверждено — v2 endpoints возвращают 404.

### DD-6: Ozon Finance Timestamp Format

**Решение:** `operation_date` и `posting.order_date` парсить как `"yyyy-MM-dd HH:mm:ss"`,
НЕ как ISO 8601. **Timezone = Moscow (UTC+3) — empirically confirmed 2026-03-31.**

**Обоснование:** Реальный ответ содержит "2025-01-02 00:00:00" (без T, без Z).

**Timezone verification (2026-03-31):** Cross-reference `posting.created_at` (UTC ISO) vs `finance.posting.order_date`:
- `created_at: 2025-01-04T01:25:58Z` → `order_date: 2025-01-04 04:25:58` (+3h)
- `created_at: 2025-01-04T03:10:38Z` → `order_date: 2025-01-04 06:10:38` (+3h)
- `created_at: 2025-01-01T17:06:39Z` → `order_date: 2025-01-01 20:06:39` (+3h)
Constant +3h offset = Moscow timezone. Confirmed on 7 data points.

**Реализация:** `OffsetDateTime.of(LocalDateTime.parse(value, formatter), ZoneOffset.ofHours(3))`

### DD-7: WB Finance Sign Convention

**Решение:** Все числовые поля WB `reportDetailByPeriod` — **ПОЛОЖИТЕЛЬНЫЕ абсолютные значения**.
Кредит/дебит определяется **именем поля**, не знаком.

**Credit (к продавцу):** `ppvz_for_pay`, `ppvz_vw`, `additional_payment`
**Debit (с продавца):** `ppvz_sales_commission`, `acquiring_fee`, `delivery_rub`, `penalty`,
`storage_fee`, `deduction`, `rebill_logistic_cost`, `acceptance`

**Обоснование:** Подтверждено по official API documentation sample response.
Принципиально отличается от Ozon (где negative = cost):
- Ozon: `sale_commission` = -35.95 (отрицательное = расход)
- WB: `ppvz_sales_commission` = 23.74 (положительное, расход определяется по имени поля)

### DD-8: Ozon Brand via Attribute API

**Решение:** Brand для Ozon товаров получать через `POST /v4/product/info/attributes`.
Атрибут `id=85` (name="Бренд", type=String, is_required=true).
Path: `result[].attributes[?(@.id==85)].values[0].value`

**Обоснование:** brand отсутствует в v3/product/info/list. Подтверждено эмпирически —
attributes endpoint возвращает brand="BOROFONE" для product_id=1074782997.
Требует дополнительного API-вызова при ingestion catalog.

### DD-9: WB Finance Timestamp Format — Dual Parser Required

**Решение:** WB finance поля `rr_dt`, `date_from`, `date_to`, `create_dt` могут приходить
как date-only (`"2022-10-20"`) так и full ISO 8601 datetime (`"2025-12-30T22:54:20Z"`).

**Обоснование:** Official docs v5 sample показывает date-only для `rr_dt`, `date_from`,
`date_to`, `create_dt`. Sandbox API возвращает full ISO 8601 datetime для всех этих полей.
`order_dt` и `sale_dt` в обоих источниках — ISO 8601 datetime.

**Реализация:** WB finance adapter ДОЛЖЕН использовать flexible parser:
попытка ISO 8601 datetime → fallback на date-only → LocalDate.atStartOfDay(ZoneOffset.UTC).

### DD-10: WB Finance Optional Fields (v5 Additions)

**Решение:** 15+ полей из official v5 docs (`cashback_amount`, `cashback_discount`,
`delivery_method`, `kiz`, `seller_promo_*`, `loyalty_*`, `order_uid`, `report_type` и др.)
отсутствуют в sandbox response. Считать их опциональными.

**Обоснование:** Sandbox генерирует упрощённые test data с subset полей.
В production эти поля скорее всего присутствуют. Adapter должен безопасно
десериализовать response с/без этих полей (`@JsonIgnoreProperties(ignoreUnknown = true)`).

**Для P&L:** Поля `cashback_amount`, `cashback_commission_change`, `seller_promo_discount`,
`loyalty_discount` влияют на расчёт итоговой суммы к выплате. Их учёт будет добавлен
при реализации finance ingestion handler.

### DD-15: Ozon Acquiring Join Key — order_number, NOT posting_number (2026-03-30)

**Решение:** Ozon `MarketplaceRedistributionOfAcquiringOperation` использует `order_number` формат
(без суффикса `-1`), а не полный `posting_number`.

**Обоснование:** Эмпирически верифицировано (2026-03-30):
- Sale operation для posting "0151413710-0012-1" → `posting.posting_number` = "0151413710-0012-1"
- Acquiring operation для того же заказа → `posting.posting_number` = "0151413710-0012" (без -1)
- При фильтрации по posting_number="0151413710-0012-1" → возвращаются sale + brand + stars (3 ops), НО НЕ acquiring
- При фильтрации по posting_number="0151413710-0012" → возвращается acquiring (-0.58 RUB)

**Join strategy для per-order P&L:**
- Sale/BrandCommission/StarsMembership: join по `posting_number` (полный формат, с суффиксом)
- Acquiring: join по `order_number` = `posting_number` без последнего `-N` суффикса
- Реализация: `posting_number.substring(0, posting_number.lastIndexOf('-'))` → order_number

**Альтернатива:** Acquiring может аллоцироваться на posting через `items[].sku` + `order_date` fuzzy match,
но order_number join надёжнее и проще.

### DD-16: Ozon Storage Operations — no per-order attribution (2026-03-30)

**Решение:** Ozon `OperationMarketplaceServiceStorage` — daily aggregate charge, НЕ привязан к заказу.

**Обоснование:** Эмпирически верифицировано:
- `posting.posting_number` = "" (пустая строка)
- `posting.warehouse_id` = 0
- `posting.order_date` = "" (пустая строка)
- `items` = [] (пустой массив)
- `services` = [] (пустой массив, стоимость только в `amount`)

**Последствие для P&L:** Storage costs аллоцируются пропорционально (pro-rata) по SKU или
учитываются как period-level overhead. Не привязываются к конкретному заказу.

---

## 1. CATALOG

### WB → NormalizedCatalogItem — SANDBOX VERIFIED

| WB field | Normalized field | Confidence | Notes |
|----------|------------------|------------|-------|
| `vendorCode` | `sellerSku` | C | Sandbox verified. Seller's own article, primary join key |
| `nmID` | `marketplaceSku` | C | Sandbox verified. WB internal product ID, stored as string |
| `title` | `name` | C | Sandbox verified |
| `brand` | `brand` | C | Sandbox verified — brand IS in WB catalog response (unlike Ozon) |
| `subjectName` | `category` | C | Sandbox verified. WB subject = product category |
| `sizes[0].skus[0]` | `barcode` | C | Sandbox verified. First barcode of first size; multi-size products need strategy |
| (derived) | `status` | A | No explicit status field in response; default to ACTIVE |

### Ozon → NormalizedCatalogItem

| Ozon field | Normalized field | Confidence | Notes |
|------------|------------------|------------|-------|
| `offer_id` | `sellerSku` | C | Direct from v3 response |
| `id` (= product_id) | `marketplaceSku` | C | Ozon product ID, stored as string |
| `name` | `name` | C | |
| via `/v4/product/info/attributes`, attr_id=85 | `brand` | C | Brand via attribute API; `values[0].value` where `id==85` |
| `description_category_id` | `category` | C | Category ID (replaces deprecated `category_id`); name requires lookup |
| `barcodes[0]` | `barcode` | C | Array field; take first element |
| `is_archived` + `is_autoarchived` | `status` | C | `false` + `false` → ACTIVE; any `true` → INACTIVE |

**Verified changes from initial contract:**
- `category_id` → `description_category_id` (verified)
- `barcode` → `barcodes[]` (array, verified)
- `visible` + `status` → `is_archived` + `is_autoarchived` (verified)
- `brand` RESOLVED via v4/product/info/attributes (attribute_id=85, confirmed)

### NormalizedCatalogItem → CanonicalOffer

CanonicalOffer реализована как три таблицы: `product_master`, `seller_sku`, `marketplace_offer`. См. [ETL Pipeline](../modules/etl-pipeline.md) §Связи каталожных сущностей.

| Normalized field | Canonical target table.field | Confidence | Notes |
|------------------|------------------------------|------------|-------|
| `sellerSku` | `seller_sku.sku_code` | C | Direct mapping |
| `marketplaceSku` | `marketplace_offer.marketplace_sku` | C | Direct mapping |
| `name` | `marketplace_offer.name` | C | |
| `brand` | `product_master.brand` | C (WB sandbox) / C (Ozon via attributes) | WB: direct field. Ozon: via v4/attributes (id=85) |
| `category` | `marketplace_offer.category_id` (FK → category) | C (WB sandbox) / C (Ozon) | Name for WB, ID for Ozon |
| `barcode` | `seller_sku.barcode` | C (both) | |
| `status` | `marketplace_offer.status` | C (Ozon) / A (WB) | Ozon: `is_archived` verified; WB: derived |

---

## 2. PRICES

### WB → NormalizedPriceItem

| WB field | Normalized field | Confidence | Notes |
|----------|------------------|------------|-------|
| `vendorCode` | `sellerSku` | C | Sandbox verified — vendorCode IS in prices response |
| `sizes[0].price` | `price` | C | Sandbox verified. Base price in rubles per size |
| `sizes[0].discountedPrice` | `discountPrice` | C | Sandbox verified. Price after seller discount |
| `currencyIsoCode4217` | `currency` | C | Sandbox verified. Explicit "RUB" |

**RESOLVED via sandbox**: WB prices are per-size. `price` → apply `discount` → `discountedPrice` → apply `clubDiscount` → `clubDiscountedPrice`.
`editableSizePrice` controls whether sizes can have individual prices.

### Ozon → NormalizedPriceItem

| Ozon field | Normalized field | Confidence | Notes |
|------------|------------------|------------|-------|
| `offer_id` | `sellerSku` | C | Direct from v5 response |
| `price.price` | `price` | C | Current price (NUMBER in v5, not string!) |
| `price.marketing_seller_price` | `discountPrice` | C | Marketing action price (DD-1) |
| `price.currency_code` | `currency` | C | Explicit "RUB" in nested price object |
| `price.old_price` | (informational) | C | Old/crossed-out price |
| `price.min_price` | (informational) | C | Seller's min price floor |

**Verified changes from initial contract:**
- Price is NESTED object `price.price` (not top-level)
- Price values are NUMBERS (not strings as in product/info)
- `currency_code` is inside `price` object
- `marketing_seller_price` chosen as `discountPrice` per DD-1

### NormalizedPriceItem → CanonicalPriceCurrent

| Normalized field | Canonical field (DDL: `canonical_price_current`) | Confidence | Notes |
|------------------|--------------------------------------------------|------------|-------|
| (resolved via offer lookup) | `marketplace_offer_id` | C | Resolved by `resolveOffer()` in orchestrator |
| `price` | `price` | C | |
| `discountPrice` | `discount_price` | C (Ozon) / A (WB) | Ozon: `marketing_seller_price`; WB: computed |
| `currency` | `currency` | C (Ozon) / A (WB) | |
| (ingestion time) | `captured_at` | C | Set from rawArtifact.capturedAt |

---

## 3. STOCKS

### WB → NormalizedStockItem

| WB field | Normalized field | Confidence | Notes |
|----------|------------------|------------|-------|
| (derived from WB article → vendorCode) | `sellerSku` | A | Not verified with data |
| `warehouseId` | `warehouseId` | A | Field name not verified with real data |
| stock quantity field | `available` | A | Field name not verified |
| (not clearly in response) | `reserved` | U | May not be available |

### Ozon → NormalizedStockItem

| Ozon field | Normalized field | Confidence | Notes |
|------------|------------------|------------|-------|
| `offer_id` | `sellerSku` | C | |
| `stocks[].warehouse_ids[0]` or `stocks[].type` | `warehouseId` | C | DD-2: use warehouse_ids if populated, else type |
| `stocks[].present` | `available` | C | |
| `stocks[].reserved` | `reserved` | C | |
| `stocks[].type` | (informational) | C | "fbo" or "fbs" |
| `stocks[].sku` | (informational) | C | Ozon system SKU |
| `stocks[].shipment_type` | (informational) | C | e.g. "SHIPMENT_TYPE_GENERAL" |

**Verified changes from initial contract:**
- `warehouse_ids` IS present in v4 response (was documented as absent)
- Ozon `warehouseId` mapping updated to use `warehouse_ids[0]` with type fallback (DD-2)

### NormalizedStockItem → CanonicalStockCurrent

| Normalized field | Canonical field (DDL: `canonical_stock_current`) | Confidence | Notes |
|------------------|--------------------------------------------------|------------|-------|
| (resolved via offer lookup) | `marketplace_offer_id` | C | |
| `warehouseId` | `warehouse_id` | C (Ozon) / A (WB) | DD-2 for Ozon |
| `available` | `available` | C (Ozon) / A (WB) | |
| `reserved` | `reserved` | C (Ozon) / U (WB) | |
| (ingestion time) | `captured_at` | C | |

---

## 4. ORDERS

### WB → NormalizedOrderItem

| WB field | Normalized field | Confidence | Notes |
|----------|------------------|------------|-------|
| `srid` | `externalOrderId` | C-docs | Unique row identifier |
| `supplierArticle` | `sellerSku` | C-docs | Seller's vendorCode |
| (always 1 per row) | `quantity` | C-docs | 1 row = 1 unit |
| `totalPrice` | `pricePerUnit` | A | Gross price before discount |
| `priceWithDisc` or `totalPrice * (1-discountPercent/100)` | `totalAmount` | A | |
| (implicit) | `currency` | A | Assumed "RUB" |
| `date` | `orderDate` | C-docs | Parsed to LocalDate |
| `isCancel` → mapped | `status` | A | Boolean cancel flag → status string |

### Ozon → NormalizedOrderItem

| Ozon field | Normalized field | Confidence | Notes |
|------------|------------------|------------|-------|
| `posting_number` | `externalOrderId` | C | |
| `products[].offer_id` | `sellerSku` | C | Per-product in posting |
| `products[].quantity` | `quantity` | C | |
| `products[].price` | `pricePerUnit` | C | String → BigDecimal |
| `price * quantity` | `totalAmount` | C | Computed |
| `products[].currency_code` | `currency` | C | Explicit "RUB" |
| `created_at` | `orderDate` | C | ISO 8601 UTC → LocalDate |
| `status` | `status` | C | Direct posting status string |

**VERIFIED** Ozon order fields:
- `products[].price` = STRING ("103.00")
- `financial_data.products[].price` = NUMBER (103)
- `analytics_data.warehouse_id` = LONG (populated!)
- `created_at` = ISO 8601 UTC with nanoseconds
- `substatus` provides additional status detail

### NormalizedOrderItem → CanonicalOrder

| Normalized field | Canonical field (DDL: `canonical_order`) | Confidence | Notes |
|------------------|------------------------------------------|------------|-------|
| `externalOrderId` | `external_order_id` | C | |
| (resolved) | `marketplace_offer_id` (FK → marketplace_offer) | C | |
| `quantity` | `quantity` | C | |
| `pricePerUnit` | `price_per_unit` | C (Ozon) / A (WB) | |
| `totalAmount` | `total_amount` | C (Ozon) / A (WB) | |
| `currency` | `currency` | C (Ozon) / A (WB) | |
| `orderDate` | `order_date` | C | |
| `status` | `status` | C (Ozon) / A (WB) | |

---

## 5. SALES

### DD-12: WB Sales endpoint vs Finance report

**WB official documentation explicitly states** (2026-03-30 verified):
> "The values of the `priceWithDisc` and `forPay` fields are calculated using a simplified logic
> and **may differ** from `retail_price_withdisc_rub` and `ppvz_for_pay`, respectively,
> in details for the realization reports."
>
> "Use details for the realization reports for **accurate financial calculations**, reconciliation, and reporting."

**Design decision**: WB sales facts for P&L are materialized from `reportDetailByPeriod`, NOT from `/api/v1/supplier/sales`. The Sales endpoint provides operational monitoring only (near-realtime, 30-min delay, preliminary data).

### WB → NormalizedSaleItem (from `/api/v1/supplier/sales` — operational, NOT for P&L)

| WB field | Normalized field | Confidence | Notes |
|----------|------------------|------------|-------|
| `saleID` | `externalSaleId` | C-docs | Starts with S (sale) or R (return/storno) |
| `srid` | `srid` (join key) | C-docs | Used to correlate with finance report |
| `supplierArticle` | `sellerSku` | C-docs | |
| (always 1) | `quantity` | C-docs | 1 row = 1 unit |
| `priceWithDisc` | `saleAmount` (approximate) | C-docs | **May differ from `retail_price_withdisc_rub` in finance** |
| `forPay` | `netPayout` (approximate) | C-docs | **May differ from `ppvz_for_pay` in finance** |
| `paymentSaleAmount` | `paymentSaleAmount` | C-docs | Payment sale amount (new field) |
| `spp` | `sppPercent` | C-docs | WB buyer loyalty discount % |
| (implicit) | `currency` | A | "RUB" |
| `date` | `saleDate` | C-docs | |

### WB → CanonicalSale (from `reportDetailByPeriod` — source of truth for P&L)

| WB finance field | Canonical field | → fact_finance measure | Confidence | Notes |
|------------------|-----------------|------------------------|------------|-------|
| `srid` | join key | — | C | Correlates to sales/orders endpoints |
| `retail_price_withdisc_rub` | `saleAmount` | `revenue_amount` | C-docs | Price with discount (pre-SPP) |
| `ppvz_for_pay` | `netPayout` | `net_payout` | C-docs | Amount payable to seller |
| `ppvz_sales_commission` | `commission` | `marketplace_commission_amount` | C-docs | WB commission (DEBIT, positive value) |
| `acquiring_fee` | `acquiringFee` | `acquiring_commission_amount` | C-docs | Acquiring (DEBIT, positive value) |
| `delivery_rub` | `logisticsCost` | `logistics_cost_amount` | C | Logistics (DEBIT) |
| `rebill_logistic_cost` | `rebillLogistics` | `logistics_cost_amount` | C | Re-billed logistics |
| `penalty` | `penalty` | `penalties_amount` | C-docs | Fines (DEBIT) |
| `storage_fee` | `storageFee` | `storage_cost_amount` | C-docs | Storage (DEBIT) |
| `deduction` | `deduction` | `penalties_amount` | C-docs | Withholdings (DEBIT) |
| `acceptance` | `acceptance` | `acceptance_cost_amount` | C-docs | Acceptance fees (DEBIT) |
| `additional_payment` | `additionalPayment` | `compensation_amount` | C-docs | Compensation (CREDIT when positive) |
| `doc_type_name` | `operationType` | — | C | "Продажа" / "Возврат" / "Логистика" etc. |
| `supplier_oper_name` | `operationName` | — | C | Operation detail |
| `sale_dt` | `saleDate` | — | C | Sale date (ISO 8601) |

**DD-13: WB revenue_amount — RESOLVED (2026-03-30)**

`retail_price_withdisc_rub` vs `ppvz_for_pay + ppvz_sales_commission + acquiring_fee`:
- `retail_price_withdisc_rub` = 399.68 (price after product discount, BEFORE SPP buyer loyalty)
- `ppvz_for_pay + commission + acquiring` = 376.99 + 23.74 + 14.89 = 415.62
- Difference = +15.94 (SPP compensation from WB to seller)
- WB absorbs SPP discount: buyer pays less, but WB pays seller based on pre-SPP price

**Решение**: `revenue_amount` = `retail_price_withdisc_rub` для строк с `doc_type_name = 'Продажа'`.

**Обоснование**:
1. Семантически согласовано с Ozon `accruals_for_sale` — оба = цена продавца до комиссий МП
2. `ppvz_vw` — НЕ подходит: это доля продавца после split-а с WB, уже за вычетом WB markup
3. `ppvz_for_pay` — НЕ подходит: это net payout, уже после комиссии и acquiring
4. WB SPP — не уменьшает revenue продавца (WB компенсирует SPP скидку из своих средств)
5. `reconciliation_residual` = `net_payout - Σ(компоненты)` поймает SPP delta

**WB SPP (Special Partner Price) — DD-14: не отдельный компонент P&L**

SPP — скидка для покупателя, оплачиваемая WB (не продавцом). В финансовом отчёте:
- `ppvz_spp_prc` = процент SPP скидки (e.g. 25.31%)
- Покупатель платит: `retail_price_withdisc_rub × (1 - spp/100)`
- Продавец получает на основе `retail_price_withdisc_rub` (ДО SPP)

SPP не включается в формулу P&L как отдельная строка. `revenue_amount` = pre-SPP цена,
что корректно с точки зрения P&L продавца — SPP не снижает его выручку.

### Ozon → NormalizedSaleItem

| Ozon field | Normalized field | Confidence | Notes |
|------------|------------------|------------|-------|
| `posting_number` | `externalSaleId` | C | Use posting_number of delivered posting (DD-3) |
| `products[].offer_id` | `sellerSku` | C | |
| `products[].quantity` | `quantity` | C | |
| `financial_data.products[].price * quantity` | `saleAmount` | C | From financial_data (NUMBER) |
| `financial_data.products[].commission_amount` | `commission` | C | Available in posting financial_data |
| `products[].currency_code` | `currency` | C | |
| `created_at` (or delivery date if tracked) | `saleDate` | A | Posting creation date; actual delivery date needs status tracking |

**Verified changes from initial contract:**
- `financial_data.products[].commission_amount` CONFIRMED available in posting response
- Ozon SALES readiness upgraded from BLOCKED to READY (composite capability, DD-3)

### Via Finance Transactions (alternative/supplement)

| Ozon finance field | Normalized field | Confidence | Notes |
|--------------------|------------------|------------|-------|
| `posting.posting_number` | `externalSaleId` | C | Join key |
| `accruals_for_sale` | `saleAmount` (gross) | C | Positive = revenue |
| `sale_commission` | `commission` | C | Negative = cost |
| `amount` | (net payout) | C | Net of all deductions |
| `operation_date` | `saleDate` | C | "YYYY-MM-DD HH:MM:SS" format! (DD-6) |

### NormalizedSaleItem → CanonicalSale

| Normalized field | Canonical field (DDL: `canonical_sale`) | Confidence | Notes |
|------------------|----------------------------------------|------------|-------|
| `externalSaleId` | `external_sale_id` | C | |
| (resolved) | `marketplace_offer_id` (FK → marketplace_offer) | C | |
| (resolved) | `seller_sku_id` (FK → seller_sku) | C | Via offer lookup |
| `quantity` | `quantity` | C | |
| `saleAmount` | `sale_amount` | C (Ozon) / A (WB) | |
| `commission` | `commission` | C (Ozon) / U (WB) | WB commission not in sales endpoint |
| `currency` | `currency` | C (Ozon) / A (WB) | |
| `saleDate` | `sale_date` | C (Ozon) / C-docs (WB) | |

---

## 6. RETURNS

### WB → NormalizedReturnItem

| WB field | Normalized field | Confidence | Notes |
|----------|------------------|------------|-------|
| `rid` | `externalReturnId` | C-docs | Return identifier |
| (derived from nmId → supplierArticle) | `sellerSku` | A | Need join via catalog |
| (1 per row) | `quantity` | A | Assumed 1 unit per row |
| (not in goods-return) | `returnAmount` | U | Amount not in returns endpoint; in reportDetailByPeriod |
| `returnType` | `returnReason` | C-docs | Return type classification |
| (implicit) | `currency` | A | "RUB" |
| return date field | `returnDate` | U | Exact field name not confirmed; endpoint returned 400 |

**BLOCKER**: WB goods-return endpoint returned 400 for test account.
Must combine with `reportDetailByPeriod` for monetary values.

### Ozon → NormalizedReturnItem

| Ozon field | Normalized field | Confidence | Notes |
|------------|------------------|------------|-------|
| `id` | `externalReturnId` | C | NOT `return_id`! Field is `id` (long) |
| `product.offer_id` | `sellerSku` | C | |
| `product.quantity` | `quantity` | C | |
| `product.price.price` | `returnAmount` | C | Product price at time of return |
| `return_reason_name` | `returnReason` | C | |
| `product.price.currency_code` | `currency` | C | Explicit "RUB" |
| `logistic.return_date` | `returnDate` | C | ISO 8601 UTC |

**Verified changes from initial contract:**
- Return identifier is `id`, NOT `return_id` (verified)
- `product.price` IS present (object with `currency_code` + `price`) (verified)
- `product.commission` IS present (verified)
- `logistic.return_date` CONFIRMED as return date field (ISO 8601 UTC)
- Ozon RETURNS readiness upgraded from PARTIAL to READY

### Additional Ozon Return Fields (available for enrichment)

| Ozon field | Semantics | Confidence |
|------------|-----------|------------|
| `product.price_without_commission` | Price minus commission | C |
| `product.commission_percent` | Commission % | C |
| `product.commission.price` | Commission amount | C |
| `visual.status.sys_name` | Machine-readable status | C |
| `type` | Return type ("Cancellation", etc.) | C |
| `schema` | Fulfillment schema ("Fbo" / "Fbs") | C |
| `logistic.final_moment` | Final processing timestamp | C |
| `storage.sum.price` | Storage cost | C |
| `storage.days` | Storage days | C |

### NormalizedReturnItem → CanonicalReturn

| Normalized field | Canonical field (DDL: `canonical_return`) | Confidence | Notes |
|------------------|------------------------------------------|------------|-------|
| `externalReturnId` | `external_return_id` | C | |
| (resolved) | `marketplace_offer_id` (FK → marketplace_offer) | C | |
| (resolved) | `seller_sku_id` (FK → seller_sku) | C | Via offer lookup |
| `quantity` | `quantity` | C (Ozon) / A (WB) | |
| `returnAmount` | `return_amount` | C (Ozon) / U (WB) | |
| `returnReason` | `return_reason` | C | |
| `currency` | `currency` | C (Ozon) / A (WB) | |
| `returnDate` | `return_date` | C (Ozon) / U (WB) | |

---

## 7. FINANCES

### WB → NormalizedFinanceItem

| WB field | Normalized field | Confidence | Notes |
|----------|------------------|------------|-------|
| `doc_type_name` + `supplier_oper_name` | `entryType` | A | Must map to FinanceEntryType enum |
| `srid` | `externalRef` | C | Sandbox+docs verified |
| `sa_name` | `sellerSku` | C | Sandbox verified (sa_name = supplier article) |
| per-field amounts (see below) | `amount` | C-docs | Which field depends on entryType |
| (implicit) | `currency` | C-docs | "RUB" (`currency_name` = "руб" in docs) |
| `sale_dt` | `entryDate` | C-docs | DD-9: parse both date-only and ISO 8601. **`sale_dt` only** — `rr_dt` is report attribution metadata, not economic event date (T-1 invariant) |
| `realizationreport_id` | `reportId` | C | Report grouping key |
| `rrd_id` | `rowId` | C | Row-level unique ID, pagination cursor |

**SIGN CONVENTION (DD-7):** All WB finance values are POSITIVE absolute amounts.
Field name determines credit/debit semantics. When normalizing to canonical:
- CREDIT fields (positive in canonical): `ppvz_for_pay`, `ppvz_vw`, `additional_payment`
- DEBIT fields (negate in canonical): all cost/fee fields below

**WB Finance amount mapping by operation type:**

| Operation | Relevant WB field | → NormalizedFinanceItem.amount | Confidence | Canonical sign |
|-----------|-------------------|-------------------------------|------------|----------------|
| Revenue | `retail_price_withdisc_rub` | amount | C-docs | positive (revenue), docs: 399.68 (see DD-13) |
| Net payout | `ppvz_for_pay` | amount | C | positive (credit), sandbox: 0 in test |
| Commission | `ppvz_sales_commission` | amount | C | negate (debit), docs: 23.74 |
| Logistics | `delivery_rub` | amount | C | negate (debit), sandbox: **20** |
| Reverse logistics | `rebill_logistic_cost` | amount | C | negate (debit), sandbox: **1.349** |
| Penalty | `penalty` | amount | C-docs | negate (debit), docs: 231.35 |
| Compensation | `additional_payment` | amount | C | positive (credit), sandbox: 0 |
| Acquiring | `acquiring_fee` | amount | C-docs | negate (debit), docs: 14.89 |
| Storage | `storage_fee` | amount | C-docs | negate (debit), docs: 12647.29 (optional in response) |
| Acceptance | `acceptance` | amount | C-docs | negate (debit), docs: 865 (optional in response) |
| Deduction | `deduction` | amount | C-docs | negate (debit), docs: 6354 (optional in response) |

**New v5 fields affecting P&L (DD-10, optional in response):**

| Operation | Relevant WB field | → NormalizedFinanceItem.amount | Confidence | Canonical sign |
|-----------|-------------------|-------------------------------|------------|----------------|
| Cashback | `cashback_amount` | amount | C-docs | negate (debit), absent in sandbox |
| Cashback commission delta | `cashback_commission_change` | amount | C-docs | depends on sign, absent in sandbox |
| Seller promo | `seller_promo_discount` | amount | C-docs | negate (debit), absent in sandbox |
| Loyalty | `loyalty_discount` | amount | C-docs | negate (debit), absent in sandbox |

**DD-8**: One WB `reportDetailByPeriod` row contains MULTIPLE financial
dimensions simultaneously (commission + delivery + penalty in same row).
**Composite row model**: одна WB row → одна `NormalizedFinanceItem` → одна `canonical_finance_entry` → одна `fact_finance` row. Все financial measures (commission, delivery, penalty и т.д.) хранятся как отдельные поля в одном record. Splitting не выполняется.

**DD-9**: Timestamp fields `rr_dt`, `date_from`, `date_to`, `create_dt` require
dual-format parser (date-only OR ISO 8601 datetime). See wb-read-contracts.md v5.

**DD-10**: Fields `storage_fee`, `deduction`, `acceptance`, `cashback_*`,
`seller_promo_*`, `loyalty_*` may be absent from response.
Use `@JsonIgnoreProperties(ignoreUnknown = true)` + nullable types.

### Ozon → NormalizedFinanceItem

| Ozon field | Normalized field | Confidence | Notes |
|------------|------------------|------------|-------|
| `operation_type` | `entryType` | C | Map via operation_type → FinanceEntryType (see table below) |
| `operation_id` | `externalRef` | C | Long, unique per transaction |
| `items[0].sku` → lookup → `offer_id` | `sellerSku` | A | `items[]` has `sku` only, NOT `offer_id`; lookup required |
| `amount` | `amount` | C | Net amount (DD-4: positive = credit, negative = debit) |
| (implicit) | `currency` | C | RUB (verified from context) |
| `operation_date` | `entryDate` | C | Parse as "yyyy-MM-dd HH:mm:ss" NOT ISO 8601 (DD-6) |

### Ozon operation_type → FinanceEntryType — VERIFIED (2026-03-30, real data, 7590 ops / Jan 2025)

| Ozon operation_type | `type` cat | → FinanceEntryType | → fact_finance measure | Confidence | Sign |
|---------------------|------------|---------------------|------------------------|------------|------|
| `OperationAgentDeliveredToCustomer` | orders | SALE_ACCRUAL | `revenue_amount` (accruals_for_sale) | C | amount > 0 |
| `ClientReturnAgentOperation` | returns | RETURN_REVERSAL | `refund_amount` (accruals_for_sale < 0) | C | amount < 0 |
| `OperationAgentStornoDeliveredToCustomer` | returns | STORNO_CORRECTION | `refund_amount` | C | amount > 0 |
| `OperationItemReturn` | returns | RETURN_LOGISTICS | `logistics_cost_amount` | C | amount < 0 |
| `MarketplaceRedistributionOfAcquiringOperation` | other | ACQUIRING | `acquiring_commission_amount` | C | amount < 0 |
| `MarketplaceServiceBrandCommission` | services | BRAND_COMMISSION | `marketplace_commission_amount` | C | amount < 0 |
| `MarketplaceServiceItemCrossdocking` | services | LOGISTICS | `logistics_cost_amount` | C | amount < 0 |
| `OperationElectronicServiceStencil` | services | PACKAGING | `other_marketplace_charges_amount` | C | amount < 0 |
| `OperationMarketplaceServiceStorage` | services | STORAGE | `storage_cost_amount` | C | amount < 0 |
| `StarsMembership` | services | SUBSCRIPTION | `other_marketplace_charges_amount` | C | amount < 0 |
| `MarketplaceSaleReviewsOperation` | services | REVIEWS_PURCHASE | `marketing_cost_amount` | C | amount < 0 |
| `DisposalReasonFailedToPickupOnTime` | services | DISPOSAL | `penalties_amount` | C | amount < 0 |
| `DisposalReasonDamagedPackaging` | services | DISPOSAL | `penalties_amount` | C | amount < 0 |
| `AccrualInternalClaim` | compensation | COMPENSATION | `compensation_amount` | C | amount > 0 |
| `AccrualWithoutDocs` | compensation | COMPENSATION | `compensation_amount` | C | amount > 0 |
| `MarketplaceSellerCompensationOperation` | compensation | COMPENSATION | `compensation_amount` | C | amount > 0 |
| `OperationReturnGoodsFBSofRMS` | returns | FBS_RETURN_LOGISTICS | `logistics_cost_amount` | C | amount < 0 |

**17 operation types verified from real data (Jan 2025, 7590 operations).**
Unmapped types MUST default to OTHER with logging.

**CRITICAL: One posting → multiple finance operations.** A single delivery (posting "87621408-0010-1")
generates 3 separate operations: `OperationAgentDeliveredToCustomer` (sale + commission + services),
`MarketplaceServiceBrandCommission` (-0.79), `StarsMembership` (-0.79). All share the same `posting_number`.
Per-posting P&L requires aggregating ALL operations by `posting_number`.

### Ozon services[].name → fact_finance measure classification — VERIFIED (2026-03-30)

| Service name | → fact_finance measure | Count (Jan) | Total (Jan) | Confidence |
|-------------|------------------------|-------------|-------------|------------|
| `MarketplaceServiceItemDirectFlowLogistic` | `logistics_cost_amount` | 1917 | -119,908 | C |
| `MarketplaceServiceItemDelivToCustomer` | `logistics_cost_amount` | 1838 | -14,557 | C |
| `MarketplaceServiceItemReturnFlowLogistic` | `logistics_cost_amount` | 100 | -6,146 | C |
| `MarketplaceServiceItemReturnNotDelivToCustomer` | `logistics_cost_amount` | 64 | 0 | C |
| `MarketplaceServiceItemReturnAfterDelivToCustomer` | `logistics_cost_amount` | 36 | 0 | C |
| `MarketplaceServiceItemRedistributionReturnsPVZ` | `logistics_cost_amount` | 60 | -900 | C |
| `MarketplaceServiceItemRedistributionDropOffApvz` | `logistics_cost_amount` | 7 | -105 | C |
| `MarketplaceServiceItemDropoffPVZ` | `logistics_cost_amount` | 7 | -105 | C |
| `ItemAgentServiceStarsMembership` | `other_marketplace_charges_amount` | 1854 | -1,466 | C |
| `MarketplaceRedistributionOfAcquiringOperation` | `acquiring_commission_amount` | 1648 | -2,864 | C |
| `MarketplaceServiceBrandCommission` | `marketplace_commission_amount` | 1847 | -1,398 | C |
| `MarketplaceServiceItemDisposalDetailed` | `penalties_amount` | 4 | -300 | C |

### Ozon Finance Breakdown Fields (per sale operation)

| Field | Semantics | Sign | Confidence |
|-------|-----------|------|------------|
| `accruals_for_sale` | Buyer-paid price × quantity (after all discounts, before marketplace fees) | + for sale, − for return | C |
| `sale_commission` | Sales commission | − for sale, + for return refund | C |
| `services[].price` | Service costs (logistics, etc.) | − (cost) | C |
| `amount` | Net = accruals + commission + Σservices | ± | C |
| `delivery_charge` | Always 0 (costs in services instead) | 0 | C |
| `return_delivery_charge` | Always 0 (costs in services instead) | 0 | C |

**DD-11: accruals_for_sale = seller-facing price (NOT necessarily buyer-paid)**

Verified by cross-referencing postings and finance:
- FBO posting: `products[].price` = 105, no `customer_price` field → buyer pays 105 = seller price
- FBS posting: `products[].price` = 357, `customer_price` = 299 → buyer pays LESS than seller price!
  - Commission: -85.32 (23% of 357 seller price, NOT of 299 customer price)
  - Payout: 271.68 = 357 - 85.32 ✓
  - Ozon absorbs 357 - 299 = 58 RUB as marketing discount

**Conclusion**: `accruals_for_sale` = `financial_data.products[].price` (seller-facing price).
For FBO, seller price = buyer price. For FBS with Ozon marketing, seller price > buyer price.
This is analogous to WB's SPP where marketplace absorbs discount.
`customer_price` is only present in FBS postings (FBO postings don't have this field).

**Impact on P&L**: `revenue_amount` = `accruals_for_sale` = seller's revenue recognition price.
The Ozon/WB marketing subsidy is internal to the marketplace and does NOT reduce seller revenue.
This is consistent with the WB model where `retail_price_withdisc_rub` > buyer-paid price due to SPP.

### NormalizedFinanceItem → CanonicalFinanceEntry

| Normalized field | Canonical field (DDL: `canonical_finance_entry`) | Confidence | Notes |
|------------------|--------------------------------------------------|------------|-------|
| `entryType` | `entry_type` → `FinanceEntryType` | C (Ozon) / A (WB) | |
| `externalRef` | `external_entry_id` | C | rrd_id (WB), operation_id (Ozon) |
| (resolved) | `seller_sku_id` (FK → seller_sku) | C | Via offer lookup; NULL if SKU not found |
| `amount` | `amount` | C (Ozon) / A (WB) | Ozon sign convention verified |
| `currency` | `currency` | C (Ozon) / A (WB) | |
| `entryDate` | `entry_date` | C (Ozon) / A (WB) | Ozon: custom format! |

---

## 8. PROMO

Promo mapping (WB + Ozon → CanonicalPromoCampaign, CanonicalPromoProduct) documented in a separate file: [promo-advertising-contracts.md](promo-advertising-contracts.md).

Referenced from [ETL Pipeline](../modules/etl-pipeline.md) §Promo canonical entities.

---

## Cross-Capability Join Key Summary

### WB Join Keys (docs-confirmed)

```
nmID (catalog) ←→ nmId (prices) ←→ nmId (orders/sales) ←→ nm_id (finance)
vendorCode (catalog) = supplierArticle (orders/sales/finance) = seller's SKU
sizes[].skus[] (catalog) = barcode (orders/sales/stocks)
srid links orders ↔ sales ↔ finance rows
```

### Ozon Join Keys (empirically verified)

```
product_id / id (catalog) ←→ product_id (prices) ←→ product_id (stocks)
offer_id (catalog) = offer_id (prices/stocks/postings) = seller's SKU
sku (stocks) ↔ sku (postings) ↔ items[].sku (finance) — Ozon system SKU
posting_number links orders ↔ sales ↔ returns ↔ finance (sale/brand/stars operations)

ACQUIRING JOIN (DD-15):
  acquiring.posting_number = posting.order_number (without -N suffix)
  Join: strip last "-N" from posting_number to get order_number
  Example: posting "0151413710-0012-1" → acquiring "0151413710-0012"

STANDALONE OPS (storage, disposal, reviews, compensation):
  posting_number = "" → use operation_id as unique key
  Pro-rata allocation needed for per-order P&L

WARNING: finance items[] has sku + name only, NOT offer_id!
         Join: items[].sku → catalog sources[].sku → product_id → offer_id
```

---

## Mapping Readiness Summary

| Capability | WB Readiness | Ozon Readiness | Blockers |
|------------|-------------|----------------|----------|
| CATALOG | **READY** (sandbox) | READY | Both fully verified |
| PRICES | **READY** (sandbox) | READY | WB: per-size pricing hierarchy confirmed; Ozon: resolved (DD-1) |
| STOCKS | PARTIAL | READY | WB: no warehouses in sandbox; Ozon: resolved (DD-2) |
| SUPPLY | **READY** (sandbox) | N/A | WB: endpoint works (deprecated June 2026); Ozon: no supply concept |
| WAREHOUSES | **READY** (sandbox+prod) | READY | WB: /api/v3/offices (108/225), Ozon: resolved (DD-2) |
| ORDERS | **READY** (sandbox) | READY | Both verified; WB: isCancel/cancelDate confirmed |
| SALES | **READY** (sandbox) | READY (composite) | WB: forPay/finishedPrice/priceWithDisc verified; Ozon: resolved (DD-3) |
| RETURNS | **READY** | READY | WB: unblocked — date-only format was root cause; Ozon: all fields verified |
| FINANCES | **READY** (sandbox+docs) | READY | Both fully verified: WB sign convention confirmed (DD-7); Ozon: DD-4 |

### Resolved Blockers

1. ~~CanonicalSale.commission (Ozon)~~ — **CONFIRMED** via `financial_data.products[].commission_amount` in posting
2. ~~CanonicalReturn.returnAmount (Ozon)~~ — **CONFIRMED** via `product.price.price` in returns endpoint
3. ~~CanonicalReturn.returnDate (Ozon)~~ — **CONFIRMED** as `logistic.return_date` (ISO 8601 UTC)
4. ~~CanonicalFinanceEntry sign semantics (Ozon)~~ — **CONFIRMED** (DD-4): positive = credit, negative = debit
5. ~~CanonicalFinanceEntry.entryType mapping (Ozon)~~ — **10 types mapped** from empirical data
6. ~~CanonicalPriceSnapshot.discountPrice (Ozon)~~ — **RESOLVED** (DD-1): `marketing_seller_price`
7. ~~CanonicalStockSnapshot.reserved (Ozon)~~ — **CONFIRMED** present in v4 response

### Remaining Blockers

1. ~~WB Finance sign convention~~ — **RESOLVED**: confirmed via sandbox + official docs
2. ~~WB Returns~~ — **RESOLVED**: endpoint works with date-only format `YYYY-MM-DD`
3. ~~WB Stocks field names~~ — PARTIAL: no warehouses in sandbox; endpoint accessible
4. ~~WB Sales commission~~ — **RESOLVED**: `forPay` confirmed in sales endpoint; detailed commission in finance only
5. ~~WB Price markup~~ — **RESOLVED**: `price` → `discountedPrice` → `clubDiscountedPrice` hierarchy confirmed
6. ~~Ozon Brand~~ — **RESOLVED**: via `POST /v4/product/info/attributes` (attribute_id=85)
7. **Ozon FBS** — NOT empirically tested (FBS endpoint returns 400 for FBO-only accounts)
8. ~~WB revenue_amount~~ — **RESOLVED (DD-13)**: `retail_price_withdisc_rub`
9. ~~Ozon acquiring join~~ — **RESOLVED (DD-15)**: acquiring uses `order_number` format, join possible
10. ~~Ozon storage attribution~~ — **RESOLVED (DD-16)**: daily aggregate, pro-rata allocation
11. ~~WB SPP~~ — **RESOLVED (DD-14)**: не отдельный компонент P&L, WB компенсирует из своих средств
12. ~~P&L formula~~ — **RESOLVED**: обновлена до 13 компонентов (storage, acceptance добавлены)
13. ~~seller_discount_amount~~ — **RESOLVED**: удалён из measures
14. ~~Double refund counting~~ — **RESOLVED**: revenue spine = only sales, refunds aggregated separately

### Required Actions Before Full Implementation

1. ~~WB with data~~ — **RESOLVED**: Sandbox account provides test data for all endpoints (finance, sales, orders, incomes, offices)
2. ~~WB finance verification~~ — **DONE** (from official API documentation sample)
3. ~~WB returns~~: **DONE** — endpoint works; root cause was date format, not token scope
4. **Ozon FBS**: Test with FBS-enabled account (not a blocker — same contract as FBO per docs)
5. ~~Ozon brand enrichment~~ — **DONE** (attribute_id=85 via v4/product/info/attributes)
6. ~~Revenue mapping~~ — **DONE** (DD-13: WB=retail_price_withdisc_rub, Ozon=accruals_for_sale)
7. ~~Acquiring join~~ — **DONE** (DD-15: order_number format join)
8. ~~P&L completeness~~ — **DONE** (13 components, all mapped to provider fields)

### What CAN be Implemented Now (Ozon)

All 7 Ozon capabilities have sufficient contract clarity for implementation:
- CATALOG: v3 endpoints + brand via v4/attributes, all fields mapped
- PRICES: v5 endpoint, nested price object, design decision made (DD-1)
- STOCKS: v4 endpoint, warehouse strategy decided (DD-2)
- ORDERS: FBO verified (FBS follows same contract per docs)
- SALES: composite capability design confirmed (DD-3)
- RETURNS: v1 endpoint with full financial data
- FINANCES: sign convention verified, 10 operation types mapped (DD-4)

### What CAN be Implemented Now (WB)

5 out of 7 WB capabilities are now READY for implementation:
- **CATALOG**: READY — all fields sandbox-verified (brand, vendorCode, nmID, sizes, dimensions, timestamps)
- **PRICES**: READY — per-size pricing sandbox-verified (price, discountedPrice, clubDiscountedPrice, currency)
- **ORDERS**: READY — sandbox-verified (totalPrice, discountPercent, isCancel, cancelDate, srid)
- **SALES**: READY — sandbox-verified (forPay, finishedPrice, priceWithDisc, saleID prefix, spp)
- **FINANCES**: READY — sandbox + official docs (sign convention, all amount fields, timestamps)

### What CAN be Implemented Now (WB, additionally)

- **SUPPLY/INCOMES**: READY — sandbox-verified (incomeId, supplierArticle, nmId, quantity, warehouseName, status)
- **WAREHOUSES (dim_warehouse)**: READY — offices endpoint verified (sandbox: 108, production: 225 WB warehouses)

### What Requires Further Verification (WB)

- **WB STOCKS**: PARTIAL — no warehouses in sandbox; endpoint accessible but no field-level data

### Write Contracts

Write contracts documented in `write-contracts.md`. Key findings:

- **WB Price Write**: `POST /api/v2/upload/task` — async model (upload → poll → verify)
- **Ozon Price Write**: `POST /v1/product/import/prices` — synchronous model
- **CRITICAL**: WB host `discounts-api.wildberries.ru` no longer resolves; migrated to `discounts-prices-api.wildberries.ru`
- **Reconciliation read-after-write**: endpoints available (WB: `/api/v2/list/goods/filter`, Ozon: `/v5/product/info/prices`) but verification logic not implemented
- **WB token scope**: production token returns 401 for write endpoint — needs "Prices and Discounts → Write" scope

| Capability | WB | Ozon | Contract |
|------------|----|------|----------|
| Price Write | **BROKEN** (host DNS + token 401) | READY | write-contracts.md §1.1, §2.1 |
| Write Poll | **BROKEN** (same host) | N/A | write-contracts.md §1.2 |
| Reconciliation Read | READY | READY | write-contracts.md §1.3, §2.2 |
| Reconciliation Logic | NOT IMPLEMENTED | NOT IMPLEMENTED | ADR-016 gap |

### Advertising Contracts (Phase B extended)

Advertising read contracts documented in `promo-advertising-contracts.md` §2 (WB) and §4 (Ozon).

| Capability | WB | Ozon | Contract |
|------------|----|------|----------|
| Ad Campaigns (dim) | NEEDS WORK (v2 DTO expansion) | NEEDS WORK (OAuth2 + adapter) | promo-advertising-contracts.md §2.1, §4.2 |
| Ad Stats (fact) | NEEDS WORK (v3 POST→GET migration) | NEEDS WORK (OAuth2 + async flow) | promo-advertising-contracts.md §2.2, §4.3 |

**WB Advertising field mapping** (fullstats v3 → `fact_advertising`):

| API field | ClickHouse column | Transform |
|-----------|-------------------|-----------|
| `advertId` | `campaign_id` | Direct |
| `days[].date` | `ad_date` | Parse `YYYY-MM-DD` |
| `days[].apps[].nms[].nmId` | `marketplace_sku` | Cast to String |
| `days[].apps[].nms[].views` | `views` | Direct |
| `days[].apps[].nms[].clicks` | `clicks` | Direct |
| `days[].apps[].nms[].sum` | `spend` | Direct (RUB) |
| `days[].apps[].nms[].orders` | `orders` | Direct |
| `days[].apps[].nms[].shks` | `ordered_units` | Direct |
| `days[].apps[].nms[].sum_price` | `ordered_revenue` | Direct (RUB) |
| `days[].apps[].nms[].canceled` | `canceled` | Direct |
| `days[].apps[].nms[].ctr` | `ctr` | Direct (%) |
| `days[].apps[].nms[].cpc` | `cpc` | Direct (RUB) |
| `days[].apps[].nms[].cr` | `cr` | Direct (%) |

**Flatten rule:** Hierarchical response (campaign → days → apps → nms) flattened into one row per `(campaign_id, ad_date, marketplace_sku)`. `appType` collapsed (aggregated across apps within same day/product).

**Ozon Advertising field mapping** (Performance report → `fact_advertising`):

| API field | ClickHouse column | Transform |
|-----------|-------------------|-----------|
| campaign `id` | `campaign_id` | Direct |
| report `date` | `ad_date` | Parse `YYYY-MM-DD` |
| `views` | `views` | Direct |
| `clicks` | `clicks` | Direct |
| `moneySpent` | `spend` | Parse String → Decimal |
| `orders` | `orders` | Direct (if available) |

> Ozon `marketplace_sku` mapping requires cross-referencing campaign product list.
> Full field inventory pending empirical verification (see promo-advertising-contracts.md §4.3).
