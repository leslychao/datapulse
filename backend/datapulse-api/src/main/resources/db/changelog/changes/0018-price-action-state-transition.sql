--liquibase formatted sql

--changeset datapulse:0018-price-action-state-transition

CREATE TABLE price_action_state_transition (
    id              bigserial       PRIMARY KEY,
    price_action_id bigint          NOT NULL,
    from_status     varchar(30)     NOT NULL,
    to_status       varchar(30)     NOT NULL,
    actor_user_id   bigint,
    reason          text,
    created_at      timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT fk_past_action FOREIGN KEY (price_action_id) REFERENCES price_action (id),
    CONSTRAINT fk_past_actor  FOREIGN KEY (actor_user_id)   REFERENCES app_user     (id)
);

CREATE INDEX idx_past_action ON price_action_state_transition (price_action_id, created_at);

--rollback DROP TABLE price_action_state_transition;
