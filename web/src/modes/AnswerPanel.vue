<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import type {
  PendingAnswer,
  PracticeInteractionState,
  PracticeItem,
  ReviewRating,
} from '../core/domain';

const props = withDefaults(
  defineProps<{
    currentItem: PracticeItem;
    interaction: PracticeInteractionState;
    pendingAnswer: PendingAnswer | null;
    paused: boolean;
    busy: boolean;
    error: string | null;
    allowRetry?: boolean;
  }>(),
  {
    allowRetry: false,
  },
);

const emit = defineEmits<{
  draft: [value: string];
  hint: [];
  reveal: [];
  submit: [];
  skip: [];
  rate: [rating: ReviewRating];
  practice: [];
  retry: [];
}>();

const input = ref<HTMLInputElement | null>(null);
const answerVisible = computed(
  () => props.pendingAnswer !== null || props.interaction.answerRevealed,
);
const hintUsed = computed(() => props.interaction.revealedHintCount > 0);
const answerModeLabel = computed(() =>
  props.interaction.answerRevealed ? '看词抄写' : '隐藏答案默写',
);
const answerStateLabel = computed(() => {
  if (props.paused) {
    return '已暂停';
  }
  if (props.pendingAnswer !== null) {
    return '选择结果';
  }
  return props.interaction.answerRevealed ? '抄写练习' : '输入英文';
});
const visibleHints = computed(() =>
  props.currentItem.wordEntry.hints.slice(0, props.interaction.revealedHintCount),
);
const canUseMoreHints = computed(
  () => props.interaction.revealedHintCount < props.currentItem.wordEntry.hints.length,
);
const formalEligible = computed(
  () =>
    props.pendingAnswer !== null &&
    !props.paused &&
    props.pendingAnswer.answerWasHidden &&
    !props.pendingAnswer.hintUsed &&
    !props.pendingAnswer.answerRevealed,
);

const ratings: ReadonlyArray<{ value: ReviewRating; label: string; description: string }> = [
  { value: 'again', label: 'Again', description: '没想起来' },
  { value: 'hard', label: 'Hard', description: '很吃力' },
  { value: 'good', label: 'Good', description: '记得' },
  { value: 'easy', label: 'Easy', description: '很熟练' },
];

async function focusInput(): Promise<void> {
  await nextTick();
  input.value?.focus();
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' || event.isComposing) {
    return;
  }
  event.preventDefault();
  if (!props.paused && !props.busy && props.pendingAnswer === null) {
    emit('submit');
  }
}

watch(
  () => [props.currentItem.studyItem.id, props.paused, props.pendingAnswer] as const,
  () => {
    if (!props.paused && props.pendingAnswer === null) {
      void focusInput();
    }
  },
  { immediate: true },
);
</script>

<template>
  <section class="answer-panel" aria-labelledby="answer-title">
    <header class="answer-heading">
      <div>
        <p class="answer-eyebrow">{{ answerModeLabel }}</p>
        <h2 id="answer-title">{{ currentItem.wordEntry.meaning }}</h2>
      </div>
      <span class="answer-state">
        {{ answerStateLabel }}
      </span>
    </header>

    <div v-if="paused" class="paused-note" role="status">
      模式已暂停。恢复后继续当前题目，草稿会保留。
    </div>

    <form v-else @submit.prevent="emit('submit')">
      <label class="input-label" for="answer-input">英文拼写</label>
      <div class="input-row">
        <input
          id="answer-input"
          ref="input"
          class="answer-input"
          type="text"
          autocomplete="off"
          autocapitalize="off"
          spellcheck="false"
          :value="pendingAnswer?.response ?? interaction.draft"
          :disabled="pendingAnswer !== null || busy"
          aria-describedby="answer-help answer-feedback"
          @input="emit('draft', ($event.target as HTMLInputElement).value)"
          @keydown="handleKeydown"
        />
        <button
          v-if="pendingAnswer === null"
          class="primary-action"
          type="submit"
          :disabled="busy || interaction.draft.trim().length === 0"
        >
          提交
        </button>
      </div>
      <p id="answer-help" class="input-help">
        输入只归属于上方高亮的当前目标。按 Enter 提交；没有自动超时，也不会因动画结束或键盘误触自动记错。
      </p>
    </form>

    <div v-if="pendingAnswer === null && !paused" class="secondary-actions">
      <button
        type="button"
        :disabled="busy || !canUseMoreHints"
        @click="emit('hint')"
      >
        提示 {{ interaction.revealedHintCount }}/{{ currentItem.wordEntry.hints.length }}
      </button>
      <button type="button" :disabled="busy || answerVisible" @click="emit('reveal')">
        看答案抄写
      </button>
      <button type="button" class="quiet-danger" :disabled="busy" @click="emit('skip')">
        跳过
      </button>
    </div>

    <div v-if="visibleHints.length > 0" class="hint-box" aria-live="polite">
      <strong>提示</strong>
      <ul>
        <li v-for="hint in visibleHints" :key="hint">{{ hint }}</li>
      </ul>
      <p>使用提示后，本次只记录练习，不更新 FSRS。</p>
    </div>

    <div v-if="answerVisible" class="answer-reveal" aria-live="polite">
      <span>正确拼写</span>
      <strong>{{ currentItem.wordEntry.term }}</strong>
      <p v-if="hintUsed">本次已使用提示，不会提交正式复习。</p>
      <p v-else-if="interaction.answerRevealed && pendingAnswer === null">
        显示答案后可用于抄写练习，不会自动记为 Good 或 Easy。
      </p>
    </div>

    <div
      v-if="pendingAnswer !== null"
      id="answer-feedback"
      class="feedback"
      :class="{ correct: pendingAnswer.inputMatchedAnswer }"
      role="status"
      aria-live="polite"
    >
      <strong>{{ pendingAnswer.inputMatchedAnswer ? '拼写匹配' : '拼写不同' }}</strong>
      <span>你的输入：{{ pendingAnswer.response || '空' }}</span>
    </div>

    <div v-if="pendingAnswer !== null" class="rating-section">
      <template v-if="formalEligible">
        <p class="rating-prompt">选择本次正式复习评分</p>
        <div class="rating-grid">
          <button
            v-for="rating in ratings"
            :key="rating.value"
            type="button"
            class="rating-button"
            :disabled="busy || paused"
            @click="emit('rate', rating.value)"
          >
            <strong>{{ rating.label }}</strong>
            <span>{{ rating.description }}</span>
          </button>
        </div>
        <button
          v-if="!pendingAnswer.inputMatchedAnswer && allowRetry"
          type="button"
          class="retry-action"
          :disabled="busy || paused"
          @click="emit('retry')"
        >
          加入错词重练
        </button>
      </template>
      <template v-else>
        <p class="practice-notice">本次使用提示、显示答案或主动放弃，仅记录练习。</p>
        <div class="practice-actions">
          <button
            v-if="!pendingAnswer.inputMatchedAnswer && allowRetry"
            type="button"
            class="primary-action"
            :disabled="busy || paused"
            @click="emit('retry')"
          >
            记录练习并重练
          </button>
          <button
            v-else
            type="button"
            class="primary-action"
            :disabled="busy || paused"
            @click="emit('practice')"
          >
            记录练习并继续
          </button>
        </div>
      </template>
    </div>

    <p v-if="error" class="error-message" role="alert">{{ error }}</p>
    <p v-if="busy" class="busy-message" role="status">正在保存...</p>
  </section>
</template>

<style scoped>
.answer-panel {
  margin-top: 18px;
  padding: 22px;
  border: 1px solid #d0dad4;
  border-radius: 8px;
  background: #fff;
}

.answer-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.answer-heading h2 {
  margin: 2px 0 0;
  color: #21352a;
  font-size: 1.2rem;
  font-weight: 700;
}

.answer-eyebrow {
  margin: 0;
  color: #78867e;
  font-size: 0.7rem;
  font-weight: 700;
}

.answer-state {
  padding: 4px 9px;
  border: 1px solid #cfd8d2;
  border-radius: 6px;
  color: #5f7067;
  font-size: 0.72rem;
}

.input-label {
  display: block;
  margin-bottom: 7px;
  color: #53655b;
  font-size: 0.76rem;
  font-weight: 700;
}

.input-row {
  display: flex;
  gap: 10px;
}

.answer-input {
  width: 100%;
  min-height: 46px;
  padding: 10px 12px;
  border: 1px solid #aebdb4;
  border-radius: 6px;
  color: #1e3026;
  font-size: 1.15rem;
}

.answer-input:focus-visible,
button:focus-visible {
  outline: 3px solid rgb(45 113 83 / 28%);
  outline-offset: 2px;
}

.input-help,
.practice-notice,
.paused-note,
.busy-message {
  margin: 8px 0 0;
  color: #708078;
  font-size: 0.76rem;
  line-height: 1.5;
}

button {
  min-height: 38px;
  padding: 8px 13px;
  border: 1px solid #bfcbc4;
  border-radius: 6px;
  background: #f7f9f8;
  color: #30453a;
  font-size: 0.82rem;
  font-weight: 650;
  cursor: pointer;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.primary-action {
  min-width: 96px;
  border-color: #244f3d;
  background: #244f3d;
  color: #fff;
}

.secondary-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.quiet-danger {
  color: #9b4935;
}

.hint-box,
.answer-reveal,
.feedback {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 6px;
  background: #f3f6f4;
}

.hint-box strong,
.answer-reveal span,
.feedback strong {
  color: #53665b;
  font-size: 0.74rem;
}

.hint-box ul {
  margin: 6px 0;
  padding-left: 18px;
  color: #2b4034;
}

.hint-box p,
.answer-reveal p {
  margin: 6px 0 0;
  color: #74827a;
  font-size: 0.75rem;
}

.answer-reveal {
  display: grid;
  gap: 3px;
  border-left: 3px solid #b06a45;
}

.answer-reveal strong {
  color: #2e4337;
  font-size: 1.15rem;
}

.feedback {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  border-left: 3px solid #a55b43;
}

.feedback.correct {
  border-left-color: #39745a;
}

.feedback span {
  color: #68776f;
  font-size: 0.8rem;
}

.rating-prompt {
  margin: 0 0 10px;
  color: #465b50;
  font-size: 0.8rem;
  font-weight: 700;
}

.rating-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.retry-action {
  margin-top: 10px;
  border-color: #c38c76;
  color: #874a37;
}

.practice-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.rating-button {
  display: grid;
  gap: 3px;
  min-height: 60px;
  text-align: left;
}

.rating-button span {
  color: #718078;
  font-size: 0.7rem;
  font-weight: 500;
}

.error-message {
  margin: 12px 0 0;
  color: #9e3e2c;
  font-size: 0.8rem;
}

.paused-note {
  padding: 12px;
  border-left: 3px solid #8d6b3f;
  background: #f5f2eb;
}

@media (max-width: 720px) {
  .answer-panel {
    padding: 16px;
  }

  .input-row {
    align-items: stretch;
    flex-direction: column;
  }

  .primary-action {
    width: 100%;
  }

  .rating-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
