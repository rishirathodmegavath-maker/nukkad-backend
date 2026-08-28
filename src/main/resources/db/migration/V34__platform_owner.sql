ALTER TABLE users ADD COLUMN is_platform_owner BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users SET is_platform_owner = TRUE
WHERE id = (SELECT id FROM (SELECT id FROM users ORDER BY created_at ASC LIMIT 1) t);
