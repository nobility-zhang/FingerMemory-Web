import {
  onBeforeUnmount,
  onMounted,
  ref,
  type Ref,
} from 'vue';
import {
  loadAudioPreferences,
  saveAudioPreferences,
} from '../audio/audioPreferences';
import type { GameBridgeEvent } from './eventBridge';
import type { GameSoundKey } from './soundCatalog';

export type PhaserAudioStatus = 'idle' | 'starting' | 'ready' | 'error';

export interface PhaserAudioHostHandle {
  playSound(soundKey: GameSoundKey, eventId: string, volume?: number): void;
  playFlip(eventId: string): void;
  setMuted(muted: boolean): void;
  setVolume(volume: number): void;
  setPaused(paused: boolean): void;
}

export function usePhaserAudio(enabled: Ref<boolean>) {
  const initial = loadAudioPreferences();
  const host = ref<PhaserAudioHostHandle>();
  const muted = ref(initial.muted);
  const volume = ref(initial.volume);
  const paused = ref(false);
  const status = ref<PhaserAudioStatus>(enabled.value ? 'starting' : 'idle');
  const error = ref<string | null>(null);
  const lastPlayedEventId = ref<string | null>(null);
  const lastSkippedEventId = ref<string | null>(null);
  const rateLimitedCount = ref(0);

  function persistPreferences(): void {
    saveAudioPreferences({
      muted: muted.value,
      volume: volume.value,
    });
  }

  function toggleMuted(): void {
    muted.value = !muted.value;
    persistPreferences();
    host.value?.setMuted(muted.value);
  }

  function setVolume(nextVolume: number): void {
    volume.value = Math.min(1, Math.max(0, nextVolume));
    persistPreferences();
    host.value?.setVolume(volume.value);
  }

  function setPaused(nextPaused: boolean): void {
    paused.value = nextPaused;
    host.value?.setPaused(nextPaused);
  }

  function setHost(value: unknown): void {
    if (
      typeof value === 'object'
      && value !== null
      && 'playSound' in value
      && typeof value.playSound === 'function'
      && 'playFlip' in value
      && typeof value.playFlip === 'function'
      && 'setMuted' in value
      && typeof value.setMuted === 'function'
      && 'setVolume' in value
      && typeof value.setVolume === 'function'
      && 'setPaused' in value
      && typeof value.setPaused === 'function'
    ) {
      host.value = value as PhaserAudioHostHandle;
      host.value.setMuted(muted.value);
      host.value.setVolume(volume.value);
      host.value.setPaused(paused.value);
      return;
    }
    host.value = undefined;
  }

  function playSound(
    soundKey: GameSoundKey,
    eventId: string,
    volumeScale = 1,
  ): void {
    if (!enabled.value || muted.value) {
      return;
    }
    host.value?.playSound(soundKey, eventId, volumeScale);
  }

  function playFlip(eventId: string): void {
    playSound('flashcard-flip', eventId);
  }

  function handleRuntimeEvent(event: GameBridgeEvent): void {
    if (event.type === 'game-ready') {
      if (status.value !== 'error') {
        status.value = 'ready';
      }
      return;
    }
    if (event.type === 'sound-played') {
      if (event.accepted) {
        lastPlayedEventId.value = event.eventId;
      }
      return;
    }
    if (event.type === 'sound-skipped') {
      lastSkippedEventId.value = event.eventId;
      if (event.reason === 'rate-limit') {
        rateLimitedCount.value += 1;
      }
      return;
    }
    if (event.type === 'sound-error') {
      status.value = 'error';
      error.value = event.message;
    }
  }

  function handleVisibilityChange(): void {
    if (enabled.value) {
      setPaused(document.visibilityState === 'hidden');
    }
  }

  onMounted(() => {
    document.addEventListener('visibilitychange', handleVisibilityChange);
    handleVisibilityChange();
  });

  onBeforeUnmount(() => {
    document.removeEventListener('visibilitychange', handleVisibilityChange);
  });

  return {
    host,
    setHost,
    muted,
    volume,
    paused,
    status,
    error,
    lastPlayedEventId,
    lastSkippedEventId,
    rateLimitedCount,
    toggleMuted,
    setVolume,
    setPaused,
    playSound,
    playFlip,
    handleRuntimeEvent,
  };
}
