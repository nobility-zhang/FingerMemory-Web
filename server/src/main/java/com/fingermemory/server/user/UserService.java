package com.fingermemory.server.user;

import com.fingermemory.server.auth.AuthDtos.ChangePasswordRequest;
import com.fingermemory.server.auth.AuthDtos.RegisterRequest;
import com.fingermemory.server.auth.FmUserPrincipal;
import com.fingermemory.server.auth.PersonalAccessTokenMapper;
import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.learning.UserDataStore;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private static final String DEFAULT_SHORTCUTS = """
            {"submit":"Enter","pause":"Escape","again":"1","hard":"2","good":"3","easy":"4"}
            """.trim();

    private final UserMapper userMapper;
    private final UserPreferencesMapper preferencesMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserDataStore userDataStore;
    private final PersonalAccessTokenMapper tokenMapper;

    public UserService(UserMapper userMapper, UserPreferencesMapper preferencesMapper,
                       PasswordEncoder passwordEncoder, UserDataStore userDataStore,
                       PersonalAccessTokenMapper tokenMapper) {
        this.userMapper = userMapper;
        this.preferencesMapper = preferencesMapper;
        this.passwordEncoder = passwordEncoder;
        this.userDataStore = userDataStore;
        this.tokenMapper = tokenMapper;
    }

    @Transactional
    public UserAccount register(RegisterRequest request) {
        return createAccount(request, "USER", "standard", false);
    }

    @Transactional
    public UserAccount createAccount(RegisterRequest request, String role, String accountKind,
                                     boolean mustChangePassword) {
        validatePassword(request.password());
        String normalized = request.username().trim().toLowerCase(Locale.ROOT);
        if (userMapper.findByNormalizedUsername(normalized) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "AUTH_USERNAME_TAKEN", "用户名已被使用");
        }

        long now = Instant.now().toEpochMilli();
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? request.username().trim()
                : request.displayName().trim();
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(request.username().trim());
        user.setUsernameNormalized(normalized);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus("active");
        user.setRole(role);
        user.setAccountKind(accountKind);
        user.setMustChangePassword(mustChangePassword);
        user.setCreatedAtMs(now);
        user.setUpdatedAtMs(now);
        user.setLastLoginAtMs(null);
        user.setVersion(0);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "AUTH_USERNAME_TAKEN", "用户名已被使用");
        }

        UserPreferences preferences = new UserPreferences();
        preferences.setUserId(user.getId());
        preferences.setNickname(displayName);
        preferences.setAvatarKey("starter");
        preferences.setTimezone("UTC");
        preferences.setDailyGoal(20);
        preferences.setTheme("starlit");
        preferences.setSoundEnabled(true);
        preferences.setShortcutsJson(DEFAULT_SHORTCUTS);
        preferences.setCreatedAtMs(now);
        preferences.setUpdatedAtMs(now);
        preferences.setVersion(0);
        preferencesMapper.insert(preferences);
        userDataStore.initialize(user.getId());
        return user;
    }

    public UserPreferences preferences(String userId) {
        UserPreferences preferences = preferencesMapper.findByUserId(userId);
        if (preferences == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PREFERENCES_MISSING", "用户偏好数据缺失");
        }
        return preferences;
    }

    public void touchLastLogin(String userId) {
        userMapper.touchLastLogin(userId, Instant.now().toEpochMilli());
    }

    @Transactional
    public void changePassword(FmUserPrincipal principal, ChangePasswordRequest request) {
        UserAccount user = userMapper.findById(principal.id());
        if (user == null || !"active".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "请先登录");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_CURRENT_PASSWORD_INVALID", "当前密码不正确");
        }
        replacePassword(user.getId(), request.newPassword());
        tokenMapper.revokeAll(user.getId(), Instant.now().toEpochMilli());
    }

    @Transactional
    public void resetPassword(String userId, String password) {
        replacePassword(userId, password, true);
    }

    private void replacePassword(String userId, String password) {
        replacePassword(userId, password, false);
    }

    private void replacePassword(String userId, String password, boolean mustChangePassword) {
        validatePassword(password);
        int updated = userMapper.updatePassword(
                userId,
                passwordEncoder.encode(password),
                mustChangePassword,
                Instant.now().toEpochMilli());
        if (updated != 1) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "请先登录");
        }
    }

    private void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_PASSWORD_TOO_LONG",
                    "密码的 UTF-8 编码不能超过 72 字节");
        }
    }
}
