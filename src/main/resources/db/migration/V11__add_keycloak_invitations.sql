ALTER TABLE users
    ALTER COLUMN password DROP NOT NULL,
    ADD CONSTRAINT ck_users_credential
        CHECK (password IS NOT NULL OR identity_subject IS NOT NULL);

CREATE TABLE user_invitations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    request_id UUID NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    roles VARCHAR(255) NOT NULL,
    user_id UUID REFERENCES users(id),
    sent_at TIMESTAMPTZ,
    UNIQUE (tenant_id, request_id)
);
