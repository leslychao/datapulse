--liquibase formatted sql

--changeset datapulse:0030-alert-event-resolved-by
ALTER TABLE alert_event ADD COLUMN IF NOT EXISTS resolved_by BIGINT;
ALTER TABLE alert_event ADD CONSTRAINT fk_alert_event_resolved_user
    FOREIGN KEY (resolved_by) REFERENCES app_user (id);

--rollback ALTER TABLE alert_event DROP CONSTRAINT IF EXISTS fk_alert_event_resolved_user;
--rollback ALTER TABLE alert_event DROP COLUMN IF EXISTS resolved_by;
