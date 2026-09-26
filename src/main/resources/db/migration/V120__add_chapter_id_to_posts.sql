ALTER TABLE posts
  ADD COLUMN chapter_id CHAR(36) NULL,
  ADD KEY idx_posts_chapter_id (chapter_id);
