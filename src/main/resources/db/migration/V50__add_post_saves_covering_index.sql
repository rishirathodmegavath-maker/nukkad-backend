-- Covers the new dedicated Saved Posts query (WHERE user_id = ? ORDER BY created_at [ASC|DESC], id)
-- so MySQL can satisfy the filter + sort directly from the index instead of a filesort per page.
ALTER TABLE post_saves ADD KEY idx_post_saves_user_created (user_id, created_at, id);
