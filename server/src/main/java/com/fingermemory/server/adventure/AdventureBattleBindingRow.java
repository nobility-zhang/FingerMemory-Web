package com.fingermemory.server.adventure;

public record AdventureBattleBindingRow(
        String ownerId,
        String runId,
        String nodeId,
        String encounterId,
        String battleId,
        long createdAtMs) {
}
