package com.fingermemory.server.user;

public class UserAccount {
    private String id;
    private String username;
    private String usernameNormalized;
    private String displayName;
    private String passwordHash;
    private String status;
    private long createdAtMs;
    private long updatedAtMs;
    private Long lastLoginAtMs;
    private long version;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsernameNormalized() {
        return usernameNormalized;
    }

    public void setUsernameNormalized(String usernameNormalized) {
        this.usernameNormalized = usernameNormalized;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public void setCreatedAtMs(long createdAtMs) {
        this.createdAtMs = createdAtMs;
    }

    public long getUpdatedAtMs() {
        return updatedAtMs;
    }

    public void setUpdatedAtMs(long updatedAtMs) {
        this.updatedAtMs = updatedAtMs;
    }

    public Long getLastLoginAtMs() {
        return lastLoginAtMs;
    }

    public void setLastLoginAtMs(Long lastLoginAtMs) {
        this.lastLoginAtMs = lastLoginAtMs;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
