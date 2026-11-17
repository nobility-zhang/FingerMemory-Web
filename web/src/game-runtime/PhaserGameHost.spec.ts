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
    const destroy = vi.fn();
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
      setMuted,
      destroy,
    };
    const createGame = vi.fn(async () => controller);
    let host: InstanceType<typeof PhaserGameHost> | undefined;
    const muted = ref(false);
    const Host = defineComponent({
      setup() {
        return () => h(PhaserGameHost, {
          ref: (value) => {
            host = value as InstanceType<typeof PhaserGameHost> | undefined;
          },
          createGame,
          muted: muted.value,
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
    await nextTick();

    expect(playFlip).toHaveBeenCalledWith('flip-1');
    expect(events).toEqual([
      { type: 'game-ready' },
      {
        type: 'sound-played',
        eventId: 'flip-1',
        soundKey: 'flashcard-flip',
        accepted: true,
      },
    ]);

    muted.value = true;
    await nextTick();
    expect(setMuted).toHaveBeenLastCalledWith(true);

    app.unmount();
    mountedApp = null;
    expect(destroy).toHaveBeenCalledOnce();
    expect(createGame).toHaveBeenCalledOnce();
  });
});
