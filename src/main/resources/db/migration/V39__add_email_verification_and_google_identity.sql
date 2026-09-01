-- Email verification + Google account-linking (see AuthService for the full flow).
-- Existing accounts are grandfathered as verified: they already successfully used the
-- app, so retroactively locking them out on this migration would be a self-inflicted
-- incident, not a security improvement.
ALTER TABLE users
  ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN google_subject VARCHAR(255) NULL,
  ADD CONSTRAINT uq_users_google_subject UNIQUE (google_subject);

UPDATE users SET email_verified = TRUE;

CREATE TABLE email_verification_tokens (
  id          CHAR(36) NOT NULL,
  user_id     CHAR(36) NOT NULL,
  token_hash  VARCHAR(255) NOT NULL,
  expires_at  TIMESTAMP NOT NULL,
  used_at     TIMESTAMP NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_email_verif_hash (token_hash),
  KEY idx_email_verif_user (user_id),
  CONSTRAINT fk_email_verif_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
