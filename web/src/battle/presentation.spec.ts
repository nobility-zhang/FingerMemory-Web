import { describe, expect, it } from 'vitest';
import { resolveBattlePresentationMode } from './presentation';

describe('battle presentation mode', () => {
  it('uses a static DOM stage when the game runtime is unavailable', () => {
    expect(resolveBattlePresentationMode({
      reduceMotion: false,
      runtimeUnavailable: true,
    })).toBe('static');
    expect(resolveBattlePresentationMode({
      reduceMotion: true,
      runtimeUnavailable: true,
    })).toBe('static');
  });

  it('keeps battle actions available in animated and reduced modes', () => {
    expect(resolveBattlePresentationMode({
      reduceMotion: false,
      runtimeUnavailable: false,
    })).toBe('animated');
    expect(resolveBattlePresentationMode({
      reduceMotion: true,
      runtimeUnavailable: false,
    })).toBe('reduced');
  });
});
