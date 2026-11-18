import { describe, expect, it } from 'vitest';
import {
  GameSoundRateLimiter,
  MIN_SAME_SOUND_INTERVAL_MS,
  SOUND_WINDOW_MS,
} from './soundRateLimiter';

describe('GameSoundRateLimiter', () => {
  it('drops rapid repeats of the same sound while allowing recovery', () => {
    const limiter = new GameSoundRateLimiter();
    expect(limiter.canPlay('battle-hit', 100)).toBe(true);
    expect(limiter.canPlay('battle-hit', 110)).toBe(false);
    expect(
      limiter.canPlay('battle-hit', 100 + MIN_SAME_SOUND_INTERVAL_MS),
    ).toBe(true);
  });

  it('caps distinct sounds inside the rolling overlap window', () => {
    const limiter = new GameSoundRateLimiter();
    const keys = [
      'battle-draw',
      'battle-play',
      'battle-hit',
      'battle-block',
      'battle-heal',
      'battle-energy',
      'battle-enemy-turn',
      'battle-victory',
    ] as const;
    for (const [index, key] of keys.entries()) {
      expect(limiter.canPlay(key, 100 + index)).toBe(true);
    }
    expect(limiter.canPlay('battle-rescue', 109)).toBe(false);
    expect(limiter.canPlay('battle-rescue', 100 + SOUND_WINDOW_MS)).toBe(true);
  });
});
