import type { PracticeModeId } from '../core/domain';

export const LAUNCHABLE_PRACTICE_MODE_IDS = [
  'flashcard',
  'keyboard-recall',
] as const;

export const RETIRED_PRACTICE_MODE_IDS = [
  'lyrics-scroll',
  'danmaku-stream',
  'card-rain',
] as const;

export type LaunchablePracticeModeId =
  (typeof LAUNCHABLE_PRACTICE_MODE_IDS)[number];

export const ALL_PRACTICE_MODE_IDS: readonly PracticeModeId[] = [
  ...LAUNCHABLE_PRACTICE_MODE_IDS,
  ...RETIRED_PRACTICE_MODE_IDS,
];

export type RetiredPracticeModeId =
  (typeof RETIRED_PRACTICE_MODE_IDS)[number];

export function isRetiredPracticeModeId(
  modeId: string,
): modeId is RetiredPracticeModeId {
  return RETIRED_PRACTICE_MODE_IDS.some((retiredId) => retiredId === modeId);
}

export function isPracticeModeId(modeId: string): modeId is PracticeModeId {
  return ALL_PRACTICE_MODE_IDS.some((knownId) => knownId === modeId);
}

export function retiredPracticeModeLabel(modeId: RetiredPracticeModeId): string {
  switch (modeId) {
    case 'lyrics-scroll':
      return '歌词滚动';
    case 'danmaku-stream':
      return '弹幕模式';
    case 'card-rain':
      return '卡片雨';
  }
}
