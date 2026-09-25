-- A manager adding someone to a startup's team now sends an invitation the person has to accept (see
-- StartupService#addMember), instead of putting them straight on the team. INVITED is that waiting state.
-- Appending a value to the end of a MySQL ENUM is a metadata-only change: no rows are rewritten and every
-- existing ACTIVE / PENDING / REJECTED row keeps its value.
ALTER TABLE startup_team_members
  MODIFY COLUMN status ENUM('ACTIVE','PENDING','REJECTED','INVITED') NOT NULL DEFAULT 'ACTIVE';
