ALTER TABLE notifications MODIFY COLUMN type ENUM('connection','idea_interest','opportunity','event','reply','endorsement','recommendation','startup','chapter','investor') NOT NULL;
ALTER TABLE user_notification_mutes MODIFY COLUMN type ENUM('connection','idea_interest','opportunity','event','reply','endorsement','recommendation','startup','chapter','investor') NOT NULL;

CREATE TABLE investor_profiles (
  id                CHAR(36)     NOT NULL,
  user_id           CHAR(36)     NOT NULL,
  investor_type     ENUM('Angel','VC','Family Office','Corporate VC','Accelerator','Other') NOT NULL,
  firm_name         VARCHAR(200) NULL,
  thesis            TEXT         NULL,
  ticket_min        BIGINT       NULL,
  ticket_max        BIGINT       NULL,
  portfolio_count   INT          NOT NULL DEFAULT 0,
  website           VARCHAR(300) NULL,
  created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_investor_profiles_user (user_id),
  CONSTRAINT fk_invprofile_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE investor_profile_sectors (
  investor_profile_id CHAR(36) NOT NULL,
  sector VARCHAR(100) NOT NULL,
  PRIMARY KEY (investor_profile_id, sector),
  CONSTRAINT fk_invsector_profile FOREIGN KEY (investor_profile_id) REFERENCES investor_profiles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investor_profile_stages (
  investor_profile_id CHAR(36) NOT NULL,
  stage VARCHAR(50) NOT NULL,
  PRIMARY KEY (investor_profile_id, stage),
  CONSTRAINT fk_invstage_profile FOREIGN KEY (investor_profile_id) REFERENCES investor_profiles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investor_profile_geographies (
  investor_profile_id CHAR(36) NOT NULL,
  geography VARCHAR(100) NOT NULL,
  PRIMARY KEY (investor_profile_id, geography),
  CONSTRAINT fk_invgeo_profile FOREIGN KEY (investor_profile_id) REFERENCES investor_profiles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fundraises (
  id              CHAR(36)     NOT NULL,
  startup_id      CHAR(36)     NOT NULL,
  target_amount   BIGINT       NOT NULL,
  amount_raised   BIGINT       NOT NULL DEFAULT 0,
  funding_stage   ENUM('Idea','MVP','Early Traction','Growth','Scaling') NOT NULL,
  use_of_funds    TEXT         NULL,
  minimum_ticket  BIGINT       NULL,
  status          ENUM('Open','Closed') NOT NULL DEFAULT 'Open',
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_fundraises_startup (startup_id),
  KEY idx_fundraises_status (status),
  CONSTRAINT fk_fundraise_startup FOREIGN KEY (startup_id) REFERENCES startups(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE intro_requests (
  id            CHAR(36)      NOT NULL,
  requester_id  CHAR(36)      NOT NULL,
  recipient_id  CHAR(36)      NOT NULL,
  direction     ENUM('FOUNDER_TO_INVESTOR','INVESTOR_TO_FOUNDER') NOT NULL,
  startup_id    CHAR(36)      NULL,
  idea_id       CHAR(36)      NULL,
  message       VARCHAR(1000) NOT NULL,
  status        ENUM('Pending','Accepted','Rejected','Withdrawn') NOT NULL DEFAULT 'Pending',
  created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at   TIMESTAMP     NULL,
  PRIMARY KEY (id),
  KEY idx_intro_requester (requester_id),
  KEY idx_intro_recipient (recipient_id, status),
  CONSTRAINT fk_intro_requester FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_intro_recipient FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_intro_startup FOREIGN KEY (startup_id) REFERENCES startups(id) ON DELETE CASCADE,
  CONSTRAINT fk_intro_idea FOREIGN KEY (idea_id) REFERENCES ideas(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
