-- Serves the personalized feed's "recent posts from followed creators/connections" candidate
-- query cheaply (author_id IN (...) AND created_at >= X) — the existing idx_posts_author and
-- idx_posts_created single-column indexes each only cover half of that access pattern.
ALTER TABLE posts ADD KEY idx_posts_author_created (author_id, created_at);
