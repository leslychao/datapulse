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

--rollback DROP TABLE saved_view;
