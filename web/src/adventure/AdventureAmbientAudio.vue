<script setup lang="ts">
import {
  computed,
  onBeforeUnmount,
  ref,
  watch,
} from 'vue';
import PhaserGameHost from '../game-runtime/PhaserGameHost.vue';
import type { GameBridgeEvent } from '../game-runtime/eventBridge';
import type { GameSoundKey } from '../game-runtime/soundCatalog';
import { usePhaserAudio } from '../game-runtime/usePhaserAudio';

const props = withDefaults(
  defineProps<{
    enabled?: boolean;
    ambientKey?: GameSoundKey;
    label?: string;
  }>(),
  {
    enabled: false,
    ambientKey: 'ambient-coast',
    label: '潮声',
  },
);

const audio = usePhaserAudio(computed(() => props.enabled));
const {
  muted: audioMuted,
  volume: audioVolume,
  paused: audioPaused,
  status: audioStatus,
  setHost: setAudioHost,
  toggleMuted: toggleAudioMuted,
  setVolume: setAudioVolume,
  setPaused: setAudioPaused,
  playAmbient,
  stopAmbient,
  handleRuntimeEvent: handleAudioRuntimeEvent,
} = audio;
const playingKey = ref<GameSoundKey>();
const lastEvent = ref('');

function handleHostEvent(event: GameBridgeEvent): void {
  if (event.type === 'ambient-started' || event.type === 'ambient-stopped') {
    lastEvent.value = event.eventId;
  }
  handleAudioRuntimeEvent(event);
}

function syncAmbient(): void {
  if (!props.enabled || audioStatus.value !== 'ready') {
    return;
  }
  if (playingKey.value !== undefined) {
    stopAmbient(`ambient-stop:${playingKey.value}`);
    playingKey.value = undefined;
  }
  if (props.ambientKey !== undefined) {
    playAmbient(
      props.ambientKey,
      `ambient-start:${props.ambientKey}`,
    );
    playingKey.value = props.ambientKey;
  }
}

watch(
  () => [props.enabled, props.ambientKey, audioStatus.value] as const,
  syncAmbient,
  { immediate: true },
);

onBeforeUnmount(() => {
  if (playingKey.value !== undefined) {
    stopAmbient(`ambient-stop:${playingKey.value}`);
  }
  playingKey.value = undefined;
});
</script>

<template>
  <section
    v-if="enabled"
    class="adventure-ambient"
    data-ambient-controls
    :data-ambient-key="ambientKey"
    :data-ambient-status="audioStatus"
    :data-ambient-muted="audioMuted"
    :data-ambient-paused="audioPaused"
    :data-last-ambient-event="lastEvent"
  >
    <PhaserGameHost
      :ref="setAudioHost"
      :muted="audioMuted"
      :volume="audioVolume"
      :paused="audioPaused"
      @event="handleHostEvent"
    />
    <span class="ambient-label">{{ label }}</span>
    <button
      type="button"
      :aria-label="audioMuted ? '打开潮声音景' : '静音潮声音景'"
      :title="audioMuted ? '打开潮声音景' : '静音潮声音景'"
      @click="toggleAudioMuted"
    >
      {{ audioMuted ? '静音中' : '海潮' }}
    </button>
    <label>
      <span>潮声音量</span>
      <input
        type="range"
        min="0"
        max="1"
        step="0.05"
        :value="audioVolume"
        aria-label="潮声音量"
        @input="setAudioVolume(Number(($event.target as HTMLInputElement).value))"
      />
    </label>
    <button
      type="button"
      :aria-label="audioPaused ? '继续潮声音景' : '暂停潮声音景'"
      :title="audioPaused ? '继续潮声音景' : '暂停潮声音景'"
      @click="setAudioPaused(!audioPaused)"
    >
      {{ audioPaused ? '继续' : '暂停' }}
    </button>
    <span v-if="audioStatus === 'error'" class="ambient-error" role="status">
      音景不可用
    </span>
  </section>
</template>

<style scoped>
.adventure-ambient {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: #d8e7df;
  font-size: 0.76rem;
}

.adventure-ambient :deep(.phaser-game-host) {
  width: 72px;
  height: 28px;
}

.ambient-label {
  color: #b8d8d1;
}

.adventure-ambient button {
  min-height: 30px;
  border: 1px solid rgb(172 213 204 / 50%);
  border-radius: 4px;
  background: rgb(17 44 48 / 62%);
  color: #e3f0e9;
  cursor: pointer;
}

.adventure-ambient label {
  display: flex;
  align-items: center;
  gap: 6px;
}

.adventure-ambient input {
  width: 76px;
}

.ambient-error {
  color: #ffd1be;
}
</style>
