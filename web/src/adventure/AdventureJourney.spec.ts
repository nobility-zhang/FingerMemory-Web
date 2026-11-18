import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AdventureRunView } from '../api/client';
import { lia } from '../companions/catalog';
import AdventureJourney from './AdventureJourney.vue';

function run(overrides: Partial<AdventureRunView> = {}): AdventureRunView {
  return {
    id: 'run-journey',
    installationId: 'installation-journey',
    namespace: 'fm.demo',
    bookId: 'warm-lantern',
    contentVersion: '1.0.0',
    contentHash: 'abcdef12'.repeat(8),
    title: '暖灯与失语之森',
    companionKey: 'lia',
    status: 'active',
    runVersion: 2,
    sessionId: 'session-journey',
    currentNode: {
      nodeId: 'inn.arrival',
      chapterId: 'chapter-1',
      type: 'choice',
      title: '暖灯旅店',
      speaker: '',
      text: '信纸边缘又淡了一角。',
      choices: [{ choiceId: 'prepare', label: '拿起暖灯，准备出发' }],
    },
    flags: {},
    createdAtMs: 1,
    updatedAtMs: 2,
    ...overrides,
  };
}

let mountedApp: ReturnType<typeof createApp> | null = null;

afterEach(() => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
});

function mountJourney(
  adventure: AdventureRunView,
  handlers: Record<string, (...args: unknown[]) => void> = {},
): HTMLElement {
  const root = document.createElement('div');
  document.body.append(root);
  mountedApp = createApp(AdventureJourney, {
    run: adventure,
    companion: lia,
    loading: false,
    busy: false,
    error: null,
    notice: null,
    ...handlers,
  });
  mountedApp.mount(root);
  return root;
}

function buttonByText(root: ParentNode, text: string): HTMLButtonElement {
  return [...root.querySelectorAll<HTMLButtonElement>('button')].find(
    (button) => button.textContent?.includes(text),
  )!;
}

describe('AdventureJourney', () => {
  it('renders the fixed package version and emits a real choice', async () => {
    const choose = vi.fn();
    const root = mountJourney(run(), { onChoose: choose });
    await nextTick();

    const scene = root.querySelector<HTMLElement>('.adventure-journey');
    expect(scene?.dataset.contentVersion).toBe('1.0.0');
    expect(scene?.dataset.contentHash).toHaveLength(64);
    expect(root.textContent).toContain('abcdef12');
    buttonByText(root, '拿起暖灯').click();
    expect(choose).toHaveBeenCalledWith('prepare');
  });

  it('moves through camp and completes from the chapter-end checkpoint', async () => {
    const advance = vi.fn();
    const complete = vi.fn();
    const root = mountJourney(run({
      currentNode: {
        nodeId: 'camp.fire',
        chapterId: 'chapter-1',
        type: 'camp',
        title: '营火',
        speaker: '旅伴',
        text: '营火把路标照得清清楚楚。',
        choices: [],
      },
    }), {
      onAdvance: advance,
      onComplete: complete,
    });
    await nextTick();

    expect(root.textContent).toContain('莉娅');
    buttonByText(root, '在营火旁收好笔记').click();
    expect(advance).toHaveBeenCalledOnce();

    mountedApp?.unmount();
    document.body.innerHTML = '';
    const ending = mountJourney(run({
      runVersion: 8,
      currentNode: {
        nodeId: 'chapter1.end',
        chapterId: 'chapter-1',
        type: 'chapter-end',
        title: '第一章完成',
        speaker: '',
        text: '第一段旅途已经写入存档。',
        choices: [],
      },
    }), { onComplete: complete });
    await nextTick();
    buttonByText(ending, '完成第一章').click();
    expect(complete).toHaveBeenCalledOnce();
  });

  it('uses dedicated chapter scene art outside the inn', async () => {
    const forest = mountJourney(run({
      currentNode: {
        nodeId: 'forest.signpost',
        chapterId: 'chapter-1',
        type: 'choice',
        title: '失语之森',
        speaker: '',
        text: '两道路痕都通向溪桥。',
        choices: [{ choiceId: 'cross', label: '沿旧路前进' }],
      },
    }));
    await nextTick();
    expect(
      forest.querySelector<HTMLElement>('.adventure-journey')?.style
        .getPropertyValue('--scene-background'),
    ).toContain('forest-path');

    mountedApp?.unmount();
    document.body.innerHTML = '';
    const camp = mountJourney(run({
      currentNode: {
        nodeId: 'camp.fire',
        chapterId: 'chapter-1',
        type: 'camp',
        title: '营火',
        speaker: '旅伴',
        text: '营火把路标照亮。',
        choices: [],
      },
    }));
    await nextTick();
    expect(
      camp.querySelector<HTMLElement>('.adventure-journey')?.style
        .getPropertyValue('--scene-background'),
    ).toContain('camp-fire');
  });
});
