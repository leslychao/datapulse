# Промты для завершения Fulfillment Type Tracking (Phase 3 — Returns)

**Источник:** `docs/features/fulfillment-type-tracking.md`
**Дата:** 2026-04-06
**Контекст:** Phase 1 (Finance) и Phase 2 (Sales) полностью реализованы. Осталась Phase 3 — добавить fulfillment_type для возвратов.

---

## Промт 1: ETL pipeline — fulfillment_type в returns

Контекст фичи: Fulfillment Type Tracking (docs/features/fulfillment-type-tracking.md), Phase 3 — Returns.

Phase 1 (Finance) и Phase 2 (Sales) уже полностью реализованы. `fulfillment_type` сквозно прокинут для finance и sales: normalized DTO → entity → mapper → upsert → materializer → ClickHouse. Осталось аналогично прокинуть для returns.

### Что нужно сделать

**Шаг 1: Добавить `fulfillmentType` в `NormalizedReturnItem`**

Файл: `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/normalized/NormalizedReturnItem.java`

Текущее состояние record:
```java
public record NormalizedReturnItem(
        String externalReturnId,
        String sellerSku,
        int quantity,
        BigDecimal returnAmount,
        String returnReason,
        String currency,
        OffsetDateTime returnDate,
        String status
) {}
```

Добавить поле `String fulfillmentType` **последним** (после `status`).

**Шаг 2: Добавить поле в `CanonicalReturnEntity`**

Файл: `backend/datapulse-etl/src/main/java/io/datapulse/etl/persistence/canonical/CanonicalReturnEntity.java`

Добавить после строки `private String currency;` (строка 53):
```java
@Column(name = "fulfillment_type", length = 10)
private String fulfillmentType;
```

**Шаг 3: Обновить `WbNormalizer.normalizeReturn()`**

Файл: `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/wb/WbNormalizer.java`

Текущий метод `normalizeReturn()` (строка 203–214):
```java
public NormalizedReturnItem normalizeReturn(WbReturnItem item) {
    OffsetDateTime returnDate = WbTimestampParser.parseFlexible(item.orderDt());
    return new NormalizedReturnItem(
            item.srid(),
            null,
            1,
            BigDecimal.ZERO,
            item.returnType(),
            "RUB",
            returnDate,
            item.status()
    );
}
```

Добавить `"FBW"` как последний аргумент конструктора `NormalizedReturnItem` (после `item.status()`). WB returns API (GET /api/v1/analytics/goods-return) возвращает только FBW-возвраты, поэтому хардкод "FBW" корректен — аналогично тому, как `normalizeSale()` уже передаёт "FBW".

**Шаг 4: Обновить `OzonNormalizer.normalizeReturn()`**

Файл: `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/ozon/OzonNormalizer.java`

Текущий метод `normalizeReturn()` (строка 247–281) — конструктор `NormalizedReturnItem` вызывается в строке 271–280.

Добавить `null` как последний аргумент. Ozon unified returns API (`POST /v1/returns/list`) не содержит delivery_schema, поэтому fulfillmentType = null — будет resolved через JOIN к canonical_order в будущем (когда canonical_order_id начнёт заполняться для returns).

**Шаг 5: Обновить `CanonicalEntityMapper.toReturn()`**

Файл: `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/CanonicalEntityMapper.java`

Текущий метод `toReturn()` (строка 149–162):
```java
public CanonicalReturnEntity toReturn(NormalizedReturnItem norm, IngestContext ctx) {
    var entity = new CanonicalReturnEntity();
    entity.setConnectionId(ctx.connectionId());
    entity.setSourcePlatform(platformName(ctx));
    entity.setExternalReturnId(norm.externalReturnId());
    entity.setReturnDate(norm.returnDate());
    entity.setReturnAmount(norm.returnAmount());
    entity.setReturnReason(norm.returnReason());
    entity.setQuantity(norm.quantity());
    entity.setStatus(norm.status());
    entity.setCurrency(norm.currency() != null ? norm.currency() : "RUB");
    entity.setJobExecutionId(ctx.jobExecutionId());
    return entity;
}
```

Добавить **после** строки `entity.setCurrency(...)`:
```java
entity.setFulfillmentType(norm.fulfillmentType());
```

**Шаг 6: Обновить `CanonicalReturnUpsertRepository`**

Файл: `backend/datapulse-etl/src/main/java/io/datapulse/etl/persistence/canonical/CanonicalReturnUpsertRepository.java`

Изменения в SQL `UPSERT`:

1. INSERT column list — добавить `fulfillment_type` после `currency`:
```sql
INSERT INTO canonical_return (connection_id, source_platform, external_return_id,
                              canonical_order_id, marketplace_offer_id, seller_sku_id,
                              return_date, return_amount, return_reason,
                              quantity, status, currency, fulfillment_type,
                              job_execution_id, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
```

Было 13 плейсхолдеров `?`, стало 14. `fulfillment_type` = позиция 13, `job_execution_id` сдвигается на позицию 14.

2. ON CONFLICT ... DO UPDATE SET — добавить:
```sql
fulfillment_type = EXCLUDED.fulfillment_type,
```

3. IS DISTINCT FROM — добавить `canonical_return.fulfillment_type` и `EXCLUDED.fulfillment_type` в обе стороны сравнения.

4. В лямбде `batchUpsert` — добавить **после** `ps.setString(12, e.getCurrency())`:
```java
ps.setString(13, e.getFulfillmentType());
```
И сдвинуть job_execution_id:
```java
ps.setLong(14, e.getJobExecutionId());
```

**Шаг 7: Создать PostgreSQL миграцию**

Создать файл: `backend/datapulse-api/src/main/resources/db/changelog/changes/0027-return-fulfillment-type.sql`

```sql
--liquibase formatted sql

--changeset datapulse:0027-return-fulfillment-type
ALTER TABLE canonical_return
    ADD COLUMN fulfillment_type varchar(10);

COMMENT ON COLUMN canonical_return.fulfillment_type
    IS 'Delivery schema: FBO, FBS (Ozon), FBW, DBS (WB). NULL for Ozon returns (resolved via canonical_order JOIN).';

--rollback ALTER TABLE canonical_return DROP COLUMN fulfillment_type;
```

Зарегистрировать в `backend/datapulse-api/src/main/resources/db/changelog/db.changelog-master.yaml`:
```yaml
  - include:
      file: changes/0027-return-fulfillment-type.sql
      relativeToChangelogFile: true
```

Добавить в конец файла (после записи для `0026-pricing-insight-table.sql`).

**ClickHouse миграция НЕ нужна** — колонка `fulfillment_type` в `fact_returns` уже добавлена в `0008-fulfillment-type.sql`.

### Тесты

Добавить или обновить unit-тесты:

1. **`WbNormalizerTest`** — проверить что `normalizeReturn()` возвращает item с `fulfillmentType = "FBW"`.

2. **`OzonNormalizerTest`** — проверить что `normalizeReturn()` возвращает item с `fulfillmentType = null`.

3. **`CanonicalEntityMapperTest`** — проверить что `toReturn()` маппит `fulfillmentType` из normalized в entity:
   - Создать `NormalizedReturnItem` с `fulfillmentType = "FBW"` → entity.getFulfillmentType() == "FBW"
   - Создать `NormalizedReturnItem` с `fulfillmentType = null` → entity.getFulfillmentType() == null

### Файлы для изменения:
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/normalized/NormalizedReturnItem.java`
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/persistence/canonical/CanonicalReturnEntity.java`
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/wb/WbNormalizer.java`
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/ozon/OzonNormalizer.java`
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/CanonicalEntityMapper.java`
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/persistence/canonical/CanonicalReturnUpsertRepository.java`
- `backend/datapulse-api/src/main/resources/db/changelog/changes/0027-return-fulfillment-type.sql` (NEW)
- `backend/datapulse-api/src/main/resources/db/changelog/db.changelog-master.yaml`
- Тест-файлы для WbNormalizer, OzonNormalizer, CanonicalEntityMapper

Не трогай другие файлы. Следуй coding style из правил проекта (Google Java Style, 2 пробела, constructor injection через @RequiredArgsConstructor, AssertJ для assert-ов в тестах).

---

## Промт 2: Materializer COALESCE + backfill + архдоки

Контекст фичи: Fulfillment Type Tracking (docs/features/fulfillment-type-tracking.md), Phase 3 — Returns (продолжение).

Промт 1 уже выполнен: `fulfillment_type` прокинут сквозь ETL pipeline для returns (NormalizedReturnItem → Entity → Mapper → UpsertRepo → PG migration). Теперь нужно обновить materializer и подготовить backfill.

### Что нужно сделать

**Шаг 1: Обновить `FactReturnsMaterializer`**

Файл: `backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/domain/materializer/fact/FactReturnsMaterializer.java`

В **PG_QUERY** (строка 26–43) и в **инкрементальном запросе** (строка 85–101) — заменить:
```sql
co.fulfillment_type,
```
на:
```sql
COALESCE(cr.fulfillment_type, co.fulfillment_type) AS fulfillment_type,
```

Логика: берём fulfillment_type напрямую из `canonical_return` (заполнен для WB — "FBW"). Если NULL (Ozon) — fallback на JOIN к `canonical_order`. Если оба NULL — вернёт NULL (допустимо, пока canonical_order_id linkage не реализован).

Остальной код materializer'а (CH_INSERT, insertBatch) НЕ менять — они уже корректно обрабатывают `fulfillment_type` (позиция 4 в insertBatch).

**Шаг 2: Подготовить backfill SQL**

Создать файл: `docs/features/fulfillment-type-backfill.sql`

```sql
-- WB backfill: set fulfillment_type = 'FBW' for existing WB returns
-- Safe: WB returns API returns only FBW returns
-- Run AFTER migration 0027-return-fulfillment-type.sql is applied
-- Run BEFORE triggering full materialization

UPDATE canonical_return
SET fulfillment_type = 'FBW', updated_at = now()
WHERE source_platform = 'wb'
  AND fulfillment_type IS NULL;

-- Ozon returns: remain NULL (unified API doesn't expose delivery_schema)
-- Will be resolved when canonical_order_id linkage is implemented

-- After this SQL, trigger full materialization for fact_returns:
-- This will re-populate ClickHouse with correct fulfillment_type values
```

**Шаг 3: Обновить архитектурный документ `docs/modules/analytics-pnl.md`**

Найти секцию, описывающую `fact_returns` или Star Schema / Fact Tables. Добавить информацию:
- `fact_returns` теперь содержит `fulfillment_type` (LowCardinality(Nullable(String)))
- Значение берётся через `COALESCE(cr.fulfillment_type, co.fulfillment_type)` — прямое значение с fallback на JOIN к canonical_order
- WB: "FBW", Ozon: NULL (будет resolved через order linkage)

Если секция Star Schema содержит таблицу columns для fact_returns — добавить `fulfillment_type` в список.

**Шаг 4: Обновить архитектурный документ `docs/modules/etl-pipeline.md`**

Если документ содержит описание canonical модели или поля canonical_return — добавить `fulfillment_type varchar(10)` в описание.

### Тесты

Добавить unit-тест для `FactReturnsMaterializer`:

Проверить что PG_QUERY содержит `COALESCE(cr.fulfillment_type, co.fulfillment_type)` — можно проверить через рефлексию или просто проверить что SQL-строка содержит нужный фрагмент. Если в проекте нет такого паттерна для materializer-тестов — создать минимальный тест.

### Файлы для изменения:
- `backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/domain/materializer/fact/FactReturnsMaterializer.java`
- `docs/features/fulfillment-type-backfill.sql` (NEW)
- `docs/modules/analytics-pnl.md`
- `docs/modules/etl-pipeline.md`

Не трогай файлы из Промта 1 (ETL pipeline). Следуй coding style из правил проекта (Google Java Style, 2 пробела).

### После выполнения промта

Обнови Acceptance Criteria в `docs/features/fulfillment-type-tracking.md`:
- `[ ] Returns: fulfillment_type заполняется в canonical_return` → `[x]`
- `[ ] ClickHouse: fact_returns.fulfillment_type populated` → `[x]`
- `[ ] Materializer: COALESCE` → `[x]`

Обнови Testing Checklist — отметь выполненные тесты.
