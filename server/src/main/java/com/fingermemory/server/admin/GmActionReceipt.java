package com.fingermemory.server.admin;

public record GmActionReceipt(
        String actorId,
        String idempotencyKey,
        String requestFingerprint,
        String responseJson,
        long createdAtMs) {
}
