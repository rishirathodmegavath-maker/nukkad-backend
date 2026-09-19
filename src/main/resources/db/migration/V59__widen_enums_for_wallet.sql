-- Widens the two native MySQL ENUM columns that gain new literals from the Wallet/Payments
-- feature. Every new table this feature introduces (wallets, wallet_transactions, payments)
-- deliberately uses VARCHAR + application-level enum validation instead of a native ENUM, to
-- avoid ever needing another migration like this one for its own future values. These two
-- columns predate that convention and must be widened the same way V26/V28/V31/V57 already did.

ALTER TABLE notifications
    MODIFY COLUMN type ENUM(
        'connection','idea_interest','opportunity','event','reply','endorsement',
        'recommendation','startup','chapter','investor','admin','wallet'
    ) NOT NULL;

ALTER TABLE user_notification_mutes
    MODIFY COLUMN type ENUM(
        'connection','idea_interest','opportunity','event','reply','endorsement',
        'recommendation','startup','chapter','investor','admin','wallet'
    ) NOT NULL;

ALTER TABLE audit_logs
    MODIFY COLUMN action ENUM(
        'LOGIN','LOGOUT','CREATE_IDEA','UPDATE_IDEA','DELETE_IDEA','CREATE_STARTUP',
        'CREATE_OPPORTUNITY','APPLY_OPPORTUNITY','INVESTOR_INTRODUCTION','ADMIN_ACTION',
        'ADMIN_USER_STATUS_CHANGED','ADMIN_USER_ROLE_CHANGED','ADMIN_REPORT_RESOLVED',
        'WALLET_ADMIN_ADJUSTMENT','PAYMENT_INITIATED','PAYMENT_SUCCEEDED','PAYMENT_FAILED'
    ) NOT NULL;
