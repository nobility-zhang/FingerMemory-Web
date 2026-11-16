package com.fingermemory.server.admin;

import com.fingermemory.server.auth.CurrentUser;
import com.fingermemory.server.auth.FmUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gm")
public class GmController {
    private final GmService gmService;

    public GmController(GmService gmService) {
        this.gmService = gmService;
    }

    @GetMapping("/context")
    public GmDtos.Context context() {
        return gmService.context();
    }

    @GetMapping("/users")
    public GmDtos.UserPage users(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor) {
        return gmService.listUsers(limit, cursor);
    }

    @PostMapping("/users")
    public ResponseEntity<GmDtos.User> createUser(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody GmDtos.CreateUserRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        GmDtos.User user = gmService.createUser(
                principal.id(),
                idempotencyKey,
                requestId,
                request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PatchMapping("/users/{id}")
    public GmDtos.User updateUser(
            Authentication authentication,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody GmDtos.UpdateUserRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return gmService.updateUser(
                principal.id(),
                idempotencyKey,
                requestId,
                id,
                request);
    }

    @GetMapping("/audit")
    public GmDtos.AuditPage audit(@RequestParam(defaultValue = "20") int limit) {
        return gmService.listAudit(limit);
    }
}
