CREATE TABLE fm_content_installation (
  owner_id VARCHAR(36) NOT NULL,
  id VARCHAR(36) NOT NULL,
  namespace_id VARCHAR(128) NOT NULL,
  book_id VARCHAR(128) NOT NULL,
  content_version VARCHAR(32) NOT NULL,
  content_hash CHAR(64) NOT NULL,
  title VARCHAR(200) NOT NULL,
  entry_node_id VARCHAR(128) NOT NULL,
  engine_version VARCHAR(32) NOT NULL,
  schema_version INT NOT NULL,
  manifest_json JSON NOT NULL,
  storage_relative_path VARCHAR(255) NOT NULL,
  word_count INT NOT NULL,
  chapter_count INT NOT NULL,
  node_count INT NOT NULL,
  character_count INT NOT NULL,
  card_count INT NOT NULL,
  encounter_count INT NOT NULL,
  asset_count INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  installed_at_ms BIGINT NOT NULL,
  updated_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_content_installation_pk PRIMARY KEY (owner_id, id),
  CONSTRAINT fm_content_installation_version_unique
    UNIQUE (owner_id, namespace_id, book_id, content_version),
  CONSTRAINT fm_content_installation_hash_unique UNIQUE (owner_id, content_hash),
  INDEX fm_content_installation_owner_time_idx (owner_id, installed_at_ms)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fm_content_installation_receipt (
  owner_id VARCHAR(36) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  installation_id VARCHAR(36) NOT NULL,
  response_json JSON NOT NULL,
  created_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_content_installation_receipt_pk
    PRIMARY KEY (owner_id, idempotency_key),
  INDEX fm_content_installation_receipt_time_idx (owner_id, created_at_ms)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fm_content_asset (
  owner_id VARCHAR(36) NOT NULL,
  installation_id VARCHAR(36) NOT NULL,
  asset_path VARCHAR(512) NOT NULL,
  content_hash CHAR(64) NOT NULL,
  mime VARCHAR(128) NOT NULL,
  byte_size BIGINT NOT NULL,
  asset_kind VARCHAR(64) NOT NULL,
  license_text VARCHAR(512) NOT NULL,
  created_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_content_asset_pk
    PRIMARY KEY (owner_id, installation_id, asset_path),
  INDEX fm_content_asset_owner_hash_idx (owner_id, content_hash),
  CONSTRAINT fm_content_asset_size_check CHECK (byte_size > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
