# Модуль: Seller Operations

**Фаза:** E — Seller Operations
**Зависимости:** [Analytics & P&L](analytics-pnl.md), [Pricing](pricing.md), [Execution](execution.md), [Promotions](promotions.md)
**Runtime:** datapulse-api

---

## Назначение

Ежедневный рабочий инструмент продавца. Обязательный capability, не опциональный. Обеспечивает операционный контур: наблюдение, принятие решений, контроль исполнения.

## Компоненты

| Компонент | Описание |
|-----------|----------|
| Operational Grid | Мастер-таблица по marketplace_offer: цена, маржа, velocity, остатки, алерты |
| Saved Views | Персональные пресеты фильтров и сортировок |
| Working Queues | Очереди задач: «требует внимания», «ожидает решения», «в процессе» |
| Price Journal | История всех ценовых решений и действий |
| Promo Journal | История участия в промо-акциях и результатов |
| Mismatch Monitor | Визуализация расхождений между data domains |

## Operational Grid

### Grain

Каждая строка = один `marketplace_offer` (конкретное предложение конкретного товара на конкретном маркетплейсе через конкретное подключение).

Один `seller_sku` с предложениями на WB и Ozon → **две строки** в гриде. Столбец «Маркетплейс» + «Подключение» — обязательные visible columns.

Группировка по `seller_sku` доступна через saved view с `group_by_sku = true`.

### Group-by SKU mode

Когда saved view имеет `group_by_sku = true`, грид показывает **expandable rows**: одна строка на `seller_sku`, с вложенными строками per `marketplace_offer`.

**Агрегация parent row (SKU level):**

| Колонка | Агрегация | Описание |
|---------|-----------|----------|
| `sku_code` | Прямое значение | Артикул |
| `product_name` | Первое `marketplace_offer.name` | Название |
| `marketplace_type` | Список: "WB, Ozon" | Маркетплейсы с предложениями |
| `current_price` | MIN — MAX (range) | Диапазон цен |
| `margin_pct` | MIN — MAX (range) | Диапазон маржи |
| `available_stock` | SUM | Суммарный остаток |
| `revenue_30d` | SUM | Суммарная выручка |
| `velocity_14d` | SUM | Суммарные продажи |
| `stock_risk` | MAX severity (CRITICAL > WARNING > NORMAL) | Наихудший stock risk |
| Остальные | `—` (dash) | Не агрегируются на parent level |

**Реализация:** AG Grid [Row Grouping](https://www.ag-grid.com/angular-data-grid/grouping/) (community feature — `groupDisplayType: 'groupRows'`). Данные приходят плоскими (per-offer), группировка — клиентская. Пагинация — по offers, не по SKU-группам.

**Ограничение MVP:** group-by SKU работает корректно только в пределах текущей страницы (клиентская группировка). Если один SKU имеет offers на разных страницах — они не группируются. Для MVP это допустимо (workspace редко имеет > 200 offers/page).

### Grid columns

| Column | Source | Store | Описание |
|--------|--------|-------|----------|
| `sku_code` | `seller_sku.sku_code` | PostgreSQL | Артикул продавца |
| `product_name` | `marketplace_offer.name` | PostgreSQL | Название товара |
| `marketplace_type` | `marketplace_connection.marketplace_type` | PostgreSQL | WB / Ozon |
| `connection_name` | `marketplace_connection.name` | PostgreSQL | Название подключения |
| `status` | `marketplace_offer.status` | PostgreSQL | ACTIVE / ARCHIVED / BLOCKED |
| `category` | `category.name` (via marketplace_offer.category_id FK) | PostgreSQL | Категория |
| `current_price` | `canonical_price_current.price` | PostgreSQL | Текущая цена |
| `discount_price` | `canonical_price_current.discount_price` | PostgreSQL | Цена со скидкой |
| `cost_price` | `cost_profile.cost_price` (SCD2 current) | PostgreSQL | Себестоимость |
| `margin_pct` | Computed: `(current_price - cost_price) / current_price × 100` | Computed | Маржинальность % |
| `available_stock` | `canonical_stock_current.available` (SUM across warehouses) | PostgreSQL | Доступный остаток |
| `days_of_cover` | `mart_inventory_analysis.days_of_cover` (latest) | ClickHouse | Дней до stock-out |
| `stock_risk` | `mart_inventory_analysis.stock_out_risk` | ClickHouse | CRITICAL / WARNING / NORMAL |
| `revenue_30d` | `SUM(fact_finance.revenue_amount)` WHERE 30 days | ClickHouse | Выручка за 30 дней |
| `net_pnl_30d` | `SUM(fact_finance.net_payout)` WHERE 30 days, `attribution_level IN ('POSTING','PRODUCT')` | ClickHouse | Marketplace P&L за 30 дней (до COGS) |
| `velocity_14d` | `SUM(fact_sales.quantity)` / 14 | ClickHouse | Средние продажи в день |
| `return_rate_pct` | `mart_returns_analysis.return_rate_pct` | ClickHouse | % возвратов |
| `active_policy` | `price_policy.name` (via assignment resolution) | PostgreSQL | Активная ценовая политика |
| `last_decision` | `price_decision` (latest) | PostgreSQL | Последнее решение (CHANGE / SKIP / HOLD) |
| `last_action_status` | `price_action.status` (latest) | PostgreSQL | Статус последнего action |
| `promo_status` | `canonical_promo_product.participation_status` (active campaigns) | PostgreSQL | В промо? (PARTICIPATING / ELIGIBLE / —) |
| `manual_lock` | `manual_price_lock EXISTS` | PostgreSQL | Ручная блокировка |
| `simulated_price` | `simulated_offer_state.simulated_price` | PostgreSQL | Симулированная цена (Phase F; nullable) |
| `simulated_delta_pct` | `simulated_offer_state.price_delta_pct` | PostgreSQL | Разница simulated vs canonical (%) |
| `ad_spend_30d` | `mart_advertising_product.spend` (latest period) | ClickHouse | Расход на рекламу за 30 дней (Phase A; hidden by default, visible in system view "Реклама") |
| `drr_30d_pct` | `mart_advertising_product.drr_pct` | ClickHouse | ДРР за 30 дней (Phase A; hidden by default) |
| `ad_cpo` | `mart_advertising_product.cpo` | ClickHouse | Cost Per Order (Phase A; hidden by default) |
| `ad_roas` | `mart_advertising_product.roas` | ClickHouse | ROAS (Phase A; hidden by default) |
| `last_sync_at` | `marketplace_sync_state.last_success_at` | PostgreSQL | Время последней синхронизации |
| `data_freshness` | Computed: `STALE` if `NOW() - last_sync_at > staleness_threshold` | Computed | FRESH / STALE. Default threshold: 4 hours (configurable via `datapulse.grid.staleness-threshold=PT4H`) |

### Read model

Grid строится из **dedicated read model** — denormalized view/query, оптимизированный для фильтрации, сортировки и пагинации.

**Archirecture:** grid read model — JDBC repository с динамическим SQL. Не JPA (N+1 risk). Не materialized view (сложность обновления).

```
Grid query structure:

  PostgreSQL (main query):
    marketplace_offer
    LEFT JOIN seller_sku
    LEFT JOIN marketplace_connection
    LEFT JOIN canonical_price_current
    LEFT JOIN canonical_stock_current (aggregated)
    LEFT JOIN cost_profile (SCD2 current)
    LEFT JOIN category
    LEFT JOIN manual_price_lock (active)
    LEFT JOIN (latest price_decision subquery)
    LEFT JOIN (latest price_action subquery)
    LEFT JOIN (active promo_product subquery)
    LEFT JOIN marketplace_sync_state
    WHERE workspace_id = :workspace_id
      AND [dynamic filters]
    ORDER BY [dynamic sort — whitelist]
    LIMIT :limit OFFSET :offset

  ClickHouse (enrichment query, batched):
    Per page of marketplace_offer_ids:
      revenue_30d, net_pnl_30d, velocity_14d, return_rate_pct,
      days_of_cover, stock_risk
    Joined in-memory by marketplace_offer_id
```

**Two-store pattern:** PostgreSQL для authoritative state + filters + pagination. ClickHouse для derived analytics, batched per-page. Merging in application layer.

### Filtering

| Filter | Type | Column |
|--------|------|--------|
| `marketplace_type` | Enum | `marketplace_connection.marketplace_type` |
| `connection_id` | ID | `marketplace_offer.marketplace_connection_id` |
| `category_id` | ID | `marketplace_offer.category_id` |
| `status` | Enum | `marketplace_offer.status` |
| `sku_code` | Text (ILIKE) | `seller_sku.sku_code` |
| `product_name` | Text (ILIKE) | `marketplace_offer.name` |
| `margin_min` / `margin_max` | Range | Computed |
| `stock_risk` | Enum | ClickHouse-enriched (pre-filter, см. ниже) |
| `has_manual_lock` | Boolean | `manual_price_lock EXISTS` |
| `has_active_promo` | Boolean | `canonical_promo_product EXISTS` |
| `last_decision` | Enum | Latest `price_decision.decision_type` |
| `last_action_status` | Enum | Latest `price_action.status` |

**Dynamic sorting whitelist:**

```java
Map<String, String> SORT_WHITELIST = Map.ofEntries(
    Map.entry("sku_code",         "ss.sku_code"),
    Map.entry("product_name",     "mo.name"),
    Map.entry("marketplace_type", "mc.marketplace_type"),
    Map.entry("connection_name",  "mc.name"),
    Map.entry("status",           "mo.status"),
    Map.entry("category",         "cat.name"),
    Map.entry("current_price",    "cpc.price"),
    Map.entry("discount_price",   "cpc.discount_price"),
    Map.entry("cost_price",       "cp.cost_price"),
    Map.entry("margin_pct",       "(cpc.price - cp.cost_price) / NULLIF(cpc.price, 0)"),
    Map.entry("available_stock",  "stock_agg.total_available"),
    Map.entry("last_decision",    "pd_latest.decision_type"),
    Map.entry("last_action_status", "pa_latest.status"),
    Map.entry("last_sync_at",     "mss.last_success_at")
);
```

**Несортируемые PG-колонки:** `active_policy` (требует дополнительного JOIN), `promo_status` (subquery), `manual_lock` (EXISTS), `simulated_price` (Phase F). При необходимости — добавлять в whitelist по мере реализации.

**CH-sortable колонки** (отдельный whitelist, обрабатываются через ClickHouse sort strategy):

```java
Set<String> CH_SORT_COLUMNS = Set.of(
    "revenue_30d", "net_pnl_30d", "velocity_14d",
    "return_rate_pct", "days_of_cover", "stock_risk"
);
```

ClickHouse-sourced columns (revenue_30d, velocity_14d, etc.) — post-sort в application layer. Если primary sort = ClickHouse column → pre-fetch sorted IDs из ClickHouse, затем join в PostgreSQL.

**ClickHouse-based filtering (stock_risk и другие CH-колонки):**

Фильтр по `stock_risk` (и любому CH-enriched полю) нельзя выполнить post-filter на странице — это сломает пагинацию (страница вернёт < `size` элементов). Поэтому CH-фильтры работают как **pre-filter**:

1. ClickHouse query: `SELECT marketplace_offer_id FROM mart_inventory_analysis WHERE workspace_id = ? AND stock_out_risk = ?` → set of matching IDs
2. PostgreSQL query: добавляет `AND mo.id IN (?)` к основному WHERE — ID set от ClickHouse становится дополнительным PG-фильтром
3. Пагинация работает корректно — PostgreSQL видит полный набор отфильтрованных строк

**Pre-filter ID set limit:** max 10 000 IDs. Если CH возвращает больше → log.warn, берутся первые 10 000 (по order CH-запроса). Для MVP допустимо — workspace с > 10 000 critical stock offers маловероятен.

## Saved Views

### Schema

```
saved_view:
  id                    BIGSERIAL PK
  workspace_id          BIGINT FK → workspace                     NOT NULL
  user_id               BIGINT FK → app_user                      NOT NULL
  name                  VARCHAR(200) NOT NULL
  is_default            BOOLEAN NOT NULL DEFAULT false
  filters               JSONB NOT NULL                            -- serialized filter state
  sort_column           VARCHAR(60)                               -- column name from whitelist
  sort_direction        VARCHAR(4) DEFAULT 'ASC'                  -- ASC / DESC
  visible_columns       JSONB NOT NULL                            -- ordered list of visible column names
  group_by_sku          BOOLEAN NOT NULL DEFAULT false             -- group rows by seller_sku
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (workspace_id, user_id, name)
```

**filters JSONB format:**

```json
{
  "marketplace_type": ["WB"],
  "status": ["ACTIVE"],
  "category_id": [42, 55],
  "margin_min": 10.0,
  "has_manual_lock": false,
  "sku_code": "ABC"
}
```

### Limits

| Параметр | Лимит | Обоснование |
|----------|-------|-------------|
| Max views per user per workspace | 50 | Предотвращает мусорное накопление; достаточно для реальных сценариев |
| `filters` JSONB max size | 10 KB | Валидация на API-уровне; JSON с 15 фильтрами ≈ 1 KB |
| `visible_columns` max items | 30 (= total grid columns) | Нельзя показать колонок больше, чем есть |
| `name` max length | 200 chars (VARCHAR constraint) | — |
| System views | Не редактируемые, не удаляемые | `is_system = true` seeded при создании workspace |

Shared views (видимые другим пользователям workspace) — **out of scope** для текущей фазы. Все custom views — per-user.

**Default views (seeded per workspace):**
- "Все товары" — no filters, all columns
- "Требуют внимания" — `stock_risk IN [CRITICAL, WARNING]` OR `last_action_status = FAILED`
- "В промо" — `has_active_promo = true`

## Working Queues

### Concept

Working queue — именованная очередь задач, привязанная к фильтру. Элементы попадают в очередь автоматически по criteria или вручную оператором.

### Schema

```
working_queue_definition:
  id                    BIGSERIAL PK
  workspace_id          BIGINT FK → workspace                     NOT NULL
  name                  VARCHAR(200) NOT NULL
  queue_type            VARCHAR(30) NOT NULL                      -- ATTENTION, DECISION, PROCESSING
  auto_criteria         JSONB                                     -- auto-population filter (nullable — manual-only queue)
  enabled               BOOLEAN NOT NULL DEFAULT true
  is_system             BOOLEAN NOT NULL DEFAULT false            -- системная очередь (не удаляется пользователем)
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (workspace_id, name)
```

```
working_queue_assignment:
  id                    BIGSERIAL PK
  queue_definition_id   BIGINT FK → working_queue_definition      NOT NULL
  entity_type           VARCHAR(60) NOT NULL                      -- 'marketplace_offer', 'price_action', 'promo_action', 'alert_event'
  entity_id             BIGINT NOT NULL                           -- FK to respective entity
  status                VARCHAR(20) NOT NULL DEFAULT 'PENDING'    -- PENDING, IN_PROGRESS, DONE, DISMISSED
  assigned_to_user_id   BIGINT FK → app_user                      (nullable — unassigned)
  note                  TEXT                                       (nullable — operator comment)
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (queue_definition_id, entity_type, entity_id) WHERE status NOT IN ('DONE', 'DISMISSED')
```

### Default queues (seeded per workspace)

| Queue | Type | Auto-criteria | Описание |
|-------|------|---------------|----------|
| Failed Actions | ATTENTION | `price_action.status = 'FAILED'` | Неуспешные ценовые действия |
| Pending Approvals | DECISION | `price_action.status = 'PENDING_APPROVAL'` | Ожидают ручного одобрения |
| Stock Critical | ATTENTION | `mart_inventory_analysis.stock_out_risk = 'CRITICAL'` | Критический stock-out risk |
| Promo Deadlines | DECISION | `canonical_promo_product.participation_status = 'ELIGIBLE'` AND `promo_campaign.participation_deadline < NOW() + interval '48 hours'` | Промо с приближающимся дедлайном |
| Price Mismatches | ATTENTION | `alert_event.rule_type = 'MISMATCH'` AND `alert_event.mismatch_type = 'PRICE'` AND `alert_event.status = 'ACTIVE'` | Расхождения цен (Mismatch Monitor) |
| Failed Promo Actions | ATTENTION | `promo_action.status = 'FAILED'` | Неуспешные promo-действия |

### Limits

| Параметр | Лимит | Обоснование |
|----------|-------|-------------|
| Max custom queues per workspace | 20 | Системные (6) + custom. Достаточно для реальных workflow |
| Max active assignments per queue | 10 000 | Assignment cleanup (DONE/DISMISSED → archive) предотвращает рост |
| Max manual assignments per user per day | 100 | Предотвращает массовый мусор |

### Queue lifecycle

```
PENDING → IN_PROGRESS → DONE
                      → DISMISSED
PENDING → DISMISSED
```

### Assignment cleanup

DONE и DISMISSED assignments накапливаются. Scheduled cleanup job:

```sql
DELETE FROM working_queue_assignment
WHERE status IN ('DONE', 'DISMISSED')
  AND updated_at < NOW() - interval '30 days'
```

Schedule: ежедневно, `@SchedulerLock`. Retention configurable: `datapulse.queues.assignment-retention-days=30`.

### Claim concurrency

Claim = CAS-операция. Два оператора одновременно claim-ят один item → только один успешно:

```sql
UPDATE working_queue_assignment
SET status = 'IN_PROGRESS', assigned_to_user_id = :userId, updated_at = now()
WHERE id = :itemId AND status = 'PENDING'
```

`WHERE status = 'PENDING'` гарантирует: если item уже claim-нут (status = IN_PROGRESS), UPDATE затронет 0 строк → сервис возвращает 409 Conflict с message key `queues.item.already_claimed`. Frontend показывает toast и обновляет список.

### auto_criteria JSONB structure

```json
{
  "entity_type": "price_action",
  "match_rules": [
    { "field": "status", "op": "eq", "value": "FAILED" }
  ]
}
```

```json
{
  "entity_type": "marketplace_offer",
  "match_rules": [
    { "field": "stock_out_risk", "op": "eq", "value": "CRITICAL", "source": "clickhouse" }
  ]
}
```

Supported operators: `eq`, `neq`, `in`, `gt`, `lt`, `gte`, `lte`. `source: "clickhouse"` — для ClickHouse-enriched полей (requires pre-query). Default source: PostgreSQL.

### Auto-population job

**Schedule:** каждые 5 минут (`@Scheduled`, `@SchedulerLock`).

**Алгоритм per queue:**

1. Evaluate `auto_criteria` → SELECT matching entity IDs
2. INSERT new assignments: `ON CONFLICT DO NOTHING` (idempotent на unique partial index)
3. Auto-resolution: SELECT all PENDING assignments для данной queue → re-evaluate criteria → если entity больше не матчит → UPDATE status = `DONE`. IN_PROGRESS assignments **не** auto-resolve'ятся (оператор уже взял в работу)

**Batch и транзакции:**

| Параметр | Значение |
|----------|----------|
| Batch size (entities per evaluation) | 1 000 |
| Transaction scope | Per-queue (одна транзакция на queue definition) |
| Total execution budget | 60s (`@SchedulerLock(lockAtMostFor = "PT2M")`) |

**Error handling:**

| Ошибка | Поведение |
|--------|-----------|
| ClickHouse недоступен (cross-store criteria) | Skip queues с CH-criteria до следующего run. PG-only queues обрабатываются нормально. `log.warn` |
| Exception при обработке одной queue | Catch + `log.error`, переход к следующей queue. Partial success допускается |
| Entity не существует (stale reference) | `ON CONFLICT DO NOTHING` — assignment не создаётся |

### Cross-store auto-criteria evaluation

Некоторые default queues используют criteria из ClickHouse (например, `stock_out_risk = 'CRITICAL'` из `mart_inventory_analysis`). Auto-population job выполняет трёхэтапную evaluation:

1. **PostgreSQL query:** отбирает candidate entities по PostgreSQL-criteria (если есть)
2. **ClickHouse enrichment:** для candidates batch-запрос к ClickHouse для ClickHouse-criteria
3. **Merge:** пересечение результатов → INSERT assignments

Для queues с **только PostgreSQL criteria** (e.g. `price_action.status = 'FAILED'`) — ClickHouse не вовлекается.

Для queues с **только ClickHouse criteria** (e.g. `stock_out_risk = 'CRITICAL'`) — ClickHouse query возвращает `marketplace_offer_id` set → INSERT assignments по этим IDs.

**Performance:** auto-population job выполняется каждые 5 минут. ClickHouse queries — batched, latency < 100ms.

## Price Journal

Read-only view: история всех ценовых решений и действий для marketplace_offer.

### Data source

Price journal строится JOIN-ом PostgreSQL таблиц (authoritative state):

```
price_decision
  JOIN price_action (LEFT — не все decisions создают actions)
  JOIN price_action_attempt (LEFT — actions в PENDING_APPROVAL ещё не имеют attempts)
WHERE marketplace_offer_id = :offer_id
ORDER BY price_decision.created_at DESC
```

### Journal entry fields

| Field | Source | Описание |
|-------|--------|----------|
| `decision_date` | `price_decision.created_at` | Когда принято решение |
| `decision_type` | `price_decision.decision_type` | CHANGE / SKIP / HOLD |
| `skip_reason` | `price_decision.skip_reason` | Причина пропуска (nullable) |
| `policy_name` | `price_decision.policy_snapshot → name` | Какая политика |
| `policy_version` | `price_decision.policy_version` | Версия политики |
| `current_price` | `price_decision.current_price` | Цена на момент решения |
| `target_price` | `price_decision.target_price` | Целевая цена (nullable — для SKIP/HOLD) |
| `price_change_pct` | Computed | % изменения |
| `action_status` | `price_action.status` | Статус action (nullable) |
| `execution_mode` | `price_action.execution_mode` | LIVE / SIMULATED |
| `actual_price` | `price_action_attempt.actual_price` | Фактическая цена после reconciliation |
| `reconciliation_source` | `price_action_attempt.reconciliation_source` | IMMEDIATE / DEFERRED / MANUAL |
| `explanation_summary` | `price_decision.explanation → summary` | Краткое объяснение решения |

### Retention

Price journal читает из `price_decision` + `price_action` + `price_action_attempt`. Retention этих таблиц управляется модулем Pricing. Для MVP — без архивации (все записи хранятся бессрочно). При росте > 1M decisions per workspace — рассмотреть партиционирование `price_decision` по `created_at` (monthly partitions).

### REST API

`GET /api/workspaces/{workspaceId}/offers/{offerId}/price-journal?page=0&size=20`

## Promo Journal

Read-only view: история участия marketplace_offer в промо-акциях.

### Data source

```
promo_decision
  JOIN promo_evaluation (LEFT — via promo_decision.promo_evaluation_id)
  JOIN promo_action (LEFT — via promo_decision.id = promo_action.promo_decision_id)
  JOIN canonical_promo_product (via promo_decision.canonical_promo_product_id)
  JOIN canonical_promo_campaign (via canonical_promo_product.canonical_promo_campaign_id)
WHERE canonical_promo_product.marketplace_offer_id = :offer_id
ORDER BY promo_decision.created_at DESC
```

### Journal entry fields

| Field | Source | Описание |
|-------|--------|----------|
| `promo_name` | `canonical_promo_campaign.promo_name` | Название акции |
| `promo_type` | `canonical_promo_campaign.promo_type` | Тип акции |
| `period` | `canonical_promo_campaign.date_from .. date_to` | Период акции |
| `evaluation_result` | `promo_evaluation.evaluation_result` | PROFITABLE / MARGINAL / UNPROFITABLE / INSUFFICIENT_STOCK / INSUFFICIENT_DATA |
| `participation_decision` | `promo_decision.decision_type` | PARTICIPATE / DECLINE / DEACTIVATE / PENDING_REVIEW |
| `action_status` | `promo_action.status` | Статус promo action (nullable — RECOMMENDATION mode не создаёт action) |
| `required_price` | `canonical_promo_product.required_price` | Цена участия |
| `margin_at_promo_price` | `promo_evaluation.margin_at_promo_price` | Маржа при промо-цене (nullable — нет evaluation) |
| `margin_delta_pct` | `promo_evaluation.margin_delta_pct` | Потеря маржи vs обычная цена (nullable) |
| `explanation_summary` | `promo_decision.explanation_summary` | Объяснение решения |

### Retention

Promo journal читает из `promo_decision` + `promo_evaluation` + `promo_action` + `canonical_promo_product` + `canonical_promo_campaign`. Retention управляется модулем Promotions. Для MVP — без архивации. Аналогично Price Journal: при росте > 1M decisions — партиционирование `promo_decision` по `created_at`.

## Mismatch Monitor

Визуализация расхождений между связанными data domains.

### Mismatch types

| Type | Comparison | Описание |
|------|-----------|----------|
| Price mismatch | `canonical_price_current.price` vs last `price_action.target_price` WHERE SUCCEEDED | Текущая цена ≠ последней успешно установленной |
| Stock inconsistency | `canonical_stock_current` vs `fact_inventory_snapshot` (last) | Расхождение между canonical и analytics |
| Promo participation | `canonical_promo_product.participation_status` vs `promo_action` outcome | Ожидаемое участие ≠ фактическое |
| Finance gap | Missing `canonical_finance_entry` for expected periods | Пропуски в финансовых данных |

### Detection thresholds

| Type | Threshold (default) | Severity |
|------|---------------------|----------|
| Price mismatch | `|delta_pct| > 1%` | WARNING if 1-5%, CRITICAL if > 5% |
| Stock inconsistency | `|delta| > 10 units` OR `|delta_pct| > 20%` | WARNING |
| Promo participation | Любое расхождение | WARNING |
| Finance gap | Missing entry за expected period (> 48h) | CRITICAL |

Thresholds — configurable через `@ConfigurationProperties`.

### Implementation

Mismatch checker (scheduled, after each sync) записывает результаты в `alert_event` (rule_type = MISMATCH). UI отображает active mismatches с drill-down к конкретным offers.

**Cross-module dependency:** `alert_event` — таблица, принадлежащая модулю [Audit & Alerting](audit-alerting.md). Mismatch checker использует `AlertEventRepository` (persistence API модуля Audit) для записи и обновления mismatch events. Направление зависимости: `Seller Operations → Audit & Alerting` (допустимо — периферийный модуль зависит от инфраструктурного). Seller Operations **не** владеет таблицей `alert_event` и не создаёт миграции для неё.

### Resolution workflow

```
ACTIVE → ACKNOWLEDGED → RESOLVED
  ↓                       ↗
  → AUTO_RESOLVED (condition cleared)
```

| Переход | Инициатор | Описание |
|---------|-----------|----------|
| ACTIVE → ACKNOWLEDGED | Operator (POST .../acknowledge) | Оператор видел расхождение, начал разбираться |
| ACKNOWLEDGED → RESOLVED | Operator (POST .../resolve) | Оператор зафиксировал результат: resolution type + note |
| ACTIVE → AUTO_RESOLVED | System (next sync) | Расхождение исчезло (цена синхронизировалась, promo status обновился) |

**Resolution types:**
- `ACCEPTED` — расхождение ожидаемо (маркетплейс ещё не применил цену, задержка)
- `REPRICED` — запущен manual pricing run для исправления
- `INVESTIGATED` — причина найдена и задокументирована
- `EXTERNAL` — причина на стороне маркетплейса, исправление невозможно

**Auto-resolution:** при каждом sync mismatch checker перепроверяет все ACTIVE mismatches. Если condition больше не матчит → статус = AUTO_RESOLVED. ACKNOWLEDGED mismatches не auto-resolve'ятся (оператор уже взял в работу).

### Schedule и fallback

Mismatch checker запускается:
1. **After each ETL sync** — event-driven (`@TransactionalEventListener` на `EtlSyncCompletedEvent`)
2. **Fallback schedule** — `@Scheduled` каждые 2 часа. Если sync не было > 2h (например, ETL сбой) — checker всё равно перепроверяет active mismatches на случай auto-resolution или появления новых через другие каналы (manual price set, promo status change).

### Retention

| Статус | Retention | Механизм |
|--------|-----------|----------|
| ACTIVE, ACKNOWLEDGED | Бессрочно | Оператор должен обработать |
| RESOLVED, AUTO_RESOLVED | 90 дней | Scheduled cleanup job: `DELETE FROM alert_event WHERE status IN ('RESOLVED', 'AUTO_RESOLVED') AND updated_at < NOW() - interval '90 days'` |

Retention configurable через `@ConfigurationProperties` (`datapulse.mismatches.retention-days=90`).

## REST API contracts

### Grid

| Endpoint | Method | Описание |
|----------|--------|----------|
| `/api/workspaces/{workspaceId}/grid` | GET | Paginated grid с фильтрами и сортировкой |
| `/api/workspaces/{workspaceId}/grid/matching-ids` | GET | Список offer IDs, matching текущим фильтрам (для Select All, max 500) |
| `/api/workspaces/{workspaceId}/grid/export` | GET | CSV export (streaming, `Content-Disposition: attachment`; PG-only колонки) |

**Query parameters для grid:**

| Parameter | Type | Описание |
|-----------|------|----------|
| `page` | int | Номер страницы (0-based) |
| `size` | int | Размер страницы (default 50, max 200) |
| `sort` | string | Column name from whitelist |
| `direction` | string | ASC / DESC |
| `marketplace_type` | string[] | Filter |
| `connection_id` | long[] | Filter |
| `status` | string[] | Filter |
| `sku_code` | string | Text search (ILIKE) |
| `product_name` | string | Text search (ILIKE) |
| `category_id` | long[] | Filter |
| `margin_min` / `margin_max` | decimal | Range filter |
| `has_manual_lock` | boolean | Filter |
| `has_active_promo` | boolean | Filter |
| `view_id` | long | Применить saved view (overrides individual filters) |

**Grid response format:**

```json
{
  "content": [
    {
      "offerId": 12345,
      "skuCode": "ABC-001",
      "productName": "Футболка синяя",
      "marketplaceType": "WB",
      "connectionName": "Мой кабинет WB",
      "status": "ACTIVE",
      "category": "Футболки",
      "currentPrice": 1500.00,
      "discountPrice": 1200.00,
      "costPrice": 600.00,
      "marginPct": 60.0,
      "availableStock": 142,
      "daysOfCover": 18.5,
      "stockRisk": "NORMAL",
      "revenue30d": 45000.00,
      "netPnl30d": 12000.00,
      "velocity14d": 3.2,
      "returnRatePct": 4.1,
      "activePolicy": "Маржа 25% WB",
      "lastDecision": "CHANGE",
      "lastActionStatus": "SUCCEEDED",
      "promoStatus": "PARTICIPATING",
      "manualLock": false,
      "simulatedPrice": null,
      "simulatedDeltaPct": null,
      "lastSyncAt": "2026-03-30T14:23:00Z",
      "dataFreshness": "FRESH"
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 1234,
  "totalPages": 25
}
```

Формат — стандартный Spring `Page<T>`. Каждый element — плоский объект, ClickHouse-enriched поля (`daysOfCover`, `stockRisk`, `revenue30d`, `netPnl30d`, `velocity14d`, `returnRatePct`) мержатся в application layer. Если ClickHouse недоступен — поля возвращаются как `null`, grid остаётся функциональным (graceful degradation).

**ClickHouse sort strategy:**

Сортировка по ClickHouse-sourced columns (revenue_30d, velocity_14d, net_pnl_30d, return_rate_pct, days_of_cover, stock_risk) требует особого подхода, т.к. primary sort в PostgreSQL невозможен по внешним данным.

| Шаг | Действие |
|-----|----------|
| 1 | ClickHouse query: `SELECT marketplace_offer_id, {sort_column} FROM {mart/fact} WHERE workspace_id = ? ORDER BY {sort_column} {direction} LIMIT 5000` |
| 2 | Получаем упорядоченный список `offer_ids` (materialized window — до 5000 IDs) |
| 3 | PostgreSQL query: фильтруем по `marketplace_offer_id IN (?)`, применяем только PostgreSQL-based filters, сохраняем порядок через `ORDER BY array_position(ARRAY[...], mo.id)` |
| 4 | Пагинация — в application layer по уже отсортированному результату |

**Ограничения:**
- Pre-fetch window: 5000 offers. Если workspace содержит > 5000 offers, последние страницы могут быть неточными. Для MVP это допустимо.
- PostgreSQL filters могут отсечь часть IDs из ClickHouse-window, уменьшив totalElements. Компенсация: `totalElements` показывает фактическое число после фильтрации.
- Performance: ClickHouse query < 100ms, PostgreSQL `IN (5000)` с index scan < 200ms.

**Fallback при невалидной колонке:** если `sort` column не в PostgreSQL whitelist и не в ClickHouse list → 400 Bad Request.

**Fallback при ClickHouse unavailable:**
- Запрос с sort по CH-колонке + CH недоступен → ответ 200 с fallback: сортировка заменяется на `last_sync_at DESC` (дефолтная PG-сортировка). Response header `X-Sort-Fallback: true`. Frontend показывает info-toast: «Сортировка по аналитическим данным временно недоступна, применена сортировка по умолчанию».
- Запрос с sort по PG-колонке + CH недоступен → CH-enriched поля = `null`, сортировка работает нормально (graceful degradation).

### Offer Detail

| Endpoint | Method | Описание |
|----------|--------|----------|
| `/api/workspaces/{workspaceId}/offers/{offerId}` | GET | Composite detail для offer (Detail Panel) |

**Offer detail response:**

```json
{
  "offerId": 12345,
  "skuCode": "ABC-001",
  "productName": "Футболка синяя",
  "marketplaceType": "WB",
  "connectionName": "Мой кабинет WB",
  "status": "ACTIVE",
  "category": "Футболки",
  "currentPrice": 1500.00,
  "discountPrice": 1200.00,
  "costPrice": 600.00,
  "marginPct": 60.0,
  "availableStock": 142,
  "daysOfCover": 18.5,
  "stockRisk": "NORMAL",
  "revenue30d": 45000.00,
  "netPnl30d": 12000.00,
  "velocity14d": 3.2,
  "returnRatePct": 4.1,
  "activePolicy": {
    "policyId": 42,
    "name": "Маржа 25% WB",
    "strategyType": "TARGET_MARGIN",
    "executionMode": "SEMI_AUTO"
  },
  "lastDecision": {
    "decisionId": 789,
    "decisionType": "CHANGE",
    "currentPrice": 1500.00,
    "targetPrice": 1200.00,
    "explanationSummary": "[Решение] CHANGE: 1 500 → 1 200 (−20.0%)\n...",
    "createdAt": "2026-03-30T10:00:00Z"
  },
  "lastAction": {
    "actionId": 456,
    "status": "SUCCEEDED",
    "targetPrice": 1200.00,
    "executionMode": "LIVE",
    "createdAt": "2026-03-30T10:01:00Z"
  },
  "promoStatus": {
    "participating": true,
    "campaignName": "Весенняя распродажа",
    "promoPrice": 1100.00,
    "endsAt": "2026-04-15T00:00:00Z"
  },
  "manualLock": null,
  "simulatedPrice": null,
  "lastSyncAt": "2026-03-30T14:23:00Z",
  "dataFreshness": "FRESH"
}
```

Composite query: PostgreSQL (canonical state, pricing, execution, promo) + ClickHouse (analytics enrichment). Аналогична grid query, но для одного offer с полным набором полей.

**Response shape convention:** Grid response — **flat** (все поля на верхнем уровне, строки для AG Grid). Detail response — **nested** (вложенные объекты `activePolicy`, `lastDecision`, `lastAction`, `promoStatus` с расширенными полями). Разные DTO: `GridRowResponse` (flat) и `OfferDetailResponse` (nested). MapStruct маппит из одних и тех же domain-сущностей в два разных формата.

### Saved Views

| Endpoint | Method | Описание |
|----------|--------|----------|
| `/api/workspaces/{workspaceId}/views` | GET | Список saved views текущего пользователя |
| `/api/workspaces/{workspaceId}/views` | POST | Создать saved view |
| `/api/workspaces/{workspaceId}/views/{viewId}` | PUT | Обновить saved view |
| `/api/workspaces/{workspaceId}/views/{viewId}` | DELETE | Удалить saved view |

**Create/Update request body (POST / PUT):**

```json
{
  "name": "Высокая маржа WB",
  "isDefault": false,
  "filters": {
    "marketplace_type": ["WB"],
    "status": ["ACTIVE"],
    "margin_min": 25.0
  },
  "sortColumn": "margin_pct",
  "sortDirection": "DESC",
  "visibleColumns": ["sku_code", "product_name", "current_price", "margin_pct", "velocity_14d"],
  "groupBySku": false
}
```

**View list response (GET):**

```json
[
  {
    "viewId": 1,
    "name": "Все товары",
    "isDefault": true,
    "isSystem": true,
    "createdAt": "2026-01-15T10:00:00Z"
  },
  {
    "viewId": 5,
    "name": "Высокая маржа WB",
    "isDefault": false,
    "isSystem": false,
    "createdAt": "2026-03-20T14:00:00Z"
  }
]
```

`isSystem = true` для seeded default views — не редактируемые и не удаляемые.

### Working Queues

| Endpoint | Method | Описание |
|----------|--------|----------|
| `/api/workspaces/{workspaceId}/queues` | GET | Список очередей с count per queue |
| `/api/workspaces/{workspaceId}/queues` | POST | Создать очередь |
| `/api/workspaces/{workspaceId}/queues/{queueId}` | PUT | Обновить очередь (name, auto_criteria, enabled) |
| `/api/workspaces/{workspaceId}/queues/{queueId}` | DELETE | Удалить очередь (каскадно удаляет assignments) |
| `/api/workspaces/{workspaceId}/queues/{queueId}/items` | GET | Paginated items в очереди. Filters: `?status=PENDING&assignedToMe=true` |
| `/api/workspaces/{workspaceId}/queues/{queueId}/items/{itemId}/claim` | POST | Взять item в работу (assign to current user). CAS: `WHERE status = 'PENDING'` |
| `/api/workspaces/{workspaceId}/queues/{queueId}/items/{itemId}/done` | POST | Отметить item как done |
| `/api/workspaces/{workspaceId}/queues/{queueId}/items/{itemId}/dismiss` | POST | Отклонить item |
| `/api/workspaces/{workspaceId}/queues/{queueId}/items` | POST | Вручную добавить entity в очередь. Body: `{ entityType, entityId, note }` |

**Queue list response (GET /queues):**

```json
[
  {
    "queueId": 1,
    "name": "Failed Actions",
    "queueType": "ATTENTION",
    "pendingCount": 3,
    "inProgressCount": 1,
    "totalActiveCount": 4
  },
  {
    "queueId": 2,
    "name": "Pending Approvals",
    "queueType": "DECISION",
    "pendingCount": 12,
    "inProgressCount": 0,
    "totalActiveCount": 12
  }
]
```

**Queue items response (GET /queues/{id}/items):**

```json
{
  "content": [
    {
      "itemId": 101,
      "entityType": "price_action",
      "entityId": 456,
      "status": "PENDING",
      "assignedTo": null,
      "note": null,
      "createdAt": "2026-03-30T08:00:00Z",
      "entitySummary": {
        "offerName": "Футболка синяя",
        "skuCode": "ABC-001",
        "actionStatus": "FAILED",
        "lastError": "HTTP 429 rate limit"
      }
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 4,
  "totalPages": 1
}
```

`entitySummary` — denormalized snippet для отображения в очереди без дополнительных запросов. Формат зависит от `entityType`.

**Query parameters для items:** `?status=PENDING&assignedToMe=true&page=0&size=20`

### Journals

| Endpoint | Method | Описание |
|----------|--------|----------|
| `/api/workspaces/{workspaceId}/offers/{offerId}/price-journal` | GET | Price journal для offer (paginated) |
| `/api/workspaces/{workspaceId}/offers/{offerId}/promo-journal` | GET | Promo journal для offer (paginated) |

**Journal query parameters:**

| Parameter | Type | Описание |
|-----------|------|----------|
| `page` | int | Номер страницы (0-based) |
| `size` | int | Размер страницы (default 20) |
| `from` | ISO date | Начало периода (inclusive, по `created_at`) |
| `to` | ISO date | Конец периода (inclusive) |
| `decisionType` | string | Фильтр: CHANGE / SKIP / HOLD (price) или PARTICIPATE / DECLINE / DEACTIVATE / PENDING_REVIEW (promo) |
| `actionStatus` | string | Фильтр по статусу action: SUCCEEDED / FAILED / ... |

### Mismatch Monitor

| Endpoint | Method | Описание |
|----------|--------|----------|
| `/api/workspaces/{workspaceId}/mismatches` | GET | Active mismatches (paginated). Filters: `?type=PRICE&sourcePlatform=...&severity=...` |
| `/api/workspaces/{workspaceId}/mismatches/{mismatchId}/acknowledge` | POST | Acknowledge mismatch (оператор подтверждает осведомлённость) |
| `/api/workspaces/{workspaceId}/mismatches/{mismatchId}/resolve` | POST | Resolve mismatch. Body: `{ resolution, note }` |

**Mismatch list response:**

```json
{
  "content": [
    {
      "mismatchId": 77,
      "type": "PRICE",
      "offerId": 12345,
      "offerName": "Футболка синяя",
      "skuCode": "ABC-001",
      "expectedValue": "1200.00",
      "actualValue": "1500.00",
      "deltaPct": 25.0,
      "severity": "WARNING",
      "status": "ACTIVE",
      "detectedAt": "2026-03-30T15:00:00Z",
      "connectionName": "Мой кабинет WB"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5,
  "totalPages": 1
}
```

**Resolution types:** `ACCEPTED` (расхождение ожидаемо, no action), `REPRICED` (запущен manual pricing run), `INVESTIGATED` (причина найдена и задокументирована), `AUTO_RESOLVED` (расхождение исчезло при следующем sync).

### Actions (delegated to Execution/Pricing/Promotions modules)

| Endpoint | Method | Описание | Delegated to |
|----------|--------|----------|--------------|
| `/api/workspaces/{workspaceId}/actions/{actionId}/approve` | POST | Одобрить price action | [Execution](execution.md) |
| `/api/workspaces/{workspaceId}/actions/{actionId}/hold` | POST | Поставить on hold | [Execution](execution.md) |
| `/api/workspaces/{workspaceId}/actions/{actionId}/cancel` | POST | Отменить action | [Execution](execution.md) |
| `/api/workspaces/{workspaceId}/offers/{offerId}/lock` | POST | Ручная блокировка цены | [Pricing](pricing.md) |
| `/api/workspaces/{workspaceId}/offers/{offerId}/unlock` | POST | Снять блокировку | [Pricing](pricing.md) |
| `/api/workspaces/{workspaceId}/pricing/runs` | POST | Запустить pricing run | [Pricing](pricing.md) |
| `/api/workspaces/{workspaceId}/promo/products/{promoProductId}/participate` | POST | Принять участие в промо | [Promotions](promotions.md) |
| `/api/workspaces/{workspaceId}/promo/products/{promoProductId}/decline` | POST | Отклонить участие в промо | [Promotions](promotions.md) |
| `/api/workspaces/{workspaceId}/promo/products/{promoProductId}/deactivate` | POST | Удалить из акции (PARTICIPATING → DEACTIVATE) | [Promotions](promotions.md) |

## Модель данных

### Таблицы PostgreSQL

| Таблица | Назначение |
|---------|------------|
| `saved_view` | Персональные пресеты фильтров и сортировок для operational grid |
| `working_queue_definition` | Правила очереди: filter criteria, название, тип |
| `working_queue_assignment` | Назначение элемента в очереди: entity, assigned user, status |

### Read models

Grid read model — не отдельная таблица, а denormalized JDBC query (см. §Read model). Обоснование:
- Нет необходимости в materialized view: grid refresh = per-request query.
- Избегаем complexity асинхронного обновления materialized view.
- PostgreSQL-часть запроса укладывается в 50-100ms при правильных индексах.
- ClickHouse enrichment — batched per-page, ~20-50ms.

### Required indexes (PostgreSQL)

| Table | Index | Columns |
|-------|-------|---------|
| `marketplace_offer` | `idx_mo_workspace_status` | `(marketplace_connection_id, status)` |
| `canonical_price_current` | `idx_cpc_offer` | `(marketplace_offer_id)` — PK/UNIQUE |
| `canonical_stock_current` | `idx_csc_offer_wh` | `(marketplace_offer_id, warehouse_id)` — PK/UNIQUE |
| `price_decision` | `idx_pd_offer_latest` | `(marketplace_offer_id, created_at DESC)` |
| `price_action` | `idx_pa_offer_latest` | `(marketplace_offer_id, created_at DESC)` |
| `manual_price_lock` | `idx_mpl_offer_active` | `(marketplace_offer_id) WHERE unlocked_at IS NULL` |
| `saved_view` | `idx_sv_user` | `(workspace_id, user_id)` |
| `working_queue_assignment` | `idx_wqa_queue_status` | `(queue_definition_id, status)` |
| `working_queue_assignment` | `idx_wqa_entity` | `(entity_type, entity_id)` |

## Bulk Operations & Draft Mode

Массовые операции и режим черновика — расширение Operational Grid для сценария «глобальный пересмотр цен» (альтернатива Excel export → modify → import). Детальная спецификация: [Bulk Operations & Draft Mode](../features/2026-03-31-bulk-operations-draft-mode.md).

### Bulk formula panel

Доступ: при выделении ≥ 1 строки в гриде. Кнопка «Изменить цену» в Bulk Actions Bar.

Фиксированный набор формул (не DSL):
- Увеличить/уменьшить на %
- Умножить на коэффициент
- Установить фиксированную цену
- Наценка от себестоимости
- Округлить до шага

Client-side preview (instant). Server-side dry-run при Apply (constraints + guards через [Pricing](pricing.md) `POST /api/pricing/bulk-manual/preview`).

### Bulk cost update

Массовое изменение себестоимости (SCD2). Кнопка «Себестоимость» в Bulk Actions Bar. Операции: фиксированная, % увеличение/уменьшение, коэффициент. API: `POST /api/cost-profiles/bulk-update`.

### Draft Mode

Toggle «Черновик» в toolbar грида. При активации:
- Колонка `current_price` становится editable (double-click → number input)
- Изменения хранятся **клиентски** (`Map<offerId, DraftChange>`)
- Diff visualization: зачёркнутая старая цена, projected margin
- Draft banner: counter, avg %, min margin, guards warning
- «Показать diff» — фильтрует grid до изменённых строк

Apply flow:
1. «Применить» → `POST /api/pricing/bulk-manual/preview` (dry-run)
2. Confirmation modal с summary + guards warnings
3. `POST /api/pricing/bulk-manual/apply` → pricing_run (MANUAL_BULK) → decisions (MANUAL_OVERRIDE) → actions (APPROVED)
4. Draft очищается, grid refresh

Draft **не** persisted на сервере. `beforeunload` warning при несохранённых изменениях.

### Select all matching

При работе с bulk operations пользователь может выбрать все строки, matching текущим фильтрам (не только текущую страницу).

**Endpoint:** `GET /api/workspaces/{workspaceId}/grid/matching-ids?[filters]`

Response: `{ "offerIds": [1, 2, 3, ...], "totalCount": 347 }`

**Limits:** max 500 offer IDs в ответе. Если matching > 500 → response включает `"truncated": true` и `"totalCount"` с полным числом. UI показывает warning: «Выбрано 500 из 1 234 товаров. Сузьте фильтры для выбора всех.»

**Связь с bulk operations:** `matching-ids` endpoint возвращает PG-only offer IDs (без CH enrichment). Полученные IDs используются как input для formula panel / draft mode. Limit 500 синхронизирован с лимитом bulk-manual API (max 500 offers per request).

### Permissions

| Операция | Roles |
|----------|-------|
| Bulk price formula | PRICING_MANAGER, ADMIN, OWNER |
| Bulk cost update | PRICING_MANAGER, ADMIN, OWNER |
| Draft mode | PRICING_MANAGER, ADMIN, OWNER |

### Пользовательский сценарий: SC-8 — Массовый пересмотр цен

1. Оператор открывает Operational Grid, применяет фильтр (например, категория «Футболки», маркетплейс WB)
2. Выделяет все отфильтрованные строки (или «Select all matching»)
3. Нажимает «Изменить цену» → Formula Panel
4. Выбирает «Увеличить на %», значение 5%, округление до 10 ₽
5. Видит instant preview: средний %, min маржа, количество заблокированных
6. Нажимает «Применить» → server-side dry-run → confirmation modal
7. Подтверждает → pricing_run создаётся → actions отправляются на исполнение
8. Grid обновляется: `lastActionStatus` = APPROVED → SCHEDULED → SUCCEEDED (WebSocket push)

## Real-time updates (WebSocket)

Grid и queues получают real-time обновления через STOMP/WebSocket (инфраструктура — модуль `datapulse-platform`).

### Topics

| Topic | Payload | Описание |
|-------|---------|----------|
| `/topic/workspaces/{workspaceId}/actions` | `{ actionId, offerId, status, updatedAt }` | Статус price/promo action изменился (APPROVED → SCHEDULED → SUCCEEDED / FAILED) |
| `/topic/workspaces/{workspaceId}/sync` | `{ connectionId, status, completedAt }` | ETL sync завершён — grid может обновить данные |
| `/topic/workspaces/{workspaceId}/queues` | `{ queueId, pendingDelta, inProgressDelta }` | Изменение counts в очереди (new assignment, claim, done) |

**Frontend-реакция:**
- Action status → обновить `lastActionStatus` в grid row (optimistic update по `offerId`). При SUCCEEDED/FAILED — invalidate TanStack Query для grid page.
- Sync completed → invalidate grid query (refetch текущей страницы), обновить `lastSyncAt` / `dataFreshness`.
- Queue delta → обновить badge counts в sidebar queue list.

## Обязательные свойства

- Server-side filtering, sorting, pagination — dedicated read models.
- Data freshness indicators на гриде (last sync time, stale markers).
- Working queue assignment не блокирует другие операции.
- Dynamic sorting через whitelist (DTO field → SQL column mapping; SQL injection prevention).
- CSV export — streaming response (не загружать весь dataset в память).

## Performance

| Требование | Обоснование |
|------------|-------------|
| Grid page load < 200ms (P95) | Server-side pagination + batched ClickHouse enrichment |
| Server-side filtering, sorting, pagination | Клиент не загружает полный dataset |
| Dedicated read models | Операционные screens не читают из write-оптимизированных таблиц |
| Dynamic sorting через whitelist | DTO field → SQL column mapping; SQL injection prevention |
| CSV export streaming | `StreamingResponseBody` + `Content-Disposition: attachment` |

### Запрещённые anti-patterns

| Anti-pattern | Причина |
|--------------|---------|
| Wrong-store reads | Аналитика из PostgreSQL вместо ClickHouse — performance bug |
| N+1 queries | Lazy loading без batch fetch |
| Full table scans на hot tables | Отсутствие index на claim/filter columns |
| Client-side pagination | Загрузка полного dataset на клиент |
| Full-dataset CSV export | OOM risk; использовать streaming |

## CSV export specification

### Формат

- Encoding: UTF-8 with BOM (для Excel compatibility)
- Delimiter: `;` (semicolon — Excel ru locale default)
- Header row: column display names (ru)
- Rows: все строки, matching текущие фильтры (не только current page)

### Streaming

```
GET /api/workspaces/{workspaceId}/grid/export?[filters]
→ Content-Type: text/csv
→ Content-Disposition: attachment; filename="datapulse-export-{date}.csv"
→ Transfer-Encoding: chunked
```

Реализация: `StreamingResponseBody`. Cursor-based iteration (JDBC `fetchSize = 500`). Не загружает весь dataset в память.

### ClickHouse-enriched columns в export

CSV export включает **только PostgreSQL-sourced колонки**. ClickHouse-enriched колонки (`revenue_30d`, `net_pnl_30d`, `velocity_14d`, `return_rate_pct`, `days_of_cover`, `stock_risk`) **исключены** из streaming export.

**Обоснование:**
- Streaming export итерирует JDBC cursor по PostgreSQL (fetchSize = 500). Параллельный batch-enrichment из ClickHouse для 100 000 строк создаёт 200 CH-запросов, увеличивает latency в 3–5× и создаёт risk timeout.
- PG-only export покрывает основной сценарий: выгрузка каталога с ценами, маржой, остатками, статусами.
- Если пользователю нужна аналитика (revenue, velocity) — аналитический экспорт из модуля Analytics & P&L (future phase).

**Исключённые колонки в CSV header:** колонки `days_of_cover`, `stock_risk`, `revenue_30d`, `net_pnl_30d`, `velocity_14d`, `return_rate_pct` не включаются в CSV. Это отражено в visible_columns export mapping.

### Limits

- Max rows per export: 100 000 (configurable). При превышении → 400 с рекомендацией сузить фильтры.
- Timeout: 60s (configurable).

## Пользовательские сценарии

### SC-2: Ежедневная операционная работа

Operational Grid → saved view / working queue → фильтрация и сортировка SKU → просмотр explanation → принятие решения или hold.

### SC-4: Расследование прибыльности

P&L → breakdown (комиссии, логистика, штрафы, реклама, COGS) → mismatch / residual investigation.

### SC-5: Управление остатками

Stock-out risk / overstock → days of cover → решение по replenishment и pricing.

### SC-6: Анализ результата действия

Price Journal → что изменено → применилось ли → какой эффект.

### SC-7: Обработка рабочей очереди

Working Queues → claim item → investigate → approve/hold/dismiss → next item.

## Связанные модули

- [Tenancy & IAM](tenancy-iam.md) — saved views и queues per workspace; role-based access
- [Analytics & P&L](analytics-pnl.md) — ClickHouse enrichment data (P&L, inventory, returns, velocity)
- [Pricing](pricing.md) — price decisions, manual locks, pricing runs
- [Promotions](promotions.md) — promo journal, promo evaluation results, promo working queues
- [Execution](execution.md) — action status, approvals, failed action queues
- [Bulk Operations & Draft Mode](../features/2026-03-31-bulk-operations-draft-mode.md) — bulk formula, draft mode, bulk cost update
