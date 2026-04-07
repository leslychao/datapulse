--liquibase formatted sql

--changeset datapulse:0027-return-fulfillment-type
ALTER TABLE canonical_return
    ADD COLUMN fulfillment_type varchar(10);

COMMENT ON COLUMN canonical_return.fulfillment_type
    IS 'Delivery schema: FBO, FBS (Ozon), FBW, DBS (WB). NULL for Ozon returns (resolved via canonical_order JOIN).';

--rollback ALTER TABLE canonical_return DROP COLUMN fulfillment_type;
