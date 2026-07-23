CREATE TABLE refresh_tokens (

    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,

    token_hash VARCHAR(64) NOT NULL UNIQUE,

    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    revoked_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
            REFERENCES users(id)

);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
