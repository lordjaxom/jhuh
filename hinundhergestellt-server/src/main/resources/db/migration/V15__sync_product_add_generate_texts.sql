ALTER TABLE sync_product
    ADD generate_texts BIT(1) NULL;

UPDATE sync_product
SET generate_texts = b'0';

COMMIT;

ALTER TABLE sync_product
    MODIFY generate_texts BIT(1) NOT NULL;