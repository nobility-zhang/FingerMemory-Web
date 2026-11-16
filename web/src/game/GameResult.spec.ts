import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it } from 'vitest';
import { serin } from '../companions/catalog';
import GameResult from './GameResult.vue';

let mountedApp: ReturnType<typeof createApp> | null = null;

afterEach(() => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
});

describe('GameResult', () => {
  it('uses the selected companion and explicit Again response', async () => {
    const root = document.createElement('div');
    document.body.append(root);
    mountedApp = createApp(GameResult, {
      companion: serin,
      result: {
        reviewedCount: 1,
        practiceCount: 0,
        matchedCount: 1,
        totalAnswerCount: 1,
        startedAt: 1,
        endedAt: 2,
        ratings: { again: 1, hard: 0, good: 0, easy: 0 },
      },
      reduceMotion: true,
      lastRating: 'again',
    });
    mountedApp.mount(root);
    await nextTick();

    expect(root.textContent).toContain('瑟琳 · 古树契约的旅行祭司');
    expect(root.textContent).toContain('这一枚符文还不熟悉');
    expect(root.querySelector('[data-companion="serin"]')).not.toBeNull();
    expect(root.querySelector('[data-expression="concern"]')).not.toBeNull();
  });
});
