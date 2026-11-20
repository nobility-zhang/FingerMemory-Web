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
  it('starts the independent classic flashcard from the local queue', async () => {
    await FingerMemoryDatabase.delete(databaseName);
    database = new FingerMemoryDatabase(databaseName);
    const repository = createFingerMemoryRepository(database, {
      scheduler,
      clock: () => Date.now(),
    });
    await repository.initialize();
    const lexicon = await repository.createLexicon({
      name: '经典闪卡词库',
      description: '',
      now: Date.now(),
    });
    await repository.createStudyItem({
      lexiconId: lexicon.id,
      term: 'lantern',
      meaning: '提灯',
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
      expect(buttonByText(root, '开始经典闪卡')).toBeDefined();
    });
    buttonByText(root, '开始经典闪卡')?.click();
    await vi.waitFor(async () => {
      expect(root.textContent).toContain('经典闪卡');
      expect(root.textContent).toContain('提灯');
      await expect(repository.getLatestPracticeSession()).resolves.toMatchObject({
        modeId: 'flashcard',
      });
    });
  });

  it('honors disabled mode preferences and keeps one mode enabled', async () => {
    await FingerMemoryDatabase.delete(databaseName);
    database = new FingerMemoryDatabase(databaseName);
    const repository = createFingerMemoryRepository(database, {
      scheduler,
      clock: () => Date.now(),
    });
    await repository.initialize();
    const lexicon = await repository.createLexicon({
      name: '模式测试',
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
    await database.appMeta.put({
      key: 'practice.modePreferences',
      value: JSON.stringify({
        disabledModeIds: [
          'card-rain',
          'danmaku-stream',
          'keyboard-recall',
          'lyrics-scroll',
        ],
      }),
      updatedAt: Date.now() + 2,
    });

    const root = document.createElement('div');
    document.body.append(root);
    const app = createApp(PracticeWorkspace, { repository });
    mountedApp = app;
    app.mount(root);

    await vi.waitFor(() => {
      expect(buttonByText(root, '开始经典闪卡')).toBeDefined();
      expect(buttonByText(root, '开始键盘对照')).toBeUndefined();
    });
    const keyboardPreference = root.querySelector<HTMLInputElement>(
      '.mode-preferences input[type="checkbox"]',
    );
    keyboardPreference?.click();
    await vi.waitFor(() => {
      expect(buttonByText(root, '开始键盘对照')).toBeDefined();
    });
    await expect(repository.getPracticeModePreferences()).resolves.toEqual({
      disabledModeIds: [],
    });

    await vi.waitFor(() => {
      expect(buttonByText(root, '开始键盘对照')?.disabled).toBe(false);
    });
    buttonByText(root, '开始键盘对照')?.click();
    await vi.waitFor(() => {
      expect(root.querySelector('#session-title')?.textContent).toBe('键盘对照');
    });
  });

  it('uses configured queue limits and makes pause empty the next plan', async () => {
    await FingerMemoryDatabase.delete(databaseName);
    database = new FingerMemoryDatabase(databaseName);
    const repository = createFingerMemoryRepository(database, {
      scheduler,
      clock: () => Date.now(),
    });
    await repository.initialize();
    const lexicon = await repository.createLexicon({
      name: '队列界面测试',
      description: '',
      now: Date.now(),
    });
    for (const [index, term] of ['memory', 'cursor', 'window'].entries()) {
      await repository.createStudyItem({
        lexiconId: lexicon.id,
        term,
        meaning: ['记忆', '光标', '窗口'][index]!,
        acceptedAnswers: [],
        hints: [],
        now: Date.now() + index + 1,
      });
    }
    await repository.savePracticeQueueSettings(
      { newLimit: 2, reviewLimit: 0, paused: false },
      Date.now() + 10,
    );

    const root = document.createElement('div');
    document.body.append(root);
    const app = createApp(PracticeWorkspace, { repository });
    mountedApp = app;
    app.mount(root);

    await vi.waitFor(() => {
      expect(root.textContent).toContain('本次计划 2');
    });
    buttonByText(root, '开始键盘对照')?.click();
    await vi.waitFor(async () => {
      await expect(repository.getLatestPracticeSession()).resolves.toMatchObject({
        queue: [
          { source: 'new', status: 'pending' },
          { source: 'new', status: 'pending' },
        ],
      });
    });
    buttonByText(root, '退出模式')?.click();
    await vi.waitFor(() => {
      expect(root.textContent).toContain('暂停加入新内容');
    });

    root.querySelector<HTMLInputElement>('.queue-pause input')?.click();
    await vi.waitFor(() => {
      expect(root.textContent).toContain('当前计划为空');
      expect(buttonByText(root, '开始键盘对照')?.disabled).toBe(true);
    });
    await expect(repository.getPracticeQueueSettings()).resolves.toMatchObject({
      paused: true,
    });
  });

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
      expect(root.textContent).toContain('开始键盘对照');
    });
    buttonByText(root, '开始键盘对照')?.click();
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

  it('explicitly migrates a retired session to classic flashcards', async () => {
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
      {
        ...createPracticeSession({
          id: 'recoverable-session',
          modeId: 'keyboard-recall',
          items: [created.practiceItem],
          now: Date.now() + 2,
          reduceMotion: false,
        }),
        modeId: 'lyrics-scroll',
      },
    );
    await repository.deleteLexicon(lexicon.id);

    const root = document.createElement('div');
    document.body.append(root);
    const app = createApp(PracticeWorkspace, { repository });
    mountedApp = app;
    app.mount(root);

    await vi.waitFor(() => {
      expect(root.textContent).toContain('歌词滚动会话不再启动');
    });
    buttonByText(root, '转为经典闪卡')?.click();

    await vi.waitFor(async () => {
      await expect(repository.getLatestPracticeSession()).resolves.toMatchObject({
        modeId: 'flashcard',
        status: 'active',
      });
      expect(root.textContent).toContain('旧会话已显式转为经典闪卡');
      expect(root.textContent).toContain('打开上次会话');
    });
    buttonByText(root, '打开上次会话')?.click();
    await nextTick();
    await vi.waitFor(() => {
      expect(root.textContent).toContain('经典闪卡');
      expect(root.textContent).toContain('记忆');
    });
    await expect(repository.getStatus()).resolves.toMatchObject({
      counts: { practiceAttempts: 0, reviewStates: 0, reviewLogs: 0 },
    });
  });

  it('explicitly archives a retired session without deleting history', async () => {
    await FingerMemoryDatabase.delete(databaseName);
    database = new FingerMemoryDatabase(databaseName);
    const repository = createFingerMemoryRepository(database, {
      scheduler,
      clock: () => Date.now(),
    });
    await repository.initialize();
    const lexicon = await repository.createLexicon({
      name: '旧会话归档词库',
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
      {
        ...createPracticeSession({
          id: 'retired-archive-session',
          modeId: 'keyboard-recall',
          items: [created.practiceItem],
          now: Date.now() + 2,
          reduceMotion: false,
        }),
        modeId: 'card-rain',
      },
    );

    const root = document.createElement('div');
    document.body.append(root);
    const app = createApp(PracticeWorkspace, { repository });
    mountedApp = app;
    app.mount(root);

    await vi.waitFor(() => {
      expect(root.textContent).toContain('卡片雨会话不再启动');
    });
    buttonByText(root, '归档旧会话')?.click();
    await vi.waitFor(async () => {
      await expect(repository.getLatestPracticeSession()).resolves.toMatchObject({
        modeId: 'card-rain',
        status: 'completed',
        endedReason: 'abandoned',
      });
      expect(root.textContent).toContain('旧会话已归档');
      expect(root.textContent).not.toContain('打开上次会话');
    });
  });
});

function buttonByText(root: HTMLElement, text: string): HTMLButtonElement | undefined {
  return [...root.querySelectorAll('button')].find((button) =>
    button.textContent?.trim().includes(text),
  );
}
