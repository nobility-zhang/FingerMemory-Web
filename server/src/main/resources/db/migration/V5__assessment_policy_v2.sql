ALTER TABLE fm_user_data
  DROP CHECK fm_user_data_schema_check,
  DROP CHECK fm_user_data_format_check;

ALTER TABLE fm_user_data
  ADD CONSTRAINT fm_user_data_schema_check CHECK (schema_version IN (4, 5)),
  ADD CONSTRAINT fm_user_data_format_check CHECK (data_format_version IN (4, 5));

UPDATE fm_user_data
SET schema_version = 5,
    data_format_version = 5,
    payload_json = JSON_SET(
      payload_json,
      '$.schemaVersion', 5,
      '$.dataFormatVersion', 5
    );
