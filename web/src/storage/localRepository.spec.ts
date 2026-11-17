import 'fake-indexeddb/auto';
import { Dexie } from 'dexie';
import { afterEach, describe, expect, it } from 'vitest';
import type { PracticeAttemptInput, ReviewSubmissionInput } from '../core/repository';
import {
  ImportBatchConflictError,
  InvalidStorageSnapshotError,
  ReviewStateVersionMismatchError,
  ReviewSubmissionConflictError,
  ReviewSubmissionRejectedError,
  StudyItemInUseError,
  StudyItemNotFoundError,
} from '../core/repository';
import { createDefaultReviewScheduler } from '../scheduler/fsrsScheduler';
import { createPracticeSession } from '../session/practiceSession';
import {
  APP_META_KEYS,
  DATA_FORMAT_VERSION,
  FingerMemoryDatabase,
  LEGACY_STORES_V1,
  STORES_V2,
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
  it('creates the v5 tables and indexed relationships', async () => {
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

  it('migrates v3 sessions by filling missing queue sources', async () => {
    const name = nextDatabaseName('finger-memory-v3-upgrade');
    const legacy = new Dexie(name);
    legacy.version(3).stores({
      ...STORES_V2,
      practiceSessions: 'id, updatedAt, status, modeId',
    });
    await legacy.open();
    await legacy.table('appMeta').bulkPut([
      {
        key: APP_META_KEYS.dataFormatVersion,
        value: '3',
        updatedAt: INITIAL_TIME,
      },
      {
        key: APP_META_KEYS.initializedAt,
        value: new Date(INITIAL_TIME).toISOString(),
        updatedAt: INITIAL_TIME,
      },
    ]);
    await legacy.table('words').put({
      id: 'word-memory',
      term: 'memory',
      meaning: '记忆',
      acceptedAnswers: [],
      hints: [],
      normalizedTerm: 'memory',
      createdAt: INITIAL_TIME,
      updatedAt: INITIAL_TIME,
    });
    await legacy.table('studyItems').put({
      id: 'study-memory',
      wordEntryId: 'word-memory',
      direction: 'meaning-to-spelling',
      createdAt: INITIAL_TIME,
      updatedAt: INITIAL_TIME,
    });
    await legacy.table('practiceSessions').put({
      id: 'legacy-session',
      modeId: 'lyrics-scroll',
      status: 'paused',
      queue: [
        {
          studyItemId: 'study-memory',
          wordEntryId: 'word-memory',
          status: 'pending',
        },
      ],
      currentIndex: 0,
      currentStudyItemId: 'study-memory',
      interactions: {},
      pendingAnswer: null,
      speed: 1,
      reduceMotion: false,
      createdAt: INITIAL_TIME,
      updatedAt: INITIAL_TIME,
      pausedAt: INITIAL_TIME,
      completedAt: null,
    });
    legacy.close();

    const database = new FingerMemoryDatabase(name);
    openDatabases.push(database);
    const repository = createRepository(database);
    await expect(repository.initialize()).resolves.toMatchObject({
      schemaVersion: STORAGE_SCHEMA_VERSION,
      dataFormatVersion: DATA_FORMAT_VERSION,
    });
    await expect(repository.getLatestPracticeSession()).resolves.toMatchObject({
      id: 'legacy-session',
      queue: [{ source: 'new', status: 'pending' }],
    });
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

describe('lexicon and study item management', () => {
  it('creates, edits, archives and deletes lexicon memberships without auto-merging duplicates', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    const lexicon = await repository.createLexicon({
      name: '测试词库',
      description: '初始说明',
      now: INITIAL_TIME,
    });

    const first = await repository.createStudyItem({
      lexiconId: lexicon.id,
      term: 'memory',
      meaning: '记忆',
      acceptedAnswers: [],
      hints: ['提示一'],
      now: INITIAL_TIME + 1,
    });
    await repository.createStudyItem({
      lexiconId: lexicon.id,
      term: 'memory',
      meaning: '内存',
      acceptedAnswers: ['RAM'],
      hints: [],
      now: INITIAL_TIME + 2,
    });
    await expect(repository.listLexiconItems(lexicon.id)).resolves.toHaveLength(2);

    const updated = await repository.updateStudyItem({
      lexiconId: lexicon.id,
      studyItemId: first.practiceItem.studyItem.id,
      term: 'remember',
      meaning: '记住',
      acceptedAnswers: ['recall'],
      hints: ['不是 memory'],
      now: INITIAL_TIME + 3,
    });
    expect(updated.practiceItem.wordEntry).toMatchObject({
      term: 'remember',
      meaning: '记住',
      normalizedTerm: 'remember',
    });

    await repository.setLexiconArchived(lexicon.id, true, INITIAL_TIME + 4);
    await expect(repository.listPracticeItems()).resolves.toEqual([]);
    await expect(repository.listLexicons(false)).resolves.toEqual([]);
    await repository.setLexiconArchived(lexicon.id, false, INITIAL_TIME + 5);
    await expect(repository.listPracticeItems()).resolves.toHaveLength(2);

    await repository.removeStudyItemFromLexicon(
      lexicon.id,
      first.practiceItem.studyItem.id,
    );
    await expect(repository.listLexiconItems(lexicon.id)).resolves.toHaveLength(1);
    await expect(repository.getStatus()).resolves.toMatchObject({
      counts: { words: 2, studyItems: 2, lexiconItems: 1 },
    });

    await repository.deleteLexicon(lexicon.id);
    await expect(repository.listLexicons(true)).resolves.toEqual([]);
    await expect(repository.getStatus()).resolves.toMatchObject({
      counts: { lexicons: 0, words: 2, studyItems: 2, lexiconItems: 0 },
    });
  });

  it('rejects removing a study item referenced by an unfinished session', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    const items = await repository.ensureStarterContent();
    const lexicon = (await repository.listLexicons())[0];
    if (lexicon === undefined || items[0] === undefined) {
      throw new Error('starter data missing');
    }
    await repository.savePracticeSession(
      createPracticeSession({
        id: 'active-session',
        modeId: 'lyrics-scroll',
        items: [items[0]],
        now: INITIAL_TIME,
        reduceMotion: false,
      }),
    );

    await expect(
      repository.removeStudyItemFromLexicon(
        lexicon.id,
        items[0].studyItem.id,
      ),
    ).rejects.toBeInstanceOf(StudyItemInUseError);
    await expect(repository.listLexiconItems(lexicon.id)).resolves.toHaveLength(10);
  });

  it('deleting a non-empty lexicon preserves study state and review history', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    const lexicon = await repository.createLexicon({
      name: '历史词库',
      description: '',
      now: INITIAL_TIME,
    });
    const created = await repository.createStudyItem({
      lexiconId: lexicon.id,
      term: 'memory',
      meaning: '记忆',
      acceptedAnswers: [],
      hints: [],
      now: INITIAL_TIME + 1,
    });
    await repository.submitReview(
      createReviewInput({
        studyItemId: created.practiceItem.studyItem.id,
        reviewedAt: INITIAL_TIME + 2,
      }),
    );
    await repository.savePracticeSession(
      createPracticeSession({
        id: 'historical-session',
        modeId: 'lyrics-scroll',
        items: [created.practiceItem],
        now: INITIAL_TIME + 3,
        reduceMotion: false,
      }),
    );

    await repository.deleteLexicon(lexicon.id);

    await expect(repository.getStatus()).resolves.toMatchObject({
      counts: { lexicons: 0, studyItems: 1, lexiconItems: 0, reviewStates: 1, reviewLogs: 1 },
    });
    await expect(
      repository.getReviewState(created.practiceItem.studyItem.id),
    ).resolves.toBeDefined();
    await expect(
      repository.listPracticeItemsByStudyItemIds([created.practiceItem.studyItem.id]),
    ).resolves.toMatchObject([
      {
        studyItem: { id: created.practiceItem.studyItem.id },
        wordEntry: { term: 'memory', meaning: '记忆' },
      },
    ]);
  });
});

describe('practice session lifecycle', () => {
  it('abandons other unfinished sessions when a new session starts', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    const lexicon = await repository.createLexicon({
      name: '会话测试',
      description: '',
      now: INITIAL_TIME,
    });
    const created = await repository.createStudyItem({
      lexiconId: lexicon.id,
      term: 'memory',
      meaning: '记忆',
      acceptedAnswers: [],
      hints: [],
      now: INITIAL_TIME + 1,
    });
    const first = await repository.savePracticeSession(
      createPracticeSession({
        id: 'first-session',
        modeId: 'lyrics-scroll',
        items: [created.practiceItem],
        now: INITIAL_TIME + 2,
        reduceMotion: false,
      }),
    );

    const second = await repository.savePracticeSession(
      createPracticeSession({
        id: 'new-session',
        modeId: 'keyboard-recall',
        items: [created.practiceItem],
        now: INITIAL_TIME + 3,
        reduceMotion: false,
      }),
    );

    expect(second.status).toBe('active');
    await expect(database.practiceSessions.get(first.id)).resolves.toMatchObject({
      status: 'completed',
      endedReason: 'abandoned',
    });
    await expect(repository.getLatestPracticeSession()).resolves.toMatchObject({
      id: second.id,
      status: 'active',
    });
  });
});

describe('TSV import persistence', () => {
  it('imports selected rows atomically and replays the same batch without duplicate writes', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    const lexicon = await repository.createLexicon({
      name: '导入测试',
      description: '',
      now: INITIAL_TIME,
    });
    await repository.createStudyItem({
      lexiconId: lexicon.id,
      term: 'memory',
      meaning: '记忆',
      acceptedAnswers: [],
      hints: [],
      now: INITIAL_TIME + 1,
    });
    const input = {
      batchId: 'import-batch-1',
      lexiconId: lexicon.id,
      rows: [
        {
          term: 'memory',
          meaning: '记忆',
          hints: [],
          duplicateAction: 'skip' as const,
        },
        {
          term: 'memory',
          meaning: '记忆',
          hints: ['独立目标'],
          duplicateAction: 'create' as const,
        },
        {
          term: 'focus',
          meaning: '专注',
          hints: ['注意力', '焦点'],
          duplicateAction: 'create' as const,
        },
      ],
      now: INITIAL_TIME + 2,
    };

    await expect(repository.importStudyItems(input)).resolves.toEqual({
      outcome: 'created',
      importedCount: 2,
      skippedCount: 1,
    });
    await expect(repository.listLexiconItems(lexicon.id)).resolves.toHaveLength(3);
    await expect(repository.importStudyItems(input)).resolves.toEqual({
      outcome: 'duplicate',
      importedCount: 2,
      skippedCount: 1,
    });
    await expect(repository.getStatus()).resolves.toMatchObject({
      counts: { words: 3, studyItems: 3, lexiconItems: 3 },
    });
    await expect(
      repository.importStudyItems({
        ...input,
        rows: [{ ...input.rows[2]!, hints: ['changed'] }],
      }),
    ).rejects.toBeInstanceOf(ImportBatchConflictError);
  });

  it('rolls back all rows when a write fails during import', async () => {
    const database = createDatabase();
    let generatedId = 0;
    const repository = createFingerMemoryRepository(database, {
      scheduler,
      clock: () => INITIAL_TIME,
      idFactory: () => {
        generatedId += 1;
        return generatedId === 3 ? 'id-2' : `id-${generatedId}`;
      },
    });
    await repository.initialize();
    const lexicon = await repository.createLexicon({
      name: '回滚测试',
      description: '',
      now: INITIAL_TIME,
    });

    await expect(
      repository.importStudyItems({
        batchId: 'rollback-batch',
        lexiconId: lexicon.id,
        rows: [
          {
            term: 'first',
            meaning: '第一',
            hints: [],
            duplicateAction: 'create',
          },
          {
            term: 'second',
            meaning: '第二',
            hints: [],
            duplicateAction: 'create',
          },
        ],
        now: INITIAL_TIME + 1,
      }),
    ).rejects.toBeDefined();
    await expect(repository.getStatus()).resolves.toMatchObject({
      counts: { lexicons: 1, words: 0, studyItems: 0, lexiconItems: 0 },
    });
    await expect(repository.listLexiconItems(lexicon.id)).resolves.toEqual([]);
  });

  it('imports and exports a personal-scale 500-item batch', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    const lexicon = await repository.createLexicon({
      name: '容量测试',
      description: '',
      now: INITIAL_TIME,
    });
    const rows = Array.from({ length: 500 }, (_, index) => ({
      term: `word-${String(index + 1).padStart(3, '0')}`,
      meaning: `释义 ${index + 1}`,
      hints: [`提示 ${index + 1}`],
      duplicateAction: 'create' as const,
    }));

    await expect(
      repository.importStudyItems({
        batchId: 'capacity-batch',
        lexiconId: lexicon.id,
        rows,
        now: INITIAL_TIME + 1,
      }),
    ).resolves.toMatchObject({ importedCount: 500, skippedCount: 0 });
    const snapshot = await repository.exportSnapshot(INITIAL_TIME + 2);
    expect(snapshot.tables.studyItems).toHaveLength(500);
    expect(snapshot.tables.lexiconItems).toHaveLength(500);
    expect(snapshot.tables.words).toHaveLength(500);
  }, 20_000);
});

describe('study note persistence', () => {
  it('keeps one note per study item and clears it with empty content', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    const lexicon = await repository.createLexicon({
      name: '笔记测试',
      description: '',
      now: INITIAL_TIME,
    });
    const created = await repository.createStudyItem({
      lexiconId: lexicon.id,
      term: 'memory',
      meaning: '记忆',
      acceptedAnswers: [],
      hints: [],
      now: INITIAL_TIME + 1,
    });
    const studyItemId = created.practiceItem.studyItem.id;

    const firstNote = await repository.saveStudyNote({
      studyItemId,
      content: '注意 memory 和 remember 的区别。',
      now: INITIAL_TIME + 2,
    });
    expect(firstNote).toMatchObject({
      studyItemId,
      content: '注意 memory 和 remember 的区别。',
    });

    const updatedNote = await repository.saveStudyNote({
      studyItemId,
      content: '补充：memory 也有内存的含义。',
      now: INITIAL_TIME + 3,
    });
    expect(updatedNote?.id).toBe(firstNote?.id);
    await expect(repository.listStudyNotes()).resolves.toMatchObject([
      {
        id: firstNote?.id,
        studyItemId,
        content: '补充：memory 也有内存的含义。',
        createdAt: INITIAL_TIME + 2,
        updatedAt: INITIAL_TIME + 3,
      },
    ]);

    await expect(
      repository.saveStudyNote({
        studyItemId,
        content: '   ',
        now: INITIAL_TIME + 4,
      }),
    ).resolves.toBeUndefined();
    await expect(repository.listStudyNotes()).resolves.toEqual([]);
    await expect(
      repository.saveStudyNote({
        studyItemId: 'missing-study-item',
        content: '不能保存',
        now: INITIAL_TIME + 5,
      }),
    ).rejects.toBeInstanceOf(StudyItemNotFoundError);
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

  it('accepts a v2 flashcard rating only after reveal-for-check', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);

    const result = await repository.submitReview(
      createReviewInput({
        submissionId: 'flashcard-checked',
        assessmentType: 'flashcard-self-report',
        assessmentPolicyVersion: 2,
        assessmentStage: 'reveal-for-check',
        answerRevealed: true,
      }),
    );

    expect(result.log).toMatchObject({
      assessmentType: 'flashcard-self-report',
      assessmentPolicyVersion: 2,
      assessmentStage: 'reveal-for-check',
      answerWasHidden: true,
      answerRevealed: true,
      priorExposure: false,
      explicitNotRemembered: false,
    });
  });

  it('keeps guided practice and plain answer reveal out of formal review', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);

    await expect(
      repository.submitReview(
        createReviewInput({
          assessmentType: 'guided-practice',
          assessmentPolicyVersion: 2,
          assessmentStage: 'reveal-for-check',
          answerRevealed: true,
        }),
      ),
    ).rejects.toMatchObject({ reason: 'guided-practice' });
    await expect(
      repository.submitReview(
        createReviewInput({
          submissionId: 'typed-revealed',
          assessmentType: 'typed-recall',
          assessmentPolicyVersion: 2,
          assessmentStage: 'reveal-for-check',
          answerRevealed: true,
        }),
      ),
    ).rejects.toMatchObject({ reason: 'answer-revealed' });
  });

  it('allows an explicit v2 not-remembered event to record Again after exposure', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);

    const result = await repository.submitReview(
      createReviewInput({
        rating: 'again',
        answerWasHidden: false,
        hintUsed: true,
        answerRevealed: true,
        assessmentType: 'typed-recall',
        assessmentPolicyVersion: 2,
        assessmentStage: 'reveal-for-check',
        priorExposure: true,
        explicitNotRemembered: true,
      }),
    );

    expect(result.log).toMatchObject({
      rating: 'again',
      explicitNotRemembered: true,
      priorExposure: true,
    });
  });

  it('previews all four ratings without writing and matches the submitted plan', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);

    const previews = await repository.previewReviewIntervals({
      studyItemId: 'study-memory',
      reviewedAt: INITIAL_TIME,
    });
    expect(Object.keys(previews).sort()).toEqual(['again', 'easy', 'good', 'hard']);
    expect(previews.again.dueAt).toBeLessThanOrEqual(previews.hard.dueAt);
    expect(previews.hard.dueAt).toBeLessThanOrEqual(previews.good.dueAt);
    expect(previews.good.dueAt).toBeLessThanOrEqual(previews.easy.dueAt);
    await expect(repository.getStatus()).resolves.toMatchObject({
      counts: { reviewStates: 0, reviewLogs: 0 },
    });

    const submitted = await repository.submitReview(createReviewInput());
    expect(submitted.state.dueAt).toBe(previews.good.dueAt);
  });

  it('returns the same formal state for every mode query', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);
    const submitted = await repository.submitReview(
      createReviewInput({ modeId: 'lyrics-scroll' }),
    );

    await expect(repository.listReviewStates(['study-memory'])).resolves.toEqual([
      submitted.state,
    ]);
    await expect(repository.listReviewStates(['missing-study'])).resolves.toEqual([]);
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

  it('allows at most one formal review per study item in a session', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);
    const wordEntry = await database.words.get('word-memory');
    const studyItem = await database.studyItems.get('study-memory');
    if (wordEntry === undefined || studyItem === undefined) {
      throw new Error('seed data missing');
    }
    await repository.savePracticeSession(
      createPracticeSession({
        id: 'session-one-review',
        modeId: 'keyboard-recall',
        items: [{ wordEntry, studyItem }],
        now: INITIAL_TIME,
        reduceMotion: false,
      }),
    );
    const pendingAnswer = {
      studyItemId: 'study-memory',
      response: 'memory',
      answerWasHidden: true,
      hintUsed: false,
      answerRevealed: false,
      inputMatchedAnswer: true,
      practiceAttemptId: 'practice-one-review',
      reviewSubmissionId: 'submission-1',
      submittedAt: INITIAL_TIME,
    };
    const session = await repository.getLatestPracticeSession();
    if (session === undefined) {
      throw new Error('practice session missing');
    }
    await repository.savePracticeSession({
      ...session,
      pendingAnswer,
      updatedAt: INITIAL_TIME,
    });

    await expect(
      repository.submitReview(
        createReviewInput({ sessionId: 'session-one-review' }),
      ),
    ).resolves.toMatchObject({ outcome: 'created' });

    const reviewedSession = await repository.getLatestPracticeSession();
    if (reviewedSession === undefined || reviewedSession.pendingAnswer === null) {
      throw new Error('reviewed session missing');
    }
    await repository.savePracticeSession({
      ...reviewedSession,
      pendingAnswer: {
        ...reviewedSession.pendingAnswer,
        reviewSubmissionId: 'submission-session-limit',
        submittedAt: INITIAL_TIME + 60_000,
      },
      updatedAt: INITIAL_TIME + 60_000,
    });

    await expect(
      repository.submitReview(
        createReviewInput({
          submissionId: 'submission-session-limit',
          sessionId: 'session-one-review',
          rating: 'easy',
          reviewedAt: INITIAL_TIME + 60_000,
        }),
      ),
    ).rejects.toMatchObject({ reason: 'session-review-limit' });
    await expect(repository.getStatus()).resolves.toMatchObject({
      counts: { reviewStates: 1, reviewLogs: 1 },
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

  it('lists review diagnostics with submission ids and detects state-log drift', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    await seedStudyItem(database);
    const first = await repository.submitReview(createReviewInput());
    await repository.submitReview(
      createReviewInput({
        submissionId: 'submission-diagnostic-2',
        rating: 'hard',
        reviewedAt: Math.max(INITIAL_TIME + 60_000, first.state.dueAt),
      }),
    );

    const history = await repository.listReviewHistory();
    expect(history).toHaveLength(1);
    expect(history[0]).toMatchObject({
      studyItem: { id: 'study-memory' },
      wordEntry: { term: 'memory', meaning: '记忆' },
      consistent: true,
      issue: null,
      logs: [
        { submissionId: 'submission-1' },
        { submissionId: 'submission-diagnostic-2' },
      ],
    });

    await database.reviewStates.update('study-memory', {
      dueAt: history[0]!.state.dueAt + 1,
    });
    await expect(repository.listReviewHistory()).resolves.toMatchObject([
      {
        consistent: false,
        issue: '当前计划与最新正式复习日志不一致。',
      },
    ]);
  });

  it('plans bounded new and due queues and honors a persisted pause', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    const lexicon = await repository.createLexicon({
      name: '队列测试',
      description: '',
      now: INITIAL_TIME,
    });
    const created = [];
    for (const [index, term] of ['memory', 'cursor', 'window'].entries()) {
      created.push(
        await repository.createStudyItem({
          lexiconId: lexicon.id,
          term,
          meaning: ['记忆', '光标', '窗口'][index]!,
          acceptedAnswers: [],
          hints: [],
          now: INITIAL_TIME + index + 1,
        }),
      );
    }
    await repository.submitReview(
      createReviewInput({
        submissionId: 'queue-review',
        studyItemId: created[0]!.practiceItem.studyItem.id,
        modeId: 'lyrics-scroll',
      }),
    );
    await database.reviewStates.update(created[0]!.practiceItem.studyItem.id, {
      dueAt: INITIAL_TIME + 30 * 60_000,
    });
    await repository.savePracticeQueueSettings(
      { newLimit: 1, reviewLimit: 1, paused: false },
      INITIAL_TIME + 10,
    );

    const plan = await repository.planPracticeQueue({
      at: INITIAL_TIME + 20,
      timeZone: 'UTC',
    });
    expect(plan).toMatchObject({
      dueAvailableCount: 1,
      newAvailableCount: 2,
      settings: { newLimit: 1, reviewLimit: 1, paused: false },
    });
    expect(plan.selected.map((entry) => entry.source)).toEqual(['review', 'new']);
    expect(plan.selected.map((entry) => entry.item.studyItem.id)).toContain(
      created[0]!.practiceItem.studyItem.id,
    );

    await repository.savePracticeQueueSettings(
      { newLimit: 1, reviewLimit: 1, paused: true },
      INITIAL_TIME + 30,
    );
    const paused = await repository.planPracticeQueue({
      at: INITIAL_TIME + 31,
      timeZone: 'UTC',
    });
    expect(paused.selected).toEqual([]);
    expect(paused).toMatchObject({ dueAvailableCount: 1, newAvailableCount: 2 });
    await expect(repository.getPracticeQueueSettings()).resolves.toEqual({
      newLimit: 1,
      reviewLimit: 1,
      paused: true,
    });
  });

  it('persists enabled mode preferences and refuses to disable every mode', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();

    await expect(repository.getPracticeModePreferences()).resolves.toEqual({
      disabledModeIds: [],
    });
    await expect(
      repository.savePracticeModePreferences(
        { disabledModeIds: ['keyboard-recall'] },
        INITIAL_TIME,
      ),
    ).resolves.toEqual({ disabledModeIds: ['keyboard-recall'] });
    await expect(repository.getPracticeModePreferences()).resolves.toEqual({
      disabledModeIds: ['keyboard-recall'],
    });
    await expect(
      repository.savePracticeModePreferences(
        {
          disabledModeIds: [
            'card-rain',
            'danmaku-stream',
            'keyboard-recall',
            'lyrics-scroll',
          ],
        },
        INITIAL_TIME + 1,
      ),
    ).rejects.toThrow('at least one practice mode');
  });

  it('persists and validates in-app reminder settings and state', async () => {
    const database = createDatabase();
    const repository = createRepository(database);
    await repository.initialize();
    const initial = await repository.getInAppReminderRecord();
    expect(initial.settings).toMatchObject({
      enabled: true,
      minIntervalMinutes: 30,
      maxIntervalMinutes: 90,
      dailyLimit: 3,
      quietStart: '22:00',
      quietEnd: '08:00',
      snoozeMinutes: 15,
    });

    const changed = {
      ...initial,
      settings: {
        ...initial.settings,
        enabled: false,
        dailyLimit: 2,
        quietStart: '21:30',
      },
      state: {
        ...initial.state,
        dayKey: '2026-11-15',
        shownToday: 1,
        nextAt: INITIAL_TIME + 60_000,
      },
    };
    await expect(
      repository.saveInAppReminderRecord(changed, INITIAL_TIME),
    ).resolves.toEqual(changed);
    await expect(repository.getInAppReminderRecord()).resolves.toEqual(changed);

    await expect(
      repository.saveInAppReminderRecord(
        {
          ...changed,
          settings: { ...changed.settings, minIntervalMinutes: 120 },
        },
        INITIAL_TIME + 1,
      ),
    ).rejects.toThrow('reminder interval');
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

  it('migrates a v3 backup without losing sessions or review history', async () => {
    const sourceDatabase = createDatabase();
    const sourceRepository = createRepository(sourceDatabase);
    await sourceRepository.initialize();
    await seedStudyItem(sourceDatabase);
    await sourceRepository.submitReview(createReviewInput());
    const wordEntry = await sourceDatabase.words.get('word-memory');
    const studyItem = await sourceDatabase.studyItems.get('study-memory');
    if (wordEntry === undefined || studyItem === undefined) {
      throw new Error('seed data missing');
    }
    await sourceRepository.savePracticeSession(
      createPracticeSession({
        id: 'v3-backed-session',
        modeId: 'lyrics-scroll',
        items: [{ wordEntry, studyItem }],
        now: INITIAL_TIME,
        reduceMotion: false,
      }),
    );
    const snapshot = await sourceRepository.exportSnapshot(INITIAL_TIME + 120_000);
    const legacy = structuredClone(snapshot);
    legacy.schemaVersion = 3;
    legacy.dataFormatVersion = 3;
    const versionRecord = legacy.tables.appMeta.find(
      (record) => record.key === APP_META_KEYS.dataFormatVersion,
    );
    if (versionRecord === undefined) {
      throw new Error('data format metadata missing');
    }
    versionRecord.value = '3';
    for (const session of legacy.tables.practiceSessions) {
      for (const queueItem of session.queue) {
        delete (queueItem as { source?: string }).source;
      }
    }

    const destinationDatabase = createDatabase();
    const destinationRepository = createRepository(destinationDatabase);
    await destinationRepository.initialize();
    await expect(
      destinationRepository.previewSnapshot(JSON.parse(JSON.stringify(legacy))),
    ).resolves.toMatchObject({
      schemaVersion: STORAGE_SCHEMA_VERSION,
      dataFormatVersion: DATA_FORMAT_VERSION,
      counts: { practiceSessions: 1, reviewStates: 1, reviewLogs: 1 },
    });
    await destinationRepository.importSnapshot(legacy);

    await expect(destinationRepository.getLatestPracticeSession()).resolves.toMatchObject({
      id: 'v3-backed-session',
      queue: [{ source: 'new' }],
    });
    await expect(destinationRepository.getReviewLog('submission-1')).resolves.toMatchObject({
      rating: 'good',
      afterCard: { cardId: 'study-memory' },
    });
  });

  it('upgrades a v4 backup to v5 without reinterpreting legacy review logs', async () => {
    const sourceDatabase = createDatabase();
    const sourceRepository = createRepository(sourceDatabase);
    await sourceRepository.initialize();
    await seedStudyItem(sourceDatabase);
    await sourceRepository.submitReview(createReviewInput());
    const snapshot = await sourceRepository.exportSnapshot(INITIAL_TIME + 120_000);
    const legacy = structuredClone(snapshot);
    legacy.schemaVersion = 4;
    legacy.dataFormatVersion = 4;
    const versionRecord = legacy.tables.appMeta.find(
      (record) => record.key === APP_META_KEYS.dataFormatVersion,
    );
    if (versionRecord === undefined) {
      throw new Error('data format metadata missing');
    }
    versionRecord.value = '4';
    delete (legacy.tables.reviewLogs[0] as { assessmentPolicyVersion?: number })
      .assessmentPolicyVersion;

    const destinationDatabase = createDatabase();
    const destinationRepository = createRepository(destinationDatabase);
    await destinationRepository.initialize();
    await expect(
      destinationRepository.previewSnapshot(JSON.parse(JSON.stringify(legacy))),
    ).resolves.toMatchObject({
      schemaVersion: STORAGE_SCHEMA_VERSION,
      dataFormatVersion: DATA_FORMAT_VERSION,
    });
    await destinationRepository.importSnapshot(legacy);

    const migratedLog = await destinationRepository.getReviewLog('submission-1');
    expect(migratedLog).toMatchObject({
      rating: 'good',
    });
    expect(migratedLog).not.toHaveProperty('assessmentPolicyVersion');
  });

  it('previews a valid backup without replacing the destination data', async () => {
    const sourceDatabase = createDatabase();
    const sourceRepository = createRepository(sourceDatabase);
    await sourceRepository.initialize();
    const inventory = await sourceRepository.createLexicon({
      name: '备份词库',
      description: '',
      now: INITIAL_TIME,
    });
    const created = await sourceRepository.createStudyItem({
      lexiconId: inventory.id,
      term: 'memory',
      meaning: '记忆',
      acceptedAnswers: [],
      hints: [],
      now: INITIAL_TIME + 1,
    });
    await sourceRepository.saveStudyNote({
      studyItemId: created.practiceItem.studyItem.id,
      content: '备份中的笔记',
      now: INITIAL_TIME + 2,
    });
    const snapshot = await sourceRepository.exportSnapshot(INITIAL_TIME + 3);

    const destinationDatabase = createDatabase();
    const destinationRepository = createRepository(destinationDatabase);
    await destinationRepository.initialize();
    const kept = await destinationRepository.createLexicon({
      name: '现有词库',
      description: '',
      now: INITIAL_TIME + 4,
    });

    await expect(destinationRepository.previewSnapshot(snapshot)).resolves.toMatchObject({
      schemaVersion: STORAGE_SCHEMA_VERSION,
      dataFormatVersion: DATA_FORMAT_VERSION,
      exportedAt: INITIAL_TIME + 3,
      counts: {
        lexicons: 1,
        words: 1,
        studyItems: 1,
        notes: 1,
      },
    });
    await expect(destinationRepository.listLexicons()).resolves.toMatchObject([
      { id: kept.id, name: '现有词库' },
    ]);
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
