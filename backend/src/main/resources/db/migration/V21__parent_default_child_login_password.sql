-- One child login password per parent account — shared across all paired devices.
ALTER TABLE parent_profiles
    ADD COLUMN IF NOT EXISTS default_child_login_password_hash VARCHAR(255);
