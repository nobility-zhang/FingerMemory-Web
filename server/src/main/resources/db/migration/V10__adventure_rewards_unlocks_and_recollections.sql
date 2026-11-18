ALTER TABLE fm_adventure_run
  ADD COLUMN deck_json JSON NULL AFTER flags_json;

CREATE TABLE fm_adventure_reward_offer (
  owner_id VARCHAR(36) NOT NULL,
  run_id VARCHAR(36) NOT NULL,
  offer_id VARCHAR(36) NOT NULL,
  node_id VARCHAR(128) NOT NULL,
  target_node_id VARCHAR(128) NOT NULL,
  candidates_json JSON NOT NULL,
  status VARCHAR(16) NOT NULL,
  selected_choice_id VARCHAR(128) NULL,
  selected_card_definition_id VARCHAR(64) NULL,
  created_at_ms BIGINT NOT NULL,
  updated_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_adventure_reward_offer_pk
    PRIMARY KEY (owner_id, run_id, offer_id),
  CONSTRAINT fm_adventure_reward_offer_node_unique
    UNIQUE (owner_id, run_id, node_id),
  CONSTRAINT fm_adventure_reward_offer_status_check
    CHECK (status IN ('pending', 'claimed')),
  INDEX fm_adventure_reward_offer_pending_idx
    (owner_id, run_id, status, created_at_ms)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fm_adventure_character_unlock (
  owner_id VARCHAR(36) NOT NULL,
  character_key VARCHAR(64) NOT NULL,
  unlock_key VARCHAR(128) NOT NULL,
  source_run_id VARCHAR(36) NOT NULL,
  source_node_id VARCHAR(128) NOT NULL,
  unlocked_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_adventure_character_unlock_pk
    PRIMARY KEY (owner_id, character_key),
  INDEX fm_adventure_character_unlock_source_idx (owner_id, source_run_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fm_adventure_recollection (
  owner_id VARCHAR(36) NOT NULL,
  recollection_key VARCHAR(255) NOT NULL,
  namespace_id VARCHAR(128) NOT NULL,
  book_id VARCHAR(128) NOT NULL,
  chapter_id VARCHAR(128) NOT NULL,
  title VARCHAR(200) NOT NULL,
  speaker VARCHAR(120) NOT NULL,
  text TEXT NOT NULL,
  source_run_id VARCHAR(36) NOT NULL,
  source_node_id VARCHAR(128) NOT NULL,
  unlocked_at_ms BIGINT NOT NULL,
  CONSTRAINT fm_adventure_recollection_pk
    PRIMARY KEY (owner_id, recollection_key),
  INDEX fm_adventure_recollection_book_idx
    (owner_id, namespace_id, book_id, unlocked_at_ms)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
