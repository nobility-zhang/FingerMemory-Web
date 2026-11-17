package com.fingermemory.server.sandbox;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class GmSandboxDtos {
    private GmSandboxDtos() {
    }

    public record Page(List<Sandbox> items, String nextCursor) {
    }

    public record Sandbox(
            String id,
            String scope,
            String ownerAdminId,
            String templateId,
            String contentInstallationId,
            long version,
            String dataEpoch,
            SandboxState state) {
    }

    public record SandboxState(
            int schemaVersion,
            int fixtureVersion,
            String fixtureKey,
            String currentNodeId,
            String encounterId,
            String clockMode,
            Long virtualNowMs,
            long randomSeed,
            List<String> deck,
            List<String> unlockedCharacters,
            List<String> availableNodes,
            List<String> availableCards,
            List<String> availableCharacters) {
    }

    public record CreateSandboxRequest(
            @NotBlank
            @Size(max = 64)
            String templateId,
            @Size(max = 128)
            String contentInstallationId,
            @NotBlank
            @Size(max = 300)
            String reason) {
    }

    public record CommandRequest(
            @NotBlank
            @Pattern(regexp = "^(jump-node|grant-cards|unlock-character|set-clock|reset-sandbox)$")
            String command,
            @NotNull
            @PositiveOrZero
            Long expectedVersion,
            @NotBlank
            @Size(max = 36)
            String dataEpoch,
            @NotBlank
            @Size(max = 300)
            String reason,
            @NotNull
            JsonNode args) {
    }

    public record CommandReceipt(
            String commandId,
            String status,
            String scope,
            Sandbox sandbox) {
    }
}
