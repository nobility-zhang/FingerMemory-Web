package com.fingermemory.server.backup;

import com.fingermemory.server.auth.CurrentUser;
import com.fingermemory.server.auth.FmUserPrincipal;
import com.fingermemory.server.backup.BackupDtos.BackupPreview;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/api/v1/backup")
public class AccountBackupController {
    private final AccountBackupService backupService;

    public AccountBackupController(AccountBackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping("/export")
    public ObjectNode export(Authentication authentication) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return backupService.export(principal.id());
    }

    @PostMapping("/import/preview")
    public BackupPreview preview(Authentication authentication,
                                 @RequestBody JsonNode request) {
        CurrentUser.require(authentication);
        return backupService.preview(request);
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ObjectNode restore(Authentication authentication,
                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                              @RequestBody JsonNode request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return backupService.restore(principal.id(), idempotencyKey, request);
    }
}
