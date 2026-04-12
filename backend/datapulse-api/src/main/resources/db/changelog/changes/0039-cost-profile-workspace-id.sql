--liquibase formatted sql

--changeset datapulse:0039-cost-profile-workspace-id
ALTER TABLE cost_profile ADD COLUMN workspace_id bigint;

UPDATE cost_profile cp SET workspace_id = (
    SELECT pm.workspace_id FROM seller_sku ss
    JOIN product_master pm ON ss.product_master_id = pm.id
    WHERE ss.id = cp.seller_sku_id
);

ALTER TABLE cost_profile ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE cost_profile ADD CONSTRAINT fk_cost_profile_workspace
    FOREIGN KEY (workspace_id) REFERENCES workspace(id);

CREATE INDEX idx_cost_profile_workspace_id ON cost_profile (workspace_id);

--rollback ALTER TABLE cost_profile DROP CONSTRAINT fk_cost_profile_workspace;
--rollback DROP INDEX idx_cost_profile_workspace_id;
--rollback ALTER TABLE cost_profile DROP COLUMN workspace_id;
