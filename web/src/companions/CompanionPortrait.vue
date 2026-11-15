<script setup lang="ts">
import { computed } from 'vue';
import type {
  CompanionExpression,
  CompanionProfile,
} from './catalog';

const props = withDefaults(
  defineProps<{
    companion: CompanionProfile;
    expression?: CompanionExpression;
    reduceMotion?: boolean;
  }>(),
  {
    expression: 'neutral',
    reduceMotion: false,
  },
);

const source = computed(
  () => props.companion.portraits[props.expression],
);
const alternativeText = computed(
  () => `${props.companion.name}，${props.companion.epithet}的全景立绘`,
);
</script>

<template>
  <figure
    class="companion-portrait"
    :class="[`expression-${expression}`, { 'reduce-motion': reduceMotion }]"
    :data-companion="companion.id"
    :data-expression="expression"
  >
    <img :src="source" :alt="alternativeText" draggable="false" />
  </figure>
</template>

<style scoped>
.companion-portrait {
  width: min(100%, 480px);
  height: 100%;
  min-height: 0;
  margin: 0;
  animation: companion-arrive 480ms ease-out both;
}

.companion-portrait img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center bottom;
  filter:
    drop-shadow(0 22px 30px rgb(38 21 17 / 36%))
    drop-shadow(0 0 20px rgb(255 197 113 / 10%));
  user-select: none;
}

.reduce-motion {
  animation: none;
}

.reduce-motion img {
  filter: drop-shadow(0 16px 20px rgb(38 21 17 / 28%));
}

@keyframes companion-arrive {
  from {
    opacity: 0;
    transform: translateY(12px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .companion-portrait {
    animation: none;
  }
}
</style>
