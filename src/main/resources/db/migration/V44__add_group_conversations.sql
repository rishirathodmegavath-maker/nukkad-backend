ALTER TABLE conversations
  ADD COLUMN conversation_type ENUM('DIRECT','GROUP') NOT NULL DEFAULT 'DIRECT' AFTER id,
  ADD COLUMN group_name VARCHAR(100) NULL AFTER conversation_type,
  ADD COLUMN group_avatar_url VARCHAR(500) NULL AFTER group_name,
  ADD COLUMN created_by CHAR(36) NULL AFTER group_avatar_url,
  MODIFY COLUMN user_a_id CHAR(36) NULL,
  MODIFY COLUMN user_b_id CHAR(36) NULL;

CREATE TABLE conversation_participants (
  id              CHAR(36) NOT NULL,
  conversation_id CHAR(36) NOT NULL,
  user_id         CHAR(36) NOT NULL,
  role            ENUM('ADMIN','MEMBER') NOT NULL DEFAULT 'MEMBER',
  joined_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  muted_at        TIMESTAMP NULL,
  nickname        VARCHAR(50) NULL,
  last_read_at    TIMESTAMP NULL,
  deleted_at      TIMESTAMP NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_conversation_participant (conversation_id, user_id),
  KEY idx_conversation_participants_user (user_id),
  CONSTRAINT fk_conv_participants_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
  CONSTRAINT fk_conv_participants_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
