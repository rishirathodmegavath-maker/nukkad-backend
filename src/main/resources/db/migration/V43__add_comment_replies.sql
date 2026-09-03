ALTER TABLE post_comments
  ADD COLUMN parent_comment_id CHAR(36) NULL AFTER post_id,
  ADD KEY idx_post_comments_parent (parent_comment_id, created_at),
  ADD CONSTRAINT fk_post_comments_parent FOREIGN KEY (parent_comment_id) REFERENCES post_comments(id) ON DELETE CASCADE;
