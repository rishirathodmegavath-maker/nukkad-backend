-- investor_activation_requests was created by V36 but never wired to any application code — any
-- authenticated user could self-declare as an investor via POST /api/investors with zero review.
-- This table is still empty (nothing has ever written to it), so it's safe to reshape directly
-- rather than widen-in-place: normalizes status to the same UPPERCASE convention every other
-- status enum in this codebase uses (ModerationStatus, WithdrawalStatus, ReportStatus, ...), and
-- adds the review-note/reviewed-by columns the original schema was missing (it had reviewed_at
-- but no reviewed_by, and no way to record why a request was rejected).

ALTER TABLE investor_activation_requests
    MODIFY COLUMN status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    ADD COLUMN review_note VARCHAR(500) NULL,
    ADD COLUMN reviewed_by CHAR(36) NULL,
    ADD CONSTRAINT fk_invactreq_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id);
