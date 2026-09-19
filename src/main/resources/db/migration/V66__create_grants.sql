CREATE TABLE grants (
  id                   CHAR(36)     NOT NULL,
  name                 VARCHAR(200) NOT NULL,
  provider             VARCHAR(200) NOT NULL,
  provider_type        ENUM('Government','Accelerator','Corporate','Foundation','Other') NOT NULL DEFAULT 'Other',
  description          TEXT         NULL,
  funding_amount       VARCHAR(200) NULL,
  eligibility_criteria TEXT         NULL,
  deadline             TIMESTAMP    NULL,
  application_url      VARCHAR(500) NOT NULL,
  created_by_user_id   CHAR(36)     NOT NULL,
  created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_grants_provider_type (provider_type),
  KEY idx_grants_deadline (deadline),
  CONSTRAINT fk_grants_creator FOREIGN KEY (created_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE grant_eligible_sectors (
  grant_id CHAR(36)     NOT NULL,
  sector   VARCHAR(100) NOT NULL,
  PRIMARY KEY (grant_id, sector),
  CONSTRAINT fk_ges_grant FOREIGN KEY (grant_id) REFERENCES grants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE grant_eligible_stages (
  grant_id CHAR(36)    NOT NULL,
  stage    VARCHAR(30) NOT NULL,
  PRIMARY KEY (grant_id, stage),
  CONSTRAINT fk_gest_grant FOREIGN KEY (grant_id) REFERENCES grants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
