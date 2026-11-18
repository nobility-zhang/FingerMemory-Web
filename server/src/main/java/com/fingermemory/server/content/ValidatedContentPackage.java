package com.fingermemory.server.content;

import java.nio.file.Path;
import java.util.List;

record ValidatedContentPackage(
        String fileName,
        String title,
        String namespace,
        String bookId,
        String contentVersion,
        String contentHash,
        String engineVersion,
        String entryNodeId,
        int schemaVersion,
        String manifestJson,
        int wordCount,
        int chapterCount,
        int nodeCount,
        int characterCount,
        int cardCount,
        int encounterCount,
        List<ContentPackageDtos.AssetView> assets,
        Path extractedDirectory) {
}
