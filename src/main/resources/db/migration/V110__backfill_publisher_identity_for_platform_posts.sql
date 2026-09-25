-- V109 gave every post a publisher_identity column defaulting to BUILDADDA, so every existing
-- platform post (posted_as_platform = true) still shows plain "BuildAdda" even though an admin can
-- now pick from six identities for a *new* platform post. This backfill makes the six identities
-- apply to history too, without creating any user, like, or other row.
--
-- Distribution: ROW_NUMBER() OVER (ORDER BY created_at, id) gives every platform post a stable
-- sequence number — created_at alone isn't unique (it's second-precision, see V9's comment on the
-- same gotcha for messages), so id (a UUID, never reused or changed) breaks ties deterministically.
-- ELT() then round-robins that sequence across the six identities in a fixed order, 1-for-1, so the
-- split is as even as the row count allows (e.g. 120 posts -> exactly 20 each).
--
-- Idempotent by construction: the assignment is a pure function of created_at/id, which never
-- change, and never reads the row's *current* publisher_identity — so running this UPDATE again
-- (whether by Flyway repair/replay or by hand) recomputes the identical mapping and changes nothing.
-- Nothing else about a post — author_id, likes_count, post_likes, comments, created_at itself — is
-- touched. Member posts (posted_as_platform = false) are excluded by the WHERE and never appear in
-- the ranked subquery at all, so they keep whatever publisher_identity they already had (the
-- meaningless BUILDADDA default from V109, since display logic always gates on posted_as_platform
-- first — see Post.java).
UPDATE posts p
JOIN (
    SELECT id,
           ELT(
               (ROW_NUMBER() OVER (ORDER BY created_at, id) - 1) % 6 + 1,
               'BUILDADDA', 'BUILDADDA_INSIGHTS', 'BUILDADDA_GRANTS',
               'BUILDADDA_COMMUNITY', 'BUILDADDA_STARTUP_DESK', 'BUILDADDA_EDITORIAL'
           ) AS assigned_identity
    FROM posts
    WHERE posted_as_platform = TRUE
) ranked ON ranked.id = p.id
SET p.publisher_identity = ranked.assigned_identity;
