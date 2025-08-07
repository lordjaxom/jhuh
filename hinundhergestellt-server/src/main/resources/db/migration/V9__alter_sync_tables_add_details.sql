CREATE TABLE sync_product_technical_details
(
    sync_product_id UUID         NOT NULL,
    name            VARCHAR(255) NOT NULL,
    value           VARCHAR(255) NOT NULL,
    sort_order      INT          NOT NULL
);

ALTER TABLE sync_product
    ADD description_html TEXT NULL;

ALTER TABLE sync_variant
    ADD weight DECIMAL(10,2) NULL;

ALTER TABLE sync_product_technical_details
    ADD CONSTRAINT fk_syncproduct_technicaldetails_on_sync_product FOREIGN KEY (sync_product_id) REFERENCES sync_product (id);
