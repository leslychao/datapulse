# Модуль: Audit & Alerting

**Фаза:** A — Foundation (audit_log, system alerts), B (configurable alert rules), E (notification UI)
**Зависимости:** [Tenancy & IAM](tenancy-iam.md)
**Runtime:** datapulse-api (REST API, WebSocket, scheduled checkers)

---

## Назначение

Единая подсистема аудита, бизнес-алертинга и уведомлений. Три связанные ответственности:

1. **Audit** — запись и хранение всех значимых действий (user + system) с immutability guarantee.
2. **Alerting** — правила бизнес-проверок, scheduled evaluation, генерация alert events с open/resolved lifecycle.
3. **Notification** — доставка событий пользователю: WebSocket (STOMP) в реальном времени, REST API для sync, read/unread tracking.

## Обязательные свойства

- Audit records immutable: UPDATE и DELETE запрещены.
- Audit write — best-effort: failure to write audit не прерывает основную операцию (`log.error`, не rethrow).
- Alert events имеют lifecycle (OPEN → ACKNOWLEDGED → RESOLVED / AUTO_RESOLVED).
- Alert rules с `blocks_automation = true` блокируют pricing/promo pipeline для connection.
- Notification delivery — WebSocket (STOMP) в UI. Оператор должен быть онлайн. Без email / Telegram / push.
- Reconnection fallback: при потере WebSocket — exponential backoff reconnect + REST API для sync текущего состояния.

---

## Audit

### Audit domains

| Domain | Хранилище | Описание |
|--------|-----------|----------|
| User action audit | `audit_log` | Действия пользователей: login, configuration changes, manual approvals/overrides |
| Credentials audit | `audit_log` | Все попытки доступа к credentials: кто, когда, какой account, результат |
| ETL audit | `job_execution` (module-specific) | Каждая загрузка: execution state, source, timing, outcome, error details |
| Price change journal | `price_decision` + `price_action` (module-specific) | Durable history ценовых решений: inputs, constraints, explanation, outcome |
| Data provenance | `job_execution_id` FK chain (module-specific) | Каждая каноническая запись прослеживаема до raw source |

ETL audit, price change journal и data provenance хранятся в таблицах модулей-владельцев ([ETL Pipeline](etl-pipeline.md), [Pricing](pricing.md), [Execution](execution.md)). `audit_log` — единая таблица для user action audit и credentials audit.

### audit_log — schema

```
audit_log:
  id                     BIGSERIAL PK
  workspace_id           BIGINT FK → workspace                     NOT NULL
  actor_type             VARCHAR(20) NOT NULL                      -- USER, SYSTEM, SCHEDULER
  actor_user_id          BIGINT FK → app_user                      (nullable; NULL для SYSTEM/SCHEDULER)
  action_type            VARCHAR(80) NOT NULL                      -- dot-separated key
  entity_type            VARCHAR(60) NOT NULL                      -- e.g. 'marketplace_connection', 'price_policy', 'workspace'
  entity_id              VARCHAR(120) NOT NULL                     -- PK/composite key of target entity
  outcome                VARCHAR(20) NOT NULL                      -- SUCCESS, DENIED, FAILED
  details                JSONB                                     -- context-specific payload (changes diff, reason, etc.)
  ip_address             INET                                      (nullable)
  correlation_id         UUID                                      (nullable; links to request correlation)
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
```

### Indexes

| Index | Columns | Назначение |
|-------|---------|------------|
| `idx_audit_workspace_created` | `(workspace_id, created_at DESC)` | Листинг аудита по workspace |
| `idx_audit_entity` | `(entity_type, entity_id, created_at DESC)` | Аудит конкретной entity |
| `idx_audit_actor` | `(actor_user_id, created_at DESC)` | Аудит действий пользователя |
| `idx_audit_action_type` | `(action_type, created_at DESC)` | Фильтр по типу действия |

### action_type taxonomy

| Prefix | Примеры | Source module |
|--------|---------|--------------|
| `connection.*` | `connection.create`, `connection.update`, `connection.delete`, `connection.sync.manual` | [Integration](integration.md) |
| `credential.*` | `credential.access`, `credential.rotate` | [Integration](integration.md) |
| `policy.*` | `policy.create`, `policy.update`, `policy.activate`, `policy.deactivate` | [Pricing](pricing.md) |
| `lock.*` | `lock.create`, `lock.remove` | [Pricing](pricing.md) |
| `action.*` | `action.approve`, `action.hold`, `action.cancel`, `action.manual_override` | [Execution](execution.md) |
| `workspace.*` | `workspace.create`, `workspace.suspend`, `workspace.reactivate`, `workspace.archive`, `workspace.transfer_ownership` | [Tenancy & IAM](tenancy-iam.md) |
| `user.*` | `user.provision`, `user.deactivate`, `user.reactivate` | [Tenancy & IAM](tenancy-iam.md) |
| `member.*` | `member.invite`, `member.accept_invitation`, `member.cancel_invitation`, `member.change_role`, `member.remove` | [Tenancy & IAM](tenancy-iam.md) |
| `promo.*` | `promo.participation.override`, `promo.activate`, `promo.deactivate` | [Promotions](promotions.md) |
| `alert.*` | `alert.rule.create`, `alert.rule.update`, `alert.acknowledge`, `alert.resolve` | Audit & Alerting |

### Audit write mechanism

Через Spring `ApplicationEvent` → `@Async @TransactionalEventListener(AFTER_COMMIT)` → INSERT в отдельной транзакции (`REQUIRES_NEW`). Failure to write audit — `log.error`, не прерывает основную операцию (best-effort durability).

Source-модули публикуют `AuditEvent` (domain event). Audit & Alerting listener записывает в `audit_log`. Source-модули не зависят от деталей хранения — только от контракта события.

**Canonical location контракта:** `io.datapulse.platform.audit.AuditEvent` (модуль `datapulse-platform`). Source-модули зависят от `datapulse-platform`, listener в `datapulse-audit-alerting` слушает этот тип.

### Retention

| Артефакт | Retention |
|----------|-----------|
| `audit_log` | ≥ 12 месяцев (configurable, deployment-specific) |
| `alert_event` | 6 месяцев (RESOLVED / AUTO_RESOLVED); OPEN — бессрочно |
| `user_notification` | 90 дней |

Cleanup — scheduled job, партиционирование `audit_log` по `created_at` (monthly).

---

## Business Alerting

Помимо infrastructure-level alerting (Grafana Alerting по метрикам Prometheus), система поддерживает **business-level alerts** — алерты, основанные на бизнес-правилах (stale data, anomalies, mismatches). Эти алерты живут в PostgreSQL и доступны через REST API / WebSocket для отображения в UI.

### alert_rule

Определяет, какие бизнес-проверки выполняются и как.

```
alert_rule:
  id                    BIGSERIAL PK
  workspace_id          BIGINT FK → workspace                       NOT NULL
  rule_type             VARCHAR(60) NOT NULL                        -- STALE_DATA, RESIDUAL_ANOMALY, SPIKE_DETECTION, MISSING_SYNC, MISMATCH
  target_entity_type    VARCHAR(60)                                 -- 'marketplace_connection', 'marketplace_offer', NULL (workspace-wide)
  target_entity_id      BIGINT                                      (nullable; NULL = all entities of type)
  config                JSONB NOT NULL                              -- threshold, baseline period, etc.
  enabled               BOOLEAN NOT NULL DEFAULT true
  severity              VARCHAR(20) NOT NULL DEFAULT 'WARNING'      -- INFO, WARNING, CRITICAL
  blocks_automation     BOOLEAN NOT NULL DEFAULT false              -- true → pricing pipeline blocked
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
```

**Default rules:** при создании workspace система seed-ит default alert_rules для каждого rule_type с рекомендованными thresholds. Оператор может настроить (change thresholds, disable).

### alert_event

Каждый инцидент — одна строка. Open/resolved lifecycle.

```
alert_event:
  id                    BIGSERIAL PK
  alert_rule_id         BIGINT FK → alert_rule                      (nullable; NULL for event-driven alerts)
  workspace_id          BIGINT FK → workspace                       NOT NULL
  connection_id         BIGINT FK → marketplace_connection           (nullable)
  status                VARCHAR(20) NOT NULL                         -- OPEN, ACKNOWLEDGED, RESOLVED, AUTO_RESOLVED
  severity              VARCHAR(20) NOT NULL                         -- snapshot from alert_rule at trigger time
  title                 VARCHAR(500) NOT NULL                        -- human-readable summary
  details               JSONB                                        -- rule_type-specific evidence
  blocks_automation     BOOLEAN NOT NULL                             -- snapshot from alert_rule
  opened_at             TIMESTAMPTZ NOT NULL DEFAULT now()
  acknowledged_at       TIMESTAMPTZ                                  (nullable)
  acknowledged_by       BIGINT FK → app_user                         (nullable)
  resolved_at           TIMESTAMPTZ                                  (nullable)
  resolved_reason       VARCHAR(60)                                  -- AUTO (condition cleared), MANUAL (operator dismissed)
```

### alert_event lifecycle

```
OPEN → ACKNOWLEDGED → RESOLVED
     → AUTO_RESOLVED
ACKNOWLEDGED → RESOLVED
             → AUTO_RESOLVED
```

| Переход | Триггер |
|---------|---------|
| → OPEN | Scheduled checker обнаружил нарушение rule |
| OPEN → ACKNOWLEDGED | Оператор подтвердил через UI |
| OPEN → AUTO_RESOLVED | Следующий check показал, что условие больше не нарушается |
| ACKNOWLEDGED → RESOLVED | Оператор вручную закрыл |
| ACKNOWLEDGED → AUTO_RESOLVED | Условие восстановилось |

### Scheduled checkers

| Checker | Interval (default) | Source data | Rule type |
|---------|-------------------|-------------|-----------|
| Stale data checker | 5 min | `marketplace_sync_state.last_success_at` | STALE_DATA |
| Missing sync checker | 15 min | `job_execution` (expected vs actual) | MISSING_SYNC |
| Residual anomaly checker | After each materialization | `mart_posting_pnl.reconciliation_residual` | RESIDUAL_ANOMALY |
| Spike detection checker | After each materialization | Period-over-period changes in fact_finance measures | SPIKE_DETECTION |
| Mismatch checker | After each sync | Cross-domain consistency | MISMATCH |

Checkers запускаются в `datapulse-api`. Post-materialization checkers триггерятся через `ETL_SYNC_COMPLETED` event (fanout consumer в datapulse-api).

### Checker algorithms

#### STALE_DATA checker

**Trigger:** scheduled, every 5 min.

**Algorithm:**
1. Для каждого workspace с enabled `STALE_DATA` rule:
2. Query `marketplace_sync_state` per `marketplace_connection`:
   ```sql
   SELECT mc.id AS connection_id, mc.name, mss.data_domain,
          mss.last_success_at,
          EXTRACT(EPOCH FROM (NOW() - mss.last_success_at)) / 3600 AS hours_since_sync
   FROM marketplace_connection mc
   JOIN marketplace_sync_state mss ON mss.connection_id = mc.id
   WHERE mc.workspace_id = :workspaceId AND mc.status = 'ACTIVE'
   ```
3. Сравниваем `hours_since_sync` с thresholds из `alert_rule.config`:
   ```json
   {
     "finance_stale_hours": 24,
     "state_stale_hours": 48
   }
   ```
4. Finance domains (SALES, RETURNS, FINANCE): порог `finance_stale_hours`. State domains (CATALOG, PRICES, STOCKS): порог `state_stale_hours`.
5. Если порог превышен и нет OPEN/ACKNOWLEDGED alert для этого connection + rule → INSERT `alert_event` (OPEN).

**Auto-resolve condition:** следующий checker run обнаружил, что `hours_since_sync < threshold` для всех domains → UPDATE `alert_event SET status = 'AUTO_RESOLVED', resolved_at = NOW(), resolved_reason = 'AUTO'` для OPEN/ACKNOWLEDGED events этого connection + rule.

#### MISSING_SYNC checker

**Trigger:** scheduled, every 15 min.

**Algorithm:**
1. Для каждого active connection определяем expected sync schedule:
   ```json
   { "expected_interval_minutes": 60, "tolerance_factor": 2.0 }
   ```
2. Query: последний `job_execution` per connection + domain:
   ```sql
   SELECT connection_id, data_domain, MAX(started_at) AS last_started_at
   FROM job_execution
   WHERE workspace_id = :workspaceId AND status = 'COMPLETED'
   GROUP BY connection_id, data_domain
   ```
3. Если `NOW() - last_started_at > expected_interval_minutes × tolerance_factor` → OPEN alert.
4. Не алертит если connection status = DISABLED.

**Auto-resolve condition:** `job_execution` с `status = 'COMPLETED'` появился для пропущенного domain + connection → AUTO_RESOLVED.

#### RESIDUAL_ANOMALY checker

**Trigger:** event-driven, after each ClickHouse materialization (`ETL_SYNC_COMPLETED` event).

**Algorithm:**
1. Query ClickHouse:
   ```sql
   SELECT marketplace_offer_id,
          ABS(reconciliation_residual) AS abs_residual,
          reconciliation_residual
   FROM mart_posting_pnl
   WHERE workspace_id = :workspaceId
     AND report_date >= today() - 30
   ```
2. Вычисляем baseline: `mean(abs_residual)` и `stddev(abs_residual)` за последние 30 дней.
3. Для каждого offer с `abs_residual > mean + config.sigma_threshold × stddev`:
   ```json
   { "sigma_threshold": 2.0, "min_absolute_threshold": 500.0 }
   ```
   Дополнительный guard: `abs_residual > min_absolute_threshold` (игнорируем копеечные аномалии).
4. Группируем аномалии по connection → один alert per connection (details JSONB содержит list of offer_ids и residuals).

**Auto-resolve condition:** следующая materialization не показала превышений для connection → AUTO_RESOLVED.

#### SPIKE_DETECTION checker

**Trigger:** event-driven, after each ClickHouse materialization.

**Algorithm:**
1. Query ClickHouse: агрегирует ключевые measures (revenue, logistic costs, returns amount) за текущий день vs медиана за предыдущие 30 дней по connection:
   ```sql
   SELECT connection_id, measure_name,
          today_value,
          median_30d,
          today_value / NULLIF(median_30d, 0) AS ratio
   FROM (
     -- subquery computing daily aggregates and 30-day medians
   )
   ```
2. Config:
   ```json
   { "spike_ratio_threshold": 3.0, "min_baseline_days": 7 }
   ```
3. Если `ratio > spike_ratio_threshold` и baseline имеет >= `min_baseline_days` дней данных → OPEN alert.
4. Для молодых connections (< min_baseline_days) → checker пропускает (недостаточно данных для baseline).

**Auto-resolve condition:** следующая materialization: `ratio <= spike_ratio_threshold × 0.8` (hysteresis, чтобы избежать флаппинга) → AUTO_RESOLVED.

#### MISMATCH checker

**Trigger:** event-driven, after each sync completion.

**Algorithm:**
1. Cross-domain consistency checks (PostgreSQL):
   - `marketplace_offer` без `canonical_price_current` (ожидается для ACTIVE offers)
   - `canonical_order` с `offer_id` не существующим в `marketplace_offer`
   - `canonical_finance_entry` без matching `canonical_sale` / `canonical_order`
2. Каждая проверка — отдельный SQL count:
   ```sql
   SELECT COUNT(*) FROM marketplace_offer mo
   WHERE mo.status = 'ACTIVE'
     AND mo.workspace_id = :workspaceId
     AND mo.marketplace_connection_id = :connectionId
     AND NOT EXISTS (SELECT 1 FROM canonical_price_current cpc WHERE cpc.marketplace_offer_id = mo.id)
   ```
3. Config:
   ```json
   { "max_orphan_count": 5, "checks": ["price_coverage", "order_orphans", "finance_orphans"] }
   ```
4. Если count > `max_orphan_count` для любой проверки → OPEN alert.

**Auto-resolve condition:** следующий sync привёл count <= `max_orphan_count` для всех проверок → AUTO_RESOLVED.

### Auto-resolve mechanism (общий)

Auto-resolve выполняется каждым checker-ом в конце run:

```
FOR EACH alert_event WHERE alert_rule_id = current_rule.id
                      AND status IN ('OPEN', 'ACKNOWLEDGED')
                      AND connection_id = checked_connection_id:
  IF condition no longer violated:
    UPDATE alert_event SET status = 'AUTO_RESOLVED',
                           resolved_at = NOW(),
                           resolved_reason = 'AUTO'
    → publish AlertResolvedEvent → notification fan-out
```

Event-driven alerts (`alert_rule_id = NULL`) НЕ auto-resolve. Их lifecycle управляется оператором вручную (ACKNOWLEDGED → RESOLVED) или module-specific logic (например, action retry succeeded → модуль Execution публикует `AlertResolvedEvent`).

### Automation blocker integration

Pricing pipeline читает:

```sql
SELECT EXISTS (
  SELECT 1 FROM alert_event
  WHERE workspace_id = :workspaceId
    AND connection_id = :connectionId
    AND blocks_automation = true
    AND status IN ('OPEN', 'ACKNOWLEDGED')
)
```

Если TRUE → pricing run для этого connection blocked. Блокировка per-connection, не global. Ozon stale → Ozon pricing blocked, WB unaffected.

Thresholds и calibration описаны в [Analytics & P&L](analytics-pnl.md) §Контроль качества данных. Audit & Alerting модуль отвечает за evaluation, event lifecycle и automation blocker query. Analytics отвечает за определение *что* проверять и *какие* thresholds.

### Alert type registry

| alert_type (= rule_type) | Source data module | Default severity | Default blocks_automation | Описание |
|---------------------------|-------------------|------------------|---------------------------|----------|
| `STALE_DATA` | [Analytics & P&L](analytics-pnl.md) | CRITICAL | true | Finance sync > 24h, state sync > 48h |
| `MISSING_SYNC` | [ETL Pipeline](etl-pipeline.md) | WARNING | false | Ожидаемый sync пропущен по расписанию |
| `RESIDUAL_ANOMALY` | [Analytics & P&L](analytics-pnl.md) | CRITICAL | true | Reconciliation residual отклонение от baseline > 2σ |
| `SPIKE_DETECTION` | [Analytics & P&L](analytics-pnl.md) | WARNING | false | Period-over-period change > 3× median за 30 дней |
| `MISMATCH` | [Analytics & P&L](analytics-pnl.md) | WARNING | false | Расхождения между связанными data domains |

### Execution и promo alerts

Помимо scheduled business checkers, модули [Execution](execution.md) и [Promotions](promotions.md) публикуют alert events через domain events:

| Событие | Source module | Default severity | Описание |
|---------|-------------|------------------|----------|
| Action failed | [Execution](execution.md) | CRITICAL | price_action перешёл в FAILED; action ID, failure reason, attempt history |
| Stuck-state detected | [Execution](execution.md) | WARNING | Action застрял в non-terminal state > TTL |
| Reconciliation failed | [Execution](execution.md) | WARNING | Deferred reconciliation не подтвердила write |
| Poison pill detected | [Execution](execution.md) | CRITICAL | Unhandled exception в consumer; message consumed без обработки |
| Promo reconciliation mismatch | [Promotions](promotions.md) | WARNING | PROMO_SYNC показал расхождение с ожидаемым participation_status |
| Action deferred | [Pricing](pricing.md) | INFO | Создание price_action отложено из-за in-flight action |

Эти alerts не требуют `alert_rule` — они event-driven (publish `AlertTriggeredEvent` → Audit & Alerting listener → INSERT `alert_event` с `alert_rule_id = NULL`). Auto-resolve не применяется — lifecycle управляется оператором (ACKNOWLEDGED → RESOLVED) или module-specific logic.

**Механизм подписки:** `AlertEventListener` использует `@EventListener` (а не `@TransactionalEventListener`), чтобы alert создавался даже при rollback source-транзакции — operational alert важнее транзакционной консистентности. Для сравнения: `AuditEventListener` использует `@TransactionalEventListener(AFTER_COMMIT)` — audit-запись создаётся только при успешном коммите source-операции.

---

## Notification

### user_notification

Персональная лента уведомлений. Бэкенд для notification bell в UI.

```
user_notification:
  id                    BIGSERIAL PK
  workspace_id          BIGINT FK → workspace                       NOT NULL
  user_id               BIGINT FK → app_user                        NOT NULL
  alert_event_id        BIGINT FK → alert_event                     (nullable; не все notifications из alerts)
  notification_type     VARCHAR(60) NOT NULL                         -- ALERT, APPROVAL_REQUEST, SYNC_COMPLETED, ACTION_FAILED
  title                 VARCHAR(255) NOT NULL
  body                  TEXT                                         (nullable)
  severity              VARCHAR(20) NOT NULL                         -- INFO, WARNING, CRITICAL
  read_at               TIMESTAMPTZ                                  (nullable; NULL = unread)
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
```

### Indexes

| Index | Columns | Назначение |
|-------|---------|------------|
| `idx_notification_user_unread` | `(user_id, workspace_id) WHERE read_at IS NULL` | Unread count badge |
| `idx_notification_user_created` | `(user_id, workspace_id, created_at DESC)` | Notification feed |

### Fan-out

При создании `alert_event` → fan-out: INSERT `user_notification` per workspace member. Фильтрация по роли:

| notification_type | Minimum role |
|-------------------|-------------|
| `ALERT` (CRITICAL) | OPERATOR |
| `ALERT` (WARNING) | OPERATOR |
| `ALERT` (INFO) | ANALYST |
| `APPROVAL_REQUEST` | PRICING_MANAGER |
| `SYNC_COMPLETED` | ANALYST |
| `ACTION_FAILED` | OPERATOR |

### WebSocket / STOMP architecture

**Technology:** Spring WebSocket + STOMP protocol (SockJS fallback).

**Runtime:** `datapulse-api` — единственный WebSocket endpoint (`/ws`).

#### STOMP destinations

| Destination | Type | Описание | Payload |
|-------------|------|----------|---------|
| `/topic/workspace/{workspaceId}/alerts` | Topic (broadcast) | Новые alert_events (OPEN), auto-resolution | `{ alertEventId, ruleType, severity, title, status, connectionId }` |
| `/topic/workspace/{workspaceId}/sync-status` | Topic | Статус ETL sync (started, completed, failed) | `{ connectionId, jobExecutionId, status, completedDomains[] }` |
| `/topic/workspace/{workspaceId}/actions` | Topic | Изменения статуса price/promo actions | `{ actionId, actionType, status, offerId }` |
| `/user/queue/notifications` | User-specific | Персональные уведомления | `{ notificationId, notificationType, title, severity, createdAt }` |

#### Message flow

```
Business event (alert_event INSERT, action status change, sync completion)
  → Spring ApplicationEvent
  → @EventListener in WebSocket notification service
  → SimpMessagingTemplate.convertAndSend("/topic/workspace/{id}/...")
  → Connected STOMP clients receive message
```

При создании `user_notification` → push через `SimpMessagingTemplate.convertAndSendToUser(userId, "/queue/notifications", payload)`.

#### Authentication

WebSocket handshake authenticated через JWT token (query parameter `?token=...` при initial connect). `HandshakeInterceptor` валидирует token, извлекает user/workspace context. STOMP CONNECT frame — без дополнительной auth (handshake уже authenticated).

**Authorization:** workspace-level subscription check. User может подписаться только на `/topic/workspace/{id}/*` где у него есть membership. Enforced через `ChannelInterceptor` на SUBSCRIBE frame.

#### Scalability (Phase G)

Single `datapulse-api` instance: in-memory `SimpleBrokerMessageHandler` (Spring default). Для multi-instance: switch to external STOMP broker (RabbitMQ STOMP plugin) — shared topic across instances. Phase A-E: single instance, in-memory broker.

### REST API

| Endpoint | Method | Описание |
|----------|--------|----------|
| `/api/notifications` | GET | Notification feed (paginated, newest first) |
| `/api/notifications/unread-count` | GET | Unread count для badge |
| `/api/notifications/{id}/read` | POST | Mark read |
| `/api/notifications/read-all` | POST | Mark all read |
| `/api/audit-log` | GET | Audit log (paginated, filtered by entityType/entityId/actorUserId/actionType/dateFrom/dateTo). Response `details` — JSON string (клиент парсит самостоятельно) |
| `/api/alerts` | GET | Active alert events (paginated, filtered by severity/status/connection) |
| `/api/alerts/{id}/acknowledge` | POST | OPEN → ACKNOWLEDGED |
| `/api/alerts/{id}/resolve` | POST | ACKNOWLEDGED → RESOLVED |
| `/api/alert-rules` | GET | Alert rules для workspace |
| `/api/alert-rules/{id}` | PUT | Update rule (thresholds, enabled, severity) |

### Reconnection fallback

При потере WebSocket — клиент выполняет exponential backoff reconnect (1s → 2s → 4s → max 30s). После reconnect — `GET /api/notifications?since={lastSeenTimestamp}` для sync пропущенных событий.

---

## Модель данных

### Таблицы PostgreSQL (владение модуля)

| Таблица | Назначение |
|---------|------------|
| `audit_log` | Immutable log всех значимых user/system действий |
| `alert_rule` | Configurable бизнес-правила для scheduled checkers |
| `alert_event` | Инциденты: факт срабатывания правила или event-driven alert |
| `user_notification` | Персональная лента уведомлений per user |

---

## Фазовое разделение

| Компонент | Фаза |
|-----------|------|
| `audit_log` (write + read API) | **A** |
| `alert_event` (event-driven, без configurable rules) | **A** |
| WebSocket notification push (ETL_SYNC_COMPLETED — первый use case) | **A** |
| `alert_rule` (configurable rules, scheduled checkers) | **B** |
| Automation blocker query | **B** (data quality controls) |
| `user_notification` + notification REST API | **E** |
| Notification bell + unread tracking (frontend) | **E** |
| Alert rule CRUD API | **E** |
| Retention cleanup job | **B** |

---

## Design decisions

### AA-1: Unified audit_log vs per-module audit tables — RESOLVED

Единая `audit_log` для user action audit и credentials audit. Module-specific audit (ETL timing, price decision snapshots, data provenance) остаётся в таблицах модулей-владельцев. Обоснование: module-specific audit требует domain-specific schema (JSONB не покрывает query patterns), а user actions имеют унифицированную структуру (actor, entity, action_type, outcome).

### AA-2: Event-driven alerts vs scheduled-only — RESOLVED

Два механизма генерации alert_event:

1. **Scheduled checkers** — для data quality controls (STALE_DATA, RESIDUAL_ANOMALY, etc.). Проверяют условия периодически, поддерживают auto-resolve.
2. **Event-driven** — для operational alerts (action failed, stuck state, poison pill). Модули публикуют `AlertTriggeredEvent`, listener записывает `alert_event` с `alert_rule_id = NULL`. Auto-resolve не применяется.

Единая таблица `alert_event` для обоих типов. `alert_rule_id` nullable — distinguishes scheduled vs event-driven.

### AA-3: Notification fan-out — RESOLVED

Fan-out per workspace member при создании alert_event. Роль-based фильтрация (CRITICAL → OPERATOR+, INFO → ANALYST+). Для workspace с малым количеством пользователей (MVP: 1-5) fan-out trivial. Для больших workspace (Phase G+) — возможна оптимизация через lazy notification (создаётся при чтении, не при alert).

### AA-4: alert_rule scope — configurable by operator — RESOLVED

Default rules seed-ятся при создании workspace. Оператор может:
- Изменить thresholds (e.g. stale data: 24h → 12h)
- Отключить rule (`enabled = false`)
- Изменить severity (WARNING → CRITICAL)
- Включить/выключить `blocks_automation`

Создание custom rules — Phase G+ (расширение rule_type taxonomy).

---

## Связанные модули

- [Tenancy & IAM](tenancy-iam.md) — workspace isolation, user roles для notification fan-out
- [Analytics & P&L](analytics-pnl.md) — data quality thresholds и calibration; source data для scheduled checkers
- [Execution](execution.md) — event-driven alerts (action failed, stuck state, reconciliation failed, poison pill); audit записи для CAS-переходов и manual operations
- [Pricing](pricing.md) — automation blocker consumer; event-driven alert (action deferred); audit записи для policy CRUD
- [Promotions](promotions.md) — event-driven alert (promo reconciliation mismatch); audit записи для promo actions
- [ETL Pipeline](etl-pipeline.md) — source data для MISSING_SYNC checker; ETL_SYNC_COMPLETED как trigger для post-materialization checkers
- [Integration](integration.md) — audit записи для connection/credential operations
- [Seller Operations](seller-operations.md) — notification UI, alert dashboard, audit log viewer
