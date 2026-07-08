-- Backfill family password from the earliest paired device when parents upgraded mid-flight.
UPDATE parent_profiles p
SET default_child_login_password_hash = sub.child_login_password_hash
FROM (
    SELECT DISTINCT ON (d.parent_profile_id)
        d.parent_profile_id,
        d.child_login_password_hash
    FROM devices d
    WHERE d.child_login_password_hash IS NOT NULL
    ORDER BY d.parent_profile_id, d.created_at ASC
) sub
WHERE p.id = sub.parent_profile_id
  AND p.default_child_login_password_hash IS NULL;

-- Align every device to the parent account's single family password.
UPDATE devices d
SET child_login_password_hash = p.default_child_login_password_hash
FROM parent_profiles p
WHERE d.parent_profile_id = p.id
  AND p.default_child_login_password_hash IS NOT NULL
  AND (d.child_login_password_hash IS DISTINCT FROM p.default_child_login_password_hash);
