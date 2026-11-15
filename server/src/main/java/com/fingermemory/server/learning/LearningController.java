package com.fingermemory.server.learning;

import com.fingermemory.server.auth.CurrentUser;
import com.fingermemory.server.auth.FmUserPrincipal;
import com.fingermemory.server.learning.LearningDtos.CreateSessionRequest;
import com.fingermemory.server.learning.LearningDtos.CreateWordRequest;
import com.fingermemory.server.learning.LearningDtos.PracticeResponseRequest;
import com.fingermemory.server.learning.LearningDtos.PracticeResponseView;
import com.fingermemory.server.learning.LearningDtos.PracticeCompletionRequest;
import com.fingermemory.server.learning.LearningDtos.PracticeCompletionView;
import com.fingermemory.server.learning.LearningDtos.QueuePlanView;
import com.fingermemory.server.learning.LearningDtos.SessionEnvelope;
import com.fingermemory.server.learning.LearningDtos.SessionListResponse;
import com.fingermemory.server.learning.LearningDtos.SessionPatchRequest;
import com.fingermemory.server.learning.LearningDtos.WordListResponse;
import com.fingermemory.server.learning.LearningDtos.WordStudyItemView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class LearningController {
    private final LearningQueryService queryService;
    private final SessionService sessionService;

    public LearningController(LearningQueryService queryService, SessionService sessionService) {
        this.queryService = queryService;
        this.sessionService = sessionService;
    }

    @GetMapping("/lexicons/{lexiconId}/words")
    public WordListResponse listWords(Authentication authentication,
                                      @PathVariable String lexiconId) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return queryService.listWords(principal.id(), lexiconId);
    }

    @PostMapping("/lexicons/{lexiconId}/words")
    @ResponseStatus(HttpStatus.CREATED)
    public WordStudyItemView createWord(Authentication authentication,
                                        @PathVariable String lexiconId,
                                        @Valid @RequestBody CreateWordRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return queryService.createWord(principal.id(), lexiconId, request);
    }

    @GetMapping("/queue/plan")
    public QueuePlanView queuePlan(Authentication authentication,
                                   @RequestParam String timeZone,
                                   @RequestParam(required = false) Integer newLimit,
                                   @RequestParam(required = false) Integer reviewLimit) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return queryService.queuePlan(principal.id(), timeZone, newLimit, reviewLimit);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionEnvelope createSession(Authentication authentication,
                                         @Valid @RequestBody CreateSessionRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return sessionService.create(principal.id(), request);
    }

    @GetMapping("/sessions")
    public SessionListResponse listSessions(Authentication authentication) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return sessionService.listActive(principal.id());
    }

    @GetMapping("/sessions/{id}")
    public SessionEnvelope getSession(Authentication authentication, @PathVariable String id) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return sessionService.get(principal.id(), id);
    }

    @PatchMapping("/sessions/{id}")
    public SessionEnvelope patchSession(Authentication authentication, @PathVariable String id,
                                        @Valid @RequestBody SessionPatchRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return sessionService.patch(principal.id(), id, request);
    }

    @PostMapping("/sessions/{id}/responses")
    @ResponseStatus(HttpStatus.CREATED)
    public PracticeResponseView submitResponse(Authentication authentication,
                                               @PathVariable String id,
                                               @Valid @RequestBody PracticeResponseRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return sessionService.submitResponse(principal.id(), id, request);
    }

    @PostMapping("/sessions/{id}/practice")
    @ResponseStatus(HttpStatus.CREATED)
    public PracticeCompletionView submitPractice(Authentication authentication,
                                                 @PathVariable String id,
                                                 @Valid @RequestBody
                                                 PracticeCompletionRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return sessionService.submitPracticeCompletion(principal.id(), id, request);
    }
}
