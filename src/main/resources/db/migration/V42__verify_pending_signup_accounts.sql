-- Production had no SMTP configured when these accounts registered, so their verification email
-- never sent and they were stuck at "please verify your email" with no way to receive the link.
-- Google's token already isn't in play here, but the signup itself succeeded (password set,
-- account created) — this is a one-off unblock for the accounts caught by that outage, not a
-- general bypass of email verification.
UPDATE users SET email_verified = TRUE WHERE email = 'rajat@sportozen.com';
