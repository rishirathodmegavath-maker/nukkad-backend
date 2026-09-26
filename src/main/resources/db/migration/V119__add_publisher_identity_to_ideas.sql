-- Same idea as posts/opportunities/startups/grants (V102/V109, V113-115): lets an admin-published
-- idea show a named public display identity instead of the admin's own account name. No admin-create
-- endpoint has ever existed for ideas before this change, so there is no historical backfill to do
-- here (unlike V113/V114/V115) - every existing row was created by a real member through the one
-- existing member-facing endpoint and defaults correctly to FALSE/BUILDADDA, which is never
-- displayed anyway since IdeaDetailPage's creator block already always shows the real creator.
ALTER TABLE ideas
    ADD COLUMN posted_as_platform BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN publisher_identity VARCHAR(30) NOT NULL DEFAULT 'BUILDADDA';
