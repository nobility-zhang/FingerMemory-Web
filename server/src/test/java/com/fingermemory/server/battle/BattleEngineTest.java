package com.fingermemory.server.battle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class BattleEngineTest {
    private final BattleEngine engine = new BattleEngine(new ObjectMapper());

    @Test
    void bindsLearningThenGrantsEnergyAndPlaysAFullRound() {
        BattleTransition created = engine.create(new BattleEngine.CreateBattleCommand(
                "battle-1",
                "bridge-guardian",
                "task-1",
                "session-1",
                "study-1",
                "lia",
                42L,
                false));
        assertThat(created.state().phase()).isEqualTo("learning");
        assertThat(created.state().energy()).isZero();
        assertThat(created.state().hand()).hasSize(4);
        assertThat(created.state().learningTask().energyGranted()).isFalse();

        BattleTransition claimed = engine.claimLearning(
                created.state(), 0, "review-1");
        assertThat(claimed.state().phase()).isEqualTo("player-action");
        assertThat(claimed.state().energy()).isEqualTo(BattleCatalog.BASE_ENERGY);
        assertThat(claimed.state().learningTask().energyGranted()).isTrue();
        assertThat(claimed.state().learningTask().submissionId()).isEqualTo("review-1");

        BattleState.CardInstance guard = claimed.state().hand().get(0);
        BattleTransition guarded = engine.playCard(
                claimed.state(), 1, guard.instanceId(), null);
        assertThat(guarded.state().party().block()).isEqualTo(5);
        assertThat(guarded.state().energy()).isEqualTo(2);

        BattleTransition skilled = engine.useCompanionSkill(
                guarded.state(), 2);
        assertThat(skilled.state().companion().skillUsed()).isTrue();
        assertThat(skilled.state().party().hp()).isEqualTo(30);
        assertThat(skilled.state().energy()).isEqualTo(2);

        BattleTransition ended = engine.endTurn(
                skilled.state(),
                3,
                new BattleEngine.NextLearningTask("task-2", "session-2", "study-2"));
        assertThat(ended.state().turnId()).isEqualTo(2);
        assertThat(ended.state().phase()).isEqualTo("learning");
        assertThat(ended.state().energy()).isZero();
        assertThat(ended.state().party().hp()).isEqualTo(29);
        assertThat(ended.state().learningTask().taskId()).isEqualTo("task-2");
    }

    @Test
    void keepsCompanionSkillEncounterScopedAndRequiresNextTaskAfterSurvival() {
        BattleTransition created = engine.create(new BattleEngine.CreateBattleCommand(
                "battle-2",
                "bridge-guardian",
                "task-1",
                "session-1",
                "study-1",
                "serin",
                7L,
                false));
        BattleTransition claimed = engine.claimLearning(created.state(), 0, "review-2");
        BattleTransition skilled = engine.useCompanionSkill(claimed.state(), 1);

        assertThat(skilled.state().party().block()).isEqualTo(8);
        assertThatThrownBy(() -> engine.useCompanionSkill(skilled.state(), 2))
                .isInstanceOf(BattleEngine.BattleRuleException.class);
        assertThatThrownBy(() -> engine.endTurn(skilled.state(), 2, null))
                .isInstanceOf(BattleEngine.BattleRuleException.class)
                .hasMessage("next learning task is required");
    }

    @Test
    void rejectsStaleVersionsAndInvalidTargets() {
        BattleTransition created = engine.create(new BattleEngine.CreateBattleCommand(
                "battle-3",
                "bridge-guardian",
                "task-1",
                "session-1",
                "study-1",
                "lia",
                1L,
                false));
        assertThatThrownBy(() -> engine.claimLearning(created.state(), 1, "review-3"))
                .isInstanceOf(BattleEngine.BattleRuleException.class)
                .hasMessage("battle state version conflict");
        BattleTransition claimed = engine.claimLearning(created.state(), 0, "review-3");
        assertThatThrownBy(() -> engine.playCard(
                claimed.state(),
                1,
                claimed.state().hand().get(1).instanceId(),
                null))
                .isInstanceOf(BattleEngine.BattleRuleException.class)
                .hasMessage("valid enemy target required");
    }

    @Test
    void endsInVictoryBeforeIntentWhenBurnKillsTheOnlyEnemy() {
        BattleState state = new BattleState(
                1,
                BattleCatalog.RULES_VERSION,
                "battle-burn",
                "bridge-guardian",
                1,
                "player-action",
                0,
                0,
                new BattleState.Party(30, 30, 0),
                List.of(new BattleState.Enemy(
                        "bridge-guardian",
                        1,
                        36,
                        0,
                        new BattleState.Burn(1, 2))),
                3,
                List.of(),
                0,
                List.of(),
                List.of(),
                List.of(),
                new BattleState.Intent("bridge-guardian", "attack", 6),
                List.of(new BattleState.IntentTemplate("attack", 6)),
                0,
                null,
                null,
                new BattleState.Companion("lia", false));

        BattleTransition transition = engine.endTurn(state, 0, null);

        assertThat(transition.state().phase()).isEqualTo("victory");
        assertThat(transition.state().party().hp()).isEqualTo(30);
        assertThat(transition.events())
                .extracting(node -> node.path("type").asText())
                .doesNotContain("intent-executed")
                .contains("battle-ended");
    }
}
