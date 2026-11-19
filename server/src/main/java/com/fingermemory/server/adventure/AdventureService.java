package com.fingermemory.server.adventure;

import com.fingermemory.server.battle.BattleDtos;
import com.fingermemory.server.battle.BattleDeckCatalog;
import com.fingermemory.server.battle.BattleEngine;
import com.fingermemory.server.battle.BattleService;
import com.fingermemory.server.content.ContentInstallationRow;
import com.fingermemory.server.content.ContentPackageService;
import com.fingermemory.server.content.ContentRequirement;
import com.fingermemory.server.content.ContentVersions;
import com.fingermemory.server.learning.LearningDtos;
import com.fingermemory.server.learning.SessionService;
import com.fingermemory.server.learning.UserDataStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final Set<String> BUILTIN_COMPANIONS =
            Set.of("lia", "serin", "fina");
    private static final long TUTORIAL_BATTLE_SEED = 42L;

    private final AdventureMapper adventureMapper;
    private final AdventureProgressMapper progressMapper;
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
            AdventureProgressMapper progressMapper,
            AdventurePackageReader packageReader,
            AdventureSessionImporter sessionImporter,
            ContentPackageService contentPackageService,
            BattleService battleService,
            SessionService sessionService,
            UserDataStore userDataStore,
            ObjectMapper objectMapper,
            Clock clock) {
        this.adventureMapper = adventureMapper;
        this.progressMapper = progressMapper;
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
        requireCompanionAvailable(ownerId, request.companionKey(), content);
        AdventureCompletionRow sourceCompletion = requireStartCompletion(
                ownerId,
                request.sourceCompletionId(),
                content);
        String fingerprint = fingerprint(
                "start",
                request.installationId(),
                request.companionKey(),
                request.sourceCompletionId(),
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
        Map<String, Boolean> initialFlags = sourceCompletion == null
                ? new HashMap<>()
                : readFlags(sourceCompletion.inheritedFlagsJson());
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
                request.sourceCompletionId(),
                0,
                objectMapper.writeValueAsString(initialFlags),
                objectMapper.writeValueAsString(BattleDeckCatalog.teachingDeck()),
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
        var startedEvent = objectMapper.createObjectNode();
        startedEvent.put("installationId", row.installationId());
        startedEvent.put("contentVersion", row.contentVersion());
        if (row.sourceCompletionId() != null) {
            startedEvent.put("sourceCompletionId", row.sourceCompletionId());
        }
        adventureMapper.insertEvent(
                ownerId,
                row.id(),
                0,
                "run-started",
                row.currentNodeId(),
                objectMapper.writeValueAsString(startedEvent),
                now);
        applyNodeProgress(ownerId, row, requireNode(content, row.currentNodeId()), now);
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

    public List<AdventureDtos.Completion> listCompletions(
            String ownerId,
            int limit) {
        if (limit < 1 || limit > 100) {
            throw validation("limit 必须在 1 到 100 之间");
        }
        return adventureMapper.listCompletions(ownerId, limit).stream()
                .map(this::toCompletionView)
                .toList();
    }

    @Transactional
    public AdventureDtos.UpgradeResult upgrade(
            String ownerId,
            String runId,
            String actionId,
            AdventureDtos.UpgradeRequest request) {
        validateActionId(actionId);
        AdventureRunRow row = adventureMapper.findForUpdate(ownerId, runId);
        if (row == null) {
            throw notFound();
        }
        String targetInstallationId = request.installationId();
        String fingerprint = fingerprint("upgrade", runId, targetInstallationId);
        AdventureReceiptRow existingReceipt =
                adventureMapper.findReceipt(ownerId, runId, actionId);
        if (existingReceipt != null) {
            if (!fingerprint.equals(existingReceipt.requestFingerprint())) {
                throw conflict(
                        "ADVENTURE_IDEMPOTENCY_CONFLICT",
                        "同一幂等键不能用于不同冒险请求");
            }
            return objectMapper.readValue(
                    existingReceipt.responseJson(),
                    AdventureDtos.UpgradeResult.class);
        }
        if (!"completed".equals(row.status())) {
            throw conflict(
                    "ADVENTURE_UPGRADE_UNSAFE",
                    "只能从已通关的安全检查点开启新增章节");
        }
        ContentPackageService.InstalledPackage target =
                contentPackageService.installedPackage(ownerId, targetInstallationId);
        AdventurePackageReader.AdventurePackage content =
                packageReader.read(target.root());
        if (!row.namespaceId().equals(target.installation().namespaceId())
                || !row.bookId().equals(target.installation().bookId())) {
            throw conflict(
                    "ADVENTURE_UPGRADE_BOOK_MISMATCH",
                    "新增章节必须来自同一本书");
        }
        if (ContentVersions.compare(
                target.installation().contentVersion(),
                row.contentVersion()) <= 0) {
            throw conflict(
                    "ADVENTURE_UPGRADE_VERSION_INVALID",
                    "目标内容版本必须高于旧 run 固定版本");
        }
        AdventurePackageReader.AdventureNode node = content.nodes().get(row.currentNodeId());
        if (node == null) {
            throw conflict(
                    "ADVENTURE_UPGRADE_CHECKPOINT_MISSING",
                    "新版本缺少旧 run 的检查点，未执行升级");
        }
        if (!"chapter-end".equals(node.type()) && !"camp".equals(node.type())) {
            throw conflict(
                    "ADVENTURE_UPGRADE_UNSAFE",
                    "新版本检查点不是营地或章节结束，未执行升级");
        }
        if (node.nextNodeId() == null) {
            throw conflict(
                    "ADVENTURE_UPGRADE_NO_CHAPTER",
                    "新版本没有从该检查点开启的章节");
        }
        if (progressMapper.findPendingReward(ownerId, runId) != null) {
            throw conflict(
                    "ADVENTURE_REWARD_PENDING",
                    "需要先处理旧检查点的牌组奖励");
        }
        if (adventureMapper.findActive(ownerId, targetInstallationId) != null) {
            throw conflict(
                    "ADVENTURE_UPGRADE_ACTIVE_CONFLICT",
                    "该书的新版本已有进行中的冒险");
        }
        String sessionId = sessionImporter.importWordsAndCreateSession(
                ownerId,
                target.installation(),
                content,
                false);
        long now = clock.instant().toEpochMilli();
        long nextVersion = row.runVersion() + 1;
        AdventureRunRow upgraded = new AdventureRunRow(
                row.ownerId(),
                row.id(),
                target.installation().id(),
                target.installation().namespaceId(),
                target.installation().bookId(),
                target.installation().contentVersion(),
                target.installation().contentHash(),
                target.installation().entryNodeId(),
                row.currentNodeId(),
                sessionId,
                row.companionKey(),
                "active",
                targetInstallationId,
                row.sourceCompletionId(),
                nextVersion,
                row.flagsJson(),
                row.deckJson(),
                null,
                row.createdAtMs(),
                now);
        try {
            if (adventureMapper.updateRun(
                    upgraded,
                    ownerId,
                    runId,
                    row.runVersion()) != 1) {
                throw versionConflict();
            }
        } catch (DuplicateKeyException exception) {
            throw conflict(
                    "ADVENTURE_UPGRADE_ACTIVE_CONFLICT",
                    "该书的新版本已有进行中的冒险");
        }
        adventureMapper.insertEvent(
                ownerId,
                runId,
                nextVersion,
                "adventure-upgraded",
                row.currentNodeId(),
                objectMapper.writeValueAsString(Map.of(
                        "fromVersion", row.contentVersion(),
                        "toVersion", upgraded.contentVersion(),
                        "installationId", upgraded.installationId())),
                now);
        AdventureDtos.UpgradeResult response = new AdventureDtos.UpgradeResult(
                "upgraded",
                toView(upgraded, target, content));
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
        AdventurePackageReader.AdventurePackage content =
                packageReader.read(installed.root());
        AdventurePackageReader.AdventureNode node =
                content.nodes().get(row.currentNodeId());
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
        AdventurePackageReader.AdventureCharacter character =
                BUILTIN_COMPANIONS.contains(row.companionKey())
                        ? null
                        : content.characters().get(row.companionKey());
        BattleEngine.CompanionSkillDefinition companionSkill =
                character == null || character.skill() == null
                        ? null
                        : new BattleEngine.CompanionSkillDefinition(
                                character.skill().name(),
                                character.skill().description(),
                                character.skill().type(),
                                character.skill().amount());
        BattleDtos.BattleEnvelope battle = battleService.createAdventure(
                ownerId,
                battleCreateActionId,
                new BattleDtos.CreateBattleRequest(
                        battleId,
                        node.encounterId(),
                        row.sessionId(),
                        session.session().path("currentStudyItemId").asText(),
                        row.companionKey(),
                        TUTORIAL_BATTLE_SEED,
                        false,
                        readDeck(row.deckJson())),
                companionSkill);
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
                request.battleId(),
                request.companionKey());
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
        AdventureRewardOfferRow pendingReward =
                progressMapper.findPendingReward(ownerId, runId);
        if (pendingReward != null && !"claim-reward".equals(request.action())) {
            throw conflict(
                    "ADVENTURE_REWARD_PENDING",
                    "需要先选择本场遭遇的牌组奖励");
        }
        Map<String, Boolean> flags = readFlags(row.flagsJson());
        String nextNodeId;
        String status = "active";
        String battleId = row.battleId();
        String selectedCompanion = row.companionKey();
        String deckJson = row.deckJson();
        long now = clock.instant().toEpochMilli();
        switch (request.action()) {
            case "advance" -> nextNodeId = advanceTarget(node);
            case "choose" -> nextNodeId = choiceTarget(node, request.choiceId());
            case "select-companion" -> {
                if (!"camp".equals(node.type()) || request.companionKey() == null) {
                    throw conflict(
                            "ADVENTURE_COMPANION_SWITCH_UNAVAILABLE",
                            "只能在营地切换带队伙伴");
                }
                requireCompanionAvailable(ownerId, request.companionKey(), content);
                nextNodeId = node.nodeId();
                selectedCompanion = request.companionKey();
                battleId = null;
            }
            case "claim-reward" -> {
                if (pendingReward == null) {
                    throw conflict(
                            "ADVENTURE_REWARD_NOT_FOUND",
                            "当前没有待选择的牌组奖励");
                }
                AdventureDtos.RewardChoice choice = readRewardChoices(
                        pendingReward.candidatesJson()).stream()
                        .filter(candidate ->
                                candidate.choiceId().equals(request.choiceId()))
                        .findFirst()
                        .orElseThrow(() -> conflict(
                                "ADVENTURE_REWARD_INVALID",
                                "奖励选项不存在"));
                if (!BattleDeckCatalog.supports(choice.cardDefinitionId())) {
                    throw conflict(
                            "ADVENTURE_REWARD_UNSUPPORTED",
                            "奖励牌定义不受当前战斗规则支持");
                }
                List<String> deck = new ArrayList<>(readDeck(deckJson));
                deck.add(choice.cardDefinitionId());
                try {
                    BattleDeckCatalog.validateAdventureDeck(deck);
                } catch (BattleEngine.BattleRuleException exception) {
                    throw conflict(
                            "ADVENTURE_REWARD_DECK_INVALID",
                            "选择奖励后牌组超过规则限制");
                }
                if (progressMapper.claimReward(
                        ownerId,
                        runId,
                        pendingReward.offerId(),
                        choice.choiceId(),
                        choice.cardDefinitionId(),
                        now) != 1) {
                    throw conflict(
                            "ADVENTURE_REWARD_ALREADY_CLAIMED",
                            "牌组奖励已经被领取");
                }
                deckJson = objectMapper.writeValueAsString(deck);
                nextNodeId = pendingReward.targetNodeId();
                battleId = null;
                flags.put("reward." + pendingReward.offerId(), true);
            }
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
                    String victoryNodeId = node.victoryNodeId() != null
                            ? node.victoryNodeId()
                            : node.nextNodeId();
                    AdventureRewardOfferRow offer = createRewardOffer(
                            ownerId,
                            row,
                            node,
                            content,
                            now);
                    nextNodeId = offer == null ? victoryNodeId : node.nodeId();
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
                if (node.nextNodeId() == null) {
                    nextNodeId = node.nodeId();
                    status = "completed";
                } else {
                    nextNodeId = node.nextNodeId();
                }
            }
            default -> throw validation("不支持的冒险动作");
        }
        if (nextNodeId == null || !content.nodes().containsKey(nextNodeId)) {
            throw conflict(
                    "ADVENTURE_TRANSITION_INVALID",
                    "当前节点没有可用的后续节点");
        }
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
                selectedCompanion,
                status,
                "completed".equals(status) ? null : row.activeSlot(),
                row.sourceCompletionId(),
                nextVersion,
                objectMapper.writeValueAsString(flags),
                deckJson,
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
        applyNodeProgress(
                ownerId,
                next,
                requireNode(content, nextNodeId),
                now);
        if ("completed".equals(status)) {
            recordCompletion(ownerId, next, node, content, flags, now);
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

    public List<String> listUnlockedCompanions(String ownerId) {
        List<String> unlocked = new ArrayList<>(List.of("lia", "serin"));
        for (String characterKey : progressMapper.listUnlockedCharacters(ownerId)) {
            if (!unlocked.contains(characterKey)) {
                unlocked.add(characterKey);
            }
        }
        return List.copyOf(unlocked);
    }

    public List<AdventureDtos.Character> listCompanionDefinitions(String ownerId) {
        List<String> unlocked = listUnlockedCompanions(ownerId);
        Map<String, AdventureDtos.Character> characters = new HashMap<>();
        for (var installation : contentPackageService.listOwned(ownerId, 100)) {
            ContentPackageService.InstalledPackage installed =
                    contentPackageService.installedPackage(ownerId, installation.id());
            for (AdventurePackageReader.AdventureCharacter character
                    : packageReader.read(installed.root()).characters().values()) {
                if (!BUILTIN_COMPANIONS.contains(character.characterKey())
                        && unlocked.contains(character.characterKey())) {
                    characters.putIfAbsent(
                            character.characterKey(),
                            toCharacterView(installation.id(), character));
                }
            }
        }
        return List.copyOf(characters.values());
    }

    public List<AdventureDtos.Recollection> listRecollections(
            String ownerId,
            int limit) {
        if (limit < 1 || limit > 100) {
            throw validation("limit 必须在 1 到 100 之间");
        }
        return progressMapper.listRecollections(ownerId, limit).stream()
                .map(row -> new AdventureDtos.Recollection(
                        row.recollectionKey(),
                        row.namespaceId(),
                        row.bookId(),
                        row.chapterId(),
                        row.title(),
                        row.speaker(),
                        row.text(),
                        row.unlockedAtMs()))
                .toList();
    }

    private AdventureRewardOfferRow createRewardOffer(
            String ownerId,
            AdventureRunRow run,
            AdventurePackageReader.AdventureNode node,
            AdventurePackageReader.AdventurePackage content,
            long now) {
        if (run.deckJson() == null || readDeck(run.deckJson()).size() >= 18) {
            return null;
        }
        AdventurePackageReader.AdventureEncounter encounter =
                content.encounters().get(node.encounterId());
        if (encounter == null) {
            return null;
        }
        List<String> deck = readDeck(run.deckJson());
        List<AdventureDtos.RewardChoice> choices = new ArrayList<>();
        for (String cardDefinitionId : encounter.rewardCardIds()) {
            if (choices.size() >= 3 || !BattleDeckCatalog.supports(cardDefinitionId)) {
                continue;
            }
            long copies = deck.stream()
                    .filter(cardDefinitionId::equals)
                    .count();
            if (copies >= 3) {
                continue;
            }
            AdventurePackageReader.AdventureCard card =
                    content.cards().get(cardDefinitionId);
            String name = card == null
                    ? BattleDeckCatalog.displayName(cardDefinitionId)
                    : card.name();
            String description = card == null ? "" : card.description();
            choices.add(new AdventureDtos.RewardChoice(
                    cardDefinitionId,
                    cardDefinitionId,
                    name,
                    description));
        }
        if (choices.isEmpty()) {
            return null;
        }
        String offerId = UUID.nameUUIDFromBytes(
                ("adventure-reward:" + ownerId + ":" + run.id() + ":"
                        + node.nodeId()).getBytes(StandardCharsets.UTF_8))
                .toString();
        String targetNodeId = node.victoryNodeId() != null
                ? node.victoryNodeId()
                : node.nextNodeId();
        AdventureRewardOfferRow row = new AdventureRewardOfferRow(
                ownerId,
                run.id(),
                offerId,
                node.nodeId(),
                targetNodeId,
                objectMapper.writeValueAsString(choices),
                "pending",
                null,
                null,
                now,
                now);
        progressMapper.insertReward(row);
        return row;
    }

    private List<AdventureDtos.RewardChoice> readRewardChoices(String raw) {
        List<AdventureDtos.RewardChoice> choices = new ArrayList<>();
        for (var value : objectMapper.readTree(raw)) {
            choices.add(new AdventureDtos.RewardChoice(
                    value.path("choiceId").asText(),
                    value.path("cardDefinitionId").asText(),
                    value.path("name").asText(),
                    value.path("description").asText()));
        }
        return choices;
    }

    private List<String> readDeck(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>(BattleDeckCatalog.teachingDeck());
        }
        List<String> deck = new ArrayList<>();
        for (var value : objectMapper.readTree(raw)) {
            if (!value.isString()) {
                throw new IllegalStateException("Stored adventure deck is invalid");
            }
            deck.add(value.stringValue());
        }
        return deck;
    }

    private AdventureCompletionRow requireStartCompletion(
            String ownerId,
            String sourceCompletionId,
            AdventurePackageReader.AdventurePackage content) {
        List<ContentRequirement> requirements = content.requirements();
        if (requirements.isEmpty()) {
            if (sourceCompletionId != null) {
                throw conflict(
                        "ADVENTURE_SOURCE_UNEXPECTED",
                        "这本冒险不需要前作完成回执");
            }
            return null;
        }
        String requirementsText = requirements.stream()
                .map(requirement -> requirement.bookId() + "/" + requirement.endingId())
                .distinct()
                .reduce((left, right) -> left + " 或 " + right)
                .orElse("指定前作结局");
        if (sourceCompletionId == null) {
            throw conflict(
                    "ADVENTURE_REQUIREMENT_MISSING",
                    "需要先完成前作结局：" + requirementsText);
        }
        AdventureCompletionRow completion =
                adventureMapper.findCompletion(ownerId, sourceCompletionId);
        if (completion == null
                || requirements.stream().noneMatch(requirement ->
                        matchesRequirement(completion, requirement))) {
            throw conflict(
                    "ADVENTURE_REQUIREMENT_MISSING",
                    "所选完成回执不满足前作结局：" + requirementsText);
        }
        return completion;
    }

    private boolean matchesRequirement(
            AdventureCompletionRow completion,
            ContentRequirement requirement) {
        if (!requirement.bookId().equals(completion.bookId())
                || !requirement.endingId().equals(completion.endingId())) {
            return false;
        }
        if (requirement.minContentVersion() != null
                && ContentVersions.compare(
                        completion.contentVersion(),
                        requirement.minContentVersion()) < 0) {
            return false;
        }
        return requirement.maxContentVersionExclusive() == null
                || ContentVersions.compare(
                        completion.contentVersion(),
                        requirement.maxContentVersionExclusive()) < 0;
    }

    private void recordCompletion(
            String ownerId,
            AdventureRunRow run,
            AdventurePackageReader.AdventureNode node,
            AdventurePackageReader.AdventurePackage content,
            Map<String, Boolean> flags,
            long now) {
        Map<String, Boolean> inheritedFlags = new HashMap<>();
        for (String flag : node.inheritedFlags()) {
            if (flags.getOrDefault(flag, false)) {
                inheritedFlags.put(flag, true);
            }
        }
        String seriesId = content.seriesId().isBlank()
                ? run.bookId()
                : content.seriesId();
        String completionId = UUID.nameUUIDFromBytes(
                ("adventure-completion:" + ownerId + ":" + run.id() + ":"
                        + run.contentHash() + ":" + node.endingId())
                        .getBytes(StandardCharsets.UTF_8))
                .toString();
        AdventureCompletionRow row = new AdventureCompletionRow(
                ownerId,
                completionId,
                run.id(),
                run.namespaceId(),
                run.bookId(),
                seriesId,
                run.contentVersion(),
                run.contentHash(),
                node.endingId(),
                node.endingTitle() == null ? node.title() : node.endingTitle(),
                node.text(),
                node.nodeId(),
                objectMapper.writeValueAsString(inheritedFlags),
                now);
        if (adventureMapper.insertCompletion(row) == 0) {
            AdventureCompletionRow existing =
                    adventureMapper.findCompletion(ownerId, completionId);
            if (existing == null
                    || !existing.endingId().equals(row.endingId())) {
                throw conflict(
                        "ADVENTURE_COMPLETION_CONFLICT",
                        "该 run 与内容版本已经记录了不同结局");
            }
        }
    }

    private AdventureDtos.Completion toCompletionView(AdventureCompletionRow row) {
        return new AdventureDtos.Completion(
                row.id(),
                row.runId(),
                row.namespaceId(),
                row.bookId(),
                row.seriesId(),
                row.contentVersion(),
                row.contentHash(),
                row.endingId(),
                row.endingTitle(),
                row.endingText(),
                readFlags(row.inheritedFlagsJson()),
                row.completedAtMs());
    }

    private void requireCompanionAvailable(
            String ownerId,
            String companionKey,
            AdventurePackageReader.AdventurePackage content) {
        if ("lia".equals(companionKey) || "serin".equals(companionKey)) {
            return;
        }
        if ("fina".equals(companionKey)) {
            if (progressMapper.hasCharacter(ownerId, companionKey) > 0) {
                return;
            }
            throw conflict(
                    "ADVENTURE_COMPANION_LOCKED",
                    "该伙伴尚未在剧情中加入");
        }
        AdventurePackageReader.AdventureCharacter character =
                content.characters().get(companionKey);
        if (character == null || character.skill() == null
                || progressMapper.hasCharacter(ownerId, companionKey) == 0) {
            throw conflict(
                    "ADVENTURE_COMPANION_LOCKED",
                    "该伙伴尚未在剧情中加入");
        }
    }

    private void applyNodeProgress(
            String ownerId,
            AdventureRunRow run,
            AdventurePackageReader.AdventureNode node,
            long now) {
        if (node.unlockCharacterId() != null) {
            progressMapper.unlockCharacter(
                    ownerId,
                    node.unlockCharacterId(),
                    node.nodeId() + "." + node.unlockCharacterId(),
                    run.id(),
                    node.nodeId(),
                    now);
        }
        if (node.recollectionId() != null) {
            String recollectionKey = run.namespaceId()
                    + ":" + run.bookId()
                    + ":" + node.recollectionId();
            progressMapper.unlockRecollection(
                    ownerId,
                    recollectionKey,
                    run.namespaceId(),
                    run.bookId(),
                    node.chapterId(),
                    node.recollectionTitle(),
                    node.speaker(),
                    node.recollectionText(),
                    run.id(),
                    node.nodeId(),
                    now);
        }
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
        AdventureRewardOfferRow pendingReward =
                progressMapper.findPendingReward(row.ownerId(), row.id());
        AdventureDtos.RewardOffer rewardOffer = pendingReward == null
                ? null
                : new AdventureDtos.RewardOffer(
                        pendingReward.offerId(),
                        pendingReward.nodeId(),
                        readRewardChoices(pendingReward.candidatesJson()));
        List<AdventureDtos.Character> characters = content.characters().values().stream()
                .map(character -> toCharacterView(installation.id(), character))
                .toList();
        List<AdventureDtos.Scene> scenes = content.scenes().values().stream()
                .map(scene -> new AdventureDtos.Scene(
                        scene.sceneId(),
                        scene.name(),
                        scene.background(),
                        scene.battleTheme(),
                        scene.ambientKey()))
                .toList();
        return new AdventureDtos.Run(
                row.id(),
                row.installationId(),
                row.namespaceId(),
                row.bookId(),
                row.contentVersion(),
                row.contentHash(),
                installation.title(),
                content.seriesId(),
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
                        node.portraitCharacterId(),
                        node.sceneId(),
                        node.costumeId(),
                        node.encounterName(),
                        node.encounterDescription(),
                        node.choices().stream()
                                .map(choice -> new AdventureDtos.Choice(
                                        choice.choiceId(),
                                        choice.label()))
                                .toList()),
                readDeck(row.deckJson()),
                listUnlockedCompanions(row.ownerId()),
                characters,
                scenes,
                row.sourceCompletionId(),
                rewardOffer,
                readFlags(row.flagsJson()),
                row.createdAtMs(),
                row.updatedAtMs());
    }

    private AdventureDtos.Character toCharacterView(
            String installationId,
            AdventurePackageReader.AdventureCharacter character) {
        AdventurePackageReader.AdventureDialogue dialogue = character.dialogue();
        AdventurePackageReader.AdventureSkill skill = character.skill();
        return new AdventureDtos.Character(
                character.characterKey(),
                installationId,
                character.name(),
                character.epithet(),
                character.description(),
                character.age(),
                character.defaultCostumeId(),
                character.costumes(),
                new AdventureDtos.Dialogue(
                        dialogue.welcome(),
                        dialogue.again(),
                        dialogue.rest(),
                        dialogue.training(),
                        dialogue.practice(),
                        dialogue.result()),
                skill == null
                        ? null
                        : new AdventureDtos.Skill(
                                skill.name(),
                                skill.description(),
                                skill.type(),
                                skill.amount()));
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
