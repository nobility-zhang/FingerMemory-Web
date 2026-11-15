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

interface CsrfView {
  headerName: string;
  parameterName: string;
  token: string;
}

interface ApiErrorBody {
  code?: string;
  message?: string;
}

export class ApiClientError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'ApiClientError';
  }
}

export interface AccountClient {
  loadCurrentUser(): Promise<UserView | null>;
  login(username: string, password: string): Promise<UserView>;
  register(username: string, password: string, displayName: string): Promise<UserView>;
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
    expectedDataVersion: number;
    expectedSessionVersion: number;
  }): Promise<PracticeResponseResult>;
  submitPracticeCompletion(input: {
    sessionId: string;
    practiceAttemptId: string;
    studyItemId: string;
    response: string;
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
    expectedDataVersion: number;
    expectedReviewVersion: number;
    expectedSessionVersion: number;
  }): Promise<ReviewSubmissionResult>;
  previewLocalMigration(snapshot: unknown): Promise<MigrationPreview>;
  importLocalMigration(batchId: string, snapshot: unknown): Promise<MigrationResult>;
  exportAccountBackup(): Promise<AccountBackup>;
  previewAccountBackup(backup: AccountBackup): Promise<BackupPreview>;
  restoreAccountBackup(
    idempotencyKey: string,
    backup: AccountBackup,
  ): Promise<BackupRestoreResult>;
}

type FetchImplementation = typeof fetch;

export class AccountApi implements AccountClient {
  private csrfToken: string | undefined;
  private csrfHeaderName = 'X-CSRF-TOKEN';
  private readonly fetchImplementation: FetchImplementation;
  private readonly baseUrl: string;

  constructor(fetchImplementation?: FetchImplementation, baseUrl = '/api/v1') {
    this.fetchImplementation =
      fetchImplementation ?? ((input, init) => globalThis.fetch(input, init));
    this.baseUrl = baseUrl;
  }

  async loadCurrentUser(): Promise<UserView | null> {
    try {
      return await this.request<UserView>('/auth/me');
    } catch (error) {
      if (error instanceof ApiClientError && error.status === 401) {
        return null;
      }
      throw error;
    }
  }

  async login(username: string, password: string): Promise<UserView> {
    const body = new URLSearchParams({ username, password });
    const user = await this.request<UserView>('/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
    this.csrfToken = undefined;
    return user;
  }

  async register(
    username: string,
    password: string,
    displayName: string,
  ): Promise<UserView> {
    await this.request<UserView>('/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password, displayName }),
    });
    return this.login(username, password);
  }

  async logout(): Promise<void> {
    await this.request<void>('/auth/logout', { method: 'POST' });
    this.csrfToken = undefined;
  }

  async listLexicons(): Promise<LexiconView[]> {
    return this.request<LexiconView[]>('/lexicons?limit=100');
  }

  async createLexicon(name: string, description: string): Promise<LexiconView> {
    return this.request<LexiconView>('/lexicons', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, description }),
    });
  }

  async loadQueuePlan(input: {
    timeZone: string;
    newLimit?: number;
    reviewLimit?: number;
  }): Promise<QueuePlanView> {
    const query = new URLSearchParams({ timeZone: input.timeZone });
    if (input.newLimit !== undefined) {
      query.set('newLimit', String(input.newLimit));
    }
    if (input.reviewLimit !== undefined) {
      query.set('reviewLimit', String(input.reviewLimit));
    }
    return this.request<QueuePlanView>(`/queue/plan?${query.toString()}`);
  }

  async listActiveSessions(): Promise<SessionListResponse> {
    return this.request<SessionListResponse>('/sessions');
  }

  async createSession(input: {
    modeId: string;
    timeZone: string;
    newLimit?: number;
    reviewLimit?: number;
    reduceMotion: boolean;
  }): Promise<SessionEnvelope> {
    return this.request<SessionEnvelope>('/sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    });
  }

  async getSession(sessionId: string): Promise<SessionEnvelope> {
    return this.request<SessionEnvelope>(`/sessions/${encodeURIComponent(sessionId)}`);
  }

  async updateSession(input: {
    sessionId: string;
    expectedDataVersion: number;
    expectedVersion: number;
    status: 'active' | 'paused' | 'completed';
  }): Promise<SessionEnvelope> {
    return this.request<SessionEnvelope>(
      `/sessions/${encodeURIComponent(input.sessionId)}`,
      {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          expectedDataVersion: input.expectedDataVersion,
          expectedVersion: input.expectedVersion,
          status: input.status,
        }),
      },
    );
  }

  async submitPracticeResponse(input: {
    sessionId: string;
    practiceAttemptId: string;
    reviewSubmissionId: string;
    studyItemId: string;
    response: string;
    expectedDataVersion: number;
    expectedSessionVersion: number;
  }): Promise<PracticeResponseResult> {
    return this.request<PracticeResponseResult>(
      `/sessions/${encodeURIComponent(input.sessionId)}/responses`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          practiceAttemptId: input.practiceAttemptId,
          reviewSubmissionId: input.reviewSubmissionId,
          studyItemId: input.studyItemId,
          response: input.response,
          answerWasHidden: true,
          hintUsed: false,
          answerRevealed: false,
          abandoned: false,
          expectedDataVersion: input.expectedDataVersion,
          expectedSessionVersion: input.expectedSessionVersion,
        }),
      },
    );
  }

  async submitPracticeCompletion(input: {
    sessionId: string;
    practiceAttemptId: string;
    studyItemId: string;
    response: string;
    answerWasHidden: boolean;
    hintUsed: boolean;
    answerRevealed: boolean;
    abandoned: boolean;
    outcome: 'completed' | 'skipped';
    expectedDataVersion: number;
    expectedSessionVersion: number;
  }): Promise<PracticeCompletionResult> {
    return this.request<PracticeCompletionResult>(
      `/sessions/${encodeURIComponent(input.sessionId)}/practice`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          practiceAttemptId: input.practiceAttemptId,
          studyItemId: input.studyItemId,
          response: input.response,
          answerWasHidden: input.answerWasHidden,
          hintUsed: input.hintUsed,
          answerRevealed: input.answerRevealed,
          abandoned: input.abandoned,
          outcome: input.outcome,
          expectedDataVersion: input.expectedDataVersion,
          expectedSessionVersion: input.expectedSessionVersion,
        }),
      },
    );
  }

  async previewReview(input: {
    sessionId: string;
    studyItemId: string;
    expectedDataVersion: number;
    expectedSessionVersion: number;
    expectedReviewVersion: number;
  }): Promise<ReviewPreviewView> {
    return this.request<ReviewPreviewView>(
      `/sessions/${encodeURIComponent(input.sessionId)}/review-preview`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          studyItemId: input.studyItemId,
          expectedDataVersion: input.expectedDataVersion,
          expectedSessionVersion: input.expectedSessionVersion,
          expectedReviewVersion: input.expectedReviewVersion,
        }),
      },
    );
  }

  async submitReview(input: {
    sessionId: string;
    submissionId: string;
    rating: 'again' | 'hard' | 'good' | 'easy';
    expectedDataVersion: number;
    expectedReviewVersion: number;
    expectedSessionVersion: number;
  }): Promise<ReviewSubmissionResult> {
    return this.request<ReviewSubmissionResult>(
      `/sessions/${encodeURIComponent(input.sessionId)}/reviews`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': input.submissionId,
        },
        body: JSON.stringify({
          rating: input.rating,
          expectedDataVersion: input.expectedDataVersion,
          expectedReviewVersion: input.expectedReviewVersion,
          expectedSessionVersion: input.expectedSessionVersion,
          evidence: {
            userConfirmedRating: true,
            answerWasHidden: true,
            hintUsed: false,
            answerRevealed: false,
            abandoned: false,
          },
        }),
      },
    );
  }

  async previewLocalMigration(snapshot: unknown): Promise<MigrationPreview> {
    return this.request<MigrationPreview>('/migrations/local/preview', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ snapshot }),
    });
  }

  async importLocalMigration(
    batchId: string,
    snapshot: unknown,
  ): Promise<MigrationResult> {
    return this.request<MigrationResult>('/migrations/local', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': batchId,
      },
      body: JSON.stringify({ snapshot }),
    });
  }

  async exportAccountBackup(): Promise<AccountBackup> {
    return this.request<AccountBackup>('/backup/export');
  }

  async previewAccountBackup(backup: AccountBackup): Promise<BackupPreview> {
    return this.request<BackupPreview>('/backup/import/preview', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ backup }),
    });
  }

  async restoreAccountBackup(
    idempotencyKey: string,
    backup: AccountBackup,
  ): Promise<BackupRestoreResult> {
    return this.request<BackupRestoreResult>('/backup/import', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify({ backup }),
    });
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const method = (init.method ?? 'GET').toUpperCase();
    const headers = new Headers(init.headers);
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      await this.ensureCsrfToken();
      if (this.csrfToken !== undefined) {
        headers.set(this.csrfHeaderName, this.csrfToken);
      }
    }

    const response = await this.fetchImplementation(`${this.baseUrl}${path}`, {
      ...init,
      method,
      headers,
      credentials: 'same-origin',
    });

    if (!response.ok) {
      if (response.status === 401 || response.status === 403) {
        this.csrfToken = undefined;
      }
      let body: ApiErrorBody = {};
      try {
        body = (await response.json()) as ApiErrorBody;
      } catch {
        body = {};
      }
      throw new ApiClientError(
        response.status,
        body.code ?? 'REQUEST_FAILED',
        body.message ?? '请求失败',
      );
    }
    if (response.status === 204) {
      return undefined as T;
    }
    return (await response.json()) as T;
  }

  private async ensureCsrfToken(): Promise<void> {
    if (this.csrfToken !== undefined) {
      return;
    }
    const response = await this.fetchImplementation(`${this.baseUrl}/auth/csrf`, {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' },
    });
    if (!response.ok) {
      throw new ApiClientError(response.status, 'CSRF_UNAVAILABLE', '无法建立安全会话');
    }
    const csrf = (await response.json()) as CsrfView;
    this.csrfToken = csrf.token;
    this.csrfHeaderName = csrf.headerName;
  }
}

export const accountApi = new AccountApi();
