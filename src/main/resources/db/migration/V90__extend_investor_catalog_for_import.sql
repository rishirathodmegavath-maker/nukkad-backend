-- Extends the admin-managed investor catalog (V88) with the fields a real investor dataset carries
-- (company_url/domain, country, industries, program, investment/exit counts, key people, social links,
-- private contact details) and adds the bulk CSV import pipeline: a batch record per upload (progress +
-- counts, polled by the admin UI) and a per-row issue log (what happened to every row that wasn't a clean
-- success — see InvestorImportService). Nothing here changes the meaning of an existing column; every new
-- investors column is nullable so existing rows (created by hand, with none of this data) are unaffected.

ALTER TABLE investors
    ADD COLUMN external_source_id     VARCHAR(100)  NULL AFTER id,
    ADD COLUMN country                VARCHAR(100)  NULL AFTER location,
    ADD COLUMN domain                 VARCHAR(255)  NULL AFTER website,
    ADD COLUMN investment_count       INT           NULL,
    ADD COLUMN exit_count             INT           NULL,
    -- Admin-only — never sent in the founder-facing InvestorDto (see InvestorMapper).
    ADD COLUMN contact_email          VARCHAR(255)  NULL,
    ADD COLUMN contact_email_verified BOOLEAN       NULL,
    ADD COLUMN secondary_email        VARCHAR(255)  NULL,
    ADD COLUMN phone_number           VARCHAR(50)   NULL,
    ADD COLUMN facebook_url           VARCHAR(300)  NULL,
    ADD COLUMN instagram_url          VARCHAR(300)  NULL,
    ADD COLUMN linkedin_url           VARCHAR(300)  NULL,
    ADD COLUMN twitter_url            VARCHAR(300)  NULL,
    -- Which import last created/updated this row, for traceability — nullable, hand-added rows have none.
    ADD COLUMN source_batch_id        CHAR(36)      NULL,
    ADD UNIQUE INDEX uq_investors_external_source_id (external_source_id),
    ADD INDEX idx_investors_country (country);

CREATE TABLE investor_programs (
    investor_id CHAR(36)     NOT NULL,
    program     VARCHAR(255) NOT NULL,
    CONSTRAINT fk_investor_programs_investor FOREIGN KEY (investor_id) REFERENCES investors (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investor_key_people (
    investor_id CHAR(36)     NOT NULL,
    person      VARCHAR(255) NOT NULL,
    CONSTRAINT fk_investor_key_people_investor FOREIGN KEY (investor_id) REFERENCES investors (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One row per CSV upload an admin confirms. Processing happens off-request (InvestorImportService is
-- @Async), so the admin UI polls this row for progress instead of one call blocking on the whole file.
CREATE TABLE investor_import_batches (
    id                CHAR(36)     NOT NULL PRIMARY KEY,
    admin_id          CHAR(36)     NOT NULL,
    original_filename VARCHAR(255) NULL,
    -- The exact file uploaded, kept for re-download/audit — reuses FileStorageService#storeResourceFile,
    -- not a new storage path.
    source_file_url   VARCHAR(500) NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    total_rows        INT          NOT NULL DEFAULT 0,
    processed_rows    INT          NOT NULL DEFAULT 0,
    created_count     INT          NOT NULL DEFAULT 0,
    updated_count     INT          NOT NULL DEFAULT 0,
    skipped_count     INT          NOT NULL DEFAULT 0,
    failed_count      INT          NOT NULL DEFAULT 0,
    error_message     VARCHAR(1000) NULL,
    started_at        TIMESTAMP    NULL,
    completed_at      TIMESTAMP    NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_investor_import_batches_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Every row that wasn't a clean, unremarkable success: a hard failure (ERROR — the row was skipped
-- entirely, e.g. no company name) or a soft anomaly the row still imported despite (WARNING — e.g. an
-- unrecognised investor_type defaulted to "Other"). A clean CSV of 100,000 good rows produces zero rows
-- here; this table is the exception report, not a full audit log of every row.
CREATE TABLE investor_import_issues (
    id                CHAR(36)      NOT NULL PRIMARY KEY,
    batch_id          CHAR(36)      NOT NULL,
    row_number        INT           NOT NULL,
    external_source_id VARCHAR(100) NULL,
    investor_name     VARCHAR(200)  NULL,
    severity          VARCHAR(10)   NOT NULL,
    message           VARCHAR(500)  NOT NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_investor_import_issues_batch FOREIGN KEY (batch_id) REFERENCES investor_import_batches (id) ON DELETE CASCADE,
    INDEX idx_investor_import_issues_batch (batch_id, row_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
