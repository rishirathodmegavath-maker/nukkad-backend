-- Same idea as posts/opportunities (V102/V109, V113): lets an admin-added startup show a named
-- public display identity instead of the admin's own account name. There is no separate "creator"
-- column on startups (ownership lives entirely in startup_team_members) - this flag exists purely
-- to record "the admin's own account ended up as the FOUNDER" without disturbing that real row at
-- all. Defaulting every existing row to FALSE/BUILDADDA changes nothing about what members currently
-- see (no startup surface has ever rendered a "curated by" identity before this).
ALTER TABLE startups
    ADD COLUMN posted_as_platform BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN publisher_identity VARCHAR(30) NOT NULL DEFAULT 'BUILDADDA';

-- Backfill: a startup counts as unattributed platform content when its own ADMIN_STARTUP_CREATED
-- audit row's actor is the SAME user who ended up as its FOUNDER team member - the join V102 used
-- for posts, adapted for startups' two-table ownership model. An admin who attributed the startup
-- to a real member (founderEmail set) ends up with a different FOUNDER id than the acting admin, so
-- that row correctly fails this join and is left alone.
UPDATE startups s
JOIN audit_logs a
  ON a.entity_type = 'Startup'
 AND a.entity_id = s.id
 AND a.action = 'ADMIN_STARTUP_CREATED'
JOIN startup_team_members tm
  ON tm.startup_id = s.id
 AND tm.team_role = 'FOUNDER'
 AND tm.user_id = a.user_id
SET s.posted_as_platform = TRUE
WHERE s.posted_as_platform = FALSE;

-- Deterministic identity distribution across the now-flagged platform startups, same
-- (created_at, id) round-robin technique as V110/V112/V113 - idempotent, never re-reads its own
-- output.
UPDATE startups s
JOIN (
    SELECT id,
           ELT(
               (ROW_NUMBER() OVER (ORDER BY created_at, id) - 1) % 5 + 1,
               'BUILDADDA', 'ARJUN_MEHTA', 'KARAN_SHAH', 'NEEL_KAPOOR', 'VIKRAM_RAO'
           ) AS assigned_identity
    FROM startups
    WHERE posted_as_platform = TRUE
) ranked ON ranked.id = s.id
SET s.publisher_identity = ranked.assigned_identity;
