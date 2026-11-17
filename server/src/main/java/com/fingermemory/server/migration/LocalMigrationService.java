package com.fingermemory.server.migration;

import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.common.JsonSupport;
import com.fingermemory.server.learning.RewardEventMapper;
import com.fingermemory.server.learning.ReviewReceiptMapper;
import com.fingermemory.server.learning.LockedUserData;
import com.fingermemory.server.learning.UserDataRow;
import com.fingermemory.server.learning.UserDataStore;
import com.fingermemory.server.learning.UserDataValidator;
import com.fingermemory.server.lexicon.Lexicon;
import com.fingermemory.server.lexicon.LexiconMapper;
import com.fingermemory.server.migration.LocalMigrationTransformer.LexiconRecord;
import com.fingermemory.server.migration.LocalMigrationTransformer.TransformResult;
import com.fingermemory.server.migration.MigrationDtos.MigrationPreview;
import com.fingermemory.server.migration.MigrationDtos.MigrationResult;
import com.fingermemory.server.migration.MigrationDtos.SnapshotCounts;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
public class LocalMigrationService {
    private static final Pattern BATCH_ID = Pattern.compile("^[A-Za-z0-9_.-]{3,80}$");
    private static final int MAX_TABLE_ROWS = 50_000;
    private static final Set<String> DATA_TABLES = Set.of(
            "appMeta", "words", "studyItems", "lexiconItems", "notes",
            "practiceAttempts", "practiceSessions", "reviewStates", "reviewLogs");

    private final UserDataStore userDataStore;
    private final UserDataValidator validator;
    private final LocalMigrationTransformer transformer;
    private final MigrationReceiptMapper receiptMapper;
    private final ImportIdMapMapper importIdMapMapper;
    private final ReviewReceiptMapper reviewReceiptMapper;
    private final RewardEventMapper rewardEventMapper;
    private final LexiconMapper lexiconMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LocalMigrationService(
            UserDataStore userDataStore,
            UserDataValidator validator,
            LocalMigrationTransformer transformer,
            MigrationReceiptMapper receiptMapper,
            ImportIdMapMapper importIdMapMapper,
            ReviewReceiptMapper reviewReceiptMapper,
            RewardEventMapper rewardEventMapper,
            LexiconMapper lexiconMapper,
            ObjectMapper objectMapper,
            Clock clock) {
        this.userDataStore = userDataStore;
        this.validator = validator;
        this.transformer = transformer;
        this.receiptMapper = receiptMapper;
        this.importIdMapMapper = importIdMapMapper;
        this.reviewReceiptMapper = reviewReceiptMapper;
        this.rewardEventMapper = rewardEventMapper;
        this.lexiconMapper = lexiconMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public MigrationPreview preview(String ownerId, JsonNode input) {
        ObjectNode snapshot = requireSnapshot(input);
        ValidatedSnapshot validated = validateSnapshot(snapshot);
        UserDataRow row = userDataStore.read(ownerId);
        return new MigrationPreview(
                validated.fingerprint(),
                row.getDataVersion(),
                validated.counts(),
                "6.0.0",
                "fsrs-7",
                validated.weights());
    }

    @Transactional
    public ObjectNode importBatch(String ownerId, String batchId, JsonNode input) {
        validateBatchId(batchId);
        ObjectNode snapshot = requireSnapshot(input);
        String fingerprint = JsonSupport.fingerprint(objectMapper, snapshot);
        ObjectNode existingReceipt = findReceipt(ownerId, batchId, fingerprint);
        if (existingReceipt != null) {
            return existingReceipt;
        }
        ValidatedSnapshot validated = validateSnapshot(snapshot);
        if (!fingerprint.equals(validated.fingerprint())) {
            throw new IllegalStateException("Snapshot fingerprint changed during validation");
        }

        LockedUserData locked = userDataStore.lock(ownerId);
        ObjectNode currentReceipt = findReceipt(ownerId, batchId, fingerprint);
        if (currentReceipt != null) {
            return currentReceipt;
        }
        Set<String> currentLexiconIds = lexiconIds(ownerId);
        validator.validate(locked.document(), currentLexiconIds);

        TransformResult transformed = transformer.transform(ownerId, batchId, snapshot);
        List<String> allLexiconIds = new ArrayList<>(currentLexiconIds);
        try {
            for (LexiconRecord record : transformed.lexicons()) {
                Lexicon lexicon = toLexicon(ownerId, batchId, record);
                lexiconMapper.insert(lexicon);
                allLexiconIds.add(lexicon.getId());
            }
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "MIGRATION_NAME_CONFLICT",
                    "导入批次包含重复词库名称或 ID");
        }

        merge(locked.document(), transformed);
        validator.validate(locked.document(), Set.copyOf(allLexiconIds));
        userDataStore.save(locked, locked.document());
        long dataVersion = locked.row().getDataVersion();
        long now = clock.instant().toEpochMilli();

        insertImportedReviewArtifacts(
                ownerId, batchId, fingerprint, transformed.reviewLogs(), locked.document(), dataVersion);
        ObjectNode response = objectMapper.createObjectNode();
        response.put("batchId", batchId);
        response.put("snapshotFingerprint", fingerprint);
        response.put("dataVersion", dataVersion);
        response.set("importedCounts", objectMapper.valueToTree(validated.counts()));
        response.put("idMappings", importIdMapMapper.countByBatch(ownerId, batchId));
        receiptMapper.insert(
                ownerId, batchId, fingerprint,
                objectMapper.writeValueAsString(response), now);
        return response;
    }

    private void insertImportedReviewArtifacts(
            String ownerId,
            String batchId,
            String migrationFingerprint,
            List<ObjectNode> logs,
            ObjectNode mergedDocument,
            long dataVersion) {
        Map<String, Integer> versions = new HashMap<>();
        List<ObjectNode> sorted = new ArrayList<>(logs);
        sorted.sort(Comparator
                .comparing((ObjectNode log) -> JsonSupport.requireIdentifier(log, "studyItemId"))
                .thenComparingLong(log -> JsonSupport.requireLong(log, "reviewedAt"))
                .thenComparing(log -> JsonSupport.requireIdentifier(log, "submissionId")));
        for (ObjectNode log : sorted) {
            String studyItemId = JsonSupport.requireIdentifier(log, "studyItemId");
            int version = versions.merge(studyItemId, 1, Integer::sum);
            ObjectNode state = ((ObjectNode) log.get("afterCard")).deepCopy();
            state.put("studyItemId", studyItemId);
            state.set("schedulerConfig", log.get("schedulerConfig").deepCopy());
            state.put("schedulerPackageVersion", log.path("schedulerPackageVersion").asText());
            state.put("schedulerModelVersion", log.path("schedulerModelVersion").asText());
            state.put("configHash",
                    JsonSupport.fingerprint(objectMapper, log.get("schedulerConfig")));
            state.put("updatedAt", JsonSupport.requireLong(log, "reviewedAt"));
            state.put("version", version);

            ObjectNode response = objectMapper.createObjectNode();
            response.put("outcome", "migrated");
            response.set("state", state);
            response.set("log", log.deepCopy());
            response.putNull("session");
            response.put("dataVersion", dataVersion);
            response.put("rewardEligible", false);
            String receiptFingerprint = JsonSupport.fingerprint(objectMapper,
                    objectMapper.getNodeFactory().textNode(
                            "migration\u0000" + batchId + "\u0000" + migrationFingerprint
                                    + "\u0000" + log.path("submissionId").asText()));
            reviewReceiptMapper.insert(
                    ownerId,
                    JsonSupport.requireIdentifier(log, "submissionId"),
                    receiptFingerprint,
                    objectMapper.writeValueAsString(response),
                    JsonSupport.requireLong(log, "reviewedAt"));

            ObjectNode rewardPayload = objectMapper.createObjectNode();
            rewardPayload.put("batchId", batchId);
            rewardPayload.put("submissionId", log.path("submissionId").asText());
            rewardPayload.put("source", "migration");
            rewardEventMapper.insert(
                    ownerId,
                    "review",
                    JsonSupport.requireIdentifier(log, "submissionId"),
                    false,
                    JsonSupport.requireLong(log, "reviewedAt"),
                    objectMapper.writeValueAsString(rewardPayload));
        }
    }

    private ObjectNode findReceipt(String ownerId, String batchId, String fingerprint) {
        MigrationReceiptRow receipt = receiptMapper.find(ownerId, batchId);
        if (receipt == null) {
            return null;
        }
        if (!fingerprint.equals(receipt.getRequestFingerprint())) {
            throw new ApiException(HttpStatus.CONFLICT, "MIGRATION_BATCH_CONFLICT",
                    "迁移批次已用于不同快照");
        }
        return JsonSupport.copyObject(
                objectMapper.readTree(receipt.getResponseJson()), "migration receipt");
    }

    private ValidatedSnapshot validateSnapshot(ObjectNode snapshot) {
        JsonNode tablesNode = snapshot.get("tables");
        if (tablesNode == null || !tablesNode.isObject()) {
            throw JsonSupport.validation("本地快照缺少 tables");
        }
        ObjectNode tables = (ObjectNode) tablesNode;
        ArrayNode lexicons = JsonSupport.requireArray(tables, "lexicons");
        Set<String> lexiconIds = new HashSet<>();
        for (ObjectNode lexicon : JsonSupport.objectValues(lexicons, "lexicons")) {
            lexiconIds.add(JsonSupport.requireIdentifier(lexicon, "id"));
            JsonSupport.requireText(lexicon, "name", 120);
            JsonSupport.optionalText(lexicon, "description", 2000);
            JsonSupport.requireTimestamp(lexicon, "createdAt");
            JsonSupport.requireTimestamp(lexicon, "updatedAt");
            JsonNode archivedAt = lexicon.get("archivedAt");
            if (archivedAt != null && !archivedAt.isNull()
                    && (!archivedAt.isIntegralNumber() || archivedAt.longValue() <= 0)) {
                throw JsonSupport.validation("本地词库 archivedAt 无效");
            }
        }
        if (lexiconIds.size() != lexicons.size()) {
            throw JsonSupport.validation("本地词库包含重复 ID");
        }
        validator.validate(snapshot, lexiconIds);
        for (String table : DATA_TABLES) {
            ArrayNode array = JsonSupport.requireArray(tables, table);
            if (array.size() > MAX_TABLE_ROWS) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "MIGRATION_TOO_LARGE", "本地数据超过单次迁移上限");
            }
        }
        if (lexicons.size() > 2_000) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "MIGRATION_TOO_LARGE", "本地词库数量超过迁移上限");
        }
        String dataFormat = null;
        for (ObjectNode record : JsonSupport.objectValues(
                JsonSupport.requireArray(tables, "appMeta"), "appMeta")) {
            if ("data.formatVersion".equals(record.path("key").asText())) {
                dataFormat = record.path("value").asText();
            }
        }
        if (!"4".equals(dataFormat) && !"5".equals(dataFormat)) {
            throw JsonSupport.validation("本地快照 data.formatVersion 必须为 4 或 5");
        }
        int weights = validateScheduler(snapshot);
        SnapshotCounts counts = counts(tables);
        return new ValidatedSnapshot(
                JsonSupport.fingerprint(objectMapper, snapshot), counts, weights);
    }

    private ObjectNode requireSnapshot(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw JsonSupport.validation("请求必须包含本地 v4/v5 快照");
        }
        JsonNode snapshot = input.get("snapshot");
        if (snapshot == null || !snapshot.isObject()) {
            throw JsonSupport.validation("snapshot 必须是对象");
        }
        return ((ObjectNode) snapshot).deepCopy();
    }

    private int validateScheduler(ObjectNode snapshot) {
        ObjectNode tables = (ObjectNode) snapshot.get("tables");
        int weights = 34;
        for (ObjectNode state : JsonSupport.objectValues(
                JsonSupport.requireArray(tables, "reviewStates"), "reviewStates")) {
            weights = validateSchedulerObject(state);
        }
        for (ObjectNode log : JsonSupport.objectValues(
                JsonSupport.requireArray(tables, "reviewLogs"), "reviewLogs")) {
            weights = validateSchedulerObject(log);
        }
        return weights;
    }

    private int validateSchedulerObject(ObjectNode value) {
        JsonNode weightsNode = value.path("schedulerConfig").path("weights");
        if (!"6.0.0".equals(value.path("schedulerPackageVersion").asText())
                || !"fsrs-7".equals(value.path("schedulerModelVersion").asText())
                || !weightsNode.isArray() || weightsNode.size() != 34) {
            throw JsonSupport.validation("本地数据不是兼容的 FSRS-7/34 权重格式");
        }
        return weightsNode.size();
    }

    private SnapshotCounts counts(ObjectNode tables) {
        return new SnapshotCounts(
                JsonSupport.requireArray(tables, "lexicons").size(),
                JsonSupport.requireArray(tables, "words").size(),
                JsonSupport.requireArray(tables, "studyItems").size(),
                JsonSupport.requireArray(tables, "lexiconItems").size(),
                JsonSupport.requireArray(tables, "notes").size(),
                JsonSupport.requireArray(tables, "practiceAttempts").size(),
                JsonSupport.requireArray(tables, "practiceSessions").size(),
                JsonSupport.requireArray(tables, "reviewStates").size(),
                JsonSupport.requireArray(tables, "reviewLogs").size());
    }

    private Lexicon toLexicon(String ownerId, String batchId, LexiconRecord record) {
        ObjectNode source = record.source();
        Lexicon lexicon = new Lexicon();
        lexicon.setId(record.targetId());
        lexicon.setOwnerId(ownerId);
        lexicon.setImportBatchId(batchId);
        lexicon.setName(JsonSupport.requireText(source, "name", 120).trim());
        lexicon.setNormalizedName(lexicon.getName().toLowerCase(Locale.ROOT));
        lexicon.setDescription(JsonSupport.optionalText(source, "description", 2000).trim());
        lexicon.setSource("migration");
        JsonNode archivedAt = source.get("archivedAt");
        lexicon.setArchivedAtMs(archivedAt == null || archivedAt.isNull()
                ? null : archivedAt.longValue());
        lexicon.setCreatedAtMs(JsonSupport.requireTimestamp(source, "createdAt"));
        lexicon.setUpdatedAtMs(JsonSupport.requireTimestamp(source, "updatedAt"));
        lexicon.setVersion(0);
        return lexicon;
    }

    private void merge(ObjectNode target, TransformResult transformed) {
        ObjectNode targetTables = (ObjectNode) target.get("tables");
        ObjectNode sourceTables = (ObjectNode) transformed.document().get("tables");
        Set<String> appMetaKeys = new HashSet<>();
        for (ObjectNode record : JsonSupport.objectValues(
                JsonSupport.requireArray(targetTables, "appMeta"), "appMeta")) {
            appMetaKeys.add(record.path("key").asText());
        }
        for (ObjectNode record : transformed.appMeta()) {
            if (appMetaKeys.add(record.path("key").asText())) {
                targetTables.withArray("appMeta").add(record);
            }
        }
        for (String table : List.of(
                "words", "studyItems", "lexiconItems", "notes", "practiceAttempts",
                "practiceSessions", "reviewStates", "reviewLogs")) {
            ArrayNode targetArray = targetTables.withArray(table);
            for (JsonNode value : JsonSupport.requireArray(sourceTables, table)) {
                targetArray.add(value.deepCopy());
            }
        }
    }

    private Set<String> lexiconIds(String ownerId) {
        Set<String> ids = new HashSet<>();
        lexiconMapper.listAllByOwner(ownerId).forEach(lexicon -> ids.add(lexicon.getId()));
        return ids;
    }

    private void validateBatchId(String batchId) {
        if (batchId == null || !BATCH_ID.matcher(batchId).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "迁移批次 ID 必须为 3-80 位字母、数字、点、下划线或连字符");
        }
    }

    private record ValidatedSnapshot(String fingerprint, SnapshotCounts counts, int weights) {
    }
}
