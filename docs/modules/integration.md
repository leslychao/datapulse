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
  marketplace_type        VARCHAR(10) NOT NULL                -- 'ozon', 'wb'
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
DISABLED → ACTIVE (admin re-enable + successful validation)
         → ARCHIVED
```

| Переход | Триггер | Последствия |
|---------|---------|-------------|
| → PENDING_VALIDATION | POST create connection | Credentials сохранены в Vault. Async validation запущена |
| PENDING_VALIDATION → ACTIVE | Validation success | `marketplace_sync_state` записи создаются для каждого data_domain. `external_account_id` заполнен. Автоматически триггерится первый `FULL_SYNC` (INSERT `job_execution` + INSERT `outbox_event` в той же транзакции) |
| PENDING_VALIDATION → AUTH_FAILED | Validation failure | `last_error_code` = причина. Sync не запускается |
| ACTIVE → AUTH_FAILED | Health-check failure | Scheduled syncs paused для этого connection |
| → DISABLED | Admin action | Все syncs stopped. Connection скрыт из active list |
| → ARCHIVED | Workspace archived / admin | Soft delete. Data retained |

### Health-check

- **Механизм:** scheduled job (каждые 15 мин) для всех ACTIVE connections.
- **Проверка:** lightweight API call (WB: `GET /api/v2/cards/list?limit=1`; Ozon: `POST /v1/product/list` с `limit=1`).
- **Success:** обновить `last_check_at`, `last_success_at`.
- **Failure:** обновить `last_check_at`, `last_error_at`, `last_error_code`. Если 3 consecutive failures → `status = AUTH_FAILED`, dispatch `ConnectionHealthDegraded` event.
- **Config:** interval и failure threshold — `@ConfigurationProperties`.

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

- Каждый адаптер реализует rate limiting в соответствии с документированными или эмпирически определёнными лимитами провайдера.
- Rate limiter — token-bucket на уровне адаптера.
- При получении HTTP 429 — backoff и retry; не прерывать sync целиком.
- Лимиты конфигурируются через `@ConfigurationProperties`.

### Лимиты по провайдерам

#### Wildberries


| API Group                      | Limit                      | Источник      |
| ------------------------------ | -------------------------- | ------------- |
| Statistics (orders, sales)     | 1 запрос/мин               | Official docs |
| Analytics (stocks)             | 1 запрос/20 сек            | Official docs |
| Finance (reportDetailByPeriod) | 1 запрос/мин               | Official docs |
| Promo (calendar)               | 10 запросов/6 сек, burst 5 | Эмпирически   |
| Advertising                    | 5 запросов/60 сек          | Эмпирически   |


#### Ozon


| API Group       | Limit                    | Scope       | Источник      |
| --------------- | ------------------------ | ----------- | ------------- |
| Price Write     | 10 updates/hour          | per product | confirmed-docs |
| Promo (actions) | 20 запросов/60 сек       | per account | Эмпирически   |
| Все остальные   | Не документированы       | —           | Определяются эмпирически |


### При отсутствии документированных лимитов

Conservative defaults → мониторинг 429 responses → корректировка эмпирически → фиксация в конфигурации.

## Retry при ошибках провайдера


| Тип ошибки                        | Поведение                                                  |
| --------------------------------- | ---------------------------------------------------------- |
| HTTP 429 (rate limit)             | Backoff + retry                                            |
| HTTP 5xx (transient)              | Backoff + retry                                            |
| HTTP 4xx (кроме 429)              | Не retry; зафиксировать ошибку, перейти к следующему item  |
| Connection timeout                | Backoff + retry                                            |
| Неизвестный payload / parse error | Зафиксировать ошибку, не retry; расследовать изменение API |


## Устойчивость к частичной деградации

- Сбой одного маркетплейса не блокирует обработку другого (lane isolation).
- Сбой одного data domain в рамках маркетплейса не блокирует другие domains (event-level isolation внутри lane).
- Partial failure при загрузке batch: зафиксировать ошибочные items, продолжить с остальными.

### Circuit breaker (transient provider degradation)

Health-check (§Health-check выше) обнаруживает полные auth failures. Для transient degradation (медленные ответы, периодические 500-ки без auth failure) применяется дополнительная логика:

| Сигнал | Порог | Реакция |
|--------|-------|---------|
| Error rate per connection за последние 15 мин | > 50% calls failed (5xx, timeout) при ≥ 5 calls | `ConnectionDegradedEvent`. Log warning. Backoff: увеличить интервал retry |
| Latency p95 per connection | > 3× от baseline (rolling average) | Log warning. Не прерывать sync, но замедлить request rate |
| Consecutive sync job failures (одного domain) | 3 подряд | `SyncDomainStalled` alert. Pause scheduled sync для этого domain+connection. Manual resume через API |

**Отличие от health-check:**
- Health-check определяет auth failures (credentials протухли / отозваны) → connection-level transition в `AUTH_FAILED`.
- Circuit breaker определяет transient degradation → connection остаётся `ACTIVE`, но отдельные domain syncs могут быть приостановлены.

**Recovery:** при успешном sync job после stall → automatic resume. При manual pause → manual resume через `POST /api/connections/{connectionId}/sync`.

### Vault unavailability

При недоступности Vault (сетевой сбой, Vault sealed, timeout):

| Сценарий | Поведение | Обоснование |
|----------|-----------|-------------|
| Vault down при scheduled sync | Sync job fails: `FAILED` с `error_details: "vault_unavailable"`. Retry на следующем schedule. Не трогать connection status | Transient infrastructure issue, не credential problem |
| Vault down при connection creation | `PENDING_VALIDATION` → не переходит. UI показывает ошибку. Retry: пользователь повторно нажимает «Save» | Create = user-initiated, можно запросить retry от пользователя |
| Vault down при health-check | Health-check skip для connections, где credentials в Vault. Log error. Не менять status на `AUTH_FAILED` | Неясно, протух ли credential или инфраструктура лежит |
| Vault down при price/promo write | Action attempt fails → retry (стандартный retry path из Execution). `error_class: INFRASTRUCTURE` | Write adapters получают credentials из Vault перед каждым вызовом |
| Prolonged Vault outage (> 30 мин) | Alert: `VAULT_UNAVAILABLE` (CRITICAL). Все syncs stalled. Price/promo writes failing. Manual investigation required | Критическая зависимость |

**Кеширование credentials в памяти не допускается** — secret material не должен покидать Vault дольше, чем на время одного API-вызова. Исключение: Ozon Performance OAuth2 access token (не secret, а derived short-lived token) кешируется in-memory с TTL.

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
| Cache invalidation | При credential rotation (PUT credentials) — cache eviction через Spring event |
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
| POST | `/api/connections/{connectionId}/validate` | ADMIN, OWNER | Ручная валидация credentials. Response: `{ valid: true/false, error: "..." }` |
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
| POST | `/api/connections/{connectionId}/sync` | ADMIN, OWNER | Trigger manual sync. Body: `{ domains: ["CATALOG", "PRICES"] }` (optional — all if empty). Создаёт `job_execution` |

### Call log

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/connections/{connectionId}/call-log` | ADMIN, OWNER | Paginated call log. Filters: `?from=...&to=...&endpoint=...&httpStatus=...` |

## Связанные модули

- [Tenancy & IAM](tenancy-iam.md) — workspace-привязка connections
- [ETL Pipeline](etl-pipeline.md) — adapters используют connections для загрузки данных
- [Execution](execution.md) — write-адаптеры для price actions
- [Promotions](promotions.md) — write-адаптеры для promo activate/deactivate
- Детальные контракты: [Provider API Specs](../provider-api-specs/)

