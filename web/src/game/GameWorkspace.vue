<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
} from 'vue';
import {
  CircleHelp,
  LogIn,
  RefreshCw,
  ShieldCheck,
  TriangleAlert,
  UserPlus,
} from '@lucide/vue';
import {
  accountApi,
  ApiClientError,
  type AccountClient,
  type LexiconView,
  type PendingAnswerView,
  type QueuePlanView,
  type ReviewPreviewView,
  type SessionEnvelope,
  type SessionItemView,
  type UserView,
} from '../api/client';
import CompanionPortrait from '../companions/CompanionPortrait.vue';
import innBackground from '../assets/scenes/inn/runtime/inn-warm-lantern.jpg';
import InnLobby from '../inn/InnLobby.vue';
import GameResult from './GameResult.vue';
import GameShortcutHelp from './GameShortcutHelp.vue';
import GameTrainingStage from './GameTrainingStage.vue';
import type { GameRunResult } from './types';
import { useCompanionPreferences } from './useCompanionPreferences';
import {
  resolveGameShortcut,
  type GamePhase,
  type GameShortcutAction,
} from './shortcutStateMachine';

const props = withDefaults(
  defineProps<{
    api?: AccountClient;
  }>(),
  {
    api: () => accountApi,
  },
);

const emit = defineEmits<{
  'focus-mode': [enabled: boolean];
  'classic-study': [];
}>();

const user = ref<UserView | null>(null);
const {
  selectedCompanion,
  companionDensity,
  selectCompanion,
  selectCompanionDensity,
} = useCompanionPreferences();
const phase = ref<GamePhase>('login');
const initializing = ref(true);
const busy = ref(false);
const authError = ref<string | null>(null);
const lobbyError = ref<string | null>(null);
const sessionError = ref<string | null>(null);
const authMode = ref<'login' | 'register'>('login');
const username = ref('');
const displayName = ref('');
const password = ref('');
const plan = ref<QueuePlanView>();
const lexicons = ref<LexiconView[]>([]);
const sessions = ref<SessionEnvelope[]>([]);
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
const helpOpen = ref(false);
const focusMode = ref(false);
const reduceMotion = ref(false);
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
const trainingStage = ref<InstanceType<typeof GameTrainingStage>>();
let generation = 0;

const currentItem = computed<SessionItemView | undefined>(() => {
  const studyItemId = session.value?.session.currentStudyItemId;
  if (studyItemId === null || studyItemId === undefined) {
    return undefined;
  }
  return session.value?.items.find((item) => item.studyItem.id === studyItemId);
});
const activeSession = computed(() => sessions.value[0]);
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

function errorMessage(cause: unknown): string {
  if (cause instanceof ApiClientError) {
    return cause.message;
  }
  if (cause instanceof Error && cause.message.trim().length > 0) {
    return cause.message;
  }
  return '请求失败，请稍后重试。';
}

function newId(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function currentTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

function resetPageScroll(): void {
  document.documentElement.scrollTop = 0;
  document.body.scrollTop = 0;
}

function ensureAttemptIds(): {
  practiceAttemptId: string;
  reviewSubmissionId: string;
} {
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
  sessionError.value = null;
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

async function loadLobby(): Promise<void> {
  if (user.value === null) {
    return;
  }
  const request = ++generation;
  lobbyError.value = null;
  busy.value = true;
  try {
    const [nextPlan, nextLexicons, nextSessions] = await Promise.all([
      props.api.loadQueuePlan({ timeZone: currentTimeZone() }),
      props.api.listLexicons(),
      props.api.listActiveSessions(),
    ]);
    if (request !== generation) {
      return;
    }
    plan.value = nextPlan;
    lexicons.value = nextLexicons;
    sessions.value = nextSessions.sessions;
  } catch (cause) {
    if (request === generation) {
      lobbyError.value = errorMessage(cause);
    }
  } finally {
    if (request === generation) {
      busy.value = false;
    }
  }
}

async function loadCurrentUser(): Promise<void> {
  initializing.value = true;
  reduceMotion.value = window.matchMedia?.(
    '(prefers-reduced-motion: reduce)',
  ).matches ?? false;
  try {
    user.value = await props.api.loadCurrentUser();
    if (user.value === null) {
      phase.value = 'login';
      return;
    }
    await loadLobby();
    phase.value = 'lobby';
  } catch (cause) {
    authError.value = errorMessage(cause);
    phase.value = 'login';
  } finally {
    initializing.value = false;
  }
}

async function submitAuth(): Promise<void> {
  if (busy.value) {
    return;
  }
  busy.value = true;
  authError.value = null;
  try {
    if (authMode.value === 'register') {
      await props.api.register(username.value, password.value, displayName.value);
    } else {
      user.value = await props.api.login(username.value, password.value);
    }
    password.value = '';
    if (user.value === null) {
      user.value = await props.api.loadCurrentUser();
    }
    if (user.value === null) {
      throw new Error('登录状态不可用');
    }
    await loadLobby();
    phase.value = 'lobby';
  } catch (cause) {
    authError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function enterSession(nextSession: SessionEnvelope): Promise<void> {
  resetPageScroll();
  session.value = nextSession;
  reduceMotion.value = nextSession.session.reduceMotion;
  resetRun();
  startedAt.value = Date.now();
  resetItemState();
  const storedPending = nextSession.session.pendingAnswer;
  const item = nextSession.items.find(
    (value) => value.studyItem.id === nextSession.session.currentStudyItemId,
  );
  if (nextSession.session.status === 'completed') {
    endedAt.value = Date.now();
    phase.value = 'result';
    return;
  }
  if (storedPending !== null && item !== undefined) {
    pendingAnswer.value = storedPending;
    phase.value = nextSession.session.status === 'paused' ? 'paused' : 'rating';
    try {
      reviewPreviews.value = await loadReviewPreviews(
        nextSession,
        storedPending.studyItemId,
        item.reviewVersion,
      );
    } catch (cause) {
      sessionError.value = errorMessage(cause);
    }
    return;
  }
  phase.value = nextSession.session.status === 'paused' ? 'paused' : 'typing';
  await nextTick();
  void trainingStage.value?.focusAnswer();
}

async function trainAgain(): Promise<void> {
  phase.value = 'lobby';
  session.value = undefined;
  await loadLobby();
  if (plan.value?.selected.length) {
    await startTraining();
  }
}

async function startTraining(): Promise<void> {
  if (busy.value || phase.value !== 'lobby') {
    return;
  }
  if (activeSession.value) {
    await enterSession(activeSession.value);
    return;
  }
  busy.value = true;
  lobbyError.value = null;
  try {
    reduceMotion.value = window.matchMedia?.(
      '(prefers-reduced-motion: reduce)',
    ).matches ?? false;
    const created = await props.api.createSession({
      modeId: 'keyboard-recall',
      timeZone: currentTimeZone(),
      newLimit: plan.value?.settings.newLimit,
      reviewLimit: plan.value?.settings.reviewLimit,
      reduceMotion: reduceMotion.value,
    });
    await enterSession(created);
  } catch (cause) {
    lobbyError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function resumeTraining(): Promise<void> {
  const current = session.value;
  if (current === undefined || busy.value) {
    return;
  }
  busy.value = true;
  sessionError.value = null;
  try {
    session.value = await props.api.updateSession({
      sessionId: current.session.id,
      expectedDataVersion: current.dataVersion,
      expectedVersion: current.session.version,
      status: 'active',
    });
    phase.value = pendingAnswer.value !== undefined
      ? 'rating'
      : answerRevealed.value
        ? 'answer-ready'
        : 'typing';
    await nextTick();
    if (phase.value === 'typing') {
      void trainingStage.value?.focusAnswer();
    }
  } catch (cause) {
    sessionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function pauseTraining(): Promise<void> {
  const current = session.value;
  if (current === undefined || busy.value || phase.value === 'paused') {
    return;
  }
  busy.value = true;
  sessionError.value = null;
  try {
    session.value = await props.api.updateSession({
      sessionId: current.session.id,
      expectedDataVersion: current.dataVersion,
      expectedVersion: current.session.version,
      status: 'paused',
    });
    phase.value = 'paused';
  } catch (cause) {
    sessionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function loadReviewPreviews(
  current: SessionEnvelope,
  studyItemId: string,
  reviewVersion: number,
): Promise<ReviewPreviewView> {
  return props.api.previewReview({
    sessionId: current.session.id,
    studyItemId,
    expectedDataVersion: current.dataVersion,
    expectedSessionVersion: current.session.version,
    expectedReviewVersion: reviewVersion,
  });
}

async function submitFormalAnswer(): Promise<void> {
  const current = session.value;
  const item = currentItem.value;
  if (
    current === undefined
    || item === undefined
    || busy.value
    || answer.value.trim().length === 0
  ) {
    return;
  }
  if (hintUsed.value || answerRevealed.value) {
    await completePractice('completed');
    return;
  }
  busy.value = true;
  sessionError.value = null;
  const ids = ensureAttemptIds();
  try {
    const response = await props.api.submitPracticeResponse({
      sessionId: current.session.id,
      practiceAttemptId: ids.practiceAttemptId,
      reviewSubmissionId: ids.reviewSubmissionId,
      studyItemId: item.studyItem.id,
      response: answer.value,
      expectedDataVersion: current.dataVersion,
      expectedSessionVersion: current.session.version,
    });
    totalAnswerCount.value += 1;
    if (response.pendingAnswer.inputMatchedAnswer) {
      matchedCount.value += 1;
      combo.value += 1;
    } else {
      combo.value = 0;
    }
    session.value = {
      ...current,
      session: response.session,
      dataVersion: response.dataVersion,
    };
    pendingAnswer.value = response.pendingAnswer;
    phase.value = 'rating';
    reviewPreviews.value = await loadReviewPreviews(
      session.value,
      response.pendingAnswer.studyItemId,
      item.reviewVersion,
    );
  } catch (cause) {
    sessionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function retryReviewPreview(): Promise<void> {
  const current = session.value;
  const pending = pendingAnswer.value;
  const item = currentItem.value;
  if (current === undefined || pending === undefined || item === undefined || busy.value) {
    return;
  }
  busy.value = true;
  sessionError.value = null;
  try {
    reviewPreviews.value = await loadReviewPreviews(
      current,
      pending.studyItemId,
      item.reviewVersion,
    );
  } catch (cause) {
    sessionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function rateReview(
  rating: 'again' | 'hard' | 'good' | 'easy',
): Promise<void> {
  const current = session.value;
  const pending = pendingAnswer.value;
  const previews = reviewPreviews.value;
  if (
    current === undefined
    || pending === undefined
    || previews === undefined
    || busy.value
  ) {
    return;
  }
  busy.value = true;
  sessionError.value = null;
  try {
    await props.api.submitReview({
      sessionId: current.session.id,
      submissionId: pending.reviewSubmissionId,
      rating,
      expectedDataVersion: previews.dataVersion,
      expectedReviewVersion: previews.reviewVersion,
      expectedSessionVersion: previews.sessionVersion,
    });
    reviewedCount.value += 1;
    ratings.value[rating] += 1;
    lastRating.value = rating;
    await refreshCurrentSession();
  } catch (cause) {
    sessionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function completePractice(outcome: 'completed' | 'skipped'): Promise<void> {
  const current = session.value;
  const item = currentItem.value;
  if (current === undefined || item === undefined || busy.value) {
    return;
  }
  busy.value = true;
  sessionError.value = null;
  const ids = ensureAttemptIds();
  try {
    const completion = await props.api.submitPracticeCompletion({
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
    totalAnswerCount.value += 1;
    if (completion.attempt.inputMatchedAnswer) {
      matchedCount.value += 1;
    }
    practiceCount.value += 1;
    combo.value = 0;
    lastRating.value = undefined;
    session.value = await props.api.getSession(current.session.id);
    await afterItemCompleted();
  } catch (cause) {
    sessionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function refreshCurrentSession(): Promise<void> {
  const current = session.value;
  if (current === undefined) {
    return;
  }
  session.value = await props.api.getSession(current.session.id);
  await afterItemCompleted();
}

async function afterItemCompleted(): Promise<void> {
  resetPageScroll();
  if (session.value?.session.status === 'completed') {
    if (focusMode.value) {
      setFocusMode(false);
    }
    endedAt.value = Date.now();
    phase.value = 'result';
    return;
  }
  resetItemState();
  phase.value = session.value?.session.status === 'paused' ? 'paused' : 'typing';
  await nextTick();
  void trainingStage.value?.focusAnswer();
}

function showNextHint(): void {
  const hints = currentItem.value?.wordEntry.hints ?? [];
  if (hints.length === 0) {
    sessionError.value = '当前词条没有配置提示。';
    return;
  }
  if (hintIndex.value >= hints.length) {
    sessionError.value = '提示已经全部显示。';
    return;
  }
  hintIndex.value += 1;
  hintUsed.value = true;
  sessionError.value = null;
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

async function reloadSession(): Promise<void> {
  const current = session.value;
  if (current === undefined || busy.value) {
    return;
  }
  busy.value = true;
  sessionError.value = null;
  try {
    sessions.value = (await props.api.listActiveSessions()).sessions;
    const refreshed = sessions.value.find(
      (value) => value.session.id === current.session.id,
    );
    if (refreshed === undefined) {
      await returnToLobby();
      return;
    }
    await enterSession(refreshed);
  } catch (cause) {
    sessionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function returnToLobby(): Promise<void> {
  resetPageScroll();
  if (focusMode.value) {
    setFocusMode(false);
  }
  session.value = undefined;
  resetItemState();
  phase.value = 'lobby';
  await loadLobby();
}

async function leaveTrainingToLobby(): Promise<void> {
  const current = session.value;
  if (current === undefined || busy.value) {
    return;
  }
  busy.value = true;
  sessionError.value = null;
  try {
    if (current.session.status === 'active') {
      await props.api.updateSession({
        sessionId: current.session.id,
        expectedDataVersion: current.dataVersion,
        expectedVersion: current.session.version,
        status: 'paused',
      });
    }
    await returnToLobby();
  } catch (cause) {
    sessionError.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

function toggleHelp(): void {
  helpOpen.value = !helpOpen.value;
}

function toggleFocus(): void {
  setFocusMode(!focusMode.value);
}

function setFocusMode(enabled: boolean): void {
  focusMode.value = enabled;
  emit('focus-mode', enabled);
}

function performShortcut(action: GameShortcutAction): void {
  switch (action) {
    case 'submit-answer':
      void submitFormalAnswer();
      return;
    case 'complete-practice':
      void completePractice('completed');
      return;
    case 'rate-again':
      void rateReview('again');
      return;
    case 'rate-hard':
      void rateReview('hard');
      return;
    case 'rate-good':
      void rateReview('good');
      return;
    case 'rate-easy':
      void rateReview('easy');
      return;
    case 'show-hint':
      showNextHint();
      return;
    case 'reveal-answer':
      revealAnswer();
      return;
    case 'skip-item':
      void skipItem();
      return;
    case 'pause':
      void pauseTraining();
      return;
    case 'resume':
      void resumeTraining();
      return;
    case 'toggle-help':
      toggleHelp();
      return;
    case 'close-help':
      helpOpen.value = false;
      return;
    case 'start-or-resume':
      void startTraining();
      return;
    case 'return-lobby':
      void returnToLobby();
      return;
    case 'toggle-focus':
      toggleFocus();
  }
}

function handleGlobalKeydown(event: KeyboardEvent): void {
  const target = event.target;
  const isEditable = target instanceof HTMLElement
    && (
      target.matches('input, textarea, select')
      || target.isContentEditable
    );
  const action = resolveGameShortcut({
    key: event.key,
    phase: phase.value,
    isComposing: event.isComposing,
    repeat: event.repeat,
    isEditable,
    helpOpen: helpOpen.value,
    ctrlKey: event.ctrlKey,
    metaKey: event.metaKey,
    shiftKey: event.shiftKey,
    altKey: event.altKey,
  });
  if (action === null) {
    return;
  }
  event.preventDefault();
  performShortcut(action);
}

onMounted(() => {
  window.addEventListener('keydown', handleGlobalKeydown);
  void loadCurrentUser();
});

onBeforeUnmount(() => {
  generation += 1;
  window.removeEventListener('keydown', handleGlobalKeydown);
  if (focusMode.value) {
    emit('focus-mode', false);
  }
  const current = session.value;
  if (
    current !== undefined
    && current.session.status === 'active'
    && !busy.value
    && phase.value !== 'result'
  ) {
    void props.api.updateSession({
      sessionId: current.session.id,
      expectedDataVersion: current.dataVersion,
      expectedVersion: current.session.version,
      status: 'paused',
    }).catch(() => undefined);
  }
});
</script>

<template>
  <section
    class="game-workspace"
    :class="{ 'focus-background': focusMode }"
    :style="{ '--game-backdrop': `url(${innBackground})` }"
  >
    <div v-if="initializing" class="game-loading" role="status">
      <span aria-hidden="true"></span>
      <p>正在回到暖灯旅店...</p>
    </div>

    <div v-else-if="phase === 'login'" class="auth-stage">
      <div class="auth-copy">
        <p class="eyebrow">Warm Lantern Inn</p>
        <h1>登录后，回到暖灯旅店。</h1>
        <p>
          莉娅会把正式笔记、到期时间和会话书签收在账号里。本地模式仍保持独立。
        </p>
        <div class="auth-feature-list">
          <div>
            <ShieldCheck :size="18" aria-hidden="true" />
            <span>HttpOnly 会话与 CSRF 防护</span>
          </div>
          <div>
            <RefreshCw :size="18" aria-hidden="true" />
            <span>跨设备续接同一份旅途笔记</span>
          </div>
        </div>
      </div>
      <div class="auth-card">
        <div class="auth-tabs" role="tablist" aria-label="账号操作">
          <button
            type="button"
            role="tab"
            :aria-selected="authMode === 'login'"
            :class="{ selected: authMode === 'login' }"
            @click="authMode = 'login'"
          >
            登录
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="authMode === 'register'"
            :class="{ selected: authMode === 'register' }"
            @click="authMode = 'register'"
          >
            注册
          </button>
        </div>
        <form @submit.prevent="submitAuth">
          <label>
            <span>用户名</span>
            <input
              v-model="username"
              type="text"
              autocomplete="username"
              minlength="3"
              maxlength="32"
              required
            />
          </label>
          <label v-if="authMode === 'register'">
            <span>显示名称</span>
            <input
              v-model="displayName"
              type="text"
              autocomplete="nickname"
              maxlength="80"
            />
          </label>
          <label>
            <span>密码</span>
            <input
              v-model="password"
              type="password"
              :autocomplete="authMode === 'register' ? 'new-password' : 'current-password'"
              minlength="12"
              maxlength="128"
              required
            />
          </label>
          <p v-if="authError" class="auth-error" role="alert">
            <TriangleAlert :size="17" />
            {{ authError }}
          </p>
          <button type="submit" class="primary-action" :disabled="busy">
            <UserPlus v-if="authMode === 'register'" :size="18" />
            <LogIn v-else :size="18" />
            {{ authMode === 'register' ? '创建旅团账号' : '回到暖灯旅店' }}
          </button>
        </form>
      </div>
      <CompanionPortrait
        class="auth-companion"
        :companion="selectedCompanion"
        expression="welcome"
        :reduce-motion="reduceMotion"
      />
    </div>

    <InnLobby
      v-else-if="phase === 'lobby' && user"
      :companion="selectedCompanion"
      :user="user"
      :plan="plan"
      :lexicons-count="lexicons.length"
      :active-session="activeSession"
      :session-count="sessions.length"
      :companion-density="companionDensity"
      :reduce-motion="reduceMotion"
      :loading="busy"
      :error="lobbyError"
      @start="startTraining"
      @resume="enterSession"
      @help="toggleHelp"
      @select-companion="selectCompanion"
      @select-density="selectCompanionDensity"
      @classic-study="emit('classic-study')"
    />

    <GameTrainingStage
      v-else-if="session && phase !== 'result'"
      ref="trainingStage"
      :session="session"
      :item="currentItem"
      :phase="phase"
      :answer="answer"
      :pending-answer="pendingAnswer"
      :review-previews="reviewPreviews"
      :hint-text="currentHint"
      :hint-used="hintUsed"
      :answer-revealed="answerRevealed"
      :busy="busy"
      :error="sessionError"
      :combo="combo"
      :reviewed-count="reviewedCount"
      :practice-count="practiceCount"
      :focus-mode="focusMode"
      :reduce-motion="reduceMotion"
      :companion="selectedCompanion"
      :last-rating="lastRating"
      @update:answer="answer = $event"
      @submit="submitFormalAnswer"
      @hint="showNextHint"
      @reveal="revealAnswer"
      @skip="skipItem"
      @rate="rateReview"
      @pause="pauseTraining"
      @resume="resumeTraining"
      @exit="leaveTrainingToLobby"
      @help="toggleHelp"
      @retry-preview="retryReviewPreview"
      @reload-session="reloadSession"
      @toggle-focus="toggleFocus"
    />

    <GameResult
      v-else-if="phase === 'result'"
      :companion="selectedCompanion"
      :result="result"
      :reduce-motion="reduceMotion"
      :last-rating="lastRating"
      @again="trainAgain"
      @lobby="returnToLobby"
    />

    <button
      v-if="phase === 'lobby' || phase === 'result'"
      type="button"
      class="global-help"
      aria-label="打开快捷键帮助"
      @click="toggleHelp"
    >
      <CircleHelp :size="19" />
    </button>

    <GameShortcutHelp :open="helpOpen" @close="helpOpen = false" />
  </section>
</template>

<style scoped>
.game-workspace {
  position: relative;
  min-height: calc(100vh - 130px);
  margin: calc(clamp(20px, 3vw, 34px) * -1);
  padding: clamp(20px, 3vw, 34px);
  overflow: hidden;
  border: 1px solid rgb(217 183 121 / 22%);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgb(24 15 15 / 28%), rgb(20 12 14 / 66%)),
    var(--game-backdrop) center / cover no-repeat,
    #171014;
}

.game-workspace::before {
  position: absolute;
  inset: 0;
  z-index: 0;
  background:
    radial-gradient(circle at 21% 18%, rgb(235 200 142 / 8%), transparent 32%),
    linear-gradient(90deg, rgb(21 13 14 / 22%), transparent 38% 72%, rgb(21 13 14 / 18%));
  content: "";
  pointer-events: none;
}

.game-workspace > :not(.global-help) {
  position: relative;
  z-index: 1;
}

.focus-background::before {
  opacity: 0.32;
}

.game-loading {
  display: grid;
  min-height: 560px;
  place-content: center;
  justify-items: center;
  gap: 14px;
  color: #c6b49d;
}

.game-loading span {
  width: 42px;
  height: 42px;
  border: 2px solid rgb(219 183 122 / 18%);
  border-top-color: #d9b779;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.game-loading p {
  margin: 0;
  font-size: 0.8rem;
}

.auth-stage {
  position: relative;
  display: grid;
  grid-template-columns: minmax(330px, 1fr) minmax(300px, 360px) minmax(210px, 280px);
  gap: clamp(20px, 3vw, 42px);
  min-height: calc(100vh - 210px);
  align-items: center;
  max-width: 1180px;
  margin: 0 auto;
}

.auth-copy {
  max-width: 680px;
}

.eyebrow {
  margin: 0 0 13px;
  color: #d9b779;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.auth-copy h1 {
  margin: 0;
  color: #fff0d7;
  font-family: "Songti SC", "Noto Serif CJK SC", Georgia, serif;
  font-size: clamp(2.8rem, 5.4vw, 5rem);
  font-weight: 600;
  letter-spacing: 0;
  line-height: 1.05;
  text-wrap: balance;
}

.auth-copy > p:not(.eyebrow) {
  max-width: 56ch;
  margin: 20px 0 0;
  color: #d2c0a8;
  line-height: 1.85;
}

.auth-feature-list {
  display: grid;
  gap: 9px;
  margin-top: 24px;
}

.auth-feature-list div {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #d9c9b4;
  font-size: 0.8rem;
}

.auth-feature-list svg {
  color: #d9b779;
}

.auth-card {
  padding: 22px;
  border: 1px solid rgb(217 183 121 / 30%);
  border-radius: 8px;
  background: rgb(35 24 22 / 90%);
  backdrop-filter: blur(14px);
  box-shadow: 0 24px 70px rgb(24 12 12 / 38%);
}

.auth-companion {
  width: min(100%, 280px);
  height: calc(100vh - 220px);
  align-self: end;
  justify-self: center;
  opacity: 0.9;
}

.auth-tabs {
  display: flex;
  gap: 5px;
  margin-bottom: 18px;
}

.auth-tabs button {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #b5a087;
  cursor: pointer;
}

.auth-tabs button.selected {
  border-color: rgb(217 183 121 / 34%);
  background: rgb(217 183 121 / 10%);
  color: #ffebca;
}

.auth-card form {
  display: grid;
  gap: 13px;
}

.auth-card label {
  display: grid;
  gap: 6px;
  color: #cbb79c;
  font-size: 0.72rem;
  font-weight: 700;
}

.auth-card input {
  min-height: 46px;
  padding: 0 12px;
  border: 1px solid rgb(195 165 119 / 32%);
  border-radius: 6px;
  background: #fff3df;
  color: #2d211c;
}

.auth-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0;
  padding: 9px 10px;
  border-left: 3px solid #f07878;
  background: rgb(240 120 120 / 10%);
  color: #ffd6d6;
  font-size: 0.76rem;
}

.primary-action {
  display: inline-flex;
  min-height: 46px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 16px;
  border: 1px solid #d9b779;
  border-radius: 6px;
  background: #d9b779;
  color: #302017;
  font-weight: 800;
  cursor: pointer;
}

.primary-action:disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

.global-help {
  position: fixed;
  z-index: 15;
  right: 20px;
  bottom: 20px;
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  padding: 0;
  border: 1px solid rgb(222 192 145 / 30%);
  border-radius: 50%;
  background: rgb(34 23 22 / 84%);
  color: #e4d0b2;
  cursor: pointer;
  backdrop-filter: blur(8px);
}

button:focus-visible,
input:focus-visible {
  outline: 3px solid rgb(231 195 132 / 54%);
  outline-offset: 2px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 860px) {
  .auth-stage {
    grid-template-columns: 1fr;
    align-content: start;
    padding: 30px 0;
  }

  .auth-card {
    width: min(100%, 540px);
  }

  .auth-companion {
    position: absolute;
    right: -18px;
    bottom: -18px;
    display: block;
    width: 130px;
    height: 190px;
    opacity: 0.45;
    pointer-events: none;
  }

  .auth-copy,
  .auth-card {
    position: relative;
    z-index: 1;
  }
}

@media (max-width: 620px) {
  .game-workspace {
    margin: -18px;
    min-height: calc(100vh - 110px);
    padding: 18px;
    border-radius: 0;
  }

  .auth-copy h1 {
    font-size: 2.55rem;
  }

  .auth-companion {
    right: -24px;
    width: 104px;
    height: 152px;
    opacity: 0.32;
  }
}

@media (prefers-reduced-motion: reduce) {
  .game-loading span {
    animation: none;
  }
}
</style>
