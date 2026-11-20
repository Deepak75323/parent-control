-- Auto good-morning / good-night chat greetings (settings-gated).
ALTER TABLE parent_profiles
    ADD COLUMN IF NOT EXISTS auto_greeting_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS auto_greeting_room_id UUID,
    ADD COLUMN IF NOT EXISTS auto_greeting_morning_time TIME NOT NULL DEFAULT '07:05',
    ADD COLUMN IF NOT EXISTS auto_greeting_evening_start TIME NOT NULL DEFAULT '23:55',
    ADD COLUMN IF NOT EXISTS auto_greeting_evening_end TIME NOT NULL DEFAULT '00:00',
    ADD COLUMN IF NOT EXISTS auto_greeting_timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    ADD COLUMN IF NOT EXISTS auto_greeting_last_morning_on DATE,
    ADD COLUMN IF NOT EXISTS auto_greeting_last_evening_on DATE;
