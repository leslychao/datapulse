--liquibase formatted sql

--changeset datapulse:0035-bid-decision-pause-reason
ALTER TABLE bid_decision
    ADD COLUMN pause_reason_code varchar(30);

COMMENT ON COLUMN bid_decision.pause_reason_code IS 'Structured reason for PAUSE decisions: STOCK_OUT, NEGATIVE_MARGIN, DRR_CRITICAL, GUARD_BLOCK';

--rollback ALTER TABLE bid_decision DROP COLUMN pause_reason_code;
