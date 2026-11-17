import { describe, expect, it } from 'vitest';
import {
  resolveFlashcardShortcut,
  type FlashcardShortcutInput,
} from './shortcutStateMachine';

function shortcut(
  overrides: Partial<FlashcardShortcutInput> = {},
) {
  return resolveFlashcardShortcut({
    key: ' ',
    phase: 'front',
    isComposing: false,
    repeat: false,
    isInteractiveTarget: false,
    notRemembered: false,
    ...overrides,
  });
}

describe('flashcard shortcut state machine', () => {
  it('flips with Space only on the recall side', () => {
    expect(shortcut()).toBe('flip');
    expect(shortcut({ key: 'Spacebar' })).toBe('flip');
    expect(shortcut({ phase: 'rating' })).toBeNull();
  });

  it('records a direct not-remembered action with N', () => {
    expect(shortcut({ key: 'n' })).toBe('not-remembered');
    expect(shortcut({ key: 'N' })).toBe('not-remembered');
  });

  it('maps 1-4 after reveal and limits direct not-remembered to Again', () => {
    expect(shortcut({ phase: 'rating', key: '1' })).toBe('rate-again');
    expect(shortcut({ phase: 'rating', key: '4' })).toBe('rate-easy');
    expect(shortcut({
      phase: 'rating',
      key: '1',
      notRemembered: true,
    })).toBe('rate-again');
    expect(shortcut({
      phase: 'rating',
      key: '3',
      notRemembered: true,
    })).toBeNull();
  });

  it('does not steal IME, repeat, modified or interactive-button keys', () => {
    expect(shortcut({ isComposing: true })).toBeNull();
    expect(shortcut({ repeat: true })).toBeNull();
    expect(shortcut({ ctrlKey: true })).toBeNull();
    expect(shortcut({ isInteractiveTarget: true })).toBeNull();
    expect(shortcut({ phase: 'rating', key: '3', metaKey: true })).toBeNull();
  });
});
