--liquibase formatted sql

--changeset datapulse:0001-tenancy-iam-tables

CREATE TABLE app_user (
    id              bigserial PRIMARY KEY,
    external_id     varchar(120) NOT NULL,
    email           varchar(320) NOT NULL,
    name            varchar(255) NOT NULL,
    status          varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_app_user_external_id UNIQUE (external_id),
    CONSTRAINT uq_app_user_email       UNIQUE (email)
);

CREATE TABLE tenant (
    id              bigserial PRIMARY KEY,
    name            varchar(255) NOT NULL,
    slug            varchar(80)  NOT NULL,
    status          varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    owner_user_id   bigint       NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_tenant_slug        UNIQUE (slug),
    CONSTRAINT fk_tenant_owner       FOREIGN KEY (owner_user_id) REFERENCES app_user (id)
);

CREATE TABLE workspace (
    id              bigserial PRIMARY KEY,
    tenant_id       bigint       NOT NULL,
    name            varchar(255) NOT NULL,
    slug            varchar(80)  NOT NULL,
    status          varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    owner_user_id   bigint       NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_workspace_tenant_slug UNIQUE (tenant_id, slug),
    CONSTRAINT fk_workspace_tenant      FOREIGN KEY (tenant_id)     REFERENCES tenant   (id),
    CONSTRAINT fk_workspace_owner       FOREIGN KEY (owner_user_id) REFERENCES app_user (id)
);

CREATE INDEX idx_workspace_tenant_id ON workspace (tenant_id);

CREATE TABLE workspace_member (
    id              bigserial PRIMARY KEY,
    workspace_id    bigint      NOT NULL,
    user_id         bigint      NOT NULL,
    role            varchar(30) NOT NULL,
    status          varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uq_workspace_member UNIQUE (workspace_id, user_id),
    CONSTRAINT fk_member_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_member_user      FOREIGN KEY (user_id)      REFERENCES app_user  (id)
);

CREATE INDEX idx_workspace_member_user_id ON workspace_member (user_id);

CREATE TABLE workspace_invitation (
    id                  bigserial    PRIMARY KEY,
    workspace_id        bigint       NOT NULL,
    email               varchar(320) NOT NULL,
    role                varchar(30)  NOT NULL,
    status              varchar(20)  NOT NULL DEFAULT 'PENDING',
    token_hash          varchar(64)  NOT NULL,
    expires_at          timestamptz  NOT NULL,
    invited_by_user_id  bigint       NOT NULL,
    accepted_by_user_id bigint,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_invitation_workspace  FOREIGN KEY (workspace_id)        REFERENCES workspace (id),
    CONSTRAINT fk_invitation_invited_by FOREIGN KEY (invited_by_user_id)  REFERENCES app_user  (id),
    CONSTRAINT fk_invitation_accepted   FOREIGN KEY (accepted_by_user_id) REFERENCES app_user  (id)
);

CREATE UNIQUE INDEX uq_invitation_pending_email
    ON workspace_invitation (workspace_id, email)
    WHERE status = 'PENDING';

CREATE INDEX idx_invitation_email      ON workspace_invitation (email);
CREATE INDEX idx_invitation_token_hash ON workspace_invitation (token_hash);

--rollback DROP TABLE workspace_invitation;
--rollback DROP TABLE workspace_member;
--rollback DROP TABLE workspace;
--rollback DROP TABLE tenant;
--rollback DROP TABLE app_user;
