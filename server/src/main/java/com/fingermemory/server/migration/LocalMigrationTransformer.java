package com.fingermemory.server.migration;

import com.fingermemory.server.common.JsonSupport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class LocalMigrationTransformer {
    private final ImportIdMapMapper idMapMapper;
    private final ObjectMapper objectMapper;

    public LocalMigrationTransformer(ImportIdMapMapper idMapMapper, ObjectMapper objectMapper) {
        this.idMapMapper = idMapMapper;
        this.objectMapper = objectMapper;
    }

    public TransformResult transform(String ownerId, String batchId, ObjectNode source) {
        Context context = new Context(ownerId, batchId);
        ObjectNode target = JsonSupport.emptyUserData(objectMapper);
        ObjectNode sourceTables = (ObjectNode) source.get("tables");
        ObjectNode targetTables = (ObjectNode) target.get("tables");

        for (ObjectNode record : JsonSupport.objectValues(
                JsonSupport.requireArray(sourceTables, "appMeta"), "appMeta")) {
            context.appMeta.add(record.deepCopy());
        }
        for (ObjectNode sourceWord : JsonSupport.objectValues(
                JsonSupport.requireArray(sourceTables, "words"), "words")) {
            ObjectNode word = sourceWord.deepCopy();
            String originalId = JsonSupport.requireIdentifier(word, "id");
            word.put("id", context.map("word", originalId));
            targetTables.withArray("words").add(word);
        }
        for (ObjectNode sourceStudyItem : JsonSupport.objectValues(
                JsonSupport.requireArray(sourceTables, "studyItems"), "studyItems")) {
            ObjectNode studyItem = sourceStudyItem.deepCopy();
            String originalId = JsonSupport.requireIdentifier(studyItem, "id");
            studyItem.put("id", context.map("studyItem", originalId));
            studyItem.put("wordEntryId",
                    context.map("word", JsonSupport.requireIdentifier(studyItem, "wordEntryId")));
            targetTables.withArray("studyItems").add(studyItem);
        }
        for (ObjectNode sourceMembership : JsonSupport.objectValues(
                JsonSupport.requireArray(sourceTables, "lexiconItems"), "lexiconItems")) {
            ObjectNode membership = sourceMembership.deepCopy();
            membership.put("lexiconId", context.map(
                    "lexicon", JsonSupport.requireIdentifier(membership, "lexiconId")));
            membership.put("studyItemId", context.map(
                    "studyItem", JsonSupport.requireIdentifier(membership, "studyItemId")));
            targetTables.withArray("lexiconItems").add(membership);
        }
        for (ObjectNode sourceNote : JsonSupport.objectValues(
                JsonSupport.requireArray(sourceTables, "notes"), "notes")) {
            ObjectNode note = sourceNote.deepCopy();
            note.put("id", context.map("note", JsonSupport.requireIdentifier(note, "id")));
            note.put("studyItemId", context.map(
                    "studyItem", JsonSupport.requireIdentifier(note, "studyItemId")));
            targetTables.withArray("notes").add(note);
        }
        for (ObjectNode sourceAttempt : JsonSupport.objectValues(
                JsonSupport.requireArray(sourceTables, "practiceAttempts"),
                "practiceAttempts")) {
            ObjectNode attempt = sourceAttempt.deepCopy();
            attempt.put("id", context.map(
                    "practiceAttempt", JsonSupport.requireIdentifier(attempt, "id")));
            attempt.put("studyItemId", context.map(
                    "studyItem", JsonSupport.requireIdentifier(attempt, "studyItemId")));
            String sessionId = nullableId(attempt, "sessionId");
            if (sessionId != null) {
                attempt.put("sessionId", context.map("session", sessionId));
            }
            targetTables.withArray("practiceAttempts").add(attempt);
        }
        for (ObjectNode sourceSession : JsonSupport.objectValues(
                JsonSupport.requireArray(sourceTables, "practiceSessions"),
                "practiceSessions")) {
            targetTables.withArray("practiceSessions").add(
                    transformSession(sourceSession, context));
        }

        List<ObjectNode> sourceLogs = JsonSupport.objectValues(
                JsonSupport.requireArray(sourceTables, "reviewLogs"), "reviewLogs");
        List<ObjectNode> mappedLogs = new ArrayList<>();
        for (ObjectNode sourceLog : sourceLogs) {
            mappedLogs.add(transformLog(sourceLog, context));
        }
        mappedLogs.sort(Comparator
                .comparing((ObjectNode log) -> JsonSupport.requireIdentifier(log, "studyItemId"))
                .thenComparingLong(log -> JsonSupport.requireLong(log, "reviewedAt"))
                .thenComparing(log -> JsonSupport.requireIdentifier(log, "submissionId")));
        mappedLogs.forEach(log -> targetTables.withArray("reviewLogs").add(log));

        Map<String, Integer> logCounts = new HashMap<>();
        for (ObjectNode log : mappedLogs) {
            logCounts.merge(JsonSupport.requireIdentifier(log, "studyItemId"), 1, Integer::sum);
        }
        for (ObjectNode sourceState : JsonSupport.objectValues(
                JsonSupport.requireArray(sourceTables, "reviewStates"), "reviewStates")) {
            ObjectNode state = transformState(sourceState, context);
            String studyItemId = JsonSupport.requireIdentifier(state, "studyItemId");
            state.put("version", logCounts.getOrDefault(studyItemId, 0));
            targetTables.withArray("reviewStates").add(state);
        }

        List<LexiconRecord> lexicons = new ArrayList<>();
        for (ObjectNode sourceLexicon : JsonSupport.objectValues(
                JsonSupport.requireArray(sourceTables, "lexicons"), "lexicons")) {
            lexicons.add(new LexiconRecord(
                    context.map("lexicon", JsonSupport.requireIdentifier(sourceLexicon, "id")),
                    sourceLexicon.deepCopy()));
        }
        return new TransformResult(target, List.copyOf(context.appMeta), lexicons,
                List.copyOf(mappedLogs), context.mappingCount());
    }

    private ObjectNode transformSession(ObjectNode source, Context context) {
        ObjectNode session = source.deepCopy();
        session.put("id", context.map("session", JsonSupport.requireIdentifier(session, "id")));
        ArrayNode queue = JsonSupport.requireArray(session, "queue");
        for (ObjectNode item : JsonSupport.objectValues(queue, "session queue")) {
            item.put("studyItemId", context.map(
                    "studyItem", JsonSupport.requireIdentifier(item, "studyItemId")));
            item.put("wordEntryId", context.map(
                    "word", JsonSupport.requireIdentifier(item, "wordEntryId")));
        }
        String currentId = nullableId(session, "currentStudyItemId");
        if (currentId != null) {
            session.put("currentStudyItemId", context.map("studyItem", currentId));
        }
        JsonNode interactionsNode = session.get("interactions");
        if (interactionsNode != null && interactionsNode.isObject()) {
            ObjectNode mappedInteractions = objectMapper.createObjectNode();
            ((ObjectNode) interactionsNode).properties().forEach(entry ->
                    mappedInteractions.set(context.map("studyItem", entry.getKey()),
                            entry.getValue().deepCopy()));
            session.set("interactions", mappedInteractions);
        }
        JsonNode pendingNode = session.get("pendingAnswer");
        if (pendingNode != null && pendingNode.isObject()) {
            ObjectNode pending = ((ObjectNode) pendingNode).deepCopy();
            pending.put("studyItemId", context.map(
                    "studyItem", JsonSupport.requireIdentifier(pending, "studyItemId")));
            pending.put("practiceAttemptId", context.map(
                    "practiceAttempt", JsonSupport.requireIdentifier(pending, "practiceAttemptId")));
            pending.put("reviewSubmissionId", context.map(
                    "reviewLog", JsonSupport.requireIdentifier(pending, "reviewSubmissionId")));
            session.set("pendingAnswer", pending);
        }
        if (!session.has("version")) {
            session.put("version", 0);
        }
        return session;
    }

    private ObjectNode transformLog(ObjectNode source, Context context) {
        ObjectNode log = source.deepCopy();
        String studyItemId = JsonSupport.requireIdentifier(log, "studyItemId");
        String mappedStudyItemId = context.map("studyItem", studyItemId);
        log.put("submissionId", context.map(
                "reviewLog", JsonSupport.requireIdentifier(log, "submissionId")));
        log.put("studyItemId", mappedStudyItemId);
        log.put("wordEntryId", context.map(
                "word", JsonSupport.requireIdentifier(log, "wordEntryId")));
        String sessionId = nullableId(log, "sessionId");
        if (sessionId != null) {
            log.put("sessionId", context.map("session", sessionId));
        }
        transformCard(log.get("beforeCard"), mappedStudyItemId);
        transformCard(log.get("afterCard"), mappedStudyItemId);
        JsonNode revlog = log.get("srsRevlog");
        if (revlog != null && revlog.isObject()) {
            ((ObjectNode) revlog).put("cardId", mappedStudyItemId);
        }
        return log;
    }

    private ObjectNode transformState(ObjectNode source, Context context) {
        ObjectNode state = source.deepCopy();
        String mappedStudyItemId = context.map(
                "studyItem", JsonSupport.requireIdentifier(state, "studyItemId"));
        state.put("studyItemId", mappedStudyItemId);
        state.put("cardId", mappedStudyItemId);
        JsonNode config = state.get("schedulerConfig");
        if (config != null && config.isObject()) {
            if (!state.has("configHash")) {
                state.put("configHash", JsonSupport.fingerprint(objectMapper, config));
            }
        }
        return state;
    }

    private void transformCard(JsonNode cardNode, String mappedStudyItemId) {
        if (cardNode != null && cardNode.isObject()) {
            ((ObjectNode) cardNode).put("cardId", mappedStudyItemId);
        }
    }

    private String nullableId(ObjectNode value, String field) {
        JsonNode node = value.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw JsonSupport.validation(field + " must be a string or null");
        }
        return node.textValue();
    }

    public record TransformResult(
            ObjectNode document,
            List<ObjectNode> appMeta,
            List<LexiconRecord> lexicons,
            List<ObjectNode> reviewLogs,
            int mappingCount) {
    }

    public record LexiconRecord(String targetId, ObjectNode source) {
    }

    private final class Context {
        private final String ownerId;
        private final String batchId;
        private final Map<String, String> values = new LinkedHashMap<>();
        private final List<ObjectNode> appMeta = new ArrayList<>();

        private Context(String ownerId, String batchId) {
            this.ownerId = ownerId;
            this.batchId = batchId;
        }

        private String map(String entityType, String sourceId) {
            String key = entityType + "\u0000" + sourceId;
            String existing = values.get(key);
            if (existing != null) {
                return existing;
            }
            String targetId = "m" + sha256(ownerId + "\u0000" + batchId + "\u0000"
                    + entityType + "\u0000" + sourceId).substring(0, 32);
            idMapMapper.insert(ownerId, batchId, entityType, sourceId, targetId);
            values.put(key, targetId);
            return targetId;
        }

        private int mappingCount() {
            return values.size();
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
