--liquibase formatted sql

--changeset datapulse:0016-audit-log-workspace-nullable

ALTER TABLE audit_log ALTER COLUMN workspace_id DROP NOT NULL;
ALTER TABLE audit_log DROP CONSTRAINT fk_audit_log_workspace;
ALTER TABLE audit_log
    ADD CONSTRAINT fk_audit_log_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id);

--rollback ALTER TABLE audit_log DROP CONSTRAINT fk_audit_log_workspace;
--rollback ALTER TABLE audit_log ADD CONSTRAINT fk_audit_log_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id);
--rollback ALTER TABLE audit_log ALTER COLUMN workspace_id SET NOT NULL;
