package com.fingermemory.server.migration;

import com.fingermemory.server.auth.CurrentUser;
import com.fingermemory.server.auth.FmUserPrincipal;
import com.fingermemory.server.migration.MigrationDtos.MigrationPreview;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/api/v1/migrations/local")
public class LocalMigrationController {
    private final LocalMigrationService migrationService;

    public LocalMigrationController(LocalMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @PostMapping("/preview")
    public MigrationPreview preview(Authentication authentication,
                                    @RequestBody JsonNode request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return migrationService.preview(principal.id(), request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ObjectNode importBatch(
            Authentication authentication,
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = "^[A-Za-z0-9_.-]{3,80}$") String batchId,
            @RequestBody JsonNode request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return migrationService.importBatch(principal.id(), batchId, request);
    }
}
