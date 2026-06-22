# Database Schema

PostgreSQL 16. All tables use UUID primary keys, `created_at` / `updated_at`, and soft delete via `deleted_at`.

## Core Tables (Phase 1)

### users
| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| email | VARCHAR(255) | UNIQUE, NOT NULL |
| password_hash | VARCHAR(255) | NOT NULL |
| role | VARCHAR(50) | ROLE_PARENT, ROLE_ADMIN |
| email_verified | BOOLEAN | DEFAULT false |
| consent_accepted | BOOLEAN | DEFAULT false |
| parent_pin_hash | VARCHAR(255) | Optional |
| created_at, updated_at, deleted_at | TIMESTAMPTZ | |

### parent_profiles
| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| user_id | UUID | FK → users, UNIQUE |
| first_name, last_name | VARCHAR(100) | NOT NULL |
| phone | VARCHAR(50) | |
| timezone | VARCHAR(64) | DEFAULT 'UTC' |

### child_profiles
| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| parent_profile_id | UUID | FK → parent_profiles |
| display_name | VARCHAR(100) | NOT NULL |
| date_of_birth | DATE | |
| avatar_url | TEXT | |

### devices
| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| parent_profile_id | UUID | FK |
| child_profile_id | UUID | FK, nullable |
| device_name | VARCHAR(255) | NOT NULL |
| android_id | VARCHAR(255) | UNIQUE |
| status | VARCHAR(50) | ONLINE, OFFLINE, etc. |
| battery_level | INT | |
| last_seen_at | TIMESTAMPTZ | |
| last_latitude, last_longitude | DOUBLE | |

### pairing_codes
| Column | Type | Notes |
|--------|------|-------|
| code | VARCHAR(8) | UNIQUE |
| parent_profile_id | UUID | FK |
| expires_at | TIMESTAMPTZ | 15 min TTL |
| used_at | TIMESTAMPTZ | |
| device_id | UUID | Set after pairing |

### refresh_tokens
| Column | Type | Notes |
|--------|------|-------|
| user_id | UUID | |
| token_hash | VARCHAR(64) | SHA-256 of JWT |
| expires_at | TIMESTAMPTZ | |
| revoked | BOOLEAN | |

## Monitoring Tables (Phase 2+)

Pre-created in migration for forward compatibility:

- `notification_logs`, `app_usage_logs`, `location_history`
- `call_logs`, `sms_logs`, `browser_history`
- `screen_sessions`, `camera_sessions`, `audio_sessions`

## Policy & Billing (Phase 3–4)

- `monitoring_policies`, `geofences`, `alerts`
- `subscriptions`, `payments`, `audit_logs`

## Indexes

See `V1__initial_schema.sql` for full index definitions on foreign keys and query columns (`device_id`, `parent_profile_id`, `recorded_at`).
