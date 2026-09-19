ALTER TABLE ideas
  ADD COLUMN removed_by_admin BOOLEAN NOT NULL DEFAULT FALSE AFTER chapter_id,
  ADD COLUMN removal_reason VARCHAR(500) NULL AFTER removed_by_admin;

ALTER TABLE startups
  ADD COLUMN removed_by_admin BOOLEAN NOT NULL DEFAULT FALSE AFTER is_raising,
  ADD COLUMN removal_reason VARCHAR(500) NULL AFTER removed_by_admin;

ALTER TABLE opportunities
  ADD COLUMN removed_by_admin BOOLEAN NOT NULL DEFAULT FALSE AFTER closed,
  ADD COLUMN removal_reason VARCHAR(500) NULL AFTER removed_by_admin;
