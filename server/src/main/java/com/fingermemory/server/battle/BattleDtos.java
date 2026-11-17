package com.fingermemory.server.battle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.databind.node.ObjectNode;

public final class BattleDtos {
    private BattleDtos() {
    }

    public record CreateBattleRequest(
            @NotBlank @Size(max = 36) String battleId,
            @NotBlank @Size(max = 64) String encounterId,
            @NotBlank @Size(max = 36) String sessionId,
            @NotBlank @Size(max = 128) String studyItemId,
            @NotBlank @Pattern(regexp = "^(lia|serin|fina)$") String companionKey,
            Long seed,
            Boolean shuffle) {
    }

    public record BattleCommandRequest(
            @PositiveOrZero long expectedStateVersion) {
    }

    public record PlayCardRequest(
            @PositiveOrZero long expectedStateVersion,
            @NotBlank @Size(max = 128) String cardInstanceId,
            @Size(max = 128) String targetId) {
    }

    public record LearningClaimRequest(
            @PositiveOrZero long expectedStateVersion,
            @NotBlank @Size(max = 128) String submissionId) {
    }

    public record CompanionSkillRequest(
            @PositiveOrZero long expectedStateVersion) {
    }

    public record EndTurnRequest(
            @PositiveOrZero long expectedStateVersion,
            @Size(max = 36) String nextSessionId,
            @Size(max = 128) String nextStudyItemId) {
    }

    public record BattleEnvelope(
            ObjectNode battle,
            List<ObjectNode> events,
            long stateVersion) {
    }
}
