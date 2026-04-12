CREATE TABLE IF NOT EXISTS materialization_watermark (
    table_name            String,
    last_materialized_at  DateTime64(3, 'UTC'),
    ver                   UInt64
) ENGINE = ReplacingMergeTree(ver)
ORDER BY table_name;
