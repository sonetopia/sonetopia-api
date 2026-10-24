ALTER TABLE users
    ADD COLUMN apple_id VARCHAR(255);

ALTER TABLE users
    ADD CONSTRAINT uq_users_apple_id UNIQUE (apple_id);
