--liquibase formatted sql

--changeset datapulse:0005-outbox-event

CREATE TABLE outbox_event (
    id              BIGSERIAL    PRIMARY KEY,
    event_type      VARCHAR(60)  NOT NULL,
    aggregate_type  VARCHAR(60)  NOT NULL,
    aggregate_id    BIGINT       NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    retry_count     INT          NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMPTZ
);

CREATE INDEX idx_outbox_event_poll
    ON outbox_event (status, event_type, created_at)
    WHERE status IN ('PENDING', 'FAILED');

--rollback DROP TABLE outbox_event;
