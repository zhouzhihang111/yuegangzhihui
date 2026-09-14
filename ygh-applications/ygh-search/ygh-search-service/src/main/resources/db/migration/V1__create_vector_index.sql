CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE search_embedding(document_id BIGINT NOT NULL,chunk_id BIGINT NOT NULL,index_version VARCHAR(64) NOT NULL,visibility VARCHAR(16) NOT NULL,embedding vector(1024) NOT NULL,content_sha256 CHAR(64) NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),PRIMARY KEY(document_id,chunk_id,index_version));
CREATE INDEX idx_search_embedding_hnsw ON search_embedding USING hnsw(embedding vector_cosine_ops);
CREATE TABLE search_index_version(alias_name VARCHAR(64) PRIMARY KEY,active_version VARCHAR(64) NOT NULL,previous_version VARCHAR(64),switched_at TIMESTAMPTZ NOT NULL DEFAULT now());
