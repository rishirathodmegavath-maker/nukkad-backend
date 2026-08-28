ALTER TABLE conversations
  ADD COLUMN muted_by_user_a      BOOLEAN   NOT NULL DEFAULT FALSE,
  ADD COLUMN muted_by_user_b      BOOLEAN   NOT NULL DEFAULT FALSE,
  ADD COLUMN nickname_by_user_a   VARCHAR(50) NULL,
  ADD COLUMN nickname_by_user_b   VARCHAR(50) NULL,
  ADD COLUMN deleted_at_by_user_a TIMESTAMP NULL,
  ADD COLUMN deleted_at_by_user_b TIMESTAMP NULL;

CREATE TABLE user_blocks (
  id          CHAR(36) NOT NULL,
  blocker_id  CHAR(36) NOT NULL,
  blocked_id  CHAR(36) NOT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_user_blocks_pair (blocker_id, blocked_id),
  KEY idx_user_blocks_blocked (blocked_id),
  CONSTRAINT ck_user_blocks_not_self CHECK (blocker_id <> blocked_id),
  CONSTRAINT fk_user_blocks_blocker FOREIGN KEY (blocker_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_blocks_blocked FOREIGN KEY (blocked_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reports (
  id                CHAR(36) NOT NULL,
  reporter_id       CHAR(36) NOT NULL,
  reported_user_id  CHAR(36) NOT NULL,
  conversation_id   CHAR(36) NULL,
  category          VARCHAR(60) NOT NULL,
  created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_reports_reported_user (reported_user_id),
  CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_reports_reported FOREIGN KEY (reported_user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_reports_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
