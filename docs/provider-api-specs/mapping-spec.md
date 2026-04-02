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

### DD-9: WB Finance Timestamp Format — Dual Parser Required (updated 2026-03-31)

**Решение:** WB finance timestamp поля имеют **разный формат** в official docs vs sandbox:

| Поле | Official docs (v5 sample) | Sandbox (verified 2026-03-31) |
|------|---------------------------|-------------------------------|
| `sale_dt` | `"2022-10-20T00:00:00Z"` (datetime) | `"2026-01-02T06:54:29Z"` (datetime) |
| `order_dt` | `"2022-10-13T00:00:00Z"` (datetime) | `"2026-01-02T06:54:29Z"` (datetime) |
| `rr_dt` | `"2022-10-20"` (date-only) | `"2026-01-02T06:54:29Z"` (datetime!) |
| `date_from` | `"2022-10-17"` (date-only) | `"2025-12-31T23:54:29Z"` (datetime!) |
| `date_to` | `"2022-10-23"` (date-only) | `"2026-01-01T22:54:29Z"` (datetime!) |
| `create_dt` | `"2022-10-24"` (date-only) | `"2026-01-01T22:54:29Z"` (datetime!) |
| `fix_tariff_date_from` | — | `"2024-10-23"` (date-only) |
| `fix_tariff_date_to` | — | `"2024-11-18"` (date-only) |

**Обоснование:** Official docs v5 sample показывает date-only для `rr_dt`, `date_from`,
`date_to`, `create_dt`. Sandbox API возвращает full ISO 8601 datetime для всех этих полей.
`order_dt` и `sale_dt` в обоих источниках — ISO 8601 datetime.
`fix_tariff_date_from/to` в sandbox — date-only (единственные стабильно date-only поля).

**Реализация:** WB finance adapter ДОЛЖЕН использовать flexible parser:
попытка ISO 8601 datetime → fallback на date-only → LocalDate.atStartOfDay(ZoneOffset.UTC).

### DD-17: WB sale_dt Nullability — sandbox-verified (2026-03-31)

**Решение:** `sale_dt` **всегда заполнен** в sandbox, включая non-sale entry types
(logistics entries с `delivery_rub > 0`, `quantity = 0`).

**Обоснование:** Sandbox response (177 KB, ~100 records) — все записи имеют `sale_dt`
с ISO 8601 datetime. Записи являются logistics/delivery entries (`delivery_amount: 1`,
`delivery_rub: 20`, `retail_price: 0`, `ppvz_for_pay: 0`).

**Ограничение:** Sandbox не генерирует entry types `storage_fee`, `penalty`, `deduction`
(все эти поля = 0 в sandbox). Для этих типов `sale_dt` **not confirmed on real data**.

**Fallback strategy (обязателен):** Если `sale_dt` is null/empty → использовать `rr_dt`
(report settlement date) как fallback для `canonical_finance_entry.entryDate`.
Это гарантирует NOT NULL constraint и корректную привязку к периоду P&L.

### DD-18: WB canonical_sale.external_sale_id = srid (2026-03-31)

**Решение:** Для WB `canonical_sale.external_sale_id` = `srid` из `reportDetailByPeriod`.
НЕ `saleID` из `/api/v1/supplier/sales` endpoint.

**Обоснование:**
- `canonical_sale` для WB заполняется из `reportDetailByPeriod` (DD-12: finance report = source of truth)
- Finance report не содержит поля `saleID` (это поле только в sales endpoint)
- `srid` — единственный стабильный идентификатор, присутствующий и в finance, и в sales, и в orders
- `srid` = unique shipment/row identifier, фактически grain key строки отчёта

**Sales endpoint** (`/api/v1/supplier/sales`):
- `saleID` (e.g. "S3207347857") → operational monitoring только, не записывается в canonical
- `srid` → cross-reference key для связи с finance report

**Последствие:** `canonical_sale.external_sale_id` для WB — это shipment-level ID, не sale ID.
Naming `external_sale_id` не полностью точен, но это documented limitation.

### DD-10: WB Finance Optional Fields (v5 Additions) — EXPANDED 2026-03-31

**Решение:** 25+ полей из official v5 docs отсутствуют в sandbox response.
Считать их опциональными. Adapter: `@JsonIgnoreProperties(ignoreUnknown = true)`.

**Обоснование:** Sandbox генерирует упрощённые test data с subset полей.
Official v5 sample (dev.wildberries.ru, 2026-03-31) содержит все эти поля.

**Полный список optional v5 полей (confirmed-docs):**

| Поле | Тип | Семантика | P&L relevance |
|------|-----|-----------|---------------|
| `cashback_amount` | number | Cashback amount | YES — влияет на net payout |
| `cashback_discount` | number | Cashback discount | YES |
| `cashback_commission_change` | number | Cashback commission adjustment | YES |
| `seller_promo_id` | int | Seller promo campaign ID | Informational (promo attribution) |
| `seller_promo_discount` | number | Seller promo discount amount | YES — seller-funded promo cost |
| `loyalty_id` | int | Loyalty program ID | Informational |
| `loyalty_discount` | number | Loyalty program discount | YES |
| `uuid_promocode` | string | Promo code UUID | Informational |
| `sale_price_promocode_discount_prc` | number | Promo code discount % | Informational |
| `order_uid` | string | Order unique ID (new v5) | Informational (cross-reference) |
| `report_type` | int | Report type flag | Informational |
| `delivery_method` | string | Delivery method (e.g. "FBS, (МГТ)") | Informational (segmentation) |
| `kiz` | string | Marking code (Честный знак) | Informational (compliance) |
| `trbx_id` | string | Transport box ID (e.g. "WB-TRBX-1234567") | Informational (logistics) |
| `is_legal_entity` | boolean | Legal entity flag | Informational |
| `installment_cofinancing_amount` | number | Installment co-financing | YES — impacts payout calculation |
| `wibes_wb_discount_percent` | number | WB discount percentage | Informational |
| `payment_schedule` | int | Payment schedule flag | Informational |
| `fix_tariff_date_from` | string | Fixed tariff period start (date-only) | Informational |
| `fix_tariff_date_to` | string | Fixed tariff period end (date-only) | Informational |
| `dlv_prc` | number | Delivery percentage | Informational |
| `srv_dbs` | boolean | DBS (delivery by seller) flag | Informational (fulfillment type) |
| `payment_processing` | string | Payment processing description | Informational |
| `acquiring_bank` | string | Acquiring bank name | Informational |
| `site_country` | string | Marketplace country ("RU") | Informational |

**Для P&L:** Поля `cashback_amount`, `cashback_discount`, `cashback_commission_change`, `seller_promo_discount`,
`loyalty_discount`, `installment_cofinancing_amount` влияют на расчёт итоговой суммы
к выплате. Canonical measure assignment: см. DD-19 в §NormalizedFinanceItem → CanonicalFinanceEntry.

**StarsMembership:** Отсутствует в official v5 sample (2026-03-31). Видимо, заменено
программой лояльности (`loyalty_id`, `loyalty_discount`) и кешбеком (`cashback_*`).
Парсер должен обрабатывать оба набора полей для backward compatibility.

### DD-15: Ozon Acquiring Join Key — DUAL FORMAT (updated 2026-03-31)

**Решение:** Ozon `MarketplaceRedistributionOfAcquiringOperation` использует **ДВА формата** `posting_number`:
- **2-part format** (order_number, без суффикса `-N`): e.g. `"93284743-0263"` — 57% acquiring ops
- **3-part format** (полный posting_number, с суффиксом `-N`): e.g. `"39222582-0174-1"` — 43% acquiring ops

**Обоснование:** Эмпирически верифицировано (2026-03-31, 190 acquiring ops / Feb 2026):
- 109/190 (57%) — 2-part format (order_number)
- 81/190 (43%) — 3-part format (full posting_number)
- Cross-reference с 182 sale ops:
  - 81 exact posting_number match (3-part acquiring = exact match to sale)
  - 78 order_number match (2-part acquiring → strip -N from sale posting → match)
  - 31 no match (cross-month: acquiring in Feb для sales в Jan; расширение date range покроет)

**Предыдущая версия DD-15 (2026-03-30)** утверждала, что acquiring ВСЕГДА использует order_number format.
Это было верно для наблюдённого примера, но не для всей выборки.

**Join strategy для per-order P&L (обновлённая):**
1. Exact match: `acquiring.posting_number = sale.posting_number` (covers 3-part format)
2. Order match: strip `-N` от `sale.posting_number` → compare с 2-part `acquiring.posting_number`
3. Unmatched: cross-month operations → resolve при расширении date window

**Реализация:** `if (acq.posting_number == sale.posting_number) → match; else strip(sale.posting_number) == acq.posting_number → match`

**Wider join window (recommended):** Для 16% unmatched ops (31 из 190 в тесте) причина —
cross-month boundary: acquiring в Feb для sale из Jan. Решение:
- Finance ingestion query ДОЛЖЕН запрашивать период **±1 месяц** от целевого
- Join window: `sale.operation_date BETWEEN acq.operation_date - INTERVAL '35 days' AND acq.operation_date + INTERVAL '5 days'`
- Unmatched после wider window → `attribution_level = 'ACCOUNT'` (pro-rata allocation)
- Monitoring: `unmatched_acquiring_ratio` per connection per month → alert if > 5%

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
| `imtID` | `marketplaceSkuAlt` | C | IMT-модель карточки (группирует sizes). Join key для WB internal grouping |
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
| `sources[].sku` | `marketplaceSkuAlt` | C | FBS/FBO warehouse SKU. Multiple values possible; take first non-null. Join key для Ozon finance/postings |
| `name` | `name` | C | |
| via `/v4/product/info/attributes`, attr_id=85 | `brand` | C | Brand via attribute API; `values[0].value` where `id==85` |
| `description_category_id` | `category` | C | Category ID (replaces deprecated `category_id`); name requires lookup |
| `barcodes[0]` | `barcode` | C | Array field; take first element |
| `is_archived` + `is_autoarchived` | `status` | C | `false` + `false` → ACTIVE; any `true` → ARCHIVED (aligned with marketplace_offer DDL: ACTIVE / ARCHIVED / BLOCKED) |

**Verified changes from initial contract:**
- `category_id` → `description_category_id` (verified)
- `barcode` → `barcodes[]` (array, verified)
- `visible` + `status` → `is_archived` + `is_autoarchived` (verified; maps to ACTIVE/ARCHIVED per DDL)
- `brand` RESOLVED via v4/product/info/attributes (attribute_id=85, confirmed)
- `updated_at` IS present in v3/product/info/list (confirmed 2026-03-31; previously documented as absent)

**Acceptable limitation:** `type_id` (Ozon product type ID, confirmed in v3/product/info/list)
is NOT mapped to NormalizedCatalogItem. Reason: no corresponding field in canonical model
(`product_master`, `seller_sku`, `marketplace_offer`). If needed for analytics:
add `type_id` to `marketplace_offer` as nullable field + join to categories via
`description_category_id` + `type_id` → `/v1/description-category/tree`.

**Optimization opportunity:** `updated_at` from v3/product/info/list can enable incremental
catalog sync (only fetch products updated since last sync). Not required for Phase A/B but
recommended for Phase C+ to reduce API calls. See DD in etl-pipeline.md.

### NormalizedCatalogItem → CanonicalOffer

CanonicalOffer реализована как три таблицы: `product_master`, `seller_sku`, `marketplace_offer`. См. [ETL Pipeline](../modules/etl-pipeline.md) §Связи каталожных сущностей.

| Normalized field | Canonical target table.field | Confidence | Notes |
|------------------|------------------------------|------------|-------|
| `sellerSku` | `product_master.external_code` | C | Phase A default: `external_code` = `sellerSku` (1:1 с seller_sku). Кросс-маркетплейсное объединение — Phase C (ручной merge или auto-match по barcode/name) |
| `sellerSku` | `seller_sku.sku_code` | C | Direct mapping |
| `marketplaceSku` | `marketplace_offer.marketplace_sku` | C | Direct mapping. WB: `nmID`; Ozon: `product_id` |
| `marketplaceSkuAlt` | `marketplace_offer.marketplace_sku_alt` | C | WB: `imtID` (IMT-модель карточки); Ozon: `sku` (FBS/FBO warehouse SKU). Nullable |
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
| `discountPercent` | `discount_pct` | C (WB) / B (Ozon) | WB: `discount` (seller discount %). Ozon: computed `((old_price - price) / old_price * 100)` if `old_price > 0`, else NULL |
| `minPrice` | `min_price` | C (Ozon) / — (WB) | Ozon: `price.min_price` (marketplace-enforced floor). WB: NULL (нет аналога в API) |
| `maxPrice` | `max_price` | — | Neither provider exposes max price constraint. Reserved for future use. Always NULL |
| (ingestion time) | `captured_at` | C | Set from rawArtifact.capturedAt |

---

## 3. STOCKS

### WB → NormalizedStockItem — CONFIRMED from Official Docs (2026-03-31)

| WB field | Normalized field | Confidence | Notes |
|----------|------------------|------------|-------|
| `nmId` → lookup via `marketplace_offer.marketplace_sku` → `seller_sku.sku_code` | `sellerSku` | C-docs | No `vendorCode` in stocks response; join via `nmId` |
| `warehouseId` | `warehouseId` | C-docs | Int, WB warehouse identifier |
| `quantity` | `available` | C-docs | Available stock (per chrtId × warehouse) |
| (not in response) | `reserved` | C-docs | **Field does not exist.** Set to 0 for WB |
| `chrtId` | (informational) | C-docs | Size-level characteristic ID; needed for aggregation |
| `warehouseName` | (informational) | C-docs | Warehouse name string |
| `regionName` | (informational) | C-docs | Shipping region name |
| `inWayToClient` | (informational) | C-docs | Units in transit to customer |
| `inWayFromClient` | (informational) | C-docs | Units in transit from customer |

**Granularity:** 1 row = 1 size (`chrtId`) × 1 warehouse (`warehouseId`).
For product-level stock: `SUM(quantity) GROUP BY nmId, warehouseId`.

**Verified changes from initial contract (2026-03-31):**
- `vendorCode` is NOT in stocks response — resolved via `nmId` lookup through catalog
- Field is `quantity` (not generic "stock quantity field")
- `reserved` does NOT exist in this endpoint
- Additional fields: `chrtId`, `warehouseName`, `regionName`, `inWayToClient`, `inWayFromClient`
- Data freshness: updated every 30 minutes (confirmed-docs)

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
| (resolved via offer lookup) | `marketplace_offer_id` | C | WB: via `nmId` → `marketplace_offer.marketplace_sku`; Ozon: via `offer_id` |
| `warehouseId` | `warehouse_id` | C (Ozon) / C-docs (WB) | DD-2 for Ozon; WB: `warehouseId` confirmed in official docs |
| `available` | `available` | C (Ozon) / C-docs (WB) | WB: `quantity` field (aggregated by nmId+warehouse) |
| `reserved` | `reserved` | C (Ozon) / C-docs (WB) | **WB: always 0** — field does not exist in stocks endpoint |
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
| `fulfillmentType` | `fulfillment_type` | C (Ozon) / B (WB) | Ozon: derived from endpoint (`/v2/posting/fbo/list` → `FBO`, `/v3/posting/fbs/list` → `FBS`). WB: inferred from `warehouseType` or hardcoded per endpoint; less reliable |
| `region` | `region` | C (Ozon) / — (WB) | Ozon: `analytics_data.city` (FBS) / `analytics_data.region` (FBO). WB: NULL (not available in orders API) |

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
| `externalSaleId` | `external_sale_id` | C | WB: `srid` from finance report (DD-18); Ozon: `posting_number` |
| (resolved) | `marketplace_offer_id` (FK → marketplace_offer) | C | |
| (resolved) | `seller_sku_id` (FK → seller_sku) | C | Via offer lookup |
| `quantity` | `quantity` | C | |
| `saleAmount` | `sale_amount` | C (Ozon) / C-docs (WB) | WB: `retail_price_withdisc_rub` from finance report |
| `commission` | `commission` | C (Ozon) / C-docs (WB) | WB: `ppvz_sales_commission` from finance report (positive, DEBIT) |
| `currency` | `currency` | C (Ozon) / C-docs (WB) | |
| `saleDate` | `sale_date` | C (Ozon) / C-docs (WB) | WB: `sale_dt` from finance report |
| (resolved) | `canonical_order_id` (FK → canonical_order) | B | Resolved post-persist: lookup `canonical_order` by `(connection_id, external_order_id)` where `external_order_id` = WB `srid` / Ozon `posting_number`. NULL if order not yet ingested |
| `postingId` | `posting_id` | C | WB: `srid` (same as `external_sale_id`, DD-18). Ozon: `posting_number`. Join key for order ↔ sale reconciliation |

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

### WB → CanonicalReturn (from `reportDetailByPeriod` — source of truth)

Аналогично `CanonicalSale`, WB returns материализуются из finance report. Строки с `doc_type_name = "Возврат"` создают `canonical_return`.

| WB finance field | Canonical field | Confidence | Notes |
|------------------|-----------------|------------|-------|
| `srid` | `external_return_id` | C | Unique row identifier (аналогично canonical_sale) |
| `sa_name` (= supplierArticle) | `seller_sku_id` (resolved) | C | Lookup: `vendorCode → seller_sku.id` |
| `nm_id` | `marketplace_offer_id` (resolved) | C | Lookup: `nmID → marketplace_offer.marketplace_sku` |
| `retail_price_withdisc_rub` | `return_amount` | C-docs | Отрицательная (WB: сторно → знак инвертирован normalizer-ом) |
| `doc_type_name` = "Возврат" | `return_reason` | A | Грубая классификация; WB не предоставляет детальную причину в finance report |
| `sale_dt` | `return_date` | C | Дата операции |
| 1 per row | `quantity` | C | WB finance: 1 строка = 1 единица |

**Filtering:** строки finance report с `doc_type_name = "Возврат"` → `canonical_return`. Остальные `doc_type_name` (включая "Продажа") → `canonical_sale` или `canonical_finance_entry`.

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
| (resolved) | `canonical_order_id` (FK → canonical_order) | B | Resolved post-persist: lookup `canonical_order` by `(connection_id, external_order_id)`. WB: via `srid` / `order_id` from goods-return. Ozon: via `order_number`. NULL if order not yet ingested |
| `status` | `status` | C (Ozon) / A (WB) | WB: `status` from goods-return (e.g. `"На складе продавца"`). Ozon: `visual.status.sys_name` (e.g. `"ReturnedToOzon"`) |

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
Field name determines credit/debit semantics. Canonical sign convention (positive = credit to seller, negative = debit from seller) defined in [etl-pipeline.md](../modules/etl-pipeline.md) §Sign conventions. When normalizing to canonical:
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
| Cashback discount | `cashback_discount` | amount | C-docs | negate (debit), absent in sandbox |
| Cashback commission delta | `cashback_commission_change` | amount | C-docs | sign as-is (adjusts commission), absent in sandbox |
| Seller promo | `seller_promo_discount` | amount | C-docs | negate (debit), absent in sandbox |
| Loyalty | `loyalty_discount` | amount | C-docs | negate (debit), absent in sandbox |
| Installment co-financing | `installment_cofinancing_amount` | amount | C-docs | negate (debit), absent in sandbox |

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

### Ozon operation_type → FinanceEntryType — COMPREHENSIVE (updated 2026-03-31)

**Sources:** Official Ozon OpenAPI enum (35 types) + empirical data Jan 2025 (7590 ops, 17 types) + Feb 2026 (589 ops, 13 types).

**Part A — Empirically verified (23 types, from real API responses):**

| Ozon operation_type | `type` cat | → FinanceEntryType | → fact_finance measure | Confidence | Sign | First seen |
|---------------------|------------|---------------------|------------------------|------------|------|-----------|
| `OperationAgentDeliveredToCustomer` | orders | SALE_ACCRUAL | `revenue_amount` (accruals_for_sale) | C | amount > 0 | Jan 2025 |
| `ClientReturnAgentOperation` | returns | RETURN_REVERSAL | `refund_amount` (accruals_for_sale < 0) | C | amount < 0 | Jan 2025 |
| `OperationAgentStornoDeliveredToCustomer` | returns | STORNO_CORRECTION | `refund_amount` | C | amount > 0 | Jan 2025 |
| `OperationItemReturn` | returns | RETURN_LOGISTICS | `logistics_cost_amount` | C | amount < 0 | Jan 2025 |
| `MarketplaceRedistributionOfAcquiringOperation` | other | ACQUIRING | `acquiring_commission_amount` | C | amount < 0 | Jan 2025 |
| `MarketplaceServiceBrandCommission` | services | BRAND_COMMISSION | `marketplace_commission_amount` | C | amount < 0 | Jan 2025 |
| `MarketplaceServiceItemCrossdocking` | services | LOGISTICS | `logistics_cost_amount` | C | amount < 0 | Jan 2025 |
| `OperationElectronicServiceStencil` | services | PACKAGING | `other_marketplace_charges_amount` | C | amount < 0 | Jan 2025 |
| `OperationMarketplaceServiceStorage` | services | STORAGE | `storage_cost_amount` | C | amount < 0 | Jan 2025 |
| `StarsMembership` | services | SUBSCRIPTION | `other_marketplace_charges_amount` | C | amount < 0 | Jan 2025 |
| `MarketplaceSaleReviewsOperation` | services | REVIEWS_PURCHASE | `marketing_cost_amount` | C | amount < 0 | Jan 2025 |
| `DisposalReasonFailedToPickupOnTime` | services | DISPOSAL | `penalties_amount` | C | amount < 0 | Jan 2025 |
| `DisposalReasonDamagedPackaging` | services | DISPOSAL | `penalties_amount` | C | amount < 0 | Jan 2025 |
| `AccrualInternalClaim` | compensation | COMPENSATION | `compensation_amount` | C | amount > 0 | Jan 2025 |
| `AccrualWithoutDocs` | compensation | COMPENSATION | `compensation_amount` | C | amount > 0 | Jan 2025 |
| `MarketplaceSellerCompensationOperation` | compensation | COMPENSATION | `compensation_amount` | C | amount > 0 | Jan 2025 |
| `OperationReturnGoodsFBSofRMS` | returns | FBS_RETURN_LOGISTICS | `logistics_cost_amount` | C | amount < 0 | Jan 2025 |
| `OperationMarketplaceCostPerClick` | services | CPC_ADVERTISING | `marketing_cost_amount` | C | amount < 0 | **Feb 2026** |
| `OperationPromotionWithCostPerOrder` | services | PROMO_CPC | `marketing_cost_amount` | C | amount < 0 | **Feb 2026** |
| `DefectRateCancellation` | services | DEFECT_PENALTY | `penalties_amount` | C | amount < 0 | **Feb 2026** |
| `OperationPointsForReviews` | services | REVIEWS_PURCHASE | `marketing_cost_amount` | C | amount < 0 | **Feb 2026** |
| `DefectFineShipmentDelayRated` | services | SHIPMENT_DELAY_FINE | `penalties_amount` | C | amount < 0 | **Feb 2026** |
| `DefectFineCancellation` | services | CANCELLATION_FINE | `penalties_amount` | C | amount < 0 | **Feb 2026** |

**Part B — From official Ozon OpenAPI enum, not yet observed in our data (C-docs):**

| Ozon operation_type | `type` cat | → FinanceEntryType | → fact_finance measure | Confidence | Sign (assumed) | Source |
|---------------------|------------|---------------------|------------------------|------------|----------------|--------|
| `OperationAgentDeliveredToCustomerCanceled` | returns | DELIVERY_CANCEL_ACCRUAL | `refund_amount` | C-docs | amount < 0 | Official enum: delivery cancellation accrual |
| `OperationClaim` | compensation | CLAIM | `compensation_amount` | C-docs | amount > 0 | Official enum: claim accrual |
| `OperationCorrectionSeller` | other | CORRECTION | `other_marketplace_charges_amount` | C-docs | ± | Official enum: mutual settlement |
| `OperationDefectiveWriteOff` | compensation | DEFECTIVE_WRITEOFF | `compensation_amount` | C-docs | amount > 0 | Official enum: warehouse damaged goods compensation |
| `OperationLackWriteOff` | compensation | LACK_WRITEOFF | `compensation_amount` | C-docs | amount > 0 | Official enum: warehouse lost goods compensation |
| `OperationSetOff` | other | SETOFF | `other_marketplace_charges_amount` | C-docs | ± | Official enum: offset with counterparties |
| `OperationMarketplaceCrossDockServiceWriteOff` | services | CROSSDOCK_LOGISTICS | `logistics_cost_amount` | C-docs | amount < 0 | Official enum: cross-dock delivery service |
| `ReturnAgentOperationRFBS` | returns | RFBS_RETURN | `logistics_cost_amount` | C-docs | amount < 0 | Official enum: rFBS delivery return transfer |
| `MarketplaceSellerReexposureDeliveryReturnOperation` | transferDelivery | REEXPOSURE_DELIVERY | `logistics_cost_amount` | C-docs | amount < 0 | Official enum: buyer delivery transfer |
| `MarketplaceSellerShippingCompensationReturnOperation` | transferDelivery | SHIPPING_COMPENSATION | `compensation_amount` | C-docs | amount > 0 | Official enum: shipping fee compensation transfer |
| `MarketplaceMarketingActionCostOperation` | services | MARKETING_ACTION | `marketing_cost_amount` | C-docs | amount < 0 | Official enum: product promotion services |
| `OperationMarketplaceServicePremiumCashback` | services | PREMIUM_CASHBACK | `marketing_cost_amount` | C-docs | amount < 0 | Official enum: Premium promotion service |
| `MarketplaceServicePremiumPromotion` | services | PREMIUM_PROMOTION | `marketing_cost_amount` | C-docs | amount < 0 | Official enum: Premium promotion fixed commission |
| `MarketplaceServicePremiumCashbackIndividualPoints` | services | PREMIUM_SELLER_BONUS | `marketing_cost_amount` | C-docs | amount < 0 | Official enum: seller bonus promotion |
| `OperationSubscriptionPremium` | services | PREMIUM_SUBSCRIPTION | `other_marketplace_charges_amount` | C-docs | amount < 0 | Official enum: Premium subscription |
| `InsuranceServiceSellerItem` | services | INSURANCE_SELLER | `other_marketplace_charges_amount` | C-code | amount < 0 | Discovered empirically; seller insurance service charge |
| `MarketplaceReturnStorageServiceAtThePickupPointFbsItem` | services | FBS_RETURN_STORAGE_PVZ | `storage_cost_amount` | C-docs | amount < 0 | Official enum: FBS short-term return storage at PVZ |
| `MarketplaceReturnStorageServiceInTheWarehouseFbsItem` | services | FBS_RETURN_STORAGE_WH | `storage_cost_amount` | C-docs | amount < 0 | Official enum: FBS long-term return storage in warehouse |
| `MarketplaceServiceItemDeliveryKGT` | services | KGT_LOGISTICS | `logistics_cost_amount` | C-docs | amount < 0 | Official enum: oversized item logistics |
| `OperationMarketplaceWithHoldingForUndeliverableGoods` | services | WITHHOLDING_UNDELIVERABLE | `penalties_amount` | C-docs | amount < 0 | Official enum: delivery hold for undeliverable items |
| `OperationElectronicServicesPromotionInSearch` | services | SEARCH_PROMOTION | `marketing_cost_amount` | C-docs | amount < 0 | Official enum: search promotion service |
| `OperationMarketplaceServiceItemElectronicServicesBrandShelf` | services | BRAND_SHELF | `marketing_cost_amount` | C-docs | amount < 0 | Official enum: brand shelf service |
| `ItemAgentServiceStarsMembership` | services | SUBSCRIPTION | `other_marketplace_charges_amount` | C-docs | amount < 0 | Official enum: stars service (alias of `StarsMembership`?) |

**Total: 44 operation types mapped (23 empirical + 21 from official enum).**

**Note:** 11 of our 23 empirical types (`DefectRateCancellation`, `DefectFineCancellation`, `DefectFineShipmentDelayRated`, `OperationMarketplaceCostPerClick`, `OperationPromotionWithCostPerOrder`, `OperationPointsForReviews`, `MarketplaceServiceBrandCommission`, `DisposalReasonFailedToPickupOnTime`, `DisposalReasonDamagedPackaging`, `AccrualInternalClaim`, `AccrualWithoutDocs`) are NOT in the official enum — they were added after the enum was last updated (Sept 2024). This confirms that Ozon adds new types without updating the enum.

Unmapped types MUST default to OTHER with logging.

**New types detail (Feb 2026, empirical):**
- `OperationMarketplaceCostPerClick` (23 ops, e.g. -3.1 RUB): CPC advertising charge. `posting_number` = campaign/ad ID (numeric), not posting format. `items=[]`, `services=[]`. Attribution: ACCOUNT (pro-rata allocation).
- `OperationPromotionWithCostPerOrder` (7 ops, e.g. -49.9 RUB): Promotion cost-per-order. `posting_number` = promotion ID (numeric). `items=[]`, `services=[]`. Attribution: ACCOUNT.
- `DefectRateCancellation` (4 ops, -150 RUB): Defect rate penalty. `posting_number` = posting format, `warehouse_id` populated. Attribution: POSTING.
- `OperationPointsForReviews` (2 ops, -585.6 RUB): Points for reviews (replaces or supplements `MarketplaceSaleReviewsOperation`). Standalone (`posting_number=""`). Attribution: ACCOUNT.
- `DefectFineShipmentDelayRated` (1 op, -50 RUB): Shipment delay fine. `posting_number` = posting format. Attribution: POSTING.
- `DefectFineCancellation` (1 op, -150 RUB): Cancellation fine. `posting_number` = posting format. Attribution: POSTING.

**Observation (Feb 2026):** `StarsMembership` absent from Feb 2026 data (was 1854/7590 = 24% of ops in Jan 2025). Possible program change by Ozon. Adapter MUST handle both presence and absence.

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
| `MarketplaceServiceItemRedistributionLastMileCourier` | `logistics_cost_amount` | — | — | C-code (discovered empirically, not in official enum) |
| `ItemAgentServiceStarsMembership` | `other_marketplace_charges_amount` | 1854 | -1,466 | C |
| `MarketplaceRedistributionOfAcquiringOperation` | `acquiring_commission_amount` | 1648 | -2,864 | C |
| `MarketplaceServiceBrandCommission` | `marketplace_commission_amount` | 1847 | -1,398 | C |
| `MarketplaceServiceItemDisposalDetailed` | `penalties_amount` | 4 | -300 | C |

**Additional operation-level services (Feb 2026, no services[] breakdown — charges at operation level):**

| Operation type (as service) | → fact_finance measure | Count (Feb) | Total (Feb) | Confidence |
|----------------------------|------------------------|-------------|-------------|------------|
| `OperationMarketplaceCostPerClick` | `marketing_cost_amount` | 23 | -71.3 | C |
| `OperationPromotionWithCostPerOrder` | `marketing_cost_amount` | 7 | -349.3 | C |
| `DefectRateCancellation` | `penalties_amount` | 4 | -600 | C |
| `OperationPointsForReviews` | `marketing_cost_amount` | 2 | -1171.2 | C |
| `DefectFineShipmentDelayRated` | `penalties_amount` | 1 | -50 | C |
| `DefectFineCancellation` | `penalties_amount` | 1 | -150 | C |

Note: These 6 types have `items=[]` and `services=[]`. Charge amount is at `operation.amount` only.

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

Canonical DDL содержит per-measure columns (DD-8 composite row model). Normalizer decomposит provider-specific fields в individual measures:

| Normalized field | Canonical field (DDL: `canonical_finance_entry`) | Confidence | Notes |
|------------------|--------------------------------------------------|------------|-------|
| `entryType` | `entry_type` → `FinanceEntryType` | C (Ozon) / A (WB) | |
| `externalRef` | `external_entry_id` | C | rrd_id (WB), operation_id (Ozon) |
| (resolved) | `seller_sku_id` (FK → seller_sku) | C | Via offer lookup; NULL if SKU not found |
| `currency` | `currency` | C (Ozon) / A (WB) | |
| `entryDate` | `entry_date` | C (Ozon) / A (WB) | Ozon: custom format! |
| (resolved) | `warehouse_id` (FK → warehouse) | B (WB) / B (Ozon) | WB: `ppvz_office_id` → lookup `warehouse.external_warehouse_id`. Ozon: `posting.warehouse_id` → lookup `warehouse.external_warehouse_id`. NULL for standalone operations (no warehouse context) |

**Per-measure mapping (WB):**

| WB finance field | → canonical measure column | Sign | Confidence |
|------------------|---------------------------|------|------------|
| `retail_price_withdisc_rub` | `revenue_amount` | positive (credit) | C-docs |
| `ppvz_sales_commission` | `marketplace_commission_amount` | negate (debit) | C-docs |
| `acquiring_fee` | `acquiring_commission_amount` | negate (debit) | C-docs |
| `delivery_rub` + `rebill_logistic_cost` | `logistics_cost_amount` | negate (debit) | C |
| `storage_fee` | `storage_cost_amount` | negate (debit) | C-docs |
| `penalty` + `deduction` | `penalties_amount` | negate (debit) | C-docs |
| `acceptance` | `acceptance_cost_amount` | negate (debit) | C-docs |
| `additional_payment` | `compensation_amount` | positive (credit) | C-docs |
| (return entries: retail_price_withdisc_rub) | `refund_amount` | negate (debit to seller) | C-docs |
| `ppvz_for_pay` | `net_payout` | positive (credit) | C |
| `seller_promo_discount` | `marketing_cost_amount` | negate (debit) | C-docs |
| `cashback_amount` + `cashback_discount` + `loyalty_discount` + `installment_cofinancing_amount` | `other_marketplace_charges_amount` | negate (debit) | C-docs |
| `cashback_commission_change` | `marketplace_commission_amount` (additive) | sign as-is (adjusts base commission) | C-docs |

**DD-19: WB v5 P&L fields → canonical measures (2026-03-31)**

6 полей v5, помеченных как P&L-relevant в DD-10, теперь имеют назначенные canonical measures:

| WB v5 field | → canonical measure | Обоснование |
|---|---|---|
| `seller_promo_discount` | `marketing_cost_amount` | Селлерская промо-скидка — маркетинговый расход продавца |
| `cashback_amount` | `other_marketplace_charges_amount` | Кешбек — удержание МП |
| `cashback_discount` | `other_marketplace_charges_amount` | Скидка кешбека — удержание МП |
| `cashback_commission_change` | `marketplace_commission_amount` | Дельта комиссии из-за кешбека — корректировка основной комиссии |
| `loyalty_discount` | `other_marketplace_charges_amount` | Скидка лояльности — удержание МП |
| `installment_cofinancing_amount` | `other_marketplace_charges_amount` | Софинансирование рассрочки — удержание |

**DD-20: WB informational fields — deliberately excluded from canonical mapping (2026-03-31)**

Следующие поля из `reportDetailByPeriod` являются **внутренним breakdown WB** и НЕ маппятся в отдельные canonical measures, т.к. уже покрыты `retail_price_withdisc_rub` (revenue) и другими measures:

| WB field | Semantics | Why excluded |
|---|---|---|
| `ppvz_vw` | Вознаграждение продавца (доля от цены) | Sub-component of revenue. DD-13: «НЕ подходит» для revenue_amount. Already covered by `retail_price_withdisc_rub` |
| `ppvz_vw_nds` | НДС на вознаграждение | VAT breakdown of `ppvz_vw`. Already included in `retail_price_withdisc_rub` |
| `retail_price` | Базовая розничная цена (до скидок) | Informational; pre-discount price |
| `retail_amount` | Фактическая сумма реализации покупателю | Informational; buyer-paid amount (after SPP) |
| `acquiring_percent` | Процент эквайринга | Informational; absolute amount in `acquiring_fee` |
| `ppvz_spp_prc` | Процент СПП (скидка постоянного покупателя) | Informational; DD-14: SPP не компонент P&L |

**DD-21: WB supplier_oper_name full list (2026-03-31, official WB docs)**

Полный перечень значений поля `supplier_oper_name` (= "Обоснование для оплаты") из официальной документации WB (seller.wildberries.ru, обновлено 30.03.2026):

| supplier_oper_name | Семантика | Влияет на P&L | Наш маппинг |
|---|---|---|---|
| Продажа | Покупатель выкупил товар | Yes: `revenue_amount` | ✅ via `retail_price_withdisc_rub` |
| Возврат | Покупатель вернул товар | Yes: `refund_amount` | ✅ via `retail_price_withdisc_rub` (return entry) |
| Логистика | Доставка товара | Yes: `logistics_cost_amount` | ✅ via `delivery_rub` |
| Хранение | Оплата хранения на складах WB | Yes: `storage_cost_amount` | ✅ via `storage_fee` |
| Обработка товара | Приёмка поставок на складах | Yes: `acceptance_cost_amount` | ✅ via `acceptance` |
| Штраф | Штрафные санкции | Yes: `penalties_amount` | ✅ via `penalty` |
| Удержания | Оплата сервисов WB (ВБ.Продвижение, Джем, и пр.) | Yes: `penalties_amount` | ✅ via `deduction` |
| Компенсация ущерба | Компенсация утерянного/подменённого товара | Yes: `compensation_amount` | ✅ via `additional_payment` |
| Добровольная компенсация при возврате | Компенсация повреждённого товара | Yes: `compensation_amount` | ✅ via `additional_payment` |
| Коррекция продаж/логистики/эквайринга | Корректировка ранее начисленных сумм | Yes: varies by type | ⚠️ Handled via composite row — correction adjusts the corresponding amount field |
| Возмещение издержек по перевозке | Расходы сторонних перевозчиков | Yes: `logistics_cost_amount` | ✅ via `rebill_logistic_cost` |
| Возмещение за выдачу и возврат товаров на ПВЗ | Расходы на услуги ПВЗ | Yes: `logistics_cost_amount` | ✅ via `rebill_logistic_cost` |
| Услуга платной доставки | DBS/EDBS платная доставка | Yes: `logistics_cost_amount` | ⚠️ New: DBS-specific, maps to `delivery_rub` field |
| Бронирование товара через самовывоз | C&C (Click & Collect) бронирование | Informational | ⚠️ New: C&C model, likely zero-impact on P&L measures |
| Стоимость участия в программе лояльности | Комиссия за кешбек продавца | Yes: `other_marketplace_charges_amount` | ✅ via `cashback_amount` (DD-19) |
| Сумма, удержанная за начисленные баллы программы лояльности | Кешбек покупателя | Yes: `other_marketplace_charges_amount` | ✅ via `cashback_discount` (DD-19) |
| Компенсация скидки по программе лояльности | Кешбек, потраченный на оплату товара | Yes: `other_marketplace_charges_amount` | ✅ via `loyalty_discount` (DD-19) |
| Разовое изменение срока перечисления денежных средств | Комиссия за услугу «Вывести сейчас» | Yes: `other_marketplace_charges_amount` | ⚠️ New: early withdrawal fee, maps to `deduction` field |

**Notes:**
- Коррекция: WB корректирует ранее начисленные суммы (продажи, логистика, эквайринг). Correction row содержит дельту в соответствующем amount-поле (e.g., `retail_price_withdisc_rub` для коррекции продаж). Composite row model обрабатывает это автоматически.
- Услуга платной доставки (DBS) и Бронирование (C&C) — специфичны для моделей продаж, которые пока не активны у нашего тестового продавца. Финансовый impact попадает в стандартные поля (`delivery_rub`, `deduction`).

**Per-measure mapping (Ozon):**

| Ozon finance field | → canonical measure column | Sign | Confidence |
|--------------------|---------------------------|------|------------|
| `accruals_for_sale` | `revenue_amount` (sale) or `refund_amount` (return, negative) | as-is | C |
| `sale_commission` | `marketplace_commission_amount` | as-is (negative for sale, positive for return refund) | C |
| `services[].price` by service name | mapped per §Ozon services classification | as-is (negative) | C |
| `amount` | `net_payout` | as-is (signed) | C |
| N/A — Ozon has no acceptance fee concept | `acceptance_cost_amount` | defaults to 0 | N/A |

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

ACQUIRING JOIN (DD-15, updated 2026-03-31):
  DUAL FORMAT: 57% use order_number (2-part), 43% use full posting_number (3-part)
  Join: exact match first, then strip-suffix match
  Example: sale "39222582-0174-1" → acquiring "39222582-0174-1" (exact) OR "93284743-0263" (2-part)

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
| FINANCES | **READY** (sandbox+docs) | READY | Both fully verified: WB sign convention (DD-7), v5 P&L fields mapped (DD-19), supplier_oper_name full list (DD-21); Ozon: 44 op types mapped (23 empirical + 21 official enum), dual acquiring format (DD-15) |

### Resolved Blockers

1. ~~CanonicalSale.commission (Ozon)~~ — **CONFIRMED** via `financial_data.products[].commission_amount` in posting
2. ~~CanonicalReturn.returnAmount (Ozon)~~ — **CONFIRMED** via `product.price.price` in returns endpoint
3. ~~CanonicalReturn.returnDate (Ozon)~~ — **CONFIRMED** as `logistic.return_date` (ISO 8601 UTC)
4. ~~CanonicalFinanceEntry sign semantics (Ozon)~~ — **CONFIRMED** (DD-4): positive = credit, negative = debit
5. ~~CanonicalFinanceEntry.entryType mapping (Ozon)~~ — **44 types mapped** (23 empirical + 21 from official OpenAPI enum, 2026-03-31)
6. ~~CanonicalPriceSnapshot.discountPrice (Ozon)~~ — **RESOLVED** (DD-1): `marketing_seller_price`
7. ~~CanonicalStockSnapshot.reserved (Ozon)~~ — **CONFIRMED** present in v4 response

### Remaining Blockers

1. ~~WB Finance sign convention~~ — **RESOLVED**: confirmed via sandbox + official docs
2. ~~WB Returns~~ — **RESOLVED**: endpoint works with date-only format `YYYY-MM-DD`
3. ~~WB Stocks field names~~ — PARTIAL: no warehouses in sandbox; endpoint accessible
4. ~~WB Sales commission~~ — **RESOLVED**: `forPay` confirmed in sales endpoint; detailed commission in finance only
5. ~~WB Price markup~~ — **RESOLVED**: `price` → `discountedPrice` → `clubDiscountedPrice` hierarchy confirmed
6. ~~Ozon Brand~~ — **RESOLVED**: via `POST /v4/product/info/attributes` (attribute_id=85)
7. ~~Ozon FBS~~ — **RESOLVED (2026-03-31)**: FBS endpoint verified with real data. Same core contract as FBO, additional fields: `delivery_method`, `customer_price`, `cancellation`, `shipment_date`. Date range limit: ~3 months (`PERIOD_IS_TOO_LONG`).
8. ~~WB revenue_amount~~ — **RESOLVED (DD-13)**: `retail_price_withdisc_rub`
9. ~~Ozon acquiring join~~ — **RESOLVED (DD-15, updated)**: acquiring uses DUAL format (57% order_number, 43% full posting_number), exact+strip match strategy
10. ~~Ozon storage attribution~~ — **RESOLVED (DD-16)**: daily aggregate, pro-rata allocation
11. ~~WB SPP~~ — **RESOLVED (DD-14)**: не отдельный компонент P&L, WB компенсирует из своих средств
12. ~~P&L formula~~ — **RESOLVED**: обновлена до 13 компонентов (storage, acceptance добавлены)
13. ~~seller_discount_amount~~ — **RESOLVED**: удалён из measures
14. ~~Double refund counting~~ — **RESOLVED**: revenue spine = only sales, refunds aggregated separately

### Required Actions Before Full Implementation

1. ~~WB with data~~ — **RESOLVED**: Sandbox account provides test data for all endpoints (finance, sales, orders, incomes, offices)
2. ~~WB finance verification~~ — **DONE** (from official API documentation sample)
3. ~~WB returns~~: **DONE** — endpoint works; root cause was date format, not token scope
4. ~~Ozon FBS~~: **DONE (2026-03-31)** — FBS verified with FBS-active account. Same core structure as FBO. Additional fields: `customer_price` (buyer-paid, lower than seller price), `delivery_method`, `cancellation`, `shipment_date`.
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
- **WB host FIXED**: `discounts-api.wildberries.ru` → `discounts-prices-api.wildberries.ru` (DNS confirmed 2026-03-31)
- **WB write endpoint WORKS**: sandbox returns 400 for invalid nmID (expected), production returns 401 for read-only token (need write-scope token)
- **WB 401 confirmed (2026-03-31)**: `"read-only token scope not allowed for this route"` — need token with "Prices and Discounts → Write" scope
- **Reconciliation read-after-write**: endpoints available (WB: `/api/v2/list/goods/filter`, Ozon: `/v5/product/info/prices`) but verification logic not implemented

| Capability | WB | Ozon | Contract |
|------------|----|------|----------|
| Price Write | **READY** (host confirmed, need write-scope token) | READY | write-contracts.md §1.1, §2.1 |
| Write Poll | **READY** (same host as write) | N/A | write-contracts.md §1.2 |
| Reconciliation Read | READY | READY | write-contracts.md §1.3, §2.2 |
| Reconciliation Logic | NOT IMPLEMENTED | NOT IMPLEMENTED | ADR-016 gap |

### Advertising Contracts (Phase B extended)

Advertising read contracts documented in `promo-advertising-contracts.md` §2 (WB) and §4 (Ozon).

| Capability | WB | Ozon | Contract |
|------------|----|------|----------|
| Ad Campaigns (dim) | NEEDS WORK (v2 DTO expansion) | NEEDS WORK (OAuth2 credentials) | promo-advertising-contracts.md §2.1, §4.2 |
| Ad Stats (fact) | **READY** (v3 GET endpoint verified 2026-03-31) | NEEDS WORK (OAuth2 credentials) | promo-advertising-contracts.md §2.2, §4.3 |

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

---

## Verification Log

### 2026-03-31 — Comprehensive Verification Run

**Scope:** All 9 planned verification checks across WB and Ozon APIs.

| # | Check | Result | Key Finding |
|---|-------|--------|-------------|
| 1 | WB Stocks field names | **BLOCKED** | 204 No Content — account has no stock data. Cannot verify field names empirically. Proceed with assumed field names; verify at first real customer onboarding. |
| 2 | WB Finance entry types + v5 fields | **BLOCKED** | 204 No Content for all periods (Q1 2025, Q4 2025, Q1 2026). Account has no finance data. WB sign convention confirmed via sandbox + official docs only. |
| 3 | WB Offices structure | **CONFIRMED** | 225 offices (production). New field discovered: `federalDistrict` (string). Structure matches docs + new field. Informational, no DDL impact. |
| 4 | Ozon Finance — new operation types | **6 NEW TYPES** | Feb 2026 (589 ops) vs Jan 2025 (7590 ops): 6 new types — `OperationMarketplaceCostPerClick`, `OperationPromotionWithCostPerOrder`, `DefectRateCancellation`, `OperationPointsForReviews`, `DefectFineShipmentDelayRated`, `DefectFineCancellation`. `StarsMembership` absent (was 24% of ops). Total: 23 mapped types. |
| 5 | DD-15 Acquiring join | **PARTIALLY CONTRADICTED** | Acquiring uses DUAL format: 57% order_number (2-part), 43% full posting_number (3-part). Updated join strategy: exact match first, then strip-suffix match. |
| 6 | Ozon SKU lookup chain | **CONFIRMED** | `items[].sku` → `catalog sources[].sku` → `product_id` → `offer_id` verified end-to-end. |
| 7 | Ozon `updated_at` in catalog | **CORRECTED** | `updated_at` IS present in v3/product/info/list (was documented as absent). Readiness: CATALOG upgraded from PARTIAL to READY. |
| 8 | Cross-document consistency | **5/6 PASS** | DDL ↔ P&L formula: PASS. ETL events ↔ materialization: PASS. UPSERT keys ↔ DDL: PASS. data_domain ↔ ETL mapping: PASS. data-model.md ↔ etl-pipeline.md: PASS. mapping-spec measures ↔ DDL: FAIL (3 provider-specific N/A gaps now documented). |
| 9 | WB Orders/Sales amount fields | **BLOCKED** | Same account limitation. Confirmed via sandbox only. |

**Documents updated:** mapping-spec.md, ozon-read-contracts.md, wb-read-contracts.md, etl-pipeline.md, analytics-pnl.md.

### 2026-03-31 — Open Blockers Resolution Run

**Scope:** 5 previously open blockers (B-2, B-3, B-4, P-4, V-1).

| # | Blocker | Result | Key Finding |
|---|---------|--------|-------------|
| B-4 | WB Price Write (DNS + 401) | **RESOLVED** | Host `discounts-prices-api.wildberries.ru` confirmed (200 OK for read). Sandbox write: 400 "All item Nos. are specified incorrectly" (endpoint works, nmID not valid in sandbox). Production write: 401 `"read-only token scope not allowed for this route"`. **Contract is correct, need write-scope token.** |
| B-3 | WB Advertising v3 fullstats | **RESOLVED** | `GET /adv/v3/fullstats` → 200 OK (returns `null` for no campaigns — expected). `POST /adv/v2/fullstats` → 404 (confirmed dead). `GET /api/advert/v2/adverts` → 200 OK `{"adverts":[]}`. v3 endpoint fully accessible. |
| B-2 | Ozon Performance OAuth2 | **RESOLVED** | `POST /api/client/token` → 401 `{"error":"invalid_client"}` for test credentials (endpoint accessible). Old host `performance.ozon.ru` → 404 (migration confirmed). Standard `client_credentials` flow. Need real credentials from seller.ozon.ru → Settings → Performance API. |
| P-4 | WB Promo Write | **RESOLVED** | `POST /api/v1/calendar/promotions/upload` → 401 `"read-only token scope not allowed for this route"` (endpoint exists). All alternative paths → 404. Official docs confirm this is the upload endpoint. Reads work with current token. **Contract path confirmed, need write-scope token.** |
| V-1 | Ozon FBS Postings | **RESOLVED** | `POST /v3/posting/fbs/list` → 200 OK with full FBS posting data! Same core structure as FBO: `posting_number`, `products[]`, `financial_data`, `analytics_data`. Additional FBS fields: `delivery_method`, `customer_price`, `cancellation`, `shipment_date`. **Critical:** `customer_price` (105.71) << seller `price` (293) — confirms DD-11 (Ozon marketing subsidy). Date range limit: ~3 months (`PERIOD_IS_TOO_LONG`). v2 → 404 (deprecated). |

**Summary of remaining operational items (no longer architectural blockers):**
1. **WB Write-scope token:** Provision token with "Prices and Discounts → Write" + "Promotions → Write" scopes in WB seller cabinet
2. **Ozon Performance credentials:** Register Performance API in seller.ozon.ru → Settings → API Keys
3. **WB Advertising adapter:** Migrate code from POST to GET (v3 endpoint verified and accessible)

**Documents updated:** mapping-spec.md, ozon-read-contracts.md, wb-read-contracts.md, write-contracts.md, promo-advertising-contracts.md.

### 2026-03-31 — Completeness Audit: Official Enum + Docs Cross-Reference

**Scope:** Cross-reference our mapping against official Ozon OpenAPI enum and WB official documentation to find unmapped operation types/fields.

| # | Check | Result | Key Finding |
|---|-------|--------|-------------|
| 1 | Ozon operation_type completeness | **21 NEW TYPES** | Official Ozon OpenAPI enum (Apifox mirror, Sept 2024 version) contains 35 types. We had 23 from empirical data. 21 additional types found in enum but not yet observed: `OperationAgentDeliveredToCustomerCanceled`, `OperationClaim`, `OperationCorrectionSeller`, `OperationDefectiveWriteOff`, `OperationLackWriteOff`, `OperationSetOff`, `OperationMarketplaceCrossDockServiceWriteOff`, `ReturnAgentOperationRFBS`, Premium-related (4 types), FBS return storage (2 types), KGT logistics, search/brand shelf promotion (2 types), shipping compensation, withholding. All mapped to FinanceEntryType + fact_finance measure. |
| 2 | Ozon enum vs empirical gap | **11 types in our data NOT in official enum** | `DefectRateCancellation`, `DefectFineCancellation`, `DefectFineShipmentDelayRated`, `OperationMarketplaceCostPerClick`, `OperationPromotionWithCostPerOrder`, `OperationPointsForReviews`, `MarketplaceServiceBrandCommission`, `DisposalReasonFailedToPickupOnTime`, `DisposalReasonDamagedPackaging`, `AccrualInternalClaim`, `AccrualWithoutDocs`. Confirms Ozon adds types without updating public enum. |
| 3 | WB supplier_oper_name completeness | **10 NEW VALUES** | Official WB docs (seller.wildberries.ru, 2026-03-30) list 18+ values. We had ~8 implicit. Added: Компенсация ущерба, Добровольная компенсация при возврате, Коррекция (продаж/логистики/эквайринга), Услуга платной доставки, Бронирование (C&C), loyalty program (3 types), Вывести сейчас. All mapped to existing amount fields. |
| 4 | WB v5 P&L fields canonical mapping | **6 FIELDS MAPPED** | `cashback_amount`, `cashback_discount`, `cashback_commission_change`, `seller_promo_discount`, `loyalty_discount`, `installment_cofinancing_amount` — all assigned canonical measures (DD-19). No DDL changes needed. |
| 5 | WB informational fields | **6 FIELDS DOCUMENTED** | `ppvz_vw`, `ppvz_vw_nds`, `retail_price`, `retail_amount`, `acquiring_percent`, `ppvz_spp_prc` — deliberately excluded from canonical mapping with rationale (DD-20). |

**Total operation types: Ozon 44 (was 23), WB supplier_oper_name 18+ (was ~8).**
**Documents updated:** mapping-spec.md, ozon-read-contracts.md, wb-read-contracts.md.
