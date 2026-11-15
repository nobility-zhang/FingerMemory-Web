<script setup lang="ts">
import { computed } from 'vue';
import type { PracticeModeProps } from './types';

const props = defineProps<PracticeModeProps>();
const emit = defineEmits<{
  select: [index: number];
}>();

const ITEM_HEIGHT = 84;

const trackStyle = computed(() => ({
  transform: `translateY(calc(50% - ${(props.currentIndex + 0.5) * ITEM_HEIGHT}px))`,
  transitionDuration: props.paused || props.reduceMotion ? '0ms' : `${Math.round(520 / props.speed)}ms`,
}));

const activeItem = computed(() => props.items[props.currentIndex]);
</script>

<template>
  <section class="lyrics-mode" aria-labelledby="lyrics-title">
    <header class="mode-heading">
      <div>
        <p class="mode-eyebrow">当前输入目标</p>
        <h2 id="lyrics-title">{{ activeItem?.wordEntry.meaning }}</h2>
      </div>
      <span class="position">{{ currentIndex + 1 }} / {{ queue.length }}</span>
    </header>

    <div class="lyrics-window" :class="{ paused }">
      <div class="lyrics-track" :style="trackStyle">
        <ol class="lyrics-list">
          <li v-for="(item, index) in items" :key="item.studyItem.id">
            <button
              type="button"
              class="lyric-line"
              :class="{
                current: index === currentIndex,
                complete: queue[index]?.status !== 'pending',
              }"
              :aria-current="index === currentIndex ? 'step' : undefined"
              :disabled="paused || queue[index]?.status !== 'pending' || queue.length === 0"
              @click="emit('select', index)"
            >
              <span class="line-index">{{ String(index + 1).padStart(2, '0') }}</span>
              <span class="line-content">
                <strong>{{ item.wordEntry.meaning }}</strong>
                <span>{{ item.wordEntry.term.length }} 个字母</span>
              </span>
              <span v-if="index === currentIndex" class="current-marker">正在输入</span>
              <span v-else-if="queue[index]?.status === 'completed'" class="line-state">已完成</span>
              <span v-else-if="queue[index]?.status === 'skipped'" class="line-state">已跳过</span>
            </button>
          </li>
        </ol>
      </div>
    </div>
  </section>
</template>

<style scoped>
.lyrics-mode {
  min-width: 0;
}

.mode-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 12px;
}

.mode-heading h2 {
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

.lyrics-window {
  height: 336px;
  overflow: hidden;
  border: 1px solid #d4ddd7;
  border-radius: 8px;
  background:
    linear-gradient(#f7faf8 0, transparent 18%),
    linear-gradient(to top, #f7faf8 0, transparent 18%),
    #edf2ef;
  mask-image: linear-gradient(to bottom, transparent, #000 15%, #000 85%, transparent);
}

.lyrics-window.paused {
  background-color: #eef1ef;
}

.lyrics-track {
  transition-property: transform;
  transition-timing-function: cubic-bezier(0.22, 0.72, 0.18, 1);
}

.lyrics-list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.lyric-line {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  width: min(100%, 760px);
  min-height: 76px;
  margin: 0 auto;
  padding: 12px 18px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #65736b;
  text-align: left;
  cursor: pointer;
}

.lyric-line:hover:not(:disabled) {
  border-color: #c2cec7;
  background: rgb(255 255 255 / 54%);
}

.lyric-line.current {
  border-color: #3e7260;
  background: #fff;
  color: #1d3026;
  box-shadow: 0 10px 30px rgb(31 70 53 / 10%);
}

.lyric-line.complete {
  opacity: 0.5;
}

.lyric-line:disabled {
  cursor: default;
}

.line-index {
  color: #8b9891;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 1.1rem;
}

.line-content {
  display: grid;
  gap: 4px;
}

.line-content strong {
  overflow-wrap: anywhere;
  font-size: 1.15rem;
  font-weight: 600;
}

.line-content span {
  color: #87938c;
  font-size: 0.7rem;
}

.current-marker,
.line-state {
  color: #3e7260;
  font-size: 0.68rem;
  font-weight: 700;
}

.line-state {
  color: #84928a;
}

@media (max-width: 720px) {
  .lyrics-window {
    height: 288px;
  }

  .lyric-line {
    grid-template-columns: 30px minmax(0, 1fr);
    padding: 10px 12px;
  }

  .current-marker,
  .line-state {
    grid-column: 2;
  }
}

@media (prefers-reduced-motion: reduce) {
  .lyrics-track {
    transition: none;
  }
}
</style>
