ALTER TABLE users ADD COLUMN chapter_joined_at TIMESTAMP NULL AFTER chapter_id;

-- Backfill: a chapter's president was never automatically counted as a member (chapter_id was
-- only set once they explicitly joined their own chapter), which produced the QA-visible bug of
-- a brand-new chapter showing "No members joined yet" while its president was displayed right
-- above it. Going forward, createChapter() auto-joins the creator; this backfills existing
-- chapters so their presidents show up as members too. Only applied when the president has no
-- chapter membership recorded yet, so an existing, deliberate membership elsewhere is untouched.
UPDATE users u
JOIN chapters c ON c.president_user_id = u.id
SET u.chapter_id = c.id, u.chapter_joined_at = c.created_at
WHERE u.chapter_id IS NULL;
