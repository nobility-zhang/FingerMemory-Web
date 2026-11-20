ALTER TABLE fm_user_data
  ADD COLUMN data_epoch CHAR(36) NULL AFTER data_version;

ALTER TABLE fm_battle
  ADD COLUMN data_epoch CHAR(36) NULL AFTER battle_id;

ALTER TABLE fm_battle_receipt
  ADD COLUMN data_epoch CHAR(36) NULL AFTER battle_id;

ALTER TABLE fm_adventure_run
  ADD COLUMN data_epoch CHAR(36) NULL AFTER id;

ALTER TABLE fm_adventure_receipt
  ADD COLUMN data_epoch CHAR(36) NULL AFTER run_id;

ALTER TABLE fm_content_installation_receipt
  ADD COLUMN data_epoch CHAR(36) NULL AFTER owner_id;

UPDATE fm_user_data
SET data_epoch = UUID();

UPDATE fm_battle battle
JOIN fm_user_data data ON data.owner_id = battle.owner_id
SET battle.data_epoch = data.data_epoch;

UPDATE fm_battle_receipt receipt
JOIN fm_user_data data ON data.owner_id = receipt.owner_id
SET receipt.data_epoch = data.data_epoch;

UPDATE fm_adventure_run run_row
JOIN fm_user_data data ON data.owner_id = run_row.owner_id
SET run_row.data_epoch = data.data_epoch;

UPDATE fm_adventure_receipt receipt
JOIN fm_user_data data ON data.owner_id = receipt.owner_id
SET receipt.data_epoch = data.data_epoch;

UPDATE fm_content_installation_receipt receipt
JOIN fm_user_data data ON data.owner_id = receipt.owner_id
SET receipt.data_epoch = data.data_epoch;

ALTER TABLE fm_user_data
  MODIFY COLUMN data_epoch CHAR(36) NOT NULL;

ALTER TABLE fm_battle
  MODIFY COLUMN data_epoch CHAR(36) NOT NULL,
  ADD INDEX fm_battle_owner_epoch_idx (owner_id, data_epoch);

ALTER TABLE fm_battle_receipt
  MODIFY COLUMN data_epoch CHAR(36) NOT NULL,
  DROP PRIMARY KEY,
  ADD CONSTRAINT fm_battle_receipt_pk
    PRIMARY KEY (owner_id, battle_id, data_epoch, action_id),
  ADD INDEX fm_battle_receipt_owner_epoch_idx (owner_id, data_epoch);

ALTER TABLE fm_adventure_run
  MODIFY COLUMN data_epoch CHAR(36) NOT NULL,
  ADD INDEX fm_adventure_run_owner_epoch_idx (owner_id, data_epoch);

ALTER TABLE fm_adventure_receipt
  MODIFY COLUMN data_epoch CHAR(36) NOT NULL,
  DROP PRIMARY KEY,
  ADD CONSTRAINT fm_adventure_receipt_pk
    PRIMARY KEY (owner_id, run_id, data_epoch, action_id),
  ADD INDEX fm_adventure_receipt_owner_epoch_idx (owner_id, data_epoch);

ALTER TABLE fm_content_installation_receipt
  MODIFY COLUMN data_epoch CHAR(36) NOT NULL,
  DROP PRIMARY KEY,
  ADD CONSTRAINT fm_content_installation_receipt_pk
    PRIMARY KEY (owner_id, data_epoch, idempotency_key),
  ADD INDEX fm_content_installation_receipt_epoch_idx (owner_id, data_epoch);
