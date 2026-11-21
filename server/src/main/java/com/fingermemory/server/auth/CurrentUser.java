package com.fingermemory.server.auth;

import com.fingermemory.server.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static FmUserPrincipal require(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof FmUserPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "请先登录");
        }
        return principal;
    }

    public static FmUserPrincipal requireBrowserSession(Authentication authentication) {
        FmUserPrincipal principal = require(authentication);
        boolean personalAccessToken = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_PAT".equals(authority.getAuthority()));
        if (personalAccessToken) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BROWSER_SESSION_REQUIRED",
                    "该操作必须在已登录的浏览器会话中完成");
        }
        return principal;
    }
}
