package com.fingermemory.server.backup;

import com.fingermemory.server.common.JsonSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class AccountBackupTableStore {
    private static final Set<String> JSON_COLUMNS = Set.of(
            "manifest_json",
            "response_json",
            "state_json",
            "event_json",
            "flags_json",
            "deck_json",
            "payload_json",
            "candidates_json",
            "inherited_flags_json");

    private static final List<TableSpec> TABLES = List.of(
            spec(
                    "contentInstallations",
                    "fm_content_installation",
                    List.of(
                            "id", "catalog_content_id", "namespace_id", "book_id",
                            "content_version", "content_hash", "title", "entry_node_id",
                            "engine_version", "schema_version", "manifest_json", "word_count",
                            "chapter_count", "node_count", "character_count", "card_count",
                            "encounter_count", "asset_count", "status", "installed_at_ms",
                            "updated_at_ms"),
                    List.of(
                            "owner_id", "id", "catalog_content_id", "namespace_id", "book_id",
                            "content_version", "content_hash", "storage_relative_path", "title",
                            "entry_node_id", "engine_version", "schema_version", "manifest_json",
                            "word_count", "chapter_count", "node_count", "character_count",
                            "card_count", "encounter_count", "asset_count", "status",
                            "installed_at_ms", "updated_at_ms"),
                    "id"),
            spec(
                    "contentInstallationReceipts",
                    "fm_content_installation_receipt",
                    List.of(
                            "data_epoch", "idempotency_key", "request_fingerprint", "installation_id",
                            "response_json", "created_at_ms"),
                    "idempotency_key"),
            spec(
                    "contentAssets",
                    "fm_content_asset",
                    List.of(
                            "installation_id", "asset_path", "content_hash", "mime",
                            "byte_size", "asset_kind", "license_text", "created_at_ms"),
                    "installation_id, asset_path"),
            spec(
                    "battles",
                    "fm_battle",
                    List.of(
                            "battle_id", "data_epoch", "rules_version", "state_version", "phase", "turn_id",
                            "next_event_sequence", "state_json", "created_at_ms", "updated_at_ms"),
                    "battle_id"),
            spec(
                    "battleLearningTasks",
                    "fm_battle_learning_task",
                    List.of(
                            "battle_id", "turn_id", "task_id", "session_id", "study_item_id",
                            "energy_granted", "submission_id", "created_at_ms", "updated_at_ms"),
                    "battle_id, turn_id"),
            spec(
                    "battleReceipts",
                    "fm_battle_receipt",
                    List.of(
                            "battle_id", "data_epoch", "action_id", "request_fingerprint", "response_json",
                            "state_version", "created_at_ms"),
                    "battle_id, action_id"),
            spec(
                    "battleEvents",
                    "fm_battle_event",
                    List.of(
                            "battle_id", "event_sequence", "state_version", "event_type",
                            "event_json", "created_at_ms"),
                    "battle_id, event_sequence"),
            spec(
                    "adventureRuns",
                    "fm_adventure_run",
                    List.of(
                            "id", "data_epoch", "installation_id", "namespace_id", "book_id",
                            "content_version", "content_hash", "entry_node_id", "current_node_id",
                            "session_id", "companion_key", "status", "active_slot",
                            "source_completion_id", "run_version", "flags_json", "deck_json",
                            "battle_id", "created_at_ms", "updated_at_ms"),
                    "id"),
            spec(
                    "adventureReceipts",
                    "fm_adventure_receipt",
                    List.of(
                            "run_id", "data_epoch", "action_id", "request_fingerprint", "response_json",
                            "run_version", "created_at_ms"),
                    "run_id, action_id"),
            spec(
                    "adventureBattleBindings",
                    "fm_adventure_battle_binding",
                    List.of(
                            "run_id", "node_id", "encounter_id", "battle_id", "created_at_ms"),
                    "battle_id"),
            spec(
                    "adventureEvents",
                    "fm_adventure_event",
                    List.of(
                            "run_id", "event_sequence", "event_type", "node_id",
                            "payload_json", "created_at_ms"),
                    "run_id, event_sequence"),
            spec(
                    "adventureRewardOffers",
                    "fm_adventure_reward_offer",
                    List.of(
                            "run_id", "offer_id", "node_id", "target_node_id", "candidates_json",
                            "status", "selected_choice_id", "selected_card_definition_id",
                            "created_at_ms", "updated_at_ms"),
                    "run_id, offer_id"),
            spec(
                    "adventureCharacterUnlocks",
                    "fm_adventure_character_unlock",
                    List.of(
                            "character_key", "unlock_key", "source_run_id", "source_node_id",
                            "unlocked_at_ms"),
                    "character_key"),
            spec(
                    "adventureRecollections",
                    "fm_adventure_recollection",
                    List.of(
                            "recollection_key", "namespace_id", "book_id", "chapter_id",
                            "title", "speaker", "text", "source_run_id", "source_node_id",
                            "unlocked_at_ms"),
                    "recollection_key"),
            spec(
                    "adventureCompletions",
                    "fm_adventure_completion",
                    List.of(
                            "id", "run_id", "namespace_id", "book_id", "series_id",
                            "content_version", "content_hash", "ending_id", "ending_title",
                            "ending_text", "source_node_id", "inherited_flags_json",
                            "completed_at_ms"),
                    "id"));

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AccountBackupTableStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public ObjectNode export(String ownerId) {
        ObjectNode tables = objectMapper.createObjectNode();
        for (TableSpec spec : TABLES) {
            ArrayNode values = tables.putArray(spec.key());
            String sql = "SELECT " + String.join(", ", spec.exportColumns())
                    + " FROM " + spec.table()
                    + " WHERE owner_id = ? ORDER BY " + spec.orderBy();
            for (Map<String, Object> row : jdbcTemplate.queryForList(sql, ownerId)) {
                ObjectNode value = values.addObject();
                for (String column : spec.exportColumns()) {
                    putColumn(value, column, row.get(column));
                }
            }
        }
        return tables;
    }

    public ObjectNode emptySnapshot() {
        ObjectNode tables = objectMapper.createObjectNode();
        for (TableSpec spec : TABLES) {
            tables.putArray(spec.key());
        }
        return tables;
    }

    public ObjectNode validateSnapshot(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw JsonSupport.validation("备份缺少 serverTables");
        }
        ObjectNode tables = (ObjectNode) input;
        Set<String> allowedKeys = TABLES.stream()
                .map(TableSpec::key)
                .collect(Collectors.toSet());
        tables.properties().forEach(entry -> {
            if (!allowedKeys.contains(entry.getKey())) {
                throw JsonSupport.validation("备份包含未知服务器表：" + entry.getKey());
            }
        });
        ObjectNode normalized = emptySnapshot();
        for (TableSpec spec : TABLES) {
            JsonNode source = tables.get(spec.key());
            if (source == null) {
                throw JsonSupport.validation("备份缺少服务器表：" + spec.key());
            }
            if (!source.isArray()) {
                throw JsonSupport.validation("服务器表必须是数组：" + spec.key());
            }
            ArrayNode target = (ArrayNode) normalized.get(spec.key());
            Set<String> allowedColumns = Set.copyOf(spec.exportColumns());
            for (JsonNode row : source) {
                if (!row.isObject()) {
                    throw JsonSupport.validation("服务器表行必须是对象：" + spec.key());
                }
                ObjectNode record = (ObjectNode) row;
                record.properties().forEach(property -> {
                    if (!allowedColumns.contains(property.getKey())) {
                        throw JsonSupport.validation(
                                "服务器表包含未知字段：" + spec.key() + "." + property.getKey());
                    }
                });
                ObjectNode copy = target.addObject();
                for (String column : spec.exportColumns()) {
                    JsonNode value = record.get(column);
                    if (value == null) {
                        throw JsonSupport.validation(
                                "服务器表缺少字段：" + spec.key() + "." + column);
                    }
                    copy.set(column, value.deepCopy());
                }
            }
        }
        return normalized;
    }

    public void deleteOwner(String ownerId) {
        for (int index = TABLES.size() - 1; index >= 0; index -= 1) {
            TableSpec spec = TABLES.get(index);
            jdbcTemplate.update(
                    "DELETE FROM " + spec.table() + " WHERE owner_id = ?",
                    ownerId);
        }
    }

    public void restoreOwner(String ownerId, ObjectNode tables) {
        for (TableSpec spec : TABLES) {
            ArrayNode rows = (ArrayNode) tables.get(spec.key());
            String insertSql = insertSql(spec);
            for (JsonNode input : rows) {
                Object[] values = insertValues(ownerId, spec, (ObjectNode) input);
                jdbcTemplate.update(insertSql, values);
            }
        }
    }

    public int count(ObjectNode tables, String key) {
        return JsonSupport.requireArray(tables, key).size();
    }

    private String insertSql(TableSpec spec) {
        List<String> placeholders = new ArrayList<>();
        for (String column : spec.insertColumns()) {
            placeholders.add(JSON_COLUMNS.contains(column) ? "CAST(? AS JSON)" : "?");
        }
        return "INSERT INTO " + spec.table()
                + " (" + String.join(", ", spec.insertColumns()) + ")"
                + " VALUES (" + String.join(", ", placeholders) + ")";
    }

    private Object[] insertValues(String ownerId, TableSpec spec, ObjectNode input) {
        List<Object> values = new ArrayList<>();
        for (String column : spec.insertColumns()) {
            if ("owner_id".equals(column)) {
                values.add(ownerId);
            } else if ("storage_relative_path".equals(column)) {
                values.add(ownerId + "/" + JsonSupport.requireText(input, "content_hash", 64));
            } else {
                values.add(jsonValue(input, column, JSON_COLUMNS.contains(column)));
            }
        }
        return values.toArray();
    }

    private Object jsonValue(ObjectNode input, String column, boolean json) {
        JsonNode value = input.get(column);
        if (value == null || value.isNull()) {
            return null;
        }
        if (json) {
            return objectMapper.writeValueAsString(value);
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isIntegralNumber()) {
            return value.longValue();
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        return value.stringValue();
    }

    private void putColumn(ObjectNode value, String column, Object raw) {
        if (raw == null) {
            value.putNull(column);
        } else if (JSON_COLUMNS.contains(column)) {
            value.set(column, objectMapper.readTree(raw.toString()));
        } else if (raw instanceof Boolean bool) {
            value.put(column, bool);
        } else if (raw instanceof Number number) {
            value.put(column, number.longValue());
        } else {
            value.put(column, raw.toString());
        }
    }

    private static TableSpec spec(
            String key,
            String table,
            List<String> exportColumns,
            String orderBy) {
        return spec(
                key,
                table,
                exportColumns,
                prependOwner(exportColumns),
                orderBy);
    }

    private static TableSpec spec(
            String key,
            String table,
            List<String> exportColumns,
            List<String> insertColumns,
            String orderBy) {
        return new TableSpec(
                key,
                table,
                List.copyOf(exportColumns),
                List.copyOf(insertColumns),
                orderBy);
    }

    private static List<String> prependOwner(List<String> columns) {
        List<String> values = new ArrayList<>();
        values.add("owner_id");
        values.addAll(columns);
        return values;
    }

    private record TableSpec(
            String key,
            String table,
            List<String> exportColumns,
            List<String> insertColumns,
            String orderBy) {
    }
}
