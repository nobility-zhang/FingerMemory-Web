package com.fingermemory.server.battle;

import java.util.List;

public record BattleState(
        int schemaVersion,
        String battleRulesVersion,
        String battleId,
        String encounterId,
        int turnId,
        String phase,
        long stateVersion,
        long rngState,
        Party party,
        List<Enemy> enemies,
        int energy,
        List<CardInstance> drawPile,
        int drawCursor,
        List<CardInstance> hand,
        List<CardInstance> discardPile,
        List<CardInstance> exhaustPile,
        Intent currentIntent,
        List<IntentTemplate> intentCycle,
        int intentIndex,
        String terminal,
        LearningTask learningTask,
        Companion companion) {

    public record Party(int hp, int maxHp, int block) {
    }

    public record Burn(int remainingTurns, int damagePerTurn) {
    }

    public record Enemy(
            String id,
            int hp,
            int maxHp,
            int block,
            Burn burn) {
    }

    public record CardInstance(String instanceId, String definitionId) {
    }

    public record Intent(String enemyId, String type, int value) {
    }

    public record IntentTemplate(String type, int value) {
    }

    public record LearningTask(
            String taskId,
            String sessionId,
            String studyItemId,
            boolean energyGranted,
            String submissionId) {
    }

    public record Companion(
            String key,
            boolean skillUsed,
            String skillName,
            String skillDescription,
            String skillType,
            int skillAmount) {

        public Companion(String key, boolean skillUsed) {
            this(key, skillUsed, null, null, null, 0);
        }
    }
}
