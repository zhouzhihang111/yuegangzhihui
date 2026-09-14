ALTER TABLE knowledge_document
    MODIFY COLUMN category VARCHAR(32)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL;
