CREATE TABLE user_endorsements (
  id               CHAR(36)     NOT NULL,
  endorsed_user_id CHAR(36)     NOT NULL,
  endorser_user_id CHAR(36)     NOT NULL,
  skill            VARCHAR(100) NOT NULL,
  created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_user_endorsements_triple (endorsed_user_id, endorser_user_id, skill),
  KEY idx_user_endorsements_endorsed (endorsed_user_id, skill),
  CONSTRAINT fk_user_endorsements_endorsed FOREIGN KEY (endorsed_user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_endorsements_endorser FOREIGN KEY (endorser_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_recommendations (
  id                CHAR(36)     NOT NULL,
  subject_user_id   CHAR(36)     NOT NULL,
  author_user_id    CHAR(36)     NOT NULL,
  relationship      VARCHAR(100) NULL,
  body              TEXT         NOT NULL,
  status            ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  responded_at      TIMESTAMP    NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_user_recommendations_pair (subject_user_id, author_user_id),
  KEY idx_user_recommendations_subject (subject_user_id, status),
  CONSTRAINT fk_user_recommendations_subject FOREIGN KEY (subject_user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_recommendations_author FOREIGN KEY (author_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE notifications MODIFY COLUMN type ENUM('connection','idea_interest','opportunity','event','reply','endorsement','recommendation') NOT NULL;
