package com.fingermemory.server.admin;

import com.fingermemory.server.auth.AuthDtos.RegisterRequest;
import com.fingermemory.server.user.UserAccount;
import com.fingermemory.server.user.UserMapper;
import com.fingermemory.server.user.UserService;
import java.io.Console;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.admin.command")
public class UserAdminCommand implements ApplicationRunner {
    private final String command;
    private final String username;
    private final String displayName;
    private final UserMapper userMapper;
    private final UserService userService;

    public UserAdminCommand(
            @org.springframework.beans.factory.annotation.Value("${app.admin.command}") String command,
            @org.springframework.beans.factory.annotation.Value("${app.admin.username:}") String username,
            @org.springframework.beans.factory.annotation.Value("${app.admin.display-name:}") String displayName,
            UserMapper userMapper,
            UserService userService) {
        this.command = command;
        this.username = username;
        this.displayName = displayName;
        this.userMapper = userMapper;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        switch (command) {
            case "list" -> listUsers();
            case "create" -> createUser();
            case "reset-password" -> resetPassword();
            case "disable" -> setStatus("disabled");
            case "enable" -> setStatus("active");
            default -> throw new IllegalArgumentException("Unsupported app.admin.command: " + command);
        }
    }

    private void createUser() {
        String normalized = requireUsername();
        if (userMapper.findByNormalizedUsername(normalized) != null) {
            throw new IllegalStateException("User already exists: " + username);
        }
        char[] password = readPassword();
        try {
            userService.register(new RegisterRequest(username, displayName, new String(password)));
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
        System.out.println("Created user: " + username);
    }

    private void resetPassword() {
        UserAccount user = requireUser();
        char[] password = readPassword();
        try {
            userService.resetPassword(user.getId(), new String(password));
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
        System.out.println("Password reset: " + user.getUsername());
    }

    private void setStatus(String status) {
        UserAccount user = requireUser();
        userMapper.updateStatus(user.getId(), status, Instant.now().toEpochMilli());
        System.out.println("User status updated: " + user.getUsername() + " -> " + status);
    }

    private void listUsers() {
        List<UserAccount> users = userMapper.listAll();
        for (UserAccount user : users) {
            System.out.println(user.getUsername() + "\t" + user.getStatus() + "\t" + user.getDisplayName());
        }
    }

    private UserAccount requireUser() {
        UserAccount user = userMapper.findByNormalizedUsername(requireUsername());
        if (user == null) {
            throw new IllegalStateException("User not found: " + username);
        }
        return user;
    }

    private String requireUsername() {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("--app.admin.username is required");
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[a-z0-9_.-]{3,32}$")) {
            throw new IllegalArgumentException("Username must be 3-32 letters, digits, dot, underscore or hyphen");
        }
        return normalized;
    }

    private char[] readPassword() {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("A terminal is required to read the password securely");
        }
        char[] first = console.readPassword("Password: ");
        char[] second = console.readPassword("Confirm password: ");
        if (first == null || second == null || !java.util.Arrays.equals(first, second)) {
            java.util.Arrays.fill(first == null ? new char[0] : first, '\0');
            java.util.Arrays.fill(second == null ? new char[0] : second, '\0');
            throw new IllegalArgumentException("Password confirmation does not match");
        }
        if (first.length < 12 || first.length > 128) {
            java.util.Arrays.fill(first, '\0');
            java.util.Arrays.fill(second, '\0');
            throw new IllegalArgumentException("Password must be 12-128 characters");
        }
        java.util.Arrays.fill(second, '\0');
        return first;
    }
}
