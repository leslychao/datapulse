--liquibase formatted sql

--changeset datapulse:0010-pricing-tables

-- ── price_policy ────────────────────────────────────────────────────────

CREATE TABLE price_policy (
    id                      BIGSERIAL       PRIMARY KEY,
    workspace_id            BIGINT          NOT NULL,
    name                    VARCHAR(255)    NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    strategy_type           VARCHAR(30)     NOT NULL,
    strategy_params         JSONB           NOT NULL,
    min_margin_pct          DECIMAL,
    max_price_change_pct    DECIMAL,
    min_price               DECIMAL,
    max_price               DECIMAL,
    guard_config            JSONB,
    execution_mode          VARCHAR(20)     NOT NULL DEFAULT 'RECOMMENDATION',
    approval_timeout_hours  INT             NOT NULL DEFAULT 72,
    priority                INT             NOT NULL DEFAULT 0,
    version                 INT             NOT NULL DEFAULT 1,
    created_by              BIGINT          NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT fk_pp_workspace  FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_pp_created_by FOREIGN KEY (created_by)   REFERENCES app_user  (id)
);

CREATE INDEX idx_pp_workspace_status ON price_policy (workspace_id, status);

-- ── price_policy_assignment ─────────────────────────────────────────────

CREATE TABLE price_policy_assignment (
    id                          BIGSERIAL   PRIMARY KEY,
    price_policy_id             BIGINT      NOT NULL,
    marketplace_connection_id   BIGINT      NOT NULL,
    scope_type                  VARCHAR(20) NOT NULL,
    category_id                 BIGINT,
    marketplace_offer_id        BIGINT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_ppa_policy     FOREIGN KEY (price_policy_id)           REFERENCES price_policy           (id),
    CONSTRAINT fk_ppa_connection FOREIGN KEY (marketplace_connection_id) REFERENCES marketplace_connection (id),
    CONSTRAINT fk_ppa_offer      FOREIGN KEY (marketplace_offer_id)      REFERENCES marketplace_offer      (id)
);

CREATE UNIQUE INDEX uq_ppa_policy_scope ON price_policy_assignment (
    price_policy_id,
    marketplace_connection_id,
    scope_type,
    COALESCE(category_id, 0),
    COALESCE(marketplace_offer_id, 0)
);
CREATE INDEX idx_ppa_connection ON price_policy_assignment (marketplace_connection_id);
CREATE INDEX idx_ppa_offer      ON price_policy_assignment (marketplace_offer_id) WHERE marketplace_offer_id IS NOT NULL;

-- ── manual_price_lock ───────────────────────────────────────────────────

CREATE TABLE manual_price_lock (
    id                      BIGSERIAL   PRIMARY KEY,
    workspace_id            BIGINT      NOT NULL,
    marketplace_offer_id    BIGINT      NOT NULL,
    locked_price            DECIMAL     NOT NULL,
    reason                  TEXT,
    locked_by               BIGINT      NOT NULL,
    locked_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ,
    unlocked_at             TIMESTAMPTZ,
    unlocked_by             BIGINT,

    CONSTRAINT fk_mpl_workspace FOREIGN KEY (workspace_id)       REFERENCES workspace        (id),
    CONSTRAINT fk_mpl_offer     FOREIGN KEY (marketplace_offer_id) REFERENCES marketplace_offer (id),
    CONSTRAINT fk_mpl_locked_by FOREIGN KEY (locked_by)          REFERENCES app_user          (id),
    CONSTRAINT fk_mpl_unlocked  FOREIGN KEY (unlocked_by)        REFERENCES app_user          (id)
);

CREATE UNIQUE INDEX uq_mpl_active_lock
    ON manual_price_lock (marketplace_offer_id)
    WHERE unlocked_at IS NULL;

CREATE INDEX idx_mpl_workspace ON manual_price_lock (workspace_id);
CREATE INDEX idx_mpl_expiring  ON manual_price_lock (expires_at) WHERE unlocked_at IS NULL AND expires_at IS NOT NULL;

-- ── pricing_run ─────────────────────────────────────────────────────────

CREATE TABLE pricing_run (
    id                          BIGSERIAL       PRIMARY KEY,
    workspace_id                BIGINT          NOT NULL,
    connection_id               BIGINT          NOT NULL,
    trigger_type                VARCHAR(30)     NOT NULL,
    request_hash                VARCHAR(64),
    requested_offers_count      INT,
    source_job_execution_id     BIGINT,
    status                      VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    total_offers                INT,
    eligible_count              INT,
    change_count                INT,
    skip_count                  INT,
    hold_count                  INT,
    started_at                  TIMESTAMPTZ,
    completed_at                TIMESTAMPTZ,
    error_details               JSONB,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT fk_pr_workspace  FOREIGN KEY (workspace_id)  REFERENCES workspace             (id),
    CONSTRAINT fk_pr_connection FOREIGN KEY (connection_id) REFERENCES marketplace_connection (id)
);

CREATE INDEX idx_pr_workspace_created ON pricing_run (workspace_id, created_at DESC);
CREATE INDEX idx_pr_connection_status ON pricing_run (connection_id, status);

-- ── price_decision ──────────────────────────────────────────────────────

CREATE TABLE price_decision (
    id                      BIGSERIAL       PRIMARY KEY,
    workspace_id            BIGINT          NOT NULL,
    pricing_run_id          BIGINT          NOT NULL,
    marketplace_offer_id    BIGINT          NOT NULL,
    price_policy_id         BIGINT          NOT NULL,
    policy_version          INT             NOT NULL,
    policy_snapshot         JSONB           NOT NULL,
    decision_type           VARCHAR(20)     NOT NULL,
    current_price           DECIMAL,
    target_price            DECIMAL,
    price_change_amount     DECIMAL,
    price_change_pct        DECIMAL,
    strategy_type           VARCHAR(30)     NOT NULL,
    strategy_raw_price      DECIMAL,
    signal_snapshot         JSONB,
    constraints_applied     JSONB,
    guards_evaluated        JSONB,
    skip_reason             VARCHAR(255),
    explanation_summary     TEXT,
    execution_mode          VARCHAR(20)     NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT fk_pd_workspace  FOREIGN KEY (workspace_id)       REFERENCES workspace        (id),
    CONSTRAINT fk_pd_run        FOREIGN KEY (pricing_run_id)     REFERENCES pricing_run       (id),
    CONSTRAINT fk_pd_offer      FOREIGN KEY (marketplace_offer_id) REFERENCES marketplace_offer (id),
    CONSTRAINT fk_pd_policy     FOREIGN KEY (price_policy_id)    REFERENCES price_policy       (id)
);

CREATE INDEX idx_pd_workspace_created ON price_decision (workspace_id, created_at DESC);
CREATE INDEX idx_pd_offer_latest      ON price_decision (marketplace_offer_id, created_at DESC);
CREATE INDEX idx_pd_run               ON price_decision (pricing_run_id);
CREATE INDEX idx_pd_policy_version    ON price_decision (price_policy_id, policy_version);

--rollback DROP TABLE price_decision;
--rollback DROP TABLE pricing_run;
--rollback DROP TABLE manual_price_lock;
--rollback DROP TABLE price_policy_assignment;
--rollback DROP TABLE price_policy;
