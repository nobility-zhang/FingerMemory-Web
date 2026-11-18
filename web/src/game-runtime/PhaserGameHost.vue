<script setup lang="ts">
import {
  onBeforeUnmount,
  onMounted,
  ref,
  shallowRef,
  watch,
} from 'vue';
import type { GameBridgeEvent } from './eventBridge';
import {
  createPhaserAudioGame,
  type PhaserAudioGameController,
} from './PhaserAudioGame';
import type { GameSoundKey } from './soundCatalog';

const props = withDefaults(
  defineProps<{
    muted?: boolean;
    volume?: number;
    paused?: boolean;
    createGame?: typeof createPhaserAudioGame;
  }>(),
  {
    muted: false,
    volume: 1,
    paused: false,
    createGame: () => createPhaserAudioGame,
  },
);

const emit = defineEmits<{
  event: [event: GameBridgeEvent];
}>();

const container = ref<HTMLElement>();
const controller = shallowRef<PhaserAudioGameController>();
const runtimeState = ref<'starting' | 'ready' | 'error'>('starting');
const unlocked = ref(false);
const lastPlayedEventId = ref('');
const skippedCount = ref(0);
const runtimeError = ref('');
const playedEvents = ref<Array<{ soundKey: GameSoundKey; eventId: string }>>([]);
let generation = 0;
let disposed = false;
let unsubscribeBridge: (() => void) | undefined;
const AUDIO_EVENT_HISTORY_LIMIT = 24;

function handleBridgeEvent(event: GameBridgeEvent): void {
  if (event.type === 'game-ready') {
    runtimeState.value = runtimeError.value.length === 0 ? 'ready' : 'error';
  } else if (event.type === 'audio-unlocked') {
    unlocked.value = true;
  } else if (event.type === 'sound-played' && event.accepted) {
    lastPlayedEventId.value = event.eventId;
    playedEvents.value = [
      ...playedEvents.value.slice(-(AUDIO_EVENT_HISTORY_LIMIT - 1)),
      { soundKey: event.soundKey, eventId: event.eventId },
    ];
  } else if (event.type === 'sound-error') {
    runtimeState.value = 'error';
    runtimeError.value = event.message;
  } else if (event.type === 'sound-skipped') {
    skippedCount.value += 1;
  }
  emit('event', event);
}

async function start(): Promise<void> {
  if (disposed || controller.value !== undefined || container.value === undefined) {
    return;
  }
  const lifecycle = ++generation;
  try {
    const createGame = props.createGame ?? createPhaserAudioGame;
    const nextController = await createGame({
      parent: container.value,
    });
    if (disposed || lifecycle !== generation) {
      nextController.destroy();
      return;
    }
    unsubscribeBridge = nextController.bridge.on(handleBridgeEvent);
    nextController.setMuted(props.muted);
    nextController.setVolume(props.volume);
    nextController.setPaused(props.paused);
    controller.value = nextController;
    if (nextController.isReady()) {
      emit('event', { type: 'game-ready' });
    }
  } catch (error) {
    if (!disposed && lifecycle === generation) {
      runtimeState.value = 'error';
      runtimeError.value = error instanceof Error
        ? error.message
        : 'Phaser startup failed';
      emit('event', {
        type: 'sound-error',
        eventId: 'runtime-start',
        soundKey: 'flashcard-flip',
        message: runtimeError.value,
      });
    }
  }
}

function playFlip(eventId: string): void {
  if (props.muted) {
    return;
  }
  controller.value?.playFlip(eventId);
}

function playSound(
  soundKey: GameSoundKey,
  eventId: string,
  volume = 1,
): void {
  if (props.muted) {
    return;
  }
  controller.value?.playSound(soundKey, eventId, volume);
}

function setMuted(muted: boolean): void {
  controller.value?.setMuted(muted);
}

function setVolume(volume: number): void {
  controller.value?.setVolume(volume);
}

function setPaused(paused: boolean): void {
  controller.value?.setPaused(paused);
}

defineExpose({ playSound, playFlip, setMuted, setVolume, setPaused });

watch(
  () => props.muted,
  (muted) => {
    controller.value?.setMuted(muted);
  },
);

watch(
  () => props.volume,
  (volume) => {
    controller.value?.setVolume(volume);
  },
);

watch(
  () => props.paused,
  (paused) => {
    controller.value?.setPaused(paused);
  },
);

onMounted(() => {
  void start();
});

onBeforeUnmount(() => {
  disposed = true;
  generation += 1;
  unsubscribeBridge?.();
  unsubscribeBridge = undefined;
  controller.value?.destroy();
  controller.value = undefined;
});
</script>

<template>
  <div
    ref="container"
    class="phaser-game-host"
    aria-hidden="true"
    data-phaser-runtime="audio"
    :data-audio-status="runtimeState"
    :data-audio-unlocked="unlocked"
    :data-audio-muted="muted"
    :data-audio-volume="volume"
    :data-audio-paused="paused"
    :data-last-audio-event="lastPlayedEventId"
    :data-audio-events="JSON.stringify(playedEvents)"
    :data-audio-skipped-count="skippedCount"
    :data-audio-error="runtimeError"
  ></div>
</template>

<style scoped>
.phaser-game-host {
  width: 120px;
  height: 44px;
  overflow: hidden;
  opacity: 0.9;
  pointer-events: none;
}

.phaser-game-host :deep(canvas) {
  display: block;
  width: 100% !important;
  height: 100% !important;
}
</style>
