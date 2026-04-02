--liquibase formatted sql

--changeset datapulse:0019-add-last-preview-version
ALTER TABLE price_policy ADD COLUMN IF NOT EXISTS last_preview_version integer NOT NULL DEFAULT 0;

--rollback ALTER TABLE price_policy DROP COLUMN IF EXISTS last_preview_version;

--changeset datapulse:0019-add-execution-mode-changed-at
ALTER TABLE price_policy ADD COLUMN IF NOT EXISTS execution_mode_changed_at timestamptz DEFAULT now();
UPDATE price_policy SET execution_mode_changed_at = created_at WHERE execution_mode_changed_at IS NULL;

--rollback ALTER TABLE price_policy DROP COLUMN IF EXISTS execution_mode_changed_at;
