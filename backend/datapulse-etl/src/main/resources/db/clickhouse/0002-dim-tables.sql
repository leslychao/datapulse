-- dim_product: one row per marketplace_offer
CREATE TABLE IF NOT EXISTS dim_product (
    product_id        UInt64,
    connection_id     UInt32,
    source_platform   LowCardinality(String),
    seller_sku_id     UInt64,
    product_master_id UInt64,
    sku_code          String,
    marketplace_sku   String,
    product_name      String,
    brand             Nullable(String),
    category          Nullable(String),
    status            LowCardinality(String),
    ver               UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY source_platform
ORDER BY (connection_id, product_id);

-- dim_warehouse: one row per warehouse
CREATE TABLE IF NOT EXISTS dim_warehouse (
    warehouse_id          UInt32,
    external_warehouse_id String,
    name                  String,
    warehouse_type        LowCardinality(String),
    marketplace_type      LowCardinality(String),
    ver                   UInt64
) ENGINE = ReplacingMergeTree(ver)
ORDER BY (marketplace_type, warehouse_id);

-- dim_category: one row per category
CREATE TABLE IF NOT EXISTS dim_category (
    category_id          UInt64,
    connection_id        UInt32,
    external_category_id String,
    name                 String,
    parent_category_id   Nullable(UInt64),
    marketplace_type     LowCardinality(String),
    ver                  UInt64
) ENGINE = ReplacingMergeTree(ver)
ORDER BY (marketplace_type, category_id);
