ALTER TABLE sync_variant
    ADD artoo_id INT NULL;

ALTER TABLE sync_variant
    ADD shopify_id VARCHAR(255) NULL;

ALTER TABLE sync_variant
    ADD CONSTRAINT uc_syncvariant_artooid UNIQUE (artoo_id);

ALTER TABLE sync_variant
    ADD CONSTRAINT uc_syncvariant_shopifyid UNIQUE (shopify_id);

CREATE INDEX idx_syncvariant_artooid ON sync_variant (artoo_id);

CREATE INDEX idx_syncvariant_shopifyid ON sync_variant (shopify_id);
