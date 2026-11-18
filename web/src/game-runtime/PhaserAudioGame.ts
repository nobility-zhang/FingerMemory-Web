import {
  FLASHCARD_FLIP_SOUND_KEY,
  GameEventBridge,
  type GameBridgeEvent,
} from './eventBridge';
import {
  GAME_SOUND_BASE_VOLUME,
  GAME_SOUND_KEYS,
  type GameSoundKey,
} from './soundCatalog';
import { GameSoundRateLimiter } from './soundRateLimiter';

export const SOUND_URLS: Readonly<Record<GameSoundKey, string>> = {
  'flashcard-flip': new URL(
    '../assets/audio/flashcard-flip.wav',
    import.meta.url,
  ).href,
  'battle-draw': new URL('../assets/audio/battle-draw.wav', import.meta.url).href,
  'battle-play': new URL('../assets/audio/battle-play.wav', import.meta.url).href,
  'battle-hit': new URL('../assets/audio/battle-hit.wav', import.meta.url).href,
  'battle-block': new URL('../assets/audio/battle-block.wav', import.meta.url).href,
  'battle-heal': new URL('../assets/audio/battle-heal.wav', import.meta.url).href,
  'battle-energy': new URL('../assets/audio/battle-energy.wav', import.meta.url).href,
  'battle-enemy-turn': new URL(
    '../assets/audio/battle-enemy-turn.wav',
    import.meta.url,
  ).href,
  'battle-victory': new URL(
    '../assets/audio/battle-victory.wav',
    import.meta.url,
  ).href,
  'battle-rescue': new URL(
    '../assets/audio/battle-rescue.wav',
    import.meta.url,
  ).href,
  'ambient-coast': new URL(
    '../assets/audio/ambient-coast.wav',
    import.meta.url,
  ).href,
};

const MAX_ACTIVE_SOUNDS = 8;

export interface PhaserAudioGameController {
  bridge: GameEventBridge;
  isReady(): boolean;
  getLastSoundError(): Extract<GameBridgeEvent, { type: 'sound-error' }> | null;
  playSound(soundKey: GameSoundKey, eventId: string, volume?: number): void;
  playAmbient(soundKey: GameSoundKey, eventId: string, volume?: number): void;
  stopAmbient(eventId: string): void;
  playFlip(eventId: string): void;
  setMuted(muted: boolean): void;
  setVolume(volume: number): void;
  setPaused(paused: boolean): void;
  destroy(): void;
}

export interface PhaserAudioGameInput {
  parent: HTMLElement;
}

interface PendingPlay {
  soundKey: GameSoundKey;
  eventId: string;
  volume: number;
}

interface PendingAmbient {
  soundKey: GameSoundKey;
  eventId: string;
  volume: number;
}

function clampVolume(volume: number): number {
  return Math.min(1, Math.max(0, volume));
}

export async function createPhaserAudioGame(
  input: PhaserAudioGameInput,
): Promise<PhaserAudioGameController> {
  const Phaser = await import('phaser');
  const bridge = new GameEventBridge();
  let gameReady = false;
  let pendingUnlockEventId: string | null = null;
  let pendingPlay: PendingPlay | null = null;
  let pendingAmbient: PendingAmbient | null = null;
  let muted = false;
  let paused = false;
  let volume = 1;
  let mutedBeforeReady = false;
  let lastSoundError:
    Extract<GameBridgeEvent, { type: 'sound-error' }> | null = null;
  const rateLimiter = new GameSoundRateLimiter();

  function reportSoundError(
    eventId: string,
    soundKey: GameSoundKey,
    error: unknown,
  ): void {
    lastSoundError = {
      type: 'sound-error',
      eventId,
      soundKey,
      message: error instanceof Error ? error.message : 'unknown audio error',
    };
    bridge.emit(lastSoundError);
  }

  const unsubscribeBridge = bridge.on((event) => {
    if (event.type !== 'game-ready') {
      return;
    }
    gameReady = true;
    bridge.emit({ type: 'audio-mute', muted: mutedBeforeReady || muted });
    bridge.emit({ type: 'audio-volume', volume });
    bridge.emit({ type: 'audio-pause', paused });
    if (pendingUnlockEventId !== null) {
      bridge.emit({
        type: 'audio-unlock',
        eventId: pendingUnlockEventId,
      });
      pendingUnlockEventId = null;
    }
    if (pendingPlay !== null) {
      bridge.emit({
        type: 'audio-play',
        eventId: pendingPlay.eventId,
        soundKey: pendingPlay.soundKey,
        volume: pendingPlay.volume,
      });
      pendingPlay = null;
    }
    if (pendingAmbient !== null) {
      bridge.emit({
        type: 'audio-ambient-play',
        eventId: pendingAmbient.eventId,
        soundKey: pendingAmbient.soundKey,
        volume: pendingAmbient.volume,
      });
      pendingAmbient = null;
    }
  });

  const AudioScene = class extends Phaser.Scene {
    private rune?: Phaser.GameObjects.Arc;
    private stopBridge?: () => void;
    private activeSounds: unknown[] = [];
    private ambientSound: unknown;

    private stopAmbientSound(): void {
      if (this.ambientSound === undefined || this.ambientSound === null) {
        return;
      }
      const stop = Reflect.get(this.ambientSound, 'stop');
      if (typeof stop === 'function') {
        Reflect.apply(stop, this.ambientSound, []);
      }
      this.ambientSound = undefined;
    }

    private handleLoadError = (file: { key?: string; src?: string }): void => {
      const soundKey = file.key;
      if (soundKey === undefined || !(soundKey in SOUND_URLS)) {
        return;
      }
      reportSoundError(
        `load:${soundKey}`,
        soundKey as GameSoundKey,
        new Error(`Audio resource failed to load: ${file.src ?? soundKey}`),
      );
    };

    private flashRune = (): void => {
      if (this.rune === undefined || !this.rune.active) {
        return;
      }
      this.tweens.killTweensOf(this.rune);
      this.rune.setScale(1).setAlpha(0.92);
      this.tweens.add({
        targets: this.rune,
        scale: 1.9,
        alpha: 0,
        duration: 190,
        ease: 'Quad.easeOut',
      });
    };

    preload(): void {
      for (const soundKey of Object.values(GAME_SOUND_KEYS)) {
        this.load.audio(soundKey, SOUND_URLS[soundKey]);
      }
      this.load.on('loaderror', this.handleLoadError);
    }

    create(): void {
      this.cameras.main.setBackgroundColor('rgba(0, 0, 0, 0)');
      this.rune = this.add.circle(60, 22, 5, 0xf1d08b, 0.9).setAlpha(0);
      this.game.events.on('fm:audio-pulse', this.flashRune, this);
      this.stopBridge = bridge.on((event) => {
        if (event.type === 'audio-mute') {
          const setMute = Reflect.get(this.sound, 'setMute');
          if (typeof setMute === 'function') {
            Reflect.apply(setMute, this.sound, [event.muted]);
          }
          return;
        }
        if (event.type === 'audio-volume') {
          const setVolume = Reflect.get(this.sound, 'setVolume');
          if (typeof setVolume === 'function') {
            Reflect.apply(setVolume, this.sound, [clampVolume(event.volume)]);
          }
          return;
        }
        if (event.type === 'audio-pause') {
          const methodName = event.paused ? 'pauseAll' : 'resumeAll';
          const method = Reflect.get(this.sound, methodName);
          if (typeof method === 'function') {
            Reflect.apply(method, this.sound, []);
          }
          return;
        }
        if (event.type === 'audio-unlock') {
          const unlock = Reflect.get(this.sound, 'unlock');
          if (typeof unlock === 'function') {
            Reflect.apply(unlock, this.sound, []);
          }
          const context = Reflect.get(this.sound, 'context');
          if (
            context !== null
            && typeof context === 'object'
            && 'resume' in context
            && typeof context.resume === 'function'
          ) {
            void context.resume();
          }
          bridge.emit({ type: 'audio-unlocked', eventId: event.eventId });
          return;
        }
        if (event.type === 'audio-ambient-play') {
          if (paused) {
            bridge.emit({
              type: 'sound-skipped',
              eventId: event.eventId,
              soundKey: event.soundKey,
              reason: 'paused',
            });
            return;
          }
          try {
            this.stopAmbientSound();
            const played = this.sound.play(event.soundKey, {
              loop: true,
              volume: clampVolume(event.volume),
            });
            this.ambientSound = played;
            const accepted = played !== false && played !== null;
            bridge.emit({
              type: 'ambient-started',
              eventId: event.eventId,
              soundKey: event.soundKey,
              accepted,
            });
            if (accepted) {
              this.game.events.emit('fm:audio-pulse');
            }
          } catch (error) {
            reportSoundError(event.eventId, event.soundKey, error);
          }
          return;
        }
        if (event.type === 'audio-ambient-stop') {
          this.stopAmbientSound();
          bridge.emit({ type: 'ambient-stopped', eventId: event.eventId });
          return;
        }
        if (event.type !== 'audio-play') {
          return;
        }
        if (paused) {
          bridge.emit({
            type: 'sound-skipped',
            eventId: event.eventId,
            soundKey: event.soundKey,
            reason: 'paused',
          });
          return;
        }
        try {
          this.activeSounds = this.activeSounds.filter(
            (sound) =>
              typeof sound === 'object'
              && sound !== null
              && Reflect.get(sound, 'isPlaying') !== false,
          );
          if (this.activeSounds.length >= MAX_ACTIVE_SOUNDS) {
            const oldest = this.activeSounds.shift();
            const stop = oldest === undefined || oldest === null
              ? undefined
              : Reflect.get(oldest, 'stop');
            if (oldest !== null && oldest !== undefined && typeof stop === 'function') {
              Reflect.apply(stop, oldest, []);
            }
          }
          const played = this.sound.play(event.soundKey, {
            volume: clampVolume(event.volume),
          });
          if (typeof played === 'object' && played !== null) {
            this.activeSounds.push(played);
          }
          const accepted = played !== false && played !== null;
          bridge.emit({
            type: 'sound-played',
            eventId: event.eventId,
            soundKey: event.soundKey,
            accepted,
          });
          if (accepted) {
            this.game.events.emit('fm:audio-pulse');
          }
        } catch (error) {
          reportSoundError(event.eventId, event.soundKey, error);
        }
      });
      this.events.once('shutdown', () => {
        this.load.off('loaderror', this.handleLoadError);
        this.stopBridge?.();
        this.stopBridge = undefined;
        this.stopAmbientSound();
        this.activeSounds = [];
        this.game.events.off('fm:audio-pulse', this.flashRune, this);
      });
      bridge.emit({ type: 'game-ready' });
    }
  };

  const game = new Phaser.Game({
    type: Phaser.CANVAS,
    parent: input.parent,
    width: 120,
    height: 44,
    transparent: true,
    banner: false,
    scene: [AudioScene],
    scale: {
      mode: Phaser.Scale.RESIZE,
      autoCenter: Phaser.Scale.CENTER_BOTH,
    },
    render: {
      antialias: true,
      pixelArt: false,
    },
  });

  function requestPlay(
    soundKey: GameSoundKey,
    eventId: string,
    requestedVolume = 1,
  ): void {
    const playVolume = clampVolume(
      (requestedVolume <= 1 ? requestedVolume : 1)
        * GAME_SOUND_BASE_VOLUME[soundKey],
    );
    if (paused) {
      bridge.emit({
        type: 'sound-skipped',
        eventId,
        soundKey,
        reason: 'paused',
      });
      return;
    }
    if (!rateLimiter.canPlay(soundKey, Date.now())) {
      bridge.emit({
        type: 'sound-skipped',
        eventId,
        soundKey,
        reason: 'rate-limit',
      });
      return;
    }
    if (!gameReady) {
      pendingUnlockEventId = `unlock:${eventId}`;
      pendingPlay = { soundKey, eventId, volume: playVolume };
      return;
    }
    bridge.emit({ type: 'audio-unlock', eventId: `unlock:${eventId}` });
    bridge.emit({
      type: 'audio-play',
      eventId,
      soundKey,
      volume: playVolume,
    });
  }

  function requestAmbient(
    soundKey: GameSoundKey,
    eventId: string,
    requestedVolume = 1,
  ): void {
    const playVolume = clampVolume(
      (requestedVolume <= 1 ? requestedVolume : 1)
        * GAME_SOUND_BASE_VOLUME[soundKey],
    );
    if (paused || !gameReady) {
      pendingAmbient = { soundKey, eventId, volume: playVolume };
      if (!paused) {
        bridge.emit({ type: 'audio-unlock', eventId: `unlock:${eventId}` });
      }
      return;
    }
    bridge.emit({ type: 'audio-unlock', eventId: `unlock:${eventId}` });
    bridge.emit({
      type: 'audio-ambient-play',
      eventId,
      soundKey,
      volume: playVolume,
    });
  }

  return {
    bridge,
    isReady(): boolean {
      return gameReady;
    },
    getLastSoundError() {
      return lastSoundError;
    },
    playSound(soundKey: GameSoundKey, eventId: string, requestedVolume = 1): void {
      requestPlay(soundKey, eventId, requestedVolume);
    },
    playAmbient(soundKey: GameSoundKey, eventId: string, requestedVolume = 1): void {
      requestAmbient(soundKey, eventId, requestedVolume);
    },
    stopAmbient(eventId: string): void {
      pendingAmbient = null;
      bridge.emit({ type: 'audio-ambient-stop', eventId });
    },
    playFlip(eventId: string): void {
      requestPlay(FLASHCARD_FLIP_SOUND_KEY, eventId);
    },
    setMuted(nextMuted: boolean): void {
      muted = nextMuted;
      mutedBeforeReady = nextMuted;
      if (gameReady) {
        bridge.emit({ type: 'audio-mute', muted: nextMuted });
      }
    },
    setVolume(nextVolume: number): void {
      volume = clampVolume(nextVolume);
      if (gameReady) {
        bridge.emit({ type: 'audio-volume', volume });
      }
    },
    setPaused(nextPaused: boolean): void {
      paused = nextPaused;
      if (gameReady) {
        bridge.emit({ type: 'audio-pause', paused });
      }
    },
    destroy(): void {
      unsubscribeBridge();
      if (gameReady) {
        game.sound.stopAll();
      }
      bridge.emit({ type: 'game-destroyed' });
      bridge.clear();
      game.destroy(true, false);
      gameReady = false;
    },
  };
}
