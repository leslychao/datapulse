# DataPulse — Архитектура проекта

## Стек технологий

- **Framework:** Spring Boot 3.3.5
- **Язык:** Java 17 (source/target 17)
- **Сборка:** Maven (модульный монолит)
- **База данных:** PostgreSQL
- **Очереди:** RabbitMQ
- **Миграции:** Liquibase
- **Messaging:** Spring Integration DSL
- **Аутентификация:** OAuth2 Resource Server
- **Real-time:** WebSocket (STOMP)

## Модули и зависимости

```
datapulse-application  (REST / Security / IAM / WebSocket — точка входа)
    ├── datapulse-etl          (загрузка, материализация, пайплайны, RabbitMQ, outbox)
    │     ├── datapulse-marketplaces  (anti-corruption layer к Ozon / WB API)
    │     │     └── datapulse-domain
    │     └── datapulse-core   (JPA, Liquibase, бизнес-сервисы, MapStruct)
    │           └── datapulse-domain
    └── datapulse-domain       (DTO, enums, exceptions, API-контракты — без persistence)
```

Зависимости идут строго сверху вниз. Обратные зависимости (например `core → etl` или `domain → core`) запрещены.

## Принципы размещения кода

| Что | Модуль | Пакет |
|-----|--------|-------|
| REST-контроллеры | `datapulse-application` | `io.datapulse.rest.*` |
| Security / IAM / фильтры | `datapulse-application` | `io.datapulse.security`, `io.datapulse.iam` |
| WebSocket конфигурация и dispatch | `datapulse-application` | `io.datapulse.websocket` |
| DTO, enums, request/response, exceptions, events, API-контракты | `datapulse-domain` | `io.datapulse.domain.*` |
| JPA-сущности и Spring Data репозитории | `datapulse-core` | `io.datapulse.core.entity.*`, `io.datapulse.core.repository` |
| Бизнес-сервисы (аккаунты, участники, IAM, уведомления, инвайты и т.д.) | `datapulse-core` | `io.datapulse.core.service.*` |
| MapStruct маппинг entity ↔ DTO | `datapulse-core` | `io.datapulse.core.mapper` |
| Конфигурация внешних систем (Keycloak Admin и т.д.) | `datapulse-core` | `io.datapulse.core.keycloak`, `io.datapulse.core.properties` |
| Клиенты к API маркетплейсов (Ozon, WB), raw DTO, rate limiting | `datapulse-marketplaces` | `io.datapulse.marketplaces.*` |
| ETL: загрузка, материализация, факты/измерения, пайплайны, worker, outbox | `datapulse-etl` | `io.datapulse.etl.*` |
| JDBC batch-операции (сырьё, факты) | `datapulse-etl` | `io.datapulse.etl.repository.jdbc` |
| JDBC чтение mart | `datapulse-core` | `io.datapulse.core.repository.orderpnl`, `io.datapulse.core.repository.productpnl` |
| Миграции Liquibase | `datapulse-core` | `resources/db/changelog/` |
| Конфигурация | — | модульные YAML: `datapulse-core.yml`, `datapulse-marketplaces.yml`, `datapulse-etl.yml`, `application.yml` |

## Архитектурные паттерны

| Паттерн | Реализация |
|---------|-----------|
| Constructor injection | `@RequiredArgsConstructor` (Lombok). Field injection запрещён |
| Транзакции | `@Transactional` на сервисах `core`; `TransactionTemplate` для явных границ в ETL worker |
| Обработка ошибок | Иерархия `AppException` в `datapulse-domain` + `GenericControllerAdvice` в application. i18n через `I18nMessageService` |
| Messaging | Spring AMQP + Spring Integration DSL. Очереди ETL. Outbox-паттерн с polling flow |
| Scheduling | `@Scheduled` для периодических задач (user activity flush, file cleanup, cache cleanup, invite cleanup) |
| Async events | `@EnableAsync` + `@Async` для событийных listener-ов (уведомления, email и т.д.) |
| Идемпотентность | SHA-256 ключи + batch insert для raw данных в ETL |
| Состояние ETL | Enum-статусы в БД (`EtlExecutionStatus`, `SourceExecutionStatus`, `EtlScenarioStatus`, `OutboxMessageStatus`) + CAS |
| Persistence | JPA для CRUD в `core`, JDBC (`JdbcTemplate`) для batch/heavy-read в `etl` и mart в `core` |
| Маппинг | MapStruct (`MapperFacade` в core) |
| Server-side sort (JDBC) | Whitelist `Map<String, String>` (DTO field → SQL column) + `buildOrderByClause(Sort)` с fallback. Предотвращает SQL injection |

## Система уведомлений

### Архитектура

```
Доменное событие (Spring ApplicationEvent)
  → EventListener (InviteNotificationListener, SyncNotificationListener и т.д.)
  → NotificationService.create(userId, type, payload)
  → JPA persist NotificationEntity
  → Spring event NotificationCreatedEvent
  → NotificationWebSocketDispatcher (@Async, STOMP push)
  → Клиент получает real-time уведомление
```

### Компоненты

| Компонент | Слой | Ответственность |
|-----------|------|-----------------|
| `NotificationEntity` | core (entity) | JPA-сущность, `userId`, `type`, `payload` (JSON), `read`, `createdAt` |
| `NotificationRepository` | core (repository) | Spring Data JPA, запросы по userId, непрочитанные |
| `NotificationService` | core (service) | CRUD, подсчёт непрочитанных, пометка прочитанными, создание + публикация event |
| `NotificationCreatedEvent` | core (service) | Spring ApplicationEvent после persist |
| `InviteNotificationListener` | core (service) | Слушает invite events → создаёт notification |
| `SyncNotificationListener` | core (service) | Слушает ETL terminal events → создаёт notification |
| `NotificationController` | application (rest) | REST API: list, unread count, mark read |
| `NotificationWebSocketDispatcher` | application (websocket) | `@Async` + STOMP push в `/user/queue/notifications` |

### WebSocket

| Компонент | Ответственность |
|-----------|-----------------|
| `WebSocketConfig` | STOMP endpoint `/ws/notifications/websocket`, SockJS fallback, user destination prefix `/user` |
| `IamHandshakeHandler` | Извлечение Principal из OAuth2 token при WebSocket handshake |
| `NotificationWebSocketDispatcher` | `SimpMessagingTemplate.convertAndSendToUser()` |

### Добавление нового типа уведомления

1. Добавить значение в `NotificationType` enum (`datapulse-domain`).
2. Создать `@EventListener` в `datapulse-core`, слушающий нужное доменное событие.
3. Вызвать `NotificationService.create()` с нужным type и payload.
4. Real-time dispatch и REST API работают автоматически.

## Система инвайтов

### Поток

```
AccountInviteController.create(request)
  → InviteAuthorizationService.canCreate() (проверка canManageMembers по каждому accountId)
  → AccountInviteService.createInvite()
    → persist InviteEntity + InviteTargetEntity
    → Spring event → AccountInviteEmailListener (отправка email)
    → Spring event → InviteNotificationListener (уведомление)

AccountInviteController.resolve(token)
  → AccountInviteService.resolveInvite(token)
    → InviteUserProvisioner.provisionIfNeeded(email) — создание пользователя в Keycloak
    → привязка к аккаунтам
    → возврат AccountInviteResolveResponse
```

### Keycloak Admin интеграция

| Компонент | Слой | Ответственность |
|-----------|------|-----------------|
| `KeycloakProperties` | core (properties) | `@ConfigurationProperties` для Keycloak Admin API (server-url, realm, client-id, client-secret) |
| `KeycloakAdminConfig` | core (keycloak) | `@Bean Keycloak` — клиент admin API |
| `InviteUserProvisioner` | core (service) | Интерфейс: `provisionIfNeeded(email)` → `Optional<String>` userId |
| `KeycloakInviteUserProvisioner` | core (service) | Реализация: поиск/создание пользователя через Keycloak Admin API |
| `AccountInviteCleanupJob` | core (service) | `@Scheduled` — удаление просроченных инвайтов |

## ETL Pipeline (обзор)

Полное описание ETL-подсистемы см. в [docs/etl-implementation.md](etl-implementation.md).

```
EventSource (описание источника)
  → загрузка снапшотов (JSON на диск)
  → EtlSourcePipelineService (стриминг + batch insert в raw-таблицы)
  → материализация (handlers по доменам: finance, sales, dim product и т.д.)
  → mart refresh
  → completion → EtlScenarioTerminalEvent
```

Оркестрация: `EtlOrchestratorService` (bootstrap), `EtlWorkerService` (шаги с guards + CAS), `SourceStepProgressionService`, `EtlCompletionService`.

`EtlScenarioTerminalEvent` — Spring event при завершении сценария (success/failure). Слушается `SyncNotificationListener` для создания уведомлений.

## Аналитическая модель (Star Schema)

### Поток данных

```
API маркетплейсов (Ozon / WB)
  → raw_* таблицы (JSONB payload, идемпотентность по SHA-256 record_key)
  → Materialization handlers (SQL: JSONB → upsert в dim_* / fact_*)
  → fact_finance (консолидация: raw + fact_commission + fact_logistics + fact_penalties + fact_marketing)
  → mart_order_pnl (агрегат fact_finance + fact_sales + fact_returns + fact_product_cost)
  → mart_product_pnl (агрегат по продуктам из fact_sales + fact_finance + fact_product_cost)
```

### Raw-таблицы

Единая схема: `(request_id, account_id, event, source_id, record_key, payload JSONB)`.
Создаются динамически через `RawTableSchemaJdbcRepository`, вставка через `RawBatchInsertJdbcRepository`.

Ключевые raw-таблицы (константы в `RawTableNames`):

| Домен | Таблицы |
|-------|---------|
| Склады | `raw_ozon_warehouses_fbs/fbo`, `raw_wb_warehouses_fbw`, `raw_wb_offices_fbs` |
| Категории | `raw_ozon_category_tree`, `raw_wb_categories_parent`, `raw_wb_subjects` |
| Тарифы | `raw_wb_tariffs_commission`, `raw_ozon_product_info_prices` |
| Продукты | `raw_ozon_products`, `raw_ozon_product_info`, `raw_wb_products` |
| Продажи | `raw_wb_supplier_sales`, `raw_ozon_postings_fbs/fbo` |
| Финансы | `raw_wb_sales_report_detail`, `raw_ozon_finance_transactions` |
| Возвраты | `raw_ozon_returns` (WB — из `raw_wb_supplier_sales`) |
| Остатки | `raw_wb_stocks`, `raw_ozon_product_info_stocks`, `raw_ozon_analytics_stocks` |
| Поставки | `raw_wb_incomes` |

### Таблицы измерений (dim_*)

| Таблица | Natural Key | Особенности |
|---------|-------------|-------------|
| `dim_product` | `(account_id, source_platform, source_product_id)` | JPA-сущность в core |
| `dim_warehouse` | `(account_id, source_platform, source_warehouse_id)` | |
| `dim_category` | дерево категорий Ozon / WB | |
| `dim_subject_wb` | предметы WB | связка с категориями |
| `dim_tariff_wb` | FK на `dim_category_id` | SCD2: `valid_from` / `valid_to` |
| `dim_tariff_ozon` | FK на `product_id` | SCD2: `valid_from` / `valid_to` |

### Таблицы фактов (fact_*)

| Таблица | Grain | FK | Назначение |
|---------|-------|----|------------|
| `fact_sales` | `(account_id, source_platform, source_event_id)` | `dim_product` | Продажи: quantity, unit_price, sale_amount, discount_amount |
| `fact_returns` | `(account_id, source_platform, source_event_id)` | `dim_product` | Возвраты |
| `fact_commission` | по операции/заказу | — | Комиссии: `commission_type` |
| `fact_logistics_costs` | по операции/заказу | — | Логистика: `logistics_type` |
| `fact_penalties` | по заказу | — | Штрафы |
| `fact_marketing_costs` | по заказу | — | Маркетинг (marketplace-initiated) |
| `fact_advertising_costs` | `(account_id, source_platform, campaign_id, advertising_date, source_product_id)` | — | Рекламные затраты (seller-initiated CPM/CPC), campaign-level |
| `fact_finance` | `(account_id, source_platform, order_id, finance_date)` | — | Консолидированный финансовый факт по заказу по дням |
| `fact_product_cost` | `(account_id, product_id, valid_from)` | `dim_product` | Себестоимость (SCD2), управляется из core |
| `fact_inventory_snapshot` | по дате/продукту/складу | `dim_product`, `dim_warehouse` | Снимки остатков |

### Семантика order_id

Поле `order_id` в fact-таблицах и в `mart_order_pnl` **не** является идентификатором заказа покупателя. Его семантика зависит от маркетплейса:

| Маркетплейс | order_id означает | Примеры |
|-------------|-------------------|---------|
| **Ozon** | `posting_number` — номер отправления | `"12345678-0001-1"` |
| **WB** | `srid` — идентификатор строки реализации | UUID-подобная строка |

Один покупательский заказ может содержать несколько отправлений (Ozon) или строк реализации (WB). Это значит:
- `mart_order_pnl` содержит одну строку на отправление/srid, а не на покупательский заказ.
- PnL считается на уровне отправления, а не покупательского заказа.

### fact_finance — ключевой факт

Собирается в `FinanceFactJdbcRepository`. Меры:

| Мера | Описание |
|------|----------|
| `revenue_gross` | Выручка |
| `seller_discount_amount` | Скидка продавца |
| `marketplace_commission_amount` | Комиссия маркетплейса |
| `acquiring_commission_amount` | Эквайринг |
| `logistics_cost_amount` | Логистика |
| `penalties_amount` | Штрафы |
| `marketing_cost_amount` | Маркетинг |
| `compensation_amount` | Компенсации |
| `refund_amount` | Возвраты |
| `net_payout` | Итоговая выплата из сырья |
| `reconciliation_residual` | Остаток сверки: `net_payout` минус сумма разложенных компонентов |

Источники: WB — `raw_wb_sales_report_detail`, Ozon — `raw_ozon_finance_transactions`, плюс данные из `fact_commission`, `fact_logistics_costs`, `fact_penalties`, `fact_marketing_costs`.

### mart_order_pnl — витрина PnL по заказам

Grain: один ряд на заказ `(account_id, source_platform, order_id)`.
Реализация: `OrderPnlMartJdbcRepository.refresh(accountId)` — полный пересчёт UPSERT по аккаунту.

Формула PnL:

```
pnl_amount = revenue_gross
  - seller_discount_amount
  - marketplace_commission_amount
  - acquiring_commission_amount
  - logistics_cost_amount
  - penalties_amount
  - marketing_cost_amount
  - advertising_cost_amount
  - refund_amount
  + compensation_amount
  - cogs_amount
```

- `advertising_cost_amount` аллоцируется из `fact_advertising_costs` пропорционально доле выручки продукта за день (pro-rata по `fact_sales.sale_amount`).
- COGS = `sum(quantity * cost)` из `fact_sales × fact_product_cost` по SCD2 (`valid_from / valid_to` относительно `sale_ts`).

API чтение: `OrderPnlReadJdbcRepository` в core → `OrderPnlResponse` DTO в domain.

### mart_product_pnl — витрина PnL по продуктам

Grain: один ряд на продукт `(account_id, source_platform, source_product_id)`.
Реализация: `ProductPnlMartJdbcRepository.refresh(accountId)` — полный пересчёт UPSERT по аккаунту.

API чтение: `ProductPnlReadJdbcRepository` в core → `ProductPnlResponse` DTO в domain.

### Materialization handlers

Пакет `io.datapulse.etl.materialization`. Каждый handler: `supportedEvent()` + `marketplace()`.

| Домен | Событие | Ozon | WB |
|-------|---------|------|-----|
| Категории | `CATEGORY_DICT` | + | + |
| Склады | `WAREHOUSE_DICT` | + | + |
| Тарифы | `TARIFF_DICT` | + | + |
| Продукты | `PRODUCT_DICT` | + | + |
| Продажи | `SALES_FACT` | + | + |
| Остатки | `INVENTORY_FACT` | + | + |
| Реклама | `ADVERTISING_FACT` | scaffold* | scaffold* |
| Финансы | `FACT_FINANCE` | + | + |

\* Ozon Performance API и WB Promotion API имеют scaffold-реализации, готовые к активации.

### Mart refresh

`MartRefreshService.refreshAfterEvent(...)` вызывается после материализации.

| Событие | `mart_order_pnl` | `mart_product_pnl` |
|---------|------------------|---------------------|
| `SALES_FACT` | да | да |
| `ADVERTISING_FACT` | да | да |
| `FACT_FINANCE` | да | да |

## Добавление нового функционала — чеклист

1. **Не создавать новые модули** без явного запроса. Код размещается в существующих модулях.
2. **Не нарушать направление зависимостей** между модулями.
3. **REST endpoints** — только в `datapulse-application` (`io.datapulse.rest`).
4. **DTO / request / response** — в `datapulse-domain`.
5. **JPA-сущности** — в `datapulse-core`, с миграцией Liquibase в `db/changelog/`.
6. **ETL-источники** — реализация `EventSource` в `datapulse-etl`, регистрация через `@EtlSourceMeta`.
7. **Материализации** — handler в `datapulse-etl.materialization`.
8. **Интеграции с маркетплейсами** — в `datapulse-marketplaces`.
9. **Конфигурация** — через `@ConfigurationProperties`, значения в модульном YAML.
10. **Тесты** — JUnit 5 + Spring Test; для JDBC — Testcontainers PostgreSQL.
11. **Уведомления** — enum в `NotificationType`, `@EventListener` в core, вызов `NotificationService.create()`.
12. **WebSocket topics** — endpoint + dispatcher в `datapulse-application/websocket`.
13. **Scheduled jobs** — `@Scheduled` в core service, конфигурация cron в YAML.
14. **Новый fact** — JDBC-репозиторий в `datapulse-etl/repository/jdbc`, миграция в core.
15. **Новое измерение** — JDBC в etl, миграция в core.
16. **Новый materialization handler** — интерфейс `MaterializationHandler`, указать `supportedEvent()` + `marketplace()`.
17. **Новая мера в `fact_finance`** — колонка в миграции, SQL в `FinanceFactJdbcRepository`, обновить `mart_order_pnl`.
18. **Новый mart** — JDBC write в etl + JDBC read в core, обновить `MartRefreshService`, добавить REST/DTO.
19. **Новая raw-таблица** — константа в `RawTableNames`, использовать `RawBatchInsertJdbcRepository`.
