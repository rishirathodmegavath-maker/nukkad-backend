CREATE TABLE post_saves (
  id          CHAR(36) NOT NULL,
  post_id     CHAR(36) NOT NULL,
  user_id     CHAR(36) NOT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_post_saves_pair (post_id, user_id),
  KEY idx_post_saves_user (user_id),
  CONSTRAINT fk_post_saves_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
  CONSTRAINT fk_post_saves_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
