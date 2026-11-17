package com.fingermemory.server.learning;

import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.common.JsonSupport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class UserDataValidator {
    private static final Set<String> MODES = Set.of(
            "flashcard", "keyboard-recall", "lyrics-scroll", "danmaku-stream", "card-rain");
    private static final Set<String> RATINGS = Set.of("again", "hard", "good", "easy");

    public void validate(ObjectNode root, Set<String> lexiconIds) {
        int schemaVersion = JsonSupport.requireInt(root, "schemaVersion");
        int dataFormatVersion = JsonSupport.requireInt(root, "dataFormatVersion");
        if (!((schemaVersion == 4 && dataFormatVersion == 4)
                || (schemaVersion == 5 && dataFormatVersion == 5))) {
            throw JsonSupport.validation("仅支持本地 v4/v5 学习数据");
        }
        JsonNode tablesNode = root.get("tables");
        if (tablesNode == null || !tablesNode.isObject()) {
            throw JsonSupport.validation("tables must be an object");
        }
        ObjectNode tables = (ObjectNode) tablesNode;
        ArrayNode appMeta = JsonSupport.requireArray(tables, "appMeta");
        ArrayNode words = JsonSupport.requireArray(tables, "words");
        ArrayNode studyItems = JsonSupport.requireArray(tables, "studyItems");
        ArrayNode lexiconItems = JsonSupport.requireArray(tables, "lexiconItems");
        ArrayNode notes = JsonSupport.requireArray(tables, "notes");
        ArrayNode attempts = JsonSupport.requireArray(tables, "practiceAttempts");
        ArrayNode sessions = JsonSupport.requireArray(tables, "practiceSessions");
        ArrayNode states = JsonSupport.requireArray(tables, "reviewStates");
        ArrayNode logs = JsonSupport.requireArray(tables, "reviewLogs");

        JsonSupport.requireUniqueIds(appMeta, "key", "appMeta");
        JsonSupport.requireUniqueIds(words, "id", "words");
        JsonSupport.requireUniqueIds(studyItems, "id", "studyItems");
        JsonSupport.requireUniqueIds(notes, "id", "notes");
        JsonSupport.requireUniqueIds(attempts, "id", "practiceAttempts");
        JsonSupport.requireUniqueIds(sessions, "id", "practiceSessions");
        JsonSupport.requireUniqueIds(states, "studyItemId", "reviewStates");
        JsonSupport.requireUniqueIds(logs, "submissionId", "reviewLogs");

        Map<String, ObjectNode> wordById = byId(words, "id");
        Map<String, ObjectNode> studyById = byId(studyItems, "id");
        Map<String, ObjectNode> sessionById = byId(sessions, "id");
        Set<String> membershipKeys = new HashSet<>();
        for (ObjectNode membership : JsonSupport.objectValues(lexiconItems, "lexiconItems")) {
            String lexiconId = JsonSupport.requireIdentifier(membership, "lexiconId");
            String studyItemId = JsonSupport.requireIdentifier(membership, "studyItemId");
            if (!lexiconIds.contains(lexiconId)) {
                throw JsonSupport.validation("lexicon item references a missing lexicon");
            }
            if (!studyById.containsKey(studyItemId)) {
                throw JsonSupport.validation("lexicon item references a missing study item");
            }
            if (JsonSupport.requireInt(membership, "order") < 0) {
                throw JsonSupport.validation("lexicon item order must not be negative");
            }
            if (!membershipKeys.add(lexiconId + "\u0000" + studyItemId)) {
                throw JsonSupport.validation("lexicon items contain duplicate membership");
            }
        }

        for (ObjectNode study : JsonSupport.objectValues(studyItems, "studyItems")) {
            String wordEntryId = JsonSupport.requireIdentifier(study, "wordEntryId");
            if (!wordById.containsKey(wordEntryId)) {
                throw JsonSupport.validation("study item references a missing word");
            }
            if (!"meaning-to-spelling".equals(study.path("direction").asText())) {
                throw JsonSupport.validation("unsupported study direction");
            }
        }

        Set<String> noteStudyItems = new HashSet<>();
        for (ObjectNode note : JsonSupport.objectValues(notes, "notes")) {
            String studyItemId = JsonSupport.requireIdentifier(note, "studyItemId");
            if (!studyById.containsKey(studyItemId) || !noteStudyItems.add(studyItemId)) {
                throw JsonSupport.validation("note references a missing or duplicate study item");
            }
        }

        for (ObjectNode attempt : JsonSupport.objectValues(attempts, "practiceAttempts")) {
            String studyItemId = JsonSupport.requireIdentifier(attempt, "studyItemId");
            if (!studyById.containsKey(studyItemId)) {
                throw JsonSupport.validation("practice attempt references a missing study item");
            }
            validateAssessment(attempt, "practice attempt", false);
            String sessionId = nullableIdentifier(attempt, "sessionId");
            if (sessionId != null && !sessionById.containsKey(sessionId)) {
                throw JsonSupport.validation("practice attempt references a missing session");
            }
        }

        for (ObjectNode session : JsonSupport.objectValues(sessions, "practiceSessions")) {
            validateSession(session, studyById, wordById);
        }

        Map<String, List<ObjectNode>> logsByStudyItem = new HashMap<>();
        for (ObjectNode log : JsonSupport.objectValues(logs, "reviewLogs")) {
            validateLog(log, studyById);
            logsByStudyItem
                    .computeIfAbsent(JsonSupport.requireIdentifier(log, "studyItemId"),
                            ignored -> new ArrayList<>())
                    .add(log);
        }

        Set<String> stateIds = new HashSet<>();
        for (ObjectNode state : JsonSupport.objectValues(states, "reviewStates")) {
            String studyItemId = JsonSupport.requireIdentifier(state, "studyItemId");
            if (!studyById.containsKey(studyItemId) || !stateIds.add(studyItemId)) {
                throw JsonSupport.validation("review state references a missing or duplicate study item");
            }
            validateCard(state, "review state");
            validateSchedulerConfig(state);
            long expectedVersion = logsByStudyItem.getOrDefault(studyItemId, List.of()).size();
            long version = state.has("version") ? JsonSupport.requireLong(state, "version") : expectedVersion;
            if (version != expectedVersion) {
                throw JsonSupport.validation("review state version does not match its formal log count");
            }
        }

        for (Map.Entry<String, List<ObjectNode>> entry : logsByStudyItem.entrySet()) {
            List<ObjectNode> itemLogs = entry.getValue().stream()
                    .sorted(Comparator
                            .comparingLong((ObjectNode log) -> JsonSupport.requireLong(log, "reviewedAt"))
                            .thenComparing(log -> JsonSupport.requireIdentifier(log, "submissionId")))
                    .toList();
            for (int index = 1; index < itemLogs.size(); index += 1) {
                ObjectNode previous = itemLogs.get(index - 1);
                ObjectNode current = itemLogs.get(index);
                JsonNode currentBefore = current.get("beforeCard");
                if (currentBefore == null || currentBefore.isNull()
                        || !JsonSupport.canonicalize(currentBefore)
                        .equals(JsonSupport.canonicalize(previous.get("afterCard")))) {
                    throw JsonSupport.validation("formal review log chain is discontinuous");
                }
            }
        }

        for (ObjectNode state : JsonSupport.objectValues(states, "reviewStates")) {
            String studyItemId = JsonSupport.requireIdentifier(state, "studyItemId");
            List<ObjectNode> itemLogs = logsByStudyItem.get(studyItemId);
            if (itemLogs == null || itemLogs.isEmpty()) {
                throw JsonSupport.validation("review state has no formal review log");
            }
            ObjectNode latest = itemLogs.stream()
                    .max(Comparator
                            .comparingLong((ObjectNode log) -> JsonSupport.requireLong(log, "reviewedAt"))
                            .thenComparing(log -> JsonSupport.requireIdentifier(log, "submissionId")))
                    .orElseThrow();
            ObjectNode card = state.deepCopy();
            card.remove(List.of(
                    "studyItemId",
                    "schedulerConfig",
                    "schedulerPackageVersion",
                    "schedulerModelVersion",
                    "updatedAt",
                    "version",
                    "configHash"));
            if (!JsonSupport.canonicalize(card)
                    .equals(JsonSupport.canonicalize(latest.get("afterCard")))) {
                throw JsonSupport.validation("latest review log does not match current review state");
            }
        }
    }

    private void validateSession(ObjectNode session, Map<String, ObjectNode> studyById,
                                 Map<String, ObjectNode> wordById) {
        String modeId = JsonSupport.requireText(session, "modeId", 64);
        if (!MODES.contains(modeId)) {
            throw JsonSupport.validation("session mode is unsupported");
        }
        String status = JsonSupport.requireText(session, "status", 16);
        if (!Set.of("active", "paused", "completed").contains(status)) {
            throw JsonSupport.validation("session status is unsupported");
        }
        if (JsonSupport.requireLong(session, "version") < 0) {
            throw JsonSupport.validation("session version must not be negative");
        }
        ArrayNode queue = JsonSupport.requireArray(session, "queue");
        if (queue.isEmpty()) {
            throw JsonSupport.validation("session queue must not be empty");
        }
        Set<String> queueStudyItemIds = new HashSet<>();
        for (ObjectNode item : JsonSupport.objectValues(queue, "session queue")) {
            String studyItemId = JsonSupport.requireIdentifier(item, "studyItemId");
            String wordEntryId = JsonSupport.requireIdentifier(item, "wordEntryId");
            ObjectNode study = studyById.get(studyItemId);
            if (study == null || !wordEntryId.equals(study.path("wordEntryId").asText())
                    || !wordById.containsKey(wordEntryId)) {
                throw JsonSupport.validation("session queue references a missing word or study item");
            }
            queueStudyItemIds.add(studyItemId);
        }
        String currentId = nullableIdentifier(session, "currentStudyItemId");
        if (currentId != null && !queueStudyItemIds.contains(currentId)) {
            throw JsonSupport.validation("session current item is outside its queue");
        }
        JsonNode interactions = session.get("interactions");
        if (interactions == null || !interactions.isObject()) {
            throw JsonSupport.validation("session interactions must be an object");
        }
        interactions.properties().forEach(entry -> {
            if (!queueStudyItemIds.contains(entry.getKey())) {
                throw JsonSupport.validation("session interaction references an item outside its queue");
            }
        });
        JsonNode pending = session.get("pendingAnswer");
        if (pending != null && !pending.isNull()) {
            if (!pending.isObject()) {
                throw JsonSupport.validation("session pending answer must be an object");
            }
            ObjectNode pendingAnswer = (ObjectNode) pending;
            String studyItemId = JsonSupport.requireIdentifier(pendingAnswer, "studyItemId");
            if (!queueStudyItemIds.contains(studyItemId)) {
                throw JsonSupport.validation("pending answer references an item outside the queue");
            }
            validateAssessment(pendingAnswer, "pending answer", false);
        }
    }

    private void validateLog(ObjectNode log, Map<String, ObjectNode> studyById) {
        String studyItemId = JsonSupport.requireIdentifier(log, "studyItemId");
        if (!studyById.containsKey(studyItemId)) {
            throw JsonSupport.validation("review log references a missing study item");
        }
        if (!RATINGS.contains(JsonSupport.requireText(log, "rating", 16))) {
            throw JsonSupport.validation("review log rating is unsupported");
        }
        validateAssessment(log, "review log", true);
        JsonSupport.requireTimestamp(log, "reviewedAt");
        JsonNode beforeCard = log.get("beforeCard");
        if (beforeCard != null && beforeCard.isObject()) {
            validateCard((ObjectNode) beforeCard, "beforeCard");
        } else if (beforeCard == null || !beforeCard.isNull()) {
            throw JsonSupport.validation("beforeCard must be an object or null");
        }
        JsonNode afterCard = log.get("afterCard");
        if (afterCard == null || !afterCard.isObject()) {
            throw JsonSupport.validation("afterCard must be an object");
        }
        validateCard((ObjectNode) afterCard, "afterCard");
        validateSchedulerConfig(log);
    }

    private void validateCard(ObjectNode card, String label) {
        JsonSupport.requireIdentifier(card, "cardId");
        for (String field : List.of(
                "stability", "stabilityFast", "difficulty", "scheduledDays")) {
            JsonNode value = card.get(field);
            if (value == null || !value.isNumber() || !Double.isFinite(value.doubleValue())) {
                throw JsonSupport.validation(label + "." + field + " must be finite");
            }
        }
        JsonSupport.requireTimestamp(card, "dueAt");
        JsonNode lastReviewAt = card.get("lastReviewAt");
        if (lastReviewAt != null && !lastReviewAt.isNull()
                && (!lastReviewAt.isIntegralNumber() || lastReviewAt.longValue() <= 0)) {
            throw JsonSupport.validation(label + ".lastReviewAt is invalid");
        }
        JsonSupport.requireLong(card, "reps");
        JsonSupport.requireLong(card, "lapses");
        JsonSupport.requireLong(card, "learningStep");
    }

    private void validateSchedulerConfig(ObjectNode parent) {
        String packageVersion = JsonSupport.requireText(parent, "schedulerPackageVersion", 32);
        String modelVersion = JsonSupport.requireText(parent, "schedulerModelVersion", 32);
        if (!"6.0.0".equals(packageVersion) || !"fsrs-7".equals(modelVersion)) {
            throw JsonSupport.validation("scheduler package or model is unsupported");
        }
        JsonNode config = parent.get("schedulerConfig");
        if (config == null || !config.isObject()) {
            throw JsonSupport.validation("schedulerConfig must be an object");
        }
        JsonNode weights = config.get("weights");
        if (weights == null || !weights.isArray() || weights.size() != 34) {
            throw JsonSupport.validation("FSRS-7 scheduler weights must contain 34 values");
        }
    }

    private void validateAssessment(ObjectNode value, String label, boolean formalLog) {
        if (!value.has("assessmentPolicyVersion")) {
            return;
        }
        int version = JsonSupport.requireInt(value, "assessmentPolicyVersion");
        if (version != 1 && version != 2) {
            throw JsonSupport.validation(label + " assessmentPolicyVersion is unsupported");
        }
        if (version != 2) {
            return;
        }
        String type = JsonSupport.requireText(value, "assessmentType", 32);
        if (!Set.of("typed-recall", "flashcard-self-report", "guided-practice")
                .contains(type)) {
            throw JsonSupport.validation(label + " assessmentType is unsupported");
        }
        if (formalLog && "guided-practice".equals(type)) {
            throw JsonSupport.validation("guided practice cannot create a formal review log");
        }
        String stage = JsonSupport.requireText(value, "assessmentStage", 32);
        if (!Set.of("prompt", "attempt", "reveal-for-check", "rate").contains(stage)) {
            throw JsonSupport.validation(label + " assessmentStage is unsupported");
        }
        JsonSupport.requireBoolean(value, "priorExposure");
        JsonSupport.requireBoolean(value, "explicitNotRemembered");
    }

    private String nullableIdentifier(ObjectNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw JsonSupport.validation(field + " must be a string or null");
        }
        return value.textValue();
    }

    private Map<String, ObjectNode> byId(ArrayNode array, String field) {
        Map<String, ObjectNode> values = new HashMap<>();
        for (ObjectNode value : JsonSupport.objectValues(array, field)) {
            values.put(JsonSupport.requireIdentifier(value, field), value);
        }
        return values;
    }
}
