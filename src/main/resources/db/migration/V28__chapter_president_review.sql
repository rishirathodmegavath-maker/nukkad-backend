ALTER TABLE president_applications ADD COLUMN reviewed_at TIMESTAMP NULL AFTER status;

ALTER TABLE notifications MODIFY COLUMN type ENUM('connection','idea_interest','opportunity','event','reply','endorsement','recommendation','startup','chapter') NOT NULL;
ALTER TABLE user_notification_mutes MODIFY COLUMN type ENUM('connection','idea_interest','opportunity','event','reply','endorsement','recommendation','startup','chapter') NOT NULL;
