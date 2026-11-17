import type {
  AssessmentPolicyVersion,
  AssessmentStage,
  AssessmentType,
  ReviewRating,
} from './domain';

export const CURRENT_ASSESSMENT_POLICY_VERSION = 2 as const;

export interface AssessmentMetadata {
  assessmentType: AssessmentType;
  assessmentPolicyVersion: AssessmentPolicyVersion;
  assessmentStage: AssessmentStage;
  priorExposure: boolean;
  explicitNotRemembered: boolean;
}

export interface AssessmentMetadataInput {
  assessmentType?: AssessmentType;
  assessmentPolicyVersion?: AssessmentPolicyVersion;
  assessmentStage?: AssessmentStage;
  priorExposure?: boolean;
  explicitNotRemembered?: boolean;
}

export interface AssessmentEvidenceInput extends AssessmentMetadataInput {
  answerWasHidden: boolean;
  hintUsed: boolean;
  answerRevealed: boolean;
  abandoned: boolean;
  userConfirmedRating: boolean;
}

export interface AssessmentReviewInput extends AssessmentEvidenceInput {
  rating: ReviewRating;
}

export type AssessmentRejectionReason =
  | 'rating-not-confirmed'
  | 'answer-not-hidden'
  | 'hint-used'
  | 'answer-revealed'
  | 'abandoned'
  | 'assessment-stage-invalid'
  | 'guided-practice'
  | 'prior-exposure'
  | 'explicit-not-remembered-requires-again'
  | 'flashcard-requires-reveal-for-check'
  | 'typed-recall-requires-hidden-attempt';

export function assessmentMetadata(
  input: AssessmentMetadataInput = {},
): AssessmentMetadata {
  return {
    assessmentType: input.assessmentType ?? 'typed-recall',
    assessmentPolicyVersion: input.assessmentPolicyVersion ?? 1,
    assessmentStage: input.assessmentStage ?? 'attempt',
    priorExposure: input.priorExposure ?? false,
    explicitNotRemembered: input.explicitNotRemembered ?? false,
  };
}

export function assessmentTypeForPractice(input: {
  answerRevealed: boolean;
  hintUsed: boolean;
}): AssessmentType {
  return input.answerRevealed || input.hintUsed
    ? 'guided-practice'
    : 'typed-recall';
}

export function assessmentRejectionReason(
  input: AssessmentReviewInput,
): AssessmentRejectionReason | null {
  const metadata = assessmentMetadata(input);
  if (!input.userConfirmedRating) {
    return 'rating-not-confirmed';
  }
  if (input.abandoned) {
    return 'abandoned';
  }

  // Missing policy metadata is a historical legacy-v1 record. Keep its
  // original hidden-answer gate instead of retroactively applying v2.
  if (metadata.assessmentPolicyVersion !== CURRENT_ASSESSMENT_POLICY_VERSION) {
    if (!input.answerWasHidden) {
      return 'answer-not-hidden';
    }
    if (input.hintUsed) {
      return 'hint-used';
    }
    if (input.answerRevealed) {
      return 'answer-revealed';
    }
    return null;
  }

  if (metadata.assessmentType === 'guided-practice') {
    return 'guided-practice';
  }
  if (metadata.explicitNotRemembered && input.rating !== 'again') {
    return 'explicit-not-remembered-requires-again';
  }
  if (metadata.assessmentType === 'flashcard-self-report') {
    if (
      metadata.assessmentStage !== 'reveal-for-check' ||
      !input.answerWasHidden ||
      !input.answerRevealed
    ) {
      return 'flashcard-requires-reveal-for-check';
    }
    if (metadata.priorExposure && !metadata.explicitNotRemembered) {
      return 'prior-exposure';
    }
    if (input.hintUsed && !metadata.explicitNotRemembered) {
      return 'hint-used';
    }
    return null;
  }

  if (!input.answerWasHidden && !metadata.explicitNotRemembered) {
    return 'typed-recall-requires-hidden-attempt';
  }
  if (metadata.priorExposure && !metadata.explicitNotRemembered) {
    return 'prior-exposure';
  }
  if (
    (input.hintUsed || input.answerRevealed) &&
    !metadata.explicitNotRemembered
  ) {
    return input.hintUsed ? 'hint-used' : 'answer-revealed';
  }
  if (
    metadata.assessmentStage !== 'attempt' &&
    metadata.assessmentStage !== 'reveal-for-check'
  ) {
    return 'assessment-stage-invalid';
  }
  return null;
}

export function isAssessmentFormalEligible(
  input: AssessmentEvidenceInput,
): boolean {
  return assessmentRejectionReason({ ...input, rating: 'good' }) === null;
}
