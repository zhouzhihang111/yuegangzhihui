CREATE TABLE system_ai_provider_config (
    config_id TINYINT UNSIGNED NOT NULL,
    provider VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    base_url VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    chat_model VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    embedding_model VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    api_key_ciphertext TEXT NULL,
    api_key_nonce VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    encryption_key_version INT UNSIGNED NOT NULL DEFAULT 1,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (config_id),
    CONSTRAINT chk_system_ai_provider_singleton CHECK (config_id = 1)
);

INSERT INTO system_ai_provider_config(
    config_id, provider, base_url, chat_model, embedding_model, updated_by
) VALUES (
    1,
    'DOUBAO_ARK',
    'https://ark.cn-beijing.volces.com/api/v3',
    'doubao-seed-2-0-lite-260215',
    'doubao-embedding-text-240515',
    0
);
