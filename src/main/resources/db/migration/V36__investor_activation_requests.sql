CREATE TABLE investor_activation_requests (
  id CHAR(36) NOT NULL,
  requester_user_id CHAR(36) NOT NULL,
  investor_type ENUM('Angel','VC','Family Office','Corporate VC','Accelerator','Other') NOT NULL,
  firm_name VARCHAR(200) NULL,
  thesis TEXT NULL,
  ticket_min BIGINT NULL,
  ticket_max BIGINT NULL,
  portfolio_count INT NOT NULL DEFAULT 0,
  website VARCHAR(300) NULL,
  status ENUM('Pending','Approved','Rejected') NOT NULL DEFAULT 'Pending',
  resulting_profile_id CHAR(36) NULL,
  reviewed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_invactreq_requester (requester_user_id),
  KEY idx_invactreq_status (status),
  CONSTRAINT fk_invactreq_requester FOREIGN KEY (requester_user_id) REFERENCES users(id),
  CONSTRAINT fk_invactreq_profile FOREIGN KEY (resulting_profile_id) REFERENCES investor_profiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE investor_activation_request_sectors (
  investor_activation_request_id CHAR(36) NOT NULL,
  sector VARCHAR(100) NOT NULL,
  PRIMARY KEY (investor_activation_request_id, sector),
  CONSTRAINT fk_invactreqsector_req FOREIGN KEY (investor_activation_request_id) REFERENCES investor_activation_requests(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investor_activation_request_stages (
  investor_activation_request_id CHAR(36) NOT NULL,
  stage VARCHAR(50) NOT NULL,
  PRIMARY KEY (investor_activation_request_id, stage),
  CONSTRAINT fk_invactreqstage_req FOREIGN KEY (investor_activation_request_id) REFERENCES investor_activation_requests(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE investor_activation_request_geographies (
  investor_activation_request_id CHAR(36) NOT NULL,
  geography VARCHAR(100) NOT NULL,
  PRIMARY KEY (investor_activation_request_id, geography),
  CONSTRAINT fk_invactreqgeo_req FOREIGN KEY (investor_activation_request_id) REFERENCES investor_activation_requests(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
