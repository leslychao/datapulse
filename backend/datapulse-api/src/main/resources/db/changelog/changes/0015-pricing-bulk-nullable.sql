--liquibase formatted sql

--changeset datapulse:0015-pricing-bulk-nullable

ALTER TABLE price_decision ALTER COLUMN price_policy_id DROP NOT NULL;
ALTER TABLE price_decision ALTER COLUMN policy_snapshot DROP NOT NULL;

--rollback ALTER TABLE price_decision ALTER COLUMN price_policy_id SET NOT NULL;
--rollback ALTER TABLE price_decision ALTER COLUMN policy_snapshot SET NOT NULL;
