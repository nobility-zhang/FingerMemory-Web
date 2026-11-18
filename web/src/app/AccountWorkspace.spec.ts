import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type {
  AccountClient,
  LexiconView,
  PracticeSessionView,
  SessionEnvelope,
  SessionItemView,
  UserView,
} from '../api/client';
import AccountWorkspace from './AccountWorkspace.vue';

const user: UserView = {
  id: 'user-a',
  username: 'alice',
  displayName: 'Alice',
  preferences: {
    nickname: 'Alice',
    avatarKey: 'starter',
    timezone: 'UTC',
    dailyGoal: 20,
    theme: 'starlit',
    soundEnabled: true,
    shortcutsJson: '{}',
  },
};

let mountedApp: ReturnType<typeof createApp> | null = null;

afterEach(() => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
});

describe('AccountWorkspace', () => {
  it('logs in and creates a private lexicon through the account client', async () => {
    let currentUser: UserView | null = null;
    const lexicons: LexiconView[] = [];
    const api: AccountClient = {
      loadCurrentUser: async () => currentUser,
      login: vi.fn(async () => {
        currentUser = user;
        return user;
      }),
      register: vi.fn(),
      logout: vi.fn(async () => {
        currentUser = null;
      }),
      changePassword: vi.fn(),
      listLexicons: async () => [...lexicons],
      createLexicon: vi.fn(async (name: string, description: string) => {
        const lexicon: LexiconView = {
          id: 'lexicon-a',
          name,
          description,
          source: 'user',
          archivedAtMs: null,
          createdAtMs: 1,
          updatedAtMs: 1,
          version: 0,
        };
        lexicons.push(lexicon);
        return lexicon;
      }),
      loadQueuePlan: vi.fn(async () => ({
        at: 1,
        timeZone: 'UTC',
        dayStartAt: 0,
        dayEndAt: 86_400_000,
        settings: { newLimit: 10, reviewLimit: 50, paused: false },
        dueAvailableCount: 0,
        newAvailableCount: 0,
        selected: [],
        dataVersion: 0,
      })),
      listActiveSessions: vi.fn(async () => ({ sessions: [], dataVersion: 0 })),
      createSession: vi.fn(async () => {
        throw new Error('not used');
      }),
      getSession: vi.fn(async () => {
        throw new Error('not used');
      }),
      updateSession: vi.fn(async () => {
        throw new Error('not used');
      }),
      submitPracticeResponse: vi.fn(async () => {
        throw new Error('not used');
      }),
      submitPracticeCompletion: vi.fn(async () => {
        throw new Error('not used');
      }),
      previewReview: vi.fn(async () => {
        throw new Error('not used');
      }),
      submitReview: vi.fn(async () => {
        throw new Error('not used');
      }),
      createBattle: vi.fn(),
      getBattle: vi.fn(),
      claimBattleLearning: vi.fn(),
      playBattleCard: vi.fn(),
      useBattleCompanionSkill: vi.fn(),
      endBattleTurn: vi.fn(),
      previewContentPackage: vi.fn(),
      installContentPackage: vi.fn(),
      listContentPackages: vi.fn(async () => []),
      previewLocalMigration: vi.fn(async () => {
        throw new Error('not used');
      }),
      importLocalMigration: vi.fn(async () => {
        throw new Error('not used');
      }),
      exportAccountBackup: vi.fn(async () => {
        throw new Error('not used');
      }),
      previewAccountBackup: vi.fn(async () => {
        throw new Error('not used');
      }),
      restoreAccountBackup: vi.fn(async () => {
        throw new Error('not used');
      }),
      loadGmContext: vi.fn(),
      listGmUsers: vi.fn(),
      createGmUser: vi.fn(),
      updateGmUser: vi.fn(),
      listGmAudit: vi.fn(),
      listGmSandboxes: vi.fn(),
      createGmSandbox: vi.fn(),
      executeGmSandboxCommand: vi.fn(),
    };

    const root = document.createElement('div');
    document.body.append(root);
    const app = createApp(AccountWorkspace, { api });
    mountedApp = app;
    app.mount(root);
    await nextTick();

    await vi.waitFor(() => {
      expect(root.textContent).toContain('登录账号');
    });
    const username = root.querySelector<HTMLInputElement>('input[autocomplete="username"]');
    const password = root.querySelector<HTMLInputElement>('input[autocomplete="current-password"]');
    username!.value = 'alice';
    username!.dispatchEvent(new Event('input', { bubbles: true }));
    password!.value = 'correct horse battery';
    password!.dispatchEvent(new Event('input', { bubbles: true }));
    root.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true }));

    await vi.waitFor(() => {
      expect(root.textContent).toContain('@alice');
      expect(root.textContent).toContain('账号中还没有词库');
    });
    const nameInput = root.querySelector<HTMLInputElement>('.new-lexicon-form input');
    nameInput!.value = '私人词库';
    nameInput!.dispatchEvent(new Event('input', { bubbles: true }));
    root.querySelector('.new-lexicon-form')?.dispatchEvent(
      new Event('submit', { bubbles: true }),
    );

    await vi.waitFor(() => {
      expect(root.textContent).toContain('私人词库');
      expect(root.textContent).toContain('lexicon-a');
    });
    expect(api.login).toHaveBeenCalledWith('alice', 'correct horse battery');
    expect(api.createLexicon).toHaveBeenCalledWith('私人词库', '');
  });

  it('runs one keyboard response through preview and explicit formal rating', async () => {
    const item: SessionItemView = {
      wordEntry: {
        id: 'word-1',
        term: 'memory',
        meaning: '记忆',
        acceptedAnswers: ['memory'],
        hints: [],
        normalizedTerm: 'memory',
        createdAt: 1,
        updatedAt: 1,
      },
      studyItem: {
        id: 'study-1',
        wordEntryId: 'word-1',
        direction: 'meaning-to-spelling',
        createdAt: 1,
        updatedAt: 1,
      },
      lexiconItem: null,
      reviewVersion: 0,
    };
    const session: PracticeSessionView = {
      id: 'session-1',
      modeId: 'keyboard-recall',
      status: 'active',
      queue: [
        {
          studyItemId: 'study-1',
          wordEntryId: 'word-1',
          status: 'pending',
          source: 'new',
        },
      ],
      currentIndex: 0,
      currentStudyItemId: 'study-1',
      interactions: {},
      pendingAnswer: null,
      speed: 1,
      reduceMotion: false,
      createdAt: 1,
      updatedAt: 1,
      pausedAt: null,
      completedAt: null,
      version: 0,
    };
    const sessionEnvelope: SessionEnvelope = {
      session,
      items: [item],
      dataVersion: 1,
    };
    const pending = {
      studyItemId: 'study-1',
      response: 'memory',
      answerWasHidden: true,
      hintUsed: false,
      answerRevealed: false,
      inputMatchedAnswer: true,
      practiceAttemptId: 'attempt-1',
      reviewSubmissionId: 'review-1',
      submittedAt: 2,
    };
    const previews = {
      previews: {
        again: { rating: 'again', dueAt: 3, scheduledDays: 0 },
        hard: { rating: 'hard', dueAt: 4, scheduledDays: 1 },
        good: { rating: 'good', dueAt: 5, scheduledDays: 2 },
        easy: { rating: 'easy', dueAt: 6, scheduledDays: 3 },
      },
      schedulerPackageVersion: '6.0.0',
      schedulerModelVersion: 'fsrs-7',
      schedulerConfigHash: 'hash',
      reviewedAtMs: 2,
      reviewVersion: 0,
      sessionVersion: 1,
      dataVersion: 2,
    };
    const submitReview = vi.fn(async () => ({
      outcome: 'created' as const,
      state: { dueAt: 5, version: 1 },
      log: { submissionId: 'review-1', reviewedAt: 2 },
      session: null,
      dataVersion: 3,
      rewardEligible: true,
    }));
    const api = {
      loadCurrentUser: async () => user,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn(),
      changePassword: vi.fn(),
      listLexicons: async () => [],
      createLexicon: vi.fn(),
      loadQueuePlan: async () => ({
        at: 1,
        timeZone: 'UTC',
        dayStartAt: 0,
        dayEndAt: 86_400_000,
        settings: { newLimit: 10, reviewLimit: 50, paused: false },
        dueAvailableCount: 0,
        newAvailableCount: 1,
        selected: [{ studyItem: item.studyItem, wordEntry: item.wordEntry, source: 'new' as const }],
        dataVersion: 0,
      }),
      listActiveSessions: vi.fn(async () => ({ sessions: [], dataVersion: 0 })),
      createSession: vi.fn(async () => sessionEnvelope),
      getSession: vi.fn(async () => ({
        session: { ...session, status: 'completed' as const, version: 2 },
        items: [item],
        dataVersion: 3,
      })),
      updateSession: vi.fn(),
      submitPracticeResponse: vi.fn(async () => ({
        attempt: {},
        pendingAnswer: pending,
        session: { ...session, pendingAnswer: pending, version: 1 },
        dataVersion: 2,
      })),
      submitPracticeCompletion: vi.fn(),
      previewReview: vi.fn(async () => previews),
      submitReview,
      createBattle: vi.fn(),
      getBattle: vi.fn(),
      claimBattleLearning: vi.fn(),
      playBattleCard: vi.fn(),
      useBattleCompanionSkill: vi.fn(),
      endBattleTurn: vi.fn(),
      previewContentPackage: vi.fn(),
      installContentPackage: vi.fn(),
      listContentPackages: vi.fn(async () => []),
      previewLocalMigration: vi.fn(),
      importLocalMigration: vi.fn(),
      exportAccountBackup: vi.fn(),
      previewAccountBackup: vi.fn(),
      restoreAccountBackup: vi.fn(),
      loadGmContext: vi.fn(),
      listGmUsers: vi.fn(),
      createGmUser: vi.fn(),
      updateGmUser: vi.fn(),
      listGmAudit: vi.fn(),
      listGmSandboxes: vi.fn(),
      createGmSandbox: vi.fn(),
      executeGmSandboxCommand: vi.fn(),
    } as AccountClient;
    const root = document.createElement('div');
    document.body.append(root);
    const app = createApp(AccountWorkspace, { api });
    mountedApp = app;
    app.mount(root);

    await vi.waitFor(() => {
      expect(root.textContent).toContain('开始在线训练');
    });
    const start = [...root.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === '开始在线训练',
    );
    start?.click();
    await vi.waitFor(() => {
      expect(root.textContent).toContain('记忆');
    });
    const answer = root.querySelector<HTMLInputElement>('.online-answer input');
    answer!.value = 'memory';
    answer!.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    const submitAnswer = [...root.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === '提交答案',
    );
    submitAnswer?.click();

    await vi.waitFor(() => {
      expect(root.textContent).toContain('Again');
      expect(root.textContent).toContain('Easy');
    });
    const good = [...root.querySelectorAll('button')].find(
      (button) => button.textContent?.includes('Good'),
    );
    good?.click();

    await vi.waitFor(() => {
      expect(submitReview).toHaveBeenCalledWith(
        expect.objectContaining({
          sessionId: 'session-1',
          submissionId: 'review-1',
          rating: 'good',
          expectedDataVersion: 2,
          expectedReviewVersion: 0,
          expectedSessionVersion: 1,
        }),
      );
    });
  });

  it('runs an online classic flashcard with Space, answer reveal and Again', async () => {
    const item: SessionItemView = {
      wordEntry: {
        id: 'word-flash',
        term: 'lantern',
        meaning: '提灯',
        acceptedAnswers: ['lantern'],
        hints: [],
        normalizedTerm: 'lantern',
        createdAt: 1,
        updatedAt: 1,
      },
      studyItem: {
        id: 'study-flash',
        wordEntryId: 'word-flash',
        direction: 'meaning-to-spelling',
        createdAt: 1,
        updatedAt: 1,
      },
      lexiconItem: null,
      reviewVersion: 0,
    };
    const session: PracticeSessionView = {
      id: 'session-flash',
      modeId: 'flashcard',
      status: 'active',
      queue: [{
        studyItemId: 'study-flash',
        wordEntryId: 'word-flash',
        status: 'pending',
        source: 'new',
      }],
      currentIndex: 0,
      currentStudyItemId: 'study-flash',
      interactions: {},
      pendingAnswer: null,
      speed: 1,
      reduceMotion: false,
      createdAt: 1,
      updatedAt: 1,
      pausedAt: null,
      completedAt: null,
      version: 0,
    };
    const envelope: SessionEnvelope = {
      session,
      items: [item],
      dataVersion: 1,
    };
    const pending = {
      studyItemId: 'study-flash',
      response: '',
      answerWasHidden: true,
      hintUsed: false,
      answerRevealed: true,
      inputMatchedAnswer: false,
      assessmentType: 'flashcard-self-report' as const,
      assessmentPolicyVersion: 2 as const,
      assessmentStage: 'reveal-for-check' as const,
      priorExposure: false,
      explicitNotRemembered: false,
      practiceAttemptId: 'flash-attempt',
      reviewSubmissionId: 'flash-review',
      submittedAt: 2,
    };
    const createSession = vi.fn(async () => envelope);
    const submitReview = vi.fn(async () => ({
      outcome: 'created' as const,
      state: { dueAt: 3, version: 1 },
      log: { submissionId: 'flash-review', reviewedAt: 2 },
      session: null,
      dataVersion: 3,
      rewardEligible: true,
    }));
    const api = {
      loadCurrentUser: async () => user,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn(),
      changePassword: vi.fn(),
      listLexicons: async () => [],
      createLexicon: vi.fn(),
      loadQueuePlan: async () => ({
        at: 1,
        timeZone: 'UTC',
        dayStartAt: 0,
        dayEndAt: 86_400_000,
        settings: { newLimit: 10, reviewLimit: 50, paused: false },
        dueAvailableCount: 0,
        newAvailableCount: 1,
        selected: [{
          studyItem: item.studyItem,
          wordEntry: item.wordEntry,
          source: 'new' as const,
        }],
        dataVersion: 0,
      }),
      listActiveSessions: vi.fn(async () => ({ sessions: [], dataVersion: 0 })),
      createSession,
      getSession: vi.fn(async () => ({
        session: {
          ...session,
          status: 'completed' as const,
          currentStudyItemId: null,
          completedAt: 3,
          version: 2,
        },
        items: [item],
        dataVersion: 3,
      })),
      updateSession: vi.fn(),
      submitPracticeResponse: vi.fn(async () => ({
        attempt: {},
        pendingAnswer: pending,
        session: { ...session, pendingAnswer: pending, version: 1 },
        dataVersion: 2,
      })),
      submitPracticeCompletion: vi.fn(),
      previewReview: vi.fn(async () => ({
        previews: {
          again: { rating: 'again', dueAt: 3, scheduledDays: 0 },
          hard: { rating: 'hard', dueAt: 4, scheduledDays: 1 },
          good: { rating: 'good', dueAt: 5, scheduledDays: 2 },
          easy: { rating: 'easy', dueAt: 6, scheduledDays: 3 },
        },
        schedulerPackageVersion: '6.0.0',
        schedulerModelVersion: 'fsrs-7',
        schedulerConfigHash: 'hash',
        reviewedAtMs: 2,
        reviewVersion: 0,
        sessionVersion: 1,
        dataVersion: 2,
      })),
      submitReview,
      createBattle: vi.fn(),
      getBattle: vi.fn(),
      claimBattleLearning: vi.fn(),
      playBattleCard: vi.fn(),
      useBattleCompanionSkill: vi.fn(),
      endBattleTurn: vi.fn(),
      previewContentPackage: vi.fn(),
      installContentPackage: vi.fn(),
      listContentPackages: vi.fn(async () => []),
      previewLocalMigration: vi.fn(),
      importLocalMigration: vi.fn(),
      exportAccountBackup: vi.fn(),
      previewAccountBackup: vi.fn(),
      restoreAccountBackup: vi.fn(),
      loadGmContext: vi.fn(),
      listGmUsers: vi.fn(),
      createGmUser: vi.fn(),
      updateGmUser: vi.fn(),
      listGmAudit: vi.fn(),
      listGmSandboxes: vi.fn(),
      createGmSandbox: vi.fn(),
      executeGmSandboxCommand: vi.fn(),
    } as AccountClient;
    const root = document.createElement('div');
    document.body.append(root);
    const app = createApp(AccountWorkspace, { api });
    mountedApp = app;
    app.mount(root);

    await vi.waitFor(() => {
      expect(root.textContent).toContain('开始在线经典闪卡');
    });
    const start = [...root.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === '开始在线经典闪卡',
    );
    start?.click();
    await vi.waitFor(() => {
      expect(createSession).toHaveBeenCalledWith(
        expect.objectContaining({ modeId: 'flashcard' }),
      );
      expect(root.textContent).toContain('提灯');
      expect(root.textContent).not.toContain('lantern');
    });

    window.dispatchEvent(new KeyboardEvent('keydown', {
      key: ' ',
      bubbles: true,
      cancelable: true,
    }));
    await vi.waitFor(() => {
      expect(root.textContent).toContain('lantern');
      expect(root.textContent).toContain('Again');
    });
    window.dispatchEvent(new KeyboardEvent('keydown', {
      key: '1',
      bubbles: true,
      cancelable: true,
    }));

    await vi.waitFor(() => {
      expect(submitReview).toHaveBeenCalledWith(
        expect.objectContaining({
          sessionId: 'session-flash',
          submissionId: 'flash-review',
          rating: 'again',
          assessmentType: 'flashcard-self-report',
          assessmentPolicyVersion: 2,
          assessmentStage: 'reveal-for-check',
          answerWasHidden: true,
          answerRevealed: true,
        }),
      );
    });
  });
});
