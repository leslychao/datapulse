-- Add denormalized product labels to mart tables.
-- Materializers resolve these from dim_product at materialization time,
-- so read queries use only marts without joining dims.

ALTER TABLE mart_posting_pnl
    ADD COLUMN IF NOT EXISTS sku_code Nullable(String) AFTER reconciliation_residual;
ALTER TABLE mart_posting_pnl
    ADD COLUMN IF NOT EXISTS product_name Nullable(String) AFTER sku_code;
ALTER TABLE mart_posting_pnl
    ADD COLUMN IF NOT EXISTS marketplace_sku Nullable(String) AFTER product_name;

ALTER TABLE mart_product_pnl
    ADD COLUMN IF NOT EXISTS sku_code Nullable(String) AFTER full_pnl;
ALTER TABLE mart_product_pnl
    ADD COLUMN IF NOT EXISTS product_name Nullable(String) AFTER sku_code;
ALTER TABLE mart_product_pnl
    ADD COLUMN IF NOT EXISTS marketplace_sku Nullable(String) AFTER product_name;
