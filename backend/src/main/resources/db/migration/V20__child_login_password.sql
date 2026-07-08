-- Per-device child login password (set by parent during pairing; used on every child app login).
ALTER TABLE pairing_codes
    ADD COLUMN IF NOT EXISTS child_login_password_hash VARCHAR(255);

ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS child_login_password_hash VARCHAR(255);
