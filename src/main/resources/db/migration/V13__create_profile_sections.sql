ALTER TABLE users ADD COLUMN cover_url VARCHAR(500) NULL AFTER avatar_url;

ALTER TABLE user_projects CHANGE COLUMN url live_url VARCHAR(300) NULL;

ALTER TABLE user_projects
  ADD COLUMN technologies VARCHAR(500) NULL,
  ADD COLUMN image_url     VARCHAR(500) NULL,
  ADD COLUMN github_url    VARCHAR(300) NULL,
  ADD COLUMN start_date    DATE NULL,
  ADD COLUMN end_date      DATE NULL,
  ADD COLUMN project_type  ENUM('PERSONAL','ACADEMIC','OPEN_SOURCE','STARTUP') NOT NULL DEFAULT 'PERSONAL';

CREATE TABLE user_experiences (
  id               CHAR(36)     NOT NULL,
  user_id          CHAR(36)     NOT NULL,
  company          VARCHAR(200) NOT NULL,
  role             VARCHAR(150) NOT NULL,
  employment_type  VARCHAR(50)  NULL,
  location         VARCHAR(200) NULL,
  start_date       DATE         NOT NULL,
  end_date         DATE         NULL,
  is_current       BOOLEAN      NOT NULL DEFAULT FALSE,
  description      TEXT         NULL,
  company_url      VARCHAR(300) NULL,
  sort_order       INT          NOT NULL DEFAULT 0,
  created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_experiences_user (user_id, sort_order),
  CONSTRAINT fk_user_experiences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_education (
  id              CHAR(36)     NOT NULL,
  user_id         CHAR(36)     NOT NULL,
  institution     VARCHAR(200) NOT NULL,
  degree          VARCHAR(150) NULL,
  field_of_study  VARCHAR(150) NULL,
  start_year      INT          NULL,
  end_year        INT          NULL,
  grade           VARCHAR(50)  NULL,
  description     TEXT         NULL,
  sort_order      INT          NOT NULL DEFAULT 0,
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_education_user (user_id, sort_order),
  CONSTRAINT fk_user_education_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_achievements (
  id              CHAR(36)     NOT NULL,
  user_id         CHAR(36)     NOT NULL,
  title           VARCHAR(200) NOT NULL,
  organization    VARCHAR(200) NULL,
  achieved_on     DATE         NULL,
  description     TEXT         NULL,
  credential_url  VARCHAR(300) NULL,
  sort_order      INT          NOT NULL DEFAULT 0,
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_achievements_user (user_id, sort_order),
  CONSTRAINT fk_user_achievements_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_social_links (
  user_id   CHAR(36) NOT NULL,
  platform  ENUM('LINKEDIN','GITHUB','PORTFOLIO','TWITTER','KAGGLE','LEETCODE','BEHANCE','DRIBBBLE','MEDIUM') NOT NULL,
  url       VARCHAR(300) NOT NULL,
  PRIMARY KEY (user_id, platform),
  CONSTRAINT fk_user_social_links_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_open_to (
  user_id   CHAR(36) NOT NULL,
  open_to   ENUM('Collaborating','Building ideas','Startup projects','Technical projects','Research','Speaking','Mentorship') NOT NULL,
  PRIMARY KEY (user_id, open_to),
  CONSTRAINT fk_user_open_to_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
