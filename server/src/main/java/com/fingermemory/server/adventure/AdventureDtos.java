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
            @Pattern(regexp = "^(advance|choose|resolve-encounter|complete)$")
            String action,
            @Size(max = 128) String choiceId,
            @Size(max = 36) String battleId) {
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
            List<Choice> choices) {
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
}
