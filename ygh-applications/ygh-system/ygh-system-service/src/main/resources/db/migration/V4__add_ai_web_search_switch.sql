ALTER TABLE system_ai_provider_config
    ADD COLUMN web_search_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER embedding_model;
