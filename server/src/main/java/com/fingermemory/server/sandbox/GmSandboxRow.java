package com.fingermemory.server.sandbox;

public record GmSandboxRow(
        String ownerAdminId,
        String id,
        String templateId,
        String contentInstallationId,
        long version,
        String dataEpoch,
        String stateJson,
        long createdAtMs,
        long updatedAtMs) {
}
