CREATE TABLE sync_product
(
    id         UUID NOT NULL,
    artoo_id   VARCHAR(255),
    shopify_id VARCHAR(255),
    CONSTRAINT pk_syncproduct PRIMARY KEY (id)
);

CREATE TABLE sync_product_tags
(
    sync_product_id UUID NOT NULL,
    tags            VARCHAR(255)
);

CREATE TABLE sync_product_variants
(
    sync_product_id UUID NOT NULL,
    variants_id     UUID NOT NULL
);

CREATE TABLE sync_variant
(
    id         UUID         NOT NULL,
    product_id UUID         NOT NULL,
    barcode    VARCHAR(255) NOT NULL,
    deleted    BOOLEAN      NOT NULL,
    CONSTRAINT pk_syncvariant PRIMARY KEY (id)
);

ALTER TABLE sync_product_variants
    ADD CONSTRAINT uc_sync_product_variants_variants UNIQUE (variants_id);

ALTER TABLE sync_variant
    ADD CONSTRAINT uc_syncvariant_barcode UNIQUE (barcode);

CREATE INDEX idx_syncproduct_artooid ON sync_product (artoo_id);

CREATE INDEX idx_syncproduct_shopifyid ON sync_product (shopify_id);

CREATE INDEX idx_syncvariant_barcode ON sync_variant (barcode);

ALTER TABLE sync_variant
    ADD CONSTRAINT FK_SYNCVARIANT_ON_PRODUCT FOREIGN KEY (product_id) REFERENCES sync_product (id);

ALTER TABLE sync_product_tags
    ADD CONSTRAINT fk_syncproduct_tags_on_sync_product FOREIGN KEY (sync_product_id) REFERENCES sync_product (id);

ALTER TABLE sync_product_variants
    ADD CONSTRAINT fk_synprovar_on_sync_product FOREIGN KEY (sync_product_id) REFERENCES sync_product (id);

ALTER TABLE sync_product_variants
    ADD CONSTRAINT fk_synprovar_on_sync_variant FOREIGN KEY (variants_id) REFERENCES sync_variant (id);