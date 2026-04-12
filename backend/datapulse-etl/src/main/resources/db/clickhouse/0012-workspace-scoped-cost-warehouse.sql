-- Fix workspace-scoping gap for dim_warehouse and fact_product_cost.
-- Both tables were missed in 0009-workspace-scoped-keys.sql.
-- Full rematerialization required after this migration.

-- === dim_warehouse ===

DROP TABLE IF EXISTS dim_warehouse;
CREATE TABLE dim_warehouse (
    workspace_id          UInt64,
    warehouse_id          UInt32,
    external_warehouse_id String,
    name                  String,
    warehouse_type        LowCardinality(String),
    marketplace_type      LowCardinality(String),
    ver                   UInt64
) ENGINE = ReplacingMergeTree(ver)
ORDER BY (workspace_id, marketplace_type, warehouse_id);

-- === fact_product_cost ===

DROP TABLE IF EXISTS fact_product_cost;
CREATE TABLE fact_product_cost (
    workspace_id  UInt64,
    cost_id       UInt64,
    seller_sku_id UInt64,
    cost_price    Decimal(18, 2),
    currency      LowCardinality(String),
    valid_from    Date,
    valid_to      Nullable(Date),
    ver           UInt64
) ENGINE = ReplacingMergeTree(ver)
ORDER BY (workspace_id, seller_sku_id, valid_from);
