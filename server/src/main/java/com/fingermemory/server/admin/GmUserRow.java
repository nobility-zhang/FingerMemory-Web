package com.fingermemory.server.admin;

public record GmUserRow(
        String id,
        String username,
        String displayName,
        String role,
        String status,
        String accountKind,
        long version) {
}
