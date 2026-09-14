CREATE TABLE training_document_progress (
    assignment_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NOT_STARTED',
    opened_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (assignment_id, document_id),
    CONSTRAINT fk_training_document_progress_assignment FOREIGN KEY (assignment_id)
        REFERENCES training_assignment(id) ON DELETE CASCADE,
    CONSTRAINT fk_training_document_progress_document FOREIGN KEY (document_id)
        REFERENCES training_document(id) ON DELETE CASCADE,
    INDEX idx_training_document_progress_status (assignment_id, status)
);
