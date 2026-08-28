-- Full rollback of the admin/platform-owner concept and the two review-gated features that
-- existed only to be admin-approved (chapter proposals, investor activation requests), plus the
-- admin-only president-application review flow. Existing migrations (V5, V28, V32-V36) are left
-- untouched per Flyway convention; this migration undoes their effects going forward.

-- ---- Drop the review-gated feature tables ----
DROP TABLE IF EXISTS investor_activation_request_sectors;
DROP TABLE IF EXISTS investor_activation_request_stages;
DROP TABLE IF EXISTS investor_activation_request_geographies;
DROP TABLE IF EXISTS investor_activation_requests;
DROP TABLE IF EXISTS chapter_proposals;
DROP TABLE IF EXISTS president_applications;

-- ---- Remove the admin notification type: delete orphaned rows first, then shrink the enum ----
DELETE FROM notifications WHERE type = 'admin';
DELETE FROM user_notification_mutes WHERE type = 'admin';
ALTER TABLE notifications MODIFY COLUMN type ENUM('connection','idea_interest','opportunity','event','reply','endorsement','recommendation','startup','chapter','investor') NOT NULL;
ALTER TABLE user_notification_mutes MODIFY COLUMN type ENUM('connection','idea_interest','opportunity','event','reply','endorsement','recommendation','startup','chapter','investor') NOT NULL;

-- ---- Remove the ADMIN security role: delete role rows first, then shrink the enum ----
DELETE FROM user_security_roles WHERE role = 'ADMIN';
ALTER TABLE user_security_roles MODIFY COLUMN role ENUM('USER','FOUNDER','INVESTOR','CHAPTER_PRESIDENT') NOT NULL;

-- ---- Remove the platform-owner flag entirely ----
ALTER TABLE users DROP COLUMN is_platform_owner;
