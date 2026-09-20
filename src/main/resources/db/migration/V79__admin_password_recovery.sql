-- Admin password recovery (forgot / reset / change password for the admin portal).
--
-- 1) password_reset_tokens.audience: a reset token minted for an ADMIN must only ever be redeemable
--    through the admin flow (which also invalidates the still-live access token), and a MEMBER token
--    only through the member flow. Without this column both flows would accept each other's tokens,
--    so an emailed admin reset link could be redeemed via the member endpoint, which does not bump
--    tokenVersion. Existing rows are all member tokens, hence the DEFAULT.
--
-- 2) audit_logs.action is a native MySQL ENUM (see V59/V78 for why every new AuditAction literal
--    needs its own migration): widen it with the three admin password events so they can be recorded.

ALTER TABLE password_reset_tokens
    ADD COLUMN audience VARCHAR(10) NOT NULL DEFAULT 'MEMBER' AFTER user_id;

ALTER TABLE audit_logs
    MODIFY COLUMN action ENUM(
        'LOGIN','LOGOUT','CREATE_IDEA','UPDATE_IDEA','DELETE_IDEA','CREATE_STARTUP',
        'CREATE_OPPORTUNITY','APPLY_OPPORTUNITY','INVESTOR_INTRODUCTION','ADMIN_ACTION',
        'ADMIN_USER_STATUS_CHANGED','ADMIN_USER_ROLE_CHANGED','ADMIN_REPORT_RESOLVED',
        'ADMIN_CONTENT_REMOVED','ADMIN_CONTENT_RESTORED','ADMIN_CONTENT_APPROVED','ADMIN_CONTENT_REJECTED',
        'WALLET_ADMIN_ADJUSTMENT','PAYMENT_INITIATED','PAYMENT_SUCCEEDED','PAYMENT_FAILED',
        'WALLET_WITHDRAWAL_REQUESTED','WALLET_WITHDRAWAL_APPROVED','WALLET_WITHDRAWAL_REJECTED',
        'WALLET_STATUS_CHANGED','INVESTOR_ACTIVATION_APPROVED','INVESTOR_ACTIVATION_REJECTED',
        'ADMIN_PASSWORD_RESET_REQUESTED','ADMIN_PASSWORD_RESET_COMPLETED','ADMIN_PASSWORD_CHANGED'
    ) NOT NULL;
