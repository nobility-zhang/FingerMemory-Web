package com.fingermemory.server.admin;

import com.fingermemory.server.auth.CurrentUser;
import com.fingermemory.server.auth.FmUserPrincipal;
import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.content.GmContentAuditService;
import com.fingermemory.server.content.GmContentDtos;
import com.fingermemory.server.content.GmContentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/gm/content")
public class GmContentController {
    private final GmContentService contentService;
    private final GmContentAuditService auditService;

    public GmContentController(
            GmContentService contentService,
            GmContentAuditService auditService) {
        this.contentService = contentService;
        this.auditService = auditService;
    }

    @GetMapping
    public GmContentDtos.ContentPage list(
            Authentication authentication,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return contentService.list(principal.id(), limit, cursor);
    }

    @GetMapping("/{id}/preview")
    public GmContentDtos.Preview preview(
            Authentication authentication,
            @PathVariable String id) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return contentService.preview(principal.id(), id);
    }

    @GetMapping("/{id}/assets/{*assetPath}")
    public ResponseEntity<org.springframework.core.io.Resource> asset(
            Authentication authentication,
            @PathVariable String id,
            @PathVariable String assetPath) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        String normalized = assetPath.replaceFirst("^/", "");
        GmContentService.ArchiveAssetResource asset =
                contentService.asset(principal.id(), id, normalized);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.mime()))
                .header("Cache-Control", "private, max-age=3600, immutable")
                .body(asset.resource());
    }

    @PostMapping(
            path = "/drafts",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GmContentDtos.Content> upload(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @RequestParam String reason,
            @RequestPart("file") MultipartFile file) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        try {
            GmContentDtos.Content content = contentService.upload(
                    principal.id(),
                    idempotencyKey,
                    requestId,
                    reason,
                    file);
            return ResponseEntity.status(HttpStatus.CREATED).body(content);
        } catch (ApiException exception) {
            recordRejected(
                    principal.id(),
                    "gm.content.upload",
                    "new",
                    requestId,
                    reason,
                    exception);
            throw exception;
        }
    }

    @PostMapping("/{id}/actions")
    public GmContentDtos.Content act(
            Authentication authentication,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody GmContentDtos.ContentAction request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        try {
            return contentService.act(
                    principal.id(),
                    id,
                    idempotencyKey,
                    requestId,
                    request);
        } catch (ApiException exception) {
            recordRejected(
                    principal.id(),
                    "gm.content." + request.action(),
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
