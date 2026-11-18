package com.fingermemory.server.adventure;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public final class AdventureDtos {
    private AdventureDtos() {
    }

    public record StartRequest(
            @NotBlank @Size(max = 36) String installationId,
            @NotBlank
            @Pattern(regexp = "^(lia|serin|fina)$")
            String companionKey,
            boolean reduceMotion) {
    }

    public record ActionRequest(
            @Min(0) long expectedVersion,
            @NotBlank
            @Pattern(regexp = "^(advance|choose|resolve-encounter|claim-reward|select-companion|complete)$")
            String action,
            @Size(max = 128) String choiceId,
            @Size(max = 36) String battleId,
            @Pattern(regexp = "^(lia|serin|fina)$") String companionKey) {
    }

    public record Choice(
            String choiceId,
            String label) {
    }

    public record Node(
            String nodeId,
            String chapterId,
            String type,
            String title,
            String speaker,
            String text,
            String encounterId,
            String portraitCharacterId,
            List<Choice> choices) {
    }

    public record RewardChoice(
            String choiceId,
            String cardDefinitionId,
            String name,
            String description) {
    }

    public record RewardOffer(
            String offerId,
            String nodeId,
            List<RewardChoice> choices) {
    }

    public record Run(
            String id,
            String installationId,
            String namespace,
            String bookId,
            String contentVersion,
            String contentHash,
            String title,
            String companionKey,
            String status,
            long runVersion,
            String sessionId,
            String battleId,
            Node currentNode,
            List<String> deck,
            List<String> unlockedCharacters,
            RewardOffer rewardOffer,
            Map<String, Boolean> flags,
            long createdAtMs,
            long updatedAtMs) {
    }

    public record StartResult(
            String outcome,
            Run run) {
    }

    public record ActionResult(
            String outcome,
            Run run) {
    }

    public record Encounter(
            String outcome,
            String battleId,
            String runId,
            long runVersion) {
    }

    public record Recollection(
            String key,
            String namespace,
            String bookId,
            String chapterId,
            String title,
            String speaker,
            String text,
            long unlockedAtMs) {
    }
}
