ALTER TABLE users
    ADD COLUMN name VARCHAR(100),
    ADD COLUMN email_verified_at TIMESTAMP,
    ADD COLUMN email_verification_sent_at TIMESTAMP;

UPDATE users
SET name = username
WHERE name IS NULL;

-- Existing accounts are grandfathered; new registrations start unverified.
UPDATE users
SET email_verified_at = CURRENT_TIMESTAMP;

ALTER TABLE users
    ALTER COLUMN name SET NOT NULL;
