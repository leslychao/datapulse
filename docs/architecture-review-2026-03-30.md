# Datapulse — Архитектурный анализ

**Дата:** 2026-03-30
**Роль:** Senior Staff / Principal Architect
**Scope:** полная документация (22 документа, ~5000 строк)

---

## A. Confirmed problem the architecture is trying to solve

1. **Консолидация данных из разнородных API маркетплейсов** (WB, Ozon) с несовместимыми контрактами, sign conventions, pagination, rate limits, timestamp formats — в единую canonical model.

2. **Правдивый P&L по SKU** — reconcilable с фактическими выплатами маркетплейса, с drill-down до raw evidence. Ключевая сложность: маркетплейсы не предоставляют готовый P&L; его нужно собирать из десятков полей с разной семантикой.

3. **Объяснимое ценообразование** — не чёрный ящик, а pipeline с audit trail: почему именно такая цена, какие сигналы, какие ограничения, почему заблокировано.

4. **Контролируемое исполнение** — price change = внешнее действие через API маркетплейса с неопределённым результатом. Нужен lifecycle с подтверждением, retry, reconciliation.

5. **Операционный инструмент продавца** — не аналитический дашборд, а рабочий стол для ежедневных операций: фильтрация, очереди, журналы, действия.

6. **Мультитенантность** — несколько продавцов, несколько кабинетов маркетплейсов, изоляция данных.

---

## B. Architectural verdict on direction

**Вектор решения правильный.**

Обоснование:

1. **Природа проблемы = integration + analytical + workflow.** Выбрана архитектура, которая соответствует: ETL pipeline для integration, двойной store (PostgreSQL + ClickHouse) для transactional+analytical, outbox + state machine для workflow. Это правильная комбинация для данной задачи.

2. **Ключевое правильное решение — canonical model.** Вместо попытки работать напрямую с provider DTO (что было бы быстрее в реализации) выбрана четырёхступенчатая нормализация: Raw → Normalized → Canonical → Analytics. Это правильно, потому что бизнес-логика (P&L, pricing) должна работать с единым языком, а не с WB-specific и Ozon-specific полями. Стоимость абстракции оправдана количеством downstream consumers.

3. **DB-first + outbox вместо event-driven.** Для системы, где потеря state = финансовые потери (ложный SUCCEEDED у price action), это единственно правильный подход. Event sourcing или broker-first были бы архитектурно неправильным выбором.

4. **Фазированная поставка с жёсткими prerequisites.** Phase A (canonical truth) → Phase B (analytics) → Phase C (pricing) → Phase D (execution). Это правильный порядок: нельзя автоматизировать ценообразование без правдивого P&L, нельзя считать P&L без canonical data.

5. **Уровень сложности соответствует задаче.** Решение не over-engineered (нет event sourcing, CQRS, saga orchestrator, отдельных микросервисов) и не under-engineered (есть outbox, CAS, reconciliation, stale data guards). Выбранные механизмы — прямой ответ на конкретные проблемы, а не generic best practices.

---

## C. What is structurally correct

### C-1. Каноническая модель как anti-corruption boundary

Raw → Normalized → Canonical — строго последовательный pipeline, где provider shapes не протекают дальше adapter boundary. Это правильно: WB `ppvz_sales_commission` (positive, debit by field name) и Ozon `sale_commission` (negative = cost) нормализуются в единый `marketplace_commission_amount`. Бизнес-логика не знает о провайдерах. Добавление нового маркетплейса = новый adapter + mapping, без изменения pricing/analytics.

### C-2. Разделение Canonical State / Canonical Flow

State-сущности (цены, остатки, каталог) живут в PostgreSQL и обновляются через UPSERT. Flow-сущности (заказы, продажи, финансы) пишутся в PostgreSQL, аналитические агрегаты — в ClickHouse. Это правильная модель: state нужен для decision-grade reads (pricing), flow нужен для aggregation (P&L). Разные access patterns → разные stores.

### C-3. fact_finance как consolidated financial fact

Удаление промежуточных fact_commission, fact_logistics_costs и т.д. в пользу одной таблицы — правильное решение. Компоненты P&L — это measures одного fact'а (финансовая операция), а не отдельные факты. Spine pattern (join нескольких fact'ов) создавал бы ненужную сложность и риск рассинхронизации.

### C-4. reconciliation_residual как встроенный контроль

`net_payout − Σ(компоненты)` — это self-check P&L формулы. Стабильный residual ~3-5% (SPP compensation у WB) — известная и задокументированная норма. Резкое изменение — anomaly. Это production-ready подход: система не pretends к 100% точности, а явно трекает расхождение.

### C-5. State machine execution lifecycle

PENDING_APPROVAL → APPROVED → SCHEDULED → EXECUTING → RECONCILIATION_PENDING → SUCCEEDED. Каждый transition — через CAS SQL. Retry truth — в PostgreSQL. SUCCEEDED = confirmed by re-read, не provider response alone. Это правильный уровень защиты для системы, которая записывает цены в маркетплейсы реальных продавцов.

### C-6. Signal assembler как единственная точка входа derived signals в pricing

Pricing pipeline не читает ClickHouse напрямую. Signal assembler собирает сигналы из обоих stores и предоставляет `PricingSignalSet`. Это правильно: инкапсулирует источники, позволяет менять store-strategy без изменения pricing logic. Также создаёт точку для мониторинга quality сигналов.

### C-7. Safety gates для FULL_AUTO execution

5 условий для перехода в полную автоматизацию — практичный подход. Обязательная фаза в SEMI_AUTO (min 7 дней), запрет на отключение stale data guard — это предотвращает сценарий «включили автоматизацию на сырых данных и сломали цены на маркетплейсе».

### C-8. Эмпирическая верификация provider contracts

16 Design Decisions (DD-1 через DD-16) с обоснованием из реальных API ответов. Confidence levels (C/C-docs/A/U) на каждом поле. Это единственно правильный подход для работы с маркетплейс API, которые меняются без предупреждения (R-01).

---

## D. Gaps, risks, contradictions, inconsistencies

### D-1. Outbox poller single-instance: нет механизма leader election

**Что не так:** Outbox poller (`SELECT ... FOR UPDATE SKIP LOCKED`, interval 1s) предполагает один активный poller. При multiple instances без координации — все будут poll'ить, один claim'ит. Работает, но создаёт lock contention на `outbox_event`.

**Почему важно:** Сейчас — single instance, это fine. Но `data-model.md` определяет 4 separate entrypoints. ETL outbox poll'ится из `datapulse-ingest-worker`, action outbox — из `datapulse-executor-worker`. Если запустить два ingest-worker'а — оба будут poll'ить ETL outbox. `FOR UPDATE SKIP LOCKED` гарантирует single-winner на claim, но не предотвращает wasted polling cycles.

**Что сломается:** При горизонтальном масштабировании worker'ов (даже 2 инстанса) — increased PostgreSQL connection usage и wasted poll cycles. Не критично для текущего масштаба (единицы-десятки аккаунтов), но архитектурно не чисто.

**Критичность:** Low. Single instance достаточен для Phase A-E scope. `SKIP LOCKED` корректно обрабатывает concurrency. Документировать как known limitation.

### D-2. AcknowledgeMode.AUTO + defaultRequeueRejected=true — потенциальный poison pill loop

**Что не так:** `execution.md` фиксирует consumer config: `AcknowledgeMode.AUTO, prefetchCount=1, defaultRequeueRejected=true`. При `AUTO` ack — если обработка message бросает unhandled exception, Spring AMQP reject'ит message. С `defaultRequeueRejected=true` — message requeue'd. Если ошибка persistent (например, corrupted payload, null pointer в handler) — infinite requeue loop.

**Почему важно:** Outbox pattern гарантирует, что message loss не теряет state (DB-first). Но poison pill loop блокирует **всю очередь** (prefetchCount=1, один consumer). Все последующие ETL tasks или price actions встанут в очередь за poison pill.

**Что сломается:** Один corrupted outbox_event → один message → infinite requeue → queue blocked → все новые tasks не обрабатываются → stale data → automation blocked. Recovery: manual intervention (удалить message из RabbitMQ).

**Критичность:** High. Это реальный production failure mode. Решение: DLQ (Dead Letter Queue) с max retry count на RabbitMQ уровне, или `defaultRequeueRejected=false` + retry logic в consumer code (что уже частично реализовано через CAS + outbox retry).

### D-3. Reconciliation flow не определяет timing и triggering

**Что не так:** `execution.md` описывает reconciliation flow: «re-reads current state, compares expected vs actual». Но не определяет:
- **Когда** запускается reconciliation check? Через N секунд после EXECUTING? По расписанию? По outbox event?
- Outbox event type `RECONCILIATION_CHECK` существует в schema, но **нигде не описан flow создания** этого event'а.
- Что если reconciliation re-read тоже неопределённый? Retry reconciliation?
- Timeout: через сколько RECONCILIATION_PENDING → FAILED?

**Почему важно:** Это не cosmetic gap. Reconciliation — критический архитектурный инвариант: «SUCCEEDED = confirmed only». Без определённого timing action может висеть в RECONCILIATION_PENDING indefinitely.

**Что сломается:** Actions зависают в RECONCILIATION_PENDING. Оператор не видит ни success, ни failure. На маркетплейсе цена уже изменилась, а система не подтвердила. Pricing pipeline может создавать duplicate actions на тот же SKU (guard `last_price_change_at` не обновится, потому что action не terminal).

**Критичность:** High. Нужно зафиксировать: (a) outbox event RECONCILIATION_CHECK создаётся при переходе в RECONCILIATION_PENDING; (b) reconciliation worker poll delay; (c) max reconciliation attempts; (d) timeout → FAILED.

### D-4. Pricing batch processing reads из двух stores без snapshot isolation

**Что не так:** `pricing.md` → Batch processing: «3. Batch signal assembly (one ClickHouse query per signal type)». Signal assembler читает PostgreSQL (canonical state: prices, stocks, COGS) и ClickHouse (derived signals: avg_commission, return_rate). Это два отдельных store — **нет транзакционной границы** между ними.

**Почему важно:** Сценарий: ETL обновляет `canonical_price_snapshot` для SKU A (цена изменилась). Одновременно pricing worker читает старую цену из PostgreSQL, но новый avg_commission из ClickHouse (который уже материализовался). Signals inconsistent → decision based on mixed state.

**Что сломается:** В worst case — pricing decision использует новую commission rate с старой ценой → target price рассчитан неправильно. Guard'ы (stale data guard, volatility guard) могут не поймать это, потому что freshness check проверяет `marketplace_sync_state`, а не consistency между stores.

**Критичность:** Medium. Mitigation: pricing run запускается **post-sync** (задокументировано как триггер). Если ingest worker завершил sync → canonical и ClickHouse обновлены → reads consistent. Но нужно зафиксировать как инвариант: **pricing run НЕ ДОЛЖЕН запускаться во время активного ETL sync для того же connection**. Сейчас это не задокументировано.

### D-5. ETL step 4→5 boundary: canonical UPSERT без транзакционной связи с ClickHouse materialization

**Что не так:** `etl-pipeline.md` → Ingestion flow: шаг 4 (UPSERT canonical в PostgreSQL) и шаг 5 (materialize в ClickHouse) — отдельные операции. Если шаг 5 fails:
- Canonical data обновлена в PostgreSQL ✓
- ClickHouse не обновлён ✗
- `marketplace_sync_state` не обновлён (шаг 6) ✗

При retry: шаг 4 повторяется (UPSERT + `IS DISTINCT FROM` = no-churn = безопасно). Шаг 5 повторяется (ClickHouse ReplacingMergeTree = idempotent). OK.

**Почему важно:** Между failed шагом 5 и retry — canonical truth (PostgreSQL) и analytical truth (ClickHouse) **рассинхронизированы**. Pricing signal assembler читает оба. Stale data guard проверяет `marketplace_sync_state.last_success_at` — которая НЕ обновлена → stale data guard сработает → pricing blocked.

**Что сломается:** Ничего, если stale data guard работает корректно: он заблокирует pricing до завершения ETL retry. **Но** если ClickHouse materialization fail'ится consistently (ClickHouse down) — stale data guard блокирует pricing indefinitely. Это correct behavior, но нигде не задокументировано как explicit failure mode.

**Критичность:** Low. Система ведёт себя безопасно (blocks automation on broken state). Нужно добавить в runbook: «ClickHouse materialization failure → stale data → automation blocked → recover ClickHouse → ETL retry → unblocked».

### D-6. `price_action` on SKU level vs `marketplace_offer` multiplicity

**Что не так:** `pricing.md` определяет decision per `marketplace_offer_id`. `execution.md` → `price_action` содержит «target entity, target price». Но один `seller_sku` может иметь несколько `marketplace_offer` (один на WB, один на Ozon). Price policy assignment scope может быть CONNECTION-level (один маркетплейс) или SKU-level.

Сценарий: один `seller_sku` → два marketplace_offer (WB + Ozon). Pricing run для WB создаёт action с target_price=1000₽ для WB offer. Pricing run для Ozon создаёт action с target_price=950₽ для Ozon offer (другая commission). Оба action'а на одну и ту же `product_master` → визуально в UI это один «товар» с двумя разными ценами.

**Почему важно:** Это не баг — это by design. Но нигде явно не задокументировано, что pricing decisions **per marketplace offer, not per product**. Seller Operations grid показывает что? Цену по offer или по product? Если по product — какую? Если по offer — нужна группировка.

**Что сломается:** Путаница в UI: оператор видит «товар X, цена 1000₽» → одобряет → оказывается это WB, а на Ozon — 950₽. Не архитектурная проблема, но documentation gap.

**Критичность:** Low. Нужно зафиксировать в `seller-operations.md`: grain operational grid = marketplace_offer, не product.

### D-7. Approval timeout: нет описания механизма expiration

**Что не так:** `pricing.md` → `approval_timeout_hours INT DEFAULT 72`. `execution.md` → `PENDING_APPROVAL → EXPIRED`. Но **кто** и **как часто** проверяет expired actions?

Варианты: scheduled job (cron), outbox event с TTL (как retry), lazy check при UI read. Ни один не задокументирован.

**Почему важно:** Без expiration mechanism actions навсегда остаются в PENDING_APPROVAL. Pricing pipeline создаёт новый decision → новый action → PENDING_APPROVAL. Через неделю: десятки pending actions на один SKU, каждый с разным target_price (данные менялись).

**Что сломается:** Оператор видит 10 pending actions на один SKU. Одобряет старый (target_price на основе данных недельной давности). Execution пишет устаревшую цену в маркетплейс.

**Критичность:** Medium. Нужно зафиксировать: (a) механизм (scheduled job, interval 1h); (b) при EXPIRED — action отменяется, не блокирует creation нового.

### D-8. WB Price Write BROKEN — Phase D blocker, не зафиксирован как risk

**Что не так:** `write-contracts.md` → F-1: DNS failure на WB write host. F-2: 401 на production token. Но `risk-register.md` не содержит этого риска. `integration.md` → capability matrix: WB Price Write = BLOCKED.

**Почему важно:** Phase D (Execution) для WB невозможен без работающего price write endpoint. Это не архитектурная проблема, но operational blocker, который должен быть tracked.

**Критичность:** Medium (для Phase D). Not blocking Phase A-C.

### D-9. R-06 в risk-register устарел

**Что не так:** R-06: «WB Returns endpoint заблокирован (400)». Статус: «Подтверждён». Но `wb-read-contracts.md` → «Previously BLOCKED — resolved 2026-03-30. Root cause: incorrect date format». `integration.md` → Returns = READY.

**Критичность:** Low. Косметика, но подрывает доверие к risk register если risks не обновляются.

### D-10. Workspace isolation enforcement: нет описания DB-level mechanism

**Что не так:** `tenancy-iam.md` → «Workspace — граница изоляции данных». `non-functional-architecture.md` → `@PreAuthorize("@accessService.canRead(#connectionId)")`. Это application-level enforcement. Но нет описания DB-level isolation:
- Row-Level Security (RLS)?
- `workspace_id` обязателен в WHERE каждого запроса (application convention)?
- Общие таблицы (canonical_order, fact_finance) содержат данные всех workspace'ов?

**Почему важно:** Application-level isolation = один баг в access check → cross-tenant data leak. Для финансовых данных это серьёзно.

**Что сломается:** Разработчик забывает `.where("workspace_id", :id)` в новом JDBC query → видит данные чужого workspace. `@PreAuthorize` проверяет доступ к endpoint'у, но не к данным внутри.

**Критичность:** Medium. Для MVP с одним-двумя продавцами — acceptable. Для multi-tenant production — нужно зафиксировать стратегию: (a) convention + code review, (b) RLS, (c) schema-per-workspace. Рекомендация: convention + ArchUnit test (проверка что все repository methods принимают workspaceId).

### D-11. `strategy_params` и `guard_config` как JSONB — evolution risk

**Что не так:** `pricing.md` → `price_policy.strategy_params JSONB`, `guard_config JSONB`. Это гибко для Phase C-G (добавление новых стратегий и guards). Но нет validation contract для содержимого JSONB.

**Почему важно:** JSONB без JSON Schema = нет compile-time safety, нет DB constraint validation. Сериализованный `strategy_params` может содержать невалидные данные → runtime error глубоко в pricing pipeline.

**Что сломается:** Оператор задаёт `target_margin_pct: 1.25` (125% маржа вместо 25%). Нет DB constraint, нет validation на этом уровне. Pricing pipeline считает target_price → отрицательный знаменатель (1 − 1.25 − cost_rate < 0) → деление на отрицательное → nonsensical price → guard может не поймать (зависит от min/max bounds).

**Критичность:** Medium. Решение: Java validation (`@Valid` на deserialized strategy params record) задокументировано в coding style (records с Jakarta Validation constraints). Нужно зафиксировать, что strategy_params validation — обязательный слой.

---

## E. Missing checks that must be added during design

### E-1. Concurrent pricing decisions на один SKU

Два pricing run'а (manual + post-sync) запускаются одновременно. Оба создают decision + action для одного marketplace_offer_id. Оба PENDING_APPROVAL. Оператор одобряет оба. Два action'а SCHEDULED → EXECUTING → два вызова price write API → second write overwrites first. Reconciliation подтверждает второй, но первый тоже SUCCEEDED (verified re-read показывает цену из второго).

**Нужно определить:** uniqueness constraint на `price_action` per marketplace_offer_id в non-terminal status? Или pricing pipeline при action scheduling проверяет наличие existing non-terminal action?

### E-2. Ozon per-product rate limit (10 updates/hour)

`write-contracts.md`: Ozon позволяет максимум 10 обновлений цены на продукт в час. Pricing pipeline может генерировать decisions чаще (post-sync + manual + schedule + policy change = 4+ triggers/day). Если products ~1000 → fine. Но если policy change triggers pricing run для всех → один hour window может переполниться.

**Нужно определить:** execution worker должен трекать rate per product? Или это ответственность adapter-level rate limiter?

### E-3. ClickHouse schema evolution path

Analytics модель (facts, dims, marts) будет меняться по мере добавления capabilities (Phase G: advertising facts, promo facts). `ALTER TABLE` в ClickHouse ограничен (нельзя менять sorting key). Re-materialization из canonical при schema change.

**Нужно определить:** migration mechanism, rollback strategy, data backfill process.

### E-4. cost_profile lifecycle

`cost_profile` (SCD2) — критическая зависимость для TARGET_MARGIN strategy и COGS в P&L. Но нет описания: кто создаёт, как обновляет, что происходит при отсутствии (pricing eligibility = SKIP, P&L COGS = 0). Import mechanism, validation rules, default behavior.

### E-5. End-to-end latency budget

ETL sync → canonical UPSERT → ClickHouse materialization → pricing run → decision → action → execution → reconciliation. Сколько времени от изменения цены у конкурента до реакции системы? Нет даже target'а.

---

## F. Minimal corrective actions

| # | Действие | Где | Усилие |
|---|----------|-----|--------|
| 1 | Зафиксировать consumer DLQ/retry strategy для RabbitMQ (D-2) | `execution.md`, `etl-pipeline.md` | Добавить секцию: DLQ topology, max retry before DLQ, monitoring DLQ depth |
| 2 | Определить reconciliation timing и triggering (D-3) | `execution.md` | Добавить: outbox event creation timing, reconciliation poll delay, max attempts, timeout → FAILED |
| 3 | Зафиксировать инвариант: pricing run не запускается во время ETL sync для того же connection (D-4) | `pricing.md` | Одно предложение в секции «Pricing run → Triggers → Post-sync» |
| 4 | Определить expiration mechanism для PENDING_APPROVAL (D-7) | `execution.md` | Scheduled job, interval, behavior |
| 5 | Зафиксировать concurrent action uniqueness policy (E-1) | `execution.md` или `pricing.md` | Constraint или pre-check при action creation |
| 6 | Обновить R-06 в risk-register (D-9) | `risk-register.md` | Status: RESOLVED |
| 7 | Добавить WB Price Write blocker в risk-register (D-8) | `risk-register.md` | Новый R-16 |
| 8 | Зафиксировать strategy_params validation contract (D-11) | `pricing.md` | Секция с validation rules для каждого strategy type |

**Всего: 8 точечных дополнений к существующим документам.** Не redesign, не новые модули.

---

## G. What should NOT be done now

| Усложнение | Почему не нужно |
|------------|-----------------|
| **Event sourcing** | Outbox + CAS + DB-first уже обеспечивает auditability и recovery. Event sourcing добавит operational complexity без решения реальной проблемы. |
| **CQRS с отдельным read model store** | Canonical State в PostgreSQL + Analytics в ClickHouse — это уже разделение read/write по природе данных, но без infrastructure overhead полноценного CQRS. |
| **Saga orchestrator** | Pricing → Action → Execution — это линейный pipeline, не distributed transaction. State machine + outbox достаточно. |
| **API Gateway** | Один бэкенд (datapulse-api), одна точка входа. Gateway добавит latency и complexity без пользы. |
| **Schema-per-tenant isolation** | Для единиц-десятков workspace'ов application-level isolation + convention достаточно. RLS можно добавить позже без architectural change. |
| **GraphQL / gRPC** | REST + server-side pagination + WebSocket for real-time — достаточно для операционного UI. GraphQL не даст выигрыша для формализованных grid-centric screens. |
| **Distributed tracing обязательно с Phase A** | Jaeger задокументирован в стеке, но для Phase A с единичными worker'ами — structured logging с correlation_id достаточно. |
| **REST API formal spec (OpenAPI) до начала кода** | Контракты нужны, но OpenAPI spec до первого endpoint'а — premature. Определить key contracts (pagination, error format, auth) → OpenAPI генерировать из кода. |
| **Отдельный микросервис для каждого worker** | 4 entrypoints из одной кодовой базы (shared Maven modules) — правильно. Отдельные repo'шитории и независимые deployment pipelines — overengineering для текущего масштаба. |

---

## H. Final verdict

**2) Sufficient with must-fix issues.**

Вектор решения — правильный. Декомпозиция на модули — обоснованная. Потоки данных — ясные. Source of truth — определён для каждого вида данных. Failure modes продуманы на уровне выше среднего.

Два must-fix issues до начала реализации:

1. **Poison pill protection (D-2).** Без DLQ strategy один corrupted message блокирует всю обработку. Это не теоретический риск — это standard production failure mode для любой системы с message queues. Fix: DLQ topology + max retry count.

2. **Reconciliation timing (D-3).** Без определённого triggering и timeout reconciliation — incomplete design, а не implementation detail. «SUCCEEDED = confirmed only» — центральный архитектурный инвариант, но механизм его enforcement не специфицирован.

Остальные issues (D-4 через D-11) — significant but not blocking. Они могут быть уточнены в начале реализации Phase A-C без risk'а фундаментального redesign.
