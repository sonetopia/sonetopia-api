
-- ============================================================
-- Sonetopia API
-- Migration: V9
-- Database: PostgreSQL
-- ============================================================

-- Canonical URL an audio was imported from (NULL for uploaded files)
ALTER TABLE audio
    ADD COLUMN source_url VARCHAR(2048);
