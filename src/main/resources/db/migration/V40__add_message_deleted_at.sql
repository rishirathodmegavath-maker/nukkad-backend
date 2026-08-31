-- Owner-only message deletion: a set deleted_at hides the message's content/shared-post from
-- both participants (rendered as a placeholder), independent of the existing per-viewer
-- message_deletions table used by "delete chat" (which only hides messages from one viewer).
ALTER TABLE messages ADD COLUMN deleted_at TIMESTAMP NULL;
