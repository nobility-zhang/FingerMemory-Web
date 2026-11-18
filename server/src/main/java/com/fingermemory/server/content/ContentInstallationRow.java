package com.fingermemory.server.content;

public record ContentInstallationRow(
        String ownerId,
        String id,
        String namespaceId,
        String bookId,
        String contentVersion,
        String contentHash,
        String title,
        String entryNodeId,
        String engineVersion,
        int schemaVersion,
        String manifestJson,
        String storageRelativePath,
        int wordCount,
        int chapterCount,
        int nodeCount,
        int characterCount,
        int cardCount,
        int encounterCount,
        int assetCount,
        String status,
        long installedAtMs,
        long updatedAtMs) {
}
