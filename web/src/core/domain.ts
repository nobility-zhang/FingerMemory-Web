export type EntityId = string;

export type TimestampMs = number;

export type RecallDirection = 'meaning-to-spelling';

export type ReviewRating = 'again' | 'hard' | 'good' | 'easy';

export type ReviewLifecycleState = 'new' | 'learning' | 'review' | 'relearning';

export type ReviewScheduleStatus = 'new' | 'learning' | 'review';

export type PracticeModeId = 'keyboard-recall' | 'lyrics-scroll';

export type PracticeSessionStatus = 'active' | 'paused' | 'completed';

export type PracticeQueueItemStatus = 'pending' | 'completed' | 'skipped';

export interface AppMetaRecord {
  key: string;
  value: string;
  updatedAt: TimestampMs;
}

export interface Lexicon {
  id: EntityId;
  name: string;
  description: string;
  createdAt: TimestampMs;
  updatedAt: TimestampMs;
  archivedAt: TimestampMs | null;
}

export interface WordEntry {
  id: EntityId;
  term: string;
  meaning: string;
  acceptedAnswers: string[];
  hints: string[];
  normalizedTerm: string;
  createdAt: TimestampMs;
  updatedAt: TimestampMs;
}

export interface StudyItem {
  id: EntityId;
  wordEntryId: EntityId;
  direction: RecallDirection;
  createdAt: TimestampMs;
  updatedAt: TimestampMs;
}

export interface LexiconItem {
  lexiconId: EntityId;
  studyItemId: EntityId;
  order: number;
  sourceHints: string[];
}

export interface StudyNote {
  id: EntityId;
  studyItemId: EntityId;
  content: string;
  createdAt: TimestampMs;
  updatedAt: TimestampMs;
}

export interface PracticeAttempt {
  id: EntityId;
  studyItemId: EntityId;
  modeId: string;
  sessionId: string | null;
  direction: RecallDirection;
  promptText: string;
  response: string;
  inputMatchedAnswer: boolean;
  answerWasHidden: boolean;
  hintUsed: boolean;
  answerRevealed: boolean;
  abandoned: boolean;
  occurredAt: TimestampMs;
  requestFingerprint: string;
}

export interface SchedulerConfigSnapshot {
  packageVersion: string;
  modelVersion: string;
  weights: number[];
  fractionalDays: boolean;
  desiredRetention: Record<string, number>;
  enableFuzz: boolean;
  maximumInterval: number;
  clearStatsOnForget: boolean;
  enableShortTerm: boolean;
  learningSteps: string[];
  relearningSteps: string[];
  graduatingInterval: number;
  allowModelOverride: boolean;
}

export interface ReviewCardSnapshot {
  cardId: string;
  stability: number;
  stabilityFast: number;
  difficulty: number;
  dueAt: TimestampMs;
  lastReviewAt: TimestampMs | null;
  state: ReviewLifecycleState;
  scheduleStatus: ReviewScheduleStatus;
  reps: number;
  lapses: number;
  scheduledDays: number;
  learningStep: number;
}

export interface ReviewState extends ReviewCardSnapshot {
  studyItemId: EntityId;
  schedulerConfig: SchedulerConfigSnapshot;
  schedulerPackageVersion: string;
  schedulerModelVersion: string;
  updatedAt: TimestampMs;
}

export interface SchedulerRevlogSnapshot {
  cardId: string;
  rating: ReviewRating;
  stability: number;
  stabilityFast: number;
  difficulty: number;
  dueAt: TimestampMs;
  lastReviewAt: TimestampMs | null;
  reviewTime: TimestampMs;
  state: ReviewLifecycleState;
  scheduleStatus: ReviewScheduleStatus;
  scheduledDays: number;
  learningStep: number;
}

export interface ReviewLog {
  submissionId: EntityId;
  studyItemId: EntityId;
  wordEntryId: EntityId;
  modeId: string;
  sessionId: string | null;
  direction: RecallDirection;
  rating: ReviewRating;
  reviewedAt: TimestampMs;
  response: string;
  inputMatchedAnswer: boolean;
  answerWasHidden: boolean;
  hintUsed: boolean;
  answerRevealed: boolean;
  abandoned: boolean;
  beforeCard: ReviewCardSnapshot | null;
  afterCard: ReviewCardSnapshot;
  srsRevlog: SchedulerRevlogSnapshot;
  schedulerConfig: SchedulerConfigSnapshot;
  schedulerPackageVersion: string;
  schedulerModelVersion: string;
  requestFingerprint: string;
}

export interface PracticeItem {
  studyItem: StudyItem;
  wordEntry: WordEntry;
}

export interface PracticeSessionQueueItem {
  studyItemId: EntityId;
  wordEntryId: EntityId;
  status: PracticeQueueItemStatus;
}

export interface PracticeInteractionState {
  draft: string;
  revealedHintCount: number;
  answerRevealed: boolean;
}

export interface PendingAnswer {
  studyItemId: EntityId;
  response: string;
  answerWasHidden: boolean;
  hintUsed: boolean;
  answerRevealed: boolean;
  inputMatchedAnswer: boolean;
  practiceAttemptId: EntityId;
  reviewSubmissionId: EntityId;
  submittedAt: TimestampMs;
}

export interface PracticeSession {
  id: EntityId;
  modeId: PracticeModeId;
  status: PracticeSessionStatus;
  queue: PracticeSessionQueueItem[];
  currentIndex: number;
  currentStudyItemId: EntityId | null;
  interactions: Record<EntityId, PracticeInteractionState>;
  pendingAnswer: PendingAnswer | null;
  speed: number;
  reduceMotion: boolean;
  createdAt: TimestampMs;
  updatedAt: TimestampMs;
  pausedAt: TimestampMs | null;
  completedAt: TimestampMs | null;
}
