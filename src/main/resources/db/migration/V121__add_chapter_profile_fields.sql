ALTER TABLE chapters
  ADD COLUMN institution VARCHAR(150) NULL,
  ADD COLUMN type VARCHAR(50) NULL;

CREATE TABLE chapter_focus_areas (
  chapter_id CHAR(36) NOT NULL,
  focus_area VARCHAR(50) NOT NULL,
  PRIMARY KEY (chapter_id, focus_area),
  CONSTRAINT fk_chapter_focus_areas_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
