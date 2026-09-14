-- MediKiosk RW2: account lifecycle columns on the users table.
--
-- RW1 had a fixed, seed-only account model with no self-service account
-- lifecycle. RW2 adds the security/account state required for a real clinical
-- workflow platform:
--
--   must_change_password  - forces the user to set a password on next login
--                           (admin-provisioned accounts, admin password reset)
--   failed_login_attempts - running counter used by the account lockout policy
--   locked_until          - non-null means the account is locked until this
--                           instant (temporary lockout); admin can unlock early
--
-- The account itself is never locked permanently by the lockout policy; an
-- administrator can deactivate (enabled=false) or unlock (locked_until=null).

ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMP WITH TIME ZONE;