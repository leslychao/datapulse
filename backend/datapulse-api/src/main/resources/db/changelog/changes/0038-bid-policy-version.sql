--liquibase formatted sql

--changeset datapulse:0038-bid-policy-version
ALTER TABLE bid_policy ADD COLUMN version bigint NOT NULL DEFAULT 0;

COMMENT ON COLUMN bid_policy.version IS 'Optimistic locking version for concurrent edit protection';

--rollback ALTER TABLE bid_policy DROP COLUMN version;
