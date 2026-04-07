-- Migrate ClickHouse tables to workspace-scoped ORDER BY keys.
-- connection_id stays for provenance but is no longer part of the dedup key.
-- Data is re-materialized from PostgreSQL after migration.

-- === fact tables ===

DROP TABLE IF EXISTS fact_finance;
CREATE TABLE fact_finance (
    workspace_id                       UInt32,
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
    fulfillment_type                   LowCardinality(Nullable(String)),
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
ORDER BY (workspace_id, source_platform, entry_id);

DROP TABLE IF EXISTS fact_sales;
CREATE TABLE fact_sales (
    workspace_id    UInt32,
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
    fulfillment_type LowCardinality(Nullable(String)),
    job_execution_id UInt64,
    ver             UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(sale_date)
ORDER BY (workspace_id, source_platform, sale_id);

DROP TABLE IF EXISTS fact_orders;
CREATE TABLE fact_orders (
    workspace_id     UInt32,
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
ORDER BY (workspace_id, source_platform, order_id_pk);

DROP TABLE IF EXISTS fact_returns;
CREATE TABLE fact_returns (
    workspace_id      UInt32,
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
    fulfillment_type  LowCardinality(Nullable(String)),
    job_execution_id  UInt64,
    ver               UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(return_date)
ORDER BY (workspace_id, source_platform, return_id);

DROP TABLE IF EXISTS fact_price_snapshot;
CREATE TABLE fact_price_snapshot (
    workspace_id    UInt32,
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
ORDER BY (workspace_id, product_id, captured_at);

DROP TABLE IF EXISTS fact_inventory_snapshot;
CREATE TABLE fact_inventory_snapshot (
    workspace_id    UInt32,
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
ORDER BY (workspace_id, product_id, warehouse_id, captured_at);

-- === dim tables ===

DROP TABLE IF EXISTS dim_product;
CREATE TABLE dim_product (
    workspace_id      UInt32,
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
ORDER BY (workspace_id, source_platform, product_id);

DROP TABLE IF EXISTS dim_category;
CREATE TABLE dim_category (
    workspace_id         UInt32,
    category_id          UInt64,
    connection_id        UInt32,
    external_category_id String,
    name                 String,
    parent_category_id   Nullable(UInt64),
    marketplace_type     LowCardinality(String),
    ver                  UInt64
) ENGINE = ReplacingMergeTree(ver)
ORDER BY (workspace_id, marketplace_type, category_id);

DROP TABLE IF EXISTS dim_advertising_campaign;
CREATE TABLE dim_advertising_campaign (
    workspace_id    UInt32,
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
ORDER BY (workspace_id, source_platform, campaign_id);

DROP TABLE IF EXISTS fact_advertising;
CREATE TABLE fact_advertising (
    workspace_id     UInt32,
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
ORDER BY (workspace_id, source_platform, campaign_id, ad_date, marketplace_sku);

-- === mart tables ===

DROP TABLE IF EXISTS mart_posting_pnl;
CREATE TABLE mart_posting_pnl (
    workspace_id                       UInt32,
    posting_id                         String,
    connection_id                      UInt32,
    source_platform                    LowCardinality(String),
    fulfillment_type                   LowCardinality(Nullable(String)),
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
    sku_code                           Nullable(String),
    product_name                       Nullable(String),
    marketplace_sku                    Nullable(String),
    ver                                UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYYYYMM(finance_date)
ORDER BY (workspace_id, source_platform, posting_id);

DROP TABLE IF EXISTS mart_product_pnl;
CREATE TABLE mart_product_pnl (
    workspace_id                       UInt32,
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
    sku_code                           Nullable(String),
    product_name                       Nullable(String),
    marketplace_sku                    Nullable(String),
    ver                                UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY period
ORDER BY (workspace_id, source_platform, seller_sku_id, period, attribution_level);

DROP TABLE IF EXISTS mart_inventory_analysis;
CREATE TABLE mart_inventory_analysis (
    workspace_id                 UInt32,
    connection_id                UInt32,
    source_platform              LowCardinality(String),
    product_id                   UInt64,
    seller_sku_id                UInt64,
    warehouse_id                 UInt32 DEFAULT 0,
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
ORDER BY (workspace_id, product_id, warehouse_id, analysis_date);

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
    financial_refund_amount    Decimal(18, 2),
    penalties_amount           Decimal(18, 2),
    top_return_reason          Nullable(String),
    ver                        UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY period
ORDER BY (workspace_id, source_platform, seller_sku_id, period);

DROP TABLE IF EXISTS mart_advertising_product;
CREATE TABLE mart_advertising_product (
    workspace_id     UInt32,
    connection_id    UInt32,
    source_platform  LowCardinality(String),
    marketplace_sku  String,
    period           UInt32,
    spend            Decimal(18, 2),
    impressions      UInt64,
    clicks           UInt64,
    ad_orders        UInt32,
    ad_revenue       Decimal(18, 2),
    total_revenue    Decimal(18, 2),
    drr_pct          Nullable(Decimal(8, 2)),
    cpo              Nullable(Decimal(18, 2)),
    roas             Nullable(Decimal(8, 2)),
    cpc              Nullable(Decimal(18, 2)),
    ctr_pct          Nullable(Decimal(8, 4)),
    cr_pct           Nullable(Decimal(8, 4)),
    ver              UInt64
) ENGINE = ReplacingMergeTree(ver)
PARTITION BY toYear(toDate(toString(period) || '01', 'yyyyMMdd'))
ORDER BY (workspace_id, source_platform, marketplace_sku, period);
