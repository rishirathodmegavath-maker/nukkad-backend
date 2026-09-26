-- Same idea as posts/opportunities/startups/grants (V102/V109, V113-115): lets a resource show a
-- named public curator identity instead of the admin's own account name. Unlike those other types,
-- every resource is admin-curated already (there is no member-create path at all - see
-- ResourceService's class comment), so there's no separate "unattributed platform content" boolean
-- here - publisher_identity is always meaningful, and every existing row is eligible for the
-- backfill below, not a filtered subset. This is never the `provider` column (e.g. "Y Combinator",
-- who actually made the content) - it only credits a BuildAdda curator.
ALTER TABLE resources
    ADD COLUMN publisher_identity VARCHAR(30) NOT NULL DEFAULT 'BUILDADDA';

-- Deterministic identity distribution across every existing resource, same (created_at, id)
-- round-robin technique as V110/V112/V113/V114/V115 - idempotent, never re-reads its own output.
UPDATE resources r
JOIN (
    SELECT id,
           ELT(
               (ROW_NUMBER() OVER (ORDER BY created_at, id) - 1) % 5 + 1,
               'BUILDADDA', 'ARJUN_MEHTA', 'KARAN_SHAH', 'NEEL_KAPOOR', 'VIKRAM_RAO'
           ) AS assigned_identity
    FROM resources
) ranked ON ranked.id = r.id
SET r.publisher_identity = ranked.assigned_identity;
