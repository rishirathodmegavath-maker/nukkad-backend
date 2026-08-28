ALTER TABLE refresh_tokens
  ADD COLUMN user_agent VARCHAR(255) NULL,
  ADD COLUMN device_label VARCHAR(150) NULL,
  ADD COLUMN last_used_at TIMESTAMP NULL;
