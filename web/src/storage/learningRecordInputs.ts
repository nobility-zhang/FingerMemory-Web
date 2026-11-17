import type {
  TimestampMs,
  WordEntry,
} from '../core/domain';
import type {
  PracticeAttemptInput,
  ReviewSubmissionInput,
} from '../core/repository';
import { normalizeStudyTerm } from '../core/studyText';

export function assertRepositoryIdentifier(
  value: string,
  label: string,
): void {
  if (value.trim().length === 0) {
    throw new TypeError(`${label} must not be empty`);
  }
}

export function assertRepositoryTimestamp(
  value: TimestampMs,
  label: string,
): void {
  if (!Number.isFinite(value) || value <= 0) {
    throw new TypeError(`${label} must be a positive finite timestamp`);
  }
}

export function inputMatchesWord(
  word: WordEntry,
  response: string,
): boolean {
  const normalizedResponse = normalizeStudyTerm(response);
  if (normalizedResponse.length === 0) {
    return false;
  }
  return [word.term, ...word.acceptedAnswers].some(
    (answer) => normalizeStudyTerm(answer) === normalizedResponse,
  );
}

export function reviewSubmissionFingerprint(
  input: ReviewSubmissionInput,
): string {
  return JSON.stringify([
    input.submissionId,
    input.studyItemId,
    input.modeId,
    input.sessionId,
    input.rating,
    input.response,
    input.answerWasHidden,
    input.hintUsed,
    input.answerRevealed,
    input.abandoned,
    input.userConfirmedRating,
    input.reviewedAt,
  ]);
}

export function practiceAttemptFingerprint(
  input: PracticeAttemptInput,
): string {
  return JSON.stringify([
    input.id,
    input.studyItemId,
    input.modeId,
    input.sessionId,
    input.response,
    input.answerWasHidden,
    input.hintUsed,
    input.answerRevealed,
    input.abandoned,
    input.occurredAt,
  ]);
}
