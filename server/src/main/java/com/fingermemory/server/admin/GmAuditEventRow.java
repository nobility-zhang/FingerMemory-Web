package com.fingermemory.server.admin;

public record GmAuditEventRow(
        String id,
        String actorId,
        String action,
        String target,
        String scope,
        String requestId,
        String reason,
        String outcome,
        long occurredAtMs) {
}
