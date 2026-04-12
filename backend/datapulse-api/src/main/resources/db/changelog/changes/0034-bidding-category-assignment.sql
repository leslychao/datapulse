--liquibase formatted sql

--changeset datapulse:0034-bidding-category-assignment
ALTER TABLE bid_policy_assignment ADD COLUMN category_id bigint;

CREATE INDEX idx_bid_policy_assignment_category
    ON bid_policy_assignment (bid_policy_id, category_id)
    WHERE category_id IS NOT NULL;

--rollback DROP INDEX idx_bid_policy_assignment_category;
--rollback ALTER TABLE bid_policy_assignment DROP COLUMN category_id;
