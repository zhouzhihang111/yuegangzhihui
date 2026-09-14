ALTER TABLE auth_login_attempt
    ADD COLUMN client_ip_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER principal_hash;

UPDATE auth_login_attempt
SET client_ip_hash = LOWER(HEX(RANDOM_BYTES(32)))
WHERE client_ip_hash IS NULL;

ALTER TABLE auth_login_attempt
    DROP INDEX idx_auth_login_client_time,
    DROP COLUMN client_ip,
    MODIFY COLUMN client_ip_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD INDEX idx_auth_login_ip_hash_time (client_ip_hash, occurred_at);
