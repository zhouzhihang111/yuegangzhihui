CREATE TABLE knowledge_processing_job (
    id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    task_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    progress INT UNSIGNED NOT NULL DEFAULT 0,
    retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_at DATETIME(6) NULL,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_knowledge_processing_dispatch (status, next_retry_at),
    INDEX idx_knowledge_processing_document (document_id, created_at),
    CONSTRAINT fk_knowledge_processing_document FOREIGN KEY (document_id) REFERENCES knowledge_document(id) ON DELETE CASCADE
);
