ALTER TABLE fm_adventure_run
  ADD COLUMN source_completion_id VARCHAR(36) NULL AFTER active_slot;

CREATE TABLE fm_adventure_completion (
  owner_id VARCHAR(36) NOT NULL,
  id VARCHAR(36) NOT NULL,
  run_id VARCHAR(36) NOT NULL,
  namespace_id VARCHAR(128) NOT NULL,
  book_id VARCHAR(128) NOT NULL,
  series_id VARCHAR(128) NOT NULL,
  content_version VARCHAR(32) NOT NULL,
  content_hash CHAR(64) NOT NULL,
  ending_id VARCHAR(128) NOT NULL,
  ending_title VARCHAR(200) NOT NULL,
  ending_text TEXT NOT NULL,
  source_node_id VARCHAR(128) NOT NULL,
  inherited_flags_json JSON NOT NULL,
  completed_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_adventure_completion_pk PRIMARY KEY (owner_id, id),
  CONSTRAINT fm_adventure_completion_run_version_unique
    UNIQUE (owner_id, run_id, content_hash),
  INDEX fm_adventure_completion_book_idx
    (owner_id, namespace_id, book_id, completed_at_ms),
  INDEX fm_adventure_completion_series_idx
    (owner_id, series_id, completed_at_ms)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
