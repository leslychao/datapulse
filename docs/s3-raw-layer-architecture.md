# S3/MinIO Raw Layer — Implementation Spec

> Этот документ — детальная спецификация реализации raw layer. Архитектурные решения и место raw layer в общем pipeline описаны в [Архитектуре данных](data-architecture.md). Здесь — streaming capture, cursor extraction, memory footprint, per-endpoint analysis, risk catalog.

## 1. Зачем нужен S3/MinIO

### Роль в архитектуре

S3-compatible storage выполняет роль **immutable raw layer** — первого слоя четырёхступенчатого data pipeline:

```
API маркетплейсов → Raw (S3) → Normalized (in-process) → Canonical (PostgreSQL) → Analytics (ClickHouse)
```

### Что конкретно хранится


| Содержимое                  | Формат                         | Пример                                                                       |
| --------------------------- | ------------------------------ | ---------------------------------------------------------------------------- |
| Ответы API маркетплейсов    | Исходный JSON, как есть        | WB: массив из `reportDetailByPeriod`; Ozon: `result.operations[]` из finance |
| Каждая "страница" пагинации | Отдельный файл (blob)          | WB catalog page 1 (100 cards), page 2 (100 cards), ...                       |
| Метаданные запроса          | Часть S3 key или user metadata | account_id, event, source_id, request_id, page_number                        |


### Почему не PostgreSQL JSONB

Альтернативный подход — хранить raw payloads в PostgreSQL JSONB. Это создаёт ряд архитектурных проблем:


| Проблема PostgreSQL JSONB для raw                                                                                                                                                        | S3 решает                                                                                        |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| **Размер:** WB finance report — до 100K+ строк, каждая ~2-3 KB JSON → один отчёт ~200-300 MB. PostgreSQL JSONB не предназначен для blob-хранилища такого масштаба                        | S3 оптимизирован для хранения произвольных объектов без давления на WAL, vacuum, replication lag |
| **Давление на PostgreSQL:** raw payloads увеличивают размер WAL, backup, replication. Авторитетный store получает write amplification от данных, которые после нормализации ему не нужны | S3 изолирует write load; PostgreSQL хранит только index (`job_item` — ключи, статусы, SHA-256)   |
| **Retention:** очистка raw данных (keep_count=3) в PostgreSQL — DELETE + VACUUM. В S3 — lifecycle policy                                                                                 | S3 lifecycle rules: automatic expiration, no vacuum                                              |
| **Стоимость хранения:** PostgreSQL storage дороже (IOPS, replication) для данных, к которым обращаются раз в месяц (forensics)                                                           | S3-compatible — дешёвый object storage                                                           |
| **Streaming:** PostgreSQL JSONB не поддерживает streaming read по частям; весь JSONB грузится в memory при SELECT                                                                        | S3 поддерживает range requests и streaming                                                       |


### Почему S3-compatible, а не файловая система


| Критерий          | Файловая система                          | S3-compatible (MinIO)                           |
| ----------------- | ----------------------------------------- | ----------------------------------------------- |
| Развёртывание     | Требует shared volume при масштабировании | HTTP API, доступен из любого runtime entrypoint |
| Retention policy  | Cron + find + rm                          | S3 lifecycle rules (declarative)                |
| Metadata          | Отдельный index файл или БД               | Native object metadata (user metadata, tags)    |
| Durability        | Зависит от конфигурации FS                | MinIO: erasure coding, replication              |
| API-совместимость | Проприетарный                             | AWS S3 API — индустриальный стандарт            |


### Конкретные use cases

**1. Replay (перезагрузка):** При ошибке в materializer или изменении маппинга можно перезапустить pipeline с raw слоя, без повторного вызова API маркетплейса. Критично при rate limits (WB: 1 req/min на finance).

**2. Forensics (расследование):** Reconciliation residual > threshold → нужно посмотреть, что именно пришло от маркетплейса. Raw payload — единственный source of evidence.

**3. Provider contract evolution:** Ozon меняет API (v2→v3→v4→v5). Старый raw payload позволяет сравнить, какие поля исчезли/появились, без live API-вызова.

**4. Data provenance:** Каждая каноническая запись прослеживаема до raw source через `job_item.s3_key`.

---

## 2. Архитектура записи: API → S3 (Write Path)

### 2.1. Проблема: HTTP response 300 MB → как сохранить, не загрузив в память?

Наивный подход — прочитать HTTP response в `byte[]` или `String`, затем передать в S3 `putObject`:

```
response.bodyToMono(byte[].class).block()  // ← 300 MB в Java heap!
s3.putObject(key, bytes)
```

Для WB finance (100K строк, 200-300 MB) это означает 300 MB в heap на один API-вызов. При параллельной обработке 4 аккаунтов — 1.2 GB только на raw buffers. **Это неприемлемо.**

### 2.2. Решение: streaming через temp file

HTTP response → temp file (disk) → S3 upload из файла → удаление temp file.

```
Детальный flow:

1. WebClient выполняет HTTP-запрос к API маркетплейса
2. Response body приходит как поток DataBuffer chunks (по 8-64 KB каждый)
3. Каждый chunk:
   a. Записывается в temp file на диск (FileChannel / OutputStream)
   b. Подаётся в SHA-256 digest (для дедупликации)
   c. Счётчик byte_size инкрементируется
4. После завершения HTTP response:
   - temp file закрыт, содержит полный response as-is
   - SHA-256 вычислен
   - byte_size известен → Content-Length для S3
5. S3 putObject(key, tempFile, contentLength)
   - S3 SDK читает файл потоково (FileInputStream), не загружая в heap
6. INSERT job_item (s3_key, sha256, byte_size, status=CAPTURED)
7. Удаление temp file
```

**Memory footprint записи:** O(chunk_size) = **8-64 KB** в heap, вне зависимости от размера response. 300 MB WB finance → 64 KB в heap.

**Disk footprint:** O(response_size) = temp file 300 MB. Временный — существует от начала HTTP response до завершения S3 upload (секунды-минуты). Одновременно на диске: один temp file per активный source (не per account).

### 2.3. Почему temp file, а не streaming напрямую в S3?


| Подход                                  | Проблема                                                                                                                                                                                        |
| --------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **HTTP stream → S3 putObject напрямую** | S3 `putObject` требует `Content-Length`. HTTP response с chunked transfer encoding не имеет Content-Length. Нельзя передать InputStream без размера                                             |
| **S3 multipart upload (streaming)**     | Не требует Content-Length. Но: каждая часть ≥ 5 MB, complex API (initiate → upload parts → complete), при ошибке — abort + cleanup incomplete uploads. Для payloads < 5 MB multipart невозможен |
| **Temp file → S3 putObject**            | Размер файла известен. Один простой вызов putObject. Файл можно перечитать при retry. SHA-256 вычислен заранее. Минус — нужен disk space                                                        |


**Выбор: temp file** для всех payloads. Обоснование:

1. **Простота:** один API-вызов `putObject(key, file, length)` вместо multipart ceremony.
2. **Retry safety:** если S3 upload падает — temp file на месте, повторная попытка не требует повторного HTTP-запроса к маркетплейсу.
3. **SHA-256:** хеш вычисляется при записи в temp file, не требует второго прохода.
4. **Content-Length:** известен из размера файла.
5. **Disk space:** temp file живёт секунды; при одновременной обработке 4 sources = max ~1.2 GB temp на диске (worst case: 4 × 300 MB WB finance). Приемлемо.

### 2.4. Альтернатива для больших payloads: S3 multipart upload

Для payloads > 100 MB (WB finance) **допускается** S3 multipart upload как оптимизация:

```
1. WebClient HTTP response → DataBuffer stream
2. initiateMultipartUpload(key)
3. Для каждых 10 MB accumulated:
   a. uploadPart(uploadId, partNumber, buffer)
   b. SHA-256 digest обновляется
   c. buffer сбрасывается
4. completeMultipartUpload(uploadId, parts[])
5. INSERT job_item (s3_key, sha256, byte_size, status=CAPTURED)
```

Memory: O(part_size) = **10 MB** в heap. Нет temp file. Но:

- Нужен abort при ошибке (`abortMultipartUpload`), иначе incomplete uploads занимают S3 space
- S3 lifecycle rule для автоочистки incomplete multipart uploads (рекомендуется: 1 day)
- Нельзя re-read payload при ошибке (нет temp file) → при ошибке на шаге 3-4 весь HTTP-запрос к API повторяется

**Рекомендация:** temp file — основной подход. Multipart — опциональная оптимизация для Phase G при необходимости.

### 2.5. Что насчёт пагинации? Нужно ли знать последнюю запись?

Отдельная проблема: для cursor-based пагинации (WB finance: `rrdid`, WB catalog: `updatedAt + nmID`) нужно прочитать **последний элемент** response, чтобы получить cursor для следующей страницы. Если response — 300 MB, как узнать `rrd_id` последней строки, не загружая всё в memory?

**Решение: двухфазный processing temp file.**

```
Phase 1 — Capture (streaming write + extract cursor):

  1. HTTP response → temp file (streaming, chunk-by-chunk)
  2. Одновременно: lightweight cursor extractor следит за потоком
     - Для WB finance (root JSON array): парсит начало каждого объекта,
       запоминает rrd_id последнего увиденного объекта
     - Для WB catalog: парсит cursor object в конце response
  3. Результат: temp file + cursor для следующей страницы + SHA-256 + byte_size

Phase 2 — Persist:

  4. S3 putObject(key, tempFile)
  5. INSERT job_item
  6. Удаление temp file
```

Cursor extractor — лёгкий streaming JSON parser, который не десериализует весь объект. Для WB finance (root array): при встрече `"rrd_id":` token записывает значение. Memory: O(1) — одно long значение.

Для WB catalog: cursor находится ПОСЛЕ массива `cards` в поле `cursor` на том же уровне. Parser дожидается конца массива, затем десериализует маленький cursor-объект (~100 bytes).

### 2.6. Общая схема (итоговая)

```
MarketplaceAdapter.fetch(page_params)
  → HTTP response stream (chunked)
  → temp file write (streaming, 64 KB chunks) + SHA-256 digest + cursor extraction
  → S3 putObject(bucket, key, tempFile)    ← upload с известным Content-Length
  → job_item INSERT (PostgreSQL)           ← index: s3_key, sha256, byte_size, status=CAPTURED
  → delete temp file
  → return cursor для следующей страницы
```

**Memory footprint всего write path: 64 KB (chunk buffer) + O(1) cursor state.**
**Disk footprint: один temp file = размер одной страницы API (max ~300 MB для WB finance).**

### 2.7. Структура S3 ключей

```
s3://{bucket}/raw/{account_id}/{event}/{source_id}/{request_id}/page-{page_number}.json
```

Пример:

```
s3://datapulse-raw/raw/42/FACT_FINANCE/WbFinanceSource/550e8400-e29b-41d4-a716-446655440000/page-0001.json
s3://datapulse-raw/raw/42/FACT_FINANCE/WbFinanceSource/550e8400-e29b-41d4-a716-446655440000/page-0002.json
s3://datapulse-raw/raw/42/PRODUCT_DICT/OzonCatalogSource/550e8400-e29b-41d4-a716-446655440000/page-0001.json
```

Компоненты ключа:


| Компонент       | Назначение                                             | Пример                                 |
| --------------- | ------------------------------------------------------ | -------------------------------------- |
| `raw/`          | Prefix для raw layer (отделяет от других use cases S3) | —                                      |
| `{account_id}`  | Tenant isolation + partition для retention             | `42`                                   |
| `{event}`       | Тип ETL event                                          | `FACT_FINANCE`, `PRODUCT_DICT`         |
| `{source_id}`   | Конкретный source (класс адаптера)                     | `WbFinanceSource`, `OzonCatalogSource` |
| `{request_id}`  | UUID конкретного ETL run                               | `550e8400-...`                         |
| `page-{N}.json` | Номер страницы пагинации                               | `page-0001.json`                       |


### 2.8. Пагинация и запись по провайдерам

#### WB Catalog (`/content/v2/get/cards/list`)

```
Pagination: cursor-based (updatedAt + nmID)
Page size: max 100 items
Page size: ~50-200 KB ← помещается в память, но используем temp file для единообразия
Termination: cursor.total < limit

Write flow:
  page = 1
  cursor = {updatedAt: "", nmID: 0}
  loop:
    response = POST /content/v2/get/cards/list {cursor, limit: 100}
    (tempFile, sha256, size, parsedCursor) = streamToTempFile(response.bodyStream)
    s3.putObject("raw/{account}/PRODUCT_DICT/WbCatalogSource/{reqId}/page-{page}.json", tempFile)
    job_item.insert(request_id, source_id, page, s3_key, sha256, size, status=CAPTURED)
    deleteTempFile()
    if parsedCursor.total < 100: break
    cursor = parsedCursor
    page++
```

Ожидаемый объём: средний селлер 500-5000 SKU → 5-50 страниц → 5-50 S3 objects, каждый ~50-200 KB.
Memory при записи: **64 KB** (chunk buffer). Temp file: 50-200 KB (мгновенно удаляется).

#### WB Finance (`/api/v5/supplier/reportDetailByPeriod`)

```
Pagination: cursor-based (rrdid), limit до 100000
Termination: HTTP 204 No Content
Rate limit: 1 req/min
⚠️ WORST CASE: одна страница = 200-300 MB JSON

Write flow:
  page = 1
  rrdid = 0
  loop:
    response = GET ...?dateFrom=...&dateTo=...&rrdid={rrdid}&limit=100000
    if response.status == 204: break
    (tempFile, sha256, size, lastRrdId) = streamToTempFile(response.bodyStream)
      // cursor extractor: при записи следит за "rrd_id" tokens, запоминает последний
      // memory: 64 KB chunk + 8 bytes (long lastRrdId)
    s3.putObject("raw/{account}/FACT_FINANCE/WbFinanceSource/{reqId}/page-{page}.json", tempFile)
    job_item.insert(...)
    deleteTempFile()
    rrdid = lastRrdId
    page++
    sleep(60s)  // rate limit 1 req/min
```

Ожидаемый объём: крупный селлер — 100K+ строк → 1-2 страницы → 1-2 S3 objects по 200-300 MB.
Memory при записи: **64 KB**. Temp file: до **300 MB** (живёт ~10-30 секунд, пока идёт S3 upload).

#### Ozon Finance (`/v3/finance/transaction/list`)

```
Pagination: page-based (page + page_size)
Max period: 1 month
Page size: ~50-100 KB (100 операций)
Rate limit: не документирован

Write flow:
  page = 1
  loop:
    response = POST /v3/finance/transaction/list {filter: {date: {from, to}}, page, page_size: 100}
    (tempFile, sha256, size, pageCount) = streamToTempFile(response.bodyStream)
      // cursor extractor: парсит result.page_count из response
    s3.putObject("raw/{account}/FACT_FINANCE/OzonFinanceSource/{reqId}/page-{page}.json", tempFile)
    job_item.insert(...)
    deleteTempFile()
    if page >= pageCount: break
    page++
```

Ожидаемый объём: средний селлер 5K-20K операций/мес → 50-200 страниц → 50-200 S3 objects по 50-100 KB каждый.
Memory при записи: **64 KB**. Temp file: 50-100 KB.

#### Ozon Catalog (`/v3/product/list` + `/v3/product/info/list`)

```
Two-phase:
  Phase 1: GET product IDs via /v3/product/list (cursor-based, last_id)
  Phase 2: GET product details via /v3/product/info/list (batch by product_id, up to 100 per request)
  Page size: ~100-500 KB

Write flow (Phase 2 — основные данные):
  batches = chunk(product_ids, 100)
  for batch in batches:
    response = POST /v3/product/info/list {product_id: batch}
    (tempFile, sha256, size) = streamToTempFile(response.bodyStream)
    s3.putObject("raw/{account}/PRODUCT_DICT/OzonCatalogInfoSource/{reqId}/page-{batchIdx}.json", tempFile)
    job_item.insert(...)
    deleteTempFile()
```

Memory при записи: **64 KB**. Temp file: 100-500 KB.

#### Сводка memory footprint при записи


| Provider / Domain         | Размер page  | Heap memory | Temp file на диске | Temp file lifetime |
| ------------------------- | ------------ | ----------- | ------------------ | ------------------ |
| WB Finance                | 200-300 MB   | **64 KB**   | 200-300 MB         | ~10-30 сек         |
| WB Catalog                | 50-200 KB    | **64 KB**   | 50-200 KB          | < 1 сек            |
| WB Orders/Sales           | 10-500 KB    | **64 KB**   | 10-500 KB          | < 1 сек            |
| Ozon Finance              | 50-100 KB    | **64 KB**   | 50-100 KB          | < 1 сек            |
| Ozon Catalog              | 100-500 KB   | **64 KB**   | 100-500 KB         | < 1 сек            |
| **Concurrent worst case** | 4 WB Finance | **256 KB**  | **~1.2 GB**        | ~30 сек            |


### 2.9. Индексная таблица `job_item` (PostgreSQL)

`job_item` — лёгкая index-таблица, связывающая raw payload в S3 с execution context:


| Поле               | Тип          | Назначение                            |
| ------------------ | ------------ | ------------------------------------- |
| `id`               | BIGSERIAL    | PK                                    |
| `job_execution_id` | BIGINT FK    | Привязка к ETL run                    |
| `request_id`       | VARCHAR(64)  | UUID ETL run                          |
| `source_id`        | VARCHAR(128) | Класс source (e.g. `WbFinanceSource`) |
| `page_number`      | INT          | Номер страницы пагинации              |
| `s3_key`           | VARCHAR(512) | Полный S3 key для payload             |
| `record_count`     | INT          | Количество записей на странице        |
| `content_sha256`   | VARCHAR(64)  | SHA-256 от payload для дедупликации   |
| `byte_size`        | BIGINT       | Размер payload в байтах               |
| `status`           | VARCHAR(32)  | `CAPTURED` → `PROCESSED` → `ARCHIVED` |
| `captured_at`      | TIMESTAMPTZ  | Время захвата                         |


Дедупликация: `ON CONFLICT (request_id, source_id, content_sha256) DO NOTHING` — повторная загрузка той же страницы безопасна.

### 2.10. Транзакционная семантика записи

```
1. MarketplaceAdapter получает HTTP response
2. S3Client.putObject() — запись raw payload
3. BEGIN TRANSACTION
   3a. INSERT job_item (s3_key, sha256, status=CAPTURED)
   3b. UPDATE etl_source_execution_state (progress)
   COMMIT
4. Yield данные для нормализации
```

Если шаг 2 (S3 put) прошёл, а шаг 3 (PostgreSQL) упал — при retry worker обнаружит orphan в S3 (нет job_item). Варианты:

- **Overwrite:** перезаписать тот же S3 key (идемпотентно — тот же payload).
- **Cleanup:** background job удаляет S3 objects без соответствующего job_item (orphan GC).

Если шаг 2 упал — ничего не записано, retry целиком.

---

## 3. Архитектура чтения: S3 → Java Memory (Read Path)

### 3.1. Когда и зачем читаем из S3


| Сценарий                             | Когда                                    | Объём данных                      |
| ------------------------------------ | ---------------------------------------- | --------------------------------- |
| **Primary ingestion** (нормализация) | Сразу после записи, в рамках ETL run     | Страница целиком (50 KB — 300 MB) |
| **Replay** (перезагрузка)            | Ручной запуск после исправления маппинга | Все страницы одного ETL run       |
| **Forensic investigation**           | По запросу оператора / при аномалии      | Конкретная страница или запись    |
| **Provider contract debug**          | При подозрении на изменение API          | Конкретная страница               |


### 3.2. Primary ingestion — основной read path

При первичном ingestion raw data можно обработать **двумя стратегиями**:

#### Стратегия A: Write-through (рекомендуемая)

```
API response → TeeInputStream → [S3 write] + [streaming JSON parse]
```

Данные обрабатываются **одновременно** с записью в S3. HTTP response body дублируется (tee):

- один поток → S3 putObject (persisted raw)
- второй поток → Jackson streaming parser → normalized DTO → canonical

**Плюсы:**

- Один HTTP-запрос, одно чтение из сети
- Нет повторного чтения из S3 для нормализации
- Минимальная latency

**Минусы:**

- Усложнение error handling: если S3 write сломался, а parsing уже начался
- Нужен буферизированный tee (byte[] или temp file) при нестабильном S3

#### Стратегия B: Write-then-read

```
API response → S3 write → S3 read → streaming JSON parse
```

Сначала пишем в S3, потом читаем оттуда же для нормализации.

**Плюсы:**

- Простая семантика: raw payload гарантированно сохранён до начала обработки
- При ошибке обработки — retry читает из S3, без повторного вызова API
- DB-first principle: данные durably сохранены до начала бизнес-обработки

**Минусы:**

- Двойная передача данных (сеть → S3 → сеть → Java)
- Latency: дополнительный round-trip к MinIO

#### Рекомендация

**Стратегия B (write-then-read)** для primary ingestion. Обоснование:

1. Соответствует принципу **DB-first** (данные сохранены до обработки).
2. При crash между записью и обработкой — при рестарте worker видит `job_item.status = CAPTURED` и повторяет обработку из S3 без вызова API.
3. MinIO в том же Docker Compose — latency round-trip ~1-5ms, приемлемо.
4. Для WB finance (300 MB payload) критичен именно этот паттерн: вызов API занимает минуту (rate limit), а S3 read — секунды.

### 3.3. Streaming JSON parse из S3

Критический момент: **нельзя загружать весь JSON в memory как `String` или `JsonNode`**. WB finance report 100K+ строк = 200-300 MB JSON. При 4 одновременных accounts = 1.2 GB только на raw JSON, не считая десериализованных объектов.

#### Streaming parser flow

```java
S3Client.getObject(bucket, key)
  → InputStream
  → JsonParser (Jackson streaming API)
  → iterate tokens: START_ARRAY → START_OBJECT → fields → END_OBJECT → ...
  → для каждого объекта: deserialize → NormalizedDTO → batch buffer
  → при заполнении batch (500 records) → flush batch:
      → canonical UPSERT (PostgreSQL)
      → ClickHouse insert (facts)
      → clear batch buffer
```

#### Определение JSON array path (JsonArrayLocator)

Разные API возвращают массив данных в разных местах JSON:


| Provider/Endpoint   | JSON path к массиву данных | Другие поля response                    |
| ------------------- | -------------------------- | --------------------------------------- |
| WB Catalog          | `$.cards`                  | `cursor` (pagination)                   |
| WB Prices           | `$.data.listGoods`         | `error`, `errorText`                    |
| WB Orders/Sales     | `$` (root array)           | —                                       |
| WB Finance          | `$` (root array)           | —                                       |
| WB Incomes          | `$` (root array)           | —                                       |
| WB Offices          | `$` (root array)           | —                                       |
| WB Returns          | `$.report`                 | —                                       |
| Ozon Catalog (list) | `$.result.items`           | `result.total`, `result.last_id`        |
| Ozon Catalog (info) | `$.result.items`           | —                                       |
| Ozon Prices         | `$.result.items`           | `result.total`, `cursor`                |
| Ozon Stocks         | `$.result.items`           | `result.total`, `cursor`                |
| Ozon Orders (FBO)   | `$.result`                 | `has_next`                              |
| Ozon Finance        | `$.result.operations`      | `result.page_count`, `result.row_count` |
| Ozon Returns        | `$.returns`                | —                                       |


Реестр JSON layout маппит `(source_id) → json_path_to_array`. Streaming parser навигирует к этому path, затем итерирует элементы массива.

#### Пример: streaming parse WB finance из S3

```
1. s3.getObject("raw/42/FACT_FINANCE/WbFinanceSource/req-123/page-0001.json")
   → InputStream (300 MB, streaming, NOT buffered in memory)

2. JsonParser parser = objectMapper.getFactory().createParser(inputStream)
   parser — потоковый, читает по токенам

3. parser.nextToken()  // START_ARRAY (root — массив объектов)
   while (parser.nextToken() == START_OBJECT):
     WbFinanceRow row = objectMapper.readValue(parser, WbFinanceRow.class)
     // ~2-3 KB на объект, мгновенно GC после batch flush
     batch.add(row)
     if (batch.size() >= 500):
       processBatch(batch)  // normalize → canonical UPSERT
       batch.clear()
   processBatch(batch)  // flush remaining
```

Memory footprint при streaming: ~500 × 3 KB = **1.5 MB** в пике (один batch). Не 300 MB.

### 3.4. Оценка memory footprint по провайдерам


| Provider/Domain | Объём page | Records/page | Record size | Peak memory (batch=500) |
| --------------- | ---------- | ------------ | ----------- | ----------------------- |
| WB Finance      | 200-300 MB | до 100K      | ~2-3 KB     | **1.5 MB**              |
| WB Catalog      | 50-200 KB  | 100          | ~1-2 KB     | **0.5-1 MB**            |
| WB Orders/Sales | 10-500 KB  | 100-5000     | ~0.5-1 KB   | **0.25-0.5 MB**         |
| Ozon Finance    | 50-100 KB  | 100          | ~1-3 KB     | **0.5-1.5 MB**          |
| Ozon Catalog    | 100-500 KB | 100          | ~2-5 KB     | **1-2.5 MB**            |
| Ozon Stocks     | 50-200 KB  | 100-1000     | ~0.3-0.5 KB | **0.15-0.25 MB**        |


Вывод: при streaming parse + batch processing memory footprint от raw data — **единицы мегабайт** вне зависимости от размера payload. Узкое место — не чтение, а скорость PostgreSQL batch UPSERT.

### 3.5. Read path для replay

```
1. Оператор / система запускает replay для request_id = "req-123"
2. SELECT s3_key, page_number FROM job_item
   WHERE request_id = 'req-123' AND source_id = 'WbFinanceSource'
   ORDER BY page_number
3. Для каждого job_item:
   a. s3.getObject(s3_key) → InputStream
   b. Streaming parse → normalize → canonical UPSERT (overwrite)
4. Materialization (canonical → ClickHouse facts → marts)
```

Replay идентичен primary ingestion, но:

- Не вызывает API маркетплейса
- Читает из S3 по сохранённым ключам
- Может использовать обновлённый маппинг (новый нормализатор)

### 3.6. Read path для forensic investigation

```
1. UI: оператор кликает "показать raw data" для posting_id = "87621408-0010-1"
2. API: GET /api/raw-evidence/{posting_id}
3. Service:
   a. Найти job_item по posting_id (через join canonical → job_item или search по s3_key pattern)
   b. s3.getObject(s3_key) → InputStream
   c. Streaming parse → find matching record → return JSON fragment
4. Альтернатива: для мелких forensic запросов — хранить record-level index в job_item
   (record_key → offset в S3 object для random access)
```

---

## 4. S3 key design — rationale

### Partition strategy

Ключ `raw/{account_id}/{event}/{source_id}/{request_id}/page-{N}.json` обеспечивает:


| Операция                               | S3 API                                                             | Performance           |
| -------------------------------------- | ------------------------------------------------------------------ | --------------------- |
| Все страницы одного ETL run            | `ListObjects(prefix=raw/42/FACT_FINANCE/WbFinanceSource/req-123/)` | O(pages)              |
| Все ETL runs одного event для account  | `ListObjects(prefix=raw/42/FACT_FINANCE/)`                         | O(runs × pages)       |
| Retention cleanup: удалить старые runs | `ListObjects(prefix=raw/42/)` → sort by date → delete              | Efficient prefix scan |
| Forensic: найти конкретный payload     | Через job_item.s3_key → direct GET                                 | O(1)                  |


### Bucket strategy

Один bucket `datapulse-raw` с prefix-based isolation:

- `raw/` — raw API payloads
- `evidence/` — pricing decision evidence (Phase C)
- `exports/` — user-requested exports (Phase E)

Multi-tenant isolation: через account_id в key prefix + IAM policies (при необходимости).

---

## 5. Retention и lifecycle

### Retention policy


| Тип данных                                  | Retention                                           | Обоснование                                     |
| ------------------------------------------- | --------------------------------------------------- | ----------------------------------------------- |
| Raw finance (FACT_FINANCE)                  | 12 месяцев                                          | Audit requirement; reconciliation investigation |
| Raw catalog/prices/stocks (state snapshots) | keep_count=3 последних per (account, event, source) | Достаточно для replay; state перезаписывается   |
| Raw orders/sales (flow)                     | 6 месяцев                                           | Forensics; replay при изменении маппинга        |


### Реализация retention

```
RetentionPolicy:
  1. List all request_ids for (account, event, source), sorted by captured_at DESC
  2. Skip first keep_count
  3. For remaining request_ids:
     a. Delete all S3 objects with prefix raw/{account}/{event}/{source}/{request_id}/
     b. UPDATE job_item SET status = 'PURGED' WHERE request_id = ...
```

S3 lifecycle rules как fallback: `Expiration: 365 days` на prefix `raw/` — гарантия, что даже при ошибке в application-level cleanup данные не растут бесконечно.

---

## 6. Почему не row-per-record в PostgreSQL

### Антипаттерн: row-per-record raw storage

Подход, при котором каждая запись из API (один товар, одна финансовая операция) сохраняется как отдельная строка в PostgreSQL с JSONB payload, создаёт следующие проблемы:

1. **Грануляция:** WB finance 100K строк = 100K INSERT. Row-per-record превращает PostgreSQL в blob storage.
2. **PostgreSQL load:** 100K INSERT × 2-3 KB JSONB = 200-300 MB в WAL за один ETL run одного аккаунта. При 10 аккаунтах × ежедневная синхронизация = 2-3 GB/день только в raw таблицы.
3. **JSONB overhead:** PostgreSQL парсит и валидирует JSONB при INSERT. Для raw layer это unnecessary overhead — цель raw слоя — хранить as-is.
4. **Vacuum pressure:** raw таблицы с retention cleanup (DELETE старых записей) создают dead tuples → VACUUM pressure.
5. **Replication lag:** при PostgreSQL streaming replication raw payload увеличивает replication lag.

### Целевой подход: page-per-file в S3

```
Одна страница API = один S3 object (as-is JSON blob)
job_item в PostgreSQL = лёгкий index (без payload)
```

- Payload в S3: no WAL, no vacuum, no replication impact
- Index в PostgreSQL: ~200 bytes per page (not per record)
- WB finance 100K строк = 1-2 S3 objects + 1-2 job_item rows

---

## 7. Конфигурация MinIO

### Docker Compose

```yaml
minio:
  image: minio/minio:latest
  command: server /data --console-address ":9001"
  ports:
    - "9000:9000"   # S3 API
    - "9001:9001"   # Console UI
  environment:
    MINIO_ROOT_USER: datapulse
    MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
  volumes:
    - minio-data:/data
  healthcheck:
    test: ["CMD", "mc", "ready", "local"]
    interval: 10s
    timeout: 5s
    retries: 5
```

### Spring Boot конфигурация

```yaml
datapulse:
  s3:
    endpoint: http://minio:9000
    access-key: ${S3_ACCESS_KEY}
    secret-key: ${S3_SECRET_KEY}
    bucket: datapulse-raw
    region: us-east-1  # MinIO default
  raw:
    retention:
      finance-keep-months: 12
      state-keep-count: 3
      flow-keep-months: 6
```

### Java client

AWS S3 SDK v2 (совместим с MinIO). Модуль `datapulse-adapter-s3` содержит:


| Компонент               | Ответственность                                            |
| ----------------------- | ---------------------------------------------------------- |
| S3 properties           | `@ConfigurationProperties` с endpoint, credentials, bucket |
| S3 client configuration | `@Configuration` → S3Client bean                           |
| Raw payload repository  | `@Repository` — put/get/delete/list operations             |
| Retention service       | Lifecycle cleanup по retention policy                      |


---

## 8. Сводка архитектурных решений


| Решение                    | Выбор                                                       | Обоснование                                                       |
| -------------------------- | ----------------------------------------------------------- | ----------------------------------------------------------------- |
| Raw storage                | S3-compatible (MinIO)                                       | Isolation от PostgreSQL; cheap blob storage; streaming; lifecycle |
| S3 key structure           | `raw/{account}/{event}/{source}/{request_id}/page-{N}.json` | Efficient prefix operations; partition по account и event         |
| Granularity                | Одна страница API = один S3 object                          | Минимизация S3 operations; natural boundary пагинации             |
| Index                      | `job_item` в PostgreSQL (без payload)                       | Связь raw → execution context; дедупликация                       |
| Primary ingestion strategy | Write-then-read                                             | DB-first principle; crash recovery без повторного API call        |
| JSON parsing               | Jackson streaming API (JsonParser)                          | Memory: O(batch_size), не O(payload_size)                         |
| Batch size                 | 500 records                                                 | Balance между memory и transaction overhead                       |
| Retention                  | Configurable per data domain                                | Finance: 12 мес (audit); state: keep_count=3; flow: 6 мес         |
| Deduplication              | SHA-256 от page content → ON CONFLICT DO NOTHING            | Idempotent re-upload                                              |
| Bucket strategy            | Один bucket, prefix isolation                               | Simplicity для Docker Compose deployment                          |


---

## 9. Риски и митигации

### R-S3-01: MinIO недоступен


| Параметр  | Значение                                                                                 |
| --------- | ---------------------------------------------------------------------------------------- |
| Влияние   | ETL не может сохранить raw data → pipeline blocked                                       |
| Митигация | Health check MinIO в readiness probe; fallback: buffer to local temp file → retry upload |
| Detection | S3 error rate metric; health endpoint                                                    |


### R-S3-02: Объём хранилища


| Параметр  | Значение                                                                                                       |
| --------- | -------------------------------------------------------------------------------------------------------------- |
| Влияние   | Диск MinIO заполняется                                                                                         |
| Митигация | Retention policies; S3 lifecycle rules; мониторинг disk usage                                                  |
| Расчёт    | 10 accounts × daily sync × 300 MB finance = 3 GB/day raw. С retention 12 мес = ~1 TB. MinIO disk: 2 TB minimum |


### R-S3-03: Streaming parse failure mid-page


| Параметр  | Значение                                                                                                                     |
| --------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Влияние   | Часть записей со страницы обработана, часть — нет                                                                            |
| Митигация | Транзакционная обработка: весь batch в одной транзакции. При ошибке — rollback + retry от начала страницы. Idempotent UPSERT |


### R-CAP-01: DataBuffer memory leak (WebClient)


| Параметр  | Значение                                                                                                                                                            |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Влияние   | WebClient DataBuffer (Netty ByteBuf) — reference-counted. Failure to release после записи в temp file → DirectByteBuffer exhaustion → OOM из native memory, не heap |
| Митигация | `try-finally` с `DataBufferUtils.release()` в каждом chunk handler. Мониторинг native memory usage                                                                 |
| Severity  | HIGH — проявляется под нагрузкой, не на happy path                                                                                                                  |


### R-CAP-02: Temp file leak при JVM crash


| Параметр  | Значение                                                                                                                                                   |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Влияние   | JVM crash между созданием и удалением temp file → orphan файлы. Накопление → disk space exhaustion                                                         |
| Митигация | `Files.createTempFile` с prefix `datapulse-raw-`. Startup cleanup: при старте приложения удалять файлы с этим prefix старше 1 часа. OS-level tmpdir cleanup |
| Severity  | MEDIUM — накапливается постепенно                                                                                                                          |


### R-CAP-03: HTTP response truncation


| Параметр  | Значение                                                                                                                                                                                            |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Влияние   | Обрыв соединения mid-transfer → partial response в temp file. SHA-256 computed на неполных данных. Если загружен в S3 — невалидный JSON, не парсится при read path                                  |
| Митигация | WebClient signal completion (onComplete vs onError). При onError — удалить temp file, не загружать в S3. При onComplete — optional: проверить валидность JSON (последний байт = `]` или `}`)         |
| Severity  | HIGH — WB Finance (300 MB) наиболее уязвим (длительная передача через сеть)                                                                                                                        |


### R-CAP-04: Content-Encoding mismatch


| Параметр  | Значение                                                                                                                                                                                    |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Влияние   | HTTP response может быть gzip-compressed (Content-Encoding: gzip). WebClient авто-декомпрессирует, но SHA-256 должен считаться от декомпрессированного контента (то, что хранится в S3 и temp file) |
| Митигация | SHA-256 считается от байт, записанных в temp file (= декомпрессированные). Убедиться, что WebClient декомпрессирует ДО передачи DataBuffer chunks                                           |
| Severity  | LOW — WebClient по умолчанию декомпрессирует                                                                                                                                                |


### R-CAP-05: Cursor extraction failure


| Параметр  | Значение                                                                                                                                                                                                     |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Влияние   | Malformed JSON → cursor extractor не может извлечь cursor → pagination loop прерывается. Страницы ДО текущей уже сохранены в S3 и обработаны; текущая страница сохранена в S3 (payload as-is), но не продолжена |
| Митигация | Mark `job_item.status = CAPTURED` + flag extraction failure. Alert оператору. НЕ ретраить вслепую — тот же ответ = тот же сбой. Temp file в S3 позволяет forensic-анализ                                     |
| Severity  | MEDIUM — pagination stops, но данные до текущей страницы сохранены                                                                                                                                           |


### R-CAP-06: WB Finance empty string body


| Параметр  | Значение                                                                                                                                                              |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Влияние   | WB Finance возвращает пустую строку (body = "") для аккаунтов без данных. Не HTTP 204 (отличие). SHA-256 пустой строки — валидный хеш, но создаёт degenerate job_item |
| Митигация | Detect empty / very small response (byte_size == 0 или < 10 bytes) ДО создания temp file. Treat как "no data" signal — пропустить S3 upload, завершить pagination    |
| Severity  | LOW — edge case для новых аккаунтов                                                                                                                                   |


### R-CAP-07: Concurrent temp file disk pressure


| Параметр  | Значение                                                                                                                                                                           |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Влияние   | Несколько ETL job одновременно создают temp files. Worst case: 4 × WB Finance 300 MB = 1.2 GB. Если диск app-сервера near capacity → IO errors mid-write                         |
| Митигация | Pre-check available disk space before capture (require 2× expected response size). Limit concurrent WB Finance captures (семафор на source type). Мониторинг disk free space alert |
| Расчёт    | 4 concurrent WB Finance + Ozon pages = ~1.5 GB temp. App disk: minimum 5 GB free recommended                                                                                      |
| Severity  | MEDIUM — только при максимальной параллельности                                                                                                                                    |


### R-CAP-08: S3 upload timeout for large files


| Параметр  | Значение                                                                                                                                                                       |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Влияние   | WB Finance temp file 300 MB → S3 upload. MinIO slow / network congestion → upload minutes. Temp file на диске. Next pagination step ждёт                                      |
| Митигация | Upload timeout (5 min). Retry с exponential backoff. Temp file остаётся → retry не требует повторного HTTP-запроса к API. Multipart upload как Phase G optimization для >100 MB |
| Severity  | LOW — MinIO в том же Docker Compose, latency минимальна                                                                                                                        |


### R-CAP-09: Pagination cursor invalidation between pages


| Параметр  | Значение                                                                                                                                                                                                                      |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Влияние   | Между страницами (WB Finance: 60s sleep для rate limit) данные могут измениться. Вопрос: пропущены/дублированы ли записи?                                                                                                     |
| Митигация | WB Finance: cursor `rrdid` монотонный → safe (новые данные = выше rrdid). WB Catalog: cursor (updatedAt, nmID) монотонный → safe. Ozon page-based: `page_count` может измениться → edge-case duplicates. Dedup по SHA-256    |
| Severity  | LOW — cursor-based пагинации inherently safe; page-based — acceptable duplicate risk                                                                                                                                          |


### R-CAP-10: API возвращает HTML error page вместо JSON


| Параметр  | Значение                                                                                                                                                                             |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Влияние   | API gateway (Cloudflare, nginx) возвращает 502/503 с HTML body. Capture сохраняет HTML как raw payload. SHA-256 не совпадёт с реальным JSON при retry → нет dedup-коллизии (корректно) |
| Митигация | Проверка Content-Type header ДО начала capture. Если не `application/json` → не создавать temp file, бросить retriable error                                                         |
| Severity  | LOW — API gateway errors обычно возвращают non-200 HTTP status                                                                                                                        |


### R-CAP-11: Rate limit hit во время pagination


| Параметр  | Значение                                                                                                                                                                      |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Влияние   | Mid-pagination: API возвращает 429. Текущая страница уже saved (предыдущие HTTP-запросы успешны). Следующая страница заблокирована                                             |
| Митигация | Rate limiter per source type (превентивный). При 429: exponential backoff, resume с последнего cursor. Уже сохранённые страницы не повторяются                                |
| Severity  | LOW — rate limiter предотвращает; retry resumable                                                                                                                              |


---

## 10. Глубокий анализ: streaming capture с cursor extraction

### 10.1. Формулировка задачи

Capture pipeline должен решить **пять задач параллельно**:

1. Сохранить HTTP response потоково в temp file (не грузя в heap)
2. Посчитать SHA-256 от raw bytes
3. Посчитать byte_size
4. При необходимости — извлечь cursor для следующей страницы
5. Сохранить raw payload as-is в S3

**Ключевое ограничение**: не все endpoints нуждаются в cursor extraction. Вопрос — где проходит граница?

### 10.2. Таксономия pagination shapes

Анализ **всех** endpoints (WB: 9, Ozon: 8) выявляет **три семейства** pagination, которые принципиально различаются по потребности в cursor extraction:

#### Семейство 1: Externally-paged — cursor extraction НЕ нужен

Состояние пагинации управляется **целиком клиентом**. Response body не содержит continuation token. Следующая страница вычисляется из request params.


| Endpoint                | Механизм         | Следующая страница             | Termination                                |
| ----------------------- | ---------------- | ------------------------------ | ------------------------------------------ |
| WB Orders               | dateFrom + flag  | dateFrom из request params     | Все данные в одном response                |
| WB Sales                | dateFrom + flag  | Аналогично orders              | Все данные в одном response                |
| WB Returns              | dateFrom + dateTo | Один запрос (≤31 день)        | Один response                              |
| WB Incomes              | dateFrom         | Один запрос                    | Один response                              |
| WB Offices / Warehouses | нет              | Один запрос, все данные        | Один response                              |
| WB Prices               | offset           | offset += limit                | Пустой `data.listGoods[]` или items < limit |
| WB Stocks               | offset           | offset += limit                | Пустой response или items < 250K          |
| Ozon Orders FBO/FBS     | offset + limit   | offset += limit                | Пустой `result[]`                          |
| Ozon Returns            | offset + limit   | offset += limit                | Пустой `returns[]`                         |
| Ozon Catalog info       | batch по IDs     | Внешний список product_id      | Все батчи обработаны                       |


**~11 endpoints** — capture pipeline записывает raw bytes в temp file без какого-либо JSON parsing.

**Termination для offset-based**: слепой подход — всегда запрашивать следующую страницу, останавливаться на пустом response. Один лишний HTTP-запрос per pagination sequence. Приемлемо:
- Ozon endpoints: rate limits не документированы, один extra request negligible
- WB Prices: rate limit неизвестен, один extra request допустим
- WB Stocks: 1 req/20s, один extra request = 20 секунд. Допустимо (sync выполняется background)

#### Семейство 2: Metadata cursor — extract маленького поля из response

Cursor — небольшой JSON-объект/поле по фиксированному пути в response. Cursor — **sibling** массива данных, а не часть данных.


| Endpoint         | JSON path к cursor                                     | Cursor size | Page size    | Termination              |
| ---------------- | ------------------------------------------------------ | ----------- | ------------ | ------------------------ |
| WB Catalog       | `$.cursor` `{updatedAt, nmID, total}`                  | ~100 bytes  | 50–200 KB    | `cursor.total < limit`   |
| Ozon Catalog list | `$.result.last_id`, `$.result.total`                  | ~50 bytes   | 50–200 KB    | `total == 0`             |
| Ozon Stocks      | `$.cursor`, `$.total`                                  | ~80 bytes   | 50–200 KB    | пустой cursor            |
| Ozon Prices      | cursor fields в response                               | ~80 bytes   | 50–200 KB    | пустой cursor            |
| Ozon Finance     | `$.result.page_count`, `$.result.row_count`            | ~30 bytes   | 50–100 KB    | `page >= page_count`     |


**~5 endpoints** — extract ~50–100 bytes из response размером 50–500 KB.

**Ключевое свойство**: cursor-данные расположены на том же уровне, что и data array (sibling). Cursor может быть ДО или ПОСЛЕ массива данных в JSON (зависит от serialization order API). Все pages малого размера (< 500 KB) → полный JSON parse temp file для extraction = sub-millisecond, данные уже в OS page cache.

#### Семейство 3: Data-derived cursor — cursor из последнего элемента данных

**Один endpoint**: WB Finance (`reportDetailByPeriod`).


| Endpoint   | Cursor field | Расположение                     | Page size   | Termination |
| ---------- | ------------ | -------------------------------- | ----------- | ----------- |
| WB Finance | `rrd_id`     | Последний объект в root array `$[*]` | 200–300 MB | HTTP 204    |


**Уникальная проблема**: response — **root JSON array** (без wrapper-объекта). Cursor = значение поля `rrd_id` из **последнего** элемента массива. Массив содержит до 100K объектов. Нельзя узнать `rrd_id` последнего без прохождения через весь массив (или чтения хвоста файла).

**Termination**: HTTP 204 No Content. Не требует парсинга body — определяется по HTTP status code.

### 10.3. Сводная карта: endpoint → extraction strategy


| Endpoint                | Family | CursorExtractor          | Объяснение                                           |
| ----------------------- | ------ | ------------------------ | ---------------------------------------------------- |
| WB Orders               | 1      | `NoCursorExtractor`      | Date-range, все данные в одном response              |
| WB Sales                | 1      | `NoCursorExtractor`      | Date-range                                            |
| WB Returns              | 1      | `NoCursorExtractor`      | Date-range, single request                           |
| WB Incomes              | 1      | `NoCursorExtractor`      | Date-range, single request                           |
| WB Offices              | 1      | `NoCursorExtractor`      | Single-page, no pagination                           |
| WB Warehouses           | 1      | `NoCursorExtractor`      | Single-page                                           |
| WB Prices               | 1      | `NoCursorExtractor`      | Offset-based, blind termination                      |
| WB Stocks               | 1      | `NoCursorExtractor`      | Offset-based, blind termination                      |
| Ozon Orders FBO/FBS     | 1      | `NoCursorExtractor`      | Offset-based, blind termination                      |
| Ozon Returns            | 1      | `NoCursorExtractor`      | Offset-based, blind termination                      |
| Ozon Catalog info       | 1      | `NoCursorExtractor`      | Batch, externally chunked                            |
| WB Catalog              | 2      | `JsonPathCursorExtractor` | `$.cursor` (~100 bytes, page < 200 KB)              |
| Ozon Catalog list       | 2      | `JsonPathCursorExtractor` | `$.result.last_id` + `$.result.total`               |
| Ozon Stocks             | 2      | `JsonPathCursorExtractor` | cursor + total                                       |
| Ozon Prices             | 2      | `JsonPathCursorExtractor` | cursor fields                                        |
| Ozon Finance            | 2      | `JsonPathCursorExtractor` | `$.result.page_count` (~30 bytes, page < 100 KB)   |
| WB Finance              | 3      | `TailFieldExtractor`     | `rrd_id` из хвоста файла 300 MB                     |


**Распределение: 11 endpoints без extraction, 5 с metadata extraction, 1 с tail extraction.**

### 10.4. Варианты архитектуры capture pipeline

#### Вариант A: Streaming JSON tokenizer во время записи

```
HTTP DataBuffer chunks → for each chunk:
  1. Write bytes to temp file (FileChannel)
  2. Feed bytes to SHA-256 MessageDigest
  3. Increment byte_size counter
  4. Feed bytes to streaming JSON tokenizer → cursor extractor
→ After all chunks: temp file + sha256 + byte_size + cursor ready
→ S3 putObject(tempFile)
```

Cursor extractor работает **параллельно** с записью: JSON tokenizer потоково разбирает JSON tokens, при встрече cursor-relevant tokens записывает значение.

**Плюсы:**
- Один проход — cursor доступен сразу после завершения HTTP transfer
- Для WB Finance: не нужен второй проход по 300 MB

**Минусы:**
- **JSON tokenizer на partial chunks**: chunk boundary может разрезать JSON token (`"rrd_` | `id":`). Tokenizer должен буферизировать state across chunks — усложнение
- **Coupling**: write path (чисто байтовый) зависит от JSON structure. Ошибка в tokenizer → нужно решать: прервать запись? Игнорировать? Log + continue?
- **Два контракта**: write path обязан быть 100% reliable (raw payload as-is), cursor extraction — best-effort. Объединение их в один поток создаёт конфликт reliability requirements
- **Тестируемость**: нельзя тестировать cursor extraction отдельно от streaming infrastructure
- **Сложность для Family 2**: для WB Catalog (`$.cursor` = sibling of `$.cards`) tokenizer должен пропустить весь массив `cards`, затем прочитать `cursor`. На 200 KB response — overkill
- **16 endpoints, один tokenizer**: tokenizer универсален (все JSON), но cursor extraction logic различна → нужна полиморфная стратегия поверх tokenizer → дополнительная абстракция

#### Вариант B: Post-write extraction из temp file (рекомендуемый)

```
HTTP DataBuffer chunks → for each chunk:
  1. Write bytes to temp file (FileChannel)
  2. Feed bytes to SHA-256 MessageDigest
  3. Increment byte_size counter
→ After all chunks: temp file + sha256 + byte_size ready
→ CursorExtractor.extract(tempFilePath)  ← only if needed
→ S3 putObject(tempFile)
→ DELETE temp file
```

Cursor extraction — **отдельная фаза** после записи temp file. Читает из temp file (данные уже в OS page cache).

**Плюсы:**
- **Чистое разделение**: write path — чисто байтовый (temp file + SHA-256 + counter). Ноль JSON awareness. Гарантированно корректно записывает payload as-is
- **Изоляция ошибок**: если cursor extraction сломался — temp file всё равно существует, SHA-256 корректен, payload сохранён. Можно retry extraction или fallback
- **Тестируемость**: cursor extractors тестируются на статических JSON файлах, без streaming infrastructure
- **Простота для 11 endpoints**: `NoCursorExtractor.extract()` = no-op, ноль overhead
- **Performance для Family 2**: 5 endpoints с pages < 500 KB. Temp file в OS page cache. Полный JSON parse = sub-millisecond. Дополнительный I/O = 0 (данные в page cache после записи)
- **Performance для Family 3**: WB Finance 300 MB. Tail read 32 KB = seek + read, sub-millisecond. НЕ перечитываем 300 MB

**Минусы:**
- Для WB Finance: формально "второй проход" (tail read). Но 32 KB из 300 MB = 0.01%. Фактически один seek + один read syscall
- Для Family 2: формально "второй проход" (full parse). Но 200 KB из OS page cache = instant

**Performance analysis:**


| Endpoint    | Page size  | Post-write extraction cost | Metric         |
| ----------- | ---------- | -------------------------- | -------------- |
| Family 1    | any        | 0 (NoCursorExtractor)     | —              |
| WB Catalog  | 50–200 KB  | Full JSON parse, in cache  | < 1 ms         |
| Ozon Catalog| 50–200 KB  | Full JSON parse, in cache  | < 1 ms         |
| Ozon Stocks | 50–200 KB  | Full JSON parse, in cache  | < 1 ms         |
| Ozon Prices | 50–200 KB  | Full JSON parse, in cache  | < 1 ms         |
| Ozon Finance| 50–100 KB  | Full JSON parse, in cache  | < 1 ms         |
| WB Finance  | 200–300 MB | Tail read 32 KB            | < 1 ms (seek)  |


#### Вариант C: Гибрид (streaming для WB Finance, post-write для остальных)

WB Finance (300 MB) → streaming tokenizer для `rrd_id` во время записи.
Все остальные → post-write extraction.

**Отклонён.** Причины:
- Два code path для write pipeline (streaming vs non-streaming) → удвоение тестов и error handling
- Выигрыш: избежать tail read 32 KB из 300 MB файла. Экономия < 1 ms. Не оправдывает complexity
- WB Finance имеет rate limit 1 req/min. 1 ms на extraction vs 60 000 ms sleep — незначимо

### 10.5. Рекомендация: Вариант B (post-write extraction)

**Обоснование:**

1. **Единый write path** для всех 16+ endpoints: bytes → temp file + SHA-256 + counter. Один код, один набор тестов, один error handling
2. **Pluggable cursor strategy**: `CursorExtractor<C>` с тремя реализациями, каждая тестируется отдельно
3. **Error isolation**: сбой cursor extraction не влияет на raw payload persistence. Payload always saved as-is
4. **Performance**: overhead extraction < 1 ms для всех endpoints. На фоне HTTP latency (100ms–60s) и S3 upload — незаметно
5. **Простота debugging**: при проблеме с cursor — temp file существует, можно инспектировать вручную

### 10.6. Cursor Extractor strategies: детальный дизайн

#### 10.6.1. Интерфейс

```
CursorExtractor<C>:
  C extract(Path tempFile, long byteSize) throws CursorExtractionException

CaptureResult<C>:
  Path tempFile
  String sha256Hex
  long byteSize
  C cursor          // nullable для NoCursorExtractor
```

Параметр `byteSize` передаётся для оптимизации (TailFieldExtractor использует для seek offset).

#### 10.6.2. NoCursorExtractor

Для Family 1 (~11 endpoints). Возвращает `null`. Ноль I/O.

Используется: WB Orders, Sales, Returns, Incomes, Offices, Warehouses, Prices, Stocks; Ozon Orders, Returns, Catalog info.

#### 10.6.3. JsonPathCursorExtractor

Для Family 2 (~5 endpoints). Парсит **весь** temp file через Jackson StreamingAPI, навигирует к нужному JSON path, десериализует cursor.

Конфигурация per endpoint:

```
WB Catalog:
  path = "$.cursor"
  type = WbCatalogCursor.class  // record(String updatedAt, long nmID, int total)

Ozon Catalog list:
  paths = ["$.result.last_id", "$.result.total"]
  type = OzonCatalogListCursor.class  // record(String lastId, int total)

Ozon Finance:
  paths = ["$.result.page_count", "$.result.row_count"]
  type = OzonFinancePagination.class  // record(int pageCount, int rowCount)

Ozon Stocks:
  path = "$.cursor"
  type = String.class  // Base64 cursor

Ozon Prices:
  path = cursor field
  type = String.class
```

**Алгоритм**: Jackson `JsonParser` — streaming token iteration. При достижении target path — десериализация. При выходе из scope — stop (не парсить остаток файла для paths ДО data array). Для paths ПОСЛЕ data array — skip через array (`parser.skipChildren()`) → десериализация target.

**Оптимизация для paths после data array** (WB Catalog: `cursor` после `cards`):
- `JsonParser` при встрече `cards` вызывает `skipChildren()` — пропускает весь массив без десериализации
- Переходит к `cursor` и десериализует ~100 bytes
- Memory: O(1) — токены `cards` массива не материализуются в heap

**Page size**: все Family 2 endpoints = 50–500 KB. Даже без оптимизации full parse = < 1 ms. Оптимизация с `skipChildren()` не обязательна, но рекомендуется для consistency.

#### 10.6.4. TailFieldExtractor (WB Finance)

Для Family 3 (один endpoint). Читает **хвост** temp file и извлекает `rrd_id` последнего JSON-объекта.

**Алгоритм:**

```
1. TAIL_SIZE = 32 KB  (один WB Finance row ~2-3 KB → буфер покрывает 10-15 полных объектов)
2. readOffset = max(0, byteSize - TAIL_SIZE)
3. Read bytes [readOffset .. byteSize] into byte[] buffer
4. Convert buffer to String (UTF-8)
5. Regex: find ALL matches of  "rrd_id"\s*:\s*(\d+)  in buffer
6. Return last match's captured group as long
```

**Корректность**: буфер 32 KB гарантированно содержит несколько полных JSON-объектов (каждый ~2-3 KB). Regex находит ВСЕ `rrd_id` в буфере, берёт последний — это `rrd_id` последнего объекта response.

**Edge cases:**

- File < 32 KB → readOffset = 0, читается весь файл. Корректно
- Поле `rrd_id` в середине JSON-объекта, объект разрезан началом буфера → regex найдёт `rrd_id` в полных объектах. Неполный объект в начале буфера → его `rrd_id` может быть найден или нет, но он НЕ последний, поэтому результат корректен
- HTTP 204 (no body) → termination определяется по HTTP status, TailFieldExtractor не вызывается

**Performance:**

- Seek: 1 syscall
- Read: 32 KB (1 disk read, или 0 если в page cache — а для хвоста large file, скорее всего в cache)
- Regex: на 32 KB строке — microseconds
- **Total: < 1 ms**

**Robustness:**

- Если regex не находит ни одного match → `CursorExtractionException`. Job_item сохраняется со статусом CAPTURED, alert
- Не зависит от структуры JSON кроме наличия поля `rrd_id` с числовым значением
- Не требует полного JSON parse 300 MB файла

### 10.7. Полный flow capture pipeline (итоговый)

```
MarketplaceAdapter.fetchPage(sourceConfig, pageParams):

  1. HTTP request → WebClient.get/post(...)
     - Для WB Finance: проверить HTTP status. Если 204 → return END_OF_DATA (без capture)

  2. Response body → DataBuffer Flux → streamToTempFile():
     For each DataBuffer chunk:
       a. tempFileChannel.write(chunk.asByteBuffer())
       b. sha256Digest.update(chunk.asByteBuffer())
       c. byteSize += chunk.readableByteCount()
       d. DataBufferUtils.release(chunk)
     → CaptureResult(tempFile, sha256Hex, byteSize)

  3. Cursor extraction (post-write):
     cursorExtractor = sourceConfig.cursorExtractor()
     cursor = cursorExtractor.extract(tempFile, byteSize)
     // NoCursorExtractor → null (instant)
     // JsonPathCursorExtractor → parse temp file (< 1ms)
     // TailFieldExtractor → read tail 32KB (< 1ms)

  4. S3 upload:
     s3Key = buildKey(accountId, event, sourceId, requestId, pageNumber)
     s3Client.putObject(bucket, s3Key, tempFile, byteSize)

  5. PostgreSQL index:
     BEGIN
       INSERT job_item (request_id, source_id, page, s3_key, sha256, byte_size, status=CAPTURED)
       UPDATE source_execution_state (progress)
     COMMIT

  6. Cleanup:
     Files.deleteIfExists(tempFile)

  7. Return:
     PageResult(cursor, captureResult.sha256, captureResult.byteSize)
     // caller uses cursor for next page request
     // null cursor + non-204 = "try blind next offset" for Family 1
```

**Memory footprint: O(chunk_size) = 8–64 KB** per capture, вне зависимости от response size.

**Единый write path** для всех 16+ endpoints. Различие только в шаге 3 (cursor extractor).

### 10.8. Нетривиальные решения и обоснования

#### Почему не "для каждого API-метода свой extractor"

Из 16+ endpoints:
- 11 не нуждаются в extraction вообще → `NoCursorExtractor` (один класс)
- 5 нуждаются в metadata extraction → `JsonPathCursorExtractor` (один класс, параметризованный JSON path и типом cursor)
- 1 нуждается в tail extraction → `TailFieldExtractor` (один класс)

**3 класса, не 16+.** Pagination shape определяет strategy, не конкретный endpoint.

Конфигурация per endpoint — через source config:

```
WbCatalogSource:
  cursorExtractor = JsonPathCursorExtractor("$.cursor", WbCatalogCursor.class)

WbFinanceSource:
  cursorExtractor = TailFieldExtractor("rrd_id")

WbOrdersSource:
  cursorExtractor = NoCursorExtractor.INSTANCE

OzonFinanceSource:
  cursorExtractor = JsonPathCursorExtractor(["$.result.page_count"], OzonFinancePagination.class)
```

#### Почему TailFieldExtractor использует regex, а не JSON parser

Для 32 KB буфера:
- **Regex**: одна строковая операция, microseconds. Нет зависимости от JSON parser state. Нет risk partial object parse failure
- **JSON parser**: нужно найти начало валидного JSON в середине буфера (буфер начинается с обрезанного объекта). Сложно корректно определить границу первого полного объекта. Overkill для extraction одного числового поля

Regex `"rrd_id"\s*:\s*(\d+)` — детерминирован, безопасен (нет injection — данные от API, не от user input), покрывает все разумные форматы WB Finance response.

#### Почему Ozon Finance — JsonPathCursorExtractor, а не NoCursorExtractor

Ozon Finance page-based (`page` + `page_size`). Можно было бы использовать "blind" approach (increment page until empty). Но:

1. `page_count` из first response позволяет сразу вычислить total iterations → progress tracking, ETA
2. Запрос page > page_count может вернуть ошибку (не пустой response) — зависит от API
3. Page size 50–100 KB → extraction trivial, zero overhead
4. Extraction `page_count` — полезная operational метрика (сколько pages для данного аккаунта/периода)

**Решение**: `JsonPathCursorExtractor` для Ozon Finance — минимальный overhead, максимальная информация.

#### Почему "blind" offset approach, а не post-write item count

Для offset-based endpoints (WB Prices, WB Stocks, Ozon Orders, Ozon Returns) termination можно определить двумя способами:

1. **Blind**: increment offset → fetch → пустой response = stop. Один extra HTTP request
2. **Post-write count**: после записи temp file, парсить JSON чтобы посчитать элементы → if count < limit, stop

"Blind" выбран потому что:
- Один extra HTTP request < parsing JSON для item count
- Пустые responses — tiny (100–200 bytes), instant temp file + S3
- Избегает JSON parsing в capture path для этих endpoints → чистый байтовый write path сохраняется
- Для WB Stocks (1 req/20s) и WB Prices: extra request = 20s wait, но sync background → acceptable

### 10.9. Риски capture pipeline (дополненные)

Помимо рисков R-CAP-01 — R-CAP-11 (секция 9), выделяются дополнительные risk areas, специфичные для cursor extraction:

#### R-CAP-CE-01: TailFieldExtractor — rrd_id совпадение в nested field

| Параметр  | Значение                                                                                                                                                                    |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Влияние   | Если WB Finance response содержит поле с подстрокой `rrd_id` внутри nested object (например `"nested_rrd_id": 999`), regex может ошибочно match                           |
| Вероятность | VERY LOW — WB Finance response flat (нет nested objects с rrd_id). Поле `rrd_id` — primary key строки отчёта, не появляется в других контекстах                          |
| Митигация | Regex точный: `"rrd_id"\s*:\s*(\d+)`. Не matchит `"some_rrd_id"`. Валидация: extracted rrd_id > previous cursor rrd_id (монотонность). Если нарушена — alert            |


#### R-CAP-CE-02: JSON field order change в API

| Параметр  | Значение                                                                                                                                                          |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Влияние   | API provider меняет порядок полей в JSON (e.g., Ozon `page_count` перемещается из начала `result` в конец). JsonPathCursorExtractor с `skipChildren()` оптимизацией может пропустить поле |
| Вероятность | LOW — JSON field order обычно стабилен. Jackson streaming API навигирует по field names, не по positions                                                        |
| Митигация | JsonPathCursorExtractor НЕ полагается на порядок — итерирует все fields в scope. `skipChildren()` применяется только к data array (по имени), не ко всему response |


#### R-CAP-CE-03: WB Finance response — не root array

| Параметр  | Значение                                                                                                                                                                      |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Влияние   | WB меняет API: response становится `{"data": [...], "meta": {...}}` вместо root array `[...]`. TailFieldExtractor regex всё ещё найдёт rrd_id, но JsonArrayLocator для read path сломается |
| Вероятность | LOW — WB v5 API стабилен. Но v6 может изменить structure                                                                                                                   |
| Митигация | TailFieldExtractor robust (regex на tail). Проблема проявится на read path (streaming parse), не на capture. Provider contract monitoring: алерт при изменении response structure |


#### R-CAP-CE-04: Ozon cursor = empty string vs null

| Параметр  | Значение                                                                                                                      |
| --------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Влияние   | Ozon возвращает `"cursor": ""` (empty string) vs `"cursor": null` vs отсутствие поля. Три разных случая → разная семантика end-of-data |
| Митигация | JsonPathCursorExtractor обрабатывает все три случая: empty/null/absent → return `null` cursor → caller stops pagination       |


---

## Связанные документы

- [Архитектура данных](data-architecture.md) — raw layer definition, pipeline, инварианты
- [Нефункциональная архитектура](non-functional-architecture.md) — streaming ingest, retention
- [Runbook](runbook.md) — S3-compatible в production checklist
- [Provider contracts](provider-contracts/) — pagination semantics per endpoint

