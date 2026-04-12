--liquibase formatted sql

--changeset datapulse:0031-connection-metadata-column
ALTER TABLE marketplace_connection
    ADD COLUMN metadata jsonb;

COMMENT ON COLUMN marketplace_connection.metadata
    IS 'Provider-specific metadata (JSONB). Yandex: businessId + discovered campaigns.';

--rollback ALTER TABLE marketplace_connection DROP COLUMN metadata;

--changeset datapulse:0031-stock-source-campaign-id
ALTER TABLE canonical_stock_current
    ADD COLUMN source_campaign_id varchar(50);

COMMENT ON COLUMN canonical_stock_current.source_campaign_id
    IS 'Yandex campaignId for campaign-level stock data. NULL for WB/Ozon.';

--rollback ALTER TABLE canonical_stock_current DROP COLUMN source_campaign_id;

--changeset datapulse:0031-return-source-campaign-id
ALTER TABLE canonical_return
    ADD COLUMN source_campaign_id varchar(50);

COMMENT ON COLUMN canonical_return.source_campaign_id
    IS 'Yandex campaignId for campaign-level return data. NULL for WB/Ozon.';

--rollback ALTER TABLE canonical_return DROP COLUMN source_campaign_id;
