import 'fake-indexeddb/auto';
import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createDefaultReviewScheduler } from '../scheduler/fsrsScheduler';
import { FingerMemoryDatabase } from '../storage/database';
import { createFingerMemoryRepository } from '../storage/localRepository';
import LexiconManager from './LexiconManager.vue';

const databaseName = 'finger-memory-lexicon-manager-test';
const scheduler = createDefaultReviewScheduler();
let database: FingerMemoryDatabase | null = null;
let mountedApp: ReturnType<typeof createApp> | null = null;

function buttonByText(root: HTMLElement, text: string): HTMLButtonElement | undefined {
  return [...root.querySelectorAll('button')].find((button) =>
    button.textContent?.includes(text),
  );
}

async function setInputValue(element: HTMLInputElement | HTMLTextAreaElement, value: string) {
  element.value = value;
  element.dispatchEvent(new Event('input', { bubbles: true }));
  await nextTick();
}

afterEach(async () => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
  database?.close();
  database = null;
  await FingerMemoryDatabase.delete(databaseName);
});

describe('LexiconManager', () => {
  it('creates, edits, archives and confirmed-deletes a real lexicon with its study item', async () => {
    await FingerMemoryDatabase.delete(databaseName);
    database = new FingerMemoryDatabase(databaseName);
    let id = 0;
    const repository = createFingerMemoryRepository(database, {
      scheduler,
      clock: () => Date.now(),
      idFactory: () => `test-${++id}`,
    });
    await repository.initialize();

    const root = document.createElement('div');
    document.body.append(root);
    const app = createApp(LexiconManager, { repository });
    mountedApp = app;
    app.mount(root);
    await nextTick();

    await vi.waitFor(() => {
      expect(root.textContent).toContain('还没有词库');
    });

    await setInputValue(root.querySelector<HTMLInputElement>('#new-lexicon-name')!, '测试词库');
    await setInputValue(
      root.querySelector<HTMLInputElement>('#new-lexicon-description')!,
      '管理界面创建',
    );
    buttonByText(root, '创建词库')?.click();

    await vi.waitFor(async () => {
      await expect(repository.listLexicons()).resolves.toHaveLength(1);
    });
    await vi.waitFor(() => {
      expect(root.querySelector('#word-term')).not.toBeNull();
    });

    await setInputValue(root.querySelector<HTMLInputElement>('#word-term')!, 'memory');
    await setInputValue(root.querySelector<HTMLInputElement>('#word-meaning')!, '记忆');
    await setInputValue(
      root.querySelector<HTMLTextAreaElement>('#word-hints')!,
      '内存也是它',
    );
    buttonByText(root, '添加词条')?.click();

    await vi.waitFor(async () => {
      const lexicon = (await repository.listLexicons())[0];
      if (lexicon === undefined) {
        throw new Error('lexicon missing');
      }
      await expect(repository.listLexiconItems(lexicon.id)).resolves.toHaveLength(1);
    });

    await vi.waitFor(() => {
      expect(buttonByText(root, '编辑')?.disabled).toBe(false);
    });
    buttonByText(root, '编辑')?.click();
    await nextTick();
    await vi.waitFor(() => {
      expect(buttonByText(root, '保存词条')).toBeDefined();
    });
    await setInputValue(root.querySelector<HTMLInputElement>('#word-meaning')!, '记忆；内存');
    buttonByText(root, '保存词条')?.click();

    await vi.waitFor(async () => {
      const lexicon = (await repository.listLexicons())[0];
      if (lexicon === undefined) {
        throw new Error('lexicon missing');
      }
      const items = await repository.listLexiconItems(lexicon.id);
      expect(items[0]?.practiceItem.wordEntry.meaning).toBe('记忆；内存');
    });

    await vi.waitFor(() => {
      expect(buttonByText(root, '归档')?.disabled).toBe(false);
    });
    buttonByText(root, '归档')?.click();
    await vi.waitFor(async () => {
      await expect(repository.listPracticeItems()).resolves.toEqual([]);
    });

    await vi.waitFor(() => {
      expect(buttonByText(root, '删除词库')?.disabled).toBe(false);
    });
    buttonByText(root, '删除词库')?.click();
    await vi.waitFor(() => {
      expect(root.textContent).toContain('学习目标、复习历史和日志会保留');
    });
    buttonByText(root, '确认删除')?.click();
    await vi.waitFor(async () => {
      await expect(repository.listLexicons()).resolves.toEqual([]);
      await expect(repository.getStatus()).resolves.toMatchObject({
        counts: { lexicons: 0, words: 1, studyItems: 1, lexiconItems: 0 },
      });
    });
  });

  it('previews TSV, blocks invalid rows, and imports only confirmed duplicate choices', async () => {
    await FingerMemoryDatabase.delete(databaseName);
    database = new FingerMemoryDatabase(databaseName);
    const repository = createFingerMemoryRepository(database, {
      scheduler,
      clock: () => Date.now(),
    });
    await repository.initialize();
    const lexicon = await repository.createLexicon({
      name: '导入词库',
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
    const app = createApp(LexiconManager, { repository });
    mountedApp = app;
    app.mount(root);

    await vi.waitFor(() => {
      expect(root.textContent).toContain('导入词库');
    });
    root.querySelector<HTMLElement>('summary')?.click();
    await nextTick();

    const textarea = root.querySelector<HTMLTextAreaElement>('#tsv-import-text')!;
    await setInputValue(
      textarea,
      'memory\t记忆\nfocus\t专注\t注意力\nfocus\t专注\ninvalid\n',
    );
    buttonByText(root, '生成预览')?.click();
    await vi.waitFor(() => {
      expect(root.textContent).toContain('存在无效记录');
      expect(buttonByText(root, '确认导入')?.disabled).toBe(true);
    });
    buttonByText(root, '取消')?.click();
    await vi.waitFor(async () => {
      await expect(repository.listLexiconItems(lexicon.id)).resolves.toHaveLength(1);
    });

    await setInputValue(
      root.querySelector<HTMLTextAreaElement>('#tsv-import-text')!,
      'memory\t记忆\nfocus\t专注\t注意力\nfocus\t专注\n',
    );
    buttonByText(root, '生成预览')?.click();
    await vi.waitFor(() => {
      expect(root.textContent).toContain('重复记录');
      expect(buttonByText(root, '确认导入')?.disabled).toBe(false);
    });

    const createIndependent = [...root.querySelectorAll<HTMLInputElement>('input[value="create"]')][0];
    createIndependent?.click();
    await nextTick();
    buttonByText(root, '确认导入')?.click();

    await vi.waitFor(async () => {
      await expect(repository.listLexiconItems(lexicon.id)).resolves.toHaveLength(3);
      expect(root.textContent).toContain('已导入 2 条，跳过 1 条');
    });
  });
});
