CREATE TABLE fm_adventure_run (
  owner_id VARCHAR(36) NOT NULL,
  id VARCHAR(36) NOT NULL,
  installation_id VARCHAR(36) NOT NULL,
  namespace_id VARCHAR(128) NOT NULL,
  book_id VARCHAR(128) NOT NULL,
  content_version VARCHAR(32) NOT NULL,
  content_hash CHAR(64) NOT NULL,
  entry_node_id VARCHAR(128) NOT NULL,
  current_node_id VARCHAR(128) NOT NULL,
  session_id VARCHAR(36) NOT NULL,
  companion_key VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  active_slot VARCHAR(36) NULL,
  run_version BIGINT NOT NULL,
  flags_json JSON NOT NULL,
  battle_id VARCHAR(36) NULL,
  created_at_ms BIGINT NOT NULL,
  updated_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_adventure_run_pk PRIMARY KEY (owner_id, id),
  CONSTRAINT fm_adventure_run_active_unique UNIQUE (owner_id, active_slot),
  CONSTRAINT fm_adventure_run_version_check CHECK (run_version >= 0),
  INDEX fm_adventure_run_owner_updated_idx (owner_id, updated_at_ms),
  INDEX fm_adventure_run_owner_book_idx (owner_id, namespace_id, book_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fm_adventure_receipt (
  owner_id VARCHAR(36) NOT NULL,
  run_id VARCHAR(36) NOT NULL,
  action_id VARCHAR(128) NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  response_json JSON NOT NULL,
  run_version BIGINT NOT NULL,
  created_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_adventure_receipt_pk
    PRIMARY KEY (owner_id, run_id, action_id),
  INDEX fm_adventure_receipt_created_idx (owner_id, created_at_ms)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fm_adventure_battle_binding (
  owner_id VARCHAR(36) NOT NULL,
  run_id VARCHAR(36) NOT NULL,
  node_id VARCHAR(128) NOT NULL,
  encounter_id VARCHAR(128) NOT NULL,
  battle_id VARCHAR(36) NOT NULL,
  created_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_adventure_battle_binding_pk PRIMARY KEY (owner_id, battle_id),
  CONSTRAINT fm_adventure_battle_binding_node_unique
    UNIQUE (owner_id, run_id, node_id),
  INDEX fm_adventure_battle_binding_run_idx (owner_id, run_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fm_adventure_event (
  owner_id VARCHAR(36) NOT NULL,
  run_id VARCHAR(36) NOT NULL,
  event_sequence BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  node_id VARCHAR(128) NOT NULL,
  payload_json JSON NOT NULL,
  created_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_adventure_event_pk
    PRIMARY KEY (owner_id, run_id, event_sequence)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
