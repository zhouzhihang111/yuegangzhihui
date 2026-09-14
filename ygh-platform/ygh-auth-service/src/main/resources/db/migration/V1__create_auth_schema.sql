CREATE TABLE auth_account (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    principal VARCHAR(190) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    failed_login_count INT UNSIGNED NOT NULL DEFAULT 0,
    locked_until DATETIME(6) NULL,
    last_login_at DATETIME(6) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_account_principal UNIQUE (principal),
    CONSTRAINT uk_auth_account_user_type UNIQUE (user_id, account_type),
    INDEX idx_auth_account_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_credential (
    id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    password_algorithm VARCHAR(32) NOT NULL,
    password_parameters JSON NULL,
    password_version INT UNSIGNED NOT NULL DEFAULT 1,
    changed_at DATETIME(6) NOT NULL,
    password_expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_credential_account UNIQUE (account_id),
    CONSTRAINT fk_auth_credential_account FOREIGN KEY (account_id)
        REFERENCES auth_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_refresh_token (
    id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_family VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    parent_token_id BIGINT NULL,
    replaced_by_token_id BIGINT NULL,
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    last_used_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    revoke_reason VARCHAR(64) NULL,
    client_fingerprint_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_refresh_token_hash UNIQUE (token_hash),
    INDEX idx_auth_refresh_account_family_expiry (account_id, token_family, expires_at),
    INDEX idx_auth_refresh_expiry (expires_at),
    CONSTRAINT fk_auth_refresh_account FOREIGN KEY (account_id)
        REFERENCES auth_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_auth_refresh_parent FOREIGN KEY (parent_token_id)
        REFERENCES auth_refresh_token (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_auth_refresh_replacement FOREIGN KEY (replaced_by_token_id)
        REFERENCES auth_refresh_token (id) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_login_attempt (
    id BIGINT NOT NULL,
    account_id BIGINT NULL,
    principal_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    client_ip VARBINARY(16) NOT NULL,
    result VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(64) NULL,
    occurred_at DATETIME(6) NOT NULL,
    trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_auth_login_principal_time (principal_hash, occurred_at),
    INDEX idx_auth_login_client_time (client_ip, occurred_at),
    INDEX idx_auth_login_account_time (account_id, occurred_at),
    INDEX idx_auth_login_occurred_at (occurred_at),
    CONSTRAINT fk_auth_login_account FOREIGN KEY (account_id)
        REFERENCES auth_account (id) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
