export type BattleShortcutContext =
  | 'dialogue'
  | 'learning-input'
  | 'flashcard-front'
  | 'rating'
  | 'card-select'
  | 'target-select'
  | 'paused'
  | 'result'
  | 'blocked';

export type EndTurnShortcut =
  | 'ctrl+enter'
  | 'alt+enter'
  | 'ctrl+shift+enter';

export type BattleShortcutAction =
  | { type: 'submit-answer' }
  | { type: 'rate'; rating: 'again' | 'hard' | 'good' | 'easy' }
  | { type: 'select-card'; index: number }
  | { type: 'cycle-target'; direction: 'previous' | 'next' }
  | { type: 'confirm-target' }
  | { type: 'cancel-target' }
  | { type: 'pause' }
  | { type: 'resume' }
  | { type: 'end-turn' }
  | { type: 'finish' }
  | { type: 'toggle-help' }
  | { type: 'close-help' };

export interface BattleShortcutInput {
  key: string;
  context: BattleShortcutContext;
  endTurnShortcut: EndTurnShortcut;
  isComposing: boolean;
  repeat: boolean;
  isEditable: boolean;
  isInteractiveTarget: boolean;
  helpOpen: boolean;
  ctrlKey?: boolean;
  metaKey?: boolean;
  altKey?: boolean;
  shiftKey?: boolean;
}

const RATING_ACTIONS: Record<
  string,
  Extract<BattleShortcutAction, { type: 'rate' }>['rating']
> = {
  '1': 'again',
  '2': 'hard',
  '3': 'good',
  '4': 'easy',
};

function hasNoModifier(input: BattleShortcutInput): boolean {
  return input.ctrlKey !== true
    && input.metaKey !== true
    && input.altKey !== true
    && input.shiftKey !== true;
}

function hasNoCommandOrAltModifier(input: BattleShortcutInput): boolean {
  return input.ctrlKey !== true
    && input.metaKey !== true
    && input.altKey !== true;
}

function matchesEndTurnShortcut(input: BattleShortcutInput): boolean {
  if (input.key !== 'Enter' || input.metaKey === true) {
    return false;
  }
  switch (input.endTurnShortcut) {
    case 'ctrl+enter':
      return input.ctrlKey === true
        && input.altKey !== true
        && input.shiftKey !== true;
    case 'alt+enter':
      return input.altKey === true
        && input.ctrlKey !== true
        && input.shiftKey !== true;
    case 'ctrl+shift+enter':
      return input.ctrlKey === true
        && input.shiftKey === true
        && input.altKey !== true;
  }
}

/**
 * Resolves one physical key into one battle intent. Input methods, key repeat,
 * browser shortcuts and native button activation are handled before game keys.
 */
export function resolveBattleShortcut(
  input: BattleShortcutInput,
): BattleShortcutAction | null {
  if (input.isComposing || input.repeat) {
    return null;
  }
  if (input.helpOpen) {
    return (input.key === 'Escape' || input.key === '?')
      && hasNoCommandOrAltModifier(input)
      ? { type: 'close-help' }
      : null;
  }

  if (
    input.key === '?'
    && !input.isEditable
    && (
      input.context === 'rating'
      || input.context === 'card-select'
      || input.context === 'target-select'
      || input.context === 'result'
    )
    && hasNoCommandOrAltModifier(input)
  ) {
    return { type: 'toggle-help' };
  }

  if (input.context === 'target-select') {
    if (!hasNoModifier(input)) {
      return null;
    }
    if (input.key === 'Escape') {
      return { type: 'cancel-target' };
    }
    if (input.key === 'ArrowLeft' || input.key === 'ArrowUp') {
      return { type: 'cycle-target', direction: 'previous' };
    }
    if (input.key === 'ArrowRight' || input.key === 'ArrowDown') {
      return { type: 'cycle-target', direction: 'next' };
    }
    if (input.key === 'Enter') {
      return { type: 'confirm-target' };
    }
    return null;
  }

  if (
    matchesEndTurnShortcut(input)
    && input.context === 'card-select'
  ) {
    return { type: 'end-turn' };
  }

  if (input.key === 'Escape' && hasNoModifier(input)) {
    if (
      input.context === 'learning-input'
      || input.context === 'rating'
      || input.context === 'card-select'
    ) {
      return { type: 'pause' };
    }
    if (input.context === 'paused') {
      return { type: 'resume' };
    }
    return null;
  }

  if (input.context === 'paused') {
    return input.key === 'Enter' && hasNoModifier(input)
      ? { type: 'resume' }
      : null;
  }

  if (input.context === 'result') {
    if (input.isInteractiveTarget || !hasNoModifier(input)) {
      return null;
    }
    return input.key === 'Enter' ? { type: 'finish' } : null;
  }

  if (input.isInteractiveTarget && !input.isEditable && input.key === 'Enter') {
    return null;
  }

  if (input.context === 'learning-input') {
    if (input.key !== 'Enter' || !hasNoModifier(input)) {
      return null;
    }
    if (input.isInteractiveTarget && !input.isEditable) {
      return null;
    }
    return { type: 'submit-answer' };
  }

  if (input.context === 'rating') {
    if (!hasNoModifier(input)) {
      return null;
    }
    const rating = RATING_ACTIONS[input.key];
    return rating === undefined ? null : { type: 'rate', rating };
  }

  if (input.context === 'card-select') {
    if (!hasNoModifier(input)) {
      return null;
    }
    const index = Number(input.key);
    return Number.isInteger(index) && index >= 1 && index <= 6
      ? { type: 'select-card', index }
      : null;
  }

  return null;
}
