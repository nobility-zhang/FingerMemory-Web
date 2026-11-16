package com.fingermemory.server.admin;

public record AdminBootstrapReceipt(
        String receiptKey,
        String userId,
        String usernameNormalized,
        long createdAtMs) {
}
