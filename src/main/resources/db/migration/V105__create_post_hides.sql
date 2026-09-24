-- Mirrors post_saves exactly. "Hide this post" is the personalized feed engine's strong-negative
-- signal and its own candidate-pool exclusion filter — a hidden post never reappears in the
-- viewer's personalized feed, but (unlike a topic-level exclusion) it says nothing about that
-- topic in general: liking other AI posts still raises AI content, this specific post just never
-- comes back.
CREATE TABLE post_hides (
  id          CHAR(36) NOT NULL,
  post_id     CHAR(36) NOT NULL,
  user_id     CHAR(36) NOT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_post_hides_pair (post_id, user_id),
  KEY idx_post_hides_user (user_id),
  CONSTRAINT fk_post_hides_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
  CONSTRAINT fk_post_hides_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
