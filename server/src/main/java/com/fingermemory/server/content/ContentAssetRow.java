package com.fingermemory.server.content;

public record ContentAssetRow(
        String ownerId,
        String installationId,
        String assetPath,
        String contentHash,
        String mime,
        long byteSize,
        String assetKind,
        String licenseText,
        long createdAtMs) {
}
