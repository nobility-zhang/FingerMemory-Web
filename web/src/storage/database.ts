import { Dexie, type EntityTable, type Table } from 'dexie';
import type {
  AppMetaRecord,
  EntityId,
  Lexicon,
  LexiconItem,
  PracticeAttempt,
  PracticeSession,
  ReviewLog,
  ReviewState,
  StudyItem,
  StudyNote,
  WordEntry,
} from '../core/domain';

export const DATABASE_NAME = 'finger-memory';
export const STORAGE_SCHEMA_VERSION = 3;
export const DATA_FORMAT_VERSION = 3;

export const APP_META_KEYS = {
  initializedAt: 'database.initializedAt',
  dataFormatVersion: 'data.formatVersion',
  practiceQueueSettings: 'practice.queueSettings',
  practiceModePreferences: 'practice.modePreferences',
  inAppReminder: 'practice.inAppReminder',
} as const;

export const LEGACY_STORES_V1 = {
  appMeta: 'key',
  lexicons: 'id, name, updatedAt',
  studyItems: 'id, term, normalization',
  lexiconItems:
    '[lexiconId+studyItemId], lexiconId, studyItemId, [lexiconId+order]',
  notes: 'id, studyItemId',
} as const;

export const STORES_V2 = {
  appMeta: 'key',
  lexicons: 'id, name, updatedAt',
  words: 'id, normalizedTerm',
  studyItems: 'id, wordEntryId, direction, [wordEntryId+direction]',
  lexiconItems:
    '[lexiconId+studyItemId], lexiconId, studyItemId, [lexiconId+order]',
  notes: 'id, studyItemId',
  practiceAttempts: 'id, studyItemId, occurredAt, modeId',
  reviewStates: 'studyItemId, dueAt, scheduleStatus',
  reviewLogs: 'submissionId, studyItemId, reviewedAt, rating',
} as const;

interface LegacyStudyItemV1 {
  id: EntityId;
  term: string;
  primaryAnswer: string;
  acceptedAnswers: string[];
  normalization: string;
  createdAt: number;
  updatedAt: number;
}

export class FingerMemoryDatabase extends Dexie {
  appMeta!: EntityTable<AppMetaRecord, 'key'>;
  lexicons!: EntityTable<Lexicon, 'id'>;
  words!: EntityTable<WordEntry, 'id'>;
  studyItems!: EntityTable<StudyItem, 'id'>;
  lexiconItems!: Table<LexiconItem, [EntityId, EntityId]>;
  notes!: EntityTable<StudyNote, 'id'>;
  practiceAttempts!: EntityTable<PracticeAttempt, 'id'>;
  practiceSessions!: EntityTable<PracticeSession, 'id'>;
  reviewStates!: EntityTable<ReviewState, 'studyItemId'>;
  reviewLogs!: EntityTable<ReviewLog, 'submissionId'>;

  constructor(name = DATABASE_NAME) {
    super(name);

    this.version(1).stores(LEGACY_STORES_V1);
    this.version(2)
      .stores(STORES_V2)
      .upgrade(async (transaction) => {
        const legacyStudyItems = await transaction
          .table<LegacyStudyItemV1>('studyItems')
          .toArray();

        if (legacyStudyItems.length > 0) {
          const words: WordEntry[] = legacyStudyItems.map((item) => ({
            id: `word:${item.id}`,
            term: item.term,
            meaning: item.primaryAnswer,
            acceptedAnswers: item.acceptedAnswers,
            hints: [],
            normalizedTerm: item.normalization,
            createdAt: item.createdAt,
            updatedAt: item.updatedAt,
          }));
          const studyItems: StudyItem[] = legacyStudyItems.map((item) => ({
            id: item.id,
            wordEntryId: `word:${item.id}`,
            direction: 'meaning-to-spelling',
            createdAt: item.createdAt,
            updatedAt: item.updatedAt,
          }));

          await transaction.table<WordEntry>('words').bulkPut(words);
          await transaction.table<StudyItem>('studyItems').bulkPut(studyItems);
        }

        const versionRecord = await transaction
          .table<AppMetaRecord>('appMeta')
          .get(APP_META_KEYS.dataFormatVersion);

        if (versionRecord !== undefined && versionRecord.value !== '1') {
          throw new Error(`Unsupported v1 data format version: ${versionRecord.value}`);
        }

        if (versionRecord !== undefined) {
          await transaction.table<AppMetaRecord>('appMeta').put({
            ...versionRecord,
            value: '2',
            updatedAt: Date.now(),
          });
        }
      });
    this.version(STORAGE_SCHEMA_VERSION)
      .stores({
        ...STORES_V2,
        practiceSessions: 'id, updatedAt, status, modeId',
      })
      .upgrade(async (transaction) => {
        const versionRecord = await transaction
          .table<AppMetaRecord>('appMeta')
          .get(APP_META_KEYS.dataFormatVersion);

        if (versionRecord !== undefined && versionRecord.value !== '2') {
          throw new Error(`Unsupported v2 data format version: ${versionRecord.value}`);
        }

        if (versionRecord !== undefined) {
          await transaction.table<AppMetaRecord>('appMeta').put({
            ...versionRecord,
            value: String(DATA_FORMAT_VERSION),
            updatedAt: Date.now(),
          });
        }
      });
  }
}
