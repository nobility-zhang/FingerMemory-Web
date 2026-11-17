import { describe, expect, it, vi } from 'vitest';
import { GameEventBridge } from './eventBridge';

describe('GameEventBridge', () => {
  it('delivers typed events and unsubscribes only the removed listener', () => {
    const bridge = new GameEventBridge();
    const first = vi.fn();
    const second = vi.fn();
    const removeFirst = bridge.on(first);
    bridge.on(second);

    bridge.emit({ type: 'audio-mute', muted: true });
    removeFirst();
    bridge.emit({ type: 'game-ready' });

    expect(first).toHaveBeenCalledOnce();
    expect(second).toHaveBeenCalledTimes(2);
  });

  it('clears every subscription when the owning game is destroyed', () => {
    const bridge = new GameEventBridge();
    const listener = vi.fn();
    bridge.on(listener);
    bridge.clear();
    bridge.emit({ type: 'game-destroyed' });
    expect(listener).not.toHaveBeenCalled();
  });
});
