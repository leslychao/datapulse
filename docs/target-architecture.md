# Datapulse — Целевая архитектура

## Архитектурный стиль

Модульный монолит. Один репозиторий, Maven multi-module со строгими dependency rules. Несколько runtime entrypoints для разных типов нагрузки.

Архитектурные границы проверяются в CI (ArchUnit, Maven Enforcer). Нарушение boundary — build failure.

### Запрещённые подходы

| Подход | Причина |
|--------|---------|
| Микросервисы | Не оправданы для текущего масштаба; усложняют consistency |
| Event sourcing | Append-only raw layer + DB-first outbox покрывают требования |
| Workflow engine | Explicit code предпочтительнее magic framework |
| Broker-centric architecture | RabbitMQ — только transport/delay; business truth — только PostgreSQL |
| Universal rule engine / DSL | Explicit pricing pipeline вместо generic rule evaluation |

## Стек технологий

| Компонент | Технология |
|-----------|------------|
| Язык | Java 17 (строго; Java 18+ запрещена) |
| Фреймворк | Spring Boot 3.x |
| Сборка | Maven (multi-module) |
| БД (авторитетная) | PostgreSQL |
| БД (аналитика) | ClickHouse |
| Брокер | RabbitMQ |
| Миграции | Liquibase |
| Аутентификация | OAuth2 Resource Server + Keycloak |
| Edge proxy | oauth2-proxy |
| Real-time | WebSocket (STOMP) |
| Секреты | HashiCorp Vault |
| Маппинг | MapStruct |
| Кеш / locks | Redis |
| Объектное хранилище | S3-compatible |
| Метрики | Prometheus / Micrometer |
| Дашборды | Grafana |
| Distributed tracing | Jaeger |
| Агрегация логов | Loki |
| Развёртывание | Docker Compose |

## Контекст системы

```
┌──────────────┐     ┌──────────────┐
│  Ozon API    │     │   WB API     │
└──────┬───────┘     └──────┬───────┘
       │                    │
       └────────┬───────────┘
                │ HTTP (rate-limited)
                ▼
┌─────────────────────────────────────┐
│  Marketplace Integration Layer     │  Anti-corruption boundary
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│  Data Pipeline                      │  Raw → Normalized → Canonical → Analytics
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│  Business Logic & Decisioning       │  Pricing, execution, reconciliation
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│  Seller Operations & API            │  REST API, WebSocket, operational layer
└──────────────┬──────────────────────┘
               │ HTTP/WS
               ▼
        ┌──────────────┐
        │   Клиенты    │
        └──────────────┘
```

### Границы системы

**Внутри:** загрузка данных маркетплейсов, нормализация, каноническая модель, аналитика, pricing, execution, reconciliation, операционный слой, аудит.

**Вне системы (внешние зависимости):** WB API, Ozon API, Keycloak, Vault.

**Не входит:** ERP, CRM, документооборот, compliance suite, mobile app.

## Модульная структура

```
datapulse/
├─ datapulse-bom/                    BOM для версий зависимостей
├─ datapulse-common/                 Технические примитивы (без бизнес-логики)
│  ├─ datapulse-common-core
│  ├─ datapulse-common-db
│  ├─ datapulse-common-json
│  ├─ datapulse-common-messaging
│  └─ datapulse-common-security
├─ datapulse-domain/                 Доменная модель (DTO, enums, exceptions, контракты)
├─ datapulse-application/            Бизнес-логика (сервисы, policies, pipelines)
├─ datapulse-adapters/               Infrastructure (persistence, messaging, HTTP)
│  ├─ datapulse-adapter-postgres
│  ├─ datapulse-adapter-clickhouse
│  ├─ datapulse-adapter-rabbitmq
│  ├─ datapulse-adapter-s3
│  ├─ datapulse-adapter-marketplace-wb
│  ├─ datapulse-adapter-marketplace-ozon
│  └─ datapulse-adapter-marketplace-simulated
└─ datapulse-bootstrap/              Runtime entrypoints
   ├─ datapulse-api
   ├─ datapulse-ingest-worker
   ├─ datapulse-pricing-worker
   └─ datapulse-executor-worker
```

### Правила модулей

| Модуль | Содержимое | Запрещено |
|--------|------------|-----------|
| `common` | Технические примитивы: утилиты, base config, shared DTO-базы | Бизнес-логика |
| `domain` | Доменная модель: DTO, enums, exceptions, API-контракты | Persistence-зависимости |
| `application` | Бизнес-логика: сервисы, policies, pipelines | Прямые инфраструктурные зависимости |
| `adapters` | Infrastructure: persistence, messaging, HTTP-клиенты, marketplace adapters | Бизнес-логика |
| `bootstrap` | Runtime entrypoints с Spring Boot конфигурацией | Бизнес-логика |

Зависимости строго top-down: `bootstrap` → `application` → `domain` ← `adapters`. Reverse dependencies запрещены.

### Runtime entrypoints

| Entrypoint | Ответственность |
|------------|-----------------|
| `datapulse-api` | REST API, WebSocket, IAM, operational screens |
| `datapulse-ingest-worker` | Data pipeline: fetch → raw → normalize → canonicalize |
| `datapulse-pricing-worker` | Pricing pipeline: eligibility → decision → action scheduling |
| `datapulse-executor-worker` | Action execution: attempt → external call → reconciliation |

## Bounded contexts

| Контекст | Ответственность | Правило ownership |
|----------|-----------------|-------------------|
| Tenancy & Access | Tenant (юрлицо), workspace (бренд/команда), пользователи, membership, роли, приглашения | Единственный хозяин tenant/user/access state |
| Marketplace Integration | Connections, secret references, sync state, provider call metadata, нормализация | Единственный хозяин provider interaction state и credential references |
| Catalog Core | Канонические товары (product_master → seller_sku → marketplace_offer), cost profiles | Единственный хозяин canonical product truth |
| Canonical Data | Canonical state (цены, остатки) и canonical flow (заказы, продажи, возвраты, финансы) | Единственный хозяин canonical business data |
| Pricing | Policies, strategies, constraints, decisions, explanations, action intents | Единственный хозяин pricing decision и action intent |
| Analytics | Derived facts, marts, projections, derived signals (velocity, return rate) | Read-only потребитель канонической модели; поставщик derived signals для pricing |
| Execution | Action lifecycle, attempts, retries, reconciliation | Единственный хозяин execution state и retry truth |
| Seller Operations | Operational workspace: grids, views, queues, assignments, journals | Единственный хозяин operational workspace state |
| Alerting & Audit | Alert rules, events, audit log | Единственный хозяин alert/audit state |

### Ownership split: Pricing vs Execution

Pricing владеет решением и action intent. Execution владеет попытками, retries и reconciliation.

- Pricing не знает о retry scheduling.
- Execution не изобретает pricing decisions.
- Lifecycle-логика не размазывается между контекстами.

## Хранилища и их роли

| Хранилище | Роль | Хранит | Запрещено |
|-----------|------|--------|-----------|
| PostgreSQL | Авторитетный store | Tenancy, canonical state (цены, остатки), canonical flow (заказы, продажи, возвраты, финансы), connections, secret refs, decisions, actions, attempts, reconciliation, outbox, audit | — |
| ClickHouse | Analytical store | Append-only facts, historical snapshots, marts, derived signals | Action lifecycle, retries, reconciliation, decision state |
| Redis | Technical fast layer | Short-lived caches, distributed locks, dedupe | Каноническую модель, action state, hidden business state |
| S3-compatible | Raw / explainability | Raw payloads, evidence artifacts, replay inputs | Runtime business truth |
| RabbitMQ | Transport / delay | Async decoupling, queue-based work distribution, TTL/DLX delay | Business state, retry truth, action lifecycle |

## Основные потоки данных

### Data pipeline

```
API маркетплейсов
 → Raw layer (S3/JSONB, immutable)
 → Normalized layer (typed DTO, in-process)
 → Canonical layer (PostgreSQL, marketplace-agnostic)
 → Analytics layer (ClickHouse, facts/marts)
```

Обязательный порядок. Пропуск стадий запрещён. Каноническая модель — единственный допустимый вход для business computations. Pricing pipeline читает current state из canonical (PostgreSQL), derived signals (velocity, return rate) — из analytics (ClickHouse) через signal assembler.

Детали pipeline и модели данных — [Архитектура данных](data-architecture.md).

### Pricing & Execution

```
Eligibility → Signal Assembly → Strategy Evaluation → Constraint Resolution → Guard Pipeline → Decision → Explanation
 → Action Scheduling → Execute → Reconcile
```

Pricing pipeline реализуется как explicit kernel: `EligibilityPolicy`, `SignalAssembler`, `StrategyEngine`, `ConstraintResolver`, `GuardPipeline`, `DecisionEngine`, `DecisionExplainer`, `ActionFactory`.

Execution и reconciliation — отдельный lifecycle, не часть decision logic.

Детали pipeline — [Архитектура ценообразования](pricing-architecture-analysis.md).

### Async flow

```
API / Scheduler → Job в БД → Outbox insert → Outbox poller → RabbitMQ
 → Worker → DB update → optional next outbox command
```

Retry truth живёт в PostgreSQL. RabbitMQ обеспечивает только delay через DLX/TTL.

## Marketplace integration

Marketplace layer строится через capability interfaces.

**Обязательные:**

- `CatalogReadGateway` — каталог товаров
- `PriceReadGateway` — текущие цены
- `PriceWriteGateway` — запись цен
- `StockReadGateway` — остатки
- `OrderReadGateway` — заказы и отправления
- `FinanceReadGateway` — финансовые транзакции
- `CredentialValidatorGateway` — валидация API-ключей

**Опциональные:**

- `PromoReadGateway`, `PromoWriteGateway` — промо-акции
- `ReportReadGateway` — отчёты
- `AdvertisingReadGateway` — рекламная статистика

Provider DTO и transport details остаются внутри adapter boundary (anti-corruption layer). Протекание provider shapes в domain/application — architectural violation.

## WebSocket (real-time уведомления)

Протокол: STOMP over WebSocket через `datapulse-api`.

| Событие | Канал | Когда |
|---------|-------|-------|
| Sync progress | `/topic/connection.{connectionId}.sync` | Изменение статуса ETL job |
| Price action update | `/topic/connection.{connectionId}.actions` | Изменение статуса price action |
| Alert fired | `/topic/workspace.{workspaceId}.alerts` | Срабатывание alert rule |
| Data freshness | `/topic/connection.{connectionId}.freshness` | Периодический heartbeat свежести |

Правила:

- Подписка scoped по `connectionId` / `workspaceId` — пользователь получает события только своих подключений.
- Авторизация при handshake (JWT).
- События — notification-only; клиент обязан перечитывать актуальное состояние через REST API.
- Notification delivery — только через WebSocket в UI. Оператор должен быть онлайн для получения алертов.

### Reconnection strategy

| Правило | Описание |
|---------|----------|
| Client-side reconnect | Exponential backoff: 1s → 2s → 4s → 8s → max 30s |
| Resubscribe | После reconnect клиент переподписывается на все topic-и |
| State sync | После reconnect клиент обязан перечитать текущее состояние через REST API (события за время disconnection не гарантируются) |
| Server-side | Сервер не хранит историю сообщений; missed events — ответственность клиента (REST fallback) |
| Heartbeat | STOMP heartbeat (10s/10s) для раннего обнаружения разрыва |

## Архитектурные принципы

| # | Принцип | Суть |
|---|---------|------|
| 1 | Truth first | Сначала надёжная каноническая модель, потом automation |
| 2 | DB-first | Критическое состояние — сначала в PostgreSQL, потом внешний вызов |
| 3 | Explainability first | Каждое решение обязано быть объяснимым |
| 4 | Controlled automation | Automation через явный lifecycle с idempotency, reconciliation и human override |
| 5 | Explicit ownership | У каждого критичного состояния — один хозяин (один bounded context) |
| 6 | No hidden business state | RabbitMQ, Redis, S3 — не source of truth |
| 7 | Enforced boundaries | Архитектурные границы проверяются в CI |
| 8 | Capability-based integration | Marketplace adapters — по capability interfaces, не через универсальный gateway |
| 9 | SUCCEEDED = confirmed only | SUCCEEDED означает подтверждённый эффект, не просто HTTP 2xx |

## Ограничения реализации

| Ограничение | Обоснование |
|-------------|-------------|
| Java 17 строго | Запрещены API из Java 18+ |
| Модульный монолит | Maven multi-module; микросервисы не допускаются |
| PostgreSQL — единственный авторитетный store | ClickHouse — только аналитика |
| Только официальные API маркетплейсов | Неофициальные источники запрещены |
| DB-first + Transactional Outbox | Критическое состояние в PostgreSQL до внешних действий |
| RabbitMQ — только transport/delay | Хранение business state в брокере запрещено |
| Redis — только technical fast layer | Хранение канонической модели в Redis запрещено |

## Связанные документы

- [Видение и границы](project-vision-and-scope.md) — scope, фазы
- [Функциональные возможности](functional-capabilities.md) — что система делает
- [Архитектура данных](data-architecture.md) — data pipeline, модель данных
- [Исполнение и сверка](execution-and-reconciliation.md) — action lifecycle
- [Нефункциональная архитектура](non-functional-architecture.md) — security, resilience, observability
- [Матрица возможностей провайдеров](provider-capability-matrix.md) — provider coverage
