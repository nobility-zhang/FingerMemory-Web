package com.fingermemory.server.battle;

import com.fingermemory.server.auth.CurrentUser;
import com.fingermemory.server.auth.FmUserPrincipal;
import com.fingermemory.server.battle.BattleDtos.BattleCommandRequest;
import com.fingermemory.server.battle.BattleDtos.BattleEnvelope;
import com.fingermemory.server.battle.BattleDtos.CompanionSkillRequest;
import com.fingermemory.server.battle.BattleDtos.CreateBattleRequest;
import com.fingermemory.server.battle.BattleDtos.EndTurnRequest;
import com.fingermemory.server.battle.BattleDtos.LearningClaimRequest;
import com.fingermemory.server.battle.BattleDtos.PlayCardRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/battles")
public class BattleController {
    private final BattleService battleService;

    public BattleController(BattleService battleService) {
        this.battleService = battleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BattleEnvelope create(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String actionId,
            @Valid @RequestBody CreateBattleRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return battleService.create(principal.id(), actionId, request);
    }

    @GetMapping("/{battleId}")
    public BattleEnvelope get(
            Authentication authentication,
            @PathVariable String battleId) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return battleService.get(principal.id(), battleId);
    }

    @GetMapping
    public List<BattleEnvelope> listActive(
            Authentication authentication,
            @RequestParam(defaultValue = "20") int limit) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return battleService.listActive(principal.id(), limit);
    }

    @PostMapping("/{battleId}/learning-claims")
    public BattleEnvelope claimLearning(
            Authentication authentication,
            @PathVariable String battleId,
            @RequestHeader("Idempotency-Key") String actionId,
            @Valid @RequestBody LearningClaimRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return battleService.claimLearning(principal.id(), battleId, actionId, request);
    }

    @PostMapping("/{battleId}/cards")
    public BattleEnvelope playCard(
            Authentication authentication,
            @PathVariable String battleId,
            @RequestHeader("Idempotency-Key") String actionId,
            @Valid @RequestBody PlayCardRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return battleService.playCard(principal.id(), battleId, actionId, request);
    }

    @PostMapping("/{battleId}/companion-skill")
    public BattleEnvelope useCompanionSkill(
            Authentication authentication,
            @PathVariable String battleId,
            @RequestHeader("Idempotency-Key") String actionId,
            @Valid @RequestBody CompanionSkillRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return battleService.useCompanionSkill(principal.id(), battleId, actionId, request);
    }

    @PostMapping("/{battleId}/end-turn")
    public BattleEnvelope endTurn(
            Authentication authentication,
            @PathVariable String battleId,
            @RequestHeader("Idempotency-Key") String actionId,
            @Valid @RequestBody EndTurnRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return battleService.endTurn(principal.id(), battleId, actionId, request);
    }
}
