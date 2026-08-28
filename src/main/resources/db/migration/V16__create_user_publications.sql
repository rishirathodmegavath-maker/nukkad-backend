CREATE TABLE user_publications (
  id           CHAR(36)     NOT NULL,
  user_id      CHAR(36)     NOT NULL,
  title        VARCHAR(250) NOT NULL,
  publisher    VARCHAR(200) NULL,
  publish_date DATE         NULL,
  description  TEXT         NULL,
  url          VARCHAR(300) NULL,
  sort_order   INT          NOT NULL DEFAULT 0,
  created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_publications_user (user_id, sort_order),
  CONSTRAINT fk_user_publications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
