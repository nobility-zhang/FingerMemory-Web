import 'fake-indexeddb/auto';
import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createDefaultReviewScheduler } from '../scheduler/fsrsScheduler';
import { createPracticeSession } from '../session/practiceSession';
import { FingerMemoryDatabase } from '../storage/database';
import { createFingerMemoryRepository } from '../storage/localRepository';
import PracticeWorkspace from './PracticeWorkspace.vue';

const databaseName = 'finger-memory-practice-workspace-test';
const scheduler = createDefaultReviewScheduler();
let database: FingerMemoryDatabase | null = null;
let mountedApp: ReturnType<typeof createApp> | null = null;

afterEach(async () => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
  database?.close();
  database = null;
  await FingerMemoryDatabase.delete(databaseName);
});

describe('PracticeWorkspace', () => {
  it('keeps a newly started session resumable after exiting the mode', async () => {
    await FingerMemoryDatabase.delete(databaseName);
    database = new FingerMemoryDatabase(databaseName);
    const repository = createFingerMemoryRepository(database, {
      scheduler,
      clock: () => Date.now(),
    });
    await repository.initialize();
    const lexicon = await repository.createLexicon({
      name: '恢复测试词库',
      description: '',
      now: Date.now(),
    });
    await repository.createStudyItem({
      lexiconId: lexicon.id,
      term: 'memory',
      meaning: '记忆',
      acceptedAnswers: [],
      hints: [],
      now: Date.now() + 1,
    });

    const root = document.createElement('div');
    document.body.append(root);
    const app = createApp(PracticeWorkspace, { repository });
    mountedApp = app;
    app.mount(root);

    await vi.waitFor(() => {
      expect(root.textContent).toContain('开始歌词滚动');
    });
    buttonByText(root, '开始歌词滚动')?.click();
    await vi.waitFor(() => {
      expect(root.textContent).toContain('退出模式');
    });
    buttonByText(root, '退出模式')?.click();
    await vi.waitFor(() => {
      expect(root.textContent).toContain('打开上次会话');
    });
    buttonByText(root, '放弃上次会话')?.click();
    await vi.waitFor(async () => {
      await expect(repository.getLatestPracticeSession()).resolves.toMatchObject({
        status: 'completed',
        endedReason: 'abandoned',
      });
      expect(root.textContent).not.toContain('打开上次会话');
    });
  });

  it('restores an unfinished session after its lexicon is deleted', async () => {
    await FingerMemoryDatabase.delete(databaseName);
    database = new FingerMemoryDatabase(databaseName);
    const repository = createFingerMemoryRepository(database, {
      scheduler,
      clock: () => Date.now(),
      idFactory: () => 'recovery-test',
    });
    await repository.initialize();
    const lexicon = await repository.createLexicon({
      name: '待删除词库',
      description: '',
      now: Date.now(),
    });
    const created = await repository.createStudyItem({
      lexiconId: lexicon.id,
      term: 'memory',
      meaning: '记忆',
      acceptedAnswers: [],
      hints: [],
      now: Date.now() + 1,
    });
    await repository.savePracticeSession(
      createPracticeSession({
        id: 'recoverable-session',
        modeId: 'lyrics-scroll',
        items: [created.practiceItem],
        now: Date.now() + 2,
        reduceMotion: false,
      }),
    );
    await repository.deleteLexicon(lexicon.id);

    const root = document.createElement('div');
    document.body.append(root);
    const app = createApp(PracticeWorkspace, { repository });
    mountedApp = app;
    app.mount(root);

    await vi.waitFor(() => {
      expect(root.textContent).toContain('打开上次会话');
    });
    const continueButton = [...root.querySelectorAll('button')].find((button) =>
      button.textContent?.includes('打开上次会话'),
    );
    continueButton?.click();
    await nextTick();

    await vi.waitFor(() => {
      expect(root.textContent).toContain('记忆');
    });
    expect(root.textContent).toContain('歌词滚动');
  });
});

function buttonByText(root: HTMLElement, text: string): HTMLButtonElement | undefined {
  return [...root.querySelectorAll('button')].find((button) =>
    button.textContent?.trim().includes(text),
  );
}
