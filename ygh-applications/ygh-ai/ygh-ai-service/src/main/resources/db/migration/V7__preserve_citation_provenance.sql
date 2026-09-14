ALTER TABLE ai_citation
    ADD COLUMN document_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER source_id,
    ADD COLUMN document_version BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER url,
    ADD COLUMN source_updated_at DATETIME(6) NULL AFTER document_version;

CREATE INDEX idx_ai_citation_document ON ai_citation(document_id);
