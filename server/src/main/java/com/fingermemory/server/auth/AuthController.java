package com.fingermemory.server.auth;

import com.fingermemory.server.auth.AuthDtos.ChangePasswordRequest;
import com.fingermemory.server.auth.AuthDtos.CsrfView;
import com.fingermemory.server.auth.AuthDtos.RegisterRequest;
import com.fingermemory.server.auth.AuthDtos.UserView;
import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.config.AppProperties;
import com.fingermemory.server.user.UserAccount;
import com.fingermemory.server.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserService userService;
    private final AuthRateLimiter rateLimiter;
    private final AppProperties properties;

    public AuthController(UserService userService, AuthRateLimiter rateLimiter, AppProperties properties) {
        this.userService = userService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @GetMapping("/csrf")
    public CsrfView csrf(CsrfToken token) {
        return new CsrfView(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @PostMapping("/register")
    public ResponseEntity<UserView> register(@Valid @RequestBody RegisterRequest request,
                                             HttpServletRequest servletRequest) {
        rateLimiter.checkRegistration(servletRequest.getRemoteAddr());
        if (!properties.auth().registrationEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AUTH_REGISTRATION_DISABLED", "当前未开放注册");
        }
        UserAccount user = userService.register(request);
        FmUserPrincipal principal = new FmUserPrincipal(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserView.from(principal, userService.preferences(user.getId())));
    }

    @GetMapping("/me")
    public UserView me(Authentication authentication) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return UserView.from(principal, userService.preferences(principal.id()));
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(Authentication authentication,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        userService.changePassword(principal, request);
        return ResponseEntity.noContent().build();
    }
}
