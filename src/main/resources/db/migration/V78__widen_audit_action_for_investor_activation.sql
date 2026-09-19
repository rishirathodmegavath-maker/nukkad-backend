-- Two native MySQL ENUM columns need widening for the investor-activation-request feature (see
-- V59's comment for why this class of column needs a migration on every new literal): audit_logs
-- for the admin approve/reject action, and notifications.type for its own dedicated notification
-- type — reusing the existing 'investor' type (already used for IntroRequest notifications, which
-- route to /investors/requests) would misroute an activation decision's click-through.

ALTER TABLE audit_logs
    MODIFY COLUMN action ENUM(
        'LOGIN','LOGOUT','CREATE_IDEA','UPDATE_IDEA','DELETE_IDEA','CREATE_STARTUP',
        'CREATE_OPPORTUNITY','APPLY_OPPORTUNITY','INVESTOR_INTRODUCTION','ADMIN_ACTION',
        'ADMIN_USER_STATUS_CHANGED','ADMIN_USER_ROLE_CHANGED','ADMIN_REPORT_RESOLVED',
        'ADMIN_CONTENT_REMOVED','ADMIN_CONTENT_RESTORED','ADMIN_CONTENT_APPROVED','ADMIN_CONTENT_REJECTED',
        'WALLET_ADMIN_ADJUSTMENT','PAYMENT_INITIATED','PAYMENT_SUCCEEDED','PAYMENT_FAILED',
        'WALLET_WITHDRAWAL_REQUESTED','WALLET_WITHDRAWAL_APPROVED','WALLET_WITHDRAWAL_REJECTED',
        'WALLET_STATUS_CHANGED','INVESTOR_ACTIVATION_APPROVED','INVESTOR_ACTIVATION_REJECTED'
    ) NOT NULL;

ALTER TABLE notifications
    MODIFY COLUMN type ENUM(
        'connection','idea_interest','opportunity','event','reply','endorsement',
        'recommendation','startup','chapter','investor','admin','wallet','grant','investor_activation'
    ) NOT NULL;

ALTER TABLE user_notification_mutes
    MODIFY COLUMN type ENUM(
        'connection','idea_interest','opportunity','event','reply','endorsement',
        'recommendation','startup','chapter','investor','admin','wallet','grant','investor_activation'
    ) NOT NULL;
