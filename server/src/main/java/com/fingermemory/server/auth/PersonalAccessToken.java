package com.fingermemory.server.auth;

public class PersonalAccessToken {
    private String id;
    private String ownerId;
    private String name;
    private String tokenHash;
    private String tokenPrefix;
    private String scope;
    private long createdAtMs;
    private long expiresAtMs;
    private Long lastUsedAtMs;
    private Long revokedAtMs;
    private long version;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public void setTokenPrefix(String tokenPrefix) {
        this.tokenPrefix = tokenPrefix;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public void setCreatedAtMs(long createdAtMs) {
        this.createdAtMs = createdAtMs;
    }

    public long getExpiresAtMs() {
        return expiresAtMs;
    }

    public void setExpiresAtMs(long expiresAtMs) {
        this.expiresAtMs = expiresAtMs;
    }

    public Long getLastUsedAtMs() {
        return lastUsedAtMs;
    }

    public void setLastUsedAtMs(Long lastUsedAtMs) {
        this.lastUsedAtMs = lastUsedAtMs;
    }

    public Long getRevokedAtMs() {
        return revokedAtMs;
    }

    public void setRevokedAtMs(Long revokedAtMs) {
        this.revokedAtMs = revokedAtMs;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
