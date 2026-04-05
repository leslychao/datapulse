--liquibase formatted sql

--changeset datapulse:0020-job-execution-params

ALTER TABLE job_execution
    ADD COLUMN IF NOT EXISTS params jsonb;

COMMENT ON COLUMN job_execution.params IS
    'Optional JSON: domains (EtlEventType names), sourceJobId, trigger, etc.';

--rollback ALTER TABLE job_execution DROP COLUMN IF EXISTS params;
