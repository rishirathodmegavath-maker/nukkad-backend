-- An admin-published post left unattributed to any member (authorId ends up being the admin's own
-- account — see FeedService#createAsAdmin) was showing "Admin" as its public author, since that's
-- literally the bootstrap admin account's User.name (see AdminBootstrapRunner). This flag lets the
-- API tell the frontend to show the BuildAdda platform identity instead, without touching the
-- admin account's own name or hiding it from audit/admin-panel views (those still key off authorId).

ALTER TABLE posts
    ADD COLUMN posted_as_platform BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill: every already-existing post that was created by this same admin-published-unattributed
-- path also needs the flag, or it would keep showing "Admin" until it happened to be re-saved.
-- FeedService#createAsAdmin always writes an ADMIN_POST_CREATED audit row (user_id = the acting
-- admin, entity_id = the post) for both an unattributed and an attributed post — the only thing
-- that differs is who ends up as posts.author_id. Unattributed: author_id is left as that same
-- admin (authorId == adminId in the code). Attributed: author_id is the resolved member, which is
-- never equal to the admin's own id. So joining on "the audit row's actor is this post's author"
-- picks out exactly the unattributed admin posts — an ordinary member post never has a matching
-- ADMIN_POST_CREATED row at all, and an attributed admin post has one but fails the equality, so
-- both are correctly left untouched. Safe to re-run: rows already TRUE are simply excluded by the
-- WHERE and nothing changes.
UPDATE posts p
JOIN audit_logs a
  ON a.entity_type = 'Post'
 AND a.entity_id = p.id
 AND a.action = 'ADMIN_POST_CREATED'
 AND a.user_id = p.author_id
SET p.posted_as_platform = TRUE
WHERE p.posted_as_platform = FALSE;
