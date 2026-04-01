--liquibase formatted sql

--changeset datapulse:0017-wqd-add-is-system
ALTER TABLE working_queue_definition
    ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT false;

--rollback ALTER TABLE working_queue_definition DROP COLUMN is_system;
