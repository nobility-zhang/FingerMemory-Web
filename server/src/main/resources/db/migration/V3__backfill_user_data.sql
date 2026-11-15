INSERT IGNORE INTO fm_user_data (
  owner_id,
  data_version,
  schema_version,
  data_format_version,
  payload_json,
  created_at_ms,
  updated_at_ms
)
SELECT
  id,
  0,
  4,
  4,
  JSON_OBJECT(
    'schemaVersion', 4,
    'dataFormatVersion', 4,
    'tables', JSON_OBJECT(
      'appMeta', JSON_ARRAY(),
      'words', JSON_ARRAY(),
      'studyItems', JSON_ARRAY(),
      'lexiconItems', JSON_ARRAY(),
      'notes', JSON_ARRAY(),
      'practiceAttempts', JSON_ARRAY(),
      'practiceSessions', JSON_ARRAY(),
      'reviewStates', JSON_ARRAY(),
      'reviewLogs', JSON_ARRAY()
    )
  ),
  created_at_ms,
  updated_at_ms
FROM fm_user;
