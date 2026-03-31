--liquibase formatted sql

--changeset datapulse:0004-etl-canonical-tables

-- ============================================================
-- Dictionary tables
-- ============================================================

CREATE TABLE category (
    id                          bigserial    PRIMARY KEY,
    marketplace_connection_id   bigint       NOT NULL,
    external_category_id        varchar(120) NOT NULL,
    name                        varchar(500) NOT NULL,
    parent_category_id          bigint,
    marketplace_type            varchar(10)  NOT NULL,
    job_execution_id            bigint       NOT NULL,
    created_at                  timestamptz  NOT NULL DEFAULT now(),
    updated_at                  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_category_connection    FOREIGN KEY (marketplace_connection_id) REFERENCES marketplace_connection (id),
    CONSTRAINT fk_category_parent        FOREIGN KEY (parent_category_id)        REFERENCES category (id),
    CONSTRAINT fk_category_job_execution FOREIGN KEY (job_execution_id)          REFERENCES job_execution (id),
    CONSTRAINT uq_category_connection_external UNIQUE (marketplace_connection_id, external_category_id)
);

CREATE INDEX idx_category_connection_id   ON category (marketplace_connection_id);
CREATE INDEX idx_category_parent_id       ON category (parent_category_id);
CREATE INDEX idx_category_job_execution   ON category (job_execution_id);

CREATE TABLE warehouse (
    id                          bigserial    PRIMARY KEY,
    marketplace_connection_id   bigint       NOT NULL,
    external_warehouse_id       varchar(120) NOT NULL,
    name                        varchar(500) NOT NULL,
    warehouse_type              varchar(20)  NOT NULL,
    marketplace_type            varchar(10)  NOT NULL,
    job_execution_id            bigint       NOT NULL,
    created_at                  timestamptz  NOT NULL DEFAULT now(),
    updated_at                  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_warehouse_connection    FOREIGN KEY (marketplace_connection_id) REFERENCES marketplace_connection (id),
    CONSTRAINT fk_warehouse_job_execution FOREIGN KEY (job_execution_id)          REFERENCES job_execution (id),
    CONSTRAINT uq_warehouse_connection_external UNIQUE (marketplace_connection_id, external_warehouse_id)
);

CREATE INDEX idx_warehouse_connection_id   ON warehouse (marketplace_connection_id);
CREATE INDEX idx_warehouse_job_execution   ON warehouse (job_execution_id);

-- ============================================================
-- Catalog tables
-- ============================================================

CREATE TABLE product_master (
    id                bigserial    PRIMARY KEY,
    workspace_id      bigint       NOT NULL,
    external_code     varchar(120) NOT NULL,
    name              varchar(500),
    brand             varchar(255),
    job_execution_id  bigint       NOT NULL,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_product_master_workspace     FOREIGN KEY (workspace_id)     REFERENCES workspace (id),
    CONSTRAINT fk_product_master_job_execution FOREIGN KEY (job_execution_id) REFERENCES job_execution (id),
    CONSTRAINT uq_product_master_workspace_code UNIQUE (workspace_id, external_code)
);

CREATE INDEX idx_product_master_workspace_id   ON product_master (workspace_id);
CREATE INDEX idx_product_master_job_execution  ON product_master (job_execution_id);

CREATE TABLE seller_sku (
    id                 bigserial    PRIMARY KEY,
    product_master_id  bigint       NOT NULL,
    sku_code           varchar(120) NOT NULL,
    barcode            varchar(120),
    job_execution_id   bigint       NOT NULL,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_seller_sku_product_master  FOREIGN KEY (product_master_id) REFERENCES product_master (id),
    CONSTRAINT fk_seller_sku_job_execution   FOREIGN KEY (job_execution_id)  REFERENCES job_execution (id),
    CONSTRAINT uq_seller_sku_master_code UNIQUE (product_master_id, sku_code)
);

CREATE INDEX idx_seller_sku_product_master_id  ON seller_sku (product_master_id);
CREATE INDEX idx_seller_sku_job_execution      ON seller_sku (job_execution_id);

CREATE TABLE marketplace_offer (
    id                          bigserial      PRIMARY KEY,
    seller_sku_id               bigint         NOT NULL,
    marketplace_connection_id   bigint         NOT NULL,
    marketplace_sku             varchar(120)   NOT NULL,
    marketplace_sku_alt         varchar(120),
    name                        varchar(500),
    category_id                 bigint,
    status                      varchar(30)    NOT NULL DEFAULT 'ACTIVE',
    url                         varchar(1000),
    image_url                   varchar(1000),
    job_execution_id            bigint         NOT NULL,
    created_at                  timestamptz    NOT NULL DEFAULT now(),
    updated_at                  timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT fk_offer_seller_sku   FOREIGN KEY (seller_sku_id)             REFERENCES seller_sku (id),
    CONSTRAINT fk_offer_connection   FOREIGN KEY (marketplace_connection_id) REFERENCES marketplace_connection (id),
    CONSTRAINT fk_offer_category     FOREIGN KEY (category_id)               REFERENCES category (id),
    CONSTRAINT fk_offer_job_execution FOREIGN KEY (job_execution_id)         REFERENCES job_execution (id),
    CONSTRAINT uq_offer_sku_connection_msku UNIQUE (seller_sku_id, marketplace_connection_id, marketplace_sku)
);

CREATE INDEX idx_offer_seller_sku_id      ON marketplace_offer (seller_sku_id);
CREATE INDEX idx_offer_connection_id      ON marketplace_offer (marketplace_connection_id);
CREATE INDEX idx_offer_category_id        ON marketplace_offer (category_id);
CREATE INDEX idx_offer_job_execution      ON marketplace_offer (job_execution_id);

-- ============================================================
-- State tables
-- ============================================================

CREATE TABLE canonical_price_current (
    id                    bigserial   PRIMARY KEY,
    marketplace_offer_id  bigint      NOT NULL UNIQUE,
    price                 decimal     NOT NULL,
    discount_price        decimal,
    discount_pct          decimal,
    currency              varchar(3)  NOT NULL DEFAULT 'RUB',
    min_price             decimal,
    max_price             decimal,
    job_execution_id      bigint      NOT NULL,
    captured_at           timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_price_current_offer         FOREIGN KEY (marketplace_offer_id) REFERENCES marketplace_offer (id),
    CONSTRAINT fk_price_current_job_execution FOREIGN KEY (job_execution_id)     REFERENCES job_execution (id)
);

CREATE INDEX idx_price_current_job_execution ON canonical_price_current (job_execution_id);

CREATE TABLE canonical_stock_current (
    id                    bigserial   PRIMARY KEY,
    marketplace_offer_id  bigint      NOT NULL,
    warehouse_id          bigint      NOT NULL,
    available             int         NOT NULL DEFAULT 0,
    reserved              int         NOT NULL DEFAULT 0,
    job_execution_id      bigint      NOT NULL,
    captured_at           timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_stock_current_offer         FOREIGN KEY (marketplace_offer_id) REFERENCES marketplace_offer (id),
    CONSTRAINT fk_stock_current_warehouse     FOREIGN KEY (warehouse_id)         REFERENCES warehouse (id),
    CONSTRAINT fk_stock_current_job_execution FOREIGN KEY (job_execution_id)     REFERENCES job_execution (id),
    CONSTRAINT uq_stock_current_offer_warehouse UNIQUE (marketplace_offer_id, warehouse_id)
);

CREATE INDEX idx_stock_current_warehouse_id    ON canonical_stock_current (warehouse_id);
CREATE INDEX idx_stock_current_job_execution   ON canonical_stock_current (job_execution_id);

-- ============================================================
-- Flow tables
-- ============================================================

CREATE TABLE canonical_order (
    id                    bigserial    PRIMARY KEY,
    connection_id         bigint       NOT NULL,
    source_platform       varchar(10)  NOT NULL,
    external_order_id     varchar(120) NOT NULL,
    marketplace_offer_id  bigint,
    order_date            timestamptz  NOT NULL,
    quantity              int          NOT NULL,
    price_per_unit        decimal      NOT NULL,
    total_amount          decimal,
    currency              varchar(3)   NOT NULL DEFAULT 'RUB',
    status                varchar(30)  NOT NULL,
    fulfillment_type      varchar(10),
    region                varchar(255),
    job_execution_id      bigint       NOT NULL,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_order_connection     FOREIGN KEY (connection_id)         REFERENCES marketplace_connection (id),
    CONSTRAINT fk_order_offer          FOREIGN KEY (marketplace_offer_id)  REFERENCES marketplace_offer (id),
    CONSTRAINT fk_order_job_execution  FOREIGN KEY (job_execution_id)      REFERENCES job_execution (id),
    CONSTRAINT uq_order_connection_external UNIQUE (connection_id, external_order_id)
);

CREATE INDEX idx_order_connection_id        ON canonical_order (connection_id);
CREATE INDEX idx_order_offer_id             ON canonical_order (marketplace_offer_id);
CREATE INDEX idx_order_job_execution        ON canonical_order (job_execution_id);

CREATE TABLE canonical_sale (
    id                    bigserial    PRIMARY KEY,
    connection_id         bigint       NOT NULL,
    source_platform       varchar(10)  NOT NULL,
    external_sale_id      varchar(120) NOT NULL,
    canonical_order_id    bigint,
    marketplace_offer_id  bigint,
    posting_id            varchar(120),
    seller_sku_id         bigint,
    sale_date             timestamptz  NOT NULL,
    sale_amount           decimal      NOT NULL,
    commission            decimal,
    quantity              int          NOT NULL DEFAULT 1,
    currency              varchar(3)   NOT NULL DEFAULT 'RUB',
    job_execution_id      bigint       NOT NULL,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_sale_connection     FOREIGN KEY (connection_id)         REFERENCES marketplace_connection (id),
    CONSTRAINT fk_sale_order          FOREIGN KEY (canonical_order_id)    REFERENCES canonical_order (id),
    CONSTRAINT fk_sale_offer          FOREIGN KEY (marketplace_offer_id)  REFERENCES marketplace_offer (id),
    CONSTRAINT fk_sale_seller_sku     FOREIGN KEY (seller_sku_id)         REFERENCES seller_sku (id),
    CONSTRAINT fk_sale_job_execution  FOREIGN KEY (job_execution_id)      REFERENCES job_execution (id),
    CONSTRAINT uq_sale_connection_external UNIQUE (connection_id, external_sale_id)
);

CREATE INDEX idx_sale_connection_id         ON canonical_sale (connection_id);
CREATE INDEX idx_sale_order_id              ON canonical_sale (canonical_order_id);
CREATE INDEX idx_sale_offer_id              ON canonical_sale (marketplace_offer_id);
CREATE INDEX idx_sale_seller_sku_id         ON canonical_sale (seller_sku_id);
CREATE INDEX idx_sale_job_execution         ON canonical_sale (job_execution_id);

CREATE TABLE canonical_return (
    id                    bigserial    PRIMARY KEY,
    connection_id         bigint       NOT NULL,
    source_platform       varchar(10)  NOT NULL,
    external_return_id    varchar(120) NOT NULL,
    canonical_order_id    bigint,
    marketplace_offer_id  bigint,
    seller_sku_id         bigint,
    return_date           timestamptz  NOT NULL,
    return_amount         decimal      NOT NULL,
    return_reason         varchar(255),
    quantity              int          NOT NULL DEFAULT 1,
    status                varchar(30),
    currency              varchar(3)   NOT NULL DEFAULT 'RUB',
    job_execution_id      bigint       NOT NULL,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_return_connection     FOREIGN KEY (connection_id)         REFERENCES marketplace_connection (id),
    CONSTRAINT fk_return_order          FOREIGN KEY (canonical_order_id)    REFERENCES canonical_order (id),
    CONSTRAINT fk_return_offer          FOREIGN KEY (marketplace_offer_id)  REFERENCES marketplace_offer (id),
    CONSTRAINT fk_return_seller_sku     FOREIGN KEY (seller_sku_id)         REFERENCES seller_sku (id),
    CONSTRAINT fk_return_job_execution  FOREIGN KEY (job_execution_id)      REFERENCES job_execution (id),
    CONSTRAINT uq_return_connection_external UNIQUE (connection_id, external_return_id)
);

CREATE INDEX idx_return_connection_id       ON canonical_return (connection_id);
CREATE INDEX idx_return_order_id            ON canonical_return (canonical_order_id);
CREATE INDEX idx_return_offer_id            ON canonical_return (marketplace_offer_id);
CREATE INDEX idx_return_seller_sku_id       ON canonical_return (seller_sku_id);
CREATE INDEX idx_return_job_execution       ON canonical_return (job_execution_id);

CREATE TABLE canonical_finance_entry (
    id                                  bigserial    PRIMARY KEY,
    connection_id                       bigint       NOT NULL,
    source_platform                     varchar(10)  NOT NULL,
    external_entry_id                   varchar(120) NOT NULL,
    entry_type                          varchar(60)  NOT NULL,
    posting_id                          varchar(120),
    order_id                            varchar(120),
    seller_sku_id                       bigint,
    warehouse_id                        bigint,

    revenue_amount                      decimal      NOT NULL DEFAULT 0,
    marketplace_commission_amount       decimal      NOT NULL DEFAULT 0,
    acquiring_commission_amount         decimal      NOT NULL DEFAULT 0,
    logistics_cost_amount               decimal      NOT NULL DEFAULT 0,
    storage_cost_amount                 decimal      NOT NULL DEFAULT 0,
    penalties_amount                    decimal      NOT NULL DEFAULT 0,
    acceptance_cost_amount              decimal      NOT NULL DEFAULT 0,
    marketing_cost_amount               decimal      NOT NULL DEFAULT 0,
    other_marketplace_charges_amount    decimal      NOT NULL DEFAULT 0,
    compensation_amount                 decimal      NOT NULL DEFAULT 0,
    refund_amount                       decimal      NOT NULL DEFAULT 0,
    net_payout                          decimal,

    currency                            varchar(3)   NOT NULL DEFAULT 'RUB',
    entry_date                          timestamptz  NOT NULL,
    attribution_level                   varchar(10)  NOT NULL,
    job_execution_id                    bigint       NOT NULL,
    created_at                          timestamptz  NOT NULL DEFAULT now(),
    updated_at                          timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_finance_connection     FOREIGN KEY (connection_id)     REFERENCES marketplace_connection (id),
    CONSTRAINT fk_finance_seller_sku     FOREIGN KEY (seller_sku_id)     REFERENCES seller_sku (id),
    CONSTRAINT fk_finance_warehouse      FOREIGN KEY (warehouse_id)      REFERENCES warehouse (id),
    CONSTRAINT fk_finance_job_execution  FOREIGN KEY (job_execution_id)  REFERENCES job_execution (id),
    CONSTRAINT uq_finance_connection_platform_entry UNIQUE (connection_id, source_platform, external_entry_id)
);

CREATE INDEX idx_finance_connection_id      ON canonical_finance_entry (connection_id);
CREATE INDEX idx_finance_seller_sku_id      ON canonical_finance_entry (seller_sku_id);
CREATE INDEX idx_finance_warehouse_id       ON canonical_finance_entry (warehouse_id);
CREATE INDEX idx_finance_job_execution      ON canonical_finance_entry (job_execution_id);

-- ============================================================
-- Cost profile (SCD2)
-- ============================================================

CREATE TABLE cost_profile (
    id                   bigserial   PRIMARY KEY,
    seller_sku_id        bigint      NOT NULL,
    cost_price           decimal     NOT NULL,
    currency             varchar(3)  NOT NULL DEFAULT 'RUB',
    valid_from           date        NOT NULL,
    valid_to             date,
    updated_by_user_id   bigint      NOT NULL,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_cost_profile_seller_sku FOREIGN KEY (seller_sku_id)      REFERENCES seller_sku (id),
    CONSTRAINT fk_cost_profile_user       FOREIGN KEY (updated_by_user_id) REFERENCES app_user (id),
    CONSTRAINT uq_cost_profile_sku_valid_from UNIQUE (seller_sku_id, valid_from)
);

CREATE INDEX idx_cost_profile_seller_sku_id   ON cost_profile (seller_sku_id);
CREATE INDEX idx_cost_profile_user_id         ON cost_profile (updated_by_user_id);

-- ============================================================
-- Promo tables
-- ============================================================

CREATE TABLE canonical_promo_campaign (
    id                        bigserial      PRIMARY KEY,
    connection_id             bigint         NOT NULL,
    external_promo_id         varchar(120)   NOT NULL,
    source_platform           varchar(10)    NOT NULL,
    promo_name                varchar(500)   NOT NULL,
    promo_type                varchar(60)    NOT NULL,
    status                    varchar(30)    NOT NULL,
    date_from                 timestamptz,
    date_to                   timestamptz,
    freeze_at                 timestamptz,
    participation_deadline    timestamptz,
    description               text,
    mechanic                  varchar(60),
    is_participating          boolean,
    raw_payload               jsonb,
    job_execution_id          bigint         NOT NULL,
    synced_at                 timestamptz,
    created_at                timestamptz    NOT NULL DEFAULT now(),
    updated_at                timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT fk_promo_campaign_connection     FOREIGN KEY (connection_id)     REFERENCES marketplace_connection (id),
    CONSTRAINT fk_promo_campaign_job_execution  FOREIGN KEY (job_execution_id)  REFERENCES job_execution (id),
    CONSTRAINT uq_promo_campaign_connection_external UNIQUE (connection_id, external_promo_id)
);

CREATE INDEX idx_promo_campaign_connection_id   ON canonical_promo_campaign (connection_id);
CREATE INDEX idx_promo_campaign_job_execution   ON canonical_promo_campaign (job_execution_id);

CREATE TABLE canonical_promo_product (
    id                              bigserial   PRIMARY KEY,
    canonical_promo_campaign_id     bigint      NOT NULL,
    marketplace_offer_id            bigint      NOT NULL,
    participation_status            varchar(30) NOT NULL,
    required_price                  decimal,
    current_price                   decimal,
    max_promo_price                 decimal,
    max_discount_pct                decimal,
    min_stock_required              int,
    stock_available                 int,
    add_mode                        varchar(60),
    participation_decision_source   varchar(20),
    job_execution_id                bigint      NOT NULL,
    synced_at                       timestamptz,
    created_at                      timestamptz NOT NULL DEFAULT now(),
    updated_at                      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_promo_product_campaign      FOREIGN KEY (canonical_promo_campaign_id) REFERENCES canonical_promo_campaign (id),
    CONSTRAINT fk_promo_product_offer         FOREIGN KEY (marketplace_offer_id)        REFERENCES marketplace_offer (id),
    CONSTRAINT fk_promo_product_job_execution FOREIGN KEY (job_execution_id)            REFERENCES job_execution (id),
    CONSTRAINT uq_promo_product_campaign_offer UNIQUE (canonical_promo_campaign_id, marketplace_offer_id)
);

CREATE INDEX idx_promo_product_campaign_id     ON canonical_promo_product (canonical_promo_campaign_id);
CREATE INDEX idx_promo_product_offer_id        ON canonical_promo_product (marketplace_offer_id);
CREATE INDEX idx_promo_product_job_execution   ON canonical_promo_product (job_execution_id);

--rollback DROP TABLE canonical_promo_product;
--rollback DROP TABLE canonical_promo_campaign;
--rollback DROP TABLE cost_profile;
--rollback DROP TABLE canonical_finance_entry;
--rollback DROP TABLE canonical_return;
--rollback DROP TABLE canonical_sale;
--rollback DROP TABLE canonical_order;
--rollback DROP TABLE canonical_stock_current;
--rollback DROP TABLE canonical_price_current;
--rollback DROP TABLE marketplace_offer;
--rollback DROP TABLE seller_sku;
--rollback DROP TABLE product_master;
--rollback DROP TABLE warehouse;
--rollback DROP TABLE category;
