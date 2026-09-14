INSERT INTO search_index_version(alias_name,active_version) VALUES('knowledge-active','knowledge-v1') ON CONFLICT(alias_name) DO NOTHING;
