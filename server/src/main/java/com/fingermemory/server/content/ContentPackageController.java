package com.fingermemory.server.content;

import com.fingermemory.server.auth.CurrentUser;
import com.fingermemory.server.auth.FmUserPrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/content-packages")
public class ContentPackageController {
    private final ContentPackageService contentPackageService;

    public ContentPackageController(ContentPackageService contentPackageService) {
        this.contentPackageService = contentPackageService;
    }

    @PostMapping(
            path = "/preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContentPackageDtos.Preview preview(
            Authentication authentication,
            @RequestPart("file") MultipartFile file) {
        CurrentUser.require(authentication);
        return contentPackageService.preview(file);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContentPackageDtos.InstallResult> install(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestPart("file") MultipartFile file) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        ContentPackageDtos.InstallResult result =
                contentPackageService.install(principal.id(), idempotencyKey, file);
        return ResponseEntity
                .status("installed".equals(result.outcome())
                        ? HttpStatus.CREATED
                        : HttpStatus.OK)
                .body(result);
    }

    @GetMapping
    public List<ContentPackageDtos.Installation> list(
            Authentication authentication,
            @RequestParam(defaultValue = "50") int limit) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return contentPackageService.listOwned(principal.id(), limit);
    }

    @GetMapping("/{id}")
    public ContentPackageDtos.Installation get(
            Authentication authentication,
            @PathVariable String id) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return contentPackageService.getOwned(principal.id(), id);
    }

    @GetMapping("/{id}/assets/{*assetPath}")
    public ResponseEntity<org.springframework.core.io.Resource> asset(
            Authentication authentication,
            @PathVariable String id,
            @PathVariable String assetPath) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        String normalized = assetPath.replaceFirst("^/", "");
        ContentPackageService.AssetResource asset =
                contentPackageService.asset(principal.id(), id, normalized);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.mime()))
                .body(asset.resource());
    }
}
