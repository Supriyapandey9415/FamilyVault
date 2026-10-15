CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    auth_provider VARCHAR(20) NOT NULL,
    google_id VARCHAR(100),
    avatar_url VARCHAR(500),
    email_verified BIT(1) NOT NULL,
    vault_pin_hash VARCHAR(100),
    vault_pin_failed_attempts INT NOT NULL DEFAULT 0,
    vault_pin_locked_until DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_google_id UNIQUE (google_id)
) ENGINE=InnoDB;

CREATE TABLE families (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE family_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    family_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(255) NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_family_members_family FOREIGN KEY (family_id) REFERENCES families (id)
) ENGINE=InnoDB;

CREATE TABLE family_invitations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    family_id BIGINT NOT NULL,
    invited_email VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    invited_by_user_id BIGINT NOT NULL,
    status VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    accepted_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_family_invitation_token UNIQUE (token_hash),
    CONSTRAINT fk_family_invitations_family FOREIGN KEY (family_id) REFERENCES families (id)
) ENGINE=InnoDB;

CREATE TABLE document_folders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    family_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    hidden BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    family_id BIGINT NOT NULL,
    uploaded_by BIGINT NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    document_type VARCHAR(255),
    file_path VARCHAR(255) NOT NULL,
    expiry_date DATE,
    folder_id BIGINT,
    hidden BIT(1) NOT NULL,
    password_protected BIT(1) NOT NULL,
    password_hash VARCHAR(100),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE memories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    family_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    memory_date DATE,
    location VARCHAR(255),
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE memory_media (
    id BIGINT NOT NULL AUTO_INCREMENT,
    memory_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    file_type VARCHAR(255) NOT NULL,
    uploaded_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_memory_media_memory FOREIGN KEY (memory_id) REFERENCES memories (id)
) ENGINE=InnoDB;

CREATE TABLE notification_preferences (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    in_app_enabled BIT(1) NOT NULL,
    family_activity_enabled BIT(1) NOT NULL,
    invitation_enabled BIT(1) NOT NULL,
    document_expiry_enabled BIT(1) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_preferences_user UNIQUE (user_id)
) ENGINE=InnoDB;

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    family_id BIGINT NOT NULL,
    message VARCHAR(1000) NOT NULL,
    type VARCHAR(64),
    title VARCHAR(180),
    actor_user_id BIGINT,
    entity_type VARCHAR(64),
    entity_id BIGINT,
    action_url VARCHAR(500),
    dedupe_key VARCHAR(180),
    is_read BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notifications_dedupe UNIQUE (dedupe_key)
) ENGINE=InnoDB;

CREATE TABLE password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token VARCHAR(120) NOT NULL,
    user_id BIGINT NOT NULL,
    expiry_date DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    attempt_count INT DEFAULT 0,
    used BIT(1) DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_reset_token_user (user_id),
    INDEX idx_reset_token_val (token),
    CONSTRAINT fk_reset_token_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE smtp_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    from_email VARCHAR(255) NOT NULL,
    from_name VARCHAR(255) NOT NULL,
    auth BIT(1) NOT NULL,
    starttls BIT(1) NOT NULL,
    active BIT(1) NOT NULL,
    updated_at DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB;
