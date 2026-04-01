--liquibase formatted sql

--changeset datapulse:0014-alert-event-indexes

CREATE INDEX IF NOT EXISTS idx_alert_event_blocking
    ON alert_event (workspace_id, connection_id)
    WHERE blocks_automation = true AND status IN ('OPEN', 'ACKNOWLEDGED');

--rollback DROP INDEX idx_alert_event_blocking;

--changeset datapulse:0014-alert-event-active-rule-connection

CREATE INDEX IF NOT EXISTS idx_alert_event_active_rule_connection
    ON alert_event (alert_rule_id, connection_id)
    WHERE status IN ('OPEN', 'ACKNOWLEDGED');

--rollback DROP INDEX idx_alert_event_active_rule_connection;
