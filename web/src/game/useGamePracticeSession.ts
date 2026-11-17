import {
  computed,
  nextTick,
  ref,
  type ComputedRef,
  type Ref,
} from 'vue';
import type {
  AccountClient,
  PendingAnswerView,
  QueuePlanView,
  ReviewPreviewView,
  SessionEnvelope,
  SessionItemView,
} from '../api/client';
import type { GamePhase } from './shortcutStateMachine';
import type { GameRunResult } from './types';

type PracticeSessionApi = Pick<
  AccountClient,
  | 'createSession'
  | 'getSession'
  | 'updateSession'
  | 'submitPracticeResponse'
  | 'submitPracticeCompletion'
  | 'previewReview'
  | 'submitReview'
>;

interface UseGamePracticeSessionOptions {
  api: () => PracticeSessionApi;
  describeError(cause: unknown): string;
  phase: Ref<GamePhase>;
  reduceMotion: Ref<boolean>;
  activeSession(): SessionEnvelope | undefined;
  scrollToTop(): void;
  requestAnswerFocus(): void;
  onSessionCompleted(): void;
}

export type SessionReloadResult = 'reloaded' | 'missing' | 'failed';

export function useGamePracticeSession({
  api,
  describeError,
  phase,
  reduceMotion,
  activeSession,
  scrollToTop,
  requestAnswerFocus,
  onSessionCompleted,
}: UseGamePracticeSessionOptions): {
  session: Ref<SessionEnvelope | undefined>;
  currentItem: ComputedRef<SessionItemView | undefined>;
  currentHint: ComputedRef<string | null>;
  answer: Ref<string>;
  pendingAnswer: Ref<PendingAnswerView | undefined>;
  reviewPreviews: Ref<ReviewPreviewView | undefined>;
  hintUsed: Ref<boolean>;
  answerRevealed: Ref<boolean>;
  busy: Ref<boolean>;
  error: Ref<string | null>;
  combo: Ref<number>;
  reviewedCount: Ref<number>;
  practiceCount: Ref<number>;
  result: ComputedRef<GameRunResult>;
  lastRating: Ref<'again' | 'hard' | 'good' | 'easy' | undefined>;
  enterSession(nextSession: SessionEnvelope): Promise<void>;
  startSession(plan?: QueuePlanView): Promise<boolean>;
  resumeSession(): Promise<void>;
  pauseSession(): Promise<void>;
  submitFormalAnswer(): Promise<void>;
  retryReviewPreview(): Promise<void>;
  rateReview(rating: 'again' | 'hard' | 'good' | 'easy'): Promise<void>;
  completePractice(outcome: 'completed' | 'skipped'): Promise<void>;
  showNextHint(): void;
  revealAnswer(): void;
  skipItem(): Promise<void>;
  reloadSession(
    fetchActiveSessions: () => Promise<SessionEnvelope[]>,
  ): Promise<SessionReloadResult>;
  pauseForExit(): Promise<boolean>;
  pauseForUnmount(): Promise<void>;
  resetSession(): void;
  dispose(): void;
} {
  const session = ref<SessionEnvelope>();
  const answer = ref('');
  const pendingAnswer = ref<PendingAnswerView>();
  const reviewPreviews = ref<ReviewPreviewView>();
  const hintIndex = ref(0);
  const hintUsed = ref(false);
  const answerRevealed = ref(false);
  const attemptIds = ref<{
    practiceAttemptId: string;
    reviewSubmissionId: string;
  }>();
  const busy = ref(false);
  const error = ref<string | null>(null);
  const combo = ref(0);
  const reviewedCount = ref(0);
  const practiceCount = ref(0);
  const matchedCount = ref(0);
  const totalAnswerCount = ref(0);
  const ratings = ref<Record<'again' | 'hard' | 'good' | 'easy', number>>({
    again: 0,
    hard: 0,
    good: 0,
    easy: 0,
  });
  const startedAt = ref(0);
  const endedAt = ref(0);
  const lastRating = ref<'again' | 'hard' | 'good' | 'easy'>();
  let generation = 0;

  const currentItem = computed<SessionItemView | undefined>(() => {
    const studyItemId = session.value?.session.currentStudyItemId;
    if (studyItemId === null || studyItemId === undefined) {
      return undefined;
    }
    return session.value?.items.find(
      (item) => item.studyItem.id === studyItemId,
    );
  });
  const currentHint = computed(() => {
    const hints = currentItem.value?.wordEntry.hints ?? [];
    return hints[hintIndex.value - 1] ?? null;
  });
  const result = computed<GameRunResult>(() => ({
    reviewedCount: reviewedCount.value,
    practiceCount: practiceCount.value,
    matchedCount: matchedCount.value,
    totalAnswerCount: totalAnswerCount.value,
    startedAt: startedAt.value,
    endedAt: endedAt.value,
    ratings: ratings.value,
  }));

  function newId(): string {
    if (typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
    return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function currentTimeZone(): string {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  }

  function ensureAttemptIds() {
    attemptIds.value ??= {
      practiceAttemptId: newId(),
      reviewSubmissionId: newId(),
    };
    return attemptIds.value;
  }

  function resetItemState(): void {
    answer.value = '';
    pendingAnswer.value = undefined;
    reviewPreviews.value = undefined;
    hintIndex.value = 0;
    hintUsed.value = false;
    answerRevealed.value = false;
    attemptIds.value = undefined;
    error.value = null;
  }

  function resetRun(): void {
    combo.value = 0;
    reviewedCount.value = 0;
    practiceCount.value = 0;
    matchedCount.value = 0;
    totalAnswerCount.value = 0;
    ratings.value = { again: 0, hard: 0, good: 0, easy: 0 };
    startedAt.value = 0;
    endedAt.value = 0;
    lastRating.value = undefined;
  }

  async function loadReviewPreviews(
    current: SessionEnvelope,
    studyItemId: string,
    reviewVersion: number,
  ): Promise<ReviewPreviewView> {
    return api().previewReview({
      sessionId: current.session.id,
      studyItemId,
      expectedDataVersion: current.dataVersion,
      expectedSessionVersion: current.session.version,
      expectedReviewVersion: reviewVersion,
    });
  }

  async function focusAnswerAfterRender(lifecycle: number): Promise<void> {
    await nextTick();
    if (lifecycle === generation) {
      requestAnswerFocus();
    }
  }

  async function enterSession(nextSession: SessionEnvelope): Promise<void> {
    const lifecycle = generation;
    scrollToTop();
    session.value = nextSession;
    reduceMotion.value = nextSession.session.reduceMotion;
    resetRun();
    startedAt.value = Date.now();
    resetItemState();
    const storedPending = nextSession.session.pendingAnswer;
    const item = nextSession.items.find(
      (value) =>
        value.studyItem.id === nextSession.session.currentStudyItemId,
    );
    if (nextSession.session.status === 'completed') {
      endedAt.value = Date.now();
      onSessionCompleted();
      phase.value = 'result';
      return;
    }
    if (storedPending !== null && item !== undefined) {
      pendingAnswer.value = storedPending;
      phase.value =
        nextSession.session.status === 'paused' ? 'paused' : 'rating';
      try {
        const previews = await loadReviewPreviews(
          nextSession,
          storedPending.studyItemId,
          item.reviewVersion,
        );
        if (lifecycle === generation) {
          reviewPreviews.value = previews;
        }
      } catch (cause) {
        if (lifecycle === generation) {
          error.value = describeError(cause);
        }
      }
      return;
    }
    phase.value =
      nextSession.session.status === 'paused' ? 'paused' : 'typing';
    await focusAnswerAfterRender(lifecycle);
  }

  async function startSession(plan?: QueuePlanView): Promise<boolean> {
    if (busy.value || phase.value !== 'lobby') {
      return false;
    }
    const resumed = activeSession();
    if (resumed !== undefined) {
      await enterSession(resumed);
      return true;
    }

    const lifecycle = generation;
    busy.value = true;
    error.value = null;
    try {
      reduceMotion.value = window.matchMedia?.(
        '(prefers-reduced-motion: reduce)',
      ).matches ?? false;
      const created = await api().createSession({
        modeId: 'keyboard-recall',
        timeZone: currentTimeZone(),
        newLimit: plan?.settings.newLimit,
        reviewLimit: plan?.settings.reviewLimit,
        reduceMotion: reduceMotion.value,
      });
      if (lifecycle !== generation) {
        return false;
      }
      await enterSession(created);
      return true;
    } catch (cause) {
      if (lifecycle === generation) {
        error.value = describeError(cause);
      }
      return false;
    } finally {
      if (lifecycle === generation) {
        busy.value = false;
      }
    }
  }

  async function resumeSession(): Promise<void> {
    const current = session.value;
    if (current === undefined || busy.value) {
      return;
    }
    const lifecycle = generation;
    busy.value = true;
    error.value = null;
    try {
      const resumed = await api().updateSession({
        sessionId: current.session.id,
        expectedDataVersion: current.dataVersion,
        expectedVersion: current.session.version,
        status: 'active',
      });
      if (lifecycle !== generation) {
        return;
      }
      session.value = resumed;
      phase.value =
        pendingAnswer.value !== undefined
          ? 'rating'
          : answerRevealed.value
            ? 'answer-ready'
            : 'typing';
      if (phase.value === 'typing') {
        await focusAnswerAfterRender(lifecycle);
      }
    } catch (cause) {
      if (lifecycle === generation) {
        error.value = describeError(cause);
      }
    } finally {
      if (lifecycle === generation) {
        busy.value = false;
      }
    }
  }

  async function pauseSession(): Promise<void> {
    const current = session.value;
    if (
      current === undefined ||
      busy.value ||
      phase.value === 'paused'
    ) {
      return;
    }
    const lifecycle = generation;
    busy.value = true;
    error.value = null;
    try {
      const paused = await api().updateSession({
        sessionId: current.session.id,
        expectedDataVersion: current.dataVersion,
        expectedVersion: current.session.version,
        status: 'paused',
      });
      if (lifecycle !== generation) {
        return;
      }
      session.value = paused;
      phase.value = 'paused';
    } catch (cause) {
      if (lifecycle === generation) {
        error.value = describeError(cause);
      }
    } finally {
      if (lifecycle === generation) {
        busy.value = false;
      }
    }
  }

  async function submitFormalAnswer(): Promise<void> {
    const current = session.value;
    const item = currentItem.value;
    if (
      current === undefined ||
      item === undefined ||
      busy.value ||
      answer.value.trim().length === 0
    ) {
      return;
    }
    if (hintUsed.value || answerRevealed.value) {
      await completePractice('completed');
      return;
    }

    const lifecycle = generation;
    busy.value = true;
    error.value = null;
    const ids = ensureAttemptIds();
    try {
      const response = await api().submitPracticeResponse({
        sessionId: current.session.id,
        practiceAttemptId: ids.practiceAttemptId,
        reviewSubmissionId: ids.reviewSubmissionId,
        studyItemId: item.studyItem.id,
        response: answer.value,
        expectedDataVersion: current.dataVersion,
        expectedSessionVersion: current.session.version,
      });
      if (lifecycle !== generation) {
        return;
      }
      totalAnswerCount.value += 1;
      if (response.pendingAnswer.inputMatchedAnswer) {
        matchedCount.value += 1;
        combo.value += 1;
      } else {
        combo.value = 0;
      }
      const updatedSession = {
        ...current,
        session: response.session,
        dataVersion: response.dataVersion,
      };
      session.value = updatedSession;
      pendingAnswer.value = response.pendingAnswer;
      phase.value = 'rating';
      const previews = await loadReviewPreviews(
        updatedSession,
        response.pendingAnswer.studyItemId,
        item.reviewVersion,
      );
      if (lifecycle === generation) {
        reviewPreviews.value = previews;
      }
    } catch (cause) {
      if (lifecycle === generation) {
        error.value = describeError(cause);
      }
    } finally {
      if (lifecycle === generation) {
        busy.value = false;
      }
    }
  }

  async function retryReviewPreview(): Promise<void> {
    const current = session.value;
    const pending = pendingAnswer.value;
    const item = currentItem.value;
    if (
      current === undefined ||
      pending === undefined ||
      item === undefined ||
      busy.value
    ) {
      return;
    }
    const lifecycle = generation;
    busy.value = true;
    error.value = null;
    try {
      const previews = await loadReviewPreviews(
        current,
        pending.studyItemId,
        item.reviewVersion,
      );
      if (lifecycle === generation) {
        reviewPreviews.value = previews;
      }
    } catch (cause) {
      if (lifecycle === generation) {
        error.value = describeError(cause);
      }
    } finally {
      if (lifecycle === generation) {
        busy.value = false;
      }
    }
  }

  async function rateReview(
    rating: 'again' | 'hard' | 'good' | 'easy',
  ): Promise<void> {
    const current = session.value;
    const pending = pendingAnswer.value;
    const previews = reviewPreviews.value;
    if (
      current === undefined ||
      pending === undefined ||
      previews === undefined ||
      busy.value
    ) {
      return;
    }
    const lifecycle = generation;
    busy.value = true;
    error.value = null;
    try {
      await api().submitReview({
        sessionId: current.session.id,
        submissionId: pending.reviewSubmissionId,
        rating,
        expectedDataVersion: previews.dataVersion,
        expectedReviewVersion: previews.reviewVersion,
        expectedSessionVersion: previews.sessionVersion,
      });
      if (lifecycle !== generation) {
        return;
      }
      reviewedCount.value += 1;
      ratings.value[rating] += 1;
      lastRating.value = rating;
      const refreshed = await api().getSession(current.session.id);
      if (lifecycle === generation) {
        session.value = refreshed;
        await afterItemCompleted();
      }
    } catch (cause) {
      if (lifecycle === generation) {
        error.value = describeError(cause);
      }
    } finally {
      if (lifecycle === generation) {
        busy.value = false;
      }
    }
  }

  async function completePractice(
    outcome: 'completed' | 'skipped',
  ): Promise<void> {
    const current = session.value;
    const item = currentItem.value;
    if (current === undefined || item === undefined || busy.value) {
      return;
    }
    const lifecycle = generation;
    busy.value = true;
    error.value = null;
    const ids = ensureAttemptIds();
    try {
      const completion = await api().submitPracticeCompletion({
        sessionId: current.session.id,
        practiceAttemptId: ids.practiceAttemptId,
        studyItemId: item.studyItem.id,
        response: answer.value,
        answerWasHidden: true,
        hintUsed: hintUsed.value,
        answerRevealed: answerRevealed.value,
        abandoned: outcome === 'skipped',
        outcome,
        expectedDataVersion: current.dataVersion,
        expectedSessionVersion: current.session.version,
      });
      if (lifecycle !== generation) {
        return;
      }
      totalAnswerCount.value += 1;
      if (completion.attempt.inputMatchedAnswer) {
        matchedCount.value += 1;
      }
      practiceCount.value += 1;
      combo.value = 0;
      lastRating.value = undefined;
      const refreshed = await api().getSession(current.session.id);
      if (lifecycle === generation) {
        session.value = refreshed;
        await afterItemCompleted();
      }
    } catch (cause) {
      if (lifecycle === generation) {
        error.value = describeError(cause);
      }
    } finally {
      if (lifecycle === generation) {
        busy.value = false;
      }
    }
  }

  async function afterItemCompleted(): Promise<void> {
    scrollToTop();
    if (session.value?.session.status === 'completed') {
      onSessionCompleted();
      endedAt.value = Date.now();
      phase.value = 'result';
      return;
    }
    resetItemState();
    phase.value =
      session.value?.session.status === 'paused' ? 'paused' : 'typing';
    await focusAnswerAfterRender(generation);
  }

  function showNextHint(): void {
    const hints = currentItem.value?.wordEntry.hints ?? [];
    if (hints.length === 0) {
      error.value = '当前词条没有配置提示。';
      return;
    }
    if (hintIndex.value >= hints.length) {
      error.value = '提示已经全部显示。';
      return;
    }
    hintIndex.value += 1;
    hintUsed.value = true;
    error.value = null;
  }

  function revealAnswer(): void {
    if (currentItem.value === undefined || phase.value !== 'typing') {
      return;
    }
    answerRevealed.value = true;
    hintUsed.value = true;
    phase.value = 'answer-ready';
  }

  async function skipItem(): Promise<void> {
    if (!busy.value) {
      await completePractice('skipped');
    }
  }

  async function reloadSession(
    fetchActiveSessions: () => Promise<SessionEnvelope[]>,
  ): Promise<SessionReloadResult> {
    const current = session.value;
    if (current === undefined || busy.value) {
      return 'failed';
    }
    const lifecycle = generation;
    busy.value = true;
    error.value = null;
    try {
      const sessions = await fetchActiveSessions();
      if (lifecycle !== generation) {
        return 'failed';
      }
      const refreshed = sessions.find(
        (value) => value.session.id === current.session.id,
      );
      if (refreshed === undefined) {
        resetSession();
        return 'missing';
      }
      await enterSession(refreshed);
      return 'reloaded';
    } catch (cause) {
      if (lifecycle === generation) {
        error.value = describeError(cause);
      }
      return 'failed';
    } finally {
      if (lifecycle === generation) {
        busy.value = false;
      }
    }
  }

  async function pauseForExit(): Promise<boolean> {
    const current = session.value;
    if (current === undefined || busy.value) {
      return false;
    }
    if (current.session.status !== 'active') {
      return true;
    }
    const lifecycle = generation;
    busy.value = true;
    error.value = null;
    try {
      await api().updateSession({
        sessionId: current.session.id,
        expectedDataVersion: current.dataVersion,
        expectedVersion: current.session.version,
        status: 'paused',
      });
      return lifecycle === generation;
    } catch (cause) {
      if (lifecycle === generation) {
        error.value = describeError(cause);
      }
      return false;
    } finally {
      if (lifecycle === generation) {
        busy.value = false;
      }
    }
  }

  async function pauseForUnmount(): Promise<void> {
    const current = session.value;
    if (
      current === undefined ||
      current.session.status !== 'active' ||
      busy.value ||
      phase.value === 'result'
    ) {
      return;
    }
    await api().updateSession({
      sessionId: current.session.id,
      expectedDataVersion: current.dataVersion,
      expectedVersion: current.session.version,
      status: 'paused',
    }).catch(() => undefined);
  }

  function resetSession(): void {
    session.value = undefined;
    resetItemState();
  }

  function dispose(): void {
    generation += 1;
  }

  return {
    session,
    currentItem,
    currentHint,
    answer,
    pendingAnswer,
    reviewPreviews,
    hintUsed,
    answerRevealed,
    busy,
    error,
    combo,
    reviewedCount,
    practiceCount,
    result,
    lastRating,
    enterSession,
    startSession,
    resumeSession,
    pauseSession,
    submitFormalAnswer,
    retryReviewPreview,
    rateReview,
    completePractice,
    showNextHint,
    revealAnswer,
    skipItem,
    reloadSession,
    pauseForExit,
    pauseForUnmount,
    resetSession,
    dispose,
  };
}
