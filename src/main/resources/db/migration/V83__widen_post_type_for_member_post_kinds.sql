-- Members can now pick what kind of post they are writing. The four new literals (discussion, build_update,
-- question, milestone) join the original five; existing rows and their default ('text') are unchanged.
ALTER TABLE posts
  MODIFY COLUMN type ENUM('text','startup_update','idea','opportunity','event','discussion','build_update','question','milestone') NOT NULL DEFAULT 'text';
