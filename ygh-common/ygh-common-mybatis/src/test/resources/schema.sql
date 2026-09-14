DROP TABLE IF EXISTS audit_test;

CREATE TABLE audit_test (
    id BIGINT NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL
);
