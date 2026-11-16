package com.fingermemory.server.auth;

import com.fingermemory.server.user.UserPreferences;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank
            @Size(min = 3, max = 32)
            @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "只能包含字母、数字、点、下划线和连字符")
            String username,
            @Size(max = 80)
            String displayName,
            @NotBlank
            @Size(min = 12, max = 128)
            String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    public record UserView(
            String id,
            String username,
            String displayName,
            boolean mustChangePassword,
            PreferencesView preferences) {
        public static UserView from(FmUserPrincipal principal, UserPreferences preferences) {
            return new UserView(
                    principal.id(),
                    principal.getUsername(),
                    principal.displayName(),
                    principal.mustChangePassword(),
                    new PreferencesView(
                            preferences.getNickname(),
                            preferences.getAvatarKey(),
                            preferences.getTimezone(),
                            preferences.getDailyGoal(),
                            preferences.getTheme(),
                            preferences.isSoundEnabled(),
                            preferences.getShortcutsJson()));
        }
    }

    public record PreferencesView(
            String nickname,
            String avatarKey,
            String timezone,
            int dailyGoal,
            String theme,
            boolean soundEnabled,
            String shortcutsJson) {
    }

    public record CsrfView(String headerName, String parameterName, String token) {
    }
}
