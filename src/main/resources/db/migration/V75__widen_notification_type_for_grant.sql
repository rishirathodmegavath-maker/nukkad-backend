-- Adds the 'grant' literal so a grant-review notification can carry its own dedicated type
-- (routing to /grants/{id} on the frontend) instead of being misfiled under an unrelated type
-- whose click-through would route somewhere else entirely. Mirrors V26/V28/V31/V33/V59's pattern
-- for this same pair of native MySQL ENUM columns.

ALTER TABLE notifications
    MODIFY COLUMN type ENUM(
        'connection','idea_interest','opportunity','event','reply','endorsement',
        'recommendation','startup','chapter','investor','admin','wallet','grant'
    ) NOT NULL;

ALTER TABLE user_notification_mutes
    MODIFY COLUMN type ENUM(
        'connection','idea_interest','opportunity','event','reply','endorsement',
        'recommendation','startup','chapter','investor','admin','wallet','grant'
    ) NOT NULL;
