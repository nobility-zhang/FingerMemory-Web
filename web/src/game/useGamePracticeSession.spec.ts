import { ref } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import type {
  AccountClient,
  PracticeSessionView,
  QueuePlanView,
  SessionEnvelope,
} from '../api/client';
import { gameErrorMessage } from './gameErrorMessage';
import { useGamePracticeSession } from './useGamePracticeSession';

const item: SessionEnvelope['items'][number] = {
  wordEntry: {
    id: 'word-session',
    term: 'memory',
    meaning: '记忆',
    acceptedAnswers: ['memory'],
    hints: ['以 m 开头'],
    normalizedTerm: 'memory',
    createdAt: 1,
    updatedAt: 1,
  },
  studyItem: {
    id: 'study-session',
    wordEntryId: 'word-session',
    direction: 'meaning-to-spelling',
    createdAt: 1,
    updatedAt: 1,
  },
  lexiconItem: null,
  reviewVersion: 0,
};

const plan: QueuePlanView = {
  at: 1,
  timeZone: 'UTC',
  dayStartAt: 0,
  dayEndAt: 86_400_000,
  settings: { newLimit: 10, reviewLimit: 50, paused: false },
  dueAvailableCount: 0,
  newAvailableCount: 1,
  selected: [],
  dataVersion: 0,
};

function practiceSession(
  overrides: Partial<PracticeSessionView> = {},
): PracticeSessionView {
  return {
    id: 'session-flow',
    modeId: 'keyboard-recall',
    status: 'active',
    queue: [{
      studyItemId: 'study-session',
      wordEntryId: 'word-session',
      status: 'pending',
      source: 'new',
    }],
    currentIndex: 0,
    currentStudyItemId: 'study-session',
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

function envelope(
  session = practiceSession(),
  dataVersion = 1,
): SessionEnvelope {
  return { session, items: [item], dataVersion };
}

function createApi() {
  return {
    createSession: vi.fn(async () => envelope()),
    getSession: vi.fn(async () => envelope(
      practiceSession({
        status: 'completed',
        currentStudyItemId: null,
        completedAt: 3,
        version: 2,
      }),
      3,
    )),
    updateSession: vi.fn(async () => envelope()),
    submitPracticeResponse: vi.fn(async () => ({
      attempt: {},
      pendingAnswer: {
        studyItemId: 'study-session',
        response: 'memory',
        answerWasHidden: true,
        hintUsed: false,
        answerRevealed: false,
        inputMatchedAnswer: true,
        practiceAttemptId: 'attempt-flow',
        reviewSubmissionId: 'review-flow',
        submittedAt: 2,
      },
      session: practiceSession({ version: 1 }),
      dataVersion: 2,
    })),
    submitPracticeCompletion: vi.fn(async () => ({
      attempt: {
        id: 'practice-flow',
        inputMatchedAnswer: true,
        hintUsed: false,
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
      log: { submissionId: 'review-flow', reviewedAt: 2 },
      session: null,
      dataVersion: 3,
      rewardEligible: true,
    })),
  } satisfies Pick<
    AccountClient,
    | 'createSession'
    | 'getSession'
    | 'updateSession'
    | 'submitPracticeResponse'
    | 'submitPracticeCompletion'
    | 'previewReview'
    | 'submitReview'
  >;
}

function createSession(
  api: ReturnType<typeof createApi>,
  onSessionCompleted = vi.fn(),
) {
  const phase = ref<'lobby' | 'typing' | 'rating' | 'paused' | 'result'>(
    'lobby',
  );
  const session = useGamePracticeSession({
    api: () => api,
    describeError: gameErrorMessage,
    phase,
    reduceMotion: ref(false),
    activeSession: () => undefined,
    scrollToTop: vi.fn(),
    requestAnswerFocus: vi.fn(),
    onSessionCompleted,
  });
  return { phase, session, onSessionCompleted };
}

describe('useGamePracticeSession', () => {
  it('runs create, answer, explicit rating and result through the application service', async () => {
    const api = createApi();
    const flow = createSession(api);

    await expect(flow.session.startSession(plan)).resolves.toBe(true);
    expect(flow.phase.value).toBe('typing');
    expect(flow.session.currentItem.value?.studyItem.id).toBe('study-session');

    flow.session.answer.value = 'memory';
    await flow.session.submitFormalAnswer();
    expect(flow.phase.value).toBe('rating');
    expect(flow.session.pendingAnswer.value?.reviewSubmissionId).toBe(
      'review-flow',
    );

    await flow.session.rateReview('good');

    expect(api.submitReview).toHaveBeenCalledWith(
      expect.objectContaining({
        sessionId: 'session-flow',
        submissionId: 'review-flow',
        rating: 'good',
      }),
    );
    expect(flow.phase.value).toBe('result');
    expect(flow.session.result.value.reviewedCount).toBe(1);
    expect(flow.session.result.value.ratings.good).toBe(1);
    expect(flow.onSessionCompleted).toHaveBeenCalledOnce();
  });

  it('ignores a create-session response that arrives after disposal', async () => {
    let resolveCreated: ((value: SessionEnvelope) => void) | undefined;
    const pendingCreated = new Promise<SessionEnvelope>((resolve) => {
      resolveCreated = resolve;
    });
    const api = createApi();
    api.createSession.mockReturnValueOnce(pendingCreated);
    const flow = createSession(api);

    const starting = flow.session.startSession(plan);
    flow.session.dispose();
    resolveCreated?.(envelope());

    await expect(starting).resolves.toBe(false);
    expect(flow.phase.value).toBe('lobby');
    expect(flow.session.session.value).toBeUndefined();
  });

  it('pauses an active session before leaving to the lobby', async () => {
    const api = createApi();
    const flow = createSession(api);
    await flow.session.startSession(plan);

    await expect(flow.session.pauseForExit()).resolves.toBe(true);

    expect(api.updateSession).toHaveBeenCalledWith(
      expect.objectContaining({
        sessionId: 'session-flow',
        status: 'paused',
      }),
    );
  });
});
