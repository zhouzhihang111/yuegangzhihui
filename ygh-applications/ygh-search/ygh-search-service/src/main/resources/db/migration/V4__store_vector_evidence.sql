ALTER TABLE search_embedding
    ADD COLUMN title VARCHAR(500) NOT NULL DEFAULT '',
    ADD COLUMN category VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN content TEXT NOT NULL DEFAULT '';

CREATE INDEX idx_search_embedding_scope
    ON search_embedding(index_version, visibility, category);
