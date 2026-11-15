package com.fingermemory.server.auth;

import com.fingermemory.server.user.UserAccount;
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

    public FmUserPrincipal(UserAccount user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.displayName = user.getDisplayName();
        this.passwordHash = user.getPasswordHash();
        this.enabled = "active".equals(user.getStatus());
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
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
