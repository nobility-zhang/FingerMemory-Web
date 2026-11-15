ALTER TABLE fm_lexicon
  ADD COLUMN import_batch_id VARCHAR(80) NOT NULL DEFAULT 'native' AFTER owner_id,
  DROP INDEX fm_lexicon_owner_name_unique,
  ADD CONSTRAINT fm_lexicon_owner_batch_name_unique
    UNIQUE (owner_id, import_batch_id, normalized_name);

CREATE TABLE fm_user_data (
  owner_id VARCHAR(36) NOT NULL,
  data_version BIGINT NOT NULL DEFAULT 0,
  schema_version INT NOT NULL,
  data_format_version INT NOT NULL,
  payload_json JSON NOT NULL,
  created_at_ms BIGINT NOT NULL,
  updated_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_user_data_pk PRIMARY KEY (owner_id),
  CONSTRAINT fm_user_data_version_check CHECK (data_version >= 0),
  CONSTRAINT fm_user_data_schema_check CHECK (schema_version = 4),
  CONSTRAINT fm_user_data_format_check CHECK (data_format_version = 4)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fm_review_receipt (
  owner_id VARCHAR(36) NOT NULL,
  submission_id VARCHAR(128) NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  response_json JSON NOT NULL,
  reviewed_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_review_receipt_pk PRIMARY KEY (owner_id, submission_id),
  INDEX fm_review_receipt_reviewed_idx (owner_id, reviewed_at_ms)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fm_reward_event (
  owner_id VARCHAR(36) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  event_key VARCHAR(128) NOT NULL,
  reward_eligible BOOLEAN NOT NULL,
  created_at_ms BIGINT NOT NULL,
  payload_json JSON NOT NULL,
  CONSTRAINT fm_reward_event_pk PRIMARY KEY (owner_id, event_type, event_key),
  INDEX fm_reward_event_created_idx (owner_id, created_at_ms)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fm_migration_receipt (
  owner_id VARCHAR(36) NOT NULL,
  batch_id VARCHAR(80) NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  response_json JSON NOT NULL,
  created_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_migration_receipt_pk PRIMARY KEY (owner_id, batch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fm_backup_receipt (
  owner_id VARCHAR(36) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  response_json JSON NOT NULL,
  created_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_backup_receipt_pk PRIMARY KEY (owner_id, idempotency_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fm_import_id_map (
  owner_id VARCHAR(36) NOT NULL,
  batch_id VARCHAR(80) NOT NULL,
  entity_type VARCHAR(32) NOT NULL,
  source_id VARCHAR(128) NOT NULL,
  target_id VARCHAR(128) NOT NULL,
  CONSTRAINT fm_import_id_map_pk
    PRIMARY KEY (owner_id, batch_id, entity_type, source_id),
  INDEX fm_import_id_map_target_idx (owner_id, target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
