-- Message deletion is per-viewer only (see message_deletions), never a global tombstone: a single
-- deleted_at column on messages would hide a message from BOTH participants once either one
-- deleted it, which is the wrong semantics. The column added in V40 is dropped before it was ever
-- relied on by any released version.
ALTER TABLE messages DROP COLUMN deleted_at;
