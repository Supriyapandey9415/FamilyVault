ALTER TABLE users ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN vault_version BIGINT NOT NULL DEFAULT 0;

-- Outstanding links issued by the insecure legacy reset flow are intentionally invalidated.
DELETE FROM password_reset_tokens;
CREATE UNIQUE INDEX uk_reset_token_hash ON password_reset_tokens (token);
