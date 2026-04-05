-- Add fulfillment_type to fact and mart tables for FBO/FBS/FBW/DBS breakdown

ALTER TABLE fact_finance
    ADD COLUMN IF NOT EXISTS fulfillment_type LowCardinality(Nullable(String))
    AFTER attribution_level;

ALTER TABLE fact_sales
    ADD COLUMN IF NOT EXISTS fulfillment_type LowCardinality(Nullable(String))
    AFTER source_platform;

ALTER TABLE fact_returns
    ADD COLUMN IF NOT EXISTS fulfillment_type LowCardinality(Nullable(String))
    AFTER source_platform;

ALTER TABLE mart_posting_pnl
    ADD COLUMN IF NOT EXISTS fulfillment_type LowCardinality(Nullable(String))
    AFTER source_platform;
