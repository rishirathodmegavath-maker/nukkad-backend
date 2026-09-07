ALTER TABLE users ADD COLUMN onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE AFTER email_verified;

-- Grandfather in every account that already existed before this flag was introduced — only
-- accounts created from this point on should be routed through the onboarding flow.
UPDATE users SET onboarding_completed = TRUE;
