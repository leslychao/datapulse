# Fulfillment Type Tracking — Technical Breakdown Document

**Статус:** READY TO IMPLEMENT (Phase 3 — Returns)
**Дата создания:** 2026-04-01
**Дата обновления:** 2026-04-06
**Автор:** Виталий Ким
**Целевая фаза:** D — Analytics & P&L

---

## Business Context

### Проблема

FBO/FBS/FBW/DBS fulfillment type — измерение, определяющее КТО обрабатывает логистику для каждой операции. Текущее состояние:

- **Заказы** (`canonical_order`) — fulfillment_type captured correctly (FBO/FBS/FBW) ✅
- **Финансы** (`canonical_finance_entry`) — fulfillment_type captured ✅
- **Продажи** (`canonical_sale`) — fulfillment_type captured ✅
- **Возвраты** (`canonical_return`) — fulfillment_type **NOT captured** ❌
- **Аналитика ClickHouse**: `fact_finance` ✅, `fact_sales` ✅, `fact_returns` ❌ (колонка есть, но всегда NULL), `mart_posting_pnl` ✅

### Бизнес-ценность

**Без fulfillment_type в возвратах** невозможно ответить на вопрос: «Сколько возвратов по FBO vs FBS?», «Какая доля возвратов по типу фулфилмента?» — это критично для анализа юнит-экономики по каналам.

### Что уже реализовано (Phase 1 + Phase 2)

Phase 1 (Finance) и Phase 2 (Sales) **полностью реализованы**:

| Слой | Файл | Статус |
|------|------|--------|
| Normalized DTO | `NormalizedFinanceItem.fulfillmentType` | ✅ |
| Normalized DTO | `NormalizedSaleItem.fulfillmentType` | ✅ |
| Ozon normalizer | `OzonFinanceNormalizer.resolveOzonFulfillment()` | ✅ |
| Ozon normalizer | `OzonNormalizer.normalizeFboSale()` → "FBO", `normalizeFbsSale()` → "FBS" | ✅ |
| WB normalizer | `WbNormalizer.normalizeFinance()` → srvDbs ? "DBS" : "FBW" | ✅ |
| WB normalizer | `WbNormalizer.normalizeSale()` → "FBW" | ✅ |
| JPA entity | `CanonicalFinanceEntryEntity.fulfillmentType` | ✅ |
| JPA entity | `CanonicalSaleEntity.fulfillmentType` | ✅ |
| Mapper | `CanonicalEntityMapper.toFinanceEntry()` + `toSale()` | ✅ |
| Upsert repo | `CanonicalFinanceEntryUpsertRepository` — SQL + bind | ✅ |
| Upsert repo | `CanonicalSaleUpsertRepository` — SQL + bind | ✅ |
| PG migration | `0024-fulfillment-type-columns.sql` | ✅ |
| CH migration | `0008-fulfillment-type.sql` — fact_finance, fact_sales, fact_returns, mart_posting_pnl | ✅ |
| Materializer | `FactFinanceMaterializer` — PG SELECT + CH INSERT | ✅ |
| Materializer | `FactSalesMaterializer` — PG SELECT + CH INSERT | ✅ |
| Materializer | `MartPostingPnlMaterializer` — `any(fulfillment_type)` | ✅ |

### Что осталось (Phase 3 — Returns)

`FactReturnsMaterializer` уже содержит `LEFT JOIN canonical_order co ON cr.canonical_order_id = co.id` и выбирает `co.fulfillment_type`. Но `canonical_order_id` в `canonical_return` **никогда не заполняется** (маппер его не ставит), поэтому JOIN всегда возвращает NULL.

---

## User Stories

### US-1: P&L breakdown по типу фулфилмента

**Как** селлер,
**я хочу** видеть в P&L аналитике разбивку по FBO/FBS (Ozon) и FBW/DBS (WB),
**чтобы** понимать, какой канал фулфилмента прибыльнее.

### US-2: Возвраты по типу фулфилмента

**Как** менеджер по операциям,
**я хочу** видеть количество и сумму возвратов в разрезе FBO/FBS/FBW,
**чтобы** оценить, какой тип фулфилмента генерирует больше возвратов.

---

## Acceptance Criteria

- [x] Finance: `fulfillment_type` заполняется в `canonical_finance_entry` (Ozon: FBO/FBS, WB: FBW/DBS)
- [x] Sales: `fulfillment_type` заполняется в `canonical_sale` (Ozon: FBO/FBS, WB: FBW)
- [x] ClickHouse: `fact_finance.fulfillment_type` populated after materialization
- [x] ClickHouse: `fact_sales.fulfillment_type` populated after materialization
- [x] ClickHouse: `mart_posting_pnl.fulfillment_type` populated
- [ ] Returns: `fulfillment_type` заполняется в `canonical_return` (WB: FBW, Ozon: null → fallback через JOIN)
- [ ] ClickHouse: `fact_returns.fulfillment_type` populated after materialization
- [ ] Materializer: `FactReturnsMaterializer` использует `COALESCE(cr.fulfillment_type, co.fulfillment_type)` — прямое значение с fallback на JOIN

---

## Terminology

| Value | Marketplace | Meaning |
|---|---|---|
| `FBO` | Ozon | Fulfillment by Ozon — goods stored at Ozon warehouse |
| `FBS` | Ozon | Fulfillment by Seller — seller ships from own warehouse |
| `FBW` | WB | Fulfillment by Wildberries — goods stored at WB warehouse |
| `DBS` | WB | Delivery by Seller — seller delivers from own warehouse |

Canonical column type: `varchar(10)` / `LowCardinality(Nullable(String))`. Nullable — for operations that don't have an associated posting (e.g. account-level penalties).

---

## Data Source Evidence

### Ozon Returns API

`OzonReturnItem` does NOT carry `delivery_schema`. The unified endpoint `POST /v1/returns/list` returns all returns without FBO/FBS distinction. Fields available: `id`, `returnId`, `orderId`, `orderNumber`, `postingNumber`, `status`, `returnReasonName`, etc.

**Decision:** Ozon returns fulfillment_type = `null` at normalization time. Will be resolved via:
1. `canonical_order_id` JOIN (when order linkage is implemented — separate task)
2. Future: Ozon may expose `delivery_schema` in returns API

### WB Returns API

`WbReturnItem` (from `GET /api/v1/analytics/goods-return`) does NOT have explicit fulfillment field. However, WB returns API returns only FBW returns (DBS returns are via a separate endpoint not yet integrated).

**Decision:** WB returns fulfillment_type = `"FBW"` (hardcode, same logic as sales).

---

## Phase 3 Implementation Plan (Returns)

### Architectural Decision: Direct Column + Fallback JOIN

**Вопрос:** как получить fulfillment_type для возвратов?

**Варианты:**
- **A: Только JOIN** к `canonical_order` через `canonical_order_id` — текущий подход в materializer
- **B: Только прямая колонка** в `canonical_return` — заполняется при нормализации
- **C: Прямая колонка + fallback JOIN** — COALESCE

**Выбор: C (Direct + Fallback)**

**Обоснование:**
1. `canonical_order_id` в `canonical_return` сейчас **не заполняется** (`CanonicalEntityMapper.toReturn()` не ставит его) → JOIN всегда даёт NULL. Исправление order linkage — отдельная задача.
2. WB fulfillment известен при нормализации ("FBW") — нет смысла делать JOIN когда значение детерминировано.
3. Ozon fulfillment неизвестен при нормализации (unified returns API) — будет null, но заработает через JOIN когда order linkage починят.
4. `COALESCE(cr.fulfillment_type, co.fulfillment_type)` — берёт прямое значение если есть, иначе пробует JOIN.

### Step 3.1: Add `fulfillmentType` to `NormalizedReturnItem`

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/normalized/NormalizedReturnItem.java`

Add field after `status`:

```java
public record NormalizedReturnItem(
        String externalReturnId,
        String sellerSku,
        int quantity,
        BigDecimal returnAmount,
        String returnReason,
        String currency,
        OffsetDateTime returnDate,
        String status,
        String fulfillmentType     // ← NEW: "FBW" for WB, null for Ozon
) {}
```

### Step 3.2: Add column to `CanonicalReturnEntity`

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/persistence/canonical/CanonicalReturnEntity.java`

Add field after `currency`:

```java
@Column(name = "fulfillment_type", length = 10)
private String fulfillmentType;
```

### Step 3.3: Populate in WB normalizer

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/wb/WbNormalizer.java`

In `normalizeReturn()` — pass `"FBW"` as last argument to `NormalizedReturnItem`:

```java
return new NormalizedReturnItem(
        item.srid(),
        null,
        1,
        BigDecimal.ZERO,
        item.returnType(),
        "RUB",
        returnDate,
        item.status(),
        "FBW"              // ← NEW
);
```

### Step 3.4: Populate in Ozon normalizer

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/ozon/OzonNormalizer.java`

In `normalizeReturn()` — pass `null` as last argument (Ozon unified returns API doesn't expose delivery_schema):

```java
return new NormalizedReturnItem(
        String.valueOf(item.id()),
        sellerSku,
        quantity,
        returnAmount,
        item.returnReasonName(),
        currency,
        returnDate,
        item.status(),
        null                // ← NEW: unknown at normalization time
);
```

### Step 3.5: Update `CanonicalEntityMapper.toReturn()`

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/CanonicalEntityMapper.java`

Add line after `entity.setCurrency(...)`:

```java
entity.setFulfillmentType(norm.fulfillmentType());
```

### Step 3.6: PostgreSQL migration

**File**: `backend/datapulse-api/src/main/resources/db/changelog/changes/0027-return-fulfillment-type.sql`

```sql
--liquibase formatted sql

--changeset datapulse:0027-return-fulfillment-type
ALTER TABLE canonical_return
    ADD COLUMN fulfillment_type varchar(10);

COMMENT ON COLUMN canonical_return.fulfillment_type
    IS 'Delivery schema: FBO, FBS (Ozon), FBW, DBS (WB). NULL for Ozon returns (resolved via canonical_order JOIN).';

--rollback ALTER TABLE canonical_return DROP COLUMN fulfillment_type;
```

Register in `db.changelog-master.yaml`:
```yaml
- include:
    file: changes/0027-return-fulfillment-type.sql
    relativeToChangelogFile: true
```

**Note:** номер 0027 — следующий после текущего последнего (0026-pricing-insight-table.sql). ClickHouse migration не нужна — `fact_returns.fulfillment_type` уже добавлена в `0008-fulfillment-type.sql`.

### Step 3.7: Update `CanonicalReturnUpsertRepository`

**File**: `backend/datapulse-etl/src/main/java/io/datapulse/etl/persistence/canonical/CanonicalReturnUpsertRepository.java`

Changes to UPSERT SQL:
- INSERT column list: add `fulfillment_type` after `currency` (position 13)
- VALUES: 14 `?` placeholders (was 13), shift `job_execution_id` to position 14
- ON CONFLICT SET: add `fulfillment_type = EXCLUDED.fulfillment_type`
- IS DISTINCT FROM: add `canonical_return.fulfillment_type` / `EXCLUDED.fulfillment_type`

In `batchUpsert` lambda:
- Add `ps.setString(13, e.getFulfillmentType())` after `ps.setString(12, e.getCurrency())`
- Shift `job_execution_id` from position 13 to 14: `ps.setLong(14, e.getJobExecutionId())`

### Step 3.8: Update `FactReturnsMaterializer`

**File**: `backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/domain/materializer/fact/FactReturnsMaterializer.java`

Change PG_QUERY and incremental query to use COALESCE + add direct column:

```sql
SELECT cr.id                AS return_id,
       cr.connection_id,
       cr.source_platform,
       COALESCE(cr.fulfillment_type, co.fulfillment_type) AS fulfillment_type,
       cr.external_return_id,
       ...
FROM canonical_return cr
LEFT JOIN canonical_order co ON cr.canonical_order_id = co.id
```

This gives:
- WB returns → `cr.fulfillment_type = 'FBW'` → COALESCE returns 'FBW' ✅
- Ozon returns (no order link) → `cr.fulfillment_type = NULL`, `co.fulfillment_type = NULL` → returns NULL (acceptable until order linkage fixed)
- Ozon returns (with order link, future) → `cr.fulfillment_type = NULL`, `co.fulfillment_type = 'FBO'` → returns 'FBO' ✅

---

## File Change Summary

### New files

| File | Description |
|---|---|
| `backend/.../db/changelog/changes/0027-return-fulfillment-type.sql` | PG migration: add `fulfillment_type` to `canonical_return` |

### Modified files

| File | Change |
|---|---|
| `db.changelog-master.yaml` | Register migration 0027 |
| `NormalizedReturnItem.java` | Add `fulfillmentType` field (last position) |
| `CanonicalReturnEntity.java` | Add `fulfillmentType` field + JPA annotation |
| `OzonNormalizer.java` | `normalizeReturn()`: pass `null` as fulfillmentType |
| `WbNormalizer.java` | `normalizeReturn()`: pass `"FBW"` as fulfillmentType |
| `CanonicalEntityMapper.java` | `toReturn()`: set `fulfillmentType` from normalized |
| `CanonicalReturnUpsertRepository.java` | Add `fulfillment_type` to UPSERT SQL + bind param (position 13, shift job_execution_id to 14) |
| `FactReturnsMaterializer.java` | PG_QUERY + incremental: `COALESCE(cr.fulfillment_type, co.fulfillment_type)` |

### NOT modified (by design)

| File | Reason |
|---|---|
| ClickHouse migration | `fact_returns.fulfillment_type` already added in `0008-fulfillment-type.sql` |
| `MartProductPnlMaterializer.java` | Product-level aggregation stays fulfillment-agnostic (by design decision) |
| `OzonReturnItem.java` | Ozon returns API doesn't expose delivery_schema |
| `WbReturnItem.java` | WB returns API doesn't have explicit fulfillment field |

---

## Backfill Strategy

After deployment, existing `canonical_return` rows will have `fulfillment_type = NULL`.

### WB: SQL backfill (immediate)

```sql
UPDATE canonical_return
SET fulfillment_type = 'FBW', updated_at = now()
WHERE source_platform = 'wb'
  AND fulfillment_type IS NULL;
```

Safe because WB returns API returns only FBW returns. DBS not yet integrated.

### Ozon: deferred

Ozon returns will remain NULL until `canonical_order_id` linkage is implemented. This is acceptable — FBO/FBS breakdown for Ozon returns is a data quality improvement tracked separately.

### ClickHouse: re-materialize

After PG backfill, trigger full materialization of `fact_returns` to propagate changes to ClickHouse.

---

## Testing Checklist

### Unit tests

- [ ] `WbNormalizerTest`: verify `normalizeReturn()` returns `fulfillmentType = "FBW"`
- [ ] `OzonNormalizerTest`: verify `normalizeReturn()` returns `fulfillmentType = null`
- [ ] `CanonicalEntityMapperTest`: verify `toReturn()` maps `fulfillmentType`

### Integration tests

- [ ] `CanonicalReturnUpsertRepository`: UPSERT with `fulfillment_type = "FBW"`, verify stored
- [ ] `CanonicalReturnUpsertRepository`: UPSERT with `fulfillment_type = null`, verify stored as NULL
- [ ] Full ETL cycle: verify WB returns get `fulfillment_type = "FBW"` in `canonical_return`

### Data verification (post-deployment)

- [ ] Query `canonical_return` — verify WB entries have FBW after backfill
- [ ] Query `fact_returns` in ClickHouse — verify `fulfillment_type` populated for WB
- [ ] Verify return totals unchanged (fulfillment_type is a dimension, not a measure)

---

## Implementation Order

```
Step 3.1 (NormalizedReturnItem) → 3.3 (WbNormalizer) + 3.4 (OzonNormalizer)
→ 3.2 (Entity) → 3.5 (Mapper) → 3.6 (PG migration) → 3.7 (UpsertRepo)
→ 3.8 (FactReturnsMaterializer COALESCE)
→ Backfill (WB SQL + full CH materialization)
```

All steps in one PR — to avoid partial schema states.

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `NormalizedReturnItem` record change breaks callers | Medium | Build failure | Compile immediately; update all callers in same commit |
| UPSERT param index shift introduces silent data corruption | Low | High | Careful counting; integration test covers |
| Ozon returns stay NULL for fulfillment_type | Expected | Limited | Acceptable — Ozon doesn't expose this field. Will resolve via order linkage |
| WB backfill SQL updates too many rows | Low | Slow query | Run with `LIMIT 10000` in batches if needed |

---

## Scope Boundaries

**In scope:**
- `fulfillment_type` column in `canonical_return`
- Population from WB normalizer ("FBW")
- COALESCE fallback in `FactReturnsMaterializer`
- WB SQL backfill
- Unit + integration tests

**Out of scope (separate tasks):**
- Frontend UI for fulfillment_type filter/breakdown
- `canonical_order_id` population in `canonical_return` (order linkage — separate data quality task)
- Ozon rFBS (realFBS) support
- WB DBS return ingestion
- Seller Operations grid fulfillment column

---

## Definition of Done

- [ ] Код написан и прошёл code review
- [ ] Unit-тесты: `WbNormalizerTest`, `OzonNormalizerTest`, `CanonicalEntityMapperTest` для fulfillmentType
- [ ] Integration-тесты: `CanonicalReturnUpsertRepository` с fulfillment_type
- [ ] Liquibase миграция `0027-return-fulfillment-type.sql` создана и зарегистрирована
- [ ] `FactReturnsMaterializer` использует `COALESCE(cr.fulfillment_type, co.fulfillment_type)`
- [ ] WB backfill SQL подготовлен
- [ ] Архитектурные документы обновлены: `analytics-pnl.md` (fulfillment_type в fact_returns)
- [ ] Проект собирается, все существующие тесты проходят
