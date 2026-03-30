# Datapulse — Ревью архитектуры (2026-03-30)

## Вердикт

Архитектура **концептуально сильная**. Документация — на уровне, который редко встречается в проектах до первого коммита кода. Особенно впечатляют: эмпирическая верификация provider API, зрелая модель P&L, и дисциплина «DB-first + outbox». Ниже — конкретные замечания, разделённые по критичности.

---

## 1. Критические проблемы (блокеры или риски архитектурных ошибок)

### 1.1 «Модульный монолит» с 4 runtime entrypoints — терминологическое противоречие

`project-vision-and-scope.md` фиксирует constraint: **«модульный монолит, Maven multi-module»**. Но `data-model.md` определяет 4 отдельных runtime entrypoints:
- `datapulse-api`
- `datapulse-ingest-worker`
- `datapulse-pricing-worker`
- `datapulse-executor-worker`

Это не монолит — это 4 отдельных процесса с разделённым deployment. Технически это **modular codebase with independent deployment units** (ближе к «independently deployable modules» или «service-based architecture»).

**Почему важно:** выбор влияет на:
- Транзакционные границы (shared DB connection pool vs отдельные пулы)
- Maven packaging (один fat JAR vs несколько)
- Версионирование и deployment (атомарный релиз vs independent)
- Shared in-process state (event bus, Spring context) — невозможен при отдельных процессах

**Рекомендация:** явно зафиксировать в `project-vision-and-scope.md`:
1. Это один Maven multi-module проект с **отдельными Spring Boot applications** (разные `@SpringBootApplication` классы).
2. Shared modules (domain, infrastructure) — библиотеки, не self-running.
3. Каждый entrypoint — отдельный Docker-контейнер.
4. Транзакции между entrypoints — только через PostgreSQL + outbox (уже задокументировано, но стоит явно привязать к deployment model).

### 1.2 ClickHouse `SELECT ... FINAL` — скрытая проблема производительности

`analytics-pnl.md` фиксирует: `ReplacingMergeTree` + `SELECT ... FINAL при чтении`.

`FINAL` заставляет ClickHouse выполнять on-the-fly merge всех parts при каждом запросе. Для таблиц с десятками миллионов строк это **кратно замедляет чтения** (10-50× degradation в зависимости от числа parts).

**Рекомендация:** задокументировать стратегию:
- **Вариант A:** `OPTIMIZE TABLE ... FINAL` после batch materialization (допустимо для ETL-driven refresh 1-4 раза в день).
- **Вариант B:** Дедупликация на уровне подзапроса (`HAVING count() = 1` для уже-merged строк + `argMax` для дублей).
- **Вариант C:** `ReplacingMergeTree` + `do_not_merge_across_partitions_select_final = 1` (ClickHouse 23.12+) — существенно быстрее.

Текущая формулировка «SELECT ... FINAL при чтении» создаёт иллюзию, что это решённый вопрос.

### 1.3 Отсутствует: контракт между frontend и backend (REST API spec)

Есть детальное описание frontend (design direction, components, stack) и backend (modules, data model, pipeline). Но **нет ни одного описания REST endpoint'а**, который будет между ними.

Для Phase A это означает:
- Нет endpoint'ов для Tenancy (create workspace, manage members, invitations)
- Нет endpoint'ов для Integration (create connection, validate credentials, sync status)
- Нет endpoint'ов для Seller Operations grid (list offers, filter, sort, paginate)

**Рекомендация:** добавить `docs/api-contracts.md` или per-module API секции с:
- Method + path
- Request/response schema (можно records, не обязательно OpenAPI)
- Pagination contract (cursor vs offset, sort contract)
- Error contract (уже описан в NFR, но нужна привязка к endpoints)

### 1.4 Отсутствует: стратегия миграции ClickHouse schema

Liquibase зафиксирован для PostgreSQL. Но ClickHouse schema (facts, dims, marts) **не имеет миграционного механизма**.

При изменении star schema (добавление measure в `fact_finance`, изменение sorting key) нужна стратегия:
- Как мигрировать schema?
- Как пересчитать существующие данные?
- `ALTER TABLE` vs `DROP + CREATE + re-materialize`?

**Рекомендация:** зафиксировать подход в `data-model.md` или `analytics-pnl.md`. Простейший вариант: ClickHouse schema управляется через numbered SQL scripts (аналог Flyway), re-materialization — через ETL replay из canonical.

### 1.5 Race condition: pricing-worker читает canonical во время записи ingest-worker

`pricing-worker` читает `canonical_price_snapshot`, `cost_profile`, `marketplace_offer` для signal assembly. `ingest-worker` пишет в эти же таблицы (UPSERT). CAS guards защищают action lifecycle, но **не защищают от чтения частично обновлённых данных** pricing-worker'ом.

Сценарий: ETL обновляет `canonical_price_snapshot` для SKU A, но ещё не обновил `canonical_stock_snapshot` для SKU A. Pricing worker читает новую цену со старыми остатками.

**Рекомендация:** зафиксировать одно из:
- **Допустимо:** pricing run запускается только после полного завершения ETL sync (event-driven trigger). Уже частично задокументировано в `pricing.md` → «Post-sync trigger», но не формализовано как инвариант.
- **Альтернатива:** pricing read выполняется в одной `@Transactional(readOnly = true)` сессии — PostgreSQL MVCC гарантирует snapshot isolation.

---

## 2. Значимые замечания (не блокеры, но влияют на качество реализации)

### 2.1 Дублирование permission matrix

Матрица разрешений определена **в трёх местах**:
1. `tenancy-iam.md` → секция «Матрица разрешений»
2. `non-functional-architecture.md` → секция «Матрица разрешений»
3. `.cursor/rules/coding-style.mdc` → неявно через `@PreAuthorize` примеры

Три источника = три точки рассинхронизации.

**Рекомендация:** единственная canonical матрица — в `tenancy-iam.md`. Остальные документы — ссылки на неё.

### 2.2 R-06 в risk-register рассинхронизирован

`risk-register.md` → R-06: «Dedicated endpoint возвратов WB возвращает 400; требует Analytics-scoped токен». Статус: подтверждён.

Но `integration.md` → capability matrix: Returns = **READY**. И `wb-read-contracts.md` → Returns: «Previously BLOCKED — resolved 2026-03-30. Root cause: incorrect date format».

R-06 должен быть закрыт или переформулирован.

### 2.3 WB Price Write BLOCKED — не отражён в risk-register как blocker Phase D

`write-contracts.md` и `integration.md` фиксируют: WB Price Write **BROKEN** (DNS failure + 401). Но в `risk-register.md` нет отдельного риска для этого. R-08 (ложный SUCCEEDED) — про reconciliation, не про невозможность записи.

**Рекомендация:** добавить R-16: «WB Price Write недоступен: DNS + token scope». Impact: Phase D Execution для WB невозможен до разрешения. Priority: высокий.

### 2.4 WebSocket: нет контракта сообщений

`non-functional-architecture.md` фиксирует STOMP over SockJS. `frontend-design-direction.md` описывает real-time обновления grid. Но **нигде нет** STOMP topic structure, message format, или subscription model.

**Рекомендация:** добавить секцию в NFR или в отдельный документ:
- Topic naming: `/topic/workspace/{id}/sync-status`, `/topic/workspace/{id}/alerts`
- Message payload format
- Subscription lifecycle (connect → subscribe → heartbeat → reconnect)

### 2.5 cost_profile (SCD2) — нет UI/import механизма

`etl-pipeline.md` и `analytics-pnl.md` ссылаются на `cost_profile` как SCD2 таблицу себестоимости. P&L зависит от неё (`COGS = fact_sales × cost_profile`). Но:
- Нет endpoint'а для управления cost_profile
- Нет UI-экрана
- Нет механизма импорта (CSV, manual entry)

Без COGS P&L будет **структурно неполным**, а TARGET_MARGIN стратегия pricing — неработоспособной (eligibility: «COGS задана»).

**Рекомендация:** добавить в Phase A или B задачу: «CRUD API + UI для cost_profile». Минимум: bulk CSV import + manual per-SKU entry.

### 2.6 alert_rule / alert_event — упомянуты, но не специфицированы

`data-model.md` упоминает таблицы `alert_rule`, `alert_event`, `audit_log` как «Audit (cross-cutting)». Ни один модульный документ не описывает их структуру, lifecycle, или connection к notification system.

**Рекомендация:** добавить секцию в `non-functional-architecture.md` или создать отдельный audit cross-cutting документ.

### 2.7 Working Queues — слишком схематично

`seller-operations.md` описывает `working_queue_definition` и `working_queue_assignment`, но не определяет:
- Формат filter criteria (JSONB? DSL?)
- Автоматическое vs ручное assignment
- Lifecycle assignment (OPEN → IN_PROGRESS → DONE?)
- Пересчёт очереди при изменении данных

Для Phase E это потребует существенной доработки архитектуры.

### 2.8 Frontend: не принято решение по grid-компоненту

`frontend-design-direction.md` указывает: «TanStack Table **or** AG Grid Community». Это ключевое решение, влияющее на:
- Virtual scrolling (AG Grid — из коробки; TanStack Table — нужна самостоятельная реализация)
- Column pinning, resizing, reordering
- Server-side integration
- Bundle size

**Рекомендация:** принять решение до начала Phase E. AG Grid Community — значительно более feature-rich для операционных гридов, но менее гибкий для кастомизации.

---

## 3. Мелкие замечания и предложения

### 3.1 Naming: `marketplace_connection` vs «account» vs «connection» vs «cabinet»

В документации используются 4 термина для одного и того же концепта:
- `marketplace_connection` (data model)
- «кабинет» (русский текст)
- «account» (correlation context: `account_id`)
- «connection» (API: `connectionId`)

**Рекомендация:** зафиксировать canonical глоссарий. Предложение: `connection` в коде и API, «подключение» или «кабинет» в UI.

### 3.2 Отсутствует: Docker Compose для полного стека

В `docker/docker-compose.yml` только WireMock. Для локальной разработки нужен compose с: PostgreSQL, ClickHouse, RabbitMQ, Redis, Keycloak, Vault, MinIO, WireMock.

Это задача для Phase A implementation, но стоит зафиксировать в runbook или в отдельном `docker-compose.dev.yml`.

### 3.3 Нет SLA/SLO даже как target

`non-functional-architecture.md` → «Открытые вопросы» → SLA/SLO. Для Phase A это допустимо, но для Phase B (Analytics) и Phase C (Pricing) нужны хотя бы target'ы:
- Data freshness target: <N часов от sync до mart
- Grid response time target: <N мс для P95
- Pricing run duration target: <N секунд для M SKU

### 3.4 Отсутствует: error recovery при недоступности ClickHouse

`non-functional-architecture.md` описывает recovery для PostgreSQL, RabbitMQ, Keycloak, Vault, WB/Ozon API. **ClickHouse** не упомянут.

Если ClickHouse недоступен:
- ETL materialization → ClickHouse шаг — fail. Canonical data в PostgreSQL не пострадает.
- Pricing signal assembly → ClickHouse read — fail. Pricing run — fail.
- Analytics screens — fail.

**Рекомендация:** добавить ClickHouse в таблицу «Критичные интеграции» в `runbook.md`.

### 3.5 Approval timeout: нет механизма batch expiration

`pricing.md`: `approval_timeout_hours INT DEFAULT 72`. `execution.md`: `PENDING_APPROVAL → EXPIRED`. Но нет описания, **кто** и **как** проверяет expired actions.

Варианты:
- Scheduled job (добавить в `runbook.md` → «Scheduled jobs»)
- Outbox event with TTL
- Lazy check при чтении

### 3.6 `version` в docker-compose.yml — deprecated

`docker/docker-compose.yml` содержит `version: "3.9"`. Docker Compose V2 игнорирует это поле. Убрать для чистоты.

---

## 4. Сильные стороны (что сделано хорошо)

### 4.1 Эмпирическая верификация provider API

`wb-read-contracts.md`, `ozon-read-contracts.md`, `mapping-spec.md`, `empirical-verification-log.md` — исключительно тщательная верификация. Confidence levels (C/C-docs/A/U) — отличная практика. Design Decisions (DD-1 через DD-16) с обоснованием — образцовый подход.

### 4.2 Sign conventions — resolved для обоих провайдеров

WB (все значения положительные, credit/debit по имени поля) vs Ozon (positive = credit, negative = debit) — зафиксировано, верифицировано, и нормализация задокументирована. Это часто источник ошибок в финансовых системах.

### 4.3 P&L модель после санации

13-компонентная формула с `reconciliation_residual` — зрелый подход. Решение «revenue spine = only sales, refunds separate» (K-3) предотвращает классическую ошибку двойного учёта. Удаление избыточных fact-таблиц (fact_commission, fact_logistics_costs и т.д.) в пользу consolidated `fact_finance` — правильное упрощение.

### 4.4 DB-first + Transactional Outbox

Последовательный и продуманный подход: state → DB commit → outbox → broker → consumer. CAS guards для state transitions. Retry truth в PostgreSQL, не в broker. Это правильная архитектура для системы, где потеря state — критичное событие.

### 4.5 Phased delivery с явными prerequisites

Каждая фаза имеет «Запрещено начинать раньше» — anti-scope-creep mechanism. Пример: «Pricing decisions — без confirmed P&L truth» (Phase C blocked by Phase B). Это предотвращает построение автоматизации на ненадёжных данных.

### 4.6 Frontend Design Direction

Выбор Cursor IDE-inspired design language для operational tool — обоснованный. Явные anti-goals (no glossy SaaS, no oversized cards, no mobile-first) — помогают сохранить фокус. Design tokens с CSS custom properties для будущего dark mode — forward-compatible.

### 4.7 Risk Register

Комплексный, с 15 рисками, каждый с вероятностью, влиянием, митигацией, и detection mechanism. Presence of R-13 (P&L без рекламных расходов как known gap) — честная фиксация ограничений.

---

## 5. Рекомендуемый порядок действий

| # | Действие | Документ | Критичность |
|---|----------|----------|-------------|
| 1 | Уточнить deployment model (п. 1.1) | `project-vision-and-scope.md` | Критический |
| 2 | Задокументировать ClickHouse read strategy (п. 1.2) | `analytics-pnl.md` | Критический |
| 3 | Начать REST API contracts (п. 1.3) | Новый: `api-contracts.md` или per-module | Критический |
| 4 | Определить ClickHouse migration strategy (п. 1.4) | `analytics-pnl.md` | Критический |
| 5 | Зафиксировать pricing read isolation (п. 1.5) | `pricing.md` | Критический |
| 6 | Убрать дублирование permission matrix (п. 2.1) | `non-functional-architecture.md` | Средний |
| 7 | Обновить R-06 в risk-register (п. 2.2) | `risk-register.md` | Средний |
| 8 | Добавить R-16 WB Price Write (п. 2.3) | `risk-register.md` | Средний |
| 9 | Определить cost_profile CRUD (п. 2.5) | `etl-pipeline.md` или feature | Средний |
| 10 | Выбрать grid-компонент (п. 2.8) | `frontend-design-direction.md` | Средний |
| 11 | Docker Compose для dev stack (п. 3.2) | `docker/docker-compose.dev.yml` | Низкий (Phase A impl) |

---

## Резюме

Архитектура готова к началу реализации Phase A. Пять критических замечаний (§1) требуют уточнения **до** написания кода, но ни одно из них не требует фундаментального redesign — это дорабатываемые gaps в otherwise solid architecture. Mapping spec и provider contracts — production-grade quality. P&L модель — зрелая и верифицированная. Frontend direction — обоснованная и практичная.
