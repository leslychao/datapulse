# Wildberries — Advertising Bidding Contracts (Write + Read)

**Статус:** confirmed-docs + real-api-verified
**Источники:**
- WB Advert API: https://dev.wildberries.ru/en/docs/openapi/promotion
- WB Swagger: https://dev.wildberries.ru/en/swagger/promotion
- WB API Digest March 2026: https://dev.wildberries.ru/en/news/302
- Web search cross-verification: 2026-04-12
- Real API verification: 2026-04-12 (production, read-only token)
- Кодовая база: `datapulse-etl` module (read-only adapters existing)
**Верификация:** official WB docs (web-scraped + web search) + real API calls on production

Этот документ фиксирует **полный** функциональный контур Wildberries Advert API, необходимый
для реализации autobidding: чтение кампаний, чтение текущих ставок, изменение ставок,
рекомендации по ставкам, управление состоянием кампаний и статистика.

> **Связанный документ:** Контракты для read-only ingestion (campaigns list, fullstats)
> описаны в [promo-advertising-contracts.md](promo-advertising-contracts.md) §2.
> Здесь описаны **только** bidding-specific endpoints и дополнения к read-контрактам.

Confidence levels:
- **confirmed** — подтверждено реальным API-ответом или кодом
- **confirmed-docs** — проверено по официальной документации WB
- **web-verified** — подтверждено через web search + community sources
- **code-verified** — подтверждено наличием в кодовой базе Datapulse
- **assumed** — выведено логически, не подтверждено

---

## 1. ОБЩИЕ СВЕДЕНИЯ

### 1.1 Base URL и авторизация

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Base URL | `https://advert-api.wildberries.ru` | confirmed |
| Auth | API Key в header `Authorization` | confirmed |
| Token category | **Promotion** | confirmed-docs |
| Token scope | **Read + Write** (требуется Write для изменения ставок) | confirmed-docs |

### 1.2 Тайминги синхронизации (из WB API Digest March 2026)

| Что | Частота | Confidence |
|-----|---------|------------|
| Синхронизация данных из БД | Каждые **3 минуты** | confirmed-docs |
| Изменение статусов кампаний | Каждую **1 минуту** | confirmed-docs |
| Применение изменения ставок | Каждые **30 секунд** | confirmed-docs |

> Источник: "Data synchronization from the database occurs every 3 minutes. Status changes occur every 1 minute. The bid change occurs every 30 seconds. The latest changes are saved within the intervals" — WB API Promotion docs header.

**Архитектурное значение:** Reconciliation window после write-операции: ≥ 30s для ставок, ≥ 60s для статусов. При polling рекомендаций (`competitiveBid`, `leadersBid`) — данные обновляются с задержкой ≤ 3 мин.

### 1.3 Единицы измерения

| Величина | Единица | Confidence |
|----------|---------|------------|
| Ставки (bids) | **Копейки** (1 руб = 100 копеек) | confirmed-docs |
| Бюджеты | Копейки | confirmed-docs |
| Статистика (spend, revenue) | **Рубли** | confirmed-docs |

### 1.4 Статусы кампаний

| Код | Название | Bidding-операции | Confidence |
|-----|----------|------------------|------------|
| `4` | Ready to launch | Можно менять ставки, запускать | confirmed-docs |
| `7` | Completed | Только чтение | confirmed-docs |
| `9` | Active (running) | Можно менять ставки, ставить на паузу | confirmed-docs |
| `11` | Paused | Можно менять ставки, запускать, останавливать | confirmed-docs |

**Важно:** Bidding write-операции (`PATCH /api/advert/v1/bids`) работают только для кампаний в статусах **4, 9, 11**.

### 1.5 Типы кампаний

| Код | Тип | Confidence |
|-----|-----|------------|
| `9` | Unified (единая, с Oct 2025) | confirmed-docs |

С октября 2025 все новые кампании создаются как Unified (type=9). Старые типы (4, 5, 6, 7, 8) — legacy.

### 1.6 Bid type (тип управления ставками)

| Значение | Описание | Confidence |
|----------|----------|------------|
| `unified` | Автоматическая ставка (WB алгоритм) | confirmed-docs |
| `manual` | Ручная ставка (продавец задаёт сам) | confirmed-docs |

**Архитектурное решение:** Для autobidding relevants только `manual` bid type — API позволяет программно управлять ставками. При `unified` WB управляет ставками автоматически.

### 1.7 Placement (место размещения)

| Значение | Описание | Confidence |
|----------|----------|------------|
| `search` | Поисковая выдача | confirmed-docs |
| `recommendations` | Блок рекомендаций | confirmed-docs |
| `combined` | Поиск + рекомендации (для unified bid type) | confirmed-docs |

### 1.8 Payment type (модель оплаты)

| Значение | Описание | Confidence |
|----------|----------|------------|
| `cpm` | Оплата за 1000 показов | confirmed-docs |
| `cpc` | Оплата за клик | confirmed-docs |

---

## 2. BIDDING WRITE ENDPOINTS

### 2.1 Изменение ставок — `PATCH /api/advert/v1/bids`

Основной endpoint для управления ставками на уровне товара. Это **главный** endpoint для autobidding.

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `PATCH` | confirmed-docs |
| Path | `/api/advert/v1/bids` | confirmed-docs |
| Base URL | `https://advert-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |
| Token category | Promotion (Write) | confirmed-docs |
| Idempotency | Да — повторный вызов с теми же значениями не создаёт побочных эффектов | assumed |

#### Rate Limits

| Period | Limit | Interval | Burst | Confidence |
|--------|-------|----------|-------|------------|
| 1 s | 5 requests | 200 ms | — | confirmed-docs |

#### Request Body

```json
{
  "bids": [
    {
      "advert_id": 12345,
      "nm_bids": [
        {
          "nm_id": 13335157,
          "bid_kopecks": 250,
          "placement": "recommendations"
        }
      ]
    }
  ]
}
```

| Field | Type | Required | Max items | Semantics | Confidence |
|-------|------|----------|-----------|-----------|------------|
| `bids` | array | yes | 50 | Массив кампаний с изменениями ставок | confirmed-docs |
| `bids[].advert_id` | integer | yes | — | ID рекламной кампании | confirmed-docs |
| `bids[].nm_bids` | array | yes | — | Массив товаров со ставками | confirmed-docs |
| `bids[].nm_bids[].nm_id` | integer | yes | — | WB артикул (nmId) товара | confirmed-docs |
| `bids[].nm_bids[].bid_kopecks` | integer | yes | — | Ставка в копейках | confirmed-docs |
| `bids[].nm_bids[].placement` | string | yes | — | Место размещения: `search`, `recommendations`, `combined` | confirmed-docs |

#### Placement rules

- `combined` — для кампаний с автоматической ставкой (`bidType = unified`). Устанавливает одну ставку для поиска и рекомендаций.
- `search` или `recommendations` — для кампаний с ручным управлением (`bidType = manual`). Ставки задаются раздельно.

#### Response — 200 OK

```json
{
  "bids": [
    {
      "advert_id": 12345,
      "nm_bids": [
        {
          "nm_id": 13335157,
          "bid_kopecks": 250,
          "placement": "recommendations"
        }
      ]
    }
  ]
}
```

Успешный ответ зеркалирует запрос — подтверждает применённые значения.

#### Error Responses

| HTTP Status | Описание | Confidence |
|-------------|----------|------------|
| 400 | Invalid payload (невалидный placement, payment_type и т.д.) | confirmed-docs |
| 401 | Unauthorized (невалидный или read-only токен) | confirmed |
| 403 | Access denied | confirmed-docs |
| 429 | Too many requests | confirmed-docs |

#### Error Response Body

```json
{
  "detail": "invalid payment_type value",
  "origin": "camp-api-public-cache",
  "request_id": "7e5cb1f106cc6e85b5b29eb2e8815da2",
  "status": 400,
  "title": "invalid payload"
}
```

#### Constraints

- Работает **только** для кампаний в статусах `4`, `9`, `11`.
- Ставка должна быть ≥ минимальной ставки для данного товара/placement.
- Максимум **50 items** в массиве `bids`.
- `placement` должен соответствовать `bidType` кампании: `combined` для `unified`, `search`/`recommendations` для `manual`.

#### Бизнес-сценарий

Autobidding использует этот endpoint для:
1. Повышения ставки (стратегия `ECONOMY_HOLD` при DRR < target)
2. Понижения ставки (стратегия `ECONOMY_HOLD` при DRR > target)
3. Установки минимальной ставки (стратегия `MINIMAL_PRESENCE`)
4. Корректировки ставки до рекомендованного уровня

---

### 2.2 Получение минимальных ставок — `POST /api/advert/v1/bids/min`

Возвращает минимально допустимые ставки для товаров в кампании. Необходим для стратегии `MINIMAL_PRESENCE` и как guard для всех стратегий (ставка не может быть ниже минимальной).

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | **confirmed** (real API) |
| Path | `/api/advert/v1/bids/min` | **confirmed** (real API) |
| Base URL | `https://advert-api.wildberries.ru` | **confirmed** (real API) |
| Auth | API Key (header `Authorization`) | **confirmed** (real API) |
| WB docs category | Campaigns Creation | confirmed-docs |

#### Rate Limits

| Period | Limit | Interval | Burst | Confidence |
|--------|-------|----------|-------|------------|
| 1 min | 20 requests | 3 s | 5 | confirmed-docs |

#### Request Body

```json
{
  "advert_id": 12345,
  "nm_ids": [13335157, 13335158],
  "payment_type": "cpm",
  "placement_types": ["search", "recommendations"]
}
```

| Field | Type | Required | Max items | Semantics | Confidence |
|-------|------|----------|-----------|-----------|------------|
| `advert_id` | integer | yes | — | ID рекламной кампании | **confirmed** (real API) |
| `nm_ids` | integer[] | yes (min 1) | 100 | Массив WB артикулов | **confirmed** (real API) |
| `payment_type` | string | yes | — | Тип оплаты: `cpm`, `cpc` | **confirmed** (real API) |
| `placement_types` | string[] | yes (min 1) | — | Типы размещения: `search`, `recommendations` | **confirmed** (real API) |

**API Verification (2026-04-12):**
- Формат подтверждён реальным API-вызовом на production. Field naming — **snake_case**.
- Пустой `{}` body возвращает validation error с Go struct: `V0GetMinBidRequest.AdvertId`, `NmIds`, `PaymentType`, `PlacementTypes`.
- Валидный body с nm_id, не принадлежащим продавцу → `400` `"some nm are not belong to supplier"`.
- **GOTCHA (BOM):** WB Go-бэкенд чувствителен к BOM в JSON body. Файл — строго UTF-8 без BOM. В Java WebClient проблема не возникает.

#### Response — 200 OK

Ответ содержит минимальные ставки в копейках для каждого товара и placement.

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| Response body | object | Минимальные ставки per nm_id × placement | confirmed-docs |
| Min bid value | integer | Ставка в копейках | confirmed-docs |

> **PARTIALLY RESOLVED (2026-04-12):** Request format подтверждён реальным API. Точная структура response не получена — тестовый аккаунт не имеет товаров. Для полной верификации response нужен аккаунт с товарами.

#### Error Responses (confirmed via real API, 2026-04-12)

| HTTP Status | Error Detail | Semantics | Confidence |
|-------------|-------------|-----------|------------|
| 400 | `"can not deserialize response body"` | Невалидный JSON (BOM или неверный формат) | **confirmed** |
| 400 | `"some nm are not belong to supplier"` | nm_id не принадлежит продавцу | **confirmed** |
| 400 | Validation error with field details | Отсутствуют обязательные поля (`{}` body) | **confirmed** |

#### Бизнес-сценарий

- Стратегия `MINIMAL_PRESENCE`: установка ставки = minBid.
- Guard `MinBidGuard`: проверка, что рассчитанная ставка ≥ minBid.
- Стратегия `ECONOMY_HOLD`: floor для расчётной ставки.

---

## 3. BIDDING READ ENDPOINTS

### 3.1 Рекомендованные ставки — `GET /api/advert/v0/bids/recommendations`

Возвращает рекомендации по ставкам для товаров, основанные на конкурентном окружении. Критически важный сигнал для autobidding.

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | **confirmed** (real API) |
| Path | `/api/advert/v0/bids/recommendations` | **confirmed** (real API) |
| Base URL | `https://advert-api.wildberries.ru` | **confirmed** (real API) |
| Auth | API Key (header `Authorization`) | **confirmed** (real API) |
| Ограничение | Только для кампаний с моделью оплаты **CPM** | confirmed-docs |

#### Rate Limits

| Period | Limit | Interval | Burst | Confidence |
|--------|-------|----------|-------|------------|
| 1 min | 5 requests | — | — | confirmed-docs |

#### Query Parameters

| Param | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `advertId` | integer | yes | ID кампании (camelCase!) | **confirmed** (real API) |
| `nmId` | integer | yes | WB артикул товара (camelCase!) | **confirmed** (real API) |

**API Verification (2026-04-12):**
- Параметры используют **camelCase** (не snake_case): `advertId`, `nmId`.
- Попытка с `advert_id` → `400 "Invalid format for parameter advertId"`.
- Endpoint — **per-item**: один товар за запрос (не batch). Для N товаров нужно N запросов.
- Без активной кампании → `400` с описательной ошибкой.

**Implications for rate limiting:** При 5 req/min и per-item формате — polling 100 товаров займёт 20 минут. Необходимо приоритизировать товары для polling.

#### Response Structure

Ответ содержит два уровня рекомендаций:

**Для товарных карточек (product-level):**

| Field | Type | Unit | Semantics | Confidence |
|-------|------|------|-----------|------------|
| `competitiveBid` | integer | kopecks | Средняя ставка конкурентов в категории | web-verified |
| `leadersBid` | integer | kopecks | Средняя ставка лидеров категории | web-verified |
| `top2` | integer | kopecks | Ставка для попадания в топ позиций | web-verified |

**Для поисковых кластеров (`normQueries`, per search cluster):**

Рекомендации разбиты по уровням охвата:

| Level | Name | Coverage | Confidence |
|-------|------|----------|------------|
| `reachMax` | Максимальный | 76–100% | web-verified |
| `reachMedium` | Средний | 61–75% | web-verified |
| `reachMin` | Минимальный | 50–60% | web-verified |

Для каждого уровня:

| Field | Type | Unit | Semantics | Confidence |
|-------|------|------|-----------|------------|
| `bidKopecks` | integer | kopecks | Рекомендуемая ставка для этого уровня | web-verified |
| `bidKopecksMin` | integer | kopecks | Минимальная ставка для этого уровня | web-verified |

#### Бизнес-сценарий

Signal assembly для autobidding:
- `competitiveBid` → сигнал `competitive_bid` (целевой уровень для удержания позиций)
- `leadersBid` → сигнал `leaders_bid` (агрессивный уровень для захвата топа)
- `bidKopecksMin` → сигнал `min_competitive_bid` (минимально конкурентный уровень)
- Стратегия `ECONOMY_HOLD` сравнивает текущую ставку с `competitiveBid` для принятия решения

---

### 3.2 Текущие ставки по поисковым кластерам — `POST /adv/v0/normquery/get-bids`

Возвращает текущие установленные ставки по поисковым кластерам (ключевым фразам) для конкретного товара в кампании.

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/adv/v0/normquery/get-bids` | confirmed-docs |
| Base URL | `https://advert-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |

#### Rate Limits

| Period | Limit | Interval | Burst | Confidence |
|--------|-------|----------|-------|------------|
| 1 s | 5 requests | 200 ms | 10 | confirmed-docs |

#### Request Body

```json
{
  "items": [
    {
      "advert_id": 1825035,
      "nm_id": 983512347
    }
  ]
}
```

| Field | Type | Required | Max items | Semantics | Confidence |
|-------|------|----------|-----------|-----------|------------|
| `items` | array | yes | 100 | Список пар кампания-товар | confirmed-docs |
| `items[].advert_id` | integer | yes | — | ID кампании | confirmed-docs |
| `items[].nm_id` | integer | yes | — | WB артикул товара | confirmed-docs |

#### Response — 200 OK

```json
{
  "bids": [
    {
      "advert_id": 1825035,
      "bid": 700,
      "nm_id": 983512347,
      "norm_query": "Фраза 1"
    },
    {
      "advert_id": 1825035,
      "bid": 9000,
      "nm_id": 983512347,
      "norm_query": "Фраза 2"
    }
  ]
}
```

| Field | Type | Unit | Semantics | Confidence |
|-------|------|------|-----------|------------|
| `bids` | array | — | Список ставок по кластерам | confirmed-docs |
| `bids[].advert_id` | integer | — | ID кампании | confirmed-docs |
| `bids[].nm_id` | integer | — | WB артикул товара | confirmed-docs |
| `bids[].bid` | integer | kopecks | Текущая ставка в копейках | confirmed-docs |
| `bids[].norm_query` | string | — | Название поискового кластера (фраза) | confirmed-docs |

#### Бизнес-сценарий

- Чтение текущих ставок перед принятием решения (signal `current_bid`)
- Reconciliation: проверка, что ставка была применена после write

---

### 3.3 Установка ставок по поисковым кластерам — `POST /adv/v0/normquery/bids`

Устанавливает ставки для конкретных поисковых кластеров. Используется для fine-grained управления ставками на уровне ключевых фраз.

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `POST` | confirmed-docs |
| Path | `/adv/v0/normquery/bids` | confirmed-docs |
| Base URL | `https://advert-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |
| Ограничение | Только для кампаний с `manual` bid type и `cpm` payment model | confirmed-docs |

#### Rate Limits

| Period | Limit | Interval | Burst | Confidence |
|--------|-------|----------|-------|------------|
| 1 s | 2 requests | 500 ms | 4 | confirmed-docs |

#### Request Body

```json
{
  "bids": [
    {
      "advert_id": 1825035,
      "nm_id": 983512347,
      "norm_query": "Фраза 1",
      "bid": 1000
    }
  ]
}
```

| Field | Type | Required | Max items | Semantics | Confidence |
|-------|------|----------|-----------|-----------|------------|
| `bids` | array | yes | 100 | Массив ставок по кластерам | confirmed-docs |
| `bids[].advert_id` | integer | yes | — | ID кампании | confirmed-docs |
| `bids[].nm_id` | integer | yes | — | WB артикул товара | confirmed-docs |
| `bids[].norm_query` | string | yes | — | Название поискового кластера | confirmed-docs |
| `bids[].bid` | integer | yes | — | Ставка в копейках | confirmed-docs |

#### Response — 200 OK

Пустой ответ при успехе (no body).

#### Error Responses

Аналогичны §2.1 — стандартная error response structure.

#### Бизнес-сценарий

MVP автобиддинга работает на уровне **product** (через `PATCH /api/advert/v1/bids`). Keyword-level управление через этот endpoint — **после MVP**.

---

### 3.4 Удаление ставок поисковых кластеров — `DELETE /adv/v0/normquery/bids`

Удаляет установленные ставки с поисковых кластеров (сбрасывает на минимальную/дефолтную).

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `DELETE` | confirmed-docs |
| Path | `/adv/v0/normquery/bids` | confirmed-docs |
| Base URL | `https://advert-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |
| Ограничение | Только для кампаний с `manual` bid type и `cpm` payment model | confirmed-docs |

#### Rate Limits

| Period | Limit | Interval | Burst | Confidence |
|--------|-------|----------|-------|------------|
| 1 s | 5 requests | 200 ms | 10 | confirmed-docs |

#### Request Body

```json
{
  "bids": [
    {
      "advert_id": 1825035,
      "nm_id": 983512347,
      "norm_query": "Фраза 1",
      "bid": 1000
    }
  ]
}
```

Структура идентична §3.3 (Set Bids).

| Field | Type | Required | Max items | Confidence |
|-------|------|----------|-----------|------------|
| `bids` | array | yes | 100 | confirmed-docs |

#### Бизнес-сценарий

После MVP — возможность «сбросить» кластерные ставки при деактивации autobidding для товара.

---

## 4. CAMPAIGN MANAGEMENT ENDPOINTS

Необходимы для полного bidding lifecycle: паузы, возобновления кампаний.

### 4.1 Запуск кампании — `GET /adv/v0/start`

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | confirmed-docs |
| Path | `/adv/v0/start` | confirmed-docs |
| Base URL | `https://advert-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |

#### Query Parameters

| Param | Type | Required | Semantics | Confidence |
|-------|------|----------|-----------|------------|
| `id` | integer | yes | Campaign ID | confirmed-docs |

#### Rate Limits

| Period | Limit | Interval | Burst | Confidence |
|--------|-------|----------|-------|------------|
| 1 s | 5 requests | 200 ms | 5 | confirmed-docs |

#### Constraints

- Работает для кампаний в статусах `4` (ready to launch) и `11` (paused).
- Требует достаточный бюджет — если бюджет недостаточен, кампания не запустится.

#### Responses

| HTTP Status | Описание | Confidence |
|-------------|----------|------------|
| 200 | Кампания запущена | confirmed-docs |
| 400 | Invalid campaign ID | confirmed-docs |
| 401 | Unauthorized | confirmed-docs |
| 422 | Status not changed (already running, insufficient budget) | confirmed-docs |
| 429 | Too many requests | confirmed-docs |

#### Error Response — 422

```json
{
  "error": "Invalid Advert: invalid advert"
}
```

---

### 4.2 Пауза кампании — `GET /adv/v0/pause`

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | confirmed-docs |
| Path | `/adv/v0/pause` | confirmed-docs |
| Base URL | `https://advert-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |

#### Query Parameters, Rate Limits, Responses

Аналогичны §4.1. Constraints: только для кампаний в статусе `9` (active).

---

### 4.3 Остановка кампании — `GET /adv/v0/stop`

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `GET` | confirmed-docs |
| Path | `/adv/v0/stop` | confirmed-docs |
| Base URL | `https://advert-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |

#### Query Parameters, Rate Limits

Аналогичны §4.1. Constraints: для кампаний в статусах `4`, `9`, `11`.

**IMPORTANT:** Остановка кампании — необратимая операция. Остановленную кампанию (status=7) нельзя возобновить.

---

### 4.4 Управление товарами в кампании — `PATCH /adv/v0/auction/nms`

Добавление и удаление товарных карточек в рекламных кампаниях.

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `PATCH` | confirmed-docs |
| Path | `/adv/v0/auction/nms` | confirmed-docs |
| Base URL | `https://advert-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |

#### Rate Limits

| Period | Limit | Interval | Burst | Confidence |
|--------|-------|----------|-------|------------|
| 1 s | 1 request | 1 s | 1 | confirmed-docs |

#### Request Body

```json
{
  "nms": [
    {
      "advert_id": 12345,
      "nms": {
        "add": [11111111, 44444444],
        "delete": [55555555]
      }
    }
  ]
}
```

| Field | Type | Required | Max items | Semantics | Confidence |
|-------|------|----------|-----------|-----------|------------|
| `nms` | array | yes | 20 | Массив операций | confirmed-docs |
| `nms[].advert_id` | integer | yes | — | ID кампании | confirmed-docs |
| `nms[].nms.add` | integer[] | no | — | nmId товаров для добавления | confirmed-docs |
| `nms[].nms.delete` | integer[] | no | — | nmId товаров для удаления | confirmed-docs |

#### Response — 200 OK

```json
{
  "nms": [
    {
      "advert_id": 12345,
      "nms": {
        "added": [11111111, 44444444],
        "deleted": [55555555]
      }
    }
  ]
}
```

**Note:** При добавлении товара устанавливается текущая минимальная ставка.

#### Бизнес-сценарий

Используется если autobidding должен добавить товар в кампанию перед управлением ставками. **Out of scope for MVP** — MVP работает только с товарами, уже находящимися в кампании.

---

### 4.5 Управление placement — `PUT /adv/v0/auction/placements`

Изменение мест размещения (поиск, рекомендации) для кампаний с ручным управлением.

| Свойство | Значение | Confidence |
|----------|----------|------------|
| Method | `PUT` | confirmed-docs |
| Path | `/adv/v0/auction/placements` | confirmed-docs |
| Base URL | `https://advert-api.wildberries.ru` | confirmed |
| Auth | API Key (header `Authorization`) | confirmed |
| Ограничение | Только для кампаний с `manual` bid type и `cpm` payment | confirmed-docs |

#### Rate Limits

| Period | Limit | Interval | Burst | Confidence |
|--------|-------|----------|-------|------------|
| 1 s | 1 request | 1 s | 1 | confirmed-docs |

#### Request Body

```json
{
  "placements": [
    {
      "advert_id": 12345,
      "placements": {
        "search": true,
        "recommendations": true
      }
    }
  ]
}
```

| Field | Type | Required | Max items | Semantics | Confidence |
|-------|------|----------|-----------|-----------|------------|
| `placements` | array | yes | 50 | Массив настроек placement | confirmed-docs |
| `placements[].advert_id` | integer | yes | — | ID кампании | confirmed-docs |
| `placements[].placements.search` | boolean | yes | — | Включить/выключить поиск | confirmed-docs |
| `placements[].placements.recommendations` | boolean | yes | — | Включить/выключить рекомендации | confirmed-docs |

#### Response — 204

Пустой ответ при успехе.

#### Бизнес-сценарий

**Out of scope for MVP.** Может быть полезно в будущем для оптимизации placement.

---

## 5. READ ENDPOINTS (для signal assembly)

### 5.1 Список кампаний — `GET /api/advert/v2/adverts`

> Полное описание в [promo-advertising-contracts.md §2.1](promo-advertising-contracts.md#21-list-campaigns).

Дополнительные поля, критичные для autobidding:

| Field | Type | Semantics | Confidence |
|-------|------|-----------|------------|
| `bidType` | string | `manual` / `unified` — определяет, можно ли управлять ставками | confirmed-docs |
| `placement` | string | `search` / `recommendations` / `combined` | confirmed-docs |
| `dailyBudget` | integer | Дневной бюджет в копейках | confirmed-docs |
| `status` | integer | Статус кампании (4/7/9/11) | confirmed-docs |

#### Архитектурное решение

Для autobidding необходимо расширить существующий `WbAdvertCampaignDto` полями `bidType` и `placement`.
Текущий DTO (code-verified):

```java
public record WbAdvertCampaignDto(
    long advertId, int type, int status,
    long dailyBudget, String createTime, String changeTime,
    String startTime, String endTime, String name
) {}
```

**Требуется добавить:** `bidType`, `placement`.

---

### 5.2 Fullstats (статистика) — `GET /adv/v3/fullstats`

> Полное описание в [promo-advertising-contracts.md §2.2](promo-advertising-contracts.md#22-full-stats).

Используется для signal assembly: views, clicks, spend, orders, CTR, CPC per product per day.

**Текущий статус (code-verified):** Adapter и DTO полностью реализованы. Расширение не требуется.

---

## 6. RATE LIMIT SUMMARY

| Endpoint | Method | Rate | Interval | Burst | RateLimitGroup |
|----------|--------|------|----------|-------|----------------|
| `/api/advert/v1/bids` | PATCH | 5/s | 200 ms | — | `WB_ADVERT_BIDS` (new) |
| `/api/advert/v1/bids/min` | POST | 20/min | 3 s | 5 | `WB_ADVERT_BIDS_MIN` (new) |
| `/api/advert/v0/bids/recommendations` | GET | 5/min | — | — | `WB_ADVERT_RECOMMENDATIONS` (new) |
| `/adv/v0/normquery/get-bids` | POST | 5/s | 200 ms | 10 | `WB_ADVERT` (existing) |
| `/adv/v0/normquery/bids` (set) | POST | 2/s | 500 ms | 4 | `WB_ADVERT_BIDS` (new) |
| `/adv/v0/normquery/bids` (delete) | DELETE | 5/s | 200 ms | 10 | `WB_ADVERT` (existing) |
| `/adv/v0/start` | GET | 5/s | 200 ms | 5 | `WB_ADVERT` (existing) |
| `/adv/v0/pause` | GET | 5/s | 200 ms | 5 | `WB_ADVERT` (existing) |
| `/adv/v0/stop` | GET | 5/s | 200 ms | 5 | `WB_ADVERT` (existing) |
| `/adv/v0/auction/nms` | PATCH | 1/s | 1 s | 1 | `WB_ADVERT_MANAGEMENT` (new) |
| `/adv/v0/auction/placements` | PUT | 1/s | 1 s | 1 | `WB_ADVERT_MANAGEMENT` (new) |
| `/api/advert/v2/adverts` | GET | 5/60s | — | — | `WB_ADVERT` (existing) |
| `/adv/v3/fullstats` | GET | 5/60s | — | — | `WB_ADVERT` (existing) |

---

## 7. RECONCILIATION STRATEGY

Для autobidding write operations:

1. **Write:** `PATCH /api/advert/v1/bids` → ответ зеркалирует применённые значения.
2. **Primary evidence:** Успешный 200 с matching response body → `SUCCEEDED` с primary confirmation.
3. **Secondary verification:** `POST /adv/v0/normquery/get-bids` → проверить, что ставка реально изменилась.
4. **Reconciliation window:** 30s после write (WB применяет ставки синхронно, но кеш обновляется с задержкой).

---

## 8. IDEMPOTENCY

- `PATCH /api/advert/v1/bids`: Повторный вызов с теми же значениями — безопасен (ставка не меняется, если уже установлена).
- `POST /adv/v0/normquery/bids`: Аналогично — перезапись существующей ставки тем же значением — no-op.
- Campaign management (`start`, `pause`, `stop`): повторный вызов для кампании в неподходящем статусе → 422, но без побочных эффектов.

**Confidence:** assumed — эмпирическая верификация idempotency не проводилась.

---

## 9. ARCHITECTURAL REQUIREMENTS FOR IMPLEMENTATION

### 9.1 Новый adapter для bidding

```
datapulse-bidding/
  adapter/
    wb/
      WbBidCommandAdapter.java       — PATCH /api/advert/v1/bids
      WbBidReadAdapter.java          — recommendations + current bids
      WbCampaignManagementAdapter.java — start/pause/stop
      dto/
        WbSetBidsRequest.java
        WbSetBidsResponse.java
        WbMinBidsRequest.java
        WbMinBidsResponse.java
        WbBidRecommendationsResponse.java
```

### 9.2 Новые RateLimitGroup

Добавить в `RateLimitGroup`:
- `WB_ADVERT_BIDS` — 5/s для `PATCH /api/advert/v1/bids` и `POST /adv/v0/normquery/bids`
- `WB_ADVERT_BIDS_MIN` — 20/min для `POST /api/advert/v1/bids/min`
- `WB_ADVERT_RECOMMENDATIONS` — 5/min для `GET /api/advert/v0/bids/recommendations`
- `WB_ADVERT_MANAGEMENT` — 1/s для `PATCH /adv/v0/auction/nms`, `PUT /adv/v0/auction/placements`

### 9.3 DTO расширение (existing code)

Добавить в `WbAdvertCampaignDto`:
- `bidType` (String)
- `placement` (String)

---

## 10. RESOLVED GAPS (verified 2026-04-12)

| # | Gap | Resolution | Confidence |
|---|-----|-----------|------------|
| ~~U-1~~ | Request format `POST /api/advert/v1/bids/min` | **RESOLVED.** snake_case JSON object: `advert_id`, `nm_ids`, `payment_type`, `placement_types`. Response structure — partially resolved (request OK, response не получен из-за отсутствия товаров в тестовом аккаунте) | **confirmed** (real API) |
| ~~U-2~~ | Query params format `GET /api/advert/v0/bids/recommendations` | **RESOLVED.** camelCase: `advertId`, `nmId`. Per-item endpoint (один товар за запрос) | **confirmed** (real API) |
| ~~U-5~~ | Частота обновления `competitiveBid` | **RESOLVED.** Синхронизация данных: 3 мин. Статусы: 1 мин. Ставки: 30 сек | confirmed-docs (WB API March 2026 Digest) |

## 11. REMAINING UNRESOLVED GAPS

| # | Gap | Impact | Possible resolution |
|---|-----|--------|---------------------|
| U-3 | Поведение при установке ставки ниже минимальной | Предположительно 400, но не подтверждено | Тест с реальной кампанией + write token |
| U-4 | Поведение при ставке = 0 | Интерпретируется ли как "убрать ставку" или ошибка | Тест с реальной кампанией + write token |
| U-6 | Write scope token — нужен для bidding | Текущий боевой токен имеет read-only scope | Выпустить новый токен с Write scope в кабинете WB |
| U-7 | Точная response structure для `POST /api/advert/v1/bids/min` | Request подтверждён, но response не получен (нет товаров в аккаунте) | Тест с аккаунтом, имеющим товары и кампанию |

---

## 12. FUNCTIONAL COVERAGE MATRIX

| Use Case | Endpoint | Status | Phase |
|----------|----------|--------|-------|
| Прочитать список кампаний | `GET /api/advert/v2/adverts` | ✅ Реализован (ETL) | A (existing) |
| Прочитать статистику (views/clicks/spend) | `GET /adv/v3/fullstats` | ✅ Реализован (ETL) | A (existing) |
| Изменить ставку товара в кампании | `PATCH /api/advert/v1/bids` | 📋 Задокументирован | MVP |
| Получить минимальные ставки | `POST /api/advert/v1/bids/min` | ✅ Request verified (response pending) | MVP |
| Получить рекомендованные ставки | `GET /api/advert/v0/bids/recommendations` | ✅ Verified (per-item, camelCase) | MVP |
| Прочитать текущие keyword-ставки | `POST /adv/v0/normquery/get-bids` | 📋 Задокументирован | Post-MVP |
| Установить keyword-ставки | `POST /adv/v0/normquery/bids` | 📋 Задокументирован | Post-MVP |
| Удалить keyword-ставки | `DELETE /adv/v0/normquery/bids` | 📋 Задокументирован | Post-MVP |
| Запустить кампанию | `GET /adv/v0/start` | 📋 Задокументирован | Post-MVP |
| Поставить на паузу | `GET /adv/v0/pause` | 📋 Задокументирован | Post-MVP |
| Остановить кампанию | `GET /adv/v0/stop` | 📋 Задокументирован | Out of scope |
| Управление товарами в кампании | `PATCH /adv/v0/auction/nms` | 📋 Задокументирован | Out of scope |
| Управление placement | `PUT /adv/v0/auction/placements` | 📋 Задокументирован | Out of scope |
