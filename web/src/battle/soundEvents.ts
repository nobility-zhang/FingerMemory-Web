import type { GameSoundKey } from '../game-runtime/soundCatalog';

export interface BattleSoundCue {
  soundKey: GameSoundKey;
  eventId: string;
  volumeScale: number;
}

function text(event: Record<string, unknown>, field: string): string {
  const value = event[field];
  return typeof value === 'string' ? value : '';
}

function sequence(event: Record<string, unknown>, index: number): string {
  const value = event.eventSequence;
  return typeof value === 'number' ? String(value) : `event-${index}`;
}

/**
 * Converts committed battle events into presentation-only sound cues. The
 * server has already accepted the transition before these are played.
 */
export function battleSoundCues(
  events: ReadonlyArray<Record<string, unknown>>,
  eventPrefix: string,
): BattleSoundCue[] {
  const cues: BattleSoundCue[] = [];
  const seen = new Set<string>();
  for (const [index, event] of events.entries()) {
    const type = text(event, 'type');
    let soundKey: GameSoundKey | undefined;
    let volumeScale = 1;
    if (type === 'cards-drawn') {
      soundKey = 'battle-draw';
    } else if (type === 'card-played') {
      soundKey = 'battle-play';
    } else if (type === 'damage') {
      soundKey = 'battle-hit';
      volumeScale = text(event, 'source') === 'enemy' ? 0.82 : 1;
    } else if (type === 'block-gained') {
      soundKey = 'battle-block';
    } else if (type === 'healed') {
      soundKey = 'battle-heal';
    } else if (type === 'learning-energy-awarded') {
      soundKey = 'battle-energy';
    } else if (type === 'intent-executed') {
      soundKey = 'battle-enemy-turn';
    } else if (type === 'battle-ended') {
      soundKey = text(event, 'result') === 'victory'
        ? 'battle-victory'
        : 'battle-rescue';
    }
    if (soundKey === undefined) {
      continue;
    }
    const eventId = `${eventPrefix}:${sequence(event, index)}`;
    if (seen.has(eventId)) {
      continue;
    }
    seen.add(eventId);
    cues.push({ soundKey, eventId, volumeScale });
  }
  return cues;
}
