package com.fingermemory.server.auth;

import tools.jackson.databind.ObjectMapper;
import com.fingermemory.server.auth.AuthDtos.UserView;
import com.fingermemory.server.common.ApiError;
import com.fingermemory.server.common.ApiErrorWriter;
import com.fingermemory.server.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class JsonAuthenticationHandlers {
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final ApiErrorWriter errorWriter;

    public JsonAuthenticationHandlers(ObjectMapper objectMapper, UserService userService,
                                      ApiErrorWriter errorWriter) {
        this.objectMapper = objectMapper;
        this.userService = userService;
        this.errorWriter = errorWriter;
    }

    public AuthenticationSuccessHandler success() {
        return (request, response, authentication) -> writeSuccess(request, response, authentication);
    }

    public AuthenticationFailureHandler failure() {
        return (request, response, exception) -> errorWriter.write(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                new ApiError("AUTH_INVALID_CREDENTIALS", "用户名或密码不正确"));
    }

    private void writeSuccess(HttpServletRequest request, HttpServletResponse response,
                              Authentication authentication) throws IOException {
        FmUserPrincipal principal = (FmUserPrincipal) authentication.getPrincipal();
        userService.touchLastLogin(principal.id());
        UserView view = UserView.from(principal, userService.preferences(principal.id()));
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), view);
    }
}
