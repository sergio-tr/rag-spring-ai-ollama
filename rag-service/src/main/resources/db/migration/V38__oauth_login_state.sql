-- OAuth: short-lived one-time state tokens for CSRF protection.

CREATE TABLE oauth_login_state_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    state_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_oauth_login_state_tokens_hash ON oauth_login_state_tokens(state_hash);

