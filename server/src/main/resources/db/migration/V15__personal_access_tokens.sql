CREATE TABLE fm_personal_access_token (
  id VARCHAR(36) NOT NULL,
  owner_id VARCHAR(36) NOT NULL,
  name VARCHAR(80) NOT NULL,
  token_hash CHAR(64) NOT NULL,
  token_prefix VARCHAR(24) NOT NULL,
  scope VARCHAR(32) NOT NULL,
  created_at_ms BIGINT NOT NULL,
  expires_at_ms BIGINT NOT NULL,
  last_used_at_ms BIGINT NULL,
  revoked_at_ms BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fm_personal_access_token_pk PRIMARY KEY (id),
  CONSTRAINT fm_personal_access_token_hash_unique UNIQUE (token_hash),
  INDEX fm_personal_access_token_owner_created_idx (owner_id, created_at_ms),
  INDEX fm_personal_access_token_owner_active_idx (owner_id, revoked_at_ms, expires_at_ms)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
