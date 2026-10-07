-- ============================================================
-- Sonetopia API
-- Initial Database Schema
-- Migration: V1
-- Database: PostgreSQL
-- ============================================================


-- ============================================================
-- USERS
-- ============================================================

CREATE TABLE users (
    id UUID PRIMARY KEY,

    username VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,

    storage_used BIGINT NOT NULL DEFAULT 0,
    is_admin BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
);


-- ============================================================
-- AUDIOS
-- ============================================================

CREATE TABLE audios (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,

    title VARCHAR(255) NOT NULL,
    type VARCHAR(50),
    author VARCHAR(255),
    album VARCHAR(255),
    year SMALLINT,

    image_cover TEXT,

    storage_key TEXT NOT NULL,

    mime_type VARCHAR(100) NOT NULL,
    file_extension VARCHAR(20),
    file_size BIGINT NOT NULL,
    duration INTEGER,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audios_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);


-- ============================================================
-- PLAYLISTS
-- ============================================================

CREATE TABLE playlists (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,

    title VARCHAR(255) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_playlists_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);


-- ============================================================
-- PLAYLIST AUDIOS
-- ============================================================

CREATE TABLE playlist_audios (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    playlist_id UUID NOT NULL,
    audio_id UUID NOT NULL,

    position INTEGER NOT NULL,

    CONSTRAINT uq_playlist_audios_playlist_audio
        UNIQUE (playlist_id, audio_id),

    CONSTRAINT fk_playlist_audios_playlist
        FOREIGN KEY (playlist_id)
        REFERENCES playlists (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_playlist_audios_audio
        FOREIGN KEY (audio_id)
        REFERENCES audios (id)
        ON DELETE CASCADE
);


-- ============================================================
-- INDEXES
-- ============================================================

CREATE INDEX idx_audios_user_id
    ON audios (user_id);

CREATE INDEX idx_playlists_user_id
    ON playlists (user_id);

CREATE INDEX idx_playlist_audios_audio_id
    ON playlist_audios (audio_id);

CREATE INDEX idx_playlist_audios_position
    ON playlist_audios (playlist_id, position);
