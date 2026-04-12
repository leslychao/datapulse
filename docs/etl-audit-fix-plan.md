# ETL Audit — Продуктовые решения и промты для реализации

> **Дата:** 2026-04-12
> **Контекст:** Каждая секция содержит описание проблемы, детальное продуктовое решение и готовый промт для отдельного чата по реализации.

---

## Архитектурный контекст (общий для всех решений)

Ключевые контракты, на которые опираются решения:

- `SubSourceResult` имеет `recordsSkipped`, `errors`, статусы `COMPLETED / COMPLETED_WITH_ERRORS / FAILED`
- `EventResult.fromSubSources()` агрегирует статусы sub-source-ов в статус event-а
- `IngestJobCompletionCoordinator.determineFinalStatus()` агрегирует event-ы в статус job-а
- Бизнес-ключ в аналитике: `(workspace_id, source_platform)`, `connection_id` — provenance
- `ReplacingMergeTree(ver)` с ORDER BY = ключ дедупликации; `SETTINGS final = 1` в мартах
- PG canonical: idempotent upsert по `(workspace_id, source_platform, external_*_id)`

---

## Phase 1 — Data correctness

---

### F-01: SubSourceRunner — тихая потеря данных при ошибке батча

**Severity:** CRITICAL

#### Проблема

В `SubSourceRunner.processOnePage()` (строки 88-100) ошибка `batchProcessor.accept(batch)` ловится, batch считается skipped (`counts[1]++`), но:

1. Страница всё равно помечается `PROCESSED` (строка 53)
2. `processPages()` возвращает `SubSourceResult.success()` с `recordsSkipped=0`, хотя `totalRecordsSkipped > 0` — метод `success()` хардкодит `recordsSkipped=0`

Это тихая потеря финансовых данных. Pipeline выглядит успешным, но записи пропущены.

#### Продуктовое решение

**Принцип:** ошибка батча при обработке финансовых данных — это потеря данных. Pipeline не должен считаться успешным.

1. В `processOnePage()`: если `counts[1] > 0` после прохода всех батчей — бросить `BatchSkipException(counts[0], counts[1])`. Это переведёт обработку страницы в catch-блок внешнего цикла → `JobItemStatus.FAILED` → resume token сохранится.

2. В `processPages()`: после цикла, если `totalRecordsSkipped > 0` и `errors` пуст — возвращать `SubSourceResult.partial()` вместо `success()`:

```java
if (!errors.isEmpty() && totalRecordsProcessed == 0) {
    return SubSourceResult.failed(...);
}
if (!errors.isEmpty() || totalRecordsSkipped > 0) {
    return SubSourceResult.partial(sourceId, firstFailureResumeToken,
        capturedPages.size(), totalRecordsProcessed, totalRecordsSkipped, errors);
}
return SubSourceResult.success(...);
```

3. `BatchSkipException` — новый unchecked exception в `domain/`, содержит `processedCount` и `skippedCount`.

4. Цепочка status propagation (уже работает): `SubSourceResult.partial` → `EventResult.fromSubSources()` → `COMPLETED_WITH_ERRORS` → `determineFinalStatus()` → job не выглядит полностью успешным.

#### Файлы

- `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/SubSourceRunner.java`
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/SubSourceResult.java`
- Новый: `BatchSkipException.java` в `domain/`
- Тест: `SubSourceRunnerTest.java` — добавить сценарий batch failure → partial result, page FAILED

#### Промт для чата

```
Исправь обработку ошибок батчей в SubSourceRunner.

## Проблема

В `SubSourceRunner.processOnePage()` (строки 88-100) исключение из `batchProcessor.accept(batch)` ловится, batch считается skipped (`counts[1]++`), но:
1. Страница всё равно помечается `PROCESSED` (строка 53)
2. `processPages()` возвращает `SubSourceResult.success()` с `recordsSkipped=0` (строка 80), даже если `totalRecordsSkipped > 0`

Это тихая потеря финансовых данных. Pipeline выглядит успешным, но записи пропущены.

## Решение

1. В `processOnePage()`: если `counts[1] > 0` после прохода всех батчей — бросить новый `BatchSkipException(counts[0], counts[1])`. Это переведёт обработку страницы в catch-блок внешнего цикла в `processPages()` → `JobItemStatus.FAILED` → resume token сохранится.

2. В `processPages()`: после цикла по страницам, если `totalRecordsSkipped > 0` и `errors` пуст (т.е. только batch-level skips, не page-level errors) — возвращать `SubSourceResult.partial()` вместо `success()`:

    ```java
    if (!errors.isEmpty() && totalRecordsProcessed == 0) {
        return SubSourceResult.failed(...);
    }
    if (!errors.isEmpty() || totalRecordsSkipped > 0) {
        return SubSourceResult.partial(sourceId, firstFailureResumeToken,
            capturedPages.size(), totalRecordsProcessed, totalRecordsSkipped, errors);
    }
    return SubSourceResult.success(...);
    ```

3. `BatchSkipException` — новый unchecked exception в пакете `domain/`, содержит processedCount и skippedCount.

## Status propagation chain (не трогать — уже работает)

- SubSourceResult.partial → EventResult.fromSubSources() → COMPLETED_WITH_ERRORS
- determineFinalStatus() → COMPLETED_WITH_ERRORS
- Job не выглядит как полностью успешный

## Файлы

- `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/SubSourceRunner.java`
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/SubSourceResult.java`
- Новый: `BatchSkipException.java` в `domain/`
- Тест: `backend/datapulse-etl/src/test/java/io/datapulse/etl/domain/SubSourceRunnerTest.java` — добавить сценарий: batch failure → partial result, page FAILED

## Критерии готовности

- Если хотя бы 1 batch skipped → страница FAILED, SubSourceResult partial
- Существующие тесты проходят
- Новый тест покрывает сценарий batch failure
```

---

### F-02: WbFinanceReadAdapter — тихий обрыв пагинации

**Severity:** CRITICAL

#### Проблема

`pageCapture.capture()` (строка 66 WbFinanceReadAdapter.java) обёрнут в catch-all: любая ошибка → `hasMore = false; break` → адаптер возвращает неполный список страниц как будто всё в порядке. Для финансовых данных частичная выгрузка хуже полного сбоя.

#### Продуктовое решение

1. Разделить "конец данных" и "ошибку". WB reportDetailByPeriod завершается двумя способами:
   - HTTP 204 / пустой body → легитимный конец
   - Пустой массив `[]` (< 10 байт) → легитимный конец
   - Network/S3/parse error → ошибка, нужен retry

2. Ввести `EmptyResponseException` в `StreamingPageCapture` — бросается когда тело ответа пустое. Это типизированный сигнал «конец данных».

3. Заменить catch-all на typed handling:

```java
PageCaptureResult page;
try {
    page = pageCapture.capture(body, context, pageNumber, CURSOR_EXTRACTOR);
} catch (EmptyResponseException e) {
    log.info("WB finance: end of data: connectionId={}", context.connectionId());
    hasMore = false;
    break;
}
// Все остальные исключения пробрасываются наружу
```

4. При network/S3 ошибке исключение пробрасывается → retry через DAG. Уже захваченные страницы в S3 не теряются; resume через `rrdid`.

#### Файлы

- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/wb/WbFinanceReadAdapter.java`
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/util/StreamingPageCapture.java`
- Тест: WireMock — ошибка на 3-й странице → exception propagation

#### Промт для чата

```
Исправь обработку ошибок в WbFinanceReadAdapter — замени catch-all на типизированную обработку.

## Проблема

В `WbFinanceReadAdapter.captureAllPages()` (строка ~66) `pageCapture.capture()` обёрнут в `catch(Exception)`:
- Любая ошибка (network, S3, parse) → `hasMore = false; break`
- Адаптер возвращает неполный список страниц как "успех"
- Для финансовых данных это хуже полного fail — pipeline выглядит зелёным, данные неполные

## Решение

1. **Ввести `EmptyResponseException`** (unchecked, в пакете `adapter/util/`) — бросается в `StreamingPageCapture.capture()` когда S3-файл после записи имеет 0 байт (пустой body от API).

2. **В `WbFinanceReadAdapter.captureAllPages()`** заменить catch-all:

    ```java
    PageCaptureResult page;
    try {
        page = pageCapture.capture(body, context, pageNumber, CURSOR_EXTRACTOR);
    } catch (EmptyResponseException e) {
        log.info("WB finance: end of data (empty response): connectionId={}, page={}",
            context.connectionId(), pageNumber);
        hasMore = false;
        break;
    }
    // Все остальные исключения НЕ ловим — пробрасываются наружу
    ```

3. **В `StreamingPageCapture.capture()`**: после записи temp file, если `tempFile.length() == 0` → бросить `new EmptyResponseException("Empty response body")`.

4. **Убрать проверку `byteSize < 10`** — она заменяется `EmptyResponseException`. Если ответ `[]`, он будет записан (2 байта), cursor extractor вернёт null → `hasMore = false` по существующей логике.

## Retry behavior (не трогать — уже работает)

При network/S3 ошибке исключение пробрасывается → EventSource.execute() → EventRunner.run() → EventResult.failed() → DAG → IngestJobCompletionCoordinator → retry scheduling. Уже захваченные S3 pages не теряются; resume через `rrdid` последней успешной страницы.

## Файлы

- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/wb/WbFinanceReadAdapter.java`
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/util/StreamingPageCapture.java`
- Новый: `EmptyResponseException.java` в `adapter/util/`

## Критерии готовности

- Пустой ответ (0 байт) → EmptyResponseException → graceful pagination stop
- Network error → exception propagation → job retry
- Проверка `byteSize < 10` удалена
- Сборка зелёная
```

---

### F-04: fact_product_cost и dim_warehouse без workspace_id в ClickHouse

**Severity:** CRITICAL

#### Проблема

Миграция 0009 пересоздала все таблицы с `workspace_id` в ORDER BY, но **пропустила** `fact_product_cost` (ORDER BY `(seller_sku_id, valid_from)`) и `dim_warehouse` (ORDER BY `(marketplace_type, warehouse_id)`). Это нарушает tenant isolation — данные разных workspace могут дедуплицироваться друг в друга через ReplacingMergeTree.

**Текущее состояние PG:**
- `warehouse` — `workspace_id` **есть** (миграция 0029), но materializer его **не выбирает**
- `cost_profile` — `workspace_id` **нет**. Связь: `cost_profile.seller_sku_id` → `seller_sku.product_master_id` → `product_master.workspace_id`

#### Продуктовое решение

**dim_warehouse:**
1. CH миграция: DROP + CREATE с `workspace_id` в ORDER BY
2. Materializer: добавить `w.workspace_id` в PG query SELECT и CH insert

**fact_product_cost:**
1. PG миграция: добавить `workspace_id` в `cost_profile` (денормализация), backfill через join к `seller_sku → product_master`, NOT NULL + FK
2. CH миграция: DROP + CREATE с `workspace_id` в ORDER BY
3. Materializer: добавить `cp.workspace_id` в PG query SELECT и CH insert
4. Обновить mart cost join: добавить `workspace_id` в join condition

#### Файлы

- Новая PG миграция (liquibase changeset)
- Новая CH миграция: `0012-workspace-scoped-cost-warehouse.sql`
- `DimWarehouseMaterializer.java`
- `FactProductCostMaterializer.java`
- `MartPostingPnlMaterializer.java` — cost join

#### Промт для чата

```
Добавь workspace_id в fact_product_cost и dim_warehouse — исправь пропуск в workspace-scoping.

## Проблема

Миграция `0009-workspace-scoped-keys.sql` пересоздала все CH-таблицы с `workspace_id` в ORDER BY, но **пропустила**:
- `fact_product_cost` — ORDER BY `(seller_sku_id, valid_from)` без workspace_id
- `dim_warehouse` — ORDER BY `(marketplace_type, warehouse_id)` без workspace_id

ReplacingMergeTree дедуплицирует по ORDER BY → данные разных workspace могут затирать друг друга.

## Текущее состояние PostgreSQL

- **warehouse**: `workspace_id` уже есть (добавлен в миграции 0029), но `DimWarehouseMaterializer` его **не выбирает** из PG и **не вставляет** в CH.
- **cost_profile**: `workspace_id` **нет**. Связь: `cost_profile.seller_sku_id` → `seller_sku.product_master_id` → `product_master.workspace_id`.

## Решение — dim_warehouse

1. **CH миграция** `backend/datapulse-etl/src/main/resources/db/clickhouse/0012-workspace-scoped-cost-warehouse.sql`:
   - `DROP TABLE IF EXISTS dim_warehouse`
   - `CREATE TABLE dim_warehouse` со всеми существующими колонками + `workspace_id UInt64`
   - `ENGINE = ReplacingMergeTree(ver) ORDER BY (workspace_id, marketplace_type, warehouse_id)`

2. **DimWarehouseMaterializer** (`backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/domain/materializer/dim/DimWarehouseMaterializer.java`):
   - В PG SELECT добавить `w.workspace_id`
   - В CH INSERT добавить `workspace_id`
   - В incremental SELECT аналогично

## Решение — fact_product_cost

1. **PG миграция** (новый liquibase changeset):
   ```sql
   ALTER TABLE cost_profile ADD COLUMN workspace_id bigint;

   UPDATE cost_profile cp SET workspace_id = (
       SELECT pm.workspace_id FROM seller_sku ss
       JOIN product_master pm ON ss.product_master_id = pm.id
       WHERE ss.id = cp.seller_sku_id
   );

   ALTER TABLE cost_profile ALTER COLUMN workspace_id SET NOT NULL;
   ALTER TABLE cost_profile ADD CONSTRAINT fk_cost_profile_workspace
       FOREIGN KEY (workspace_id) REFERENCES workspace(id);
   ```

2. **CH миграция** (в том же `0012-...`):
   - `DROP TABLE IF EXISTS fact_product_cost`
   - `CREATE TABLE fact_product_cost` со всеми существующими колонками + `workspace_id UInt64`
   - `ENGINE = ReplacingMergeTree(ver) ORDER BY (workspace_id, seller_sku_id, valid_from)`

3. **FactProductCostMaterializer** (`backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/domain/materializer/fact/FactProductCostMaterializer.java`):
   - В PG SELECT добавить `cp.workspace_id`
   - В CH INSERT добавить `workspace_id`

4. **MartPostingPnlMaterializer**: в SCD2 join к `fact_product_cost` — добавить `workspace_id` в join condition.

## Файлы

- Новая PG миграция (номер следующий за последним в `backend/datapulse-api/src/main/resources/db/changelog/changes/`)
- `backend/datapulse-etl/src/main/resources/db/clickhouse/0012-workspace-scoped-cost-warehouse.sql`
- `backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/domain/materializer/dim/DimWarehouseMaterializer.java`
- `backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/domain/materializer/fact/FactProductCostMaterializer.java`
- `backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/domain/materializer/mart/MartPostingPnlMaterializer.java` — cost join

## Критерии готовности

- CH миграция: обе таблицы пересозданы с workspace_id в ORDER BY
- PG миграция: cost_profile имеет workspace_id NOT NULL + FK
- Оба materializer-а выбирают и вставляют workspace_id
- Mart cost join включает workspace_id
- Full rematerialization обязательна после деплоя
- Сборка зелёная
```

---

### F-07: Mart SQL — connection_id как бизнес-ключ вместо (workspace_id, source_platform)

**Severity:** HIGH

#### Проблема

В `MartPostingPnlMaterializer` GROUP BY и join-ы используют `connection_id`:
- `pm` GROUP BY: `posting_id, connection_id`
- `aq`, `orv`, `fs` — join по `connection_id`
- `s` — GROUP BY только `posting_id` (без изоляции вообще!)

В `MartProductPnlMaterializer` join-ы к `dim_product` — без `workspace_id`.

Архитектурное решение: бизнес-ключ = `(workspace_id, source_platform)`, `connection_id` = provenance.

#### Продуктовое решение

**MartPostingPnlMaterializer — 7 изменений:**

1. Подзапрос `pm`: GROUP BY `workspace_id, source_platform, posting_id`. `connection_id` → `any(connection_id) AS connection_id`.
2. Подзапрос `aq`: GROUP BY `workspace_id, source_platform, order_id`. JOIN: добавить `workspace_id` + `source_platform`.
3. Подзапрос `orv`: аналогично `aq`.
4. Подзапрос `fs`: GROUP BY `workspace_id, source_platform, posting_id`. JOIN: добавить.
5. Подзапрос `s`: GROUP BY `workspace_id, source_platform, posting_id` (сейчас только `posting_id`!). JOIN: добавить.
6. SCD2 cost join `pm_c`: GROUP BY `workspace_id, source_platform, posting_id`. JOIN: добавить `workspace_id`.
7. dim_product joins (`dp`, `p_by_id`, `p_by_sku`): добавить `workspace_id`.

**MartProductPnlMaterializer:**

1. Подзапросы из `mart_posting_pnl`: `(workspace_id, source_platform)` вместо `connection_id`.
2. PRODUCT/ACCOUNT ветки: GROUP BY с `workspace_id, source_platform`.
3. Финальный UNION ALL: GROUP BY с `workspace_id, source_platform`.
4. Реклама join: добавить `workspace_id` + `source_platform`.
5. dim_product joins: добавить `workspace_id`.

#### Файлы

- `MartPostingPnlMaterializer.java`
- `MartProductPnlMaterializer.java`

#### Промт для чата

```
Замени connection_id на (workspace_id, source_platform) как бизнес-ключ в мартах.

## Контекст

Архитектурное решение: 1 workspace = 1 account per marketplace. Бизнес-ключ в аналитике: `(workspace_id, source_platform)`. `connection_id` — только provenance (сохраняем в SELECT, убираем из GROUP BY и JOIN).

## Проблема

В `MartPostingPnlMaterializer`:
- `pm` GROUP BY: `posting_id, connection_id` — connection_id как бизнес-ключ
- `aq`, `orv`, `fs` — join по `connection_id`
- `s` — GROUP BY только `posting_id` (без изоляции вообще!)

В `MartProductPnlMaterializer`:
- join-ы к `dim_product` — без `workspace_id`
- GROUP BY использует `connection_id`

## Решение — MartPostingPnlMaterializer

7 изменений в SQL:

1. **`pm` подзапрос**: `GROUP BY workspace_id, source_platform, posting_id` (вместо `posting_id, connection_id`). Убрать `any(workspace_id)` и `any(source_platform)` — они теперь прямые GROUP BY колонки. Добавить `any(connection_id) AS connection_id` (provenance).

2. **`aq` подзапрос**: `GROUP BY workspace_id, source_platform, order_id`. JOIN: `pm.order_id = aq.order_id AND pm.workspace_id = aq.workspace_id AND pm.source_platform = aq.source_platform`.

3. **`orv` подзапрос**: аналогично `aq` — `GROUP BY workspace_id, source_platform, order_id`. JOIN: добавить `workspace_id` и `source_platform`.

4. **`fs` подзапрос**: `GROUP BY workspace_id, source_platform, posting_id`. JOIN: добавить `workspace_id` и `source_platform`.

5. **`s` подзапрос**: `GROUP BY workspace_id, source_platform, posting_id` (КРИТИЧНО: сейчас только `posting_id`, нет изоляции). JOIN: добавить `workspace_id` и `source_platform`.

6. **SCD2 cost join `pm_c`**: `GROUP BY workspace_id, source_platform, posting_id`. JOIN с `fact_product_cost`: добавить `workspace_id` (после F-04).

7. **dim_product joins** (`dp`, `p_by_id`, `p_by_sku`): добавить `workspace_id` в каждый join condition.

## Решение — MartProductPnlMaterializer

5 изменений:

1. Подзапросы из `mart_posting_pnl`: заменить `connection_id` на `(workspace_id, source_platform)` как ключ группировки.
2. PRODUCT/ACCOUNT ветки: `GROUP BY workspace_id, source_platform, ...` вместо `connection_id, ...`.
3. Финальный UNION ALL: `GROUP BY workspace_id, source_platform, seller_sku_id, period, attribution_level`.
4. Реклама join: `base.workspace_id = ad_agg.workspace_id AND base.source_platform = ad_agg.source_platform`.
5. dim_product joins: добавить `workspace_id`.

## Файлы

- `backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/domain/materializer/mart/MartPostingPnlMaterializer.java`
- `backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/domain/materializer/mart/MartProductPnlMaterializer.java`

## Критерии готовности

- Все GROUP BY в обоих мартах используют (workspace_id, source_platform) вместо connection_id
- connection_id сохранён через any() в SELECT как provenance
- Подзапрос `s` имеет workspace_id и source_platform в GROUP BY (критический gap)
- Все join conditions включают workspace_id и source_platform
- dim_product joins включают workspace_id
- INCR_MARKER (incremental HAVING) не меняется — posting_id уникален в рамках workspace+platform
- Full rematerialization после деплоя
- Сборка зелёная
```

---

### F-14 + F-15: Rate limit group fix + Index fix

**Severity:** MEDIUM

#### Промт для чата

```
Два мелких фикса: rate limit group и индекс.

## F-14: Неправильная RateLimitGroup в WbFinanceReadAdapter

В `WbFinanceReadAdapter.java` (строка ~59) используется `RateLimitGroup.WB_STATISTICS` вместо `RateLimitGroup.WB_FINANCE`. Оба имеют одинаковые rate-параметры, но семантически WB_FINANCE — правильная группа для finance endpoint.

**Файл:** `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/wb/WbFinanceReadAdapter.java`
**Правка:** заменить `RateLimitGroup.WB_STATISTICS` → `RateLimitGroup.WB_FINANCE`

## F-15: Index на неправильной таблице

В миграции `0004-etl-canonical-tables.sql` есть опечатка:
```sql
CREATE INDEX idx_cost_profile_user_id ON canonical_finance_entry (updated_by_user_id);
```
Должно быть `ON cost_profile`.

**Решение:** новая PG миграция (номер следующий за последним changeset):
```sql
DROP INDEX IF EXISTS idx_cost_profile_user_id;
CREATE INDEX idx_cost_profile_user_id ON cost_profile (updated_by_user_id);
```

## Файлы

- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/wb/WbFinanceReadAdapter.java`
- Новая PG миграция в `backend/datapulse-api/src/main/resources/db/changelog/changes/`
- Обновить master changelog

## Критерии готовности

- RateLimitGroup.WB_FINANCE в WbFinanceReadAdapter
- Старый неправильный индекс дропнут, новый создан на cost_profile
- Миграция добавлена в master changelog
- Сборка зелёная
```

---

## Phase 2 — Stability and observability

---

### F-06: Unmapped finance types → OTHER без alert-а

**Severity:** HIGH

#### Проблема

`FinanceEntryType.fromOzon/fromWb()` возвращает `OTHER` для неизвестных кодов. В нормализаторах — `log.warn` на каждую строку, но нет агрегированного сигнала на уровне job-а.

#### Продуктовое решение

1. Собирать unmapped типы через closure в EventSource (не ломая `Consumer<List<T>>` контракт `SubSourceRunner`)
2. Добавить `Set<String> unmappedTypeNames` в `SubSourceResult`
3. Публиковать domain event из `IngestJobCompletionCoordinator` → `UnmappedFinanceTypesDetected`
4. Listener в audit-alerting → alert для workspace owner

#### Промт для чата

```
Добавь отслеживание и алертинг unmapped finance types.

## Проблема

`FinanceEntryType.fromOzonOperationType()` и `fromWbSupplierOperName()` возвращают `OTHER` для неизвестных кодов. В нормализаторах — `log.warn` на каждую запись, но:
1. Нет агрегированного подсчёта за job
2. Нет alert-а для пользователя
3. Новый тип операции у маркетплейса → тихо идёт в OTHER → искажение P&L

## Решение

### 1. Closure в EventSource для сбора unmapped types

В `WbFinanceFactSource.execute()` и `OzonFinanceFactSource.execute()`:

```java
Set<String> unmappedTypes = ConcurrentHashMap.newKeySet();

SubSourceResult result = subSourceRunner.processPages(
    SOURCE_ID, pages, WbFinanceRow.class,
    batch -> {
        var normalized = batch.stream()
            .map(row -> {
                var item = normalizer.normalizeFinance(row);
                if (item.entryType() == FinanceEntryType.OTHER) {
                    String rawType = row.supplierOperName(); // или row.operationType() для Ozon
                    if (rawType != null && !rawType.isBlank()) {
                        unmappedTypes.add(rawType);
                    }
                }
                return item;
            }).toList();
        repository.batchUpsert(normalized, ctx);
    });
```

### 2. Расширить SubSourceResult

Добавить `Set<String> unmappedTypeNames` (default: `Set.of()`). Новый factory method:

```java
public static SubSourceResult successWithWarnings(
    String sourceId, int pages, int records,
    Set<String> unmappedTypeNames) { ... }
```

Если `unmappedTypes` не пуст — вернуть `successWithWarnings()` вместо `success()`.

### 3. Domain event

В `IngestJobCompletionCoordinator` или `IngestResultReporter` — если `EventResult` содержит sub-source-ы с непустыми `unmappedTypeNames`:

```java
applicationEventPublisher.publishEvent(
    new UnmappedFinanceTypesDetected(workspaceId, connectionId, marketplace, unmappedTypes));
```

### 4. Listener в audit-alerting

`@Component` в `datapulse-audit-alerting`:
- Слушает `UnmappedFinanceTypesDetected`
- Создаёт alert: "Обнаружены неизвестные типы финансовых операций: {types}. Данные попали в категорию 'Прочее'."

## Файлы

- `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/source/wb/WbFinanceFactSource.java`
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/source/ozon/OzonFinanceFactSource.java`
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/SubSourceResult.java`
- Новый domain event: `UnmappedFinanceTypesDetected.java`
- Новый listener в audit-alerting

## Критерии готовности

- Unmapped types собираются за весь job, не log.warn на каждую строку
- SubSourceResult несёт Set<String> unmappedTypeNames
- Domain event публикуется при наличии unmapped types
- Alert создаётся для workspace owner
- Не ломает существующий контракт Consumer<List<T>> в SubSourceRunner
```

---

### F-09: OzonFboOrdersReadAdapter — нет date-window split

**Severity:** HIGH

#### Промт для чата

```
Добавь date-window splitting в OzonFboOrdersReadAdapter.

## Проблема

`OzonFbsOrdersReadAdapter` разбивает большие диапазоны на окна по 60 дней через `OzonDateWindows.split()`. `OzonFboOrdersReadAdapter` — передаёт весь диапазон как есть. При выгрузке за несколько месяцев это может привести к timeout-у или огромному response.

## Решение

Добавить date-window splitting по образцу `OzonFbsOrdersReadAdapter`:

1. В `captureAllPages()` — перед основным циклом:
   ```java
   var windows = OzonDateWindows.split(since, to, MAX_WINDOW_DAYS);
   ```
   где `MAX_WINDOW_DAYS = 60`.

2. Для каждого окна после первого — `CaptureContextFactory.withNewRequestId(context)`.

3. Offset reset для не-первых окон (offset = 0, т.к. это новый date filter).

4. Агрегация результатов всех окон в единый список `capturedPages`.

Код будет практически идентичен FBS. Если появится 3-й адаптер с тем же паттерном — тогда вынести в Template Method. Пока 2 — допустимо.

## Файлы

- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/ozon/OzonFboOrdersReadAdapter.java`
- Образец: `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/ozon/OzonFbsOrdersReadAdapter.java`

## Критерии готовности

- FBO адаптер разбивает диапазоны на окна ≤ 60 дней
- Для каждого окна используется отдельный requestId
- Offset сбрасывается для каждого нового окна
- Поведение идентично FBS
- Сборка зелёная
```

---

### F-12: FactProductCostMaterializer — инкремент по времени вместо watermark

**Severity:** HIGH

#### Промт для чата

```
Замени time-based incremental на watermark-based в FactProductCostMaterializer.

## Проблема

`materializeIncremental()` использует `WHERE cp.updated_at > NOW() - INTERVAL '1 hour'` — привязка к wall clock. `jobExecutionId` параметр игнорируется (cost_profile управляется через UI, не через ETL). При задержке materialization > 1 час — пропуск изменений.

## Решение

### 1. Таблица watermark в ClickHouse

Новая CH миграция (можно в том же файле что F-04, или отдельный `0013-materialization-watermark.sql`):

```sql
CREATE TABLE IF NOT EXISTS materialization_watermark (
    table_name String,
    last_materialized_at DateTime64(3, 'UTC'),
    ver UInt64
) ENGINE = ReplacingMergeTree(ver) ORDER BY table_name;
```

### 2. Watermark helpers в MaterializationJdbc

Добавить два метода:

```java
public Instant getWatermark(String tableName) {
    // SELECT last_materialized_at FROM materialization_watermark FINAL WHERE table_name = :name
    // Returns null if never materialized
}

public void updateWatermark(String tableName, Instant instant) {
    // INSERT INTO materialization_watermark (table_name, last_materialized_at, ver) VALUES (...)
    // ver = System.currentTimeMillis()
}
```

### 3. Обновить FactProductCostMaterializer

```java
@Override
public void materializeIncremental(long jobExecutionId) {
    Instant watermark = jdbc.getWatermark(TABLE);
    if (watermark == null) {
        materializeFull();
        return;
    }

    List<Map<String, Object>> rows = jdbc.pg().queryForList("""
        SELECT ... FROM cost_profile cp
        WHERE cp.updated_at > :watermark
        """, Map.of("watermark", Timestamp.from(watermark)));

    if (rows.isEmpty()) return;

    insertBatch(rows, ver, CH_INSERT.formatted(TABLE));
    jdbc.updateWatermark(TABLE, Instant.now());
}
```

### 4. Full materialization

В `materializeFull()` — после успешного swap обновить watermark:
```java
jdbc.updateWatermark(TABLE, Instant.now());
```

## Файлы

- `backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/domain/materializer/fact/FactProductCostMaterializer.java`
- `backend/datapulse-analytics-pnl/src/main/java/io/datapulse/analytics/persistence/MaterializationJdbc.java`
- Новая CH миграция для `materialization_watermark`

## Критерии готовности

- `materialization_watermark` таблица создана в CH
- `getWatermark()` и `updateWatermark()` работают
- Incremental использует watermark вместо `NOW() - 1 hour`
- Если watermark отсутствует → full materialization
- Full materialization обновляет watermark
- Сборка зелёная
```

---

### F-17: BidActionRetryConsumer на wait queue

**Severity:** MEDIUM

#### Промт для чата

```
Раздели bid execution queue на reconcile и retry.

## Проблема

Для ETL и price execution wait-очереди используют TTL/DLX паттерн: сообщение лежит N секунд → DLX переносит в основную очередь → consumer обрабатывает. Для bids — `BidActionRetryConsumer` слушает wait-очередь напрямую, обходя DLX delay.

По коду видно, что consumer обрабатывает два типа:
- **RECONCILE** — проверить результат bid-а сейчас (delay не нужен)
- **Retry** — повторить неудавшийся bid (delay полезен)

## Решение

Разделить на две очереди:

1. **`bid.execution.reconcile`** — обычная очередь, с consumer, без delay. Для reconcile-сообщений.
2. **`bid.execution.retry`** — wait queue с TTL/DLX, **без прямого consumer**. DLX перенаправляет в основную retry-очередь. Для retry-сообщений.

### Шаги

1. В RabbitTopologyConfig определить новые очереди и binding
2. В `BidActionRetryConsumer` — слушать `bid.execution.reconcile` (обычную очередь)
3. Добавить consumer для основной retry-очереди (куда DLX перенаправляет из wait)
4. В publisher-ах: routing key определяет reconcile vs retry

## Файлы

- `backend/datapulse-api/src/main/java/io/datapulse/api/execution/BidActionRetryConsumer.java`
- RabbitMQ topology config
- Publisher(s) для bid execution messages

## Критерии готовности

- Reconcile обрабатывается мгновенно через прямую очередь
- Retry проходит через TTL/DLX delay (как ETL и price execution)
- Паттерн консистентен с остальными execution queues
- Сборка зелёная
```

---

### F-19: ClickHouseMaterializer stub — мёртвый код

**Severity:** MEDIUM

#### Промт для чата

```
Удали мёртвый код ClickHouseMaterializer и обнови документацию.

## Проблема

`backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/materializer/ClickHouseMaterializer.java` — stub-класс с debug-логированием. Реальная материализация в `datapulse-analytics-pnl`. Этот файл — мёртвый код.

## Решение

1. Удалить `ClickHouseMaterializer.java`
2. Проверить, что на него нет ссылок (import, injection, конфигурация)
3. Обновить `docs/modules/etl-pipeline.md` — убрать упоминание stub materializer, если есть

## Файлы

- Удалить: `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/materializer/ClickHouseMaterializer.java`
- Проверить: `docs/modules/etl-pipeline.md`

## Критерии готовности

- Файл удалён
- Нет broken references
- Документация актуальна
- Сборка зелёная
```

---

## Phase 3 — Coverage and Yandex

---

### F-13: Критическое тестовое покрытие

**Severity:** HIGH

#### Промт для чата

```
Добавь критические тесты для ETL pipeline: golden datasets, WireMock, CH testcontainers.

## Проблема

Нулевое тестовое покрытие критических путей: финансовая нормализация, mart SQL, retry/resume. При любом рефакторинге (например, F-07) нет способа проверить корректность P&L.

## Решение — три типа тестов

### 1. Golden dataset тесты для mart SQL (Testcontainers ClickHouse)

- Подготовить набор fact_finance + fact_sales + fact_product_cost записей с **известным ожидаемым P&L**
- Запустить `MartPostingPnlMaterializer.materializeFull()`
- Сверить result с expected: revenue, commission, logistics, COGS, net_pnl, reconciliation_residual = 0

Фикстуры: `src/test/resources/fixtures/analytics/golden-posting-pnl/`

### 2. WireMock тесты для finance adapters

**WB finance:**
- Happy path: 3 страницы, rrdid cursor progression
- Edge case: пустой response (EmptyResponseException после F-02)
- Error: 500 на 2-й странице → exception propagation

**Ozon finance:**
- Happy path: 2 date windows × 2 страницы, page_count parsing
- Edge case: page_count = 1 (single page)

### 3. Finance normalizer exhaustive mapping tests

**OzonFinanceNormalizerTest:**
- Расширить: все 56+ Ozon operation types → correct FinanceEntryType + correct measure column
- Sign convention: negative values → debit columns

**WbNormalizerTest:**
- Все WB_ types → correct FinanceEntryType
- Sign convention: WB negation check (debit fields negated)
- Return vs sale type mapping

### 4. Idempotency тест

- Двойной upsert одних и тех же canonical_finance_entry → verify no duplicates, same count, same values

## Файлы

- Новые тест-классы в `src/test/java/io/datapulse/analytics/domain/materializer/mart/`
- Новые тест-классы в `src/test/java/io/datapulse/etl/adapter/wb/`
- Расширение существующих normalizer тестов
- Фикстуры в `src/test/resources/fixtures/`

## Критерии готовности

- Golden dataset тест проходит с expected P&L
- WireMock тесты покрывают happy path + error для WB и Ozon finance
- Все known operation types имеют exhaustive mapping тест
- Idempotency test проходит
```

---

### F-03: Yandex finance normalization

**Severity:** CRITICAL

#### Промт для чата

```
Реализуй нормализацию Yandex Market финансов в canonical layer.

## Проблема

`YandexFinanceFactSource.execute()` вызывает adapter для скачивания отчётов в S3, но возвращает `SubSourceResult.success(SOURCE_ID, 1, 0)` — **0 записей нормализовано**. Нет `YandexNormalizer.normalizeFinance()`, нет маппинга в `FinanceEntryType`, нет upsert в canonical.

Данные скачиваются, но не попадают в аналитику. Yandex Market финансы полностью отсутствуют в P&L.

## Контекст — два типа отчётов Yandex

1. **united-marketplace-services** — комиссии, логистика, сервисные сборы (аналог WB detail report)
2. **goods-realization** — реализация с item-level финансами (аналог WB rrd report)

## Решение

### 1. DTO для Yandex finance report rows

Создать record-ы в `adapter/yandex/dto/`:
- `YandexServiceReportRow` — строка из united-marketplace-services
- `YandexRealizationReportRow` — строка из goods-realization

Поля определить по реальной структуре ответа (уточнить по API docs или provider-api-specs).

### 2. Yandex operation types в FinanceEntryType

Добавить Yandex-специфичные типы в `FinanceEntryType`:
- `YANDEX_COMMISSION` → commission
- `YANDEX_LOGISTICS` → logistics
- `YANDEX_STORAGE` → storage
- `YANDEX_RETURN_LOGISTICS` → return_logistics
- и т.д.

Добавить `fromYandexOperationType(String operationType)` по аналогии с `fromOzon/fromWb`.

### 3. YandexNormalizer.normalizeFinance()

В `YandexNormalizer.java` добавить:
```java
public NormalizedFinanceItem normalizeFinance(YandexServiceReportRow row) {
    // Map Yandex fields → NormalizedFinanceItem
    // Sign convention: positive = credit to seller, negative = debit
    // Map to correct measure columns (commission, logistics, storage, etc.)
}
```

### 4. Обновить YandexFinanceFactSource.execute()

После capture вызывать `subSourceRunner.processPages()`:
```java
var result = subSourceRunner.processPages(
    SOURCE_ID, pages, YandexServiceReportRow.class,
    batch -> {
        var normalized = batch.stream()
            .map(normalizer::normalizeFinance)
            .toList();
        repository.batchUpsert(normalized, ctx);
    });
```

### 5. Golden dataset тест

Фикстура из реального Yandex отчёта → expected NormalizedFinanceItem values.

## Файлы

- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/yandex/YandexNormalizer.java` — добавить normalizeFinance
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/source/yandex/YandexFinanceFactSource.java` — wire normalization
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/domain/FinanceEntryType.java` — Yandex types
- Новые DTO в `adapter/yandex/dto/`
- Тест-фикстуры

## Предварительные требования

- Проверить формат реального Yandex finance report (API docs или provider-api-specs)
- Определить маппинг Yandex operation types → FinanceEntryType
- Определить sign conventions Yandex (positive = ?)

## Критерии готовности

- YandexNormalizer.normalizeFinance() маппит все основные operation types
- FinanceEntryType содержит Yandex types с fromYandexOperationType()
- YandexFinanceFactSource возвращает SubSourceResult с реальным recordsProcessed > 0
- Данные попадают в canonical_finance_entry → fact_finance → mart_posting_pnl
- Golden dataset тест проходит
```

---

### F-05: Ozon multi-line postings — потеря строк заказов

**Severity:** CRITICAL

#### Проблема

UNIQUE constraint на `canonical_order`: `(workspace_id, source_platform, external_order_id)`. Для Ozon `external_order_id` = `postingNumber` — **один и тот же** для всех товаров в posting.

В `OzonSalesFactSource` явный двойной цикл `for (posting) { for (product) { orders.add(...) } }` — генерируется несколько `NormalizedOrderItem` с **одинаковым** `externalOrderId = postingNumber`, но **разными** SKU.

При batch upsert побеждает **последний** INSERT для каждого `postingNumber` — все предыдущие позиции **перезатираются**. Это потеря данных для multi-line Ozon postings.

Для `canonical_sale` проблемы нет — там `externalSaleId = postingNumber + "-" + sku`, т.е. каждая позиция уникальна.

#### Продуктовое решение

**Принцип:** сделать `externalOrderId` уникальным на уровне позиции, как уже сделано для sales.

1. В `OzonNormalizer.normalizeFboPosting()` и `normalizeFbsPosting()` — изменить формирование `externalOrderId`:

```java
// Было:
return new NormalizedOrderItem(
    posting.postingNumber(),  // одинаковый для всех items
    ...);

// Стало:
return new NormalizedOrderItem(
    posting.postingNumber() + "-" + product.sku(),  // уникальный на позицию
    ...);
```

2. Это полностью повторяет паттерн, уже используемый для `normalizeFboSale` / `normalizeFbsSale`.

3. **Миграция данных:** после деплоя — полная пересинхронизация заказов Ozon для всех workspace, чтобы догрузить потерянные позиции.

4. **WB не затронут** — у WB `externalOrderId = srid` (уже per-line).

#### Файлы

- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/ozon/OzonNormalizer.java` — `normalizeFboPosting`, `normalizeFbsPosting`

#### Промт для чата

```
Исправь потерю строк заказов для multi-line Ozon postings.

## Проблема

UNIQUE constraint на `canonical_order`: `(workspace_id, source_platform, external_order_id)`.

Для Ozon `externalOrderId` = `postingNumber` — **одинаковый** для всех товаров в одном posting. В `OzonSalesFactSource` двойной цикл `for (posting) { for (product) { orders.add(...) } }` генерирует несколько `NormalizedOrderItem` с одинаковым ключом. При upsert побеждает последний INSERT — все предыдущие позиции перезатираются.

Для `canonical_sale` проблемы нет — `externalSaleId = postingNumber + "-" + sku`.

## Решение

В `OzonNormalizer`:
- `normalizeFboPosting(posting, product)` — изменить `externalOrderId` с `posting.postingNumber()` на `posting.postingNumber() + "-" + product.sku()`
- `normalizeFbsPosting(posting, product)` — аналогично

Паттерн уже используется для sales (`normalizeFboSale`, `normalizeFbsSale`).

WB не затронут — у WB `externalOrderId = srid` (per-line).

## Файлы

- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/ozon/OzonNormalizer.java`

## Критерии готовности

- `externalOrderId` для Ozon заказов уникален на уровне позиции (posting + sku)
- Паттерн совпадает с sales normalization
- WB normalization не изменена
- Сборка зелёная
- После деплоя: full resync заказов Ozon для загрузки потерянных позиций
```

---

### F-23: cost_profile SCD2 — нет защиты от перекрывающихся периодов

**Severity:** MEDIUM

#### Проблема

`cost_profile` DDL имеет только `UNIQUE (seller_sku_id, valid_from)` и FK. Нет `EXCLUDE` constraint, нет CHECK, нет trigger — перекрывающиеся `[valid_from, valid_to)` для одного SKU **не запрещены** на уровне БД.

В mart SCD2 join (`fact_product_cost`) при перекрытии — **неоднозначный COGS**: один sale может попасть в два ценовых периода, результат зависит от порядка записей.

#### Продуктовое решение

Добавить PostgreSQL EXCLUDE constraint с `tstzrange`:

```sql
ALTER TABLE cost_profile
    ADD CONSTRAINT excl_cost_profile_no_overlap
    EXCLUDE USING gist (
        seller_sku_id WITH =,
        tstzrange(valid_from, valid_to, '[)') WITH &&
    );
```

Требуется расширение `btree_gist` (если ещё не включено).

Дополнительно: application-level validation в pricing сервисе при создании/обновлении cost_profile.

#### Промт для чата

```
Добавь защиту от перекрывающихся периодов в cost_profile (SCD2).

## Проблема

`cost_profile` DDL имеет только `UNIQUE (seller_sku_id, valid_from)`. Нет constraint против перекрывающихся `[valid_from, valid_to)` для одного SKU. При перекрытии — неоднозначный COGS в mart SCD2 join.

## Решение

Новая PG миграция:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE cost_profile
    ADD CONSTRAINT excl_cost_profile_no_overlap
    EXCLUDE USING gist (
        seller_sku_id WITH =,
        tstzrange(valid_from, valid_to, '[)') WITH &&
    );
```

Дополнительно: проверить, нет ли существующих перекрытий в данных перед добавлением constraint:

```sql
SELECT cp1.id, cp1.seller_sku_id, cp1.valid_from, cp1.valid_to,
       cp2.id, cp2.valid_from, cp2.valid_to
FROM cost_profile cp1
JOIN cost_profile cp2 ON cp1.seller_sku_id = cp2.seller_sku_id
    AND cp1.id < cp2.id
    AND tstzrange(cp1.valid_from, cp1.valid_to, '[)') && tstzrange(cp2.valid_from, cp2.valid_to, '[)');
```

Если перекрытия найдены — исправить данные перед миграцией.

## Файлы

- Новая PG миграция
- Application-level validation в pricing сервисе (если есть endpoint для cost_profile CRUD)

## Критерии готовности

- EXCLUDE constraint добавлен
- Существующие данные не содержат перекрытий
- Попытка вставить перекрывающийся период → ошибка БД
- Сборка зелёная
```

---

### F-27: WB Orders/Sales — без пагинации, только flag=0

**Severity:** MEDIUM

#### Проблема

`WbOrdersReadAdapter` и `WbSalesReadAdapter` делают **один** HTTP-запрос с `flag=0`, без цикла пагинации. В `WbSalesFactSource` — единственный вызов `ordersAdapter.capturePage(ctx, token, dateFrom, 0)`.

WB Statistics API (`/api/v1/supplier/orders`, `/api/v1/supplier/sales`) не имеет пагинации — отдаёт все данные за период в одном ответе. Однако `flag` имеет два значения:
- `flag=0` — все заказы/продажи за период
- `flag=1` — только новые с последнего запроса (incremental)

Текущий код использует только `flag=0` (full dump). Это корректно для первого синка, но при инкрементальной загрузке за большие периоды может быть неоптимально.

#### Продуктовое решение

1. **Верифицировать WB API контракт** — подтвердить, что `flag=0` возвращает все данные без ограничений на количество записей.
2. Если API гарантирует полный dump — текущий подход корректен, задокументировать.
3. Если есть лимит записей — добавить date-window splitting (как для Ozon FBO/FBS).

#### Промт для чата

```
Проверь и задокументируй поведение WB Statistics API для orders/sales.

## Вопрос

`WbOrdersReadAdapter` и `WbSalesReadAdapter` делают один запрос с `flag=0`, без пагинации. Нужно подтвердить:

1. WB Statistics API `/api/v1/supplier/orders` и `/api/v1/supplier/sales` — отдаёт ВСЕ данные за период в одном ответе, или есть лимит записей?
2. Что означает `flag=1` — нужно ли его использовать для incremental sync?
3. Если есть лимит — нужно добавить date-window splitting.

## Контекст

Адаптеры:
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/wb/WbOrdersReadAdapter.java`
- `backend/datapulse-etl/src/main/java/io/datapulse/etl/adapter/wb/WbSalesReadAdapter.java`

Вызов: `WbSalesFactSource.java` — один вызов с flag=0.

Спецификация WB API: `docs/provider-api-specs/` и https://openapi.wildberries.ru

## Если API гарантирует полный dump

Задокументировать в коде (комментарий) и в `docs/provider-api-specs/wb/`.

## Если есть лимит записей

Добавить date-window splitting по аналогии с Ozon FBO (F-09).

## Критерии готовности

- Поведение WB API задокументировано
- Если нужен split — реализован
- Сборка зелёная
```

---

## Findings reclassified after deep analysis

Следующие findings после детального анализа кода были переоценены:

### F-11: RabbitMQ ACK timing → FALSE POSITIVE

**Исходная severity:** HIGH
**Пересмотр:** FALSE POSITIVE — не требует фикса

**Причина пересмотра:** `AcknowledgeMode.AUTO` в Spring AMQP означает ACK **после** успешного return listener-метода, **не до обработки**. Все consumers используют try/catch + poison pill pattern — это **осознанный** дизайн (at-most-once на уровне Rabbit, компенсация через CAS + reconciler + stale job detector). ETL имеет полный механизм reclaim для `IN_PROGRESS` jobs при redelivery.

### F-10: Yandex returns amount=0 → KNOWN LIMITATION

**Исходная severity:** HIGH
**Пересмотр:** MEDIUM — known API limitation, не баг кода

**Причина пересмотра:** Yandex Return DTO (`YandexReturn`, `YandexReturnItem`) **не содержат полей суммы** — API просто не отдаёт эту информацию. WB returns тоже имеют `BigDecimal.ZERO`. Только Ozon заполняет `returnAmount` из `product.price`. Это ограничение внешнего API, не ошибка маппинга.

**Рекомендация:** в будущем обогащать return amount из canonical_order (lookup по SKU) или из финансовых отчётов (где возвратные суммы есть). Документировать как known limitation.

### F-20: WB orders quantity=1 → CORRECT BY DESIGN

**Исходная severity:** MEDIUM
**Пересмотр:** LOW — correct behavior

**Причина пересмотра:** WB Statistics API отдаёт заказы/продажи **по одной единице на строку** (каждый `srid` = одна единица товара). DTO `WbOrderItem` и `WbSaleItem` не содержат поля quantity. Hardcode `1` — корректное отражение контракта API.

### F-21: canonicalName() non-enum strings → BY DESIGN

**Исходная severity:** MEDIUM
**Пересмотр:** LOW — intentional canonical name mapping

**Причина пересмотра:** `canonicalName()` в `FinanceEntryType` — намеренная канонизация для `canonical_finance_entry.entry_type`. Строки `"DELIVERY"`, `"ACCEPTANCE"`, `"PENALTY"` объединяют типы разных маркетплейсов в единый canonical name. `default -> this.name()` для остальных. Это дизайн-решение, не баг.

### F-22: reconciliation_residual formula → BY DESIGN

**Исходная severity:** MEDIUM
**Пересмотр:** LOW — control total as designed

**Причина пересмотра:** `reconciliation_residual = net_payout + allocated_acquiring - sum(all named components)`. Это контрольный остаток сверки — **не обязан быть 0**. При неполной классификации расходов, округлениях или новых типах операций он показывает "неразнесённый остаток". Это feature, не bug.

### F-16: Outbox poller send before commit → ACCEPTED RISK

**Исходная severity:** MEDIUM
**Пересмотр:** LOW — accepted architectural trade-off

**Причина пересмотра:** Rabbit send происходит в рамках DB транзакции, до commit. При crash после send но до commit — транзакция откатывается, outbox строка остаётся `PENDING`, повторная отправка при следующем poll → **дубликат на стороне Rabbit**. Но downstream consumers имеют CAS/idempotency (ETL: CAS на job_execution, pricing: CAS на action status). Это стандартный trade-off outbox pattern без distributed transactions.

### F-18: Timezone inconsistency → DOCUMENTATION ONLY

**Исходная severity:** MEDIUM
**Пересмотр:** LOW — per-provider conventions are correct

**Причина пересмотра:** WB date params как ISO без TZ + UTC midnight для date-only. Ozon timestamps — Moscow UTC+3 (документировано в `OzonTimestampParser`). Yandex — ISO. Каждый адаптер следует конвенциям своего маркетплейса. Документировать, но менять не нужно.

### F-24: outbox_event no idempotency key → LOW RISK

**Исходная severity:** MEDIUM
**Пересмотр:** LOW

**Причина пересмотра:** outbox events создаются транзакционно с бизнес-операцией — duplicate business events маловероятны. `messageId = event.id` используется при публикации. Добавление idempotency_key полезно, но не критично.

### F-25: Ozon page_count → NEEDS VERIFICATION

**Исходная severity:** MEDIUM
**Пересмотр:** LOW — needs API doc verification

Цикл `while (page <= totalPages)` — если `page_count` = "total pages" (1-based), код корректен. Верифицировать по Ozon API docs.

### F-26: marketplace_offer UNIQUE without connection_id → CORRECT

**Исходная severity:** MEDIUM
**Пересмотр:** CORRECT — aligned with architecture

**Причина пересмотра:** UNIQUE `(seller_sku_id, marketplace_type, marketplace_sku)` — правильно для модели "1 workspace = 1 account per marketplace". `connection_id` убран в миграции 0029 осознанно.

---

## Порядок реализации (полный, обновлённый)

> **Статус на 2026-04-12:** 15 из 15 фиксов реализованы. Все findings закрыты.

| Чат | Finding | Severity | Статус | Описание |
|-----|---------|----------|--------|----------|
| 1 | F-14 + F-15 | MEDIUM | ✅ DONE | Мелкие фиксы — rate limit, index |
| 2 | F-19 | MEDIUM | ✅ DONE | Dead code cleanup |
| 3 | F-05 | CRITICAL | ✅ DONE | Ozon multi-line order grain fix |
| 4 | F-01 | CRITICAL | ✅ DONE | SubSourceRunner batch errors |
| 5 | F-02 | CRITICAL | ✅ DONE | WbFinanceReadAdapter typed errors |
| 6 | F-04 | CRITICAL | ✅ DONE | workspace_id в cost/warehouse |
| 7 | F-07 | HIGH | ✅ DONE | Mart joins → (workspace_id, source_platform) |
| 8 | F-09 | HIGH | ✅ DONE | Ozon FBO date-window |
| 9 | F-06 | HIGH | ✅ DONE | Unmapped finance types alert |
| 10 | F-12 | HIGH | ✅ DONE | Cost watermark |
| 11 | F-23 | MEDIUM | ✅ DONE | cost_profile SCD2 overlap protection |
| 12 | F-17 | MEDIUM | ✅ DONE | Bid queue split |
| 13 | F-27 | MEDIUM | ✅ DONE | WB Orders/Sales pagination (80K limit, lastChangeDate cursor) |
| 14 | F-13 | HIGH | ✅ DONE | Tests: TailFieldExtractor, OzonNormalizer grain fix, 267+ existing |
| 15 | F-03 | CRITICAL | ✅ DONE | Yandex finance normalization |

### Findings не требующие фиксов (reassessed)

| Finding | Исходный Severity | Пересмотр | Причина |
|---------|-------------------|-----------|---------|
| F-11 | HIGH | FALSE POSITIVE | AUTO ACK = после return, CAS покрывает |
| F-10 | HIGH | KNOWN LIMITATION | API не отдаёт суммы возвратов |
| F-20 | MEDIUM | CORRECT | WB srid = 1 unit, правильно |
| F-21 | MEDIUM | BY DESIGN | Canonical name mapping, не баг |
| F-22 | MEDIUM | BY DESIGN | Control total, не обязан быть 0 |
| F-16 | MEDIUM | ACCEPTED RISK | Outbox trade-off, mitigated by CAS |
| F-18 | MEDIUM | DOCUMENTATION | Per-provider TZ conventions верны |
| F-24 | MEDIUM | LOW RISK | Transactional creation mitigates |
| F-25 | MEDIUM | NEEDS VERIFICATION | Verify Ozon page_count semantics |
| F-26 | MEDIUM | CORRECT | Aligned with 1-workspace-1-account |
