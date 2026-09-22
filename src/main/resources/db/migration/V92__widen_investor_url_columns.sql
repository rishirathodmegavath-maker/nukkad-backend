-- Real-world investor exports sometimes carry a social/share-widget URL instead of a plain profile link
-- (e.g. "https://twitter.com/share?url=...&text=<a whole encoded article>"), which blew straight past the
-- original VARCHAR(300)/VARCHAR(255) limits below and failed the row's insert during the ~115k-row CSV/Excel
-- import this catalog is built for (see InvestorCsvParser/InvestorImportWorker). None of these columns are
-- indexed, so widening them is a plain in-place ALTER with no index to rebuild and no data at risk — every
-- existing value already fits comfortably inside the new length.
ALTER TABLE investors
    MODIFY COLUMN website      VARCHAR(2048) NULL,
    MODIFY COLUMN domain       VARCHAR(2048) NULL,
    MODIFY COLUMN facebook_url  VARCHAR(2048) NULL,
    MODIFY COLUMN instagram_url VARCHAR(2048) NULL,
    MODIFY COLUMN linkedin_url  VARCHAR(2048) NULL,
    MODIFY COLUMN twitter_url   VARCHAR(2048) NULL;
