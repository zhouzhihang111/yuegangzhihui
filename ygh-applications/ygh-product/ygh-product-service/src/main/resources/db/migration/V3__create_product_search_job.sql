CREATE TABLE product_search_job (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sku_id BIGINT NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_at DATETIME(6) NULL,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), INDEX idx_product_search_dispatch (status, next_retry_at),
    INDEX idx_product_search_sku (sku_id, created_at),
    CONSTRAINT fk_product_search_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id)
);
