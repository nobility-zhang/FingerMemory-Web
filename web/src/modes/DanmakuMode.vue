<script setup lang="ts">
import { computed } from 'vue';
import type { PracticeModeProps } from './types';

const props = defineProps<PracticeModeProps>();
const emit = defineEmits<{
  select: [index: number];
}>();

const activeItem = computed(() => props.items[props.currentIndex]);

function laneStyle(index: number): Record<string, string> {
  return {
    top: `${8 + (index % 5) * 17}%`,
    animationDuration: `${Math.round(22 / props.speed)}s`,
    animationDelay: `${-index * 3.1}s`,
  };
}
</script>

<template>
  <section class="danmaku-mode" aria-labelledby="danmaku-title">
    <header class="danmaku-heading">
      <div>
        <p class="mode-eyebrow">弹幕目标</p>
        <h2 id="danmaku-title">{{ activeItem?.wordEntry.meaning }}</h2>
      </div>
      <span class="position">{{ currentIndex + 1 }} / {{ queue.length }}</span>
    </header>

    <div
      class="danmaku-stage"
      :class="{
        paused,
        'reduced-motion': reduceMotion,
      }"
    >
      <div class="stage-axis" aria-hidden="true"></div>
      <button
        v-for="(item, index) in items"
        :key="item.studyItem.id"
        type="button"
        class="danmaku-bullet"
        :class="{
          current: index === currentIndex,
          complete: queue[index]?.status !== 'pending',
        }"
        :style="laneStyle(index)"
        :disabled="paused || queue[index]?.status !== 'pending'"
        :aria-current="index === currentIndex ? 'step' : undefined"
        @click="emit('select', index)"
      >
        <span>{{ item.wordEntry.meaning }}</span>
        <small>{{ item.wordEntry.term.length }} 字母</small>
        <em v-if="index === currentIndex">当前</em>
        <em v-else-if="queue[index]?.status === 'completed'">完成</em>
        <em v-else-if="queue[index]?.status === 'skipped'">跳过</em>
      </button>
    </div>
  </section>
</template>

<style scoped>
.danmaku-mode {
  min-width: 0;
}

.danmaku-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 12px;
}

.danmaku-heading h2 {
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

.danmaku-stage {
  position: relative;
  height: 310px;
  overflow: hidden;
  border: 1px solid #d2dcd6;
  border-radius: 8px;
  background:
    linear-gradient(90deg, rgb(240 244 241 / 96%), rgb(230 238 233 / 84%)),
    #eef3f0;
}

.stage-axis {
  position: absolute;
  top: 50%;
  right: 0;
  left: 0;
  border-top: 1px dashed rgb(62 98 80 / 18%);
}

.danmaku-bullet {
  position: absolute;
  left: -220px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 7px 12px;
  border: 1px solid #bfd0c6;
  border-radius: 999px;
  background: rgb(255 255 255 / 92%);
  color: #42594d;
  white-space: nowrap;
  cursor: pointer;
  animation-name: danmaku-drift;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}

.danmaku-bullet span {
  font-size: 0.85rem;
  font-weight: 700;
}

.danmaku-bullet small,
.danmaku-bullet em {
  color: #7a8981;
  font-size: 0.66rem;
  font-style: normal;
}

.danmaku-bullet em {
  padding: 2px 6px;
  border-radius: 999px;
  background: #edf3ef;
  color: #42614f;
  font-weight: 700;
}

.danmaku-bullet.current {
  z-index: 2;
  border-color: #b7653f;
  background: #fff8f3;
  color: #263d31;
  box-shadow: 0 8px 24px rgb(122 66 43 / 16%);
}

.danmaku-bullet.complete {
  opacity: 0.42;
}

.danmaku-bullet:focus-visible {
  outline: 3px solid rgb(45 113 83 / 28%);
  outline-offset: 2px;
}

.danmaku-stage.paused .danmaku-bullet {
  animation-play-state: paused;
}

.danmaku-stage.reduced-motion .danmaku-bullet {
  position: static;
  left: auto;
  animation: none;
  margin: 8px;
}

.danmaku-stage.reduced-motion {
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
  padding: 10px;
}

@keyframes danmaku-drift {
  from {
    transform: translateX(0);
  }

  to {
    transform: translateX(calc(100vw + 440px));
  }
}

@media (prefers-reduced-motion: reduce) {
  .danmaku-bullet {
    position: static;
    animation: none;
  }

  .danmaku-stage {
    display: flex;
    flex-wrap: wrap;
    align-content: flex-start;
    padding: 10px;
  }
}

@media (max-width: 720px) {
  .danmaku-stage {
    height: 270px;
  }
}
</style>
