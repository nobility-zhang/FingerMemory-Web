package com.fingermemory.server.adventure;

public record AdventureRecollectionRow(
        String ownerId,
        String recollectionKey,
        String namespaceId,
        String bookId,
        String chapterId,
        String title,
        String speaker,
        String text,
        String sourceRunId,
        String sourceNodeId,
        long unlockedAtMs) {
}
