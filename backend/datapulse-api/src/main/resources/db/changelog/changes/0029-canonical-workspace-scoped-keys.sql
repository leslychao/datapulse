--liquibase formatted sql

--changeset datapulse:0029-canonical-workspace-scoped-keys

-- ============================================================
-- Migrate canonical unique keys from connection-scoped to workspace-scoped.
-- connection_id stays as a provenance FK (NOT NULL), but is no longer
-- part of the business/natural key.
-- ============================================================

-- Phase 1: truncate all canonical tables (data will be re-synced)
TRUNCATE TABLE canonical_promo_product,
               canonical_stock_current,
               canonical_price_current,
               canonical_sale,
               canonical_return,
               canonical_finance_entry,
               canonical_order,
               canonical_promo_campaign,
               canonical_advertising_campaign,
               marketplace_offer,
               category,
               warehouse
CASCADE;

-- Phase 2: add new columns
ALTER TABLE canonical_order ADD COLUMN workspace_id bigint NOT NULL REFERENCES workspace(id);
ALTER TABLE canonical_sale ADD COLUMN workspace_id bigint NOT NULL REFERENCES workspace(id);
ALTER TABLE canonical_return ADD COLUMN workspace_id bigint NOT NULL REFERENCES workspace(id);
ALTER TABLE canonical_finance_entry ADD COLUMN workspace_id bigint NOT NULL REFERENCES workspace(id);
ALTER TABLE canonical_promo_campaign ADD COLUMN workspace_id bigint NOT NULL REFERENCES workspace(id);
ALTER TABLE canonical_advertising_campaign ADD COLUMN workspace_id bigint NOT NULL REFERENCES workspace(id);
ALTER TABLE canonical_advertising_campaign ADD COLUMN source_platform varchar(10) NOT NULL DEFAULT '';
ALTER TABLE canonical_advertising_campaign ALTER COLUMN source_platform DROP DEFAULT;
ALTER TABLE category ADD COLUMN workspace_id bigint NOT NULL REFERENCES workspace(id);
ALTER TABLE warehouse ADD COLUMN workspace_id bigint NOT NULL REFERENCES workspace(id);
ALTER TABLE marketplace_offer ADD COLUMN marketplace_type varchar(10) NOT NULL DEFAULT '';
ALTER TABLE marketplace_offer ALTER COLUMN marketplace_type DROP DEFAULT;

-- Phase 3: drop old unique constraints
ALTER TABLE canonical_order DROP CONSTRAINT uq_order_connection_external;
ALTER TABLE canonical_sale DROP CONSTRAINT uq_sale_connection_external;
ALTER TABLE canonical_return DROP CONSTRAINT uq_return_connection_external;
ALTER TABLE canonical_finance_entry DROP CONSTRAINT uq_finance_connection_platform_entry;
ALTER TABLE canonical_promo_campaign DROP CONSTRAINT uq_promo_campaign_connection_external;
ALTER TABLE category DROP CONSTRAINT uq_category_connection_external;
ALTER TABLE warehouse DROP CONSTRAINT uq_warehouse_connection_external;
ALTER TABLE marketplace_offer DROP CONSTRAINT uq_offer_sku_connection_msku;

--changeset datapulse:0029-drop-adcampaign-dynamic-constraints splitStatements:false
DO $$
DECLARE r RECORD;
BEGIN
    FOR r IN (
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'canonical_advertising_campaign'::regclass AND contype = 'u'
    ) LOOP
        EXECUTE format('ALTER TABLE canonical_advertising_campaign DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;

--changeset datapulse:0029-canonical-workspace-scoped-constraints
-- Phase 4: create new workspace-scoped unique constraints
ALTER TABLE canonical_order
    ADD CONSTRAINT uq_order_workspace_platform_external
    UNIQUE (workspace_id, source_platform, external_order_id);

ALTER TABLE canonical_sale
    ADD CONSTRAINT uq_sale_workspace_platform_external
    UNIQUE (workspace_id, source_platform, external_sale_id);

ALTER TABLE canonical_return
    ADD CONSTRAINT uq_return_workspace_platform_external
    UNIQUE (workspace_id, source_platform, external_return_id);

ALTER TABLE canonical_finance_entry
    ADD CONSTRAINT uq_finance_workspace_platform_entry
    UNIQUE (workspace_id, source_platform, external_entry_id);

ALTER TABLE canonical_promo_campaign
    ADD CONSTRAINT uq_promo_workspace_platform_external
    UNIQUE (workspace_id, source_platform, external_promo_id);

ALTER TABLE canonical_advertising_campaign
    ADD CONSTRAINT uq_adcampaign_workspace_platform_external
    UNIQUE (workspace_id, source_platform, external_campaign_id);

ALTER TABLE category
    ADD CONSTRAINT uq_category_workspace_type_external
    UNIQUE (workspace_id, marketplace_type, external_category_id);

ALTER TABLE warehouse
    ADD CONSTRAINT uq_warehouse_workspace_type_external
    UNIQUE (workspace_id, marketplace_type, external_warehouse_id);

ALTER TABLE marketplace_offer
    ADD CONSTRAINT uq_offer_sku_type_msku
    UNIQUE (seller_sku_id, marketplace_type, marketplace_sku);

-- Phase 5: workspace indexes for query performance
CREATE INDEX idx_order_workspace ON canonical_order (workspace_id);
CREATE INDEX idx_sale_workspace ON canonical_sale (workspace_id);
CREATE INDEX idx_return_workspace ON canonical_return (workspace_id);
CREATE INDEX idx_finance_workspace ON canonical_finance_entry (workspace_id);
CREATE INDEX idx_promo_campaign_workspace ON canonical_promo_campaign (workspace_id);
CREATE INDEX idx_adcampaign_workspace ON canonical_advertising_campaign (workspace_id);
CREATE INDEX idx_category_workspace ON category (workspace_id);
CREATE INDEX idx_warehouse_workspace ON warehouse (workspace_id);

--rollback ALTER TABLE canonical_order DROP CONSTRAINT uq_order_workspace_platform_external;
--rollback ALTER TABLE canonical_order DROP COLUMN workspace_id;
--rollback -- Full rollback requires restoring old constraints and re-sync
