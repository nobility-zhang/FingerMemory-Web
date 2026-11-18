package com.fingermemory.server.adventure;

public record AdventureReceiptRow(
        String ownerId,
        String runId,
        String actionId,
        String requestFingerprint,
        String responseJson,
        long runVersion,
        long createdAtMs) {
}
