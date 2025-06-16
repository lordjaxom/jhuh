ALTER TABLE sync_product_variants
    DROP FOREIGN KEY fk_synprovar_on_sync_product;

ALTER TABLE sync_product_variants
    DROP FOREIGN KEY fk_synprovar_on_sync_variant;

DROP TABLE sync_product_variants;

ALTER TABLE sync_product
    DROP COLUMN artoo_id;

ALTER TABLE sync_product
    ADD artoo_id VARCHAR(255) NULL;

ALTER TABLE sync_product
    ADD CONSTRAINT uc_syncproduct_artooid UNIQUE (artoo_id);
