CREATE TABLE resources (
  id                 CHAR(36)     NOT NULL,
  title              VARCHAR(200) NOT NULL,
  description        TEXT         NULL,
  type               ENUM('Document','Link','Video','Note','Template') NOT NULL,
  url                VARCHAR(500) NOT NULL,
  uploader_user_id   CHAR(36)     NOT NULL,
  chapter_id         CHAR(36)     NULL,
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_resources_chapter (chapter_id),
  KEY idx_resources_uploader (uploader_user_id),
  KEY idx_resources_type (type),
  FULLTEXT KEY ft_resources_search (title, description),
  CONSTRAINT fk_resources_uploader FOREIGN KEY (uploader_user_id) REFERENCES users(id),
  CONSTRAINT fk_resources_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE resource_tags (
  resource_id CHAR(36) NOT NULL,
  tag VARCHAR(50) NOT NULL,
  PRIMARY KEY (resource_id, tag),
  CONSTRAINT fk_rtag_resource FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE resource_saves (
  id           CHAR(36)  NOT NULL,
  resource_id  CHAR(36)  NOT NULL,
  user_id      CHAR(36)  NOT NULL,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_resource_saves_pair (resource_id, user_id),
  KEY idx_rsave_user (user_id),
  CONSTRAINT fk_rsave_resource FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE,
  CONSTRAINT fk_rsave_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
