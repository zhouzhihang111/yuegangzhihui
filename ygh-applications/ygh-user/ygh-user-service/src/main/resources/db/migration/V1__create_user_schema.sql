CREATE TABLE user_profile (
    user_id BIGINT NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    avatar_url VARCHAR(512) NULL,
    locale VARCHAR(16) NOT NULL DEFAULT 'zh-CN',
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    profile_completed BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    CONSTRAINT chk_user_profile_display_name CHECK (CHAR_LENGTH(TRIM(display_name)) BETWEEN 1 AND 80)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_department (
    id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    department_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    department_name VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_user_department_code UNIQUE (department_code),
    INDEX idx_user_department_parent_sort (parent_id, sort_order, id),
    CONSTRAINT fk_user_department_parent FOREIGN KEY (parent_id)
        REFERENCES user_department (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_user_department_not_self CHECK (parent_id IS NULL OR parent_id <> id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_position (
    id BIGINT NOT NULL,
    position_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    position_name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_user_position_code UNIQUE (position_code),
    INDEX idx_user_position_enabled_name (enabled, position_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_employee (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    employee_no VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    department_id BIGINT NULL,
    employment_status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    hired_on DATE NULL,
    left_on DATE NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_user_employee_user UNIQUE (user_id),
    CONSTRAINT uk_user_employee_no UNIQUE (employee_no),
    INDEX idx_user_employee_department_status (department_id, employment_status),
    CONSTRAINT fk_user_employee_profile FOREIGN KEY (user_id)
        REFERENCES user_profile (user_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_user_employee_department FOREIGN KEY (department_id)
        REFERENCES user_department (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_user_employee_status CHECK (employment_status IN ('ACTIVE','SUSPENDED','LEFT')),
    CONSTRAINT chk_user_employee_dates CHECK (left_on IS NULL OR hired_on IS NULL OR left_on >= hired_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_employee_position (
    employee_id BIGINT NOT NULL,
    position_id BIGINT NOT NULL,
    primary_position BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (employee_id, position_id),
    INDEX idx_user_employee_position_position (position_id, employee_id),
    CONSTRAINT fk_user_employee_position_employee FOREIGN KEY (employee_id)
        REFERENCES user_employee (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_user_employee_position_position FOREIGN KEY (position_id)
        REFERENCES user_position (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_address (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    label VARCHAR(32) NULL,
    recipient_name_ciphertext VARBINARY(512) NOT NULL,
    recipient_phone_ciphertext VARBINARY(512) NOT NULL,
    pii_key_version SMALLINT UNSIGNED NOT NULL,
    country_code CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    province_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
    province_name VARCHAR(64) NOT NULL,
    city_name VARCHAR(64) NOT NULL,
    district_name VARCHAR(64) NOT NULL,
    address_detail_ciphertext VARBINARY(2048) NOT NULL,
    postal_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    default_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN is_default THEN user_id ELSE NULL END) STORED,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_user_address_one_default UNIQUE (default_user_id),
    INDEX idx_user_address_owner_updated (user_id, updated_at, id),
    CONSTRAINT fk_user_address_profile FOREIGN KEY (user_id)
        REFERENCES user_profile (user_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_user_address_country CHECK (country_code REGEXP '^[A-Z]{2}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
