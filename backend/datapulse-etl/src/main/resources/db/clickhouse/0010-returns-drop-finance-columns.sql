-- Remove financial columns (financial_refund_amount, penalties_amount) from mart_returns_analysis.
-- Returns section is purely operational: fact_returns + fact_sales only.
-- Financial impact (refund, penalties) lives exclusively in P&L marts.

DROP TABLE IF EXISTS mart_returns_analysis;
CREATE TABLE mart_returns_analysis (
    workspace_id               UInt32,
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
    top_return_reason          Nullable(String),
    ver                        UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY period
ORDER BY (workspace_id, source_platform, seller_sku_id, period);
