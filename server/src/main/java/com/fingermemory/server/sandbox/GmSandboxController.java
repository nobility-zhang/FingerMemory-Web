package com.fingermemory.server.sandbox;

import com.fingermemory.server.auth.CurrentUser;
import com.fingermemory.server.auth.FmUserPrincipal;
import com.fingermemory.server.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gm/sandboxes")
public class GmSandboxController {
    private final GmSandboxService sandboxService;
    private final GmSandboxAuditService auditService;

    public GmSandboxController(
            GmSandboxService sandboxService,
            GmSandboxAuditService auditService) {
        this.sandboxService = sandboxService;
        this.auditService = auditService;
    }

    @GetMapping
    public GmSandboxDtos.Page list(
            Authentication authentication,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return sandboxService.listOwned(principal.id(), limit, cursor);
    }

    @PostMapping
    public ResponseEntity<GmSandboxDtos.Sandbox> create(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody GmSandboxDtos.CreateSandboxRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        try {
            GmSandboxDtos.Sandbox sandbox = sandboxService.create(
                    principal.id(),
                    idempotencyKey,
                    requestId,
                    request);
            return ResponseEntity.status(HttpStatus.CREATED).body(sandbox);
        } catch (ApiException exception) {
            recordRejected(
                    principal.id(),
                    "gm.sandbox.create",
                    "new",
                    requestId,
                    request.reason(),
                    exception);
            throw exception;
        }
    }

    @PostMapping("/{id}/commands")
    public GmSandboxDtos.CommandReceipt execute(
            Authentication authentication,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody GmSandboxDtos.CommandRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        try {
            return sandboxService.execute(
                    principal.id(),
                    id,
                    idempotencyKey,
                    requestId,
                    request);
        } catch (ApiException exception) {
            recordRejected(
                    principal.id(),
                    "gm.sandbox." + request.command(),
                    id,
                    requestId,
                    request.reason(),
                    exception);
            throw exception;
        }
    }

    private void recordRejected(
            String actorId,
            String action,
            String target,
            String requestId,
            String reason,
            ApiException exception) {
        try {
            auditService.recordRejected(
                    actorId,
                    action,
                    target,
                    requestId,
                    reason,
                    exception.getMessage());
        } catch (RuntimeException auditFailure) {
            exception.addSuppressed(auditFailure);
        }
    }
}
