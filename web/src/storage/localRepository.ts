import type {
  AppMetaRecord,
  Lexicon,
  LexiconItem,
  PracticeItem,
  PracticeModeId,
  PracticeSession,
  ReviewLog,
  ReviewState,
  StudyItem,
  StudyNote,
  TimestampMs,
  WordEntry,
} from '../core/domain';
import type {
  DueReviewItem,
  FingerMemoryRepository,
  CreateLexiconInput,
  CreateStudyItemInput,
  ImportStudyItemsInput,
  ImportStudyItemsResult,
  InAppReminderRecord,
  LexiconStudyItem,
  PracticeQueuePlan,
  PracticeQueueSettings,
  PracticeModePreferences,
  PracticeAttemptInput,
  PracticeAttemptResult,
  ReviewHistoryEntry,
  ReviewRatingPreviews,
  ReviewSubmissionInput,
  ReviewSubmissionResult,
  SaveStudyNoteInput,
  StorageSnapshot,
  StorageSnapshotPreview,
  StorageStatus,
  UpdateLexiconInput,
  UpdateStudyItemInput,
} from '../core/repository';
import { getLocalDayRange } from '../core/due';
import {
  ImportBatchConflictError,
  LexiconNotFoundError,
  StudyItemInUseError,
  StudyItemNotFoundError,
} from '../core/repository';
import type { ReviewScheduler } from '../core/scheduler';
import {
  normalizeStudyTerm,
  normalizeTextList,
  studyTextKey,
} from '../core/studyText';
import { createDefaultReviewScheduler } from '../scheduler/fsrsScheduler';
import {
  APP_META_KEYS,
  DATA_FORMAT_VERSION,
  FingerMemoryDatabase,
  STORAGE_SCHEMA_VERSION,
} from './database';
import {
  loadInAppReminderRecord,
  loadPracticeModePreferences,
  loadPracticeQueueSettings,
  parsePracticeQueueSettings,
  storeInAppReminderRecord,
  storePracticeModePreferences,
  storePracticeQueueSettings,
} from './practicePreferences';
import { LocalSnapshotStore } from './localSnapshotStore';
import { LocalPracticeSessionStore } from './localPracticeSessionStore';
import { LocalReviewStore } from './localReviewStore';
import {
  assertRepositoryIdentifier,
  assertRepositoryTimestamp,
} from './learningRecordInputs';
import {
  STARTER_LEXICON_ID,
  STARTER_WORDS,
} from './starterContent';

export { APP_META_KEYS };

export class UnsupportedDataFormatVersionError extends Error {
  constructor(actualVersion: string) {
    super(`Unsupported FingerMemory data format version: ${actualVersion}`);
    this.name = 'UnsupportedDataFormatVersionError';
  }
}

interface RepositoryOptions {
  scheduler?: Promise<ReviewScheduler>;
  clock?: () => TimestampMs;
  idFactory?: () => string;
}

const IMPORT_RECEIPT_KEY_PREFIX = 'import.receipt.';

interface ImportReceipt {
  fingerprint: string;
  importedCount: number;
  skippedCount: number;
}

function importFingerprint(input: ImportStudyItemsInput): string {
  return JSON.stringify({
    lexiconId: input.lexiconId,
    rows: input.rows.map((row) => [
      row.term.trim(),
      row.meaning.trim(),
      normalizeTextList(row.hints),
      row.duplicateAction,
    ]),
  });
}

function readImportReceipt(value: string): ImportReceipt | undefined {
  try {
    const parsed: unknown = JSON.parse(value);
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      'fingerprint' in parsed &&
      typeof parsed.fingerprint === 'string' &&
      'importedCount' in parsed &&
      typeof parsed.importedCount === 'number' &&
      Number.isInteger(parsed.importedCount) &&
      'skippedCount' in parsed &&
      typeof parsed.skippedCount === 'number' &&
      Number.isInteger(parsed.skippedCount)
    ) {
      return {
        fingerprint: parsed.fingerprint,
        importedCount: parsed.importedCount,
        skippedCount: parsed.skippedCount,
      };
    }
  } catch {
    return undefined;
  }
  return undefined;
}

function buildStudyItemEntities(input: {
  id: string;
  lexiconId: string;
  term: string;
  meaning: string;
  acceptedAnswers: string[];
  hints: string[];
  order: number;
  now: TimestampMs;
}): {
  wordEntry: WordEntry;
  studyItem: StudyItem;
  membership: LexiconItem;
} {
  const wordEntry: WordEntry = {
    id: `word:${input.id}`,
    term: input.term,
    meaning: input.meaning,
    acceptedAnswers: normalizeTextList(input.acceptedAnswers),
    hints: normalizeTextList(input.hints),
    normalizedTerm: normalizeStudyTerm(input.term),
    createdAt: input.now,
    updatedAt: input.now,
  };
  const studyItem: StudyItem = {
    id: `study:${input.id}`,
    wordEntryId: wordEntry.id,
    direction: 'meaning-to-spelling',
    createdAt: input.now,
    updatedAt: input.now,
  };
  const membership: LexiconItem = {
    lexiconId: input.lexiconId,
    studyItemId: studyItem.id,
    order: input.order,
    sourceHints: wordEntry.hints,
  };
  return { wordEntry, studyItem, membership };
}

class DexieFingerMemoryRepository implements FingerMemoryRepository {
  private readonly scheduler: Promise<ReviewScheduler>;
  private readonly clock: () => TimestampMs;
  private readonly idFactory: () => string;
  private readonly snapshots: LocalSnapshotStore;
  private readonly practiceSessionStore: LocalPracticeSessionStore;
  private readonly reviewStore: LocalReviewStore;

  constructor(
    private readonly database: FingerMemoryDatabase,
    options: RepositoryOptions,
  ) {
    this.scheduler = options.scheduler ?? createDefaultReviewScheduler();
    this.clock = options.clock ?? Date.now;
    this.idFactory = options.idFactory ?? (() => crypto.randomUUID());
    this.snapshots = new LocalSnapshotStore(database);
    this.practiceSessionStore = new LocalPracticeSessionStore(database);
    this.reviewStore = new LocalReviewStore(database, this.scheduler);
  }

  async initialize(): Promise<StorageStatus> {
    await this.database.open();

    const now = this.clock();
    assertRepositoryTimestamp(now, 'clock');
    await this.database.transaction('rw', this.database.appMeta, async () => {
      const dataFormatVersion = await this.database.appMeta.get(
        APP_META_KEYS.dataFormatVersion,
      );

      if (
        dataFormatVersion !== undefined &&
        dataFormatVersion.value !== String(DATA_FORMAT_VERSION)
      ) {
        throw new UnsupportedDataFormatVersionError(dataFormatVersion.value);
      }

      const records: AppMetaRecord[] = [];
      if (dataFormatVersion === undefined) {
        records.push({
          key: APP_META_KEYS.dataFormatVersion,
          value: String(DATA_FORMAT_VERSION),
          updatedAt: now,
        });
      }

      const initializedAt = await this.database.appMeta.get(
        APP_META_KEYS.initializedAt,
      );
      if (initializedAt === undefined) {
        records.push({
          key: APP_META_KEYS.initializedAt,
          value: new Date(now).toISOString(),
          updatedAt: now,
        });
      }

      if (records.length > 0) {
        await this.database.appMeta.bulkPut(records);
      }
    });

    return this.getStatus();
  }

  async getStatus(): Promise<StorageStatus> {
    await this.database.open();
    const scheduler = await this.scheduler;

    return this.database.transaction(
      'r',
      [
        this.database.appMeta,
        this.database.lexicons,
        this.database.words,
        this.database.studyItems,
        this.database.lexiconItems,
        this.database.notes,
        this.database.practiceAttempts,
        this.database.practiceSessions,
        this.database.reviewStates,
        this.database.reviewLogs,
      ],
      async () => {
        const [
          lexicons,
          words,
          studyItems,
          lexiconItems,
          notes,
          practiceAttempts,
          practiceSessions,
          reviewStates,
          reviewLogs,
          dataFormatVersion,
        ] = await Promise.all([
          this.database.lexicons.count(),
          this.database.words.count(),
          this.database.studyItems.count(),
          this.database.lexiconItems.count(),
          this.database.notes.count(),
          this.database.practiceAttempts.count(),
          this.database.practiceSessions.count(),
          this.database.reviewStates.count(),
          this.database.reviewLogs.count(),
          this.database.appMeta.get(APP_META_KEYS.dataFormatVersion),
        ]);

        if (
          dataFormatVersion === undefined ||
          dataFormatVersion.value !== String(DATA_FORMAT_VERSION)
        ) {
          throw new UnsupportedDataFormatVersionError(
            dataFormatVersion?.value ?? 'missing',
          );
        }

        return {
          schemaVersion: STORAGE_SCHEMA_VERSION,
          dataFormatVersion: DATA_FORMAT_VERSION,
          scheduler: {
            packageVersion: scheduler.packageVersion,
            modelVersion: scheduler.modelVersion,
          },
          counts: {
            lexicons,
            words,
            studyItems,
            lexiconItems,
            notes,
            practiceAttempts,
            practiceSessions,
            reviewStates,
            reviewLogs,
          },
        };
      },
    );
  }

  async listLexicons(includeArchived = true): Promise<Lexicon[]> {
    await this.database.open();
    const lexicons = await this.database.lexicons.toArray();
    return lexicons
      .filter((lexicon) => includeArchived || lexicon.archivedAt === null)
      .sort(
        (left, right) =>
          Number(left.archivedAt !== null) - Number(right.archivedAt !== null) ||
          left.name.localeCompare(right.name) ||
          left.id.localeCompare(right.id),
      );
  }

  async createLexicon(input: CreateLexiconInput): Promise<Lexicon> {
    const name = input.name.trim();
    if (name.length === 0) {
      throw new TypeError('lexicon name must not be empty');
    }
    assertRepositoryTimestamp(input.now, 'lexicon creation time');
    const lexicon: Lexicon = {
      id: `lexicon:${this.idFactory()}`,
      name,
      description: input.description.trim(),
      createdAt: input.now,
      updatedAt: input.now,
      archivedAt: null,
    };
    await this.database.open();
    await this.database.lexicons.add(lexicon);
    return lexicon;
  }

  async updateLexicon(input: UpdateLexiconInput): Promise<Lexicon> {
    assertRepositoryIdentifier(input.id, 'lexicon id');
    const name = input.name.trim();
    if (name.length === 0) {
      throw new TypeError('lexicon name must not be empty');
    }
    assertRepositoryTimestamp(input.now, 'lexicon update time');
    await this.database.open();

    return this.database.transaction('rw', this.database.lexicons, async () => {
      const existing = await this.database.lexicons.get(input.id);
      if (existing === undefined) {
        throw new LexiconNotFoundError(input.id);
      }
      const updated: Lexicon = {
        ...existing,
        name,
        description: input.description.trim(),
        updatedAt: input.now,
      };
      await this.database.lexicons.put(updated);
      return updated;
    });
  }

  async setLexiconArchived(
    id: string,
    archived: boolean,
    now: TimestampMs,
  ): Promise<Lexicon> {
    assertRepositoryIdentifier(id, 'lexicon id');
    assertRepositoryTimestamp(now, 'lexicon archive time');
    await this.database.open();

    return this.database.transaction('rw', this.database.lexicons, async () => {
      const existing = await this.database.lexicons.get(id);
      if (existing === undefined) {
        throw new LexiconNotFoundError(id);
      }
      const updated: Lexicon = {
        ...existing,
        archivedAt: archived ? now : null,
        updatedAt: now,
      };
      await this.database.lexicons.put(updated);
      return updated;
    });
  }

  async deleteLexicon(id: string): Promise<void> {
    assertRepositoryIdentifier(id, 'lexicon id');
    await this.database.open();

    await this.database.transaction(
      'rw',
      [this.database.lexicons, this.database.lexiconItems],
      async () => {
        const existing = await this.database.lexicons.get(id);
        if (existing === undefined) {
          throw new LexiconNotFoundError(id);
        }
        await this.database.lexiconItems.where('lexiconId').equals(id).delete();
        await this.database.lexicons.delete(id);
      },
    );
  }

  async listLexiconItems(lexiconId: string): Promise<LexiconStudyItem[]> {
    assertRepositoryIdentifier(lexiconId, 'lexicon id');
    await this.database.open();

    return this.database.transaction(
      'r',
      [
        this.database.lexicons,
        this.database.lexiconItems,
        this.database.studyItems,
        this.database.words,
      ],
      async () => {
        if ((await this.database.lexicons.get(lexiconId)) === undefined) {
          throw new LexiconNotFoundError(lexiconId);
        }
        const memberships = await this.database.lexiconItems
          .where('lexiconId')
          .equals(lexiconId)
          .toArray();
        memberships.sort((left, right) => left.order - right.order);

        const result: LexiconStudyItem[] = [];
        for (const membership of memberships) {
          const studyItem = await this.database.studyItems.get(membership.studyItemId);
          if (studyItem === undefined) {
            throw new StudyItemNotFoundError(membership.studyItemId);
          }
          const wordEntry = await this.database.words.get(studyItem.wordEntryId);
          if (wordEntry === undefined) {
            throw new StudyItemNotFoundError(studyItem.id);
          }
          result.push({ membership, practiceItem: { studyItem, wordEntry } });
        }
        return result;
      },
    );
  }

  async createStudyItem(input: CreateStudyItemInput): Promise<LexiconStudyItem> {
    assertRepositoryIdentifier(input.lexiconId, 'lexicon id');
    const term = input.term.trim();
    const meaning = input.meaning.trim();
    if (term.length === 0 || meaning.length === 0) {
      throw new TypeError('study item term and meaning must not be empty');
    }
    assertRepositoryTimestamp(input.now, 'study item creation time');
    const entityId = this.idFactory();
    await this.database.open();

    return this.database.transaction(
      'rw',
      [
        this.database.lexicons,
        this.database.words,
        this.database.studyItems,
        this.database.lexiconItems,
      ],
      async () => {
        if ((await this.database.lexicons.get(input.lexiconId)) === undefined) {
          throw new LexiconNotFoundError(input.lexiconId);
        }
        const memberships = await this.database.lexiconItems
          .where('lexiconId')
          .equals(input.lexiconId)
          .toArray();
        const order = memberships.reduce((maximum, item) => Math.max(maximum, item.order), -1) + 1;
        const { wordEntry, studyItem, membership } = buildStudyItemEntities({
          id: entityId,
          lexiconId: input.lexiconId,
          term,
          meaning,
          acceptedAnswers: input.acceptedAnswers,
          hints: input.hints,
          order,
          now: input.now,
        });

        await this.database.words.add(wordEntry);
        await this.database.studyItems.add(studyItem);
        await this.database.lexiconItems.add(membership);
        return { membership, practiceItem: { studyItem, wordEntry } };
      },
    );
  }

  async updateStudyItem(input: UpdateStudyItemInput): Promise<LexiconStudyItem> {
    assertRepositoryIdentifier(input.lexiconId, 'lexicon id');
    assertRepositoryIdentifier(input.studyItemId, 'study item id');
    const term = input.term.trim();
    const meaning = input.meaning.trim();
    if (term.length === 0 || meaning.length === 0) {
      throw new TypeError('study item term and meaning must not be empty');
    }
    assertRepositoryTimestamp(input.now, 'study item update time');
    await this.database.open();

    return this.database.transaction(
      'rw',
      [
        this.database.words,
        this.database.studyItems,
        this.database.lexiconItems,
      ],
      async () => {
        const studyItem = await this.database.studyItems.get(input.studyItemId);
        if (studyItem === undefined) {
          throw new StudyItemNotFoundError(input.studyItemId);
        }
        const wordEntry = await this.database.words.get(studyItem.wordEntryId);
        if (wordEntry === undefined) {
          throw new StudyItemNotFoundError(input.studyItemId);
        }
        const membership = await this.database.lexiconItems.get([
          input.lexiconId,
          input.studyItemId,
        ]);
        if (membership === undefined) {
          throw new StudyItemNotFoundError(input.studyItemId);
        }

        const updatedWord: WordEntry = {
          ...wordEntry,
          term,
          meaning,
          acceptedAnswers: normalizeTextList(input.acceptedAnswers),
          hints: normalizeTextList(input.hints),
          normalizedTerm: normalizeStudyTerm(term),
          updatedAt: input.now,
        };
        const updatedStudyItem = { ...studyItem, updatedAt: input.now };
        const updatedMembership = { ...membership, sourceHints: updatedWord.hints };
        await this.database.words.put(updatedWord);
        await this.database.studyItems.put(updatedStudyItem);
        await this.database.lexiconItems.put(updatedMembership);
        return {
          membership: updatedMembership,
          practiceItem: { studyItem: updatedStudyItem, wordEntry: updatedWord },
        };
      },
    );
  }

  async removeStudyItemFromLexicon(
    lexiconId: string,
    studyItemId: string,
  ): Promise<void> {
    assertRepositoryIdentifier(lexiconId, 'lexicon id');
    assertRepositoryIdentifier(studyItemId, 'study item id');
    await this.database.open();

    await this.database.transaction(
      'rw',
      [this.database.lexicons, this.database.lexiconItems, this.database.practiceSessions],
      async () => {
        if ((await this.database.lexicons.get(lexiconId)) === undefined) {
          throw new LexiconNotFoundError(lexiconId);
        }
        const sessions = await this.database.practiceSessions.toArray();
        const inUse = sessions.some(
          (session) =>
            session.status !== 'completed' &&
            session.queue.some((item) => item.studyItemId === studyItemId),
        );
        if (inUse) {
          throw new StudyItemInUseError(studyItemId);
        }
        await this.database.lexiconItems.delete([lexiconId, studyItemId]);
      },
    );
  }

  async importStudyItems(input: ImportStudyItemsInput): Promise<ImportStudyItemsResult> {
    assertRepositoryIdentifier(input.batchId, 'import batch id');
    assertRepositoryIdentifier(input.lexiconId, 'lexicon id');
    assertRepositoryTimestamp(input.now, 'import time');
    if (input.rows.length === 0) {
      throw new TypeError('import rows must not be empty');
    }
    for (const row of input.rows) {
      if (row.term.trim().length === 0 || row.meaning.trim().length === 0) {
        throw new TypeError('import row term and meaning must not be empty');
      }
      if (row.duplicateAction !== 'skip' && row.duplicateAction !== 'create') {
        throw new TypeError('import row duplicate action is invalid');
      }
    }

    const fingerprint = importFingerprint(input);
    const receiptKey = `${IMPORT_RECEIPT_KEY_PREFIX}${input.batchId}`;
    await this.database.open();

    return this.database.transaction(
      'rw',
      [
        this.database.appMeta,
        this.database.lexicons,
        this.database.words,
        this.database.studyItems,
        this.database.lexiconItems,
      ],
      async () => {
        const existingReceiptRecord = await this.database.appMeta.get(receiptKey);
        if (existingReceiptRecord !== undefined) {
          const existingReceipt = readImportReceipt(existingReceiptRecord.value);
          if (existingReceipt?.fingerprint !== fingerprint) {
            throw new ImportBatchConflictError(input.batchId);
          }
          return {
            outcome: 'duplicate',
            importedCount: existingReceipt.importedCount,
            skippedCount: existingReceipt.skippedCount,
          };
        }

        if ((await this.database.lexicons.get(input.lexiconId)) === undefined) {
          throw new LexiconNotFoundError(input.lexiconId);
        }

        const memberships = await this.database.lexiconItems
          .where('lexiconId')
          .equals(input.lexiconId)
          .toArray();
        let nextOrder =
          memberships.reduce((maximum, membership) => Math.max(maximum, membership.order), -1) +
          1;
        const existingKeys = new Set<string>();
        for (const membership of memberships) {
          const studyItem = await this.database.studyItems.get(membership.studyItemId);
          const wordEntry =
            studyItem === undefined
              ? undefined
              : await this.database.words.get(studyItem.wordEntryId);
          if (studyItem === undefined || wordEntry === undefined) {
            throw new StudyItemNotFoundError(membership.studyItemId);
          }
          existingKeys.add(studyTextKey(wordEntry.term, wordEntry.meaning));
        }

        const importedKeys = new Set<string>();
        let importedCount = 0;
        let skippedCount = 0;
        for (const row of input.rows) {
          const key = studyTextKey(row.term, row.meaning);
          const alreadyExists = existingKeys.has(key) || importedKeys.has(key);
          if (row.duplicateAction === 'skip' && alreadyExists) {
            skippedCount += 1;
            continue;
          }

          const entities = buildStudyItemEntities({
            id: this.idFactory(),
            lexiconId: input.lexiconId,
            term: row.term.trim(),
            meaning: row.meaning.trim(),
            acceptedAnswers: [],
            hints: row.hints,
            order: nextOrder,
            now: input.now,
          });
          await this.database.words.add(entities.wordEntry);
          await this.database.studyItems.add(entities.studyItem);
          await this.database.lexiconItems.add(entities.membership);
          importedKeys.add(key);
          importedCount += 1;
          nextOrder += 1;
        }

        const receipt: ImportReceipt = {
          fingerprint,
          importedCount,
          skippedCount,
        };
        await this.database.appMeta.put({
          key: receiptKey,
          value: JSON.stringify(receipt),
          updatedAt: input.now,
        });
        return { outcome: 'created', importedCount, skippedCount };
      },
    );
  }

  async listStudyNotes(): Promise<StudyNote[]> {
    await this.database.open();
    const notes = await this.database.notes.toArray();
    return notes.sort(
      (left, right) =>
        left.studyItemId.localeCompare(right.studyItemId) ||
        right.updatedAt - left.updatedAt ||
        left.id.localeCompare(right.id),
    );
  }

  async saveStudyNote(input: SaveStudyNoteInput): Promise<StudyNote | undefined> {
    assertRepositoryIdentifier(input.studyItemId, 'study item id');
    assertRepositoryTimestamp(input.now, 'study note update time');
    await this.database.open();

    return this.database.transaction(
      'rw',
      [this.database.studyItems, this.database.notes],
      async () => {
        if ((await this.database.studyItems.get(input.studyItemId)) === undefined) {
          throw new StudyItemNotFoundError(input.studyItemId);
        }
        const existing = await this.database.notes
          .where('studyItemId')
          .equals(input.studyItemId)
          .first();
        if (input.content.trim().length === 0) {
          if (existing !== undefined) {
            await this.database.notes.delete(existing.id);
          }
          return undefined;
        }

        const note: StudyNote = {
          id: existing?.id ?? `note:${input.studyItemId}`,
          studyItemId: input.studyItemId,
          content: input.content,
          createdAt: existing?.createdAt ?? input.now,
          updatedAt: input.now,
        };
        await this.database.notes.put(note);
        return note;
      },
    );
  }

  async ensureStarterContent(): Promise<PracticeItem[]> {
    await this.database.open();
    const now = this.clock();
    assertRepositoryTimestamp(now, 'clock');

    await this.database.transaction(
      'rw',
      [
        this.database.lexicons,
        this.database.words,
        this.database.studyItems,
        this.database.lexiconItems,
      ],
      async () => {
        if ((await this.database.lexicons.get(STARTER_LEXICON_ID)) === undefined) {
          await this.database.lexicons.add({
            id: STARTER_LEXICON_ID,
            name: '入门词库',
            description: '用于开始本地练习的示例词条',
            createdAt: now,
            updatedAt: now,
            archivedAt: null,
          });
        }

        for (const [order, starterWord] of STARTER_WORDS.entries()) {
          const wordId = `starter-word:${starterWord.slug}`;
          const studyItemId = `starter-study:${starterWord.slug}`;
          if ((await this.database.words.get(wordId)) === undefined) {
            await this.database.words.add({
              id: wordId,
              term: starterWord.term,
              meaning: starterWord.meaning,
              acceptedAnswers: [starterWord.term],
              hints: starterWord.hints,
              normalizedTerm: normalizeStudyTerm(starterWord.term),
              createdAt: now,
              updatedAt: now,
            });
          }
          if ((await this.database.studyItems.get(studyItemId)) === undefined) {
            await this.database.studyItems.add({
              id: studyItemId,
              wordEntryId: wordId,
              direction: 'meaning-to-spelling',
              createdAt: now,
              updatedAt: now,
            });
          }
          if (
            (await this.database.lexiconItems.get([
              STARTER_LEXICON_ID,
              studyItemId,
            ])) === undefined
          ) {
            await this.database.lexiconItems.add({
              lexiconId: STARTER_LEXICON_ID,
              studyItemId,
              order,
              sourceHints: starterWord.hints,
            });
          }
        }
      },
    );

    return this.listPracticeItems(STARTER_WORDS.length);
  }

  async getPracticeQueueSettings(): Promise<PracticeQueueSettings> {
    return loadPracticeQueueSettings(this.database);
  }

  async savePracticeQueueSettings(
    settings: PracticeQueueSettings,
    now: TimestampMs,
  ): Promise<PracticeQueueSettings> {
    return storePracticeQueueSettings(this.database, settings, now);
  }

  async getPracticeModePreferences(): Promise<PracticeModePreferences> {
    return loadPracticeModePreferences(this.database);
  }

  async savePracticeModePreferences(
    preferences: PracticeModePreferences,
    now: TimestampMs,
  ): Promise<PracticeModePreferences> {
    return storePracticeModePreferences(this.database, preferences, now);
  }

  async getInAppReminderRecord(): Promise<InAppReminderRecord> {
    return loadInAppReminderRecord(this.database);
  }

  async saveInAppReminderRecord(
    record: InAppReminderRecord,
    now: TimestampMs,
  ): Promise<InAppReminderRecord> {
    return storeInAppReminderRecord(this.database, record, now);
  }

  async planPracticeQueue(input: {
    at: TimestampMs;
    timeZone: string;
  }): Promise<PracticeQueuePlan> {
    assertRepositoryTimestamp(input.at, 'queue plan time');
    if (input.timeZone.trim().length === 0) {
      throw new TypeError('queue plan time zone must not be empty');
    }
    const day = getLocalDayRange(input.at, input.timeZone);
    await this.database.open();

    return this.database.transaction(
      'r',
      [
        this.database.appMeta,
        this.database.lexicons,
        this.database.lexiconItems,
        this.database.studyItems,
        this.database.words,
        this.database.reviewStates,
      ],
      async () => {
        const settings = parsePracticeQueueSettings(
          (
            await this.database.appMeta.get(
              APP_META_KEYS.practiceQueueSettings,
            )
          )?.value,
        );
        const activeLexiconIds = new Set(
          (await this.database.lexicons.toArray())
            .filter((lexicon) => lexicon.archivedAt === null)
            .map((lexicon) => lexicon.id),
        );
        const studyItemIds = new Set(
          (await this.database.lexiconItems.toArray())
            .filter((membership) => activeLexiconIds.has(membership.lexiconId))
            .map((membership) => membership.studyItemId),
        );
        const practiceItems: PracticeItem[] = [];
        for (const studyItemId of studyItemIds) {
          const studyItem = await this.database.studyItems.get(studyItemId);
          if (studyItem === undefined) {
            throw new Error(`Study item ${studyItemId} has no persisted record`);
          }
          const wordEntry = await this.database.words.get(studyItem.wordEntryId);
          if (wordEntry === undefined) {
            throw new Error(`Study item ${studyItem.id} has no word entry`);
          }
          practiceItems.push({ studyItem, wordEntry });
        }
        practiceItems.sort(
          (left, right) =>
            left.wordEntry.term.localeCompare(right.wordEntry.term) ||
            left.studyItem.id.localeCompare(right.studyItem.id),
        );

        const states = (await this.database.reviewStates.toArray()).filter((state) =>
          studyItemIds.has(state.studyItemId),
        );
        const stateByStudyItemId = new Map(
          states.map((state) => [state.studyItemId, state]),
        );
        const dueItems = practiceItems
          .filter((item) => {
            const state = stateByStudyItemId.get(item.studyItem.id);
            return state !== undefined && state.dueAt < day.endAt;
          })
          .sort((left, right) => {
            const leftDue = stateByStudyItemId.get(left.studyItem.id)?.dueAt ?? 0;
            const rightDue = stateByStudyItemId.get(right.studyItem.id)?.dueAt ?? 0;
            return leftDue - rightDue || left.studyItem.id.localeCompare(right.studyItem.id);
          });
        const newItems = practiceItems.filter(
          (item) => !stateByStudyItemId.has(item.studyItem.id),
        );

        const selected = settings.paused
          ? []
          : [
              ...dueItems.slice(0, settings.reviewLimit).map((item) => ({
                item,
                source: 'review' as const,
              })),
              ...newItems.slice(0, settings.newLimit).map((item) => ({
                item,
                source: 'new' as const,
              })),
            ];

        return {
          at: input.at,
          timeZone: input.timeZone,
          dayStartAt: day.startAt,
          dayEndAt: day.endAt,
          settings,
          dueAvailableCount: dueItems.length,
          newAvailableCount: newItems.length,
          selected,
        };
      },
    );
  }

  async listPracticeItems(limit = 50): Promise<PracticeItem[]> {
    if (!Number.isInteger(limit) || limit <= 0) {
      throw new TypeError('practice item limit must be a positive integer');
    }
    await this.database.open();

    return this.database.transaction(
      'r',
      [
        this.database.lexicons,
        this.database.lexiconItems,
        this.database.studyItems,
        this.database.words,
      ],
      async () => {
        const activeLexiconIds = new Set(
          (await this.database.lexicons.toArray())
            .filter((lexicon) => lexicon.archivedAt === null)
            .map((lexicon) => lexicon.id),
        );
        const memberships = await this.database.lexiconItems.toArray();
        const studyItemIds = new Set(
          memberships
            .filter((membership) => activeLexiconIds.has(membership.lexiconId))
            .map((membership) => membership.studyItemId),
        );
        const practiceItems: PracticeItem[] = [];
        for (const studyItemId of studyItemIds) {
          const studyItem = await this.database.studyItems.get(studyItemId);
          if (studyItem === undefined) {
            throw new Error(`Study item ${studyItemId} has no persisted record`);
          }
          const wordEntry = await this.database.words.get(studyItem.wordEntryId);
          if (wordEntry === undefined) {
            throw new Error(`Study item ${studyItem.id} has no word entry`);
          }
          practiceItems.push({ studyItem, wordEntry });
        }
        practiceItems.sort(
          (left, right) =>
            left.wordEntry.term.localeCompare(right.wordEntry.term) ||
            left.studyItem.id.localeCompare(right.studyItem.id),
        );
        return practiceItems.slice(0, limit);
      },
    );
  }

  async listPracticeItemsByStudyItemIds(studyItemIds: string[]): Promise<PracticeItem[]> {
    await this.database.open();
    const uniqueIds = [...new Set(studyItemIds)];
    if (uniqueIds.some((id) => id.trim().length === 0)) {
      throw new TypeError('study item ids must not contain empty values');
    }

    return this.database.transaction(
      'r',
      [this.database.studyItems, this.database.words],
      async () => {
        const practiceItems: PracticeItem[] = [];
        for (const studyItemId of uniqueIds) {
          const studyItem = await this.database.studyItems.get(studyItemId);
          if (studyItem === undefined) {
            continue;
          }
          const wordEntry = await this.database.words.get(studyItem.wordEntryId);
          if (wordEntry === undefined) {
            continue;
          }
          practiceItems.push({ studyItem, wordEntry });
        }
        return practiceItems;
      },
    );
  }

  async getLatestPracticeSession(
    modeId?: PracticeModeId,
  ): Promise<PracticeSession | undefined> {
    return this.practiceSessionStore.getLatestPracticeSession(modeId);
  }

  async savePracticeSession(
    session: PracticeSession,
  ): Promise<PracticeSession> {
    return this.practiceSessionStore.savePracticeSession(session);
  }

  async recordPracticeAttempt(
    input: PracticeAttemptInput,
  ): Promise<PracticeAttemptResult> {
    return this.practiceSessionStore.recordPracticeAttempt(input);
  }

  async submitReview(
    input: ReviewSubmissionInput,
  ): Promise<ReviewSubmissionResult> {
    return this.reviewStore.submitReview(input);
  }

  async previewReviewIntervals(input: {
    studyItemId: string;
    reviewedAt: TimestampMs;
  }): Promise<ReviewRatingPreviews> {
    return this.reviewStore.previewReviewIntervals(input);
  }

  async getReviewState(
    studyItemId: string,
  ): Promise<ReviewState | undefined> {
    return this.reviewStore.getReviewState(studyItemId);
  }

  async getReviewLog(
    submissionId: string,
  ): Promise<ReviewLog | undefined> {
    return this.reviewStore.getReviewLog(submissionId);
  }

  async listReviewStates(studyItemIds?: string[]): Promise<ReviewState[]> {
    return this.reviewStore.listReviewStates(studyItemIds);
  }

  async listReviewHistory(): Promise<ReviewHistoryEntry[]> {
    return this.reviewStore.listReviewHistory();
  }

  async listDueReviewItems(at: TimestampMs): Promise<DueReviewItem[]> {
    return this.reviewStore.listDueReviewItems(at);
  }

  async exportSnapshot(exportedAt: TimestampMs): Promise<StorageSnapshot> {
    return this.snapshots.exportSnapshot(exportedAt);
  }

  async previewSnapshot(snapshot: unknown): Promise<StorageSnapshotPreview> {
    return this.snapshots.previewSnapshot(snapshot);
  }

  async importSnapshot(snapshot: unknown): Promise<StorageStatus> {
    await this.snapshots.importSnapshot(snapshot);
    return this.getStatus();
  }
}

export function createFingerMemoryRepository(
  database = new FingerMemoryDatabase(),
  options: RepositoryOptions = {},
): FingerMemoryRepository {
  return new DexieFingerMemoryRepository(database, options);
}

export const localRepository = createFingerMemoryRepository();
