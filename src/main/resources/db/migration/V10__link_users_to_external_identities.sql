ALTER TABLE users
    ADD COLUMN identity_issuer VARCHAR(512),
    ADD COLUMN identity_subject VARCHAR(255),
    ADD CONSTRAINT ck_users_external_identity_pair
        CHECK ((identity_issuer IS NULL) = (identity_subject IS NULL)),
    ADD CONSTRAINT uq_users_external_identity
        UNIQUE (identity_issuer, identity_subject);
