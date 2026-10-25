-- Parents granted portal kill-switch control via family handle (not everyone).
CREATE TABLE IF NOT EXISTS portal_operators (
  user_id UUID PRIMARY KEY REFERENCES users (id),
  family_handle VARCHAR(32) NOT NULL,
  granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  granted_by UUID NULL REFERENCES users (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_portal_operators_family_handle
  ON portal_operators (LOWER(family_handle));

-- Stop using the global share flag; handle-based operators replace it.
UPDATE portal_site_settings
SET shared_with_everyone = FALSE
WHERE id = 1;
