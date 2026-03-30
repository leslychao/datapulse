# Datapulse — Нефункциональная архитектура

## NFR-1: Безопасность

### Аутентификация


| Требование                                     | Обоснование                                          |
| ---------------------------------------------- | ---------------------------------------------------- |
| OAuth2 Resource Server (JWT от Keycloak)       | Единый identity provider; stateless token validation |
| Edge proxy (oauth2-proxy) для внешнего трафика | Дополнительный слой защиты на границе                |


### Авторизация


| Требование                                              | Обоснование                                           |
| ------------------------------------------------------- | ----------------------------------------------------- |
| `@PreAuthorize` на уровне методов с SpEL                | Декларативная авторизация, account-scoped проверки    |
| RBAC: OWNER, ADMIN, PRICING_MANAGER, OPERATOR, ANALYST, VIEWER | Минимально достаточный набор ролей; соответствует enum `workspace_member.role` |
| Multi-tenant access isolation                           | `@PreAuthorize("@accessService.canRead(#connectionId)")` — проверка принадлежности connection к workspace пользователя |


### Матрица разрешений


| Capability                              | viewer | analyst | operator | pricing manager | admin |
| --------------------------------------- | ------ | ------- | -------- | --------------- | ----- |
| Просмотр данных (P&L, остатки, заказы)  | ✓      | ✓       | ✓        | ✓               | ✓     |
| Saved views (создание / редактирование) | —      | ✓       | ✓        | ✓               | ✓     |
| Working queues (просмотр / assignment)  | —      | —       | ✓        | ✓               | ✓     |
| Manual price lock / hold                | —      | —       | ✓        | ✓               | ✓     |
| Pricing policy configuration            | —      | —       | —        | ✓               | ✓     |
| Manual approval / reject price actions  | —      | —       | —        | ✓               | ✓     |
| Enable auto-execution                   | —      | —       | —        | ✓               | ✓     |
| Marketplace account management          | —      | —       | —        | —               | ✓     |
| User / role management                  | —      | —       | —        | —               | ✓     |
| Workspace configuration                 | —      | —       | —        | —               | ✓     |


### Управление секретами


| Требование                                         | Обоснование                           |
| -------------------------------------------------- | ------------------------------------- |
| API-ключи маркетплейсов хранятся в HashiCorp Vault | Разделение metadata и secret material |
| Маскирование credentials в логах                   | Предотвращение утечки через logs      |
| Raw payload access — restricted и аудитируемый     | Минимизация attack surface            |


## NFR-2: Аудит

### Обязательные audit domains


| Domain               | Требование                                                                       |
| -------------------- | -------------------------------------------------------------------------------- |
| ETL audit            | Каждая загрузка: execution state, source, timing, outcome, error details         |
| Credentials audit    | Все попытки доступа к credentials: кто, когда, какой account, результат          |
| Price change journal | Durable history ценовых решений: inputs, constraints, explanation, outcome       |
| User action audit    | Действия пользователей: login, configuration changes, manual approvals/overrides |
| Data provenance      | Каждая каноническая запись прослеживаема до raw source                           |


### Требования к audit records

- Обязательные поля: `timestamp`, `actor` (user или system), `action_type`, `target_entity`, `outcome`.
- Audit records immutable: update и delete запрещены.
- Retention: не менее 12 месяцев (конкретный срок — в deployment configuration).

## NFR-3: Observability

### Correlation context

Каждый критический flow обязан содержать:

- `correlation_id`
- `workspace_id` / `account_id`
- `entity_id` / `action_id` / `job_id`
- `source` / `provider`
- `capability`
- `outcome_class`
- `timing` (duration_ms)
- `retry_count`
- `execution_mode` (live / simulated)

### Обязательные метрики


| Группа      | Метрики                                                              |
| ----------- | -------------------------------------------------------------------- |
| Integration | Call rates, failure rates, provider throttling rates                 |
| Sync        | Sync freshness per account/marketplace, sync duration                |
| Pipeline    | Queue lag, outbox backlog, outbox error rate                         |
| Pricing     | Decision counts, skip counts, guard hit rates per guard type         |
| Execution   | Action success/failure/reconciliation rates, attempt counts          |
| Analytics   | Mart freshness, anomaly counts, reconciliation residual distribution |
| Simulation  | Simulated vs live action counts, parity deviation                    |
| Operations  | Latency операционных экранов (grid, views)                           |


### Логирование

- Structured logging через SLF4J (`@Slf4j`).
- Формат: key=value стиль.
- `log.info` — start/end операций, ключевые бизнес-события.
- `log.debug` — guard skips, пропуск дублирующей работы.
- `log.warn` — retry exhaustion, backoff, skip-сценарии.
- `log.error` — терминальные ошибки, с exception.

### Стек observability

| Компонент | Технология | Назначение |
|-----------|------------|------------|
| Метрики | Prometheus / Micrometer | Сбор и хранение метрик всех runtime-компонентов |
| Дашборды | Grafana | Визуализация метрик, алерты по threshold-ам |
| Distributed tracing | Jaeger | Трассировка flows через outbox → RabbitMQ → worker |
| Агрегация логов | Loki | Централизованный сбор логов из всех runtime entrypoints |
| Alerting | Grafana Alerting | Alert rules для critical metric thresholds |

## NFR-4: Устойчивость и восстановление


| Требование                            | Обоснование                                                                                              |
| ------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| DB-first guarantee                    | Критическое состояние в PostgreSQL до внешних вызовов; потеря in-flight message не теряет business state |
| Transactional outbox                  | At-least-once delivery; idempotent consumers; ACK после durable DB commit                                |
| CAS guards на state transitions       | Single-winner semantics; race conditions на уровне БД                                                    |
| Configurable retry с backoff          | Max attempts, min/max backoff, exponential growth; retry truth в PostgreSQL                              |
| Terminal failure → downstream blocked | После max attempts → FAILED; downstream останавливается                                                  |
| Lane isolation                        | Сбой одного маркетплейса не блокирует другой                                                             |
| Reconciliation safety                 | Uncertain outcomes → RECONCILIATION_PENDING, не SUCCEEDED                                                |


Детали реализации (state machine, outbox schema, CAS SQL, retry flow) — [Execution](modules/execution.md).

### Открытые вопросы

- Multi-instance deployment: partitioning, leader election, health checks.
- Disaster recovery: backup strategy PostgreSQL, RPO/RTO.
- Circuit breaker policy: provider failure thresholds, recovery strategy.
- Approval timeout duration (EXPIRED state) для price actions.

## NFR-5: Консистентность данных

### Pipeline integrity


| Требование                                                   | Обоснование                                                   |
| ------------------------------------------------------------ | ------------------------------------------------------------- |
| Обязательный путь: raw → normalized → canonical → analytics  | Пропуск стадий запрещён                                       |
| Decision-grade reads только из канонической модели           | Pricing decisions на raw/normalized — architectural violation |
| PostgreSQL для business truth; ClickHouse — только аналитика | Consistency guarantee только в PostgreSQL                     |


### Изоляция truth


| Требование                                                     | Обоснование                                                                    |
| -------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| Каноническая модель продавца и external observations разделены | Observation data с provenance/freshness/confidence; не смешивается с каноникой |
| Simulated truth изолирована от авторитетной                    | Simulated mode не мутирует каноническую модель                                 |


### Идемпотентность


| Уровень         | Механизм                                                          |
| --------------- | ----------------------------------------------------------------- |
| Raw ingest      | SHA-256 record keys + `ON CONFLICT DO NOTHING`                    |
| Materialization | UPSERT с `IS DISTINCT FROM` (no-churn)                            |
| Execution       | Idempotent action attempts; повторная попытка не дублирует эффект |


## NFR-6: Производительность

### Загрузка данных


| Требование                          | Обоснование                                                   |
| ----------------------------------- | ------------------------------------------------------------- |
| Streaming ingest для large payloads | Загрузка не держит весь payload в памяти                      |
| Batch processing                    | Batch insert (default: 500 records); message-per-SKU запрещён |


### Hot tables


| Требование                                         | Обоснование                                |
| -------------------------------------------------- | ------------------------------------------ |
| `FOR UPDATE SKIP LOCKED` для конкурентного доступа | Worker claim без блокировки других workers |
| Composite indexes на claim/filter columns          | Предотвращение full table scans            |


### Операционные экраны


| Требование                                 | Обоснование                                                     |
| ------------------------------------------ | --------------------------------------------------------------- |
| Server-side filtering, sorting, pagination | Клиент не загружает полный dataset                              |
| Dedicated read models                      | Операционные screens не читают из write-оптимизированных таблиц |
| Dynamic sorting через whitelist            | DTO field → SQL column mapping; SQL injection prevention        |


### Запрещённые anti-patterns


| Anti-pattern                   | Причина                                                     |
| ------------------------------ | ----------------------------------------------------------- |
| Wrong-store reads              | Аналитика из PostgreSQL вместо ClickHouse — performance bug |
| N+1 queries                    | Lazy loading без batch fetch                                |
| Full table scans на hot tables | Отсутствие index на claim/filter columns                    |
| Client-side pagination         | Загрузка полного dataset на клиент                          |


### Target latency budget (Phase C)

| Сегмент | Target | Обоснование |
|---------|--------|-------------|
| API sync → raw (S3) | 1-10 мин | Зависит от rate limits МП |
| Raw → canonical | 1-5 мин | Batch 500, streaming parse |
| Canonical → ClickHouse | 1-3 мин | Batch INSERT |
| ClickHouse → pricing run | < 1 мин | Post-sync trigger |
| Pricing run (1000 SKU) | < 30 сек | Batch signal assembly |
| Action → execution | 1-7 сек | Outbox poll (1s) + provider call |
| Execution → reconciliation | 30s-10 мин | Deferred reconciliation |
| **Total: data change → confirmed price update** | **~15-30 мин** | При штатной работе |

Это **не SLA** — это target для engineering decisions. SLA определяется позже на основе эмпирических данных после Phase A-B.

### Открытые вопросы

- SLA/SLO: конкретные значения после эмпирических замеров (Phase A-B).
- Data retention: политика по raw artifacts (S3), historical facts (ClickHouse), operational state (PostgreSQL).
- Connection pool sizing: PostgreSQL, ClickHouse.

## NFR-7: Операбельность

### Конфигурация

- Spring profiles: `@Profile("local")` для stub/mock, `@Profile("!local")` для production.
- Secrets через Vault; environment-specific configuration через environment variables.
- Rate limits, retry parameters, batch sizes — через `@ConfigurationProperties`.

### API error contract

Все REST API ошибки — стандартизированный формат:


| Поле         | Описание                       |
| ------------ | ------------------------------ |
| `timestamp`  | ISO 8601                       |
| `status`     | HTTP status code               |
| `error`      | HTTP status reason phrase      |
| `message`    | Human-readable описание        |
| `messageKey` | Machine-readable ключ для i18n |
| `path`       | Request path                   |


Validation errors (`400`) содержат `fieldErrors[]`: `field`, `messageKey`, `rejectedValue`.

### Health & Readiness

- Health endpoint для каждого runtime entrypoint.
- Readiness probe: доступность PostgreSQL, RabbitMQ, Vault.
- Liveness probe: приложение не зависло.

## NFR-8: Доставка уведомлений

| Требование | Обоснование |
|------------|-------------|
| Канал доставки — WebSocket (STOMP) в UI | Оператор получает алерты в реальном времени при работе в интерфейсе |
| Без email / Telegram / push | Сознательное ограничение: оператор должен быть онлайн |
| Reconnection fallback | При потере WebSocket — exponential backoff reconnect + REST API для sync текущего состояния |

## Связанные документы

- [Data Model](data-model.md) — shared data model overview, инварианты
- [Execution](modules/execution.md) — retry, outbox, CAS details
- [Integration](modules/integration.md) — rate limits, provider constraints
- [ETL Pipeline](modules/etl-pipeline.md) — pipeline integrity

