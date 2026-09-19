ALTER TABLE opportunities
  ADD COLUMN work_mode ENUM('Remote','Hybrid','In-person') NOT NULL DEFAULT 'In-person' AFTER remote,
  ADD COLUMN responsibilities TEXT NULL AFTER description;

UPDATE opportunities SET work_mode = 'Remote' WHERE remote = TRUE;

ALTER TABLE opportunities DROP COLUMN remote;

CREATE TABLE opportunity_required_skills (
  opportunity_id CHAR(36) NOT NULL,
  sort_order     INT NOT NULL,
  skill          VARCHAR(300) NOT NULL,
  PRIMARY KEY (opportunity_id, sort_order),
  CONSTRAINT fk_oskill_opp FOREIGN KEY (opportunity_id) REFERENCES opportunities(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
