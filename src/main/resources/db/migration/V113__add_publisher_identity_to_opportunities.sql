-- Same idea as posts.posted_as_platform/publisher_identity (V102/V109): lets an admin-published
-- opportunity show a named public display identity instead of the admin's own account name, while
-- posted_by_user_id keeps pointing at the real admin account either way. Defaulting every existing
-- row to FALSE/BUILDADDA changes nothing about what members currently see (an opportunity's poster
-- was never rendered anywhere except OpportunityDetailPage's real-user lookup, which stays exactly
-- as it is for every row this migration doesn't touch).
ALTER TABLE opportunities
    ADD COLUMN posted_as_platform BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN publisher_identity VARCHAR(30) NOT NULL DEFAULT 'BUILDADDA';

-- Backfill: an existing opportunity counts as unattributed platform content when its own
-- ADMIN_OPPORTUNITY_CREATED audit row's actor is the SAME id as posted_by_user_id - exactly the
-- join V102 used for posts, and for the same reason: an admin who attributed the posting to a real
-- member (postedByEmail set) ends up with posted_by_user_id != the acting admin, so that row
-- correctly fails this join and is left alone; only a genuinely unattributed admin posting matches.
UPDATE opportunities o
JOIN audit_logs a
  ON a.entity_type = 'Opportunity'
 AND a.entity_id = o.id
 AND a.action = 'ADMIN_OPPORTUNITY_CREATED'
 AND a.user_id = o.posted_by_user_id
SET o.posted_as_platform = TRUE
WHERE o.posted_as_platform = FALSE;

-- Deterministic identity distribution across the now-flagged platform opportunities, same
-- (created_at, id) round-robin technique as V110/V112 - idempotent, never re-reads its own output.
UPDATE opportunities o
JOIN (
    SELECT id,
           ELT(
               (ROW_NUMBER() OVER (ORDER BY created_at, id) - 1) % 5 + 1,
               'BUILDADDA', 'ARJUN_MEHTA', 'KARAN_SHAH', 'NEEL_KAPOOR', 'VIKRAM_RAO'
           ) AS assigned_identity
    FROM opportunities
    WHERE posted_as_platform = TRUE
) ranked ON ranked.id = o.id
SET o.publisher_identity = ranked.assigned_identity;
