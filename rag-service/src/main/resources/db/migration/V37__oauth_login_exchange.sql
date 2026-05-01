-- OAuth: short-lived one-time exchange codes to avoid putting JWTs in URLs.

CREATE TABLE oauth_login_exchange_codes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code_hash VARCHAR(128) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_oauth_login_exchange_codes_hash ON oauth_login_exchange_codes(code_hash);
CREATE INDEX idx_oauth_login_exchange_codes_user_id ON oauth_login_exchange_codes(user_id);

