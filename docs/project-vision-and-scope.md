# Datapulse — Project Vision & Scope

## System Purpose

Datapulse — проприетарная аналитическая и операционная платформа для селлеров маркетплейсов Wildberries и Ozon. Система консолидирует разрозненные данные кабинетов в единую trusted seller truth с правдивым P&L, управлением остатками, объяснимым ценообразованием и контролируемым исполнением решений.

Datapulse реализуется как **explainable marketplace operating system** — платформа решений и ежедневный рабочий инструмент селлера, закрывающая полный управленческий цикл:

> наблюдение → нормализация → trusted truth → аналитика → объяснимое решение → контролируемое действие → подтверждение результата → анализ эффекта

## Business Goal

Построить единый источник правды (trusted seller truth) по продажам, остаткам, логистике, финансам, рекламе и штрафам по нескольким маркетплейсам и кабинетам селлера — с правдивым P&L, поддержкой операционных решений и контролируемой автоматизацией.

## Target Users


| Роль                 | Описание                                                                                                            |
| -------------------- | ------------------------------------------------------------------------------------------------------------------- |
| Product owner        | Виталий Ким — архитектура, продуктовое направление, реализация                                                      |
| Целевые пользователи | Селлеры маркетплейсов (WB/Ozon), e-commerce менеджеры, менеджеры ценообразования, операционные менеджеры, аналитики |


## Mandatory Capabilities

Система обязана реализовать следующие capability-группы. Приоритет и фазировка — в разделе Delivery Phases.


| #   | Capability                          | Назначение                                                                                                                   |
| --- | ----------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| 1   | Unified marketplace data foundation | Загрузка и нормализация данных WB и Ozon: каталог, цены, остатки, заказы, продажи, возвраты, финансы, тарифы, промо, реклама |
| 2   | Truthful P&L и unit economics       | Правдивая прибыльность по SKU, категории, кабинету, маркетплейсу и периоду                                                   |
| 3   | Inventory intelligence              | Days of cover, stock-out risk, overstock, frozen capital, рекомендации по пополнению                                         |
| 4   | Returns & penalties intelligence    | Агрегация потерь по возвратам, невыкупам, штрафам; drill-down до evidence                                                    |
| 5   | Pricing decisioning                 | Объяснимый repricing: eligibility → signals → constraints → strategy → decision → explanation                                |
| 6   | Controlled execution                | Lifecycle внешних действий: action → attempt → retry → reconciliation → terminal state                                       |
| 7   | Reconciliation                      | Подтверждение фактического результата действия; защита от ложного "success"                                                  |
| 8   | Data quality & anomaly controls     | Stale data, missing syncs, spikes, mismatch, residuals; блокировка automation при broken truth                               |
| 9   | Seller Operations Layer             | Операционный грид, saved views, working queues, price/promo journals, mismatch monitor                                       |
| 10  | Auditability & explainability       | Provenance, traceability, visible source semantics, explanation summary                                                      |
| 11  | Safe simulated execution mode       | Полный pricing pipeline без реальной записи в маркетплейс; simulated shadow-state                                            |


## Success Criteria

1. Селлер видит точный, сверяемый P&L по всем подключённым аккаунтам
2. Финансовые данные совпадают с выплатами маркетплейса в пределах документированного reconciliation residual
3. Данные загружаются надёжно с идемпотентностью и обработкой retry
4. Система корректно обрабатывает rate limits API маркетплейсов без потери данных
5. Пользователь получает объяснимые рекомендации по ценообразованию, основанные на canonical truth
6. Действия (price actions) проходят полный lifecycle с подтверждением результата
7. Seller Operations Layer обеспечивает ежедневный рабочий контур (грид, очереди, журналы)
8. Simulated mode позволяет безопасно тестировать автоматизацию без реальных записей в маркетплейс

## Technology Stack


| Компонент           | Технология                                         |
| ------------------- | -------------------------------------------------- |
| Язык                | Java 17                                            |
| Фреймворк           | Spring Boot 3.x                                    |
| Сборка              | Maven (multi-module)                               |
| База данных         | PostgreSQL (authoritative), ClickHouse (аналитика) |
| Очереди             | RabbitMQ + Spring AMQP                             |
| Миграции            | Liquibase                                          |
| Аутентификация      | OAuth2 Resource Server + Keycloak                  |
| Edge proxy          | oauth2-proxy                                       |
| Real-time           | WebSocket (STOMP)                                  |
| Секреты             | HashiCorp Vault                                    |
| Маппинг             | MapStruct                                          |
| Кеш                 | Redis (distributed locks / cache only)             |
| Объектное хранилище | S3-совместимое (raw artifacts, replay)             |


## Implementation Constraints


| Ограничение                                       | Обоснование                                                                                                                        |
| ------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| **Java 17**                                       | Запрещены API и конструкции из Java 18+. Codebase baseline.                                                                        |
| **Модульный монолит**                             | Maven multi-module со строгими внутренними границами. Переход к микросервисной архитектуре не допускается в рамках текущего scope. |
| **PostgreSQL — единственный authoritative store** | Вся business truth, decision state, action lifecycle, retry truth фиксируются в PostgreSQL. ClickHouse — только аналитика.         |
| **Только официальные API**                        | Адаптеры используют только текущие, документированные API маркетплейсов. Неофициальные источники запрещены.                        |
| **DB-first + Transactional Outbox**               | Критическое состояние фиксируется в PostgreSQL до внешних действий.                                                                |
| **Enforced boundaries**                           | Архитектурные границы проверяются автоматически в CI (ArchUnit, Maven Enforcer).                                                   |
| **RabbitMQ — только transport/delay**             | Хранение retry truth, business truth, action lifecycle truth или decision truth в брокере запрещено.                               |
| **Redis — только technical fast layer**           | Хранение canonical truth, action truth или hidden business state в Redis запрещено.                                                |
| **Проприетарная лицензия**                        | Только для ревью, без прав на распространение.                                                                                     |


## Delivery Phases


| Фаза                      | Цель                                 | Обязательные capabilities                                                                                      | Обязательные контракты                                                       | Обязательные NFR                                                  | Запрещено начинать раньше                                    |
| ------------------------- | ------------------------------------ | -------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- | ----------------------------------------------------------------- | ------------------------------------------------------------ |
| **A — Foundation**        | Tenancy, интеграция, canonical truth | Tenancy, credentials, sync orchestration, raw/normalized/canonical pipeline, initial catalog and finance truth | Provider read contracts validated (catalog, prices, stocks, orders, finance) | Idempotency, rate limit handling, lane isolation, basic audit     | Automation pricing, execution — без reliable canonical truth |
| **B — Trust Analytics**   | Правдивая аналитика                  | Facts/marts, truthful P&L, returns/penalties, inventory intelligence, anomaly controls                         | Finance sign conventions validated, join keys confirmed                      | Data freshness visibility, reconciliation residual tracking       | Pricing decisions — без confirmed P&L truth                  |
| **C — Pricing**           | Объяснимое ценообразование           | Policies, eligibility, signals, constraints, decision, explanation, manual approval                            | Price write contracts validated                                              | Decision-grade reads only from canonical, explanation audit trail | Auto-execution — без manual approval flow                    |
| **D — Execution**         | Контролируемое исполнение            | Action lifecycle, executor, retries, reconciliation, failed action alerting                                    | Reconciliation read contracts validated                                      | SUCCEEDED = confirmed only, CAS guards, outbox guarantee          | Full automation — без reconciliation                         |
| **E — Seller Operations** | Операционный рабочий слой            | Grid, saved views, working queues, journals, mismatch monitor                                                  | —                                                                            | Operational screen performance, server-side pagination            | —                                                            |
| **F — Simulation**        | Безопасное тестирование              | Execution mode, simulated gateway, shadow-state, parity tests                                                  | —                                                                            | Simulated truth isolation from authoritative truth                | Production simulation — без parity tests                     |
| **G — Intelligence**      | Расширенная аналитика                | Richer observation, advertising analytics, scenario modelling                                                  | Advertising contracts validated                                              | —                                                                 | —                                                            |


## Out of Scope

Следующие направления осознанно исключены из scope системы:

- Микросервисная архитектура
- ERP широкого профиля
- Generalized integration platform
- Универсальный rule engine / DSL
- Event sourcing / workflow engine
- Отдельная simulation platform
- Отдельный marketplace emulator
- Полный planning suite
- Mobile-first продукт
- Полный compliance suite (маркировка, сертификация)
- Универсальный BI-конструктор
- Broker-centric architecture

## Design Decisions

### A. Resolved design decisions

1. **Deployment model** — Docker Compose. Все runtime-компоненты и инфраструктура (PostgreSQL, ClickHouse, RabbitMQ, Redis, Keycloak, Vault) развёртываются через Docker Compose.
2. **Observability baseline** — Prometheus/Micrometer (метрики), Grafana (дашборды), Jaeger (distributed tracing), Loki (агрегация логов).
3. **Raw artifacts storage** — S3-compatible хранилище.
4. **Notification delivery** — WebSocket в UI. Алерты доставляются через интерфейс; оператор должен быть онлайн.

### B. Open design decisions (before implementation)

1. **Backup/restore model** — стратегия бэкапов PostgreSQL, RPO/RTO
2. **Worker scaling model** — горизонтальное масштабирование ingest/pricing/executor worker-ов
3. **CI/CD pipeline** — build, test, deploy pipeline
4. **SLA/SLO** — целевые показатели свежести данных, доступности API, latency operational screens

### C. External dependency limits / blockers

1. **WB Returns** — dedicated endpoint заблокирован (400), требует Analytics-scoped токен; альтернативные источники используются
2. **Ozon FBS** — не тестировался эмпирически (аккаунт без FBS); требует production-аккаунт с FBS для валидации
3. **Ozon rate limits** — явно не документированы ни для одного endpoint; эмпирическое определение пороговых значений
4. **Ozon Advertising** — Performance API требует отдельной OAuth2 регистрации (client_credentials flow) на отдельном хосте (`api-performance.ozon.ru`)
5. **WB Advertising v2→v3 migration** — endpoint `POST /adv/v2/fullstats` отключён; новый `GET /adv/v3/fullstats` требует миграции adapter code (POST→GET)

## Related Documents

- [Target Architecture](target-architecture.md) — bounded contexts, runtime model, data flow, forbidden approaches
- [Functional Capabilities](functional-capabilities.md) — capability groups, user flows
- [Non-Functional Architecture](non-functional-architecture.md) — security, consistency, audit, resilience, observability
- [Data Architecture](data-architecture.md) — data layers, provenance, sign conventions, source-of-truth rules
- [Provider Capability Matrix](provider-capability-matrix.md) — provider contracts coverage
- [Execution & Reconciliation](execution-and-reconciliation.md) — action lifecycle, outbox, retry, idempotency
- [Marketplace API Policy](marketplace-api-policy.md) — mandatory rules for marketplace adapters
- [Provider Contracts](provider-contracts/) — detailed read/write contracts per provider

