--liquibase formatted sql

--changeset datapulse:0024-finance-fulfillment-type
ALTER TABLE canonical_finance_entry
    ADD COLUMN fulfillment_type varchar(10);

COMMENT ON COLUMN canonical_finance_entry.fulfillment_type
    IS 'Delivery schema: FBO, FBS (Ozon), FBW, DBS (WB). NULL for non-order operations.';

--rollback ALTER TABLE canonical_finance_entry DROP COLUMN fulfillment_type;

--changeset datapulse:0024-sale-fulfillment-type
ALTER TABLE canonical_sale
    ADD COLUMN fulfillment_type varchar(10);

--rollback ALTER TABLE canonical_sale DROP COLUMN fulfillment_type;
