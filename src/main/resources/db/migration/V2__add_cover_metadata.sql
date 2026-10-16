
-- ============================================================
-- Sonetopia API
-- Migration: V2
-- Database: PostgreSQL
-- ============================================================

-- Remove deprecated column
ALTER TABLE audio
    DROP COLUMN file_extension;

-- Rename cover column
ALTER TABLE audio
    RENAME COLUMN image_cover TO cover_storage_key;

-- Add cover metadata
ALTER TABLE audio
    ADD COLUMN cover_mime_type VARCHAR(100),
    ADD COLUMN cover_file_size BIGINT,
    ADD COLUMN cover_version INTEGER DEFAULT 0;
