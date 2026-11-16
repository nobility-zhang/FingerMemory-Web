<script setup lang="ts">
import { computed } from 'vue';
import {
  Home,
  Keyboard,
  RotateCcw,
  Sparkles,
  Target,
  Timer,
  Trophy,
} from '@lucide/vue';
import CompanionPortrait from '../companions/CompanionPortrait.vue';
import type { CompanionProfile } from '../companions/catalog';
import type { GameRunResult } from './types';

const props = defineProps<{
  companion: CompanionProfile;
  result: GameRunResult;
  reduceMotion: boolean;
  lastRating?: 'again' | 'hard' | 'good' | 'easy';
}>();

const emit = defineEmits<{
  again: [];
  lobby: [];
}>();

const accuracy = computed(() => {
  if (props.result.totalAnswerCount === 0) {
    return 0;
  }
  return Math.round(
    props.result.matchedCount / props.result.totalAnswerCount * 100,
  );
});
const duration = computed(() => {
  const seconds = Math.max(
    0,
    Math.round((props.result.endedAt - props.result.startedAt) / 1000),
  );
  const minutes = Math.floor(seconds / 60);
  const remaining = seconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(remaining).padStart(2, '0')}`;
});
const ratings = [
  ['again', 'Again'],
  ['hard', 'Hard'],
  ['good', 'Good'],
  ['easy', 'Easy'],
] as const;
const completionLine = computed(() => {
  if (props.lastRating === 'again') {
    return props.companion.dialogue.again;
  }
  if (props.result.reviewedCount > 0) {
    return props.companion.dialogue.result;
  }
  return props.companion.dialogue.practice;
});
</script>

<template>
  <section class="result-screen" aria-labelledby="result-title">
    <div class="result-copy">
      <p class="eyebrow">旅途手记</p>
      <span class="result-emblem" aria-hidden="true">
        <Trophy :size="30" />
      </span>
      <h1 id="result-title">今天的旅途笔记收好了</h1>
      <p class="result-lead">
        以下数字来自本次与服务端会话对应的真实提交。拼写输入命中率仅作练习反馈，
        不替代你的 FSRS 记忆评分。
      </p>

      <div class="result-stats">
        <div>
          <Sparkles :size="18" aria-hidden="true" />
          <span>正式复习</span>
          <strong>{{ result.reviewedCount }}</strong>
        </div>
        <div>
          <Keyboard :size="18" aria-hidden="true" />
          <span>练习记录</span>
          <strong>{{ result.practiceCount }}</strong>
        </div>
        <div>
          <Target :size="18" aria-hidden="true" />
          <span>输入命中</span>
          <strong>{{ accuracy }}%</strong>
        </div>
        <div>
          <Timer :size="18" aria-hidden="true" />
          <span>本次用时</span>
          <strong>{{ duration }}</strong>
        </div>
      </div>

      <div class="rating-summary" aria-label="本次评分分布">
        <div v-for="[key, label] in ratings" :key="key">
          <span>{{ label }}</span>
          <strong>{{ result.ratings[key] }}</strong>
          <i
            :style="{
              width: result.reviewedCount === 0
                ? '0%'
                : `${result.ratings[key] / result.reviewedCount * 100}%`,
            }"
            aria-hidden="true"
          ></i>
        </div>
      </div>

      <div class="result-actions">
        <button type="button" class="primary-action" @click="emit('again')">
          <RotateCcw :size="18" />
          再复习一轮
        </button>
        <button type="button" @click="emit('lobby')">
          <Home :size="18" />
          返回旅店
        </button>
      </div>
    </div>

    <aside
      class="result-companion"
      :aria-label="`${companion.name}的旅途结算`"
    >
      <CompanionPortrait
        :companion="companion"
        :expression="lastRating === 'again'
          ? 'concern'
          : result.reviewedCount > 0 ? 'relief' : 'welcome'"
        :reduce-motion="reduceMotion"
      />
      <div>
        <span>{{ companion.name }} · {{ companion.epithet }}</span>
        <p>{{ completionLine }}</p>
      </div>
    </aside>
  </section>
</template>

<style scoped>
.result-screen {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(300px, 0.85fr);
  gap: 24px;
  min-height: calc(100vh - 164px);
  align-items: stretch;
}

.result-copy {
  align-self: center;
  max-width: 760px;
  padding: 20px 0;
}

.eyebrow {
  margin: 0 0 12px;
  color: #d8b171;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.result-emblem {
  display: grid;
  width: 58px;
  height: 58px;
  place-items: center;
  margin-bottom: 15px;
  border: 1px solid rgb(201 141 145 / 34%);
  border-radius: 8px;
  background: rgb(201 141 145 / 10%);
  color: #c98d91;
}

h1 {
  margin: 0;
  color: #fff0d8;
  font-family: "Songti SC", "Noto Serif CJK SC", Georgia, serif;
  font-size: clamp(2.6rem, 5.4vw, 4.8rem);
  font-weight: 600;
  letter-spacing: 0;
  line-height: 1.05;
}

.result-lead {
  max-width: 62ch;
  margin: 18px 0 0;
  color: #b4c9d8;
  font-size: 0.9rem;
  line-height: 1.85;
}

.result-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1px;
  margin-top: 24px;
  overflow: hidden;
  border: 1px solid rgb(216 177 113 / 18%);
  border-radius: 8px;
  background: rgb(216 177 113 / 16%);
}

.result-stats > div {
  display: grid;
  gap: 7px;
  padding: 14px;
  background: rgb(32 23 21 / 90%);
  color: #d8b171;
}

.result-stats span {
  color: #b09b82;
  font-size: 0.72rem;
}

.result-stats strong {
  color: #fff0d8;
  font-size: 1.45rem;
}

.rating-summary {
  display: grid;
  gap: 8px;
  margin-top: 22px;
}

.rating-summary > div {
  position: relative;
  display: grid;
  grid-template-columns: 80px auto;
  gap: 12px;
  align-items: center;
  min-height: 30px;
  overflow: hidden;
  padding: 0 10px;
}

.rating-summary span,
.rating-summary strong {
  position: relative;
  z-index: 1;
}

.rating-summary span {
  color: #c1ae94;
  font-size: 0.72rem;
}

.rating-summary strong {
  color: #fff0d8;
  font-size: 0.78rem;
}

.rating-summary i {
  position: absolute;
  inset: 4px auto 4px 0;
  border-left: 2px solid rgb(216 177 113 / 50%);
  background: rgb(216 177 113 / 10%);
}

.result-actions {
  display: flex;
  gap: 9px;
  flex-wrap: wrap;
  margin-top: 24px;
}

.result-actions button {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  gap: 8px;
  padding: 0 15px;
  border: 1px solid rgb(197 174 140 / 30%);
  border-radius: 6px;
  background: rgb(48 35 29 / 76%);
  color: #dcecf7;
  font-weight: 750;
  cursor: pointer;
}

.result-actions .primary-action {
  border-color: #d8b171;
  background: #d8b171;
  color: #2d2018;
}

.result-companion {
  display: flex;
  min-height: 500px;
  flex-direction: column;
  justify-content: flex-end;
  overflow: hidden;
  border: 1px solid rgb(201 141 145 / 22%);
  border-radius: 8px;
  background: rgb(38 28 25 / 52%);
}

.result-companion :deep(.companion-portrait) {
  width: 100%;
  height: 82%;
  opacity: 0.94;
}

.result-companion :deep(.companion-portrait img) {
  object-fit: contain;
  object-position: center bottom;
}

.result-companion > div {
  padding: 13px 15px;
  border-top: 1px solid rgb(201 141 145 / 22%);
  background: rgb(32 23 21 / 88%);
}

.result-companion span {
  color: #c98d91;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.1em;
}

.result-companion p {
  margin: 5px 0 0;
  color: #b8cad7;
  font-size: 0.78rem;
  line-height: 1.65;
}

button:focus-visible {
  outline: 3px solid rgb(216 177 113 / 46%);
  outline-offset: 2px;
}

@media (max-width: 900px) {
  .result-screen {
    grid-template-columns: 1fr;
  }

  .result-companion {
    display: none;
  }
}

@media (max-width: 620px) {
  .result-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
