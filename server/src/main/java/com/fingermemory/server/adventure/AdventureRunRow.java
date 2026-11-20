package com.fingermemory.server.adventure;

public record AdventureRunRow(
        String ownerId,
        String id,
        String dataEpoch,
        String installationId,
        String namespaceId,
        String bookId,
        String contentVersion,
        String contentHash,
        String entryNodeId,
        String currentNodeId,
        String sessionId,
        String companionKey,
        String status,
        String activeSlot,
        String sourceCompletionId,
        long runVersion,
        String flagsJson,
        String deckJson,
        String battleId,
        long createdAtMs,
        long updatedAtMs) {
}
