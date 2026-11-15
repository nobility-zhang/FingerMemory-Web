package com.fingermemory.server.learning;

import com.fingermemory.server.auth.CurrentUser;
import com.fingermemory.server.auth.FmUserPrincipal;
import com.fingermemory.server.learning.LearningDtos.ReviewPreviewRequest;
import com.fingermemory.server.learning.LearningDtos.ReviewPreviewView;
import com.fingermemory.server.learning.LearningDtos.ReviewSubmissionRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}")
public class ReviewController {
    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/review-preview")
    public ReviewPreviewView preview(Authentication authentication,
                                     @PathVariable String sessionId,
                                     @Valid @RequestBody ReviewPreviewRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return reviewService.preview(principal.id(), sessionId, request);
    }

    @PostMapping("/reviews")
    public ObjectNode submit(Authentication authentication,
                             @PathVariable String sessionId,
                             @RequestHeader("Idempotency-Key") String submissionId,
                             @Valid @RequestBody ReviewSubmissionRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return reviewService.submit(principal.id(), sessionId, submissionId, request);
    }
}
