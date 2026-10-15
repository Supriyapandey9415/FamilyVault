CREATE TABLE user_auth_identities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    provider_email VARCHAR(255),
    email_verified BIT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    last_login_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_identity_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uk_auth_identity_user_provider UNIQUE (user_id, provider),
    CONSTRAINT fk_auth_identity_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE apple_auth_sessions (
    state_hash VARCHAR(64) NOT NULL,
    nonce VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (state_hash),
    INDEX idx_apple_auth_sessions_expires_at (expires_at)
) ENGINE=InnoDB;
