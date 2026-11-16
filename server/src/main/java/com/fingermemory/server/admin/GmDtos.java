package com.fingermemory.server.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class GmDtos {
    private GmDtos() {
    }

    public record Context(String role, List<String> capabilities) {
    }

    public record User(
            String id,
            String username,
            String displayName,
            String role,
            String status,
            String accountKind,
            long version) {
        public static User from(GmUserRow row) {
            return new User(
                    row.id(),
                    row.username(),
                    row.displayName(),
                    row.role(),
                    row.status(),
                    row.accountKind(),
                    row.version());
        }
    }

    public record UserPage(List<User> items, String nextCursor) {
    }

    public record CreateUserRequest(
            @NotBlank
            @Size(min = 3, max = 32)
            @Pattern(regexp = "^[A-Za-z0-9_.-]+$")
            String username,
            @Size(max = 80)
            String displayName,
            @NotBlank
            @Size(min = 12, max = 128)
            String password,
            @NotBlank
            @Pattern(regexp = "USER|ADMIN")
            String role,
            @NotBlank
            @Pattern(regexp = "standard|test")
            String accountKind,
            @NotBlank
            @Size(max = 300)
            String reason) {
    }

    public record UpdateUserRequest(
            @NotNull
            Long expectedVersion,
            @NotBlank
            @Size(max = 300)
            String reason,
            @Pattern(regexp = "USER|ADMIN")
            String role,
            @Pattern(regexp = "active|disabled")
            String status) {
    }

    public record AuditEvent(
            String id,
            String actorId,
            String action,
            String target,
            String scope,
            long occurredAtMs,
            String requestId,
            String reason,
            String outcome) {
    }

    public record AuditPage(List<AuditEvent> items, String nextCursor) {
    }
}
