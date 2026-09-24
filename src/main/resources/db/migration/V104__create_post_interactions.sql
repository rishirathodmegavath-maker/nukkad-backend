-- Real behavioral-signal rows for the personalized feed engine: one per action, piggybacked on
-- the existing like/save/comment/hide/open endpoints (see FeedService). Deliberately excludes an
-- IMPRESSION row per feed response — it would be the single highest-volume, lowest-signal row in
-- this design, and dedup during scrolling is handled client-side (excludeIds), not by this table.
CREATE TABLE post_interactions (
  id                CHAR(36) NOT NULL,
  user_id           CHAR(36) NOT NULL,
  post_id           CHAR(36) NOT NULL,
  interaction_type  VARCHAR(20) NOT NULL,
  created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_post_interactions_user_created (user_id, created_at),
  KEY idx_post_interactions_post (post_id),
  CONSTRAINT fk_post_interactions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_post_interactions_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
