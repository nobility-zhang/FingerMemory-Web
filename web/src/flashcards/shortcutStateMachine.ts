export type FlashcardPhase = 'front' | 'rating';

export type FlashcardShortcutAction =
  | 'flip'
  | 'not-remembered'
  | 'rate-again'
  | 'rate-hard'
  | 'rate-good'
  | 'rate-easy';

export interface FlashcardShortcutInput {
  key: string;
  phase: FlashcardPhase;
  isComposing: boolean;
  repeat: boolean;
  isInteractiveTarget: boolean;
  notRemembered: boolean;
  ctrlKey?: boolean;
  metaKey?: boolean;
  altKey?: boolean;
  shiftKey?: boolean;
}

export function resolveFlashcardShortcut(
  input: FlashcardShortcutInput,
): FlashcardShortcutAction | null {
  if (input.isComposing || input.repeat) {
    return null;
  }
  if (
    input.ctrlKey === true
    || input.metaKey === true
    || input.altKey === true
    || input.shiftKey === true
  ) {
    return null;
  }

  if (input.phase === 'front') {
    if (input.isInteractiveTarget) {
      return null;
    }
    if (input.key === ' ' || input.key === 'Spacebar') {
      return 'flip';
    }
    if (input.key.toLowerCase() === 'n') {
      return 'not-remembered';
    }
    return null;
  }

  if (input.notRemembered && input.key !== '1') {
    return null;
  }
  const actions: Record<string, FlashcardShortcutAction> = {
    '1': 'rate-again',
    '2': 'rate-hard',
    '3': 'rate-good',
    '4': 'rate-easy',
  };
  return actions[input.key] ?? null;
}
