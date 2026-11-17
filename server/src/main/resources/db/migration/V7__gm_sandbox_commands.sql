CREATE TABLE fm_gm_sandbox (
  owner_admin_id VARCHAR(36) NOT NULL,
  id VARCHAR(36) NOT NULL,
  template_id VARCHAR(64) NOT NULL,
  content_installation_id VARCHAR(128) NULL,
  version BIGINT NOT NULL,
  data_epoch VARCHAR(36) NOT NULL,
  state_json JSON NOT NULL,
  created_at_ms BIGINT NOT NULL,
  updated_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_gm_sandbox_pk PRIMARY KEY (owner_admin_id, id),
  CONSTRAINT fm_gm_sandbox_version_check CHECK (version >= 0),
  INDEX fm_gm_sandbox_owner_updated_idx (owner_admin_id, updated_at_ms),
  INDEX fm_gm_sandbox_owner_template_idx (owner_admin_id, template_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fm_gm_sandbox_receipt (
  owner_admin_id VARCHAR(36) NOT NULL,
  sandbox_id VARCHAR(36) NOT NULL,
  data_epoch VARCHAR(36) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  response_json JSON NOT NULL,
  sandbox_version BIGINT NOT NULL,
  created_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_gm_sandbox_receipt_pk
    PRIMARY KEY (owner_admin_id, sandbox_id, data_epoch, idempotency_key),
  INDEX fm_gm_sandbox_receipt_created_idx (owner_admin_id, created_at_ms)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
