--liquibase formatted sql

--changeset datapulse:0036-bid-decision-explanation-i18n
ALTER TABLE bid_decision
    ADD COLUMN explanation_key varchar(100),
    ADD COLUMN explanation_args jsonb;

COMMENT ON COLUMN bid_decision.explanation_key IS 'i18n message key for structured explanation (e.g. bidding.strategy.economy_hold.bid_down)';
COMMENT ON COLUMN bid_decision.explanation_args IS 'Interpolation arguments for explanation_key as JSON object';

--rollback ALTER TABLE bid_decision DROP COLUMN explanation_key, DROP COLUMN explanation_args;
