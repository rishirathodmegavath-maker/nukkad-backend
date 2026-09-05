-- Disambiguate any pre-existing duplicate chapter names (case/whitespace-insensitive) before adding
-- the uniqueness constraint below, without touching any other data or relationships. Chapters are
-- never looked up by name (only by id) anywhere in the app, so a cosmetic rename of an older
-- duplicate is safe — merging or deleting rows instead would orphan the members, ideas, startups,
-- opportunities, events, and resources already foreign-keyed to that chapter, which is far riskier.
UPDATE chapters c
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY LOWER(TRIM(name)) ORDER BY created_at, id) AS rn
    FROM chapters
) ranked ON ranked.id = c.id
SET c.name = CONCAT(TRIM(c.name), ' (', ranked.rn, ')')
WHERE ranked.rn > 1;

-- The table's collation (utf8mb4_0900_ai_ci, set in V1) is already case/accent-insensitive, so this
-- constraint alone catches "Nukkad SF" vs "nukkad sf" for free — ChapterService additionally trims
-- input before the pre-check below, since collation doesn't normalize leading/trailing whitespace.
ALTER TABLE chapters ADD UNIQUE KEY uq_chapters_name (name);
