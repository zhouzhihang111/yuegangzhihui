CREATE TABLE auth_account_admin_audit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    operator_user_id BIGINT NOT NULL,
    action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_auth_admin_audit_account_created (account_id, created_at),
    INDEX idx_auth_admin_audit_operator_created (operator_user_id, created_at),
    CONSTRAINT fk_auth_admin_audit_account FOREIGN KEY (account_id)
        REFERENCES auth_account(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_auth_admin_audit_action CHECK (action IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
