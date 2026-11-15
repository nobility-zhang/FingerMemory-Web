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
}
