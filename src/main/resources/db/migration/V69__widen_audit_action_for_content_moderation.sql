-- audit_logs.action is a native MySQL ENUM (see V59's comment on why this column needs widening
-- on every new AuditAction literal, unlike newer tables which use VARCHAR + app-level validation).
-- Adds the two literals introduced for admin content moderation (remove/restore an Idea, Startup
-- or Opportunity).

ALTER TABLE audit_logs
    MODIFY COLUMN action ENUM(
        'LOGIN','LOGOUT','CREATE_IDEA','UPDATE_IDEA','DELETE_IDEA','CREATE_STARTUP',
        'CREATE_OPPORTUNITY','APPLY_OPPORTUNITY','INVESTOR_INTRODUCTION','ADMIN_ACTION',
        'ADMIN_USER_STATUS_CHANGED','ADMIN_USER_ROLE_CHANGED','ADMIN_REPORT_RESOLVED',
        'ADMIN_CONTENT_REMOVED','ADMIN_CONTENT_RESTORED',
        'WALLET_ADMIN_ADJUSTMENT','PAYMENT_INITIATED','PAYMENT_SUCCEEDED','PAYMENT_FAILED'
    ) NOT NULL;
