<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue';
import {
  Eye,
  RotateCcw,
  SkipForward,
  Sparkles,
  Volume2,
  VolumeX,
} from '@lucide/vue';
import type {
  PracticeItem,
  ReviewRating,
} from '../core/domain';
import {
  resolveFlashcardShortcut,
  type FlashcardPhase,
  type FlashcardShortcutAction,
} from './shortcutStateMachine';

const props = withDefaults(
  defineProps<{
    item: PracticeItem;
    position: number;
    total: number;
    flipped: boolean;
    paused: boolean;
    busy: boolean;
    notRemembered: boolean;
    reduceMotion?: boolean;
    shortcutsEnabled?: boolean;
    audioControl?: boolean;
    audioMuted?: boolean;
    audioVolume?: number;
    audioStatus?: 'idle' | 'starting' | 'ready' | 'error';
    ratingPreviews?: Partial<Record<ReviewRating, string>>;
    ratingPreviewLoading?: boolean;
    ratingPreviewError?: string | null;
  }>(),
  {
    reduceMotion: false,
    shortcutsEnabled: true,
    audioControl: false,
    audioMuted: false,
    audioVolume: 1,
    audioStatus: 'idle',
    ratingPreviews: () => ({}),
    ratingPreviewLoading: false,
    ratingPreviewError: null,
  },
);

const emit = defineEmits<{
  flip: [];
  notRemembered: [];
  rate: [rating: ReviewRating];
  skip: [];
  toggleAudio: [];
  updateAudioVolume: [volume: number];
}>();

const card = ref<HTMLElement>();
const phase = computed<FlashcardPhase>(() => props.flipped ? 'rating' : 'front');
const canUseShortcuts = computed(
  () => props.shortcutsEnabled && !props.paused && !props.busy,
);
const ratings: ReadonlyArray<{
  value: ReviewRating;
  label: string;
  description: string;
  key: string;
}> = [
  {
    value: 'again',
    label: 'Again',
    description: '没想起来',
    key: '1',
  },
  {
    value: 'hard',
    label: 'Hard',
    description: '很吃力',
    key: '2',
  },
  {
    value: 'good',
    label: 'Good',
    description: '记得',
    key: '3',
  },
  {
    value: 'easy',
    label: 'Easy',
    description: '很熟练',
    key: '4',
  },
];

function isInteractiveTarget(target: EventTarget | null): boolean {
  return target instanceof HTMLElement
    && target.closest('button, a, input, textarea, select, [contenteditable="true"]') !== null;
}

function performShortcut(action: FlashcardShortcutAction): void {
  const ratingActions: Record<
    Extract<FlashcardShortcutAction, `rate-${ReviewRating}`>,
    ReviewRating
  > = {
    'rate-again': 'again',
    'rate-hard': 'hard',
    'rate-good': 'good',
    'rate-easy': 'easy',
  };
  if (action === 'flip') {
    emit('flip');
    return;
  }
  if (action === 'not-remembered') {
    emit('notRemembered');
    return;
  }
  emit('rate', ratingActions[action]);
}

function handleKeydown(event: KeyboardEvent): void {
  if (!canUseShortcuts.value) {
    return;
  }
  const action = resolveFlashcardShortcut({
    key: event.key,
    phase: phase.value,
    isComposing: event.isComposing,
    repeat: event.repeat,
    isInteractiveTarget: isInteractiveTarget(event.target),
    notRemembered: props.notRemembered,
    ctrlKey: event.ctrlKey,
    metaKey: event.metaKey,
    altKey: event.altKey,
    shiftKey: event.shiftKey,
  });
  if (action === null) {
    return;
  }
  event.preventDefault();
  performShortcut(action);
}

function focusCard(): void {
  void nextTick(() => {
    card.value?.focus();
  });
}

watch(
  () => [
    props.item.studyItem.id,
    props.flipped,
    props.paused,
    props.shortcutsEnabled,
    props.audioMuted,
  ] as const,
  focusCard,
);

onMounted(() => {
  window.addEventListener('keydown', handleKeydown);
  focusCard();
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown);
});
</script>

<template>
  <section
    ref="card"
    class="flashcard-panel"
    tabindex="0"
    aria-labelledby="flashcard-title"
    :aria-busy="busy"
  >
    <header class="flashcard-heading">
      <div>
        <p>经典闪卡</p>
        <h2 id="flashcard-title">
          {{ flipped ? '检查答案并自评' : '先在心里回忆' }}
        </h2>
      </div>
      <span>{{ position }} / {{ total }}</span>
    </header>

    <div
      class="flashcard-shell"
      :class="{ flipped, 'reduce-motion': reduceMotion }"
    >
      <article v-if="!flipped" class="flashcard-face front">
        <div class="face-copy">
          <span class="card-label">中文释义</span>
          <h3>{{ item.wordEntry.meaning }}</h3>
          <p>不要先看词面。想好英文后翻面检查，再按真实感受评分。</p>
        </div>
        <div class="front-actions">
          <button
            type="button"
            class="primary-action"
            :disabled="paused || busy"
            @click="emit('flip')"
          >
            <Eye :size="18" aria-hidden="true" />
            翻面看答案
            <kbd>Space</kbd>
          </button>
          <button
            type="button"
            class="not-remembered-action"
            :disabled="paused || busy"
            @click="emit('notRemembered')"
          >
            <RotateCcw :size="17" aria-hidden="true" />
            没想起来
            <kbd>N</kbd>
          </button>
        </div>
      </article>

      <article v-else class="flashcard-face back" aria-live="polite">
        <div class="answer-copy">
          <span class="card-label">英文拼写</span>
          <h3>{{ item.wordEntry.term }}</h3>
          <p class="answer-source">{{ item.wordEntry.meaning }}</p>
          <p v-if="notRemembered" class="again-note">
            你已经明确表示没想起。本次只适合记为 Again；之后会按调度重新遇到它。
          </p>
          <p v-else>按照刚才脑中真正发生的回忆情况，选择一项评分。</p>
        </div>

        <div class="rating-grid" aria-label="经典闪卡正式复习评分">
          <button
            v-for="rating in ratings"
            :key="rating.value"
            type="button"
            class="rating-button"
            :class="{ 'again-selected': notRemembered && rating.value === 'again' }"
            :disabled="
              paused ||
                busy ||
                (notRemembered && rating.value !== 'again') ||
                ratingPreviews[rating.value] === undefined
            "
            @click="emit('rate', rating.value)"
          >
            <kbd>{{ rating.key }}</kbd>
            <strong>{{ rating.label }}</strong>
            <span>{{ rating.description }}</span>
            <small>
              {{
                ratingPreviews[rating.value] ??
                  (ratingPreviewLoading ? '正在计算预计时间' : '预计时间不可用')
              }}
            </small>
          </button>
        </div>

        <p v-if="ratingPreviewError" class="preview-error" role="alert">
          {{ ratingPreviewError }}
        </p>
      </article>
    </div>

    <div class="flashcard-footer">
      <div class="footer-actions">
        <button
          type="button"
          class="quiet-action"
          :disabled="paused || busy"
          @click="emit('skip')"
        >
          <SkipForward :size="16" aria-hidden="true" />
          跳过，不算正式复习
        </button>
        <button
          v-if="audioControl"
          type="button"
          class="audio-toggle"
          :aria-label="audioMuted ? '开启音效' : '静音'"
          :aria-pressed="audioMuted"
          :title="audioMuted ? '开启音效' : '静音'"
          @click="emit('toggleAudio')"
        >
          <VolumeX v-if="audioMuted" :size="16" aria-hidden="true" />
          <Volume2 v-else :size="16" aria-hidden="true" />
        </button>
        <label v-if="audioControl" class="audio-volume">
          <span>音量</span>
          <input
            type="range"
            min="0"
            max="1"
            step="0.05"
            :value="audioVolume"
            aria-label="音效音量"
            @input="emit('updateAudioVolume', Number(($event.target as HTMLInputElement).value))"
          />
        </label>
        <span v-if="audioStatus === 'error'" class="audio-unavailable">
          音效不可用
        </span>
      </div>
      <slot name="audio-visual"></slot>
      <span v-if="paused">会话已暂停，恢复后可继续当前卡片。</span>
      <span v-else-if="busy">
        <Sparkles :size="15" aria-hidden="true" />
        正在保存...
      </span>
      <span v-else-if="!flipped">空格翻面 · N 记录没想起</span>
      <span v-else>1 Again · 2 Hard · 3 Good · 4 Easy</span>
    </div>
  </section>
</template>

<style scoped>
.flashcard-panel {
  display: grid;
  gap: 14px;
  outline: none;
}

.flashcard-panel:focus-visible .flashcard-shell {
  box-shadow:
    0 0 0 3px rgb(62 114 96 / 24%),
    0 18px 46px rgb(31 70 53 / 11%);
}

.flashcard-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.flashcard-heading p {
  margin: 0 0 5px;
  color: #718078;
  font-size: 0.7rem;
  font-weight: 700;
}

.flashcard-heading h2 {
  margin: 0;
  color: #1e3328;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 1.65rem;
  font-weight: 500;
}

.flashcard-heading > span {
  color: #68766e;
  font-size: 0.8rem;
}

.flashcard-shell {
  min-height: 430px;
  border: 1px solid #cbd8d0;
  border-radius: 8px;
  background:
    linear-gradient(145deg, rgb(255 255 255 / 96%), rgb(246 250 247 / 96%)),
    #fff;
  box-shadow: 0 18px 46px rgb(31 70 53 / 9%);
  overflow: hidden;
}

.flashcard-face {
  display: grid;
  min-height: 430px;
  padding: clamp(24px, 4vw, 46px);
  align-content: space-between;
  gap: 26px;
  animation: card-flip-in 260ms cubic-bezier(0.22, 0.72, 0.18, 1);
}

.flashcard-face.back {
  align-content: start;
}

.card-label {
  color: #718078;
  font-size: 0.7rem;
  font-weight: 700;
}

.face-copy h3,
.answer-copy h3 {
  margin: 14px 0;
  color: #1c3227;
  font-family: Georgia, "Times New Roman", serif;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.face-copy h3 {
  max-width: 18ch;
  font-size: clamp(2.2rem, 6vw, 4.5rem);
  line-height: 1.08;
}

.answer-copy h3 {
  font-size: clamp(2.4rem, 7vw, 5rem);
  line-height: 1.04;
}

.face-copy p,
.answer-copy p {
  max-width: 60ch;
  margin: 0;
  color: #65766d;
  line-height: 1.75;
}

.answer-source {
  font-size: 1rem;
}

.again-note {
  margin-top: 14px !important;
  padding-left: 12px;
  border-left: 3px solid #b8763d;
  color: #7f542f !important;
}

.front-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.primary-action,
.not-remembered-action,
.quiet-action,
.audio-toggle {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 6px;
  font-weight: 700;
  cursor: pointer;
}

.audio-volume {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #718078;
  font-size: 0.68rem;
}

.audio-volume input {
  width: 68px;
  min-height: 24px;
  padding: 0;
  accent-color: #3c8061;
}

.primary-action {
  padding: 0 16px;
  border: 1px solid #315e4d;
  background: #315e4d;
  color: #fff;
}

.not-remembered-action {
  padding: 0 14px;
  border: 1px solid #d7b28e;
  background: #fff8ef;
  color: #7c4d28;
}

.quiet-action {
  padding: 0 10px;
  border: 1px solid transparent;
  background: transparent;
  color: #74827a;
  font-size: 0.74rem;
}

.audio-toggle {
  width: 32px;
  min-height: 32px;
  padding: 0;
  border: 1px solid #cbd8d0;
  background: #fff;
  color: #557165;
}

.primary-action:disabled,
.not-remembered-action:disabled,
.quiet-action:disabled,
.audio-toggle:disabled {
  cursor: not-allowed;
  opacity: 0.48;
}

kbd {
  padding: 2px 6px;
  border: 1px solid currentcolor;
  border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.65rem;
  font-weight: 600;
  opacity: 0.72;
}

.rating-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-top: 24px;
}

.rating-button {
  display: grid;
  min-height: 116px;
  padding: 14px 10px;
  align-content: start;
  justify-items: start;
  gap: 5px;
  border: 1px solid #cfd9d2;
  border-radius: 7px;
  background: #fff;
  color: #263a2f;
  text-align: left;
  cursor: pointer;
}

.rating-button:hover:not(:disabled) {
  border-color: #6c9883;
  background: #f8fbf9;
}

.rating-button:disabled {
  cursor: not-allowed;
  opacity: 0.44;
}

.rating-button.again-selected {
  border-color: #b8763d;
  background: #fff8ef;
  opacity: 1;
}

.rating-button strong {
  font-size: 0.9rem;
}

.rating-button span,
.rating-button small {
  color: #718078;
  font-size: 0.7rem;
  line-height: 1.45;
}

.preview-error {
  margin-top: 12px !important;
  color: #9d3e37 !important;
}

.flashcard-footer {
  display: flex;
  min-height: 34px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: #74827a;
  font-size: 0.72rem;
}

.flashcard-footer span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.footer-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.audio-unavailable {
  color: #9d3e37;
  font-size: 0.68rem;
}

@keyframes card-flip-in {
  from {
    opacity: 0;
    transform: perspective(900px) rotateY(-12deg) translateX(8px);
  }

  to {
    opacity: 1;
    transform: perspective(900px) rotateY(0) translateX(0);
  }
}

@media (max-width: 760px) {
  .flashcard-shell,
  .flashcard-face {
    min-height: 500px;
  }

  .rating-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .flashcard-footer {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {
  .flashcard-face {
    animation: none;
  }
}

.reduce-motion .flashcard-face {
  animation: none;
}
</style>
