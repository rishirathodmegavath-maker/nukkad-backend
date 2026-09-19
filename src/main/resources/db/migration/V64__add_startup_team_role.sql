ALTER TABLE startup_team_members
  ADD COLUMN team_role ENUM('FOUNDER','ADMIN','MEMBER') NOT NULL DEFAULT 'MEMBER' AFTER is_founder;

UPDATE startup_team_members SET team_role = 'FOUNDER' WHERE is_founder = TRUE;

ALTER TABLE startup_team_members DROP COLUMN is_founder;
