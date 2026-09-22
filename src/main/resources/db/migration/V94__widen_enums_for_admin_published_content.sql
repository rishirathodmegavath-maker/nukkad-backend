-- Admins can now publish a Feed post, an Opportunity, or a Grant directly from the admin panel
-- (see FeedService#createAsAdmin, OpportunityService#postOpportunityAsAdmin,
-- GrantService#createGrantAsAdmin), mirroring how admin-created Startups already work. Two native
-- MySQL ENUM columns need widening for this, exactly as V78 widened both together for the
-- investor-activation feature (see V59's comment for why this class of column needs a migration on
-- every new literal): audit_logs for the three new admin-create actions, and notifications.type
-- for the one new 'post' notification (a member an admin attributes a post to, mirroring the
-- existing 'startup'/'opportunity'/'grant' "this was posted for you" notifications). No table or
-- data change: existing rows and every existing literal are untouched.

ALTER TABLE audit_logs
    MODIFY COLUMN action ENUM(
        'LOGIN','LOGOUT','CREATE_IDEA','UPDATE_IDEA','DELETE_IDEA','CREATE_STARTUP',
        'CREATE_OPPORTUNITY','APPLY_OPPORTUNITY','INVESTOR_INTRODUCTION','ADMIN_ACTION',
        'ADMIN_USER_STATUS_CHANGED','ADMIN_USER_ROLE_CHANGED','ADMIN_REPORT_RESOLVED',
        'ADMIN_CONTENT_REMOVED','ADMIN_CONTENT_RESTORED','ADMIN_CONTENT_APPROVED','ADMIN_CONTENT_REJECTED',
        'WALLET_ADMIN_ADJUSTMENT','PAYMENT_INITIATED','PAYMENT_SUCCEEDED','PAYMENT_FAILED',
        'WALLET_WITHDRAWAL_REQUESTED','WALLET_WITHDRAWAL_APPROVED','WALLET_WITHDRAWAL_REJECTED',
        'WALLET_STATUS_CHANGED','INVESTOR_ACTIVATION_APPROVED','INVESTOR_ACTIVATION_REJECTED',
        'ADMIN_PASSWORD_RESET_REQUESTED','ADMIN_PASSWORD_RESET_COMPLETED','ADMIN_PASSWORD_CHANGED',
        'WALLET_PIN_SET','WALLET_PIN_CHANGED','WALLET_PIN_RESET','WALLET_PIN_LOCKED',
        'ADMIN_RESOURCE_CREATED','ADMIN_RESOURCE_UPDATED','ADMIN_RESOURCE_DELETED',
        'ADMIN_STARTUP_CREATED',
        'ADMIN_INVESTOR_CREATED','ADMIN_INVESTOR_UPDATED','ADMIN_INVESTOR_DELETED','ADMIN_INVESTOR_INTRO_CLOSED',
        'ADMIN_INVESTOR_IMPORTED',
        'ADMIN_POST_CREATED','ADMIN_OPPORTUNITY_CREATED','ADMIN_GRANT_CREATED'
    ) NOT NULL;

ALTER TABLE notifications
    MODIFY COLUMN type ENUM(
        'connection','idea_interest','opportunity','event','reply','endorsement',
        'recommendation','startup','chapter','investor','admin','wallet','grant','investor_activation','post'
    ) NOT NULL;

ALTER TABLE user_notification_mutes
    MODIFY COLUMN type ENUM(
        'connection','idea_interest','opportunity','event','reply','endorsement',
        'recommendation','startup','chapter','investor','admin','wallet','grant','investor_activation','post'
    ) NOT NULL;
