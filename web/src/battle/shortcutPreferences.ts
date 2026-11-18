import type { EndTurnShortcut } from './shortcutStateMachine';

export const BATTLE_SHORTCUT_STORAGE_KEY = 'finger-memory:battle-shortcuts';
export const DEFAULT_END_TURN_SHORTCUT: EndTurnShortcut = 'ctrl+enter';

export interface BattleShortcutPreferences {
  endTurn: EndTurnShortcut;
}

const END_TURN_SHORTCUTS: readonly EndTurnShortcut[] = [
  'ctrl+enter',
  'alt+enter',
  'ctrl+shift+enter',
];

function storage(): Storage | undefined {
  try {
    return window.localStorage;
  } catch {
    return undefined;
  }
}

function isEndTurnShortcut(value: unknown): value is EndTurnShortcut {
  return typeof value === 'string'
    && END_TURN_SHORTCUTS.includes(value as EndTurnShortcut);
}

export function endTurnShortcutLabel(shortcut: EndTurnShortcut): string {
  switch (shortcut) {
    case 'ctrl+enter':
      return 'Ctrl + Enter';
    case 'alt+enter':
      return 'Alt + Enter';
    case 'ctrl+shift+enter':
      return 'Ctrl + Shift + Enter';
  }
}

export function endTurnShortcutAriaLabel(shortcut: EndTurnShortcut): string {
  return endTurnShortcutLabel(shortcut)
    .replaceAll('Ctrl', 'Control')
    .replaceAll(' + ', '+');
}

export function loadBattleShortcutPreferences(
  target: Storage | undefined = storage(),
): BattleShortcutPreferences {
  if (target === undefined) {
    return { endTurn: DEFAULT_END_TURN_SHORTCUT };
  }
  try {
    const raw = target.getItem(BATTLE_SHORTCUT_STORAGE_KEY);
    if (raw === null) {
      return { endTurn: DEFAULT_END_TURN_SHORTCUT };
    }
    const parsed: unknown = JSON.parse(raw);
    if (typeof parsed !== 'object' || parsed === null || !('endTurn' in parsed)) {
      return { endTurn: DEFAULT_END_TURN_SHORTCUT };
    }
    return {
      endTurn: isEndTurnShortcut(parsed.endTurn)
        ? parsed.endTurn
        : DEFAULT_END_TURN_SHORTCUT,
    };
  } catch {
    return { endTurn: DEFAULT_END_TURN_SHORTCUT };
  }
}

export function saveBattleShortcutPreferences(
  preferences: BattleShortcutPreferences,
  target: Storage | undefined = storage(),
): BattleShortcutPreferences {
  const normalized = {
    endTurn: isEndTurnShortcut(preferences.endTurn)
      ? preferences.endTurn
      : DEFAULT_END_TURN_SHORTCUT,
  };
  try {
    target?.setItem(BATTLE_SHORTCUT_STORAGE_KEY, JSON.stringify(normalized));
  } catch {
    // Storage can be unavailable in private contexts. Keep the in-memory
    // preference and do not block battle controls.
  }
  return normalized;
}
