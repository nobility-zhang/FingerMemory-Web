import { describe, expect, it } from 'vitest';
import {
  resolveBattleShortcut,
  type BattleShortcutContext,
  type BattleShortcutInput,
} from './shortcutStateMachine';

function shortcut(
  context: BattleShortcutContext,
  key: string,
  overrides: Partial<BattleShortcutInput> = {},
) {
  return resolveBattleShortcut({
    key,
    context,
    endTurnShortcut: 'ctrl+enter',
    isComposing: false,
    repeat: false,
    isEditable: false,
    isInteractiveTarget: false,
    helpOpen: false,
    ...overrides,
  });
}

describe('battle shortcut state machine', () => {
  it('submits learning input only without IME, repeat or command modifiers', () => {
    expect(shortcut('learning-input', 'Enter')).toEqual({ type: 'submit-answer' });
    expect(shortcut('learning-input', 'Enter', { isEditable: true }))
      .toEqual({ type: 'submit-answer' });
    expect(shortcut('learning-input', 'Enter', { isComposing: true })).toBeNull();
    expect(shortcut('learning-input', 'Enter', { repeat: true })).toBeNull();
    expect(shortcut('learning-input', 'Enter', { metaKey: true })).toBeNull();
    expect(shortcut('learning-input', '3')).toBeNull();
  });

  it('keeps rating keys separate from card numbers and target confirmation', () => {
    expect(shortcut('rating', '1')).toEqual({ type: 'rate', rating: 'again' });
    expect(shortcut('rating', '2')).toEqual({ type: 'rate', rating: 'hard' });
    expect(shortcut('rating', '3')).toEqual({ type: 'rate', rating: 'good' });
    expect(shortcut('rating', '4')).toEqual({ type: 'rate', rating: 'easy' });
    expect(shortcut('rating', '5')).toBeNull();

    expect(shortcut('card-select', '1')).toEqual({ type: 'select-card', index: 1 });
    expect(shortcut('card-select', '6')).toEqual({ type: 'select-card', index: 6 });
    expect(shortcut('card-select', '7')).toBeNull();
    expect(shortcut('target-select', '1')).toBeNull();
  });

  it('cycles and confirms targets while Escape cancels selection before pausing', () => {
    expect(shortcut('target-select', 'ArrowLeft'))
      .toEqual({ type: 'cycle-target', direction: 'previous' });
    expect(shortcut('target-select', 'ArrowDown'))
      .toEqual({ type: 'cycle-target', direction: 'next' });
    expect(shortcut('target-select', 'Enter')).toEqual({ type: 'confirm-target' });
    expect(shortcut('target-select', 'Escape')).toEqual({ type: 'cancel-target' });
    expect(shortcut('card-select', 'Escape')).toEqual({ type: 'pause' });
  });

  it('pauses and resumes without stealing native button activation', () => {
    expect(shortcut('card-select', 'Enter', { isInteractiveTarget: true })).toBeNull();
    expect(shortcut('paused', 'Enter')).toEqual({ type: 'resume' });
    expect(shortcut('paused', 'Escape')).toEqual({ type: 'resume' });
    expect(shortcut('result', 'Enter')).toEqual({ type: 'finish' });
    expect(shortcut('result', 'Enter', { isInteractiveTarget: true })).toBeNull();
  });

  it('uses the configured end-turn combo only during card selection', () => {
    expect(shortcut('card-select', 'Enter', { ctrlKey: true }))
      .toEqual({ type: 'end-turn' });
    expect(shortcut('card-select', 'Enter', {
      ctrlKey: true,
      altKey: true,
    })).toBeNull();
    expect(shortcut('rating', 'Enter', { ctrlKey: true })).toBeNull();
    expect(shortcut('card-select', 'Enter', {
      altKey: true,
      endTurnShortcut: 'alt+enter',
    })).toEqual({ type: 'end-turn' });
    expect(shortcut('card-select', 'Enter', {
      ctrlKey: true,
      shiftKey: true,
      endTurnShortcut: 'ctrl+shift+enter',
    })).toEqual({ type: 'end-turn' });
  });

  it('opens and closes help without eating text or browser shortcuts', () => {
    expect(shortcut('card-select', '?', { shiftKey: true }))
      .toEqual({ type: 'toggle-help' });
    expect(shortcut('learning-input', '?', { isEditable: true })).toBeNull();
    expect(shortcut('card-select', 'Tab')).toBeNull();
    expect(shortcut('card-select', 'c', { metaKey: true })).toBeNull();
    expect(shortcut('card-select', 'Escape', { helpOpen: true }))
      .toEqual({ type: 'close-help' });
    expect(shortcut('card-select', '?', { helpOpen: true, shiftKey: true }))
      .toEqual({ type: 'close-help' });
    expect(shortcut('card-select', '1', { helpOpen: true })).toBeNull();
    expect(shortcut('paused', '?', { shiftKey: true })).toBeNull();
  });
});
