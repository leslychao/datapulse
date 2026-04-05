# Fulfillment Type Tracking — Technical Breakdown Document

## Problem Statement

FBO/FBS/FBW/DBS fulfillment type is the dimension that determines WHO handles logistics for each order/sale/financial operation. Currently:

- **Orders** (`canonical_order`) — fulfillment_type captured correctly (FBO/FBS/FBW)
- **Sales** (`canonical_sale`) — fulfillment_type NOT captured (lost at normalization)
- **Returns** (`canonical_return`) — fulfillment_type NOT captured
- **Finance** (`canonical_finance_entry`) — fulfillment_type NOT captured, despite Ozon API providing `delivery_schema` and WB API providing `srv_dbs` + `delivery_method`
- **Analytics** (`fact_finance`, `fact_sales`, `fact_returns`, `mart_posting_pnl`, `mart_product_pnl`) — fulfillment_type absent

**Business impact**: impossible to answer "how much do I spend on FBO logistics vs FBS logistics?" — the most common fulfillment-level P&L question.

**Data availability**: both Ozon and WB APIs already provide this data, it's just not being propagated.

## Terminology

| Value | Marketplace | Meaning |
|---|---|---|
| `FBO` | Ozon | Fulfillment by Ozon — goods stored at Ozon warehouse |
| `FBS` | Ozon | Fulfillment by Seller — seller ships from own warehouse |
| `FBW` | WB | Fulfillment by Wildberries — goods stored at WB warehouse |
| `DBS` | WB | Delivery by Seller — seller delivers from own warehouse |

Canonical column type: `varchar(10)` / `LowCardinality(Nullable(String))`. Nullable — for operations that don't have an associated posting (e.g. account-level penalties).

## Data Source Evidence

### Ozon Finance API

`OzonFinancePosting` already deserializes `delivery_schema`:

```java
// OzonFinancePosting.java (existing)
@JsonProperty("delivery_schema") String deliverySchema  // "FBO", "FBS", or ""
```

Verified in real API data (docs/provider-api-specs/samples/empirical-verification-log.md:341):
```json
"delivery_schema": "FBO"
```

Per ozon-read-contracts.md:794 — confirmed field, values: `"FBO"`, `"FBS"`, empty string for non-order operations.

### Ozon FBO/FBS Postings (Orders/Sales)

FBO and FBS are already fetched via separate API endpoints:
- FBO: `POST /v2/posting/fbo/list` → `OzonFboOrdersReadAdapter`
- FBS: `POST /v3/posting/fbs/list` → `OzonFbsOrdersReadAdapter`

`OzonNormalizer` already hardcodes `"FBO"` / `"FBS"` in `normalizeFboPosting()` / `normalizeFbsPosting()`. Same for sales: `normalizeFboSale()` / `normalizeFbsSale()`. The fulfillment type is known at normalization time.

### WB Finance API

`WbFinanceRow` already deserializes both fields:

```java
// WbFinanceRow.java (existing)
@JsonProperty("srv_dbs") Boolean srvDbs               // true = DBS, false/null = FBW
@JsonProperty("delivery_method") String deliveryMethod // e.g. "FBS, (МГТ)"
```

Per wb-read-contracts.md:781 — `srv_dbs` confirmed in docs + sandbox. Per wb-read-contracts.md:765 — `delivery_method` confirmed in docs.

Logic: `srvDbs == true` → `"DBS"`, else → `"FBW"`.

### WB Orders API

`WbOrderItem` has `isSupply` (boolean) and `isRealization` (boolean), but no direct fulfillment type marker. WB orders API (`/api/v1/supplier/orders`) returns only FBW orders by design. DBS orders are via a separate marketplace API not currently integrated.

Current hardcode `"FBW"` in `normalizeOrder()` is acceptable for Phase 1, since we only ingest FBW orders.

### Ozon Returns API

`OzonReturnItem` does not carry `delivery_schema` directly. However, the return's `posting_number` can be used to infer fulfillment type: Ozon FBO posting numbers follow pattern `NNNNNNN-NNNN-N`, same as FBS. The `order_number` field links to the original order.

For returns, fulfillment_type will be resolved via JOIN to `canonical_order` at materialization time (fact_returns → canonical_order.fulfillment_type), not at ETL normalization time. This avoids adding a second lookup to the returns adapter.

---

## Implementation Plan

### Phase 1: Finance (Critical Path)

Finance is the highest-priority layer because P&L analysis depends on it.

#### Step 1.1: Add `fulfillmentType` to `NormalizedFinanceItem`

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/normalized/NormalizedFinanceItem.java`

Add field after `warehouseExternalId`:

```java
public record NormalizedFinanceItem(
        String externalEntryId,
        FinanceEntryType entryType,
        String postingId,
        String orderId,
        String sellerSku,
        String marketplaceSku,
        String warehouseExternalId,
        String fulfillmentType,        // ← NEW: "FBO", "FBS", "FBW", "DBS", or null
        BigDecimal revenueAmount,
        // ... rest unchanged
) {}
```

#### Step 1.2: Populate in `OzonFinanceNormalizer`

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/ozon/OzonFinanceNormalizer.java`

In `normalizeFinanceTransaction()`, extract `deliverySchema` from posting and normalize:

```java
String rawPosting = tx.posting() != null ? tx.posting().postingNumber() : null;
String warehouseExternalId = tx.posting() != null && tx.posting().warehouseId() != 0
        ? String.valueOf(tx.posting().warehouseId())
        : null;
String fulfillmentType = resolveOzonFulfillment(tx.posting());  // ← NEW
```

New private method:

```java
private static String resolveOzonFulfillment(OzonFinancePosting posting) {
    if (posting == null || posting.deliverySchema() == null
            || posting.deliverySchema().isBlank()) {
        return null;
    }
    return posting.deliverySchema().toUpperCase();
}
```

Pass `fulfillmentType` as new argument to `NormalizedFinanceItem` constructor (position 8, after `warehouseExternalId`).

#### Step 1.3: Populate in `WbNormalizer.normalizeFinance()`

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/wb/WbNormalizer.java`

In `normalizeFinance()`, determine fulfillment from `srvDbs`:

```java
String fulfillmentType = Boolean.TRUE.equals(row.srvDbs()) ? "DBS" : "FBW";
```

Pass as new argument to `NormalizedFinanceItem` constructor (position 8).

#### Step 1.4: Add column to `CanonicalFinanceEntryEntity`

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/persistence/canonical/CanonicalFinanceEntryEntity.java`

Add field after `attributionLevel`:

```java
@Column(name = "fulfillment_type", length = 10)
private String fulfillmentType;
```

#### Step 1.5: Update `CanonicalEntityMapper.toFinanceEntry()`

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/CanonicalEntityMapper.java`

Add line after `entity.setAttributionLevel(attributionLevel)`:

```java
entity.setFulfillmentType(norm.fulfillmentType());
```

#### Step 1.6: PostgreSQL migration

**File**: `backend/datapulse-api/src/main/resources/db/changelog/changes/0024-finance-fulfillment-type.sql`

```sql
--liquibase formatted sql

--changeset datapulse:0024-finance-fulfillment-type
ALTER TABLE canonical_finance_entry
    ADD COLUMN fulfillment_type varchar(10);

COMMENT ON COLUMN canonical_finance_entry.fulfillment_type
    IS 'Delivery schema: FBO, FBS (Ozon), FBW, DBS (WB). NULL for non-order operations.';

--rollback ALTER TABLE canonical_finance_entry DROP COLUMN fulfillment_type;
```

Register in `db.changelog-master.yaml`:
```yaml
- include:
    file: changes/0024-finance-fulfillment-type.sql
    relativeToChangelogFile: true
```

#### Step 1.7: Update `CanonicalFinanceEntryUpsertRepository`

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/persistence/canonical/CanonicalFinanceEntryUpsertRepository.java`

Changes to UPSERT SQL:
- INSERT column list: add `fulfillment_type` after `attribution_level`
- VALUES: add one more `?` (position 25, shift `now(), now()` to 26-27)
- ON CONFLICT SET: add `fulfillment_type = EXCLUDED.fulfillment_type`
- IS DISTINCT FROM: add `canonical_finance_entry.fulfillment_type` / `EXCLUDED.fulfillment_type`

In `batchUpsert` lambda:
- Add `ps.setString(25, e.getFulfillmentType())` after `ps.setLong(24, ...)` (attribution)
- Shift `created_at, updated_at` handling if needed (they use `now()` in SQL, not params — no shift needed, just add the new param)

Exact change: current param count is 24, new param count is 25. Add after position 24:

```java
ps.setString(25, e.getFulfillmentType());
```

Update VALUES clause to have 25 `?` placeholders.

#### Step 1.8: ClickHouse migration — `fact_finance`

**File**: `backend/datapulse-etl/src/main/resources/db/clickhouse/0008-fulfillment-type.sql`

```sql
-- Add fulfillment_type to fact tables that need it

ALTER TABLE fact_finance
    ADD COLUMN IF NOT EXISTS fulfillment_type LowCardinality(Nullable(String))
    AFTER attribution_level;
```

#### Step 1.9: Update `FactFinanceMaterializer`

**File**: `backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/domain/materializer/fact/FactFinanceMaterializer.java`

PG_QUERY and PG_INCREMENTAL_QUERY — add `fulfillment_type` to SELECT:

```sql
SELECT id, connection_id, source_platform, entry_type,
       posting_id, order_id, seller_sku_id, warehouse_id,
       revenue_amount, marketplace_commission_amount, acquiring_commission_amount,
       logistics_cost_amount, storage_cost_amount, penalties_amount,
       acceptance_cost_amount, marketing_cost_amount, other_marketplace_charges_amount,
       compensation_amount, refund_amount, net_payout,
       entry_date, attribution_level, fulfillment_type,
       job_execution_id
FROM canonical_finance_entry
```

CH_INSERT — add `fulfillment_type` after `attribution_level`:

```sql
INSERT INTO %s
(connection_id, source_platform, entry_id, posting_id, order_id,
 seller_sku_id, warehouse_id, finance_date, entry_type, attribution_level,
 fulfillment_type,
 revenue_amount, ...)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
```

In `insertBatch` lambda — add after `ps.setString(10, attribution_level)`:

```java
ps.setString(11, (String) row.get("fulfillment_type"));
```

Shift all subsequent parameter indices by +1 (12→revenue through 26→materialized_at).

#### Step 1.10: Update `MartPostingPnlMaterializer`

**File**: `backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/domain/materializer/mart/MartPostingPnlMaterializer.java`

In the inner `pm` subquery (posting-level aggregation from fact_finance), add:

```sql
any(fulfillment_type) AS fulfillment_type,
```

Add `fulfillment_type` column to the final SELECT and the outer query chain.

**ClickHouse migration** — add column to `mart_posting_pnl`:

```sql
ALTER TABLE mart_posting_pnl
    ADD COLUMN IF NOT EXISTS fulfillment_type LowCardinality(Nullable(String))
    AFTER source_platform;
```

#### Step 1.11: Update `MartProductPnlMaterializer`

**File**: `backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/domain/materializer/mart/MartProductPnlMaterializer.java`

`mart_product_pnl` aggregates per (seller_sku_id, period). A single SKU can have both FBO and FBS entries. Two approaches:

**Option A (recommended)**: DO NOT add fulfillment_type to `mart_product_pnl` — it's a product-level aggregation that intentionally merges all fulfillment types. P&L breakdown by fulfillment is available at `mart_posting_pnl` level.

**Option B**: Add fulfillment_type to the GROUP BY key, creating separate rows per fulfillment type per product. This changes the mart semantics significantly.

**Decision**: Option A. Product P&L stays aggregated. Fulfillment breakdown is a mart_posting_pnl concern. Frontend can drill down from product to posting level for fulfillment analysis.

---

### Phase 2: Sales

#### Step 2.1: Add `fulfillmentType` to `NormalizedSaleItem`

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/normalized/NormalizedSaleItem.java`

```java
public record NormalizedSaleItem(
        String externalSaleId,
        String sellerSku,
        int quantity,
        BigDecimal saleAmount,
        BigDecimal commission,
        String currency,
        OffsetDateTime saleDate,
        String fulfillmentType     // ← NEW
) {}
```

#### Step 2.2: Populate in Ozon normalizers

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/ozon/OzonNormalizer.java`

`normalizeFboSale()` — pass `"FBO"` as last argument.
`normalizeFbsSale()` — pass `"FBS"` as last argument.

#### Step 2.3: Populate in WB normalizer

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/wb/WbNormalizer.java`

`normalizeSale()` — pass `"FBW"` as last argument. (WB sales API returns only FBW sales.)

#### Step 2.4: Add column to `CanonicalSaleEntity`

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/persistence/canonical/CanonicalSaleEntity.java`

```java
@Column(name = "fulfillment_type", length = 10)
private String fulfillmentType;
```

#### Step 2.5: Update `CanonicalEntityMapper.toSale()`

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/CanonicalEntityMapper.java`

Add: `entity.setFulfillmentType(norm.fulfillmentType());`

#### Step 2.6: PostgreSQL migration

Same migration file `0024-finance-fulfillment-type.sql` — add:

```sql
--changeset datapulse:0024-sale-fulfillment-type
ALTER TABLE canonical_sale
    ADD COLUMN fulfillment_type varchar(10);

--rollback ALTER TABLE canonical_sale DROP COLUMN fulfillment_type;
```

#### Step 2.7: Update `CanonicalSaleUpsertRepository`

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/persistence/canonical/CanonicalSaleUpsertRepository.java`

- Add `fulfillment_type` to INSERT column list after `currency`
- Add `?` to VALUES (position 13, shift `job_execution_id` to 14)
- Add to ON CONFLICT SET: `fulfillment_type = EXCLUDED.fulfillment_type`
- Add to IS DISTINCT FROM
- Add `ps.setString(13, e.getFulfillmentType())` in lambda, shift job_execution_id to 14

#### Step 2.8: ClickHouse migration — `fact_sales`

Same file `0008-fulfillment-type.sql`:

```sql
ALTER TABLE fact_sales
    ADD COLUMN IF NOT EXISTS fulfillment_type LowCardinality(Nullable(String))
    AFTER source_platform;
```

#### Step 2.9: Update `FactSalesMaterializer`

**File**: `backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/domain/materializer/fact/FactSalesMaterializer.java`

Add `cs.fulfillment_type` to PG_QUERY SELECT list and incremental query.
Add `fulfillment_type` to CH_INSERT column list and VALUES.
Add `ps.setString(...)` in `insertBatch` lambda.

---

### Phase 3: Returns

#### Step 3.1: ClickHouse migration — `fact_returns`

Same file `0008-fulfillment-type.sql`:

```sql
ALTER TABLE fact_returns
    ADD COLUMN IF NOT EXISTS fulfillment_type LowCardinality(Nullable(String))
    AFTER source_platform;
```

#### Step 3.2: Resolve via JOIN in `FactReturnsMaterializer`

Returns do NOT need a new field in `NormalizedReturnItem` or `CanonicalReturnEntity`. The fulfillment type is resolved at PostgreSQL materialization time by adding a LEFT JOIN to `canonical_order`.

**Current state**: `FactReturnsMaterializer.PG_QUERY` does NOT join `canonical_order`:

```sql
-- Current (no fulfillment)
SELECT cr.id AS return_id, cr.connection_id, cr.source_platform,
       cr.external_return_id, cr.seller_sku_id, ...
FROM canonical_return cr
```

**Target state**: add JOIN to resolve fulfillment_type from the linked order:

```sql
SELECT cr.id AS return_id, cr.connection_id, cr.source_platform,
       co.fulfillment_type,
       cr.external_return_id, cr.seller_sku_id, ...
FROM canonical_return cr
LEFT JOIN canonical_order co ON cr.canonical_order_id = co.id
```

Update both `PG_QUERY` (full) and the inline incremental query.

Add `fulfillment_type` to `CH_INSERT` column list and VALUES clause.
Add `ps.setString(...)` for `fulfillment_type` in `insertBatch` lambda.

**Fallback**: if `canonical_order_id` is NULL (return without order link), `fulfillment_type` will be NULL in the fact table. This is acceptable — the order link resolution is a separate data quality concern.

**Alternative (if order link coverage is too low)**: Add `fulfillment_type` directly to `NormalizedReturnItem` → `CanonicalReturnEntity` and populate it:
- Ozon: the source is known at processing time (FBO adapter → `"FBO"`, FBS adapter → `"FBS"`)
- WB: hardcode `"FBW"` (same as orders)

Decision deferred until data quality check of `canonical_order_id` population rate after deployment.

---

### Phase 4: WB DBS — Order Fulfillment Fix

#### Step 4.1: Fix `WbNormalizer.normalizeOrder()`

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/wb/WbNormalizer.java`

Currently hardcoded `"FBW"`. WB orders API (`/api/v1/supplier/orders`) returns only FBO/FBW orders, so `"FBW"` is correct for this endpoint.

**No change needed** — WB DBS orders come through a separate marketplace API endpoint (`/api/v3/orders`) that is not currently integrated. When DBS order ingestion is added, that adapter will set `"DBS"`.

---

## File Change Summary

### New files

| File | Description |
|---|---|
| `backend/.../db/changelog/changes/0024-fulfillment-type-columns.sql` | PG migration: add `fulfillment_type` to `canonical_finance_entry` and `canonical_sale` |
| `backend/.../db/clickhouse/0008-fulfillment-type.sql` | CH migration: add `fulfillment_type` to `fact_finance`, `fact_sales`, `fact_returns`, `mart_posting_pnl` |

### Modified files

| File | Change |
|---|---|
| `db.changelog-master.yaml` | Register migration 0024 |
| **Normalized DTOs** | |
| `NormalizedFinanceItem.java` | Add `fulfillmentType` field (position 8) |
| `NormalizedSaleItem.java` | Add `fulfillmentType` field (last position) |
| **Ozon normalizers** | |
| `OzonFinanceNormalizer.java` | Extract `posting.deliverySchema`, pass to NormalizedFinanceItem |
| `OzonNormalizer.java` | Pass `"FBO"`/`"FBS"` to `normalizeFboSale()`/`normalizeFbsSale()` |
| **WB normalizer** | |
| `WbNormalizer.java` | `normalizeFinance()`: use `srvDbs` → `"DBS"`/`"FBW"`. `normalizeSale()`: pass `"FBW"` |
| **Canonical entities** | |
| `CanonicalFinanceEntryEntity.java` | Add `fulfillmentType` field + JPA annotation |
| `CanonicalSaleEntity.java` | Add `fulfillmentType` field + JPA annotation |
| **Entity mapper** | |
| `CanonicalEntityMapper.java` | `toFinanceEntry()` + `toSale()`: set `fulfillmentType` |
| **Upsert repositories** | |
| `CanonicalFinanceEntryUpsertRepository.java` | Add `fulfillment_type` to UPSERT SQL + bind param |
| `CanonicalSaleUpsertRepository.java` | Add `fulfillment_type` to UPSERT SQL + bind param |
| **Materializers** | |
| `FactFinanceMaterializer.java` | Add `fulfillment_type` to PG SELECT, CH INSERT, bind param |
| `FactSalesMaterializer.java` | Add `fulfillment_type` to PG SELECT, CH INSERT, bind param |
| `FactReturnsMaterializer.java` | Add LEFT JOIN to `canonical_order`, select `co.fulfillment_type`, add to CH INSERT |
| `MartPostingPnlMaterializer.java` | Add `any(fulfillment_type)` to posting aggregation |

### NOT modified (by design)

| File | Reason |
|---|---|
| `NormalizedOrderItem.java` | Already has `fulfillmentType` — no change needed |
| `NormalizedReturnItem.java` | Resolved via JOIN, not at normalization (Phase 3 decision) |
| `CanonicalReturnEntity.java` | Resolved via JOIN, not stored directly |
| `CanonicalOrderEntity.java` | Already has `fulfillmentType` — no change |
| `MartProductPnlMaterializer.java` | Product-level aggregation stays fulfillment-agnostic (Option A) |
| `WbNormalizer.normalizeOrder()` | `"FBW"` is correct — DBS comes from a separate unintegrated API |

---

## Backfill Strategy

After deployment, existing data in `canonical_finance_entry` and `canonical_sale` will have `fulfillment_type = NULL`.

### Option 1: Re-sync (recommended)

Trigger a full ETL re-sync for all connections. This will:
1. Re-fetch finance and order data from marketplace APIs
2. UPSERT with the now-populated `fulfillment_type`
3. Full materialization will rebuild all ClickHouse tables

This is the cleanest approach because it uses the same code path as normal operation.

### Option 2: SQL backfill (faster, less clean)

For finance entries that already have `posting_id`:

```sql
-- Ozon: infer from posting_id pattern (FBO/FBS use same format, 
-- so this CANNOT distinguish FBO vs FBS without re-fetching)
-- → Not viable for Ozon

-- WB: all existing entries are FBW (DBS not yet integrated)
UPDATE canonical_finance_entry
SET fulfillment_type = 'FBW'
WHERE source_platform = 'wb'
  AND fulfillment_type IS NULL;
```

For Ozon, re-sync is the only reliable option because `delivery_schema` is not inferrable from other stored fields.

**Recommendation**: Option 1 (re-sync). Schedule a full sync cycle after deployment.

---

## Testing Checklist

### Unit tests

- [ ] `OzonFinanceNormalizerTest`: verify `fulfillmentType` = "FBO" when `posting.delivery_schema = "FBO"`
- [ ] `OzonFinanceNormalizerTest`: verify `fulfillmentType` = null when posting is null
- [ ] `OzonFinanceNormalizerTest`: verify `fulfillmentType` = null when `delivery_schema` is empty
- [ ] `WbNormalizerTest`: verify `fulfillmentType` = "DBS" when `srvDbs = true`
- [ ] `WbNormalizerTest`: verify `fulfillmentType` = "FBW" when `srvDbs = false`
- [ ] `WbNormalizerTest`: verify `fulfillmentType` = "FBW" when `srvDbs = null`
- [ ] `OzonNormalizerTest`: verify `normalizeFboSale()` returns `fulfillmentType = "FBO"`
- [ ] `OzonNormalizerTest`: verify `normalizeFbsSale()` returns `fulfillmentType = "FBS"`
- [ ] `CanonicalEntityMapperTest`: verify `toFinanceEntry()` maps `fulfillmentType`
- [ ] `CanonicalEntityMapperTest`: verify `toSale()` maps `fulfillmentType`

### Integration tests

- [ ] `CanonicalFinanceEntryUpsertRepository`: UPSERT with `fulfillment_type` value, verify stored
- [ ] `CanonicalFinanceEntryUpsertRepository`: UPSERT with `fulfillment_type = null`, verify stored as NULL
- [ ] `CanonicalSaleUpsertRepository`: same as above
- [ ] Full ETL cycle: verify `fulfillment_type` flows from API → canonical → fact_finance

### Data verification (post-deployment)

- [ ] Query `canonical_finance_entry` — verify Ozon entries have FBO/FBS, WB entries have FBW/DBS
- [ ] Query `fact_finance` in ClickHouse — verify `fulfillment_type` populated
- [ ] Query `mart_posting_pnl` — verify `fulfillment_type` populated
- [ ] Verify P&L totals unchanged (fulfillment_type is a dimension, not a measure)

---

## Implementation Order

Strict sequential order within each phase; phases can overlap:

```
Phase 1 (Finance):
  1.1 NormalizedFinanceItem → 1.2 OzonFinanceNormalizer → 1.3 WbNormalizer
  → 1.4 Entity → 1.5 Mapper → 1.6 PG migration → 1.7 UpsertRepo
  → 1.8 CH migration → 1.9 FactFinanceMaterializer
  → 1.10 MartPostingPnlMaterializer

Phase 2 (Sales):
  2.1 NormalizedSaleItem → 2.2 OzonNormalizer → 2.3 WbNormalizer
  → 2.4 Entity → 2.5 Mapper → 2.6 PG migration → 2.7 UpsertRepo
  → 2.8 CH migration → 2.9 FactSalesMaterializer

Phase 3 (Returns):
  3.1 CH migration → 3.2 FactReturnsMaterializer

Post-deployment:
  Trigger full re-sync → full materialization
```

Phases 1 and 2 share the same migration files (0024 for PG, 0008 for CH) — they should be implemented together in one PR to avoid partial schema states.

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| NormalizedFinanceItem record change breaks callers | Medium | Build failure | Compile immediately after change; record position matters |
| NormalizedSaleItem record change breaks callers | Medium | Build failure | Same — all callers must be updated in same commit |
| UPSERT param index shift introduces silent data corruption | Low | High | Careful counting; integration test covers |
| ClickHouse ALTER TABLE on large fact_finance | Low | Slow migration | `ADD COLUMN IF NOT EXISTS` is instant in ClickHouse (metadata-only) |
| Backfill via re-sync takes too long | Low | Delayed data | Can run per-connection; WB can use SQL backfill |
| Ozon `delivery_schema` empty for some operations | Expected | NULL values | Acceptable — non-order operations have no fulfillment |
| WB `srvDbs` null in old data | Expected | Defaults to FBW | Correct — null means not DBS |

---

## Scope Boundaries

**In scope**:
- `fulfillment_type` column in finance, sales, returns (fact level)
- `fulfillment_type` in `mart_posting_pnl`
- Data population from existing API fields
- Backfill strategy

**Out of scope** (separate tasks):
- Frontend UI for fulfillment_type filter/breakdown
- `mart_product_pnl` fulfillment dimension (requires P&L split discussion)
- WB DBS order ingestion (separate marketplace API)
- Ozon rFBS (realFBS) support
- Seller Operations grid fulfillment column
