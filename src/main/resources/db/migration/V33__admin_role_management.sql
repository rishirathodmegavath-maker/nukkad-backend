ALTER TABLE notifications MODIFY COLUMN type ENUM('connection','idea_interest','opportunity','event','reply','endorsement','recommendation','startup','chapter','investor','admin') NOT NULL;
ALTER TABLE user_notification_mutes MODIFY COLUMN type ENUM('connection','idea_interest','opportunity','event','reply','endorsement','recommendation','startup','chapter','investor','admin') NOT NULL;
