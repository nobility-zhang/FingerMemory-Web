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

const props = withDefaults(
  defineProps<{
    muted?: boolean;
    createGame?: typeof createPhaserAudioGame;
  }>(),
  {
    muted: false,
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
const runtimeError = ref('');
let generation = 0;
let disposed = false;
let unsubscribeBridge: (() => void) | undefined;

function handleBridgeEvent(event: GameBridgeEvent): void {
  if (event.type === 'game-ready') {
    runtimeState.value = 'ready';
  } else if (event.type === 'audio-unlocked') {
    unlocked.value = true;
  } else if (event.type === 'sound-played' && event.accepted) {
    lastPlayedEventId.value = event.eventId;
  } else if (event.type === 'sound-error') {
    runtimeState.value = 'error';
    runtimeError.value = event.message;
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

function setMuted(muted: boolean): void {
  controller.value?.setMuted(muted);
}

defineExpose({ playFlip, setMuted });

watch(
  () => props.muted,
  (muted) => {
    controller.value?.setMuted(muted);
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
    :data-last-audio-event="lastPlayedEventId"
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
