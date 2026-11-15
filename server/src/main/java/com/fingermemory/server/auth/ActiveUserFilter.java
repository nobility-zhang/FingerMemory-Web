package com.fingermemory.server.auth;

import com.fingermemory.server.common.ApiError;
import com.fingermemory.server.common.ApiErrorWriter;
import com.fingermemory.server.user.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ActiveUserFilter extends OncePerRequestFilter {
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
        if (authentication != null && authentication.getPrincipal() instanceof FmUserPrincipal principal
                && !"active".equals(userMapper.findStatusById(principal.id()))) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            errorWriter.write(response, HttpStatus.UNAUTHORIZED.value(),
                    new ApiError("AUTH_REQUIRED", "账号不可用，请重新登录"));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
