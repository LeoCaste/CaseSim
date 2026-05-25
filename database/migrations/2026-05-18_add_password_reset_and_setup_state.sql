-- Incremental compatibility migration for existing CaseSim databases.
-- Safe to run multiple times.

BEGIN;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS platform_setup_state (
    id BIGINT PRIMARY KEY,
    initialized BOOLEAN NOT NULL DEFAULT FALSE,
    initialized_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO platform_setup_state (id, initialized)
VALUES (1, FALSE)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS password_reset_token (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_password_reset_token_hash
    ON password_reset_token(token_hash);

CREATE INDEX IF NOT EXISTS idx_password_reset_token_user
    ON password_reset_token(user_id);

COMMIT;
