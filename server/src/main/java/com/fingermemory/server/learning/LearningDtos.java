package com.fingermemory.server.learning;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.databind.node.ObjectNode;

public final class LearningDtos {
    private LearningDtos() {
    }

    public record CreateWordRequest(
            @NotBlank @Size(max = 512) String term,
            @NotBlank @Size(max = 4000) String meaning,
            @Size(max = 20) List<@Size(max = 512) String> acceptedAnswers,
            @Size(max = 20) List<@Size(max = 2000) String> hints) {
    }

    public record WordStudyItemView(
            ObjectNode wordEntry,
            ObjectNode studyItem,
            ObjectNode lexiconItem,
            long dataVersion) {
    }

    public record WordEntryItemView(
            ObjectNode wordEntry,
            ObjectNode studyItem,
            ObjectNode lexiconItem,
            ObjectNode reviewState,
            long reviewVersion) {
    }

    public record WordListResponse(
            List<WordEntryItemView> items,
            long dataVersion) {
    }

    public record QueueEntryView(
            ObjectNode studyItem,
            ObjectNode wordEntry,
            ObjectNode reviewState,
            String source) {
    }

    public record QueuePlanView(
            long at,
            String timeZone,
            long dayStartAt,
            long dayEndAt,
            ObjectNode settings,
            int dueAvailableCount,
            int newAvailableCount,
            List<QueueEntryView> selected,
            long dataVersion) {
    }

    public record CreateSessionRequest(
            @NotBlank @Pattern(regexp = "^(flashcard|keyboard-recall|lyrics-scroll|danmaku-stream|card-rain)$")
            String modeId,
            @NotBlank @Size(max = 64) String timeZone,
            Integer newLimit,
            Integer reviewLimit,
            boolean reduceMotion) {
    }

    public record SessionEnvelope(
            ObjectNode session,
            List<WordEntryItemView> items,
            long dataVersion) {
    }

    public record SessionListResponse(List<SessionEnvelope> sessions, long dataVersion) {
    }

    public record SessionPatchRequest(
            @PositiveOrZero long expectedDataVersion,
            @PositiveOrZero long expectedVersion,
            String status,
            Integer currentIndex,
            String currentStudyItemId,
            ObjectNode interactions,
            Double speed,
            Boolean reduceMotion) {
    }

    public record PracticeResponseRequest(
            @NotBlank @Size(max = 128) String practiceAttemptId,
            @NotBlank @Size(max = 128) String reviewSubmissionId,
            @NotBlank @Size(max = 128) String studyItemId,
            String response,
            boolean answerWasHidden,
            boolean hintUsed,
            boolean answerRevealed,
            @Pattern(regexp = "^(typed-recall|flashcard-self-report|guided-practice)$")
            String assessmentType,
            @Min(1) @Max(2) Integer assessmentPolicyVersion,
            @Pattern(regexp = "^(prompt|attempt|reveal-for-check|rate)$")
            String assessmentStage,
            Boolean priorExposure,
            Boolean explicitNotRemembered,
            boolean abandoned,
            @PositiveOrZero long expectedDataVersion,
            @PositiveOrZero long expectedSessionVersion) {
    }

    public record PracticeResponseView(
            ObjectNode attempt,
            ObjectNode pendingAnswer,
            ObjectNode session,
            long dataVersion) {
    }

    public record PracticeCompletionRequest(
            @NotBlank @Size(max = 128) String practiceAttemptId,
            @NotBlank @Size(max = 128) String studyItemId,
            String response,
            boolean answerWasHidden,
            boolean hintUsed,
            boolean answerRevealed,
            @Pattern(regexp = "^(typed-recall|flashcard-self-report|guided-practice)$")
            String assessmentType,
            @Min(1) @Max(2) Integer assessmentPolicyVersion,
            @Pattern(regexp = "^(prompt|attempt|reveal-for-check|rate)$")
            String assessmentStage,
            Boolean priorExposure,
            Boolean explicitNotRemembered,
            boolean abandoned,
            @NotBlank @Pattern(regexp = "^(completed|skipped)$") String outcome,
            @PositiveOrZero long expectedDataVersion,
            @PositiveOrZero long expectedSessionVersion) {
    }

    public record PracticeCompletionView(
            ObjectNode attempt,
            ObjectNode session,
            long dataVersion) {
    }

    public record ReviewPreviewRequest(
            @NotBlank @Size(max = 128) String studyItemId,
            @PositiveOrZero long expectedDataVersion,
            @PositiveOrZero long expectedSessionVersion,
            @PositiveOrZero long expectedReviewVersion) {
    }

    public record ReviewEvidence(
            boolean userConfirmedRating,
            boolean answerWasHidden,
            boolean hintUsed,
            boolean answerRevealed,
            @Pattern(regexp = "^(typed-recall|flashcard-self-report|guided-practice)$")
            String assessmentType,
            @Min(1) @Max(2) Integer assessmentPolicyVersion,
            @Pattern(regexp = "^(prompt|attempt|reveal-for-check|rate)$")
            String assessmentStage,
            Boolean priorExposure,
            Boolean explicitNotRemembered,
            boolean abandoned) {
    }

    public record ReviewSubmissionRequest(
            @NotBlank @Pattern(regexp = "^(again|hard|good|easy)$") String rating,
            @PositiveOrZero long expectedDataVersion,
            @PositiveOrZero long expectedReviewVersion,
            @PositiveOrZero long expectedSessionVersion,
            @Valid ReviewEvidence evidence) {
    }

    public record ReviewPreviewView(
            ObjectNode previews,
            String schedulerPackageVersion,
            String schedulerModelVersion,
            String schedulerConfigHash,
            long reviewedAtMs,
            long reviewVersion,
            long sessionVersion,
            long dataVersion) {
    }
}
