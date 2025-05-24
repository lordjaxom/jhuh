CREATE TABLE sync_product
(
    id             UUID NOT NULL,
    barcode        VARCHAR(255),
    ready2order_id INT,
    shopify_id     VARCHAR(255),
    CONSTRAINT pk_syncproduct PRIMARY KEY (id)
);

ALTER TABLE sync_product
    ADD CONSTRAINT uc_syncproduct_barcode UNIQUE (barcode);