-- The investor-activation tables were created by V36 and then dropped by V37 (remove admin concept),
-- so on any database migrated from scratch they do not exist by the time this runs. Investor
-- activation is admin-reviewed again (see InvestorActivationService), so this recreates them in
-- their final shape: status normalized to the same UPPERCASE convention every other status enum in
-- this codebase uses (ModerationStatus, WithdrawalStatus, ReportStatus, ...), plus the
-- review_note/reviewed_by columns V36's original schema lacked.
-- IF NOT EXISTS keeps this a no-op on a database where the tables were already restored by hand.

CREATE TABLE IF NOT EXISTS investor_activation_requests (
  id CHAR(36) NOT NULL,
  requester_user_id CHAR(36) NOT NULL,
  investor_type ENUM('Angel','VC','Family Office','Corporate VC','Accelerator','Other') NOT NULL,
  firm_name VARCHAR(200) NULL,
  thesis TEXT NULL,
  ticket_min BIGINT NULL,
  ticket_max BIGINT NULL,
  portfolio_count INT NOT NULL DEFAULT 0,
  website VARCHAR(300) NULL,
  status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  resulting_profile_id CHAR(36) NULL,
  review_note VARCHAR(500) NULL,
  reviewed_by CHAR(36) NULL,
  reviewed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_invactreq_requester (requester_user_id),
  KEY idx_invactreq_status (status),
  CONSTRAINT fk_invactreq_requester FOREIGN KEY (requester_user_id) REFERENCES users(id),
  CONSTRAINT fk_invactreq_profile FOREIGN KEY (resulting_profile_id) REFERENCES investor_profiles(id),
  CONSTRAINT fk_invactreq_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS investor_activation_request_sectors (
  investor_activation_request_id CHAR(36) NOT NULL,
  sector VARCHAR(100) NOT NULL,
  PRIMARY KEY (investor_activation_request_id, sector),
  CONSTRAINT fk_invactreqsector_req FOREIGN KEY (investor_activation_request_id) REFERENCES investor_activation_requests(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS investor_activation_request_stages (
  investor_activation_request_id CHAR(36) NOT NULL,
  stage VARCHAR(50) NOT NULL,
  PRIMARY KEY (investor_activation_request_id, stage),
  CONSTRAINT fk_invactreqstage_req FOREIGN KEY (investor_activation_request_id) REFERENCES investor_activation_requests(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS investor_activation_request_geographies (
  investor_activation_request_id CHAR(36) NOT NULL,
  geography VARCHAR(100) NOT NULL,
  PRIMARY KEY (investor_activation_request_id, geography),
  CONSTRAINT fk_invactreqgeo_req FOREIGN KEY (investor_activation_request_id) REFERENCES investor_activation_requests(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
