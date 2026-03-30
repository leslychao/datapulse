# Datapulse — Documentation

## How to Read

Документы организованы в три слоя:

1. **Shared архитектурные документы** — vision, общая модель данных, NFR. Читать первыми.
2. **Модульные документы** (`modules/`) — по одному на каждый модуль системы. Самодостаточные, содержат всё о модуле: назначение, модель данных, алгоритмы, design decisions.
3. **Business features** (`features/`) — описания бизнес-фич от идеи до плана реализации (TBD).

## Document Map

### Shared Architecture Documents

| Document | Contents | Audience |
|----------|----------|----------|
| [Project Vision & Scope](project-vision-and-scope.md) | Назначение системы, mandatory capabilities, delivery phases, out of scope, tech stack, constraints | All |
| [Data Model](data-model.md) | Pipeline layers, канонические сущности (обзор), star schema (обзор), таблицы по модулям, источники истины, инварианты, runtime entrypoints | All |
| [Non-Functional Architecture](non-functional-architecture.md) | Security (общее), observability stack, resilience patterns, performance, operability, notifications | Backend, DevOps, QA |

### Module Documents (`modules/`)

| Module | Phase | Contents |
|--------|-------|----------|
| [Tenancy & IAM](modules/tenancy-iam.md) | A | Multi-tenant модель, workspace isolation, пользователи, роли, permission matrix, приглашения, Keycloak/OAuth2 |
| [Integration](modules/integration.md) | A | Marketplace connections, credential management (Vault), API policy, provider capability matrix, rate limits, retry, lane isolation |
| [ETL Pipeline](modules/etl-pipeline.md) | A | Raw → Normalized → Canonical pipeline, S3 raw layer (streaming capture, cursor extraction), adapters, ETL event graph, sign conventions, join keys, canonical entities |
| [Analytics & P&L](modules/analytics-pnl.md) | B | Star schema (facts/dims/marts), P&L formula, fact_finance, inventory intelligence, returns & penalties, data quality controls, sanitation rationale |
| [Pricing](modules/pricing.md) | C | Strategies (TARGET_MARGIN, PRICE_CORRIDOR), policies, signal assembly, constraints, guards, eligibility, decisions, explanations, execution modes |
| [Execution](modules/execution.md) | D+F | Action lifecycle, outbox pattern, retry, CAS guards, reconciliation, simulation mode, shadow-state |
| [Seller Operations](modules/seller-operations.md) | E | Operational grid, saved views, working queues, price/promo journals, mismatch monitor |

### Business Features (`features/`)

| Document | Contents |
|----------|----------|
| [README](features/README.md) | Workflow: DRAFT → DESIGNING → ARCH_UPDATED → TBD_READY → IMPLEMENTING → DONE |
| [_TEMPLATE](features/_TEMPLATE.md) | Шаблон для новой бизнес-фичи |

### Operational & Policy Documents

| Document | Contents |
|----------|----------|
| [Risk Register](risk-register.md) | Architectural, integration, contractual, operational risks |
| [Runbook](runbook.md) | Operations, failure scenarios, recovery procedures |

### Provider Contracts (`provider-api-specs/`)

| Document | Contents |
|----------|----------|
| [WB Read Contracts](provider-api-specs/wb-read-contracts.md) | WB API read contracts: capabilities, field-level semantics, rate limits |
| [Ozon Read Contracts](provider-api-specs/ozon-read-contracts.md) | Ozon API read contracts: capabilities, field-level semantics |
| [Write Contracts](provider-api-specs/write-contracts.md) | Price write contracts for WB and Ozon |
| [Mapping Spec](provider-api-specs/mapping-spec.md) | Provider → Normalized → Canonical mapping |
| [Promo & Advertising Contracts](provider-api-specs/promo-advertising-contracts.md) | Promo and advertising API contracts |
| [Empirical Verification Log](provider-api-specs/samples/empirical-verification-log.md) | API verification log |

### Frontend

| Document | Contents |
|----------|----------|
| [Frontend Design Direction](frontend/frontend-design-direction.md) | Design language, UX principles, anti-goals |

## Document Hierarchy

```
Shared Architecture (how the system MUST work)
──────────────────────────────────────
project-vision-and-scope.md      ← Entry point: what and why
data-model.md                    ← Shared: entities, pipeline, invariants
non-functional-architecture.md   ← Cross-cutting: security, observability, resilience

Module Documents (self-contained per module)
──────────────────────────────────────
modules/tenancy-iam.md           ← Phase A: multi-tenancy, IAM
modules/integration.md           ← Phase A: marketplace connections, API policy
modules/etl-pipeline.md          ← Phase A: data pipeline, raw layer, adapters
modules/analytics-pnl.md         ← Phase B: star schema, P&L, inventory, returns
modules/pricing.md               ← Phase C: strategies, policies, signals, decisions
modules/execution.md             ← Phase D+F: actions, retry, reconciliation, simulation
modules/seller-operations.md     ← Phase E: grid, views, queues, journals

Business Features (idea → architecture → TBD → implementation)
──────────────────────────────────────
features/                        ← Feature specs with TBD

Operational
──────────────────────────────────────
risk-register.md                 ← Known risks and mitigations
runbook.md                       ← Operational procedures

Contracts (input dependency for adapters)
──────────────────────────────────────
provider-api-specs/              ← Detailed field-level contracts
```

## Language

Документы написаны на русском языке. Английские технические термины (API, SLA, rate limit, reconciliation, rollback и т.д.) используются там, где это общепринято.
