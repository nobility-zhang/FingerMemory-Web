package com.fingermemory.server.sandbox;

import com.fingermemory.server.admin.GmActionReceipt;
import com.fingermemory.server.admin.GmAuditEventRow;
import com.fingermemory.server.admin.GmMapper;
import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.common.JsonSupport;
import com.fingermemory.server.sandbox.GmSandboxDtos.CommandReceipt;
import com.fingermemory.server.sandbox.GmSandboxDtos.CommandRequest;
import com.fingermemory.server.sandbox.GmSandboxDtos.CreateSandboxRequest;
import com.fingermemory.server.sandbox.GmSandboxDtos.Page;
import com.fingermemory.server.sandbox.GmSandboxDtos.Sandbox;
import com.fingermemory.server.sandbox.GmSandboxDtos.SandboxState;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class GmSandboxService {
    private static final String SCOPE = "sandbox";
    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("^[A-Za-z0-9_.:-]{3,128}$");

    private final GmSandboxMapper sandboxMapper;
    private final GmMapper gmMapper;
    private final GmSandboxEngine engine;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GmSandboxService(
            GmSandboxMapper sandboxMapper,
            GmMapper gmMapper,
            GmSandboxEngine engine,
            ObjectMapper objectMapper,
            Clock clock) {
        this.sandboxMapper = sandboxMapper;
        this.gmMapper = gmMapper;
        this.engine = engine;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public Page listOwned(String actorId, int limit, String cursor) {
        if (limit < 1 || limit > 100) {
            throw validation("limit 必须在 1 到 100 之间");
        }
        String normalizedCursor = cursor == null || cursor.isBlank() ? null : cursor.trim();
        if (normalizedCursor != null && normalizedCursor.length() > 36) {
            throw validation("cursor 无效");
        }
        List<GmSandboxRow> rows =
                sandboxMapper.listOwned(actorId, normalizedCursor, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<GmSandboxRow> pageRows = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore ? pageRows.get(pageRows.size() - 1).id() : null;
        return new Page(
                pageRows.stream().map(this::toView).toList(),
                nextCursor);
    }

    @Transactional
    public Sandbox create(
            String actorId,
            String idempotencyKey,
            String requestId,
            CreateSandboxRequest request) {
        requireIdempotencyKey(idempotencyKey);
        requireReason(request.reason());
        String fingerprint = createFingerprint(request);
        GmActionReceipt existingReceipt =
                gmMapper.findReceiptForUpdate(actorId, idempotencyKey);
        if (existingReceipt != null) {
            if (!fingerprint.equals(existingReceipt.requestFingerprint())) {
                throw conflict(
                        "GM_IDEMPOTENCY_CONFLICT",
                        "同一幂等键不能用于不同请求");
            }
            return objectMapper.readValue(existingReceipt.responseJson(), Sandbox.class);
        }
        if (!GmSandboxCatalog.TEMPLATE_ID.equals(request.templateId())) {
            throw validation("不支持的 sandbox templateId");
        }
        if (request.contentInstallationId() != null
                && !request.contentInstallationId().isBlank()) {
            throw conflict(
                    "GM_SANDBOX_CONTENT_UNAVAILABLE",
                    "内容安装 sandbox 将在内容包运行时接入后可用");
        }

        long now = clock.instant().toEpochMilli();
        String sandboxId = UUID.randomUUID().toString();
        GmSandboxState state = GmSandboxCatalog.initialState();
        GmSandboxRow row = new GmSandboxRow(
                actorId,
                sandboxId,
                GmSandboxCatalog.TEMPLATE_ID,
                null,
                0L,
                UUID.randomUUID().toString(),
                objectMapper.writeValueAsString(state),
                now,
                now);
        sandboxMapper.insert(row);
        Sandbox response = toView(row);
        gmMapper.insertReceipt(new GmActionReceipt(
                actorId,
                idempotencyKey,
                fingerprint,
                objectMapper.writeValueAsString(response),
                now));
        appendAudit(
                actorId,
                "gm.sandbox.create",
                sandboxId,
                requestId,
                request.reason());
        return response;
    }

    @Transactional
    public CommandReceipt execute(
            String actorId,
            String sandboxId,
            String idempotencyKey,
            String requestId,
            CommandRequest request) {
        requireIdempotencyKey(idempotencyKey);
        requireReason(request.reason());
        GmSandboxEngine.Command command = engine.parse(request.command(), request.args());
        String fingerprint = commandFingerprint(sandboxId, request);

        GmSandboxRow row = sandboxMapper.findForUpdate(actorId, sandboxId);
        if (row == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "GM_SANDBOX_NOT_FOUND",
                    "sandbox 不存在");
        }
        GmSandboxReceiptRow receipt = sandboxMapper.findReceipt(
                actorId,
                sandboxId,
                request.dataEpoch(),
                idempotencyKey);
        if (receipt != null) {
            if (!fingerprint.equals(receipt.requestFingerprint())) {
                throw conflict(
                        "GM_SANDBOX_IDEMPOTENCY_CONFLICT",
                        "同一幂等键不能用于不同 sandbox 请求");
            }
            if (!request.dataEpoch().equals(row.dataEpoch())
                    && !engine.isReset(command)) {
                throw epochConflict();
            }
            return new CommandReceipt(
                    idempotencyKey,
                    "duplicate",
                    SCOPE,
                    toView(row));
        }
        if (!request.dataEpoch().equals(row.dataEpoch())) {
            throw epochConflict();
        }
        if (request.expectedVersion() != row.version()) {
            throw versionConflict();
        }

        GmSandboxState current = readState(row);
        GmSandboxState next = engine.apply(current, command);
        String nextEpoch = engine.isReset(command)
                ? UUID.randomUUID().toString()
                : row.dataEpoch();
        long nextVersion = row.version() + 1;
        long now = clock.instant().toEpochMilli();
        int updated = sandboxMapper.updateState(
                actorId,
                sandboxId,
                row.version(),
                nextVersion,
                nextEpoch,
                objectMapper.writeValueAsString(next),
                now);
        if (updated != 1) {
            throw versionConflict();
        }
        GmSandboxRow nextRow = new GmSandboxRow(
                row.ownerAdminId(),
                row.id(),
                row.templateId(),
                row.contentInstallationId(),
                nextVersion,
                nextEpoch,
                objectMapper.writeValueAsString(next),
                row.createdAtMs(),
                now);
        CommandReceipt response = new CommandReceipt(
                idempotencyKey,
                "applied",
                SCOPE,
                toView(nextRow));
        sandboxMapper.insertReceipt(new GmSandboxReceiptRow(
                actorId,
                sandboxId,
                request.dataEpoch(),
                idempotencyKey,
                fingerprint,
                objectMapper.writeValueAsString(response),
                nextVersion,
                now));
        appendAudit(
                actorId,
                "gm.sandbox." + request.command(),
                sandboxId,
                requestId,
                request.reason());
        return response;
    }

    private Sandbox toView(GmSandboxRow row) {
        GmSandboxState state = readState(row);
        return new Sandbox(
                row.id(),
                SCOPE,
                row.ownerAdminId(),
                row.templateId(),
                row.contentInstallationId(),
                row.version(),
                row.dataEpoch(),
                new SandboxState(
                        state.schemaVersion(),
                        state.fixtureVersion(),
                        state.fixtureKey(),
                        state.currentNodeId(),
                        state.encounterId(),
                        state.clockMode(),
                        state.virtualNowMs(),
                        state.randomSeed(),
                        state.deck(),
                        state.unlockedCharacters(),
                        GmSandboxCatalog.NODES,
                        GmSandboxCatalog.CARDS,
                        GmSandboxCatalog.CHARACTERS));
    }

    private GmSandboxState readState(GmSandboxRow row) {
        GmSandboxState state;
        try {
            state = objectMapper.readValue(row.stateJson(), GmSandboxState.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Stored GM sandbox state is invalid", exception);
        }
        if (state.schemaVersion() != GmSandboxCatalog.SCHEMA_VERSION
                || state.fixtureVersion() != GmSandboxCatalog.FIXTURE_VERSION_NUMBER
                || !GmSandboxCatalog.TEMPLATE_ID.equals(row.templateId())
                || !GmSandboxCatalog.FIXTURE_VERSION.equals(state.fixtureKey())
                || !GmSandboxCatalog.nodes().contains(state.currentNodeId())
                || (!"real".equals(state.clockMode())
                && !"fixed".equals(state.clockMode()))
                || state.deck().size() > GmSandboxCatalog.MAX_DECK_SIZE
                || !GmSandboxCatalog.characters().containsAll(state.unlockedCharacters())) {
            throw new IllegalStateException("Stored GM sandbox state is incompatible");
        }
        return state;
    }

    private String createFingerprint(CreateSandboxRequest request) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("operation", "sandbox-create");
        value.set("request", objectMapper.valueToTree(request));
        return JsonSupport.fingerprint(objectMapper, value);
    }

    private String commandFingerprint(String sandboxId, CommandRequest request) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("operation", "sandbox-command");
        value.put("sandboxId", sandboxId);
        value.set("request", objectMapper.valueToTree(request));
        return JsonSupport.fingerprint(objectMapper, value);
    }

    private void appendAudit(
            String actorId,
            String action,
            String sandboxId,
            String requestId,
            String reason) {
        gmMapper.insertAudit(new GmAuditEventRow(
                UUID.randomUUID().toString(),
                actorId,
                action,
                sandboxId,
                SCOPE,
                normalizedRequestId(requestId),
                reason.trim(),
                "success",
                clock.instant().toEpochMilli()));
    }

    private String normalizedRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId.length() <= 120 ? requestId : requestId.substring(0, 120);
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw validation("reason 不能为空");
        }
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw validation("Idempotency-Key 必须为 3-128 位安全字符");
        }
    }

    private ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private ApiException versionConflict() {
        return conflict(
                "GM_SANDBOX_VERSION_CONFLICT",
                "sandbox 版本已变化，请刷新后重试");
    }

    private ApiException epochConflict() {
        return conflict(
                "GM_SANDBOX_EPOCH_CONFLICT",
                "sandbox dataEpoch 已变化，旧动作不能重新生效");
    }
}
