-- V37 removed the ADMIN literal (along with the old admin-approval-gate feature set: chapter
-- proposals, president applications, investor activation requests, is_platform_owner). Those
-- removed features stay removed — this migration does not restore them. It only re-adds the
-- ADMIN role literal and three new audit actions for the new, narrower platform-administration
-- surface (user status/role management, report review, audit visibility) being added now.

ALTER TABLE user_security_roles
    MODIFY COLUMN role ENUM('USER','FOUNDER','INVESTOR','CHAPTER_PRESIDENT','ADMIN') NOT NULL;

ALTER TABLE audit_logs
    MODIFY COLUMN action ENUM(
        'LOGIN','LOGOUT','CREATE_IDEA','UPDATE_IDEA','DELETE_IDEA','CREATE_STARTUP',
        'CREATE_OPPORTUNITY','APPLY_OPPORTUNITY','INVESTOR_INTRODUCTION','ADMIN_ACTION',
        'ADMIN_USER_STATUS_CHANGED','ADMIN_USER_ROLE_CHANGED','ADMIN_REPORT_RESOLVED'
    ) NOT NULL;
