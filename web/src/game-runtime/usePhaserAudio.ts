import {
  ref,
  type Ref,
} from 'vue';
import {
  loadAudioPreferences,
  saveAudioPreferences,
} from '../audio/audioPreferences';
import type { GameBridgeEvent } from './eventBridge';

export type PhaserAudioStatus = 'idle' | 'starting' | 'ready' | 'error';

export interface PhaserAudioHostHandle {
  playFlip(eventId: string): void;
  setMuted(muted: boolean): void;
}

export function usePhaserAudio(enabled: Ref<boolean>) {
  const initial = loadAudioPreferences();
  const host = ref<PhaserAudioHostHandle>();
  const muted = ref(initial.muted);
  const status = ref<PhaserAudioStatus>(enabled.value ? 'starting' : 'idle');
  const error = ref<string | null>(null);
  const lastPlayedEventId = ref<string | null>(null);

  function toggleMuted(): void {
    muted.value = !muted.value;
    saveAudioPreferences({
      muted: muted.value,
      volume: initial.volume,
    });
    host.value?.setMuted(muted.value);
  }

  function setHost(value: unknown): void {
    if (
      typeof value === 'object'
      && value !== null
      && 'playFlip' in value
      && typeof value.playFlip === 'function'
      && 'setMuted' in value
      && typeof value.setMuted === 'function'
    ) {
      host.value = value as PhaserAudioHostHandle;
      return;
    }
    host.value = undefined;
  }

  function playFlip(eventId: string): void {
    if (!enabled.value || muted.value) {
      return;
    }
    host.value?.playFlip(eventId);
  }

  function handleRuntimeEvent(event: GameBridgeEvent): void {
    if (event.type === 'game-ready') {
      status.value = 'ready';
      error.value = null;
      return;
    }
    if (event.type === 'sound-played') {
      if (event.accepted) {
        lastPlayedEventId.value = event.eventId;
      }
      return;
    }
    if (event.type === 'sound-error') {
      status.value = 'error';
      error.value = event.message;
    }
  }

  return {
    host,
    setHost,
    muted,
    status,
    error,
    lastPlayedEventId,
    toggleMuted,
    playFlip,
    handleRuntimeEvent,
  };
}
