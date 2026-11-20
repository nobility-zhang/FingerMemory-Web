CREATE TABLE fm_gm_content_asset (
  content_id VARCHAR(36) NOT NULL,
  asset_path VARCHAR(512) NOT NULL,
  content_hash CHAR(64) NOT NULL,
  mime VARCHAR(128) NOT NULL,
  byte_size BIGINT NOT NULL,
  kind VARCHAR(64) NOT NULL,
  license VARCHAR(512) NOT NULL,
  CONSTRAINT fm_gm_content_asset_pk PRIMARY KEY (content_id, asset_path),
  CONSTRAINT fm_gm_content_asset_size_check CHECK (byte_size > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
