<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import {
  CircleHelp,
  ChevronLeft,
  CornerDownLeft,
  Eye,
  Focus,
  Lightbulb,
  Pause,
  Play,
  SkipForward,
  Sparkles,
  Target,
  X,
} from '@lucide/vue';
import type {
  PendingAnswerView,
  ReviewPreviewView,
  SessionEnvelope,
  SessionItemView,
} from '../api/client';
import CompanionPortrait from '../companions/CompanionPortrait.vue';
import type {
  CompanionExpression,
  CompanionProfile,
} from '../companions/catalog';
import type { GamePhase } from './shortcutStateMachine';

const props = defineProps<{
  session: SessionEnvelope;
  item?: SessionItemView;
  phase: GamePhase;
  answer: string;
  pendingAnswer?: PendingAnswerView;
  reviewPreviews?: ReviewPreviewView;
  hintText: string | null;
  hintUsed: boolean;
  answerRevealed: boolean;
  busy: boolean;
  error: string | null;
  combo: number;
  reviewedCount: number;
  practiceCount: number;
  focusMode: boolean;
  reduceMotion: boolean;
  companion: CompanionProfile;
  lastRating?: 'again' | 'hard' | 'good' | 'easy';
}>();

const emit = defineEmits<{
  'update:answer': [value: string];
  submit: [];
  hint: [];
  reveal: [];
  skip: [];
  rate: [rating: 'again' | 'hard' | 'good' | 'easy'];
  pause: [];
  resume: [];
  exit: [];
  help: [];
  'toggle-focus': [];
  'retry-preview': [];
  'reload-session': [];
}>();

const answerInput = ref<HTMLInputElement>();
const queueCompleted = computed(
  () => props.session.session.queue.filter(
    (item) => item.status === 'completed' || item.status === 'skipped',
  ).length,
);
const queueTotal = computed(() => props.session.session.queue.length);
const progress = computed(
  () => queueTotal.value === 0 ? 0 : queueCompleted.value / queueTotal.value * 100,
);
const companionExpression = computed<CompanionExpression>(() => {
  if (props.phase === 'paused' || props.phase === 'answer-ready') {
    return 'concern';
  }
  if (props.lastRating === 'again') {
    return 'concern';
  }
  if (props.lastRating === 'good' || props.lastRating === 'easy') {
    return 'relief';
  }
  return props.phase === 'rating' ? 'neutral' : 'welcome';
});
const companionLine = computed(() => {
  if (props.phase === 'paused') {
    return props.companion.dialogue.rest;
  }
  if (props.phase === 'answer-ready') {
    return props.companion.dialogue.practice;
  }
  if (props.phase === 'typing' && props.lastRating === 'again') {
    return props.companion.dialogue.again;
  }
  if (props.phase === 'rating') {
    return '作答已经封存。按 1–4 明确选择这次回忆的真实感受，不需要追求夸奖。';
  }
  return props.companion.dialogue.training;
});
const ratings = [
  {
    value: 'again',
    label: 'Again',
    description: '没有想起来',
  },
  {
    value: 'hard',
    label: 'Hard',
    description: '勉强回忆',
  },
  {
    value: 'good',
    label: 'Good',
    description: '正常回忆',
  },
  {
    value: 'easy',
    label: 'Easy',
    description: '轻松回忆',
  },
] as const;

function formatDue(timestamp: number): string {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(timestamp));
}

async function focusAnswer(): Promise<void> {
  if (props.phase !== 'typing') {
    return;
  }
  await nextTick();
  answerInput.value?.focus();
}

watch(
  () => [props.phase, props.item?.studyItem.id],
  () => {
    if (props.phase === 'typing') {
      void focusAnswer();
    }
  },
  { flush: 'post' },
);

defineExpose({ focusAnswer });
</script>

<template>
  <section
    class="training-stage"
    :class="{ 'focus-mode': focusMode, 'reduce-motion': reduceMotion }"
    aria-labelledby="training-title"
  >
    <header class="training-topline">
      <div class="training-status">
        <span class="status-light" aria-hidden="true"></span>
        <span>Session {{ session.session.id.slice(0, 8) }}</span>
        <span>v{{ session.session.version }}</span>
      </div>
      <div class="training-progress-copy">
        <span>{{ queueCompleted }}/{{ queueTotal }}</span>
        <span>连续命中 {{ combo }}</span>
      </div>
      <div class="training-tools">
        <button
          type="button"
          class="icon-button"
          title="返回大厅"
          aria-label="暂停并返回大厅"
          @click="emit('exit')"
        >
          <ChevronLeft :size="18" />
        </button>
        <button
          type="button"
          class="icon-button"
          title="切换专注模式"
          aria-label="切换专注模式"
          :aria-pressed="focusMode"
          @click="emit('toggle-focus')"
        >
          <Focus :size="18" />
        </button>
        <button
          type="button"
          class="icon-button"
          title="快捷键帮助"
          aria-label="打开快捷键帮助"
          @click="emit('help')"
        >
          <CircleHelp :size="18" />
        </button>
        <button
          v-if="phase !== 'paused'"
          type="button"
          class="icon-button"
          title="暂停训练"
          aria-label="暂停训练"
          @click="emit('pause')"
        >
          <Pause :size="18" />
        </button>
        <button
          v-else
          type="button"
          class="icon-button"
          title="继续训练"
          aria-label="继续训练"
          @click="emit('resume')"
        >
          <Play :size="18" />
        </button>
      </div>
    </header>

    <div class="progress-rail" aria-hidden="true">
      <span :style="{ width: `${progress}%` }"></span>
    </div>

    <div class="training-grid">
      <main class="word-console">
        <div class="console-label">
          <Target :size="17" aria-hidden="true" />
          <span>中文释义 → 英文拼写</span>
          <strong>{{ reviewedCount }} 正式 · {{ practiceCount }} 练习</strong>
        </div>

        <div v-if="item" class="word-stage">
          <p class="word-kicker">Recall Target</p>
          <h1 id="training-title">{{ item.wordEntry.meaning }}</h1>

          <div v-if="hintText" class="hint-line">
            <Lightbulb :size="17" aria-hidden="true" />
            <span>{{ hintText }}</span>
          </div>

          <form class="answer-console" @submit.prevent="emit('submit')">
            <label for="online-answer">英文拼写</label>
            <div class="answer-row">
              <input
                id="online-answer"
                ref="answerInput"
                :value="answer"
                type="text"
                inputmode="text"
                autocomplete="off"
                autocapitalize="off"
                spellcheck="false"
                :disabled="busy || phase === 'rating' || phase === 'answer-ready'"
                @input="emit('update:answer', ($event.target as HTMLInputElement).value)"
              />
              <button
                type="submit"
                class="submit-key"
                :disabled="busy || phase !== 'typing'"
                aria-label="提交当前答案"
              >
                <CornerDownLeft :size="20" />
              </button>
            </div>
          </form>

          <div v-if="phase === 'answer-ready'" class="practice-reveal">
            <span>答案已揭晓，本次只记练习</span>
            <strong>{{ item.wordEntry.term }}</strong>
            <p>按 Enter 继续下一个目标，不会推进 FSRS。</p>
          </div>

          <div v-if="phase === 'rating'" class="rating-console" aria-label="正式复习评分">
            <div>
              <Sparkles :size="18" aria-hidden="true" />
              <span>作答已封存。请明确选择本次记忆评分。</span>
            </div>
            <div class="rating-grid">
              <button
                v-for="(rating, index) in ratings"
                :key="rating.value"
                type="button"
                class="rating-button"
                :disabled="busy || reviewPreviews === undefined"
                @click="emit('rate', rating.value)"
              >
                <kbd>{{ index + 1 }}</kbd>
                <strong>{{ rating.label }}</strong>
                <span>{{ rating.description }}</span>
                <small v-if="reviewPreviews">
                  {{ formatDue(reviewPreviews.previews[rating.value].dueAt) }}
                </small>
                <small v-else>正在计算预计时间</small>
              </button>
            </div>
            <button
              v-if="reviewPreviews === undefined && !busy"
              type="button"
              class="text-action"
              @click="emit('retry-preview')"
            >
              重试预计间隔
            </button>
          </div>

          <div v-if="phase === 'typing'" class="training-shortcuts">
            <button type="button" @click="emit('hint')">
              <Lightbulb :size="16" />
              <span>提示</span>
              <kbd>Ctrl H</kbd>
            </button>
            <button type="button" @click="emit('reveal')">
              <Eye :size="16" />
              <span>揭晓</span>
              <kbd>Ctrl Enter</kbd>
            </button>
            <button type="button" @click="emit('skip')">
              <SkipForward :size="16" />
              <span>跳过</span>
              <kbd>Ctrl Shift Enter</kbd>
            </button>
          </div>

          <p v-if="hintUsed && phase === 'typing'" class="practice-note">
            <Eye :size="16" aria-hidden="true" />
            提示或揭晓后的提交只记练习，不推进正式复习。
          </p>
        </div>

        <div v-else class="word-stage empty">
          <Target :size="26" aria-hidden="true" />
          <h1 id="training-title">队列已完成</h1>
          <p>正在整理本次训练的正式复习与练习结果。</p>
        </div>

        <p v-if="error" class="training-error" role="alert">
          <span>{{ error }}</span>
          <button type="button" @click="emit('reload-session')">
            刷新会话
          </button>
        </p>
      </main>

      <aside
        class="training-companion"
        :aria-label="`${companion.name}的陪伴提示`"
      >
        <CompanionPortrait
          :companion="companion"
          :expression="companionExpression"
          :reduce-motion="reduceMotion"
        />
        <div>
          <span>{{ companion.name }} · {{ companion.epithet }}</span>
          <p>{{ companionLine }}</p>
        </div>
      </aside>
    </div>

    <div v-if="phase === 'paused'" class="pause-overlay" role="status">
      <Pause :size="24" aria-hidden="true" />
      <h2>训练已暂停</h2>
      <p>当前队列没有丢失。按 Enter 或点击继续恢复。</p>
      <button type="button" class="primary-action" @click="emit('resume')">
        <Play :size="17" />
        继续训练
      </button>
      <button type="button" class="secondary-action" @click="emit('exit')">
        返回大厅
      </button>
    </div>

    <div v-if="focusMode" class="focus-indicator">
      <span>Focus</span>
      <button
        type="button"
        aria-label="退出专注模式"
        @click="emit('toggle-focus')"
      >
        <X :size="16" />
      </button>
    </div>
  </section>
</template>

<style scoped>
.training-stage {
  position: relative;
  display: grid;
  gap: 12px;
  min-height: calc(100vh - 150px);
}

.training-topline {
  display: grid;
  grid-template-columns: 1fr auto auto;
  align-items: center;
  gap: 18px;
  color: #9eb5c8;
}

.training-status,
.training-progress-copy,
.training-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 0.72rem;
}

.training-status {
  min-width: 0;
}

.status-light {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #55d7e8;
  box-shadow: 0 0 12px rgb(85 215 232 / 75%);
}

.icon-button {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  padding: 0;
  border: 1px solid rgb(158 181 200 / 28%);
  border-radius: 6px;
  background: rgb(7 20 38 / 72%);
  color: #d8ecf7;
  cursor: pointer;
}

.progress-rail {
  height: 4px;
  overflow: hidden;
  border-radius: 2px;
  background: rgb(158 181 200 / 16%);
}

.progress-rail span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #55d7e8, #72e6c1);
  transition: width 260ms ease;
}

.training-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 230px;
  gap: 16px;
  align-items: stretch;
  min-height: 560px;
}

.word-console {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: clamp(20px, 4vw, 38px);
  border: 1px solid rgb(85 215 232 / 22%);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgb(9 26 45 / 88%), rgb(7 20 38 / 94%)),
    rgb(7 20 38 / 90%);
  box-shadow: inset 0 1px rgb(255 255 255 / 3%);
}

.console-label {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #8eaabe;
  font-size: 0.72rem;
}

.console-label strong {
  margin-left: auto;
  color: #f19ab6;
  font-size: 0.7rem;
}

.word-stage {
  display: grid;
  flex: 1;
  align-content: center;
  gap: 20px;
  max-width: 760px;
  width: 100%;
  margin: 0 auto;
  padding: 22px 0;
}

.word-kicker {
  margin: 0;
  color: #55d7e8;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.word-stage h1 {
  margin: 0;
  color: #f4fbff;
  font-family: "Songti SC", "Noto Serif CJK SC", Georgia, serif;
  font-size: clamp(2.4rem, 6vw, 4.8rem);
  font-weight: 600;
  letter-spacing: 0;
  line-height: 1.14;
  text-wrap: balance;
}

.hint-line,
.practice-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0;
  color: #f0bd6b;
  font-size: 0.8rem;
  line-height: 1.6;
}

.answer-console {
  display: grid;
  gap: 7px;
}

.answer-console label {
  color: #8ca9c1;
  font-size: 0.72rem;
  font-weight: 700;
}

.answer-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 52px;
  gap: 8px;
}

.answer-row input {
  width: 100%;
  min-height: 60px;
  padding: 0 18px;
  border: 1px solid rgb(85 215 232 / 34%);
  border-radius: 7px;
  background: #f4fbff;
  color: #071426;
  font-size: clamp(1.15rem, 2.6vw, 1.65rem);
  font-weight: 700;
  letter-spacing: 0.02em;
}

.submit-key {
  display: grid;
  place-items: center;
  min-height: 60px;
  padding: 0;
  border: 1px solid #55d7e8;
  border-radius: 7px;
  background: #55d7e8;
  color: #061426;
  cursor: pointer;
}

.practice-reveal {
  display: grid;
  gap: 6px;
  padding: 14px;
  border-left: 3px solid #f0bd6b;
  background: rgb(240 189 107 / 9%);
}

.practice-reveal span,
.practice-reveal p {
  margin: 0;
  color: #b9cad7;
  font-size: 0.75rem;
}

.practice-reveal strong {
  color: #f4fbff;
  font-size: 1.6rem;
}

.rating-console {
  display: grid;
  gap: 12px;
}

.rating-console > div:first-child {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #9eb5c8;
  font-size: 0.78rem;
}

.rating-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.rating-button {
  display: grid;
  min-height: 116px;
  align-content: center;
  justify-items: start;
  gap: 4px;
  padding: 12px;
  border: 1px solid rgb(158 181 200 / 24%);
  border-radius: 7px;
  background: rgb(18 43 67 / 76%);
  color: #edf8ff;
  text-align: left;
  cursor: pointer;
}

.rating-button:hover:not(:disabled) {
  border-color: rgb(85 215 232 / 55%);
  background: rgb(23 50 76 / 90%);
}

.rating-button kbd {
  color: #55d7e8;
  font: 800 0.7rem/1 "SFMono-Regular", Consolas, monospace;
}

.rating-button strong {
  font-size: 0.92rem;
}

.rating-button span,
.rating-button small {
  color: #8ca9c1;
  font-size: 0.68rem;
}

.text-action {
  justify-self: start;
  min-height: 34px;
  padding: 0 11px;
  border: 1px solid rgb(158 181 200 / 26%);
  border-radius: 6px;
  background: transparent;
  color: #c9dce9;
  cursor: pointer;
}

.training-shortcuts {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.training-shortcuts button {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  gap: 7px;
  padding: 0 10px;
  border: 1px solid rgb(158 181 200 / 22%);
  border-radius: 6px;
  background: transparent;
  color: #9eb5c8;
  cursor: pointer;
}

.training-shortcuts kbd {
  color: #617b90;
  font: 600 0.65rem/1 "SFMono-Regular", Consolas, monospace;
}

.practice-note {
  color: #f0bd6b;
}

.training-companion {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  overflow: hidden;
  border: 1px solid rgb(241 154 182 / 20%);
  border-radius: 8px;
  background: rgb(9 24 42 / 58%);
}

.training-companion :deep(.companion-portrait) {
  width: 100%;
  height: 78%;
  opacity: 0.92;
}

.training-companion :deep(.companion-portrait img) {
  object-fit: contain;
  object-position: center bottom;
}

.training-companion > div {
  position: relative;
  z-index: 1;
  padding: 12px;
  border-top: 1px solid rgb(241 154 182 / 20%);
  background: rgb(7 20 38 / 86%);
}

.training-companion span {
  color: #f19ab6;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.1em;
}

.training-companion p {
  margin: 5px 0 0;
  color: #b8cad7;
  font-size: 0.75rem;
  line-height: 1.65;
}

.word-stage.empty {
  justify-items: center;
  text-align: center;
}

.word-stage.empty p {
  color: #9eb5c8;
}

.training-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 12px 0 0;
  padding: 10px 12px;
  border-left: 3px solid #f07878;
  background: rgb(240 120 120 / 10%);
  color: #ffd2d2;
  font-size: 0.78rem;
}

.training-error button {
  min-height: 32px;
  padding: 0 10px;
  border: 1px solid rgb(255 210 210 / 34%);
  border-radius: 5px;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.pause-overlay {
  position: absolute;
  z-index: 5;
  inset: 52px 230px 0 0;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 8px;
  padding: 24px;
  border-radius: 8px;
  background: rgb(4 13 26 / 86%);
  backdrop-filter: blur(12px);
  text-align: center;
}

.pause-overlay h2 {
  margin: 0;
  color: #f4fbff;
}

.pause-overlay p {
  margin: 0 0 10px;
  color: #9eb5c8;
  font-size: 0.82rem;
}

.primary-action {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  gap: 8px;
  padding: 0 15px;
  border: 1px solid #55d7e8;
  border-radius: 6px;
  background: #55d7e8;
  color: #061426;
  font-weight: 800;
  cursor: pointer;
}

.secondary-action {
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid rgb(158 181 200 / 28%);
  border-radius: 6px;
  background: transparent;
  color: #d8e8f3;
  cursor: pointer;
}

.focus-indicator {
  position: fixed;
  z-index: 20;
  right: 18px;
  bottom: 18px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 8px 7px 11px;
  border: 1px solid rgb(85 215 232 / 24%);
  border-radius: 999px;
  background: rgb(7 20 38 / 88%);
  color: #55d7e8;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.focus-indicator button {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: rgb(85 215 232 / 12%);
  color: inherit;
  cursor: pointer;
}

.focus-mode .training-companion {
  opacity: 0.28;
}

button:focus-visible,
input:focus-visible {
  outline: 3px solid rgb(85 215 232 / 48%);
  outline-offset: 2px;
}

.reduce-motion .progress-rail span {
  transition: none;
}

@media (max-width: 980px) {
  .training-grid {
    grid-template-columns: 1fr;
  }

  .training-companion {
    display: none;
  }

  .pause-overlay {
    inset: 52px 0 0;
  }
}

@media (max-width: 680px) {
  .training-topline {
    grid-template-columns: 1fr auto;
  }

  .training-progress-copy {
    grid-column: 1 / -1;
    grid-row: 2;
  }

  .training-tools {
    grid-column: 2;
    grid-row: 1;
  }

  .rating-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .word-console {
    padding: 18px;
  }

  .training-shortcuts button {
    flex: 1 1 140px;
  }
}
</style>
