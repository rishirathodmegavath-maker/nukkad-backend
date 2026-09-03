ALTER TABLE messages
  ADD COLUMN reply_to_message_id CHAR(36) NULL AFTER sender_id,
  ADD KEY idx_messages_reply_to (reply_to_message_id),
  ADD CONSTRAINT fk_messages_reply_to FOREIGN KEY (reply_to_message_id) REFERENCES messages(id) ON DELETE SET NULL;
