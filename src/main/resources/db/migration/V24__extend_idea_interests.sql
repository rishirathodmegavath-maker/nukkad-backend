ALTER TABLE idea_interests
  ADD COLUMN status ENUM('Pending','Shortlisted','Accepted','Rejected','Withdrawn') NOT NULL DEFAULT 'Pending' AFTER contribution_area,
  ADD COLUMN reviewed_at TIMESTAMP NULL,
  ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  ADD KEY idx_iint_idea_status (idea_id, status),
  ADD KEY idx_iint_user (user_id);

CREATE TABLE idea_interest_skills (
  idea_interest_id CHAR(36) NOT NULL,
  sort_order INT NOT NULL,
  skill VARCHAR(150) NOT NULL,
  PRIMARY KEY (idea_interest_id, sort_order),
  CONSTRAINT fk_iiskill_interest FOREIGN KEY (idea_interest_id) REFERENCES idea_interests(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE idea_interest_experiences (
  idea_interest_id CHAR(36) NOT NULL,
  sort_order INT NOT NULL,
  experience_id CHAR(36) NOT NULL,
  PRIMARY KEY (idea_interest_id, sort_order),
  CONSTRAINT fk_iiexp_interest FOREIGN KEY (idea_interest_id) REFERENCES idea_interests(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE idea_interest_projects (
  idea_interest_id CHAR(36) NOT NULL,
  sort_order INT NOT NULL,
  project_id CHAR(36) NOT NULL,
  PRIMARY KEY (idea_interest_id, sort_order),
  CONSTRAINT fk_iiproj_interest FOREIGN KEY (idea_interest_id) REFERENCES idea_interests(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
