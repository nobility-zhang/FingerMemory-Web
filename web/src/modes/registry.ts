import KeyboardMode from './KeyboardMode.vue';
import LyricsMode from './LyricsMode.vue';
import type { PracticeModeDefinition } from './types';

export const PRACTICE_MODES: readonly PracticeModeDefinition[] = [
  { id: 'lyrics-scroll', label: '歌词滚动', component: LyricsMode },
  { id: 'keyboard-recall', label: '键盘对照', component: KeyboardMode },
];
