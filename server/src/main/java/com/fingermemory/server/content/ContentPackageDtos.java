package com.fingermemory.server.content;

import java.util.List;

public final class ContentPackageDtos {
    private ContentPackageDtos() {
    }

    public record Preview(
            String fileName,
            String title,
            String namespace,
            String bookId,
            String contentVersion,
            String contentHash,
            String engineVersion,
            String entryNodeId,
            String seriesId,
            List<ContentRequirement> requirements,
            int wordCount,
            int chapterCount,
            int nodeCount,
            int characterCount,
            int cardCount,
            int encounterCount,
            int assetCount,
            List<String> warnings) {
    }

    public record Installation(
            String id,
            String namespace,
            String bookId,
            String contentVersion,
            String contentHash,
            String title,
            String entryNodeId,
            String seriesId,
            List<ContentRequirement> requirements,
            String status,
            int wordCount,
            int chapterCount,
            int nodeCount,
            int characterCount,
            int cardCount,
            int encounterCount,
            int assetCount,
            long installedAtMs) {
    }

    public record InstallResult(
            String outcome,
            Installation installation) {
    }

    public record AssetView(
            String path,
            String sha256,
            String mime,
            long byteSize,
            String kind,
            String license) {
    }
}
