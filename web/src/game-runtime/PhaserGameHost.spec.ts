import { createApp, defineComponent, h, nextTick, ref } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import PhaserGameHost from './PhaserGameHost.vue';
import type {
  PhaserAudioGameController,
} from './PhaserAudioGame';
import {
  GameEventBridge,
  type GameBridgeEvent,
} from './eventBridge';
import type { GameSoundKey } from './soundCatalog';

let mountedApp: ReturnType<typeof createApp> | null = null;

afterEach(() => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
});

describe('PhaserGameHost', () => {
  it('creates one Game, forwards bridge events and destroys it on unmount', async () => {
    const bridge = new GameEventBridge();
    const events: GameBridgeEvent[] = [];
    const setMuted = vi.fn();
    const setVolume = vi.fn();
    const setPaused = vi.fn();
    const destroy = vi.fn();
    const playSound = vi.fn(
      (soundKey: GameSoundKey, eventId: string) => {
        bridge.emit({
          type: 'sound-played',
          eventId,
          soundKey,
          accepted: true,
        });
      },
    );
    const playFlip = vi.fn((eventId: string) => {
      bridge.emit({
        type: 'sound-played',
        eventId,
        soundKey: 'flashcard-flip',
        accepted: true,
      });
    });
    const controller: PhaserAudioGameController = {
      bridge,
      isReady: () => false,
      playFlip,
      playSound,
      setMuted,
      setVolume,
      setPaused,
      destroy,
    };
    const createGame = vi.fn(async () => controller);
    let host: InstanceType<typeof PhaserGameHost> | undefined;
    const muted = ref(false);
    const volume = ref(1);
    const paused = ref(false);
    const Host = defineComponent({
      setup() {
        return () => h(PhaserGameHost, {
          ref: (value) => {
            host = value as InstanceType<typeof PhaserGameHost> | undefined;
          },
          createGame,
          muted: muted.value,
          volume: volume.value,
          paused: paused.value,
          onEvent: (event: GameBridgeEvent) => events.push(event),
        });
      },
    });
    const root = document.createElement('div');
    document.body.append(root);
    const app = createApp(Host);
    mountedApp = app;
    app.mount(root);

    await vi.waitFor(() => {
      expect(createGame).toHaveBeenCalledOnce();
      expect(host).toBeDefined();
    });
    bridge.emit({ type: 'game-ready' });
    host!.playFlip('flip-1');
    host!.playSound('battle-hit', 'hit-1');
    await nextTick();

    expect(playFlip).toHaveBeenCalledWith('flip-1');
    expect(playSound).toHaveBeenCalledWith('battle-hit', 'hit-1', 1);
    expect(events).toEqual([
      { type: 'game-ready' },
      {
        type: 'sound-played',
        eventId: 'flip-1',
        soundKey: 'flashcard-flip',
        accepted: true,
      },
      {
        type: 'sound-played',
        eventId: 'hit-1',
        soundKey: 'battle-hit',
        accepted: true,
      },
    ]);

    muted.value = true;
    volume.value = 0.6;
    paused.value = true;
    await nextTick();
    expect(setMuted).toHaveBeenLastCalledWith(true);
    expect(setVolume).toHaveBeenLastCalledWith(0.6);
    expect(setPaused).toHaveBeenLastCalledWith(true);

    app.unmount();
    mountedApp = null;
    expect(destroy).toHaveBeenCalledOnce();
    expect(createGame).toHaveBeenCalledOnce();
  });

  it('reports startup failure without attempting audio playback', async () => {
    const events: GameBridgeEvent[] = [];
    const createGame = vi.fn(async (): Promise<PhaserAudioGameController> => {
      throw new Error('audio runtime unavailable');
    });
    const Host = defineComponent({
      setup() {
        return () => h(PhaserGameHost, {
          createGame,
          onEvent: (event: GameBridgeEvent) => events.push(event),
        });
      },
    });
    const root = document.createElement('div');
    document.body.append(root);
    const app = createApp(Host);
    mountedApp = app;
    app.mount(root);

    await vi.waitFor(() => {
      expect(root.querySelector('[data-audio-status="error"]')).not.toBeNull();
    });
    const host = root.querySelector<HTMLElement>('[data-phaser-runtime="audio"]');
    expect(host?.dataset.audioError).toBe('audio runtime unavailable');
    expect(events).toContainEqual({
      type: 'sound-error',
      eventId: 'runtime-start',
      soundKey: 'flashcard-flip',
      message: 'audio runtime unavailable',
    });
  });

  it('keeps resource failures visible after the game becomes ready', async () => {
    const bridge = new GameEventBridge();
    const events: GameBridgeEvent[] = [];
    const controller: PhaserAudioGameController = {
      bridge,
      isReady: () => false,
      playFlip: vi.fn(),
      playSound: vi.fn(),
      setMuted: vi.fn(),
      setVolume: vi.fn(),
      setPaused: vi.fn(),
      destroy: vi.fn(),
    };
    const createGame = vi.fn(async () => controller);
    const Host = defineComponent({
      setup() {
        return () => h(PhaserGameHost, {
          createGame,
          onEvent: (event: GameBridgeEvent) => events.push(event),
        });
      },
    });
    const root = document.createElement('div');
    document.body.append(root);
    const app = createApp(Host);
    mountedApp = app;
    app.mount(root);

    await vi.waitFor(() => {
      expect(createGame).toHaveBeenCalledOnce();
      expect(root.querySelector('[data-phaser-runtime="audio"]')).not.toBeNull();
    });
    bridge.emit({
      type: 'sound-error',
      eventId: 'load:battle-heal',
      soundKey: 'battle-heal',
      message: 'Audio resource failed to load: battle-heal.wav',
    });
    bridge.emit({ type: 'game-ready' });
    await nextTick();

    const runtime = root.querySelector<HTMLElement>(
      '[data-phaser-runtime="audio"]',
    );
    expect(runtime?.dataset.audioStatus).toBe('error');
    expect(runtime?.dataset.audioError).toBe(
      'Audio resource failed to load: battle-heal.wav',
    );
    expect(events.at(-1)).toEqual({ type: 'game-ready' });
  });
});
