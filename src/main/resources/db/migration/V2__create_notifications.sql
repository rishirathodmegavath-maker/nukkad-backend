CREATE TABLE notifications (
  id            CHAR(36) NOT NULL,
  user_id       CHAR(36) NOT NULL,
  type          ENUM('connection','idea_interest','opportunity','event','reply') NOT NULL,
  title         VARCHAR(200) NOT NULL,
  message       VARCHAR(500) NOT NULL,
  related_id    CHAR(36) NULL,
  actor_user_id CHAR(36) NULL,
  is_read       BOOLEAN NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_notif_user_created (user_id, created_at),
  KEY idx_notif_user_unread (user_id, is_read),
  CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_notif_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
