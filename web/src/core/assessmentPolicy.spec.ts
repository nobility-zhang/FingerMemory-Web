import { describe, expect, it } from 'vitest';
import {
  assessmentRejectionReason,
  isAssessmentFormalEligible,
} from './assessmentPolicy';

describe('assessment policy eligibility', () => {
  it('allows a flashcard check while requiring Again after explicit forgetting', () => {
    const evidence = {
      assessmentType: 'flashcard-self-report' as const,
      assessmentPolicyVersion: 2 as const,
      assessmentStage: 'reveal-for-check' as const,
      priorExposure: false,
      explicitNotRemembered: true,
      answerWasHidden: true,
      hintUsed: false,
      answerRevealed: true,
      abandoned: false,
      userConfirmedRating: true,
    };

    expect(isAssessmentFormalEligible(evidence)).toBe(true);
    expect(assessmentRejectionReason({
      ...evidence,
      rating: 'good',
    })).toBe('explicit-not-remembered-requires-again');
  });

  it('keeps guided practice and legacy plain reveal out of formal review', () => {
    expect(assessmentRejectionReason({
      assessmentType: 'guided-practice',
      assessmentPolicyVersion: 2,
      assessmentStage: 'reveal-for-check',
      priorExposure: true,
      explicitNotRemembered: false,
      answerWasHidden: true,
      hintUsed: false,
      answerRevealed: true,
      abandoned: false,
      userConfirmedRating: true,
      rating: 'again',
    })).toBe('guided-practice');
    expect(assessmentRejectionReason({
      answerWasHidden: true,
      hintUsed: false,
      answerRevealed: true,
      abandoned: false,
      userConfirmedRating: true,
      rating: 'good',
    })).toBe('answer-revealed');
  });
});
