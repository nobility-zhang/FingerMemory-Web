package com.fingermemory.server.backup;

import com.fingermemory.server.backup.BackupDtos.BackupPreview;
import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.common.JsonSupport;
import com.fingermemory.server.learning.LockedUserData;
import com.fingermemory.server.learning.RewardEventMapper;
import com.fingermemory.server.learning.RewardEventMapper.RewardEventRow;
import com.fingermemory.server.learning.ReviewReceiptMapper;
import com.fingermemory.server.learning.ReviewReceiptRow;
import com.fingermemory.server.learning.UserDataRow;
import com.fingermemory.server.learning.UserDataStore;
import com.fingermemory.server.learning.UserDataValidator;
import com.fingermemory.server.lexicon.Lexicon;
import com.fingermemory.server.lexicon.LexiconMapper;
import com.fingermemory.server.migration.ImportIdMapMapper;
import com.fingermemory.server.migration.ImportIdMapRow;
import com.fingermemory.server.migration.MigrationDtos.SnapshotCounts;
import com.fingermemory.server.migration.MigrationReceiptMapper;
import com.fingermemory.server.migration.MigrationReceiptRow;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class AccountBackupService {
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_.-]{3,128}$");

    private final UserDataStore userDataStore;
    private final UserDataValidator validator;
    private final LexiconMapper lexiconMapper;
    private final ReviewReceiptMapper reviewReceiptMapper;
    private final RewardEventMapper rewardEventMapper;
    private final MigrationReceiptMapper migrationReceiptMapper;
    private final ImportIdMapMapper importIdMapMapper;
    private final BackupReceiptMapper backupReceiptMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AccountBackupService(
            UserDataStore userDataStore,
            UserDataValidator validator,
            LexiconMapper lexiconMapper,
            ReviewReceiptMapper reviewReceiptMapper,
            RewardEventMapper rewardEventMapper,
            MigrationReceiptMapper migrationReceiptMapper,
            ImportIdMapMapper importIdMapMapper,
            BackupReceiptMapper backupReceiptMapper,
            ObjectMapper objectMapper,
            Clock clock) {
        this.userDataStore = userDataStore;
        this.validator = validator;
        this.lexiconMapper = lexiconMapper;
        this.reviewReceiptMapper = reviewReceiptMapper;
        this.rewardEventMapper = rewardEventMapper;
        this.migrationReceiptMapper = migrationReceiptMapper;
        this.importIdMapMapper = importIdMapMapper;
        this.backupReceiptMapper = backupReceiptMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ObjectNode export(String ownerId) {
        UserDataRow row = userDataStore.read(ownerId);
        ObjectNode backup = JsonSupport.copyObject(
                objectMapper.readTree(row.getPayloadJson()), "user data");
        backup.put("backupVersion", 1);
        backup.put("exportedAt", clock.instant().toEpochMilli());
        backup.put("sourceDataVersion", row.getDataVersion());
        ObjectNode tables = (ObjectNode) backup.get("tables");
        ArrayNode lexicons = tables.putArray("lexicons");
        for (Lexicon lexicon : lexiconMapper.listAllByOwner(ownerId)) {
            lexicons.add(lexiconNode(lexicon));
        }
        backup.set("reviewReceipts", reviewReceipts(ownerId));
        backup.set("rewardEvents", rewardEvents(ownerId));
        backup.set("migrationReceipts", migrationReceipts(ownerId));
        backup.set("idMappings", idMappings(ownerId));
        return backup;
    }

    public BackupPreview preview(JsonNode input) {
        ObjectNode backup = requireBackup(input);
        ValidatedBackup validated = validate(backup);
        return new BackupPreview(
                validated.fingerprint(),
                JsonSupport.requireTimestamp(backup, "exportedAt"),
                JsonSupport.requireLong(backup, "sourceDataVersion"),
                validated.counts(),
                JsonSupport.requireArray(backup, "reviewReceipts").size(),
                JsonSupport.requireArray(backup, "rewardEvents").size(),
                JsonSupport.requireArray(backup, "migrationReceipts").size(),
                JsonSupport.requireArray(backup, "idMappings").size());
    }

    @Transactional
    public ObjectNode restore(String ownerId, String idempotencyKey, JsonNode input) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "恢复幂等键必须为 3-128 位字母、数字、点、下划线或连字符");
        }
        ObjectNode backup = requireBackup(input);
        String fingerprint = JsonSupport.fingerprint(objectMapper, backup);
        ObjectNode receipt = findReceipt(ownerId, idempotencyKey, fingerprint);
        if (receipt != null) {
            return receipt;
        }
        ValidatedBackup validated = validate(backup);

        LockedUserData locked = userDataStore.lock(ownerId);
        ObjectNode currentReceipt = findReceipt(ownerId, idempotencyKey, fingerprint);
        if (currentReceipt != null) {
            return currentReceipt;
        }
        validator.validate(locked.document(), lexiconIds(ownerId));

        lexiconMapper.deleteAllByOwner(ownerId);
        reviewReceiptMapper.deleteByOwner(ownerId);
        rewardEventMapper.deleteByOwner(ownerId);
        migrationReceiptMapper.deleteByOwner(ownerId);
        importIdMapMapper.deleteByOwner(ownerId);
        backupReceiptMapper.deleteByOwner(ownerId);

        ObjectNode restoredDocument = restoreDocument(backup);
        try {
            for (ObjectNode lexiconNode : JsonSupport.objectValues(
                    JsonSupport.requireArray((ObjectNode) backup.get("tables"), "lexicons"),
                    "lexicons")) {
                lexiconMapper.insert(lexiconFromBackup(ownerId, lexiconNode));
            }
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "BACKUP_ID_CONFLICT",
                    "备份中的词库 ID 与其他账号范围冲突");
        }
        validator.validate(restoredDocument, lexiconIdsFromBackup(backup));
        insertReceipts(ownerId, backup);
        insertRewardEvents(ownerId, backup);
        insertMigrationReceipts(ownerId, backup);
        insertIdMappings(ownerId, backup);
        userDataStore.save(locked, restoredDocument);

        long now = clock.instant().toEpochMilli();
        ObjectNode response = objectMapper.createObjectNode();
        response.put("outcome", "restored");
        response.put("fingerprint", fingerprint);
        response.put("dataVersion", locked.row().getDataVersion());
        response.set("counts", objectMapper.valueToTree(validated.counts()));
        backupReceiptMapper.insert(
                ownerId,
                idempotencyKey,
                fingerprint,
                objectMapper.writeValueAsString(response),
                now);
        return response;
    }

    private ValidatedBackup validate(ObjectNode backup) {
        if (JsonSupport.requireInt(backup, "backupVersion") != 1) {
            throw JsonSupport.validation("不支持的用户备份版本");
        }
        JsonSupport.requireTimestamp(backup, "exportedAt");
        JsonSupport.requireLong(backup, "sourceDataVersion");
        JsonNode tablesNode = backup.get("tables");
        if (tablesNode == null || !tablesNode.isObject()) {
            throw JsonSupport.validation("用户备份缺少 tables");
        }
        ObjectNode tables = (ObjectNode) tablesNode;
        ArrayNode lexicons = JsonSupport.requireArray(tables, "lexicons");
        Set<String> lexiconIds = new HashSet<>();
        for (ObjectNode lexicon : JsonSupport.objectValues(lexicons, "lexicons")) {
            lexiconIds.add(JsonSupport.requireIdentifier(lexicon, "id"));
            JsonSupport.requireText(lexicon, "name", 120);
            JsonSupport.requireText(lexicon, "normalizedName", 120);
            JsonSupport.requireText(lexicon, "importBatchId", 80);
            JsonSupport.requireText(lexicon, "source", 32);
            JsonSupport.requireTimestamp(lexicon, "createdAt");
            JsonSupport.requireTimestamp(lexicon, "updatedAt");
            JsonSupport.requireLong(lexicon, "version");
            JsonNode archivedAt = lexicon.get("archivedAt");
            if (archivedAt != null && !archivedAt.isNull()
                    && (!archivedAt.isIntegralNumber() || archivedAt.longValue() <= 0)) {
                throw JsonSupport.validation("备份词库 archivedAt 无效");
            }
        }
        if (lexiconIds.size() != lexicons.size()) {
            throw JsonSupport.validation("备份词库包含重复 ID");
        }
        validator.validate(backup, lexiconIds);
        JsonSupport.requireUniqueIds(
                JsonSupport.requireArray(backup, "reviewReceipts"),
                "submissionId",
                "reviewReceipts");
        validateReviewReceipts(JsonSupport.requireArray(backup, "reviewReceipts"));
        JsonSupport.requireUniqueIds(
                JsonSupport.requireArray(backup, "migrationReceipts"),
                "batchId",
                "migrationReceipts");
        validateMigrationReceipts(JsonSupport.requireArray(backup, "migrationReceipts"));
        validateRewardEvents(JsonSupport.requireArray(backup, "rewardEvents"));
        validateIdMappings(JsonSupport.requireArray(backup, "idMappings"));
        SnapshotCounts counts = new SnapshotCounts(
                lexicons.size(),
                JsonSupport.requireArray(tables, "words").size(),
                JsonSupport.requireArray(tables, "studyItems").size(),
                JsonSupport.requireArray(tables, "lexiconItems").size(),
                JsonSupport.requireArray(tables, "notes").size(),
                JsonSupport.requireArray(tables, "practiceAttempts").size(),
                JsonSupport.requireArray(tables, "practiceSessions").size(),
                JsonSupport.requireArray(tables, "reviewStates").size(),
                JsonSupport.requireArray(tables, "reviewLogs").size());
        return new ValidatedBackup(JsonSupport.fingerprint(objectMapper, backup), counts);
    }

    private ObjectNode restoreDocument(ObjectNode backup) {
        ObjectNode document = JsonSupport.emptyUserData(objectMapper);
        ObjectNode targetTables = (ObjectNode) document.get("tables");
        ObjectNode sourceTables = (ObjectNode) backup.get("tables");
        for (String table : List.of(
                "appMeta", "words", "studyItems", "lexiconItems", "notes",
                "practiceAttempts", "practiceSessions", "reviewStates", "reviewLogs")) {
            ArrayNode target = targetTables.withArray(table);
            JsonSupport.requireArray(sourceTables, table)
                    .forEach(value -> target.add(value.deepCopy()));
        }
        return document;
    }

    private ObjectNode requireBackup(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw JsonSupport.validation("请求必须包含 backup 对象");
        }
        JsonNode backup = input.get("backup");
        if (backup == null || !backup.isObject()) {
            throw JsonSupport.validation("请求必须包含 backup 对象");
        }
        return ((ObjectNode) backup).deepCopy();
    }

    private ObjectNode findReceipt(String ownerId, String key, String fingerprint) {
        BackupReceiptRow receipt = backupReceiptMapper.find(ownerId, key);
        if (receipt == null) {
            return null;
        }
        if (!fingerprint.equals(receipt.getRequestFingerprint())) {
            throw new ApiException(HttpStatus.CONFLICT, "BACKUP_IDEMPOTENCY_CONFLICT",
                    "恢复幂等键已用于不同备份");
        }
        return JsonSupport.copyObject(
                objectMapper.readTree(receipt.getResponseJson()), "backup receipt");
    }

    private ObjectNode lexiconNode(Lexicon lexicon) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("id", lexicon.getId());
        value.put("name", lexicon.getName());
        value.put("normalizedName", lexicon.getNormalizedName());
        value.put("description", lexicon.getDescription());
        value.put("source", lexicon.getSource());
        value.put("importBatchId", lexicon.getImportBatchId());
        if (lexicon.getArchivedAtMs() == null) {
            value.putNull("archivedAt");
        } else {
            value.put("archivedAt", lexicon.getArchivedAtMs());
        }
        value.put("createdAt", lexicon.getCreatedAtMs());
        value.put("updatedAt", lexicon.getUpdatedAtMs());
        value.put("version", lexicon.getVersion());
        return value;
    }

    private Lexicon lexiconFromBackup(String ownerId, ObjectNode value) {
        Lexicon lexicon = new Lexicon();
        lexicon.setId(JsonSupport.requireIdentifier(value, "id"));
        lexicon.setOwnerId(ownerId);
        lexicon.setImportBatchId(JsonSupport.requireText(value, "importBatchId", 80));
        lexicon.setName(JsonSupport.requireText(value, "name", 120));
        lexicon.setNormalizedName(JsonSupport.requireText(value, "normalizedName", 120));
        lexicon.setDescription(JsonSupport.optionalText(value, "description", 2000));
        lexicon.setSource(JsonSupport.requireText(value, "source", 32));
        JsonNode archivedAt = value.get("archivedAt");
        lexicon.setArchivedAtMs(archivedAt == null || archivedAt.isNull()
                ? null : archivedAt.longValue());
        lexicon.setCreatedAtMs(JsonSupport.requireTimestamp(value, "createdAt"));
        lexicon.setUpdatedAtMs(JsonSupport.requireTimestamp(value, "updatedAt"));
        lexicon.setVersion(JsonSupport.requireLong(value, "version"));
        return lexicon;
    }

    private ArrayNode reviewReceipts(String ownerId) {
        ArrayNode values = objectMapper.createArrayNode();
        for (ReviewReceiptRow row : reviewReceiptMapper.listByOwner(ownerId)) {
            ObjectNode value = values.addObject();
            value.put("submissionId", row.getSubmissionId());
            value.put("requestFingerprint", row.getRequestFingerprint());
            value.set("response", objectMapper.readTree(row.getResponseJson()));
            value.put("reviewedAt", row.getReviewedAtMs());
        }
        return values;
    }

    private ArrayNode rewardEvents(String ownerId) {
        ArrayNode values = objectMapper.createArrayNode();
        for (RewardEventRow row : rewardEventMapper.listByOwner(ownerId)) {
            values.add(rewardEventNode(row));
        }
        return values;
    }

    private ObjectNode rewardEventNode(RewardEventRow row) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("eventType", row.getEventType());
        value.put("eventKey", row.getEventKey());
        value.put("rewardEligible", row.isRewardEligible());
        value.put("createdAt", row.getCreatedAtMs());
        value.set("payload", objectMapper.readTree(row.getPayloadJson()));
        return value;
    }

    private ArrayNode migrationReceipts(String ownerId) {
        ArrayNode values = objectMapper.createArrayNode();
        for (MigrationReceiptRow row : migrationReceiptMapper.listByOwner(ownerId)) {
            ObjectNode value = values.addObject();
            value.put("batchId", row.getBatchId());
            value.put("requestFingerprint", row.getRequestFingerprint());
            value.set("response", objectMapper.readTree(row.getResponseJson()));
            value.put("createdAt", row.getCreatedAtMs());
        }
        return values;
    }

    private ArrayNode idMappings(String ownerId) {
        ArrayNode values = objectMapper.createArrayNode();
        for (ImportIdMapRow row : importIdMapMapper.listByOwner(ownerId)) {
            ObjectNode value = values.addObject();
            value.put("batchId", row.getBatchId());
            value.put("entityType", row.getEntityType());
            value.put("sourceId", row.getSourceId());
            value.put("targetId", row.getTargetId());
        }
        return values;
    }

    private void insertReceipts(String ownerId, ObjectNode backup) {
        for (ObjectNode value : JsonSupport.objectValues(
                JsonSupport.requireArray(backup, "reviewReceipts"), "reviewReceipts")) {
            reviewReceiptMapper.insert(
                    ownerId,
                    JsonSupport.requireText(value, "submissionId", 128),
                    requireFingerprint(value),
                    objectMapper.writeValueAsString(value.get("response")),
                    JsonSupport.requireTimestamp(value, "reviewedAt"));
        }
    }

    private void insertRewardEvents(String ownerId, ObjectNode backup) {
        for (ObjectNode value : JsonSupport.objectValues(
                JsonSupport.requireArray(backup, "rewardEvents"), "rewardEvents")) {
            rewardEventMapper.insert(
                    ownerId,
                    JsonSupport.requireText(value, "eventType", 32),
                    JsonSupport.requireText(value, "eventKey", 128),
                    JsonSupport.requireBoolean(value, "rewardEligible"),
                    JsonSupport.requireTimestamp(value, "createdAt"),
                    objectMapper.writeValueAsString(value.get("payload")));
        }
    }

    private void insertMigrationReceipts(String ownerId, ObjectNode backup) {
        for (ObjectNode value : JsonSupport.objectValues(
                JsonSupport.requireArray(backup, "migrationReceipts"), "migrationReceipts")) {
            migrationReceiptMapper.insert(
                    ownerId,
                    JsonSupport.requireText(value, "batchId", 80),
                    requireFingerprint(value),
                    objectMapper.writeValueAsString(value.get("response")),
                    JsonSupport.requireTimestamp(value, "createdAt"));
        }
    }

    private void insertIdMappings(String ownerId, ObjectNode backup) {
        for (ObjectNode value : JsonSupport.objectValues(
                JsonSupport.requireArray(backup, "idMappings"), "idMappings")) {
            importIdMapMapper.insert(
                    ownerId,
                    JsonSupport.requireText(value, "batchId", 80),
                    JsonSupport.requireText(value, "entityType", 32),
                    JsonSupport.requireText(value, "sourceId", 128),
                    JsonSupport.requireText(value, "targetId", 128));
        }
    }

    private void validateRewardEvents(ArrayNode values) {
        Set<String> keys = new HashSet<>();
        for (ObjectNode value : JsonSupport.objectValues(values, "rewardEvents")) {
            String eventType = JsonSupport.requireText(value, "eventType", 32);
            String eventKey = JsonSupport.requireText(value, "eventKey", 128);
            JsonSupport.requireBoolean(value, "rewardEligible");
            JsonSupport.requireTimestamp(value, "createdAt");
            if (!value.path("payload").isObject()
                    || !keys.add(eventType + "\u0000" + eventKey)) {
                throw JsonSupport.validation("备份奖励事件无效或重复");
            }
        }
    }

    private void validateReviewReceipts(ArrayNode values) {
        for (ObjectNode value : JsonSupport.objectValues(values, "reviewReceipts")) {
            requireFingerprint(value);
            JsonSupport.requireTimestamp(value, "reviewedAt");
            if (!value.path("response").isObject()) {
                throw JsonSupport.validation("备份正式复习回执响应无效");
            }
        }
    }

    private void validateMigrationReceipts(ArrayNode values) {
        for (ObjectNode value : JsonSupport.objectValues(values, "migrationReceipts")) {
            requireFingerprint(value);
            JsonSupport.requireTimestamp(value, "createdAt");
            if (!value.path("response").isObject()) {
                throw JsonSupport.validation("备份迁移回执响应无效");
            }
        }
    }

    private void validateIdMappings(ArrayNode values) {
        Set<String> keys = new HashSet<>();
        for (ObjectNode value : JsonSupport.objectValues(values, "idMappings")) {
            String key = JsonSupport.requireText(value, "batchId", 80)
                    + "\u0000" + JsonSupport.requireText(value, "entityType", 32)
                    + "\u0000" + JsonSupport.requireText(value, "sourceId", 128);
            JsonSupport.requireText(value, "targetId", 128);
            if (!keys.add(key)) {
                throw JsonSupport.validation("备份 ID 映射重复");
            }
        }
    }

    private String requireFingerprint(ObjectNode value) {
        String fingerprint = JsonSupport.requireText(value, "requestFingerprint", 64);
        if (!fingerprint.matches("^[0-9a-f]{64}$")) {
            throw JsonSupport.validation("备份请求指纹无效");
        }
        return fingerprint;
    }

    private Set<String> lexiconIds(String ownerId) {
        Set<String> ids = new HashSet<>();
        lexiconMapper.listAllByOwner(ownerId).forEach(lexicon -> ids.add(lexicon.getId()));
        return ids;
    }

    private Set<String> lexiconIdsFromBackup(ObjectNode backup) {
        Set<String> ids = new HashSet<>();
        for (ObjectNode lexicon : JsonSupport.objectValues(
                JsonSupport.requireArray((ObjectNode) backup.get("tables"), "lexicons"),
                "lexicons")) {
            ids.add(JsonSupport.requireIdentifier(lexicon, "id"));
        }
        return ids;
    }

    private record ValidatedBackup(String fingerprint, SnapshotCounts counts) {
    }
}
