package com.fingermemory.server.auth;

import com.fingermemory.server.common.ApiError;
import com.fingermemory.server.common.ApiErrorWriter;
import com.fingermemory.server.user.UserAccess;
import com.fingermemory.server.user.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ActiveUserFilter extends OncePerRequestFilter {
    private static final String PASSWORD_CHANGE_PATH = "/api/v1/auth/password";
    private static final String CURRENT_USER_PATH = "/api/v1/auth/me";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String CSRF_PATH = "/api/v1/auth/csrf";

    private final UserMapper userMapper;
    private final ApiErrorWriter errorWriter;

    public ActiveUserFilter(UserMapper userMapper, ApiErrorWriter errorWriter) {
        this.userMapper = userMapper;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof FmUserPrincipal principal) {
            UserAccess access = userMapper.findAccessById(principal.id());
            if (access == null || !"active".equals(access.status())) {
                rejectUnavailableUser(request, response);
                return;
            }

            FmUserPrincipal refreshed = principal.refreshAccess(access);
            Authentication refreshedAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                    refreshed,
                    authentication.getCredentials(),
                    refreshed.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(refreshedAuthentication);

            if (access.mustChangePassword() && !isPasswordChangeAllowed(request)) {
                errorWriter.write(response, HttpStatus.FORBIDDEN.value(),
                        new ApiError("PASSWORD_CHANGE_REQUIRED", "首次登录必须先修改密码"));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPasswordChangeAllowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        return PASSWORD_CHANGE_PATH.equals(path)
                || CURRENT_USER_PATH.equals(path)
                || LOGOUT_PATH.equals(path)
                || LOGIN_PATH.equals(path)
                || CSRF_PATH.equals(path);
    }

    private void rejectUnavailableUser(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        errorWriter.write(response, HttpStatus.UNAUTHORIZED.value(),
                new ApiError("AUTH_REQUIRED", "账号不可用，请重新登录"));
    }
}
