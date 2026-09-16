ALTER TABLE opportunities
  ADD COLUMN equity VARCHAR(100) NULL AFTER compensation,
  ADD COLUMN experience_level VARCHAR(100) NULL AFTER equity,
  ADD COLUMN application_deadline TIMESTAMP NULL AFTER experience_level;
