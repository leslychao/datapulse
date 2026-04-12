--liquibase formatted sql

--changeset datapulse:0032-bidding-tables

CREATE TABLE bid_policy (
    id bigserial PRIMARY KEY,
    workspace_id bigint NOT NULL REFERENCES workspace(id),
    name varchar(255) NOT NULL,
    strategy_type varchar(50) NOT NULL,
    execution_mode varchar(30) NOT NULL DEFAULT 'RECOMMENDATION',
    status varchar(30) NOT NULL DEFAULT 'DRAFT',
    config jsonb NOT NULL DEFAULT '{}',
    created_by bigint REFERENCES app_user(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE bid_policy_assignment (
    id bigserial PRIMARY KEY,
    bid_policy_id bigint NOT NULL REFERENCES bid_policy(id) ON DELETE CASCADE,
    workspace_id bigint NOT NULL REFERENCES workspace(id),
    marketplace_offer_id bigint REFERENCES marketplace_offer(id),
    campaign_external_id varchar(100),
    assignment_scope varchar(30) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_bid_assignment_offer
    ON bid_policy_assignment(marketplace_offer_id)
    WHERE marketplace_offer_id IS NOT NULL;

CREATE TABLE bidding_run (
    id bigserial PRIMARY KEY,
    workspace_id bigint NOT NULL REFERENCES workspace(id),
    bid_policy_id bigint NOT NULL REFERENCES bid_policy(id),
    status varchar(30) NOT NULL DEFAULT 'RUNNING',
    total_eligible int NOT NULL DEFAULT 0,
    total_decisions int NOT NULL DEFAULT 0,
    total_bid_up int NOT NULL DEFAULT 0,
    total_bid_down int NOT NULL DEFAULT 0,
    total_hold int NOT NULL DEFAULT 0,
    total_pause int NOT NULL DEFAULT 0,
    started_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    error_message text
);

CREATE TABLE bid_decision (
    id bigserial PRIMARY KEY,
    bidding_run_id bigint NOT NULL REFERENCES bidding_run(id),
    workspace_id bigint NOT NULL REFERENCES workspace(id),
    marketplace_offer_id bigint NOT NULL REFERENCES marketplace_offer(id),
    bid_policy_id bigint NOT NULL REFERENCES bid_policy(id),
    strategy_type varchar(50) NOT NULL,
    decision_type varchar(30) NOT NULL,
    current_bid int,
    target_bid int,
    signal_snapshot jsonb,
    guards_applied jsonb,
    explanation_summary text,
    execution_mode varchar(30) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_bid_decision_run ON bid_decision(bidding_run_id);
CREATE INDEX idx_bid_decision_offer ON bid_decision(workspace_id, marketplace_offer_id, created_at DESC);

CREATE TABLE manual_bid_lock (
    id bigserial PRIMARY KEY,
    workspace_id bigint NOT NULL REFERENCES workspace(id),
    marketplace_offer_id bigint NOT NULL REFERENCES marketplace_offer(id),
    locked_bid int,
    reason varchar(500),
    locked_by bigint REFERENCES app_user(id),
    expires_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_manual_bid_lock_offer
    ON manual_bid_lock(workspace_id, marketplace_offer_id)
    WHERE expires_at IS NULL;

--rollback DROP TABLE manual_bid_lock;
--rollback DROP TABLE bid_decision;
--rollback DROP TABLE bidding_run;
--rollback DROP TABLE bid_policy_assignment;
--rollback DROP TABLE bid_policy;
