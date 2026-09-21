-- #hashtags in a post's text, one row per (post, tag): they power "Trending Topics" and opening the feed on one tag.
-- Rows are written when a post is created or its text is edited (see Hashtags / FeedService); posts that already exist
-- are filled in once at startup by PostHashtagBackfillRunner, which uses the same extractor.
CREATE TABLE post_hashtags (
  id          CHAR(36)    NOT NULL,
  post_id     CHAR(36)    NOT NULL,
  tag         VARCHAR(50) NOT NULL,
  created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_post_hashtags_pair (post_id, tag),
  KEY idx_post_hashtags_tag (tag, post_id),
  CONSTRAINT fk_post_hashtags_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
