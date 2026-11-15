package com.fingermemory.server.learning;

import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.common.JsonSupport;
import com.fingermemory.server.learning.LearningDtos.CreateWordRequest;
import com.fingermemory.server.learning.LearningDtos.QueueEntryView;
import com.fingermemory.server.learning.LearningDtos.QueuePlanView;
import com.fingermemory.server.learning.LearningDtos.WordEntryItemView;
import com.fingermemory.server.learning.LearningDtos.WordListResponse;
import com.fingermemory.server.learning.LearningDtos.WordStudyItemView;
import com.fingermemory.server.lexicon.Lexicon;
import com.fingermemory.server.lexicon.LexiconMapper;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class LearningQueryService {
    private final UserDataStore userDataStore;
    private final LexiconMapper lexiconMapper;
    private final LearningDocuments documents;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public LearningQueryService(UserDataStore userDataStore, LexiconMapper lexiconMapper,
                                LearningDocuments documents, Clock clock,
                                ObjectMapper objectMapper) {
        this.userDataStore = userDataStore;
        this.lexiconMapper = lexiconMapper;
        this.documents = documents;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WordStudyItemView createWord(String ownerId, String lexiconId,
                                        CreateWordRequest request) {
        requireLexicon(ownerId, lexiconId);
        LockedUserData locked = userDataStore.lock(ownerId);
        long now = clock.instant().toEpochMilli();
        String suffix = UUID.randomUUID().toString();
        String wordId = "word:" + suffix;
        String studyItemId = "study:" + suffix;

        ObjectNode word = JsonSupport.copyObject(
                locked.document().objectNode(), "word");
        word.put("id", wordId);
        word.put("term", request.term().trim());
        word.put("meaning", request.meaning().trim());
        word.set("acceptedAnswers", textArray(request.acceptedAnswers()));
        word.set("hints", textArray(request.hints()));
        word.put("normalizedTerm", normalize(request.term()));
        word.put("createdAt", now);
        word.put("updatedAt", now);

        ObjectNode studyItem = locked.document().objectNode();
        studyItem.put("id", studyItemId);
        studyItem.put("wordEntryId", wordId);
        studyItem.put("direction", "meaning-to-spelling");
        studyItem.put("createdAt", now);
        studyItem.put("updatedAt", now);

        ObjectNode membership = locked.document().objectNode();
        membership.put("lexiconId", lexiconId);
        membership.put("studyItemId", studyItemId);
        membership.put("order", nextMembershipOrder(locked.document(), lexiconId));
        membership.set("sourceHints", textArray(request.hints()));

        documents.array(locked.document(), "words").add(word);
        documents.array(locked.document(), "studyItems").add(studyItem);
        documents.array(locked.document(), "lexiconItems").add(membership);
        userDataStore.save(locked, locked.document());
        return new WordStudyItemView(word, studyItem, membership, locked.row().getDataVersion());
    }

    public WordListResponse listWords(String ownerId, String lexiconId) {
        requireLexicon(ownerId, lexiconId);
        UserDataRow row = userDataStore.read(ownerId);
        ObjectNode document = readDocument(row);
        List<WordEntryItemView> items = new ArrayList<>();
        for (ObjectNode membership : JsonSupport.objectValues(
                documents.array(document, "lexiconItems"), "lexiconItems")) {
            if (!lexiconId.equals(membership.path("lexiconId").asText())) {
                continue;
            }
            String studyItemId = membership.path("studyItemId").asText();
            ObjectNode studyItem = documents.findStudyItem(document, studyItemId);
            if (studyItem == null) {
                throw new IllegalStateException("Lexicon membership has no study item");
            }
            ObjectNode wordEntry = documents.requireWordForStudy(document, studyItem);
            ObjectNode reviewState = documents.findReviewState(document, studyItemId);
            items.add(new WordEntryItemView(wordEntry.deepCopy(), studyItem.deepCopy(),
                    membership.deepCopy(),
                    reviewState == null ? null : reviewState.deepCopy(),
                    reviewState == null ? 0 : reviewState.path("version").asLong()));
        }
        items.sort(Comparator
                .comparingInt((WordEntryItemView item) -> item.lexiconItem().path("order").asInt())
                .thenComparing(item -> item.studyItem().path("id").asText()));
        return new WordListResponse(items, row.getDataVersion());
    }

    public QueuePlanView queuePlan(String ownerId, String timeZone,
                                   Integer newLimitOverride, Integer reviewLimitOverride) {
        ZoneId zoneId = parseZone(timeZone);
        Instant now = clock.instant();
        ZonedDateTime local = now.atZone(zoneId);
        long dayStartAt = local.toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli();
        long dayEndAt = local.toLocalDate().plusDays(1).atStartOfDay(zoneId)
                .toInstant().toEpochMilli();
        UserDataRow row = userDataStore.read(ownerId);
        ObjectNode document = readDocument(row);
        LearningDocuments.QueueSettings stored = documents.queueSettings(document);
        int newLimit = newLimitOverride == null ? stored.newLimit() : newLimitOverride;
        int reviewLimit = reviewLimitOverride == null ? stored.reviewLimit() : reviewLimitOverride;
        validateLimits(newLimit, reviewLimit);

        Set<String> activeLexiconIds = new HashSet<>();
        for (Lexicon lexicon : lexiconMapper.listAllByOwner(ownerId)) {
            if (lexicon.getArchivedAtMs() == null) {
                activeLexiconIds.add(lexicon.getId());
            }
        }
        Map<String, Integer> orderByStudyItemId = new HashMap<>();
        for (ObjectNode membership : JsonSupport.objectValues(
                documents.array(document, "lexiconItems"), "lexiconItems")) {
            if (activeLexiconIds.contains(membership.path("lexiconId").asText())) {
                String studyItemId = JsonSupport.requireIdentifier(membership, "studyItemId");
                int order = JsonSupport.requireInt(membership, "order");
                orderByStudyItemId.merge(studyItemId, order, Math::min);
            }
        }

        Map<String, ObjectNode> stateByStudyItemId = new HashMap<>();
        for (ObjectNode state : JsonSupport.objectValues(
                documents.array(document, "reviewStates"), "reviewStates")) {
            stateByStudyItemId.put(JsonSupport.requireIdentifier(state, "studyItemId"), state);
        }

        List<QueueCandidate> due = new ArrayList<>();
        List<QueueCandidate> fresh = new ArrayList<>();
        for (String studyItemId : orderByStudyItemId.keySet()) {
            ObjectNode studyItem = documents.findStudyItem(document, studyItemId);
            if (studyItem == null) {
                throw new IllegalStateException("Queue membership has no study item");
            }
            ObjectNode wordEntry = documents.requireWordForStudy(document, studyItem);
            ObjectNode state = stateByStudyItemId.get(studyItemId);
            QueueCandidate candidate = new QueueCandidate(
                    studyItem, wordEntry, state, orderByStudyItemId.get(studyItemId));
            if (state != null && JsonSupport.requireLong(state, "dueAt") < dayEndAt) {
                due.add(candidate);
            } else if (state == null) {
                fresh.add(candidate);
            }
        }
        Comparator<QueueCandidate> wordOrder = Comparator
                .comparing((QueueCandidate item) ->
                        item.wordEntry().path("term").asText().toLowerCase(Locale.ROOT))
                .thenComparing(item -> item.studyItem().path("id").asText());
        due.sort(Comparator
                .comparingLong((QueueCandidate item) -> item.state().path("dueAt").asLong())
                .thenComparing(wordOrder));
        fresh.sort(Comparator
                .comparingInt(QueueCandidate::membershipOrder)
                .thenComparing(wordOrder));

        List<QueueEntryView> selected = new ArrayList<>();
        ObjectNode settingsNode = document.objectNode();
        settingsNode.put("newLimit", newLimit);
        settingsNode.put("reviewLimit", reviewLimit);
        settingsNode.put("paused", stored.paused());
        if (!stored.paused()) {
            due.stream().limit(reviewLimit).forEach(item -> selected.add(item.view("review")));
            fresh.stream().limit(newLimit).forEach(item -> selected.add(item.view("new")));
        }
        return new QueuePlanView(
                now.toEpochMilli(),
                zoneId.getId(),
                dayStartAt,
                dayEndAt,
                settingsNode,
                due.size(),
                fresh.size(),
                selected,
                row.getDataVersion());
    }

    private ObjectNode readDocument(UserDataRow row) {
        return JsonSupport.copyObject(objectMapper.readTree(row.getPayloadJson()), "user data");
    }

    private void requireLexicon(String ownerId, String lexiconId) {
        Lexicon lexicon = lexiconMapper.findByIdAndOwner(lexiconId, ownerId);
        if (lexicon == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "LEXICON_NOT_FOUND", "词库不存在");
        }
    }

    private int nextMembershipOrder(ObjectNode document, String lexiconId) {
        int maximum = -1;
        for (ObjectNode membership : JsonSupport.objectValues(
                documents.array(document, "lexiconItems"), "lexiconItems")) {
            if (lexiconId.equals(membership.path("lexiconId").asText())) {
                maximum = Math.max(maximum, membership.path("order").asInt());
            }
        }
        return maximum + 1;
    }

    private ArrayNode textArray(List<String> values) {
        ArrayNode array = objectMapper.createArrayNode();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    normalized.add(value.trim());
                }
            }
        }
        normalized.forEach(array::add);
        return array;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private ZoneId parseZone(String timeZone) {
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "timeZone 必须是有效的 IANA 时区");
        }
    }

    private void validateLimits(int newLimit, int reviewLimit) {
        if (newLimit < 0 || newLimit > 100 || reviewLimit < 0 || reviewLimit > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "队列上限超出允许范围");
        }
    }

    private record QueueCandidate(
            ObjectNode studyItem,
            ObjectNode wordEntry,
            ObjectNode state,
            int membershipOrder) {
        QueueEntryView view(String source) {
            return new QueueEntryView(
                    studyItem.deepCopy(),
                    wordEntry.deepCopy(),
                    state == null ? null : state.deepCopy(),
                    source);
        }
    }
}
