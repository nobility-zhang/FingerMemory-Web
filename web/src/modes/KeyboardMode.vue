<script setup lang="ts">
import { computed } from 'vue';
import type { PracticeModeProps } from './types';

const props = defineProps<PracticeModeProps>();
const activeItem = computed(() => props.items[props.currentIndex]);
</script>

<template>
  <section class="keyboard-mode" aria-labelledby="keyboard-mode-title">
    <p class="mode-eyebrow">键盘对照模式</p>
    <article class="keyboard-card">
      <span class="card-index">{{ currentIndex + 1 }} / {{ queue.length }}</span>
      <h2 id="keyboard-mode-title">{{ activeItem?.wordEntry.meaning }}</h2>
      <p>根据中文释义回忆英文拼写。</p>
      <span class="answer-length">{{ activeItem?.wordEntry.term.length }} 个字母</span>
    </article>
  </section>
</template>

<style scoped>
.mode-eyebrow {
  margin: 0 0 10px;
  color: #75837b;
  font-size: 0.72rem;
  font-weight: 700;
}

.keyboard-card {
  position: relative;
  min-height: 280px;
  padding: clamp(28px, 6vw, 58px);
  border: 1px solid #ccd7d0;
  border-radius: 8px;
  background: #fbfcfb;
}

.keyboard-card h2 {
  max-width: 18ch;
  margin: 44px 0 16px;
  color: #20352a;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 2.8rem;
  font-weight: 500;
  line-height: 1.15;
}

.keyboard-card p {
  max-width: 48ch;
  margin: 0 0 26px;
  color: #69776f;
  line-height: 1.7;
}

.card-index,
.answer-length {
  color: #849189;
  font-size: 0.72rem;
}

.card-index {
  position: absolute;
  top: 22px;
  right: 24px;
}

@media (max-width: 720px) {
  .keyboard-card h2 {
    margin-top: 36px;
    font-size: 2rem;
  }
}
</style>
