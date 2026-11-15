package com.fingermemory.server.learning;

import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.common.JsonSupport;
import com.fingermemory.server.learning.LearningDtos.ReviewEvidence;
import com.fingermemory.server.learning.LearningDtos.ReviewPreviewRequest;
import com.fingermemory.server.learning.LearningDtos.ReviewPreviewView;
import com.fingermemory.server.learning.LearningDtos.ReviewSubmissionRequest;
import com.fingermemory.server.scheduler.FsrsWorkerClient;
import com.fingermemory.server.scheduler.WorkerPreviewResult;
import com.fingermemory.server.scheduler.WorkerReviewResult;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ReviewService {
    private final UserDataStore userDataStore;
    private final LearningDocuments documents;
    private final ReviewReceiptMapper receiptMapper;
    private final RewardEventMapper rewardEventMapper;
    private final FsrsWorkerClient worker;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public ReviewService(UserDataStore userDataStore, LearningDocuments documents,
                         ReviewReceiptMapper receiptMapper, RewardEventMapper rewardEventMapper,
                         FsrsWorkerClient worker, ObjectMapper objectMapper, Clock clock,
                         TransactionTemplate transactionTemplate) {
        this.userDataStore = userDataStore;
        this.documents = documents;
        this.receiptMapper = receiptMapper;
        this.rewardEventMapper = rewardEventMapper;
        this.worker = worker;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    public ReviewPreviewView preview(String ownerId, String sessionId,
                                     ReviewPreviewRequest request) {
        UserDataRow row = userDataStore.read(ownerId);
        if (row.getDataVersion() != request.expectedDataVersion()) {
            throw dataVersionConflict();
        }
        ObjectNode document = JsonSupport.copyObject(
                objectMapper.readTree(row.getPayloadJson()), "user data");
        ObjectNode session = documents.requireSession(document, sessionId);
        if (JsonSupport.requireLong(session, "version") != request.expectedSessionVersion()) {
            throw sessionVersionConflict();
        }
        ObjectNode pending = requirePending(session, request.studyItemId());
        if (!request.studyItemId().equals(pending.path("studyItemId").asText())) {
            throw pendingMismatch();
        }
        ObjectNode state = documents.findReviewState(document, request.studyItemId());
        long stateVersion = stateVersion(state);
        if (stateVersion != request.expectedReviewVersion()) {
            throw reviewVersionConflict();
        }
        ObjectNode card = state == null ? null : cardFromState(state);
        long now = clock.instant().toEpochMilli();
        WorkerPreviewResult preview = worker.preview(card, request.studyItemId(), now);
        verifyStateConfig(state, preview.packageVersion(), preview.modelVersion(),
                preview.configHash(), preview.config());
        return new ReviewPreviewView(
                preview.previews(),
                preview.packageVersion(),
                preview.modelVersion(),
                preview.configHash(),
                now,
                stateVersion,
                request.expectedSessionVersion(),
                row.getDataVersion());
    }

    public ObjectNode submit(String ownerId, String sessionId, String submissionId,
                             ReviewSubmissionRequest request) {
        String fingerprint = requestFingerprint(sessionId, submissionId, request);
        ObjectNode existing = findReceipt(ownerId, submissionId, fingerprint);
        if (existing != null) {
            return existing;
        }

        UserDataRow snapshot = userDataStore.read(ownerId);
        if (snapshot.getDataVersion() != request.expectedDataVersion()) {
            throw dataVersionConflict();
        }
        ObjectNode snapshotDocument = JsonSupport.copyObject(
                objectMapper.readTree(snapshot.getPayloadJson()), "user data");
        ObjectNode snapshotSession = documents.requireSession(snapshotDocument, sessionId);
        if (JsonSupport.requireLong(snapshotSession, "version")
                != request.expectedSessionVersion()) {
            throw sessionVersionConflict();
        }
        PendingSnapshot pending = requireValidPending(
                snapshotDocument, snapshotSession, submissionId, request);
        if (documents.hasSessionReview(snapshotDocument, sessionId, pending.studyItemId())) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_REVIEW_LIMIT",
                    "该学习目标已在本次会话完成正式复习");
        }
        ObjectNode state = documents.findReviewState(snapshotDocument, pending.studyItemId());
        long stateVersion = stateVersion(state);
        if (stateVersion != request.expectedReviewVersion()) {
            throw reviewVersionConflict();
        }
        ObjectNode beforeCard = state == null ? null : cardFromState(state);
        long reviewedAt = clock.instant().toEpochMilli();
        WorkerReviewResult scheduled = worker.review(
                beforeCard, pending.studyItemId(), request.rating(), reviewedAt);
        verifyStateConfig(state, scheduled.packageVersion(), scheduled.modelVersion(),
                scheduled.configHash(), scheduled.config());

        return transactionTemplate.execute(status -> commit(
                ownerId,
                sessionId,
                submissionId,
                request,
                fingerprint,
                pending,
                beforeCard,
                stateVersion,
                reviewedAt,
                scheduled));
    }

    private ObjectNode commit(
            String ownerId,
            String sessionId,
            String submissionId,
            ReviewSubmissionRequest request,
            String fingerprint,
            PendingSnapshot pending,
            ObjectNode beforeCard,
            long stateVersion,
            long reviewedAt,
            WorkerReviewResult scheduled) {
        LockedUserData locked = userDataStore.lock(ownerId);
        if (locked.row().getDataVersion() != request.expectedDataVersion()) {
            throw dataVersionConflict();
        }
        ObjectNode currentReceipt = findReceipt(ownerId, submissionId, fingerprint);
        if (currentReceipt != null) {
            return currentReceipt;
        }
        ObjectNode session = documents.requireSession(locked.document(), sessionId);
        if (JsonSupport.requireLong(session, "version") != request.expectedSessionVersion()) {
            throw sessionVersionConflict();
        }
        PendingSnapshot currentPending = requireValidPending(
                locked.document(), session, submissionId, request);
        if (!currentPending.fingerprint().equals(pending.fingerprint())) {
            throw pendingMismatch();
        }
        if (documents.hasSessionReview(locked.document(), sessionId, pending.studyItemId())) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_REVIEW_LIMIT",
                    "该学习目标已在本次会话完成正式复习");
        }
        ObjectNode currentState = documents.findReviewState(
                locked.document(), pending.studyItemId());
        if (stateVersion(currentState) != request.expectedReviewVersion()
                || !cardsEqual(beforeCard, currentState == null ? null : cardFromState(currentState))) {
            throw reviewVersionConflict();
        }
        verifyStateConfig(currentState, scheduled.packageVersion(), scheduled.modelVersion(),
                scheduled.configHash(), scheduled.config());

        long afterVersion = request.expectedReviewVersion() + 1;
        ObjectNode afterCard = scheduled.card().deepCopy();
        ObjectNode afterState = afterCard.deepCopy();
        afterState.put("studyItemId", pending.studyItemId());
        afterState.set("schedulerConfig", scheduled.config().deepCopy());
        afterState.put("schedulerPackageVersion", scheduled.packageVersion());
        afterState.put("schedulerModelVersion", scheduled.modelVersion());
        afterState.put("configHash", scheduled.configHash());
        afterState.put("updatedAt", reviewedAt);
        afterState.put("version", afterVersion);

        ObjectNode studyItem = documents.requireStudyItem(
                locked.document(), pending.studyItemId());
        ObjectNode wordEntry = documents.requireWordForStudy(locked.document(), studyItem);
        ObjectNode log = locked.document().objectNode();
        log.put("submissionId", submissionId);
        log.put("studyItemId", pending.studyItemId());
        log.put("wordEntryId", wordEntry.path("id").asText());
        log.put("modeId", session.path("modeId").asText());
        log.put("sessionId", sessionId);
        log.put("direction", "meaning-to-spelling");
        log.put("rating", request.rating());
        log.put("reviewedAt", reviewedAt);
        log.put("response", pending.response());
        log.put("inputMatchedAnswer", pending.inputMatchedAnswer());
        log.put("answerWasHidden", pending.evidence().answerWasHidden());
        log.put("hintUsed", pending.evidence().hintUsed());
        log.put("answerRevealed", pending.evidence().answerRevealed());
        log.put("abandoned", pending.evidence().abandoned());
        if (beforeCard == null) {
            log.putNull("beforeCard");
        } else {
            log.set("beforeCard", beforeCard.deepCopy());
        }
        log.set("afterCard", afterCard);
        log.set("srsRevlog", scheduled.revlog().deepCopy());
        log.set("schedulerConfig", scheduled.config().deepCopy());
        log.put("schedulerPackageVersion", scheduled.packageVersion());
        log.put("schedulerModelVersion", scheduled.modelVersion());
        log.put("requestFingerprint", fingerprint);

        ArrayNode states = documents.array(locked.document(), "reviewStates");
        if (currentState == null) {
            states.add(afterState);
        } else {
            JsonSupport.replaceById(states, "studyItemId", afterState);
        }
        documents.array(locked.document(), "reviewLogs").add(log);
        completeSessionItem(session, pending.studyItemId(), reviewedAt);
        userDataStore.save(locked, locked.document());

        ObjectNode rewardPayload = objectMapper.createObjectNode();
        rewardPayload.put("submissionId", submissionId);
        rewardPayload.put("sessionId", sessionId);
        rewardPayload.put("studyItemId", pending.studyItemId());
        rewardPayload.put("rating", request.rating());
        try {
            rewardEventMapper.insert(
                    ownerId, "review", submissionId, true, reviewedAt,
                    objectMapper.writeValueAsString(rewardPayload));
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "REWARD_EVENT_CONFLICT",
                    "奖励事件已存在，正式复习未提交");
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("outcome", "created");
        response.set("state", afterState.deepCopy());
        response.set("log", log.deepCopy());
        response.set("session", session.deepCopy());
        response.put("dataVersion", locked.row().getDataVersion());
        response.put("rewardEligible", true);
        receiptMapper.insert(
                ownerId, submissionId, fingerprint,
                objectMapper.writeValueAsString(response), reviewedAt);
        return response;
    }

    private ObjectNode findReceipt(String ownerId, String submissionId, String fingerprint) {
        ReviewReceiptRow receipt = receiptMapper.find(ownerId, submissionId);
        if (receipt == null) {
            return null;
        }
        if (!fingerprint.equals(receipt.getRequestFingerprint())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_SUBMISSION_CONFLICT",
                    "正式复习提交标识已用于不同内容");
        }
        return JsonSupport.copyObject(
                objectMapper.readTree(receipt.getResponseJson()), "review receipt");
    }

    private String requestFingerprint(String sessionId, String submissionId,
                                      ReviewSubmissionRequest request) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("sessionId", sessionId);
        value.put("submissionId", submissionId);
        value.put("rating", request.rating());
        value.put("expectedDataVersion", request.expectedDataVersion());
        value.put("expectedReviewVersion", request.expectedReviewVersion());
        value.put("expectedSessionVersion", request.expectedSessionVersion());
        ObjectNode evidence = value.putObject("evidence");
        evidence.put("userConfirmedRating", request.evidence().userConfirmedRating());
        evidence.put("answerWasHidden", request.evidence().answerWasHidden());
        evidence.put("hintUsed", request.evidence().hintUsed());
        evidence.put("answerRevealed", request.evidence().answerRevealed());
        evidence.put("abandoned", request.evidence().abandoned());
        return JsonSupport.fingerprint(objectMapper, value);
    }

    private PendingSnapshot requireValidPending(ObjectNode document, ObjectNode session,
                                                String submissionId,
                                                ReviewSubmissionRequest request) {
        JsonNode pendingNode = session.get("pendingAnswer");
        if (pendingNode == null || !pendingNode.isObject()) {
            throw pendingMismatch();
        }
        ObjectNode pending = (ObjectNode) pendingNode;
        String studyItemId = JsonSupport.requireIdentifier(pending, "studyItemId");
        String practiceAttemptId = JsonSupport.requireIdentifier(pending, "practiceAttemptId");
        String reviewSubmissionId = JsonSupport.requireIdentifier(pending, "reviewSubmissionId");
        if (!studyItemId.equals(session.path("currentStudyItemId").asText())
                || !reviewSubmissionId.equals(submissionId)) {
            throw pendingMismatch();
        }
        ObjectNode attempt = JsonSupport.findById(
                documents.array(document, "practiceAttempts"), practiceAttemptId);
        if (attempt == null) {
            throw pendingMismatch();
        }
        ReviewEvidence evidence = request.evidence();
        if (evidence == null || !evidence.userConfirmedRating()
                || !evidence.answerWasHidden() || evidence.hintUsed()
                || evidence.answerRevealed() || evidence.abandoned()) {
            throw new ApiException(HttpStatus.CONFLICT, "FORMAL_REVIEW_INELIGIBLE",
                    "当前作答不满足正式复习门禁");
        }
        if (!pending.path("answerWasHidden").asBoolean()
                || pending.path("hintUsed").asBoolean()
                || pending.path("answerRevealed").asBoolean()
                || attempt.path("abandoned").asBoolean()) {
            throw new ApiException(HttpStatus.CONFLICT, "FORMAL_REVIEW_INELIGIBLE",
                    "提示、揭晓或放弃后的作答不能推进正式复习");
        }
        if (!evidence.answerWasHidden()
                || evidence.hintUsed() != pending.path("hintUsed").asBoolean()
                || evidence.answerRevealed() != pending.path("answerRevealed").asBoolean()) {
            throw pendingMismatch();
        }
        ObjectNode identity = objectMapper.createObjectNode();
        identity.put("practiceAttemptId", practiceAttemptId);
        identity.put("studyItemId", studyItemId);
        identity.put("response", pending.path("response").asText());
        identity.put("answerWasHidden", pending.path("answerWasHidden").asBoolean());
        identity.put("hintUsed", pending.path("hintUsed").asBoolean());
        identity.put("answerRevealed", pending.path("answerRevealed").asBoolean());
        identity.put("abandoned", attempt.path("abandoned").asBoolean());
        identity.put("submittedAt", JsonSupport.requireLong(pending, "submittedAt"));
        identity.put("reviewSubmissionId", reviewSubmissionId);
        return new PendingSnapshot(
                studyItemId,
                pending.path("response").asText(),
                pending.path("inputMatchedAnswer").asBoolean(),
                new PendingEvidence(
                        pending.path("answerWasHidden").asBoolean(),
                        pending.path("hintUsed").asBoolean(),
                        pending.path("answerRevealed").asBoolean(),
                        attempt.path("abandoned").asBoolean()),
                JsonSupport.fingerprint(objectMapper, identity));
    }

    private ObjectNode requirePending(ObjectNode session, String studyItemId) {
        JsonNode pending = session.get("pendingAnswer");
        if (pending == null || !pending.isObject()
                || !studyItemId.equals(pending.path("studyItemId").asText())) {
            throw pendingMismatch();
        }
        return (ObjectNode) pending;
    }

    private void completeSessionItem(ObjectNode session, String studyItemId, long now) {
        ArrayNode queue = JsonSupport.requireArray(session, "queue");
        int currentIndex = session.path("currentIndex").asInt();
        ObjectNode current = indexObject(queue, currentIndex);
        if (!studyItemId.equals(current.path("studyItemId").asText())
                || !"pending".equals(current.path("status").asText())) {
            throw pendingMismatch();
        }
        current.put("status", "completed");
        session.putNull("pendingAnswer");
        int nextIndex = -1;
        for (int index = currentIndex + 1; index < queue.size(); index += 1) {
            if ("pending".equals(queue.get(index).path("status").asText())) {
                nextIndex = index;
                break;
            }
        }
        if (nextIndex < 0) {
            for (int index = 0; index < queue.size(); index += 1) {
                if ("pending".equals(queue.get(index).path("status").asText())) {
                    nextIndex = index;
                    break;
                }
            }
        }
        session.put("version", session.path("version").asLong() + 1);
        session.put("updatedAt", now);
        if (nextIndex < 0) {
            session.put("status", "completed");
            session.putNull("currentStudyItemId");
            session.put("completedAt", now);
            session.put("endedReason", "completed");
        } else {
            session.put("status", "active");
            session.put("currentIndex", nextIndex);
            session.put("currentStudyItemId",
                    indexObject(queue, nextIndex).path("studyItemId").asText());
            session.putNull("completedAt");
            session.remove("endedReason");
        }
    }

    private ObjectNode indexObject(ArrayNode array, int index) {
        JsonNode value = array.get(index);
        if (value == null || !value.isObject()) {
            throw new IllegalStateException("Expected an object at queue index " + index);
        }
        return (ObjectNode) value;
    }

    private long stateVersion(ObjectNode state) {
        return state == null ? 0 : JsonSupport.requireLong(state, "version");
    }

    private ObjectNode cardFromState(ObjectNode state) {
        ObjectNode card = state.deepCopy();
        card.remove(List.of(
                "studyItemId",
                "schedulerConfig",
                "schedulerPackageVersion",
                "schedulerModelVersion",
                "updatedAt",
                "version",
                "configHash"));
        return card;
    }

    private boolean cardsEqual(ObjectNode left, ObjectNode right) {
        return left == null ? right == null
                : right != null
                && JsonSupport.canonicalize(left).equals(JsonSupport.canonicalize(right));
    }

    private void verifyStateConfig(ObjectNode state, String packageVersion, String modelVersion,
                                   String configHash, ObjectNode workerConfig) {
        if (!"6.0.0".equals(packageVersion) || !"fsrs-7".equals(modelVersion)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "FSRS_WORKER_INCOMPATIBLE", "调度组件版本不受支持");
        }
        if (state == null) {
            return;
        }
        String statePackage = state.path("schedulerPackageVersion").asText();
        String stateModel = state.path("schedulerModelVersion").asText();
        JsonNode stateConfig = state.get("schedulerConfig");
        if (stateConfig == null || !stateConfig.isObject()) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_STATE_CONFIG_MISMATCH",
                    "已有复习状态缺少调度配置");
        }
        String storedHash = state.path("configHash").asText("");
        String calculatedHash = JsonSupport.fingerprint(objectMapper, stateConfig);
        if (!packageVersion.equals(statePackage) || !modelVersion.equals(stateModel)
                || !configHash.equals(calculatedHash)
                || (!storedHash.isBlank() && !storedHash.equals(calculatedHash))) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_STATE_CONFIG_MISMATCH",
                    "已有复习状态与当前 FSRS-7 配置不兼容");
        }
    }

    private ApiException pendingMismatch() {
        return new ApiException(HttpStatus.CONFLICT, "SESSION_PENDING_MISMATCH",
                "待评分作答与服务端会话不一致");
    }

    private ApiException dataVersionConflict() {
        return new ApiException(HttpStatus.CONFLICT, "DATA_VERSION_CONFLICT",
                "学习数据已在其他客户端更新，请重新读取");
    }

    private ApiException sessionVersionConflict() {
        return new ApiException(HttpStatus.CONFLICT, "SESSION_VERSION_CONFLICT",
                "会话已在其他客户端更新，请重新读取");
    }

    private ApiException reviewVersionConflict() {
        return new ApiException(HttpStatus.CONFLICT, "REVIEW_VERSION_CONFLICT",
                "正式复习状态已在其他客户端更新");
    }

    private record PendingSnapshot(
            String studyItemId,
            String response,
            boolean inputMatchedAnswer,
            PendingEvidence evidence,
            String fingerprint) {
    }

    private record PendingEvidence(
            boolean answerWasHidden,
            boolean hintUsed,
            boolean answerRevealed,
            boolean abandoned) {
    }
}
