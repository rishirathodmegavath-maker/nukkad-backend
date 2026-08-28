CREATE TABLE user_notification_mutes (
  user_id CHAR(36) NOT NULL,
  type ENUM('connection','idea_interest','opportunity','event','reply','endorsement','recommendation') NOT NULL,
  PRIMARY KEY (user_id, type),
  CONSTRAINT fk_user_notification_mutes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE muted_accounts (
  id CHAR(36) NOT NULL, muter_id CHAR(36) NOT NULL, muted_id CHAR(36) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uq_muted_accounts_pair (muter_id, muted_id),
  CONSTRAINT fk_muted_accounts_muter FOREIGN KEY (muter_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_muted_accounts_muted FOREIGN KEY (muted_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
