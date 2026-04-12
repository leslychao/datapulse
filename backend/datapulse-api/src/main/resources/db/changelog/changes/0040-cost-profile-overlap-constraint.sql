--liquibase formatted sql

--changeset datapulse:0040-cost-profile-overlap-constraint
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE cost_profile
    ADD CONSTRAINT excl_cost_profile_no_overlap
    EXCLUDE USING gist (
        seller_sku_id WITH =,
        daterange(valid_from, valid_to, '[)') WITH &&
    );

--rollback ALTER TABLE cost_profile DROP CONSTRAINT IF EXISTS excl_cost_profile_no_overlap;
