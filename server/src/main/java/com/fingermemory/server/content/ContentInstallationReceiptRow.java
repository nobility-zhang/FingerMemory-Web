package com.fingermemory.server.content;

public record ContentInstallationReceiptRow(
        String ownerId,
        String idempotencyKey,
        String requestFingerprint,
        String installationId,
        String responseJson,
        long createdAtMs) {
}
