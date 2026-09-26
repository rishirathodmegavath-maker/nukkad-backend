-- Same idea as posts/opportunities/startups (V102/V109, V113, V114): lets an admin-published grant
-- listing show a named public display identity instead of the admin's own account name. This is
-- never the grant PROVIDER (a government body or VC, the existing free-text `provider` column) -
-- it only represents which BuildAdda curator to credit, exactly mirroring the same distinction
-- publisher_identity already draws for posts/opportunities/startups. Defaulting every existing row
-- to FALSE/BUILDADDA changes nothing about what members currently see (no grant surface has ever
-- rendered a curator identity before this - only `provider`).
ALTER TABLE grants
    ADD COLUMN posted_as_platform BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN publisher_identity VARCHAR(30) NOT NULL DEFAULT 'BUILDADDA';

-- Backfill: a grant counts as unattributed platform content when its own ADMIN_GRANT_CREATED audit
-- row's actor is the SAME id as created_by_user_id - the join V102 used for posts. This deliberately
-- never matches an AI-discovered grant (audit action AI_GRANT_DISCOVERED, not ADMIN_GRANT_CREATED)
-- or a CSV-imported one, so those correctly stay untouched - only a genuinely unattributed single
-- admin-create matches.
UPDATE grants g
JOIN audit_logs a
  ON a.entity_type = 'Grant'
 AND a.entity_id = g.id
 AND a.action = 'ADMIN_GRANT_CREATED'
 AND a.user_id = g.created_by_user_id
SET g.posted_as_platform = TRUE
WHERE g.posted_as_platform = FALSE;

-- Deterministic identity distribution across the now-flagged platform grants, same
-- (created_at, id) round-robin technique as V110/V112/V113/V114 - idempotent, never re-reads its
-- own output.
UPDATE grants g
JOIN (
    SELECT id,
           ELT(
               (ROW_NUMBER() OVER (ORDER BY created_at, id) - 1) % 5 + 1,
               'BUILDADDA', 'ARJUN_MEHTA', 'KARAN_SHAH', 'NEEL_KAPOOR', 'VIKRAM_RAO'
           ) AS assigned_identity
    FROM grants
    WHERE posted_as_platform = TRUE
) ranked ON ranked.id = g.id
SET g.publisher_identity = ranked.assigned_identity;
