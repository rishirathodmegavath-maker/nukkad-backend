-- Feed posts had no admin removal path and no way for a user to report a specific post — see
-- V68 (Idea/Startup/Opportunity's equivalent removedByAdmin) for the identical rationale.
-- reports.post_id lets a report target a specific post (mirrors the existing nullable
-- conversation_id) instead of only ever reporting the person behind it.

ALTER TABLE posts
    ADD COLUMN removed_by_admin BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN removal_reason VARCHAR(500) NULL;

ALTER TABLE reports
    ADD COLUMN post_id CHAR(36) NULL;
