CREATE TABLE ideas (
  id               CHAR(36)     NOT NULL,
  title            VARCHAR(200) NOT NULL,
  problem          TEXT         NOT NULL,
  solution         TEXT         NOT NULL,
  target_customer  VARCHAR(300) NULL,
  stage            ENUM('Concept','Validating','Building','Launched') NOT NULL DEFAULT 'Concept',
  category         VARCHAR(100) NULL,
  creator_id       CHAR(36)     NOT NULL,
  chapter_id       CHAR(36)     NULL,
  startup_id       CHAR(36)     NULL,
  created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ideas_creator (creator_id),
  KEY idx_ideas_chapter (chapter_id),
  KEY idx_ideas_stage (stage),
  KEY idx_ideas_created_at (created_at),
  FULLTEXT KEY ft_ideas_search (title, problem, solution),
  CONSTRAINT fk_ideas_creator FOREIGN KEY (creator_id) REFERENCES users(id),
  CONSTRAINT fk_ideas_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE idea_tags (
  idea_id CHAR(36) NOT NULL,
  tag VARCHAR(50) NOT NULL,
  PRIMARY KEY (idea_id, tag),
  CONSTRAINT fk_itag_idea FOREIGN KEY (idea_id) REFERENCES ideas(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE idea_help_needed (
  idea_id CHAR(36) NOT NULL,
  contribution_area ENUM('AI/ML','Technology','Product','Design','Marketing','Sales','Operations','Domain Expertise') NOT NULL,
  PRIMARY KEY (idea_id, contribution_area),
  CONSTRAINT fk_ihelp_idea FOREIGN KEY (idea_id) REFERENCES ideas(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE idea_team_members (
  idea_id    CHAR(36) NOT NULL,
  user_id    CHAR(36) NOT NULL,
  joined_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (idea_id, user_id),
  CONSTRAINT fk_iteam_idea FOREIGN KEY (idea_id) REFERENCES ideas(id) ON DELETE CASCADE,
  CONSTRAINT fk_iteam_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE idea_interests (
  id                 CHAR(36) NOT NULL,
  idea_id            CHAR(36) NOT NULL,
  user_id            CHAR(36) NOT NULL,
  contribution_area  ENUM('AI/ML','Technology','Product','Design','Marketing','Sales','Operations','Domain Expertise') NOT NULL,
  message            TEXT NULL,
  created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_idea_interest (idea_id, user_id),
  CONSTRAINT fk_iint_idea FOREIGN KEY (idea_id) REFERENCES ideas(id) ON DELETE CASCADE,
  CONSTRAINT fk_iint_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE startups (
  id           CHAR(36)     NOT NULL,
  name         VARCHAR(200) NOT NULL,
  logo_url     VARCHAR(500) NULL,
  tagline      VARCHAR(300) NULL,
  sector       VARCHAR(100) NULL,
  problem      TEXT NULL,
  solution     TEXT NULL,
  stage        ENUM('Idea','MVP','Early Traction','Growth','Scaling') NOT NULL DEFAULT 'Idea',
  traction     TEXT NULL,
  idea_id      CHAR(36) NULL,
  chapter_id   CHAR(36) NULL,
  is_raising   BOOLEAN NOT NULL DEFAULT FALSE,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_startups_sector (sector),
  KEY idx_startups_stage (stage),
  KEY idx_startups_chapter (chapter_id),
  KEY idx_startups_is_raising (is_raising),
  FULLTEXT KEY ft_startups_search (name, tagline, problem, solution),
  CONSTRAINT fk_startups_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE ideas    ADD CONSTRAINT fk_ideas_startup FOREIGN KEY (startup_id) REFERENCES startups(id);
ALTER TABLE startups ADD CONSTRAINT fk_startups_idea FOREIGN KEY (idea_id) REFERENCES ideas(id);
ALTER TABLE startups ADD UNIQUE KEY uq_startups_idea_id (idea_id);

CREATE TABLE startup_needs (
  startup_id CHAR(36) NOT NULL,
  need VARCHAR(100) NOT NULL,
  PRIMARY KEY (startup_id, need),
  CONSTRAINT fk_sneed_startup FOREIGN KEY (startup_id) REFERENCES startups(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE startup_team_members (
  id          CHAR(36) NOT NULL,
  startup_id  CHAR(36) NOT NULL,
  user_id     CHAR(36) NOT NULL,
  role        VARCHAR(150) NULL,
  is_founder  BOOLEAN NOT NULL DEFAULT FALSE,
  status      ENUM('ACTIVE','PENDING') NOT NULL DEFAULT 'ACTIVE',
  role_id     CHAR(36) NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_startup_team_user (startup_id, user_id),
  KEY idx_stm_user (user_id),
  CONSTRAINT fk_stm_startup FOREIGN KEY (startup_id) REFERENCES startups(id) ON DELETE CASCADE,
  CONSTRAINT fk_stm_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE startup_updates (
  id CHAR(36) NOT NULL,
  startup_id CHAR(36) NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_supd_startup (startup_id, created_at),
  CONSTRAINT fk_supd_startup FOREIGN KEY (startup_id) REFERENCES startups(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE startup_roles (
  id CHAR(36) NOT NULL,
  startup_id CHAR(36) NOT NULL,
  title VARCHAR(200) NOT NULL,
  type ENUM('Job','Internship','Founding Role') NOT NULL,
  location VARCHAR(200) NULL,
  remote BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_srole_startup (startup_id),
  CONSTRAINT fk_srole_startup FOREIGN KEY (startup_id) REFERENCES startups(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE startup_follows (
  user_id CHAR(36) NOT NULL,
  startup_id CHAR(36) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, startup_id),
  KEY idx_sfollow_startup (startup_id),
  CONSTRAINT fk_sfollow_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_sfollow_startup FOREIGN KEY (startup_id) REFERENCES startups(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE startup_team_members ADD CONSTRAINT fk_stm_role FOREIGN KEY (role_id) REFERENCES startup_roles(id);
