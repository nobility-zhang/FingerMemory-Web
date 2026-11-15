import 'fake-indexeddb/auto';
import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createDefaultReviewScheduler } from '../scheduler/fsrsScheduler';
import { FingerMemoryDatabase } from '../storage/database';
import { createFingerMemoryRepository } from '../storage/localRepository';
import DataWorkspace from './DataWorkspace.vue';

const databaseName = 'finger-memory-data-workspace-test';
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

describe('DataWorkspace', () => {
  it('previews and atomically restores a selected backup after confirmation', async () => {
    await FingerMemoryDatabase.delete(databaseName);
    database = new FingerMemoryDatabase(databaseName);
    const repository = createFingerMemoryRepository(database, {
      scheduler,
      clock: () => Date.now(),
    });
    await repository.initialize();
    const backupLexicon = await repository.createLexicon({
      name: '备份词库',
      description: '',
      now: Date.now(),
    });
    const backupItem = await repository.createStudyItem({
      lexiconId: backupLexicon.id,
      term: 'memory',
      meaning: '记忆',
      acceptedAnswers: [],
      hints: [],
      now: Date.now() + 1,
    });
    await repository.saveStudyNote({
      studyItemId: backupItem.practiceItem.studyItem.id,
      content: '恢复后应保留',
      now: Date.now() + 2,
    });
    const snapshot = await repository.exportSnapshot(Date.now() + 3);
    await repository.createLexicon({
      name: '恢复前临时词库',
      description: '',
      now: Date.now() + 4,
    });

    const root = document.createElement('div');
    document.body.append(root);
    const app = createApp(DataWorkspace, { repository });
    mountedApp = app;
    app.mount(root);
    await nextTick();

    const file = new File([`${JSON.stringify(snapshot)}\n`], 'backup.json', {
      type: 'application/json',
    });
    const input = root.querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: [file],
    });
    input.dispatchEvent(new Event('change', { bubbles: true }));

    await vi.waitFor(() => {
      expect(root.textContent).toContain('backup.json');
      expect(root.textContent).toContain('我确认使用该备份覆盖当前本地数据');
    });
    const confirm = root.querySelector<HTMLInputElement>('.overwrite-confirm input')!;
    confirm.click();
    await nextTick();
    const restoreButton = [...root.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === '覆盖并恢复',
    );
    expect(restoreButton?.disabled).toBe(false);
    restoreButton?.click();

    await vi.waitFor(async () => {
      await expect(repository.listLexicons()).resolves.toMatchObject([
        { name: '备份词库' },
      ]);
      await expect(repository.listStudyNotes()).resolves.toMatchObject([
        { content: '恢复后应保留' },
      ]);
      expect(root.textContent).toContain('导出的备份');
    });
  });
});
