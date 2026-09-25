-- Two additive, purely-cosmetic columns on posts, both meaningful only when posted_as_platform is
-- true (see Post.java's field comments):
--
-- publisher_identity: which BuildAdda editorial identity to show for a platform post (BuildAdda,
-- BuildAdda Insights, BuildAdda Grants, BuildAdda Community, BuildAdda Startup Desk, BuildAdda
-- Editorial) — still exactly ONE real admin account behind every one of them (author_id is
-- unchanged), this only changes the *displayed* name. Existing platform posts already show
-- "BuildAdda" today (hardcoded on the frontend), so defaulting every existing row to BUILDADDA here
-- changes nothing about what members currently see.
--
-- platform_engagement_count: a seeded engagement number ADDED to the real likes_count for display
-- only. It is never a post_likes row, so it can never appear in "who liked this", can never be
-- produced by a member's own Like/Unlike, and is deliberately kept OUT of the feed-ranking
-- engagement-velocity signal (PersonalizedFeedService reads only the real likes_count). Defaults to
-- 0 for every existing and future row — nothing is backfilled by this migration; a later, separately
-- approved backfill would set it only for eligible existing platform posts.

ALTER TABLE posts
    ADD COLUMN publisher_identity VARCHAR(30) NOT NULL DEFAULT 'BUILDADDA',
    ADD COLUMN platform_engagement_count INT NOT NULL DEFAULT 0;
