-- Server-wide chat decrypt PIN (bcrypt hash). Singleton row id=1.
CREATE TABLE IF NOT EXISTS chat_decrypt_settings (
    id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    pin_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
