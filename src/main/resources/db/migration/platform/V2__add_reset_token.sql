ALTER TABLE knox.tenant_users
ADD COLUMN reset_token VARCHAR(255),
ADD COLUMN reset_token_expiry TIMESTAMP;

CREATE INDEX idx_tenant_users_reset_token ON knox.tenant_users(reset_token);
