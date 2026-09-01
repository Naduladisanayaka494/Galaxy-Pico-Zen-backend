-- =====================================================================
--  Password reset tokens for tenant logins (Flyway V2, platform schema)
--
--  Same shape and principles as knox.refresh_tokens:
--    * token_hash, never the raw token — a DB leak must not hand out
--      live, usable reset links.
--    * single-use: used_at is stamped the moment the token is spent.
--    * short-lived: expires_at is minutes, not days (see
--      galaxy.password-reset.expiration-minutes).
--
--  A row belongs to knox.tenant_users, the platform identity directory —
--  the reset flow runs before any tenant schema is on the search_path,
--  exactly like login.
-- =====================================================================

CREATE TABLE knox.password_reset_tokens (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_user_id BIGINT      NOT NULL REFERENCES knox.tenant_users(id) ON DELETE CASCADE,
    token_hash     TEXT        NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    used_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_password_reset_tokens_hash ON knox.password_reset_tokens (token_hash);
-- The purge job and "invalidate this user's outstanding links" both scan by
-- user among the not-yet-spent rows; keep those fast to find.
CREATE INDEX idx_password_reset_tokens_active
    ON knox.password_reset_tokens (tenant_user_id) WHERE used_at IS NULL;
