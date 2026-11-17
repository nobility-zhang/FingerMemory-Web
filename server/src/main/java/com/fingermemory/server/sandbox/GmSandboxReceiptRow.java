package com.fingermemory.server.sandbox;

public record GmSandboxReceiptRow(
        String ownerAdminId,
        String sandboxId,
        String dataEpoch,
        String idempotencyKey,
        String requestFingerprint,
        String responseJson,
        long sandboxVersion,
        long createdAtMs) {
}
