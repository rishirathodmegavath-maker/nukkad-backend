-- Pre-publish approval gate for Ideas/Startups/Opportunities. DEFAULT 'APPROVED' is deliberate:
-- every row that already exists is already live in production and must stay publicly visible
-- unchanged. Only new rows going forward are set to 'PENDING' at the application layer
-- (Idea/Startup/Opportunity's @Builder.Default) so only newly created content is gated.

ALTER TABLE ideas
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN rejection_reason VARCHAR(500) NULL,
    ADD COLUMN moderation_reviewed_by CHAR(36) NULL,
    ADD COLUMN moderation_reviewed_at TIMESTAMP NULL;

ALTER TABLE startups
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN rejection_reason VARCHAR(500) NULL,
    ADD COLUMN moderation_reviewed_by CHAR(36) NULL,
    ADD COLUMN moderation_reviewed_at TIMESTAMP NULL;

ALTER TABLE opportunities
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN rejection_reason VARCHAR(500) NULL,
    ADD COLUMN moderation_reviewed_by CHAR(36) NULL,
    ADD COLUMN moderation_reviewed_at TIMESTAMP NULL;
