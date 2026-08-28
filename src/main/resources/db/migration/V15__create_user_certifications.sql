CREATE TABLE user_certifications (
  id             CHAR(36)     NOT NULL,
  user_id        CHAR(36)     NOT NULL,
  title          VARCHAR(200) NOT NULL,
  issuing_org    VARCHAR(200) NULL,
  issue_date     DATE         NULL,
  expiry_date    DATE         NULL,
  credential_id  VARCHAR(150) NULL,
  credential_url VARCHAR(300) NULL,
  sort_order     INT          NOT NULL DEFAULT 0,
  created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_certifications_user (user_id, sort_order),
  CONSTRAINT fk_user_certifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
