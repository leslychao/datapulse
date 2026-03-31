--liquibase formatted sql

--changeset datapulse:0002-integration-tables

CREATE TABLE secret_reference (
    id                bigserial    PRIMARY KEY,
    workspace_id      bigint       NOT NULL,
    provider          varchar(20)  NOT NULL,
    vault_path        varchar(500) NOT NULL,
    vault_key         varchar(120) NOT NULL,
    vault_version     int,
    secret_type       varchar(40)  NOT NULL,
    status            varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    rotated_at        timestamptz,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_secret_reference_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id)
);

CREATE INDEX idx_secret_reference_workspace_id ON secret_reference (workspace_id);

CREATE TABLE marketplace_connection (
    id                       bigserial    PRIMARY KEY,
    workspace_id             bigint       NOT NULL,
    marketplace_type         varchar(10)  NOT NULL,
    name                     varchar(255) NOT NULL,
    status                   varchar(20)  NOT NULL DEFAULT 'PENDING_VALIDATION',
    external_account_id      varchar(120),
    secret_reference_id      bigint       NOT NULL,
    perf_secret_reference_id bigint,
    last_check_at            timestamptz,
    last_success_at          timestamptz,
    last_error_at            timestamptz,
    last_error_code          varchar(60),
    created_at               timestamptz  NOT NULL DEFAULT now(),
    updated_at               timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_connection_workspace_type_account UNIQUE (workspace_id, marketplace_type, external_account_id),
    CONSTRAINT fk_connection_workspace              FOREIGN KEY (workspace_id)             REFERENCES workspace        (id),
    CONSTRAINT fk_connection_secret_reference        FOREIGN KEY (secret_reference_id)      REFERENCES secret_reference (id),
    CONSTRAINT fk_connection_perf_secret_reference   FOREIGN KEY (perf_secret_reference_id) REFERENCES secret_reference (id)
);

CREATE INDEX idx_connection_workspace_id ON marketplace_connection (workspace_id);

CREATE TABLE marketplace_sync_state (
    id                        bigserial     PRIMARY KEY,
    marketplace_connection_id bigint        NOT NULL,
    data_domain               varchar(40)   NOT NULL,
    last_sync_at              timestamptz,
    last_success_at           timestamptz,
    next_scheduled_at         timestamptz,
    sync_cursor               jsonb,
    status                    varchar(20)   NOT NULL DEFAULT 'IDLE',
    error_message             varchar(1000),
    created_at                timestamptz   NOT NULL DEFAULT now(),
    updated_at                timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_sync_state_connection_domain UNIQUE (marketplace_connection_id, data_domain),
    CONSTRAINT fk_sync_state_connection        FOREIGN KEY (marketplace_connection_id) REFERENCES marketplace_connection (id)
);

CREATE INDEX idx_sync_state_connection_id ON marketplace_sync_state (marketplace_connection_id);

CREATE TABLE integration_call_log (
    id                        bigserial     PRIMARY KEY,
    marketplace_connection_id bigint        NOT NULL,
    endpoint                  varchar(500)  NOT NULL,
    http_method               varchar(10)   NOT NULL,
    http_status               int,
    duration_ms               int           NOT NULL,
    request_size_bytes        int,
    response_size_bytes       int,
    correlation_id            varchar(60)   NOT NULL,
    error_details             varchar(1000),
    retry_attempt             int           NOT NULL DEFAULT 0,
    created_at                timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_call_log_connection FOREIGN KEY (marketplace_connection_id) REFERENCES marketplace_connection (id)
);

CREATE INDEX idx_call_log_connection_id ON integration_call_log (marketplace_connection_id);
CREATE INDEX idx_call_log_created_at    ON integration_call_log (created_at);

--rollback DROP TABLE integration_call_log;
--rollback DROP TABLE marketplace_sync_state;
--rollback DROP TABLE marketplace_connection;
--rollback DROP TABLE secret_reference;
