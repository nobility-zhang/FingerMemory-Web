import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type {
  FingerMemoryRepository,
  StorageStatus,
} from '../core/repository';
import App from './App.vue';

const readyStatus: StorageStatus = {
  schemaVersion: 3,
  dataFormatVersion: 3,
  scheduler: {
    packageVersion: '6.0.0',
    modelVersion: 'fsrs-7',
  },
  counts: {
    lexicons: 0,
    words: 0,
    studyItems: 0,
    lexiconItems: 0,
    notes: 0,
    practiceAttempts: 0,
    practiceSessions: 0,
    reviewStates: 0,
    reviewLogs: 0,
  },
};

let mountedApp: ReturnType<typeof createApp> | null = null;

function createTestRepository(
  initialize: FingerMemoryRepository['initialize'],
): FingerMemoryRepository {
  return {
    initialize,
    getStatus: async () => readyStatus,
    ensureStarterContent: async () => [],
    listLexicons: async () => [],
    createLexicon: async () => {
      throw new Error('createLexicon is not used in this test');
    },
    updateLexicon: async () => {
      throw new Error('updateLexicon is not used in this test');
    },
    setLexiconArchived: async () => {
      throw new Error('setLexiconArchived is not used in this test');
    },
    deleteLexicon: async () => undefined,
    listLexiconItems: async () => [],
    createStudyItem: async () => {
      throw new Error('createStudyItem is not used in this test');
    },
    updateStudyItem: async () => {
      throw new Error('updateStudyItem is not used in this test');
    },
    removeStudyItemFromLexicon: async () => undefined,
    importStudyItems: async () => {
      throw new Error('importStudyItems is not used in this test');
    },
    listStudyNotes: async () => [],
    saveStudyNote: async () => undefined,
    listPracticeItems: async () => [],
    listPracticeItemsByStudyItemIds: async () => [],
    getLatestPracticeSession: async () => undefined,
    savePracticeSession: async (session) => session,
    recordPracticeAttempt: async () => {
      throw new Error('recordPracticeAttempt is not used in this test');
    },
    submitReview: async () => {
      throw new Error('submitReview is not used in this test');
    },
    getReviewState: async () => undefined,
    getReviewLog: async () => undefined,
    listDueReviewItems: async () => [],
    exportSnapshot: async () => {
      throw new Error('exportSnapshot is not used in this test');
    },
    importSnapshot: async () => readyStatus,
  };
}

async function flushAsyncWork(): Promise<void> {
  await Promise.resolve();
  await nextTick();
}

describe('App', () => {
  afterEach(() => {
    mountedApp?.unmount();
    mountedApp = null;
  });

  it('shows storage initialization and then reports an empty database as ready', async () => {
    const root = document.createElement('div');
    const repository = createTestRepository(vi.fn().mockResolvedValue(readyStatus));
    mountedApp = createApp(App, { repository });

    mountedApp.mount(root);

    expect(root.querySelector('[data-testid="workspace-state"]')?.textContent?.trim()).toBe(
      '初始化中',
    );

    await flushAsyncWork();

    expect(root.querySelector('h1')?.textContent).toBe('学习工作台');
    expect(root.querySelector('nav')?.getAttribute('aria-label')).toBe('主导航');
    expect(root.querySelector('[data-testid="storage-summary"]')?.textContent?.trim()).toBe(
      'FSRS-7 · 数据 v3',
    );
    await vi.waitFor(() => {
      expect(root.textContent).toContain('本地还没有可练习的词条');
    });

    const libraryButton = [...root.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === '词库',
    );
    libraryButton?.click();
    await vi.waitFor(() => {
      expect(root.querySelector('h1')?.textContent).toBe('词库管理');
      expect(root.textContent).toContain('还没有词库');
    });

    const dataButton = [...root.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === '数据',
    );
    dataButton?.click();
    await vi.waitFor(() => {
      expect(root.querySelector('h1')?.textContent).toBe('数据管理');
      expect(root.textContent).toContain('下载完整备份');
    });
  });

  it('shows a local storage error when initialization fails', async () => {
    const root = document.createElement('div');
    const repository = createTestRepository(async () => {
      throw new Error('IndexedDB unavailable');
    });
    mountedApp = createApp(App, { repository });

    mountedApp.mount(root);
    await flushAsyncWork();

    expect(root.querySelector('[data-testid="workspace-state"]')?.textContent?.trim()).toBe(
      '不可用',
    );
    expect(root.querySelector('[data-testid="storage-message"]')?.textContent?.trim()).toBe(
      '请检查浏览器是否允许使用 IndexedDB。',
    );
  });
});
