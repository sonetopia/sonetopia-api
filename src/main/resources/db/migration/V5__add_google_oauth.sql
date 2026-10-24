ALTER TABLE users
    ADD COLUMN google_id VARCHAR(255),
    ALTER COLUMN password DROP NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT uq_users_google_id UNIQUE (google_id);
