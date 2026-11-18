import { createApp, defineComponent, h, ref } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { loadAudioPreferences } from '../audio/audioPreferences';
import { usePhaserAudio } from './usePhaserAudio';

let mountedApp: ReturnType<typeof createApp> | null = null;

afterEach(() => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
  localStorage.clear();
});

describe('usePhaserAudio', () => {
  it('owns volume, mute, pause, skip diagnostics and failure state', async () => {
    let audio: ReturnType<typeof usePhaserAudio> | undefined;
    const Harness = defineComponent({
      setup() {
        audio = usePhaserAudio(ref(true));
        return () => h('div');
      },
    });
    mountedApp = createApp(Harness);
    mountedApp.mount(document.createElement('div'));

    const host = {
      playSound: vi.fn(),
      playFlip: vi.fn(),
      setMuted: vi.fn(),
      setVolume: vi.fn(),
      setPaused: vi.fn(),
    };
    audio!.setHost(host);
    audio!.playSound('battle-hit', 'hit-1', 0.5);
    expect(host.playSound).toHaveBeenCalledWith('battle-hit', 'hit-1', 0.5);

    audio!.setVolume(0.2);
    expect(host.setVolume).toHaveBeenLastCalledWith(0.2);
    expect(loadAudioPreferences().volume).toBe(0.2);

    audio!.setPaused(true);
    expect(host.setPaused).toHaveBeenLastCalledWith(true);

    audio!.handleRuntimeEvent({
      type: 'sound-skipped',
      eventId: 'skip-1',
      soundKey: 'battle-hit',
      reason: 'rate-limit',
    });
    expect(audio!.rateLimitedCount.value).toBe(1);
    expect(audio!.lastSkippedEventId.value).toBe('skip-1');

    audio!.handleRuntimeEvent({
      type: 'sound-error',
      eventId: 'error-1',
      soundKey: 'battle-heal',
      message: 'decode failed',
    });
    expect(audio!.status.value).toBe('error');
    expect(audio!.error.value).toBe('decode failed');
    audio!.handleRuntimeEvent({ type: 'game-ready' });
    expect(audio!.status.value).toBe('error');
    expect(audio!.error.value).toBe('decode failed');

    audio!.toggleMuted();
    audio!.playSound('battle-play', 'play-1');
    expect(host.playSound).toHaveBeenCalledTimes(1);
    expect(loadAudioPreferences()).toEqual({ muted: true, volume: 0.2 });
  });
});
