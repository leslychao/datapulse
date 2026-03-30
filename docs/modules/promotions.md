# Модуль: Promotions

**Фаза:** F — Promotions Management
**Зависимости:** [ETL Pipeline](etl-pipeline.md) (promo discovery), [Analytics & P&L](analytics-pnl.md) (margin signals), [Pricing](pricing.md) (promo guard coordination), [Integration](integration.md) (write-адаптеры)
**Runtime:** datapulse-api (policy CRUD, manual decisions), datapulse-pricing-worker (evaluation pipeline)

---

## Назначение

Управление участием товаров в промо-акциях маркетплейсов (WB, Ozon). Оценка экономической целесообразности, принятие решений об участии, контролируемое исполнение и анализ результатов. Промо-акции — инициатива маркетплейса; Datapulse решает, участвовать ли и с какими товарами.

## Ключевое отличие от Pricing

| Аспект | Pricing | Promotions |
|--------|---------|------------|
| Инициатор | Селлер (через policy) | Маркетплейс (через акцию) |
| Цена | Вычисляется стратегией | Диктуется или ограничивается маркетплейсом (`action_price`, `max_action_price`) |
| Temporal | Постоянный repricing cycle | Фиксированный период акции (`starts_at` → `ends_at`) с `freeze_at` |
| Decision | CHANGE / SKIP / HOLD | PARTICIPATE / DECLINE / PENDING_REVIEW |
| Write API | Price update endpoint | Promo activate/deactivate endpoint |

## Lifecycle

```
Discovery (ETL) → Canonical Promo Model → Evaluation Pipeline → Decision → Action Scheduling → Execution → Monitoring → Post-mortem
```

### Стадии

| Стадия | Ответственный модуль | Описание |
|--------|---------------------|----------|
| Discovery | [ETL Pipeline](etl-pipeline.md) | `PROMO_SYNC`: загрузка акций и eligible/participating products |
| Canonical Model | [ETL Pipeline](etl-pipeline.md#promo-canonical-entities) | `canonical_promo_campaign` / `canonical_promo_product` в PostgreSQL (source of truth — `PROMO_SYNC`) |
| Evaluation | **Promotions** | Оценка маржи, остатков, целесообразности участия |
| Decision | **Promotions** | PARTICIPATE / DECLINE / PENDING_REVIEW |
| Action Scheduling | **Promotions** | Создание `promo_action` для activate/deactivate |
| Execution | **Promotions** (через [Integration](integration.md) adapters) | Write API вызов к маркетплейсу |
| Monitoring | [Seller Operations](seller-operations.md#promo-journal) | Promo Journal (данные из `canonical_promo_campaign`, `canonical_promo_product`, `promo_action`), working queues |
| Post-mortem | [Analytics & P&L](analytics-pnl.md) | `mart_promo_product_analysis` |

## Модель данных

### Таблицы PostgreSQL

| Таблица | Ответственность |
|---------|-----------------|
| `canonical_promo_campaign` | Каноническая акция маркетплейса (владелец — [ETL Pipeline](etl-pipeline.md#promo-canonical-entities)) |
| `canonical_promo_product` | Статус товара в акции (владелец — [ETL Pipeline](etl-pipeline.md#promo-canonical-entities); Promotions обновляет participation / decision fields) |
| `promo_policy` | Правила автоматического участия |
| `promo_evaluation` | Результат оценки товар × акция |
| `promo_decision` | Решение об участии |
| `promo_action` | Действие (activate/deactivate) с lifecycle |

### canonical_promo_campaign (owned by [ETL Pipeline](etl-pipeline.md))

Каноническое представление акции маркетплейса. Source of truth — `PROMO_SYNC` из ETL. Полная DDL — в [ETL Pipeline → Promo canonical entities](etl-pipeline.md#promo-canonical-entities).

ETL записывает данные из marketplace API. Promotions module читает для evaluation pipeline и обновляет `is_participating` после execution.

`status` вычисляется из `date_from`, `date_to`, `freeze_at` и текущего времени. UPCOMING → ACTIVE → FROZEN (если `freeze_at` задан и прошёл) → ENDED.

**Dedup key:** `(connection_id, external_promo_id)` — unique.

### canonical_promo_product (owned by [ETL Pipeline](etl-pipeline.md))

Связь товар × акция. Обновляется из `PROMO_SYNC` (ETL) и из действий Datapulse (Promotions execution). Полная DDL — в [ETL Pipeline → Promo canonical entities](etl-pipeline.md#promo-canonical-entities).

`participation_status` обновляется из sync и из действий Datapulse:
- `ELIGIBLE` — товар может участвовать, но не участвует (Ozon candidates / WB inAction=false)
- `PARTICIPATING` — товар участвует (Ozon action_products / WB inAction=true)
- `DECLINED` — Datapulse решил не участвовать
- `REMOVED` — Datapulse удалил из акции
- `BANNED` — маркетплейс заблокировал участие (Ozon banned_products)
- `AUTO_DECLINED` — автоматически отклонён по promo_policy

### promo_policy

Правила автоматического участия в акциях. Аналог `price_policy` для промо.

```
promo_policy:
  id                          BIGSERIAL PK
  workspace_id                BIGINT FK → workspace
  name                        VARCHAR
  status                      ENUM (DRAFT, ACTIVE, PAUSED, ARCHIVED)
  participation_mode          ENUM (RECOMMENDATION, SEMI_AUTO, FULL_AUTO)
  min_margin_pct              DECIMAL NOT NULL
  min_stock_days_of_cover     INT DEFAULT 7
  max_promo_discount_pct      DECIMAL (nullable)
  auto_participate_categories JSONB (nullable — whitelist категорий)
  auto_decline_categories     JSONB (nullable — blacklist категорий)
  evaluation_config           JSONB
  version                     INT NOT NULL DEFAULT 1
  created_by                  BIGINT FK → app_user
  created_at                  TIMESTAMPTZ
  updated_at                  TIMESTAMPTZ
```

### Policy versioning

Семантика `version` аналогична [Pricing → Policy versioning](pricing.md#policy-versioning). Инкрементируется при UPDATE полей, влияющих на evaluation logic: `participation_mode`, `min_margin_pct`, `min_stock_days_of_cover`, `max_promo_discount_pct`, `auto_participate_categories`, `auto_decline_categories`, `evaluation_config`.

**participation_mode:**

| Mode | Описание |
|------|----------|
| `RECOMMENDATION` | Показывает рекомендацию; оператор решает вручную |
| `SEMI_AUTO` | Создаёт action PENDING_APPROVAL; оператор одобряет |
| `FULL_AUTO` | Создаёт action APPROVED; контроль через guards |

### promo_policy_assignment

Назначение промо-политик на подключения/категории/товары. Структура аналогична `price_policy_assignment`.

```
promo_policy_assignment:
  id                          BIGSERIAL PK
  promo_policy_id             BIGINT FK → promo_policy
  marketplace_connection_id   BIGINT FK → marketplace_connection
  scope_type                  ENUM (CONNECTION, CATEGORY, SKU)
  category_id                 BIGINT (nullable)
  marketplace_offer_id        BIGINT (nullable, FK → marketplace_offer)
```

Разрешение конфликтов: специфичность + приоритет (идентично pricing).

### promo_evaluation

Результат оценки целесообразности участия товара в акции.

```
promo_evaluation:
  id                          BIGSERIAL PK
  canonical_promo_product_id  BIGINT FK → canonical_promo_product
  promo_policy_id             BIGINT FK → promo_policy
  evaluated_at                TIMESTAMPTZ
  promo_price                 DECIMAL
  regular_price               DECIMAL
  discount_pct                DECIMAL
  cogs                        DECIMAL (nullable)
  margin_at_promo_price       DECIMAL (nullable)
  margin_at_regular_price     DECIMAL (nullable)
  margin_delta_pct            DECIMAL (nullable)
  effective_cost_rate          DECIMAL (nullable)
  stock_available             INT
  expected_promo_duration_days INT
  avg_daily_velocity          DECIMAL (nullable)
  stock_days_of_cover         DECIMAL (nullable)
  stock_sufficient            BOOLEAN
  evaluation_result           ENUM (PROFITABLE, MARGINAL, UNPROFITABLE, INSUFFICIENT_STOCK, INSUFFICIENT_DATA)
  signal_snapshot             JSONB
  skip_reason                 VARCHAR (nullable)
  created_at                  TIMESTAMPTZ
```

### promo_decision

```
promo_decision:
  id                          BIGSERIAL PK
  workspace_id                BIGINT FK → workspace
  canonical_promo_product_id  BIGINT FK → canonical_promo_product
  promo_evaluation_id         BIGINT FK → promo_evaluation (nullable)
  policy_version              INT NOT NULL
  policy_snapshot             JSONB NOT NULL
  decision_type               ENUM (PARTICIPATE, DECLINE, PENDING_REVIEW)
  participation_mode          ENUM (RECOMMENDATION, SEMI_AUTO, FULL_AUTO)
  target_promo_price          DECIMAL (nullable)
  explanation_summary         TEXT
  decided_by                  BIGINT FK → app_user (nullable — NULL для auto)
  created_at                  TIMESTAMPTZ
```

`policy_version` и `policy_snapshot` — аналогично [Pricing → price_decision](pricing.md#модель-decision). Snapshot содержит: `policy_id`, `version`, `name`, `participation_mode`, `min_margin_pct`, `min_stock_days_of_cover`, `max_promo_discount_pct`, `auto_participate_categories`, `auto_decline_categories`, `evaluation_config`.

### promo_action

Действие по изменению участия в акции. Использует упрощённый lifecycle (не полную state machine Execution, т.к. promo writes проще price writes).

```
promo_action:
  id                          BIGSERIAL PK
  workspace_id                BIGINT FK → workspace
  promo_decision_id           BIGINT FK → promo_decision (nullable — manual actions)
  canonical_promo_campaign_id BIGINT FK → canonical_promo_campaign
  marketplace_offer_id        BIGINT FK → marketplace_offer
  action_type                 ENUM (ACTIVATE, DEACTIVATE)
  target_promo_price          DECIMAL (nullable — для ACTIVATE)
  status                      ENUM (PENDING_APPROVAL, APPROVED, EXECUTING, SUCCEEDED, FAILED, EXPIRED, CANCELLED)
  attempt_count               INT DEFAULT 0
  last_error                  TEXT (nullable)
  execution_mode              ENUM (LIVE, SIMULATED)
  created_at                  TIMESTAMPTZ
  updated_at                  TIMESTAMPTZ
```

**Упрощения по сравнению с price_action:**
- Нет RECONCILIATION_PENDING — promo activate/deactivate подтверждается re-read promo products при следующем `PROMO_SYNC`
- Нет RETRY_SCHEDULED как отдельного состояния — retry через immediate backoff (max 2 attempts)
- Нет SUPERSEDED — promo actions не конкурируют (одна акция = одно решение per product)
- Нет ON_HOLD — promo actions time-sensitive (freeze_at deadline)

**Reconciliation через sync:** после promo_action SUCCEEDED, следующий `PROMO_SYNC` верифицирует фактическое participation_status. Расхождение → alert в Promo Journal.

## Evaluation Pipeline

### Триггеры

| Триггер | Описание |
|---------|----------|
| Post-PROMO_SYNC | После успешного PROMO_SYNC — оценка новых/изменённых акций |
| Manual | По требованию оператора |
| Policy change | При активации/изменении promo_policy |
| New promo detected | Когда sync обнаруживает новую акцию |

### Post-sync promo evaluation trigger

Аналогично [Pricing → Post-sync trigger](pricing.md#pricing-run), promo evaluation триггерится через `ETL_SYNC_COMPLETED`:

```
pricing-worker queue receives ETL_SYNC_COMPLETED:
  1. Check: PROMO ∈ completed_domains? (no → skip promo evaluation)
  2. Check: active promo_policies exist for connection_id? (no → skip)
  3. Check: no IN_PROGRESS promo evaluation for connection_id? (yes → skip, idempotent)
  4. Execute promo evaluation batch for connection
```

**Идемпотентность:** pricing worker отслеживает `source_job_execution_id` per promo evaluation run. Повторная доставка `ETL_SYNC_COMPLETED` — дубликат игнорируется.

### Pipeline

```
1. Resolve effective promo_policy per marketplace_offer
2. Filter actionable canonical_promo_product rows:
   - canonical_promo_campaign.status IN (UPCOMING, ACTIVE)
   - canonical_promo_campaign.status ≠ FROZEN (нельзя менять участие)
   - canonical_promo_product.participation_status IN (ELIGIBLE, PARTICIPATING)
   - canonical_promo_product NOT BANNED
3. Signal assembly (batch):
   - COGS из cost_profile (PostgreSQL)
   - avg_commission_pct, avg_logistics_per_unit из ClickHouse
   - current stock из canonical_stock_current
   - avg_daily_velocity из fact_sales
4. Per-product evaluation:
   - Margin at promo_price = (promo_price − COGS − effective_costs) / promo_price
   - Stock check: stock / avg_daily_velocity ≥ promo_duration_days
   - Category filter: whitelist/blacklist
5. Decision:
   - PROFITABLE + stock sufficient → PARTICIPATE
   - MARGINAL (margin > 0 but < min_margin) → PENDING_REVIEW
   - UNPROFITABLE → DECLINE
   - INSUFFICIENT_STOCK → DECLINE
   - INSUFFICIENT_DATA → PENDING_REVIEW
6. Action scheduling (for SEMI_AUTO / FULL_AUTO)
```

### Margin evaluation

```
effective_cost_rate = avg_commission_pct + avg_logistics_per_unit / promo_price + return_adjustment_pct
margin_at_promo_price = (promo_price − COGS) / promo_price − effective_cost_rate
```

Сигналы идентичны [Pricing → Signal Assembly](pricing.md#signal-assembly). Promotions использует тот же signal assembler.

### Evaluation result classification

| Result | Условие | Default action |
|--------|---------|----------------|
| `PROFITABLE` | `margin_at_promo_price ≥ min_margin_pct` AND stock sufficient | PARTICIPATE |
| `MARGINAL` | `0 < margin_at_promo_price < min_margin_pct` AND stock sufficient | PENDING_REVIEW |
| `UNPROFITABLE` | `margin_at_promo_price ≤ 0` | DECLINE |
| `INSUFFICIENT_STOCK` | `stock_days_of_cover < min_stock_days_of_cover` | DECLINE |
| `INSUFFICIENT_DATA` | COGS не задан или critical signal missing | PENDING_REVIEW |

### Evaluation → Decision mapping

| evaluation_result | RECOMMENDATION mode | SEMI_AUTO mode | FULL_AUTO mode |
|-------------------|---------------------|----------------|----------------|
| PROFITABLE | Рекомендация «участвовать» | promo_action PENDING_APPROVAL | promo_action APPROVED |
| MARGINAL | Рекомендация «на усмотрение» | promo_action PENDING_APPROVAL | promo_action PENDING_APPROVAL (safety) |
| UNPROFITABLE | Рекомендация «отказаться» | Decline (no action) | Decline (no action) |
| INSUFFICIENT_STOCK | Рекомендация «отказаться» | Decline (no action) | Decline (no action) |
| INSUFFICIENT_DATA | Рекомендация «проверить данные» | promo_action PENDING_APPROVAL | promo_action PENDING_APPROVAL (safety) |

### Batch processing

```
1. Load all active/upcoming canonical_promo_campaign rows for connection
2. Load canonical_promo_product rows with ELIGIBLE/PARTICIPATING status
3. Batch signal assembly (one ClickHouse query per signal type)
4. Per-product: evaluate → decide → schedule action
5. Batch insert evaluations, decisions, actions
```

## Execution

### Write contracts (provider-specific)

**Ozon:**

| Действие | Endpoint | Метод |
|----------|----------|-------|
| Activate products | `/v1/actions/products/activate` | POST |
| Deactivate products | `/v1/actions/products/deactivate` | POST |

Activate request: `{ action_id, products: [{ product_id, action_price, stock }] }`
Deactivate request: `{ action_id, product_ids: [...] }`
Response: `{ result: { product_ids: [...], rejected: [{ product_id, reason }] } }`

**WB:**

| Действие | Endpoint | Метод |
|----------|----------|-------|
| Upload products to promo | `/api/v1/calendar/promotions/upload` | POST |

Статус: **требует исследования**. WB promo write API менее документирован; формат запроса и поведение при ошибках нуждаются в эмпирической верификации.

### Action lifecycle

```
PENDING_APPROVAL → APPROVED → EXECUTING → SUCCEEDED
                                 ↓
                               FAILED (→ retry: max 2 attempts)
```

Упрощения обоснованы:
- Promo activate/deactivate — идемпотентные операции
- Provider response явно сообщает о rejected products с причинами
- Reconciliation через следующий PROMO_SYNC (не через отдельный re-read)

### Outbox integration

Promo actions используют тот же `outbox_event` что и [Execution](execution.md). Авторитетная DDL — [Data Model](../data-model.md) §outbox_event:

| outbox_event.type | Описание |
|-------------------|----------|
| `PROMO_ACTION_EXECUTE` | Исполнение promo_action |

**Runtime:** promo actions исполняются `datapulse-executor-worker` (тем же runtime, что и price actions). Обоснование: shared infrastructure (outbox poller, retry, rate limit coordination через Integration module), единая blast radius isolation, общие marketplace adapters.

RabbitMQ topology:

```
Exchanges (direct):
  promo.execution       ← promo action dispatch

Queues:
  promo.execution       ← datapulse-executor-worker consumer (отдельная queue от price.execution)
```

Consumer обрабатывает activate/deactivate через соответствующие marketplace adapters. Отдельная queue `promo.execution` от `price.execution` обеспечивает:
- Независимый prefetch и concurrency tuning
- Изоляцию backpressure (burst promo deadlines не блокирует price actions)
- Отдельные DLQ для диагностики

### Temporal constraints

| Constraint | Описание |
|------------|----------|
| `freeze_at` deadline | Promo action APPROVED должен быть executed до `freeze_at`. После freeze — EXPIRED |
| Upcoming-only activation | Активация наиболее эффективна до `starts_at` (товар участвует с начала акции) |
| Active-period changes | Некоторые акции позволяют менять участие после старта; зависит от `promo_type` |

Scheduled job: promo_actions в PENDING_APPROVAL с `canonical_promo_campaign.freeze_at < NOW()` → EXPIRED.

### Idempotency

| Provider | Механизм |
|----------|----------|
| Ozon | Activate already-participating product → no-op (response includes in product_ids) |
| Ozon | Deactivate non-participating product → rejected with reason |
| WB | Requires investigation |

## Координация с Pricing

### Promo guard

[Pricing](pricing.md) содержит **Promo guard** — блокирует repricing для товаров, участвующих в активной акции.

Промо-модуль — **source of truth** для participation status. Pricing Promo guard читает:

```sql
SELECT EXISTS (
  SELECT 1 FROM canonical_promo_product pp
  JOIN canonical_promo_campaign pc ON pp.canonical_promo_campaign_id = pc.id
  WHERE pp.marketplace_offer_id = :offerId
    AND pp.participation_status = 'PARTICIPATING'
    AND pc.status IN ('ACTIVE', 'UPCOMING')
)
```

### Price restoration after promo

После завершения акции (`canonical_promo_campaign.status = ENDED`), товар возвращается к обычной ценовой политике. Pricing pipeline при следующем run видит, что Promo guard больше не блокирует → repricing resume.

Специальный «restore price» action не нужен: маркетплейс автоматически восстанавливает regular price после окончания акции.

### min_price coordination (Ozon)

Ozon price write API принимает `min_price` — минимальная цена для промо-акций. Datapulse может использовать это как safety net:

- Pricing module устанавливает `min_price` = floor price (из constraints)
- Promotions module проверяет, что `action_price ≥ min_price`
- Если `action_price < min_price` → evaluation_result = UNPROFITABLE (even if margin positive, violates floor)

## Симулированное участие

Поддерживает `execution_mode = SIMULATED` (аналогично [Execution](execution.md#симулированное-исполнение-phase-f)):

- Evaluation и decision pipeline работают полностью
- promo_action создаётся с `execution_mode = SIMULATED`
- Simulated gateway не вызывает реальный API
- canonical_promo_product.participation_status не меняется (shadow evaluation only)

Позволяет оценить: «если бы мы включили auto-participate, какие товары попали бы в акцию и какова была бы маржа?»

## REST API

### Promo campaigns

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/promo/campaigns` | Any role | Paginated. Filters: `?connectionId=...&status=ACTIVE&marketplaceType=...&from=...&to=...` |
| GET | `/api/promo/campaigns/{campaignId}` | Any role | Детали campaign + product stats (total, eligible, participating, declined) |
| GET | `/api/promo/campaigns/{campaignId}/products` | Any role | Products in campaign. Paginated. Filters: `?participationStatus=...&search=...` |

### Promo policies

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/promo/policies` | PRICING_MANAGER, ADMIN, OWNER | Создать promo policy. Status = DRAFT. Response: `201` |
| GET | `/api/promo/policies` | Any role | Список promo policies |
| GET | `/api/promo/policies/{policyId}` | Any role | Детали policy |
| PUT | `/api/promo/policies/{policyId}` | PRICING_MANAGER, ADMIN, OWNER | Обновить policy (инкрементирует version) |
| POST | `/api/promo/policies/{policyId}/activate` | PRICING_MANAGER, ADMIN, OWNER | DRAFT/PAUSED → ACTIVE |
| POST | `/api/promo/policies/{policyId}/pause` | PRICING_MANAGER, ADMIN, OWNER | ACTIVE → PAUSED |
| POST | `/api/promo/policies/{policyId}/archive` | PRICING_MANAGER, ADMIN, OWNER | → ARCHIVED |

### Policy assignments

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/promo/policies/{policyId}/assignments` | Any role | Список assignments |
| POST | `/api/promo/policies/{policyId}/assignments` | PRICING_MANAGER, ADMIN, OWNER | Добавить assignment |
| DELETE | `/api/promo/policies/{policyId}/assignments/{assignmentId}` | PRICING_MANAGER, ADMIN, OWNER | Удалить assignment |

### Evaluations & decisions

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| GET | `/api/promo/evaluations` | Any role | Paginated. Filters: `?campaignId=...&marketplaceOfferId=...&evaluationResult=...` |
| GET | `/api/promo/decisions` | Any role | Paginated. Filters: `?campaignId=...&decisionType=...&from=...&to=...` |

### Manual promo actions

| Method | Path | Roles | Описание |
|--------|------|-------|----------|
| POST | `/api/promo/products/{promoProductId}/participate` | PRICING_MANAGER, ADMIN, OWNER | Manual participate. Body: `{ targetPromoPrice? }`. Creates promo_action APPROVED |
| POST | `/api/promo/products/{promoProductId}/decline` | PRICING_MANAGER, ADMIN, OWNER | Manual decline. Body: `{ reason? }` |
| POST | `/api/promo/actions/{actionId}/approve` | PRICING_MANAGER, ADMIN, OWNER | Approve PENDING_APPROVAL promo_action |
| POST | `/api/promo/actions/{actionId}/reject` | PRICING_MANAGER, ADMIN, OWNER | Reject PENDING_APPROVAL. Body: `{ reason }` |
| POST | `/api/promo/actions/{actionId}/cancel` | PRICING_MANAGER, ADMIN, OWNER | Cancel. Body: `{ cancelReason }` |
| GET | `/api/promo/actions` | Any role | Paginated. Filters: `?campaignId=...&status=...&actionType=...` |

## Аналитика (ClickHouse)

Владение аналитическими артефактами:

| Артефакт | Фаза | Описание |
|----------|------|----------|
| `dim_promo_campaign` | F | Промо-кампании: даты, тип, marketplace |
| `fact_promo_product` | F | Товары в промо: participation status, promo_price, regular_price |
| `mart_promo_product_analysis` | G | Эффективность промо: revenue uplift, margin impact, stock-out events |

Materialization: `PROMO_SYNC` → `dim_promo_campaign` + `fact_promo_product` (уже реализовано в ETL).

### dim_promo_campaign (ClickHouse DDL)

| Column | Type | Source | Notes |
|--------|------|--------|-------|
| `promo_campaign_id` | UInt64 | canonical_promo_campaign.id | PK (тот же id, что в PostgreSQL canonical) |
| `connection_id` | UInt32 | canonical_promo_campaign.connection_id | FK |
| `source_platform` | LowCardinality(String) | canonical_promo_campaign.source_platform | `'ozon'` / `'wb'` |
| `external_promo_id` | String | canonical_promo_campaign.external_promo_id | Provider-specific |
| `name` | String | canonical_promo_campaign.promo_name | |
| `promo_type` | LowCardinality(String) | canonical_promo_campaign.promo_type | |
| `status` | LowCardinality(String) | canonical_promo_campaign.status | |
| `starts_at` | DateTime | canonical_promo_campaign.date_from | Семантика старта акции |
| `ends_at` | DateTime | canonical_promo_campaign.date_to | Семантика окончания |
| `freeze_at` | Nullable(DateTime) | canonical_promo_campaign.freeze_at | Ozon-specific |
| `ver` | UInt64 | — | Materialization timestamp |

```sql
ENGINE = ReplacingMergeTree(ver)
PARTITION BY source_platform
ORDER BY (connection_id, promo_campaign_id)
```

### fact_promo_product (ClickHouse DDL)

Grain: canonical_promo_campaign × marketplace_offer.

| Column | Type | Source | Notes |
|--------|------|--------|-------|
| `promo_product_id` | UInt64 | canonical_promo_product.id | PK |
| `promo_campaign_id` | UInt64 | canonical_promo_product.canonical_promo_campaign_id | FK dim_promo_campaign |
| `connection_id` | UInt32 | via canonical_promo_campaign | FK marketplace_connection |
| `source_platform` | LowCardinality(String) | — | `'ozon'` / `'wb'` |
| `product_id` | UInt64 | canonical_promo_product.marketplace_offer_id | FK dim_product |
| `seller_sku_id` | UInt64 | via marketplace_offer | FK seller_sku |
| `participation_status` | LowCardinality(String) | canonical_promo_product.participation_status | |
| `regular_price` | Decimal(18,2) | canonical_promo_product.current_price | Regular на момент sync |
| `promo_price` | Nullable(Decimal(18,2)) | canonical_promo_product.required_price | Цена участия (action_price и т.п.) |
| `discount_pct` | Nullable(Decimal(18,2)) | computed | |
| `ver` | UInt64 | — | Materialization timestamp |

```sql
ENGINE = ReplacingMergeTree(ver)
PARTITION BY source_platform
ORDER BY (connection_id, promo_campaign_id, promo_product_id)
```

### mart_promo_product_analysis (Phase G)

| Мера | Описание |
|------|----------|
| `revenue_during_promo` | Выручка за период акции |
| `revenue_before_promo` | Выручка за эквивалентный период до акции |
| `revenue_uplift_pct` | Прирост выручки |
| `margin_during_promo` | Маржа за период акции |
| `margin_before_promo` | Маржа за эквивалентный период до акции |
| `margin_delta` | Абсолютное изменение маржи |
| `units_sold_during_promo` | Количество продаж |
| `stock_out_during_promo` | Был ли stock-out во время акции |
| `promo_price` | Цена в промо |
| `regular_price` | Обычная цена |
| `discount_pct` | Размер скидки |

Dependencies: `fact_finance` (revenue, margin), `fact_sales` (units), `fact_inventory_snapshot` (stock-out), `dim_promo_campaign` + `fact_promo_product`.

## Модель данных — retention

| Таблица | Retention |
|---------|-----------|
| `canonical_promo_campaign` | Бессрочно (historical reference; владелец — ETL) |
| `canonical_promo_product` | Бессрочно (владелец — ETL) |
| `promo_evaluation` | 180 дней (после ends_at акции) |
| `promo_decision` | 180 дней |
| `promo_action` | Бессрочно (audit) |

## Фазовое разделение

| Компонент | Фаза |
|-----------|------|
| `canonical_promo_campaign`, `canonical_promo_product` (canonical model, ETL-owned) | **F** |
| Evaluation pipeline (margin, stock check) | **F** |
| Decision flow (RECOMMENDATION mode) | **F** |
| Promo execution (SEMI_AUTO, FULL_AUTO) | **F** |
| Ozon activate/deactivate write adapter | **F** |
| WB promo write adapter | **F** (requires API investigation) |
| Promo guard integration с Pricing | **F** (guard уже существует в C, canonical source — F) |
| `mart_promo_product_analysis` | **G** |
| Advanced analytics (uplift modeling, optimal pricing) | **G** |

## Design decisions

### P-1: Promo canonical model в PostgreSQL — RESOLVED

Промо-данные из ETL (`PROMO_SYNC`) материализуются в ClickHouse (`dim_promo_campaign`, `fact_promo_product`) для аналитики. **Единственный** набор canonical таблиц в PostgreSQL — `canonical_promo_campaign` и `canonical_promo_product`, владелец схемы и первичная запись из sync — [ETL Pipeline](etl-pipeline.md#promo-canonical-entities). Модуль Promotions не дублирует campaign/product таблицы; читает canonical слой, обновляет поля участия / решений (`is_participating`, `participation_status`, `participation_decision_source` и т.д.) и ведёт собственные сущности (`promo_policy`, `promo_evaluation`, `promo_decision`, `promo_action`).

Обоснование: decision pipeline, promo guard, action lifecycle опираются на тот же canonical PostgreSQL state, что и ETL. ClickHouse — read-only для аналитики (архитектурный инвариант).

### P-2: Отдельный action lifecycle vs reuse Execution — RESOLVED

Promo actions используют **упрощённый lifecycle** (без RECONCILIATION_PENDING, без RETRY_SCHEDULED как отдельного состояния, без SUPERSEDED). Обоснование:

1. Promo write APIs возвращают explicit success/rejected per product — не нужен deferred reconciliation
2. Promo activate идемпотентен — retry проще (immediate, max 2)
3. Freeze deadline создаёт жёсткий temporal constraint — сложный retry flow контрпродуктивен
4. Verification через следующий PROMO_SYNC — eventual reconciliation без отдельного механизма

Общий `outbox_event` переиспользуется (инфраструктура shared). Action state machine — собственная.

### P-3: Promo policy scope — RESOLVED

`promo_policy` назначается через `promo_policy_assignment` (аналогично pricing). Одна active promo_policy per marketplace_offer (resolved через specificity + priority).

Допускается scenario: promo_policy отсутствует → товар не оценивается автоматически → участие только через ручное решение оператора. Это default для новых connections.

### P-4: WB promo write — OPEN

WB promo write API (`POST /api/v1/calendar/promotions/upload`) требует эмпирической верификации:
- Точный формат запроса (JSON body vs multipart)
- Поведение при невалидных SKU
- Ограничения по timing (можно ли менять участие после старта акции)
- Rate limits для write endpoint

**Блокер Phase F:** WB promo write доступен только с Promotion-scoped токеном.

## Связанные модули

- [ETL Pipeline](etl-pipeline.md) — `PROMO_SYNC`: загрузка акций и eligible products; materialization в `canonical_promo_campaign` / `canonical_promo_product` и ClickHouse
- [Analytics & P&L](analytics-pnl.md) — `dim_promo_campaign`, `fact_promo_product`, `mart_promo_product_analysis`; signal assembly для margin evaluation
- [Pricing](pricing.md) — Promo guard (consumer canonical participation_status); signal assembler (shared); min_price coordination
- [Integration](integration.md) — Promo write adapters (Ozon activate/deactivate, WB upload)
- [Execution](execution.md) — Shared `outbox_event` инфраструктура
- [Seller Operations](seller-operations.md) — Promo Journal, promo working queues, evaluation results UI
- [Tenancy & IAM](tenancy-iam.md) — approval/decline определяются ролями
- Детальные контракты: [Promo & Advertising Contracts](../provider-api-specs/promo-advertising-contracts.md)
