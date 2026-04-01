--liquibase formatted sql

--changeset datapulse:0013-seller-operations-tables

-- ── saved_view ────────────────────────────────────────────────────────

CREATE TABLE saved_view (
    id                BIGSERIAL    PRIMARY KEY,
    workspace_id      BIGINT       NOT NULL,
    user_id           BIGINT       NOT NULL,
    name              VARCHAR(200) NOT NULL,
    is_default        BOOLEAN      NOT NULL DEFAULT false,
    is_system         BOOLEAN      NOT NULL DEFAULT false,
    filters           JSONB        NOT NULL DEFAULT '{}'::jsonb,
    sort_column       VARCHAR(60),
    sort_direction    VARCHAR(4)   DEFAULT 'ASC',
    visible_columns   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    group_by_sku      BOOLEAN      NOT NULL DEFAULT false,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_sv_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_sv_user      FOREIGN KEY (user_id)      REFERENCES app_user  (id),
    CONSTRAINT uq_sv_workspace_user_name UNIQUE (workspace_id, user_id, name)
);

CREATE INDEX idx_sv_user ON saved_view (workspace_id, user_id);

-- Grid performance indexes on canonical tables

CREATE INDEX IF NOT EXISTS idx_mo_connection_status
    ON marketplace_offer (marketplace_connection_id, status);

CREATE INDEX IF NOT EXISTS idx_pd_offer_latest
    ON price_decision (marketplace_offer_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pa_offer_latest
    ON price_action (marketplace_offer_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS idx_mpl_offer_active
    ON manual_price_lock (marketplace_offer_id)
    WHERE unlocked_at IS NULL;

-- ── working_queue_definition ──────────────────────────────────────────

CREATE TABLE working_queue_definition (
    id                BIGSERIAL    PRIMARY KEY,
    workspace_id      BIGINT       NOT NULL,
    name              VARCHAR(200) NOT NULL,
    queue_type        VARCHAR(30)  NOT NULL,
    auto_criteria     JSONB,
    enabled           BOOLEAN      NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_wqd_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT uq_wqd_workspace_name UNIQUE (workspace_id, name)
);

-- ── working_queue_assignment ─────────────────────────────────────────

CREATE TABLE working_queue_assignment (
    id                    BIGSERIAL    PRIMARY KEY,
    queue_definition_id   BIGINT       NOT NULL,
    entity_type           VARCHAR(60)  NOT NULL,
    entity_id             BIGINT       NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    assigned_to_user_id   BIGINT,
    note                  TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_wqa_queue FOREIGN KEY (queue_definition_id) REFERENCES working_queue_definition (id) ON DELETE CASCADE,
    CONSTRAINT fk_wqa_user  FOREIGN KEY (assigned_to_user_id) REFERENCES app_user (id)
);

CREATE INDEX idx_wqa_queue_status ON working_queue_assignment (queue_definition_id, status);
CREATE INDEX idx_wqa_entity       ON working_queue_assignment (entity_type, entity_id);

CREATE UNIQUE INDEX idx_wqa_active_unique
    ON working_queue_assignment (queue_definition_id, entity_type, entity_id)
    WHERE status NOT IN ('DONE', 'DISMISSED');

--rollback DROP TABLE working_queue_assignment;
--rollback DROP TABLE working_queue_definition;
--rollback DROP TABLE saved_view;
