package com.fingermemory.server.user;

public record UserAccess(
        String id,
        String status,
        String role,
        String accountKind,
        boolean mustChangePassword,
        long version) {
}
