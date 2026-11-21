package com.fingermemory.server.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AccessTokenDtos {
    private AccessTokenDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 80) String name,
            Long expiresAtMs) {
    }

    public record TokenView(
            String id,
            String name,
            String prefix,
            String scope,
            long createdAtMs,
            long expiresAtMs,
            Long lastUsedAtMs,
            Long revokedAtMs) {
        public static TokenView from(PersonalAccessToken token) {
            return new TokenView(
                    token.getId(),
                    token.getName(),
                    token.getTokenPrefix(),
                    token.getScope(),
                    token.getCreatedAtMs(),
                    token.getExpiresAtMs(),
                    token.getLastUsedAtMs(),
                    token.getRevokedAtMs());
        }
    }

    public record CreatedTokenView(TokenView token, String plaintextToken) {
    }
}
