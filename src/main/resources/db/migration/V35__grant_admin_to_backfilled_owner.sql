INSERT INTO user_security_roles (user_id, role)
SELECT u.id, 'ADMIN' FROM users u
WHERE u.is_platform_owner = TRUE
AND NOT EXISTS (
    SELECT 1 FROM user_security_roles r WHERE r.user_id = u.id AND r.role = 'ADMIN'
);
