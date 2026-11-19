package com.fingermemory.server.adventure;

public record AdventureCompletionRow(
        String ownerId,
        String id,
        String runId,
        String namespaceId,
        String bookId,
        String seriesId,
        String contentVersion,
        String contentHash,
        String endingId,
        String endingTitle,
        String endingText,
        String sourceNodeId,
        String inheritedFlagsJson,
        long completedAtMs) {
}
