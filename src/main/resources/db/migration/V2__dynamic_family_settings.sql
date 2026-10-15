CREATE TABLE family_settings (
    family_id BIGINT NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    member_document_upload_enabled BIT(1) NOT NULL DEFAULT 1,
    member_memory_create_enabled BIT(1) NOT NULL DEFAULT 1,
    admin_invitation_enabled BIT(1) NOT NULL DEFAULT 1,
    document_expiry_reminder_days INT NOT NULL DEFAULT 30,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (family_id),
    CONSTRAINT fk_family_settings_family FOREIGN KEY (family_id) REFERENCES families (id) ON DELETE CASCADE
) ENGINE=InnoDB;

INSERT INTO family_settings (
    family_id,
    timezone,
    member_document_upload_enabled,
    member_memory_create_enabled,
    admin_invitation_enabled,
    document_expiry_reminder_days,
    version,
    updated_by,
    created_at,
    updated_at
)
SELECT id, 'UTC', 1, 1, 1, 30, 0, created_by, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM families;

CREATE TABLE family_notification_preferences (
    id BIGINT NOT NULL AUTO_INCREMENT,
    family_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    family_activity_enabled BIT(1) NOT NULL DEFAULT 1,
    document_expiry_enabled BIT(1) NOT NULL DEFAULT 1,
    muted_until DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_family_notification_preference UNIQUE (family_id, user_id),
    CONSTRAINT fk_family_notification_preference_family FOREIGN KEY (family_id) REFERENCES families (id) ON DELETE CASCADE,
    CONSTRAINT fk_family_notification_preference_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE family_settings_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    family_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    action VARCHAR(80) NOT NULL,
    changed_fields TEXT NOT NULL,
    request_id VARCHAR(80),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_family_settings_audit_family_created (family_id, created_at)
) ENGINE=InnoDB;

ALTER TABLE family_members
    ADD CONSTRAINT uk_family_members_family_user UNIQUE (family_id, user_id);

ALTER TABLE family_members
    ADD COLUMN owner_family_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN role = 'OWNER' THEN family_id ELSE NULL END) STORED,
    ADD CONSTRAINT uk_family_members_single_owner UNIQUE (owner_family_id);

CREATE INDEX idx_family_members_user ON family_members (user_id);
CREATE INDEX idx_documents_family ON documents (family_id);
CREATE INDEX idx_documents_family_folder ON documents (family_id, folder_id);
CREATE INDEX idx_document_folders_family ON document_folders (family_id);
CREATE INDEX idx_memories_family ON memories (family_id);
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at);
CREATE INDEX idx_notifications_user_read ON notifications (user_id, is_read);
CREATE INDEX idx_family_invitations_family_status ON family_invitations (family_id, status);
