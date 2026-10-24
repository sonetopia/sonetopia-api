ALTER TABLE users
    ADD COLUMN github_id VARCHAR(255);

ALTER TABLE users
    ADD CONSTRAINT uq_users_github_id UNIQUE (github_id);
