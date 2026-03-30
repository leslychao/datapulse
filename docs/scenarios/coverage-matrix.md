# Scenario Coverage Matrix

## Обзор

Сценарный реестр Datapulse содержит **118 module-level сценариев** и **20 e2e сценариев**, организованных в **10 сценарных семейств**.

## Структура документов

```
docs/scenarios/
├── coverage-matrix.md          ← этот документ
├── module/
│   ├── execution-scenarios.md       (18 сценариев) — MUST
│   ├── etl-pipeline-scenarios.md    (16 сценариев) — MUST
│   ├── analytics-pnl-scenarios.md   (12 сценариев) — MUST
│   ├── pricing-scenarios.md         (16 сценариев) — MUST
│   ├── integration-scenarios.md     (14 сценариев) — MUST
│   ├── tenancy-iam-scenarios.md     (10 сценариев) — SHOULD
│   ├── promotions-scenarios.md      (12 сценариев) — MUST
│   ├── seller-operations-scenarios.md (10 сценариев) — SHOULD
│   └── audit-alerting-scenarios.md  (10 сценариев) — SHOULD
└── e2e/
    ├── pricing-execution-e2e.md     (5 сценариев)  — MUST
    ├── data-pipeline-e2e.md         (5 сценариев)  — MUST
    ├── data-quality-safety-e2e.md   (4 сценария)   — MUST
    ├── promotions-e2e.md            (3 сценария)   — SHOULD
    └── operational-lifecycle-e2e.md  (3 сценария)   — SHOULD
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
| 1 | Data Ingestion & Pipeline | Integration, ETL, Analytics | 30 |
| 2 | Pricing Decision | Pricing, Analytics | 16 |
| 3 | Action Execution | Execution, Integration | 18 |
| 4 | Reconciliation & Data Quality | Execution, Analytics, Audit | 12 |
| 5 | Promotions Lifecycle | Promotions, Pricing, Execution | 12 |
| 6 | Seller Operations & UX | Seller Ops, Analytics, Pricing | 10 |
| 7 | Tenancy & Access Control | Tenancy & IAM | 10 |
| 8 | Audit & Alerting | Audit & Alerting | 10 |
| 9 | E2E: Cross-Module Flows | All | 20 |
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

## Оставшиеся известные ограничения

| Ограничение | Влияние | Статус |
|-------------|---------|--------|
| WB Promo write API — не документирован | Promotions: WB participation execution = TBD | Blocker для Phase G (WB promo) |
| Ozon FBS orders — не тестировался | ETL: FBS ingestion может иметь нюансы | Риск, покрыт в risk-register |
| Ozon rate limits — не документированы | Integration: conservative defaults, эмпирическая корректировка | Риск, митигация описана |
| Advertising ingestion — Ozon Performance partial | Analytics: ad_cost allocation для Ozon неполон | Риск для P&L accuracy |
