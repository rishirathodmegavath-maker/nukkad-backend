-- One row per (user, topic) the personalized feed engine has learned about — topic_kind
-- distinguishes a free-text hashtag from a Post.Type value, since those are the only two
-- categorization signals that exist on a Post today. score accumulates on every relevant signal
-- (like/save/comment/...) and is decayed at READ time (see UserTopicAffinityService), not by a
-- scheduled job, so this table never needs a cron sweep to stay meaningful.
CREATE TABLE user_topic_affinity (
  id          CHAR(36) NOT NULL,
  user_id     CHAR(36) NOT NULL,
  topic_kind  VARCHAR(20) NOT NULL,
  topic_key   VARCHAR(50) NOT NULL,
  score       DOUBLE NOT NULL DEFAULT 0,
  updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_user_topic_affinity (user_id, topic_kind, topic_key),
  CONSTRAINT fk_user_topic_affinity_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
