package com.fingermemory.server.battle;

import com.fingermemory.server.common.JsonSupport;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class BattleStateValidator {
    private static final Set<String> PHASES = Set.of(
            "learning", "player-action", "enemy-action", "victory", "defeat");
    private static final Set<String> TERMINALS = Set.of(
            "victory", "defeat");
    private static final Set<String> INTENT_TYPES = Set.of(
            "attack", "defend");
    private static final Set<String> SKILL_TYPES = Set.of(
            "heal", "block", "draw", "damage");

    public void validate(BattleState state) {
        if (state == null
                || state.schemaVersion() != BattleCatalog.SCHEMA_VERSION
                || !BattleCatalog.RULES_VERSION.equals(state.battleRulesVersion())) {
            throw JsonSupport.validation("战斗规则或状态版本不受支持");
        }
        requireText(state.battleId(), "战斗 ID");
        requireText(state.encounterId(), "遭遇 ID");
        if (!PHASES.contains(state.phase())
                || (state.terminal() != null && !TERMINALS.contains(state.terminal()))) {
            throw JsonSupport.validation("战斗阶段无效");
        }
        if (state.turnId() < 1 || state.stateVersion() < 0 || state.energy() < 0) {
            throw JsonSupport.validation("战斗回合或状态版本无效");
        }
        if (state.party() == null
                || state.party().maxHp() <= 0
                || state.party().hp() < 0
                || state.party().hp() > state.party().maxHp()
                || state.party().block() < 0) {
            throw JsonSupport.validation("队伍生命或护盾无效");
        }
        if (state.enemies() == null || state.enemies().isEmpty()) {
            throw JsonSupport.validation("战斗至少需要一个敌人");
        }
        for (BattleState.Enemy enemy : state.enemies()) {
            if (enemy == null) {
                throw JsonSupport.validation("敌人状态不能为空");
            }
            requireText(enemy.id(), "敌人 ID");
            if (enemy.maxHp() <= 0
                    || enemy.hp() < 0
                    || enemy.hp() > enemy.maxHp()
                    || enemy.block() < 0) {
                throw JsonSupport.validation("敌人生命或护盾无效");
            }
        }
        if (state.drawPile() == null
                || state.hand() == null
                || state.discardPile() == null
                || state.exhaustPile() == null) {
            throw JsonSupport.validation("战斗牌区不能为空");
        }
        if (state.drawCursor() < 0 || state.drawCursor() > state.drawPile().size()) {
            throw JsonSupport.validation("抽牌游标越界");
        }
        validateCards(state);
        validateIntent(state);
        if ("learning".equals(state.phase())
                && (state.learningTask() == null
                || state.learningTask().energyGranted())) {
            throw JsonSupport.validation("学习阶段必须绑定未授能的学习任务");
        }
        if (state.learningTask() != null) {
            requireText(state.learningTask().taskId(), "学习任务 ID");
            requireText(state.learningTask().sessionId(), "学习会话 ID");
            requireText(state.learningTask().studyItemId(), "学习目标 ID");
            if (state.learningTask().energyGranted()) {
                requireText(state.learningTask().submissionId(), "学习回执 ID");
            } else if (state.learningTask().submissionId() != null) {
                throw JsonSupport.validation("未授能任务不能包含提交回执");
            }
        }
        if (state.companion() == null
                || state.companion().key() == null
                || state.companion().key().isBlank()) {
            throw JsonSupport.validation("伙伴技能状态无效");
        }
        if (state.companion().skillType() == null) {
            if (!BattleCatalog.COMPANIONS.contains(state.companion().key())) {
                throw JsonSupport.validation("伙伴技能状态无效");
            }
        } else {
            if (!SKILL_TYPES.contains(state.companion().skillType())
                    || state.companion().skillAmount() < 1
                    || state.companion().skillAmount() > 20
                    || state.companion().skillName() == null
                    || state.companion().skillName().isBlank()) {
                throw JsonSupport.validation("伙伴技能配置无效");
            }
        }
        boolean terminalPhase = "victory".equals(state.phase())
                || "defeat".equals(state.phase());
        if (terminalPhase && !state.phase().equals(state.terminal())) {
            throw JsonSupport.validation("战斗终结阶段与结果不一致");
        }
        if (!terminalPhase && state.terminal() != null) {
            throw JsonSupport.validation("未终结战斗不能保存终结结果");
        }
    }

    private void validateCards(BattleState state) {
        Set<String> instanceIds = new HashSet<>();
        List<List<BattleState.CardInstance>> zones = List.of(
                state.drawPile().subList(state.drawCursor(), state.drawPile().size()),
                state.hand(),
                state.discardPile(),
                state.exhaustPile());
        for (List<BattleState.CardInstance> zone : zones) {
            for (BattleState.CardInstance card : zone) {
                if (card == null) {
                    throw JsonSupport.validation("卡牌实例不能为空");
                }
                requireText(card.instanceId(), "卡牌实例 ID");
                requireText(card.definitionId(), "卡牌定义 ID");
                if (!instanceIds.add(card.instanceId())) {
                    throw JsonSupport.validation("卡牌实例重复出现在多个牌区");
                }
            }
        }
        if (state.hand().size() > BattleCatalog.HAND_LIMIT) {
            throw JsonSupport.validation("手牌数量超过上限");
        }
    }

    private void validateIntent(BattleState state) {
        if (state.intentCycle() == null
                || state.intentCycle().isEmpty()
                || state.intentIndex() < 0
                || state.intentIndex() >= state.intentCycle().size()
                || state.currentIntent() == null
                || !INTENT_TYPES.contains(state.currentIntent().type())
                || state.currentIntent().value() < 0) {
            throw JsonSupport.validation("敌方意图状态无效");
        }
        BattleState.IntentTemplate expected = state.intentCycle().get(state.intentIndex());
        if (expected == null
                || !expected.type().equals(state.currentIntent().type())
                || expected.value() != state.currentIntent().value()) {
            throw JsonSupport.validation("当前敌方意图与意图序列不一致");
        }
        if (state.enemies().stream().noneMatch(
                enemy -> enemy.id().equals(state.currentIntent().enemyId()))) {
            throw JsonSupport.validation("当前敌方意图引用了不存在的敌人");
        }
        for (BattleState.IntentTemplate intent : state.intentCycle()) {
            if (intent == null
                    || !INTENT_TYPES.contains(intent.type())
                    || intent.value() < 0) {
                throw JsonSupport.validation("敌方意图序列无效");
            }
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw JsonSupport.validation(label + "无效");
        }
    }
}
