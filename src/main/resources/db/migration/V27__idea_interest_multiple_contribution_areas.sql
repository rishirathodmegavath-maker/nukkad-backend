CREATE TABLE idea_interest_contribution_areas (
  idea_interest_id  CHAR(36) NOT NULL,
  contribution_area ENUM('AI/ML','Technology','Product','Design','Marketing','Sales','Operations','Domain Expertise') NOT NULL,
  PRIMARY KEY (idea_interest_id, contribution_area),
  CONSTRAINT fk_iica_interest FOREIGN KEY (idea_interest_id) REFERENCES idea_interests(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO idea_interest_contribution_areas (idea_interest_id, contribution_area)
SELECT id, contribution_area FROM idea_interests;

ALTER TABLE idea_interests DROP COLUMN contribution_area;
