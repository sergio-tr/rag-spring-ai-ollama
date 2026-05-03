-- E2E/dev UX: ensure the canonical seed user can authenticate even when email confirmation is enabled.
--
-- Notes:
-- - The seed user comes from V16__seed_dev_tenant.sql (dev@local.test / {noop}dev).
-- - Fullstack Playwright scenarios rely on this user being able to log in.
-- - Account lifecycle features still remain enforced for newly registered users.

UPDATE users
SET email_verified = true,
    email_verified_at = COALESCE(email_verified_at, CURRENT_TIMESTAMP)
WHERE lower(email) = lower('dev@local.test')
  AND (email_verified IS DISTINCT FROM true);

