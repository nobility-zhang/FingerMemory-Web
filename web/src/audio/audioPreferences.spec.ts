import { describe, expect, it } from 'vitest';
import {
  AUDIO_PREFERENCES_STORAGE_KEY,
  DEFAULT_SOUND_VOLUME,
  loadAudioPreferences,
  saveAudioPreferences,
} from './audioPreferences';

describe('audio preferences', () => {
  it('persists mute and clamps the volume', () => {
    const storage = window.localStorage;
    storage.clear();
    expect(saveAudioPreferences({ muted: true, volume: 2 }, storage)).toEqual({
      muted: true,
      volume: 1,
    });
    expect(loadAudioPreferences(storage)).toEqual({
      muted: true,
      volume: 1,
    });
    expect(storage.getItem(AUDIO_PREFERENCES_STORAGE_KEY)).not.toBeNull();
  });

  it('falls back safely for malformed or unavailable preferences', () => {
    const storage = window.localStorage;
    storage.clear();
    storage.setItem(AUDIO_PREFERENCES_STORAGE_KEY, '{bad json');
    expect(loadAudioPreferences(storage)).toEqual({
      muted: false,
      volume: DEFAULT_SOUND_VOLUME,
    });
    expect(loadAudioPreferences(undefined)).toEqual({
      muted: false,
      volume: DEFAULT_SOUND_VOLUME,
    });
  });
});
