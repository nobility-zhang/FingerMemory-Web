import { describe, expect, it } from 'vitest';
import { useCompanionPreferences } from './useCompanionPreferences';

function memoryStorage(initial: Record<string, string> = {}): {
  values: Map<string, string>;
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
} {
  const values = new Map(Object.entries(initial));
  return {
    values,
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => {
      values.set(key, value);
    },
  };
}

describe('useCompanionPreferences', () => {
  it('loads the selected companion and density from storage', () => {
    const preferences = useCompanionPreferences(
      memoryStorage({
        'fm.companion': 'serin',
        'fm.companionDensity': 'rich',
      }),
    );

    expect(preferences.selectedCompanion.value.id).toBe('serin');
    expect(preferences.companionDensity.value).toBe('rich');
  });

  it('updates the current state and persists each explicit choice', () => {
    const storage = memoryStorage();
    const preferences = useCompanionPreferences(storage);

    preferences.selectCompanion('serin');
    preferences.selectCompanionDensity('quiet');

    expect(preferences.selectedCompanion.value.id).toBe('serin');
    expect(preferences.companionDensity.value).toBe('quiet');
    expect(storage.values.get('fm.companion')).toBe('serin');
    expect(storage.values.get('fm.companionDensity')).toBe('quiet');
  });

  it('keeps in-memory choices when storage is unavailable or throws', () => {
    const throwingStorage = {
      getItem: () => {
        throw new Error('storage blocked');
      },
      setItem: () => {
        throw new Error('storage blocked');
      },
    };
    const preferences = useCompanionPreferences(throwingStorage);

    preferences.selectCompanion('serin');
    preferences.selectCompanionDensity('rich');

    expect(preferences.selectedCompanion.value.id).toBe('serin');
    expect(preferences.companionDensity.value).toBe('rich');
  });
});
