CREATE TABLE chapter_proposals (
  id CHAR(36) NOT NULL,
  proposer_user_id CHAR(36) NOT NULL,
  name VARCHAR(150) NOT NULL,
  city VARCHAR(100),
  country VARCHAR(100),
  description TEXT,
  cover_image_url VARCHAR(500),
  status ENUM('Pending','Approved','Rejected') NOT NULL DEFAULT 'Pending',
  resulting_chapter_id CHAR(36),
  reviewed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_chproposal_proposer (proposer_user_id),
  KEY idx_chproposal_status (status),
  CONSTRAINT fk_chproposal_proposer FOREIGN KEY (proposer_user_id) REFERENCES users(id),
  CONSTRAINT fk_chproposal_chapter FOREIGN KEY (resulting_chapter_id) REFERENCES chapters(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
