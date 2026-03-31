# ETL Pipeline — Module Scenarios

## Роль модуля

ETL Pipeline отвечает за движение данных от маркетплейсов до аналитического слоя: Raw (S3) → Normalized (in-process) → Canonical (PostgreSQL) → Analytics (ClickHouse). Обеспечивает idempotency, data provenance, partial failure handling, и post-sync event distribution.

## Сценарии

### ETL-01: Incremental sync (happy path)

- **Назначение:** Регулярная инкрементальная загрузка данных по одному data domain.
- **Trigger:** Scheduled job на основе `marketplace_sync_state.next_scheduled_at`.
- **Main path:** Check cursor → call provider API (paginated) → save each page to S3 (raw) → normalize → UPSERT canonical (IS DISTINCT FROM) → update cursor → INSERT outbox events (SYNC_COMPLETED, domain-specific) → job_execution = COMPLETED.
- **Dependencies:** Connection ACTIVE. Cursor (last sync timestamp/offset). Provider API available.
- **Failure risks:** Provider API changed format → normalization error. Cursor stale → duplicate data (idempotent UPSERT handles). Provider pagination inconsistency.
- **Uniqueness:** Основной operational flow. Cursor-based extraction — уникальная persistence semantics.

### ETL-02: Full sync (initial load)

- **Назначение:** Первичная загрузка всего dataset при подключении маркетплейса.
- **Trigger:** Connection transitions to ACTIVE (first time) → `FULL_SYNC` job автоматически.
- **Main path:** Как ETL-01, но без cursor: загрузка всех страниц. Reset cursor после completion.
- **Dependencies:** Connection ACTIVE. Provider API pagination (cursor-based для WB, offset/limit для Ozon).
- **Failure risks:** Large dataset → long runtime → timeout. Memory: streaming page-by-page (не весь dataset в памяти). Mid-sync failure → COMPLETED_WITH_ERRORS, next sync подхватит.
- **Uniqueness:** Другой trigger (connection creation), нет cursor, потенциально огромный объём данных.

### ETL-03: Manual sync trigger

- **Назначение:** Администратор вручную запускает sync для конкретных domains.
- **Trigger:** `POST /api/connections/{connectionId}/sync` с optional domain list.
- **Main path:** Validate connection ACTIVE → create job_execution → dispatch to worker → process как ETL-01.
- **Dependencies:** User role: ADMIN/OWNER. Connection ACTIVE.
- **Failure risks:** Concurrent manual + scheduled sync → concurrency guard (one active job per connection per domain).
- **Uniqueness:** User-initiated (не scheduler). Может задавать subset domains.

### ETL-04: Normalization error (record-level)

- **Назначение:** Отдельная запись не проходит parsing/validation.
- **Trigger:** Invalid JSON, type coercion error, missing required field.
- **Main path:** Record skipped → log warning → continue batch → job_execution.error_details updated → COMPLETED_WITH_ERRORS.
- **Dependencies:** Error category determines behavior (parse error: skip page; type coercion: skip record; validation: skip record).
- **Failure risks:** Silent data loss при aggressive skip. Мониторинг: error_count > threshold → alert.
- **Uniqueness:** Partial failure (не terminal). Другой recovery: не retry, а skip + alert.

### ETL-05: Normalization error (page-level)

- **Назначение:** Вся страница не parseable (provider вернул невалидный ответ).
- **Trigger:** Page-level parse error (unexpected structure, HTML вместо JSON).
- **Main path:** job_item → FAILED. Raw S3 object preserved для investigation. Continue с остальными pages.
- **Dependencies:** Raw layer capture must happen BEFORE normalization (capture first, process second).
- **Failure risks:** Provider API change → все pages fail → job FAILED. Alert + investigation.
- **Uniqueness:** Page-level failure (отличается от record-level). Raw preserved для forensics.

### ETL-06: SKU lookup miss

- **Назначение:** Finance entry ссылается на SKU, которого нет в canonical catalog.
- **Trigger:** Canonical write: seller_sku_id not found для finance/order entry.
- **Main path:** Record saved с seller_sku_id = NULL. Log warning. P&L projection для этой записи incomplete (SKU-level drill-down невозможен).
- **Dependencies:** Catalog sync должен предшествовать finance sync (data dependency graph).
- **Failure risks:** Systematic SKU misses → P&L accuracy degradation. Investigation via data provenance.
- **Uniqueness:** Referential integrity soft failure — запись сохраняется с nullable FK, не отбрасывается.

### ETL-07: Canonical UPSERT no-churn

- **Назначение:** Повторная загрузка тех же данных не создаёт ложных обновлений.
- **Trigger:** Re-sync тех же данных (provider вернул то же содержимое).
- **Main path:** UPSERT с IS DISTINCT FROM → если данные не изменились, UPDATE не происходит, updated_at не меняется → downstream events не генерируются.
- **Dependencies:** IS DISTINCT FROM comparison на всех business-significant columns.
- **Failure risks:** Missing column в comparison → false no-churn (данные изменились, но не detected).
- **Uniqueness:** Idempotency mechanism — критически важен для предотвращения cascade ложных pricing runs.

### ETL-08: Post-sync outbox events

- **Назначение:** После sync генерировать events для downstream consumers (Pricing, Analytics, Alerting).
- **Trigger:** job_execution completion (COMPLETED или COMPLETED_WITH_ERRORS).
- **Main path:** INSERT single `ETL_SYNC_COMPLETED` outbox event в той же транзакции с финальным update job_execution. Event payload: `{ connection_id, job_execution_id, sync_scope, completed_domains[], failed_domains[], completed_at }`. Downstream consumers проверяют `completed_domains[]` чтобы определить свою релевантность (например, Pricing реагирует если `PRICES` ∈ `completed_domains[]`, Analytics — если `FINANCE` ∈ `completed_domains[]`).
- **Dependencies:** Outbox table. RabbitMQ (eventual delivery).
- **Failure risks:** Outbox publisher lag → downstream stale. Consumer must check `completed_domains[]` — если нужный domain отсутствует или в `failed_domains[]`, consumer пропускает обработку.
- **Uniqueness:** Fan-out point: один sync → один `ETL_SYNC_COMPLETED` event с domain list. Downstream consumers фильтруют по `completed_domains[]` вместо подписки на отдельные event types.

### ETL-09: ClickHouse materialization (happy path)

- **Назначение:** Перенос данных из canonical (PostgreSQL) в analytics (ClickHouse).
- **Trigger:** Post-sync `ETL_SYNC_COMPLETED` event или daily full re-materialization.
- **Main path:** Read canonical data (batch) → INSERT into ClickHouse (facts, dims) → update materialization state.
- **Dependencies:** ClickHouse available. Canonical data consistent.
- **Failure risks:** ClickHouse down → COMPLETED_WITH_ERRORS. Data volume → batch sizing.
- **Uniqueness:** Cross-store write (PG → CH). ReplacingMergeTree обеспечивает upsert.

### ETL-10: ClickHouse materialization failure

- **Назначение:** ClickHouse недоступен или INSERT fails.
- **Trigger:** ClickHouse connection error, timeout, disk full.
- **Main path:** Canonical writes (PostgreSQL) успешны. CH INSERT fails → log error → job COMPLETED_WITH_ERRORS. Daily re-materialization восполнит gap.
- **Dependencies:** ClickHouse — derived store, не source of truth.
- **Failure risks:** Prolonged outage → analytics stale → pricing blocked (derived signals required from CH).
- **Uniqueness:** Отдельный failure path: canonical (PG) OK, analytics (CH) failed. Degraded but not catastrophic.

### ETL-11: Late-arriving data

- **Назначение:** Provider возвращает данные с задержкой (finance entries появляются через дни).
- **Trigger:** Finance sync загружает entries с entry_date значительно раньше текущей даты.
- **Main path:** UPSERT canonical (normal). Downstream: P&L re-computation для affected period.
- **Dependencies:** Finance adapter: date range filter должен покрывать достаточный lookback window.
- **Failure risks:** Lookback window слишком узкий → missed entries. Слишком широкий → performance.
- **Uniqueness:** Temporal anomaly — данные приходят позже, чем ожидалось. Влияет на analytics accuracy.

### ETL-12: Duplicate raw delivery

- **Назначение:** Одни и те же данные загружены повторно (scheduler overlap, manual re-trigger).
- **Trigger:** Concurrent sync jobs или manual re-sync.
- **Main path:** Raw: duplicate S3 objects (different keys, same content). Canonical: UPSERT IS DISTINCT FROM → no-churn. Safe.
- **Dependencies:** Canonical idempotency (ETL-07).
- **Failure risks:** Performance: processing duplicate pages is wasted work (but not incorrect).
- **Uniqueness:** Duplicate delivery scenario — validates idempotency end-to-end.

### ETL-13: Cursor corruption / reset

- **Назначение:** Cursor (last sync position) потерян или некорректен.
- **Trigger:** Manual cursor reset. Bug in cursor update. Database restore.
- **Main path:** Reset cursor → next sync = FULL_SYNC (load everything). Canonical UPSERT handles duplicates.
- **Dependencies:** Cursor stored in `marketplace_sync_state`.
- **Failure risks:** Full sync is expensive (time, API calls). But safe due to idempotency.
- **Uniqueness:** Recovery scenario — другой trigger, другой scope (full вместо incremental).

### ETL-14: Data domain dependency ordering

- **Назначение:** Некоторые domains зависят от других (finance зависит от catalog для SKU lookup).
- **Trigger:** Sync scheduling: ETL event dependency graph determines order.
- **Main path:** CATALOG_SYNC → PRICES_SYNC, STOCKS_SYNC → FINANCE_SYNC → PROMO_SYNC (parallel where safe).
- **Dependencies:** ETL event dependency graph (documented in etl-pipeline.md).
- **Failure risks:** Catalog sync failed → finance sync runs without fresh SKU data → SKU lookup misses (ETL-06).
- **Uniqueness:** Ordering constraint — не отдельный data flow, а orchestration rule.

### ETL-15: Cost profile SCD2 versioning

- **Назначение:** Управление историей COGS (cost price) через SCD2.
- **Trigger:** POST /api/cost-profiles (single) или POST /api/cost-profiles/bulk-import (CSV).
- **Main path:** Close previous version (set valid_to) → INSERT new version (valid_from = input) → post-sync event для P&L re-computation.
- **Dependencies:** seller_sku exists. User role: ADMIN/PRICING_MANAGER.
- **Failure risks:** Bulk import partial failure → row-level: skip invalid rows, import valid. Backdated valid_from → recalculation of historical P&L.
- **Uniqueness:** User-initiated data input (не marketplace sync). SCD2 persistence — другая write semantics.

### ETL-16: Job retry (failed job re-execution)

- **Назначение:** Повторное выполнение failed job.
- **Trigger:** POST /api/jobs/{jobId}/retry (ADMIN/OWNER).
- **Main path:** Create new job_execution с тем же scope → execute from scratch. Idempotent canonical writes handle overlap.
- **Dependencies:** Original job в FAILED или COMPLETED_WITH_ERRORS.
- **Failure risks:** Same failure repeats → investigation needed. Cost: full re-processing of scope.
- **Uniqueness:** User-initiated retry (не automatic). Новый job, не продолжение старого.

### ETL-17: Stale job detection (IN_PROGRESS → STALE)

- **Назначение:** Обнаружение зависших ETL jobs.
- **Trigger:** Scheduled job (every 15 min).
- **Main path:** Scan `job_execution WHERE status = 'IN_PROGRESS' AND started_at < now() - interval '1 hour'` → CAS UPDATE → STALE.
- **Dependencies:** `datapulse.etl.stale-job-threshold` configuration.
- **Failure risks:** Legitimately long sync classified as stale. Mitigation: configurable threshold (default: 1 hour).
- **Uniqueness:** Safety net для worker crash, pod eviction, или зависших provider calls.

### ETL-18: Advertising pipeline exception (Raw → ClickHouse)

- **Назначение:** Рекламные данные загружаются напрямую в ClickHouse, минуя canonical PostgreSQL.
- **Trigger:** `ADVERTISING_FACT` ETL event.
- **Main path:** Raw (S3) → Normalized → INSERT `fact_advertising` + `dim_advertising_campaign` (ClickHouse). Нет canonical entity в PostgreSQL. Data provenance через `job_execution_id` в `fact_advertising`.
- **Dependencies:** ClickHouse available. Campaign → product mapping для allocation.
- **Failure risks:** ClickHouse down → advertising data lost until next re-materialization. No canonical fallback.
- **Uniqueness:** Единственное исключение из pipeline invariant (Raw → Normalized → Canonical → Analytics). Обоснование: advertising data используется только для аналитики (P&L allocation, pricing signal `ad_cost_ratio`), ни один decision flow не читает advertising state из PostgreSQL.

### ETL-19: Concurrent sync guard (duplicate job prevention)

- **Назначение:** Предотвращение одновременного запуска двух sync jobs для одного connection/domain.
- **Trigger:** Scheduled sync + manual trigger одновременно. Или scheduler fired twice (pod restart, clock drift).
- **Main path:** Первый job → `marketplace_sync_state.status` = `SYNCING` (CAS: `IDLE → SYNCING`). Второй job → CAS fails (`status ≠ IDLE`) → job rejected → `log.debug("sync already in progress")` → no-op.
- **Dependencies:** `marketplace_sync_state.status` field. CAS guard на transition `IDLE → SYNCING`.
- **Failure risks:** Worker crash mid-sync → status stuck in `SYNCING` → ETL-17 (stale job detection) переводит в STALE → status reset → next sync proceeds. Race window: два jobs стартуют точно одновременно → CAS single winner.
- **Uniqueness:** Concurrency guard — другой failure path (reject, не error). Защита от duplicate work, не от data corruption (canonical UPSERT IS DISTINCT FROM всё равно idempotent, но duplicate work = wasted API calls + rate limit consumption).

### ETL-20: S3 unavailability mid-sync

- **Назначение:** Обработка недоступности MinIO (S3) во время sync.
- **Trigger:** S3 putObject fails (connection error, timeout, disk full).
- **Main path:** API response получен → temp file written → S3 putObject fails → `job_item` не создаётся → page lost. Sync continues с следующей page (partial failure). Job завершается как `COMPLETED_WITH_ERRORS`. Error count tracked. При persistent S3 failure (все pages fail) → job `FAILED`.
- **Dependencies:** S3 health check. Temp file on disk (buffer). Error threshold configuration.
- **Failure risks:** S3 down prolonged → все syncs fail → data staleness → stale_data alert → automation blocked. Temp files accumulate on disk → disk pressure (R-CAP-07). Recovery: S3 восстановлен → next scheduled sync succeeds → full data restored via idempotent UPSERT.
- **Uniqueness:** Infrastructure failure в первом слое pipeline (Raw). Отличается от ETL-10 (CH down на последнем слое): S3 down → canonical write тоже не произойдёт (pipeline sequential: raw → normalized → canonical).
