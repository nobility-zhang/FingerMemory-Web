import { defineComponent } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import { TrustedModeRegistry, TRUSTED_MODE_PLUGINS } from './registry';
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
    modeId: 'lyrics-scroll' as const,
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
  id: 'lyrics-scroll' | 'keyboard-recall',
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
        id: 'lyrics-scroll',
        version: '1.0.0',
        defaultConfig: { speed: 1, reduceMotion: false },
      },
      {
        id: 'keyboard-recall',
        version: '1.0.0',
        defaultConfig: { speed: 1, reduceMotion: false },
      },
      {
        id: 'danmaku-stream',
        version: '1.0.0',
        defaultConfig: { speed: 1, reduceMotion: false },
      },
    ]);
  });

  it('rejects duplicate ids and invalid configuration', () => {
    const calls: string[] = [];
    const plugin = createPlugin('lyrics-scroll', calls);
    const registry = new TrustedModeRegistry([plugin]);

    expect(() => registry.register(plugin)).toThrow('Duplicate practice mode id');
    expect(() => registry.configure('lyrics-scroll', { speed: 9 })).toThrow(
      'speed must be between',
    );
    expect(registry.getConfig('lyrics-scroll')).toEqual({
      speed: 1,
      reduceMotion: false,
    });
  });

  it('starts one plugin at a time and disposes the previous subscriptions', () => {
    const calls: string[] = [];
    const registry = new TrustedModeRegistry([
      createPlugin('lyrics-scroll', calls),
      createPlugin('keyboard-recall', calls),
    ]);

    registry.activate('lyrics-scroll', createContext());
    registry.activate('keyboard-recall', createContext());
    registry.deactivate();

    expect(calls).toEqual([
      'lyrics-scroll:start',
      'lyrics-scroll:stop',
      'lyrics-scroll:dispose',
      'keyboard-recall:start',
      'keyboard-recall:stop',
      'keyboard-recall:dispose',
    ]);
  });

  it('disposes a partially started plugin when start fails', () => {
    const calls: string[] = [];
    const plugin = createPlugin('lyrics-scroll', calls);
    plugin.setup = (context) => {
      const instance = createPlugin('lyrics-scroll', calls).setup(context);
      instance.start = () => {
        throw new Error('start failed');
      };
      return instance;
    };
    const registry = new TrustedModeRegistry([plugin]);

    expect(() => registry.activate('lyrics-scroll', createContext())).toThrow(
      'start failed',
    );
    expect(calls).toContain('lyrics-scroll:dispose');
  });
});
