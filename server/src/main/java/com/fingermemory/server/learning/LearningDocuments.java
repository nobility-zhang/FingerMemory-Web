package com.fingermemory.server.learning;

import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.common.JsonSupport;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class LearningDocuments {
    public record QueueSettings(int newLimit, int reviewLimit, boolean paused) {
    }

    private final ObjectMapper objectMapper;

    public LearningDocuments(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode tables(ObjectNode document) {
        JsonNode value = document.get("tables");
        if (value == null || !value.isObject()) {
            throw JsonSupport.validation("user data tables are missing");
        }
        return (ObjectNode) value;
    }

    public ArrayNode array(ObjectNode document, String table) {
        return JsonSupport.requireArray(tables(document), table);
    }

    public ObjectNode findWord(ObjectNode document, String id) {
        return JsonSupport.findById(array(document, "words"), id);
    }

    public ObjectNode findStudyItem(ObjectNode document, String id) {
        return JsonSupport.findById(array(document, "studyItems"), id);
    }

    public ObjectNode findSession(ObjectNode document, String id) {
        return JsonSupport.findById(array(document, "practiceSessions"), id);
    }

    public ObjectNode findReviewState(ObjectNode document, String studyItemId) {
        return JsonSupport.findById(array(document, "reviewStates"), "studyItemId", studyItemId);
    }

    public ObjectNode requireStudyItem(ObjectNode document, String studyItemId) {
        ObjectNode studyItem = findStudyItem(document, studyItemId);
        if (studyItem == null) {
            throw JsonSupport.validation("学习目标不存在或不属于当前用户");
        }
        return studyItem;
    }

    public ObjectNode requireWordForStudy(ObjectNode document, ObjectNode studyItem) {
        String wordEntryId = JsonSupport.requireIdentifier(studyItem, "wordEntryId");
        ObjectNode wordEntry = findWord(document, wordEntryId);
        if (wordEntry == null) {
            throw JsonSupport.validation("学习目标引用的词条不存在");
        }
        return wordEntry;
    }

    public ObjectNode requireSession(ObjectNode document, String sessionId) {
        ObjectNode session = findSession(document, sessionId);
        if (session == null) {
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "SESSION_NOT_FOUND", "会话不存在");
        }
        return session;
    }

    public QueueSettings queueSettings(ObjectNode document) {
        int newLimit = 10;
        int reviewLimit = 50;
        boolean paused = false;
        for (ObjectNode record : JsonSupport.objectValues(array(document, "appMeta"), "appMeta")) {
            if (!"practice.queueSettings".equals(record.path("key").asText())) {
                continue;
            }
            String value = record.path("value").asText();
            try {
                JsonNode parsed = objectMapper.readTree(value);
                if (parsed.isObject()) {
                    int parsedNewLimit = parsed.path("newLimit").asInt(newLimit);
                    int parsedReviewLimit = parsed.path("reviewLimit").asInt(reviewLimit);
                    boolean parsedPaused = parsed.path("paused").asBoolean(paused);
                    if (parsedNewLimit >= 0 && parsedNewLimit <= 100
                            && parsedReviewLimit >= 0 && parsedReviewLimit <= 500) {
                        return new QueueSettings(parsedNewLimit, parsedReviewLimit, parsedPaused);
                    }
                }
            } catch (Exception ignored) {
                return new QueueSettings(newLimit, reviewLimit, paused);
            }
        }
        return new QueueSettings(newLimit, reviewLimit, paused);
    }

    public boolean hasSessionReview(ObjectNode document, String sessionId, String studyItemId) {
        for (ObjectNode log : JsonSupport.objectValues(array(document, "reviewLogs"), "reviewLogs")) {
            if (sessionId.equals(log.path("sessionId").asText(null))
                    && studyItemId.equals(log.path("studyItemId").asText(null))) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesAnswer(ObjectNode wordEntry, String response) {
        String normalized = normalize(response);
        if (normalized.isEmpty()) {
            return false;
        }
        if (normalized.equals(normalize(wordEntry.path("term").asText()))) {
            return true;
        }
        JsonNode accepted = wordEntry.get("acceptedAnswers");
        if (accepted == null || !accepted.isArray()) {
            return false;
        }
        for (JsonNode answer : accepted) {
            if (answer.isTextual() && normalized.equals(normalize(answer.textValue()))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }
}
