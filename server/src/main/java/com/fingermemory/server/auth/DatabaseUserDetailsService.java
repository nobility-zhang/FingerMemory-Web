package com.fingermemory.server.auth;

import com.fingermemory.server.user.UserAccount;
import com.fingermemory.server.user.UserMapper;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserMapper userMapper;

    public DatabaseUserDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount user = userMapper.findByNormalizedUsername(username.trim().toLowerCase(Locale.ROOT));
        if (user == null || !"active".equals(user.getStatus())) {
            throw new UsernameNotFoundException("invalid credentials");
        }
        return new FmUserPrincipal(user);
    }
}
