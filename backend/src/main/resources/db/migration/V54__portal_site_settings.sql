-- Global parent-portal kill switch (white screen when disabled).
CREATE TABLE IF NOT EXISTS portal_site_settings (
  id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  disabled BOOLEAN NOT NULL DEFAULT FALSE,
  shared_with_everyone BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_by UUID NULL
);

INSERT INTO portal_site_settings (id, disabled, shared_with_everyone)
VALUES (1, FALSE, FALSE)
ON CONFLICT (id) DO NOTHING;
