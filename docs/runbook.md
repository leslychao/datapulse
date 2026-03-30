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
| `job_execution` | Длительные IN_PROGRESS | `SELECT * FROM job_execution WHERE status = 'IN_PROGRESS' AND started_at < now() - interval '1 hour'` |
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

**Симптомы:** ETL source execution в RETRY_SCHEDULED.

**Восстановление:** Автоматическое — worker планирует delayed retry через outbox с backoff. После исчерпания попыток (default: 3) → FAILED, downstream шаги lane → SKIPPED.

**Действие:** Проверить job_item на accumulation RETRY_SCHEDULED.

### FM-2: API маркетплейса возвращает неожиданные ошибки

**Симптомы:** ETL source → FAILED немедленно.

**Восстановление:** Ручное расследование. Проверить логи. Downstream шаги пропускаются.

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

### FM-9: ClickHouse materialization failure

**Симптомы:** Stale data alert. Canonical data в PostgreSQL свежая (`canonical_price_snapshot.captured_at` < threshold), но ClickHouse facts не обновлены. `job_execution` в статусе FAILED или IN_PROGRESS дольше ожидаемого.

**Диагностика:**
1. `SELECT status, error_details FROM job_execution WHERE status IN ('FAILED', 'IN_PROGRESS') ORDER BY started_at DESC LIMIT 10`
2. Проверить доступность ClickHouse: `SELECT 1 FROM system.one`
3. Проверить логи ingest-worker на ClickHouse connection errors

**Восстановление:**
1. Восстановить доступность ClickHouse
2. ETL автоматически retry через outbox
3. При завершении sync — `marketplace_sync_state` обновится → stale data guard снимется

**Влияние:** Canonical truth (PostgreSQL) не пострадала. Pricing заблокирован stale data guard (correct behavior). Analytics screens показывают устаревшие данные.

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

### RabbitMQ topology

#### ETL pipeline

```
Exchanges (direct):
  etl.execution       — диспетчеризация ETL-задач
  etl.execution.wait  — delayed retry (DLX target)

Queues:
  etl.execution       — ingest worker слушает
  etl.execution.wait  — TTL expiration → DLX → etl.execution

Consumer: AcknowledgeMode.AUTO, prefetchCount=1, defaultRequeueRejected=true
```

#### Action execution

```
Exchanges (direct):
  action.execution       — диспетчеризация price actions
  action.execution.wait  — delayed retry (DLX target)

Queues:
  action.execution       — executor worker слушает
  action.execution.wait  — TTL expiration → DLX → action.execution

Consumer: AcknowledgeMode.AUTO, prefetchCount=1, defaultRequeueRejected=false
```

#### Reconciliation

```
Exchanges (direct):
  action.reconciliation       — deferred reconciliation dispatch
  action.reconciliation.wait  — delayed reconciliation (DLX)

Queues:
  action.reconciliation       — reconciliation consumer
  action.reconciliation.wait  — TTL → DLX → action.reconciliation
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

- [ ] Dashboard-ы для обязательных метрик
- [ ] Инфраструктурные алерты (Grafana Alerting)
- [ ] Стратегия бэкапов и восстановления PostgreSQL (RPO/RTO)
- [ ] SLA/SLO

## Связанные документы

- [Data Model](data-model.md) — runtime entrypoints, инварианты
- [Execution](modules/execution.md) — outbox, retry, RabbitMQ topology
- [Non-Functional Architecture](non-functional-architecture.md) — health, observability requirements
- [Risk Register](risk-register.md) — failure scenarios, mitigations
