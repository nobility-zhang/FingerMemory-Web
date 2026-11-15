import type { Component } from 'vue';
import KeyboardMode from './KeyboardMode.vue';
import LyricsMode from './LyricsMode.vue';
import type {
  PracticeModeCapability,
  PracticeModeConfig,
  PracticeModeContext,
  PracticeModeInstance,
  TrustedPracticeModePlugin,
} from './types';

function assertConfig(config: PracticeModeConfig): void {
  if (!Number.isFinite(config.speed) || config.speed < 0.5 || config.speed > 2) {
    throw new TypeError('practice mode speed must be between 0.5 and 2');
  }
  if (typeof config.reduceMotion !== 'boolean') {
    throw new TypeError('practice mode reduceMotion must be a boolean');
  }
}

function createBuiltinPlugin(input: {
  id: TrustedPracticeModePlugin['manifest']['id'];
  label: string;
  capabilities: readonly PracticeModeCapability[];
  component: Component;
}): TrustedPracticeModePlugin {
  return {
    manifest: {
      id: input.id,
      label: input.label,
      version: '1.0.0',
      capabilities: input.capabilities,
      defaultConfig: { speed: 1, reduceMotion: false },
    },
    setup(context) {
      const unsubscribe = context.subscribe(() => undefined);
      return {
        component: input.component,
        start() {},
        stop() {},
        dispose() {
          unsubscribe();
        },
      };
    },
  };
}

export const TRUSTED_MODE_PLUGINS: readonly TrustedPracticeModePlugin[] = [
  createBuiltinPlugin({
    id: 'lyrics-scroll',
    label: '歌词滚动',
    capabilities: ['target-selection', 'animated-presentation'],
    component: LyricsMode,
  }),
  createBuiltinPlugin({
    id: 'keyboard-recall',
    label: '键盘对照',
    capabilities: ['keyboard-input'],
    component: KeyboardMode,
  }),
];

export class TrustedModeRegistry {
  private readonly plugins = new Map<string, TrustedPracticeModePlugin>();
  private readonly configs = new Map<string, PracticeModeConfig>();
  private active: { id: string; instance: PracticeModeInstance } | null = null;

  constructor(plugins: readonly TrustedPracticeModePlugin[]) {
    for (const plugin of plugins) {
      this.register(plugin);
    }
  }

  register(plugin: TrustedPracticeModePlugin): void {
    const { manifest } = plugin;
    if (manifest.id.trim().length === 0 || manifest.label.trim().length === 0) {
      throw new TypeError('practice mode id and label must not be empty');
    }
    if (manifest.version.trim().length === 0) {
      throw new TypeError('practice mode version must not be empty');
    }
    if (this.plugins.has(manifest.id)) {
      throw new Error(`Duplicate practice mode id: ${manifest.id}`);
    }
    if (new Set(manifest.capabilities).size !== manifest.capabilities.length) {
      throw new TypeError(`Practice mode ${manifest.id} has duplicate capabilities`);
    }
    assertConfig(manifest.defaultConfig);
    this.plugins.set(manifest.id, plugin);
    this.configs.set(manifest.id, { ...manifest.defaultConfig });
  }

  getAll(): readonly TrustedPracticeModePlugin[] {
    return [...this.plugins.values()];
  }

  get(id: string): TrustedPracticeModePlugin | undefined {
    return this.plugins.get(id);
  }

  getConfig(id: string): PracticeModeConfig {
    const config = this.configs.get(id);
    if (config === undefined) {
      throw new Error(`Unknown practice mode: ${id}`);
    }
    return { ...config };
  }

  configure(id: string, update: Partial<PracticeModeConfig>): PracticeModeConfig {
    const config = { ...this.getConfig(id), ...update };
    assertConfig(config);
    this.configs.set(id, config);
    return { ...config };
  }

  activate(id: string, context: PracticeModeContext): PracticeModeInstance {
    const plugin = this.plugins.get(id);
    if (plugin === undefined) {
      throw new Error(`Unknown practice mode: ${id}`);
    }
    if (this.active?.id === id) {
      return this.active.instance;
    }
    this.deactivate();
    const instance = plugin.setup(context);
    try {
      instance.start();
    } catch (error) {
      instance.dispose();
      throw error;
    }
    this.active = { id, instance };
    return instance;
  }

  deactivate(): void {
    const active = this.active;
    this.active = null;
    if (active === null) {
      return;
    }
    try {
      active.instance.stop();
    } finally {
      active.instance.dispose();
    }
  }
}

export function createTrustedModeRegistry(): TrustedModeRegistry {
  return new TrustedModeRegistry(TRUSTED_MODE_PLUGINS);
}
