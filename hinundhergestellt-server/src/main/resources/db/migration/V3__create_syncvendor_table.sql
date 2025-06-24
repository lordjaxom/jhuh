CREATE TABLE sync_vendor
(
    id      UUID         NOT NULL,
    name    VARCHAR(255) NOT NULL,
    address VARCHAR(255) NULL,
    email   VARCHAR(255) NULL,
    CONSTRAINT pk_syncvendor PRIMARY KEY (id)
);

ALTER TABLE sync_vendor
    ADD CONSTRAINT uc_syncvendor_name UNIQUE (name);

CREATE INDEX idx_syncvendor_name ON sync_vendor (name);
