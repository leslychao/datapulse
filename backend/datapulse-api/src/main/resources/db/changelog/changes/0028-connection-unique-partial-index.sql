--liquibase formatted sql

--changeset datapulse:0028-connection-unique-partial-index
ALTER TABLE datapulse.marketplace_connection
    DROP CONSTRAINT uq_connection_workspace_type_account;

CREATE UNIQUE INDEX uq_connection_workspace_type_account
    ON datapulse.marketplace_connection (workspace_id, marketplace_type, external_account_id)
    WHERE status <> 'ARCHIVED';

--rollback DROP INDEX datapulse.uq_connection_workspace_type_account;
--rollback ALTER TABLE datapulse.marketplace_connection ADD CONSTRAINT uq_connection_workspace_type_account UNIQUE (workspace_id, marketplace_type, external_account_id);
