--liquibase formatted sql

--changeset datapulse:0025-competitor-tables

CREATE TABLE competitor_match (
    id                      bigserial   PRIMARY KEY,
    workspace_id            bigint      NOT NULL,
    marketplace_offer_id    bigint      NOT NULL,
    competitor_name         varchar(255),
    competitor_listing_url  varchar(1000),
    match_method            varchar(20) NOT NULL DEFAULT 'MANUAL',
    trust_level             varchar(20) NOT NULL DEFAULT 'TRUSTED',
    matched_by              bigint,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_cm_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_cm_offer     FOREIGN KEY (marketplace_offer_id) REFERENCES marketplace_offer (id),
    CONSTRAINT fk_cm_user      FOREIGN KEY (matched_by) REFERENCES app_user (id)
);

CREATE INDEX idx_cm_offer ON competitor_match (marketplace_offer_id);
CREATE INDEX idx_cm_workspace ON competitor_match (workspace_id);

CREATE TABLE competitor_observation (
    id                    bigserial   PRIMARY KEY,
    competitor_match_id   bigint      NOT NULL,
    competitor_price      decimal     NOT NULL,
    currency              varchar(3)  NOT NULL DEFAULT 'RUB',
    observed_at           timestamptz NOT NULL,
    created_at            timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_co_match FOREIGN KEY (competitor_match_id) REFERENCES competitor_match (id)
);

CREATE INDEX idx_co_match      ON competitor_observation (competitor_match_id);
CREATE INDEX idx_co_observed_at ON competitor_observation (observed_at DESC);

--rollback DROP TABLE competitor_observation; DROP TABLE competitor_match;
