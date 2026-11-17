package com.fingermemory.server.sandbox;

import com.fingermemory.server.common.ApiException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class GmSandboxEngine {
    private static final Set<String> COMMANDS = Set.of(
            "jump-node",
            "grant-cards",
            "unlock-character",
            "set-clock",
            "reset-sandbox");

    public Command parse(String commandName, JsonNode args) {
        String command = requireCommand(commandName);
        if (args == null || !args.isObject()) {
            throw validation("args 必须是对象");
        }
        return switch (command) {
            case "jump-node" -> parseJumpNode(args);
            case "grant-cards" -> parseGrantCards(args);
            case "unlock-character" -> parseUnlockCharacter(args);
            case "set-clock" -> parseSetClock(args);
            case "reset-sandbox" -> parseReset(args);
            default -> throw validation("不支持的 sandbox 命令");
        };
    }

    public GmSandboxState apply(GmSandboxState state, Command command) {
        return switch (command) {
            case JumpNode jump -> jumpNode(state, jump.nodeId());
            case GrantCards grant -> grantCards(state, grant.cardDefinitionIds());
            case UnlockCharacter unlock -> unlockCharacter(state, unlock.characterKey());
            case SetClock clock -> setClock(state, clock);
            case ResetSandbox ignored -> GmSandboxCatalog.initialState();
        };
    }

    public boolean isReset(Command command) {
        return command instanceof ResetSandbox;
    }

    private JumpNode parseJumpNode(JsonNode args) {
        requireOnlyFields(args, Set.of("nodeId"));
        String nodeId = requireText(args, "nodeId", 64);
        if (!GmSandboxCatalog.nodes().contains(nodeId)) {
            throw validation("当前夹具不存在该节点");
        }
        return new JumpNode(nodeId);
    }

    private GrantCards parseGrantCards(JsonNode args) {
        requireOnlyFields(args, Set.of("cardDefinitionIds"));
        JsonNode values = args.get("cardDefinitionIds");
        if (values == null || !values.isArray() || values.isEmpty()) {
            throw validation("cardDefinitionIds 必须是非空数组");
        }
        if (values.size() > GmSandboxCatalog.MAX_DECK_SIZE) {
            throw validation("单次发牌数量超过上限");
        }
        List<String> cardDefinitionIds = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || value.textValue().isBlank()) {
                throw validation("cardDefinitionIds 只能包含非空字符串");
            }
            String cardDefinitionId = value.textValue();
            if (!GmSandboxCatalog.cards().contains(cardDefinitionId)) {
                throw validation("当前夹具不存在该卡牌定义");
            }
            cardDefinitionIds.add(cardDefinitionId);
        }
        return new GrantCards(List.copyOf(cardDefinitionIds));
    }

    private UnlockCharacter parseUnlockCharacter(JsonNode args) {
        requireOnlyFields(args, Set.of("characterKey"));
        String characterKey = requireText(args, "characterKey", 64);
        if (!GmSandboxCatalog.characters().contains(characterKey)) {
            throw validation("当前夹具不存在该角色");
        }
        return new UnlockCharacter(characterKey);
    }

    private SetClock parseSetClock(JsonNode args) {
        requireOnlyFields(args, Set.of("mode", "nowMs"));
        String mode = requireText(args, "mode", 16);
        if (!"fixed".equals(mode) && !"real".equals(mode)) {
            throw validation("时钟模式必须是 fixed 或 real");
        }
        JsonNode nowNode = args.get("nowMs");
        if ("fixed".equals(mode)) {
            if (nowNode == null || !nowNode.isIntegralNumber()
                    || !nowNode.canConvertToLong()
                    || nowNode.longValue() < 0) {
                throw validation("fixed 时钟必须提供非负 nowMs");
            }
            return new SetClock("fixed", nowNode.longValue());
        }
        if (nowNode != null && !nowNode.isNull()) {
            throw validation("real 时钟不能提供 nowMs");
        }
        return new SetClock("real", null);
    }

    private ResetSandbox parseReset(JsonNode args) {
        requireOnlyFields(args, Set.of());
        return new ResetSandbox();
    }

    private GmSandboxState jumpNode(GmSandboxState state, String nodeId) {
        return new GmSandboxState(
                state.schemaVersion(),
                state.fixtureVersion(),
                state.fixtureKey(),
                nodeId,
                GmSandboxCatalog.encounterForNode(nodeId),
                state.clockMode(),
                state.virtualNowMs(),
                state.randomSeed(),
                state.deck(),
                state.unlockedCharacters());
    }

    private GmSandboxState grantCards(
            GmSandboxState state,
            List<String> cardDefinitionIds) {
        List<String> deck = new ArrayList<>(state.deck());
        deck.addAll(cardDefinitionIds);
        if (deck.size() > GmSandboxCatalog.MAX_DECK_SIZE) {
            throw conflict("GM_SANDBOX_DECK_LIMIT", "sandbox 牌组超过容量上限");
        }
        for (String cardDefinitionId : GmSandboxCatalog.CARDS) {
            long copies = deck.stream().filter(cardDefinitionId::equals).count();
            if (copies > GmSandboxCatalog.MAX_CARD_COPIES) {
                throw conflict("GM_SANDBOX_CARD_LIMIT", "同名卡牌超过 sandbox 上限");
            }
        }
        return new GmSandboxState(
                state.schemaVersion(),
                state.fixtureVersion(),
                state.fixtureKey(),
                state.currentNodeId(),
                state.encounterId(),
                state.clockMode(),
                state.virtualNowMs(),
                state.randomSeed(),
                List.copyOf(deck),
                state.unlockedCharacters());
    }

    private GmSandboxState unlockCharacter(GmSandboxState state, String characterKey) {
        if (state.unlockedCharacters().contains(characterKey)) {
            throw conflict("GM_SANDBOX_CHARACTER_ALREADY_UNLOCKED", "该角色已在 sandbox 解锁");
        }
        List<String> characters = new ArrayList<>(state.unlockedCharacters());
        characters.add(characterKey);
        return new GmSandboxState(
                state.schemaVersion(),
                state.fixtureVersion(),
                state.fixtureKey(),
                state.currentNodeId(),
                state.encounterId(),
                state.clockMode(),
                state.virtualNowMs(),
                state.randomSeed(),
                state.deck(),
                List.copyOf(characters));
    }

    private GmSandboxState setClock(GmSandboxState state, SetClock clock) {
        return new GmSandboxState(
                state.schemaVersion(),
                state.fixtureVersion(),
                state.fixtureKey(),
                state.currentNodeId(),
                state.encounterId(),
                clock.mode(),
                clock.nowMs(),
                state.randomSeed(),
                state.deck(),
                state.unlockedCharacters());
    }

    private void requireOnlyFields(JsonNode args, Set<String> allowed) {
        Set<String> actual = new HashSet<>();
        args.properties().forEach(entry -> actual.add(entry.getKey()));
        if (!allowed.containsAll(actual)) {
            throw validation("args 包含当前命令不支持的字段");
        }
    }

    private String requireCommand(String command) {
        if (command == null || !COMMANDS.contains(command)) {
            throw validation("不支持的 sandbox 命令");
        }
        return command;
    }

    private String requireText(JsonNode parent, String field, int maxLength) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual()
                || value.textValue().isBlank()
                || value.textValue().length() > maxLength) {
            throw validation(field + " 无效");
        }
        return value.textValue();
    }

    private ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public sealed interface Command permits JumpNode, GrantCards, UnlockCharacter, SetClock, ResetSandbox {
    }

    public record JumpNode(String nodeId) implements Command {
    }

    public record GrantCards(List<String> cardDefinitionIds) implements Command {
    }

    public record UnlockCharacter(String characterKey) implements Command {
    }

    public record SetClock(String mode, Long nowMs) implements Command {
    }

    public record ResetSandbox() implements Command {
    }
}
