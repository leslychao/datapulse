--liquibase formatted sql

--changeset datapulse:0021-job-execution-materializing-at
ALTER TABLE job_execution
    ADD COLUMN materializing_at timestamptz NULL;

COMMENT ON COLUMN job_execution.materializing_at IS
    'Set when status becomes MATERIALIZING; cleared on terminal transition. Used for stale detection independent of jobTimeout (async post-ingest phase).';

--rollback ALTER TABLE job_execution DROP COLUMN IF EXISTS materializing_at;
