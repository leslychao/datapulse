--liquibase formatted sql

--changeset datapulse:0026-pricing-insight-table
CREATE TABLE pricing_insight (
    id              bigserial       PRIMARY KEY,
    workspace_id    bigint          NOT NULL,
    insight_type    varchar(50)     NOT NULL,
    title           varchar(500)    NOT NULL,
    body            text            NOT NULL,
    severity        varchar(20)     NOT NULL DEFAULT 'INFO',
    acknowledged    boolean         NOT NULL DEFAULT false,
    created_at      timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT fk_pi_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id)
);

CREATE INDEX idx_pi_workspace_created ON pricing_insight (workspace_id, created_at DESC);
CREATE INDEX idx_pi_workspace_ack ON pricing_insight (workspace_id, acknowledged)
    WHERE acknowledged = false;

--rollback DROP TABLE pricing_insight;
