-- The six BuildAdda department-style identities (BUILDADDA, BUILDADDA_INSIGHTS, ...) are being
-- replaced outright by four named publishers (ARJUN_MEHTA, KARAN_SHAH, NEEL_KAPOOR, VIKRAM_RAO) -
-- see Post.PublisherIdentity. Because the Java enum constant SET changes (not just labels), every
-- row's stored string has to move to one of the four new names or Hibernate throws
-- IllegalArgumentException reading it back - including a MEMBER post, whose publisher_identity is
-- never displayed but still has to deserialize (it currently holds the old meaningless default
-- 'BUILDADDA', which is no longer a valid constant at all).
--
-- Column default first, so any raw INSERT that skips the column also lands on a valid value.
ALTER TABLE posts
    MODIFY COLUMN publisher_identity VARCHAR(30) NOT NULL DEFAULT 'ARJUN_MEHTA';

-- Member posts: any valid new-enum value works (display logic always gates on posted_as_platform
-- first), so they all move to the new meaningless default, exactly as they all held the old
-- meaningless default before.
UPDATE posts
SET publisher_identity = 'ARJUN_MEHTA'
WHERE posted_as_platform = FALSE;

-- Platform posts: reassigned with the same deterministic technique V110 used for the six-way
-- split, now round-robining across the four new names instead. Ordering by (created_at, id) -
-- id breaks ties since created_at is only second-precision - makes this a pure function of
-- immutable columns, so it's idempotent (re-running it recomputes the identical mapping) and
-- never depends on whatever the row's V110-era value happened to be.
UPDATE posts p
JOIN (
    SELECT id,
           ELT(
               (ROW_NUMBER() OVER (ORDER BY created_at, id) - 1) % 4 + 1,
               'ARJUN_MEHTA', 'KARAN_SHAH', 'NEEL_KAPOOR', 'VIKRAM_RAO'
           ) AS assigned_identity
    FROM posts
    WHERE posted_as_platform = TRUE
) ranked ON ranked.id = p.id
SET p.publisher_identity = ranked.assigned_identity;
