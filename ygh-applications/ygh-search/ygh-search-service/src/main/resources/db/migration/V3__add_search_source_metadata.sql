ALTER TABLE search_embedding ADD COLUMN document_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE search_embedding ADD COLUMN source_updated_at TIMESTAMPTZ NULL;
