# Datapulse — Матрица возможностей провайдеров

## Назначение

Покрытие capabilities по провайдерам. Фиксирует: какие capabilities доступны, какие заблокированы, какие требуют валидации.

## Read capabilities

### Core data domains

| Capability | WB Status | WB Notes | Ozon Status | Ozon Notes |
|------------|-----------|----------|-------------|------------|
| Каталог | READY | Content API v2 | PARTIAL | v3 product/list + v3 product/info + v4 attributes (brand); `updated_at` отсутствует в API — delta-sync по дате невозможен |
| Цены | READY | Discounts & Prices API v2 | READY | v5 product/info/prices |
| Остатки | PARTIAL | Analytics API; sandbox не возвращает данных | READY | v4 product/info/stocks |
| Заказы | READY | Statistics API v1 | PARTIAL | FBO verified; FBS не тестировался |
| Продажи | READY | Statistics API v1 | READY | Composite: postings (FBO+FBS) + finance |
| Возвраты | READY | Dedicated endpoint (400) обойдён: возвраты извлекаются из finance report; дата парсится через `yyyy-MM-dd'T'HH:mm:ss` | READY | v1 returns/list |
| Финансы | READY | Statistics API v5 (reportDetailByPeriod) | READY | v3 finance/transaction/list |

### Extended data domains

| Capability | WB Status | WB Notes | Ozon Status | Ozon Notes |
|------------|-----------|----------|-------------|------------|
| Промо (list) | READY | Calendar API | READY | Actions API v1 |
| Промо (products) | READY | Calendar API nomenclatures | READY | Actions API products |
| Промо (candidates) | N/A | — | READY | Actions API candidates |
| Промо (details) | READY | Calendar API details | N/A | — |
| Реклама (campaigns) | NEEDS MIGRATION | v1 deprecated → v2 available | STUB | Performance API: отдельная OAuth2 регистрация |
| Реклама (stats) | NEEDS MIGRATION | v2 (POST) deprecated → v3 (GET) | STUB | Требует client_credentials flow на api-performance.ozon.ru |

### Write capabilities

| Capability | WB Status | WB Notes | Ozon Status | Ozon Notes |
|------------|-----------|----------|-------------|------------|
| Price write | BLOCKED | Discounts & Prices API: хост `discounts-prices-api.wildberries.ru` мигрирован (DNS failure); sandbox-токен возвращает 401. См. `write-contracts.md` F-1 | READY | Seller API price update |
| Promo participation | TBD | Calendar API — требует исследования | TBD | Actions API — требует исследования |

## Известные блокеры

| ID | Провайдер | Описание | Impact | Mitigation |
|----|-----------|----------|--------|------------|
| B-1 | WB | ~~Returns dedicated endpoint возвращает 400~~ **RESOLVED**: возвраты извлекаются из finance report; дата парсится через `yyyy-MM-dd'T'HH:mm:ss` | ~~Нет прямого источника возвратов~~ Решено | Альтернативный источник подтверждён в `wb-read-contracts.md` и `mapping-spec.md` |
| B-2 | Ozon | Performance API (advertising) требует отдельной OAuth2 регистрации | Нет данных по рекламе Ozon | Требуется: client_id + client_secret для api-performance.ozon.ru |
| B-3 | WB | Advertising v2→v3 migration: POST endpoint отключён | Загрузка рекламной статистики сломана | HTTP method change: POST → GET; body → query params; требуется переписать адаптер |
| B-4 | WB | Price write: хост `discounts-prices-api.wildberries.ru` мигрирован (DNS failure); sandbox-токен возвращает 401 | Запись цен WB невозможна | Обновить хост; получить production-токен с write scope. См. `write-contracts.md` F-1 |

## Validation gaps

| ID | Провайдер | Описание | Требуемое действие |
|----|-----------|----------|--------------------|
| V-1 | Ozon | FBS postings не тестировались эмпирически | Graceful degradation: `@JsonIgnoreProperties(ignoreUnknown = true)` на DTO; парсинг FBS-специфичных полей изолирован; сбой FBS не блокирует FBO; при несовпадении response structure — log warning + skip FBS-специфичных полей. Полная валидация — с production-аккаунтом с FBS |
| V-2 | Ozon | Rate limits не документированы ни для одного endpoint | Эмпирическое определение: conservative limits + мониторинг 429 |
| V-3 | WB | Timestamp formats могут отличаться между sandbox и production | Адаптер обязан поддерживать dual-format parsing |
| V-4 | WB | v2 advertising campaigns — response structure wrap | Adapter extractor может потребовать адаптации |

## Rate limits

### Wildberries

| API Group | Limit | Источник |
|-----------|-------|----------|
| Statistics (orders, sales) | 1 запрос/мин | Official docs |
| Analytics (stocks) | 1 запрос/20 сек | Official docs |
| Finance (reportDetailByPeriod) | 1 запрос/мин | Official docs |
| Promo (calendar) | 10 запросов/6 сек, burst 5 | Эмпирически |
| Promo (nomenclatures) | Отдельный bucket | Эмпирически |
| Advertising | 5 запросов/60 сек | Эмпирически |

### Ozon

| API Group | Limit | Источник |
|-----------|-------|----------|
| Promo (actions) | 20 запросов/60 сек | Эмпирически |
| Все остальные | Не документированы | Ozon не публикует rate limits; определяются эмпирически |

## Аутентификация

| Провайдер | Метод | Особенности |
|-----------|-------|-------------|
| WB | API Key в header `Authorization` | Разные типы токенов для разных API (Content, Statistics, Analytics); один токен может не покрывать все endpoints |
| Ozon Seller | `Client-Id` + `Api-Key` headers | Единая пара для всех Seller API |
| Ozon Performance | OAuth2 client_credentials | Отдельный host (`api-performance.ozon.ru`); отдельная регистрация; token exchange через `/api/client/token` |

## Pagination patterns

| Провайдер/API | Pattern | Описание |
|---------------|---------|----------|
| WB Content | Cursor-based | `cursor.updatedAt` + `cursor.nmID` |
| WB Statistics | Time-bounded | Один запрос = один временной диапазон |
| WB Finance | Cursor-based | `rrdid` (cursor) с limit |
| WB Promo | Offset-based | `offset` + `limit` (default 1000) |
| Ozon Products | Cursor-based | `last_id` + `limit` |
| Ozon Postings | Cursor-based | `offset` в POST body |
| Ozon Finance | Page-based | `page` + `page_size` (max 1000) |
| Ozon Promo | Offset-based | `offset` + `limit` в POST body |

## Требуемые версии API

| Провайдер | Endpoint | Версия | Deprecated |
|-----------|----------|--------|------------|
| Ozon | product/list | v3 | v2 (returns 404) |
| Ozon | product/info | v3 | v2 (returns 404) |
| Ozon | product/info/prices | v5 | — |
| Ozon | product/info/stocks | v4 | — |
| Ozon | finance/transaction/list | v3 | — |
| WB | reportDetailByPeriod | v5 | — |
| WB | content/get/cards/list | v2 | — |
| WB | advert/adverts | v2 | v1 (returns 404) |
| WB | fullstats | v3 (GET) | v2 (POST, disabled) |

## Связанные документы

- [Целевая архитектура](target-architecture.md) — capability-based integration
- [Архитектура данных](data-architecture.md) — pipeline layers, sign conventions, join keys
- [Политика работы с API маркетплейсов](marketplace-api-policy.md) — обязательные правила адаптеров
