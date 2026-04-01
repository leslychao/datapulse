--liquibase formatted sql

--changeset datapulse:0012-promotions-tables

-- ============================================================
-- Promo policy
-- ============================================================

CREATE TABLE promo_policy (
    id                          bigserial      PRIMARY KEY,
    workspace_id                bigint         NOT NULL,
    name                        varchar(255)   NOT NULL,
    status                      varchar(20)    NOT NULL DEFAULT 'DRAFT',
    participation_mode          varchar(20)    NOT NULL,
    min_margin_pct              decimal        NOT NULL,
    min_stock_days_of_cover     int            NOT NULL DEFAULT 7,
    max_promo_discount_pct      decimal,
    auto_participate_categories jsonb,
    auto_decline_categories     jsonb,
    evaluation_config           jsonb,
    version                     int            NOT NULL DEFAULT 1,
    created_by                  bigint         NOT NULL,
    created_at                  timestamptz    NOT NULL DEFAULT now(),
    updated_at                  timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT fk_promo_policy_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_promo_policy_user      FOREIGN KEY (created_by)   REFERENCES app_user (id)
);

CREATE INDEX idx_promo_policy_workspace_id ON promo_policy (workspace_id);

-- ============================================================
-- Promo policy assignment
-- ============================================================

CREATE TABLE promo_policy_assignment (
    id                          bigserial   PRIMARY KEY,
    promo_policy_id             bigint      NOT NULL,
    marketplace_connection_id   bigint      NOT NULL,
    scope_type                  varchar(20) NOT NULL,
    category_id                 bigint,
    marketplace_offer_id        bigint,
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_promo_assign_policy     FOREIGN KEY (promo_policy_id)           REFERENCES promo_policy (id),
    CONSTRAINT fk_promo_assign_connection FOREIGN KEY (marketplace_connection_id) REFERENCES marketplace_connection (id),
    CONSTRAINT fk_promo_assign_category   FOREIGN KEY (category_id)              REFERENCES category (id),
    CONSTRAINT fk_promo_assign_offer      FOREIGN KEY (marketplace_offer_id)     REFERENCES marketplace_offer (id)
);

CREATE UNIQUE INDEX uq_promo_assign ON promo_policy_assignment (
    promo_policy_id, marketplace_connection_id, scope_type,
    COALESCE(category_id, 0), COALESCE(marketplace_offer_id, 0)
);
CREATE INDEX idx_promo_assign_policy_id     ON promo_policy_assignment (promo_policy_id);
CREATE INDEX idx_promo_assign_connection_id ON promo_policy_assignment (marketplace_connection_id);

-- ============================================================
-- Promo evaluation run
-- ============================================================

CREATE TABLE promo_evaluation_run (
    id                          bigserial      PRIMARY KEY,
    workspace_id                bigint         NOT NULL,
    connection_id               bigint         NOT NULL,
    trigger_type                varchar(30)    NOT NULL,
    source_job_execution_id     bigint,
    status                      varchar(30)    NOT NULL DEFAULT 'PENDING',
    total_products              int,
    eligible_count              int,
    participate_count           int,
    decline_count               int,
    pending_review_count        int,
    deactivate_count            int,
    started_at                  timestamptz,
    completed_at                timestamptz,
    error_details               jsonb,
    created_at                  timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT fk_promo_run_workspace     FOREIGN KEY (workspace_id)            REFERENCES workspace (id),
    CONSTRAINT fk_promo_run_connection    FOREIGN KEY (connection_id)           REFERENCES marketplace_connection (id),
    CONSTRAINT fk_promo_run_job_execution FOREIGN KEY (source_job_execution_id) REFERENCES job_execution (id)
);

CREATE INDEX idx_promo_run_workspace_id  ON promo_evaluation_run (workspace_id);
CREATE INDEX idx_promo_run_connection_id ON promo_evaluation_run (connection_id);
CREATE INDEX idx_promo_run_job_exec_id   ON promo_evaluation_run (source_job_execution_id);

-- ============================================================
-- Promo evaluation
-- ============================================================

CREATE TABLE promo_evaluation (
    id                              bigserial      PRIMARY KEY,
    workspace_id                    bigint         NOT NULL,
    promo_evaluation_run_id         bigint         NOT NULL,
    canonical_promo_product_id      bigint         NOT NULL,
    promo_policy_id                 bigint,
    evaluated_at                    timestamptz,
    current_participation_status    varchar(30)    NOT NULL,
    promo_price                     decimal,
    regular_price                   decimal,
    discount_pct                    decimal,
    cogs                            decimal,
    margin_at_promo_price           decimal,
    margin_at_regular_price         decimal,
    margin_delta_pct                decimal,
    effective_cost_rate             decimal,
    stock_available                 int,
    expected_promo_duration_days    int,
    avg_daily_velocity              decimal,
    stock_days_of_cover             decimal,
    stock_sufficient                boolean,
    evaluation_result               varchar(30),
    signal_snapshot                 jsonb,
    skip_reason                     varchar(255),
    created_at                      timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT fk_promo_eval_workspace     FOREIGN KEY (workspace_id)              REFERENCES workspace (id),
    CONSTRAINT fk_promo_eval_run           FOREIGN KEY (promo_evaluation_run_id)   REFERENCES promo_evaluation_run (id),
    CONSTRAINT fk_promo_eval_promo_product FOREIGN KEY (canonical_promo_product_id) REFERENCES canonical_promo_product (id),
    CONSTRAINT fk_promo_eval_policy        FOREIGN KEY (promo_policy_id)           REFERENCES promo_policy (id)
);

CREATE INDEX idx_promo_eval_run_id           ON promo_evaluation (promo_evaluation_run_id);
CREATE INDEX idx_promo_eval_promo_product_id ON promo_evaluation (canonical_promo_product_id);
CREATE INDEX idx_promo_eval_policy_id        ON promo_evaluation (promo_policy_id);

-- ============================================================
-- Promo decision
-- ============================================================

CREATE TABLE promo_decision (
    id                              bigserial      PRIMARY KEY,
    workspace_id                    bigint         NOT NULL,
    canonical_promo_product_id      bigint         NOT NULL,
    promo_evaluation_id             bigint,
    policy_version                  int            NOT NULL,
    policy_snapshot                 jsonb          NOT NULL,
    decision_type                   varchar(30)    NOT NULL,
    participation_mode              varchar(20)    NOT NULL,
    execution_mode                  varchar(20)    NOT NULL,
    target_promo_price              decimal,
    explanation_summary             text,
    decided_by                      bigint,
    created_at                      timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT fk_promo_dec_workspace     FOREIGN KEY (workspace_id)              REFERENCES workspace (id),
    CONSTRAINT fk_promo_dec_promo_product FOREIGN KEY (canonical_promo_product_id) REFERENCES canonical_promo_product (id),
    CONSTRAINT fk_promo_dec_evaluation    FOREIGN KEY (promo_evaluation_id)       REFERENCES promo_evaluation (id),
    CONSTRAINT fk_promo_dec_user          FOREIGN KEY (decided_by)               REFERENCES app_user (id)
);

CREATE INDEX idx_promo_dec_promo_product_id ON promo_decision (canonical_promo_product_id);
CREATE INDEX idx_promo_dec_evaluation_id    ON promo_decision (promo_evaluation_id);

-- ============================================================
-- Promo action
-- ============================================================

CREATE TABLE promo_action (
    id                              bigserial      PRIMARY KEY,
    workspace_id                    bigint         NOT NULL,
    promo_decision_id               bigint         NOT NULL,
    canonical_promo_campaign_id     bigint         NOT NULL,
    marketplace_offer_id            bigint         NOT NULL,
    action_type                     varchar(20)    NOT NULL,
    target_promo_price              decimal,
    status                          varchar(30)    NOT NULL,
    attempt_count                   int            NOT NULL DEFAULT 0,
    last_error                      text,
    execution_mode                  varchar(20)    NOT NULL,
    freeze_at_snapshot              timestamptz,
    cancel_reason                   text,
    created_at                      timestamptz    NOT NULL DEFAULT now(),
    updated_at                      timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT fk_promo_action_workspace FOREIGN KEY (workspace_id)              REFERENCES workspace (id),
    CONSTRAINT fk_promo_action_decision  FOREIGN KEY (promo_decision_id)         REFERENCES promo_decision (id),
    CONSTRAINT fk_promo_action_campaign  FOREIGN KEY (canonical_promo_campaign_id) REFERENCES canonical_promo_campaign (id),
    CONSTRAINT fk_promo_action_offer     FOREIGN KEY (marketplace_offer_id)      REFERENCES marketplace_offer (id)
);

CREATE INDEX idx_promo_action_decision_id ON promo_action (promo_decision_id);
CREATE INDEX idx_promo_action_campaign_id ON promo_action (canonical_promo_campaign_id);
CREATE INDEX idx_promo_action_offer_id    ON promo_action (marketplace_offer_id);
CREATE INDEX idx_promo_action_status      ON promo_action (status);

CREATE UNIQUE INDEX idx_promo_action_active_live
    ON promo_action (canonical_promo_campaign_id, marketplace_offer_id)
    WHERE execution_mode = 'LIVE'
      AND status NOT IN ('SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED');

CREATE UNIQUE INDEX idx_promo_action_active_simulated
    ON promo_action (canonical_promo_campaign_id, marketplace_offer_id)
    WHERE execution_mode = 'SIMULATED'
      AND status NOT IN ('SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED');

-- ============================================================
-- Promo action attempt
-- ============================================================

CREATE TABLE promo_action_attempt (
    id                          bigserial      PRIMARY KEY,
    promo_action_id             bigint         NOT NULL,
    attempt_number              int            NOT NULL,
    started_at                  timestamptz    NOT NULL,
    completed_at                timestamptz,
    outcome                     varchar(30),
    error_message               text,
    provider_request_summary    jsonb,
    provider_response_summary   jsonb,
    created_at                  timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT fk_promo_attempt_action FOREIGN KEY (promo_action_id) REFERENCES promo_action (id)
);

CREATE INDEX idx_promo_attempt_action_id ON promo_action_attempt (promo_action_id);

--rollback DROP TABLE promo_action_attempt;
--rollback DROP TABLE promo_action;
--rollback DROP TABLE promo_decision;
--rollback DROP TABLE promo_evaluation;
--rollback DROP TABLE promo_evaluation_run;
--rollback DROP TABLE promo_policy_assignment;
--rollback DROP TABLE promo_policy;
