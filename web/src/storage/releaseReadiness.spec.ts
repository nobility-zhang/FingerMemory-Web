import 'fake-indexeddb/auto';
import { afterEach, describe, expect, it } from 'vitest';
import { createDefaultReviewScheduler } from '../scheduler/fsrsScheduler';
import { createPracticeSession } from '../session/practiceSession';
import { FingerMemoryDatabase } from './database';
import { createFingerMemoryRepository } from './localRepository';

const databaseName = 'finger-memory-release-readiness';
const now = Date.parse('2026-11-15T02:00:00.000Z');
let database: FingerMemoryDatabase | null = null;

afterEach(async () => {
  database?.close();
  database = null;
  await FingerMemoryDatabase.delete(databaseName);
});

describe('release readiness at personal scale', () => {
  it('imports, plans, diagnoses and exports a 1000-target lexicon within bounds', async () => {
    await FingerMemoryDatabase.delete(databaseName);
    database = new FingerMemoryDatabase(databaseName);
    const repository = createFingerMemoryRepository(database, {
      scheduler: createDefaultReviewScheduler(),
      clock: () => now,
    });
    await repository.initialize();
    const lexicon = await repository.createLexicon({
      name: '千词性能夹具',
      description: '用于发布前个人规模回归',
      now,
    });
    const startedAt = performance.now();
    const imported = await repository.importStudyItems({
      batchId: 'release-1000',
      lexiconId: lexicon.id,
      rows: Array.from({ length: 1000 }, (_, index) => ({
        term: `word${String(index).padStart(4, '0')}`,
        meaning: `释义${index}`,
        hints: [],
        duplicateAction: 'create' as const,
      })),
      now: now + 1,
    });
    expect(imported).toMatchObject({ outcome: 'created', importedCount: 1000 });

    await repository.savePracticeQueueSettings(
      { newLimit: 100, reviewLimit: 100, paused: false },
      now + 2,
    );
    const plan = await repository.planPracticeQueue({
      at: now + 3,
      timeZone: 'Asia/Shanghai',
    });
    expect(plan).toMatchObject({
      newAvailableCount: 1000,
      dueAvailableCount: 0,
    });
    expect(plan.selected).toHaveLength(100);

    const session = createPracticeSession({
      id: 'release-session',
      modeId: 'danmaku-stream',
      items: plan.selected.map((entry) => entry.item),
      sources: Object.fromEntries(
        plan.selected.map((entry) => [entry.item.studyItem.id, entry.source]),
      ),
      now: now + 4,
      reduceMotion: false,
    });
    await repository.savePracticeSession(session);
    await expect(repository.getLatestPracticeSession()).resolves.toMatchObject({
      id: 'release-session',
      queue: expect.arrayContaining([expect.objectContaining({ source: 'new' })]),
    });

    const first = plan.selected[0]!.item;
    await repository.submitReview({
      submissionId: 'release-review-1',
      studyItemId: first.studyItem.id,
      modeId: 'card-rain',
      sessionId: null,
      rating: 'good',
      response: first.wordEntry.term,
      answerWasHidden: true,
      hintUsed: false,
      answerRevealed: false,
      abandoned: false,
      userConfirmedRating: true,
      reviewedAt: now + 5,
    });
    await expect(repository.listReviewHistory()).resolves.toMatchObject([
      { consistent: true, logs: [{ submissionId: 'release-review-1' }] },
    ]);

    const snapshot = await repository.exportSnapshot(now + 6);
    expect(snapshot).toMatchObject({
      schemaVersion: 4,
      dataFormatVersion: 4,
      tables: {
        words: expect.any(Array),
        practiceSessions: [{ id: 'release-session' }],
        reviewStates: [{ studyItemId: first.studyItem.id }],
      },
    });
    expect(snapshot.tables.words).toHaveLength(1000);
    expect(snapshot.tables.studyItems).toHaveLength(1000);
    expect(snapshot.tables.lexiconItems).toHaveLength(1000);
    expect(snapshot.tables.reviewLogs).toHaveLength(1);
    expect(JSON.stringify(snapshot).length).toBeGreaterThan(200_000);
    expect(performance.now() - startedAt).toBeLessThan(5_000);
  });
});
