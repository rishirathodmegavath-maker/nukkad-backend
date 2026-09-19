-- Mirrors V68 (admin content removal) + V70 (pre-publish moderation) for Ideas/Startups/Opportunities
-- — Grants was added later (V66) and never got either mechanism, leaving user-submitted grants
-- (including their external application_url) live instantly with zero admin recourse.

ALTER TABLE grants
    ADD COLUMN removed_by_admin BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN removal_reason VARCHAR(500) NULL,
    -- DB default APPROVED: any pre-existing grant (already live) stays visible unchanged. The
    -- Java-level @Builder.Default of PENDING is what actually gates every future submission.
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN rejection_reason VARCHAR(500) NULL,
    ADD COLUMN moderation_reviewed_by CHAR(36) NULL,
    ADD COLUMN moderation_reviewed_at TIMESTAMP NULL;
