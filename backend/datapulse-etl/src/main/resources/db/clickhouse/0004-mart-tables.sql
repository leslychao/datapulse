-- mart_posting_pnl: P&L per posting, materialized from facts
CREATE TABLE IF NOT EXISTS mart_posting_pnl (
    posting_id                         String,
    connection_id                      UInt32,
    source_platform                    LowCardinality(String),
    order_id                           Nullable(String),
    seller_sku_id                      Nullable(UInt64),
    product_id                         Nullable(UInt64),
    finance_date                       Date,
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
    quantity                           Nullable(Int32),
    gross_cogs                         Nullable(Decimal(18, 2)),
    refund_ratio                       Nullable(Decimal(18, 4)),
    net_cogs                           Nullable(Decimal(18, 2)),
    cogs_status                        LowCardinality(String),
    reconciliation_residual            Decimal(18, 2),
    ver                                UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(finance_date)
ORDER BY (connection_id, source_platform, posting_id);

-- mart_product_pnl: P&L per product per period
CREATE TABLE IF NOT EXISTS mart_product_pnl (
    connection_id                      UInt32,
    source_platform                    LowCardinality(String),
    seller_sku_id                      UInt64,
    product_id                         UInt64,
    period                             UInt32,
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
    gross_cogs                         Nullable(Decimal(18, 2)),
    product_refund_ratio               Nullable(Decimal(18, 4)),
    net_cogs                           Nullable(Decimal(18, 2)),
    cogs_status                        LowCardinality(String),
    advertising_cost                   Nullable(Decimal(18, 2)),
    marketplace_pnl                    Decimal(18, 2),
    full_pnl                           Nullable(Decimal(18, 2)),
    ver                                UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY period
ORDER BY (connection_id, source_platform, seller_sku_id, period, attribution_level);

-- mart_inventory_analysis: inventory intelligence per product/warehouse/day
CREATE TABLE IF NOT EXISTS mart_inventory_analysis (
    connection_id                UInt32,
    source_platform              LowCardinality(String),
    product_id                   UInt64,
    seller_sku_id                UInt64,
    warehouse_id                 Nullable(UInt32),
    analysis_date                Date,
    available                    Int32,
    reserved                     Nullable(Int32),
    avg_daily_sales_14d          Nullable(Decimal(18, 2)),
    days_of_cover                Nullable(Decimal(18, 1)),
    stock_out_risk               LowCardinality(String),
    cost_price                   Nullable(Decimal(18, 2)),
    frozen_capital               Nullable(Decimal(18, 2)),
    recommended_replenishment    Nullable(Int32),
    ver                          UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(analysis_date)
ORDER BY (connection_id, product_id, warehouse_id, analysis_date);

-- mart_returns_analysis: returns & penalties per product per period
CREATE TABLE IF NOT EXISTS mart_returns_analysis (
    connection_id              UInt32,
    source_platform            LowCardinality(String),
    product_id                 UInt64,
    seller_sku_id              UInt64,
    period                     UInt32,
    return_count               UInt32,
    return_quantity             Int32,
    return_amount              Decimal(18, 2),
    sale_count                 UInt32,
    sale_quantity              Int32,
    return_rate_pct            Nullable(Decimal(18, 2)),
    financial_refund_amount    Decimal(18, 2),
    penalties_amount           Decimal(18, 2),
    top_return_reason          Nullable(String),
    ver                        UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY period
ORDER BY (connection_id, source_platform, seller_sku_id, period);
