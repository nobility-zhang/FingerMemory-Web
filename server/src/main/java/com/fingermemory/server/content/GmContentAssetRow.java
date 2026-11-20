package com.fingermemory.server.content;

public record GmContentAssetRow(
        String contentId,
        String assetPath,
        String contentHash,
        String mime,
        long byteSize,
        String kind,
        String license) {
}
