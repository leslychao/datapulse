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
- **Trigger:** Scheduled job (every 30 min).
- **Main path:** Check data freshness per connection/domain → stale → find matching `alert_rule` → create `alert_event` (OPEN, type = STALE_DATA) → if `blocks_automation` = true → pricing pipeline paused → WebSocket notification.
- **Dependencies:** `alert_rule` configured for connection. Freshness thresholds.
- **Failure risks:** Alert rule not configured → no alert (silent stale). Too many alerts → alert fatigue.
- **Uniqueness:** Scheduled detection, automation-blocking capability — отличается от event-driven alerts.

### AUD-05: Alert event — event-driven (execution failure)

- **Назначение:** Execution module сообщает о terminal failure.
- **Trigger:** `price_action` → FAILED (terminal). Outbox event: `PRICE_ACTION_FAILED`.
- **Main path:** Event consumed → find matching `alert_rule` → create `alert_event` (OPEN, type = ACTION_FAILED) → WebSocket notification.
- **Dependencies:** Outbox event delivery. `alert_rule` for ACTION_FAILED type.
- **Failure risks:** Event delivery delay → late alert. No matching rule → no alert.
- **Uniqueness:** Event-driven (real-time) — другой trigger mechanism (outbox event, не scheduler).

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
