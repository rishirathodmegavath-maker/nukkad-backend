CREATE TABLE conversations (
  id CHAR(36) NOT NULL,
  user_a_id CHAR(36) NOT NULL,
  user_b_id CHAR(36) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_conversation_pair (user_a_id, user_b_id),
  KEY idx_conversations_user_a (user_a_id),
  KEY idx_conversations_user_b (user_b_id),
  CONSTRAINT fk_conversations_user_a FOREIGN KEY (user_a_id) REFERENCES users(id),
  CONSTRAINT fk_conversations_user_b FOREIGN KEY (user_b_id) REFERENCES users(id),
  CONSTRAINT chk_conversation_pair_order CHECK (user_a_id < user_b_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE messages (
  id CHAR(36) NOT NULL,
  conversation_id CHAR(36) NOT NULL,
  sender_id CHAR(36) NOT NULL,
  -- AES-256-GCM ciphertext (base64: 12-byte IV || ciphertext), never plaintext at rest.
  content_ciphertext TEXT NOT NULL,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_messages_conversation (conversation_id, created_at),
  CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id),
  CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
