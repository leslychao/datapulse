-- mart_advertising_product: aggregated ad metrics per product per period (Phase A-4)
-- Source: fact_advertising (spend, views, clicks, orders, revenue) + fact_finance (total_revenue for DRR)
CREATE TABLE IF NOT EXISTS mart_advertising_product (
    connection_id    UInt32,
    source_platform  LowCardinality(String),
    marketplace_sku  String,
    period           UInt32,                    -- YYYYMM
    spend            Decimal(18, 2),
    impressions      UInt64,
    clicks           UInt64,
    ad_orders        UInt32,
    ad_revenue       Decimal(18, 2),
    total_revenue    Decimal(18, 2),            -- from fact_finance
    drr_pct          Nullable(Decimal(8, 2)),   -- spend / total_revenue * 100
    cpo              Nullable(Decimal(18, 2)),  -- spend / ad_orders
    roas             Nullable(Decimal(8, 2)),   -- ad_revenue / spend
    cpc              Nullable(Decimal(18, 2)),  -- spend / clicks
    ctr_pct          Nullable(Decimal(8, 4)),   -- clicks / impressions * 100
    cr_pct           Nullable(Decimal(8, 4)),   -- ad_orders / clicks * 100
    ver              UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYear(toDate(toString(period) || '01', 'yyyyMMdd'))
ORDER BY (connection_id, source_platform, marketplace_sku, period);
