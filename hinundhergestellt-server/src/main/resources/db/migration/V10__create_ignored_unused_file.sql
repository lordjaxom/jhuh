CREATE TABLE ignored_unused_file
(
    id        UUID         NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    CONSTRAINT pk_ignoredunusedfile PRIMARY KEY (id)
);

ALTER TABLE ignored_unused_file
    ADD CONSTRAINT uc_ignoredunusedfile_filename UNIQUE (file_name);
