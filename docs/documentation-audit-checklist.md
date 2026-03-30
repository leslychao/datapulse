# Datapulse — Финальный аудит документации

**Дата:** 2026-03-30
**Scope:** все 22 документа в `docs/`
**Цель:** выявление взаимных противоречий между документами, оценка готовности к реализации

> **Статус: ✅ ВСЕ ПРОТИВОРЕЧИЯ ИСПРАВЛЕНЫ (2026-03-30)**

---

## 1. Выявленные противоречия

### C-1: Роли — NFR vs Data Model (MEDIUM)

| Документ | Что написано |
|----------|-------------|
| `non-functional-architecture.md` §NFR-1 | 5 ролей: **viewer, analyst, operator, pricing manager, admin** |
| `data-architecture.md` §Tenancy | Enum `workspace_member.role`: **OWNER, ADMIN, OPERATOR, ANALYST, VIEWER** |

**Проблема:** NFR описывает роль `pricing manager` с явными permissions (policy config, approval, auto-execution), но в модели данных эта роль отсутствует — вместо неё есть `OWNER`. Матрица разрешений в NFR не содержит строки `OWNER`.

**Решение:** ~~Привести в соответствие.~~ **✅ ИСПРАВЛЕНО:** `PRICING_MANAGER` добавлен в enum `workspace_member.role` в `data-architecture.md`; NFR обновлён — список ролей приведён к enum-формату с `OWNER`.

---

### C-2: WB Returns — Capability Matrix vs Contracts (LOW)

| Документ | Статус |
|----------|--------|
| `provider-capability-matrix.md` | **BLOCKED** — "Dedicated endpoint возвращает 400" |
| `wb-read-contracts.md` §6 | **READY** — "Previously BLOCKED — resolved 2026-03-30" |
| `mapping-spec.md` | **READY** — "unblocked — date-only format was root cause" |

**Проблема:** Capability matrix не обновлена после разблокировки WB Returns.

**Решение:** ~~Обновить capability matrix.~~ **✅ ИСПРАВЛЕНО:** `provider-capability-matrix.md` обновлён — WB Returns → READY; blocker B-1 помечен RESOLVED.

---

### C-3: Ozon Catalog — Capability Matrix vs Contracts (LOW)

| Документ | Статус |
|----------|--------|
| `provider-capability-matrix.md` | **READY** |
| `ozon-read-contracts.md` Summary | **PARTIAL** — "`updated_at` absent" |

**Проблема:** Capability matrix говорит READY, но ozon-read-contracts оценивает как PARTIAL из-за отсутствия `updated_at` и необходимости дополнительного вызова для brand.

**Решение:** ~~Уточнить определение READY.~~ **✅ ИСПРАВЛЕНО:** `provider-capability-matrix.md` обновлён — Ozon Catalog → PARTIAL с пояснением об отсутствии `updated_at`.

---

### C-4: WB Price Write — Capability Matrix vs Write Contracts (HIGH)

| Документ | Статус |
|----------|--------|
| `provider-capability-matrix.md` | **READY** — "Discounts & Prices API" |
| `write-contracts.md` §4 F-1, F-2 | **BROKEN** — "DNS failure, token 401" |

**Проблема:** Capability matrix показывает WB Price Write как READY, но write-contracts документирует критический сбой: DNS хоста сменился, и production-токен возвращает 401.

**Решение:** ~~Обновить capability matrix.~~ **✅ ИСПРАВЛЕНО:** `provider-capability-matrix.md` обновлён — WB Price Write → BLOCKED; добавлен blocker B-4.

---

### C-5: Ozon Storage → fact_finance measure mapping (MEDIUM)

| Документ | Маппинг Ozon `OperationMarketplaceServiceStorage` |
|----------|--------------------------------------------------|
| `data-architecture.md` §fact_finance measures | → `storage_cost_amount` |
| `mapping-spec.md` §7 Ozon services classification | → `other_marketplace_charges_amount` |

**Проблема:** data-architecture говорит, что Ozon storage маппится в `storage_cost_amount`, а mapping-spec маппит его в `other_marketplace_charges_amount`.

**Решение:** ~~Обновить mapping-spec.~~ **✅ ИСПРАВЛЕНО:** `mapping-spec.md` — Ozon `OperationMarketplaceServiceStorage` → `storage_cost_amount`.

---

### C-6: Risk Register — неполная сводная таблица (LOW)

| Секция | Содержание |
|--------|-----------|
| Тело документа | Риски R-01 — R-15 (15 рисков) |
| Сводная таблица | Только R-01 — R-12 (12 рисков) |

**Проблема:** Сводная таблица `risk-register.md` не содержит R-13 (P&L без рекламы), R-14 (WB Incomes deprecation), R-15 (Standalone allocation accuracy).

**Решение:** ~~Дополнить сводную таблицу.~~ **✅ ИСПРАВЛЕНО:** R-13, R-14, R-15 добавлены в сводную таблицу `risk-register.md`.

---

### C-7: P&L формула в functional-capabilities (LOW)

| Документ | Формула |
|----------|---------|
| `functional-capabilities.md` §2 | "revenue − **скидки** − комиссии − логистика − штрафы − реклама − COGS − возвраты + компенсации" |
| `data-architecture.md` §P&L | 13 компонентов; `seller_discount_amount` удалён; скидки учтены в `revenue_amount` |

**Проблема:** functional-capabilities упоминает "скидки" как отдельный компонент P&L, но по результатам санации отдельный measure для скидок удалён — скидки уже включены в `revenue_amount`.

**Решение:** ~~Обновить формулу.~~ **✅ ИСПРАВЛЕНО:** формула P&L в `functional-capabilities.md` обновлена — убраны «скидки», добавлены хранение, прочие удержания, reconciliation_residual.

---

### C-8: Pricing pipeline — stale cross-reference (LOW)

| Документ | Что написано |
|----------|-------------|
| `pricing-architecture-analysis.md` §1 | "Pipeline шаги \| functional-capabilities.md §5 \| Eligibility → Signals → **Constraints → Guards → Strategy** → Decision" |
| `functional-capabilities.md` §5 | Уже содержит исправленный порядок: "Eligibility → Signal Assembly → **Strategy Evaluation → Constraint Resolution → Guard Pipeline** → Decision" |

**Проблема:** pricing-architecture ссылается на старый порядок pipeline из functional-capabilities (Constraints до Strategy), хотя functional-capabilities уже обновлён. Также §2 pricing-architecture описывает "проблему" с порядком, которая уже решена.

**Решение:** ~~Обновить §1 и §2.~~ **✅ ИСПРАВЛЕНО:** §2 `pricing-architecture-analysis.md` — stale «проблема» заменена на подтверждение согласованности.

---

### C-9: `pricing_strategy_config` — несуществующая таблица (LOW)

| Документ | Ссылка |
|----------|--------|
| `pricing-architecture-analysis.md` §1 | "Таблицы PostgreSQL \| data-architecture.md \| price_policy, **pricing_strategy_config**, price_decision..." |
| `data-architecture.md` §Key tables | `price_policy, price_policy_assignment, price_decision, price_action, manual_price_lock` |
| `pricing-architecture-analysis.md` §4 | `price_policy` содержит `strategy_type` + `strategy_params` (JSONB) |

**Проблема:** pricing-architecture §1 ссылается на таблицу `pricing_strategy_config`, которая не существует ни в data-architecture, ни в самом pricing-architecture. Конфигурация стратегии встроена в `price_policy`.

**Решение:** ~~Убрать ссылку.~~ **✅ ИСПРАВЛЕНО:** `pricing_strategy_config` заменён на `price_policy (strategy config как JSONB)` в §1 `pricing-architecture-analysis.md`.

---

### C-10: Spring Integration DSL — stale tech reference (LOW)

| Документ | Что написано |
|----------|-------------|
| `project-vision-and-scope.md` | "RabbitMQ + **Spring Integration DSL**" |
| `execution-and-reconciliation.md` | Стандартные Spring AMQP patterns (exchanges, queues, consumer config) |
| `runbook.md` | Стандартная RabbitMQ topology без Spring Integration |

**Проблема:** project-vision упоминает Spring Integration DSL, но ни один архитектурный документ не использует паттерны Spring Integration. Вся messaging-архитектура описана через стандартный Spring AMQP.

**Решение:** ~~Заменить в project-vision.~~ **✅ ИСПРАВЛЕНО:** `project-vision-and-scope.md` — "Spring Integration DSL" → "Spring AMQP".

---

## 2. Непротиворечивые, но стоит отметить

### N-1: Reconciliation — архитектура vs реализация

`execution-and-reconciliation.md` описывает полный reconciliation flow (re-read verification), но `write-contracts.md` §4 F-4 фиксирует: "Reconciliation Logic NOT IMPLEMENTED". Это **не противоречие** (архитектура vs текущий статус), но важный implementation gap для Phase D.

### N-2: fact_supply в materialization table

`data-architecture.md` содержит `SUPPLY_FACT → fact_supply` в таблице материализации, но одновременно отмечает "НЕ реализуется в Phase A/B". Граф зависимостей ETL корректно НЕ содержит SUPPLY_FACT. Не противоречие, но может вводить в заблуждение при чтении таблицы.

### N-3: WB Advertising — двойной blocker

`provider-capability-matrix.md` фиксирует B-3 (v2→v3 migration), `promo-advertising-contracts.md` детально описывает ту же проблему (F-1, F-2). Информация согласована, но дублируется в разных местах.

---

## 3. Области полной согласованности

Следующие критические аспекты **полностью согласованы** между документами:

| Аспект | Документы | Статус |
|--------|-----------|--------|
| Четырёхслойный pipeline (Raw → Normalized → Canonical → Analytics) | target-architecture, data-architecture, s3-raw-layer, pnl-sanitation | ✅ Consistent |
| PostgreSQL как единственный authoritative store | project-vision, target-architecture, data-architecture, non-functional | ✅ Consistent |
| SUCCEEDED = confirmed only | target-architecture, execution-and-reconciliation, functional-capabilities, risk-register | ✅ Consistent |
| DB-first + Transactional Outbox | target-architecture, execution-and-reconciliation, non-functional, runbook | ✅ Consistent |
| Sign conventions (WB: positive absolute; Ozon: signed) | data-architecture, mapping-spec, wb-read-contracts, ozon-read-contracts | ✅ Consistent |
| Canonical State vs Canonical Flow разделение | data-architecture, pnl-sanitation, target-architecture | ✅ Consistent |
| Каталожная иерархия product_master → seller_sku → marketplace_offer | data-architecture, pnl-sanitation | ✅ Consistent |
| fact_finance — прямая материализация без spine/component facts | data-architecture, pnl-sanitation, pnl-sanitation-resolved | ✅ Consistent |
| mart_posting_pnl (переименование из mart_order_pnl) | data-architecture, pnl-sanitation, pnl-sanitation-resolved | ✅ Consistent |
| Pricing ownership split (Pricing vs Execution) | target-architecture, functional-capabilities, execution-and-reconciliation | ✅ Consistent |
| Ozon acquiring DD-15 join strategy | data-architecture, mapping-spec, ozon-read-contracts | ✅ Consistent |
| WB revenue = `retail_price_withdisc_rub` (DD-13) | data-architecture, mapping-spec | ✅ Consistent |
| Ozon revenue = `accruals_for_sale` (DD-11) | data-architecture, mapping-spec, ozon-read-contracts | ✅ Consistent |
| reconciliation_residual включает SPP/marketing subsidy | data-architecture, pnl-sanitation-resolved (Q7) | ✅ Consistent |
| Standalone operations — smart allocation (Q4) | data-architecture, pnl-sanitation-resolved | ✅ Consistent |
| Bounded contexts и ownership rules | target-architecture, functional-capabilities | ✅ Consistent |
| WebSocket — STOMP, notification-only, reconnection | target-architecture, non-functional, runbook | ✅ Consistent |
| Store responsibilities (PG / CH / Redis / S3 / RabbitMQ) | target-architecture, data-architecture, non-functional | ✅ Consistent |
| Модульная структура (common / domain / application / adapters / bootstrap) | target-architecture | ✅ Self-consistent |
| Delivery phases A–G | project-vision, pricing-architecture, data-architecture | ✅ Consistent |

---

## 4. Чеклист готовности документации

### 4.1 Core Architecture — готовность к реализации

| # | Документ | Статус | Блокеры | Действие |
|---|----------|--------|---------|----------|
| 1 | `project-vision-and-scope.md` | ✅ Ready | ~~C-10~~ ✅ | Исправлено |
| 2 | `target-architecture.md` | ✅ Ready | — | — |
| 3 | `functional-capabilities.md` | ✅ Ready | ~~C-7~~ ✅ | Исправлено |
| 4 | `non-functional-architecture.md` | ✅ Ready | ~~C-1~~ ✅ | Исправлено |
| 5 | `data-architecture.md` | ✅ Ready | ~~C-1~~ ✅ | Исправлено (PRICING_MANAGER добавлен) |
| 6 | `provider-capability-matrix.md` | ✅ Ready | ~~C-2, C-3, C-4~~ ✅ | Исправлено |
| 7 | `execution-and-reconciliation.md` | ✅ Ready | — | — |

### 4.2 Pricing — готовность Phase C

| # | Документ | Статус | Блокеры | Действие |
|---|----------|--------|---------|----------|
| 8 | `pricing-architecture-analysis.md` | ✅ Ready | ~~C-8, C-9~~ ✅ | Исправлено |

### 4.3 P&L Sanitation

| # | Документ | Статус | Блокеры | Действие |
|---|----------|--------|---------|----------|
| 9 | `pnl-architecture-sanitation.md` | ✅ Ready | — | — |
| 10 | `pnl-sanitation-resolved-decisions.md` | ✅ Ready | — | — |

### 4.4 Provider Contracts

| # | Документ | Статус | Блокеры | Действие |
|---|----------|--------|---------|----------|
| 11 | `wb-read-contracts.md` | ✅ Ready | — | 6/7 capabilities verified |
| 12 | `ozon-read-contracts.md` | ✅ Ready | — | 7/7 capabilities documented |
| 13 | `write-contracts.md` | ✅ Ready | — | Documents WB BROKEN status correctly |
| 14 | `mapping-spec.md` | ✅ Ready | ~~C-5~~ ✅ | Исправлено |
| 15 | `promo-advertising-contracts.md` | ✅ Ready | — | Documents WB ads migration correctly |
| 16 | `empirical-verification-log.md` | ✅ Ready | — | Reference data |

### 4.5 Policy & Operations

| # | Документ | Статус | Блокеры | Действие |
|---|----------|--------|---------|----------|
| 17 | `marketplace-api-policy.md` | ✅ Ready | — | — |
| 18 | `risk-register.md` | ✅ Ready | ~~C-6~~ ✅ | Исправлено |
| 19 | `runbook.md` | ✅ Ready | — | — |

### 4.6 Infrastructure & Frontend

| # | Документ | Статус | Блокеры | Действие |
|---|----------|--------|---------|----------|
| 20 | `s3-raw-layer-architecture.md` | ✅ Ready | — | — |
| 21 | `frontend-design-direction.md` | ✅ Ready | — | Direction doc, not detailed spec |
| 22 | `README.md` | ✅ Ready | — | Корректная карта документов |

---

## 5. Сводка

### Статистика

| Категория | Количество |
|-----------|-----------|
| Документов проверено | 22 |
| Противоречий выявлено | 10 |
| — HIGH severity | 1 (C-4: WB Price Write status) |
| — MEDIUM severity | 2 (C-1: roles, C-5: Ozon storage mapping) |
| — LOW severity | 7 |
| Документов готовых (все исправлены) | 22 |
| Документов требующих minor fix | 0 |
| Документов с блокирующими проблемами | 0 |

### Приоритет исправлений

| Приоритет | ID | Действие | Статус |
|-----------|-----|----------|--------|
| 1 (HIGH) | C-4 | `provider-capability-matrix.md`: WB Price Write → BLOCKED | ✅ Done |
| 2 (MEDIUM) | C-1 | Согласовать roles между NFR и data model | ✅ Done |
| 3 (MEDIUM) | C-5 | `mapping-spec.md`: Ozon storage → `storage_cost_amount` | ✅ Done |
| 4 (LOW) | C-2 | `provider-capability-matrix.md`: WB Returns → READY | ✅ Done |
| 5 (LOW) | C-6 | `risk-register.md`: дополнить сводную таблицу | ✅ Done |
| 6 (LOW) | C-7 | `functional-capabilities.md`: P&L formula | ✅ Done |
| 7 (LOW) | C-3 | Ozon Catalog readiness alignment | ✅ Done |
| 8 (LOW) | C-8 | `pricing-architecture-analysis.md`: stale reference | ✅ Done |
| 9 (LOW) | C-9 | `pricing-architecture-analysis.md`: remove pricing_strategy_config | ✅ Done |
| 10 (LOW) | C-10 | `project-vision-and-scope.md`: Spring Integration DSL | ✅ Done |

### Вердикт

**Документация полностью согласована.** Все 10 выявленных противоречий исправлены. Основные архитектурные решения (pipeline, store responsibilities, P&L модель, pricing, execution) полностью согласованы между всеми 22 документами.

**Готовность к реализации Phase A:** документация готова, блокеров нет.

---

## Связанные документы

- Все документы в `docs/` (см. [README](README.md))
