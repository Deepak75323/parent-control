-- Remove the parent PDF library. V51 already ran in production, so drop rather than delete that migration.

DROP TABLE IF EXISTS library_bookmarks;
DROP TABLE IF EXISTS library_highlights;
DROP TABLE IF EXISTS library_reading_progress;
DROP TABLE IF EXISTS library_books;
