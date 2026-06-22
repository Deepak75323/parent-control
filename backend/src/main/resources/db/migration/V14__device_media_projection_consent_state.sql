ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS media_projection_consent_state VARCHAR(64) NOT NULL DEFAULT 'NEVER_GRANTED';

UPDATE devices
SET media_projection_consent_state = CASE
    WHEN media_projection_granted = TRUE THEN 'GRANTED_ACTIVE'
    ELSE 'NEVER_GRANTED'
END;
