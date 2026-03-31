# Scenario Coverage Matrix

## Обзор

Сценарный реестр Datapulse содержит **160 module-level сценариев** и **21 e2e сценарий**, организованных в **10 сценарных семейств**.

## Структура документов

```
docs/scenarios/
├── coverage-matrix.md          ← этот документ
├── module/
│   ├── execution-scenarios.md       (25 сценариев) — MUST
│   ├── etl-pipeline-scenarios.md    (20 сценариев) — MUST
│   ├── analytics-pnl-scenarios.md   (16 сценариев) — MUST
│   ├── pricing-scenarios.md         (24 сценария)  — MUST
│   ├── integration-scenarios.md     (16 сценариев) — MUST
│   ├── tenancy-iam-scenarios.md     (17 сценариев) — SHOULD
│   ├── promotions-scenarios.md      (16 сценариев) — MUST
│   ├── seller-operations-scenarios.md (12 сценариев) — SHOULD
│   └── audit-alerting-scenarios.md  (14 сценариев) — SHOULD
└── e2e/
    ├── pricing-execution-e2e.md     (5 сценариев)  — MUST
    ├── data-pipeline-e2e.md         (5 сценариев)  — MUST
    ├── data-quality-safety-e2e.md   (4 сценария)   — MUST
    ├── promotions-e2e.md            (3 сценария)   — SHOULD
    └── operational-lifecycle-e2e.md  (4 сценария)   — SHOULD
```

## Coverage Strategy

### Принцип разделения сценариев

Считать отдельным сценарием случай, где есть хотя бы одно:
- Другой инициатор (user / scheduler / event / provider)
- Другой модуль-владелец шага
- Другой контракт (API, event, table)
- Другая ветка state machine
- Другой failure/recovery path
- Другая persistence / source-of-truth semantics
- Другой business outcome
- Другая reconciliation semantics
- Другой audit requirement

### 10 сценарных семейств

| # | Семейство | Модули | Кол-во сценариев |
|---|-----------|--------|------------------|
| 1 | Data Ingestion & Pipeline | Integration, ETL, Analytics | 38 |
| 2 | Pricing Decision | Pricing, Analytics | 24 |
| 3 | Action Execution | Execution, Integration | 24 |
| 4 | Reconciliation & Data Quality | Execution, Analytics, Audit | 17 |
| 5 | Promotions Lifecycle | Promotions, Pricing, Execution | 15 |
| 6 | Seller Operations & UX | Seller Ops, Analytics, Pricing | 12 |
| 7 | Tenancy & Access Control | Tenancy & IAM | 17 |
| 8 | Audit & Alerting | Audit & Alerting | 13 |
| 9 | E2E: Cross-Module Flows | All | 21 |
| 10 | Infrastructure Resilience | Cross-cutting | (covered within module scenarios) |

## Coverage Matrix — Module × Scenario Family

| Module | Ingestion | Pricing | Execution | Reconciliation | Promos | Seller Ops | IAM | Audit | Infrastructure |
|--------|:---------:|:-------:|:---------:|:--------------:|:------:|:----------:|:---:|:-----:|:--------------:|
| Integration | ●●● | — | ●● | — | ● | — | — | ● | ●● |
| ETL Pipeline | ●●● | — | — | ●● | ● | — | — | ● | ●● |
| Analytics & P&L | ●● | ●● | — | ●●● | — | ●● | — | ●● | ● |
| Pricing | — | ●●● | — | — | ●● | — | — | ● | ● |
| Execution | — | — | ●●● | ●●● | — | — | — | ●● | ●● |
| Promotions | — | ●● | ●● | ● | ●●● | — | — | ● | — |
| Seller Ops | — | ● | ● | ●● | ● | ●●● | — | — | ● |
| Tenancy & IAM | — | — | — | — | — | — | ●●● | ●● | — |
| Audit & Alerting | ● | ● | ●● | ●●● | ● | — | ●● | ●●● | ● |

Обозначения: ●●● = основной владелец, ●● = значительное покрытие, ● = минимальное покрытие, — = не затрагивается.

## Приоритизация

### MUST — максимальный архитектурный риск

| Документ | Обоснование |
|----------|-------------|
| execution-scenarios.md | 11 состояний × 34 перехода, CAS guards, reconciliation, simulation — самая сложная state machine |
| etl-pipeline-scenarios.md | 4-stage pipeline, idempotency, partial failure, late data — data integrity зависит от корректности |
| analytics-pnl-scenarios.md | P&L = бизнес-критический output. 13-компонентная формула, allocation, reconciliation residual |
| pricing-scenarios.md | 8-stage pipeline, multi-signal decisions, guard conflicts, stale data — pricing errors = direct financial impact |
| integration-scenarios.md | External dependency boundary. Rate limits, auth, Vault, circuit breaker — failure gateway для всей системы |
| pricing-execution-e2e.md | Сквозной flow от decision до confirmed price change — главный бизнес-поток |
| data-pipeline-e2e.md | Raw → Canonical → Analytics → Pricing signal — data freshness chain |
| data-quality-safety-e2e.md | Stale data → blocked automation, mismatch → alert — safety net |

### SHOULD — средний архитектурный риск

| Документ | Обоснование |
|----------|-------------|
| promotions-scenarios.md | Promo lifecycle, margin checks, coordination с Pricing |
| tenancy-iam-scenarios.md | Multi-tenant isolation, RBAC — security boundary |
| seller-operations-scenarios.md | Read model consistency, two-store queries |
| audit-alerting-scenarios.md | Alert lifecycle, automation blocker |
| promotions-e2e.md | Promo discovery → evaluation → execution flow |
| operational-lifecycle-e2e.md | User daily workflow через все модули |

## Покрытие gap-ов (что было закрыто)

| Gap | Где закрыто | Статус |
|-----|-------------|--------|
| Disaster Recovery не описан | `non-functional-architecture.md` §Disaster Recovery Plan | ✅ Closed |
| Schema evolution / migration rollback | `non-functional-architecture.md` §Schema Evolution и миграция | ✅ Closed |
| Circuit breaker для transient degradation | `integration.md` §Circuit breaker | ✅ Closed |
| Vault unavailability fallback | `integration.md` §Vault unavailability | ✅ Closed |
| Bulk operations behavior | `etl-pipeline.md` §Bulk operations | ✅ Closed |

### Ревизия сценариев (2026-03-31)

| Категория | Что исправлено | Scope |
|-----------|---------------|-------|
| CRITICAL: EXE-02 REJECTED state | `REJECTED` → `CANCELLED` с `cancel_reason` | execution-scenarios |
| CRITICAL: EXE-18 DLX retry | Rewrite → DB-first outbox retry, poison pill handling | execution-scenarios |
| CRITICAL: EXE-10 deferred reconciliation | `deferred_action` table → `outbox_event (RECONCILIATION_CHECK)` с TTL | execution-scenarios |
| CRITICAL: ETL-08 domain events | 5 domain-specific events → единый `ETL_SYNC_COMPLETED` с `completed_domains[]` | etl, pricing, promotions, e2e |
| CRITICAL: ANA-01 P&L formula | Некорректные компоненты → 13-component formula | analytics-pnl-scenarios |
| CRITICAL: INT-11 credential caching | «No caching» → Caffeine cache, TTL 1h | integration-scenarios |
| IMPORTANT: Event naming | `PRICES_SYNC_COMPLETED`, `PROMO_SYNC_COMPLETED` и др. → `ETL_SYNC_COMPLETED` | all e2e, pricing, promotions |
| IMPORTANT: Table naming | `canonical_price_snapshot` → `canonical_price_current`, `dim_sku` → `dim_product` | analytics, seller-ops, e2e |
| IMPORTANT: PRC-06 guard | Несуществующий `active_action_guard` → Action Scheduling conflict | pricing-scenarios |
| IMPORTANT: PRC-10 lock model | Флаг на `canonical_offer` → `manual_price_lock` table | pricing-scenarios |
| IMPORTANT: IAM-01 API path | `POST /api/workspaces` → `POST /api/tenants/{tenantId}/workspaces` | tenancy-iam-scenarios |
| IMPORTANT: PRO-06 lifecycle | Missing APPROVED state | promotions-scenarios |
| IMPORTANT: AUD-04 interval | 30 min → 5 min | audit-alerting-scenarios |
| IMPORTANT: AUD-05 mechanism | Outbox → Spring ApplicationEvent | audit-alerting-scenarios |
| ADDED: 19 new scenarios | EXE-19..22, ETL-17..18, ANA-13..14, PRC-17..21, INT-15, IAM-11..12, PRO-13, AUD-11..12 | across 9 module files |

### Ревизия сценариев (2026-03-31, pass 2)

| Категория | Что исправлено | Scope |
|-----------|---------------|-------|
| CRITICAL: E2E-PE-02 REJECTED state | `REJECTED` → `CANCELLED` с `cancel_reason` (пропущено в pass 1) | pricing-execution-e2e |
| CRITICAL: ANA-01 event name | `FINANCE_SYNC_COMPLETED` → `ETL_SYNC_COMPLETED (FINANCE ∈ completed_domains)` | analytics-pnl-scenarios |
| CRITICAL: ANA-02 event name | `AD_SYNC_COMPLETED` → `ETL_SYNC_COMPLETED (ADVERTISING ∈ completed_domains)` | analytics-pnl-scenarios |
| CRITICAL: ANA-09 event name | `STOCKS_SYNC_COMPLETED` → `ETL_SYNC_COMPLETED (STOCKS ∈ completed_domains)` | analytics-pnl-scenarios |
| CRITICAL: ANA-10 event name | `FINANCE_SYNC_COMPLETED` → `ETL_SYNC_COMPLETED (FINANCE ∈ completed_domains)` | analytics-pnl-scenarios |
| IMPORTANT: ANA-06 interval | 30 min → 5 min (согласование с AUD-04) | analytics-pnl-scenarios |
| IMPORTANT: PRO-04 table name | `canonical_stock_snapshot` → `canonical_stock_current` | promotions-scenarios |
| IMPORTANT: RabbitMQ exchange names | `action.execution` / `action.reconciliation` → `price.execution` / `price.reconciliation` (согласование с data-model.md) | execution.md |
| IMPORTANT: Outbox status enum | `ERROR` → `FAILED` (согласование с data-model.md DDL) | execution.md |
| IMPORTANT: RECONCILIATION_CHECK event | Добавлен в event type registry data-model.md | data-model.md |
| ADDED: 2 new scenarios | IAM-13 (workspace suspension/reactivation), IAM-14 (user deactivation/reactivation) | tenancy-iam-scenarios |

### Ревизия сценариев (2026-03-31, pass 3 — coverage completion)

| Категория | Что добавлено | Scope |
|-----------|--------------|-------|
| ADDED: EXE-23 | Deferred supersede — scheduled job consumes deferred_action | execution-scenarios |
| ADDED: EXE-24 | WB-specific reconciliation (poll-based upload details) | execution-scenarios |
| ADDED: EXE-25 | Ozon partial rejection (HTTP 200 + errors[]) | execution-scenarios |
| ADDED: ETL-19 | Concurrent sync guard (duplicate job prevention via CAS) | etl-pipeline-scenarios |
| ADDED: ETL-20 | S3 unavailability mid-sync (partial failure) | etl-pipeline-scenarios |
| ADDED: ANA-15 | mart_product_pnl aggregation (posting → product roll-up) | analytics-pnl-scenarios |
| ADDED: ANA-16 | Advertising ingestion first connection (Phase B activation) | analytics-pnl-scenarios |
| ADDED: PRC-22 | Commission source cascading fallback (4-step cascade) | pricing-scenarios |
| ADDED: PRC-23 | Policy assignment conflict resolution (specificity + priority) | pricing-scenarios |
| ADDED: PRC-24 | Price no-change (target = current → SKIP) | pricing-scenarios |
| ADDED: INT-16 | Connection re-enable (DISABLED → ACTIVE) | integration-scenarios |
| ADDED: IAM-15 | First user auto-provision (Keycloak → app_user) | tenancy-iam-scenarios |
| ADDED: IAM-16 | Invitation expiration (PENDING → EXPIRED, scheduled) | tenancy-iam-scenarios |
| ADDED: IAM-17 | Invitation cancellation by admin | tenancy-iam-scenarios |
| ADDED: PRO-14 | MARGINAL evaluation → safety downgrade to PENDING_APPROVAL | promotions-scenarios |
| ADDED: PRO-15 | INSUFFICIENT_DATA evaluation (missing signals) | promotions-scenarios |
| ADDED: PRO-16 | Promo reconciliation via PROMO_SYNC | promotions-scenarios |
| ADDED: SEL-11 | Working queue auto-population (scheduled criteria evaluation) | seller-operations-scenarios |
| ADDED: SEL-12 | Working queue auto-resolution (condition-driven cleanup) | seller-operations-scenarios |
| ADDED: AUD-13 | MISSING_SYNC checker (absence detection) | audit-alerting-scenarios |
| ADDED: AUD-14 | Alert rule CRUD + default seeding on workspace creation | audit-alerting-scenarios |
| ADDED: E2E-OL-04 | Multi-marketplace partial failure — lane isolation | operational-lifecycle-e2e |

### Ревизия сценариев (2026-03-31, pass 4 — consistency verification)

| Категория | Что исправлено | Scope |
|-----------|---------------|-------|
| CRITICAL: Outbox status enum | `SENT` → `PUBLISHED`, `ERROR` → `FAILED` в outbox pattern (рассогласование с data-model.md DDL) | execution.md §Outbox pattern |
| CRITICAL: EXE-17 outbox status | `UPDATE status = SENT` → `UPDATE status = PUBLISHED` | execution-scenarios |
| CRITICAL: EXE-18 outbox status | `outbox_event.status → ERROR` → `outbox_event.status → FAILED` | execution-scenarios |
| IMPORTANT: ANA-07 trigger | `Scheduled checker (daily)` → `Event-driven, после каждой ClickHouse materialization` (согласование с AUD-11) | analytics-pnl-scenarios |
| STRUCTURAL: PRC-22..24 placement | Сценарии перемещены перед индексом (были после — нарушение структуры документа) | pricing-scenarios |

### Верификация (pass 4 — что проверено и подтверждено корректным)

| Проверка | Результат |
|----------|-----------|
| Подсчёт сценариев: 160 module + 21 e2e | ✅ Корректно (25+20+16+24+16+17+16+12+14 = 160; 5+5+4+3+4 = 21) |
| Event naming: ETL_SYNC_COMPLETED + completed_domains[] | ✅ Все 14 файлов используют единый event |
| Table naming: canonical_price_current, canonical_stock_current, dim_product | ✅ Консистентно во всех сценариях |
| State machine: 11 состояний, все transitions | ✅ Покрыты сценариями EXE-01..25, согласованы с execution.md |
| CANCELLED (не REJECTED) при reject | ✅ EXE-02, E2E-PE-02 корректны |
| Outbox status enum: PENDING / PUBLISHED / FAILED | ✅ После fix — согласовано с data-model.md DDL |
| RabbitMQ exchanges: price.execution, price.reconciliation | ✅ Согласовано с data-model.md event type registry |
| RECONCILIATION_CHECK в event type registry | ✅ Присутствует в data-model.md |
| Checker intervals: stale data 5 min (ANA-06 = AUD-04) | ✅ Согласовано |
| Spike detection trigger: event-driven (ANA-07 = AUD-11) | ✅ После fix — согласовано |
| E2E → module кросс-ссылки | ✅ Все e2e сценарии ссылаются на корректные module flows |
| Promo evaluation outcomes: PARTICIPATE / DECLINE / PENDING_REVIEW / MARGINAL / INSUFFICIENT_DATA | ✅ Exhaustive coverage |
| Domain sync ordering: CATALOG → PRICES/STOCKS → FINANCE → PROMO | ✅ ETL-14 = E2E-DP-05 |

## Оставшиеся известные ограничения

| Ограничение | Влияние | Статус |
|-------------|---------|--------|
| WB Promo write API — не документирован | Promotions: WB participation execution = TBD | Blocker для Phase G (WB promo) |
| Ozon FBS orders — не тестировался | ETL: FBS ingestion может иметь нюансы | Риск, покрыт в risk-register |
| Ozon rate limits — не документированы | Integration: conservative defaults, эмпирическая корректировка | Риск, митигация описана |
| Advertising ingestion — Ozon Performance partial | Analytics: ad_cost allocation для Ozon неполон | Риск для P&L accuracy |
