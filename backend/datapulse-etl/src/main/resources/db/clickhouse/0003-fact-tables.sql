-- fact_finance: consolidated financial fact, one row per canonical_finance_entry
CREATE TABLE IF NOT EXISTS fact_finance (
    connection_id                      UInt32,
    source_platform                    LowCardinality(String),
    entry_id                           UInt64,
    posting_id                         Nullable(String),
    order_id                           Nullable(String),
    seller_sku_id                      Nullable(UInt64),
    warehouse_id                       Nullable(UInt32),
    finance_date                       Date,
    entry_type                         LowCardinality(String),
    attribution_level                  LowCardinality(String),
    revenue_amount                     Decimal(18, 2),
    marketplace_commission_amount      Decimal(18, 2),
    acquiring_commission_amount        Decimal(18, 2),
    logistics_cost_amount              Decimal(18, 2),
    storage_cost_amount                Decimal(18, 2),
    penalties_amount                   Decimal(18, 2),
    marketing_cost_amount              Decimal(18, 2),
    acceptance_cost_amount             Decimal(18, 2),
    other_marketplace_charges_amount   Decimal(18, 2),
    compensation_amount                Decimal(18, 2),
    refund_amount                      Decimal(18, 2),
    net_payout                         Decimal(18, 2),
    job_execution_id                   UInt64,
    ver                                UInt64,
    materialized_at                    DateTime
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(finance_date)
ORDER BY (connection_id, source_platform, entry_id);

-- fact_sales: one row per canonical_sale
CREATE TABLE IF NOT EXISTS fact_sales (
    sale_id         UInt64,
    connection_id   UInt32,
    source_platform LowCardinality(String),
    posting_id      Nullable(String),
    order_id        Nullable(String),
    seller_sku_id   Nullable(UInt64),
    product_id      Nullable(UInt64),
    quantity        Int32,
    sale_amount     Decimal(18, 2),
    sale_date       Date,
    job_execution_id UInt64,
    ver             UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(sale_date)
ORDER BY (connection_id, source_platform, sale_id);

-- fact_orders: one row per canonical_order
CREATE TABLE IF NOT EXISTS fact_orders (
    order_id_pk      UInt64,
    connection_id    UInt32,
    source_platform  LowCardinality(String),
    external_order_id String,
    seller_sku_id    Nullable(UInt64),
    product_id       Nullable(UInt64),
    quantity         Int32,
    price_per_unit   Decimal(18, 2),
    total_amount     Decimal(18, 2),
    order_date       Date,
    status           LowCardinality(String),
    fulfillment_type LowCardinality(Nullable(String)),
    region           Nullable(String),
    job_execution_id UInt64,
    ver              UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(order_date)
ORDER BY (connection_id, source_platform, order_id_pk);

-- fact_returns: one row per canonical_return
CREATE TABLE IF NOT EXISTS fact_returns (
    return_id         UInt64,
    connection_id     UInt32,
    source_platform   LowCardinality(String),
    external_return_id String,
    seller_sku_id     Nullable(UInt64),
    product_id        Nullable(UInt64),
    quantity          Int32,
    return_amount     Nullable(Decimal(18, 2)),
    return_reason     LowCardinality(Nullable(String)),
    return_date       Date,
    job_execution_id  UInt64,
    ver               UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(return_date)
ORDER BY (connection_id, source_platform, return_id);

-- fact_price_snapshot: one row per price observation
CREATE TABLE IF NOT EXISTS fact_price_snapshot (
    connection_id   UInt32,
    source_platform LowCardinality(String),
    product_id      UInt64,
    price           Decimal(18, 2),
    discount_price  Nullable(Decimal(18, 2)),
    currency        LowCardinality(String),
    captured_at     DateTime,
    captured_date   Date,
    ver             UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(captured_date)
ORDER BY (connection_id, product_id, captured_at);

-- fact_inventory_snapshot: one row per stock observation
CREATE TABLE IF NOT EXISTS fact_inventory_snapshot (
    connection_id   UInt32,
    source_platform LowCardinality(String),
    product_id      UInt64,
    warehouse_id    UInt32,
    available       Int32,
    reserved        Nullable(Int32),
    captured_at     DateTime,
    captured_date   Date,
    ver             UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(captured_date)
ORDER BY (connection_id, product_id, warehouse_id, captured_at);

-- fact_product_cost: SCD2, one row per cost_profile validity period
CREATE TABLE IF NOT EXISTS fact_product_cost (
    cost_id       UInt64,
    seller_sku_id UInt64,
    cost_price    Decimal(18, 2),
    currency      LowCardinality(String),
    valid_from    Date,
    valid_to      Nullable(Date),
    ver           UInt64
) ENGINE = ReplacingMergeTree(ver)
ORDER BY (seller_sku_id, valid_from);
