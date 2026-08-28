CREATE TABLE message_deletions (
    id CHAR(36) NOT NULL,
    message_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    deleted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_message_deletion_user (message_id, user_id),
    KEY idx_message_deletions_user (user_id),
    KEY idx_message_deletions_message (message_id),
    CONSTRAINT fk_message_deletions_message
        FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_deletions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
