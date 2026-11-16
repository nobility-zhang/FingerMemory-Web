package com.fingermemory.server.admin;

import com.fingermemory.server.auth.AuthDtos.RegisterRequest;
import com.fingermemory.server.user.UserAccount;
import com.fingermemory.server.user.UserMapper;
import com.fingermemory.server.user.UserService;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultAdminBootstrapService {
    static final String RECEIPT_KEY = "default-admin";

    private final AdminBootstrapReceiptMapper receiptMapper;
    private final UserMapper userMapper;
    private final UserService userService;

    public DefaultAdminBootstrapService(
            AdminBootstrapReceiptMapper receiptMapper,
            UserMapper userMapper,
            UserService userService) {
        this.receiptMapper = receiptMapper;
        this.userMapper = userMapper;
        this.userService = userService;
    }

    @Transactional
    public BootstrapResult bootstrap(AdminBootstrapProperties properties) {
        AdminBootstrapReceipt existingReceipt =
                receiptMapper.findByKeyForUpdate(RECEIPT_KEY);
        if (existingReceipt != null) {
            return new BootstrapResult(false, existingReceipt.usernameNormalized());
        }

        String username = requireUsername(properties.username());
        String normalized = username.toLowerCase(Locale.ROOT);
        if (userMapper.findByNormalizedUsername(normalized) != null) {
            throw new IllegalStateException(
                    "Cannot bootstrap the default administrator because the username already exists: "
                            + normalized);
        }

        String displayName = properties.displayName() == null
                || properties.displayName().isBlank()
                ? "管理员"
                : properties.displayName().trim();
        UserAccount user = userService.createAccount(
                new RegisterRequest(username, displayName, properties.password()),
                "ADMIN",
                "standard",
                true);

        AdminBootstrapReceipt receipt = new AdminBootstrapReceipt(
                RECEIPT_KEY,
                user.getId(),
                normalized,
                Instant.now().toEpochMilli());
        receiptMapper.insert(receipt);
        return new BootstrapResult(true, normalized);
    }

    private String requireUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException(
                    "app.admin.bootstrap.username is required when bootstrap is enabled");
        }
        String trimmed = username.trim();
        if (!trimmed.matches("^[A-Za-z0-9_.-]{3,32}$")) {
            throw new IllegalStateException(
                    "app.admin.bootstrap.username must be 3-32 letters, digits, dot, underscore or hyphen");
        }
        return trimmed;
    }

    public record BootstrapResult(boolean created, String normalizedUsername) {
    }
}
