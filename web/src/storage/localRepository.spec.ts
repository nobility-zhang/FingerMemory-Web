import 'fake-indexeddb/auto';
import { Dexie } from 'dexie';
import { afterEach, describe, expect, it } from 'vitest';
import type { PracticeAttemptInput, ReviewSubmissionInput } from '../core/repository';
import {
  InvalidStorageSnapshotError,
  ReviewStateVersionMismatchError,
  ReviewSubmissionConflictError,
  ReviewSubmissionRejectedError,
} from '../core/repository';
import { createDefaultReviewScheduler } from '../scheduler/fsrsScheduler';
import { createPracticeSession } from '../session/practiceSession';
import {
  APP_META_KEYS,
  DATA_FORMAT_VERSION,
  FingerMemoryDatabase,
  LEGACY_STORES_V1,
  STORAGE_SCHEMA_VERSION,
} from './database';
import { createFingerMemoryRepository } from './localRepository';

const INITIAL_TIME = Date.parse('2026-11-15T01:00:00.000Z');
const scheduler = createDefaultReviewScheduler();
const databaseNames = new Set<string>();
const openDatabases: FingerMemoryDatabase[] = [];
let databaseSequence = 0;

function nextDatabaseName(prefix = 'finger-memory-test'): string {
  databaseSequence += 1;
  const name = `${prefix}-${databaseSequence}`;
  databaseNames.add(name);
  return name;
}

function createDatabase(): FingerMemoryDatabase {
  const database = new FingerMemoryDatabase(nextDatabaseName());
  openDatabases.push(database);
  return database;
}

function createRepository(database: FingerMemoryDatabase) {
  return createFingerMemoryRepository(database, {
    scheduler,
    clock: () => INITIAL_TIME,
  });
}

async function seedStudyItem(database: FingerMemoryDatabase): Promise<void> {
  await database.words.put({
    id: 'word-memory',
    term: 'memory',
    meaning: '记忆',
    acceptedAnswers: ['memory'],
    hints: ['与内存有关'],
    normalizedTerm: 'memory',
    createdAt: INITIAL_TIME,
    updatedAt: INITIAL_TIME,
  });
  await database.studyItems.put({
    id: 'study-memory',
    wordEntryId: 'word-memory',
    direction: 'meaning-to-spelling',
    createdAt: INITIAL_TIME,
    updatedAt: INITIAL_TIME,
  });
}

function createReviewInput(overrides: Partial<ReviewSubmissionInput> = {}): ReviewSubmissionInput {
  return {
    submissionId: 'submission-1',
    studyItemId: 'study-memory',
    modeId: 'keyboard-recall',
    sessionId: null,
    rating: 'good',
    response: 'memory',
    answerWasHidden: true,
    hintUsed: false,
    answerRevealed: false,
    abandoned: false,
    userConfirmedRating: true,
    reviewedAt: INITIAL_TIME,
    ...overrides,
  };
}

function createPracticeInput(overrides: Partial<PracticeAttemptInput> = {}): PracticeAttemptInput {
  return {
    id: 'practice-1',
    studyItemId: 'study-memory',
    modeId: 'copy-mode',
    sessionId: null,
    response: 'memory',
    answerWasHidden: false,
    hintUsed: false,
    answerRevealed: true,
    abandoned: false,
    occurredAt: INITIAL_TIME,
    ...overrides,
  };
}

afterEach(async () => {
  for (const database of openDatabases.splice(0)) {
    database.close();
  }
  for (const name of databaseNames) {
    await Dexie.delete(name);
  }
  databaseNames.clear();
});

describe('FingerMemoryDatabase', () => {
  it('creates the v3 tables and indexed relationships', async () => {
    const database = createDatabase();
    const repository = createRepository(database);

    const status = await repository.initialize();
    await seedStudyItem(database);

    expect(status.schemaVersion).toBe(STORAGE_SCHEMA_VERSION);
    expect(status.dataFormatVersion).toBe(DATA_FORMAT_VERSION);
    expect(status.counts).toMatchObject({ words: 0, studyItems: 0 });
    expect(database.tables.map((table) => table.name).sort()).toEqual([
      'appMeta',
      'lexiconItems',
      'lexicons',
      'notes',
      'practiceAttempts',
      'practiceSessions',
      'reviewLogs',
      'reviewStates',
      'studyItems',
      'words',
    ]);
    await expect(
      database.studyItems.where('[wordEntryId+direction]').equals([
        'word-memory',
        'meaning-to-spelling',
      ]).count(),
    ).resolves.toBe(1);
  });

  it('loads the starter lexicon idempotently as real persisted study items', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();

    await repository.ensureStarterContent();
    const items = await repository.ensureStarterContent();
    const status = await repository.getStatus();

    expect(items).toHaveLength(10);
    expect(status.counts).toMatchObject({
      lexicons: 1,
      words: 10,
      studyItems: 10,
      lexiconItems: 10,
    });
  });

  it('migrates a v1 study item into a word entry and meaning-to-spelling target', async () => {
    const name = nextDatabaseName('finger-memory-migration');
    const legacyDatabase = new Dexie(name);
    legacyDatabase.version(1).stores(LEGACY_STORES_V1);
    await legacyDatabase.open();
    await legacyDatabase.table('appMeta').bulkPut([
      {
        key: APP_META_KEYS.dataFormatVersion,
        value: '1',
        updatedAt: INITIAL_TIME,
      },
      {
        key: APP_META_KEYS.initializedAt,
        value: new Date(INITIAL_TIME).toISOString(),
        updatedAt: INITIAL_TIME,
      },
    ]);
    await legacyDatabase.table('studyItems').put({
      id: 'legacy-study',
      term: 'cursor',
      primaryAnswer: '光标',
      acceptedAnswers: [],
      normalization: 'cursor',
      createdAt: INITIAL_TIME,
      updatedAt: INITIAL_TIME,
    });
    legacyDatabase.close();

    const database = new FingerMemoryDatabase(name);
    openDatabases.push(database);
    const repository = createRepository(database);
    await repository.initialize();

    await expect(database.words.get('word:legacy-study')).resolves.toMatchObject({
      term: 'cursor',
      meaning: '光标',
      normalizedTerm: 'cursor',
    });
    await expect(database.studyItems.get('legacy-study')).resolves.toEqual({
      id: 'legacy-study',
      wordEntryId: 'word:legacy-study',
      direction: 'meaning-to-spelling',
      createdAt: INITIAL_TIME,
      updatedAt: INITIAL_TIME,
    });
    await expect(database.appMeta.get(APP_META_KEYS.dataFormatVersion)).resolves.toMatchObject({
      value: String(DATA_FORMAT_VERSION),
    });
  });
});

describe('practice and formal review separation', () => {
  it('records a revealed-answer copy as practice without changing review state', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);

    const result = await repository.recordPracticeAttempt(createPracticeInput());
    const status = await repository.getStatus();

    expect(result.outcome).toBe('created');
    expect(result.attempt.inputMatchedAnswer).toBe(true);
    expect(status.counts).toMatchObject({
      practiceAttempts: 1,
      reviewStates: 0,
      reviewLogs: 0,
    });
  });

  it('persists one state and one log for an eligible explicit review', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);

    const result = await repository.submitReview(createReviewInput());
    const status = await repository.getStatus();

    expect(result.outcome).toBe('created');
    expect(result.state.cardId).toBe('study-memory');
    expect(result.state.reps).toBe(1);
    expect(result.state.lastReviewAt).toBe(INITIAL_TIME);
    expect(result.state.dueAt).toBeGreaterThan(INITIAL_TIME);
    expect(result.state.schedulerPackageVersion).toBe('6.0.0');
    expect(result.state.schedulerModelVersion).toBe('fsrs-7');
    expect(result.state.schedulerConfig).toMatchObject({
      packageVersion: '6.0.0',
      modelVersion: 'fsrs-7',
      fractionalDays: true,
      enableFuzz: false,
      learningSteps: ['1m', '10m'],
      relearningSteps: ['10m'],
    });
    expect(result.log.rating).toBe('good');
    expect(result.log.inputMatchedAnswer).toBe(true);
    expect(result.log.schedulerConfig).toEqual(result.state.schedulerConfig);
    expect(status.counts).toMatchObject({ reviewStates: 1, reviewLogs: 1 });
  });

  it('does not convert a keyboard typo into Again when the user confirms Hard', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);

    const result = await repository.submitReview(
      createReviewInput({ rating: 'hard', response: 'memroy' }),
    );

    expect(result.log.rating).toBe('hard');
    expect(result.log.inputMatchedAnswer).toBe(false);
    expect(result.state.lapses).toBe(0);
  });

  it.each([
    ['rating-not-confirmed', { userConfirmedRating: false }],
    ['answer-not-hidden', { answerWasHidden: false }],
    ['hint-used', { hintUsed: true }],
    ['answer-revealed', { answerRevealed: true }],
    ['abandoned', { abandoned: true }],
  ] as const)('rejects an ineligible review: %s', async (reason, overrides) => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);

    await expect(
      repository.submitReview(createReviewInput(overrides)),
    ).rejects.toMatchObject({ reason });
    await expect(repository.getStatus()).resolves.toMatchObject({
      counts: { reviewStates: 0, reviewLogs: 0 },
    });
  });

  it('keeps animation-mode copying as practice and accepts only an eligible explicit review', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);

    await repository.recordPracticeAttempt(
      createPracticeInput({ modeId: 'card-rain', answerRevealed: true }),
    );
    await expect(
      repository.submitReview(
        createReviewInput({
          submissionId: 'animation-revealed',
          modeId: 'card-rain',
          answerRevealed: true,
        }),
      ),
    ).rejects.toMatchObject({ reason: 'answer-revealed' });

    await expect(
      repository.submitReview(
        createReviewInput({ submissionId: 'animation-recall', modeId: 'card-rain' }),
      ),
    ).resolves.toMatchObject({ outcome: 'created' });
    await expect(repository.getStatus()).resolves.toMatchObject({
      counts: { practiceAttempts: 1, reviewStates: 1, reviewLogs: 1 },
    });
  });

  it('rejects a persisted study target with an unsupported recall direction', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);
    await database.studyItems.update('study-memory', {
      // Simulate a corrupt/imported row that bypassed TypeScript validation.
      direction: 'spelling-to-meaning' as never,
    });

    await expect(repository.submitReview(createReviewInput())).rejects.toMatchObject({
      reason: 'unsupported-direction',
    });
  });
});

describe('idempotency and transactional durability', () => {
  it('returns the first result without advancing again for a repeated submission', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);
    const input = createReviewInput();

    const first = await repository.submitReview(input);
    database.close();

    const reopenedDatabase = new FingerMemoryDatabase(database.name);
    openDatabases.push(reopenedDatabase);
    const reopenedRepository = createRepository(reopenedDatabase);
    const duplicate = await reopenedRepository.submitReview(input);
    const status = await reopenedRepository.getStatus();

    expect(duplicate.outcome).toBe('duplicate');
    expect(duplicate.state.dueAt).toBe(first.state.dueAt);
    expect(status.counts).toMatchObject({ reviewStates: 1, reviewLogs: 1 });
  });

  it('rejects a different payload that reuses an existing submission id', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);
    const first = await repository.submitReview(createReviewInput());

    await expect(
      repository.submitReview(createReviewInput({ rating: 'easy' })),
    ).rejects.toBeInstanceOf(ReviewSubmissionConflictError);
    await expect(repository.getReviewState('study-memory')).resolves.toMatchObject({
      dueAt: first.state.dueAt,
      reps: 1,
    });
  });

  it('rolls back the state update when writing the review log fails', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);
    const first = await repository.submitReview(createReviewInput());

    database.reviewLogs.hook('creating').subscribe(() => {
      throw new Error('forced review log failure');
    });

    await expect(
      repository.submitReview(
        createReviewInput({
          submissionId: 'submission-2',
          rating: 'hard',
          reviewedAt: INITIAL_TIME + 60_000,
        }),
      ),
    ).rejects.toThrow('forced review log failure');
    await expect(repository.getReviewState('study-memory')).resolves.toMatchObject({
      dueAt: first.state.dueAt,
      reps: 1,
    });
    await expect(repository.getStatus()).resolves.toMatchObject({
      counts: { reviewLogs: 1 },
    });
  });

  it('rejects review updates after a scheduler configuration change', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);
    await repository.submitReview(createReviewInput());
    await database.reviewStates.update('study-memory', {
      schedulerPackageVersion: '5.0.0',
    });

    await expect(
      repository.submitReview(
        createReviewInput({
          submissionId: 'submission-version-mismatch',
          reviewedAt: INITIAL_TIME + 60_000,
        }),
      ),
    ).rejects.toBeInstanceOf(ReviewStateVersionMismatchError);
    await expect(repository.getStatus()).resolves.toMatchObject({
      counts: { reviewLogs: 1 },
    });
  });

  it('rejects a controlled review time that moves backwards', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);
    const first = await repository.submitReview(createReviewInput());

    await expect(
      repository.submitReview(
        createReviewInput({
          submissionId: 'submission-backwards',
          reviewedAt: INITIAL_TIME - 1,
        }),
      ),
    ).rejects.toMatchObject({ reason: 'review-time-out-of-order' });
    await expect(repository.getReviewState('study-memory')).resolves.toMatchObject({
      dueAt: first.state.dueAt,
      reps: 1,
    });
    await expect(repository.getStatus()).resolves.toMatchObject({
      counts: { reviewLogs: 1 },
    });
  });
});

describe('due filtering, recovery and backup import', () => {
  it('filters due items using the supplied time only', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);
    const result = await repository.submitReview(createReviewInput());

    await expect(
      repository.listDueReviewItems(result.state.dueAt - 1),
    ).resolves.toEqual([]);
    await expect(repository.listDueReviewItems(result.state.dueAt)).resolves.toMatchObject([
      {
        studyItem: { id: 'study-memory' },
        wordEntry: { term: 'memory', meaning: '记忆' },
      },
    ]);
  });

  it('restores serialized state and logs and preserves duplicate protection', async () => {
    const sourceDatabase = createDatabase();
    const sourceRepository = createRepository(sourceDatabase);
    await sourceRepository.initialize();
    await seedStudyItem(sourceDatabase);
    await sourceRepository.submitReview(createReviewInput());
    const original = await sourceRepository.submitReview(
      createReviewInput({
        submissionId: 'submission-2',
        rating: 'hard',
        reviewedAt: INITIAL_TIME + 60_000,
      }),
    );
    const seededWord = await sourceDatabase.words.get('word-memory');
    const seededStudyItem = await sourceDatabase.studyItems.get('study-memory');
    if (seededWord === undefined || seededStudyItem === undefined) {
      throw new Error('seed data missing');
    }
    await sourceRepository.savePracticeSession(
      createPracticeSession({
        id: 'session-backup',
        modeId: 'lyrics-scroll',
        items: [{ wordEntry: seededWord, studyItem: seededStudyItem }],
        now: INITIAL_TIME,
        reduceMotion: true,
      }),
    );
    const snapshot = await sourceRepository.exportSnapshot(INITIAL_TIME + 120_000);
    const serialized = JSON.parse(JSON.stringify(snapshot)) as unknown;

    const restoredDatabase = createDatabase();
    const restoredRepository = createRepository(restoredDatabase);
    const status = await restoredRepository.importSnapshot(serialized);

    expect(status.counts).toMatchObject({
      practiceSessions: 1,
      reviewStates: 1,
      reviewLogs: 2,
    });
    await expect(restoredRepository.getLatestPracticeSession()).resolves.toMatchObject({
      id: 'session-backup',
      modeId: 'lyrics-scroll',
      reduceMotion: true,
    });
    await expect(
      restoredRepository.listDueReviewItems(original.state.dueAt),
    ).resolves.toHaveLength(1);
    await expect(
      restoredRepository.submitReview(createReviewInput()),
    ).resolves.toMatchObject({
      outcome: 'duplicate',
      log: { submissionId: 'submission-1', rating: 'good' },
    });
  });

  it('rejects an invalid snapshot without replacing existing data', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);
    await repository.submitReview(createReviewInput());
    const snapshot = await repository.exportSnapshot(INITIAL_TIME + 120_000);

    const invalidSnapshot = {
      ...snapshot,
      tables: {
        ...snapshot.tables,
        studyItems: [
          {
            ...snapshot.tables.studyItems[0],
            wordEntryId: 'missing-word',
          },
        ],
      },
    };

    await expect(repository.importSnapshot(invalidSnapshot)).rejects.toBeInstanceOf(
      InvalidStorageSnapshotError,
    );
    await expect(repository.getStatus()).resolves.toMatchObject({
      counts: { words: 1, studyItems: 1, reviewStates: 1, reviewLogs: 1 },
    });
  });

  it('rolls back an interrupted snapshot import and keeps the original data', async () => {
    const sourceDatabase = createDatabase();
    const sourceRepository = createRepository(sourceDatabase);
    await sourceRepository.initialize();
    await seedStudyItem(sourceDatabase);
    await sourceRepository.submitReview(createReviewInput());
    const snapshot = await sourceRepository.exportSnapshot(INITIAL_TIME + 120_000);

    const destinationDatabase = createDatabase();
    const destinationRepository = createRepository(destinationDatabase);
    await destinationRepository.initialize();
    await destinationDatabase.words.put({
      id: 'kept-word',
      term: 'kept',
      meaning: '保留',
      acceptedAnswers: [],
      hints: [],
      normalizedTerm: 'kept',
      createdAt: INITIAL_TIME,
      updatedAt: INITIAL_TIME,
    });
    await destinationDatabase.studyItems.put({
      id: 'kept-study',
      wordEntryId: 'kept-word',
      direction: 'meaning-to-spelling',
      createdAt: INITIAL_TIME,
      updatedAt: INITIAL_TIME,
    });
    destinationDatabase.words.hook('creating').subscribe(() => {
      throw new Error('forced snapshot import failure');
    });

    await expect(destinationRepository.importSnapshot(snapshot)).rejects.toThrow(
      'forced snapshot import failure',
    );
    await expect(destinationRepository.getStatus()).resolves.toMatchObject({
      counts: { words: 1, studyItems: 1, reviewStates: 0, reviewLogs: 0 },
    });
    await expect(destinationDatabase.words.get('kept-word')).resolves.toMatchObject({
      term: 'kept',
    });
  });
});

it('uses explicit errors for invalid review evidence', async () => {
  const database = createDatabase();
  const repository = createRepository(database);
  await repository.initialize();
  await seedStudyItem(database);

  await expect(
    repository.submitReview(createReviewInput({ answerRevealed: true })),
  ).rejects.toBeInstanceOf(ReviewSubmissionRejectedError);
});
