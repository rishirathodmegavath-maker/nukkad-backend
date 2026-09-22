-- Backs the scheduled AI grant-discovery pipeline (GrantDiscoveryService): source_url is the
-- official page an auto-published record was verified against (kept for every grant, not just
-- AI-discovered ones, since it's useful provenance on a manually-added grant too); discovery_origin
-- distinguishes an admin/member-entered grant from one the pipeline auto-published, so the admin
-- panel can filter/audit them separately; last_verified_at is bumped every time a recurring run
-- re-confirms an existing AI-discovered grant is still live, so a stale-but-never-revisited record
-- is visible as such.
ALTER TABLE grants
    ADD COLUMN source_url VARCHAR(500) NULL,
    ADD COLUMN discovery_origin VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN last_verified_at TIMESTAMP NULL;

-- One row per scheduled discovery attempt (see GrantDiscoveryScheduler/DiscoveryBatchCatalog) --
-- lets the admin panel show what the automated pipeline has actually been doing, and lets the
-- batch catalog round-robin (government, topic) combinations by least-recently-run instead of
-- always hitting the same one.
CREATE TABLE grant_discovery_runs (
    id                CHAR(36)     NOT NULL,
    batch_government  VARCHAR(100) NOT NULL,
    batch_topic       VARCHAR(100) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    schemes_found     INT          NOT NULL DEFAULT 0,
    schemes_created   INT          NOT NULL DEFAULT 0,
    schemes_updated   INT          NOT NULL DEFAULT 0,
    schemes_rejected  INT          NOT NULL DEFAULT 0,
    error_message     TEXT         NULL,
    started_at        TIMESTAMP    NOT NULL,
    finished_at       TIMESTAMP    NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
