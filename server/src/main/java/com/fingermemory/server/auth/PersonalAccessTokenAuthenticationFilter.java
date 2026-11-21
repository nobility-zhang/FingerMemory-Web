package com.fingermemory.server.auth;

import com.fingermemory.server.common.ApiError;
import com.fingermemory.server.common.ApiErrorWriter;
import com.fingermemory.server.common.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class PersonalAccessTokenAuthenticationFilter extends OncePerRequestFilter {
    public static final String AUTHENTICATED_ATTRIBUTE =
            PersonalAccessTokenAuthenticationFilter.class.getName() + ".AUTHENTICATED";

    private static final String BEARER_PREFIX = "bearer ";

    private final PersonalAccessTokenService tokenService;
    private final ApiErrorWriter errorWriter;

    public PersonalAccessTokenAuthenticationFilter(
            PersonalAccessTokenService tokenService,
            ApiErrorWriter errorWriter) {
        this.tokenService = tokenService;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/")
                || request.getHeader("Authorization") == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0,
                        BEARER_PREFIX.length())) {
            filterChain.doFilter(request, response);
            return;
        }

        // A request that claims Bearer authentication must never fall back to
        // a browser session, otherwise a fake header could bypass Cookie CSRF.
        SecurityContextHolder.clearContext();
        String plaintext = authorization.substring(BEARER_PREFIX.length()).trim();
        try {
            FmUserPrincipal principal = tokenService.authenticate(plaintext);
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            principal,
                            plaintext,
                            List.of(new SimpleGrantedAuthority("ROLE_PAT")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setAttribute(AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
            filterChain.doFilter(request, response);
        } catch (ApiException exception) {
            SecurityContextHolder.clearContext();
            errorWriter.write(response, exception.status().value(),
                    new ApiError(exception.code(), exception.getMessage()));
        }
    }
}
