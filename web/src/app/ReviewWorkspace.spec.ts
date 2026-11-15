import 'fake-indexeddb/auto';
import { createApp } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createDefaultReviewScheduler } from '../scheduler/fsrsScheduler';
import { FingerMemoryDatabase } from '../storage/database';
import { createFingerMemoryRepository } from '../storage/localRepository';
import ReviewWorkspace from './ReviewWorkspace.vue';

const databaseName = 'finger-memory-review-workspace-test';
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

describe('ReviewWorkspace', () => {
  it('shows the shared state, submission id and immutable review evidence', async () => {
    await FingerMemoryDatabase.delete(databaseName);
    database = new FingerMemoryDatabase(databaseName);
    const now = Date.parse('2026-11-15T01:00:00.000Z');
    const repository = createFingerMemoryRepository(database, {
      scheduler: createDefaultReviewScheduler(),
      clock: () => now,
    });
    await repository.initialize();
    const lexicon = await repository.createLexicon({
      name: '诊断词库',
      description: '',
      now,
    });
    const created = await repository.createStudyItem({
      lexiconId: lexicon.id,
      term: 'memory',
      meaning: '记忆',
      acceptedAnswers: [],
      hints: [],
      now: now + 1,
    });
    await repository.submitReview({
      submissionId: 'submission-diagnostic-ui',
      studyItemId: created.practiceItem.studyItem.id,
      modeId: 'lyrics-scroll',
      sessionId: null,
      rating: 'good',
      response: 'memory',
      answerWasHidden: true,
      hintUsed: false,
      answerRevealed: false,
      abandoned: false,
      userConfirmedRating: true,
      reviewedAt: now + 2,
    });

    const root = document.createElement('div');
    document.body.append(root);
    const app = createApp(ReviewWorkspace, { repository });
    mountedApp = app;
    app.mount(root);

    await vi.waitFor(() => {
      expect(root.textContent).toContain('状态与日志一致');
      expect(root.textContent).toContain('submission-diagnostic-ui');
      expect(root.textContent).toContain('隐藏 是');
      expect(root.textContent).toContain('FSRS-7');
    });
    expect(root.textContent).not.toContain('还没有正式复习');
  });
});
