import { describe, expect, it } from 'vitest';
import {
  resolveGameShortcut,
  type KeyboardShortcutInput,
  type GamePhase,
} from './shortcutStateMachine';

function shortcut(
  phase: GamePhase,
  key: string,
  overrides: Partial<KeyboardShortcutInput> = {},
) {
  return resolveGameShortcut({
    phase,
    key,
    isComposing: false,
    repeat: false,
    isEditable: false,
    helpOpen: false,
    ...overrides,
  });
}

describe('game shortcut state machine', () => {
  it('does not submit Enter while an IME is composing', () => {
    expect(shortcut('typing', 'Enter', { isComposing: true, isEditable: true })).toBeNull();
  });

  it('submits Enter only in the typing context and ignores key repeat', () => {
    expect(shortcut('typing', 'Enter', { isEditable: true })).toBe('submit-answer');
    expect(shortcut('typing', 'Enter', { isEditable: true, repeat: true })).toBeNull();
  });

  it('lets ordinary digits enter the answer but maps digits only in rating', () => {
    expect(shortcut('typing', '3', { isEditable: true })).toBeNull();
    expect(shortcut('rating', '1')).toBe('rate-again');
    expect(shortcut('rating', '2')).toBe('rate-hard');
    expect(shortcut('rating', '3')).toBe('rate-good');
    expect(shortcut('rating', '4')).toBe('rate-easy');
  });

  it('closes the top help dialog before handling training keys', () => {
    expect(shortcut('typing', 'Escape', { helpOpen: true, isEditable: true }))
      .toBe('close-help');
    expect(shortcut('rating', '3', { helpOpen: true })).toBeNull();
  });

  it('pauses training on Escape without intercepting Tab or browser shortcuts', () => {
    expect(shortcut('typing', 'Escape')).toBe('pause');
    expect(shortcut('rating', 'Escape')).toBe('pause');
    expect(shortcut('typing', 'Tab')).toBeNull();
    expect(shortcut('typing', 'c', { metaKey: true, isEditable: true })).toBeNull();
  });

  it('maps help, hint, reveal, skip and focus with explicit modifiers', () => {
    expect(shortcut('typing', '?')).toBe('toggle-help');
    expect(shortcut('typing', '?', { shiftKey: true })).toBe('toggle-help');
    expect(shortcut('typing', '?', { isEditable: true })).toBeNull();
    expect(shortcut('typing', 'h', { ctrlKey: true, isEditable: true })).toBe('show-hint');
    expect(shortcut('typing', 'Enter', { ctrlKey: true, isEditable: true }))
      .toBe('reveal-answer');
    expect(shortcut('typing', 'Enter', {
      ctrlKey: true,
      shiftKey: true,
      isEditable: true,
    })).toBe('skip-item');
    expect(shortcut('typing', 'F', { ctrlKey: true, shiftKey: true }))
      .toBe('toggle-focus');
  });

  it('handles the real lobby, pause and result continuation states', () => {
    expect(shortcut('lobby', 'Enter')).toBe('start-or-resume');
    expect(shortcut('paused', 'Enter')).toBe('resume');
    expect(shortcut('answer-ready', 'Enter')).toBe('complete-practice');
    expect(shortcut('result', 'Enter')).toBe('return-lobby');
  });
});
