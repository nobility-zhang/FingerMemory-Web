package com.fingermemory.server.adventure;

import com.fingermemory.server.auth.CurrentUser;
import com.fingermemory.server.auth.FmUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/v1/adventures")
public class AdventureController {
    private final AdventureService adventureService;

    public AdventureController(AdventureService adventureService) {
        this.adventureService = adventureService;
    }

    @GetMapping
    public List<AdventureDtos.Run> listActive(
            Authentication authentication,
            @RequestParam(defaultValue = "20") int limit) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return adventureService.listActive(principal.id(), limit);
    }

    @PostMapping
    public ResponseEntity<AdventureDtos.StartResult> start(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String actionId,
            @Valid @RequestBody AdventureDtos.StartRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        AdventureDtos.StartResult result =
                adventureService.start(principal.id(), actionId, request);
        return ResponseEntity
                .status("created".equals(result.outcome())
                        ? HttpStatus.CREATED
                        : HttpStatus.OK)
                .body(result);
    }

    @GetMapping("/{runId}")
    public AdventureDtos.Run get(
            Authentication authentication,
            @PathVariable String runId) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return adventureService.get(principal.id(), runId);
    }

    @GetMapping("/companions")
    public List<String> companions(Authentication authentication) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return adventureService.listUnlockedCompanions(principal.id());
    }

    @GetMapping("/recollections")
    public List<AdventureDtos.Recollection> recollections(
            Authentication authentication,
            @RequestParam(defaultValue = "50") int limit) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return adventureService.listRecollections(principal.id(), limit);
    }

    @PostMapping("/{runId}/encounters")
    public ResponseEntity<AdventureDtos.Encounter> startEncounter(
            Authentication authentication,
            @PathVariable String runId,
            @RequestHeader("Idempotency-Key") String actionId) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        AdventureDtos.Encounter result =
                adventureService.startEncounter(principal.id(), runId, actionId);
        return ResponseEntity
                .status("started".equals(result.outcome())
                        ? HttpStatus.CREATED
                        : HttpStatus.OK)
                .body(result);
    }

    @PostMapping("/{runId}/actions")
    public AdventureDtos.ActionResult act(
            Authentication authentication,
            @PathVariable String runId,
            @RequestHeader("Idempotency-Key") String actionId,
            @Valid @RequestBody AdventureDtos.ActionRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return adventureService.act(principal.id(), runId, actionId, request);
    }
}
