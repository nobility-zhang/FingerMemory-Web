package com.fingermemory.server.auth;

import com.fingermemory.server.user.UserAccount;
import com.fingermemory.server.user.UserAccess;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class FmUserPrincipal implements UserDetails {
    private final String id;
    private final String username;
    private final String displayName;
    private final String passwordHash;
    private final boolean enabled;
    private final String role;
    private final String accountKind;
    private final boolean mustChangePassword;

    public FmUserPrincipal(UserAccount user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.displayName = user.getDisplayName();
        this.passwordHash = user.getPasswordHash();
        this.enabled = "active".equals(user.getStatus());
        this.role = user.getRole();
        this.accountKind = user.getAccountKind();
        this.mustChangePassword = user.isMustChangePassword();
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String role() {
        return role;
    }

    public String accountKind() {
        return accountKind;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public FmUserPrincipal refreshAccess(UserAccess access) {
        return new FmUserPrincipal(
                id,
                username,
                displayName,
                passwordHash,
                "active".equals(access.status()),
                access.role(),
                access.accountKind(),
                access.mustChangePassword());
    }

    private FmUserPrincipal(
            String id,
            String username,
            String displayName,
            String passwordHash,
            boolean enabled,
            String role,
            String accountKind,
            boolean mustChangePassword) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.role = role;
        this.accountKind = accountKind;
        this.mustChangePassword = mustChangePassword;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
