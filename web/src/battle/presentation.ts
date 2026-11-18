export type BattlePresentationMode = 'animated' | 'reduced' | 'static';

export function resolveBattlePresentationMode(input: {
  reduceMotion: boolean;
  runtimeUnavailable: boolean;
}): BattlePresentationMode {
  if (input.runtimeUnavailable) {
    return 'static';
  }
  return input.reduceMotion ? 'reduced' : 'animated';
}
