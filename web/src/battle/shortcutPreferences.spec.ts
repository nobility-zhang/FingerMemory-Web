import { describe, expect, it } from 'vitest';
import {
  BATTLE_SHORTCUT_STORAGE_KEY,
  DEFAULT_END_TURN_SHORTCUT,
  endTurnShortcutAriaLabel,
  endTurnShortcutLabel,
  loadBattleShortcutPreferences,
  saveBattleShortcutPreferences,
} from './shortcutPreferences';

describe('battle shortcut preferences', () => {
  it('persists only supported end-turn shortcuts', () => {
    const storage = window.localStorage;
    storage.clear();

    expect(saveBattleShortcutPreferences({ endTurn: 'alt+enter' }, storage))
      .toEqual({ endTurn: 'alt+enter' });
    expect(loadBattleShortcutPreferences(storage)).toEqual({ endTurn: 'alt+enter' });
    expect(storage.getItem(BATTLE_SHORTCUT_STORAGE_KEY)).not.toBeNull();
    expect(endTurnShortcutLabel('alt+enter')).toBe('Alt + Enter');
    expect(endTurnShortcutAriaLabel('ctrl+shift+enter'))
      .toBe('Control+Shift+Enter');
  });

  it('falls back safely for malformed or unavailable storage', () => {
    const storage = window.localStorage;
    storage.clear();
    storage.setItem(BATTLE_SHORTCUT_STORAGE_KEY, '{"endTurn":"browser-command"}');

    expect(loadBattleShortcutPreferences(storage)).toEqual({
      endTurn: DEFAULT_END_TURN_SHORTCUT,
    });
    expect(loadBattleShortcutPreferences(undefined)).toEqual({
      endTurn: DEFAULT_END_TURN_SHORTCUT,
    });
  });
});
