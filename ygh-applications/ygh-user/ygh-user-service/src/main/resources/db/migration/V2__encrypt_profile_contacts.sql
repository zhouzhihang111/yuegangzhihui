ALTER TABLE user_profile
    ADD COLUMN phone_ciphertext VARBINARY(512) NULL AFTER avatar_url,
    ADD COLUMN email_ciphertext VARBINARY(768) NULL AFTER phone_ciphertext,
    ADD COLUMN contact_pii_key_version SMALLINT UNSIGNED NULL AFTER email_ciphertext;
