<script setup lang="ts">
import { computed } from 'vue';
import type { PracticeModeProps } from './types';

const props = defineProps<PracticeModeProps>();
const emit = defineEmits<{
  select: [index: number];
}>();

const activeItem = computed(() => props.items[props.currentIndex]);

function cardStyle(index: number): Record<string, string> {
  const column = index % 4;
  return {
    left: `${8 + column * 23}%`,
    animationDuration: `${Math.round(16 / props.speed)}s`,
    animationDelay: `${-index * 2.4}s`,
  };
}
</script>

<template>
  <section class="card-rain-mode" aria-labelledby="card-rain-title">
    <header class="card-rain-heading">
      <div>
        <p class="mode-eyebrow">卡片雨目标</p>
        <h2 id="card-rain-title">{{ activeItem?.wordEntry.meaning }}</h2>
      </div>
      <span class="position">{{ currentIndex + 1 }} / {{ queue.length }}</span>
    </header>

    <div
      class="card-rain-stage"
      :class="{
        paused,
        'reduced-motion': reduceMotion,
      }"
    >
      <button
        v-for="(item, index) in items"
        :key="item.studyItem.id"
        type="button"
        class="falling-card"
        :class="{
          current: index === currentIndex,
          complete: queue[index]?.status !== 'pending',
        }"
        :style="cardStyle(index)"
        :disabled="paused || queue[index]?.status !== 'pending'"
        :aria-current="index === currentIndex ? 'step' : undefined"
        @click="emit('select', index)"
      >
        <span class="card-index">{{ String(index + 1).padStart(2, '0') }}</span>
        <strong>{{ item.wordEntry.meaning }}</strong>
        <small>{{ item.wordEntry.term.length }} 个字母</small>
        <em v-if="index === currentIndex">当前目标</em>
        <em v-else-if="queue[index]?.status === 'completed'">已完成</em>
        <em v-else-if="queue[index]?.status === 'skipped'">已跳过</em>
      </button>
    </div>
  </section>
</template>

<style scoped>
.card-rain-mode {
  min-width: 0;
}

.card-rain-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 12px;
}

.card-rain-heading h2 {
  margin: 2px 0 0;
  color: #1d3026;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 1.65rem;
  font-weight: 500;
}

.mode-eyebrow {
  margin: 0;
  color: #75837b;
  font-size: 0.72rem;
  font-weight: 700;
}

.position {
  color: #68766e;
  font-size: 0.8rem;
}

.card-rain-stage {
  position: relative;
  height: 320px;
  overflow: hidden;
  border: 1px solid #d2dcd6;
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgb(245 248 246 / 62%), transparent 24%),
    linear-gradient(90deg, rgb(40 77 58 / 4%) 1px, transparent 1px),
    linear-gradient(rgb(40 77 58 / 4%) 1px, transparent 1px),
    #edf2ef;
  background-size: auto, 42px 42px, 42px 42px, auto;
}

.falling-card {
  position: absolute;
  top: -150px;
  display: grid;
  width: 164px;
  min-height: 112px;
  align-content: start;
  gap: 7px;
  padding: 13px 14px;
  border: 1px solid #c4d1c9;
  border-radius: 8px;
  background: rgb(255 255 255 / 94%);
  color: #354d40;
  text-align: left;
  cursor: pointer;
  box-shadow: 0 12px 26px rgb(35 74 53 / 10%);
  animation-name: card-fall;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}

.falling-card .card-index {
  color: #8a978f;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 0.8rem;
}

.falling-card strong {
  font-size: 1rem;
}

.falling-card small {
  color: #7b8981;
  font-size: 0.67rem;
}

.falling-card em {
  justify-self: start;
  padding: 2px 6px;
  border-radius: 4px;
  background: #edf3ef;
  color: #476452;
  font-size: 0.64rem;
  font-style: normal;
  font-weight: 700;
}

.falling-card.current {
  border-color: #b7653f;
  background: #fff8f3;
  box-shadow: 0 14px 32px rgb(135 71 45 / 18%);
}

.falling-card.complete {
  opacity: 0.42;
}

.falling-card:focus-visible {
  outline: 3px solid rgb(45 113 83 / 28%);
  outline-offset: 2px;
}

.card-rain-stage.paused .falling-card {
  animation-play-state: paused;
}

.card-rain-stage.reduced-motion {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 10px;
  align-content: start;
  padding: 12px;
}

.card-rain-stage.reduced-motion .falling-card {
  position: static;
  width: auto;
  min-height: 0;
  animation: none;
}

@keyframes card-fall {
  from {
    transform: translateY(0) rotate(-1deg);
  }

  50% {
    transform: translateY(230px) rotate(1deg);
  }

  to {
    transform: translateY(500px) rotate(-0.5deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .card-rain-stage {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    gap: 10px;
    align-content: start;
    padding: 12px;
  }

  .falling-card {
    position: static;
    width: auto;
    min-height: 0;
    animation: none;
  }
}

@media (max-width: 720px) {
  .card-rain-stage {
    height: 280px;
  }
}
</style>
