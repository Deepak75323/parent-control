-- Morning send window end + parent-chosen greeting texts.
ALTER TABLE parent_profiles
    ADD COLUMN IF NOT EXISTS auto_greeting_morning_end TIME NOT NULL DEFAULT '12:00',
    ADD COLUMN IF NOT EXISTS auto_greeting_morning_message VARCHAR(200) NOT NULL DEFAULT 'good morning baby',
    ADD COLUMN IF NOT EXISTS auto_greeting_evening_message VARCHAR(200) NOT NULL DEFAULT 'good night baby';
