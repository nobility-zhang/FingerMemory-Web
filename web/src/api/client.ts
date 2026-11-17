import type {
  AccountBackup,
  AccountClient,
  BackupPreview,
  BackupRestoreResult,
  BattleEnvelopeView,
  GmAuditPage,
  GmContext,
  GmCommandReceipt,
  GmCreateSandboxInput,
  GmCreateUserInput,
  GmSandbox,
  GmSandboxCommandInput,
  GmSandboxPage,
  GmUpdateUserInput,
  GmUser,
  GmUserPage,
  LexiconView,
  MigrationPreview,
  MigrationResult,
  PracticeCompletionResult,
  PracticeResponseResult,
  QueuePlanView,
  ReviewPreviewView,
  ReviewSubmissionResult,
  SessionEnvelope,
  SessionListResponse,
  UserView,
} from './contracts';
import { ApiClientError, HttpApiTransport } from './httpTransport';

export type * from './contracts';
export { ApiClientError } from './httpTransport';

export class AccountApi implements AccountClient {
  private readonly transport: HttpApiTransport;

  constructor(fetchImplementation?: typeof fetch, baseUrl = '/api/v1') {
    this.transport = new HttpApiTransport(baseUrl, fetchImplementation);
  }

  async loadCurrentUser(): Promise<UserView | null> {
    try {
      return await this.transport.request<UserView>('/auth/me');
    } catch (error) {
      if (error instanceof ApiClientError && error.status === 401) {
        return null;
      }
      throw error;
    }
  }

  async login(username: string, password: string): Promise<UserView> {
    const body = new URLSearchParams({ username, password });
    const user = await this.transport.request<UserView>('/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
    this.transport.clearCsrfToken();
    return user;
  }

  async register(
    username: string,
    password: string,
    displayName: string,
  ): Promise<UserView> {
    await this.transport.request<UserView>('/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password, displayName }),
    });
    return this.login(username, password);
  }

  async logout(): Promise<void> {
    await this.transport.request<void>('/auth/logout', { method: 'POST' });
    this.transport.clearCsrfToken();
  }

  async changePassword(
    currentPassword: string,
    newPassword: string,
  ): Promise<void> {
    await this.transport.request<void>('/auth/password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ currentPassword, newPassword }),
    });
  }

  async listLexicons(): Promise<LexiconView[]> {
    return this.transport.request<LexiconView[]>('/lexicons?limit=100');
  }

  async createLexicon(name: string, description: string): Promise<LexiconView> {
    return this.transport.request<LexiconView>('/lexicons', {
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
    return this.transport.request<QueuePlanView>(
      `/queue/plan?${query.toString()}`,
    );
  }

  async listActiveSessions(): Promise<SessionListResponse> {
    return this.transport.request<SessionListResponse>('/sessions');
  }

  async createSession(input: {
    modeId: string;
    timeZone: string;
    newLimit?: number;
    reviewLimit?: number;
    reduceMotion: boolean;
  }): Promise<SessionEnvelope> {
    return this.transport.request<SessionEnvelope>('/sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    });
  }

  async getSession(sessionId: string): Promise<SessionEnvelope> {
    return this.transport.request<SessionEnvelope>(
      `/sessions/${encodeURIComponent(sessionId)}`,
    );
  }

  async updateSession(input: {
    sessionId: string;
    expectedDataVersion: number;
    expectedVersion: number;
    status: 'active' | 'paused' | 'completed';
  }): Promise<SessionEnvelope> {
    return this.transport.request<SessionEnvelope>(
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
  }): Promise<PracticeResponseResult> {
    return this.transport.request<PracticeResponseResult>(
      `/sessions/${encodeURIComponent(input.sessionId)}/responses`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          practiceAttemptId: input.practiceAttemptId,
          reviewSubmissionId: input.reviewSubmissionId,
          studyItemId: input.studyItemId,
          response: input.response,
          answerWasHidden: input.answerWasHidden ?? true,
          hintUsed: input.hintUsed ?? false,
          answerRevealed: input.answerRevealed ?? false,
          assessmentType: input.assessmentType,
          assessmentPolicyVersion: input.assessmentPolicyVersion,
          assessmentStage: input.assessmentStage,
          priorExposure: input.priorExposure,
          explicitNotRemembered: input.explicitNotRemembered,
          abandoned: input.abandoned ?? false,
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
  }): Promise<PracticeCompletionResult> {
    return this.transport.request<PracticeCompletionResult>(
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
          assessmentType: input.assessmentType,
          assessmentPolicyVersion: input.assessmentPolicyVersion,
          assessmentStage: input.assessmentStage,
          priorExposure: input.priorExposure,
          explicitNotRemembered: input.explicitNotRemembered,
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
    return this.transport.request<ReviewPreviewView>(
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
  }): Promise<ReviewSubmissionResult> {
    return this.transport.request<ReviewSubmissionResult>(
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
            answerWasHidden: input.answerWasHidden ?? true,
            hintUsed: input.hintUsed ?? false,
            answerRevealed: input.answerRevealed ?? false,
            assessmentType: input.assessmentType,
            assessmentPolicyVersion: input.assessmentPolicyVersion,
            assessmentStage: input.assessmentStage,
            priorExposure: input.priorExposure,
            explicitNotRemembered: input.explicitNotRemembered,
            abandoned: input.abandoned ?? false,
          },
        }),
      },
    );
  }

  async createBattle(input: {
    battleId: string;
    encounterId: string;
    sessionId: string;
    studyItemId: string;
    companionKey: 'lia' | 'serin' | 'fina';
    seed?: number;
    shuffle?: boolean;
    actionId: string;
  }): Promise<BattleEnvelopeView> {
    return this.transport.request<BattleEnvelopeView>('/battles', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': input.actionId,
      },
      body: JSON.stringify({
        battleId: input.battleId,
        encounterId: input.encounterId,
        sessionId: input.sessionId,
        studyItemId: input.studyItemId,
        companionKey: input.companionKey,
        seed: input.seed,
        shuffle: input.shuffle,
      }),
    });
  }

  async getBattle(battleId: string): Promise<BattleEnvelopeView> {
    return this.transport.request<BattleEnvelopeView>(
      `/battles/${encodeURIComponent(battleId)}`,
    );
  }

  async claimBattleLearning(input: {
    battleId: string;
    actionId: string;
    expectedStateVersion: number;
    submissionId: string;
  }): Promise<BattleEnvelopeView> {
    return this.battleCommand(input.battleId, 'learning-claims', input.actionId, {
      expectedStateVersion: input.expectedStateVersion,
      submissionId: input.submissionId,
    });
  }

  async playBattleCard(input: {
    battleId: string;
    actionId: string;
    expectedStateVersion: number;
    cardInstanceId: string;
    targetId?: string;
  }): Promise<BattleEnvelopeView> {
    return this.battleCommand(input.battleId, 'cards', input.actionId, {
      expectedStateVersion: input.expectedStateVersion,
      cardInstanceId: input.cardInstanceId,
      targetId: input.targetId,
    });
  }

  async useBattleCompanionSkill(input: {
    battleId: string;
    actionId: string;
    expectedStateVersion: number;
  }): Promise<BattleEnvelopeView> {
    return this.battleCommand(input.battleId, 'companion-skill', input.actionId, {
      expectedStateVersion: input.expectedStateVersion,
    });
  }

  async endBattleTurn(input: {
    battleId: string;
    actionId: string;
    expectedStateVersion: number;
    nextSessionId?: string;
    nextStudyItemId?: string;
  }): Promise<BattleEnvelopeView> {
    return this.battleCommand(input.battleId, 'end-turn', input.actionId, {
      expectedStateVersion: input.expectedStateVersion,
      nextSessionId: input.nextSessionId,
      nextStudyItemId: input.nextStudyItemId,
    });
  }

  private async battleCommand(
    battleId: string,
    suffix: string,
    actionId: string,
    body: Record<string, unknown>,
  ): Promise<BattleEnvelopeView> {
    return this.transport.request<BattleEnvelopeView>(
      `/battles/${encodeURIComponent(battleId)}/${suffix}`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': actionId,
        },
        body: JSON.stringify(body),
      },
    );
  }

  async previewLocalMigration(snapshot: unknown): Promise<MigrationPreview> {
    return this.transport.request<MigrationPreview>(
      '/migrations/local/preview',
      {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ snapshot }),
      },
    );
  }

  async importLocalMigration(
    batchId: string,
    snapshot: unknown,
  ): Promise<MigrationResult> {
    return this.transport.request<MigrationResult>('/migrations/local', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': batchId,
      },
      body: JSON.stringify({ snapshot }),
    });
  }

  async exportAccountBackup(): Promise<AccountBackup> {
    return this.transport.request<AccountBackup>('/backup/export');
  }

  async previewAccountBackup(backup: AccountBackup): Promise<BackupPreview> {
    return this.transport.request<BackupPreview>(
      '/backup/import/preview',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ backup }),
      },
    );
  }

  async restoreAccountBackup(
    idempotencyKey: string,
    backup: AccountBackup,
  ): Promise<BackupRestoreResult> {
    return this.transport.request<BackupRestoreResult>('/backup/import', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify({ backup }),
    });
  }

  async loadGmContext(): Promise<GmContext> {
    return this.transport.request<GmContext>('/gm/context');
  }

  async listGmUsers(limit = 20, cursor?: string): Promise<GmUserPage> {
    const query = new URLSearchParams({ limit: String(limit) });
    if (cursor !== undefined && cursor.length > 0) {
      query.set('cursor', cursor);
    }
    return this.transport.request<GmUserPage>(`/gm/users?${query.toString()}`);
  }

  async createGmUser(
    idempotencyKey: string,
    input: GmCreateUserInput,
  ): Promise<GmUser> {
    return this.transport.request<GmUser>('/gm/users', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify(input),
    });
  }

  async updateGmUser(
    userId: string,
    idempotencyKey: string,
    input: GmUpdateUserInput,
  ): Promise<GmUser> {
    return this.transport.request<GmUser>(
      `/gm/users/${encodeURIComponent(userId)}`,
      {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify(input),
      },
    );
  }

  async listGmAudit(limit = 20): Promise<GmAuditPage> {
    return this.transport.request<GmAuditPage>(
      `/gm/audit?limit=${encodeURIComponent(String(limit))}`,
    );
  }

  async listGmSandboxes(limit = 20, cursor?: string): Promise<GmSandboxPage> {
    const query = new URLSearchParams({ limit: String(limit) });
    if (cursor !== undefined && cursor.length > 0) {
      query.set('cursor', cursor);
    }
    return this.transport.request<GmSandboxPage>(
      `/gm/sandboxes?${query.toString()}`,
    );
  }

  async createGmSandbox(
    idempotencyKey: string,
    input: GmCreateSandboxInput,
  ): Promise<GmSandbox> {
    return this.transport.request<GmSandbox>('/gm/sandboxes', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify(input),
    });
  }

  async executeGmSandboxCommand(
    sandboxId: string,
    idempotencyKey: string,
    input: GmSandboxCommandInput,
  ): Promise<GmCommandReceipt> {
    const body: Record<string, unknown> = { ...input };
    delete body.actionId;
    return this.transport.request<GmCommandReceipt>(
      `/gm/sandboxes/${encodeURIComponent(sandboxId)}/commands`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify(body),
      },
    );
  }

}

export const accountApi = new AccountApi();
