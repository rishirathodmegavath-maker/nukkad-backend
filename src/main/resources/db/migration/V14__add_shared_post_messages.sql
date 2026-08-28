ALTER TABLE messages
  ADD COLUMN message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT' AFTER sender_id,
  ADD COLUMN shared_post_id CHAR(36) NULL AFTER message_type;

ALTER TABLE messages
  ADD CONSTRAINT fk_messages_shared_post FOREIGN KEY (shared_post_id) REFERENCES posts(id) ON DELETE SET NULL;
