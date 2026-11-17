import { createApp, defineComponent, h, nextTick, ref } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { PracticeItem } from '../core/domain';
import FlashcardPanel from './FlashcardPanel.vue';

const item: PracticeItem = {
  wordEntry: {
    id: 'word-memory',
    term: 'memory',
    meaning: '记忆',
    acceptedAnswers: ['memory'],
    hints: [],
    normalizedTerm: 'memory',
    createdAt: 1,
    updatedAt: 1,
  },
  studyItem: {
    id: 'study-memory',
    wordEntryId: 'word-memory',
    direction: 'meaning-to-spelling',
    createdAt: 1,
    updatedAt: 1,
  },
};

let mountedApp: ReturnType<typeof createApp> | null = null;

afterEach(() => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
});

function mountPanel() {
  const flipped = ref(false);
  const paused = ref(true);
  const notRemembered = ref(false);
  const rated = vi.fn();
  let changePaused: ((value: boolean) => void) | undefined;
  const Host = defineComponent({
    setup() {
      changePaused = (value) => {
        paused.value = value;
      };
      return () => h(FlashcardPanel, {
        item,
        position: 1,
        total: 1,
        flipped: flipped.value,
        paused: paused.value,
        busy: false,
        notRemembered: notRemembered.value,
        onFlip: () => {
          flipped.value = true;
        },
        onNotRemembered: () => {
          flipped.value = true;
          notRemembered.value = true;
        },
        onRate: rated,
        onSkip: vi.fn(),
      });
    },
  });
  const root = document.createElement('div');
  document.body.append(root);
  const app = createApp(Host);
  mountedApp = app;
  app.mount(root);
  return {
    root,
    rated,
    setPaused: (value: boolean) => changePaused?.(value),
  };
}

describe('FlashcardPanel', () => {
  it('keeps the answer out of the DOM until Space flips the card', async () => {
    const panel = mountPanel();
    panel.setPaused(false);
    await vi.waitFor(() => {
      expect(document.activeElement?.classList.contains('flashcard-panel')).toBe(true);
      expect(panel.root.textContent).not.toContain('memory');
    });

    window.dispatchEvent(new KeyboardEvent('keydown', {
      key: ' ',
      bubbles: true,
      cancelable: true,
    }));
    await nextTick();

    expect(panel.root.textContent).toContain('memory');
    expect(panel.root.textContent).toContain('Again');
  });

  it('records direct not-remembered intent and limits rating to Again', async () => {
    const panel = mountPanel();
    panel.setPaused(false);
    await vi.waitFor(() => {
      expect(document.activeElement?.classList.contains('flashcard-panel')).toBe(true);
    });

    window.dispatchEvent(new KeyboardEvent('keydown', {
      key: 'n',
      bubbles: true,
      cancelable: true,
    }));
    await nextTick();

    expect(panel.root.textContent).toContain('你已经明确表示没想起');
    const hard = [...panel.root.querySelectorAll('button')].find(
      (button) => button.textContent?.includes('Hard'),
    );
    expect(hard?.disabled).toBe(true);
    window.dispatchEvent(new KeyboardEvent('keydown', {
      key: '3',
      bubbles: true,
      cancelable: true,
    }));
    await nextTick();
    expect(panel.rated).not.toHaveBeenCalled();
  });
});
