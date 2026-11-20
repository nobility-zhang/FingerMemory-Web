import { defineComponent } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import {
  TrustedModeRegistry,
  TRUSTED_MODE_PLUGINS,
  createTrustedModeRegistry,
} from './registry';
import type {
  PracticeModeContext,
  TrustedPracticeModePlugin,
} from './types';

const DummyMode = defineComponent({
  render: () => null,
});

function createContext(): PracticeModeContext {
  const session = {
    id: 'session-plugin',
    modeId: 'keyboard-recall' as const,
    status: 'active' as const,
    queue: [],
    currentIndex: 0,
    currentStudyItemId: null,
    interactions: {},
    pendingAnswer: null,
    speed: 1,
    reduceMotion: false,
    createdAt: 1,
    updatedAt: 1,
    pausedAt: null,
    completedAt: null,
  };
  return {
    sessionId: session.id,
    getSession: () => session,
    getItems: () => [],
    subscribe: vi.fn(() => vi.fn()),
    commands: {
      selectTarget: vi.fn(async () => undefined),
      recordPractice: vi.fn(),
      submitReview: vi.fn(),
    } as unknown as PracticeModeContext['commands'],
  };
}

function createPlugin(
  id: 'flashcard' | 'keyboard-recall',
  calls: string[],
): TrustedPracticeModePlugin {
  return {
    manifest: {
      id,
      label: `Mode ${id}`,
      version: '1.0.0',
      capabilities: ['target-selection'],
      defaultConfig: { speed: 1, reduceMotion: false },
    },
    setup(context) {
      const unsubscribe = context.subscribe(() => undefined);
      return {
        component: DummyMode,
        start: () => calls.push(`${id}:start`),
        stop: () => calls.push(`${id}:stop`),
        dispose: () => {
          calls.push(`${id}:dispose`);
          unsubscribe();
        },
      };
    },
  };
}

describe('TrustedModeRegistry', () => {
  it('registers the built-in trusted plugins with manifests and defaults', () => {
    expect(TRUSTED_MODE_PLUGINS.map((plugin) => plugin.manifest)).toMatchObject([
      {
        id: 'keyboard-recall',
        version: '1.0.0',
        defaultConfig: { speed: 1, reduceMotion: false },
      },
    ]);
  });

  it('rejects duplicate ids and invalid configuration', () => {
    const calls: string[] = [];
    const plugin = createPlugin('keyboard-recall', calls);
    const registry = new TrustedModeRegistry([plugin]);

    expect(() => registry.register(plugin)).toThrow('Duplicate practice mode id');
    expect(() => registry.configure('keyboard-recall', { speed: 9 })).toThrow(
      'speed must be between',
    );
    expect(registry.getConfig('keyboard-recall')).toEqual({
      speed: 1,
      reduceMotion: false,
    });
  });

  it('starts one plugin at a time and disposes the previous subscriptions', () => {
    const calls: string[] = [];
    const registry = new TrustedModeRegistry([
      createPlugin('flashcard', calls),
      createPlugin('keyboard-recall', calls),
    ]);

    registry.activate('flashcard', createContext());
    registry.activate('keyboard-recall', createContext());
    registry.deactivate();

    expect(calls).toEqual([
      'flashcard:start',
      'flashcard:stop',
      'flashcard:dispose',
      'keyboard-recall:start',
      'keyboard-recall:stop',
      'keyboard-recall:dispose',
    ]);
  });

  it('disposes a partially started plugin when start fails', () => {
    const calls: string[] = [];
    const plugin = createPlugin('keyboard-recall', calls);
    plugin.setup = (context) => {
      const instance = createPlugin('keyboard-recall', calls).setup(context);
      instance.start = () => {
        throw new Error('start failed');
      };
      return instance;
    };
    const registry = new TrustedModeRegistry([plugin]);

    expect(() => registry.activate('keyboard-recall', createContext())).toThrow(
      'start failed',
    );
    expect(calls).toContain('keyboard-recall:dispose');
  });

  it('does not expose retired practice modes for new sessions', () => {
    const registry = createTrustedModeRegistry();

    expect(registry.get('lyrics-scroll')).toBeUndefined();
    expect(registry.get('danmaku-stream')).toBeUndefined();
    expect(registry.get('card-rain')).toBeUndefined();
  });
});
