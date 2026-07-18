-- Optional app icons for parent Usage Report / app pickers (PNG base64, small).
ALTER TABLE device_installed_apps
  ADD COLUMN IF NOT EXISTS icon_base64 TEXT;
