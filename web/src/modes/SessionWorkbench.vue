<script setup lang="ts">
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  shallowRef,
  toRef,
  watch,
} from 'vue';
import type { Component } from 'vue';
import type {
  PendingAnswer,
  PracticeInteractionState,
  PracticeItem,
  PracticeModeId,
  PracticeSession,
  ReviewRating,
  ReviewState,
} from '../core/domain';
import {
  assessmentTypeForPractice,
  isAssessmentFormalEligible,
} from '../core/assessmentPolicy';
import type {
  FingerMemoryRepository,
  ReviewRatingPreviews,
} from '../core/repository';
import { formatDueLabel } from '../core/due';
import FlashcardPanel from '../flashcards/FlashcardPanel.vue';
import PhaserGameHost from '../game-runtime/PhaserGameHost.vue';
import { usePhaserAudio } from '../game-runtime/usePhaserAudio';
import {
  abandonPracticeSession,
  completeCurrentSessionItem,
  pausePracticeSession,
  resumePracticeSession,
  revealSessionAnswer,
  revealSessionHint,
  selectSessionItem,
  setSessionReduceMotion,
  setSessionSpeed,
  switchSessionMode,
  updateSessionDraft,
} from '../session/practiceSession';
import AnswerPanel from './AnswerPanel.vue';
import {
  TRUSTED_MODE_PLUGINS,
  createTrustedModeRegistry,
} from './registry';
import type { PracticeModeEvent } from './types';

const props = withDefaults(
  defineProps<{
    initialSession: PracticeSession;
    items: PracticeItem[];
    repository: FingerMemoryRepository;
    enabledModeIds?: PracticeModeId[];
    enableAudio?: boolean;
    clock?: () => number;
    idFactory?: (prefix: string) => string;
  }>(),
  {
    clock: () => Date.now(),
    idFactory: (prefix: string) => `${prefix}:${crypto.randomUUID()}`,
    enabledModeIds: () => TRUSTED_MODE_PLUGINS.map((plugin) => plugin.manifest.id),
    enableAudio: false,
  },
);

const emit = defineEmits<{
  updated: [session: PracticeSession];
  exit: [session: PracticeSession];
  newSession: [];
}>();

const session = ref<PracticeSession>({ ...props.initialSession });
const modeRegistry = createTrustedModeRegistry();
const activeModeComponent = shallowRef<Component>();
const modeEventListeners = new Set<(event: PracticeModeEvent) => void>();
const busy = ref(false);
const error = ref<string | null>(null);
const confirmAbandon = ref(false);
const ratingPreviews = ref<ReviewRatingPreviews>();
const ratingPreviewLoading = ref(false);
const ratingPreviewError = ref<string | null>(null);
const reviewStatesByStudyItemId = ref<Record<string, ReviewState>>({});
const reviewStateLoading = ref(false);
const flashcardNotRemembered = ref(false);
const {
  setHost: setAudioHost,
  muted: audioMuted,
  volume: audioVolume,
  status: audioStatus,
  toggleMuted: toggleAudioMuted,
  setVolume: setAudioVolume,
  playFlip: playFlipAudio,
  handleRuntimeEvent: handleAudioRuntimeEvent,
} = usePhaserAudio(toRef(props, 'enableAudio'));
let ratingPreviewRequest = 0;
let reviewStateRequest = 0;
const itemById = computed(
  () => new Map(props.items.map((item) => [item.studyItem.id, item])),
);
const orderedItems = computed(() =>
  session.value.queue
    .map((item) => itemById.value.get(item.studyItemId))
    .filter((item): item is PracticeItem => item !== undefined),
);
const isFlashcardMode = computed(() => session.value.modeId === 'flashcard');
const activeMode = computed(() =>
  isFlashcardMode.value
    ? { label: '经典闪卡' }
    : modeRegistry.get(session.value.modeId)?.manifest
      ?? TRUSTED_MODE_PLUGINS[0]!.manifest,
);
const availableModes = computed(() => {
  if (isFlashcardMode.value) {
    return [];
  }
  const enabled = new Set(props.enabledModeIds);
  return TRUSTED_MODE_PLUGINS.filter(
    (plugin) =>
      enabled.has(plugin.manifest.id) ||
      plugin.manifest.id === session.value.modeId,
  );
});
const currentItem = computed(() => orderedItems.value[session.value.currentIndex]);
const currentInteraction = computed<PracticeInteractionState>(() => {
  const studyItemId = session.value.currentStudyItemId;
  return (
    (studyItemId === null ? undefined : session.value.interactions[studyItemId]) ?? {
      draft: '',
      revealedHintCount: 0,
      answerRevealed: false,
    }
  );
});
const isPaused = computed(() => session.value.status === 'paused');
const ratingPreviewLabels = computed<Partial<Record<ReviewRating, string>>>(() => {
  const previews = ratingPreviews.value;
  if (previews === undefined) {
    return {};
  }
  const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  return Object.fromEntries(
    Object.values(previews).map((preview) => [
      preview.rating,
      formatDueLabel(preview.dueAt, now(), timeZone),
    ]),
  );
});
const currentReviewState = computed(() => {
  const studyItemId = session.value.currentStudyItemId;
  return studyItemId === null ? undefined : reviewStatesByStudyItemId.value[studyItemId];
});
const sessionReviewStatuses = computed(() => {
  const seen = new Set<string>();
  return orderedItems.value.flatMap((item) => {
    if (seen.has(item.studyItem.id)) {
      return [];
    }
    seen.add(item.studyItem.id);
    return [{
      item,
      state: reviewStatesByStudyItemId.value[item.studyItem.id],
    }];
  });
});
const completedCount = computed(
  () => session.value.queue.filter((item) => item.status !== 'pending').length,
);
const allowRetry = computed(() => {
  const item = currentItem.value;
  const pendingAnswer = session.value.pendingAnswer;
  if (item === undefined || pendingAnswer === null || pendingAnswer.inputMatchedAnswer) {
    return false;
  }
  return (
    session.value.queue.filter(
      (queueItem) => queueItem.studyItemId === item.studyItem.id,
    ).length < 2
  );
});

function now(): number {
  return props.clock();
}

function createId(prefix: string): string {
  return props.idFactory(prefix);
}

function setSession(nextSession: PracticeSession): void {
  session.value = nextSession;
  emit('updated', nextSession);
}

async function persist(nextSession: PracticeSession): Promise<boolean> {
  setSession(nextSession);
  try {
    const saved = await props.repository.savePracticeSession(nextSession);
    setSession(saved);
    error.value = null;
    return true;
  } catch {
    error.value = '会话未能保存，请重试。';
    return false;
  }
}

async function changeMode(modeId: PracticeModeId): Promise<void> {
  if (await persist(switchSessionMode(session.value, modeId, now()))) {
    activateMode(modeId);
  }
}

async function togglePause(): Promise<void> {
  const next =
    session.value.status === 'paused'
      ? resumePracticeSession(session.value, now())
      : pausePracticeSession(session.value, now());
  await persist(next);
}

async function changeSpeed(event: Event): Promise<void> {
  const speed = Number((event.target as HTMLInputElement).value);
  await persist(setSessionSpeed(session.value, speed, now()));
}

async function changeReduceMotion(event: Event): Promise<void> {
  const reduceMotion = (event.target as HTMLInputElement).checked;
  await persist(setSessionReduceMotion(session.value, reduceMotion, now()));
}

function updateDraft(draft: string): void {
  setSession(updateSessionDraft(session.value, draft, now()));
}

async function selectItem(index: number): Promise<void> {
  if (isPaused.value || session.value.pendingAnswer !== null) {
    return;
  }
  const selected = selectSessionItem(session.value, index, now());
  if (selected !== session.value && (await persist(selected))) {
    emitModeEvent({
      type: 'target-selected',
      sessionId: session.value.id,
      studyItemId: selected.currentStudyItemId ?? '',
      occurredAt: selected.updatedAt,
    });
  }
}

async function revealHint(): Promise<void> {
  if (currentItem.value === undefined) {
    return;
  }
  await persist(
    revealSessionHint(session.value, currentItem.value.wordEntry.hints.length, now()),
  );
}

async function revealAnswer(): Promise<void> {
  await persist(revealSessionAnswer(session.value, now()));
}

async function revealFlashcard(notRemembered = false): Promise<void> {
  const item = currentItem.value;
  if (
    !isFlashcardMode.value ||
    item === undefined ||
    isPaused.value ||
    session.value.pendingAnswer !== null
  ) {
    return;
  }
  const revealedAt = now();
  const revealed = revealSessionAnswer(session.value, revealedAt);
  playFlipAudio(
    `flashcard:${session.value.id}:${item.studyItem.id}:${revealedAt}`,
  );
  flashcardNotRemembered.value = notRemembered;
  await persist({
    ...revealed,
    pendingAnswer: {
      studyItemId: item.studyItem.id,
      response: '',
      answerWasHidden: true,
      hintUsed: false,
      answerRevealed: true,
      inputMatchedAnswer: false,
      assessmentType: 'flashcard-self-report',
      assessmentPolicyVersion: 2,
      assessmentStage: 'reveal-for-check',
      priorExposure: false,
      explicitNotRemembered: notRemembered,
      practiceAttemptId: createId('flashcard-practice'),
      reviewSubmissionId: createId('review'),
      submittedAt: revealedAt,
    },
    updatedAt: revealedAt,
  });
}

function normalizeAnswer(value: string): string {
  return value.trim().normalize('NFKC').toLocaleLowerCase('en-US');
}

function answerMatches(item: PracticeItem, response: string): boolean {
  const normalized = normalizeAnswer(response);
  return (
    normalized.length > 0 &&
    [item.wordEntry.term, ...item.wordEntry.acceptedAnswers].some(
      (answer) => normalizeAnswer(answer) === normalized,
    )
  );
}

async function submitAnswer(): Promise<void> {
  if (
    currentItem.value === undefined ||
    isPaused.value ||
    session.value.pendingAnswer !== null
  ) {
    return;
  }

  const interaction = currentInteraction.value;
  const hintUsed = interaction.revealedHintCount > 0;
  const pendingAnswer: PendingAnswer = {
    studyItemId: currentItem.value.studyItem.id,
    response: interaction.draft,
    answerWasHidden: !interaction.answerRevealed,
    hintUsed,
    answerRevealed: interaction.answerRevealed,
    inputMatchedAnswer: answerMatches(currentItem.value, interaction.draft),
    assessmentType: assessmentTypeForPractice({
      answerRevealed: interaction.answerRevealed,
      hintUsed,
    }),
    assessmentPolicyVersion: 2,
    assessmentStage: interaction.answerRevealed
      ? 'reveal-for-check'
      : 'attempt',
    priorExposure: hintUsed || interaction.answerRevealed,
    explicitNotRemembered: false,
    practiceAttemptId: createId('practice'),
    reviewSubmissionId: createId('review'),
    submittedAt: now(),
  };
  busy.value = true;
  await persist({ ...session.value, pendingAnswer, updatedAt: now() });
  emitModeEvent({
    type: 'answer-submitted',
    sessionId: session.value.id,
    studyItemId: pendingAnswer.studyItemId,
    occurredAt: pendingAnswer.submittedAt,
  });
  busy.value = false;
}

function subscribeModeEvents(listener: (event: PracticeModeEvent) => void): () => void {
  modeEventListeners.add(listener);
  return () => {
    modeEventListeners.delete(listener);
  };
}

function emitModeEvent(event: PracticeModeEvent): void {
  for (const listener of modeEventListeners) {
    listener(event);
  }
}

function currentPluginContext() {
  return {
    sessionId: session.value.id,
    getSession: () => session.value,
    getItems: () => orderedItems.value,
    subscribe: subscribeModeEvents,
    commands: {
      selectTarget: selectItem,
      recordPractice: (input: Parameters<FingerMemoryRepository['recordPracticeAttempt']>[0]) =>
        props.repository.recordPracticeAttempt(input),
      submitReview: (input: Parameters<FingerMemoryRepository['submitReview']>[0]) =>
        props.repository.submitReview(input),
    },
  };
}

function activateMode(modeId: PracticeModeId): void {
  if (modeId === 'flashcard') {
    modeRegistry.deactivate();
    activeModeComponent.value = undefined;
    return;
  }
  const instance = modeRegistry.activate(modeId, currentPluginContext());
  activeModeComponent.value = instance.component;
  emitModeEvent({
    type: 'session-started',
    sessionId: session.value.id,
    occurredAt: now(),
  });
}

async function loadRatingPreviews(pendingAnswer: PendingAnswer): Promise<void> {
  const request = ++ratingPreviewRequest;
  ratingPreviews.value = undefined;
  ratingPreviewError.value = null;
  if (
    !isAssessmentFormalEligible({
      ...pendingAnswer,
      userConfirmedRating: true,
      abandoned: false,
    })
  ) {
    ratingPreviewLoading.value = false;
    return;
  }

  ratingPreviewLoading.value = true;
  try {
    const previews = await props.repository.previewReviewIntervals({
      studyItemId: pendingAnswer.studyItemId,
      reviewedAt: pendingAnswer.submittedAt,
    });
    if (request === ratingPreviewRequest) {
      ratingPreviews.value = previews;
    }
  } catch {
    if (request === ratingPreviewRequest) {
      ratingPreviewError.value = '预计间隔暂不可用，评分未提交。';
    }
  } finally {
    if (request === ratingPreviewRequest) {
      ratingPreviewLoading.value = false;
    }
  }
}

async function loadReviewStates(): Promise<void> {
  const request = ++reviewStateRequest;
  const ids = orderedItems.value.map((item) => item.studyItem.id);
  if (ids.length === 0) {
    reviewStatesByStudyItemId.value = {};
    return;
  }
  reviewStateLoading.value = true;
  try {
    const states = await props.repository.listReviewStates(ids);
    if (request === reviewStateRequest) {
      reviewStatesByStudyItemId.value = Object.fromEntries(
        states.map((state) => [state.studyItemId, state]),
      );
    }
  } catch {
    if (request === reviewStateRequest) {
      error.value = '正式复习状态暂时无法读取。';
    }
  } finally {
    if (request === reviewStateRequest) {
      reviewStateLoading.value = false;
    }
  }
}

function reviewStateLabel(state: ReviewState | undefined): string {
  if (state === undefined) {
    return '尚未正式复习';
  }
  const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  return `${formatDueLabel(state.dueAt, now(), timeZone)} · ${state.scheduleStatus} · ${state.reps} 次 · ${state.schedulerModelVersion.toUpperCase()}`;
}

async function completeItem(
  status: 'completed' | 'skipped',
  requeueCurrent = false,
): Promise<void> {
  const next = completeCurrentSessionItem(session.value, status, now(), {
    requeueCurrent,
  });
  await persist(next);
}

async function chooseRating(rating: ReviewRating): Promise<void> {
  const pendingAnswer = session.value.pendingAnswer;
  if (pendingAnswer === null || ratingPreviewLabels.value[rating] === undefined) {
    return;
  }

  busy.value = true;
  error.value = null;
  try {
    const result = await props.repository.submitReview({
      submissionId: pendingAnswer.reviewSubmissionId,
      studyItemId: pendingAnswer.studyItemId,
      modeId: session.value.modeId,
      sessionId: session.value.id,
      rating,
      response: pendingAnswer.response,
      answerWasHidden: pendingAnswer.answerWasHidden,
      hintUsed: pendingAnswer.hintUsed,
      answerRevealed: pendingAnswer.answerRevealed,
      assessmentType: pendingAnswer.assessmentType,
      assessmentPolicyVersion: pendingAnswer.assessmentPolicyVersion,
      assessmentStage: pendingAnswer.assessmentStage,
      priorExposure: pendingAnswer.priorExposure,
      explicitNotRemembered: pendingAnswer.explicitNotRemembered,
      abandoned: false,
      userConfirmedRating: true,
      reviewedAt: pendingAnswer.submittedAt,
    });
    reviewStatesByStudyItemId.value = {
      ...reviewStatesByStudyItemId.value,
      [result.state.studyItemId]: result.state,
    };
    emitModeEvent({
      type: 'review-submitted',
      sessionId: session.value.id,
      studyItemId: pendingAnswer.studyItemId,
      occurredAt: pendingAnswer.submittedAt,
    });
    await completeItem('completed');
  } catch {
    error.value = '正式复习未保存，当前答案仍保留，可重试。';
  } finally {
    busy.value = false;
  }
}

async function chooseFlashcardRating(rating: ReviewRating): Promise<void> {
  if (
    !isFlashcardMode.value ||
    (flashcardNotRemembered.value && rating !== 'again')
  ) {
    return;
  }
  await chooseRating(rating);
}

async function returnToEditing(): Promise<void> {
  if (session.value.pendingAnswer === null) {
    return;
  }
  await persist({
    ...session.value,
    pendingAnswer: null,
    updatedAt: now(),
  });
}

async function recordPractice(requeueCurrent = false): Promise<void> {
  const pendingAnswer = session.value.pendingAnswer;
  if (pendingAnswer === null) {
    return;
  }

  busy.value = true;
  error.value = null;
  try {
    await props.repository.recordPracticeAttempt({
      id: pendingAnswer.practiceAttemptId,
      studyItemId: pendingAnswer.studyItemId,
      modeId: session.value.modeId,
      sessionId: session.value.id,
      response: pendingAnswer.response,
      answerWasHidden: pendingAnswer.answerWasHidden,
      hintUsed: pendingAnswer.hintUsed,
      answerRevealed: pendingAnswer.answerRevealed,
      assessmentType: pendingAnswer.assessmentType,
      assessmentPolicyVersion: pendingAnswer.assessmentPolicyVersion,
      assessmentStage: pendingAnswer.assessmentStage,
      priorExposure: pendingAnswer.priorExposure,
      explicitNotRemembered: pendingAnswer.explicitNotRemembered,
      abandoned: false,
      occurredAt: pendingAnswer.submittedAt,
    });
    emitModeEvent({
      type: 'practice-recorded',
      sessionId: session.value.id,
      studyItemId: pendingAnswer.studyItemId,
      occurredAt: pendingAnswer.submittedAt,
    });
    await completeItem('completed', requeueCurrent);
  } catch {
    error.value = '练习记录未保存，请重试。';
  } finally {
    busy.value = false;
  }
}

async function retryPractice(): Promise<void> {
  await recordPractice(true);
}

async function skipCurrent(): Promise<void> {
  if (currentItem.value === undefined || isPaused.value) {
    return;
  }

  busy.value = true;
  error.value = null;
  try {
    const interaction = currentInteraction.value;
    await props.repository.recordPracticeAttempt({
      id: createId('skip'),
      studyItemId: currentItem.value.studyItem.id,
      modeId: session.value.modeId,
      sessionId: session.value.id,
      response: interaction.draft,
      answerWasHidden: !interaction.answerRevealed,
      hintUsed: interaction.revealedHintCount > 0,
      answerRevealed: interaction.answerRevealed,
      assessmentType: 'guided-practice',
      assessmentPolicyVersion: 2,
      assessmentStage: 'attempt',
      priorExposure: true,
      explicitNotRemembered: false,
      abandoned: true,
      occurredAt: now(),
    });
    emitModeEvent({
      type: 'practice-recorded',
      sessionId: session.value.id,
      studyItemId: currentItem.value.studyItem.id,
      occurredAt: now(),
    });
    await completeItem('skipped');
  } catch {
    error.value = '跳过记录未保存，当前题目未切换。';
  } finally {
    busy.value = false;
  }
}

async function exitSession(): Promise<void> {
  const paused = pausePracticeSession(session.value, now());
  if (await persist(paused)) {
    emit('exit', session.value);
  }
}

async function abandonSession(): Promise<void> {
  busy.value = true;
  try {
    const abandoned = abandonPracticeSession(session.value, now());
    if (await persist(abandoned)) {
      emit('exit', session.value);
    }
  } finally {
    busy.value = false;
  }
}

function handleVisibilityChange(): void {
  if (document.visibilityState === 'hidden' && session.value.status === 'active') {
    void persist(pausePracticeSession(session.value, now()));
  }
}

onMounted(() => {
  document.addEventListener('visibilitychange', handleVisibilityChange);
  activateMode(session.value.modeId);
  void loadReviewStates();
});

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange);
  emitModeEvent({
    type: 'session-stopped',
    sessionId: session.value.id,
    occurredAt: now(),
  });
  modeRegistry.deactivate();
  if (session.value.status === 'active') {
    void props.repository
      .savePracticeSession(pausePracticeSession(session.value, now()))
      .catch(() => undefined);
  }
});

watch(
  () => session.value.pendingAnswer,
  (pendingAnswer) => {
    if (pendingAnswer === null) {
      flashcardNotRemembered.value = false;
      ratingPreviewRequest += 1;
      ratingPreviews.value = undefined;
      ratingPreviewError.value = null;
      ratingPreviewLoading.value = false;
      return;
    }
    if (isFlashcardMode.value) {
      flashcardNotRemembered.value =
        pendingAnswer.explicitNotRemembered === true;
    }
    void loadRatingPreviews(pendingAnswer);
  },
  { immediate: true },
);

watch(
  () => session.value.queue.length,
  () => {
    void loadReviewStates();
  },
);

watch(
  () => session.value.currentStudyItemId,
  () => {
    flashcardNotRemembered.value = false;
  },
);
</script>

<template>
  <section class="workbench" aria-labelledby="session-title">
    <header class="session-header">
      <div>
        <p class="session-kicker">练习会话</p>
        <h2 id="session-title">{{ activeMode.label }}</h2>
        <p class="session-progress">
          已完成 {{ completedCount }} / {{ session.queue.length }}
        </p>
      </div>
      <div class="session-actions">
        <button type="button" :disabled="busy" @click="togglePause">
          {{ isPaused ? '恢复' : '暂停' }}
        </button>
        <button type="button" :disabled="busy" @click="exitSession">退出模式</button>
        <button type="button" class="danger-action" :disabled="busy" @click="confirmAbandon = true">
          放弃会话
        </button>
      </div>
    </header>

    <div v-if="confirmAbandon" class="abandon-confirm" role="alert">
      <p>放弃后会结束当前会话并保留已产生的练习事实；之后需要重新开始会话。</p>
      <div>
        <button type="button" class="danger-action" :disabled="busy" @click="abandonSession">
          确认放弃
        </button>
        <button type="button" :disabled="busy" @click="confirmAbandon = false">取消</button>
      </div>
    </div>

    <div v-if="!isFlashcardMode" class="mode-toolbar">
      <div class="mode-switch" role="group" aria-label="练习模式">
        <button
          v-for="mode in availableModes"
          :key="mode.manifest.id"
          type="button"
          :class="{ selected: mode.manifest.id === session.modeId }"
          :aria-pressed="mode.manifest.id === session.modeId"
          :disabled="busy"
          @click="changeMode(mode.manifest.id)"
        >
          {{ mode.manifest.label }}
        </button>
      </div>

      <label class="speed-control">
        <span>滚动速度</span>
        <input
          type="range"
          min="0.5"
          max="2"
          step="0.1"
          :value="session.speed"
          :disabled="busy"
          @change="changeSpeed"
        />
        <output>{{ session.speed.toFixed(1) }}×</output>
      </label>

      <label class="motion-control">
        <input
          type="checkbox"
          :checked="session.reduceMotion"
          :disabled="busy"
          @change="changeReduceMotion"
        />
        减少动画
      </label>
    </div>

    <section class="session-review-status" aria-labelledby="session-review-status-title">
      <div>
        <p id="session-review-status-title">正式复习状态</p>
        <strong>{{ currentItem?.wordEntry.meaning ?? '当前目标' }}</strong>
        <span>{{ reviewStateLoading ? '读取中...' : reviewStateLabel(currentReviewState) }}</span>
      </div>
      <details>
        <summary>本次会话全部目标</summary>
        <ul>
          <li v-for="entry in sessionReviewStatuses" :key="entry.item.studyItem.id">
            <span>{{ entry.item.wordEntry.meaning }}</span>
            <strong>{{ reviewStateLabel(entry.state) }}</strong>
          </li>
        </ul>
      </details>
    </section>

    <div v-if="session.status === 'completed'" class="complete-panel" role="status">
      <p class="session-kicker">本轮结束</p>
      <h3>这一轮已经完成</h3>
      <p>已处理 {{ session.queue.length }} 个目标。练习和正式复习仍保存在原来的学习目标下。</p>
      <button type="button" class="primary-action" @click="emit('newSession')">开始新一轮</button>
    </div>

    <template v-else-if="currentItem !== undefined">
      <FlashcardPanel
        v-if="isFlashcardMode"
        :item="currentItem"
        :position="session.currentIndex + 1"
        :total="session.queue.length"
        :flipped="session.pendingAnswer !== null"
        :paused="isPaused"
        :busy="busy"
        :not-remembered="flashcardNotRemembered"
        :reduce-motion="session.reduceMotion"
        :shortcuts-enabled="!confirmAbandon"
        :audio-control="enableAudio"
        :audio-muted="audioMuted"
        :audio-volume="audioVolume"
        :audio-status="audioStatus"
        :rating-previews="ratingPreviewLabels"
        :rating-preview-loading="ratingPreviewLoading"
        :rating-preview-error="ratingPreviewError"
        @flip="revealFlashcard(false)"
        @not-remembered="revealFlashcard(true)"
        @rate="chooseFlashcardRating"
        @skip="skipCurrent"
        @toggle-audio="toggleAudioMuted"
        @update-audio-volume="setAudioVolume"
      >
        <template #audio-visual>
          <PhaserGameHost
            v-if="enableAudio"
            :ref="setAudioHost"
            :muted="audioMuted"
            @event="handleAudioRuntimeEvent"
          />
        </template>
      </FlashcardPanel>
      <template v-else>
        <component
          :is="activeModeComponent"
          :items="orderedItems"
          :queue="session.queue"
          :current-index="session.currentIndex"
          :paused="isPaused"
          :reduce-motion="session.reduceMotion"
          :speed="session.speed"
          @select="selectItem"
        />
        <AnswerPanel
          :current-item="currentItem"
          :interaction="currentInteraction"
          :pending-answer="session.pendingAnswer"
          :paused="isPaused"
          :busy="busy"
          :error="error"
          :allow-retry="allowRetry"
          :rating-previews="ratingPreviewLabels"
          :rating-preview-loading="ratingPreviewLoading"
          :rating-preview-error="ratingPreviewError"
          @draft="updateDraft"
          @hint="revealHint"
          @reveal="revealAnswer"
          @submit="submitAnswer"
          @skip="skipCurrent"
          @rate="chooseRating"
          @cancel-rating="returnToEditing"
          @practice="recordPractice"
          @retry="retryPractice"
        />
      </template>
    </template>
  </section>
</template>

<style scoped>
.workbench {
  min-width: 0;
}

.session-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 16px;
}

.session-kicker {
  margin: 0 0 5px;
  color: #718078;
  font-size: 0.7rem;
  font-weight: 700;
}

.session-header h2 {
  margin: 0;
  color: #1e3328;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 2rem;
  font-weight: 500;
}

.session-progress {
  margin: 8px 0 0;
  color: #74827a;
  font-size: 0.78rem;
}

.session-actions,
.mode-toolbar,
.mode-switch {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.mode-toolbar {
  justify-content: space-between;
  padding: 10px;
  border: 1px solid #d3dcd6;
  border-radius: 8px;
  background: #f8faf9;
}

.mode-switch {
  gap: 4px;
}

button {
  min-height: 36px;
  padding: 7px 11px;
  border: 1px solid #c5d0c9;
  border-radius: 6px;
  background: #fff;
  color: #3b5045;
  font-size: 0.78rem;
  font-weight: 650;
  cursor: pointer;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

button.selected {
  border-color: #2e604b;
  background: #e6efe9;
  color: #18372a;
}

.danger-action {
  color: #9b4935;
  border-color: #dfc2b9;
}

button:focus-visible,
input:focus-visible {
  outline: 3px solid rgb(45 113 83 / 28%);
  outline-offset: 2px;
}

.speed-control,
.motion-control {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #637269;
  font-size: 0.75rem;
}

.speed-control input {
  width: 100px;
}

.speed-control output {
  min-width: 34px;
}

.motion-control input {
  width: 16px;
  height: 16px;
}

.complete-panel {
  padding: clamp(24px, 5vw, 48px);
  border: 1px solid #cbd5cf;
  border-radius: 8px;
  background: #fff;
}

.session-review-status {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px 24px;
  align-items: start;
  margin-top: 12px;
  padding: 12px 14px;
  border: 1px solid #d2dcd5;
  border-radius: 8px;
  background: #fbfcfb;
}

.session-review-status p {
  margin: 0 0 3px;
  color: #738078;
  font-size: 0.7rem;
  font-weight: 700;
}

.session-review-status strong {
  display: block;
  color: #284236;
  font-size: 0.82rem;
}

.session-review-status span {
  color: #66766d;
  font-size: 0.75rem;
}

.session-review-status details {
  color: #576a5f;
  font-size: 0.75rem;
}

.session-review-status summary {
  color: #3f6452;
  font-weight: 700;
  cursor: pointer;
}

.session-review-status ul {
  display: grid;
  gap: 6px;
  margin: 9px 0 0;
  padding: 0;
  list-style: none;
}

.session-review-status li {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  min-width: 260px;
}

.session-review-status li strong {
  font-size: 0.73rem;
  font-weight: 600;
}

.abandon-confirm {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding: 12px 14px;
  border: 1px solid #d9b6aa;
  border-radius: 6px;
  background: #fbf3f0;
}

.abandon-confirm p {
  margin: 0;
  color: #7f4e40;
  font-size: 0.8rem;
  line-height: 1.55;
}

.abandon-confirm > div {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.complete-panel h3 {
  margin: 0 0 8px;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 2rem;
  font-weight: 500;
}

.complete-panel p:not(.session-kicker) {
  max-width: 50ch;
  color: #68776f;
  line-height: 1.7;
}

.primary-action {
  border-color: #244f3d;
  background: #244f3d;
  color: #fff;
}

@media (max-width: 720px) {
  .session-header,
  .mode-toolbar,
  .abandon-confirm,
  .session-review-status {
    align-items: flex-start;
    flex-direction: column;
  }

  .session-review-status li {
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  .mode-toolbar {
    gap: 13px;
  }

  .session-actions,
  .mode-switch {
    width: 100%;
  }

  .session-actions button,
  .mode-switch button {
    flex: 1;
  }
}
</style>
