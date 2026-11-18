package com.fingermemory.server.adventure;

import com.fingermemory.server.battle.BattleDtos;
import com.fingermemory.server.battle.BattleService;
import com.fingermemory.server.content.ContentInstallationRow;
import com.fingermemory.server.content.ContentPackageService;
import com.fingermemory.server.learning.LearningDtos;
import com.fingermemory.server.learning.SessionService;
import com.fingermemory.server.learning.UserDataStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class AdventureService {
    private static final Pattern ACTION_ID =
            Pattern.compile("^[A-Za-z0-9_.:-]{3,128}$");
    private static final long TUTORIAL_BATTLE_SEED = 42L;

    private final AdventureMapper adventureMapper;
    private final AdventurePackageReader packageReader;
    private final AdventureSessionImporter sessionImporter;
    private final ContentPackageService contentPackageService;
    private final BattleService battleService;
    private final SessionService sessionService;
    private final UserDataStore userDataStore;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdventureService(
            AdventureMapper adventureMapper,
            AdventurePackageReader packageReader,
            AdventureSessionImporter sessionImporter,
            ContentPackageService contentPackageService,
            BattleService battleService,
            SessionService sessionService,
            UserDataStore userDataStore,
            ObjectMapper objectMapper,
            Clock clock) {
        this.adventureMapper = adventureMapper;
        this.packageReader = packageReader;
        this.sessionImporter = sessionImporter;
        this.contentPackageService = contentPackageService;
        this.battleService = battleService;
        this.sessionService = sessionService;
        this.userDataStore = userDataStore;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public AdventureDtos.StartResult start(
            String ownerId,
            String actionId,
            AdventureDtos.StartRequest request) {
        validateActionId(actionId);
        userDataStore.lock(ownerId);
        ContentPackageService.InstalledPackage installed =
                contentPackageService.installedPackage(
                        ownerId,
                        request.installationId());
        AdventurePackageReader.AdventurePackage content =
                packageReader.read(installed.root());
        String fingerprint = fingerprint(
                "start",
                request.installationId(),
                request.companionKey(),
                request.reduceMotion());
        String deterministicRunId = UUID.nameUUIDFromBytes(
                ("adventure-start:" + ownerId + ":" + request.installationId()
                        + ":" + actionId).getBytes(StandardCharsets.UTF_8))
                .toString();
        AdventureReceiptRow receipt = adventureMapper.findReceipt(
                ownerId,
                deterministicRunId,
                actionId);
        if (receipt != null) {
            if (!fingerprint.equals(receipt.requestFingerprint())) {
                throw conflict(
                        "ADVENTURE_IDEMPOTENCY_CONFLICT",
                        "同一幂等键不能用于不同冒险启动请求");
            }
            return objectMapper.readValue(
                    receipt.responseJson(),
                    AdventureDtos.StartResult.class);
        }
        AdventureRunRow active = adventureMapper.findActive(
                ownerId,
                request.installationId());
        if (active != null) {
            return startResult(
                    "existing",
                    active,
                    installed,
                    content);
        }

        String sessionId = sessionImporter.importWordsAndCreateSession(
                ownerId,
                installed.installation(),
                content,
                request.reduceMotion());
        long now = clock.instant().toEpochMilli();
        AdventureRunRow row = new AdventureRunRow(
                ownerId,
                deterministicRunId,
                installed.installation().id(),
                installed.installation().namespaceId(),
                installed.installation().bookId(),
                installed.installation().contentVersion(),
                installed.installation().contentHash(),
                installed.installation().entryNodeId(),
                installed.installation().entryNodeId(),
                sessionId,
                request.companionKey(),
                "active",
                request.installationId(),
                0,
                "{}",
                null,
                now,
                now);
        try {
            adventureMapper.insertRun(row);
        } catch (DuplicateKeyException exception) {
            AdventureRunRow concurrent = adventureMapper.findActive(
                    ownerId,
                    request.installationId());
            if (concurrent != null) {
                return startResult(
                        "existing",
                        concurrent,
                        installed,
                        content);
            }
            throw exception;
        }
        adventureMapper.insertEvent(
                ownerId,
                row.id(),
                0,
                "run-started",
                row.currentNodeId(),
                objectMapper.writeValueAsString(Map.of(
                        "installationId", row.installationId(),
                        "contentVersion", row.contentVersion())),
                now);
        AdventureDtos.StartResult result = startResult(
                "created",
                row,
                installed,
                content);
        adventureMapper.insertReceipt(new AdventureReceiptRow(
                ownerId,
                row.id(),
                actionId,
                fingerprint,
                objectMapper.writeValueAsString(result),
                0,
                now));
        return result;
    }

    public AdventureDtos.Run get(String ownerId, String runId) {
        AdventureRunRow row = requireRun(ownerId, runId);
        ContentPackageService.InstalledPackage installed =
                contentPackageService.installedPackage(
                        ownerId,
                        row.installationId());
        return toView(row, installed, packageReader.read(installed.root()));
    }

    public List<AdventureDtos.Run> listActive(String ownerId, int limit) {
        if (limit < 1 || limit > 50) {
            throw validation("limit 必须在 1 到 50 之间");
        }
        return adventureMapper.listActive(ownerId, limit).stream()
                .map(row -> {
                    ContentPackageService.InstalledPackage installed =
                            contentPackageService.installedPackage(
                                    ownerId,
                                    row.installationId());
                    return toView(
                            row,
                            installed,
                            packageReader.read(installed.root()));
                })
                .toList();
    }

    @Transactional
    public AdventureDtos.Encounter startEncounter(
            String ownerId,
            String runId,
            String actionId) {
        validateActionId(actionId);
        AdventureRunRow row = adventureMapper.findForUpdate(ownerId, runId);
        if (row == null) {
            throw notFound();
        }
        String fingerprint = fingerprint("start-encounter", runId, row.currentNodeId());
        AdventureReceiptRow receipt =
                adventureMapper.findReceipt(ownerId, runId, actionId);
        if (receipt != null) {
            if (!fingerprint.equals(receipt.requestFingerprint())) {
                throw conflict(
                        "ADVENTURE_IDEMPOTENCY_CONFLICT",
                        "同一幂等键不能用于不同冒险请求");
            }
            return objectMapper.readValue(
                    receipt.responseJson(),
                    AdventureDtos.Encounter.class);
        }
        if (row.battleId() != null) {
            return new AdventureDtos.Encounter(
                    "existing",
                    row.battleId(),
                    runId,
                    row.runVersion());
        }
        ContentPackageService.InstalledPackage installed =
                contentPackageService.installedPackage(
                        ownerId,
                        row.installationId());
        AdventurePackageReader.AdventureNode node = packageReader
                .read(installed.root())
                .nodes()
                .get(row.currentNodeId());
        if (node == null
                || !"encounter".equals(node.type())
                || node.encounterId() == null) {
            throw conflict(
                    "ADVENTURE_NOT_IN_ENCOUNTER",
                    "当前剧情节点不是遭遇");
        }
        LearningDtos.SessionEnvelope session =
                sessionService.get(ownerId, row.sessionId());
        if (session.session().path("currentStudyItemId").isNull()) {
            throw conflict(
                    "ADVENTURE_LEARNING_QUEUE_EMPTY",
                    "当前冒险没有可用的学习目标");
        }
        String battleId = UUID.nameUUIDFromBytes(
                ("adventure-battle:" + ownerId + ":" + runId + ":"
                        + row.currentNodeId()).getBytes(StandardCharsets.UTF_8))
                .toString();
        String battleCreateActionId = UUID.nameUUIDFromBytes(
                ("adventure-battle-create:" + ownerId + ":" + runId + ":"
                        + row.currentNodeId() + ":" + actionId)
                        .getBytes(StandardCharsets.UTF_8))
                .toString();
        BattleDtos.BattleEnvelope battle = battleService.create(
                ownerId,
                battleCreateActionId,
                new BattleDtos.CreateBattleRequest(
                        battleId,
                        node.encounterId(),
                        row.sessionId(),
                        session.session().path("currentStudyItemId").asText(),
                        row.companionKey(),
                        TUTORIAL_BATTLE_SEED,
                        false));
        long now = clock.instant().toEpochMilli();
        if (adventureMapper.bindBattle(
                ownerId,
                runId,
                row.runVersion(),
                battle.battle().path("battleId").asText(),
                now) != 1) {
            throw versionConflict();
        }
        adventureMapper.insertBattleBinding(new AdventureBattleBindingRow(
                ownerId,
                runId,
                row.currentNodeId(),
                node.encounterId(),
                battle.battle().path("battleId").asText(),
                now));
        long nextVersion = row.runVersion() + 1;
        AdventureDtos.Encounter response = new AdventureDtos.Encounter(
                "started",
                battle.battle().path("battleId").asText(),
                runId,
                nextVersion);
        adventureMapper.insertEvent(
                ownerId,
                runId,
                nextVersion,
                "encounter-started",
                row.currentNodeId(),
                objectMapper.writeValueAsString(response),
                now);
        adventureMapper.insertReceipt(new AdventureReceiptRow(
                ownerId,
                runId,
                actionId,
                fingerprint,
                objectMapper.writeValueAsString(response),
                nextVersion,
                now));
        return response;
    }

    @Transactional
    public AdventureDtos.ActionResult act(
            String ownerId,
            String runId,
            String actionId,
            AdventureDtos.ActionRequest request) {
        validateActionId(actionId);
        AdventureRunRow row = adventureMapper.findForUpdate(ownerId, runId);
        if (row == null) {
            throw notFound();
        }
        String fingerprint = fingerprint(
                "action",
                runId,
                request.expectedVersion(),
                request.action(),
                request.choiceId(),
                request.battleId());
        AdventureReceiptRow receipt =
                adventureMapper.findReceipt(ownerId, runId, actionId);
        if (receipt != null) {
            if (!fingerprint.equals(receipt.requestFingerprint())) {
                throw conflict(
                        "ADVENTURE_IDEMPOTENCY_CONFLICT",
                        "同一幂等键不能用于不同冒险请求");
            }
            return objectMapper.readValue(
                    receipt.responseJson(),
                    AdventureDtos.ActionResult.class);
        }
        if (request.expectedVersion() != row.runVersion()) {
            throw versionConflict();
        }
        ContentPackageService.InstalledPackage installed =
                contentPackageService.installedPackage(
                        ownerId,
                        row.installationId());
        AdventurePackageReader.AdventurePackage content =
                packageReader.read(installed.root());
        AdventurePackageReader.AdventureNode node =
                requireNode(content, row.currentNodeId());
        Map<String, Boolean> flags = readFlags(row.flagsJson());
        String nextNodeId;
        String status = "active";
        String battleId = row.battleId();
        switch (request.action()) {
            case "advance" -> nextNodeId = advanceTarget(node);
            case "choose" -> nextNodeId = choiceTarget(node, request.choiceId());
            case "resolve-encounter" -> {
                AdventureBattleBindingRow binding =
                        requireEncounterResolution(
                                ownerId,
                                row,
                                request.battleId(),
                                node);
                BattleDtos.BattleEnvelope battle =
                        battleService.get(ownerId, request.battleId());
                String phase = battle.battle().path("phase").asText();
                if (!"victory".equals(phase) && !"defeat".equals(phase)) {
                    throw conflict(
                            "ADVENTURE_ENCOUNTER_UNRESOLVED",
                            "战斗尚未结束");
                }
                if ("victory".equals(phase)) {
                    nextNodeId = node.victoryNodeId() != null
                            ? node.victoryNodeId()
                            : node.nextNodeId();
                    flags.put(
                            "encounter." + binding.encounterId() + ".resolved",
                            true);
                } else {
                    nextNodeId = node.rescueNodeId() != null
                            ? node.rescueNodeId()
                            : node.nextNodeId();
                    flags.put(
                            "encounter." + binding.encounterId() + ".assisted",
                            true);
                }
                battleId = null;
            }
            case "complete" -> {
                if (!"chapter-end".equals(node.type())) {
                    throw conflict(
                            "ADVENTURE_NOT_COMPLETE",
                            "当前节点不能完成章节");
                }
                nextNodeId = node.nodeId();
                status = "completed";
            }
            default -> throw validation("不支持的冒险动作");
        }
        if (nextNodeId == null || !content.nodes().containsKey(nextNodeId)) {
            throw conflict(
                    "ADVENTURE_TRANSITION_INVALID",
                    "当前节点没有可用的后续节点");
        }
        long now = clock.instant().toEpochMilli();
        long nextVersion = row.runVersion() + 1;
        AdventureRunRow next = new AdventureRunRow(
                row.ownerId(),
                row.id(),
                row.installationId(),
                row.namespaceId(),
                row.bookId(),
                row.contentVersion(),
                row.contentHash(),
                row.entryNodeId(),
                nextNodeId,
                row.sessionId(),
                row.companionKey(),
                status,
                "completed".equals(status) ? null : row.activeSlot(),
                nextVersion,
                objectMapper.writeValueAsString(flags),
                battleId,
                row.createdAtMs(),
                now);
        if (adventureMapper.updateRun(
                next,
                ownerId,
                runId,
                row.runVersion()) != 1) {
            throw versionConflict();
        }
        AdventureDtos.ActionResult response = new AdventureDtos.ActionResult(
                "applied",
                toView(next, installed, content));
        adventureMapper.insertEvent(
                ownerId,
                runId,
                nextVersion,
                "adventure-" + request.action(),
                nextNodeId,
                objectMapper.writeValueAsString(Map.of(
                        "from", row.currentNodeId(),
                        "to", nextNodeId,
                        "status", status)),
                now);
        adventureMapper.insertReceipt(new AdventureReceiptRow(
                ownerId,
                runId,
                actionId,
                fingerprint,
                objectMapper.writeValueAsString(response),
                nextVersion,
                now));
        return response;
    }

    private AdventureBattleBindingRow requireEncounterResolution(
            String ownerId,
            AdventureRunRow run,
            String battleId,
            AdventurePackageReader.AdventureNode node) {
        if (battleId == null
                || !battleId.equals(run.battleId())) {
            throw conflict(
                    "ADVENTURE_BATTLE_MISMATCH",
                    "战斗不属于当前冒险节点");
        }
        AdventureBattleBindingRow binding =
                adventureMapper.findBattleBinding(ownerId, battleId);
        if (binding == null
                || !run.id().equals(binding.runId())
                || !run.currentNodeId().equals(binding.nodeId())
                || !binding.encounterId().equals(node.encounterId())) {
            throw conflict(
                    "ADVENTURE_BATTLE_MISMATCH",
                    "战斗不属于当前冒险节点");
        }
        return binding;
    }

    private String advanceTarget(AdventurePackageReader.AdventureNode node) {
        if ("encounter".equals(node.type())) {
            throw conflict(
                    "ADVENTURE_ENCOUNTER_PENDING",
                    "需要先完成当前遭遇");
        }
        if ("chapter-end".equals(node.type()) || node.nextNodeId() == null) {
            throw conflict(
                    "ADVENTURE_ACTION_REQUIRED",
                    "当前节点需要选择或完成");
        }
        return node.nextNodeId();
    }

    private String choiceTarget(
            AdventurePackageReader.AdventureNode node,
            String choiceId) {
        if (choiceId == null) {
            throw validation("缺少 choiceId");
        }
        return node.choices().stream()
                .filter(choice -> choiceId.equals(choice.choiceId()))
                .findFirst()
                .map(AdventurePackageReader.AdventureChoice::targetNodeId)
                .orElseThrow(() -> conflict(
                        "ADVENTURE_CHOICE_INVALID",
                        "当前节点没有该选项"));
    }

    private AdventureDtos.StartResult startResult(
            String outcome,
            AdventureRunRow row,
            ContentPackageService.InstalledPackage installed,
            AdventurePackageReader.AdventurePackage content) {
        return new AdventureDtos.StartResult(
                outcome,
                toView(row, installed, content));
    }

    private AdventureDtos.Run toView(
            AdventureRunRow row,
            ContentPackageService.InstalledPackage installed,
            AdventurePackageReader.AdventurePackage content) {
        AdventurePackageReader.AdventureNode node =
                requireNode(content, row.currentNodeId());
        ContentInstallationRow installation = installed.installation();
        return new AdventureDtos.Run(
                row.id(),
                row.installationId(),
                row.namespaceId(),
                row.bookId(),
                row.contentVersion(),
                row.contentHash(),
                installation.title(),
                row.companionKey(),
                row.status(),
                row.runVersion(),
                row.sessionId(),
                row.battleId(),
                new AdventureDtos.Node(
                        node.nodeId(),
                        node.chapterId(),
                        node.type(),
                        node.title(),
                        node.speaker(),
                        node.text(),
                        node.encounterId(),
                        node.choices().stream()
                                .map(choice -> new AdventureDtos.Choice(
                                        choice.choiceId(),
                                        choice.label()))
                                .toList()),
                readFlags(row.flagsJson()),
                row.createdAtMs(),
                row.updatedAtMs());
    }

    private AdventurePackageReader.AdventureNode requireNode(
            AdventurePackageReader.AdventurePackage content,
            String nodeId) {
        AdventurePackageReader.AdventureNode node = content.nodes().get(nodeId);
        if (node == null) {
            throw new AdventureException(
                    HttpStatus.CONFLICT,
                    "ADVENTURE_NODE_UNAVAILABLE",
                    "固定内容版本缺少当前剧情节点");
        }
        return node;
    }

    private AdventureRunRow requireRun(String ownerId, String runId) {
        AdventureRunRow row = adventureMapper.findById(ownerId, runId);
        if (row == null) {
            throw notFound();
        }
        return row;
    }

    private Map<String, Boolean> readFlags(String raw) {
        try {
            Map<String, Boolean> flags = new HashMap<>();
            objectMapper.readTree(raw).properties().forEach(entry ->
                    flags.put(entry.getKey(), entry.getValue().asBoolean(false)));
            return flags;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Stored adventure flags are invalid", exception);
        }
    }

    private static void validateActionId(String actionId) {
        if (actionId == null || !ACTION_ID.matcher(actionId).matches()) {
            throw validation("Idempotency-Key 格式无效");
        }
    }

    private static String fingerprint(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static AdventureException validation(String message) {
        return new AdventureException(
                HttpStatus.BAD_REQUEST,
                "ADVENTURE_VALIDATION_FAILED",
                message);
    }

    private static AdventureException conflict(String code, String message) {
        return new AdventureException(HttpStatus.CONFLICT, code, message);
    }

    private static AdventureException versionConflict() {
        return conflict(
                "ADVENTURE_VERSION_CONFLICT",
                "冒险状态已在其他位置更新，请刷新后重试");
    }

    private static AdventureException notFound() {
        return new AdventureException(
                HttpStatus.NOT_FOUND,
                "ADVENTURE_NOT_FOUND",
                "冒险存档不存在");
    }
}
