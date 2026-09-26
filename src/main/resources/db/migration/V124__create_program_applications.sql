-- Startup Programs: SPARK/IGNITE application workflow. Program content itself (copy, journey,
-- benefits) is fixed code (see ProgramCatalog) rather than a database table -- only the applicant's
-- application and the handful of genuinely operational, admin-editable program settings need
-- storage. Numbered V124 (not V122/V123) to sit after the still-unmerged feat/admin-chapter-creation
-- branch's own pending migrations, avoiding a repeat of the V113 collision from earlier today;
-- out-of-order migrations are already enabled (see application.yml), so this is safe to apply
-- whichever of the two branches merges first.

CREATE TABLE program_applications (
  id                CHAR(36)      NOT NULL,
  applicant_user_id CHAR(36)      NOT NULL,
  program           VARCHAR(20)   NOT NULL,
  status            VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
  submitted_at      TIMESTAMP     NULL,
  admin_note        VARCHAR(1000) NULL,
  reviewed_by       CHAR(36)      NULL,
  reviewed_at       TIMESTAMP     NULL,
  created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_program_applications_applicant (applicant_user_id, program),
  KEY idx_program_applications_program_status (program, status),
  CONSTRAINT fk_program_applications_user FOREIGN KEY (applicant_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE program_application_answers (
  application_id CHAR(36)    NOT NULL,
  field_key      VARCHAR(60) NOT NULL,
  field_value    TEXT        NOT NULL,
  PRIMARY KEY (application_id, field_key),
  CONSTRAINT fk_program_application_answers_application FOREIGN KEY (application_id) REFERENCES program_applications(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE program_settings (
  program          VARCHAR(20)  NOT NULL,
  application_open BOOLEAN      NOT NULL DEFAULT TRUE,
  fee_amount       INT          NULL,
  fee_currency     VARCHAR(10)  NULL,
  enrollment_info  VARCHAR(500) NULL,
  selective        BOOLEAN      NULL,
  updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (program)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO program_settings (program, application_open) VALUES ('SPARK', TRUE), ('IGNITE', TRUE);
