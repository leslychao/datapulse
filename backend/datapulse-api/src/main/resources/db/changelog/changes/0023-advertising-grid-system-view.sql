--liquibase formatted sql

--changeset datapulse:0023-advertising-grid-system-view

-- Allow user_id to be NULL for system views (visible to all users in workspace)
ALTER TABLE saved_view ALTER COLUMN user_id DROP NOT NULL;

-- System views are unique per workspace by name
CREATE UNIQUE INDEX IF NOT EXISTS idx_sv_system_unique
    ON saved_view (workspace_id, name) WHERE is_system = true;

-- Seed system view "Реклама" for all existing workspaces
INSERT INTO saved_view (workspace_id, user_id, name, is_default, is_system, filters,
                        sort_column, sort_direction, visible_columns, group_by_sku)
SELECT
    w.id,
    NULL,
    'Реклама',
    false,
    true,
    '{}'::jsonb,
    NULL,
    'ASC',
    '["sku_code","product_name","marketplace_type","connection_name","current_price","margin_pct","ad_spend_30d","drr_30d_pct","ad_cpo","ad_roas","revenue_30d"]'::jsonb,
    false
FROM workspace w
WHERE NOT EXISTS (
    SELECT 1 FROM saved_view sv
    WHERE sv.workspace_id = w.id AND sv.name = 'Реклама' AND sv.is_system = true
);

--rollback DELETE FROM saved_view WHERE name = 'Реклама' AND is_system = true;
--rollback DROP INDEX IF EXISTS idx_sv_system_unique;
--rollback ALTER TABLE saved_view ALTER COLUMN user_id SET NOT NULL;
