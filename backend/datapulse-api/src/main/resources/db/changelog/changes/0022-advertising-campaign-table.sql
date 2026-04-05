--liquibase formatted sql

--changeset datapulse:0022-advertising-campaign-table
CREATE TABLE canonical_advertising_campaign (
    id                    bigserial PRIMARY KEY,
    connection_id         bigint NOT NULL REFERENCES marketplace_connection(id),
    external_campaign_id  varchar(64) NOT NULL,
    name                  varchar(500),
    campaign_type         varchar(50) NOT NULL,
    status                varchar(50) NOT NULL,
    placement             varchar(100),
    daily_budget          decimal(18, 2),
    start_time            timestamptz,
    end_time              timestamptz,
    created_at_external   timestamptz,
    synced_at             timestamptz NOT NULL DEFAULT now(),
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),

    UNIQUE (connection_id, external_campaign_id)
);

CREATE INDEX idx_adcampaign_connection ON canonical_advertising_campaign(connection_id);
CREATE INDEX idx_adcampaign_status ON canonical_advertising_campaign(connection_id, status);

--rollback DROP TABLE canonical_advertising_campaign;
