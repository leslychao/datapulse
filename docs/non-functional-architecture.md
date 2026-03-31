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
| `@PreAuthorize` на уровне методов с SpEL                | Декларативная авторизация, workspace-scoped проверки  |
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


## NFR-2: Аудит и бизнес-алертинг

Детали реализации — [Audit & Alerting](modules/audit-alerting.md).

### Принципы аудита

- 5 обязательных audit domains: ETL audit, credentials audit, price change journal, user action audit, data provenance.
- Обязательные поля: `timestamp`, `actor` (user или system), `action_type`, `target_entity`, `outcome`.
- Audit records immutable: UPDATE и DELETE запрещены.
- Retention: не менее 12 месяцев.
- Audit write — best-effort: failure to write audit не прерывает основную операцию.

### Принципы бизнес-алертинга

- Business-level alerts (stale data, anomalies, mismatches) живут в PostgreSQL (`alert_rule`, `alert_event`), доступны через REST API и WebSocket.
- Alert events имеют lifecycle: OPEN → ACKNOWLEDGED → RESOLVED / AUTO_RESOLVED.
- Alerts с `blocks_automation = true` блокируют pricing pipeline per connection.
- Scheduled checkers + event-driven alerts (из Execution, Pricing, Promotions).

## NFR-3: Observability

### Correlation context

Каждый критический flow обязан содержать:

- `correlation_id`
- `workspace_id` / `connection_id`
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

### ClickHouse unavailability handling

ClickHouse используется только для analytics (read-only для бизнес-логики). При недоступности ClickHouse:

| Сценарий | Последствия | Митигация |
|----------|-------------|-----------|
| ClickHouse down во время materialization | ETL canonical writes (PostgreSQL) успешны. ClickHouse INSERT fails | Materialization помечается как failed в `job_execution.error_details`. Job завершается как `COMPLETED_WITH_ERRORS`. Daily full re-materialization восполнит gap |
| ClickHouse down во время pricing signal assembly | Derived signals (avg_commission, logistics, ad_cost_ratio) недоступны | Pricing run завершается как FAILED. Alert: «ClickHouse unavailable for signal assembly». Canonical data (COGS, current_price) доступна из PostgreSQL — partial pricing невозможен (consistency guarantee) |
| ClickHouse down во время Seller Operations grid read | ClickHouse-enriched columns (revenue_30d, velocity, days_of_cover) недоступны | Grid возвращает PostgreSQL-часть данных. ClickHouse columns → NULL / «N/A». UI показывает warning banner «Аналитические данные временно недоступны» |
| Prolonged ClickHouse outage | Analytics stale. Pricing blocked (derived signals required) | Alert: CRITICAL. Recovery: restart ClickHouse → daily re-materialization восстанавливает consistency |

**Инвариант:** ClickHouse failure никогда не приводит к потере данных. Canonical layer (PostgreSQL) — source of truth. ClickHouse всегда восстановим через re-materialization.

### Решения по ранее открытым вопросам

| Вопрос | Решение | Фаза |
|--------|---------|------|
| Multi-instance deployment | Phase A-E: single instance per worker. Phase G: PostgreSQL advisory locks для leader election; `FOR UPDATE SKIP LOCKED` уже обеспечивает concurrency для outbox/claims. Partitioning не требуется до Phase G | A (single), G (multi) |
| Disaster recovery | См. [§Disaster Recovery Plan](#disaster-recovery-plan) ниже | A |
| Circuit breaker policy | Через connection health-check ([Integration](modules/integration.md) §Health-check): 3 consecutive failures → AUTH_FAILED → syncs paused, `ConnectionHealthDegraded` event. Recovery: successful health-check → ACTIVE. Per-connection, не global | A |
| Approval timeout | `price_policy.approval_timeout_hours` (default: 72h). Scheduled job (hourly): PENDING_APPROVAL → EXPIRED. Configurable per policy | D |

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

### Решения по ранее открытым вопросам

| Вопрос | Решение | Фаза |
|--------|---------|------|
| SLA/SLO | Определяются после Phase B на основе эмпирических данных. Target latency budget (выше) — engineering guidance, не SLA | B+ |
| Data retention | Raw S3: finance 12 мес, state keep_count=3, flow 6 мес ([ETL Pipeline](modules/etl-pipeline.md) §Retention). ClickHouse: facts бессрочно (аналитика), marts пересчитываемы. PostgreSQL: audit_log ≥12 мес, alert_event 6 мес, price_decision/action бессрочно | A |
| Connection pool sizing | PostgreSQL: HikariCP, `maximumPoolSize = 10` per runtime (datapulse-api, ingest-worker, pricing-worker, executor-worker). ClickHouse: JDBC pool, `maximumPoolSize = 5` per runtime. Configurable через `@ConfigurationProperties`. Мониторинг: `hikaricp_connections_*` Micrometer metrics | A |

## NFR-6b: Redis — scope и ограничения

**Инвариант:** Redis — **только technical fast layer**. Хранение canonical truth, action lifecycle, decision truth, business state в Redis запрещено (см. [Project Vision](project-vision-and-scope.md), Implementation constraints).

### Разрешённые use cases

| Use case | Описание | Fallback при недоступности Redis |
|----------|----------|----------------------------------|
| Distributed locks | Leader election при multi-instance workers (Phase G) | PostgreSQL advisory locks (single-instance fallback) |
| Rate limit counters | Per-provider sliding window rate limit counters (Phase A) | In-memory rate limiter (per-instance, conservative) |
| Session cache | Keycloak session cache acceleration (optional) | Direct Keycloak token validation (slower, still functional) |

### Запрещённые use cases

| Anti-pattern | Почему запрещён |
|-------------|-----------------|
| Retry truth | Retry count, next_attempt_at — в PostgreSQL (outbox_event, price_action) |
| Action state | `price_action.status` — только PostgreSQL |
| Decision cache | Pricing decisions — PostgreSQL, не Redis |
| Canonical data cache | `canonical_price_current` — PostgreSQL. Redis cache layer создаёт stale risk |
| Queue / priority ordering | RabbitMQ для async dispatch, не Redis sorted sets |

### Phase A deployment

Redis включён в Docker Compose, но **не обязателен** для Phase A. Единственный use case Phase A — rate limit counters. При отсутствии Redis → fallback на in-memory rate limiter (conservative, per-instance).

Phase G: distributed locks для multi-instance deployment.

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

Детали реализации (STOMP destinations, message flow, authentication, reconnection, scalability) — [Audit & Alerting](modules/audit-alerting.md) §Notification.

| Требование | Обоснование |
|------------|-------------|
| Канал доставки — WebSocket (STOMP) в UI | Оператор получает алерты в реальном времени при работе в интерфейсе |
| Без email / Telegram / push | Сознательное ограничение: оператор должен быть онлайн |
| Reconnection fallback | При потере WebSocket — exponential backoff reconnect + REST API для sync текущего состояния |

## Infrastructure: Docker Compose (development)

```yaml
services:
  postgres:
    image: postgres:15
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: datapulse
      POSTGRES_USER: datapulse
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data

  clickhouse:
    image: clickhouse/clickhouse-server:24.3
    ports: ["8123:8123", "9000:9000"]
    volumes:
      - clickhouse-data:/var/lib/clickhouse

  rabbitmq:
    image: rabbitmq:3-management
    ports: ["5672:5672", "15672:15672"]
    environment:
      RABBITMQ_DEFAULT_USER: datapulse
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD}

  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    ports: ["9010:9000", "9011:9001"]
    environment:
      MINIO_ROOT_USER: datapulse
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    volumes:
      - minio-data:/data

  keycloak:
    image: quay.io/keycloak/keycloak:24.0
    command: start-dev --import-realm
    ports: ["8080:8080"]
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
      KC_DB_USERNAME: datapulse
      KC_DB_PASSWORD: ${POSTGRES_PASSWORD}

  vault:
    image: hashicorp/vault:1.15
    ports: ["8200:8200"]
    environment:
      VAULT_DEV_ROOT_TOKEN_ID: dev-token
      VAULT_DEV_LISTEN_ADDRESS: "0.0.0.0:8200"
    cap_add: [IPC_LOCK]

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

volumes:
  postgres-data:
  clickhouse-data:
  minio-data:
```

**Порядок запуска:** postgres → keycloak → vault → rabbitmq → minio → redis → application services.

**Application services** (запускаются отдельно через IDE / CLI):

| Service | Main class | Port | Profile |
|---------|-----------|------|---------|
| `datapulse-api` | `DatapulseApiApplication` | 8081 | `local` |
| `datapulse-ingest-worker` | `IngestWorkerApplication` | 8082 | `local` |
| `datapulse-pricing-worker` | `PricingWorkerApplication` | 8083 | `local` |
| `datapulse-executor-worker` | `ExecutorWorkerApplication` | 8084 | `local` |

## Disaster Recovery Plan

### Цели

| Параметр | Значение | Обоснование |
|----------|----------|-------------|
| RPO (Recovery Point Objective) | 5 минут | WAL archiving обеспечивает continuous backup |
| RTO (Recovery Time Objective) | 30 минут | Время восстановления PostgreSQL из WAL + base backup |

### Компонент: PostgreSQL (source of truth)

| Механизм | Описание |
|----------|----------|
| Base backup | `pg_basebackup` daily, хранение 7 дней |
| WAL archiving | Continuous archiving в S3-compatible storage (отдельный от MinIO данных). RPO = 5 мин |
| Point-in-time recovery | `recovery_target_time` для восстановления на произвольный момент |
| Проверка бэкапов | Еженедельный automated restore test в изолированную среду |

**Процедура восстановления:**
1. Остановить все application services (api, workers).
2. Восстановить PostgreSQL из base backup + replay WAL до target time.
3. Запустить PostgreSQL, убедиться в целостности (`pg_isready`, sample queries).
4. Запустить application services.
5. Проверить: outbox backlog обработается автоматически (at-least-once delivery). In-flight `price_action` со статусом `EXECUTING` → stuck-state detector переведёт в `RECONCILIATION_PENDING` (стандартная процедура).

### Компонент: ClickHouse (analytics, derived)

ClickHouse не является source of truth. Полностью восстановим из canonical layer (PostgreSQL).

| Сценарий | Процедура |
|----------|-----------|
| Полная потеря данных | Re-create таблицы (DDL). Запустить full re-materialization из canonical layer |
| Частичная потеря (corruption) | DROP повреждённых таблиц. Re-materialization |
| Время восстановления | Зависит от объёма данных. Для 1M finance entries ≈ 5–15 мин |

### Компонент: S3 / MinIO (raw layer)

| Механизм | Описание |
|----------|----------|
| Bucket versioning | Enabled. Защита от accidental delete |
| Cross-region replication | Не требуется (Phase A-E: single-region). Phase G: optional |
| Потеря raw data | Допустима при наличии canonical. Raw используется для аудита и replay. Re-fetch возможен через повторный `FULL_SYNC` |

### Компонент: HashiCorp Vault (secrets)

| Механизм | Описание |
|----------|----------|
| Backup | `vault operator raft snapshot` daily |
| Восстановление | Restore snapshot → unseal → verify secret paths |
| Полная потеря | Пользователи заново вводят marketplace credentials через UI (connection re-validation). Болезненно, но не catastrophic |

### Компонент: RabbitMQ (messaging)

RabbitMQ — транспорт, не source of truth. Благодаря transactional outbox потеря RabbitMQ messages не приводит к потере бизнес-состояния.

| Сценарий | Процедура |
|----------|-----------|
| Полная потеря очередей | Restart RabbitMQ → очереди и exchanges re-declare автоматически (Spring Boot auto-configuration). Outbox publisher переотправит pending events |
| Split-brain | Не актуально: single-node deployment (Phase A-E). Phase G: mirror/quorum queues |

### Компонент: Keycloak (identity)

| Механизм | Описание |
|----------|----------|
| Backup | Database backup (Keycloak хранит данные в PostgreSQL). Включён в общий PostgreSQL backup если shared instance, или отдельный backup если dedicated |
| Восстановление | Restore PostgreSQL → restart Keycloak |
| Полная потеря | Re-create realm. Users потеряют session (re-login). Workspace membership не теряется — хранится в Datapulse PostgreSQL |

### Компонент: Redis (cache)

Redis — ephemeral cache. Потеря данных допустима: cache warm-up происходит автоматически.

### In-flight операции после восстановления

| Состояние до DR | Поведение после восстановления |
|-----------------|-------------------------------|
| `price_action` в `EXECUTING` | Stuck-state detector (scheduled job) → `RECONCILIATION_PENDING` → deferred reconciliation проверяет фактическое состояние на маркетплейсе |
| `price_action` в `SCHEDULED` | Scheduler подхватит при запуске — execution продолжится штатно |
| `price_action` в `PENDING_APPROVAL` | Не затронуто — ожидание ручного решения, timeout продолжает тикать |
| `job_execution` в `RUNNING` | `RUNNING` без heartbeat → stuck. Можно restart как новый job (тот же scope). Idempotency canonical layer (UPSERT) предотвращает дубли |
| Outbox events `PENDING` | Outbox publisher при запуске переотправит все `PENDING` events. Idempotent consumers обработают корректно |

## Schema Evolution и миграция

### Инструмент

Liquibase — механизм миграции схем PostgreSQL. ClickHouse — custom `ClickHouseMigrationRunner` (см. §Migration strategy).

### Правила миграций

| Правило | Обоснование |
|---------|-------------|
| Forward-only | Liquibase `update` только вперёд. `rollback` — только для reversible changesets, для остальных — compensating migration |
| Backward-compatible migrations | Каждая миграция должна быть совместима с предыдущей версией приложения (expand-contract pattern). Это обеспечивает zero-downtime deployment |
| Один SQL-файл = одна логическая операция | Атомарность миграции. Не смешивать DDL разных модулей |
| Naming convention | `{NNNN}-{short-description}.sql` (пример: `0012-etl-add-attribution-level.sql`). Подробности — в §Migration strategy |
| Тестирование | Каждая миграция проверяется на test dataset перед production apply |

### Expand-contract pattern

Для breaking changes (переименование колонки, изменение типа, удаление поля) используется трёхшаговый процесс:

1. **Expand** — добавить новую колонку/таблицу, не трогая старую. Deploy приложение, которое пишет в обе.
2. **Migrate data** — backfill новой колонки из старой.
3. **Contract** — после подтверждения, что новая колонка используется и старая не нужна, удалить старую.

### Rollback strategy

| Сценарий | Процедура |
|----------|-----------|
| Миграция failed (не применилась) | Liquibase автоматически останавливается. Исправить SQL changeset, повторить `update` |
| Миграция applied, но данные некорректны | Compensating migration — новый SQL-файл, исправляющий данные |
| Миграция applied, нужно полностью откатить | Compensating migration + откат приложения на предыдущую версию (expand-contract обеспечивает совместимость) |
| Catastrophic (миграция испортила production) | Point-in-time recovery PostgreSQL (см. [§Disaster Recovery Plan](#disaster-recovery-plan)) |

### ClickHouse миграции

ClickHouse DDL управляется custom `ClickHouseMigrationRunner` (см. §Migration strategy). Специфика:
- `ALTER TABLE` в ClickHouse имеет ограничения (нет транзакций).
- При breaking change: DROP + re-create таблицы → full re-materialization из canonical.
- Это допустимо, т.к. ClickHouse — derived store.

## Maven module structure

Modular monolith — один Git repo, один root `pom.xml`, multi-module Maven project. Каждый runtime (api, ingest-worker, pricing-worker, executor-worker) — отдельный Spring Boot executable jar. Общий код — shared library modules.

```
datapulse/
├── pom.xml                          (parent POM, dependency management, plugin management)
│
├── datapulse-common/                (shared utilities, base classes, no Spring context)
│   ├── pom.xml
│   └── src/main/java/com/datapulse/common/
│       ├── exception/               AppException, NotFoundException, BadRequestException, MessageCodes
│       ├── model/                   BaseEntity, enums shared across modules
│       └── util/                    SlugUtils, BigDecimalUtils, etc.
│
├── datapulse-domain/                (domain model: entities, records, events — no infrastructure)
│   ├── pom.xml                      depends on: datapulse-common
│   └── src/main/java/com/datapulse/domain/
│       ├── tenancy/                 Tenant, Workspace, AppUser, WorkspaceMember, WorkspaceInvitation
│       ├── integration/             MarketplaceConnection, SecretReference, SyncState
│       ├── etl/                     JobExecution, JobItem, canonical entities
│       ├── pricing/                 PricePolicy, PriceDecision, PricingRun, ManualPriceLock
│       ├── execution/               PriceAction, PriceActionAttempt, DeferredAction, SimulatedOfferState
│       ├── promotions/              PromoPolicy, PromoEvaluation, PromoDecision, PromoAction
│       ├── operations/              SavedView, WorkingQueueDefinition, WorkingQueueAssignment
│       ├── audit/                   AuditLog, AlertRule, AlertEvent, UserNotification
│       └── outbox/                  OutboxEvent
│
├── datapulse-infrastructure/        (repositories, adapters, outbox, external integrations)
│   ├── pom.xml                      depends on: datapulse-domain
│   └── src/main/java/com/datapulse/infrastructure/
│       ├── persistence/             JPA repos, JDBC repos (analytics, grid read model)
│       ├── marketplace/             WB/Ozon API adapters (WebClient)
│       ├── vault/                   VaultCredentialStore, credential caching
│       ├── s3/                      S3RawStorage (MinIO adapter)
│       ├── clickhouse/              ClickHouse JDBC repos (materialization, analytics queries)
│       ├── outbox/                  OutboxPoller, OutboxPublisher
│       └── rabbitmq/                RabbitMQ producers, topology configuration
│
├── datapulse-service/               (business logic: services, pipelines, event listeners)
│   ├── pom.xml                      depends on: datapulse-domain, datapulse-infrastructure
│   └── src/main/java/com/datapulse/service/
│       ├── tenancy/                 TenantService, WorkspaceService, InvitationService
│       ├── integration/             ConnectionService, SyncSchedulerService
│       ├── etl/                     IngestOrchestrator, adapters per domain, normalizers, canonical writers
│       ├── pricing/                 PricingPipeline, strategies, constraints, guards, signal assembly
│       ├── execution/               ActionLifecycleService, RetryService, ReconciliationService
│       ├── promotions/              PromoEvaluationPipeline, PromoDecisionService, PromoActionService
│       ├── operations/              GridService, SavedViewService, WorkingQueueService
│       └── audit/                   AuditService, AlertCheckerService, NotificationService
│
├── datapulse-api/                   (REST controllers, WebSocket, security config → executable jar)
│   ├── pom.xml                      depends on: datapulse-service
│   └── src/main/java/com/datapulse/api/
│       ├── DatapulseApiApplication.java
│       ├── config/                  SecurityConfig, WebSocketConfig, MapperConfig, CorsConfig
│       ├── controller/              REST controllers per module
│       └── websocket/               STOMP handlers, notification push
│
├── datapulse-ingest-worker/         (ETL worker → executable jar)
│   ├── pom.xml                      depends on: datapulse-service
│   └── src/main/java/com/datapulse/worker/ingest/
│       ├── IngestWorkerApplication.java
│       ├── config/                  RabbitMQ listener config, outbox poller config
│       └── listener/                ETL message consumers
│
├── datapulse-pricing-worker/        (pricing + promo evaluation worker → executable jar)
│   ├── pom.xml                      depends on: datapulse-service
│   └── src/main/java/com/datapulse/worker/pricing/
│       ├── PricingWorkerApplication.java
│       ├── config/                  RabbitMQ listener config, outbox poller config
│       └── listener/                Pricing run consumer, promo evaluation consumer
│
├── datapulse-executor-worker/       (execution worker → executable jar)
│   ├── pom.xml                      depends on: datapulse-service
│   └── src/main/java/com/datapulse/worker/executor/
│       ├── ExecutorWorkerApplication.java
│       ├── config/                  RabbitMQ listener config, outbox poller config
│       └── listener/                Price action consumer, promo action consumer
│
└── docs/                            (architecture documentation)
```

**Принципы:**

| Принцип | Описание |
|---------|----------|
| Dependency direction | `common` ← `domain` ← `infrastructure` ← `service` ← `api/workers`. Строго однонаправленные зависимости, нижние слои не знают о верхних |
| Domain purity | `datapulse-domain` — чистые Java классы (entities, records, enums, events). Без Spring-аннотаций кроме JPA (`@Entity`, `@Column`, etc.) |
| Shared infrastructure | `datapulse-infrastructure` — один модуль (не per-module infra). Обоснование: modular monolith, единый persistence context, shared DB |
| Executable modules | 4 Spring Boot apps (api, 3 workers). Каждый импортирует только нужные сервисы через `@ComponentScan` / `@Import` / `@Profile` |
| No circular deps | Maven enforced — circular dependency = build failure |

**Workers vs API — component scan scope:**

Каждый worker подключает только необходимые сервисы:

| Runtime | Сканирует пакеты |
|---------|------------------|
| `datapulse-api` | `com.datapulse.service.*`, `com.datapulse.infrastructure.*`, `com.datapulse.api.*` |
| `datapulse-ingest-worker` | `com.datapulse.service.etl`, `com.datapulse.service.integration`, `com.datapulse.infrastructure.*` |
| `datapulse-pricing-worker` | `com.datapulse.service.pricing`, `com.datapulse.service.promotions`, `com.datapulse.infrastructure.*` |
| `datapulse-executor-worker` | `com.datapulse.service.execution`, `com.datapulse.service.promotions`, `com.datapulse.infrastructure.*` |

Worker-ы не подключают REST controllers и WebSocket — эти компоненты только в `datapulse-api`.

## Migration strategy

### PostgreSQL — Liquibase

| Аспект | Описание |
|--------|----------|
| Библиотека | Liquibase (Spring Boot Starter) |
| Формат changesets | SQL (не XML/YAML — SQL даёт полный контроль и transparent DDL review) |
| Changelog structure | `db/changelog/db.changelog-master.yaml` → includes per-phase changelogs |
| Naming | `db/changelog/changes/NNNN-short-description.sql` (sequential numbering) |
| Execution | При старте `datapulse-api` (primary migration runner). Workers — `liquibase.enabled=false` (не запускают миграции) |
| Environments | `local` — auto-migrate on startup. `staging/prod` — CLI execution перед deploy |
| Rollback | Каждый changeset включает `--rollback` section для reversible changes. Irreversible changes (DROP, data migration) — explicit `rollbackCommand: NOT_POSSIBLE` |

**Changelog layout:**

```
src/main/resources/db/changelog/
├── db.changelog-master.yaml
├── changes/
│   ├── 0001-tenancy-iam-tables.sql
│   ├── 0002-integration-tables.sql
│   ├── 0003-etl-canonical-tables.sql
│   ├── 0004-outbox-event.sql
│   ├── 0005-pricing-tables.sql
│   ├── 0006-execution-tables.sql
│   ├── 0007-promotions-tables.sql
│   ├── 0008-seller-operations-tables.sql
│   ├── 0009-audit-alerting-tables.sql
│   ├── 0010-seed-default-alert-rules.sql
│   └── ...
```

### ClickHouse — versioned SQL scripts

ClickHouse не поддерживается Liquibase. Стратегия:

| Аспект | Описание |
|--------|----------|
| Формат | Plain SQL scripts с sequential numbering |
| Execution | Custom `ClickHouseMigrationRunner` (`@Component`, Phase A). При старте `datapulse-ingest-worker` — единственный runner |
| State tracking | Таблица `_schema_version` в ClickHouse: `(version UInt32, script_name String, applied_at DateTime, checksum String)` |
| Idempotency | `IF NOT EXISTS` в DDL. Runner проверяет `_schema_version` — already applied scripts пропускаются |
| Directory | `db/clickhouse/NNNN-short-description.sql` |

```
src/main/resources/db/clickhouse/
├── 0001-create-dimensions.sql
├── 0002-create-fact-tables.sql
├── 0003-create-materialized-views.sql
├── 0004-create-marts.sql
└── ...
```

**Инвариант:** ClickHouse schema — append-only (no ALTER TABLE for column renames; add new columns, deprecate old). DROP + recreate — только для materialized views (stateless, re-populatable).

## Связанные документы

- [Data Model](data-model.md) — shared data model overview, инварианты
- [Audit & Alerting](modules/audit-alerting.md) — audit, alerting, notification details
- [Execution](modules/execution.md) — retry, outbox, CAS details
- [Integration](modules/integration.md) — rate limits, provider constraints
- [ETL Pipeline](modules/etl-pipeline.md) — pipeline integrity

