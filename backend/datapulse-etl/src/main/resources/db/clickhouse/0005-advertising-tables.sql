-- dim_advertising_campaign: one row per advertising campaign (Phase B extended)
CREATE TABLE IF NOT EXISTS dim_advertising_campaign (
    connection_id   UInt32,
    source_platform LowCardinality(String),
    campaign_id     UInt64,
    name            String,
    campaign_type   LowCardinality(String),
    status          LowCardinality(String),
    placement       Nullable(String),
    daily_budget    Nullable(Decimal(18, 2)),
    start_time      Nullable(DateTime),
    end_time        Nullable(DateTime),
    created_at      Nullable(DateTime),
    ver             UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY source_platform
ORDER BY (connection_id, source_platform, campaign_id);

-- fact_advertising: ad statistics per campaign/product/day (Phase B extended)
-- Pipeline exception: Raw → ClickHouse (no canonical PostgreSQL entity, DD-AD-1)
CREATE TABLE IF NOT EXISTS fact_advertising (
    connection_id    UInt32,
    source_platform  LowCardinality(String),
    campaign_id      UInt64,
    ad_date          Date,
    marketplace_sku  String,
    views            UInt64,
    clicks           UInt64,
    spend            Decimal(18, 2),
    orders           UInt32,
    ordered_units    UInt32,
    ordered_revenue  Decimal(18, 2),
    canceled         UInt32,
    ctr              Float32,
    cpc              Decimal(18, 2),
    cr               Float32,
    job_execution_id UInt64,
    ver              UInt64,
    materialized_at  DateTime
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(ad_date)
ORDER BY (connection_id, source_platform, campaign_id, ad_date, marketplace_sku);
