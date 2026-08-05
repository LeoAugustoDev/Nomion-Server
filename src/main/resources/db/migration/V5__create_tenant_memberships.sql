CREATE TABLE tenant_memberships (

    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,

    tenant_id UUID NOT NULL,

    status VARCHAR(20) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_tenant_memberships_user
        FOREIGN KEY (user_id)
            REFERENCES users(id),

    CONSTRAINT fk_tenant_memberships_tenant
        FOREIGN KEY (tenant_id)
            REFERENCES tenants(id),

    CONSTRAINT uq_tenant_memberships_user_tenant
        UNIQUE (user_id, tenant_id),

    CONSTRAINT ck_tenant_memberships_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED'))

);

CREATE INDEX idx_tenant_memberships_tenant_status
    ON tenant_memberships(tenant_id, status);

CREATE INDEX idx_tenant_memberships_user_status
    ON tenant_memberships(user_id, status);

CREATE TABLE tenant_selection_tokens (

    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,

    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    consumed_at TIMESTAMP WITH TIME ZONE,

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_tenant_selection_tokens_user
        FOREIGN KEY (user_id)
            REFERENCES users(id)

);

CREATE INDEX idx_tenant_selection_tokens_active_expiration
    ON tenant_selection_tokens(expires_at)
    WHERE consumed_at IS NULL;

INSERT INTO tenant_memberships (
    id,
    user_id,
    tenant_id,
    status
)
SELECT
    gen_random_uuid(),
    id,
    tenant_id,
    'ACTIVE'
FROM users;

ALTER TABLE refresh_tokens
    ADD COLUMN membership_id UUID;

ALTER TABLE refresh_tokens
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE refresh_tokens
SET membership_id = tenant_memberships.id
FROM tenant_memberships
WHERE refresh_tokens.user_id = tenant_memberships.user_id;

ALTER TABLE refresh_tokens
    ALTER COLUMN membership_id SET NOT NULL;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_membership
        FOREIGN KEY (membership_id)
            REFERENCES tenant_memberships(id);

CREATE INDEX idx_refresh_tokens_membership_id
    ON refresh_tokens(membership_id);

CREATE INDEX idx_refresh_tokens_active_expiration
    ON refresh_tokens(expires_at)
    WHERE revoked_at IS NULL;

ALTER TABLE refresh_tokens
    DROP CONSTRAINT fk_refresh_tokens_user;

ALTER TABLE refresh_tokens
    DROP COLUMN user_id;

ALTER TABLE users
    DROP CONSTRAINT fk_users_tenant;

ALTER TABLE users
    DROP COLUMN tenant_id;
