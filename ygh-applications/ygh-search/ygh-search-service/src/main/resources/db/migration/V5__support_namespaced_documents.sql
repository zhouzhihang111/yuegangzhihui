ALTER TABLE search_embedding
    ALTER COLUMN document_id TYPE VARCHAR(64) USING document_id::VARCHAR,
    ALTER COLUMN chunk_id TYPE VARCHAR(64) USING chunk_id::VARCHAR;
