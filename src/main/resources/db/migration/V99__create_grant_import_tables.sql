-- Manual, zero-API-cost bulk import for Grants (CSV/Excel), replacing the disabled Gemini
-- discovery pipeline for populating the Grants list: an admin uploads a spreadsheet instead of
-- paying for AI-driven "discovery" -- see GrantImportService/GrantImportWorker. Mirrors
-- investor_import_batches/investor_import_issues (V90) exactly, minus an "updated" counter --
-- grants have no natural external-id column to dedupe by, so every imported row always creates a
-- new Grant (a re-upload of the same file is caught at the preview step, not deduped server-side).

CREATE TABLE grant_import_batches (
    id                CHAR(36)     NOT NULL PRIMARY KEY,
    admin_id          CHAR(36)     NOT NULL,
    original_filename VARCHAR(255) NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    total_rows        INT          NOT NULL DEFAULT 0,
    processed_rows    INT          NOT NULL DEFAULT 0,
    created_count     INT          NOT NULL DEFAULT 0,
    skipped_count     INT          NOT NULL DEFAULT 0,
    failed_count      INT          NOT NULL DEFAULT 0,
    error_message     VARCHAR(1000) NULL,
    started_at        TIMESTAMP    NULL,
    completed_at      TIMESTAMP    NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_grant_import_batches_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- row_num, not row_number: MySQL 8.0+ reserves ROW_NUMBER (the window function), so an unquoted
-- `row_number` column fails with a syntax error (ER_PARSE_ERROR) at CREATE TABLE time.
CREATE TABLE grant_import_issues (
    id           CHAR(36)     NOT NULL PRIMARY KEY,
    batch_id     CHAR(36)     NOT NULL,
    row_num      INT          NOT NULL,
    grant_name   VARCHAR(200) NULL,
    severity     VARCHAR(10)  NOT NULL,
    message      VARCHAR(500) NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_grant_import_issues_batch FOREIGN KEY (batch_id) REFERENCES grant_import_batches (id) ON DELETE CASCADE,
    INDEX idx_grant_import_issues_batch (batch_id, row_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
