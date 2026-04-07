-- WB backfill: set fulfillment_type = 'FBW' for existing WB returns
-- Safe: WB returns API returns only FBW returns
-- Run AFTER migration 0027-return-fulfillment-type.sql is applied
-- Run BEFORE triggering full materialization

UPDATE canonical_return
SET fulfillment_type = 'FBW', updated_at = now()
WHERE source_platform = 'wb'
  AND fulfillment_type IS NULL;

-- Ozon returns: remain NULL (unified API doesn't expose delivery_schema)
-- Will be resolved when canonical_order_id linkage is implemented

-- After this SQL, trigger full materialization for fact_returns:
-- This will re-populate ClickHouse with correct fulfillment_type values
