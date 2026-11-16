ALTER TABLE fm_user
  ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER' AFTER status,
  ADD COLUMN account_kind VARCHAR(16) NOT NULL DEFAULT 'standard' AFTER role,
  ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE AFTER account_kind,
  ADD CONSTRAINT fm_user_role_check CHECK (role IN ('USER', 'ADMIN')),
  ADD CONSTRAINT fm_user_account_kind_check CHECK (account_kind IN ('standard', 'test'));

CREATE INDEX fm_user_role_status_idx ON fm_user (role, status);

CREATE TABLE fm_admin_bootstrap_receipt (
  receipt_key VARCHAR(64) NOT NULL,
  user_id VARCHAR(36) NOT NULL,
  username_normalized VARCHAR(64) NOT NULL,
  created_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_admin_bootstrap_receipt_pk PRIMARY KEY (receipt_key),
  CONSTRAINT fm_admin_bootstrap_user_unique UNIQUE (user_id),
  INDEX fm_admin_bootstrap_username_idx (username_normalized)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fm_gm_audit_event (
  id VARCHAR(36) NOT NULL,
  actor_id VARCHAR(36) NOT NULL,
  action VARCHAR(64) NOT NULL,
  target VARCHAR(120) NOT NULL,
  scope VARCHAR(32) NOT NULL,
  request_id VARCHAR(120) NOT NULL,
  reason VARCHAR(300) NOT NULL,
  outcome VARCHAR(16) NOT NULL,
  occurred_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_gm_audit_event_pk PRIMARY KEY (id),
  CONSTRAINT fm_gm_audit_outcome_check CHECK (outcome IN ('success', 'rejected', 'failed')),
  INDEX fm_gm_audit_actor_time_idx (actor_id, occurred_at_ms),
  INDEX fm_gm_audit_target_time_idx (target, occurred_at_ms)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fm_gm_action_receipt (
  actor_id VARCHAR(36) NOT NULL,
  idempotency_key VARCHAR(120) NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  response_json JSON NOT NULL,
  created_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_gm_action_receipt_pk PRIMARY KEY (actor_id, idempotency_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
