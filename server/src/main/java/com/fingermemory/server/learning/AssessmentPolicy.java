package com.fingermemory.server.learning;

import com.fingermemory.server.common.JsonSupport;
import com.fingermemory.server.learning.LearningDtos.ReviewEvidence;
import java.util.Set;
import tools.jackson.databind.node.ObjectNode;

/**
 * Keeps the v2 assessment gate in one place so the JSON snapshot, HTTP
 * evidence and worker-facing review flow cannot drift apart.
 */
public final class AssessmentPolicy {
    public static final int CURRENT_POLICY_VERSION = 2;
    private static final Set<String> TYPES = Set.of(
            "typed-recall", "flashcard-self-report", "guided-practice");
    private static final Set<String> STAGES = Set.of(
            "prompt", "attempt", "reveal-for-check", "rate");

    private AssessmentPolicy() {
    }

    public record Metadata(
            String type,
            int policyVersion,
            String stage,
            boolean priorExposure,
            boolean explicitNotRemembered) {

        public static Metadata from(
                String type,
                Integer policyVersion,
                String stage,
                Boolean priorExposure,
                Boolean explicitNotRemembered) {
            int resolvedVersion = policyVersion == null ? 1 : policyVersion;
            return new Metadata(
                    type == null || type.isBlank() ? "typed-recall" : type,
                    resolvedVersion,
                    stage == null || stage.isBlank() ? "attempt" : stage,
                    Boolean.TRUE.equals(priorExposure),
                    Boolean.TRUE.equals(explicitNotRemembered));
        }

        public static Metadata fromJson(ObjectNode value) {
            if (!value.has("assessmentPolicyVersion")) {
                return from(null, null, null, null, null);
            }
            return from(
                    value.path("assessmentType").asText(null),
                    value.path("assessmentPolicyVersion").isInt()
                            ? value.path("assessmentPolicyVersion").asInt()
                            : null,
                    value.path("assessmentStage").asText(null),
                    value.path("priorExposure").isBoolean()
                            ? value.path("priorExposure").asBoolean()
                            : false,
                    value.path("explicitNotRemembered").isBoolean()
                            ? value.path("explicitNotRemembered").asBoolean()
                            : false);
        }

        public boolean isV2() {
            return policyVersion == CURRENT_POLICY_VERSION;
        }

        public void writeTo(ObjectNode target) {
            if (!isV2()) {
                return;
            }
            target.put("assessmentType", type);
            target.put("assessmentPolicyVersion", policyVersion);
            target.put("assessmentStage", stage);
            target.put("priorExposure", priorExposure);
            target.put("explicitNotRemembered", explicitNotRemembered);
        }

        public boolean sameAs(Metadata other) {
            return type.equals(other.type())
                    && policyVersion == other.policyVersion()
                    && stage.equals(other.stage())
                    && priorExposure == other.priorExposure()
                    && explicitNotRemembered == other.explicitNotRemembered();
        }

        public void validateStructure() {
            if (policyVersion != 1 && policyVersion != CURRENT_POLICY_VERSION) {
                throw JsonSupport.validation("assessmentPolicyVersion 仅支持 1 或 2");
            }
            if (isV2() && (!TYPES.contains(type) || !STAGES.contains(stage))) {
                throw JsonSupport.validation("v2 assessmentType 或 assessmentStage 无效");
            }
        }
    }

    public static String rejection(ReviewEvidence evidence, String rating) {
        Metadata metadata = Metadata.from(
                evidence.assessmentType(),
                evidence.assessmentPolicyVersion(),
                evidence.assessmentStage(),
                evidence.priorExposure(),
                evidence.explicitNotRemembered());
        metadata.validateStructure();
        if (!evidence.userConfirmedRating()) {
            return "rating-not-confirmed";
        }
        if (evidence.abandoned()) {
            return "abandoned";
        }
        if (!metadata.isV2()) {
            if (!evidence.answerWasHidden()) {
                return "answer-not-hidden";
            }
            if (evidence.hintUsed()) {
                return "hint-used";
            }
            if (evidence.answerRevealed()) {
                return "answer-revealed";
            }
            return null;
        }
        if ("guided-practice".equals(metadata.type())) {
            return "guided-practice";
        }
        if (metadata.explicitNotRemembered() && !"again".equals(rating)) {
            return "explicit-not-remembered-requires-again";
        }
        if ("flashcard-self-report".equals(metadata.type())) {
            if (!"reveal-for-check".equals(metadata.stage())
                    || !evidence.answerWasHidden()
                    || !evidence.answerRevealed()) {
                return "flashcard-requires-reveal-for-check";
            }
            if (metadata.priorExposure() && !metadata.explicitNotRemembered()) {
                return "prior-exposure";
            }
            if (evidence.hintUsed() && !metadata.explicitNotRemembered()) {
                return "hint-used";
            }
            return null;
        }
        if (!evidence.answerWasHidden() && !metadata.explicitNotRemembered()) {
            return "typed-recall-requires-hidden-attempt";
        }
        if (metadata.priorExposure() && !metadata.explicitNotRemembered()) {
            return "prior-exposure";
        }
        if ((evidence.hintUsed() || evidence.answerRevealed())
                && !metadata.explicitNotRemembered()) {
            return evidence.hintUsed() ? "hint-used" : "answer-revealed";
        }
        if (!"attempt".equals(metadata.stage())
                && !"reveal-for-check".equals(metadata.stage())) {
            return "assessment-stage-invalid";
        }
        return null;
    }
}
