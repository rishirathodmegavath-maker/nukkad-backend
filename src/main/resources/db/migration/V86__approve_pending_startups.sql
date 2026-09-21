-- Startups no longer wait for admin approval: a new startup goes live as soon as it is registered (the entity now defaults
-- to APPROVED). Release any startup that was submitted under the old rule and is still waiting, so it does not stay hidden
-- with no admin action left that could approve it. Startups an admin already rejected are left as they are.
UPDATE startups
SET moderation_status = 'APPROVED'
WHERE moderation_status = 'PENDING';
