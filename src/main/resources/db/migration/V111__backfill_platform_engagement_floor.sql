-- Every eligible platform post should carry a baseline platform_engagement_count of at least 15
-- (purely an internal number added to the real likesCount wherever something chooses to surface
-- that sum — nothing in the member-facing UI does anymore, see PostCard.tsx). This never touches
-- likesCount, never inserts a post_likes row, never touches a member post, and never lowers a
-- platform post that's already at or above the floor. Idempotent by construction: the WHERE clause
-- itself excludes every row already satisfying it, so running this again changes nothing.
UPDATE posts
SET platform_engagement_count = 15
WHERE posted_as_platform = TRUE
  AND platform_engagement_count < 15;
