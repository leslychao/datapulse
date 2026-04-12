--liquibase formatted sql

--changeset datapulse:0037-workspace-bidding-settings
CREATE TABLE workspace_bidding_settings (
    id bigserial PRIMARY KEY,
    workspace_id bigint NOT NULL UNIQUE REFERENCES workspace(id),
    bidding_enabled boolean NOT NULL DEFAULT true,
    max_aggregate_daily_spend numeric(12,2),
    min_decision_interval_hours int NOT NULL DEFAULT 4,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE workspace_bidding_settings IS 'Per-workspace autobidding configuration';
COMMENT ON COLUMN workspace_bidding_settings.bidding_enabled IS 'Global on/off switch for autobidding in this workspace';
COMMENT ON COLUMN workspace_bidding_settings.max_aggregate_daily_spend IS 'Max total daily ad spend across all policies (roubles). NULL = unlimited';
COMMENT ON COLUMN workspace_bidding_settings.min_decision_interval_hours IS 'Minimum hours between bid changes (frequency guard override)';

--rollback DROP TABLE workspace_bidding_settings;
