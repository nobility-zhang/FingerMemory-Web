import type { GameSoundKey } from './soundCatalog';

export const MIN_SAME_SOUND_INTERVAL_MS = 65;
export const MAX_SOUNDS_PER_WINDOW = 8;
export const SOUND_WINDOW_MS = 120;

export class GameSoundRateLimiter {
  private readonly lastPlayedAt = new Map<GameSoundKey, number>();
  private recentPlayTimes: number[] = [];

  canPlay(soundKey: GameSoundKey, now: number): boolean {
    const last = this.lastPlayedAt.get(soundKey) ?? Number.NEGATIVE_INFINITY;
    if (now - last < MIN_SAME_SOUND_INTERVAL_MS) {
      return false;
    }
    this.recentPlayTimes = this.recentPlayTimes.filter(
      (playedAt) => now - playedAt < SOUND_WINDOW_MS,
    );
    if (this.recentPlayTimes.length >= MAX_SOUNDS_PER_WINDOW) {
      return false;
    }
    this.lastPlayedAt.set(soundKey, now);
    this.recentPlayTimes.push(now);
    return true;
  }

  reset(): void {
    this.lastPlayedAt.clear();
    this.recentPlayTimes = [];
  }
}
