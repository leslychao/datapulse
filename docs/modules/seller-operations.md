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

Группировка по `seller_sku` доступна через saved view с group-by.

### Grid columns

| Column | Source | Store | Описание |
|--------|--------|-------|----------|
| `sku_code` | `seller_sku.sku_code` | PostgreSQL | Артикул продавца |
| `product_name` | `marketplace_offer.name` | PostgreSQL | Название товара |
| `marketplace_type` | `marketplace_connection.marketplace_type` | PostgreSQL | WB / Ozon |
| `connection_name` | `marketplace_connection.name` | PostgreSQL | Название подключения |
| `status` | `marketplace_offer.status` | PostgreSQL | ACTIVE / INACTIVE |
| `category` | `category.name` (via marketplace_offer.category_id FK) | PostgreSQL | Категория |
| `current_price` | `canonical_price_current.price` | PostgreSQL | Текущая цена |
| `discount_price` | `canonical_price_current.discount_price` | PostgreSQL | Цена со скидкой |
| `cost_price` | `cost_profile.cost_price` (SCD2 current) | PostgreSQL | Себестоимость |
| `margin_pct` | Computed: `(current_price - cost_price) / current_price × 100` | Computed | Маржинальность % |
| `available_stock` | `canonical_stock_current.available` (SUM across warehouses) | PostgreSQL | Доступный остаток |
| `days_of_cover` | `mart_inventory_analysis.days_of_cover` (latest) | ClickHouse | Дней до stock-out |
| `stock_risk` | `mart_inventory_analysis.stock_out_risk` | ClickHouse | CRITICAL / WARNING / NORMAL |
| `revenue_30d` | `SUM(fact_finance.revenue_amount)` WHERE 30 days | ClickHouse | Выручка за 30 дней |
| `net_pnl_30d` | `SUM(mart_product_pnl measures)` WHERE 30 days | ClickHouse | Чистая P&L за 30 дней |
| `velocity_14d` | `SUM(fact_sales.quantity)` / 14 | ClickHouse | Средние продажи в день |
| `return_rate_pct` | `mart_returns_analysis.return_rate_pct` | ClickHouse | % возвратов |
| `active_policy` | `price_policy.name` (via assignment resolution) | PostgreSQL | Активная ценовая политика |
| `last_decision` | `price_decision` (latest) | PostgreSQL | Последнее решение (CHANGE / SKIP / HOLD) |
| `last_action_status` | `price_action.status` (latest) | PostgreSQL | Статус последнего action |
| `promo_status` | `canonical_promo_product.participation_status` (active campaigns) | PostgreSQL | В промо? (PARTICIPATING / ELIGIBLE / —) |
| `manual_lock` | `manual_price_lock EXISTS` | PostgreSQL | Ручная блокировка |
| `simulated_price` | `simulated_offer_state.simulated_price` | PostgreSQL | Симулированная цена (Phase F; nullable) |
| `simulated_delta_pct` | `simulated_offer_state.price_delta_pct` | PostgreSQL | Разница simulated vs canonical (%) |
| `last_sync_at` | `marketplace_sync_state.last_success_at` | PostgreSQL | Время последней синхронизации |
| `data_freshness` | Computed: stale if > threshold | Computed | FRESH / STALE |

### Read model

Grid строится из **dedicated read model** — denormalized view/query, оптимизированный для фильтрации, сортировки и пагинации.

**Archirecture:** grid read model — JDBC repository с динамическим SQL. Не JPA (N+1 risk). Не materialized view (сложность обновления).

```
Grid query structure:

  PostgreSQL (main query):
    marketplace_offer
    LEFT JOIN seller_sku
    LEFT JOIN product_master
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
| `stock_risk` | Enum | ClickHouse-enriched (post-filter) |
| `has_manual_lock` | Boolean | `manual_price_lock EXISTS` |
| `has_active_promo` | Boolean | `canonical_promo_product EXISTS` |
| `last_decision` | Enum | Latest `price_decision.decision_type` |
| `last_action_status` | Enum | Latest `price_action.status` |

**Dynamic sorting whitelist:**

```java
Map<String, String> SORT_WHITELIST = Map.of(
    "sku_code",       "ss.sku_code",
    "product_name",   "mo.name",
    "current_price",  "cpc.price",
    "margin_pct",     "(cpc.price - cp.cost_price) / NULLIF(cpc.price, 0)",
    "available_stock", "stock_agg.total_available",
    "last_sync_at",   "mss.last_success_at"
);
```

ClickHouse-sourced columns (revenue_30d, velocity_14d, etc.) — post-sort в application layer. Если primary sort = ClickHouse column → pre-fetch sorted IDs из ClickHouse, затем join в PostgreSQL.

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
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()

  UNIQUE (workspace_id, name)
```

```
working_queue_assignment:
  id                    BIGSERIAL PK
  queue_definition_id   BIGINT FK → working_queue_definition      NOT NULL
  entity_type           VARCHAR(60) NOT NULL                      -- 'marketplace_offer', 'price_action', 'promo_action'
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

### Queue lifecycle

```
PENDING → IN_PROGRESS → DONE
                      → DISMISSED
PENDING → DISMISSED
```

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

Auto-population job (scheduled, 5 min): evaluates `auto_criteria` → INSERT new assignments для matching entities. Idempotent — `ON CONFLICT DO NOTHING` на unique index.

Auto-resolution: когда condition больше не матчит (e.g. action перешёл из FAILED в SUCCEEDED через retry) → next auto-population run помечает assignment как DONE.

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

### REST API

`GET /api/workspace/{workspaceId}/offers/{offerId}/price-journal?page=0&size=20`

## Promo Journal

Read-only view: история участия marketplace_offer в промо-акциях.

### Data source

```
promo_decision
  JOIN promo_action (LEFT)
  JOIN canonical_promo_campaign
  JOIN canonical_promo_product
WHERE marketplace_offer_id = :offer_id
ORDER BY promo_decision.created_at DESC
```

### Journal entry fields

| Field | Source | Описание |
|-------|--------|----------|
| `promo_name` | `canonical_promo_campaign.promo_name` | Название акции |
| `promo_type` | `canonical_promo_campaign.promo_type` | Тип акции |
| `period` | `date_from .. date_to` | Период акции |
| `evaluation_result` | `promo_decision.evaluation_result` | PROFITABLE / MARGINAL / UNPROFITABLE / ... |
| `participation_decision` | `promo_decision.participation_decision` | PARTICIPATE / DECLINE / PENDING |
| `action_status` | `promo_action.status` | Статус promo action |
| `required_price` | `canonical_promo_product.required_price` | Цена участия |
| `estimated_margin_impact` | `promo_decision.estimated_margin_impact` | Оценка влияния на маржу |

## Mismatch Monitor

Визуализация расхождений между связанными data domains.

### Mismatch types

| Type | Comparison | Описание |
|------|-----------|----------|
| Price mismatch | `canonical_price_current.price` vs last `price_action.target_price` WHERE SUCCEEDED | Текущая цена ≠ последней успешно установленной |
| Stock inconsistency | `canonical_stock_current` vs `fact_inventory_snapshot` (last) | Расхождение между canonical и analytics |
| Promo participation | `canonical_promo_product.participation_status` vs `promo_action` outcome | Ожидаемое участие ≠ фактическое |
| Finance gap | Missing `canonical_finance_entry` for expected periods | Пропуски в финансовых данных |

### Implementation

Mismatch checker (scheduled, after each sync) записывает результаты в `alert_event` (rule_type = MISMATCH). UI отображает active mismatches с drill-down к конкретным offers.

## REST API contracts

### Grid

| Endpoint | Method | Описание |
|----------|--------|----------|
| `/api/workspace/{workspaceId}/grid` | GET | Paginated grid с фильтрами и сортировкой |
| `/api/workspace/{workspaceId}/grid/export` | GET | CSV export (streaming, `Content-Disposition: attachment`) |

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

**Fallback:** если `sort` column не в PostgreSQL whitelist и не в ClickHouse list → 400 Bad Request.

### Saved Views

| Endpoint | Method | Описание |
|----------|--------|----------|
| `/api/workspace/{workspaceId}/views` | GET | Список saved views текущего пользователя |
| `/api/workspace/{workspaceId}/views` | POST | Создать saved view |
| `/api/workspace/{workspaceId}/views/{viewId}` | PUT | Обновить saved view |
| `/api/workspace/{workspaceId}/views/{viewId}` | DELETE | Удалить saved view |

### Working Queues

| Endpoint | Method | Описание |
|----------|--------|----------|
| `/api/workspace/{workspaceId}/queues` | GET | Список очередей с count per queue |
| `/api/workspace/{workspaceId}/queues/{queueId}/items` | GET | Paginated items в очереди |
| `/api/workspace/{workspaceId}/queues/{queueId}/items/{itemId}/claim` | POST | Взять item в работу (assign to current user) |
| `/api/workspace/{workspaceId}/queues/{queueId}/items/{itemId}/done` | POST | Отметить item как done |
| `/api/workspace/{workspaceId}/queues/{queueId}/items/{itemId}/dismiss` | POST | Отклонить item |

### Journals

| Endpoint | Method | Описание |
|----------|--------|----------|
| `/api/workspace/{workspaceId}/offers/{offerId}/price-journal` | GET | Price journal для offer (paginated) |
| `/api/workspace/{workspaceId}/offers/{offerId}/promo-journal` | GET | Promo journal для offer (paginated) |

### Actions (delegated to Execution/Pricing/Promotions modules)

| Endpoint | Method | Описание | Delegated to |
|----------|--------|----------|--------------|
| `/api/workspace/{workspaceId}/actions/{actionId}/approve` | POST | Одобрить price action | [Execution](execution.md) |
| `/api/workspace/{workspaceId}/actions/{actionId}/hold` | POST | Поставить on hold | [Execution](execution.md) |
| `/api/workspace/{workspaceId}/actions/{actionId}/cancel` | POST | Отменить action | [Execution](execution.md) |
| `/api/workspace/{workspaceId}/offers/{offerId}/lock` | POST | Ручная блокировка цены | [Pricing](pricing.md) |
| `/api/workspace/{workspaceId}/offers/{offerId}/unlock` | POST | Снять блокировку | [Pricing](pricing.md) |
| `/api/workspace/{workspaceId}/pricing/runs` | POST | Запустить pricing run | [Pricing](pricing.md) |

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
GET /api/workspace/{workspaceId}/grid/export?[filters]
→ Content-Type: text/csv
→ Content-Disposition: attachment; filename="datapulse-export-{date}.csv"
→ Transfer-Encoding: chunked
```

Реализация: `StreamingResponseBody`. Cursor-based iteration (JDBC `fetchSize = 500`). Не загружает весь dataset в память.

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
