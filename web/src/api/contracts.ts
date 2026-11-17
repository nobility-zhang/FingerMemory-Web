export interface UserPreferencesView {
  nickname: string;
  avatarKey: string;
  timezone: string;
  dailyGoal: number;
  theme: string;
  soundEnabled: boolean;
  shortcutsJson: string;
}

export interface UserView {
  id: string;
  username: string;
  displayName: string;
  mustChangePassword?: boolean;
  preferences: UserPreferencesView;
}

export interface LexiconView {
  id: string;
  name: string;
  description: string;
  source: string;
  archivedAtMs: number | null;
  createdAtMs: number;
  updatedAtMs: number;
  version: number;
}

export interface WordEntryView {
  id: string;
  term: string;
  meaning: string;
  acceptedAnswers: string[];
  hints: string[];
  normalizedTerm: string;
  createdAt: number;
  updatedAt: number;
}

export interface StudyItemView {
  id: string;
  wordEntryId: string;
  direction: 'meaning-to-spelling';
  createdAt: number;
  updatedAt: number;
}

export interface LexiconItemView {
  lexiconId: string;
  studyItemId: string;
  order: number;
  sourceHints: string[];
}

export interface SessionItemView {
  wordEntry: WordEntryView;
  studyItem: StudyItemView;
  lexiconItem: LexiconItemView | null;
  reviewState?: unknown;
  reviewVersion: number;
}

export interface SessionQueueItemView {
  studyItemId: string;
  wordEntryId: string;
  status: 'pending' | 'completed' | 'skipped';
  source: 'new' | 'review' | 'retry';
}

export interface PendingAnswerView {
  studyItemId: string;
  response: string;
  answerWasHidden: boolean;
  hintUsed: boolean;
  answerRevealed: boolean;
  inputMatchedAnswer: boolean;
  assessmentType?: 'typed-recall' | 'flashcard-self-report' | 'guided-practice';
  assessmentPolicyVersion?: 1 | 2;
  assessmentStage?: 'prompt' | 'attempt' | 'reveal-for-check' | 'rate';
  priorExposure?: boolean;
  explicitNotRemembered?: boolean;
  practiceAttemptId: string;
  reviewSubmissionId: string;
  submittedAt: number;
}

export interface PracticeSessionView {
  id: string;
  modeId: string;
  status: 'active' | 'paused' | 'completed';
  queue: SessionQueueItemView[];
  currentIndex: number;
  currentStudyItemId: string | null;
  interactions: Record<string, unknown>;
  pendingAnswer: PendingAnswerView | null;
  speed: number;
  reduceMotion: boolean;
  createdAt: number;
  updatedAt: number;
  pausedAt: number | null;
  completedAt: number | null;
  endedReason?: 'completed' | 'abandoned';
  version: number;
}

export interface SessionEnvelope {
  session: PracticeSessionView;
  items: SessionItemView[];
  dataVersion: number;
}

export interface SessionListResponse {
  sessions: SessionEnvelope[];
  dataVersion: number;
}

export interface QueueEntryView {
  studyItem: StudyItemView;
  wordEntry: WordEntryView;
  reviewState?: unknown;
  source: 'new' | 'review';
}

export interface QueuePlanView {
  at: number;
  timeZone: string;
  dayStartAt: number;
  dayEndAt: number;
  settings: {
    newLimit: number;
    reviewLimit: number;
    paused: boolean;
  };
  dueAvailableCount: number;
  newAvailableCount: number;
  selected: QueueEntryView[];
  dataVersion: number;
}

export interface ReviewPreviewView {
  previews: Record<
    'again' | 'hard' | 'good' | 'easy',
    { rating: string; dueAt: number; scheduledDays: number }
  >;
  schedulerPackageVersion: string;
  schedulerModelVersion: string;
  schedulerConfigHash: string;
  reviewedAtMs: number;
  reviewVersion: number;
  sessionVersion: number;
  dataVersion: number;
}

export interface PracticeResponseResult {
  attempt: unknown;
  pendingAnswer: PendingAnswerView;
  session: PracticeSessionView;
  dataVersion: number;
}

export interface PracticeCompletionResult {
  attempt: {
    id: string;
    inputMatchedAnswer: boolean;
    hintUsed: boolean;
    answerRevealed: boolean;
    abandoned: boolean;
  };
  session: PracticeSessionView;
  dataVersion: number;
}

export interface ReviewSubmissionResult {
  outcome: 'created' | 'migrated';
  state: { dueAt: number; version: number };
  log: { submissionId: string; reviewedAt: number };
  session: PracticeSessionView | null;
  dataVersion: number;
  rewardEligible: boolean;
}

export interface MigrationPreview {
  snapshotFingerprint: string;
  targetDataVersion: number;
  counts: Record<string, number>;
  schedulerPackageVersion: string;
  schedulerModelVersion: string;
  schedulerWeights: number;
}

export interface MigrationResult {
  batchId: string;
  snapshotFingerprint: string;
  dataVersion: number;
  importedCounts: Record<string, number>;
  idMappings: number;
}

export interface AccountBackup {
  backupVersion: number;
  schemaVersion: number;
  dataFormatVersion: number;
  exportedAt: number;
  tables: Record<string, unknown>;
  [key: string]: unknown;
}

export interface BackupPreview {
  fingerprint: string;
  exportedAt: number;
  sourceDataVersion: number;
  counts: Record<string, number>;
  reviewReceipts: number;
  rewardEvents: number;
  migrationReceipts: number;
  idMappings: number;
}

export interface BackupRestoreResult {
  outcome: 'restored';
  fingerprint: string;
  dataVersion: number;
  counts: Record<string, number>;
}

export interface GmContext {
  role: 'ADMIN';
  capabilities: Array<
    'manage-users' | 'manage-content' | 'manage-sandboxes' | 'read-audit'
  >;
}

export interface GmUser {
  id: string;
  username: string;
  displayName: string;
  role: 'USER' | 'ADMIN';
  status: 'active' | 'disabled';
  accountKind: 'standard' | 'test';
  version: number;
}

export interface GmUserPage {
  items: GmUser[];
  nextCursor: string | null;
}

export interface GmCreateUserInput {
  username: string;
  displayName: string;
  password: string;
  role: 'USER' | 'ADMIN';
  accountKind: 'standard' | 'test';
  reason: string;
}

export interface GmUpdateUserInput {
  expectedVersion: number;
  reason: string;
  role?: 'USER' | 'ADMIN';
  status?: 'active' | 'disabled';
}

export interface GmAuditEvent {
  id: string;
  actorId: string;
  action: string;
  target: string;
  scope: string;
  occurredAtMs: number;
  requestId: string;
  reason: string;
  outcome: 'success' | 'rejected' | 'failed';
}

export interface GmAuditPage {
  items: GmAuditEvent[];
  nextCursor: string | null;
}

export interface AccountClient {
  loadCurrentUser(): Promise<UserView | null>;
  login(username: string, password: string): Promise<UserView>;
  register(
    username: string,
    password: string,
    displayName: string,
  ): Promise<UserView>;
  changePassword(
    currentPassword: string,
    newPassword: string,
  ): Promise<void>;
  logout(): Promise<void>;
  listLexicons(): Promise<LexiconView[]>;
  createLexicon(name: string, description: string): Promise<LexiconView>;
  loadQueuePlan(input: {
    timeZone: string;
    newLimit?: number;
    reviewLimit?: number;
  }): Promise<QueuePlanView>;
  listActiveSessions(): Promise<SessionListResponse>;
  createSession(input: {
    modeId: string;
    timeZone: string;
    newLimit?: number;
    reviewLimit?: number;
    reduceMotion: boolean;
  }): Promise<SessionEnvelope>;
  getSession(sessionId: string): Promise<SessionEnvelope>;
  updateSession(input: {
    sessionId: string;
    expectedDataVersion: number;
    expectedVersion: number;
    status: 'active' | 'paused' | 'completed';
  }): Promise<SessionEnvelope>;
  submitPracticeResponse(input: {
    sessionId: string;
    practiceAttemptId: string;
    reviewSubmissionId: string;
    studyItemId: string;
    response: string;
    assessmentType?: 'typed-recall' | 'flashcard-self-report' | 'guided-practice';
    assessmentPolicyVersion?: 1 | 2;
    assessmentStage?: 'prompt' | 'attempt' | 'reveal-for-check' | 'rate';
    priorExposure?: boolean;
    explicitNotRemembered?: boolean;
    answerWasHidden?: boolean;
    hintUsed?: boolean;
    answerRevealed?: boolean;
    abandoned?: boolean;
    expectedDataVersion: number;
    expectedSessionVersion: number;
  }): Promise<PracticeResponseResult>;
  submitPracticeCompletion(input: {
    sessionId: string;
    practiceAttemptId: string;
    studyItemId: string;
    response: string;
    assessmentType?: 'typed-recall' | 'flashcard-self-report' | 'guided-practice';
    assessmentPolicyVersion?: 1 | 2;
    assessmentStage?: 'prompt' | 'attempt' | 'reveal-for-check' | 'rate';
    priorExposure?: boolean;
    explicitNotRemembered?: boolean;
    answerWasHidden: boolean;
    hintUsed: boolean;
    answerRevealed: boolean;
    abandoned: boolean;
    outcome: 'completed' | 'skipped';
    expectedDataVersion: number;
    expectedSessionVersion: number;
  }): Promise<PracticeCompletionResult>;
  previewReview(input: {
    sessionId: string;
    studyItemId: string;
    expectedDataVersion: number;
    expectedSessionVersion: number;
    expectedReviewVersion: number;
  }): Promise<ReviewPreviewView>;
  submitReview(input: {
    sessionId: string;
    submissionId: string;
    rating: 'again' | 'hard' | 'good' | 'easy';
    assessmentType?: 'typed-recall' | 'flashcard-self-report' | 'guided-practice';
    assessmentPolicyVersion?: 1 | 2;
    assessmentStage?: 'prompt' | 'attempt' | 'reveal-for-check' | 'rate';
    priorExposure?: boolean;
    explicitNotRemembered?: boolean;
    answerWasHidden?: boolean;
    hintUsed?: boolean;
    answerRevealed?: boolean;
    abandoned?: boolean;
    expectedDataVersion: number;
    expectedReviewVersion: number;
    expectedSessionVersion: number;
  }): Promise<ReviewSubmissionResult>;
  previewLocalMigration(snapshot: unknown): Promise<MigrationPreview>;
  importLocalMigration(
    batchId: string,
    snapshot: unknown,
  ): Promise<MigrationResult>;
  exportAccountBackup(): Promise<AccountBackup>;
  previewAccountBackup(backup: AccountBackup): Promise<BackupPreview>;
  restoreAccountBackup(
    idempotencyKey: string,
    backup: AccountBackup,
  ): Promise<BackupRestoreResult>;
  loadGmContext(): Promise<GmContext>;
  listGmUsers(limit?: number, cursor?: string): Promise<GmUserPage>;
  createGmUser(
    idempotencyKey: string,
    input: GmCreateUserInput,
  ): Promise<GmUser>;
  updateGmUser(
    userId: string,
    idempotencyKey: string,
    input: GmUpdateUserInput,
  ): Promise<GmUser>;
  listGmAudit(limit?: number): Promise<GmAuditPage>;
}
