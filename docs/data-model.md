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
| `CanonicalPriceSnapshot` | State | [ETL Pipeline](modules/etl-pipeline.md) |
| `CanonicalStockSnapshot` | State | [ETL Pipeline](modules/etl-pipeline.md) |
| `CanonicalOrder` | Flow | [ETL Pipeline](modules/etl-pipeline.md) |
| `CanonicalSale` | Flow | [ETL Pipeline](modules/etl-pipeline.md) |
| `CanonicalReturn` | Flow | [ETL Pipeline](modules/etl-pipeline.md) |
| `CanonicalFinanceEntry` | Flow | [ETL Pipeline](modules/etl-pipeline.md) |
| `cost_profile` (SCD2) | State | [ETL Pipeline](modules/etl-pipeline.md) |

## Star schema (обзор)

| Тип | Таблицы | Модуль-владелец |
|-----|---------|-----------------|
| Dimensions | `dim_product`, `dim_warehouse`, `dim_category`, `dim_promo_campaign` | [Analytics & P&L](modules/analytics-pnl.md) |
| P&L facts | `fact_finance`, `fact_sales`, `fact_product_cost`, `fact_advertising_costs` | [Analytics & P&L](modules/analytics-pnl.md) |
| Operational facts | `fact_orders`, `fact_returns`, `fact_price_snapshot`, `fact_inventory_snapshot` | [Analytics & P&L](modules/analytics-pnl.md) |
| Marts | `mart_posting_pnl`, `mart_product_pnl`, `mart_inventory_analysis`, `mart_returns_analysis` | [Analytics & P&L](modules/analytics-pnl.md) |

## Ключевые таблицы PostgreSQL по модулям

| Модуль | Таблицы |
|--------|---------|
| [Tenancy & IAM](modules/tenancy-iam.md) | `tenant`, `workspace`, `app_user`, `workspace_member`, `workspace_invitation` |
| [Integration](modules/integration.md) | `marketplace_connection`, `secret_reference`, `marketplace_sync_state`, `integration_call_log` |
| [ETL Pipeline](modules/etl-pipeline.md) | `product_master`, `seller_sku`, `marketplace_offer`, `cost_profile`, `canonical_price_snapshot`, `canonical_stock_snapshot`, `canonical_order`, `canonical_sale`, `canonical_return`, `canonical_finance_entry`, `job_execution`, `job_item` |
| [Pricing](modules/pricing.md) | `price_policy`, `price_policy_assignment`, `price_decision`, `manual_price_lock` |
| [Execution](modules/execution.md) | `price_action`, `price_action_attempt`, `outbox_event`, `simulated_offer_state` |
| [Seller Operations](modules/seller-operations.md) | `saved_view`, `working_queue_definition`, `working_queue_assignment` |
| Audit (cross-cutting) | `alert_rule`, `alert_event`, `audit_log` |

## Источники истины

| Данные | Source of truth | Запрещено |
|--------|----------------|-----------|
| Business state (tenancy, decisions, actions) | PostgreSQL | ClickHouse, Redis, RabbitMQ, S3 |
| Canonical State | PostgreSQL | Raw layer, normalized DTO |
| Canonical Flow | PostgreSQL (write); ClickHouse (analytical reads) | Raw JSON, provider DTO |
| Входы pricing: current state | Canonical State (PostgreSQL) | Raw/normalized data |
| Входы pricing: derived signals | Analytics (ClickHouse) через signal assembler | Direct ClickHouse reads |
| Action lifecycle state | PostgreSQL | RabbitMQ, Redis |
| Retry truth | PostgreSQL | RabbitMQ TTL, in-memory |
| Historical analytics | ClickHouse | — |
| Raw evidence / replay | S3-compatible | — |

## Идентификаторы

| Поле | Описание |
|------|----------|
| `posting_id` (Datapulse) | Order-level группировка. Ozon: `posting_number`, WB: `srid` |
| `operation_id` (Datapulse) | Уникальный ID финансовой операции. Ozon: `operation_id`, WB: `rrd_id` |

## Runtime entrypoints

| Entrypoint | Phase | Ответственность |
|------------|-------|-----------------|
| `datapulse-api` | A | REST API: tenancy, connections, sync triggers, reads |
| `datapulse-ingest-worker` | A | Data pipeline: fetch → raw → normalize → canonicalize → materialize |
| `datapulse-pricing-worker` | C | Pricing pipeline: eligibility → decision → explanation |
| `datapulse-executor-worker` | D | Action execution, retries, reconciliation |

## Архитектурные инварианты

| # | Инвариант |
|---|-----------|
| 1 | Pipeline строго последовательный: Raw → Normalized → Canonical → Analytics |
| 2 | Бизнес-логика работает только с canonical и analytics |
| 3 | PostgreSQL — единственный source of truth для business state |
| 4 | ClickHouse — read-only для бизнес-логики |
| 5 | Provider DTO не протекают за границу adapter |
| 6 | Каждая каноническая запись прослеживаема до raw source |
| 7 | fact_finance материализуется напрямую из canonical_finance_entry |
| 8 | UPSERT с `IS DISTINCT FROM` — no-churn |

## Scope реализации по фазам

| Фаза | Цель | Модули |
|------|------|--------|
| **A — Foundation** | Tenancy, интеграция, canonical truth | [Tenancy & IAM](modules/tenancy-iam.md), [Integration](modules/integration.md), [ETL Pipeline](modules/etl-pipeline.md) |
| **B — Trust Analytics** | Правдивая аналитика | [Analytics & P&L](modules/analytics-pnl.md) |
| **C — Pricing** | Объяснимое ценообразование | [Pricing](modules/pricing.md) |
| **D — Execution** | Контролируемое исполнение | [Execution](modules/execution.md) |
| **E — Seller Operations** | Операционный рабочий слой | [Seller Operations](modules/seller-operations.md) |
| **F — Simulation** | Безопасное тестирование | [Execution](modules/execution.md) (simulation section) |
| **G — Intelligence** | Расширенная аналитика | Расширения существующих модулей |

## Связанные документы

- [Project Vision & Scope](project-vision-and-scope.md) — delivery phases, constraints, tech stack
- [Non-Functional Architecture](non-functional-architecture.md) — security, observability, resilience
