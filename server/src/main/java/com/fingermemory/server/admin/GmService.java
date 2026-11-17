package com.fingermemory.server.admin;

import com.fingermemory.server.auth.AuthDtos.RegisterRequest;
import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.user.UserAccount;
import com.fingermemory.server.user.UserMapper;
import com.fingermemory.server.user.UserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class GmService {
    private static final List<String> CAPABILITIES = List.of(
            "manage-users",
            "manage-sandboxes",
            "read-audit");

    private final GmMapper gmMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public GmService(
            GmMapper gmMapper,
            UserMapper userMapper,
            UserService userService,
            ObjectMapper objectMapper) {
        this.gmMapper = gmMapper;
        this.userMapper = userMapper;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    public GmDtos.Context context() {
        return new GmDtos.Context("ADMIN", CAPABILITIES);
    }

    public GmDtos.UserPage listUsers(int limit, String cursor) {
        if (limit < 1 || limit > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "limit must be between 1 and 100");
        }
        String normalizedCursor = cursor == null || cursor.isBlank() ? null : cursor.trim();
        List<GmUserRow> rows = gmMapper.listUsers(normalizedCursor, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<GmUserRow> pageRows = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore ? pageRows.get(pageRows.size() - 1).username() : null;
        return new GmDtos.UserPage(
                pageRows.stream().map(GmDtos.User::from).toList(),
                nextCursor);
    }

    @Transactional
    public GmDtos.User createUser(
            String actorId,
            String idempotencyKey,
            String requestId,
            GmDtos.CreateUserRequest request) {
        String fingerprint = fingerprint(
                "create",
                request.username(),
                request.displayName(),
                request.password(),
                request.role(),
                request.accountKind(),
                request.reason());
        GmDtos.User duplicate = findCreateReceipt(actorId, idempotencyKey, fingerprint);
        if (duplicate != null) {
            return duplicate;
        }
        requireReason(request.reason());
        if ("test".equals(request.accountKind()) && "ADMIN".equals(request.role())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "test accounts cannot be administrators");
        }

        UserAccount created = userService.createAccount(
                new RegisterRequest(
                        request.username(),
                        request.displayName(),
                        request.password()),
                request.role(),
                request.accountKind(),
                false);
        GmDtos.User response = GmDtos.User.from(requireUser(created.getId()));
        saveReceipt(actorId, idempotencyKey, fingerprint, response);
        appendAudit(actorId, "gm.user.create", created.getId(), requestId,
                request.reason(), "success");
        return response;
    }

    @Transactional
    public GmDtos.User updateUser(
            String actorId,
            String idempotencyKey,
            String requestId,
            String targetUserId,
            GmDtos.UpdateUserRequest request) {
        String fingerprint = fingerprint(
                "update",
                targetUserId,
                String.valueOf(request.expectedVersion()),
                request.role(),
                request.status(),
                request.reason());
        GmDtos.User duplicate = findUpdateReceipt(actorId, idempotencyKey, fingerprint);
        if (duplicate != null) {
            return duplicate;
        }
        requireReason(request.reason());
        if (request.role() == null && request.status() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "role or status is required");
        }

        GmUserRow current = requireUser(targetUserId);
        if (current.version() != request.expectedVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "GM_USER_VERSION_CONFLICT",
                    "账号版本已变化，请刷新后重试");
        }

        String nextRole = request.role() == null ? current.role() : request.role();
        String nextStatus = request.status() == null ? current.status() : request.status();
        protectLastAdministrator(current, nextRole, nextStatus);

        int updated = userMapper.updateAccess(
                targetUserId,
                nextRole,
                nextStatus,
                request.expectedVersion(),
                Instant.now().toEpochMilli());
        if (updated != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "GM_USER_VERSION_CONFLICT",
                    "账号版本已变化，请刷新后重试");
        }

        GmDtos.User response = GmDtos.User.from(requireUser(targetUserId));
        saveReceipt(actorId, idempotencyKey, fingerprint, response);
        appendAudit(actorId, "gm.user.update", targetUserId, requestId,
                request.reason(), "success");
        return response;
    }

    public GmDtos.AuditPage listAudit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "limit must be between 1 and 100");
        }
        List<GmDtos.AuditEvent> events = gmMapper.listAudit(limit).stream()
                .map(row -> new GmDtos.AuditEvent(
                        row.id(),
                        row.actorId(),
                        row.action(),
                        row.target(),
                        row.scope(),
                        row.occurredAtMs(),
                        row.requestId(),
                        row.reason(),
                        row.outcome()))
                .toList();
        return new GmDtos.AuditPage(events, null);
    }

    private void protectLastAdministrator(GmUserRow current, String nextRole, String nextStatus) {
        boolean removesActiveAdministrator =
                "ADMIN".equals(current.role())
                        && "active".equals(current.status())
                        && (!"ADMIN".equals(nextRole) || !"active".equals(nextStatus));
        if (!removesActiveAdministrator) {
            return;
        }

        List<String> lockedAdministratorIds = userMapper.lockAdminIds();
        long activeAdministratorCount = lockedAdministratorIds.stream()
                .map(userMapper::findAccessById)
                .filter(access -> access != null && "active".equals(access.status()))
                .count();
        if (activeAdministratorCount <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "GM_LAST_ADMIN",
                    "不能停用或降权最后一个可用管理员");
        }
    }

    private GmDtos.User findCreateReceipt(
            String actorId,
            String idempotencyKey,
            String fingerprint) {
        GmActionReceipt receipt = findReceipt(actorId, idempotencyKey, fingerprint);
        return receipt == null
                ? null
                : objectMapper.readValue(receipt.responseJson(), GmDtos.User.class);
    }

    private GmDtos.User findUpdateReceipt(
            String actorId,
            String idempotencyKey,
            String fingerprint) {
        GmActionReceipt receipt = findReceipt(actorId, idempotencyKey, fingerprint);
        return receipt == null
                ? null
                : objectMapper.readValue(receipt.responseJson(), GmDtos.User.class);
    }

    private GmActionReceipt findReceipt(
            String actorId,
            String idempotencyKey,
            String fingerprint) {
        requireIdempotencyKey(idempotencyKey);
        GmActionReceipt receipt = gmMapper.findReceiptForUpdate(actorId, idempotencyKey);
        if (receipt != null && !receipt.requestFingerprint().equals(fingerprint)) {
            throw new ApiException(HttpStatus.CONFLICT, "GM_IDEMPOTENCY_CONFLICT",
                    "同一幂等键不能用于不同请求");
        }
        return receipt;
    }

    private void saveReceipt(
            String actorId,
            String idempotencyKey,
            String fingerprint,
            GmDtos.User response) {
        gmMapper.insertReceipt(new GmActionReceipt(
                actorId,
                idempotencyKey,
                fingerprint,
                objectMapper.writeValueAsString(response),
                Instant.now().toEpochMilli()));
    }

    private void appendAudit(
            String actorId,
            String action,
            String target,
            String requestId,
            String reason,
            String outcome) {
        String normalizedRequestId =
                requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        gmMapper.insertAudit(new GmAuditEventRow(
                UUID.randomUUID().toString(),
                actorId,
                action,
                target,
                "standard",
                normalizedRequestId,
                reason.trim(),
                outcome,
                Instant.now().toEpochMilli()));
    }

    private GmUserRow requireUser(String userId) {
        GmUserRow user = gmMapper.findUserById(userId);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GM_USER_NOT_FOUND", "账号不存在");
        }
        return user;
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "reason is required");
        }
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 120) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "Idempotency-Key is required");
        }
    }

    private String fingerprint(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = java.util.Arrays.stream(values)
                    .map(value -> value == null ? "" : value)
                    .collect(java.util.stream.Collectors.joining("\u0000"));
            byte[] input = canonical.getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
