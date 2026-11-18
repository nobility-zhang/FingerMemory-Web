export const GAME_SOUND_KEYS = {
  flashcardFlip: 'flashcard-flip',
  battleDraw: 'battle-draw',
  battlePlay: 'battle-play',
  battleHit: 'battle-hit',
  battleBlock: 'battle-block',
  battleHeal: 'battle-heal',
  battleEnergy: 'battle-energy',
  battleEnemyTurn: 'battle-enemy-turn',
  battleVictory: 'battle-victory',
  battleRescue: 'battle-rescue',
} as const;

export type GameSoundKey =
  (typeof GAME_SOUND_KEYS)[keyof typeof GAME_SOUND_KEYS];

export const GAME_SOUND_BASE_VOLUME: Readonly<Record<GameSoundKey, number>> = {
  'flashcard-flip': 0.35,
  'battle-draw': 0.42,
  'battle-play': 0.5,
  'battle-hit': 0.54,
  'battle-block': 0.45,
  'battle-heal': 0.4,
  'battle-energy': 0.43,
  'battle-enemy-turn': 0.5,
  'battle-victory': 0.52,
  'battle-rescue': 0.42,
};

export function isGameSoundKey(value: string): value is GameSoundKey {
  return Object.values(GAME_SOUND_KEYS).includes(value as GameSoundKey);
}
