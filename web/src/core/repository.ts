import type {
  AppMetaRecord,
  Lexicon,
  LexiconItem,
  PracticeItem,
  PracticeAttempt,
  PracticeModeId,
  PracticeSession,
  ReviewLog,
  ReviewRating,
  ReviewState,
  StudyItem,
  StudyNote,
  TimestampMs,
  WordEntry,
} from './domain';

export interface StorageCounts {
  lexicons: number;
  words: number;
  studyItems: number;
  lexiconItems: number;
  notes: number;
  practiceAttempts: number;
  practiceSessions: number;
  reviewStates: number;
  reviewLogs: number;
}

export interface StorageStatus {
  schemaVersion: number;
  dataFormatVersion: number;
  scheduler: {
    packageVersion: string;
    modelVersion: string;
  };
  counts: StorageCounts;
}

export interface ReviewSubmissionInput {
  submissionId: string;
  studyItemId: string;
  modeId: string;
  sessionId: string | null;
  rating: ReviewRating;
  response: string;
  answerWasHidden: boolean;
  hintUsed: boolean;
  answerRevealed: boolean;
  abandoned: boolean;
  userConfirmedRating: boolean;
  reviewedAt: TimestampMs;
}

export interface ReviewSubmissionResult {
  outcome: 'created' | 'duplicate';
  state: ReviewState;
  log: ReviewLog;
}

export interface PracticeAttemptInput {
  id: string;
  studyItemId: string;
  modeId: string;
  sessionId: string | null;
  response: string;
  answerWasHidden: boolean;
  hintUsed: boolean;
  answerRevealed: boolean;
  abandoned: boolean;
  occurredAt: TimestampMs;
}

export interface PracticeAttemptResult {
  outcome: 'created' | 'duplicate';
  attempt: PracticeAttempt;
}

export interface DueReviewItem {
  studyItem: StudyItem;
  wordEntry: WordEntry;
  reviewState: ReviewState;
}

export interface StorageSnapshotTableData {
  appMeta: AppMetaRecord[];
  lexicons: Lexicon[];
  words: WordEntry[];
  studyItems: StudyItem[];
  lexiconItems: LexiconItem[];
  notes: StudyNote[];
  practiceAttempts: PracticeAttempt[];
  practiceSessions: PracticeSession[];
  reviewStates: ReviewState[];
  reviewLogs: ReviewLog[];
}

export interface StorageSnapshot {
  snapshotVersion: 1;
  schemaVersion: number;
  dataFormatVersion: number;
  exportedAt: TimestampMs;
  tables: StorageSnapshotTableData;
}

export interface FingerMemoryRepository {
  initialize(): Promise<StorageStatus>;
  getStatus(): Promise<StorageStatus>;
  ensureStarterContent(): Promise<PracticeItem[]>;
  listPracticeItems(limit?: number): Promise<PracticeItem[]>;
  getLatestPracticeSession(modeId?: PracticeModeId): Promise<PracticeSession | undefined>;
  savePracticeSession(session: PracticeSession): Promise<PracticeSession>;
  recordPracticeAttempt(input: PracticeAttemptInput): Promise<PracticeAttemptResult>;
  submitReview(input: ReviewSubmissionInput): Promise<ReviewSubmissionResult>;
  getReviewState(studyItemId: string): Promise<ReviewState | undefined>;
  getReviewLog(submissionId: string): Promise<ReviewLog | undefined>;
  listDueReviewItems(at: TimestampMs): Promise<DueReviewItem[]>;
  exportSnapshot(exportedAt: TimestampMs): Promise<StorageSnapshot>;
  importSnapshot(snapshot: unknown): Promise<StorageStatus>;
}

export type ReviewSubmissionRejectionReason =
  | 'study-item-not-found'
  | 'unsupported-direction'
  | 'rating-not-confirmed'
  | 'answer-not-hidden'
  | 'hint-used'
  | 'answer-revealed'
  | 'abandoned'
  | 'review-time-out-of-order';

export class ReviewSubmissionRejectedError extends Error {
  constructor(readonly reason: ReviewSubmissionRejectionReason) {
    super(`Review submission rejected: ${reason}`);
    this.name = 'ReviewSubmissionRejectedError';
  }
}

export class ReviewSubmissionConflictError extends Error {
  constructor(readonly submissionId: string) {
    super(`Review submission id already exists with different input: ${submissionId}`);
    this.name = 'ReviewSubmissionConflictError';
  }
}

export class PracticeAttemptConflictError extends Error {
  constructor(readonly attemptId: string) {
    super(`Practice attempt id already exists with different input: ${attemptId}`);
    this.name = 'PracticeAttemptConflictError';
  }
}

export class ReviewStateVersionMismatchError extends Error {
  constructor() {
    super('The persisted review state was created with a different scheduler configuration');
    this.name = 'ReviewStateVersionMismatchError';
  }
}

export class InvalidStorageSnapshotError extends Error {
  constructor(message: string) {
    super(`Invalid storage snapshot: ${message}`);
    this.name = 'InvalidStorageSnapshotError';
  }
}
