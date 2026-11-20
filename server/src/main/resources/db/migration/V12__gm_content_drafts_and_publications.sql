ALTER TABLE fm_content_installation
  ADD COLUMN catalog_content_id VARCHAR(36) NULL AFTER id,
  ADD INDEX fm_content_installation_catalog_idx
    (owner_id, catalog_content_id);

CREATE TABLE fm_gm_content (
  id VARCHAR(36) NOT NULL,
  created_by VARCHAR(36) NOT NULL,
  namespace_id VARCHAR(128) NOT NULL,
  book_id VARCHAR(128) NOT NULL,
  content_version VARCHAR(32) NOT NULL,
  content_hash CHAR(64) NOT NULL,
  title VARCHAR(200) NOT NULL,
  series_id VARCHAR(128) NOT NULL,
  entry_node_id VARCHAR(128) NOT NULL,
  manifest_json JSON NOT NULL,
  storage_relative_path VARCHAR(1024) NOT NULL,
  word_count INTEGER NOT NULL,
  chapter_count INTEGER NOT NULL,
  node_count INTEGER NOT NULL,
  character_count INTEGER NOT NULL,
  card_count INTEGER NOT NULL,
  encounter_count INTEGER NOT NULL,
  asset_count INTEGER NOT NULL,
  status VARCHAR(16) NOT NULL,
  issues_json JSON NOT NULL,
  version BIGINT NOT NULL,
  published_by VARCHAR(36) NULL,
  archived_by VARCHAR(36) NULL,
  created_at_ms BIGINT NOT NULL,
  updated_at_ms BIGINT NOT NULL,
  published_at_ms BIGINT NULL,
  archived_at_ms BIGINT NULL,
  CONSTRAINT fm_gm_content_pk PRIMARY KEY (id),
  CONSTRAINT fm_gm_content_hash_unique UNIQUE (content_hash),
  CONSTRAINT fm_gm_content_version_unique
    UNIQUE (namespace_id, book_id, content_version),
  CONSTRAINT fm_gm_content_status_check
    CHECK (status IN ('draft', 'validated', 'published', 'archived')),
  CONSTRAINT fm_gm_content_version_check CHECK (version >= 0),
  INDEX fm_gm_content_owner_updated_idx (created_by, updated_at_ms),
  INDEX fm_gm_content_status_updated_idx (status, updated_at_ms)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
