--liquibase formatted sql

--changeset datapulse:0011-execution-tables

-- ── price_action ──────────────────────────────────────────────────────

CREATE TABLE price_action (
    id                          BIGSERIAL       PRIMARY KEY,
    workspace_id                BIGINT          NOT NULL,
    marketplace_offer_id        BIGINT          NOT NULL,
    price_decision_id           BIGINT          NOT NULL,
    execution_mode              VARCHAR(20)     NOT NULL,
    status                      VARCHAR(30)     NOT NULL,
    target_price                DECIMAL         NOT NULL,
    current_price_at_creation   DECIMAL         NOT NULL,
    approved_by_user_id         BIGINT,
    approved_at                 TIMESTAMPTZ,
    hold_reason                 TEXT,
    cancel_reason               TEXT,
    superseded_by_action_id     BIGINT,
    reconciliation_source       VARCHAR(10),
    manual_override_reason      TEXT,
    attempt_count               INT             NOT NULL DEFAULT 0,
    max_attempts                INT             NOT NULL DEFAULT 3,
    approval_timeout_hours      INT             NOT NULL DEFAULT 72,
    next_attempt_at             TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT fk_pa_workspace          FOREIGN KEY (workspace_id)          REFERENCES workspace            (id),
    CONSTRAINT fk_pa_offer              FOREIGN KEY (marketplace_offer_id)  REFERENCES marketplace_offer    (id),
    CONSTRAINT fk_pa_decision           FOREIGN KEY (price_decision_id)     REFERENCES price_decision       (id),
    CONSTRAINT fk_pa_approved_by        FOREIGN KEY (approved_by_user_id)   REFERENCES app_user             (id),
    CONSTRAINT fk_pa_superseded_by      FOREIGN KEY (superseded_by_action_id) REFERENCES price_action       (id)
);

CREATE INDEX idx_pa_workspace_status    ON price_action (workspace_id, status);
CREATE INDEX idx_pa_workspace_created   ON price_action (workspace_id, created_at DESC);
CREATE INDEX idx_pa_offer_status        ON price_action (marketplace_offer_id, status);
CREATE INDEX idx_pa_decision            ON price_action (price_decision_id);
CREATE INDEX idx_pa_next_attempt        ON price_action (next_attempt_at) WHERE next_attempt_at IS NOT NULL AND status = 'RETRY_SCHEDULED';
CREATE INDEX idx_pa_stuck_state         ON price_action (status, updated_at)
    WHERE status IN ('EXECUTING', 'RETRY_SCHEDULED', 'RECONCILIATION_PENDING', 'SCHEDULED');

CREATE UNIQUE INDEX idx_price_action_active_offer_live
    ON price_action (marketplace_offer_id)
    WHERE execution_mode = 'LIVE'
      AND status NOT IN ('SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED', 'SUPERSEDED');

CREATE UNIQUE INDEX idx_price_action_active_offer_simulated
    ON price_action (marketplace_offer_id)
    WHERE execution_mode = 'SIMULATED'
      AND status NOT IN ('SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED', 'SUPERSEDED');

-- ── price_action_attempt ──────────────────────────────────────────────

CREATE TABLE price_action_attempt (
    id                          BIGSERIAL       PRIMARY KEY,
    price_action_id             BIGINT          NOT NULL,
    attempt_number              INT             NOT NULL,
    started_at                  TIMESTAMPTZ     NOT NULL,
    completed_at                TIMESTAMPTZ,
    outcome                     VARCHAR(30),
    error_classification        VARCHAR(30),
    error_message               TEXT,
    actor_user_id               BIGINT,
    provider_request_summary    JSONB,
    provider_response_summary   JSONB,
    reconciliation_source       VARCHAR(20),
    reconciliation_read_at      TIMESTAMPTZ,
    reconciliation_snapshot     JSONB,
    actual_price                DECIMAL,
    price_match                 BOOLEAN,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT fk_paa_action        FOREIGN KEY (price_action_id)   REFERENCES price_action (id),
    CONSTRAINT fk_paa_actor         FOREIGN KEY (actor_user_id)     REFERENCES app_user     (id)
);

CREATE INDEX idx_paa_action ON price_action_attempt (price_action_id, attempt_number);

-- ── deferred_action ───────────────────────────────────────────────────

CREATE TABLE deferred_action (
    id                          BIGSERIAL       PRIMARY KEY,
    workspace_id                BIGINT          NOT NULL,
    marketplace_offer_id        BIGINT          NOT NULL,
    price_decision_id           BIGINT          NOT NULL,
    execution_mode              VARCHAR(20)     NOT NULL,
    deferred_reason             TEXT            NOT NULL,
    expires_at                  TIMESTAMPTZ     NOT NULL,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT fk_da_workspace  FOREIGN KEY (workspace_id)          REFERENCES workspace         (id),
    CONSTRAINT fk_da_offer      FOREIGN KEY (marketplace_offer_id)  REFERENCES marketplace_offer (id),
    CONSTRAINT fk_da_decision   FOREIGN KEY (price_decision_id)     REFERENCES price_decision    (id),

    CONSTRAINT uq_da_offer_mode UNIQUE (marketplace_offer_id, execution_mode)
);

CREATE INDEX idx_da_expires ON deferred_action (expires_at);

-- ── simulated_offer_state ─────────────────────────────────────────────

CREATE TABLE simulated_offer_state (
    id                              BIGSERIAL   PRIMARY KEY,
    workspace_id                    BIGINT      NOT NULL,
    marketplace_offer_id            BIGINT      NOT NULL,
    simulated_price                 DECIMAL     NOT NULL,
    simulated_at                    TIMESTAMPTZ NOT NULL,
    price_action_id                 BIGINT      NOT NULL,
    previous_simulated_price        DECIMAL,
    canonical_price_at_simulation   DECIMAL     NOT NULL,
    price_delta                     DECIMAL,
    price_delta_pct                 DECIMAL,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_sos_workspace FOREIGN KEY (workspace_id)          REFERENCES workspace         (id),
    CONSTRAINT fk_sos_offer     FOREIGN KEY (marketplace_offer_id)  REFERENCES marketplace_offer (id),
    CONSTRAINT fk_sos_action    FOREIGN KEY (price_action_id)       REFERENCES price_action      (id),

    CONSTRAINT uq_sos_offer     UNIQUE (workspace_id, marketplace_offer_id)
);

--rollback DROP TABLE simulated_offer_state;
--rollback DROP TABLE deferred_action;
--rollback DROP TABLE price_action_attempt;
--rollback DROP TABLE price_action;
