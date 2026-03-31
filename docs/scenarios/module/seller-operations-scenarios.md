# Seller Operations — Module Scenarios

## Роль модуля

Seller Operations предоставляет операционный интерфейс для продавца: Operational Grid (two-store read model), saved views, working queues, price/promo journals и mismatch monitor. Является основным user-facing read layer.

## Сценарии

### SEL-01: Operational Grid — happy path (two-store query)

- **Назначение:** Отображение продуктового грида с данными из двух источников.
- **Trigger:** `GET /api/grid` (any role).
- **Main path:** PostgreSQL query (canonical state: offer, price, stock, last action, promo status) + ClickHouse query (analytics enrichment: `revenue_30d`, velocity, `days_of_cover`, `return_rate`) → merge → paginated response.
- **Dependencies:** Canonical data (PG) fresh. ClickHouse materialized. User workspace scoping.
- **Failure risks:** ClickHouse timeout → degrade gracefully (PG-only data, CH columns = NULL, warning banner).
- **Uniqueness:** Two-store read model — уникальный query pattern (PG + CH merge).

### SEL-02: Operational Grid — ClickHouse degradation

- **Назначение:** Grid работает при недоступности ClickHouse.
- **Trigger:** ClickHouse connection error / timeout during grid query.
- **Main path:** PG query succeeds → CH query fails → return PG data with CH columns = NULL / `N/A` → UI shows warning: «Аналитические данные временно недоступны».
- **Dependencies:** Fallback logic in grid service.
- **Failure risks:** User confused by missing data. Sorting/filtering by CH columns unavailable.
- **Uniqueness:** Degraded mode — другой response shape, другой UX.

### SEL-03: Saved Views (create, load, update)

- **Назначение:** Пользователь сохраняет набор фильтров и настроек грида для повторного использования.
- **Trigger:** `POST /api/views` (create), `GET /api/views/{id}` (load), `PUT /api/views/{id}` (update).
- **Main path:** Save filter criteria, column visibility, sort order → store in `saved_view` table → load: apply to grid query.
- **Dependencies:** User role: ANALYST+ (not VIEWER). Workspace scoping.
- **Failure risks:** View references stale filters (field removed) → graceful ignore.
- **Uniqueness:** User personalization — другой persistence (view config, не operational data).

### SEL-04: Working Queues

- **Назначение:** Распределение товаров на ручную обработку (assignment to operators).
- **Trigger:** `POST /api/queues` (create queue), `POST /api/queues/{id}/assign` (assign items).
- **Main path:** Create queue с filter criteria → assign matching items to operators → operators see their queue → process items → mark done.
- **Dependencies:** User role: OPERATOR+. Items match filter. Queue owner (PRICING_MANAGER/ADMIN).
- **Failure risks:** Queue filter too broad → overwhelm operators. Stale queue (items changed since assignment).
- **Uniqueness:** Assignment workflow — другой interaction pattern (queue-based, не grid browse).

### SEL-05: Price Journal — action history

- **Назначение:** Просмотр истории ценовых действий для конкретного offer.
- **Trigger:** `GET /api/offers/{offerId}/price-journal` (any role).
- **Main path:** Query `price_action` history → enrich with decision explanations → paginated response. Shows: `old_price`, `new_price`, status, `created_at`, reason, who approved.
- **Dependencies:** `price_action` table. `pricing_decision` explanations. Workspace scoping.
- **Failure risks:** Large history → pagination. Missing explanations for old decisions → graceful null.
- **Uniqueness:** Read-only audit/investigation — другой data shape (timeline, не grid).

### SEL-06: Promo Journal

- **Назначение:** Просмотр истории промо-решений для конкретного offer.
- **Trigger:** `GET /api/offers/{offerId}/promo-journal` (any role).
- **Main path:** Query `promo_decision` + `promo_action` history → paginated response. Shows: campaign, decision, `margin_impact`, status.
- **Dependencies:** `promo_decision`, `promo_action` tables. Workspace scoping.
- **Failure risks:** Promo data incomplete (WB promo sync partial).
- **Uniqueness:** Promo-specific journal — другой data source (Promotions, не Execution).

### SEL-07: Mismatch Monitor

- **Назначение:** Обнаружение расхождений между expected price (наше решение) и actual price (маркетплейс).
- **Trigger:** Scheduled comparison или on-demand.
- **Main path:** Compare last successful `price_action.target_price` vs current `canonical_price_current.price` → if `|diff| > threshold` → mismatch record.
- **Dependencies:** Recent price sync. Successful price actions. Threshold configuration.
- **Failure risks:** Stale `canonical_price_current` → false mismatch. Marketplace processing delay → temporary mismatch.
- **Uniqueness:** Cross-data-source comparison — уникальный detection pattern (decision vs reality).

### SEL-08: Grid filtering and sorting

- **Назначение:** Фильтрация и сортировка грида по различным criteria.
- **Trigger:** User applies filters (brand, category, price range, status, `has_mismatch`, etc.).
- **Main path:** Build dynamic query (PG filters + CH filters) → execute → paginated result.
- **Dependencies:** Filter field whitelist (SQL injection prevention). Sort column whitelist.
- **Failure risks:** Complex filter → slow query. CH filter applied but CH unavailable → fallback to PG-only filters.
- **Uniqueness:** Dynamic query construction — другой execution path per filter combination.

### SEL-09: Grid drill-down (offer detail)

- **Назначение:** Детальный просмотр конкретного offer.
- **Trigger:** `GET /api/offers/{offerId}` (any role).
- **Main path:** Canonical offer + latest price/stock snapshots + last pricing decision + last price action + promo status + P&L summary → composite response.
- **Dependencies:** Multiple module data sources. Workspace scoping.
- **Failure risks:** Partial data (some modules' data missing) → return what's available, null for missing.
- **Uniqueness:** Composite read — aggregation из нескольких модулей в один response.

### SEL-10: Export (grid to CSV/Excel)

- **Назначение:** Экспорт данных грида для внешнего анализа.
- **Trigger:** `POST /api/grid/export` (ANALYST+).
- **Main path:** Apply current filters → stream all matching rows (no pagination limit) → generate file → return download link.
- **Dependencies:** Same as grid query. File generation. Large export → streaming to avoid OOM.
- **Failure risks:** Very large export → timeout. Mitigation: async export with notification.
- **Uniqueness:** Bulk read + file generation — другой output format, другой performance profile.

### SEL-11: Working queue auto-population (scheduled criteria evaluation)

- **Назначение:** Автоматическое наполнение working queues по заданным criteria.
- **Trigger:** Scheduled job (every 5 min).
- **Main path:** Для каждого enabled `working_queue_definition` с `auto_criteria` → evaluate criteria: PostgreSQL query (e.g. `price_action.status = 'FAILED'`) + optional ClickHouse enrichment (e.g. `stock_out_risk = 'CRITICAL'`) → merge results → INSERT `working_queue_assignment` (status = PENDING) → `ON CONFLICT DO NOTHING` (idempotent).
- **Dependencies:** `working_queue_definition.auto_criteria` JSONB. PostgreSQL data (canonical state). ClickHouse data (analytics enrichment, optional). Scheduled job infrastructure.
- **Failure risks:** ClickHouse down → queues с CH-only criteria не обновляются (PG-only queues unaffected). Criteria too broad → queue flooded. Criteria change → old assignments not cleaned up (separate auto-resolution handles this).
- **Uniqueness:** Scheduled auto-population — другой trigger (scheduler, не user action). Cross-store criteria evaluation (PG + CH). Idempotent INSERT.

### SEL-12: Working queue auto-resolution

- **Назначение:** Автоматическое завершение queue assignments, когда условие больше не выполняется.
- **Trigger:** Auto-population job (same 5 min schedule) — вторая фаза после population.
- **Main path:** Для каждого active assignment (status = PENDING/IN_PROGRESS) → re-evaluate criteria → condition no longer matches (e.g. action перешёл из FAILED в SUCCEEDED через retry) → UPDATE assignment status = DONE. Operator notification optional.
- **Dependencies:** Same criteria evaluation as SEL-11. Assignment status lifecycle.
- **Failure risks:** Auto-resolution removes item that operator was actively investigating (status = IN_PROGRESS) → mitigation: only auto-resolve PENDING, not IN_PROGRESS. Stale CH data → false auto-resolution.
- **Uniqueness:** Condition-driven cleanup — другой trigger (condition change, не user action). Аналог AUD-08 (alert auto-resolution) для working queues.
