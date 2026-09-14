CREATE TABLE auth_password_reset (
    id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_password_reset_token (token_hash),
    INDEX idx_auth_password_reset_account_expiry (account_id, expires_at),
    CONSTRAINT fk_auth_password_reset_account FOREIGN KEY (account_id)
        REFERENCES auth_account (id) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
