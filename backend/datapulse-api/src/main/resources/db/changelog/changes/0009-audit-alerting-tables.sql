--liquibase formatted sql

--changeset datapulse:0009-audit-alerting-tables

-- ── audit_log (immutable) ────────────────────────────────────────────────

CREATE TABLE audit_log (
    id              BIGSERIAL    PRIMARY KEY,
    workspace_id    BIGINT       NOT NULL,
    actor_type      VARCHAR(20)  NOT NULL,
    actor_user_id   BIGINT,
    action_type     VARCHAR(80)  NOT NULL,
    entity_type     VARCHAR(60)  NOT NULL,
    entity_id       VARCHAR(120) NOT NULL,
    outcome         VARCHAR(20)  NOT NULL,
    details         JSONB,
    ip_address      INET,
    correlation_id  UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_audit_log_workspace  FOREIGN KEY (workspace_id)  REFERENCES workspace (id),
    CONSTRAINT fk_audit_log_actor_user FOREIGN KEY (actor_user_id) REFERENCES app_user  (id)
);

CREATE INDEX idx_audit_workspace_created ON audit_log (workspace_id, created_at DESC);
CREATE INDEX idx_audit_entity            ON audit_log (entity_type, entity_id, created_at DESC);
CREATE INDEX idx_audit_actor             ON audit_log (actor_user_id, created_at DESC);
CREATE INDEX idx_audit_action_type       ON audit_log (action_type, created_at DESC);

-- ── alert_rule ───────────────────────────────────────────────────────────

CREATE TABLE alert_rule (
    id                   BIGSERIAL    PRIMARY KEY,
    workspace_id         BIGINT       NOT NULL,
    rule_type            VARCHAR(60)  NOT NULL,
    target_entity_type   VARCHAR(60),
    target_entity_id     BIGINT,
    config               JSONB        NOT NULL,
    enabled              BOOLEAN      NOT NULL DEFAULT true,
    severity             VARCHAR(20)  NOT NULL DEFAULT 'WARNING',
    blocks_automation    BOOLEAN      NOT NULL DEFAULT false,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_alert_rule_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id)
);

-- ── alert_event ──────────────────────────────────────────────────────────

CREATE TABLE alert_event (
    id                BIGSERIAL    PRIMARY KEY,
    alert_rule_id     BIGINT,
    workspace_id      BIGINT       NOT NULL,
    connection_id     BIGINT,
    status            VARCHAR(20)  NOT NULL,
    severity          VARCHAR(20)  NOT NULL,
    title             VARCHAR(500) NOT NULL,
    details           JSONB,
    blocks_automation BOOLEAN      NOT NULL,
    opened_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    acknowledged_at   TIMESTAMPTZ,
    acknowledged_by   BIGINT,
    resolved_at       TIMESTAMPTZ,
    resolved_reason   VARCHAR(60),

    CONSTRAINT fk_alert_event_rule       FOREIGN KEY (alert_rule_id) REFERENCES alert_rule            (id),
    CONSTRAINT fk_alert_event_workspace  FOREIGN KEY (workspace_id)  REFERENCES workspace             (id),
    CONSTRAINT fk_alert_event_connection FOREIGN KEY (connection_id) REFERENCES marketplace_connection (id),
    CONSTRAINT fk_alert_event_ack_user   FOREIGN KEY (acknowledged_by) REFERENCES app_user            (id)
);

-- ── user_notification ────────────────────────────────────────────────────

CREATE TABLE user_notification (
    id                BIGSERIAL    PRIMARY KEY,
    workspace_id      BIGINT       NOT NULL,
    user_id           BIGINT       NOT NULL,
    alert_event_id    BIGINT,
    notification_type VARCHAR(60)  NOT NULL,
    title             VARCHAR(255) NOT NULL,
    body              TEXT,
    severity          VARCHAR(20)  NOT NULL,
    read_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_notification_workspace   FOREIGN KEY (workspace_id)   REFERENCES workspace  (id),
    CONSTRAINT fk_notification_user        FOREIGN KEY (user_id)        REFERENCES app_user   (id),
    CONSTRAINT fk_notification_alert_event FOREIGN KEY (alert_event_id) REFERENCES alert_event (id)
);

CREATE INDEX idx_notification_user_unread
    ON user_notification (user_id, workspace_id)
    WHERE read_at IS NULL;

CREATE INDEX idx_notification_user_created
    ON user_notification (user_id, workspace_id, created_at DESC);

--rollback DROP TABLE user_notification;
--rollback DROP TABLE alert_event;
--rollback DROP TABLE alert_rule;
--rollback DROP TABLE audit_log;
