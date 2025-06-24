ALTER TABLE sync_product
    ADD vendor_id UUID NULL;

ALTER TABLE sync_product
    ADD CONSTRAINT FK_SYNCPRODUCT_ON_VENDOR FOREIGN KEY (vendor_id) REFERENCES sync_vendor (id);
