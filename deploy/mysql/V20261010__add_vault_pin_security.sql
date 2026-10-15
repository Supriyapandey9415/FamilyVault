-- Run once before starting the production application with JPA_DDL_AUTO=validate.
ALTER TABLE users
    ADD COLUMN vault_pin_hash VARCHAR(100) NULL,
    ADD COLUMN vault_pin_failed_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN vault_pin_locked_until DATETIME(6) NULL;
