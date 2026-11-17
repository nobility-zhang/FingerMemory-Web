import {
  FLASHCARD_FLIP_SOUND_KEY,
  GameEventBridge,
} from './eventBridge';

export const FLASHCARD_FLIP_SOUND_URL = new URL(
  '../assets/audio/flashcard-flip.wav',
  import.meta.url,
).href;

export interface PhaserAudioGameController {
  bridge: GameEventBridge;
  isReady(): boolean;
  playFlip(eventId: string): void;
  setMuted(muted: boolean): void;
  destroy(): void;
}

export interface PhaserAudioGameInput {
  parent: HTMLElement;
}

function emitSoundError(
  bridge: GameEventBridge,
  eventId: string,
  error: unknown,
): void {
  bridge.emit({
    type: 'sound-error',
    eventId,
    soundKey: FLASHCARD_FLIP_SOUND_KEY,
    message: error instanceof Error ? error.message : 'unknown audio error',
  });
}

export async function createPhaserAudioGame(
  input: PhaserAudioGameInput,
): Promise<PhaserAudioGameController> {
  const Phaser = await import('phaser');
  const bridge = new GameEventBridge();
  let gameReady = false;
  let pendingUnlockEventId: string | null = null;
  let pendingPlayEventId: string | null = null;
  let muted = false;
  let mutedBeforeReady = false;
  const unsubscribeBridge = bridge.on((event) => {
    if (event.type === 'game-ready') {
      gameReady = true;
      bridge.emit({ type: 'audio-mute', muted: mutedBeforeReady || muted });
      if (pendingUnlockEventId !== null) {
        bridge.emit({
          type: 'audio-unlock',
          eventId: pendingUnlockEventId,
        });
        pendingUnlockEventId = null;
      }
      if (pendingPlayEventId !== null) {
        bridge.emit({
          type: 'audio-play',
          eventId: pendingPlayEventId,
          soundKey: FLASHCARD_FLIP_SOUND_KEY,
          volume: 0.35,
        });
        pendingPlayEventId = null;
      }
    }
  });

  const AudioScene = class extends Phaser.Scene {
    private rune?: Phaser.GameObjects.Arc;
    private stopBridge?: () => void;
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
      this.load.audio(FLASHCARD_FLIP_SOUND_KEY, FLASHCARD_FLIP_SOUND_URL);
    }

    create(): void {
      this.cameras.main.setBackgroundColor('rgba(0, 0, 0, 0)');
      this.rune = this.add.circle(60, 22, 5, 0xf1d08b, 0.9).setAlpha(0);
      this.game.events.on('fm:audio-flip', this.flashRune, this);
      this.stopBridge = bridge.on((event) => {
        if (event.type === 'audio-mute') {
          const setMute = Reflect.get(this.sound, 'setMute');
          if (typeof setMute === 'function') {
            Reflect.apply(setMute, this.sound, [event.muted]);
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
        if (event.type !== 'audio-play') {
          return;
        }
        try {
          const accepted = this.sound.play(event.soundKey, {
            volume: event.volume,
          });
          bridge.emit({
            type: 'sound-played',
            eventId: event.eventId,
            soundKey: event.soundKey,
            accepted,
          });
          if (accepted) {
            this.game.events.emit('fm:audio-flip');
          }
        } catch (error) {
          emitSoundError(bridge, event.eventId, error);
        }
      });
      this.events.once('shutdown', () => {
        this.stopBridge?.();
        this.stopBridge = undefined;
        this.game.events.off('fm:audio-flip', this.flashRune, this);
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

  return {
    bridge,
    isReady(): boolean {
      return gameReady;
    },
    playFlip(eventId: string): void {
      if (!gameReady) {
        pendingUnlockEventId = `unlock:${eventId}`;
        pendingPlayEventId = eventId;
        return;
      }
      bridge.emit({ type: 'audio-unlock', eventId: `unlock:${eventId}` });
      bridge.emit({
        type: 'audio-play',
        eventId,
        soundKey: FLASHCARD_FLIP_SOUND_KEY,
        volume: 0.35,
      });
    },
    setMuted(nextMuted: boolean): void {
      muted = nextMuted;
      mutedBeforeReady = nextMuted;
      if (gameReady) {
        bridge.emit({ type: 'audio-mute', muted: nextMuted });
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
