-- attachment_key holds the private S3/MinIO object key (e.g. "messages/<uuid>.png"), never a permanent
-- URL: chat attachments are not publicly readable, unlike every other upload prefix in this app, so a
-- fresh short-lived presigned URL is generated from this key on every read (see ConversationService).
ALTER TABLE messages
  ADD COLUMN attachment_key VARCHAR(500) NULL AFTER shared_post_id,
  ADD COLUMN attachment_kind VARCHAR(20) NULL AFTER attachment_key,
  ADD COLUMN attachment_file_name VARCHAR(255) NULL AFTER attachment_kind;
