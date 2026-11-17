export const DEFAULT_SOUND_VOLUME = 0.35;
export const AUDIO_PREFERENCES_STORAGE_KEY = 'finger-memory:audio-preferences';

export interface AudioPreferences {
  muted: boolean;
  volume: number;
}

function storage(): Storage | undefined {
  try {
    return window.localStorage;
  } catch {
    return undefined;
  }
}

export function loadAudioPreferences(
  target: Storage | undefined = storage(),
): AudioPreferences {
  if (target === undefined) {
    return { muted: false, volume: DEFAULT_SOUND_VOLUME };
  }
  try {
    const raw = target.getItem(AUDIO_PREFERENCES_STORAGE_KEY);
    if (raw === null) {
      return { muted: false, volume: DEFAULT_SOUND_VOLUME };
    }
    const parsed: unknown = JSON.parse(raw);
    if (typeof parsed !== 'object' || parsed === null) {
      return { muted: false, volume: DEFAULT_SOUND_VOLUME };
    }
    const muted = 'muted' in parsed && typeof parsed.muted === 'boolean'
      ? parsed.muted
      : false;
    const volume = 'volume' in parsed && typeof parsed.volume === 'number'
      && Number.isFinite(parsed.volume)
      && parsed.volume >= 0
      && parsed.volume <= 1
      ? parsed.volume
      : DEFAULT_SOUND_VOLUME;
    return { muted, volume };
  } catch {
    return { muted: false, volume: DEFAULT_SOUND_VOLUME };
  }
}

export function saveAudioPreferences(
  preferences: AudioPreferences,
  target: Storage | undefined = storage(),
): AudioPreferences {
  const normalized = {
    muted: preferences.muted,
    volume: Math.min(1, Math.max(0, preferences.volume)),
  };
  try {
    target?.setItem(
      AUDIO_PREFERENCES_STORAGE_KEY,
      JSON.stringify(normalized),
    );
  } catch {
    // Storage can be disabled by browser privacy settings. Keep the in-memory
    // preference for this page and do not block learning.
  }
  return normalized;
}
