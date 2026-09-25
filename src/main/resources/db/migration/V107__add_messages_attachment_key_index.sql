-- ConversationService now checks, on every message that carries an attachment, that the stored object is
-- not already attached to another message (existsByAttachmentKey), and on unsend that no other message
-- still shares it. Without an index each of those is a full scan of `messages`.
--
-- Deliberately NOT unique: rows written before that check existed could in principle already share a key,
-- and a unique index would make this migration fail (and the app refuse to start) on such data. The rule
-- is enforced in the service layer; the index only makes the lookup cheap. NULLs (every non-attachment
-- message) are indexed too but cost nothing to skip.
ALTER TABLE messages ADD INDEX idx_messages_attachment_key (attachment_key);
