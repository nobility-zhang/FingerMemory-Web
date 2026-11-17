package com.fingermemory.server.battle;

import com.fingermemory.server.battle.BattleDtos.BattleEnvelope;
import com.fingermemory.server.battle.BattleDtos.CompanionSkillRequest;
import com.fingermemory.server.battle.BattleDtos.CreateBattleRequest;
import com.fingermemory.server.battle.BattleDtos.EndTurnRequest;
import com.fingermemory.server.battle.BattleDtos.LearningClaimRequest;
import com.fingermemory.server.battle.BattleDtos.PlayCardRequest;
import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.common.JsonSupport;
import com.fingermemory.server.learning.LearningDocuments;
import com.fingermemory.server.learning.LockedUserData;
import com.fingermemory.server.learning.ReviewReceiptMapper;
import com.fingermemory.server.learning.ReviewReceiptRow;
import com.fingermemory.server.learning.RewardEventMapper;
import com.fingermemory.server.learning.UserDataStore;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
public class BattleService {
    private static final Pattern ACTION_ID = Pattern.compile("^[A-Za-z0-9_.:-]{3,128}$");

    private final BattleMapper battleMapper;
    private final BattleEngine battleEngine;
    private final BattleStateValidator stateValidator;
    private final ReviewReceiptMapper reviewReceiptMapper;
    private final RewardEventMapper rewardEventMapper;
    private final UserDataStore userDataStore;
    private final LearningDocuments documents;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BattleService(
            BattleMapper battleMapper,
            BattleEngine battleEngine,
            BattleStateValidator stateValidator,
            ReviewReceiptMapper reviewReceiptMapper,
            RewardEventMapper rewardEventMapper,
            UserDataStore userDataStore,
            LearningDocuments documents,
            ObjectMapper objectMapper,
            Clock clock) {
        this.battleMapper = battleMapper;
        this.battleEngine = battleEngine;
        this.stateValidator = stateValidator;
        this.reviewReceiptMapper = reviewReceiptMapper;
        this.rewardEventMapper = rewardEventMapper;
        this.userDataStore = userDataStore;
        this.documents = documents;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public BattleEnvelope create(
            String ownerId,
            String actionId,
            CreateBattleRequest request) {
        validateActionId(actionId);
        LockedUserData locked = userDataStore.lock(ownerId);
        String fingerprint = fingerprint("create", request);
        BattleEnvelope replayed = replay(ownerId, request.battleId(), actionId, fingerprint);
        if (replayed != null) {
            return replayed;
        }
        if (battleMapper.findBattle(ownerId, request.battleId()) != null) {
            throw conflict("BATTLE_ID_CONFLICT", "战斗 ID 已用于其他创建请求");
        }
        validateLearningTask(locked.document(), request.sessionId(), request.studyItemId());
        long now = clock.instant().toEpochMilli();
        String taskId = UUID.randomUUID().toString();
        BattleTransition transition;
        try {
            transition = battleEngine.create(new BattleEngine.CreateBattleCommand(
                    request.battleId(),
                    request.encounterId(),
                    taskId,
                    request.sessionId(),
                    request.studyItemId(),
                    request.companionKey(),
                    request.seed(),
                    !Boolean.FALSE.equals(request.shuffle())));
        } catch (BattleEngine.BattleRuleException exception) {
            throw ruleViolation(exception);
        }
        battleMapper.insertBattle(
                ownerId,
                request.battleId(),
                transition.state().battleRulesVersion(),
                transition.state().stateVersion(),
                transition.state().phase(),
                transition.state().turnId(),
                1 + transition.events().size(),
                objectMapper.writeValueAsString(transition.state()),
                now,
                now);
        try {
            battleMapper.insertLearningTask(
                    ownerId,
                    request.battleId(),
                    1,
                    taskId,
                    request.sessionId(),
                    request.studyItemId(),
                    now,
                    now);
        } catch (DuplicateKeyException exception) {
            throw conflict(
                    "BATTLE_LEARNING_TASK_CONFLICT",
                    "该学习任务已绑定其他战斗");
        }
        List<ObjectNode> events = persistEvents(
                ownerId, request.battleId(), 1, transition.state().stateVersion(),
                transition.events(), now);
        BattleEnvelope response = new BattleEnvelope(
                stateNode(transition.state()), events, transition.state().stateVersion());
        insertReceipt(
                ownerId, request.battleId(), actionId, fingerprint, response,
                transition.state().stateVersion(), now);
        return response;
    }

    public BattleEnvelope get(String ownerId, String battleId) {
        BattleRow row = requireBattle(ownerId, battleId);
        return new BattleEnvelope(
                readStateNode(row),
                readEvents(ownerId, battleId),
                row.getStateVersion());
    }

    @Transactional
    public BattleEnvelope claimLearning(
            String ownerId,
            String battleId,
            String actionId,
            LearningClaimRequest request) {
        validateActionId(actionId);
        String fingerprint = fingerprint("learning-claim", battleId, request);
        BattleRow row = lockBattle(ownerId, battleId);
        BattleEnvelope replayed = replay(ownerId, battleId, actionId, fingerprint);
        if (replayed != null) {
            return replayed;
        }
        requireVersion(row, request.expectedStateVersion());
        BattleState state = readState(row);
        BattleState.LearningTask task = state.learningTask();
        if (task == null || task.energyGranted()) {
            throw conflict("LEARNING_ALREADY_CLAIMED", "本回合学习任务已经授能");
        }
        BattleLearningTaskRow taskRow = battleMapper.findLearningTask(
                ownerId, battleId, state.turnId());
        if (taskRow == null
                || !task.taskId().equals(taskRow.getTaskId())
                || taskRow.isEnergyGranted()) {
            throw conflict("LEARNING_TASK_MISMATCH", "战斗学习任务记录不一致");
        }
        verifyReviewReceipt(ownerId, task, request.submissionId());
        long now = clock.instant().toEpochMilli();
        BattleTransition transition;
        try {
            transition = battleEngine.claimLearning(
                    state, request.expectedStateVersion(), request.submissionId());
        } catch (BattleEngine.BattleRuleException exception) {
            throw ruleViolation(exception);
        }
        if (battleMapper.grantLearningEnergy(
                ownerId,
                battleId,
                state.turnId(),
                task.taskId(),
                request.submissionId(),
                now) != 1) {
            throw conflict("LEARNING_ALREADY_CLAIMED", "本回合学习任务已经授能");
        }
        ObjectNode rewardPayload = objectMapper.createObjectNode();
        rewardPayload.put("battleId", battleId);
        rewardPayload.put("taskId", task.taskId());
        rewardPayload.put("turnId", state.turnId());
        rewardPayload.put("submissionId", request.submissionId());
        rewardPayload.put("energy", BattleCatalog.BASE_ENERGY);
        try {
            rewardEventMapper.insert(
                    ownerId,
                    "battle-energy",
                    request.submissionId(),
                    true,
                    now,
                    objectMapper.writeValueAsString(rewardPayload));
        } catch (DuplicateKeyException exception) {
            throw conflict(
                    "BATTLE_ENERGY_CONFLICT",
                    "该正式复习已用于其他战斗授能");
        }
        return persistTransition(
                ownerId, battleId, actionId, fingerprint, row, transition, now);
    }

    @Transactional
    public BattleEnvelope playCard(
            String ownerId,
            String battleId,
            String actionId,
            PlayCardRequest request) {
        validateActionId(actionId);
        String fingerprint = fingerprint("play-card", battleId, request);
        BattleRow row = lockBattle(ownerId, battleId);
        BattleEnvelope replayed = replay(ownerId, battleId, actionId, fingerprint);
        if (replayed != null) {
            return replayed;
        }
        requireVersion(row, request.expectedStateVersion());
        BattleState state = readState(row);
        BattleTransition transition;
        try {
            transition = battleEngine.playCard(
                    state,
                    request.expectedStateVersion(),
                    request.cardInstanceId(),
                    request.targetId());
        } catch (BattleEngine.BattleRuleException exception) {
            throw ruleViolation(exception);
        }
        return persistTransition(
                ownerId,
                battleId,
                actionId,
                fingerprint,
                row,
                transition,
                clock.instant().toEpochMilli());
    }

    @Transactional
    public BattleEnvelope useCompanionSkill(
            String ownerId,
            String battleId,
            String actionId,
            CompanionSkillRequest request) {
        validateActionId(actionId);
        String fingerprint = fingerprint("companion-skill", battleId, request);
        BattleRow row = lockBattle(ownerId, battleId);
        BattleEnvelope replayed = replay(ownerId, battleId, actionId, fingerprint);
        if (replayed != null) {
            return replayed;
        }
        requireVersion(row, request.expectedStateVersion());
        BattleTransition transition;
        try {
            transition = battleEngine.useCompanionSkill(
                    readState(row), request.expectedStateVersion());
        } catch (BattleEngine.BattleRuleException exception) {
            throw ruleViolation(exception);
        }
        return persistTransition(
                ownerId,
                battleId,
                actionId,
                fingerprint,
                row,
                transition,
                clock.instant().toEpochMilli());
    }

    @Transactional
    public BattleEnvelope endTurn(
            String ownerId,
            String battleId,
            String actionId,
            EndTurnRequest request) {
        validateActionId(actionId);
        String fingerprint = fingerprint("end-turn", battleId, request);
        BattleRow row = lockBattle(ownerId, battleId);
        BattleEnvelope replayed = replay(ownerId, battleId, actionId, fingerprint);
        if (replayed != null) {
            return replayed;
        }
        requireVersion(row, request.expectedStateVersion());
        String nextSessionId = request.nextSessionId();
        String nextStudyItemId = request.nextStudyItemId();
        if ((nextSessionId == null) != (nextStudyItemId == null)) {
            throw JsonSupport.validation("下一学习任务必须同时提供会话和学习目标");
        }
        BattleEngine.NextLearningTask nextTask = null;
        if (nextSessionId != null && nextStudyItemId != null) {
            validateLearningTask(ownerId, nextSessionId, nextStudyItemId);
            nextTask = new BattleEngine.NextLearningTask(
                    UUID.randomUUID().toString(), nextSessionId, nextStudyItemId);
        }
        BattleState current = readState(row);
        BattleTransition transition;
        try {
            transition = battleEngine.endTurn(
                    current, request.expectedStateVersion(), nextTask);
        } catch (BattleEngine.BattleRuleException exception) {
            throw ruleViolation(exception);
        }
        long now = clock.instant().toEpochMilli();
        if (transition.state().turnId() > current.turnId()
                && transition.state().learningTask() != null
                && nextTask != null) {
            try {
                battleMapper.insertLearningTask(
                        ownerId,
                        battleId,
                        transition.state().turnId(),
                        nextTask.taskId(),
                        nextTask.sessionId(),
                        nextTask.studyItemId(),
                        now,
                        now);
            } catch (DuplicateKeyException exception) {
                throw conflict(
                        "BATTLE_LEARNING_TASK_CONFLICT",
                        "下一学习任务已绑定其他战斗");
            }
        }
        return persistTransition(
                ownerId, battleId, actionId, fingerprint, row, transition, now);
    }

    private BattleEnvelope persistTransition(
            String ownerId,
            String battleId,
            String actionId,
            String fingerprint,
            BattleRow row,
            BattleTransition transition,
            long now) {
        int nextEventSequence = row.getNextEventSequence();
        int updated = battleMapper.updateBattle(
                ownerId,
                battleId,
                row.getStateVersion(),
                transition.state().stateVersion(),
                transition.state().phase(),
                transition.state().turnId(),
                nextEventSequence + transition.events().size(),
                objectMapper.writeValueAsString(transition.state()),
                now);
        if (updated != 1) {
            throw versionConflict();
        }
        List<ObjectNode> events = persistEvents(
                ownerId,
                battleId,
                nextEventSequence,
                transition.state().stateVersion(),
                transition.events(),
                now);
        BattleEnvelope response = new BattleEnvelope(
                stateNode(transition.state()), events, transition.state().stateVersion());
        insertReceipt(
                ownerId,
                battleId,
                actionId,
                fingerprint,
                response,
                transition.state().stateVersion(),
                now);
        return response;
    }

    private BattleEnvelope replay(
            String ownerId,
            String battleId,
            String actionId,
            String fingerprint) {
        BattleReceiptRow receipt = battleMapper.findReceipt(ownerId, battleId, actionId);
        if (receipt == null) {
            return null;
        }
        if (!fingerprint.equals(receipt.getRequestFingerprint())) {
            throw conflict("BATTLE_ACTION_CONFLICT", "操作 ID 已用于不同战斗命令");
        }
        return readEnvelope(receipt.getResponseJson());
    }

    private void insertReceipt(
            String ownerId,
            String battleId,
            String actionId,
            String fingerprint,
            BattleEnvelope response,
            long stateVersion,
            long now) {
        battleMapper.insertReceipt(
                ownerId,
                battleId,
                actionId,
                fingerprint,
                objectMapper.writeValueAsString(response),
                stateVersion,
                now);
    }

    private void verifyReviewReceipt(
            String ownerId,
            BattleState.LearningTask task,
            String submissionId) {
        ReviewReceiptRow receipt = reviewReceiptMapper.find(ownerId, submissionId);
        if (receipt == null) {
            throw conflict(
                    "LEARNING_RECEIPT_NOT_FOUND",
                    "未找到已提交的正式复习回执");
        }
        ObjectNode response = JsonSupport.copyObject(
                objectMapper.readTree(receipt.getResponseJson()), "review receipt");
        JsonNode log = response.path("log");
        if (!submissionId.equals(log.path("submissionId").asText())
                || !task.sessionId().equals(log.path("sessionId").asText())
                || !task.studyItemId().equals(log.path("studyItemId").asText())
                || !response.path("rewardEligible").asBoolean(false)) {
            throw conflict(
                    "LEARNING_RECEIPT_MISMATCH",
                    "正式复习回执与战斗学习任务不一致");
        }
    }

    private void validateLearningTask(
            String ownerId,
            String sessionId,
            String studyItemId) {
        LockedUserData locked = userDataStore.lock(ownerId);
        validateLearningTask(locked.document(), sessionId, studyItemId);
    }

    private void validateLearningTask(
            ObjectNode document,
            String sessionId,
            String studyItemId) {
        ObjectNode session = documents.requireSession(document, sessionId);
        if (!"active".equals(session.path("status").asText())
                || !studyItemId.equals(session.path("currentStudyItemId").asText(null))
                || !session.path("pendingAnswer").isNull()) {
            throw conflict(
                    "BATTLE_LEARNING_TASK_MISMATCH",
                    "战斗只能绑定会话当前且未作答的学习目标");
        }
        documents.requireStudyItem(document, studyItemId);
    }

    private BattleRow requireBattle(String ownerId, String battleId) {
        BattleRow row = battleMapper.findBattle(ownerId, battleId);
        if (row == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "BATTLE_NOT_FOUND", "战斗不存在");
        }
        if (!BattleCatalog.RULES_VERSION.equals(row.getRulesVersion())) {
            throw conflict("BATTLE_RULES_UNSUPPORTED", "战斗规则版本不受支持");
        }
        return row;
    }

    private BattleRow lockBattle(String ownerId, String battleId) {
        BattleRow row = battleMapper.lockBattle(ownerId, battleId);
        if (row == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "BATTLE_NOT_FOUND", "战斗不存在");
        }
        if (!BattleCatalog.RULES_VERSION.equals(row.getRulesVersion())) {
            throw conflict("BATTLE_RULES_UNSUPPORTED", "战斗规则版本不受支持");
        }
        return row;
    }

    private void requireVersion(BattleRow row, long expectedVersion) {
        if (row.getStateVersion() != expectedVersion) {
            throw versionConflict();
        }
    }

    private BattleState readState(BattleRow row) {
        BattleState state = objectMapper.readValue(row.getStateJson(), BattleState.class);
        stateValidator.validate(state);
        return state;
    }

    private ObjectNode readStateNode(BattleRow row) {
        return JsonSupport.copyObject(
                objectMapper.readTree(row.getStateJson()), "battle state");
    }

    private ObjectNode stateNode(BattleState state) {
        return JsonSupport.copyObject(
                objectMapper.valueToTree(state), "battle state");
    }

    private List<ObjectNode> persistEvents(
            String ownerId,
            String battleId,
            int startSequence,
            long stateVersion,
            List<ObjectNode> events,
            long now) {
        List<ObjectNode> persisted = new ArrayList<>();
        for (int index = 0; index < events.size(); index += 1) {
            ObjectNode event = events.get(index).deepCopy();
            int sequence = startSequence + index;
            event.put("eventSequence", sequence);
            event.put("stateVersion", stateVersion);
            String type = event.path("type").asText();
            battleMapper.insertEvent(
                    ownerId,
                    battleId,
                    sequence,
                    stateVersion,
                    type,
                    objectMapper.writeValueAsString(event),
                    now);
            persisted.add(event);
        }
        return List.copyOf(persisted);
    }

    private List<ObjectNode> readEvents(String ownerId, String battleId) {
        return battleMapper.listEvents(ownerId, battleId).stream()
                .map(row -> JsonSupport.copyObject(
                        objectMapper.readTree(row.getEventJson()), "battle event"))
                .toList();
    }

    private BattleEnvelope readEnvelope(String responseJson) {
        ObjectNode response = JsonSupport.copyObject(
                objectMapper.readTree(responseJson), "battle receipt");
        ArrayNode eventArray = JsonSupport.requireArray(response, "events");
        return new BattleEnvelope(
                JsonSupport.copyObject(response.get("battle"), "battle response"),
                JsonSupport.objectValues(eventArray, "battle response events"),
                JsonSupport.requireLong(response, "stateVersion"));
    }

    private String fingerprint(String command, Object request) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("command", command);
        value.set("request", objectMapper.valueToTree(request));
        return JsonSupport.fingerprint(objectMapper, value);
    }

    private String fingerprint(String command, String battleId, Object request) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("command", command);
        value.put("battleId", battleId);
        value.set("request", objectMapper.valueToTree(request));
        return JsonSupport.fingerprint(objectMapper, value);
    }

    private void validateActionId(String actionId) {
        if (actionId == null || !ACTION_ID.matcher(actionId).matches()) {
            throw JsonSupport.validation("Idempotency-Key 必须为 3-128 位安全字符");
        }
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private ApiException versionConflict() {
        return conflict("BATTLE_VERSION_CONFLICT", "战斗状态已在其他请求中更新");
    }

    private ApiException ruleViolation(BattleEngine.BattleRuleException exception) {
        return conflict("BATTLE_RULE_VIOLATION", exception.getMessage());
    }
}
