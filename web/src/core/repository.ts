import type {
  AppMetaRecord,
  Lexicon,
  LexiconItem,
  PracticeItem,
  PracticeAttempt,
  PracticeModeId,
  PracticeQueueItemSource,
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

export interface ReviewRatingPreview {
  rating: ReviewRating;
  dueAt: TimestampMs;
  scheduledDays: number;
}

export type ReviewRatingPreviews = Record<ReviewRating, ReviewRatingPreview>;

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

export interface LexiconStudyItem {
  membership: LexiconItem;
  practiceItem: PracticeItem;
}

export interface CreateLexiconInput {
  name: string;
  description: string;
  now: TimestampMs;
}

export interface UpdateLexiconInput {
  id: string;
  name: string;
  description: string;
  now: TimestampMs;
}

export interface CreateStudyItemInput {
  lexiconId: string;
  term: string;
  meaning: string;
  acceptedAnswers: string[];
  hints: string[];
  now: TimestampMs;
}

export interface UpdateStudyItemInput {
  lexiconId: string;
  studyItemId: string;
  term: string;
  meaning: string;
  acceptedAnswers: string[];
  hints: string[];
  now: TimestampMs;
}

export interface ImportStudyItemRow {
  term: string;
  meaning: string;
  hints: string[];
  duplicateAction: 'skip' | 'create';
}

export interface ImportStudyItemsInput {
  batchId: string;
  lexiconId: string;
  rows: ImportStudyItemRow[];
  now: TimestampMs;
}

export interface ImportStudyItemsResult {
  outcome: 'created' | 'duplicate';
  importedCount: number;
  skippedCount: number;
}

export interface SaveStudyNoteInput {
  studyItemId: string;
  content: string;
  now: TimestampMs;
}

export interface DueReviewItem {
  studyItem: StudyItem;
  wordEntry: WordEntry;
  reviewState: ReviewState;
}

export interface PracticeQueueSettings {
  newLimit: number;
  reviewLimit: number;
  paused: boolean;
}

export interface PlannedPracticeQueueItem {
  item: PracticeItem;
  source: Extract<PracticeQueueItemSource, 'new' | 'review'>;
}

export interface PracticeQueuePlan {
  at: TimestampMs;
  timeZone: string;
  dayStartAt: TimestampMs;
  dayEndAt: TimestampMs;
  settings: PracticeQueueSettings;
  dueAvailableCount: number;
  newAvailableCount: number;
  selected: PlannedPracticeQueueItem[];
}

export interface ReviewHistoryEntry {
  studyItem: StudyItem;
  wordEntry: WordEntry;
  state: ReviewState;
  logs: ReviewLog[];
  consistent: boolean;
  issue: string | null;
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

export interface StorageSnapshotPreview {
  snapshotVersion: number;
  schemaVersion: number;
  dataFormatVersion: number;
  exportedAt: TimestampMs;
  counts: StorageCounts;
}

export interface FingerMemoryRepository {
  initialize(): Promise<StorageStatus>;
  getStatus(): Promise<StorageStatus>;
  ensureStarterContent(): Promise<PracticeItem[]>;
  listLexicons(includeArchived?: boolean): Promise<Lexicon[]>;
  createLexicon(input: CreateLexiconInput): Promise<Lexicon>;
  updateLexicon(input: UpdateLexiconInput): Promise<Lexicon>;
  setLexiconArchived(id: string, archived: boolean, now: TimestampMs): Promise<Lexicon>;
  deleteLexicon(id: string): Promise<void>;
  listLexiconItems(lexiconId: string): Promise<LexiconStudyItem[]>;
  createStudyItem(input: CreateStudyItemInput): Promise<LexiconStudyItem>;
  updateStudyItem(input: UpdateStudyItemInput): Promise<LexiconStudyItem>;
  removeStudyItemFromLexicon(lexiconId: string, studyItemId: string): Promise<void>;
  importStudyItems(input: ImportStudyItemsInput): Promise<ImportStudyItemsResult>;
  listStudyNotes(): Promise<StudyNote[]>;
  saveStudyNote(input: SaveStudyNoteInput): Promise<StudyNote | undefined>;
  getPracticeQueueSettings(): Promise<PracticeQueueSettings>;
  savePracticeQueueSettings(
    settings: PracticeQueueSettings,
    now: TimestampMs,
  ): Promise<PracticeQueueSettings>;
  planPracticeQueue(input: {
    at: TimestampMs;
    timeZone: string;
  }): Promise<PracticeQueuePlan>;
  listPracticeItems(limit?: number): Promise<PracticeItem[]>;
  listPracticeItemsByStudyItemIds(studyItemIds: string[]): Promise<PracticeItem[]>;
  getLatestPracticeSession(modeId?: PracticeModeId): Promise<PracticeSession | undefined>;
  savePracticeSession(session: PracticeSession): Promise<PracticeSession>;
  recordPracticeAttempt(input: PracticeAttemptInput): Promise<PracticeAttemptResult>;
  previewReviewIntervals(input: {
    studyItemId: string;
    reviewedAt: TimestampMs;
  }): Promise<ReviewRatingPreviews>;
  submitReview(input: ReviewSubmissionInput): Promise<ReviewSubmissionResult>;
  getReviewState(studyItemId: string): Promise<ReviewState | undefined>;
  getReviewLog(submissionId: string): Promise<ReviewLog | undefined>;
  listReviewHistory(): Promise<ReviewHistoryEntry[]>;
  listDueReviewItems(at: TimestampMs): Promise<DueReviewItem[]>;
  exportSnapshot(exportedAt: TimestampMs): Promise<StorageSnapshot>;
  previewSnapshot(snapshot: unknown): Promise<StorageSnapshotPreview>;
  importSnapshot(snapshot: unknown): Promise<StorageStatus>;
}

export type ReviewSubmissionRejectionReason =
  | 'session-not-found'
  | 'session-not-active'
  | 'session-pending-mismatch'
  | 'study-item-not-found'
  | 'unsupported-direction'
  | 'rating-not-confirmed'
  | 'answer-not-hidden'
  | 'hint-used'
  | 'answer-revealed'
  | 'abandoned'
  | 'session-review-limit'
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

export class LexiconNotFoundError extends Error {
  constructor(readonly lexiconId: string) {
    super(`Lexicon not found: ${lexiconId}`);
    this.name = 'LexiconNotFoundError';
  }
}

export class StudyItemNotFoundError extends Error {
  constructor(readonly studyItemId: string) {
    super(`Study item not found: ${studyItemId}`);
    this.name = 'StudyItemNotFoundError';
  }
}

export class StudyItemInUseError extends Error {
  constructor(readonly studyItemId: string) {
    super(`Study item is referenced by an unfinished practice session: ${studyItemId}`);
    this.name = 'StudyItemInUseError';
  }
}

export class ImportBatchConflictError extends Error {
  constructor(readonly batchId: string) {
    super(`Import batch id already exists with different input: ${batchId}`);
    this.name = 'ImportBatchConflictError';
  }
}
