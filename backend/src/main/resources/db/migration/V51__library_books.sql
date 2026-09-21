-- Parent library: PDF books with per-user progress, highlights, and bookmarks.

CREATE TABLE IF NOT EXISTS library_books (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users (id),
    title VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    mime_type VARCHAR(128) NOT NULL DEFAULT 'application/pdf',
    file_size_bytes BIGINT NOT NULL,
    page_count INT,
    content_hash VARCHAR(64),
    visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    status VARCHAR(16) NOT NULL DEFAULT 'READY',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_library_books_owner_created
    ON library_books (owner_user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_library_books_owner_hash_active
    ON library_books (owner_user_id, content_hash)
    WHERE deleted_at IS NULL AND content_hash IS NOT NULL;

CREATE TABLE IF NOT EXISTS library_reading_progress (
    id UUID PRIMARY KEY,
    book_id UUID NOT NULL REFERENCES library_books (id),
    user_id UUID NOT NULL REFERENCES users (id),
    last_page INT NOT NULL DEFAULT 1,
    scroll_offset DOUBLE PRECISION,
    zoom_level DOUBLE PRECISION,
    progress_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
    last_viewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_library_progress_book_user UNIQUE (book_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_library_progress_user_viewed
    ON library_reading_progress (user_id, last_viewed_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS library_highlights (
    id UUID PRIMARY KEY,
    book_id UUID NOT NULL REFERENCES library_books (id),
    user_id UUID NOT NULL REFERENCES users (id),
    page_number INT NOT NULL,
    selected_text TEXT NOT NULL,
    color VARCHAR(16) NOT NULL DEFAULT 'YELLOW',
    position_data TEXT NOT NULL,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_library_highlights_book_user_page
    ON library_highlights (book_id, user_id, page_number)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS library_bookmarks (
    id UUID PRIMARY KEY,
    book_id UUID NOT NULL REFERENCES library_books (id),
    user_id UUID NOT NULL REFERENCES users (id),
    page_number INT NOT NULL,
    label VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_library_bookmarks_book_user
    ON library_bookmarks (book_id, user_id, page_number)
    WHERE deleted_at IS NULL;
