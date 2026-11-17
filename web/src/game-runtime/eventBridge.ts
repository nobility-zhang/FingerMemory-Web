export const FLASHCARD_FLIP_SOUND_KEY = 'flashcard-flip';

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
      type: 'audio-play';
      eventId: string;
      soundKey: typeof FLASHCARD_FLIP_SOUND_KEY;
      volume: number;
    }
  | {
      type: 'sound-played';
      eventId: string;
      soundKey: typeof FLASHCARD_FLIP_SOUND_KEY;
      accepted: boolean;
    }
  | {
      type: 'sound-error';
      eventId: string;
      soundKey: typeof FLASHCARD_FLIP_SOUND_KEY;
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
