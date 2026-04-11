-- Add distinct_reason_count to mart_returns_analysis.
-- Tracks how many different return reasons each product has per period.

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
    distinct_reason_count      UInt32,
    ver                        UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY period
ORDER BY (workspace_id, source_platform, seller_sku_id, period);
