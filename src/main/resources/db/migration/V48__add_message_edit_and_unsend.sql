ALTER TABLE messages
  ADD COLUMN edited_at TIMESTAMP NULL AFTER content_ciphertext,
  ADD COLUMN unsent_at TIMESTAMP NULL AFTER edited_at;
