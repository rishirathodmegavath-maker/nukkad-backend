ALTER TABLE opportunity_applicants
  ADD COLUMN status ENUM('Pending','Shortlisted','Accepted','Rejected','Withdrawn') NOT NULL DEFAULT 'Pending' AFTER user_id,
  ADD COLUMN why_interested TEXT NULL,
  ADD COLUMN why_good_fit TEXT NULL,
  ADD COLUMN availability ENUM('Full-time','Part-time','Weekends','Not available') NULL,
  ADD COLUMN expected_commitment VARCHAR(100) NULL,
  ADD COLUMN additional_message TEXT NULL,
  ADD COLUMN reviewed_at TIMESTAMP NULL,
  ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  ADD KEY idx_oapp_opportunity_status (opportunity_id, status),
  ADD KEY idx_oapp_user (user_id);

CREATE TABLE opportunity_applicant_skills (
  opportunity_applicant_id CHAR(36) NOT NULL,
  sort_order INT NOT NULL,
  skill VARCHAR(150) NOT NULL,
  PRIMARY KEY (opportunity_applicant_id, sort_order),
  CONSTRAINT fk_oaskill_app FOREIGN KEY (opportunity_applicant_id) REFERENCES opportunity_applicants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE opportunity_applicant_experiences (
  opportunity_applicant_id CHAR(36) NOT NULL,
  sort_order INT NOT NULL,
  experience_id CHAR(36) NOT NULL,
  PRIMARY KEY (opportunity_applicant_id, sort_order),
  CONSTRAINT fk_oaexp_app FOREIGN KEY (opportunity_applicant_id) REFERENCES opportunity_applicants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE opportunity_applicant_projects (
  opportunity_applicant_id CHAR(36) NOT NULL,
  sort_order INT NOT NULL,
  project_id CHAR(36) NOT NULL,
  PRIMARY KEY (opportunity_applicant_id, sort_order),
  CONSTRAINT fk_oaproj_app FOREIGN KEY (opportunity_applicant_id) REFERENCES opportunity_applicants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
