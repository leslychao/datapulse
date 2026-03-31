# Datapulse — Runbook

## Runtime-компоненты

| Компонент | Технология | Роль |
|-----------|------------|------|
| `datapulse-api` | Spring Boot | REST API, WebSocket, IAM, operational screens |
| `datapulse-ingest-worker` | Spring Boot | Provider sync, raw capture, normalization, canonical ingestion |
| `datapulse-pricing-worker` | Spring Boot | Eligibility, signals, constraints, decisions, explanation, action scheduling |
| `datapulse-executor-worker` | Spring Boot | Action execution, attempts, retries, reconciliation |
| PostgreSQL | — | Авторитетный store: всё persistent-состояние |
| ClickHouse | — | Analytical store: facts, marts, historical snapshots |
| RabbitMQ | — | Async transport: диспетчеризация задач, delayed retry |
| Redis | — | Distributed locks, short-lived caches |
| S3-compatible | — | Raw payloads, replay inputs, evidence artifacts |
| Keycloak | — | OAuth2/OIDC аутентификация |
| HashiCorp Vault | — | Учётные данные API маркетплейсов |

## Что мониторить

### Обязательные метрики

| Метрика | Описание | Почему критично |
|---------|----------|-----------------|
| Integration call rates/failures | Частота и ошибки вызовов API маркетплейсов | Ломающиеся endpoints — самый вероятный сбой |
| Provider throttling rates | Частота 429/throttle ответов | Влияет на свежесть данных |
| Sync freshness | Время последней успешной синхронизации per account/marketplace | Stale data блокирует pricing automation |
| Queue lag | Задержка обработки RabbitMQ | Индикатор проблем worker |
| Outbox backlog | Количество PENDING/ERROR outbox-сообщений | Индикатор проблем messaging |
| Decision counts / skip counts | Сколько pricing решений создано/пропущено | Pricing pipeline health |
| Guard hit rates | Как часто guards блокируют actions | Stale data или неправильные constraints |
| Action success/failure/reconciliation rates | Результативность price actions | Основной KPI execution |
| Mart freshness | Время последнего обновления витрин | Пользователь видит устаревшие данные |
| Anomaly counts | Количество выявленных аномалий | Data quality health |
| Latency operational screens | Время отклика grid/journals | UX и серверная нагрузка |

### Таблицы PostgreSQL для мониторинга

| Таблица | Индикатор проблемы | Запрос |
|---------|-------------------|--------|
| `outbox_event` | Backlog PENDING/ERROR | `SELECT status, COUNT(*) FROM outbox_event GROUP BY status` |
| `job_execution` | Длительные IN_PROGRESS / зависшие RETRY_SCHEDULED | `SELECT * FROM job_execution WHERE (status = 'IN_PROGRESS' AND started_at < now() - interval '2 hours') OR (status = 'RETRY_SCHEDULED' AND (checkpoint->>'last_retry_at')::timestamptz < now() - interval '1 hour')` |
| `job_item` | Накопление FAILED | `SELECT * FROM job_item WHERE status IN ('FAILED', 'RETRY_SCHEDULED')` |
| `price_action` | Actions застряли в нетерминальных статусах | `SELECT status, COUNT(*) FROM price_action WHERE status NOT IN ('SUCCEEDED','FAILED') GROUP BY status` |
| `price_action_attempt` | Retry exhaustion | `SELECT * FROM price_action_attempt WHERE status = 'FAILED' ORDER BY completed_at DESC` |

## Критичные интеграции

| Интеграция | Влияние при недоступности | Восстановление |
|------------|--------------------------|----------------|
| PostgreSQL | Полная недоступность системы | Ожидание восстановления; все данные durable |
| RabbitMQ | Задачи не диспетчеризуются; outbox копит PENDING | Автоматическая разгрузка после восстановления (DB-first) |
| Keycloak | Все API-запросы отклоняются (401) | Ожидание восстановления; потери данных нет |
| Vault | Невозможно получить credentials маркетплейсов | Ожидание восстановления; текущие сессии могут работать с кешированными credentials |
| WB API | Данные WB не загружаются; price actions WB не исполняются | Retry через outbox; lane isolation не затрагивает Ozon |
| Ozon API | Данные Ozon не загружаются; price actions Ozon не исполняются | Retry через outbox; lane isolation не затрагивает WB |

## Failure modes и восстановление

### FM-1: API маркетплейса возвращает 429

**Симптомы:** `job_execution.status = 'RETRY_SCHEDULED'`, `checkpoint` содержит failed event с `error_type: API_ERROR`.

**Восстановление:** Автоматическое — worker сохраняет checkpoint (per-event progress) и планирует DLX retry через outbox с backoff (5 мин → 10 мин → 20 мин). При DLX retry worker возобновляет с места ошибки (checkpoint resume). После исчерпания попыток (`max_job_retries`, default: 3) → FAILED.

**Действие:** `SELECT id, status, checkpoint, error_details FROM job_execution WHERE status = 'RETRY_SCHEDULED'`. Проверить `checkpoint.events` — какие events зафейлились, какой cursor. Если retry не помогает — проверить rate limits маркетплейса.

### FM-2: API маркетплейса возвращает неожиданные ошибки

**Симптомы:** `job_execution` → FAILED (non-retriable), `error_details` содержит причину.

**Восстановление:** Ручное расследование. Проверить `error_details` и логи ingest-worker. DLX retry не срабатывает для non-retriable ошибок (4xx кроме 429, parse errors, credentials errors).

**Действие:** Проверить, не связано ли с ломающим изменением API (R-01). Обновить контракт провайдера и адаптер.

### FM-3: Outbox-сообщения застряли

**Симптомы:** ETL-выполнения в IN_PROGRESS без активности.

**Восстановление:** Outbox-поллер автоматически повторяет ERROR-сообщения (интервал 1с). При устойчивом сбое — проверить RabbitMQ.

**Действие:** `SELECT status, COUNT(*) FROM outbox_event GROUP BY status`. Рост PENDING → проблема с RabbitMQ или поллером.

### FM-4: Сбой материализации

**Симптомы:** ETL execution в MATERIALIZING → FAILED.

**Восстановление:** Материализация имеет retry с backoff. При исчерпании → FAILED. Перезапуск через новый ETL-прогон.

**Действие:** Проверить логи на SQL-ошибки; убедиться в корректности схемы.

### FM-5: Crash приложения во время ETL

**Симптомы:** Незавершённые ETL-выполнения при рестарте.

**Восстановление:** CAS-guards предотвращают дубли. Outbox-поллер возобновляется. Pending сообщения переотправляются.

**Действие:** После рестарта проверить, что незавершённые executions продолжились.

### FM-6: RabbitMQ недоступен

**Симптомы:** Outbox копит PENDING.

**Восстановление:** Полностью автоматическое после восстановления RabbitMQ. Данные не теряются (DB-first).

**Действие:** Мониторить `outbox_event WHERE status = 'PENDING'`.

### FM-7: Keycloak недоступен

**Симптомы:** Все API-запросы → 401/403.

**Восстановление:** Ожидание восстановления. Потери данных нет.

### FM-8: Price action uncertain outcome

**Симптомы:** Price action в RECONCILIATION_PENDING дольше ожидаемого.

**Восстановление:** Reconciliation worker проверяет фактическое состояние через provider read API. При timeout → manual investigation.

**Действие:** `SELECT * FROM price_action WHERE status = 'RECONCILIATION_PENDING' AND updated_at < now() - interval '30 minutes'`.

### FM-9: Vault недоступен

**Симптомы:** Ошибки аутентификации при вызовах marketplace API. Логи Integration module: `Vault unreachable, using cached credentials`. Credential rotation невозможна.

**Восстановление:**
1. Adapter продолжает работу на last-known credentials (in-memory cache).
2. Восстановить доступность Vault.
3. Проверить, что credential rotation не заблокирована: запустить test connection через UI.

**Влияние:** ETL и execution продолжают работу пока cached credentials валидны. Rotation и добавление новых connections невозможны. Если credentials expired во время outage — operations FAILED до восстановления Vault.

**Действие:** Мониторить `integration.vault.cache_hit` metric. Если 100% cache hits → Vault недоступен.

### FM-10: ClickHouse materialization failure

**Симптомы:** Stale data alert. Canonical data в PostgreSQL свежая (`canonical_price_current.updated_at` < threshold), но ClickHouse facts не обновлены. `job_execution` в статусе FAILED, RETRY_SCHEDULED или IN_PROGRESS дольше ожидаемого.

**Диагностика:**
1. `SELECT status, error_details, checkpoint FROM job_execution WHERE status IN ('FAILED', 'IN_PROGRESS', 'RETRY_SCHEDULED') ORDER BY started_at DESC LIMIT 10`
2. Проверить доступность ClickHouse: `SELECT 1 FROM system.one`
3. Проверить логи ingest-worker на ClickHouse connection errors

**Восстановление:**
1. Восстановить доступность ClickHouse
2. ETL автоматически retry через outbox
3. При завершении sync — `marketplace_sync_state` обновится → stale data guard снимется

**Влияние:** Canonical truth (PostgreSQL) не пострадала. Pricing заблокирован stale data guard (correct behavior). Analytics screens показывают устаревшие данные.

### FM-11: Price actions застряли в промежуточном статусе

**Симптомы:** Price actions в EXECUTING или SCHEDULED дольше ожидаемого. Stuck-state detector (каждые 5 мин) логирует предупреждения.

**Диагностика:**
1. `SELECT id, status, updated_at FROM price_action WHERE status IN ('EXECUTING', 'SCHEDULED') AND updated_at < now() - interval '15 minutes'`
2. Проверить `price_action_attempt` — есть ли зависшие попытки
3. Проверить логи executor-worker на marketplace API timeouts

**Восстановление:**
1. Stuck-state detector автоматически переводит застрявшие actions в `RECONCILIATION_PENDING`
2. Reconciliation worker проверяет фактическое состояние через provider read API
3. При подтверждении неизвестного исхода — manual investigation

**Действие:** Если проблема массовая — проверить доступность marketplace API (FM-1, FM-2). Если единичная — проверить конкретный action через `price_action_attempt.response_payload`.

## Конфигурация

### Ключевые параметры

| Параметр | Default | Назначение |
|----------|---------|------------|
| `datapulse.etl.outbox.poll-delay` | 1с | Интервал опроса outbox |
| `datapulse.etl.outbox.batch-size` | 50 | Сообщений за цикл опроса |
| `datapulse.etl.worker.allowed-skew` | 5с | Допуск рассинхронизации часов |
| `datapulse.etl.retry.max-attempts` | 3 | Макс. попыток retry |
| `datapulse.etl.retry.min-backoff` | 5с | Минимальный backoff |
| `datapulse.etl.retry.max-backoff` | 5мин | Максимальный backoff |
| `datapulse.etl.raw-retention.keep-count` | 3 | Количество хранимых raw-снапшотов |
| `datapulse.etl.stale-job-threshold` | 2h | Порог для перевода IN_PROGRESS → STALE |
| `datapulse.etl.stale-retry-threshold` | 1h | Порог для перевода RETRY_SCHEDULED → STALE (DLX message потерялось) |
| `datapulse.etl.retry.max-job-retries` | 3 | Макс. DLX retry per job_execution |
| `datapulse.etl.retry.dlx-min-backoff` | 5мин | Начальная задержка DLX retry |
| `datapulse.etl.retry.dlx-max-backoff` | 30мин | Максимальная задержка DLX retry |

### RabbitMQ topology

#### ETL pipeline

```
Exchanges (direct):
  etl.sync              — диспетчеризация ETL-задач
  etl.sync.wait         — delayed retry (DLX target)

Fanout exchange:
  datapulse.etl.events  — ETL_SYNC_COMPLETED fan-out (pricing-worker, api)

Queues:
  etl.sync              — ingest worker слушает
  etl.sync.wait         — TTL expiration → DLX → etl.sync

Consumer: AcknowledgeMode.AUTO, prefetchCount=1, defaultRequeueRejected=false
```

#### Price action execution

```
Exchanges (direct):
  price.execution       — диспетчеризация price actions
  price.execution.wait  — delayed retry (DLX target)

Queues:
  price.execution       — executor worker слушает
  price.execution.wait  — TTL expiration → DLX → price.execution

Consumer: AcknowledgeMode.AUTO, prefetchCount=1, defaultRequeueRejected=false
```

#### Reconciliation

```
Exchanges (direct):
  price.reconciliation       — deferred reconciliation dispatch
  price.reconciliation.wait  — delayed reconciliation (DLX)

Queues:
  price.reconciliation       — reconciliation consumer
  price.reconciliation.wait  — TTL → DLX → price.reconciliation
```

#### Promo action execution

```
Exchanges (direct):
  promo.execution       — диспетчеризация promo actions

Queues:
  promo.execution       — executor worker слушает (отдельная queue от price.execution)

Consumer: AcknowledgeMode.AUTO, prefetchCount=1, defaultRequeueRejected=false
```

#### Promo evaluation

```
Exchanges (direct):
  promo.evaluation       — диспетчеризация promo evaluation batches

Queues:
  promo.evaluation       — pricing worker слушает

Consumer: AcknowledgeMode.AUTO, prefetchCount=1, defaultRequeueRejected=false
```

## Базовые действия при сбоях

| Симптом | Первое действие |
|---------|-----------------|
| API отвечает 401 | Проверить credentials в Vault; проверить тип токена (WB: Content vs Statistics vs Analytics) |
| ETL зависло в IN_PROGRESS | Проверить outbox backlog; проверить RabbitMQ connectivity |
| Данные не обновляются | Проверить job_item на FAILED; проверить sync freshness |
| Финансовые данные некорректны | Проверить sign convention matching; сравнить с raw payloads; проверить reconciliation residual |
| Высокий reconciliation residual | Проверить новые типы операций маркетплейса; проверить mapping на unmapped operation types |
| Price action не исполняется | Проверить execution mode (LIVE vs SIMULATED); проверить stale data guards; проверить manual price lock |
| WebSocket не доставляет события | Проверить STOMP heartbeat; клиент обязан reconnect с exponential backoff (1s→2s→4s→max 30s) и перечитать состояние через REST |

## Scheduled jobs

| Job | Расписание | Назначение |
|-----|-----------|------------|
| User activity flush | Configurable | Сброс кеша активности |
| Очистка temp-файлов | Каждые 5 мин | Удаление устаревших файлов кеша |
| Очистка кеша | Configurable | Вытеснение устаревших кешей |
| Очистка инвайтов | Configurable | Удаление просроченных инвайтов |
| Сверка алертов | Configurable | Переоценка условий алертов |
| Stale job detector | Каждые 15 мин | Перевод зависших `job_execution` (IN_PROGRESS > 2h, RETRY_SCHEDULED > 1h) в STALE |
| Raw retention cleanup | Ежедневно | Удаление S3 objects по retention policy, обновление `job_item.status` → EXPIRED |

## Чеклист production readiness

- [ ] PostgreSQL развёрнут и доступен
- [ ] ClickHouse развёрнут и доступен
- [ ] RabbitMQ развёрнут с корректной topology (exchanges, queues, DLX)
- [ ] Redis развёрнут
- [ ] Keycloak настроен: realm, client, роли
- [ ] Vault настроен с credentials маркетплейсов
- [ ] S3-compatible storage доступен
- [ ] Application configuration для целевого окружения
- [ ] Liquibase-миграции выполняются успешно
- [ ] Credentials маркетплейсов валидированы (scope, тип токена)
- [ ] Health check endpoints верифицированы
- [ ] Лимиты ресурсов настроены (JVM heap, container limits)

### Resolved

- [x] Модель развёртывания — Docker Compose
- [x] Стек мониторинга — Prometheus/Micrometer + Grafana
- [x] Distributed tracing — Jaeger
- [x] Агрегация логов — Loki

### Открытые пункты (TBD)

- [ ] Dashboard-ы для обязательных метрик (implementation-time, метрики определены в NFA и модулях)
- [ ] Инфраструктурные алерты (Grafana Alerting) (implementation-time)
- [x] Стратегия бэкапов и восстановления PostgreSQL (RPO/RTO) — описана в [non-functional-architecture.md](non-functional-architecture.md) §Disaster Recovery Plan (RPO=5 мин, RTO=30 мин, pg_basebackup + WAL archiving)
- [ ] SLA/SLO — определяются после Phase B (см. [non-functional-architecture.md](non-functional-architecture.md))

## Advertising Failure Scenarios

### Scenario: WB Advertising API returns 404

**Симптомы:** `ADVERTISING_FACT` pipeline fails, integration_call_log показывает HTTP 404 для advert-api endpoints.

**Причина:** WB deprecation/migration of advertising endpoints (прецедент: v1→v2, v2→v3).

**Действия:**
1. Проверить WB developer changelog (https://dev.wildberries.ru/release-notes)
2. Найти новый endpoint path
3. Обновить YAML config (`endpoint-key` → new URL)
4. При изменении HTTP method (как POST→GET) или response structure — обновить adapter code
5. **P&L impact:** advertising_cost fallback = 0 до fix. UI показывает "Рекламные расходы не подключены"

### Scenario: Ozon Performance OAuth2 token failure

**Симптомы:** Ozon `ADVERTISING_FACT` pipeline fails, token exchange возвращает 401/403.

**Причина:** Невалидные client_id/client_secret, expired credentials, Ozon service outage.

**Действия:**
1. Проверить `secret_reference` для `OZON_PERFORMANCE_OAUTH2` — credentials актуальны?
2. Попробовать token exchange вручную: `curl -X POST https://api-performance.ozon.ru/api/client/token -d '{"client_id":"...","client_secret":"...","grant_type":"client_credentials"}'`
3. Если credentials expired — обновить в Vault, затем в `secret_reference`
4. Если Ozon outage — дождаться восстановления (no action needed, graceful degradation)
5. **P&L impact:** Ozon advertising_cost = 0 до fix. WB advertising продолжает работать независимо

### Scenario: Advertising data stale (no updates > 48h)

**Симптомы:** `fact_advertising.materialized_at` для connection отстаёт > 48h от now().

**Причина:** Pipeline scheduler failure, rate limit exhaustion, adapter error.

**Действия:**
1. Проверить `job_execution` для `ADVERTISING_FACT` pipeline — есть ли failed jobs?
2. Проверить rate limit counters для `WB_ADVERT` (5 req/60s)
3. Если scheduler пропускает — рестартовать ETL worker
4. **P&L impact:** Advertising data в marts устаревает, но не обнуляется. P&L использует последние доступные данные

## Связанные документы

- [Data Model](data-model.md) — runtime entrypoints, инварианты
- [Execution](modules/execution.md) — outbox, retry, RabbitMQ topology
- [Non-Functional Architecture](non-functional-architecture.md) — health, observability requirements
- [Risk Register](risk-register.md) — failure scenarios, mitigations
