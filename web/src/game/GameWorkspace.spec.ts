import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type {
  AccountClient,
  PracticeSessionView,
  SessionEnvelope,
  SessionItemView,
  UserView,
} from '../api/client';
import GameWorkspace from './GameWorkspace.vue';

const user: UserView = {
  id: 'user-game',
  username: 'game-user',
  displayName: 'Game User',
  preferences: {
    nickname: 'Game User',
    avatarKey: 'starter',
    timezone: 'UTC',
    dailyGoal: 20,
    theme: 'starlit',
    soundEnabled: false,
    shortcutsJson: '{}',
  },
};

const item: SessionItemView = {
  wordEntry: {
    id: 'word-game',
    term: 'memory',
    meaning: '记忆',
    acceptedAnswers: ['memory'],
    hints: ['以 m 开头'],
    normalizedTerm: 'memory',
    createdAt: 1,
    updatedAt: 1,
  },
  studyItem: {
    id: 'study-game',
    wordEntryId: 'word-game',
    direction: 'meaning-to-spelling',
    createdAt: 1,
    updatedAt: 1,
  },
  lexiconItem: null,
  reviewVersion: 0,
};

function practiceSession(overrides: Partial<PracticeSessionView> = {}): PracticeSessionView {
  return {
    id: 'session-game',
    modeId: 'keyboard-recall',
    status: 'active',
    queue: [
      {
        studyItemId: 'study-game',
        wordEntryId: 'word-game',
        status: 'pending',
        source: 'new',
      },
    ],
    currentIndex: 0,
    currentStudyItemId: 'study-game',
    interactions: {},
    pendingAnswer: null,
    speed: 1,
    reduceMotion: false,
    createdAt: 1,
    updatedAt: 1,
    pausedAt: null,
    completedAt: null,
    version: 0,
    ...overrides,
  };
}

function sessionEnvelope(
  session = practiceSession(),
  dataVersion = 1,
): SessionEnvelope {
  return { session, items: [item], dataVersion };
}

function baseApi(): AccountClient {
  return {
    loadCurrentUser: async () => user,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    listLexicons: async () => [{
      id: 'lexicon-game',
      name: '游戏词库',
      description: '',
      source: 'user',
      archivedAtMs: null,
      createdAtMs: 1,
      updatedAtMs: 1,
      version: 0,
    }],
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
    listActiveSessions: async () => ({ sessions: [], dataVersion: 0 }),
    createSession: vi.fn(async () => sessionEnvelope()),
    getSession: vi.fn(async () => sessionEnvelope(
      practiceSession({
        status: 'completed',
        currentStudyItemId: null,
        queue: [{
          studyItemId: 'study-game',
          wordEntryId: 'word-game',
          status: 'completed',
          source: 'new',
        }],
        version: 2,
        completedAt: 2,
        endedReason: 'completed',
      }),
      3,
    )),
    updateSession: vi.fn(),
    submitPracticeResponse: vi.fn(async () => ({
      attempt: {},
      pendingAnswer: {
        studyItemId: 'study-game',
        response: 'memory',
        answerWasHidden: true,
        hintUsed: false,
        answerRevealed: false,
        inputMatchedAnswer: true,
        practiceAttemptId: 'attempt-game',
        reviewSubmissionId: 'review-game',
        submittedAt: 2,
      },
      session: practiceSession({ pendingAnswer: null, version: 1 }),
      dataVersion: 2,
    })),
    submitPracticeCompletion: vi.fn(async () => ({
      attempt: {
        id: 'practice-game',
        inputMatchedAnswer: true,
        hintUsed: true,
        answerRevealed: false,
        abandoned: false,
      },
      session: practiceSession({ version: 1 }),
      dataVersion: 2,
    })),
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
    submitReview: vi.fn(async () => ({
      outcome: 'created' as const,
      state: { dueAt: 5, version: 1 },
      log: { submissionId: 'review-game', reviewedAt: 2 },
      session: null,
      dataVersion: 3,
      rewardEligible: true,
    })),
    previewLocalMigration: vi.fn(),
    importLocalMigration: vi.fn(),
    exportAccountBackup: vi.fn(),
    previewAccountBackup: vi.fn(),
    restoreAccountBackup: vi.fn(),
  };
}

let mountedApp: ReturnType<typeof createApp> | null = null;

afterEach(() => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
});

async function mountGame(api: AccountClient) {
  const root = document.createElement('div');
  document.body.append(root);
  const app = createApp(GameWorkspace, { api });
  mountedApp = app;
  app.mount(root);
  await vi.waitFor(() => {
    expect(root.textContent).toContain('今晚的火已经升起来了');
    expect(root.textContent).toContain('莉娅');
  });
  return root;
}

function press(
  target: EventTarget,
  key: string,
  options: KeyboardEventInit = {},
): void {
  target.dispatchEvent(new KeyboardEvent('keydown', {
    key,
    bubbles: true,
    cancelable: true,
    ...options,
  }));
}

describe('GameWorkspace', () => {
  it('completes lobby, typing, rating and result with the keyboard', async () => {
    const api = baseApi();
    const root = await mountGame(api);

    press(window, 'Enter');
    await vi.waitFor(() => {
      expect(root.textContent).toContain('记忆');
    });
    const input = root.querySelector<HTMLInputElement>('#online-answer');
    input!.value = 'memory';
    input!.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    press(input!, 'Enter');

    await vi.waitFor(() => {
      expect(root.textContent).toContain('作答已封存');
      expect(root.textContent).toContain('Again');
    });
    press(window, '3');

    await vi.waitFor(() => {
      expect(api.submitReview).toHaveBeenCalledWith(
        expect.objectContaining({
          sessionId: 'session-game',
          submissionId: 'review-game',
          rating: 'good',
        }),
      );
      expect(root.textContent).toContain('本次训练完成');
      expect(root.textContent).toContain('正式复习');
      expect(root.textContent).not.toContain('经验值');
    });
  });

  it('turns a hinted answer into practice only', async () => {
    const api = baseApi();
    const root = await mountGame(api);
    press(window, 'Enter');
    await vi.waitFor(() => {
      expect(root.querySelector('#online-answer')).not.toBeNull();
    });
    const input = root.querySelector<HTMLInputElement>('#online-answer');
    press(input!, 'h', { ctrlKey: true });
    await vi.waitFor(() => {
      expect(root.textContent).toContain('以 m 开头');
    });
    input!.value = 'memory';
    input!.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    press(input!, 'Enter');

    await vi.waitFor(() => {
      expect(api.submitPracticeCompletion).toHaveBeenCalledWith(
        expect.objectContaining({
          outcome: 'completed',
          hintUsed: true,
          practiceAttemptId: expect.any(String),
        }),
      );
      expect(api.submitReview).not.toHaveBeenCalled();
    });
  });
});
