ALTER TABLE chapters ADD COLUMN president_user_id CHAR(36) NULL AFTER cover_image_url;
ALTER TABLE chapters ADD CONSTRAINT fk_chapters_president FOREIGN KEY (president_user_id) REFERENCES users(id);

CREATE TABLE president_applications (
  id CHAR(36) NOT NULL,
  chapter_id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  motivation TEXT NOT NULL,
  status ENUM('Pending','Approved','Rejected') NOT NULL DEFAULT 'Pending',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_papp_chapter (chapter_id),
  KEY idx_papp_user (user_id),
  CONSTRAINT fk_papp_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(id),
  CONSTRAINT fk_papp_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
