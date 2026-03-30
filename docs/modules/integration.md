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


| API Group       | Limit              | Источник                 |
| --------------- | ------------------ | ------------------------ |
| Promo (actions) | 20 запросов/60 сек | Эмпирически              |
| Все остальные   | Не документированы | Определяются эмпирически |


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

## Версионирование API

- Адаптер привязан к конкретной версии endpoint.
- При deprecation провайдером — migration plan документируется.
- Deprecated endpoints заменяются до их отключения провайдером.
- `@JsonIgnoreProperties(ignoreUnknown = true)` на provider DTO — защита от minor additions.

## Observability для provider calls

Каждый вызов провайдера обязан содержать: `correlation_id`, `account_id`, provider и capability, HTTP method, endpoint, status code, timing (duration_ms), retry count, error details.

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
| Реклама (campaigns/stats) | NEEDS MIGRATION | STUB        | WB: v2→v3; Ozon: отдельная OAuth2 |


### Write capabilities


| Capability          | WB Status | Ozon Status | Notes                                             |
| ------------------- | --------- | ----------- | ------------------------------------------------- |
| Price write         | BLOCKED   | READY       | WB: DNS failure + 401; см. write-contracts.md F-1 |
| Promo participation | TBD       | TBD         | Требует исследования                              |


### Известные блокеры


| ID  | Провайдер | Описание                                                | Mitigation                                                       |
| --- | --------- | ------------------------------------------------------- | ---------------------------------------------------------------- |
| B-2 | Ozon      | Performance API требует отдельной OAuth2                | Требуется: client_id + client_secret для api-performance.ozon.ru |
| B-3 | WB        | Advertising v2→v3 migration                             | POST → GET; требуется переписать адаптер                         |
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

## Связанные модули

- [Tenancy & IAM](tenancy-iam.md) — workspace-привязка connections
- [ETL Pipeline](etl-pipeline.md) — adapters используют connections для загрузки данных
- [Execution](execution.md) — write-адаптеры для price actions
- Детальные контракты: [Provider API Specs](../provider-api-specs/)

