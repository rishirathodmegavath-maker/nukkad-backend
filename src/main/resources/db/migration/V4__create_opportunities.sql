CREATE TABLE opportunities (
  id                 CHAR(36)     NOT NULL,
  title              VARCHAR(200) NOT NULL,
  type               ENUM('Full-time','Internship','Founding Role','Co-founder','Startup Project','AI/ML Role','Campus') NOT NULL,
  startup_id         CHAR(36)     NULL,
  organization_name  VARCHAR(200) NOT NULL,
  location           VARCHAR(200) NULL,
  remote             BOOLEAN      NOT NULL DEFAULT FALSE,
  description        TEXT         NOT NULL,
  compensation       VARCHAR(200) NULL,
  posted_by_user_id  CHAR(36)     NOT NULL,
  chapter_id         CHAR(36)     NULL,
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_opp_type (type),
  KEY idx_opp_chapter (chapter_id),
  KEY idx_opp_startup (startup_id),
  FULLTEXT KEY ft_opp_search (title, description, organization_name),
  CONSTRAINT fk_opp_startup FOREIGN KEY (startup_id) REFERENCES startups(id),
  CONSTRAINT fk_opp_poster FOREIGN KEY (posted_by_user_id) REFERENCES users(id),
  CONSTRAINT fk_opp_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE opportunity_requirements (
  opportunity_id CHAR(36) NOT NULL,
  sort_order     INT NOT NULL,
  requirement    VARCHAR(300) NOT NULL,
  PRIMARY KEY (opportunity_id, sort_order),
  CONSTRAINT fk_oreq_opp FOREIGN KEY (opportunity_id) REFERENCES opportunities(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE opportunity_applicants (
  id CHAR(36) NOT NULL,
  opportunity_id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_oapp (opportunity_id, user_id),
  CONSTRAINT fk_oapp_opp FOREIGN KEY (opportunity_id) REFERENCES opportunities(id) ON DELETE CASCADE,
  CONSTRAINT fk_oapp_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE opportunity_interests (
  id CHAR(36) NOT NULL,
  opportunity_id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_oint (opportunity_id, user_id),
  CONSTRAINT fk_oint_opp FOREIGN KEY (opportunity_id) REFERENCES opportunities(id) ON DELETE CASCADE,
  CONSTRAINT fk_oint_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
