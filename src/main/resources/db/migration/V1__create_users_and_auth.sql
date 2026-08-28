-- Chapters (lean): created first because users.chapter_id references it.
-- President/application fields are added later in V4, once the chapters module lands.
CREATE TABLE chapters (
  id                CHAR(36)     NOT NULL,
  name              VARCHAR(150) NOT NULL,
  city              VARCHAR(100) NULL,
  country           VARCHAR(100) NULL,
  description       TEXT NULL,
  cover_image_url   VARCHAR(500) NULL,
  created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_chapters_city (city),
  FULLTEXT KEY ft_chapters_search (name, city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE users (
  id                   CHAR(36)      NOT NULL,
  name                 VARCHAR(120)  NOT NULL,
  email                VARCHAR(190)  NOT NULL,
  password_hash        VARCHAR(100)  NOT NULL,
  avatar_url           VARCHAR(500)  NULL,
  headline             VARCHAR(200)  NULL,
  role                 VARCHAR(150)  NULL,
  college_or_company   VARCHAR(200)  NULL,
  location             VARCHAR(200)  NULL,
  experience_years     INT           NOT NULL DEFAULT 0,
  goals                TEXT          NULL,
  bio                  TEXT          NULL,
  availability         ENUM('Full-time','Part-time','Weekends','Not available') NULL,
  chapter_id           CHAR(36)      NULL,
  connections_count    INT           NOT NULL DEFAULT 0,
  last_active_at       TIMESTAMP     NULL,
  created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_email (email),
  KEY idx_users_chapter_id (chapter_id),
  KEY idx_users_location (location),
  FULLTEXT KEY ft_users_search (name, headline, bio, college_or_company),
  CONSTRAINT fk_users_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_security_roles (
  user_id CHAR(36) NOT NULL,
  role    ENUM('USER','FOUNDER','INVESTOR','CHAPTER_PRESIDENT','ADMIN') NOT NULL,
  PRIMARY KEY (user_id, role),
  CONSTRAINT fk_usr_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_skills (
  user_id CHAR(36) NOT NULL,
  skill   VARCHAR(100) NOT NULL,
  PRIMARY KEY (user_id, skill),
  KEY idx_user_skills_skill (skill),
  CONSTRAINT fk_uskill_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_looking_for (
  user_id      CHAR(36) NOT NULL,
  looking_for  ENUM('Co-founder','Team to join','Mentorship','Investment','Job','Internship','Founding Role','Collaborators') NOT NULL,
  PRIMARY KEY (user_id, looking_for),
  CONSTRAINT fk_ulf_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_projects (
  id           CHAR(36)     NOT NULL,
  user_id      CHAR(36)     NOT NULL,
  title        VARCHAR(200) NOT NULL,
  description  TEXT         NULL,
  url          VARCHAR(500) NULL,
  sort_order   INT          NOT NULL DEFAULT 0,
  created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_projects_user_id (user_id),
  CONSTRAINT fk_uproj_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE connections (
  id            CHAR(36) NOT NULL,
  user_a_id     CHAR(36) NOT NULL,
  user_b_id     CHAR(36) NOT NULL,
  requested_by  CHAR(36) NOT NULL,
  status        ENUM('PENDING','ACCEPTED','DECLINED') NOT NULL DEFAULT 'ACCEPTED',
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_connections_pair (user_a_id, user_b_id),
  KEY idx_connections_user_b (user_b_id),
  CONSTRAINT ck_connections_order CHECK (user_a_id < user_b_id),
  CONSTRAINT fk_conn_a FOREIGN KEY (user_a_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_conn_b FOREIGN KEY (user_b_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_follows (
  follower_id  CHAR(36) NOT NULL,
  followee_id  CHAR(36) NOT NULL,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (follower_id, followee_id),
  KEY idx_user_follows_followee (followee_id),
  CONSTRAINT ck_user_follows_not_self CHECK (follower_id <> followee_id),
  CONSTRAINT fk_ufollow_follower FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_ufollow_followee FOREIGN KEY (followee_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_tokens (
  id                    CHAR(36) NOT NULL,
  user_id               CHAR(36) NOT NULL,
  token_hash            VARCHAR(255) NOT NULL,
  expires_at            TIMESTAMP NOT NULL,
  revoked_at            TIMESTAMP NULL,
  replaced_by_token_id  CHAR(36) NULL,
  created_by_ip         VARCHAR(45) NULL,
  created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_refresh_hash (token_hash),
  KEY idx_refresh_user (user_id),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE password_reset_tokens (
  id          CHAR(36) NOT NULL,
  user_id     CHAR(36) NOT NULL,
  token_hash  VARCHAR(255) NOT NULL,
  expires_at  TIMESTAMP NOT NULL,
  used_at     TIMESTAMP NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reset_hash (token_hash),
  KEY idx_reset_user (user_id),
  CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_logs (
  id           CHAR(36) NOT NULL,
  user_id      CHAR(36) NULL,
  action       ENUM('LOGIN','LOGOUT','CREATE_IDEA','UPDATE_IDEA','DELETE_IDEA','CREATE_STARTUP','CREATE_OPPORTUNITY','APPLY_OPPORTUNITY','INVESTOR_INTRODUCTION','ADMIN_ACTION') NOT NULL,
  entity_type  VARCHAR(50) NULL,
  entity_id    CHAR(36) NULL,
  details      JSON NULL,
  ip_address   VARCHAR(45) NULL,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_audit_user (user_id),
  KEY idx_audit_action_created (action, created_at),
  CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
