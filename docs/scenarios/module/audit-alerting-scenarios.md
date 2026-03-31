# Audit & Alerting — Module Scenarios

## Роль модуля

Audit & Alerting обеспечивает unified audit log, business-level alerting (alert rules + events), scheduled health checkers, automation blocker, и real-time notifications (WebSocket). Cross-cutting модуль, обслуживающий все остальные.

## Сценарии

### AUD-01: Audit log write — user action

- **Назначение:** Запись пользовательского действия в audit log.
- **Trigger:** Any user-initiated mutation (create, update, delete, approve, reject, etc.).
- **Main path:** Interceptor captures action → INSERT `audit_log` (`workspace_id`, `user_id`, `action_type`, `entity_type`, `entity_id`, `details` JSONB, `ip_address`, `created_at`).
- **Dependencies:** SecurityContext (`user_id`, `workspace_id`). Audit interceptor.
- **Failure risks:** Audit write failure → non-blocking (best-effort). Log error, continue main operation.
- **Uniqueness:** User-initiated. `user_id` populated. `ip_address` captured.

### AUD-02: Audit log write — system action

- **Назначение:** Запись системного действия (scheduler, worker) в audit log.
- **Trigger:** System-initiated operations (sync completion, pricing run, action execution, alert auto-resolution).
- **Main path:** Same INSERT, but `user_id` = NULL. `action_type` prefixed with system context.
- **Dependencies:** Correlation context (`job_id`, `action_id`).
- **Failure risks:** Same as AUD-01.
- **Uniqueness:** System actor — другой actor (`user_id` = NULL), другой trigger source.

### AUD-03: Credentials access audit

- **Назначение:** Аудит каждого обращения к Vault за credentials.
- **Trigger:** Every Vault secret read.
- **Main path:** Before Vault call → INSERT `audit_log` (`action_type` = `credentials.accessed`, `entity_type` = `secret_reference`, `details` = `{vault_path, purpose}`).
- **Dependencies:** Vault integration layer. Audit interceptor.
- **Failure risks:** Audit write failure → log error, proceed with Vault call (не блокировать sync/execution).
- **Uniqueness:** Security-sensitive audit — отдельный `action_type`, обязательное логирование `purpose`.

### AUD-04: Alert event — scheduled checker (stale data)

- **Назначение:** Scheduled checker обнаруживает устаревшие данные.
- **Trigger:** Scheduled job (every 5 min (каждые 5 мин)).
- **Main path:** Check data freshness per connection/domain → stale → find matching `alert_rule` → create `alert_event` (OPEN, type = STALE_DATA) → if `blocks_automation` = true → pricing pipeline paused → WebSocket notification.
- **Dependencies:** `alert_rule` configured for connection. Freshness thresholds.
- **Failure risks:** Alert rule not configured → no alert (silent stale). Too many alerts → alert fatigue.
- **Uniqueness:** Scheduled detection, automation-blocking capability — отличается от event-driven alerts.

### AUD-05: Alert event — event-driven (execution failure)

- **Назначение:** Execution module сообщает о terminal failure.
- **Trigger:** Spring `ApplicationEvent` (`AlertTriggeredEvent`) published by source module при terminal failure.
- **Main path:** `ApplicationEvent` listener receives `AlertTriggeredEvent` → find matching `alert_rule` → create `alert_event` (OPEN, type = ACTION_FAILED) → WebSocket notification. Event-driven alerts can have `alert_rule_id` from a matching rule or be created without a rule.
- **Dependencies:** Spring `ApplicationEvent` infrastructure. Matching `alert_rule` for event type.
- **Failure risks:** Event delivery delay → late alert. No matching rule → alert created without rule linkage.
- **Uniqueness:** Event-driven (real-time) — другой trigger mechanism (`ApplicationEvent`, не scheduler).

### AUD-06: Alert acknowledgement

- **Назначение:** Оператор подтверждает, что видел alert.
- **Trigger:** `POST /api/alerts/{id}/acknowledge` (any role).
- **Main path:** `alert_event`: OPEN → ACKNOWLEDGED. `acknowledged_by` = `user_id`. `acknowledged_at` = now().
- **Dependencies:** Alert in OPEN state.
- **Failure risks:** Alert auto-resolved before acknowledgement → conflict. Mitigation: CAS guard.
- **Uniqueness:** User-initiated state transition — другой actor (operator), другой audit trail.

### AUD-07: Alert resolution (manual)

- **Назначение:** Оператор отмечает alert как resolved после расследования.
- **Trigger:** `POST /api/alerts/{id}/resolve` (any role). Body: `{ resolution_note }`.
- **Main path:** ACKNOWLEDGED → RESOLVED. `resolution_note` stored. If alert had `blocks_automation` = true → automation unblocked.
- **Dependencies:** Alert in ACKNOWLEDGED state. Resolution note required.
- **Failure risks:** Premature resolution (underlying issue not fixed) → new alert will be created.
- **Uniqueness:** Manual resolution — другой terminal transition. Automation unblock side effect.

### AUD-08: Alert auto-resolution

- **Назначение:** Alert автоматически разрешается при исчезновении проблемы.
- **Trigger:** Scheduled checker: condition that triggered alert no longer holds.
- **Main path:** Checker runs → data now fresh / residual now normal → OPEN/ACKNOWLEDGED → AUTO_RESOLVED.
- **Dependencies:** Same checker that created the alert.
- **Failure risks:** Intermittent issue → alert created, auto-resolved, created again (flapping). Mitigation: cooldown period.
- **Uniqueness:** System-initiated resolution — другой actor (scheduler, не user), другой terminal state (AUTO_RESOLVED vs RESOLVED).

### AUD-09: Automation blocker

- **Назначение:** Alert с `blocks_automation` = true приостанавливает pricing pipeline для connection.
- **Trigger:** `alert_event` created с type matching rule where `blocks_automation` = true.
- **Main path:** Pricing run → check active blocking alerts for connection → alert found → skip pricing for this connection → log `automation_blocked, alert_id=X`.
- **Dependencies:** Pricing module checks alert state. `alert_rule.blocks_automation` flag.
- **Failure risks:** Stale alert (should be resolved but wasn't) → permanent pricing block. Mitigation: alert age monitoring.
- **Uniqueness:** Cross-module side effect — единственный механизм, где Alerting влияет на Pricing.

### AUD-10: WebSocket notification delivery

- **Назначение:** Real-time push notification о новых alerts.
- **Trigger:** `alert_event` created.
- **Main path:** Alert created → publish to WebSocket topic (workspace-scoped) → connected clients receive notification → unread counter incremented.
- **Dependencies:** WebSocket connection. Client subscribed. Workspace isolation.
- **Failure risks:** Client disconnected → notification lost (WebSocket = best-effort). Mitigation: REST API for unread alerts (pull as fallback).
- **Uniqueness:** Real-time delivery — другой transport (WebSocket, не REST). Best-effort (не guaranteed).

### AUD-11: SPIKE_DETECTION checker

- **Назначение:** Обнаружение резких скачков в ключевых метриках.
- **Trigger:** Event-driven, после каждой ClickHouse materialization (`ETL_SYNC_COMPLETED` event).
- **Main path:** Query ClickHouse: aggregate key measures (revenue, logistics costs, returns amount) за текущий день vs медиана за 30 дней per connection → if deviation > threshold (default: 3× median) → create `alert_event` (SPIKE_DETECTION).
- **Dependencies:** Materialized data in ClickHouse. `alert_rule` с type = SPIKE_DETECTION. Sufficient historical data (> 7 days).
- **Failure risks:** Legitimate spikes (sales event, holiday) → false positive. Short history → unstable baseline.
- **Uniqueness:** Statistical anomaly detection (deviation vs baseline). Event-driven trigger (после materialization, не scheduled).

### AUD-12: MISMATCH checker

- **Назначение:** Обнаружение расхождений между связанными data domains.
- **Trigger:** After each sync (`ETL_SYNC_COMPLETED` event).
- **Main path:** Cross-domain consistency checks: compare last successful `price_action.target_price` vs `canonical_price_current.price` → if `|diff| > threshold` → mismatch record. Also check: expected vs actual promo participation.
- **Dependencies:** Recent price sync. Successful price actions. Threshold configuration. `alert_rule` с type = MISMATCH.
- **Failure risks:** Marketplace processing delay → temporary false mismatch. External price change (manual marketplace UI) → genuine mismatch, not system error.
- **Uniqueness:** Cross-domain comparison (decision truth vs marketplace truth). Closes feedback loop.

### AUD-13: MISSING_SYNC checker

- **Назначение:** Обнаружение пропущенных синхронизаций по расписанию.
- **Trigger:** Scheduled job (every 15 min).
- **Main path:** Для каждого active connection: query `job_execution` → `MAX(started_at)` per domain → compare с expected interval (`expected_interval_minutes × tolerance_factor`) → если превышен → find matching `alert_rule` (MISSING_SYNC) → create `alert_event` (OPEN). Auto-resolve: new completed `job_execution` появился → condition cleared → AUTO_RESOLVED.
- **Dependencies:** `alert_rule` с type = MISSING_SYNC. `job_execution` history. Expected sync interval config: `{ "expected_interval_minutes": 60, "tolerance_factor": 2.0 }`.
- **Failure risks:** Connection DISABLED → checker не алертит (expected). Young connection (только подключена) → первый sync ещё не завершён → false positive. Mitigation: grace period after connection creation.
- **Uniqueness:** Absence detection — алертит на отсутствие события (sync не произошёл), не на наличие проблемы. Другая detection logic (expected vs actual timing).

### AUD-14: Alert rule CRUD + default seeding

- **Назначение:** Управление правилами алертинга и автоматическая seed-рассылка при создании workspace.
- **Trigger:** Workspace creation (seeding). `POST /PUT /DELETE /api/alert-rules` (ADMIN/OWNER).
- **Main path:**
  - **Seeding:** При создании workspace → INSERT default `alert_rule` для каждого rule_type (STALE_DATA, MISSING_SYNC, RESIDUAL_ANOMALY, SPIKE_DETECTION, MISMATCH) с рекомендованными thresholds и default severity/blocks_automation.
  - **CRUD:** Admin creates custom rule (e.g. STALE_DATA для конкретного connection с threshold = 12h). Update: изменить threshold, severity, blocks_automation. Delete/disable: `enabled = false` → checker skips rule. Audit: `alert.rule.create`, `alert.rule.update`.
- **Dependencies:** Workspace creation event (для seeding). User role: ADMIN/OWNER. `alert_rule` table.
- **Failure risks:** Default thresholds не подходят для specific business → alerts too noisy or too silent → user must tune. Disabling blocks_automation rule without understanding consequences → pricing runs on stale data.
- **Uniqueness:** Configuration management — другой actor (admin), другой business outcome (rule config, не alert event). Seeding — one-time initialization flow.
