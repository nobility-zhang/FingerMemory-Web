export type GamePhase =
  | 'login'
  | 'lobby'
  | 'typing'
  | 'answer-ready'
  | 'rating'
  | 'paused'
  | 'result';

export type GameShortcutAction =
  | 'submit-answer'
  | 'complete-practice'
  | 'rate-again'
  | 'rate-hard'
  | 'rate-good'
  | 'rate-easy'
  | 'show-hint'
  | 'reveal-answer'
  | 'skip-item'
  | 'pause'
  | 'resume'
  | 'toggle-help'
  | 'close-help'
  | 'start-or-resume'
  | 'return-lobby'
  | 'toggle-focus';

export interface KeyboardShortcutInput {
  key: string;
  phase: GamePhase;
  isComposing: boolean;
  repeat: boolean;
  isEditable: boolean;
  helpOpen: boolean;
  ctrlKey?: boolean;
  metaKey?: boolean;
  shiftKey?: boolean;
  altKey?: boolean;
}

function hasOnlyCtrl(input: KeyboardShortcutInput): boolean {
  return input.ctrlKey === true
    && input.metaKey !== true
    && input.altKey !== true
    && input.shiftKey !== true;
}

function hasNoCommandModifier(input: KeyboardShortcutInput): boolean {
  return input.ctrlKey !== true
    && input.metaKey !== true
    && input.altKey !== true;
}

export function resolveGameShortcut(
  input: KeyboardShortcutInput,
): GameShortcutAction | null {
  if (input.isComposing) {
    return null;
  }
  if (input.helpOpen) {
    return input.key === 'Escape' ? 'close-help' : null;
  }
  if (input.key === 'Tab' || input.metaKey === true || input.altKey === true) {
    return null;
  }

  if (input.ctrlKey === true && input.shiftKey === true) {
    if (input.key.toLowerCase() === 'f') {
      return 'toggle-focus';
    }
    if (input.phase === 'typing' && input.key === 'Enter') {
      return input.repeat ? null : 'skip-item';
    }
  }
  if (hasOnlyCtrl(input)) {
    if (input.phase === 'typing' && input.key.toLowerCase() === 'h') {
      return 'show-hint';
    }
    if (input.phase === 'typing' && input.key === 'Enter') {
      return input.repeat ? null : 'reveal-answer';
    }
  }

  if (
    input.key === '?'
    && !input.isEditable
    && hasNoCommandModifier(input)
  ) {
    return 'toggle-help';
  }

  if (input.key === 'Escape') {
    if (input.repeat) {
      return null;
    }
    if (input.phase === 'typing' || input.phase === 'answer-ready'
        || input.phase === 'rating') {
      return 'pause';
    }
    return null;
  }

  if (input.phase === 'rating' && hasNoCommandModifier(input) && !input.shiftKey) {
    const actions: Record<string, GameShortcutAction> = {
      '1': 'rate-again',
      '2': 'rate-hard',
      '3': 'rate-good',
      '4': 'rate-easy',
    };
    return input.repeat ? null : actions[input.key] ?? null;
  }

  if (input.key === 'Enter' && hasNoCommandModifier(input)
      && input.phase === 'answer-ready') {
    return input.repeat ? null : 'complete-practice';
  }

  if (input.key === 'Enter' && hasNoCommandModifier(input)) {
    if (input.repeat) {
      return null;
    }
    if (input.phase === 'typing') {
      return 'submit-answer';
    }
    if (input.phase === 'lobby') {
      return input.isEditable ? null : 'start-or-resume';
    }
    if (input.phase === 'paused') {
      return 'resume';
    }
    if (input.phase === 'result') {
      return 'return-lobby';
    }
  }

  return null;
}
