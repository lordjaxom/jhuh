CREATE TABLE sync_category
(
    id       BINARY(16) NOT NULL,
    artoo_id INT        NOT NULL,
    CONSTRAINT pk_synccategory PRIMARY KEY (id)
);

CREATE TABLE sync_category_tags
(
    sync_category_id BINARY(16)   NOT NULL,
    tags             VARCHAR(255) NULL
);

CREATE TABLE sync_product
(
    id         BINARY(16)   NOT NULL,
    artoo_id   INT          NULL,
    shopify_id VARCHAR(255) NULL,
    vendor     VARCHAR(255) NULL,
    type       VARCHAR(255) NULL,
    synced     BIT(1)       NOT NULL,
    CONSTRAINT pk_syncproduct PRIMARY KEY (id)
);

CREATE TABLE sync_product_tags
(
    sync_product_id BINARY(16)   NOT NULL,
    tags            VARCHAR(255) NULL
);

CREATE TABLE sync_product_variants
(
    sync_product_id BINARY(16) NOT NULL,
    variants_id     BINARY(16) NOT NULL
);

CREATE TABLE sync_variant
(
    id         BINARY(16)   NOT NULL,
    product_id BINARY(16)   NOT NULL,
    barcode    VARCHAR(255) NOT NULL,
    CONSTRAINT pk_syncvariant PRIMARY KEY (id)
);

ALTER TABLE sync_product_variants
    ADD CONSTRAINT uc_sync_product_variants_variants UNIQUE (variants_id);

ALTER TABLE sync_category
    ADD CONSTRAINT uc_synccategory_artooid UNIQUE (artoo_id);

ALTER TABLE sync_product
    ADD CONSTRAINT uc_syncproduct_artooid UNIQUE (artoo_id);

ALTER TABLE sync_product
    ADD CONSTRAINT uc_syncproduct_shopifyid UNIQUE (shopify_id);

ALTER TABLE sync_variant
    ADD CONSTRAINT uc_syncvariant_barcode UNIQUE (barcode);

CREATE INDEX idx_synccategory_artooid ON sync_category (artoo_id);

CREATE INDEX idx_syncproduct_artooid ON sync_product (artoo_id);

CREATE INDEX idx_syncproduct_shopifyid ON sync_product (shopify_id);

CREATE INDEX idx_syncvariant_barcode ON sync_variant (barcode);

ALTER TABLE sync_variant
    ADD CONSTRAINT FK_SYNCVARIANT_ON_PRODUCT FOREIGN KEY (product_id) REFERENCES sync_product (id);

ALTER TABLE sync_category_tags
    ADD CONSTRAINT fk_synccategory_tags_on_sync_category FOREIGN KEY (sync_category_id) REFERENCES sync_category (id);

ALTER TABLE sync_product_tags
    ADD CONSTRAINT fk_syncproduct_tags_on_sync_product FOREIGN KEY (sync_product_id) REFERENCES sync_product (id);

ALTER TABLE sync_product_variants
    ADD CONSTRAINT fk_synprovar_on_sync_product FOREIGN KEY (sync_product_id) REFERENCES sync_product (id);

ALTER TABLE sync_product_variants
    ADD CONSTRAINT fk_synprovar_on_sync_variant FOREIGN KEY (variants_id) REFERENCES sync_variant (id);