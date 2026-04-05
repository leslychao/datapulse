# Модуль: Advertising

**Фаза:** A–D (поэтапная реализация: Видимость → Мониторинг → Рекомендации → Автоматизация)
**Зависимости:** [ETL Pipeline](etl-pipeline.md) (EventSource, DagDefinition, IngestContext), [Integration](integration.md) (credentials, rate limits, DataDomain), [Analytics & P&L](analytics-pnl.md) (MartProductPnlMaterializer, fact_finance), [Pricing](pricing.md) (ad_cost_ratio signal, guard)
**Runtime:** `datapulse-etl` (EventSource для загрузки), `datapulse-analytics-pnl` (P&L mart materialization), `datapulse-pricing-worker` (ad_cost_ratio signal, guard), `datapulse-audit-alerting` (advertising checkers)

---

## Назначение

Рекламная аналитика — **сквозной сигнал**, влияющий на P&L, операционный грид, алерты и ценообразование. Модуль собирает рекламные данные с маркетплейсов (WB, Ozon), рассчитывает метрики эффективности и интегрирует их во все точки принятия решений.

### Что входит

- Сбор рекламных расходов и метрик с WB Advert API и Ozon Performance API
- Расчёт ДРР, CPO, ROAS, CPC, CTR, CR на уровне товара
- Включение `advertising_cost` в P&L (`mart_product_pnl`)
- Кросс-маркетплейс сравнение рекламной эффективности
- Дашборд рекламных кампаний
- Алерты при аномалиях (ДРР выше порога, реклама без остатков)
- Рекомендации ("стоит / не стоит рекламировать", расчёт макс. CPC)
- Pricing signal `ad_cost_ratio` и guard `ad_cost_drr`

### Что НЕ входит

- Управление рекламными кампаниями (создание, изменение ставок, пауза/запуск)
- Автоматический биддинг
- Бюджетное планирование

### Боли продавца → решения Datapulse

| # | Боль | Решение | Фаза |
|---|------|---------|------|
| 1 | Не знаю реальную прибыль (реклама не в P&L) | P&L с `advertising_cost` | A |
| 2 | Не знаю, что рекламировать (маржа vs ДРР) | Метрики ДРР/CPO + рекомендации | A/C |
| 3 | Не контролирую ДРР (узнаю постфактум) | Алерт `AD_DRR_THRESHOLD` | B |
| 4 | Реклама на товар без остатков | Алерт `AD_NO_STOCK` | B |
| 5 | Не сравниваю эффективность между МП | Кросс-МП таблица ДРР/ROAS | A |
| 6 | Не знаю оптимальную ставку | Расчёт макс. CPC | C |
| 7 | Цена не связана с рекламой | Guard + constraint в pricing | D |
| 8 | Тону в 200 кампаниях | Дашборд кампаний с метриками | A |
| 9 | Unit-экономика непрозрачна | CPO, ROAS на уровне товара | A |
| 10 | Цену снижаю без учёта рекламы | Guard `ad_cost_drr` | D |

---

## Data Flow

Рекламный ETL использует **частичный canonical слой** (DD-AD-1 revised):
- **Кампании** (справочник, десятки-сотни записей) → PostgreSQL canonical (`canonical_advertising_campaign`) + ClickHouse dim
- **Статистика** (факты, тысячи записей/день) → ClickHouse напрямую (без canonical в PostgreSQL)

```
Marketplace API → Raw JSON → S3 (provenance)
                                 ↓
                    canonical_advertising_campaign (PostgreSQL)   ← справочник кампаний
                                 ↓
                    dim_advertising_campaign (ClickHouse)         ← материализация из canonical
                    fact_advertising (ClickHouse)                 ← факты напрямую из Raw
                                 ↓
                    mart_product_pnl (advertising_cost)
                    mart_advertising_product (ДРР/CPO/ROAS)
                                 ↓
                    Grid columns, Campaign Dashboard, Alerts, Pricing Signal
```

### Обоснование DD-AD-1 (revised)

**Кампании в PostgreSQL — почему:**
- Алерты (`AdNoStockChecker`) join-ят кампании с остатками (`canonical_stock_current`) — оба в PostgreSQL, без cross-store
- Дашборд кампаний — pagination, filtering, sorting. PostgreSQL сильнее ClickHouse для этих операций
- Рекомендации join-ят кампании с товарами, ценами, себестоимостью — всё в PostgreSQL
- Закладывает фундамент для будущего управления кампаниями (write-back)
- Паттерн согласован с промо-модулем: `canonical_promo_campaign` (PG) + `dim_promo_campaign` (CH)

**Факты в ClickHouse напрямую — почему:**
- Дневная статистика по товарам — чистая аналитика, объём данных большой
- Ни один decision flow не читает advertising facts из PostgreSQL
- Агрегации (ДРР, CPO, ROAS) — задача для ClickHouse, не для PostgreSQL
- Провенанс: `fact_advertising.job_execution_id` → `job_item.s3_key` → S3 raw payload

---

## Provider API Contracts

Источник правды для API-контрактов — [promo-advertising-contracts.md](../provider-api-specs/promo-advertising-contracts.md). Ниже — ключевые контрактные данные, необходимые для реализации.

### WB Advert API

**Base URL:** `https://advert-api.wildberries.ru`

#### Campaigns list

| Параметр | Значение |
|----------|----------|
| Endpoint | `GET /api/advert/v2/adverts` |
| Auth | Header `Authorization: {token}` |
| Rate limit | `WB_ADVERT` (5 req / 60s, burst 1) |
| Response | `{"adverts": [...]}` — массив campaign objects |
| Key fields | `advertId`, `name`, `type`, `status`, `dailyBudget`, `createTime`, `endTime` |
| Idempotency | Полная перезагрузка списка при каждом sync |

#### Fullstats (v3)

| Параметр | Значение |
|----------|----------|
| Endpoint | `GET /adv/v3/fullstats` |
| Auth | Header `Authorization: {token}` |
| Rate limit | `WB_ADVERT` (shared) |
| Query params | `ids` (max 50 campaign IDs, comma-separated), `beginDate` (YYYY-MM-DD), `endDate` (YYYY-MM-DD) |
| Response hierarchy | `campaign → days[] → apps[] → nms[]` (product-level) |
| Grain | `campaign × date × nmId` — **product-level daily** |
| Key metrics | `views`, `clicks`, `ctr`, `cpc`, `sum` (spend), `atbs`, `orders`, `cr`, `shks`, `sum_price` (revenue), `canceled` |
| Ограничения | Текущий день даёт нули по части метрик — **skip today** |
| Миграция v2→v3 | POST→GET, JSON body→query params, новое поле `canceled` |

**Join key:** `nmId` → `dim_product.marketplace_sku` (WB nmId сохраняется как строка).

### Ozon Performance API

**Base URL:** `https://api-performance.ozon.ru`

#### OAuth2 Token

| Параметр | Значение |
|----------|----------|
| Endpoint | `POST /api/client/token` |
| Body | `grant_type=client_credentials&client_id={id}&client_secret={secret}` |
| Response | `{"access_token": "...", "expires_in": 1800}` |
| TTL | 30 min; кэш 25 min (Caffeine) |
| Credentials | Отдельные `performanceClientId` + `performanceClientSecret` из Vault (через `perfSecretReferenceId`) |

#### Campaigns

| Параметр | Значение |
|----------|----------|
| Endpoint | `GET /api/client/campaign` |
| Auth | Header `Authorization: Bearer {access_token}` |
| Rate limit | `OZON_PERFORMANCE` (уже существует: 60 req/60s, burst 5) |
| Response | Массив campaign objects |
| Key fields | `id`, `title`, `state` (CAMPAIGN_STATE_*), `dailyBudget`, `createdAt`, `endedAt`, `advObjectType` |

#### Statistics (SKU-level spend)

| Параметр | Значение |
|----------|----------|
| Endpoint | `GET /api/client/statistics/campaign/product` |
| Auth | Header `Authorization: Bearer {access_token}` |
| Query params | `campaigns` (campaign IDs), `dateFrom`, `dateTo` |
| Response | SKU-level rows: `sku`, `views`, `clicks`, `spend`, `orders`, `revenue` |
| Grain | `campaign × date × sku` |
| **Join key** | `sku` (Ozon `product_id` as string) → `dim_product.marketplace_sku` |

**DD-AD-5:** Ozon Performance API предоставляет SKU-level spend напрямую через `campaign/product` endpoint. Pro-rata allocation не требуется (DD-AD-8).

---

## Модель данных

### PostgreSQL: canonical_advertising_campaign

Каноническое представление рекламной кампании маркетплейса. Source of truth — `ADVERTISING_FACT` sync из ETL. По аналогии с `canonical_promo_campaign`.

**Owner:** [ETL Pipeline](etl-pipeline.md). Advertising module читает для дашборда, алертов, рекомендаций.

**Dedup key:** `(connection_id, external_campaign_id)` — unique.

```sql
CREATE TABLE canonical_advertising_campaign (
    id                    bigserial PRIMARY KEY,
    connection_id         bigint NOT NULL REFERENCES marketplace_connection(id),
    external_campaign_id  varchar(64) NOT NULL,
    name                  varchar(500),
    campaign_type         varchar(50) NOT NULL,
    status                varchar(50) NOT NULL,
    placement             varchar(100),
    daily_budget          decimal(18, 2),
    start_time            timestamptz,
    end_time              timestamptz,
    created_at_external   timestamptz,
    synced_at             timestamptz NOT NULL DEFAULT now(),
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),

    UNIQUE (connection_id, external_campaign_id)
);

CREATE INDEX idx_adcampaign_connection ON canonical_advertising_campaign(connection_id);
CREATE INDEX idx_adcampaign_status ON canonical_advertising_campaign(connection_id, status);
```

**Маппинг полей по маркетплейсам:**

| Column | WB field | Ozon field |
|--------|----------|------------|
| `external_campaign_id` | `advertId` (toString) | `id` (toString) |
| `name` | `name` | `title` |
| `campaign_type` | `type` (int → string: 4→`CATALOG`, 5→`CARD`, 6→`SEARCH`, 7→`RECO`, 8→`AUTO`, 9→`SEARCH_PLUS_CATALOG`) | `advObjectType` |
| `status` | `status` (int → string: 4→`ready`, 7→`active`, 8→`on_pause`, 9→`archived`, 11→`paused`) | `state` (e.g. `CAMPAIGN_STATE_RUNNING`→`active`) |
| `daily_budget` | `dailyBudget` (копейки → рубли для WB) | `dailyBudget` (рубли) |
| `start_time` | `createTime` | `createdAt` |
| `end_time` | `endTime` | `endedAt` |

**UPSERT pattern:** `INSERT ... ON CONFLICT (connection_id, external_campaign_id) DO UPDATE SET name = EXCLUDED.name, status = EXCLUDED.status, daily_budget = EXCLUDED.daily_budget, ..., synced_at = now(), updated_at = now() WHERE (canonical_advertising_campaign.*) IS DISTINCT FROM (EXCLUDED.*)` — no-churn, как в остальных canonical entities.

**Stale campaign detection:** аналогично `canonical_promo_campaign`. Если кампания не возвращается в sync > 48h → ETL переводит `status = 'archived'`.

### ClickHouse DDL

Полные DDL для fact-таблиц определены в [0005-advertising-tables.sql](../../backend/datapulse-etl/src/main/resources/db/clickhouse/0005-advertising-tables.sql).

### dim_advertising_campaign

Справочник рекламных кампаний в ClickHouse. Материализуется из `canonical_advertising_campaign` (PostgreSQL) через `DimAdvertisingCampaignMaterializer`. Одна строка на campaign per connection.

| Column | Type | Описание |
|--------|------|----------|
| `connection_id` | UInt32 | FK на marketplace_connection |
| `source_platform` | LowCardinality(String) | `WB` / `OZON` |
| `campaign_id` | UInt64 | ID кампании в маркетплейсе |
| `name` | String | Название кампании |
| `campaign_type` | LowCardinality(String) | Тип: `AUTO`, `SEARCH`, `CATALOG`, `PRODUCT_MEDIA`, ... |
| `status` | LowCardinality(String) | Статус: `active`, `paused`, `archived` |
| `placement` | Nullable(String) | Площадка размещения (WB-specific) |
| `daily_budget` | Nullable(Decimal(18,2)) | Дневной бюджет |
| `start_time` | Nullable(DateTime) | Дата старта |
| `end_time` | Nullable(DateTime) | Дата окончания |
| `created_at` | Nullable(DateTime) | Дата создания |
| `ver` | UInt64 | Version для ReplacingMergeTree |

Engine: `ReplacingMergeTree(ver)`, ORDER BY `(connection_id, source_platform, campaign_id)`, PARTITION BY `source_platform`.

#### Маппинг полей по маркетплейсам

| dim column | WB field | Ozon field |
|------------|----------|------------|
| `campaign_id` | `advertId` | `id` |
| `name` | `name` | `title` |
| `campaign_type` | `type` (int → string mapping) | `advObjectType` |
| `status` | `status` (int → string mapping) | `state` (`CAMPAIGN_STATE_RUNNING` → `active`, etc.) |
| `daily_budget` | `dailyBudget` (копейки → рубли) | `dailyBudget` (рубли) |
| `start_time` | `createTime` | `createdAt` |
| `end_time` | `endTime` | `endedAt` |

### fact_advertising

Рекламная статистика per campaign/product/day.

| Column | Type | Описание |
|--------|------|----------|
| `connection_id` | UInt32 | FK на marketplace_connection |
| `source_platform` | LowCardinality(String) | `WB` / `OZON` |
| `campaign_id` | UInt64 | ID кампании |
| `ad_date` | Date | Дата статистики |
| `marketplace_sku` | String | ID товара (WB: nmId as string, Ozon: product_id as string) |
| `views` | UInt64 | Показы |
| `clicks` | UInt64 | Клики |
| `spend` | Decimal(18,2) | Расход |
| `orders` | UInt32 | Заказы через рекламу |
| `ordered_units` | UInt32 | Заказано единиц |
| `ordered_revenue` | Decimal(18,2) | Выручка через рекламу |
| `canceled` | UInt32 | Отменённые заказы (v3, WB only) |
| `ctr` | Float32 | Click-through rate |
| `cpc` | Decimal(18,2) | Cost per click |
| `cr` | Float32 | Conversion rate |
| `job_execution_id` | UInt64 | Provenance: FK на job_execution |
| `ver` | UInt64 | Version для ReplacingMergeTree |
| `materialized_at` | DateTime | Timestamp записи |

Engine: `ReplacingMergeTree(ver)`, ORDER BY `(connection_id, source_platform, campaign_id, ad_date, marketplace_sku)`, PARTITION BY `toYYYYMM(ad_date)`.

**Join keys:** `marketplace_sku` → `dim_product.marketplace_sku` (DD-AD-4). Для WB: `nmId` сохраняется как строка; для Ozon: `product_id` сохраняется как строка. Оба уже присутствуют в `dim_product.marketplace_sku`.

### mart_advertising_product (Phase A-4, новая таблица)

Агрегированная витрина рекламных метрик per product per period. Материализуется `AdvertisingProductMartMaterializer`.

```sql
CREATE TABLE IF NOT EXISTS mart_advertising_product (
    connection_id    UInt32,
    source_platform  LowCardinality(String),
    marketplace_sku  String,
    period           UInt32,                    -- YYYYMM
    spend            Decimal(18, 2),
    impressions      UInt64,
    clicks           UInt64,
    ad_orders        UInt32,
    ad_revenue       Decimal(18, 2),
    total_revenue    Decimal(18, 2),            -- from fact_finance
    drr_pct          Nullable(Decimal(8, 2)),   -- spend / total_revenue * 100
    cpo              Nullable(Decimal(18, 2)),  -- spend / ad_orders
    roas             Nullable(Decimal(8, 2)),   -- ad_revenue / spend
    cpc              Nullable(Decimal(18, 2)),  -- spend / clicks
    ctr_pct          Nullable(Decimal(8, 4)),   -- clicks / impressions * 100
    cr_pct           Nullable(Decimal(8, 4)),   -- ad_orders / clicks * 100
    ver              UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYear(toDate(toString(period) || '01', 'yyyyMMdd'))
ORDER BY (connection_id, source_platform, marketplace_sku, period);
```

**Source:** `fact_advertising` (spend, views, clicks, orders, revenue) + `fact_finance` (total_revenue для ДРР).

**Materialization SQL (ядро):**

```sql
SELECT
    fa.connection_id,
    fa.source_platform,
    fa.marketplace_sku,
    toYYYYMM(fa.ad_date) AS period,
    sum(fa.spend) AS spend,
    sum(fa.views) AS impressions,
    sum(fa.clicks) AS clicks,
    sum(fa.orders) AS ad_orders,
    sum(fa.ordered_revenue) AS ad_revenue,
    coalesce(ff_rev.total_revenue, 0) AS total_revenue,
    if(ff_rev.total_revenue > 0,
       sum(fa.spend) / ff_rev.total_revenue * 100, NULL) AS drr_pct,
    if(sum(fa.orders) > 0,
       sum(fa.spend) / sum(fa.orders), NULL) AS cpo,
    if(sum(fa.spend) > 0,
       sum(fa.ordered_revenue) / sum(fa.spend), NULL) AS roas,
    if(sum(fa.clicks) > 0,
       sum(fa.spend) / sum(fa.clicks), NULL) AS cpc,
    if(sum(fa.views) > 0,
       sum(fa.clicks) / sum(fa.views) * 100, NULL) AS ctr_pct,
    if(sum(fa.clicks) > 0,
       sum(fa.orders) / sum(fa.clicks) * 100, NULL) AS cr_pct
FROM fact_advertising AS fa
LEFT JOIN (
    SELECT connection_id, seller_sku_id, toYYYYMM(finance_date) AS period,
           sum(revenue_amount) AS total_revenue
    FROM fact_finance
    WHERE attribution_level IN ('POSTING', 'PRODUCT')
    GROUP BY connection_id, seller_sku_id, period
) AS ff_rev ON fa.connection_id = ff_rev.connection_id
    AND fa.marketplace_sku = toString(ff_rev.seller_sku_id)
    AND toYYYYMM(fa.ad_date) = ff_rev.period
GROUP BY fa.connection_id, fa.source_platform, fa.marketplace_sku, period, ff_rev.total_revenue
```

**Примечание:** join `marketplace_sku` → `seller_sku_id` выполняется через `dim_product` как промежуточную таблицу. Точный join path уточняется при реализации, т.к. `fact_finance` использует `seller_sku_id` (числовой), а `fact_advertising` — `marketplace_sku` (строковый).

### Разделение PostgreSQL / ClickHouse (DD-AD-1 revised)

| Данные | Хранилище | Обоснование |
|--------|-----------|-------------|
| Кампании (справочник) | PostgreSQL (`canonical_advertising_campaign`) + ClickHouse (`dim_advertising_campaign`) | Бизнес-состояние: алерты, дашборд, рекомендации, будущее управление |
| Статистика (факты) | Только ClickHouse (`fact_advertising`) | Чистая аналитика, большой объём, агрегации |
| Метрики (марты) | Только ClickHouse (`mart_advertising_product`) | Вычисляемые агрегаты |

`dim_advertising_campaign` материализуется из `canonical_advertising_campaign` через `DimAdvertisingCampaignMaterializer` (по аналогии с `DimPromoCampaignMaterializer`). Факты (`fact_advertising`) по-прежнему пишутся напрямую в ClickHouse (DD-AD-1 exception для фактов).

---

## ETL Pipeline

### Infrastructure changes

Изменения в существующих enum-ах и конфигурации для поддержки рекламного ETL.

| Компонент | Файл | Изменение |
|-----------|------|-----------|
| `EtlEventType` | `etl/domain/EtlEventType.java` | Добавить `ADVERTISING_FACT`. `retentionCategory()` → `FLOW` (добавить в `FLOW_EVENTS`) |
| `DagDefinition` | `etl/domain/DagDefinition.java` | Добавить `ADVERTISING_FACT` на Level 2, hard dependency: `PRODUCT_DICT` |
| `DataDomain` | `integration/domain/DataDomain.java` | Добавить `ADVERTISING` |
| `RateLimitGroup` | `integration/domain/ratelimit/RateLimitGroup.java` | Добавить `WB_ADVERT(5.0 / 60.0, 1, MarketplaceType.WB)` |
| `IntegrationProperties` | `integration/config/IntegrationProperties.java` | Добавить `advertBaseUrl` в inner class `Wildberries` |
| `application.yml` | | Добавить `datapulse.integration.wildberries.advert-base-url: https://advert-api.wildberries.ru` |
| Liquibase | | Migration: INSERT `marketplace_sync_state` с `domain = 'ADVERTISING'` для существующих connections |

**DagDefinition изменения (код):**

```
Level 0:  CATEGORY_DICT | WAREHOUSE_DICT
Level 1:  PRODUCT_DICT
Level 2:  PRICE_SNAPSHOT | INVENTORY_FACT | SUPPLY_FACT | SALES_FACT | PROMO_SYNC | ADVERTISING_FACT  ← NEW
Level 3:  FACT_FINANCE
```

`ADVERTISING_FACT` на Level 2 с hard dependency на `PRODUCT_DICT` — рекламные данные нужно join-ить с `dim_product` по `marketplace_sku`, поэтому продукты должны быть загружены первыми.

**ConnectionValidationResultApplier** (DD-AD-7): итерирует `DataDomain.values()` при создании `marketplace_sync_state` записей. Добавление `ADVERTISING` в enum автоматически обеспечивает создание записи для новых и ре-валидированных connections.

### WB EventSource

**Класс:** `WbAdvertisingFactSource implements EventSource`

```
marketplace() = WB
eventType() = ADVERTISING_FACT
```

**Алгоритм:**

1. **Campaigns list:** `GET /api/advert/v2/adverts` → получить все кампании connection
2. **Canonical upsert:** `AdvertisingCampaignRepository.upsertAll(campaigns)` → UPSERT в `canonical_advertising_campaign` (PostgreSQL)
3. **Date range:** `ctx.wbFactDateFrom()` / `ctx.wbFactDateTo()`, **skip текущий день** (WB даёт нули за текущий день)
4. **Batch fullstats:** разбить campaign IDs на батчи по 50 → для каждого батча `GET /adv/v3/fullstats?ids=...&beginDate=...&endDate=...`
5. **S3 capture:** сохранить raw JSON response в S3 (provenance)
6. **Flatten:** `WbAdvertisingFlattener` — преобразовать иерархический JSON (campaign → days → apps → nms) в flat `List<AdvertisingFactRow>`
7. **ClickHouse write:** `AdvertisingClickHouseWriter` — batch INSERT в `fact_advertising`
8. **Stale detection:** campaigns в PG с `synced_at < now() - 48h` и `status IN ('active', 'on_pause')` → `status = 'archived'`

**Sub-sources:** один sub-source `"WbAdvertisingFact"`.

### Ozon EventSource

**Класс:** `OzonAdvertisingFactSource implements EventSource`

```
marketplace() = OZON
eventType() = ADVERTISING_FACT
```

**Алгоритм:**

1. **Credential resolution:** `CredentialResolver` резолвит основные credentials + дополнительно `perfSecretReferenceId` → merge в одну credential map. Если `perfSecretReferenceId == null` → skip `ADVERTISING_FACT` для этого connection
2. **OAuth2 token:** `OzonPerformanceTokenService` → `POST /api/client/token` с `performanceClientId` + `performanceClientSecret`. Token кэшируется в Caffeine (25 min TTL из 30 min TTL)
3. **Campaigns list:** `GET /api/client/campaign` с Bearer token
4. **Canonical upsert:** `AdvertisingCampaignRepository.upsertAll(campaigns)` → UPSERT в `canonical_advertising_campaign` (PostgreSQL)
5. **Statistics:** `GET /api/client/statistics/campaign/product` — SKU-level spend per campaign per date
6. **S3 capture:** raw response → S3
7. **ClickHouse write:** batch INSERT в `fact_advertising`
8. **Stale detection:** аналогично WB

**Date range:** `ctx.ozonFactSince()` / `ctx.ozonFactTo()`.

### Credential Resolution for Ozon Performance

`CredentialResolver` (ETL модуль) — текущая реализация резолвит только `secretReferenceId`. Для Ozon Performance необходимо:

1. При `OZON` marketplace + `ADVERTISING_FACT` event type: дополнительно резолвить `perfSecretReferenceId` из `MarketplaceConnectionEntity`
2. Merge credentials: base credentials (clientId, apiKey) + performance credentials (performanceClientId, performanceClientSecret) в одну map
3. Guard: если `perfSecretReferenceId == null` → log.info + return empty result (skip advertising для этого connection)

Credential keys (уже определены в `CredentialKeys`): `performanceClientId`, `performanceClientSecret`.

### OzonPerformanceTokenService (новый сервис)

```
@Service
- inject: WebClient, Caffeine Cache
- method: getAccessToken(String clientId, String clientSecret) → String
- Cache key: clientId
- Cache TTL: 25 min (из 30 min token TTL)
- HTTP: POST /api/client/token, Content-Type: application/x-www-form-urlencoded
- Error handling: 401 → log.error + throw (credentials invalid); 429 → retry via rate limiter
```

### AdvertisingClickHouseWriter

**Класс:** `AdvertisingClickHouseWriter`

- Инжектирует `clickhouseJdbcTemplate` (доступен через `ClickHouseConfig`)
- ~~`writeCampaigns()`~~ — **убрано**: кампании теперь записываются в `canonical_advertising_campaign` (PostgreSQL) через `AdvertisingCampaignRepository`, а `dim_advertising_campaign` материализуется через `DimAdvertisingCampaignMaterializer`
- Метод `writeFacts(List<AdvertisingFactRow>)` → batch INSERT в `fact_advertising`
- `ver = Instant.now().toEpochMilli()` для ReplacingMergeTree
- Idempotency: ReplacingMergeTree дедупликация по ORDER BY + ver (более поздний ver побеждает)

### WbAdvertisingFlattener

**Класс:** `WbAdvertisingFlattener`

Input: v3 hierarchical JSON response
Output: `List<AdvertisingFactRow>` — flat records

**Маппинг:**

| JSON path | AdvertisingFactRow field | fact_advertising column |
|-----------|-------------------------|------------------------|
| `campaign.advertId` | `campaignId` | `campaign_id` |
| `days[].date` | `adDate` | `ad_date` |
| `days[].apps[].nms[].nmId` | `marketplaceSku` (toString) | `marketplace_sku` |
| `days[].apps[].nms[].views` | `views` | `views` |
| `days[].apps[].nms[].clicks` | `clicks` | `clicks` |
| `days[].apps[].nms[].sum` | `spend` | `spend` |
| `days[].apps[].nms[].atbs` | `atbs` | — (не сохраняется в fact) |
| `days[].apps[].nms[].orders` | `orders` | `orders` |
| `days[].apps[].nms[].cr` | `cr` | `cr` |
| `days[].apps[].nms[].shks` | `orderedUnits` | `ordered_units` |
| `days[].apps[].nms[].sum_price` | `orderedRevenue` | `ordered_revenue` |
| `days[].apps[].nms[].canceled` | `canceled` | `canceled` |

CTR и CPC вычисляются из raw values: `ctr = clicks / views`, `cpc = spend / clicks`.

---

## P&L Integration

### MartProductPnlMaterializer changes (Phase A-3)

**Текущее состояние:**

```sql
-- advertising_cost: Phase B core = 0
toDecimal64(0, 2) AS advertising_cost
```

**Целевое состояние:** `LEFT JOIN` на агрегированные рекламные расходы из `fact_advertising`:

```sql
-- advertising_cost: from fact_advertising (Phase A-3)
coalesce(ad_agg.ad_spend, toDecimal64(0, 2)) AS advertising_cost
```

Где `ad_agg` — подзапрос:

```sql
LEFT JOIN (
    SELECT
        fa.connection_id,
        dp.seller_sku_id,
        toYYYYMM(fa.ad_date) AS period,
        sum(fa.spend) AS ad_spend
    FROM fact_advertising AS fa
    INNER JOIN dim_product AS dp
        ON fa.connection_id = dp.connection_id
        AND fa.marketplace_sku = dp.marketplace_sku
    GROUP BY fa.connection_id, dp.seller_sku_id, period
) AS ad_agg
    ON base.connection_id = ad_agg.connection_id
    AND base.seller_sku_id = ad_agg.seller_sku_id
    AND base.period = ad_agg.period
```

**Join path:**

```
fact_advertising.marketplace_sku
  → dim_product.marketplace_sku (+ connection_id)
    → dim_product.seller_sku_id
      → base.seller_sku_id (mart_product_pnl grain)
```

**full_pnl** формула (обновлённая):

```
full_pnl = marketplace_pnl - advertising_cost - net_cogs
```

Вместо текущего `- toDecimal64(0, 2)` подставляется `- advertising_cost`.

### PnlReadRepository

Существующие P&L endpoints (`/api/analytics/pnl/*`) автоматически начнут показывать `advertising_cost` после обновления материализатора — поле `advertising_cost` уже включено в `mart_product_pnl` DDL и в read model.

---

## Advertising Analytics

Расчётные метрики рекламной эффективности для Level 1 (Видимость).

### Метрики

| Метрика | Формула | Описание | Для продавца |
|---------|---------|----------|--------------|
| **ДРР** (доля рекламных расходов) | `spend / total_revenue × 100%` | Главный KPI рекламы | "Сколько % выручки уходит на рекламу" |
| **CPO** (cost per order) | `spend / ad_orders` | Стоимость привлечения заказа | "Сколько стоит один рекламный заказ" |
| **ROAS** (return on ad spend) | `ad_revenue / spend` | Возврат на вложения | "Сколько рублей выручки на 1₽ рекламы" |
| **CPC** (cost per click) | `spend / clicks` | Стоимость клика | "Сколько плачу за клик" |
| **CTR** (click-through rate) | `clicks / impressions × 100%` | Кликабельность | "Какой % видящих рекламу кликают" |
| **CR** (conversion rate) | `ad_orders / clicks × 100%` | Конверсия | "Какой % кликнувших заказывают" |

### DimAdvertisingCampaignMaterializer (Phase A-1/A-2)

Materializer для `dim_advertising_campaign` — переносит кампании из `canonical_advertising_campaign` (PostgreSQL) в ClickHouse. По аналогии с `DimPromoCampaignMaterializer`.

- `tableName()` → `"dim_advertising_campaign"`
- `phase()` → `MaterializationPhase.DIM`
- `order()` → 3 (после `DimPromoCampaignMaterializer`)
- `materializeFull()` → чтение всех campaigns из PG → batch INSERT в ClickHouse с `ver = now()`
- Запускается при `PostIngestMaterializationMessageHandler` после `ADVERTISING_FACT` sync

**Альтернатива:** если dim-таблица в ClickHouse не используется напрямую (алерты и дашборд читают из PG), materializer можно отложить до момента, когда ClickHouse-запросы потребуют join с dim. P&L materializer использует `fact_advertising` → `dim_product` → `seller_sku_id`, не dim_campaign. Однако для единообразия с прomo-паттерном (dim → mart → grid) рекомендуется создать.

### AdvertisingProductMartMaterializer (Phase A-4)

Новый materializer, аналогичный `MartProductPnlMaterializer`. Реализует `AnalyticsMaterializer`.

- `tableName()` → `"mart_advertising_product"`
- `phase()` → `MaterializationPhase.MART`
- `order()` → 2 (после `MartProductPnlMaterializer`)
- `materializeFull()` → full swap через `MaterializationJdbc.fullMaterializeWithSwap()`
- SQL: агрегация `fact_advertising` + join `fact_finance` для `total_revenue` (см. DDL `mart_advertising_product` выше)

### Кросс-маркетплейс сравнение (боль #5)

Данные в `mart_advertising_product` содержат `connection_id` + `source_platform`. Один `seller_sku` может иметь offers на разных маркетплейсах. Сравнение — query-time join через `dim_product`:

```sql
SELECT
    dp.seller_sku_id,
    map.source_platform,
    map.spend, map.drr_pct, map.roas, map.cpo
FROM mart_advertising_product AS map
INNER JOIN dim_product AS dp
    ON map.connection_id = dp.connection_id
    AND map.marketplace_sku = dp.marketplace_sku
WHERE dp.seller_sku_id IN (:skuIds)
ORDER BY dp.seller_sku_id, map.source_platform
```

Отдельная витрина не нужна (DD-AD-10 scope).

### Integration с Grid ([Seller Operations](seller-operations.md))

Новые колонки в операционном гриде:

| Column | Source | Store | Описание |
|--------|--------|-------|----------|
| `ad_spend_30d` | `mart_advertising_product` (latest period) | ClickHouse | Расход на рекламу за 30 дней |
| `drr_30d_pct` | `mart_advertising_product.drr_pct` | ClickHouse | ДРР за 30 дней |
| `ad_cpo` | `mart_advertising_product.cpo` | ClickHouse | Cost Per Order |
| `ad_roas` | `mart_advertising_product.roas` | ClickHouse | ROAS |

Колонки **скрыты по умолчанию**. Видимы в system view "Реклама". Загружаются через ClickHouse enrichment query (аналогично существующим `revenue_30d`, `net_pnl_30d`, `velocity_14d`).

### Дашборд кампаний (боль #8)

Отдельная страница со списком рекламных кампаний по всем маркетплейсам.

**Primary source:** `canonical_advertising_campaign` (PostgreSQL) — pagination, filtering, sorting.
**Enrichment:** `fact_advertising` (ClickHouse) — агрегированные метрики за 7/30 дней.

Паттерн: основной список читается из PostgreSQL с пагинацией. Для отображённых campaign_id — один ClickHouse-запрос для enrichment метрик (аналог grid enrichment pattern).

**Колонки дашборда:**

| Column | Source | Store | Описание |
|--------|--------|-------|----------|
| Кампания | `canonical_advertising_campaign.name` | PostgreSQL | Название |
| МП | `connection.marketplace_type` | PostgreSQL | WB / OZON |
| Тип | `campaign_type` | PostgreSQL | AUTO / SEARCH / CATALOG |
| Статус | `status` | PostgreSQL | active / paused / archived |
| Бюджет/день | `daily_budget` | PostgreSQL | Дневной бюджет |
| Расход за период | `SUM(fa.spend) WHERE ad_date IN period` | ClickHouse | Факт расход |
| Заказы за период | `SUM(fa.orders) WHERE ad_date IN period` | ClickHouse | Заказы через рекламу |
| ДРР за период | computed from CH data | ClickHouse | ДРР |
| Тренд ДРР | current vs previous period | ClickHouse | ↑ / ↓ / → |

**DD-AD-10:** Дашборд кампаний — **read-only**. Никаких write-операций через API. Управление кампаниями вне scope модуля.

---

## Alerting Integration

Интеграция с модулем [Audit & Alerting](audit-alerting.md). Новые alert types в alert type registry.

### Новые scheduled checkers

По аналогии с существующими `StaleDataChecker`, `MismatchChecker`, `SpikeDetectionChecker` — новые checker-ы, реализующие интерфейс `AlertChecker` (Strategy + Registry через `AlertCheckerRegistry`).

| alert_type | Source | Severity | blocks_automation | Checker class | Описание | Фаза |
|------------|--------|----------|-------------------|---------------|----------|------|
| `AD_DRR_THRESHOLD` | fact_advertising + fact_finance | WARNING | false | `AdDrrThresholdChecker` | ДРР по товару превысил порог | B-1 |
| `AD_NO_STOCK` | fact_advertising + canonical_stock_current (PG) | WARNING | false | `AdNoStockChecker` | Кампания активна + товар 0 остатков + spend > 0 | B-2 |
| `AD_INEFFICIENT_CAMPAIGN` | fact_advertising | INFO | false | `AdInefficientCampaignChecker` | CTR < threshold или CR < threshold при spend > min | B-3 |
| `AD_PRICE_DROP_HIGH_DRR` | price_decision (PG) + fact_advertising | WARNING | false | `AdPriceDropDrrChecker` | Цена снижена при ДРР > порога | B-4 |

### Механизм

Существующий паттерн:
1. Checker реализует `AlertChecker`: `ruleType()` → `"AD_DRR_THRESHOLD"`, `check(AlertRuleResponse rule)` → проверка + создание/авто-резолв алертов
2. `AlertCheckerRegistry` автоматически подбирает новые checker-ы через `List<AlertChecker>` injection
3. `AlertCheckerScheduler` вызывает все зарегистрированные checker-ы по cron
4. Пользователь настраивает `alert_rule` через UI (пороги, severity, кого уведомлять)

### Cross-domain joins

Checker-ам нужны данные из разных источников:
- `AdDrrThresholdChecker`: **только ClickHouse** (fact_advertising + fact_finance)
- `AdNoStockChecker`: **PostgreSQL** (`canonical_advertising_campaign` + `canonical_stock_current`) + **ClickHouse** (fact_advertising для spend > 0 проверки). Кампании теперь в PostgreSQL — join с остатками без cross-store. Для spend-фильтра: один запрос в ClickHouse за campaign_id с spend > 0 за сегодня, затем PostgreSQL join
- `AdInefficientCampaignChecker`: **только ClickHouse** (fact_advertising агрегация за 7 дней)
- `AdPriceDropDrrChecker`: **PostgreSQL** (price_decision) + **ClickHouse** (fact_advertising) — cross-store

**DD-AD-11:** Cross-store checkers допускают eventual consistency. Checker запускается периодически (cron), small lag между ClickHouse и PostgreSQL приемлем — это мониторинг, не транзакционная логика.

### Настраиваемые пороги

Пользователь настраивает через `alert_rule.config` (JSONB):

| Параметр | Тип | Default | Описание |
|----------|-----|---------|----------|
| `drr_threshold_pct` | BigDecimal | 15.0 | Порог ДРР для `AD_DRR_THRESHOLD` |
| `drr_lookback_days` | int | 7 | Период расчёта ДРР |
| `inefficient_min_spend` | BigDecimal | 1000.0 | Мин. расход для `AD_INEFFICIENT_CAMPAIGN` |
| `inefficient_ctr_threshold` | BigDecimal | 1.0 | CTR ниже которого кампания неэффективна |
| `inefficient_cr_threshold` | BigDecimal | 2.0 | CR ниже которого кампания неэффективна |

### Примеры уведомлений для продавца

**AD_DRR_THRESHOLD:**
> Товар "Футболка мужская оверсайз": ДРР за последние 7 дней = 22% (порог: 15%). Рекламный расход: 3 500₽, выручка: 15 900₽.

**AD_NO_STOCK:**
> Товар "Купальник размер S" — 0 шт. на складе, кампания "Купальники — поиск" продолжает тратить бюджет. За сегодня потрачено 450₽.

**AD_INEFFICIENT_CAMPAIGN:**
> Кампания "Зимние куртки" за 7 дней: 15 000 показов, 45 кликов (CTR 0.3%), 0 заказов. Расход: 2 100₽.

**AD_PRICE_DROP_HIGH_DRR:**
> Цена на "Футболка мужская" снижена с 1 500₽ до 1 200₽ (-20%). При текущем ДРР 15% ожидаемый ДРР вырастет до 19%.

---

## Recommendations

### "Стоит / не стоит рекламировать" (боль #2)

Логика: сравнить маржинальность товара с текущим или прогнозируемым ДРР.

**Формула:**

```
potential_drr = avg_cpc_category / (avg_cr_category × price)
если margin_pct > potential_drr + buffer → WORTH_ADVERTISING
если margin_pct < current_drr → NOT_WORTH_ADVERTISING
если insufficient data → INSUFFICIENT_DATA
```

**Источники:**
- `margin_pct`: из `mart_product_pnl` или computed `(price - cost) / price`
- `avg_cpc_category`: среднее CPC по категории из `fact_advertising` (кросс-продуктовая статистика по connection)
- `avg_cr_category`: средний CR по категории из `fact_advertising`

**DD-AD-12:** MVP рекомендации — простые пороговые правила. ML-модели (прогноз CPC, прогноз CR по категориям) — Phase E+.

### Расчёт максимально допустимого CPC (боль #6)

**Формула:**

```
допустимый расход на 1 заказ = price × target_drr_pct
max_cpc = допустимый расход на 1 заказ × avg_cr
```

Пример: цена 2 000₽, целевой ДРР 10%, avg CR 5%:

```
допустимый расход на 1 заказ = 2 000 × 0.10 = 200₽
max_cpc = 200 × 0.05 = 10₽
```

**DD-AD-13:** max_cpc — информативная рекомендация, не императивное ограничение. Система показывает значение, но не управляет ставками.

### Recommendation enum

| Значение | Описание | Условие |
|----------|----------|---------|
| `WORTH_ADVERTISING` | Стоит рекламировать | margin_pct > estimated_drr + 5pp buffer |
| `NOT_WORTH_ADVERTISING` | Реклама убыточна | margin_pct < current_drr |
| `REDUCE_BID` | Снизить ставку | current_cpc > max_cpc |
| `INSUFFICIENT_DATA` | Недостаточно данных | < 7 дней рекламной статистики или нет cost profile |

---

## Pricing Integration

Три уровня интеграции с модулем [Pricing](pricing.md): signal, guard, constraint.

### Signal: `ad_cost_ratio` (Phase D-1)

**Бизнес-сценарий:**
1. Продавец настраивает ценовую стратегию `TARGET_MARGIN` для товара
2. В параметрах стратегии включает `include_ad_cost = true`
3. Система каждый pricing run считает `ad_cost_ratio`:
   `SUM(fact_advertising.spend) / NULLIF(SUM(fact_finance.revenue_amount), 0)` за 30 дней
4. Ratio добавляется к `effective_cost_rate` (commission + logistics + returns + **ads**)
5. Формула `COGS / (1 - targetMargin - effectiveCostRate)` автоматически поднимает цену при росте рекламных расходов
6. Constraint `min_margin` также учитывает `ad_cost_ratio` через `effectiveCostRate`

**Текущее состояние в коде (уже реализовано, НЕ менять):**

| Компонент | Файл | Состояние |
|-----------|------|-----------|
| `PricingSignalSet.adCostRatio()` | `pricing/domain/PricingSignalSet.java` | Поле определено, сейчас `null` |
| `TargetMarginParams.includeAdCost` | `pricing/domain/TargetMarginParams.java` | Boolean toggle, default `false` |
| `TargetMarginStrategy.calculate()` | `pricing/domain/strategy/TargetMarginStrategy.java` | Использует `adCostRatio` если `includeAdCost = true` |
| `PricingConstraintResolver.computeEffectiveCostRate()` | `pricing/domain/PricingConstraintResolver.java` | Включает `adCostRatio` в расчёт если not null |
| Спецификация | [pricing.md](pricing.md) § Signal: ad_cost_ratio | Полная спецификация с SQL, fallbacks |

**Что нужно реализовать:**

1. **`PricingClickHouseReadRepository`** (новый класс в `pricing/persistence/`):
   - Инжектирует `clickhouseJdbcTemplate`
   - `Map<String, BigDecimal> findAdCostRatios(List<String> marketplaceSkus, int lookbackDays)`
   - SQL:
     ```sql
     SELECT
         fa.marketplace_sku,
         sum(fa.spend) / nullIf(sum(ff.revenue_amount), 0) AS ad_cost_ratio
     FROM fact_advertising AS fa
     INNER JOIN dim_product AS dp
         ON fa.connection_id = dp.connection_id
         AND fa.marketplace_sku = dp.marketplace_sku
     LEFT JOIN fact_finance AS ff
         ON dp.connection_id = ff.connection_id
         AND dp.seller_sku_id = ff.seller_sku_id
         AND toYYYYMM(fa.ad_date) = toYYYYMM(ff.finance_date)
     WHERE fa.marketplace_sku IN (:skus)
         AND fa.ad_date >= today() - :lookbackDays
         AND ff.finance_date >= today() - :lookbackDays
         AND ff.attribution_level IN ('POSTING', 'PRODUCT')
     GROUP BY fa.marketplace_sku
     ```
   - Batch: один запрос на все SKU в pricing run

2. **`PricingSignalCollector` изменения:**
   - Добавить зависимость: `PricingClickHouseReadRepository`
   - Получить `marketplace_sku` для каждого offerId через `PricingDataReadRepository`
   - Вызвать `findAdCostRatios(skus, 30)`
   - Замapпить результат в `PricingSignalSet.adCostRatio` (вместо текущего `null`)

**DD-AD-9:** `ad_cost_ratio` реализуется через отдельный `PricingClickHouseReadRepository`, не через `PricingDataReadRepository` (PostgreSQL-only). Этот repository также будет использоваться для будущих ClickHouse-based signals (`avgCommissionPct`, `avgLogisticsPerUnit`, `returnRatePct`).

**Fallback behavior:**
- `include_ad_cost = false` (default) → signal не участвует, `adCostRatio` может быть null
- `include_ad_cost = true`, нет рекламных данных → `ad_cost_ratio = 0` (conservative)
- `include_ad_cost = true`, нет выручки → `ad_cost_ratio = NULL` → strategy `HOLD`

### Guard: `ad_cost_drr` (Phase D-2)

**Бизнес-сценарий:**
1. Автоматическая стратегия хочет **снизить** цену на товар
2. Guard проверяет: ДРР за последние 7 дней > порога?
3. Если да → **блокирует** снижение цены
4. В журнале решений: "Снижение цены заблокировано. ДРР = 22% (порог: 15%)."

**Новый класс: `AdCostGuard implements PricingGuard`**

```
guardName() → "ad_cost_drr"
order() → 70 (после существующих guards)
check(PricingSignalSet signals, BigDecimal targetPrice, GuardConfig config):
  - if !config.isAdCostGuardEnabled() → GuardResult.pass("ad_cost_drr")
  - if signals.currentPrice() == null || targetPrice >= signals.currentPrice() → pass (не блокируем повышение)
  - if signals.adCostRatio() == null → pass (нет данных — не блокируем)
  - if signals.adCostRatio() > config.effectiveAdCostDrrThreshold() → block
  - else → pass
```

**DD-AD-14:** Guard блокирует **только снижение** цены. Повышение цены при высоком ДРР — желательное поведение (снижает ДРР).

**GuardConfig изменения:**
- Добавить `@JsonProperty("ad_cost_guard_enabled") Boolean adCostGuardEnabled`
- Добавить `@JsonProperty("ad_cost_drr_threshold_pct") BigDecimal adCostDrrThresholdPct`
- `isAdCostGuardEnabled()` → default `false` (opt-in)
- `effectiveAdCostDrrThreshold()` → default `0.15` (15%)

**Message key:** `pricing.guard.ad_cost_drr.blocked`
**ru.json:** `"Снижение цены заблокировано: ДРР {{drrPct}}% (порог: {{threshold}}%). Рекомендация: скорректировать рекламный бюджет."`

### Constraint: минимальная цена с учётом рекламы

`PricingConstraintResolver.computeEffectiveCostRate()` **уже** включает `adCostRatio` в расчёт:

```java
if (signals.adCostRatio() != null) {
    rate = rate.add(signals.adCostRatio());
}
```

Это означает, что constraint минимальной маржи (`min_margin`) автоматически учитывает рекламные расходы, когда `adCostRatio` заполнен. Дополнительный constraint не нужен.

**DD-AD-15:** Отдельный constraint "минимальная цена с учётом рекламы" не создаём. Существующий `effectiveCostRate` + `min_margin` достаточен. Нет дублирования логики.

### Пользовательский flow (UI)

1. Settings → Pricing Strategy → TARGET_MARGIN params → checkbox "Учитывать рекламные расходы" (`include_ad_cost`)
2. Settings → Guard config → checkbox "Блокировать снижение при высоком ДРР" (`ad_cost_guard_enabled`) + порог `ad_cost_drr_threshold_pct`
3. В explanation pricing decision: строка `ads=X.X%` в `effectiveCostRate` breakdown
4. В `signal_snapshot` (JSONB): `adCostRatio: 0.15` (или null)

---

## REST API

### Phase A endpoints (Видимость)

**`GET /api/advertising/campaigns`** — дашборд кампаний

| Параметр | Тип | Описание |
|----------|-----|----------|
| `connectionIds` | List\<Long\> | Фильтр по подключениям |
| `period` | String | `7d` / `30d` |
| `status` | String | `active` / `paused` / `archived` (optional) |
| `sort` | String | Поле сортировки |
| Pageable | | page, size |

Response: `Page<CampaignSummaryResponse>` с полями: `campaignId`, `name`, `sourcePlatform`, `campaignType`, `status`, `dailyBudget`, `spendForPeriod`, `ordersForPeriod`, `drrPct`, `drrTrend`.

**`GET /api/advertising/product-metrics`** — метрики на уровне товара

| Параметр | Тип | Описание |
|----------|-----|----------|
| `offerIds` | List\<Long\> | Offer IDs |
| `period` | String | `7d` / `30d` |

Response: `List<ProductAdMetricsResponse>` с полями: `offerId`, `marketplaceSku`, `sourcePlatform`, `spend`, `drrPct`, `cpo`, `roas`, `cpc`, `ctrPct`, `crPct`.

### Phase B (Alerting)

Нет новых endpoints. Алерты доступны через существующий `GET /api/alerts/*`. Новые `alert_type`: `AD_DRR_THRESHOLD`, `AD_NO_STOCK`, `AD_INEFFICIENT_CAMPAIGN`, `AD_PRICE_DROP_HIGH_DRR`.

### Phase C (Рекомендации)

**`GET /api/advertising/recommendations`**

| Параметр | Тип | Описание |
|----------|-----|----------|
| `offerIds` | List\<Long\> | Offer IDs |

Response:

```json
[
  {
    "offerId": 1,
    "recommendation": "WORTH_ADVERTISING",
    "marginPct": 35.0,
    "currentDrrPct": null,
    "estimatedDrrPct": 6.0,
    "maxCpc": 10.5,
    "reasoning": "advertising.recommendation.worth"
  }
]
```

### Phase D (Pricing)

Нет новых endpoints. Pricing integration работает через существующие pricing endpoints. Guard и signal конфигурируются через pricing strategy / guard config API.

---

## WireMock & Testing

### Существующие стабы

| Файл | Endpoint | Статус |
|------|----------|--------|
| `infra/wiremock/mappings/wb-advertising-campaigns.json` | `GET /api/advert/v2/adverts` | Корректен |
| `infra/wiremock/mappings/wb-advertising-fullstats.json` | `GET /adv/v3/fullstats` | Корректен (v3 format) |

### Необходимые дополнения

- WB fullstats: добавить `queryParameters` матчинг для integration tests
- Ozon Performance: стабы для `POST /api/client/token`, `GET /api/client/campaign`, `GET /api/client/statistics/campaign/product`
- Body files: `infra/wiremock/__files/ozon-performance-token.json`, `ozon-performance-campaigns.json`, `ozon-performance-stats.json`

### Unit tests

| Класс | Что тестировать |
|-------|-----------------|
| `WbAdvertisingFlattener` | Hierarchical JSON → flat rows, пустые days, пустые nms |
| `AdvertisingCampaignRepository` | UPSERT idempotency, stale detection |
| `AdvertisingClickHouseWriter` | Batch INSERT SQL correctness |
| `AdDrrThresholdChecker` | Threshold logic, auto-resolve |
| `AdNoStockChecker` | PG join (canonical_advertising_campaign + canonical_stock_current), spend filter |
| `AdCostGuard` | Block on decrease + high DRR, pass on increase, pass when disabled |

### Integration tests

- `WbAdvertisingFactSource`: WireMock → S3 → PostgreSQL (canonical) + ClickHouse (facts) end-to-end
- `OzonAdvertisingFactSource`: WireMock (token + campaigns + stats) → S3 → PostgreSQL (canonical) + ClickHouse (facts)
- `AdvertisingCampaignRepository`: UPSERT, stale detection, unique constraint
- `MartProductPnlMaterializer`: verify `advertising_cost > 0` after ad data loaded

---

## Design Decisions

| ID | Решение | Статус |
|----|---------|--------|
| **DD-AD-1** | **Partial canonical**: кампании → PostgreSQL (`canonical_advertising_campaign`) + ClickHouse (`dim_advertising_campaign`), факты → ClickHouse directly. Кампании — бизнес-состояние (алерты, дашборд, рекомендации); факты — чистая аналитика. По аналогии с промо-паттерном. | REVISED |
| **DD-AD-2** | Separate `dim_advertising_campaign` — материализуется из `canonical_advertising_campaign` через `DimAdvertisingCampaignMaterializer`. | RESOLVED |
| **DD-AD-3** | Naming `fact_advertising` (не `fact_advertising_costs`) — таблица содержит не только costs, но и views/clicks/orders. | RESOLVED |
| **DD-AD-4** | WB `nmId` as join key → `dim_product.marketplace_sku`. nmId сохраняется как строка в `marketplace_sku`. | RESOLVED |
| **DD-AD-5** | Ozon SKU-level spend — `GET /api/client/statistics/campaign/product` даёт SKU-level данные. | RESOLVED |
| **DD-AD-6** | `EtlEventType` → `DataDomain` mapping: прямого маппинга нет. `IngestResultReporter` обновляет все domains connection bulk. Добавление `ADVERTISING` в `DataDomain` достаточно. | RESOLVED |
| **DD-AD-7** | `ConnectionValidationResultApplier` автоматически подхватывает `ADVERTISING` через `DataDomain.values()`. | RESOLVED |
| **DD-AD-8** | Pro-rata allocation не нужен — оба маркетплейса предоставляют product-level spend. | RESOLVED |
| **DD-AD-9** | `ad_cost_ratio` signal — через отдельный `PricingClickHouseReadRepository`, не через `PricingDataReadRepository` (PostgreSQL-only). | RESOLVED |
| **DD-AD-10** | Campaign dashboard — read-only. Primary source: PostgreSQL (pagination/filter), enrichment: ClickHouse (metrics). No write operations. | RESOLVED |
| **DD-AD-11** | Cross-store checkers (ClickHouse + PostgreSQL) допускают eventual consistency. Мониторинг, не транзакция. | RESOLVED |
| **DD-AD-12** | MVP рекомендации — пороговые правила. ML (прогноз CPC/CR) — Phase E+. | RESOLVED |
| **DD-AD-13** | `max_cpc` — информативная рекомендация, не императив. Не управляем ставками. | RESOLVED |
| **DD-AD-14** | Guard `ad_cost_drr` блокирует только **снижение** цены. Повышение при высоком ДРР — желательное поведение. | RESOLVED |
| **DD-AD-15** | Отдельный constraint "мин. цена с рекламой" не нужен. `adCostRatio` в `effectiveCostRate` + `min_margin` достаточно. | RESOLVED |

---

## Фазовое разделение

4 бизнес-фазы. Каждая опирается на предыдущую — нельзя делать алерты без данных, нельзя делать автоматизацию без метрик.

### Phase A — Видимость (MVP)

Ценность уже есть: продавец видит полную картину.

| Step | Что делаем | Зависимость |
|------|-----------|-------------|
| A-0 | Infra: `canonical_advertising_campaign` Liquibase migration, `AdvertisingCampaignEntity`, `AdvertisingCampaignRepository` | — |
| A-1 | WB ETL: `WbAdvertisingFactSource`, `WbAdvertisingFlattener`, canonical upsert, `AdvertisingClickHouseWriter` | A-0, `EtlEventType`, `DagDefinition`, `RateLimitGroup`, `IntegrationProperties` |
| A-2 | Ozon ETL: `OzonAdvertisingFactSource`, `OzonPerformanceTokenService`, canonical upsert, credential resolution | A-0, `CredentialResolver`, `perfSecretReferenceId` |
| A-3 | P&L Integration: `MartProductPnlMaterializer` LEFT JOIN `fact_advertising` | A-1 / A-2 (данные в CH) |
| A-4 | Advertising Analytics: `mart_advertising_product` DDL + `AdvertisingProductMartMaterializer` + `DimAdvertisingCampaignMaterializer` | A-1 / A-2 |
| A-5 | Grid columns: `ad_spend_30d`, `drr_30d_pct`, `ad_cpo`, `ad_roas` в Grid read model | A-4 |
| A-6 | Campaign Dashboard: REST API (PG pagination + CH enrichment) + frontend page | A-0, A-1 / A-2 |

**Prerequisite chain:** A-0 → A-1/A-2 (параллельно) → A-3/A-4 (параллельно) → A-5/A-6 (параллельно)

### Phase B — Мониторинг

Предотвращение потерь: система сама замечает проблемы.

| Step | Что делаем | Зависимость |
|------|-----------|-------------|
| B-1 | `AdDrrThresholdChecker` — ДРР выше порога | Phase A |
| B-2 | `AdNoStockChecker` — реклама на товар без остатков | Phase A |
| B-3 | `AdInefficientCampaignChecker` — CTR/CR ниже порога | Phase A |
| B-4 | `AdPriceDropDrrChecker` — цена снижена при высоком ДРР | Phase A |

### Phase C — Рекомендации

Экспертиза в коробке: система подсказывает, что делать.

| Step | Что делаем | Зависимость |
|------|-----------|-------------|
| C-1 | "Стоит / не стоит рекламировать" логика | Phase A (метрики) |
| C-2 | Расчёт max CPC | Phase A |
| C-3 | REST API рекомендаций | C-1, C-2 |
| C-4 | Кросс-МП сравнение эффективности (UI) | Phase A |

### Phase D — Автоматизация

Pricing integration: система действует сама.

| Step | Что делаем | Зависимость |
|------|-----------|-------------|
| D-1 | `PricingClickHouseReadRepository` + `PricingSignalCollector` integration (`ad_cost_ratio` signal) | Phase A (данные в CH) |
| D-2 | `AdCostGuard` — блокирует снижение цены при высоком ДРР | D-1 |
| D-3 | Strategy rules "ЕСЛИ ДРР > X% N дней → повысить на Y%" | D-1 (Phase E+, advanced) |

### Общая зависимость

```
Phase A (Видимость) → Phase B (Мониторинг) → Phase C (Рекомендации)
                   ↘ Phase D (Автоматизация)
```

---

## Open Questions

| # | Вопрос | Статус | Влияние |
|---|--------|--------|---------|
| OQ-1 | Ozon Performance API: empirical verification с реальными credentials | OPEN | Phase A-2 |
| OQ-2 | Ozon: exact CSV/JSON format `campaign/product` statistics response | OPEN | Phase A-2 |
| OQ-3 | Campaign dashboard: нужна ли пагинация? Сколько кампаний у среднего продавца? | OPEN | Phase A-6 |
| OQ-4 | Recommendations: lookback период для `avg_cpc_category` и `avg_cr_category`? | OPEN | Phase C |

---

## Связанные модули

| Модуль | Связь | Что обновить |
|--------|-------|--------------|
| [ETL Pipeline](etl-pipeline.md) | `EventSource`, `DagDefinition`, DD-AD-1 revised exception | Обновить DD-AD-1 — partial canonical |
| [Integration](integration.md) | `DataDomain`, `CredentialResolver`, `perfSecretReferenceId`, `RateLimitGroup` | Добавить `ADVERTISING` domain, `WB_ADVERT` rate limit |
| [Analytics & P&L](analytics-pnl.md) | `MartProductPnlMaterializer`, `fact_finance` join | Обновить P&L формулу |
| [Pricing](pricing.md) | `ad_cost_ratio` signal, guard, constraint | Уже специфицирован в pricing.md |
| [Seller Operations](seller-operations.md) | Grid columns, detail panel | Добавить рекламные колонки |
| [Audit & Alerting](audit-alerting.md) | Alert types, checkers | Добавить `AD_*` alert types |
| [data-model.md](../data-model.md) | PostgreSQL (`canonical_advertising_campaign`) + ClickHouse entities | Добавить canonical + dim/fact/mart tables |
