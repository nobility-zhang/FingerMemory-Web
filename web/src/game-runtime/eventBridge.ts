import {
  GAME_SOUND_KEYS,
  type GameSoundKey,
} from './soundCatalog';

export const FLASHCARD_FLIP_SOUND_KEY = GAME_SOUND_KEYS.flashcardFlip;

export type GameBridgeEvent =
  | {
      type: 'game-ready';
    }
  | {
      type: 'audio-unlock';
      eventId: string;
    }
  | {
      type: 'audio-unlocked';
      eventId: string;
    }
  | {
      type: 'audio-mute';
      muted: boolean;
    }
  | {
      type: 'audio-volume';
      volume: number;
    }
  | {
      type: 'audio-pause';
      paused: boolean;
    }
  | {
      type: 'audio-play';
      eventId: string;
      soundKey: GameSoundKey;
      volume: number;
    }
  | {
      type: 'sound-played';
      eventId: string;
      soundKey: GameSoundKey;
      accepted: boolean;
    }
  | {
      type: 'sound-skipped';
      eventId: string;
      soundKey: GameSoundKey;
      reason: 'rate-limit' | 'paused';
    }
  | {
      type: 'sound-error';
      eventId: string;
      soundKey: GameSoundKey;
      message: string;
    }
  | {
      type: 'game-destroyed';
    };

export type GameBridgeListener = (event: GameBridgeEvent) => void;

export class GameEventBridge {
  private readonly listeners = new Set<GameBridgeListener>();

  on(listener: GameBridgeListener): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  emit(event: GameBridgeEvent): void {
    for (const listener of [...this.listeners]) {
      listener(event);
    }
  }

  clear(): void {
    this.listeners.clear();
  }
}
