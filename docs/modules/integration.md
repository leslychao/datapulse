# Модуль: Integration

**Фаза:** A — Foundation
**Зависимости:** [Tenancy & IAM](tenancy-iam.md)
**Runtime:** datapulse-api, datapulse-ingest-worker

---

## Назначение

Управление подключениями к маркетплейсам (WB, Ozon), безопасное хранение credentials, мониторинг здоровья подключений, журналирование вызовов к API провайдеров. Определяет политику работы с внешними API и фиксирует состояние провайдерских контрактов.

## Модель данных

### Таблицы PostgreSQL


| Таблица                  | Назначение                                                      | Ключевые поля                                                                                                                                                           |
| ------------------------ | --------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `marketplace_connection` | Подключение к кабинету маркетплейса                             | workspace_id (FK), marketplace_type (enum), name, status, secret_reference_id (FK), external_account_id, last_check_at, last_success_at, last_error_at, last_error_code |
| `secret_reference`       | Ссылка на секрет в Vault (generic, не только для маркетплейсов) | workspace_id (FK), provider, vault_path, vault_key, vault_version, secret_type (enum), status                                                                           |
| `marketplace_sync_state` | Состояние синхронизации per connection/domain                   | marketplace_connection_id (FK), data_domain, last_sync_at, last_success_at, next_scheduled_at, status                                                                   |
| `integration_call_log`   | Журнал вызовов к API маркетплейсов (observability)              | marketplace_connection_id (FK), endpoint, http_status, duration_ms, correlation_id                                                                                      |


### DDL

```sql
marketplace_connection:
  id                      BIGSERIAL PK
  workspace_id            BIGINT FK → workspace              NOT NULL
  marketplace_type        VARCHAR(10) NOT NULL                -- 'WB', 'OZON'
  name                    VARCHAR(255) NOT NULL               -- user-defined label
  status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING_VALIDATION'
  external_account_id     VARCHAR(120)                        -- provider-specific account/supplier ID
  secret_reference_id     BIGINT FK → secret_reference        NOT NULL
  perf_secret_reference_id BIGINT FK → secret_reference       (nullable — Ozon Performance OAuth2)
  last_check_at           TIMESTAMPTZ
  last_success_at         TIMESTAMPTZ
  last_error_at           TIMESTAMPTZ
  last_error_code         VARCHAR(60)                         -- e.g. 'AUTH_FAILED', 'RATE_LIMITED', 'TIMEOUT'
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (workspace_id, marketplace_type, external_account_id)

secret_reference:
  id                      BIGSERIAL PK
  workspace_id            BIGINT FK → workspace              NOT NULL
  provider                VARCHAR(20) NOT NULL                -- 'vault'
  vault_path              VARCHAR(500) NOT NULL               -- e.g. 'secret/data/datapulse/ws-42/ozon-seller'
  vault_key               VARCHAR(120) NOT NULL               -- key inside Vault secret
  vault_version           INT                                 -- Vault KV version (nullable — latest)
  secret_type             VARCHAR(40) NOT NULL                -- enum, see below
  status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
  rotated_at              TIMESTAMPTZ                         -- last rotation timestamp
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()

marketplace_sync_state:
  id                          BIGSERIAL PK
  marketplace_connection_id   BIGINT FK → marketplace_connection  NOT NULL
  data_domain                 VARCHAR(40) NOT NULL                -- enum, see below
  last_sync_at                TIMESTAMPTZ
  last_success_at             TIMESTAMPTZ
  next_scheduled_at           TIMESTAMPTZ
  sync_cursor                 JSONB                               -- domain-specific cursor (e.g. { "rrdid": 12345 })
  status                      VARCHAR(20) NOT NULL DEFAULT 'IDLE' -- IDLE, SYNCING, ERROR
  error_message               VARCHAR(1000)
  created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (marketplace_connection_id, data_domain)

integration_call_log:
  id                          BIGSERIAL PK
  marketplace_connection_id   BIGINT FK → marketplace_connection  NOT NULL
  endpoint                    VARCHAR(500) NOT NULL
  http_method                 VARCHAR(10) NOT NULL
  http_status                 INT
  duration_ms                 INT NOT NULL
  request_size_bytes          INT
  response_size_bytes         INT
  correlation_id              VARCHAR(60) NOT NULL
  error_details               VARCHAR(1000)
  retry_attempt               INT NOT NULL DEFAULT 0
  created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
```

### Enum values

| Enum | Значения | Описание |
|------|----------|----------|
| `marketplace_connection.status` | `PENDING_VALIDATION`, `ACTIVE`, `AUTH_FAILED`, `DISABLED`, `ARCHIVED` | См. §Connection lifecycle |
| `secret_reference.secret_type` | `WB_API_TOKEN`, `OZON_SELLER_CREDENTIALS`, `OZON_PERFORMANCE_OAUTH2` | Тип credentials |
| `secret_reference.status` | `ACTIVE`, `ROTATED`, `REVOKED` | ROTATED — предыдущая версия; REVOKED — отозван |
| `marketplace_sync_state.data_domain` | `CATALOG`, `PRICES`, `STOCKS`, `ORDERS`, `SALES`, `RETURNS`, `FINANCE`, `PROMO`, `ADVERTISING` | Один state per connection per domain |
| `marketplace_sync_state.status` | `IDLE`, `SYNCING`, `ERROR` | Текущее состояние sync для домена |

### Connection lifecycle

```
PENDING_VALIDATION → ACTIVE (credentials validated)
                   → AUTH_FAILED (validation failed)
ACTIVE → AUTH_FAILED (health-check detected failure)
       → DISABLED (admin manual disable)
       → ARCHIVED (workspace archived or admin action)
AUTH_FAILED → ACTIVE (re-validation success after credential update)
           → DISABLED (admin manual disable)
DISABLED → PENDING_VALIDATION (admin re-enable) → ACTIVE (validation success)
                                                → AUTH_FAILED (validation failure)
         → ARCHIVED
```

| Переход | Триггер | Последствия |
|---------|---------|-------------|
| → PENDING_VALIDATION | POST create connection | Credentials сохранены в Vault. Async validation запущена |
| PENDING_VALIDATION → ACTIVE | Validation success | `marketplace_sync_state` записи создаются для каждого data_domain. `external_account_id` заполнен. `ConnectionStatusChangedEvent` публикуется → `ConnectionActivationListener` (ETL) создаёт FULL_SYNC job через outbox |
| PENDING_VALIDATION → AUTH_FAILED | Validation failure | `last_error_code` = причина. Sync не запускается |
| ACTIVE → AUTH_FAILED | Health-check failure | Scheduled syncs paused для этого connection |
| → DISABLED | Admin action | Все syncs stopped. Connection скрыт из active list |
| DISABLED → PENDING_VALIDATION | Admin re-enable | Async re-validation запущена. При success → ACTIVE, при failure → AUTH_FAILED |
| → ARCHIVED | Workspace archived / admin | Soft delete. Data retained |

### Health-check

- **Механизм:** scheduled job (каждые 15 мин) для всех ACTIVE connections. `@Scheduled` + `@SchedulerLock(name = "connectionHealthCheck")` — гарантирует, что в кластере health-check запускается только на одном instance.
- **Проверка:** lightweight API call (WB: `POST /content/v2/get/cards/list` с `limit=1`; Ozon: `POST /v3/product/list` с `limit=1`).
- **Success:** обновить `last_check_at`, `last_success_at`.
- **Failure:** обновить `last_check_at`, `last_error_at`, `last_error_code`. Если 3 consecutive failures → `status = AUTH_FAILED`, dispatch `ConnectionHealthDegradedEvent` (Spring ApplicationEvent, in-process; не outbox).
- **Config:** interval и failure threshold — `@ConfigurationProperties`.
- **Архитектура:** интерфейс `MarketplaceHealthProbe` (Strategy pattern) с реализациями per marketplace (`WbHealthProbe`, `OzonHealthProbe`). Probes автоматически обнаруживаются через `List<MarketplaceHealthProbe>` injection.
- **Audit:** при каждом чтении credentials из Vault публикуется `CredentialAccessedEvent` с `purpose = "health_check"`.
- **Валидация credentials:** выделена в `ConnectionValidationService` — поддерживает sync (ручная проверка через API) и async (при создании и credential rotation) валидацию. Async validation использует dedicated thread pool `integrationExecutor`.

### Domain Events (Spring ApplicationEvent, in-process)

| Event | Когда публикуется | Поля | Потребители | Wired |
|-------|-------------------|------|-------------|-------|
| `ConnectionCreatedEvent` | Создание нового connection | connectionId, workspaceId, marketplaceType, userId | Audit | ⚠️ Listener для audit не реализован |
| `ConnectionStatusChangedEvent` | Любая смена статуса connection | connectionId, oldStatus, newStatus, trigger | ETL (trigger first sync при PV→ACTIVE), Audit, Notifications | ✅ ETL: `ConnectionActivationListener` (PV→ACTIVE → FULL_SYNC). ⚠️ Audit, Notifications — listener-ы не реализованы |
| `ConnectionHealthDegradedEvent` | Health-check threshold reached | connectionId, workspaceId, marketplaceType, consecutiveFailures, lastErrorCode | Alerting | ⚠️ Listener не реализован |
| `CredentialRotatedEvent` | Credential rotation (seller или performance) | connectionId, workspaceId, userId | Audit, cache eviction | ⚠️ Listener не реализован |
| `CredentialAccessedEvent` | Чтение credentials из Vault | connectionId, workspaceId, purpose | Audit trail | ✅ Публикуется из: `ConnectionValidationService`, `ConnectionHealthCheckScheduler`, ETL `CredentialResolver`, Execution `ExecutionCredentialResolver`. ⚠️ Listener для audit не реализован |
| `SyncTriggeredEvent` | Ручной trigger sync через API | connectionId, workspaceId, userId, domains (nullable) | ETL (создание job_execution) | ✅ ETL: `SyncTriggeredListener` (→ MANUAL_SYNC job через outbox) |

### Разделение ответственности

`marketplace_connection` — бизнес-сущность (что подключено, каков health). `secret_reference` — инфраструктурный concern (как достать credentials из Vault). Разделение предотвращает попадание секретов в основную таблицу.

## Управление секретами


| Требование                                         | Обоснование                           |
| -------------------------------------------------- | ------------------------------------- |
| API-ключи маркетплейсов хранятся в HashiCorp Vault | Разделение metadata и secret material |
| Маскирование credentials в логах                   | Предотвращение утечки через logs      |
| Raw payload access — restricted и аудитируемый     | Минимизация attack surface            |
| Валидация credentials перед каждой sync-сессией    | Раннее обнаружение протухших токенов  |
| Все попытки доступа к credentials аудируются       | Audit trail                           |


## Аутентификация провайдеров


| Провайдер        | Метод                            | Особенности                                                                                                      |
| ---------------- | -------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| WB               | API Key в header `Authorization` | Разные типы токенов для разных API (Content, Statistics, Analytics); один токен может не покрывать все endpoints |
| Ozon Seller      | `Client-Id` + `Api-Key` headers  | Единая пара для всех Seller API                                                                                  |
| Ozon Performance | OAuth2 client_credentials        | Отдельный host (`api-performance.ozon.ru`); отдельная регистрация; token exchange через `/api/client/token`      |

### Ozon Performance OAuth2 — Token Management

| Свойство | Значение |
|----------|----------|
| Grant type | `client_credentials` |
| Token endpoint | `POST https://api-performance.ozon.ru/api/client/token` |
| Token TTL | 30 минут (`expires_in: 1800`) |
| Refresh | Нет refresh token. Re-request с теми же credentials |
| Request body | `{"client_id": "...", "client_secret": "...", "grant_type": "client_credentials"}` |
| Response | `{"access_token": "...", "expires_in": 1800, "token_type": "Bearer"}` |

**Credentials storage:** `secret_reference` с `secret_type = OZON_PERFORMANCE_OAUTH2`. Vault path хранит `client_id` + `client_secret`. Не совпадает с Seller API credentials — отдельная регистрация.

**Connection model:** Performance API credentials привязываются к существующему `marketplace_connection` (Ozon) как дополнительный `secret_reference`. Отдельный connection не создаётся.

**Token caching:** In-memory per-connection. TTL = `expires_in − 300` (5 мин buffer). При expired token — re-request автоматически. Redis не требуется (single worker instance).

## Политика работы с API маркетплейсов

### Главное правило

Все marketplace-адаптеры используют **только текущие, официальные, публично документированные** API маркетплейсов.

### Допустимые источники

Только официальные: документация для разработчиков, API reference, release notes, документация по авторизации и rate limits.

### Запрещённые источники

Blog posts, Habr, Medium, Stack Overflow, неофициальные SDK, GitHub-примеры третьих сторон, Postman-коллекции, Telegram-чаты, устаревшие внутренние сниппеты.

### Обязательная верификация перед реализацией

Endpoint path, HTTP method, request/response schema, auth method, pagination semantics, rate limits, retry semantics, idempotency, error codes, deprecations, versioning.

### Anti-corruption boundary

Provider DTO и provider-specific response semantics остаются **внутри adapter boundaries**. Domain и application layers зависят от контрактов Datapulse, не от provider transport shapes. Протекание provider shapes в domain/application — architectural violation.

### Поведение при неясности документации

1. Не изобретать поведение.
2. Не полагаться на неофициальные источники.
3. Изолировать неопределённость на уровне adapter boundary.
4. Добавить explicit TODO/FIXME с ссылкой на official doc.
5. Предпочитать read-only или no-op-safe поведение.

## Rate limiting

### Принципы

- Rate limiting — **проактивный** (не ждём 429, а сами ограничиваем темп запросов).
- Лимиты конфигурируются через `@ConfigurationProperties` (`datapulse.integration.rate-limits.*`).
- При получении HTTP 429 — backoff и retry; не прерывать sync / execution целиком.

### Гранулярность: rate limit group × connection

Маркетплейс привязывает лимиты к **аккаунту продавца** (по API-ключу). Поэтому rate limiter работает на уровне:

```
connection_id × rate_limit_group → один token bucket
```

- **connection_id** — конкретное подключение (`marketplace_connection`). Разные seller-аккаунты имеют независимые лимиты.
- **rate_limit_group** — логическая группа endpoints с единым лимитом (например, `WB_STATISTICS`, `OZON_PROMO`).

Все runtime-ы (ETL ingest-worker, executor-worker) **разделяют** один и тот же bucket для данной пары (connection, group). Координация — через Redis (см. §Реализация ниже).

### Реализация: Redis-based token bucket

**Алгоритм:** token bucket, реализованный как атомарная Lua-операция в Redis.

**Redis key:** `rate:{connection_id}:{rate_limit_group}` → хранит `{ tokens: float, last_refill: epoch_ms }`.

**Lua-скрипт (атомарно):**

```
1. Прочитать текущие tokens и last_refill
2. Рассчитать delta = (now - last_refill) × rate
3. tokens = min(tokens + delta, burst)
4. Если tokens >= 1: tokens -= 1, вернуть 0 (разрешено, wait = 0)
5. Иначе: вернуть время ожидания до следующего токена (wait_ms)
```

**Caller (adapter):** rate limiter предоставляет контракт `CompletableFuture<Void> acquire(connectionId, group)`:
- Lua-скрипт возвращает `wait_ms`.
- Если `wait_ms == 0` — future завершается немедленно (`complete()`).
- Если `wait_ms > 0` — `ScheduledExecutorService.schedule(() -> future.complete(null), wait_ms, MILLISECONDS)`.
- Caller вызывает `future.get(timeout, SECONDS)` (блокирующий, Phase A) или `future.thenRun(apiCall)` (non-blocking, Phase G).
- Поддерживает `future.cancel()` при graceful shutdown.

Параллельные потоки (ETL events, executor) конкурируют за токены через Redis — Redis сам обеспечивает serialization.

**TTL:** ключ автоматически истекает через `max(burst / rate, 300)` секунд неактивности — чтобы не накапливался мусор.

**Fallback при недоступности Redis:** in-memory token bucket (Caffeine-based, per-JVM) с **conservative rate** (50% от нормального). Это безопасно для single-instance Phase A. При multi-instance (Phase G) — in-memory fallback допускает суммарное превышение, но 429 handling + backoff компенсируют.

### Лимиты по провайдерам

#### Wildberries

| Rate Limit Group | Limit | Burst | Источник | Consumers |
| --- | --- | --- | --- | --- |
| `WB_STATISTICS` | 1 req/min | 1 | Official docs | ETL (orders, sales) |
| `WB_ANALYTICS` | 1 req/20s | 1 | Official docs | ETL (stocks) |
| `WB_FINANCE` | 1 req/min | 1 | Official docs | ETL (reportDetailByPeriod) |
| `WB_PROMO` | 10 req/6s | 5 | Эмпирически | ETL (promo sync), Execution (promo write) |
| `WB_PROMO_NOMENCLATURES` | 10 req/6s | 5 | Эмпирически | ETL (promo products) |
| `WB_ADVERT` | 5 req/60s | 1 | Эмпирически | ETL (advertising) |
| `WB_PRICE_UPDATE` | 5 req/min | 1 | Эмпирически | Execution (price write) |
| `WB_CONTENT` | 1 req/10s | 1 | Conservative default | ETL (catalog) |
| `WB_PRICES_READ` | 1 req/10s | 1 | Conservative default | ETL (price snapshot) |
| `WB_MARKETPLACE` | 1 req/10s | 1 | Conservative default | ETL (warehouses, offices) |

#### Ozon

| Rate Limit Group | Limit | Burst | Источник | Consumers |
| --- | --- | --- | --- | --- |
| `OZON_DEFAULT` | 30 req/min | 3 | Conservative default | ETL (catalog, orders, finance, stocks) |
| `OZON_PROMO` | 20 req/60s | 3 | Эмпирически | ETL (promo sync), Execution (promo write) |
| `OZON_PRICE_UPDATE` | 30 req/min | 3 | Conservative default | Execution (price write) |
| `OZON_PERFORMANCE` | 60 req/min | 5 | Производное от 100K/day | ETL (advertising) |

**Колонка Consumers** — какие runtime-ы забирают токены из этого bucket. Если ETL и Execution используют один и тот же group — они конкурируют за бюджет через общий Redis bucket.

### Per-entity rate limiting (Ozon)

Ozon ограничивает обновление цены **per product**: 10 updates/hour per product (confirmed-docs). Это **не** API-level лимит, а business-level — один запрос может содержать до 1000 products, но каждый product имеет индивидуальный счётчик.

**Механизм:** per-product sliding window counter в Redis. Реализация: `OzonProductRateLimiter`, подключён в `OzonPriceWriteAdapter`.

- **Redis key:** `product_rate:{connection_id}:{product_identifier}` → sorted set, элементы = timestamp обновления. `product_identifier` — строка (Ozon `offer_id`).
- **Перед отправкой цены:** `canUpdate(connectionId, offerId)` проверяет `ZCOUNT key (now - 1h) +inf`. Если ≥ 10 → адаптер возвращает `REJECTED` с кодом `OZON_PRODUCT_RATE_LIMITED`, не обращаясь к API. Execution классифицирует как retriable и откладывает повтор.
- **После успешного обновления:** `recordUpdate(connectionId, offerId)` — `ZADD key now now` + `ZREMRANGEBYSCORE key 0 (now - 1h)` (очистка старых записей).
- **TTL:** 70 минут на ключ (1h + 10 min buffer).
- **Redis unavailable:** `canUpdate` возвращает `true` (allow) — полагаемся на 429 + backoff от Ozon.

**Важно:** это проактивная проверка. Если product всё же получил rejection от Ozon (per-product limit exceeded) — Execution классифицирует как `RETRIABLE_RATE_LIMIT` с backoff 10 min (см. [Execution](execution.md) §Классификация ошибок).

### Adaptive rate limiting (для unknown лимитов)

Для endpoint groups без документированных лимитов (`WB_CONTENT`, `WB_PRICES_READ`, `OZON_DEFAULT`) применяется адаптивная подстройка rate.

**Алгоритм: AIMD (Additive Increase, Multiplicative Decrease)**

```
Параметры:
  initial_rate     — стартовый conservative rate (из @ConfigurationProperties)
  min_rate         — нижняя граница (не медленнее этого)
  max_rate         — верхняя граница (2× initial_rate per group)
  increase_pct     — процент увеличения rate при стабильных 2xx (пропорциональный)
  decrease_factor  — множитель уменьшения при 429 (multiplicative, < 1.0)
  stability_window — кол-во подряд успешных запросов перед увеличением rate

Поведение:
  При HTTP 429 (или ConnectionDegradedEvent от circuit breaker):
    current_rate = max(current_rate × decrease_factor, min_rate)
    Немедленно обновить token bucket rate
    Если в ответе есть Retry-After header — использовать его как delay
    Иначе — exponential backoff из retry policy

  При stability_window подряд успешных (HTTP 2xx):
    current_rate = min(current_rate × (1 + increase_pct), max_rate)
    Обновить token bucket rate

  При рестарте worker-а:
    Начать с initial_rate (не сохраняем найденный rate между рестартами;
    conservative start безопаснее, чем stale cached rate)
```

**Почему процентный increase, а не фиксированный step:** rate limit groups имеют разброс от 0.017 req/s (WB_STATISTICS) до 0.5 req/s (OZON_DEFAULT). Фиксированный step (0.5 req/s) для медленной group означает прыжок в 30 раз. Процентный increase (20%) масштабируется автоматически: медленные groups растут медленно, быстрые — быстрее.

**Defaults:**

| Параметр | Значение | Пример для OZON_DEFAULT (0.5 req/s) |
| --- | --- | --- |
| `initial_rate` | Per-group (см. таблицы выше) | 0.5 req/s |
| `min_rate` | 1 req/60s | 0.017 req/s |
| `max_rate` | 2× initial_rate per group | 1.0 req/s |
| `increase_pct` | 0.2 (20%) | 0.5 → 0.6 → 0.72 → 0.86 → 1.0 (cap) |
| `decrease_factor` | 0.5 (halve) | 0.5 → 0.25 → 0.125... |
| `stability_window` | 20 consecutive 2xx | ~40s at 0.5 req/s |

### Cross-runtime координация

ETL (ingest-worker) и Execution (executor-worker) — разные JVM-процессы. Оба вызывают marketplace API через адаптеры Integration модуля.

```
┌─────────────────┐     ┌──────────────────┐
│  ingest-worker  │     │  executor-worker  │
│  (ETL sync)     │     │  (price/promo)    │
└────────┬────────┘     └────────┬──────────┘
         │                       │
         │  acquire token        │  acquire token
         └───────┐     ┌────────┘
                 ▼     ▼
         ┌───────────────────┐
         │  Redis token bucket │
         │  rate:{conn}:{grp}  │
         └───────────────────┘
                   │
                   ▼
         ┌──────────────────┐
         │  Marketplace API  │
         └──────────────────┘
```

**Инвариант:** один bucket per (connection_id, rate_limit_group) — независимо от того, сколько JVM-процессов конкурируют за токены. Redis обеспечивает координацию.

**Приоритизация:** Phase A — FIFO (кто первый взял токен). Execution writes не имеют приоритета над ETL reads. Обоснование: rate limits маркетплейсов низкие (1 req/min для WB Statistics), гарантировать latency для writes при таких лимитах невозможно без starvation reads. Если в будущем потребуется приоритизация — рассмотреть weighted token bucket (Phase G, при наличии данных о реальных конфликтах).

### Health-check и rate limit budget

Health-check (§Health-check выше) вызывает lightweight API для проверки credentials. Эти запросы **проходят через тот же token bucket**, что и рабочие запросы.

**Влияние:** для endpoints с лимитом 1 req/min (WB Statistics) health-check каждые 15 мин забирает 1 из ~15 доступных токенов за период. Потеря ~7% бюджета.

**Допустимость:** приемлемо. Health-check выполняет endpoint из группы `WB_CONTENT` (lightweight, `POST /content/v2/get/cards/list` с `limit=1`), а не из `WB_STATISTICS`. Для Ozon — из `OZON_DEFAULT` (`POST /v3/product/list` с `limit=1`). Влияние на тяжёлые ETL groups (Statistics, Finance) — нулевое.

**Инвариант:** health-check **обязан** проходить через token bucket. Иначе — риск 429 от маркетплейса, который может быть ошибочно интерпретирован как auth failure.

### Multi-workspace, один аккаунт маркетплейса

Если два workspace'а подключены к одному seller-аккаунту (одинаковый `external_account_id`) — маркетплейс видит один API-ключ и применяет единый лимит.

**Текущее ограничение:** `UNIQUE (workspace_id, marketplace_type, external_account_id)` не запрещает двум workspace'ам подключить тот же аккаунт через разные API-ключи (разные `secret_reference`). В этом случае — два независимых token bucket, суммарно 2× нагрузка → 429.

**Phase A решение (single-tenant):** не актуально — один workspace. Ограничение документируется.

**Phase G решение:** при создании connection — проверить `external_account_id` глобально (cross-workspace). Если совпадение — привязать rate limiter к `external_account_id`, а не к `connection_id`:

```
Redis key: rate:{external_account_id}:{rate_limit_group}  (вместо rate:{connection_id}:{rate_limit_group})
```

Переключение гранулярности — через `@ConfigurationProperties` флаг `datapulse.integration.rate-limit-key-strategy=connection|external-account` (default: `connection` для Phase A).

### При отсутствии документированных лимитов

1. Стартовать с conservative default (см. таблицы выше, группы с пометкой «Conservative default»).
2. Включить adaptive rate limiting (§AIMD выше).
3. Мониторить 429 responses (§Rate limit observability ниже).
4. При стабилизации — зафиксировать найденный rate в `@ConfigurationProperties`.
5. Обновить `docs/provider-api-specs/` с пометкой confidence = `empirical`.

### Rate limit observability

| Метрика | Тип | Labels | Описание |
| --- | --- | --- | --- |
| `marketplace_rate_limit_wait_seconds` | Histogram | `connection_id`, `rate_limit_group`, `marketplace_type` | Время ожидания токена в bucket. Показывает, насколько rate limiter тормозит запросы |
| `marketplace_rate_limit_throttled_total` | Counter | `connection_id`, `rate_limit_group`, `marketplace_type` | Количество HTTP 429 ответов (проактивный limiter не спас) |
| `marketplace_rate_limit_current_rate` | Gauge | `connection_id`, `rate_limit_group`, `marketplace_type` | Текущий effective rate (req/s) после adaptive adjustment |
| `marketplace_rate_limit_tokens_available` | Gauge | `connection_id`, `rate_limit_group`, `marketplace_type` | Доступные токены в bucket (снимок). **Не реализовано** — требует дополнительного Lua-вызова к Redis |
| `ozon_product_rate_limit_exhausted_total` | Counter | `connection_id`, `product_id` | Ozon per-product 10/hour limit exhausted (product excluded from batch) |

**Alert rules:**

| Alert | Условие | Severity | Действие |
| --- | --- | --- | --- |
| `RateLimitThrottlingHigh` | `rate(marketplace_rate_limit_throttled_total[15m]) > 3` per connection/group | WARNING | Проверить лимиты, скорректировать rate |
| `RateLimitThrottlingSustained` | `rate(marketplace_rate_limit_throttled_total[1h]) > 10` per connection/group | CRITICAL | Возможно, лимиты маркетплейса изменились. Manual investigation |
| `RateLimitWaitExcessive` | `histogram_quantile(0.95, marketplace_rate_limit_wait_seconds) > 60` | WARNING | ETL sync замедлен rate limiter-ом. Может влиять на data freshness |
| `OzonProductRateSaturation` | `rate(ozon_product_rate_limit_exhausted_total[1h]) > 50` (без label product_id) | WARNING | Массовое исчерпание per-product бюджета. Сигнал: pricing engine генерирует слишком частые изменения |

## Retry при ошибках провайдера


| Тип ошибки                        | Поведение                                                  |
| --------------------------------- | ---------------------------------------------------------- |
| HTTP 429 (rate limit)             | Если `Retry-After` header присутствует — использовать его как delay. Иначе — exponential backoff. AIMD decrease срабатывает в обоих случаях (§Adaptive rate limiting) |
| HTTP 5xx (transient)              | Exponential backoff + retry                                |
| HTTP 4xx (кроме 429)              | Не retry; зафиксировать ошибку, перейти к следующему item  |
| Connection timeout                | Exponential backoff + retry                                |
| Неизвестный payload / parse error | Зафиксировать ошибку, не retry; расследовать изменение API |


## Устойчивость к частичной деградации

- Сбой одного маркетплейса не блокирует обработку другого (lane isolation).
- Сбой одного data domain в рамках маркетплейса не блокирует другие domains (event-level isolation внутри lane).
- Partial failure при загрузке batch: зафиксировать ошибочные items, продолжить с остальными.

### Circuit breaker (transient provider degradation)

Health-check (§Health-check выше) обнаруживает полные auth failures. Для transient degradation (медленные ответы, периодические 500-ки без auth failure) применяется дополнительная логика:

| Сигнал | Порог | Реакция |
|--------|-------|---------|
| Error rate per connection за последние 15 мин | > 50% calls failed (5xx, timeout) при ≥ 5 calls | `ConnectionDegradedEvent` (Spring ApplicationEvent, in-process). Log warning. AIMD реагирует: `current_rate × decrease_factor` для затронутых rate limit groups (§Adaptive rate limiting) |
| Latency p95 per connection | > 3× от baseline (rolling average) | `ConnectionDegradedEvent`. Log warning. AIMD снижает rate. Sync не прерывается |
| Consecutive sync job failures (одного domain) | 3 подряд | `SyncDomainStalled` alert (→ `alert_event` в БД + notification). Pause scheduled sync для этого domain+connection. Manual resume через API |

**Отличие от health-check:**
- Health-check определяет auth failures (credentials протухли / отозваны) → connection-level transition в `AUTH_FAILED`.
- Circuit breaker определяет transient degradation → connection остаётся `ACTIVE`, но отдельные domain syncs могут быть приостановлены.

**Связь с AIMD:** circuit breaker — **детектор** проблемы, AIMD — **исполнитель** реакции. Circuit breaker не модифицирует rate напрямую. Он эмитирует `ConnectionDegradedEvent`, а AIMD слушает этот event и применяет `decrease_factor` к rate. Это обеспечивает единую точку управления rate (token bucket) и исключает двойное замедление.

**Recovery:** при успешном sync job после stall → automatic resume. AIMD постепенно восстанавливает rate через stability_window. При manual pause → manual resume через `POST /api/connections/{connectionId}/sync`.

### Vault unavailability

При недоступности Vault (сетевой сбой, Vault sealed, timeout):

| Сценарий | Поведение | Обоснование |
|----------|-----------|-------------|
| Vault down при scheduled sync | Sync job fails: `FAILED` с `error_details: "vault_unavailable"`. Retry на следующем schedule. Не трогать connection status | Transient infrastructure issue, не credential problem |
| Vault down при connection creation | `PENDING_VALIDATION` → не переходит. UI показывает ошибку. Retry: пользователь повторно нажимает «Save» | Create = user-initiated, можно запросить retry от пользователя |
| Vault down при health-check | Health-check skip для connections, где credentials в Vault. Log error. Не менять status на `AUTH_FAILED` | Неясно, протух ли credential или инфраструктура лежит |
| Vault down при price/promo write | Action attempt fails → retry (стандартный retry path из Execution). `error_class: INFRASTRUCTURE` | Write adapters получают credentials из Vault перед каждым вызовом |
| Prolonged Vault outage (> 30 мин) | Alert: `VAULT_UNAVAILABLE` (CRITICAL). Все syncs stalled. Price/promo writes failing. Manual investigation required | Критическая зависимость |

**Кеширование credentials:** допускается in-memory per-worker кеш (Caffeine) с коротким TTL для обеспечения continuity при кратковременном Vault outage. Детали — см. §Credential caching ниже. Credential material не попадает в shared store (Redis) — только heap memory с ограниченным TTL. Исключение с отдельным TTL: Ozon Performance OAuth2 access token (derived short-lived token, не secret).

**Мониторинг:** Prometheus metric `vault_request_errors_total`, alert rule при > 3 errors за 5 мин.

## Версионирование API

- Адаптер привязан к конкретной версии endpoint.
- При deprecation провайдером — migration plan документируется.
- Deprecated endpoints заменяются до их отключения провайдером.
- `@JsonIgnoreProperties(ignoreUnknown = true)` на provider DTO — защита от minor additions.

## Observability для provider calls

Каждый вызов провайдера обязан содержать: `correlation_id`, `connection_id`, provider и capability, HTTP method, endpoint, status code, timing (duration_ms), retry count, error details.

## Матрица возможностей провайдеров

### Core data domains


| Capability | WB Status | WB Notes                                      | Ozon Status | Ozon Notes                                                                                                  |
| ---------- | --------- | --------------------------------------------- | ----------- | ----------------------------------------------------------------------------------------------------------- |
| Каталог    | READY     | Content API v2                                | PARTIAL     | v3 product/list + v3 product/info + v4 attributes (brand); `updated_at` отсутствует — delta-sync невозможен |
| Цены       | READY     | Discounts & Prices API v2                     | READY       | v5 product/info/prices                                                                                      |
| Остатки    | PARTIAL   | Analytics API; sandbox не возвращает данных   | READY       | v4 product/info/stocks                                                                                      |
| Заказы     | READY     | Statistics API v1                             | PARTIAL     | FBO verified; FBS не тестировался                                                                           |
| Продажи    | READY     | Statistics API v1                             | READY       | Composite: postings (FBO+FBS) + finance                                                                     |
| Возвраты   | READY     | Из finance report; dedicated endpoint обойдён | READY       | v1 returns/list                                                                                             |
| Финансы    | READY     | Statistics API v5                             | READY       | v3 finance/transaction/list                                                                                 |


### Extended data domains


| Capability                | WB Status       | Ozon Status | Notes                             |
| ------------------------- | --------------- | ----------- | --------------------------------- |
| Промо (list/products)     | READY           | READY       | Calendar API / Actions API        |
| Реклама (campaigns/stats) | NEEDS MIGRATION | NEEDS IMPLEMENTATION | WB: v2→v3; Ozon: OAuth2. **Phase B extended** |


### Write capabilities


| Capability          | WB Status | Ozon Status | Notes                                             |
| ------------------- | --------- | ----------- | ------------------------------------------------- |
| Price write         | BLOCKED   | READY       | WB: DNS failure + 401; см. write-contracts.md F-1 |
| Promo participation | TBD       | READY       | Ozon: `/v1/actions/products/activate` + `/deactivate` (confirmed-docs); WB: `/api/v1/calendar/promotions/upload` (требует верификации). См. [Promotions](promotions.md) |


### Известные блокеры


| ID  | Провайдер | Описание                                                | Mitigation                                                       |
| --- | --------- | ------------------------------------------------------- | ---------------------------------------------------------------- |
| B-2 | Ozon      | Performance API требует отдельной OAuth2                | Требуется: client_id + client_secret для api-performance.ozon.ru. Token TTL = 30 min, cache с refresh. `secret_type = OZON_PERFORMANCE_OAUTH2`. Блокирует Phase B extended |
| B-3 | WB        | Advertising v2→v3 migration                             | POST → GET; batch max 50 IDs; flatten hierarchy. Блокирует Phase B extended |
| B-4 | WB        | Price write: хост мигрирован (DNS failure); sandbox 401 | Обновить хост; получить production-токен с write scope           |


### Validation gaps


| ID  | Провайдер | Описание                                              | Действие                                                          |
| --- | --------- | ----------------------------------------------------- | ----------------------------------------------------------------- |
| V-1 | Ozon      | FBS postings не тестировались                         | Graceful degradation; полная валидация с production FBS-аккаунтом |
| V-2 | Ozon      | Rate limits не документированы                        | Эмпирическое определение + мониторинг 429                         |
| V-3 | WB        | Timestamp formats могут отличаться sandbox/production | Dual-format parsing                                               |


### Pagination patterns


| Провайдер/API | Pattern      | Описание                              |
| ------------- | ------------ | ------------------------------------- |
| WB Content    | Cursor-based | `cursor.updatedAt` + `cursor.nmID`    |
| WB Statistics | Time-bounded | Один запрос = один временной диапазон |
| WB Finance    | Cursor-based | `rrdid` (cursor) с limit              |
| Ozon Products | Cursor-based | `last_id` + `limit`                   |
| Ozon Finance  | Page-based   | `page` + `page_size`                  |


### Требуемые версии API


| Провайдер | Endpoint                 | Версия   | Deprecated          |
| --------- | ------------------------ | -------- | ------------------- |
| Ozon      | product/list             | v3       | v2 (404)            |
| Ozon      | product/info             | v3       | v2 (404)            |
| Ozon      | product/info/prices      | v5       | —                   |
| Ozon      | finance/transaction/list | v3       | —                   |
| WB        | reportDetailByPeriod     | v5       | —                   |
| WB        | content/get/cards/list   | v2       | —                   |
| WB        | fullstats                | v3 (GET) | v2 (POST, disabled) |


## Review checklist для marketplace changes

- Official doc links reviewed
- Endpoint/version verified
- Auth verified
- Deprecation check performed
- DTO changes aligned with current docs
- Rate-limit/retry semantics reviewed
- Tests updated for current documented behavior

## Credential rotation

### Flow

```
1. Admin обновляет credentials через UI
2. PUT /api/connections/{id}/credentials { новые credentials }
3. Backend:
   a. Записать новый секрет в Vault (new version)
   b. Обновить secret_reference (vault_version, rotated_at)
   c. Async validation с новыми credentials
   d. Success → connection.status = ACTIVE; old secret version retained (Vault versioning)
   e. Failure → connection.status = AUTH_FAILED; rollback vault_version (point to old)
4. Audit: action_type = 'credential.rotated'
```

### Правила

- Старая версия секрета не удаляется из Vault (audit trail).
- `secret_reference.status = ROTATED` для предыдущей записи (если создаётся новая запись вместо version bump).
- Rotation не прерывает текущие active syncs — они завершат работу с cached credentials.

## Vault unavailability handling

### Credential caching

При недоступности Vault worker'ы не могут получить credentials для API-вызовов. Для обеспечения continuity:

| Аспект | Описание |
|--------|----------|
| Cache layer | In-memory per-worker-instance cache (Caffeine). Не Redis — credential material не должен попадать в shared store |
| Cache TTL | 1 час (configurable через `datapulse.vault.credential-cache-ttl`). Достаточно для пережидания кратковременного outage |
| Cache invalidation | При credential rotation (PUT credentials) — cache eviction через прямой вызов `evictCache()` в `VaultCredentialStore.rotate()`. Для multi-instance: расширить на event-based eviction (Redis pub/sub) |
| Vault health check | Health endpoint проверяет Vault availability. При failure → `log.warn`, workers продолжают с cached credentials |
| Prolonged outage (> TTL) | Cache expired, Vault недоступен → sync fails с `VAULT_UNAVAILABLE` error. `marketplace_connection.last_error_code = 'VAULT_UNAVAILABLE'`. Alert: CRITICAL |
| Security | Cache хранит расшифрованные credentials в heap memory. JVM memory dumps — restricted access |

**Инвариант:** Vault остаётся единственным persistent store для credentials. Cache — ephemeral fast layer (аналогично Redis scope из NFR-6b).

## REST API

### Connections

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/connections` | ADMIN, OWNER | Создать connection. Body: `{ marketplaceType, name, credentials: { ... } }`. Response: `201 { id, name, status: PENDING_VALIDATION }`. Credentials → Vault |
| GET | `/api/connections` | Any role | Список connections workspace. Response: `[{ id, marketplaceType, name, status, lastCheckAt, lastSuccessAt, lastErrorCode }]` |
| GET | `/api/connections/{connectionId}` | Any role | Детали connection. Включает sync state per domain |
| PUT | `/api/connections/{connectionId}` | ADMIN, OWNER | Обновить connection (name). Body: `{ name }` |
| PUT | `/api/connections/{connectionId}/credentials` | ADMIN, OWNER | Credential rotation. Body: `{ credentials: { ... } }`. Triggers async re-validation |
| POST | `/api/connections/{connectionId}/validate` | ADMIN, OWNER | Ручная валидация credentials. Response: `{ valid: true/false, errorCode: "..." }` |
| POST | `/api/connections/{connectionId}/disable` | ADMIN, OWNER | Disable connection → `status = DISABLED` |
| POST | `/api/connections/{connectionId}/enable` | ADMIN, OWNER | Enable disabled connection → triggers re-validation |
| DELETE | `/api/connections/{connectionId}` | OWNER | Archive connection → `status = ARCHIVED` |

### Credentials request format

`credentials` в `POST /api/connections` и `PUT /api/connections/{id}/credentials` — provider-specific JSON object:

**WB:**

```json
{
  "marketplaceType": "WB",
  "name": "Мой кабинет WB",
  "credentials": {
    "apiToken": "eyJhbGciOi..."
  }
}
```

| Поле | Тип | Обязательное | Описание |
|------|-----|--------------|----------|
| `apiToken` | String | Да | WB API token (JWT). Покрывает Content, Statistics, Analytics, Prices API |

**Ozon Seller:**

```json
{
  "marketplaceType": "OZON",
  "name": "Мой кабинет Ozon",
  "credentials": {
    "clientId": "1943980",
    "apiKey": "a9d31abc-6f09-4848-915e-d669fba8845a"
  }
}
```

| Поле | Тип | Обязательное | Описание |
|------|-----|--------------|----------|
| `clientId` | String | Да | Ozon Seller Client-Id |
| `apiKey` | String | Да | Ozon Seller Api-Key |

**Ozon Performance (дополнительные credentials):**

Ozon Performance OAuth2 credentials привязываются к существующему OZON-connection:

```
PUT /api/connections/{connectionId}/performance-credentials
```

```json
{
  "performanceClientId": "...",
  "performanceClientSecret": "..."
}
```

| Поле | Тип | Обязательное | Описание |
|------|-----|--------------|----------|
| `performanceClientId` | String | Да | Ozon Performance OAuth2 client_id |
| `performanceClientSecret` | String | Да | Ozon Performance OAuth2 client_secret |

Создаёт отдельный `secret_reference` с `secret_type = OZON_PERFORMANCE_OAUTH2`, привязанный к connection через `perf_secret_reference_id`.

**Validation:** credentials валидируются при создании (async). `marketplaceType` определяет, какие поля обязательны. Неизвестные поля игнорируются. Пустые / blank значения → 400 Bad Request.

### Sync management

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/connections/{connectionId}/sync-state` | Any role | Sync state per domain: `[{ dataDomain, lastSyncAt, lastSuccessAt, nextScheduledAt, status }]` |
| POST | `/api/connections/{connectionId}/sync` | ADMIN, OWNER | Trigger manual sync. Body (optional): `{ domains: ["CATALOG", "PRICES"] }` — если не передан или пустой, синхронизируются все domains. Публикует `SyncTriggeredEvent` → `SyncTriggeredListener` (ETL) создаёт MANUAL_SYNC job через outbox |

### Call log

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/connections/{connectionId}/call-log` | ADMIN, OWNER | Paginated call log. Filters: `?from=...&to=...&endpoint=...&httpStatus=...` |

## Local Mode & WireMock

### Назначение

При локальной разработке (`spring.profiles.active=local`) все marketplace API-вызовы направляются на WireMock вместо реальных хостов провайдеров. Это позволяет:
- Разрабатывать и отлаживать адаптеры без реальных credentials
- Запускать интеграционные тесты offline
- Тестировать write-операции (цены, промо) без побочных эффектов

### Архитектура

```
┌─────────────────────┐
│  datapulse-api /    │
│  ingest-worker      │
│  (profile=local)    │
│                     │
│  base-url →         │──► http://localhost:9091 ──► WireMock
│  localhost:9091     │        (docker-compose)
└─────────────────────┘

┌─────────────────────┐
│  datapulse-api /    │
│  ingest-worker      │
│  (profile=!local)   │
│                     │
│  base-url →         │──► https://api-seller.ozon.ru
│  real hosts         │──► https://discounts-prices-api.wildberries.ru
└─────────────────────┘
```

### Переключение base URL

В `application-local.yml` все базовые URL маркетплейсов указывают на WireMock:

```yaml
datapulse:
  integration:
    wildberries:
      content-base-url: http://localhost:9091
      prices-base-url: http://localhost:9091
      statistics-base-url: http://localhost:9091
      analytics-base-url: http://localhost:9091
      marketplace-base-url: http://localhost:9091
      advert-base-url: http://localhost:9091
      promo-base-url: http://localhost:9091
    ozon:
      seller-base-url: http://localhost:9091
      performance-base-url: http://localhost:9091
```

WireMock матчит запросы по path pattern — один инстанс обслуживает все провайдеры.

### WireMock в docker-compose

Сервис `wiremock` в `infra/docker-compose.yml`, порт `9091:8080`. Включён `--global-response-templating` для динамических ответов.

### Принцип создания стабов

**Каждый WireMock-стаб строго соответствует задокументированному контракту** из `docs/provider-api-specs/`. Response body берётся из:
1. Верифицированных примеров ответов в `wb-read-contracts.md`, `ozon-read-contracts.md`, `write-contracts.md`, `promo-advertising-contracts.md`
2. Реальных ответов API, зафиксированных в `samples/empirical-verification-log.md`

Запрещено выдумывать поля, менять типы данных или структуру ответа.

### Матрица покрытия WireMock стабов

#### Wildberries — Read

| Capability | Endpoint | Method | Mapping file | Status |
|------------|----------|--------|-------------|--------|
| Catalog | `/content/v2/get/cards/list` | POST | `wb-catalog-cards-list.json` | ✅ |
| Prices | `/api/v2/list/goods/filter` | GET | `wb-prices-goods-filter.json` | ✅ |
| Stocks | `/api/analytics/v1/stocks-report/wb-warehouses` | POST | `wb-stocks-report.json` | ✅ |
| Orders | `/api/v1/supplier/orders` | GET | `wb-orders.json` | ✅ |
| Sales | `/api/v1/supplier/sales` | GET | `wb-sales.json` | ✅ |
| Finance | `/api/v5/supplier/reportDetailByPeriod` | GET | `wb-finance-report.json` | ✅ |
| Incomes | `/api/v1/supplier/incomes` | GET | `wb-incomes.json` | ✅ |
| Offices (WB warehouses) | `/api/v3/offices` | GET | `wb-offices.json` | ✅ |
| Seller warehouses (FBS) | `/api/v3/warehouses` | GET | `wb-warehouses-seller.json` | ✅ |
| Returns | `/api/v1/analytics/goods-return` | GET | `wb-returns-goods-return.json` | ✅ |
| Tariffs | `/api/v1/tariffs/commission` | GET | `wb-tariffs-commission.json` | ✅ |

#### Wildberries — Write

| Capability | Endpoint | Method | Mapping file | Status |
|------------|----------|--------|-------------|--------|
| Price upload | `/api/v2/upload/task` | POST | `wb-price-upload-task.json` | ✅ |
| Price poll | `/api/v2/history/goods/task` | GET | `wb-price-upload-details.json` | ✅ |
| Promo upload | `/api/v1/calendar/promotions/upload` | POST | `wb-promo-upload.json` | ✅ |

#### Wildberries — Promo & Advertising

| Capability | Endpoint | Method | Mapping file | Status |
|------------|----------|--------|-------------|--------|
| Promo list | `/api/v1/calendar/promotions` | GET | `wb-promo-promotions.json` | ✅ |
| Promo details | `/api/v1/calendar/promotions/details` | GET | `wb-promo-promotion-details.json` | ✅ |
| Promo products | `/api/v1/calendar/promotions/nomenclatures` | GET | `wb-promo-promotion-nomenclatures.json` | ✅ |
| Ad campaigns | `/api/advert/v2/adverts` | GET | `wb-advertising-campaigns.json` | ✅ |
| Ad fullstats | `/adv/v3/fullstats` | GET | `wb-advertising-fullstats.json` | ✅ |

#### Ozon — Read

| Capability | Endpoint | Method | Mapping file | Status |
|------------|----------|--------|-------------|--------|
| Product list | `/v3/product/list` | POST | `ozon-product-list.json` | ✅ |
| Product info | `/v3/product/info/list` | POST | `ozon-product-info-list.json` | ✅ |
| Category tree | `/v1/description-category/tree` | POST | `ozon-category-tree.json` | ✅ |
| Attributes (brand) | `/v4/product/info/attributes` | POST | `ozon-product-attributes.json` | ✅ |
| Prices | `/v5/product/info/prices` | POST | `ozon-prices.json` | ✅ |
| Stocks | `/v4/product/info/stocks` | POST | `ozon-stocks.json` | ✅ |
| Postings FBO | `/v2/posting/fbo/list` | POST | `ozon-posting-fbo-list.json` | ✅ |
| Postings FBS | `/v3/posting/fbs/list` | POST | `ozon-posting-fbs-list.json` | ✅ |
| Returns | `/v1/returns/list` | POST | `ozon-returns-list.json` | ✅ |
| Finance | `/v3/finance/transaction/list` | POST | `ozon-finance-transactions.json` | ✅ |

#### Ozon — Write

| Capability | Endpoint | Method | Mapping file | Status |
|------------|----------|--------|-------------|--------|
| Price import | `/v1/product/import/prices` | POST | `ozon-import-prices.json` | ✅ |
| Promo activate | `/v1/actions/products/activate` | POST | `ozon-actions-activate.json` | ✅ |
| Promo deactivate | `/v1/actions/products/deactivate` | POST | `ozon-actions-deactivate.json` | ✅ |

#### Ozon — Promo

| Capability | Endpoint | Method | Mapping file | Status |
|------------|----------|--------|-------------|--------|
| Actions list | `/v1/actions` | GET | `ozon-actions-list.json` | ✅ |
| Action products | `/v1/actions/products` | POST | `ozon-actions-products.json` | ✅ |
| Action candidates | `/v1/actions/candidates` | POST | `ozon-actions-candidates.json` | ✅ |

## Implementation Status

Компоненты, описанные в документе, но ещё не реализованные в коде:

| Компонент | Секция | Приоритет | Комментарий |
|-----------|--------|-----------|-------------|
| Circuit breaker (transient degradation) | §Circuit breaker | Phase A+ | Error rate / latency p95 детекция, `SyncDomainStalled` alert, pause per domain. AIMD adaptive rate controller реализован, но circuit breaker детектор — нет |
| Vault health-check endpoint + метрика | §Vault unavailability handling | Phase A+ | `vault_request_errors_total` metric и alert rule |
| Ozon Performance token exchange + caching | §Ozon Performance OAuth2 | Phase B extended | Token endpoint, in-memory cache с TTL = expires_in − 300s |
| Integration call log recording | §Observability для provider calls | Phase A | Таблица и entity существуют, но нет WebClient interceptor-а для автоматической записи каждого вызова |
| Credential masking в логах | §Управление секретами | Phase A | Log filter для предотвращения утечки credential material |
| Rate limit alert rules | §Rate limit observability | Phase A+ | Prometheus alert rules (метрики реализованы, rules — нет) |
| `CredentialRotatedEvent` listener | §Domain Events | Phase A | Event публикуется при credential rotation, но listener для audit-записи и кросс-инстанс cache eviction не реализован |
| `ConnectionHealthDegradedEvent` listener | §Domain Events | Phase A | Event публикуется health-check scheduler-ом, но listener для alerting не реализован |

## Связанные модули

- [Tenancy & IAM](tenancy-iam.md) — workspace-привязка connections
- [ETL Pipeline](etl-pipeline.md) — adapters используют connections для загрузки данных
- [Execution](execution.md) — write-адаптеры для price actions
- [Promotions](promotions.md) — write-адаптеры для promo activate/deactivate
- Детальные контракты: [Provider API Specs](../provider-api-specs/)

