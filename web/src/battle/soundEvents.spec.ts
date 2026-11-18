import { describe, expect, it } from 'vitest';
import { battleSoundCues } from './soundEvents';

describe('battle sound events', () => {
  it('maps committed battle events to stable sound cues', () => {
    const cues = battleSoundCues([
      { type: 'cards-drawn', eventSequence: 1 },
      { type: 'card-played', eventSequence: 2 },
      { type: 'damage', source: 'card', eventSequence: 3 },
      { type: 'block-gained', eventSequence: 4 },
      { type: 'healed', eventSequence: 5 },
      { type: 'learning-energy-awarded', eventSequence: 6 },
      { type: 'intent-executed', eventSequence: 7 },
      { type: 'battle-ended', result: 'victory', eventSequence: 8 },
      { type: 'battle-ended', result: 'defeat', eventSequence: 8 },
    ], 'action-1');

    expect(cues.map((cue) => cue.soundKey)).toEqual([
      'battle-draw',
      'battle-play',
      'battle-hit',
      'battle-block',
      'battle-heal',
      'battle-energy',
      'battle-enemy-turn',
      'battle-victory',
    ]);
    expect(cues[0]?.eventId).toBe('action-1:1');
    expect(cues[7]?.eventId).toBe('action-1:8');
  });

  it('does not emit presentation noise for unknown events', () => {
    expect(battleSoundCues([
      { type: 'turn-phase-changed', eventSequence: 1 },
      { type: 'burn-ticked', eventSequence: 2 },
    ], 'action-2')).toEqual([]);
  });

  it('distinguishes rescue and softer incoming damage cues', () => {
    const cues = battleSoundCues([
      { type: 'damage', source: 'enemy', eventSequence: 1 },
      { type: 'battle-ended', result: 'defeat', eventSequence: 2 },
    ], 'action-3');

    expect(cues).toEqual([
      {
        soundKey: 'battle-hit',
        eventId: 'action-3:1',
        volumeScale: 0.82,
      },
      {
        soundKey: 'battle-rescue',
        eventId: 'action-3:2',
        volumeScale: 1,
      },
    ]);
  });
});
