package com.fingermemory.server.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestBodyLimitFilter extends OncePerRequestFilter {
    private static final long MAX_MIGRATION_BYTES = 20L * 1024 * 1024;
    private static final long MAX_MULTIPART_PACKAGE_BYTES = 52L * 1024 * 1024;

    private final ApiErrorWriter errorWriter;

    public RequestBodyLimitFilter(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/migrations/local")
                && !path.startsWith("/api/v1/backup/import")
                && !path.startsWith("/api/v1/content-packages");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        long limit = request.getRequestURI().startsWith("/api/v1/content-packages")
                ? MAX_MULTIPART_PACKAGE_BYTES
                : MAX_MIGRATION_BYTES;
        if (contentLength > limit) {
            errorWriter.write(response, HttpStatus.PAYLOAD_TOO_LARGE.value(),
                    new ApiError(
                            "PAYLOAD_TOO_LARGE",
                            limit == MAX_MULTIPART_PACKAGE_BYTES
                                    ? "内容包请求超过 50 MiB 上限"
                                    : "迁移或恢复请求超过 20 MiB 上限"));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
