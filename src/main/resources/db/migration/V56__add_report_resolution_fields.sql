ALTER TABLE reports
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'OPEN' AFTER category,
    ADD COLUMN resolved_by_user_id CHAR(36) NULL AFTER status,
    ADD COLUMN resolved_at TIMESTAMP NULL AFTER resolved_by_user_id,
    ADD COLUMN resolution_note TEXT NULL AFTER resolved_at;
