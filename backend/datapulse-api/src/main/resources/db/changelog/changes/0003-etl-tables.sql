--liquibase formatted sql

--changeset datapulse:0003-shedlock-table

CREATE TABLE shedlock (
    name       varchar(64)  NOT NULL PRIMARY KEY,
    lock_until timestamptz  NOT NULL,
    locked_at  timestamptz  NOT NULL,
    locked_by  varchar(255) NOT NULL
);

--rollback DROP TABLE shedlock;

--changeset datapulse:0003-etl-tables

CREATE TABLE job_execution (
    id              bigserial    PRIMARY KEY,
    connection_id   bigint       NOT NULL,
    event_type      varchar(64)  NOT NULL,
    status          varchar(32)  NOT NULL DEFAULT 'PENDING',
    started_at      timestamptz,
    completed_at    timestamptz,
    error_details   jsonb,
    checkpoint      jsonb,
    created_at      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_job_execution_connection FOREIGN KEY (connection_id) REFERENCES marketplace_connection (id)
);

CREATE INDEX idx_job_execution_connection_id        ON job_execution (connection_id);
CREATE INDEX idx_job_execution_status               ON job_execution (status);
CREATE INDEX idx_job_execution_connection_id_status  ON job_execution (connection_id, status);

CREATE TABLE job_item (
    id                 bigserial    PRIMARY KEY,
    job_execution_id   bigint       NOT NULL,
    request_id         varchar(64)  NOT NULL,
    source_id          varchar(128) NOT NULL,
    page_number        int          NOT NULL,
    s3_key             varchar(512) NOT NULL,
    record_count       int,
    content_sha256     varchar(64)  NOT NULL,
    byte_size          bigint       NOT NULL,
    status             varchar(32)  NOT NULL DEFAULT 'CAPTURED',
    captured_at        timestamptz  NOT NULL DEFAULT now(),
    processed_at       timestamptz,

    CONSTRAINT fk_job_item_execution FOREIGN KEY (job_execution_id) REFERENCES job_execution (id)
);

CREATE INDEX idx_job_item_execution_id      ON job_item (job_execution_id);
CREATE INDEX idx_job_item_status_captured   ON job_item (status, captured_at);

--rollback DROP TABLE job_item;
--rollback DROP TABLE job_execution;
