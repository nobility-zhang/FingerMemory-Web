import type {
  AppMetaRecord,
  Lexicon,
  PracticeItem,
  PracticeModeId,
  PracticeSession,
  ReviewLog,
  ReviewState,
  StudyNote,
  TimestampMs,
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
import type { ReviewScheduler } from '../core/scheduler';
import {
  normalizeStudyTerm,
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
  storeInAppReminderRecord,
  storePracticeModePreferences,
  storePracticeQueueSettings,
} from './practicePreferences';
import { LocalSnapshotStore } from './localSnapshotStore';
import { LocalPracticeSessionStore } from './localPracticeSessionStore';
import { LocalReviewStore } from './localReviewStore';
import { LocalLexiconStore } from './localLexiconStore';
import { LocalPracticeQueueStore } from './localPracticeQueueStore';
import { LocalStudyNoteStore } from './localStudyNoteStore';
import {
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

class DexieFingerMemoryRepository implements FingerMemoryRepository {
  private readonly scheduler: Promise<ReviewScheduler>;
  private readonly clock: () => TimestampMs;
  private readonly snapshots: LocalSnapshotStore;
  private readonly practiceSessionStore: LocalPracticeSessionStore;
  private readonly reviewStore: LocalReviewStore;
  private readonly lexiconStore: LocalLexiconStore;
  private readonly practiceQueueStore: LocalPracticeQueueStore;
  private readonly studyNoteStore: LocalStudyNoteStore;

  constructor(
    private readonly database: FingerMemoryDatabase,
    options: RepositoryOptions,
  ) {
    this.scheduler = options.scheduler ?? createDefaultReviewScheduler();
    this.clock = options.clock ?? Date.now;
    const idFactory = options.idFactory ?? (() => crypto.randomUUID());
    this.snapshots = new LocalSnapshotStore(database);
    this.practiceSessionStore = new LocalPracticeSessionStore(database);
    this.reviewStore = new LocalReviewStore(database, this.scheduler);
    this.lexiconStore = new LocalLexiconStore(database, idFactory);
    this.practiceQueueStore = new LocalPracticeQueueStore(database);
    this.studyNoteStore = new LocalStudyNoteStore(database);
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
    return this.lexiconStore.listLexicons(includeArchived);
  }

  async createLexicon(input: CreateLexiconInput): Promise<Lexicon> {
    return this.lexiconStore.createLexicon(input);
  }

  async updateLexicon(input: UpdateLexiconInput): Promise<Lexicon> {
    return this.lexiconStore.updateLexicon(input);
  }

  async setLexiconArchived(
    id: string,
    archived: boolean,
    now: TimestampMs,
  ): Promise<Lexicon> {
    return this.lexiconStore.setLexiconArchived(id, archived, now);
  }

  async deleteLexicon(id: string): Promise<void> {
    return this.lexiconStore.deleteLexicon(id);
  }

  async listLexiconItems(
    lexiconId: string,
  ): Promise<LexiconStudyItem[]> {
    return this.lexiconStore.listLexiconItems(lexiconId);
  }

  async createStudyItem(
    input: CreateStudyItemInput,
  ): Promise<LexiconStudyItem> {
    return this.lexiconStore.createStudyItem(input);
  }

  async updateStudyItem(
    input: UpdateStudyItemInput,
  ): Promise<LexiconStudyItem> {
    return this.lexiconStore.updateStudyItem(input);
  }

  async removeStudyItemFromLexicon(
    lexiconId: string,
    studyItemId: string,
  ): Promise<void> {
    return this.lexiconStore.removeStudyItemFromLexicon(
      lexiconId,
      studyItemId,
    );
  }

  async importStudyItems(
    input: ImportStudyItemsInput,
  ): Promise<ImportStudyItemsResult> {
    return this.lexiconStore.importStudyItems(input);
  }

  async listStudyNotes(): Promise<StudyNote[]> {
    return this.studyNoteStore.listStudyNotes();
  }

  async saveStudyNote(input: SaveStudyNoteInput): Promise<StudyNote | undefined> {
    return this.studyNoteStore.saveStudyNote(input);
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

    return this.practiceQueueStore.listPracticeItems(
      STARTER_WORDS.length,
    );
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
    return this.practiceQueueStore.planPracticeQueue(input);
  }

  async listPracticeItems(limit = 50): Promise<PracticeItem[]> {
    return this.practiceQueueStore.listPracticeItems(limit);
  }

  async listPracticeItemsByStudyItemIds(
    studyItemIds: string[],
  ): Promise<PracticeItem[]> {
    return this.practiceQueueStore.listPracticeItemsByStudyItemIds(
      studyItemIds,
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
