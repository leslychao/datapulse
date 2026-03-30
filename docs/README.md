# Datapulse — Documentation

## How to Read

Документы организованы как **нормативный комплект** для greenfield-реализации. Каждый документ отвечает на вопрос "как обязана быть устроена система", а не "что мы когда-то наблюдали в коде".

## Document Map

### Core Architecture Documents

| Document | Contents | Audience |
|---|---|---|
| [Project Vision & Scope](project-vision-and-scope.md) | Назначение системы, mandatory capabilities, delivery phases, out of scope, open design decisions | All |
| [Target Architecture](target-architecture.md) | Bounded contexts, module structure, runtime model, data flows, store responsibilities, forbidden approaches | Backend, architecture |
| [Functional Capabilities](functional-capabilities.md) | Capability groups, user flows, pipeline definitions, acceptance criteria | Product, backend, QA |
| [Non-Functional Architecture](non-functional-architecture.md) | Security, audit, observability, resilience, consistency, performance, operability | Backend, DevOps, QA |
| [Data Architecture](data-architecture.md) | Data pipeline layers, canonical entities, star schema, P&L formula, sign conventions, join keys, source-of-truth rules | Backend, data engineering |
| [Provider Capability Matrix](provider-capability-matrix.md) | Provider contracts coverage, blockers, validation gaps, rate limits, authentication, pagination patterns | Backend, integration |
| [Execution & Reconciliation](execution-and-reconciliation.md) | Action lifecycle, outbox pattern, retry semantics, reconciliation rules, CAS guards, idempotency, simulation | Backend |

### Operational & Policy Documents

| Document | Contents |
|---|---|
| [Marketplace API Policy](marketplace-api-policy.md) | Mandatory rules for marketplace adapter implementation |
| [Risk Register](risk-register.md) | Architectural, integration, contractual, operational risks |
| [Runbook](runbook.md) | Operations, failure scenarios, recovery procedures |

### Provider Contracts (folder `provider-contracts/`)

| Document | Contents |
|---|---|
| [WB Read Contracts](provider-contracts/wb-read-contracts.md) | WB API read contracts: 7 capabilities, field-level semantics, rate limits |
| [Ozon Read Contracts](provider-contracts/ozon-read-contracts.md) | Ozon API read contracts: 7 capabilities, field-level semantics, sign conventions |
| [Write Contracts](provider-contracts/write-contracts.md) | Price write contracts for WB and Ozon |
| [Mapping Spec](provider-contracts/mapping-spec.md) | Provider → Normalized → Canonical mapping: design decisions, readiness matrix |
| [Promo & Advertising Contracts](provider-contracts/promo-advertising-contracts.md) | Promo and advertising API contracts, migration notes |
| [Empirical Verification Log](provider-contracts/samples/empirical-verification-log.md) | API verification log |

### Reference Material (folder `_archive/`)

Archived documents from analysis phase. Useful as reference for validated lessons, design rationale, and anti-patterns discovered during analysis. Not normative for new implementation.

### Implementation Details (folder `implementations_for_improve/`)

Detailed implementation descriptions. Use as source of validated patterns and domain-specific solutions, not as prescriptive spec.

## Document Hierarchy

```
Normative (how the system MUST work)
──────────────────────────────────────
project-vision-and-scope.md      ← Entry point: what and why
target-architecture.md           ← How: structure, boundaries, principles
functional-capabilities.md      ← What: capability groups, user flows
non-functional-architecture.md  ← How well: quality attributes
data-architecture.md             ← Data: layers, truth, formulas
provider-capability-matrix.md   ← External: provider coverage, limits
execution-and-reconciliation.md ← Actions: lifecycle, reliability

Policy
──────────────────────────────────────
marketplace-api-policy.md        ← Rules for marketplace adapters
risk-register.md                 ← Known risks and mitigations
runbook.md                       ← Operational procedures

Contracts (input dependency for adapters)
──────────────────────────────────────
provider-contracts/              ← Detailed field-level contracts
```

## Language

Документы написаны на русском языке. Английские технические термины (API, SLA, rate limit, reconciliation, rollback и т.д.) используются там, где это общепринято.
