CREATE TABLE fm_battle (
  owner_id VARCHAR(36) NOT NULL,
  battle_id VARCHAR(36) NOT NULL,
  rules_version VARCHAR(32) NOT NULL,
  state_version BIGINT NOT NULL,
  phase VARCHAR(32) NOT NULL,
  turn_id INT NOT NULL,
  next_event_sequence INT NOT NULL,
  state_json JSON NOT NULL,
  created_at_ms BIGINT NOT NULL,
  updated_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_battle_pk PRIMARY KEY (owner_id, battle_id),
  INDEX fm_battle_owner_updated_idx (owner_id, updated_at_ms),
  CONSTRAINT fm_battle_state_version_check CHECK (state_version >= 0),
  CONSTRAINT fm_battle_event_sequence_check CHECK (next_event_sequence >= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fm_battle_learning_task (
  owner_id VARCHAR(36) NOT NULL,
  battle_id VARCHAR(36) NOT NULL,
  turn_id INT NOT NULL,
  task_id VARCHAR(36) NOT NULL,
  session_id VARCHAR(36) NOT NULL,
  study_item_id VARCHAR(128) NOT NULL,
  energy_granted BOOLEAN NOT NULL DEFAULT FALSE,
  submission_id VARCHAR(128) NULL,
  created_at_ms BIGINT NOT NULL,
  updated_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_battle_learning_task_pk
    PRIMARY KEY (owner_id, battle_id, turn_id),
  CONSTRAINT fm_battle_learning_task_unique
    UNIQUE (owner_id, session_id, study_item_id),
  CONSTRAINT fm_battle_learning_task_id_unique
    UNIQUE (owner_id, task_id),
  INDEX fm_battle_learning_submission_idx (owner_id, submission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fm_battle_receipt (
  owner_id VARCHAR(36) NOT NULL,
  battle_id VARCHAR(36) NOT NULL,
  action_id VARCHAR(128) NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  response_json JSON NOT NULL,
  state_version BIGINT NOT NULL,
  created_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_battle_receipt_pk
    PRIMARY KEY (owner_id, battle_id, action_id),
  INDEX fm_battle_receipt_created_idx (owner_id, created_at_ms)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fm_battle_event (
  owner_id VARCHAR(36) NOT NULL,
  battle_id VARCHAR(36) NOT NULL,
  event_sequence INT NOT NULL,
  state_version BIGINT NOT NULL,
  event_type VARCHAR(48) NOT NULL,
  event_json JSON NOT NULL,
  created_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_battle_event_pk
    PRIMARY KEY (owner_id, battle_id, event_sequence),
  INDEX fm_battle_event_state_idx (owner_id, battle_id, state_version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
