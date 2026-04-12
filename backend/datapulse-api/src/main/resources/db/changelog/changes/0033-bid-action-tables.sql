--liquibase formatted sql

--changeset datapulse:0033-bid-action-tables

CREATE TABLE bid_action (
    id bigserial PRIMARY KEY,
    bid_decision_id bigint NOT NULL REFERENCES bid_decision(id),
    workspace_id bigint NOT NULL REFERENCES workspace(id),
    marketplace_offer_id bigint NOT NULL REFERENCES marketplace_offer(id),
    connection_id bigint NOT NULL REFERENCES marketplace_connection(id),
    campaign_external_id varchar(100) NOT NULL,
    nm_id varchar(100),
    marketplace_type varchar(20) NOT NULL,
    target_bid int NOT NULL,
    previous_bid int,
    status varchar(30) NOT NULL DEFAULT 'PENDING_APPROVAL',
    execution_mode varchar(30) NOT NULL,
    approved_at timestamptz,
    scheduled_at timestamptz,
    executed_at timestamptz,
    reconciled_at timestamptz,
    retry_count int NOT NULL DEFAULT 0,
    max_retries int NOT NULL DEFAULT 3,
    error_message text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_bid_action_status ON bid_action(workspace_id, status);
CREATE INDEX idx_bid_action_offer ON bid_action(marketplace_offer_id, created_at DESC);

CREATE TABLE bid_action_attempt (
    id bigserial PRIMARY KEY,
    bid_action_id bigint NOT NULL REFERENCES bid_action(id),
    attempt_number int NOT NULL,
    request_summary jsonb,
    response_summary jsonb,
    reconciliation_read jsonb,
    status varchar(20) NOT NULL,
    error_code varchar(100),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_bid_action_attempt_action ON bid_action_attempt(bid_action_id);

--rollback DROP TABLE bid_action_attempt;
--rollback DROP TABLE bid_action;
