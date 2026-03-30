# ETL Pipeline — Implementation Plan

## Обзор

ETL Pipeline — центральная подсистема DataPulse, отвечающая за загрузку данных из API маркетплейсов (Ozon, Wildberries), их нормализацию в raw-таблицы, трансформацию в star schema (dim/fact) и пересчёт аналитических витрин (mart).

## Архитектура

```
REST API (POST /api/etl/run)
 → EtlOrchestratorService          (точка входа, резолвит дату)
 → EtlRunBootstrapService          (план, персистенция, outbox)
 → OutboxPollingFlowConfig         (Spring Integration poller)
 → OutboxPublishFlowConfig         (AMQP outbound → RabbitMQ)
 → RabbitExecutionInboundFlowConfig (AMQP inbound → worker)
 → EtlWorkerService                (CAS claim, pipeline, retry)
 → EtlSourcePipelineService        (стриминг, batch insert)
 → EtlCompletionService            (финализация, материализация)
 → EtlMaterializationServiceImpl   (handler dispatch)
 → MartRefreshService              (UPSERT витрин)
```

## Компоненты

### 1. Оркестрация — `EtlOrchestratorService`

**Модуль:** `datapulse-etl` → `io.datapulse.etl.service`

**Ответственность:** Единая точка входа для запуска ETL. Принимает `EtlRunRequest`, резолвит дату через `EtlDateRangeResolver`, делегирует bootstrap.

**Ключевые решения:**
- `@Transactional` — весь bootstrap выполняется в одной транзакции
- `MarketplaceEvent.fromString()` — нормализация строкового события в enum

**Зависимости:**
- `EtlDateRangeResolver` — определение `dateFrom`/`dateTo` из `EtlDateMode` (LAST_N_DAYS, CUSTOM, AUTO)
- `EtlRunBootstrapService` — создание плана и первичных записей

### 2. Bootstrap — `EtlRunBootstrapService`

**Ответственность:** Создание execution, разбивка на steps, персистенция в БД, создание outbox-записей для корневых шагов.

**Алгоритм:**
1. Генерация `requestId` (UUID)
2. Получение активных маркетплейсов для аккаунта через `AccountConnectionService.getActiveMarketplacesByAccountId()`
3. Построение `EventExecutionPlan` через `EventExecutionPlanResolver`
4. `INSERT etl_execution` со статусом `NEW`
5. Если шагов нет — CAS `NEW → COMPLETED`, возврат `BootstrapResult(requestId, immediatelyCompleted=true)`
6. `INSERT BATCH etl_source_execution_state` — lane roots со статусом `READY`, остальные `BLOCKED`
7. CAS `NEW → IN_PROGRESS`
8. `INSERT BATCH outbox_message` — по одной записи `EXECUTION_PUBLISH` на каждый lane root
9. Возврат `BootstrapResult(requestId, immediatelyCompleted=false)`

**Паттерны:**
- **CAS (Compare-And-Swap):** `casUpdateStatus(requestId, fromStatus, toStatus)` — предотвращение race conditions
- **Outbox pattern:** вместо прямой публикации в Rabbit записи создаются в таблице `outbox_message`
- **Lane model:** параллельные дорожки по маркетплейсам, последовательные шаги внутри

### 3. План выполнения — `EventExecutionPlanResolver`

**Ответственность:** Чистое построение плана без side-effects.

**Модель:**
- `EventExecutionPlan` → List of `MarketplaceLanePlan`
- `MarketplaceLanePlan` → marketplace + laneKey + List of `SourceStepPlan`
- `SourceStepPlan` → sourceId, marketplace, laneKey, stepOrder, predecessorSourceId, laneRoot

**Алгоритм:**
1. `EtlSourceRegistry.findSources(event)` — все зарегистрированные источники для события
2. Фильтрация по `activeMarketplaces` аккаунта
3. Группировка по `MarketplaceType` (EnumMap)
4. Для каждого маркетплейса — построение `MarketplaceLanePlan` с цепочкой шагов (predecessor chain)

### 4. Реестр источников — `EtlSourceRegistry`

**Ответственность:** Индексация всех `EventSource` бинов по `MarketplaceEvent`.

**Механизм:**
- Constructor injection `List<EventSource>` (все Spring-бины)
- Resolve `@EtlSourceMeta` annotation через `AopUtils.getTargetClass()` (для proxy-aware resolution)
- Индекс `Map<MarketplaceEvent, List<RegisteredSource>>` отсортирован по `order`
- `RegisteredSource` record: event, marketplace, order, sourceId (=className), source, rawTable

**Контракт EventSource:**
- `fetchSnapshotChunks(accountId, event, dateFrom, dateTo)` → `List<RawSnapshotChunk>`
- Default implementation оборачивает `fetchSnapshots()` в `RawSnapshotChunk(null, snapshot)`

**Аннотация `@EtlSourceMeta`:**
- `events[]` — какие MarketplaceEvent поддерживает
- `marketplace` — MarketplaceType
- `rawTableName` — целевая raw-таблица
- `order` — порядок внутри lane

### 5. Outbox Pattern

**Таблица `outbox_message`:**
| Колонка | Тип | Назначение |
|---------|-----|-----------|
| id | BIGSERIAL | PK |
| type | VARCHAR | `EXECUTION_PUBLISH` или `WAIT_PUBLISH` |
| payload_json | TEXT | Сериализованный `EtlSourceExecution` |
| exchange_name | VARCHAR | Целевой RabbitMQ exchange |
| routing_key | VARCHAR | Routing key |
| correlation_id | VARCHAR | requestId для трейсинга |
| ttl_millis | BIGINT | TTL для wait-сообщений (nullable) |
| status | VARCHAR | `PENDING` → `SENT` / `ERROR` |
| created_at | TIMESTAMPTZ | Время создания |
| sent_at | TIMESTAMPTZ | Время отправки |
| last_error | TEXT | Последняя ошибка (до 4000 символов) |

**Polling Flow (`OutboxPollingFlowConfig`):**
1. `Pollers.fixedDelay(outboxProperties.getPollDelay())` — периодический poll
2. `claimBatch(batchSize)` — `SELECT ... FOR UPDATE SKIP LOCKED` (конкурентный claim)
3. `split()` — разбивка батча на отдельные сообщения
4. `transform(OutboxMessageMapper::toMessage)` — маппинг в Spring Integration `Message<String>`
5. Передача в `outboxPublishChannel`

**Publish Flow (`OutboxPublishFlowConfig`):**
1. `Amqp.outboundAdapter(outboxRabbitTemplate)` — публикация в RabbitMQ
2. Exchange и routing key из headers (`EtlAmqpHeaders.OUTBOX_EXCHANGE`, `OUTBOX_ROUTING_KEY`)
3. `OutboxPublishAdvice` — AOP advice: на успех `markSent()`, на ошибку `markError()` (ошибка glотится, retry через следующий poll)

**Message Mapper (`OutboxMessageMapper`):**
- `OUTBOX_ID` header для трейсинга
- `AmqpHeaders.CONTENT_TYPE = "application/json"`
- `__TypeId__` header для Jackson deserialization
- `AmqpHeaders.EXPIRATION` для wait-сообщений (TTL в миллисекундах)

### 6. RabbitMQ Topology

**Exchanges (Direct, durable):**
- `etl.execution` — основной
- `etl.execution.wait` — для delayed retry

**Queues:**
- `etl.execution.queue` — основная очередь worker'а
- `etl.execution.wait.queue` — wait queue с **dead-letter exchange** = `etl.execution`, dead-letter routing key = execution routing key

**Механизм delayed retry:**
1. Worker получает rate limit → вставляет `WAIT_PUBLISH` outbox с `ttlMillis`
2. Outbox poller публикует в `etl.execution.wait` exchange с `AmqpHeaders.EXPIRATION`
3. Сообщение попадает в wait queue, по истечении TTL — dead-letter в execution exchange → execution queue
4. Worker обрабатывает повторно

**Listener Container:**
- `SimpleMessageListenerContainer`, prefetch=1, AUTO ack, `defaultRequeueRejected=true`

### 7. Worker — `EtlWorkerService`

**Ответственность:** Обработка одного шага ETL из RabbitMQ.

**Guards (защитные проверки):**
1. Execution не terminal и существует
2. Source state не terminal и существует
3. `nextAttemptAt` check — если retry запланирован в будущее и порог `allowedSkew` не превышен, skip
4. CAS `casStart(requestId, event, sourceId, casThreshold)` — single-winner claim

**Обработка:**
- **Успех:** `TransactionTemplate` → `progressionService.onStepCompleted()` → `completionService.checkAndFinalize()`
- **Rate limit / 429:** `handleBackoff()` → проверка `maxAttempts` → либо terminal failure, либо `casRetryScheduled()` + `WAIT_PUBLISH` outbox
- **Terminal failure:** `progressionService.onStepFailedTerminal()` → `completionService.checkAndFinalize()`

**Retry logic:**
- TTL = `max(min(retryAfterSeconds * 1000, maxBackoff), minBackoff)`
- `nextAttemptAt = Instant.now() + ttl`
- CAS `casRetryScheduled(requestId, event, sourceId, newAttempt, nextAttemptAt)`

### 8. Pipeline — `EtlSourcePipelineService`

**Ответственность:** Загрузка и сохранение raw-данных.

**Алгоритм:**
1. Resolve `RegisteredSource` из `EtlSourceRegistry`
2. `ensureRawTableExists(rawTable)` — DDL через `RawTableSchemaJdbcRepository`
3. `source.fetchSnapshotChunks(accountId, event, dateFrom, dateTo)` — получение снапшотов
4. Для каждого chunk:
   a. Resolve `JsonArrayLocator` из `SnapshotJsonLayoutRegistry`
   b. Create streaming iterator через `SnapshotIteratorFactory`
   c. Для каждой записи: serialize → SHA-256 hash → `RawEtlSnapshotEntity`
   d. Batch insert по 500 записей через `RawBatchInsertJdbcRepository`

**Идемпотентность:**
- `INSERT ... ON CONFLICT (request_id, source_id, record_key) DO NOTHING`
- `record_key` = SHA-256 от JSON-представления записи
- Повторная вставка той же записи безопасна

**Raw-таблица DDL (`RawTableSchemaJdbcRepository`):**
```sql
CREATE TABLE IF NOT EXISTS raw_* (
    id          BIGSERIAL PRIMARY KEY,
    request_id  VARCHAR(64)  NOT NULL,
    account_id  BIGINT       NOT NULL,
    event       VARCHAR(64)  NOT NULL,
    source_id   VARCHAR(128) NOT NULL,
    record_key  VARCHAR(512) NOT NULL,
    payload     JSONB        NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_<table>_dedup UNIQUE (request_id, source_id, record_key)
);
```
- Индексы: `idx_<table>_request (request_id)`, `idx_<table>_account_request (account_id, request_id)`

### 9. Step Progression — `SourceStepProgressionService`

**Ответственность:** Управление переходами между шагами внутри lane.

**onStepCompleted:**
1. CAS `IN_PROGRESS → COMPLETED`
2. Find successor в lane
3. Проверка что execution всё ещё `IN_PROGRESS`
4. CAS `BLOCKED → READY` для successor
5. Insert `EXECUTION_PUBLISH` outbox для successor

**onStepFailedTerminal:**
1. CAS `IN_PROGRESS → FAILED_TERMINAL`
2. `markLaneRemainingSkipped(requestId, laneKey)` — все downstream шаги в lane помечаются как SKIPPED

### 10. Completion — `EtlCompletionService`

**Ответственность:** Финализация execution после завершения всех шагов.

**Алгоритм:**
1. `sourceRepository.getCompletionState(requestId)` → `CompletionResult(allTerminal, hasFailedTerminal, counts)`
2. Если не все terminal — return
3. Если есть failed → CAS `IN_PROGRESS → FAILED`, notify scenario
4. Если все completed:
   a. CAS `IN_PROGRESS → MATERIALIZING` (single-winner claim)
   b. `materializationService.executeMaterialization(requestId)`
   c. `rawRetentionService.cleanupAfterExecution(requestId)` — очистка old raw данных
   d. CAS `MATERIALIZING → COMPLETED`
   e. При ошибке materialization: CAS `MATERIALIZING → FAILED`
5. Notify `scenarioProgressService.onExecutionTerminal()`

### 11. Materialization — `EtlMaterializationServiceImpl`

**Ответственность:** Dispatch materialization handlers по маркетплейсам.

**Алгоритм:**
1. Load execution, parse event
2. Load all source states, filter `COMPLETED`
3. Group by `MarketplaceType`
4. Для каждого маркетплейса: `handlerRegistry.findFor(event, marketplace)` → `handler.materialize(context)`
5. После всех handlers: `martRefreshService.refreshAfterEvent(accountId, event, requestId)`

**MaterializationHandler interface:**
- `supportedEvent()` → `MarketplaceEvent`
- `marketplace()` → `MarketplaceType`
- `materialize(MaterializationContext)` — SQL трансформации из JSONB → dim/fact upserts

**MaterializationHandlerRegistry:**
- Двойной индекс `(event, marketplace) → handler`
- Запрет дублирующих registrations (fail-fast при старте)
- Deterministic ordering by `MarketplaceType.values()` order

### 12. Mart Refresh — `MartRefreshService`

**Ответственность:** Пересчёт витрин после материализации.

**Правила обновления:**
| Событие | `mart_order_pnl` | `mart_product_pnl` | `mart_promo_product_analysis` |
|---------|------------------|---------------------|-------------------------------|
| `SALES_FACT` | ✅ | ✅ | ✅ |
| `ADVERTISING_FACT` | ✅ | ✅ | ❌ |
| `FACT_FINANCE` | ✅ | ✅ | ✅ |
| `PROMO_SYNC` | ❌ | ❌ | ✅ |

**Витрина `mart_order_pnl`:** Полный UPSERT по аккаунту через `OrderPnlMartJdbcRepository.refresh(accountId)`:
- Spine = UNION `fact_finance` + `fact_sales` (dual-driver)
- CTE: `finance_by_order`, `sales_by_order`, `returns_by_order`, `expense_unit_costs`, `expense_cost_ranges`, `cogs_by_order`, `ad_daily_product`, `sales_daily_product`, `advertising_by_order`
- COGS через `custom_expense_entry.cost_per_unit` с SCD-ranges
- Advertising allocation pro-rata по `sale_amount / daily_revenue`
- `ON CONFLICT ... DO UPDATE ... WHERE ... IS DISTINCT FROM` — skip-if-unchanged optimization

## Статусы

**EtlExecutionStatus:** `NEW` → `IN_PROGRESS` → `MATERIALIZING` → `COMPLETED` / `FAILED`

**SourceExecutionStatus:** `BLOCKED` → `READY` → `IN_PROGRESS` → `COMPLETED` / `FAILED_TERMINAL` / `SKIPPED`

**OutboxMessageStatus:** `PENDING` → `SENT` / `ERROR`

## Конфигурация

- `datapulse.etl.amqp.*` — exchanges, queues, routing keys
- `datapulse.etl.outbox.*` — batch-size, poll-delay
- `datapulse.etl.worker.*` — allowed-skew
- `datapulse.etl.retry.*` — max-attempts, min-backoff, max-backoff

## Ключевые файлы

| Файл | Модуль | Роль |
|------|--------|------|
| `EtlOrchestratorService.java` | etl | Точка входа |
| `EtlRunBootstrapService.java` | etl | Plan + persist + outbox |
| `EtlWorkerService.java` | etl | Message processing |
| `EtlSourcePipelineService.java` | etl | Raw data ingest |
| `EtlCompletionService.java` | etl | Finalization lifecycle |
| `SourceStepProgressionService.java` | etl | Lane step transitions |
| `EventExecutionPlanResolver.java` | etl | Pure plan builder |
| `EtlSourceRegistry.java` | etl | Source index by annotation |
| `OutboxPollingFlowConfig.java` | etl | SI polling flow |
| `OutboxPublishFlowConfig.java` | etl | SI → AMQP publish |
| `OutboxPublishAdvice.java` | etl | Outbox status management |
| `RabbitTopologyConfig.java` | etl | Queue/exchange declarations |
| `AmqpInfrastructureConfig.java` | etl | Listener container + template |
| `RabbitExecutionInboundFlowConfig.java` | etl | AMQP inbound → worker |
| `EtlMaterializationServiceImpl.java` | etl | Handler dispatch |
| `MartRefreshService.java` | etl | Mart UPSERT trigger |
| `OrderPnlMartJdbcRepository.java` | etl | mart_order_pnl SQL |
| `ProductPnlMartJdbcRepository.java` | etl | mart_product_pnl SQL |
| `RawBatchInsertJdbcRepository.java` | etl | Idempotent batch insert |
| `RawTableSchemaJdbcRepository.java` | etl | DDL for raw_* tables |
| `MarketplaceEvent.java` | etl | Event enum + dependencies |
| `RawTableNames.java` | etl | Raw table name constants |
