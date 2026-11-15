package com.fingermemory.server.auth;

import com.fingermemory.server.common.ApiError;
import com.fingermemory.server.common.ApiErrorWriter;
import com.fingermemory.server.common.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final AuthRateLimiter rateLimiter;
    private final ApiErrorWriter errorWriter;

    public AuthRateLimitFilter(AuthRateLimiter rateLimiter, ApiErrorWriter errorWriter) {
        this.rateLimiter = rateLimiter;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !"/api/v1/auth/login".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            rateLimiter.checkLogin(request.getRemoteAddr(), request.getParameter("username"));
            filterChain.doFilter(request, response);
        } catch (ApiException exception) {
            errorWriter.write(response, exception.status().value(), new ApiError(exception.code(), exception.getMessage()));
        }
    }
}
