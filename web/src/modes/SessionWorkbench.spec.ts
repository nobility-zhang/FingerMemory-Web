import 'fake-indexeddb/auto';
import { createApp, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PracticeItem, PracticeSession } from '../core/domain';
import { createDefaultReviewScheduler } from '../scheduler/fsrsScheduler';
import { createPracticeSession } from '../session/practiceSession';
import {
  FingerMemoryDatabase,
  STORAGE_SCHEMA_VERSION,
} from '../storage/database';
import {
  createFingerMemoryRepository,
} from '../storage/localRepository';
import SessionWorkbench from './SessionWorkbench.vue';

const now = Date.parse('2026-11-15T01:00:00.000Z');
const scheduler = createDefaultReviewScheduler();
const databaseName = 'finger-memory-mode-test';

let database: FingerMemoryDatabase;
let repository: ReturnType<typeof createFingerMemoryRepository>;
let items: PracticeItem[];
let session: PracticeSession;
let mountedApp: ReturnType<typeof createApp> | null = null;

function createItems(): PracticeItem[] {
  return [
    {
      wordEntry: {
        id: 'word-memory',
        term: 'memory',
        meaning: '记忆',
        acceptedAnswers: ['memory'],
        hints: ['内存也是它'],
        normalizedTerm: 'memory',
        createdAt: now,
        updatedAt: now,
      },
      studyItem: {
        id: 'study-memory',
        wordEntryId: 'word-memory',
        direction: 'meaning-to-spelling',
        createdAt: now,
        updatedAt: now,
      },
    },
    {
      wordEntry: {
        id: 'word-cursor',
        term: 'cursor',
        meaning: '光标',
        acceptedAnswers: ['cursor'],
        hints: ['屏幕上的位置标记'],
        normalizedTerm: 'cursor',
        createdAt: now,
        updatedAt: now,
      },
      studyItem: {
        id: 'study-cursor',
        wordEntryId: 'word-cursor',
        direction: 'meaning-to-spelling',
        createdAt: now,
        updatedAt: now,
      },
    },
  ];
}

function buttonByText(root: HTMLElement, text: string): HTMLButtonElement | undefined {
  return [...root.querySelectorAll('button')].find((button) =>
    button.textContent?.includes(text),
  );
}

async function mountWorkbench(): Promise<{ root: HTMLElement; app: ReturnType<typeof createApp> }> {
  const root = document.createElement('div');
  document.body.append(root);
  const app = createApp(SessionWorkbench, {
    initialSession: session,
    items,
    repository,
    clock: () => now + 1,
    idFactory: (prefix: string) => `${prefix}:test`,
  });
  mountedApp = app;
  app.mount(root);
  await nextTick();
  return { root, app };
}

beforeEach(async () => {
  await FingerMemoryDatabase.delete(databaseName);
  database = new FingerMemoryDatabase(databaseName);
  repository = createFingerMemoryRepository(database, {
    scheduler,
    clock: () => now,
  });
  await repository.initialize();
  items = createItems();
  await database.words.bulkPut(items.map((item) => item.wordEntry));
  await database.studyItems.bulkPut(items.map((item) => item.studyItem));
  session = createPracticeSession({
    id: 'session-mode',
    modeId: 'lyrics-scroll',
    items,
    now,
    reduceMotion: false,
  });
  await repository.savePracticeSession(session);
});

afterEach(async () => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
  database.close();
  await FingerMemoryDatabase.delete(databaseName);
  Object.defineProperty(document, 'visibilityState', {
    configurable: true,
    value: 'visible',
  });
});

describe('SessionWorkbench modes', () => {
  it('keeps input ownership on the active lyric and preserves the draft after selection', async () => {
    const { root } = await mountWorkbench();
    const input = root.querySelector<HTMLInputElement>('#answer-input');
    const lines = root.querySelectorAll<HTMLButtonElement>('.lyric-line');

    expect(root.querySelector('#answer-title')?.textContent).toBe('记忆');
    expect(lines[0]?.getAttribute('aria-current')).toBe('step');
    expect(input).not.toBeNull();

    input!.value = 'mem';
    input!.dispatchEvent(new Event('input', { bubbles: true }));
    lines[1]!.click();

    await vi.waitFor(() => {
      expect(root.querySelector('#answer-title')?.textContent).toBe('光标');
      expect(lines[1]?.getAttribute('aria-current')).toBe('step');
    });

    lines[0]!.click();
    await vi.waitFor(() => {
      expect(root.querySelector<HTMLInputElement>('#answer-input')?.value).toBe('mem');
    });
  });

  it('submits an explicit review in lyrics mode and reads the same state after switching to keyboard mode', async () => {
    const { root } = await mountWorkbench();
    const input = root.querySelector<HTMLInputElement>('#answer-input');
    input!.value = 'memory';
    input!.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    buttonByText(root, '提交')?.click();

    await vi.waitFor(() => {
      expect(buttonByText(root, 'Good')).toBeDefined();
    });
    expect(buttonByText(root, 'Again')).toBeDefined();
    await vi.waitFor(() => {
      expect(buttonByText(root, 'Good')?.disabled).toBe(false);
    });
    expect(
      [...root.querySelectorAll('.rating-button small')].map((node) =>
        node.textContent?.trim(),
      ),
    ).not.toContain('预计时间不可用');
    buttonByText(root, 'Good')?.click();

    await vi.waitFor(async () => {
      await expect(repository.getReviewState('study-memory')).resolves.toBeDefined();
    });
    expect(root.textContent).toContain('已完成 1 / 2');

    await vi.waitFor(() => {
      expect(buttonByText(root, '键盘对照')?.disabled).toBe(false);
    });
    buttonByText(root, '键盘对照')?.click();
    await vi.waitFor(() => {
      expect(root.querySelector('#session-title')?.textContent).toBe('键盘对照');
    });
    await expect(repository.getStatus()).resolves.toMatchObject({
      schemaVersion: STORAGE_SCHEMA_VERSION,
      counts: { reviewStates: 1, reviewLogs: 1, practiceSessions: 1 },
    });
  });

  it('records hint use and skip as practice-only without review state changes', async () => {
    const { root } = await mountWorkbench();
    buttonByText(root, '提示')?.click();

    await vi.waitFor(() => {
      expect(root.textContent).toContain('内存也是它');
    });

    const input = root.querySelector<HTMLInputElement>('#answer-input');
    input!.value = 'memory';
    input!.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    buttonByText(root, '提交')?.click();
    await vi.waitFor(() => {
      expect(root.textContent).toContain('仅记录练习');
    });
    expect(buttonByText(root, 'Good')).toBeUndefined();
    buttonByText(root, '记录练习并继续')?.click();

    await vi.waitFor(async () => {
      await expect(repository.getStatus()).resolves.toMatchObject({
        counts: { practiceAttempts: 1, reviewStates: 0, reviewLogs: 0 },
      });
    });

    await vi.waitFor(() => {
      expect(buttonByText(root, '跳过')?.disabled).toBe(false);
    });
    buttonByText(root, '跳过')?.click();
    await vi.waitFor(async () => {
      await expect(repository.getStatus()).resolves.toMatchObject({
        counts: { practiceAttempts: 2, reviewStates: 0, reviewLogs: 0 },
      });
    });
  });

  it('offers copy practice after revealing the answer without creating a formal review', async () => {
    const { root } = await mountWorkbench();
    buttonByText(root, '看答案抄写')?.click();
    await vi.waitFor(() => {
      expect(root.textContent).toContain('抄写练习');
      expect(root.querySelector('.answer-reveal')?.textContent).toContain('memory');
    });

    const input = root.querySelector<HTMLInputElement>('#answer-input');
    input!.value = 'memory';
    input!.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    buttonByText(root, '提交')?.click();
    await vi.waitFor(() => {
      expect(buttonByText(root, '记录练习并继续')).toBeDefined();
      expect(buttonByText(root, 'Good')).toBeUndefined();
    });
    buttonByText(root, '记录练习并继续')?.click();
    await vi.waitFor(async () => {
      await expect(repository.getStatus()).resolves.toMatchObject({
        counts: { practiceAttempts: 1, reviewStates: 0, reviewLogs: 0 },
      });
    });
  });

  it('adds a wrong target to the same session queue at most once for retry', async () => {
    const { root } = await mountWorkbench();
    const input = root.querySelector<HTMLInputElement>('#answer-input');
    input!.value = 'wrong';
    input!.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    buttonByText(root, '提交')?.click();
    await vi.waitFor(() => {
      expect(buttonByText(root, '加入错词重练')).toBeDefined();
    });
    buttonByText(root, '加入错词重练')?.click();

    await vi.waitFor(async () => {
      await expect(repository.getStatus()).resolves.toMatchObject({
        counts: { practiceAttempts: 1, reviewStates: 0, reviewLogs: 0 },
      });
      await expect(repository.getLatestPracticeSession()).resolves.toMatchObject({
        currentStudyItemId: 'study-cursor',
        queue: [
          { studyItemId: 'study-memory', status: 'completed' },
          { studyItemId: 'study-cursor', status: 'pending' },
          { studyItemId: 'study-memory', status: 'pending' },
        ],
      });
    });
    expect(root.textContent).toContain('已完成 1 / 3');
    expect(root.querySelector<HTMLInputElement>('#answer-input')?.value).toBe('');
  });

  it('does not submit while the IME is composing', async () => {
    const { root } = await mountWorkbench();
    const input = root.querySelector<HTMLInputElement>('#answer-input')!;
    input.value = 'memory';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    input.dispatchEvent(
      new KeyboardEvent('keydown', {
        key: 'Enter',
        isComposing: true,
        bubbles: true,
      }),
    );
    await nextTick();
    expect(buttonByText(root, 'Good')).toBeUndefined();
    await expect(repository.getLatestPracticeSession()).resolves.toMatchObject({
      pendingAnswer: null,
    });
    input.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }),
    );
    await vi.waitFor(() => {
      expect(buttonByText(root, 'Good')).toBeDefined();
    });
  });

  it('can return from rating preview to editing without writing a review', async () => {
    const { root } = await mountWorkbench();
    const input = root.querySelector<HTMLInputElement>('#answer-input');
    input!.value = 'memory';
    input!.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    buttonByText(root, '提交')?.click();

    await vi.waitFor(() => {
      expect(buttonByText(root, '返回修改')?.disabled).toBe(false);
    });
    buttonByText(root, '返回修改')?.click();
    await vi.waitFor(() => {
      expect(root.querySelector<HTMLInputElement>('#answer-input')?.value).toBe('memory');
      expect(buttonByText(root, 'Good')).toBeUndefined();
    });
    await expect(repository.getStatus()).resolves.toMatchObject({
      counts: { practiceAttempts: 0, reviewStates: 0, reviewLogs: 0 },
    });
  });

  it('explicitly abandons the current session without deleting practice history', async () => {
    const { root } = await mountWorkbench();
    buttonByText(root, '放弃会话')?.click();
    await nextTick();
    buttonByText(root, '确认放弃')?.click();

    await vi.waitFor(async () => {
      await expect(repository.getLatestPracticeSession()).resolves.toMatchObject({
        status: 'completed',
        endedReason: 'abandoned',
      });
    });
    await expect(repository.getStatus()).resolves.toMatchObject({
      counts: { practiceSessions: 1, reviewStates: 0, reviewLogs: 0 },
    });
  });

  it('persists speed and reduced-motion changes in the same session', async () => {
    const { root } = await mountWorkbench();
    const speed = root.querySelector<HTMLInputElement>('input[type="range"]');
    const reduceMotion = root.querySelector<HTMLInputElement>('input[type="checkbox"]');

    speed!.value = '1.6';
    speed!.dispatchEvent(new Event('change', { bubbles: true }));
    await vi.waitFor(async () => {
      await expect(repository.getLatestPracticeSession()).resolves.toMatchObject({ speed: 1.6 });
    });

    reduceMotion!.click();
    await vi.waitFor(async () => {
      await expect(repository.getLatestPracticeSession()).resolves.toMatchObject({
        reduceMotion: true,
      });
    });
  });

  it('auto-pauses in the background, removes listeners on unmount and creates no audio element', async () => {
    const addListener = vi.spyOn(document, 'addEventListener');
    const removeListener = vi.spyOn(document, 'removeEventListener');
    const intervalSpy = vi.spyOn(window, 'setInterval');
    const animationFrameSpy = vi.spyOn(window, 'requestAnimationFrame');
    const { root, app } = await mountWorkbench();
    const visibilityRegistration = addListener.mock.calls.find(
      ([eventName]) => eventName === 'visibilitychange',
    );

    expect(visibilityRegistration).toBeDefined();
    expect(root.querySelector('audio')).toBeNull();
    expect(intervalSpy).not.toHaveBeenCalled();
    expect(animationFrameSpy).not.toHaveBeenCalled();

    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'hidden',
    });
    document.dispatchEvent(new Event('visibilitychange'));

    await vi.waitFor(async () => {
      await expect(repository.getLatestPracticeSession()).resolves.toMatchObject({
        status: 'paused',
      });
    });

    app.unmount();
    mountedApp = null;
    expect(removeListener).toHaveBeenCalledWith(
      'visibilitychange',
      visibilityRegistration?.[1],
    );
    addListener.mockRestore();
    removeListener.mockRestore();
    intervalSpy.mockRestore();
    animationFrameSpy.mockRestore();
  });
});
