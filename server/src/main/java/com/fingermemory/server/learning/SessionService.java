package com.fingermemory.server.learning;

import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.common.JsonSupport;
import com.fingermemory.server.learning.LearningDtos.CreateSessionRequest;
import com.fingermemory.server.learning.LearningDtos.PracticeResponseRequest;
import com.fingermemory.server.learning.LearningDtos.PracticeResponseView;
import com.fingermemory.server.learning.LearningDtos.PracticeCompletionRequest;
import com.fingermemory.server.learning.LearningDtos.PracticeCompletionView;
import com.fingermemory.server.learning.LearningDtos.QueueEntryView;
import com.fingermemory.server.learning.LearningDtos.QueuePlanView;
import com.fingermemory.server.learning.LearningDtos.SessionEnvelope;
import com.fingermemory.server.learning.LearningDtos.SessionListResponse;
import com.fingermemory.server.learning.LearningDtos.SessionPatchRequest;
import com.fingermemory.server.learning.LearningDtos.WordEntryItemView;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class SessionService {
    private final UserDataStore userDataStore;
    private final LearningQueryService queryService;
    private final LearningDocuments documents;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SessionService(UserDataStore userDataStore, LearningQueryService queryService,
                          LearningDocuments documents, ObjectMapper objectMapper, Clock clock) {
        this.userDataStore = userDataStore;
        this.queryService = queryService;
        this.documents = documents;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public SessionEnvelope create(String ownerId, CreateSessionRequest request) {
        LockedUserData locked = userDataStore.lock(ownerId);
        QueuePlanView plan = queryService.queuePlan(
                ownerId, request.timeZone(), request.newLimit(), request.reviewLimit());
        if (plan.dataVersion() != locked.row().getDataVersion()) {
            throw versionConflict();
        }
        if (plan.selected().isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "QUEUE_EMPTY", "当前没有可开始的在线队列");
        }
        long now = clock.instant().toEpochMilli();
        ObjectNode session = locked.document().objectNode();
        session.put("id", UUID.randomUUID().toString());
        session.put("modeId", request.modeId());
        session.put("status", "active");
        ArrayNode queue = session.putArray("queue");
        Set<String> queuedStudyItemIds = new HashSet<>();
        for (QueueEntryView item : plan.selected()) {
            String studyItemId = item.studyItem().path("id").asText();
            if (!queuedStudyItemIds.add(studyItemId)) {
                continue;
            }
            ObjectNode queueItem = queue.addObject();
            queueItem.put("studyItemId", studyItemId);
            queueItem.put("wordEntryId", item.wordEntry().path("id").asText());
            queueItem.put("status", "pending");
            queueItem.put("source", item.source());
        }
        if (queue.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "QUEUE_EMPTY", "当前没有可开始的在线队列");
        }
        session.put("currentIndex", 0);
        session.put("currentStudyItemId", queue.get(0).path("studyItemId").asText());
        session.putObject("interactions");
        session.putNull("pendingAnswer");
        session.put("speed", 1);
        session.put("reduceMotion", request.reduceMotion());
        session.put("createdAt", now);
        session.put("updatedAt", now);
        session.putNull("pausedAt");
        session.putNull("completedAt");
        session.put("version", 0);
        documents.array(locked.document(), "practiceSessions").add(session);
        userDataStore.save(locked, locked.document());
        return new SessionEnvelope(
                session.deepCopy(),
                sessionItems(locked.document(), session),
                locked.row().getDataVersion());
    }

    public SessionEnvelope get(String ownerId, String sessionId) {
        UserDataRow row = userDataStore.read(ownerId);
        ObjectNode document = JsonSupport.copyObject(
                objectMapper.readTree(row.getPayloadJson()), "user data");
        ObjectNode session = documents.requireSession(document, sessionId);
        return new SessionEnvelope(
                session.deepCopy(), sessionItems(document, session), row.getDataVersion());
    }

    public SessionListResponse listActive(String ownerId) {
        UserDataRow row = userDataStore.read(ownerId);
        ObjectNode document = JsonSupport.copyObject(
                objectMapper.readTree(row.getPayloadJson()), "user data");
        List<SessionEnvelope> sessions =
                JsonSupport.objectValues(
                                documents.array(document, "practiceSessions"), "practiceSessions")
                        .stream()
                        .filter(session -> !"completed".equals(session.path("status").asText()))
                        .sorted(Comparator.comparingLong(
                                        (ObjectNode session) ->
                                                session.path("updatedAt").asLong())
                                .reversed())
                        .map(session -> new SessionEnvelope(
                                session.deepCopy(),
                                sessionItems(document, session),
                                row.getDataVersion()))
                        .toList();
        return new SessionListResponse(sessions, row.getDataVersion());
    }

    @Transactional
    public SessionEnvelope patch(String ownerId, String sessionId, SessionPatchRequest request) {
        LockedUserData locked = userDataStore.lock(ownerId);
        requireDataVersion(locked, request.expectedDataVersion());
        ObjectNode session = documents.requireSession(locked.document(), sessionId);
        requireSessionVersion(session, request.expectedVersion());
        long now = clock.instant().toEpochMilli();

        if (request.status() != null) {
            if (!Set.of("active", "paused", "completed").contains(request.status())) {
                throw JsonSupport.validation("会话状态无效");
            }
            session.put("status", request.status());
            if ("paused".equals(request.status())) {
                session.put("pausedAt", now);
            } else if ("active".equals(request.status())) {
                session.putNull("pausedAt");
            } else {
                session.put("completedAt", now);
                session.put("endedReason", "completed");
                session.putNull("pendingAnswer");
            }
        }
        ArrayNode queue = JsonSupport.requireArray(session, "queue");
        if (request.currentIndex() != null) {
            if (request.currentIndex() < 0 || request.currentIndex() >= queue.size()) {
                throw JsonSupport.validation("当前会话索引超出队列");
            }
            session.put("currentIndex", request.currentIndex());
        }
        if (request.currentStudyItemId() != null) {
            int currentIndex = session.path("currentIndex").asInt();
            ObjectNode item = requireQueueObject(queue, currentIndex);
            if (!request.currentStudyItemId().equals(item.path("studyItemId").asText())) {
                throw JsonSupport.validation("当前学习目标与队列索引不一致");
            }
            session.put("currentStudyItemId", request.currentStudyItemId());
        }
        if (request.interactions() != null) {
            validateInteractions(request.interactions(), queue);
            session.set("interactions", request.interactions().deepCopy());
        }
        if (request.speed() != null) {
            if (!Double.isFinite(request.speed()) || request.speed() < 0.5 || request.speed() > 2) {
                throw JsonSupport.validation("会话速度必须在 0.5 到 2 之间");
            }
            session.put("speed", request.speed());
        }
        if (request.reduceMotion() != null) {
            session.put("reduceMotion", request.reduceMotion());
        }
        session.put("version", request.expectedVersion() + 1);
        session.put("updatedAt", now);
        userDataStore.save(locked, locked.document());
        return new SessionEnvelope(
                session.deepCopy(),
                sessionItems(locked.document(), session),
                locked.row().getDataVersion());
    }

    @Transactional
    public PracticeResponseView submitResponse(String ownerId, String sessionId,
                                               PracticeResponseRequest request) {
        LockedUserData locked = userDataStore.lock(ownerId);
        String fingerprint = requestFingerprint(sessionId, request);
        for (ObjectNode existing : JsonSupport.objectValues(
                documents.array(locked.document(), "practiceAttempts"), "practiceAttempts")) {
            if (request.practiceAttemptId().equals(existing.path("id").asText())) {
                if (!fingerprint.equals(existing.path("requestFingerprint").asText())) {
                    throw new ApiException(HttpStatus.CONFLICT, "PRACTICE_ATTEMPT_CONFLICT",
                            "练习提交标识已用于不同内容");
                }
                ObjectNode session = documents.requireSession(locked.document(), sessionId);
                JsonNode pending = session.get("pendingAnswer");
                return new PracticeResponseView(
                        existing.deepCopy(),
                        pending != null && pending.isObject()
                                ? ((ObjectNode) pending).deepCopy() : null,
                        session.deepCopy(),
                        locked.row().getDataVersion());
            }
        }
        requireDataVersion(locked, request.expectedDataVersion());
        ObjectNode session = documents.requireSession(locked.document(), sessionId);
        requireSessionVersion(session, request.expectedSessionVersion());
        if (!"active".equals(session.path("status").asText())) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_ACTIVE", "会话当前不可作答");
        }
        if (!request.studyItemId().equals(session.path("currentStudyItemId").asText(null))) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_ITEM_MISMATCH",
                    "提交目标不是当前会话目标");
        }
        ObjectNode studyItem = documents.requireStudyItem(locked.document(), request.studyItemId());
        ObjectNode wordEntry = documents.requireWordForStudy(locked.document(), studyItem);
        long now = clock.instant().toEpochMilli();
        boolean matched = documents.matchesAnswer(wordEntry, request.response());
        AssessmentPolicy.Metadata assessment = AssessmentPolicy.Metadata.from(
                request.assessmentType(),
                request.assessmentPolicyVersion(),
                request.assessmentStage(),
                request.priorExposure(),
                request.explicitNotRemembered());
        assessment.validateStructure();
        ObjectNode attempt = locked.document().objectNode();
        attempt.put("id", request.practiceAttemptId());
        attempt.put("studyItemId", request.studyItemId());
        attempt.put("modeId", session.path("modeId").asText());
        attempt.put("sessionId", sessionId);
        attempt.put("direction", "meaning-to-spelling");
        attempt.put("promptText", wordEntry.path("meaning").asText());
        attempt.put("response", request.response());
        attempt.put("inputMatchedAnswer", matched);
        attempt.put("answerWasHidden", request.answerWasHidden());
        attempt.put("hintUsed", request.hintUsed());
        attempt.put("answerRevealed", request.answerRevealed());
        assessment.writeTo(attempt);
        attempt.put("abandoned", request.abandoned());
        attempt.put("occurredAt", now);
        attempt.put("requestFingerprint", fingerprint);

        ObjectNode pending = locked.document().objectNode();
        pending.put("studyItemId", request.studyItemId());
        pending.put("response", request.response());
        pending.put("answerWasHidden", request.answerWasHidden());
        pending.put("hintUsed", request.hintUsed());
        pending.put("answerRevealed", request.answerRevealed());
        pending.put("inputMatchedAnswer", matched);
        assessment.writeTo(pending);
        pending.put("practiceAttemptId", request.practiceAttemptId());
        pending.put("reviewSubmissionId", request.reviewSubmissionId());
        pending.put("submittedAt", now);

        documents.array(locked.document(), "practiceAttempts").add(attempt);
        session.set("pendingAnswer", pending);
        session.put("version", request.expectedSessionVersion() + 1);
        session.put("updatedAt", now);
        userDataStore.save(locked, locked.document());
        return new PracticeResponseView(
                attempt.deepCopy(), pending.deepCopy(), session.deepCopy(),
                locked.row().getDataVersion());
    }

    @Transactional
    public PracticeCompletionView submitPracticeCompletion(
            String ownerId, String sessionId, PracticeCompletionRequest request) {
        LockedUserData locked = userDataStore.lock(ownerId);
        String fingerprint = practiceCompletionFingerprint(sessionId, request);
        for (ObjectNode existing : JsonSupport.objectValues(
                documents.array(locked.document(), "practiceAttempts"), "practiceAttempts")) {
            if (request.practiceAttemptId().equals(existing.path("id").asText())) {
                if (!fingerprint.equals(existing.path("requestFingerprint").asText())) {
                    throw new ApiException(HttpStatus.CONFLICT, "PRACTICE_ATTEMPT_CONFLICT",
                            "练习提交标识已用于不同内容");
                }
                ObjectNode session = documents.requireSession(locked.document(), sessionId);
                return new PracticeCompletionView(
                        existing.deepCopy(), session.deepCopy(),
                        locked.row().getDataVersion());
            }
        }
        requireDataVersion(locked, request.expectedDataVersion());
        ObjectNode session = documents.requireSession(locked.document(), sessionId);
        requireSessionVersion(session, request.expectedSessionVersion());
        if (!"active".equals(session.path("status").asText())) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_ACTIVE", "会话当前不可作答");
        }
        if (!request.studyItemId().equals(session.path("currentStudyItemId").asText(null))) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_ITEM_MISMATCH",
                    "提交目标不是当前会话目标");
        }
        ObjectNode studyItem = documents.requireStudyItem(locked.document(), request.studyItemId());
        ObjectNode wordEntry = documents.requireWordForStudy(locked.document(), studyItem);
        long now = clock.instant().toEpochMilli();
        AssessmentPolicy.Metadata assessment = AssessmentPolicy.Metadata.from(
                request.assessmentType(),
                request.assessmentPolicyVersion(),
                request.assessmentStage(),
                request.priorExposure(),
                request.explicitNotRemembered());
        assessment.validateStructure();
        ObjectNode attempt = locked.document().objectNode();
        attempt.put("id", request.practiceAttemptId());
        attempt.put("studyItemId", request.studyItemId());
        attempt.put("modeId", session.path("modeId").asText());
        attempt.put("sessionId", sessionId);
        attempt.put("direction", "meaning-to-spelling");
        attempt.put("promptText", wordEntry.path("meaning").asText());
        attempt.put("response", request.response());
        attempt.put("inputMatchedAnswer", documents.matchesAnswer(wordEntry, request.response()));
        attempt.put("answerWasHidden", request.answerWasHidden());
        attempt.put("hintUsed", request.hintUsed());
        attempt.put("answerRevealed", request.answerRevealed());
        assessment.writeTo(attempt);
        attempt.put("abandoned", request.abandoned());
        attempt.put("occurredAt", now);
        attempt.put("requestFingerprint", fingerprint);
        documents.array(locked.document(), "practiceAttempts").add(attempt);
        completeSessionWithoutReview(
                session,
                request.studyItemId(),
                "skipped".equals(request.outcome()) ? "skipped" : "completed",
                now);
        userDataStore.save(locked, locked.document());
        return new PracticeCompletionView(
                attempt.deepCopy(), session.deepCopy(), locked.row().getDataVersion());
    }

    private void validateInteractions(ObjectNode interactions, ArrayNode queue) {
        Set<String> queueIds = new HashSet<>();
        for (JsonNode item : queue) {
            queueIds.add(item.path("studyItemId").asText());
        }
        interactions.properties().forEach(entry -> {
            if (!queueIds.contains(entry.getKey()) || !entry.getValue().isObject()) {
                throw JsonSupport.validation("会话草稿引用了队列外的学习目标");
            }
            ObjectNode interaction = (ObjectNode) entry.getValue();
            if (interaction.get("draft") == null || !interaction.get("draft").isTextual()
                    || interaction.get("revealedHintCount") == null
                    || !interaction.get("revealedHintCount").isIntegralNumber()
                    || interaction.path("revealedHintCount").asInt() < 0
                    || interaction.get("answerRevealed") == null
                    || !interaction.get("answerRevealed").isBoolean()) {
                throw JsonSupport.validation("会话草稿结构无效");
            }
        });
    }

    private ObjectNode requireQueueObject(ArrayNode queue, int index) {
        JsonNode item = queue.get(index);
        if (item == null || !item.isObject()) {
            throw JsonSupport.validation("当前会话索引没有队列目标");
        }
        return (ObjectNode) item;
    }

    private String requestFingerprint(String sessionId, PracticeResponseRequest request) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("sessionId", sessionId);
        value.put("practiceAttemptId", request.practiceAttemptId());
        value.put("reviewSubmissionId", request.reviewSubmissionId());
        value.put("studyItemId", request.studyItemId());
        value.put("response", request.response());
        value.put("answerWasHidden", request.answerWasHidden());
        value.put("hintUsed", request.hintUsed());
        value.put("answerRevealed", request.answerRevealed());
        AssessmentPolicy.Metadata.from(
                request.assessmentType(),
                request.assessmentPolicyVersion(),
                request.assessmentStage(),
                request.priorExposure(),
                request.explicitNotRemembered()).writeTo(value);
        value.put("abandoned", request.abandoned());
        return JsonSupport.fingerprint(objectMapper, value);
    }

    private String practiceCompletionFingerprint(
            String sessionId, PracticeCompletionRequest request) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("sessionId", sessionId);
        value.put("practiceAttemptId", request.practiceAttemptId());
        value.put("studyItemId", request.studyItemId());
        value.put("response", request.response());
        value.put("answerWasHidden", request.answerWasHidden());
        value.put("hintUsed", request.hintUsed());
        value.put("answerRevealed", request.answerRevealed());
        AssessmentPolicy.Metadata.from(
                request.assessmentType(),
                request.assessmentPolicyVersion(),
                request.assessmentStage(),
                request.priorExposure(),
                request.explicitNotRemembered()).writeTo(value);
        value.put("abandoned", request.abandoned());
        value.put("outcome", request.outcome());
        return JsonSupport.fingerprint(objectMapper, value);
    }

    private void completeSessionWithoutReview(
            ObjectNode session, String studyItemId, String status, long now) {
        ArrayNode queue = JsonSupport.requireArray(session, "queue");
        int currentIndex = session.path("currentIndex").asInt();
        ObjectNode current = requireQueueObject(queue, currentIndex);
        if (!studyItemId.equals(current.path("studyItemId").asText())
                || !"pending".equals(current.path("status").asText())) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_PENDING_MISMATCH",
                    "当前目标与待完成队列不一致");
        }
        current.put("status", status);
        session.putNull("pendingAnswer");
        int nextIndex = nextPendingIndex(queue, currentIndex);
        session.put("version", session.path("version").asLong() + 1);
        session.put("updatedAt", now);
        if (nextIndex < 0) {
            session.put("status", "completed");
            session.putNull("currentStudyItemId");
            session.put("completedAt", now);
            session.put("endedReason", "completed");
            return;
        }
        session.put("status", "active");
        session.put("currentIndex", nextIndex);
        session.put("currentStudyItemId",
                requireQueueObject(queue, nextIndex).path("studyItemId").asText());
        session.putNull("completedAt");
        session.remove("endedReason");
    }

    private int nextPendingIndex(ArrayNode queue, int currentIndex) {
        for (int index = currentIndex + 1; index < queue.size(); index += 1) {
            if ("pending".equals(queue.get(index).path("status").asText())) {
                return index;
            }
        }
        for (int index = 0; index < queue.size(); index += 1) {
            if ("pending".equals(queue.get(index).path("status").asText())) {
                return index;
            }
        }
        return -1;
    }

    private void requireDataVersion(LockedUserData locked, long expected) {
        if (locked.row().getDataVersion() != expected) {
            throw versionConflict();
        }
    }

    private void requireSessionVersion(ObjectNode session, long expected) {
        if (JsonSupport.requireLong(session, "version") != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_VERSION_CONFLICT",
                    "会话已在其他客户端更新，请重新读取");
        }
    }

    private ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT, "DATA_VERSION_CONFLICT",
                "学习数据已在其他客户端更新，请重新读取");
    }

    private List<WordEntryItemView> sessionItems(ObjectNode document, ObjectNode session) {
        List<WordEntryItemView> items = new ArrayList<>();
        for (ObjectNode queueItem : JsonSupport.objectValues(
                JsonSupport.requireArray(session, "queue"), "session queue")) {
            String studyItemId = queueItem.path("studyItemId").asText();
            ObjectNode studyItem = documents.findStudyItem(document, studyItemId);
            if (studyItem == null) {
                continue;
            }
            ObjectNode wordEntry = documents.findWord(
                    document, studyItem.path("wordEntryId").asText());
            if (wordEntry == null) {
                continue;
            }
            ObjectNode reviewState = documents.findReviewState(document, studyItemId);
            ObjectNode membership = null;
            for (ObjectNode candidate : JsonSupport.objectValues(
                    documents.array(document, "lexiconItems"), "lexiconItems")) {
                if (studyItemId.equals(candidate.path("studyItemId").asText())) {
                    membership = candidate.deepCopy();
                    break;
                }
            }
            items.add(new WordEntryItemView(
                    wordEntry.deepCopy(), studyItem.deepCopy(), membership,
                    reviewState == null ? null : reviewState.deepCopy(),
                    reviewState == null ? 0 : reviewState.path("version").asLong()));
        }
        return List.copyOf(items);
    }
}
