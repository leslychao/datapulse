# Datapulse — Data Model (Shared Overview)

## Data pipeline

Данные проходят через строго последовательный pipeline. Пропуск стадий запрещён.

```
API маркетплейсов → Raw (S3) → Normalized (in-process) → Canonical (PostgreSQL) → Analytics (ClickHouse)
```

| Layer | Хранилище | Назначение | Детали |
|-------|-----------|------------|--------|
| Raw | S3-compatible (MinIO) | Immutable source-faithful хранилище | [ETL Pipeline](modules/etl-pipeline.md) |
| Normalized | In-process | Типизированное представление провайдера | [ETL Pipeline](modules/etl-pipeline.md) |
| Canonical | PostgreSQL | Marketplace-agnostic бизнес-модель | [ETL Pipeline](modules/etl-pipeline.md) |
| Analytics | ClickHouse | Derived facts, marts, projections | [Analytics & P&L](modules/analytics-pnl.md) |

## Канонические сущности (обзор)

| Сущность | Категория | Модуль-владелец |
|----------|-----------|-----------------|
| `CanonicalOffer` (product_master, seller_sku, marketplace_offer) | State | [ETL Pipeline](modules/etl-pipeline.md) |
| `CanonicalPriceCurrent` | State | [ETL Pipeline](modules/etl-pipeline.md) |
| `CanonicalStockCurrent` | State | [ETL Pipeline](modules/etl-pipeline.md) |
| `CanonicalOrder` | Flow | [ETL Pipeline](modules/etl-pipeline.md) |
| `CanonicalSale` | Flow | [ETL Pipeline](modules/etl-pipeline.md) |
| `CanonicalReturn` | Flow | [ETL Pipeline](modules/etl-pipeline.md) |
| `CanonicalFinanceEntry` | Flow | [ETL Pipeline](modules/etl-pipeline.md) |
| `CanonicalPromoCampaign` | State | [ETL Pipeline](modules/etl-pipeline.md) |
| `CanonicalPromoProduct` | State | [ETL Pipeline](modules/etl-pipeline.md) |
| `cost_profile` (SCD2) | State | [ETL Pipeline](modules/etl-pipeline.md) |
| `category` | Dict | [ETL Pipeline](modules/etl-pipeline.md) |
| `warehouse` | Dict | [ETL Pipeline](modules/etl-pipeline.md) |

## Star schema (обзор)

| Тип | Таблицы | Модуль-владелец |
|-----|---------|-----------------|
| Dimensions | `dim_product`, `dim_warehouse`, `dim_category`, `dim_promo_campaign`, `dim_advertising_campaign` | [Analytics & P&L](modules/analytics-pnl.md), [Promotions](modules/promotions.md) (`dim_promo_campaign`), Analytics (`dim_advertising_campaign`) |
| P&L facts | `fact_finance`, `fact_sales`, `fact_product_cost`, `fact_advertising` | [Analytics & P&L](modules/analytics-pnl.md) |
| Operational facts | `fact_orders`, `fact_returns`, `fact_price_snapshot`, `fact_inventory_snapshot`, `fact_promo_product` | [Analytics & P&L](modules/analytics-pnl.md), [Promotions](modules/promotions.md) (`fact_promo_product`) |
| Marts | `mart_posting_pnl`, `mart_product_pnl`, `mart_inventory_analysis`, `mart_returns_analysis`, `mart_promo_product_analysis` | [Analytics & P&L](modules/analytics-pnl.md), [Promotions](modules/promotions.md) (`mart_promo_product_analysis`) |

## Ключевые таблицы PostgreSQL по модулям

| Модуль | Таблицы |
|--------|---------|
| [Tenancy & IAM](modules/tenancy-iam.md) | `tenant`, `workspace`, `app_user`, `workspace_member`, `workspace_invitation` |
| [Integration](modules/integration.md) | `marketplace_connection`, `secret_reference`, `marketplace_sync_state`, `integration_call_log` |
| [ETL Pipeline](modules/etl-pipeline.md) | `category`, `warehouse`, `product_master`, `seller_sku`, `marketplace_offer`, `cost_profile`, `canonical_price_current`, `canonical_stock_current`, `canonical_order`, `canonical_sale`, `canonical_return`, `canonical_finance_entry`, `canonical_promo_campaign`, `canonical_promo_product`, `job_execution`, `job_item` |
| [Pricing](modules/pricing.md) | `price_policy` (versioned, `last_preview_version` for mandatory preview gate), `price_policy_assignment`, `price_decision` (policy_snapshot, explanation format, strategy_type `MANUAL_OVERRIDE` for bulk), `pricing_run` (trigger_type incl. `MANUAL_BULK`, `request_hash`; statuses: PENDING, IN_PROGRESS, COMPLETED, COMPLETED_WITH_ERRORS, FAILED, PAUSED, CANCELLED), `manual_price_lock` |
| [Execution](modules/execution.md) | `price_action` (extended: decision FK, audit fields, reconciliation_source), `price_action_attempt` (provider request/response summaries, reconciliation read), `deferred_action`, `outbox_event`, `simulated_offer_state` |
| [Promotions](modules/promotions.md) | `promo_policy` (versioned), `promo_policy_assignment`, `promo_evaluation_run`, `promo_evaluation`, `promo_decision` (policy_snapshot), `promo_action`, `promo_action_attempt`. Canonical truth: `canonical_promo_campaign`, `canonical_promo_product` (owned by ETL, read + updated by Promotions) |
| [Seller Operations](modules/seller-operations.md) | `saved_view`, `working_queue_definition`, `working_queue_assignment` |
| [Audit & Alerting](modules/audit-alerting.md) | `audit_log`, `alert_rule`, `alert_event`, `user_notification` |

## Источники истины

| Данные | Source of truth | Запрещено |
|--------|----------------|-----------|
| Business state (tenancy, decisions, actions) | PostgreSQL | ClickHouse, Redis, RabbitMQ, S3 |
| Canonical State | PostgreSQL | Raw layer, normalized DTO |
| Canonical Flow | PostgreSQL (write); ClickHouse (analytical reads) | Raw JSON, provider DTO |
| Входы pricing: current state | Canonical State (PostgreSQL) | Raw/normalized data |
| Входы pricing: derived signals | Analytics (ClickHouse) через signal assembler | Direct ClickHouse reads |
| Action lifecycle state (price + promo) | PostgreSQL | RabbitMQ, Redis |
| Retry truth | PostgreSQL | RabbitMQ TTL, in-memory |
| Historical analytics | ClickHouse | — |
| Raw evidence / replay | S3-compatible | — |

## Идентификаторы

| Поле | Описание |
|------|----------|
| `connection_id` / `marketplace_connection_id` | FK на `marketplace_connection.id`. Единый tenant-isolation key для ETL, canonical и analytics слоёв. Каждый canonical/fact record привязан к конкретному подключению маркетплейса. **Naming convention:** flow/finance таблицы (`canonical_order`, `canonical_sale`, `canonical_return`, `canonical_finance_entry`, `canonical_promo_campaign`) используют короткое имя `connection_id`; каталожные таблицы (`marketplace_offer`, `category`, `warehouse`) — полное `marketplace_connection_id`. Оба поля указывают на один FK. В ClickHouse повсеместно используется `connection_id` |
| `posting_id` | Каноничный идентификатор отправки/строки. Resolved в canonical layer из provider-specific полей (см. [ETL Pipeline](modules/etl-pipeline.md) §Canonical finance resolution rules) |
| `order_id` | Каноничный идентификатор заказа. Resolved в canonical layer |
| `seller_sku_id` | FK на `seller_sku`. Resolved через canonical offer lookup |
| `operation_id` | Provider-specific ID финансовой операции (metadata для traceability). **Не используется** в sorting key fact_finance |
| `entry_id` | PK `canonical_finance_entry` (bigint). **Grain key** для `fact_finance`, FK для cross-store drill-down (ClickHouse → PostgreSQL). Используется в sorting key fact_finance |

## Runtime entrypoints

| Entrypoint | Phase | Ответственность |
|------------|-------|-----------------|
| `datapulse-api` | A | REST API: tenancy, connections, sync triggers, reads. Phase A: также outbox poller (`runtime: ALL`) и WebSocket push |
| `datapulse-ingest-worker` | A | Data pipeline: fetch → raw → normalize → canonicalize → materialize |
| `datapulse-pricing-worker` | C, F | Pricing pipeline: eligibility → decision → explanation; Promo evaluation pipeline |
| `datapulse-executor-worker` | D, F | Price action execution + Promo action execution, retries, reconciliation |

## Архитектурные инварианты

| # | Инвариант |
|---|-----------|
| 1 | Pipeline строго последовательный: Raw → Normalized → Canonical → Analytics |
| 2 | Бизнес-логика работает только с canonical и analytics |
| 3 | PostgreSQL — единственный source of truth для business state |
| 4 | ClickHouse — read-only для бизнес-логики. Pricing signal assembler читает derived signals из ClickHouse (transitive dependency). При ClickHouse unavailability — graceful degradation per signal criticality ([Pricing §Signal criticality](modules/pricing.md#signal-criticality-и-clickhouse-fallback)): REQUIRED signals cascade fallback → per-SKU HOLD; OPTIONAL signals → safe default; pricing run = COMPLETED_WITH_ERRORS (не FAILED). Полный ClickHouse failure при TARGET_MARGIN → все offers = HOLD. PRICE_CORRIDOR — не зависит от ClickHouse. Не кэширует stale signals |
| 5 | Provider DTO не протекают за границу adapter |
| 6 | Каждая каноническая запись прослеживаема до raw source |
| 7 | fact_finance материализуется 1:1 из canonical_finance_entry (composite row per entry, без группировки). Sorting key: `(connection_id, source_platform, entry_id)` |
| 8 | UPSERT с `IS DISTINCT FROM` — no-churn |
| 9 | Каждая строка fact_finance имеет `attribution_level ∈ {POSTING, PRODUCT, ACCOUNT}`. Classification exhaustive |
| 10 | `account_P&L = Σ(product_P&L) + account_level_charges` — provably true через exhaustive attribution_level |
| 11 | Standalone operations могут составлять >40% net дохода. Требуют product-level attribution через canonical `seller_sku_id` |
| 12 | `net_cogs = gross_cogs × (1 − refund_ratio)`. Revenue-ratio netting обязателен. `gross_cogs` = `fact_sales.quantity × cost_price`, `refund_ratio` = `refund_amount / revenue_amount` per applicable grain. SCD2 lookup по `cogs_date` = SALE_ACCRUAL entry's `finance_date` |
| 13 | WB `canonical_finance_entry.entryDate` = `sale_dt` (не `rr_dt`). Ozon `entryDate` = `operation_date` (Moscow TZ) |
| 14 | `mart_product_pnl` period = `toYYYYMM(finance_date)` per entry. Cash-basis accounting |

## outbox_event — unified event types

`outbox_event` — единая таблица outbox для всех async messages. Используется ETL, Pricing, Execution, Promotions.

```
outbox_event:
  id                  BIGSERIAL PK
  event_type          VARCHAR(60) NOT NULL
  aggregate_type      VARCHAR(60) NOT NULL          -- e.g. 'job_execution', 'price_action', 'promo_action'
  aggregate_id        BIGINT NOT NULL                -- FK на aggregate (job_execution.id, price_action.id, etc.)
  payload             JSONB NOT NULL                 -- event-specific payload
  status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'  -- PENDING, PUBLISHED, FAILED
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
  published_at        TIMESTAMPTZ                    -- set on successful publish
  retry_count         INT NOT NULL DEFAULT 0
  next_retry_at       TIMESTAMPTZ                    -- for FAILED events, scheduled retry
```

### Event type registry

| event_type | Module | Consumer | Exchange / Queue | Описание |
|------------|--------|----------|------------------|----------|
| `ETL_SYNC_EXECUTE` | ETL Pipeline | `datapulse-ingest-worker` | `etl.sync` / `etl.sync` | Запуск ETL sync run |
| `ETL_SYNC_RETRY` | ETL Pipeline | `datapulse-ingest-worker` | Publish → `etl.sync.wait` / `etl.sync.wait` (wait queue, TTL 25 min) → DLX → `etl.sync` / `etl.sync` | DLX auto-retry failed job_execution с checkpoint resume. payload: `{ "jobExecutionId": <id> }` |
| `ETL_SYNC_COMPLETED` | ETL Pipeline | `datapulse-pricing-worker`, `datapulse-api` | `datapulse.etl.events` / fanout | Уведомление о завершении sync |
| `PRICING_RUN_EXECUTE` | Pricing | `datapulse-pricing-worker` | `pricing.run` / `pricing.run` | Запуск pricing run batch |
| `PRICE_ACTION_EXECUTE` | Execution | `datapulse-executor-worker` | `price.execution` / `price.execution` | Исполнение price action |
| `PRICE_ACTION_RETRY` | Execution | `datapulse-executor-worker` | Publish → `price.execution.wait` / `price.execution.wait` (wait queue, TTL 1 min) → DLX → `price.execution` / `price.execution` | Retry price action |
| `RECONCILIATION_CHECK` | Execution | `datapulse-executor-worker` | Publish → `price.reconciliation.wait` / `price.reconciliation.wait` (wait queue, TTL 1 min) → DLX → `price.reconciliation` / `price.reconciliation` | Deferred reconciliation check |
| `PROMO_ACTION_EXECUTE` | Promotions | `datapulse-executor-worker` | `promo.execution` / `promo.execution` | Исполнение promo action |
| `PROMO_EVALUATION_EXECUTE` | Promotions | `datapulse-pricing-worker` | `promo.evaluation` / `promo.evaluation` | Запуск promo evaluation batch |
| `ETL_PROMO_CAMPAIGN_STALE` | ETL Pipeline | `datapulse-pricing-worker` | `datapulse.etl.events` / fanout | Stale campaign detected — кампания не возвращается в PROMO_SYNC > 48h. Promotions expires pending actions |
| `REMATERIALIZATION_REQUESTED` | Analytics | `datapulse-ingest-worker` | `etl.sync` / `etl.sync` | On-demand rematerialization ClickHouse marts (triggered by ETL backfill or manual request) |

### Outbox poller

Единый outbox poller (`@Scheduled`, per worker instance) в каждом runtime:

**Phase A (single runtime):** `datapulse-api` выполняет роль единственного outbox poller с `runtime: ALL` — обрабатывает все event types. Это упрощение для single-runtime deployment.

**Phase G (multi-runtime):** при разделении на отдельные worker-процессы каждый runtime обрабатывает только свои event types:

| Runtime | `OutboxRuntime` enum | Обрабатывает event types |
|---------|---------------------|--------------------------|
| `datapulse-api` | `ALL` (Phase A) | Все event types (Phase A single-runtime). Phase G: только WebSocket push через RabbitMQ listener, outbox poller отключается |
| `datapulse-ingest-worker` | `INGEST` | `ETL_SYNC_EXECUTE`, `ETL_SYNC_RETRY`, `ETL_SYNC_COMPLETED`, `ETL_PROMO_CAMPAIGN_STALE`, `REMATERIALIZATION_REQUESTED` |
| `datapulse-pricing-worker` | `PRICING` | `PRICING_RUN_EXECUTE`, `PROMO_EVALUATION_EXECUTE`. Дополнительно получает `ETL_PROMO_CAMPAIGN_STALE` через fanout queue `etl.events.pricing-worker` (не через outbox poller) |
| `datapulse-executor-worker` | `EXECUTOR` | `PRICE_ACTION_EXECUTE`, `PRICE_ACTION_RETRY`, `RECONCILIATION_CHECK`, `PROMO_ACTION_EXECUTE` |

Poller: `SELECT ... FROM outbox_event WHERE status = 'PENDING' AND event_type IN (...) ORDER BY created_at LIMIT :batch FOR UPDATE SKIP LOCKED`. After publish → UPDATE status = 'PUBLISHED'. On failure → UPDATE status = 'FAILED', increment retry_count, set next_retry_at.

## AuditEvent — shared domain event

`AuditEvent` — record в `datapulse-platform/audit/`, публикуемый через Spring `ApplicationEventPublisher` из любого бизнес-модуля. Consumed `AuditEventListener` в `datapulse-audit-alerting` → persist в `audit_log`.

```
AuditEvent(
  workspaceId     long                  -- workspace scope
  actorType       String                -- USER, SYSTEM, SCHEDULER
  actorUserId     Long                  -- nullable (NULL для SYSTEM/SCHEDULER)
  actionType      String                -- dot-separated key: "workspace.create", "member.invite"
  entityType      String                -- target entity table: "workspace", "workspace_invitation"
  entityId        String                -- PK or composite key
  outcome         String                -- SUCCESS, DENIED, FAILED
  details         String                -- JSON context payload (nullable)
  ipAddress       String                -- client IP (nullable)
  correlationId   String                -- request correlation UUID (nullable)
)
```

Расположен в `datapulse-platform` (а не в `datapulse-audit-alerting`), чтобы любой бизнес-модуль мог публиковать audit events без dependency на audit module.

## Scope реализации по фазам

| Фаза | Цель | Модули |
|------|------|--------|
| **A — Foundation** | Tenancy, интеграция, canonical truth | [Tenancy & IAM](modules/tenancy-iam.md), [Integration](modules/integration.md), [ETL Pipeline](modules/etl-pipeline.md), [Audit & Alerting](modules/audit-alerting.md) (audit_log, system alerts) |
| **B — Trust Analytics** | Правдивая аналитика | [Analytics & P&L](modules/analytics-pnl.md) |
| **C — Pricing** | Объяснимое ценообразование | [Pricing](modules/pricing.md) |
| **D — Execution** | Контролируемое исполнение | [Execution](modules/execution.md), [Promotions](modules/promotions.md) (promo evaluation + promo execution) |
| **E — Seller Operations** | Операционный рабочий слой | [Seller Operations](modules/seller-operations.md), [Promotions](modules/promotions.md) (promo analytics in grid) |
| **B extended — Advertising Ingestion** | Рекламные данные для P&L | [Analytics & P&L](modules/analytics-pnl.md) (`dim_advertising_campaign`, `fact_advertising`) |
| **F — Simulation** | Безопасное тестирование | [Execution](modules/execution.md) (simulation section) |
| **G — Intelligence** | Расширенная аналитика, advanced advertising analytics | Расширения существующих модулей, [Promotions](modules/promotions.md) (`mart_promo_product_analysis`) |

## Связанные документы

- [Project Vision & Scope](project-vision-and-scope.md) — delivery phases, constraints, tech stack
- [Non-Functional Architecture](non-functional-architecture.md) — security, observability, resilience
