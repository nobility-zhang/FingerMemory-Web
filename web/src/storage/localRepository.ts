import type {
  AppMetaRecord,
  Lexicon,
  LexiconItem,
  PracticeAttempt,
  PracticeItem,
  PracticeModeId,
  PracticeSession,
  ReviewCardSnapshot,
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
  LexiconStudyItem,
  PracticeQueuePlan,
  PracticeQueueSettings,
  PracticeAttemptInput,
  PracticeAttemptResult,
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
  InvalidStorageSnapshotError,
  LexiconNotFoundError,
  PracticeAttemptConflictError,
  ReviewStateVersionMismatchError,
  ReviewSubmissionConflictError,
  ReviewSubmissionRejectedError,
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
import { parseStorageSnapshot } from './snapshotSchema';
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

function assertIdentifier(value: string, label: string): void {
  if (value.trim().length === 0) {
    throw new TypeError(`${label} must not be empty`);
  }
}

function assertTimestamp(value: TimestampMs, label: string): void {
  if (!Number.isFinite(value) || value <= 0) {
    throw new TypeError(`${label} must be a positive finite timestamp`);
  }
}

function inputMatchesWord(word: WordEntry, response: string): boolean {
  const normalizedResponse = normalizeStudyTerm(response);
  if (normalizedResponse.length === 0) {
    return false;
  }

  return [word.term, ...word.acceptedAnswers].some(
    (answer) => normalizeStudyTerm(answer) === normalizedResponse,
  );
}

const IMPORT_RECEIPT_KEY_PREFIX = 'import.receipt.';

const DEFAULT_PRACTICE_QUEUE_SETTINGS: PracticeQueueSettings = {
  newLimit: 10,
  reviewLimit: 50,
  paused: false,
};

function assertPracticeQueueSettings(settings: PracticeQueueSettings): void {
  if (!Number.isInteger(settings.newLimit) || settings.newLimit < 0 || settings.newLimit > 100) {
    throw new TypeError('new-item limit must be an integer between 0 and 100');
  }
  if (
    !Number.isInteger(settings.reviewLimit) ||
    settings.reviewLimit < 0 ||
    settings.reviewLimit > 500
  ) {
    throw new TypeError('review limit must be an integer between 0 and 500');
  }
  if (typeof settings.paused !== 'boolean') {
    throw new TypeError('queue paused flag must be a boolean');
  }
}

function readPracticeQueueSettings(value: string | undefined): PracticeQueueSettings {
  if (value === undefined) {
    return { ...DEFAULT_PRACTICE_QUEUE_SETTINGS };
  }
  try {
    const parsed: unknown = JSON.parse(value);
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      'newLimit' in parsed &&
      typeof parsed.newLimit === 'number' &&
      'reviewLimit' in parsed &&
      typeof parsed.reviewLimit === 'number' &&
      'paused' in parsed &&
      typeof parsed.paused === 'boolean'
    ) {
      const settings = {
        newLimit: parsed.newLimit,
        reviewLimit: parsed.reviewLimit,
        paused: parsed.paused,
      };
      assertPracticeQueueSettings(settings);
      return settings;
    }
  } catch {
    return { ...DEFAULT_PRACTICE_QUEUE_SETTINGS };
  }
  return { ...DEFAULT_PRACTICE_QUEUE_SETTINGS };
}

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

function reviewFingerprint(input: ReviewSubmissionInput): string {
  return JSON.stringify([
    input.submissionId,
    input.studyItemId,
    input.modeId,
    input.sessionId,
    input.rating,
    input.response,
    input.answerWasHidden,
    input.hintUsed,
    input.answerRevealed,
    input.abandoned,
    input.userConfirmedRating,
    input.reviewedAt,
  ]);
}

function practiceFingerprint(input: PracticeAttemptInput): string {
  return JSON.stringify([
    input.id,
    input.studyItemId,
    input.modeId,
    input.sessionId,
    input.response,
    input.answerWasHidden,
    input.hintUsed,
    input.answerRevealed,
    input.abandoned,
    input.occurredAt,
  ]);
}

function cardFromState(state: ReviewState): ReviewCardSnapshot {
  return {
    cardId: state.cardId,
    stability: state.stability,
    stabilityFast: state.stabilityFast,
    difficulty: state.difficulty,
    dueAt: state.dueAt,
    lastReviewAt: state.lastReviewAt,
    state: state.state,
    scheduleStatus: state.scheduleStatus,
    reps: state.reps,
    lapses: state.lapses,
    scheduledDays: state.scheduledDays,
    learningStep: state.learningStep,
  };
}

function stateFromReviewLog(log: ReviewLog): ReviewState {
  return {
    studyItemId: log.studyItemId,
    ...log.afterCard,
    schedulerConfig: log.schedulerConfig,
    schedulerPackageVersion: log.schedulerPackageVersion,
    schedulerModelVersion: log.schedulerModelVersion,
    updatedAt: log.reviewedAt,
  };
}

function plainPracticeSession(session: PracticeSession): PracticeSession {
  return {
    ...session,
    queue: session.queue.map((item) => ({ ...item })),
    interactions: Object.fromEntries(
      Object.entries(session.interactions).map(([studyItemId, interaction]) => [
        studyItemId,
        { ...interaction },
      ]),
    ),
    pendingAnswer:
      session.pendingAnswer === null ? null : { ...session.pendingAnswer },
  };
}

function arraysEqual<T>(left: readonly T[], right: readonly T[]): boolean {
  return left.length === right.length && left.every((value, index) => value === right[index]);
}

function recordsEqual(
  left: Record<string, number>,
  right: Record<string, number>,
): boolean {
  const leftEntries = Object.entries(left).sort(([a], [b]) => a.localeCompare(b));
  const rightEntries = Object.entries(right).sort(([a], [b]) => a.localeCompare(b));
  return (
    leftEntries.length === rightEntries.length &&
    leftEntries.every(
      ([key, value], index) =>
        rightEntries[index]?.[0] === key && rightEntries[index]?.[1] === value,
    )
  );
}

function schedulerConfigsEqual(
  left: ReviewState['schedulerConfig'],
  right: ReviewState['schedulerConfig'],
): boolean {
  return (
    left.packageVersion === right.packageVersion &&
    left.modelVersion === right.modelVersion &&
    arraysEqual(left.weights, right.weights) &&
    left.fractionalDays === right.fractionalDays &&
    recordsEqual(left.desiredRetention, right.desiredRetention) &&
    left.enableFuzz === right.enableFuzz &&
    left.maximumInterval === right.maximumInterval &&
    left.clearStatsOnForget === right.clearStatsOnForget &&
    left.enableShortTerm === right.enableShortTerm &&
    arraysEqual(left.learningSteps, right.learningSteps) &&
    arraysEqual(left.relearningSteps, right.relearningSteps) &&
    left.graduatingInterval === right.graduatingInterval &&
    left.allowModelOverride === right.allowModelOverride
  );
}

function hasSameSchedulerConfig(
  state: ReviewState,
  scheduler: ReviewScheduler,
): boolean {
  return (
    state.schedulerPackageVersion === scheduler.packageVersion &&
    state.schedulerModelVersion === scheduler.modelVersion &&
    schedulerConfigsEqual(state.schedulerConfig, scheduler.config)
  );
}

function assertUniqueIds(values: string[], label: string): void {
  if (new Set(values).size !== values.length) {
    throw new InvalidStorageSnapshotError(`${label} contains duplicate ids`);
  }
}

function cardsHaveSameSchedulingState(
  left: ReviewCardSnapshot,
  right: ReviewCardSnapshot,
): boolean {
  return (
    left.cardId === right.cardId &&
    left.stability === right.stability &&
    left.stabilityFast === right.stabilityFast &&
    left.difficulty === right.difficulty &&
    left.dueAt === right.dueAt &&
    left.lastReviewAt === right.lastReviewAt &&
    left.state === right.state &&
    left.scheduleStatus === right.scheduleStatus &&
    left.reps === right.reps &&
    left.lapses === right.lapses &&
    left.scheduledDays === right.scheduledDays &&
    left.learningStep === right.learningStep
  );
}

function validateSnapshotReferences(snapshot: StorageSnapshot): void {
  const { tables } = snapshot;
  const dataFormatRecord = tables.appMeta.find(
    (record) => record.key === APP_META_KEYS.dataFormatVersion,
  );
  if (dataFormatRecord?.value !== String(DATA_FORMAT_VERSION)) {
    throw new InvalidStorageSnapshotError('data format metadata is missing or incompatible');
  }

  assertUniqueIds(tables.appMeta.map((record) => record.key), 'appMeta');
  assertUniqueIds(tables.lexicons.map((record) => record.id), 'lexicons');
  assertUniqueIds(tables.words.map((record) => record.id), 'words');
  assertUniqueIds(tables.studyItems.map((record) => record.id), 'studyItems');
  assertUniqueIds(tables.notes.map((record) => record.id), 'notes');
  assertUniqueIds(tables.practiceAttempts.map((record) => record.id), 'practiceAttempts');
  assertUniqueIds(tables.practiceSessions.map((record) => record.id), 'practiceSessions');
  assertUniqueIds(
    tables.reviewStates.map((record) => record.studyItemId),
    'reviewStates',
  );
  assertUniqueIds(
    tables.reviewLogs.map((record) => record.submissionId),
    'reviewLogs',
  );
  assertUniqueIds(
    tables.lexiconItems.map((record) => `${record.lexiconId}\u0000${record.studyItemId}`),
    'lexiconItems',
  );

  const lexiconIds = new Set(tables.lexicons.map((record) => record.id));
  const wordIds = new Set(tables.words.map((record) => record.id));
  const studyItemsById = new Map(tables.studyItems.map((record) => [record.id, record]));
  const studyItemIds = new Set(studyItemsById.keys());
  const practiceSessionIds = new Set(tables.practiceSessions.map((record) => record.id));
  const logsByStudyItem = new Map<string, ReviewLog[]>();

  for (const item of tables.studyItems) {
    if (!wordIds.has(item.wordEntryId)) {
      throw new InvalidStorageSnapshotError(
        `study item ${item.id} references a missing word entry`,
      );
    }
  }
  for (const item of tables.lexiconItems) {
    if (!lexiconIds.has(item.lexiconId) || !studyItemIds.has(item.studyItemId)) {
      throw new InvalidStorageSnapshotError('lexicon item references missing data');
    }
  }
  const noteStudyItemIds = new Set<string>();
  for (const note of tables.notes) {
    if (!studyItemIds.has(note.studyItemId)) {
      throw new InvalidStorageSnapshotError(`note ${note.id} references a missing study item`);
    }
    if (noteStudyItemIds.has(note.studyItemId)) {
      throw new InvalidStorageSnapshotError(
        `study item ${note.studyItemId} contains multiple notes`,
      );
    }
    noteStudyItemIds.add(note.studyItemId);
  }
  for (const attempt of tables.practiceAttempts) {
    if (
      !studyItemIds.has(attempt.studyItemId) ||
      (attempt.sessionId !== null && !practiceSessionIds.has(attempt.sessionId))
    ) {
      throw new InvalidStorageSnapshotError(
        `practice attempt ${attempt.id} references a missing study item`,
      );
    }
  }
  for (const state of tables.reviewStates) {
    if (!studyItemIds.has(state.studyItemId) || state.cardId !== state.studyItemId) {
      throw new InvalidStorageSnapshotError(
        `review state ${state.studyItemId} references invalid study data`,
      );
    }
  }
  for (const log of tables.reviewLogs) {
    const studyItem = studyItemsById.get(log.studyItemId);
    if (
      studyItem === undefined ||
      studyItem.wordEntryId !== log.wordEntryId ||
      studyItem.direction !== log.direction ||
      (log.sessionId !== null && !practiceSessionIds.has(log.sessionId)) ||
      log.afterCard.cardId !== log.studyItemId ||
      log.srsRevlog.cardId !== log.studyItemId ||
      (log.beforeCard !== null && log.beforeCard.cardId !== log.studyItemId)
    ) {
      throw new InvalidStorageSnapshotError(
        `review log ${log.submissionId} references invalid study data`,
      );
    }
    const itemLogs = logsByStudyItem.get(log.studyItemId) ?? [];
    itemLogs.push(log);
    logsByStudyItem.set(log.studyItemId, itemLogs);
  }

  for (const session of tables.practiceSessions) {
    if (session.queue.length === 0 || session.currentIndex >= session.queue.length) {
      throw new InvalidStorageSnapshotError(
        `practice session ${session.id} has an invalid queue position`,
      );
    }
    const currentItem = session.queue[session.currentIndex];
    if (
      session.status !== 'completed' &&
      currentItem?.studyItemId !== session.currentStudyItemId
    ) {
      throw new InvalidStorageSnapshotError(
        `practice session ${session.id} current item does not match its queue`,
      );
    }
    const sessionStudyItemIds = new Set<string>();
    for (const queueItem of session.queue) {
      const studyItem = studyItemsById.get(queueItem.studyItemId);
      if (studyItem === undefined || studyItem.wordEntryId !== queueItem.wordEntryId) {
        throw new InvalidStorageSnapshotError(
          `practice session ${session.id} references invalid study data`,
        );
      }
      sessionStudyItemIds.add(queueItem.studyItemId);
    }
    for (const studyItemId of Object.keys(session.interactions)) {
      if (!sessionStudyItemIds.has(studyItemId)) {
        throw new InvalidStorageSnapshotError(
          `practice session ${session.id} has an invalid interaction`,
        );
      }
    }
    if (
      session.pendingAnswer !== null &&
      !sessionStudyItemIds.has(session.pendingAnswer.studyItemId)
    ) {
      throw new InvalidStorageSnapshotError(
        `practice session ${session.id} has an invalid pending answer`,
      );
    }
  }

  for (const state of tables.reviewStates) {
    const itemLogs = (logsByStudyItem.get(state.studyItemId) ?? []).sort(
      (left, right) =>
        left.reviewedAt - right.reviewedAt ||
        left.submissionId.localeCompare(right.submissionId),
    );
    const latestLog = itemLogs.at(-1);
    if (latestLog === undefined) {
      throw new InvalidStorageSnapshotError(
        `review state ${state.studyItemId} has no formal review log`,
      );
    }

    for (let index = 1; index < itemLogs.length; index += 1) {
      const previous = itemLogs[index - 1];
      const current = itemLogs[index];
      if (
        previous === undefined ||
        current === undefined ||
        current.beforeCard === null ||
        !cardsHaveSameSchedulingState(previous.afterCard, current.beforeCard)
      ) {
        throw new InvalidStorageSnapshotError(
          `review log chain is inconsistent for study item ${state.studyItemId}`,
        );
      }
    }

    if (!cardsHaveSameSchedulingState(latestLog.afterCard, cardFromState(state))) {
      throw new InvalidStorageSnapshotError(
        `review state ${state.studyItemId} does not match its latest review log`,
      );
    }
  }
}

function parseAndValidateSnapshot(input: unknown): StorageSnapshot {
  let snapshot: StorageSnapshot;
  try {
    snapshot = parseStorageSnapshot(input);
  } catch (error) {
    throw new InvalidStorageSnapshotError(
      error instanceof Error ? error.message : 'snapshot schema validation failed',
    );
  }
  validateSnapshotReferences(snapshot);
  return snapshot;
}

function snapshotPreview(snapshot: StorageSnapshot): StorageSnapshotPreview {
  const counts = snapshot.tables;
  return {
    snapshotVersion: snapshot.snapshotVersion,
    schemaVersion: snapshot.schemaVersion,
    dataFormatVersion: snapshot.dataFormatVersion,
    exportedAt: snapshot.exportedAt,
    counts: {
      lexicons: counts.lexicons.length,
      words: counts.words.length,
      studyItems: counts.studyItems.length,
      lexiconItems: counts.lexiconItems.length,
      notes: counts.notes.length,
      practiceAttempts: counts.practiceAttempts.length,
      practiceSessions: counts.practiceSessions.length,
      reviewStates: counts.reviewStates.length,
      reviewLogs: counts.reviewLogs.length,
    },
  };
}

class DexieFingerMemoryRepository implements FingerMemoryRepository {
  private readonly scheduler: Promise<ReviewScheduler>;
  private readonly clock: () => TimestampMs;
  private readonly idFactory: () => string;

  constructor(
    private readonly database: FingerMemoryDatabase,
    options: RepositoryOptions,
  ) {
    this.scheduler = options.scheduler ?? createDefaultReviewScheduler();
    this.clock = options.clock ?? Date.now;
    this.idFactory = options.idFactory ?? (() => crypto.randomUUID());
  }

  async initialize(): Promise<StorageStatus> {
    await this.database.open();

    const now = this.clock();
    assertTimestamp(now, 'clock');
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
    assertTimestamp(input.now, 'lexicon creation time');
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
    assertIdentifier(input.id, 'lexicon id');
    const name = input.name.trim();
    if (name.length === 0) {
      throw new TypeError('lexicon name must not be empty');
    }
    assertTimestamp(input.now, 'lexicon update time');
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
    assertIdentifier(id, 'lexicon id');
    assertTimestamp(now, 'lexicon archive time');
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
    assertIdentifier(id, 'lexicon id');
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
    assertIdentifier(lexiconId, 'lexicon id');
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
    assertIdentifier(input.lexiconId, 'lexicon id');
    const term = input.term.trim();
    const meaning = input.meaning.trim();
    if (term.length === 0 || meaning.length === 0) {
      throw new TypeError('study item term and meaning must not be empty');
    }
    assertTimestamp(input.now, 'study item creation time');
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
    assertIdentifier(input.lexiconId, 'lexicon id');
    assertIdentifier(input.studyItemId, 'study item id');
    const term = input.term.trim();
    const meaning = input.meaning.trim();
    if (term.length === 0 || meaning.length === 0) {
      throw new TypeError('study item term and meaning must not be empty');
    }
    assertTimestamp(input.now, 'study item update time');
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
    assertIdentifier(lexiconId, 'lexicon id');
    assertIdentifier(studyItemId, 'study item id');
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
    assertIdentifier(input.batchId, 'import batch id');
    assertIdentifier(input.lexiconId, 'lexicon id');
    assertTimestamp(input.now, 'import time');
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
    assertIdentifier(input.studyItemId, 'study item id');
    assertTimestamp(input.now, 'study note update time');
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
    assertTimestamp(now, 'clock');

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
    await this.database.open();
    const record = await this.database.appMeta.get(APP_META_KEYS.practiceQueueSettings);
    return readPracticeQueueSettings(record?.value);
  }

  async savePracticeQueueSettings(
    settings: PracticeQueueSettings,
    now: TimestampMs,
  ): Promise<PracticeQueueSettings> {
    assertPracticeQueueSettings(settings);
    assertTimestamp(now, 'queue settings update time');
    const normalized = { ...settings };
    await this.database.open();
    await this.database.appMeta.put({
      key: APP_META_KEYS.practiceQueueSettings,
      value: JSON.stringify(normalized),
      updatedAt: now,
    });
    return normalized;
  }

  async planPracticeQueue(input: {
    at: TimestampMs;
    timeZone: string;
  }): Promise<PracticeQueuePlan> {
    assertTimestamp(input.at, 'queue plan time');
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
        const settings = readPracticeQueueSettings(
          (await this.database.appMeta.get(APP_META_KEYS.practiceQueueSettings))?.value,
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
    await this.database.open();
    const sessions = await this.database.practiceSessions.toArray();
    return sessions
      .filter((session) => modeId === undefined || session.modeId === modeId)
      .sort(
        (left, right) =>
          right.updatedAt - left.updatedAt ||
          right.createdAt - left.createdAt ||
          right.id.localeCompare(left.id),
      )[0];
  }

  async savePracticeSession(session: PracticeSession): Promise<PracticeSession> {
    const plainSession = plainPracticeSession(session);
    assertIdentifier(plainSession.id, 'practice session id');
    assertTimestamp(plainSession.createdAt, 'practice session creation time');
    assertTimestamp(plainSession.updatedAt, 'practice session update time');
    if (plainSession.updatedAt < plainSession.createdAt) {
      throw new TypeError('practice session update time cannot precede creation time');
    }
    if (plainSession.queue.length === 0) {
      throw new TypeError('practice session queue must not be empty');
    }
    if (
      !Number.isInteger(plainSession.currentIndex) ||
      plainSession.currentIndex < 0 ||
      plainSession.currentIndex >= plainSession.queue.length
    ) {
      throw new TypeError('practice session current index is out of range');
    }
    if (
      !Number.isFinite(plainSession.speed) ||
      plainSession.speed < 0.25 ||
      plainSession.speed > 4
    ) {
      throw new TypeError('practice session speed is out of range');
    }

    await this.database.open();
    return this.database.transaction(
      'rw',
      [this.database.practiceSessions, this.database.studyItems, this.database.words],
      async () => {
        const studyItemIds = new Set<string>();
        for (const queueItem of plainSession.queue) {
          const studyItem = await this.database.studyItems.get(queueItem.studyItemId);
          if (studyItem === undefined || studyItem.wordEntryId !== queueItem.wordEntryId) {
            throw new Error('Practice session queue references invalid study data');
          }
          studyItemIds.add(queueItem.studyItemId);
        }

        const currentQueueItem = plainSession.queue[plainSession.currentIndex];
        if (
          plainSession.status !== 'completed' &&
          (currentQueueItem === undefined ||
            currentQueueItem.studyItemId !== plainSession.currentStudyItemId)
        ) {
          throw new Error('Practice session current item does not match its queue');
        }
        for (const studyItemId of Object.keys(plainSession.interactions)) {
          if (!studyItemIds.has(studyItemId)) {
            throw new Error('Practice session interaction references an invalid study item');
          }
        }
        if (
          plainSession.pendingAnswer !== null &&
          !studyItemIds.has(plainSession.pendingAnswer.studyItemId)
        ) {
          throw new Error('Practice session pending answer references invalid study data');
        }

        const otherSessions = await this.database.practiceSessions.toArray();
        for (const otherSession of otherSessions) {
          if (
            otherSession.id !== plainSession.id &&
            otherSession.status !== 'completed'
          ) {
            await this.database.practiceSessions.put({
              ...otherSession,
              status: 'completed',
              pendingAnswer: null,
              updatedAt: plainSession.updatedAt,
              completedAt: plainSession.updatedAt,
              endedReason: 'abandoned',
            });
          }
        }
        await this.database.practiceSessions.put(plainSession);
        return plainSession;
      },
    );
  }

  async recordPracticeAttempt(
    input: PracticeAttemptInput,
  ): Promise<PracticeAttemptResult> {
    assertIdentifier(input.id, 'practice attempt id');
    assertIdentifier(input.studyItemId, 'study item id');
    assertIdentifier(input.modeId, 'mode id');
    if (input.sessionId !== null) {
      assertIdentifier(input.sessionId, 'practice session id');
    }
    assertTimestamp(input.occurredAt, 'practice attempt time');
    const requestFingerprint = practiceFingerprint(input);

    return this.database.transaction(
      'rw',
      [this.database.studyItems, this.database.words, this.database.practiceAttempts],
      async () => {
        const existing = await this.database.practiceAttempts.get(input.id);
        if (existing !== undefined) {
          if (existing.requestFingerprint !== requestFingerprint) {
            throw new PracticeAttemptConflictError(input.id);
          }
          return { outcome: 'duplicate', attempt: existing };
        }

        const studyItem = await this.database.studyItems.get(input.studyItemId);
        if (studyItem === undefined) {
          throw new ReviewSubmissionRejectedError('study-item-not-found');
        }
        if (studyItem.direction !== 'meaning-to-spelling') {
          throw new ReviewSubmissionRejectedError('unsupported-direction');
        }

        const wordEntry = await this.database.words.get(studyItem.wordEntryId);
        if (wordEntry === undefined) {
          throw new Error(`Study item ${studyItem.id} references a missing word entry`);
        }

        const attempt: PracticeAttempt = {
          id: input.id,
          studyItemId: studyItem.id,
          modeId: input.modeId,
          sessionId: input.sessionId,
          direction: studyItem.direction,
          promptText: wordEntry.meaning,
          response: input.response,
          inputMatchedAnswer: inputMatchesWord(wordEntry, input.response),
          answerWasHidden: input.answerWasHidden,
          hintUsed: input.hintUsed,
          answerRevealed: input.answerRevealed,
          abandoned: input.abandoned,
          occurredAt: input.occurredAt,
          requestFingerprint,
        };

        await this.database.practiceAttempts.add(attempt);
        return { outcome: 'created', attempt };
      },
    );
  }

  async submitReview(input: ReviewSubmissionInput): Promise<ReviewSubmissionResult> {
    assertIdentifier(input.submissionId, 'review submission id');
    assertIdentifier(input.studyItemId, 'study item id');
    assertIdentifier(input.modeId, 'mode id');
    if (input.sessionId !== null) {
      assertIdentifier(input.sessionId, 'practice session id');
    }
    assertTimestamp(input.reviewedAt, 'review time');

    const scheduler = await this.scheduler;
    const requestFingerprint = reviewFingerprint(input);

    return this.database.transaction(
      'rw',
      [
        this.database.practiceSessions,
        this.database.studyItems,
        this.database.words,
        this.database.reviewStates,
        this.database.reviewLogs,
      ],
      async () => {
        const existingLog = await this.database.reviewLogs.get(input.submissionId);
        if (existingLog !== undefined) {
          if (existingLog.requestFingerprint !== requestFingerprint) {
            throw new ReviewSubmissionConflictError(input.submissionId);
          }
          const existingState = await this.database.reviewStates.get(
            existingLog.studyItemId,
          );
          if (existingState === undefined) {
            throw new Error(
              `Review log ${existingLog.submissionId} has no matching review state`,
            );
          }
          return {
            outcome: 'duplicate',
            state: stateFromReviewLog(existingLog),
            log: existingLog,
          };
        }

        if (!input.userConfirmedRating) {
          throw new ReviewSubmissionRejectedError('rating-not-confirmed');
        }
        if (!input.answerWasHidden) {
          throw new ReviewSubmissionRejectedError('answer-not-hidden');
        }
        if (input.hintUsed) {
          throw new ReviewSubmissionRejectedError('hint-used');
        }
        if (input.answerRevealed) {
          throw new ReviewSubmissionRejectedError('answer-revealed');
        }
        if (input.abandoned) {
          throw new ReviewSubmissionRejectedError('abandoned');
        }

        if (input.sessionId !== null) {
          const session = await this.database.practiceSessions.get(input.sessionId);
          if (session === undefined) {
            throw new ReviewSubmissionRejectedError('session-not-found');
          }
          if (session.status !== 'active') {
            throw new ReviewSubmissionRejectedError('session-not-active');
          }
          const pendingAnswer = session.pendingAnswer;
          if (
            pendingAnswer === null ||
            pendingAnswer.reviewSubmissionId !== input.submissionId ||
            pendingAnswer.studyItemId !== input.studyItemId ||
            pendingAnswer.response !== input.response ||
            pendingAnswer.answerWasHidden !== input.answerWasHidden ||
            pendingAnswer.hintUsed !== input.hintUsed ||
            pendingAnswer.answerRevealed !== input.answerRevealed ||
            pendingAnswer.submittedAt !== input.reviewedAt
          ) {
            throw new ReviewSubmissionRejectedError('session-pending-mismatch');
          }
          const sessionLogs = await this.database.reviewLogs.toArray();
          const alreadyReviewed = sessionLogs.some(
            (log) =>
              log.sessionId === input.sessionId &&
              log.studyItemId === input.studyItemId,
          );
          if (alreadyReviewed) {
            throw new ReviewSubmissionRejectedError('session-review-limit');
          }
        }

        const studyItem = await this.database.studyItems.get(input.studyItemId);
        if (studyItem === undefined) {
          throw new ReviewSubmissionRejectedError('study-item-not-found');
        }
        if (studyItem.direction !== 'meaning-to-spelling') {
          throw new ReviewSubmissionRejectedError('unsupported-direction');
        }

        const wordEntry = await this.database.words.get(studyItem.wordEntryId);
        if (wordEntry === undefined) {
          throw new Error(`Study item ${studyItem.id} references a missing word entry`);
        }

        const existingState = await this.database.reviewStates.get(studyItem.id);
        if (existingState !== undefined && !hasSameSchedulerConfig(existingState, scheduler)) {
          throw new ReviewStateVersionMismatchError();
        }
        if (
          existingState?.lastReviewAt !== null &&
          existingState?.lastReviewAt !== undefined &&
          input.reviewedAt < existingState.lastReviewAt
        ) {
          throw new ReviewSubmissionRejectedError('review-time-out-of-order');
        }

        const beforeCard =
          existingState === undefined ? null : cardFromState(existingState);
        const card =
          beforeCard ?? scheduler.createCard(studyItem.id, input.reviewedAt);
        const scheduled = scheduler.review({
          card,
          rating: input.rating,
          now: input.reviewedAt,
        });
        const state: ReviewState = {
          studyItemId: studyItem.id,
          ...scheduled.card,
          schedulerConfig: scheduler.config,
          schedulerPackageVersion: scheduler.packageVersion,
          schedulerModelVersion: scheduler.modelVersion,
          updatedAt: input.reviewedAt,
        };
        const log: ReviewLog = {
          submissionId: input.submissionId,
          studyItemId: studyItem.id,
          wordEntryId: wordEntry.id,
          modeId: input.modeId,
          sessionId: input.sessionId,
          direction: studyItem.direction,
          rating: input.rating,
          reviewedAt: input.reviewedAt,
          response: input.response,
          inputMatchedAnswer: inputMatchesWord(wordEntry, input.response),
          answerWasHidden: input.answerWasHidden,
          hintUsed: input.hintUsed,
          answerRevealed: input.answerRevealed,
          abandoned: input.abandoned,
          beforeCard,
          afterCard: scheduled.card,
          srsRevlog: scheduled.revlog,
          schedulerConfig: scheduler.config,
          schedulerPackageVersion: scheduler.packageVersion,
          schedulerModelVersion: scheduler.modelVersion,
          requestFingerprint,
        };

        await this.database.reviewStates.put(state);
        await this.database.reviewLogs.add(log);
        return { outcome: 'created', state, log };
      },
    );
  }

  async previewReviewIntervals(input: {
    studyItemId: string;
    reviewedAt: TimestampMs;
  }): Promise<ReviewRatingPreviews> {
    assertIdentifier(input.studyItemId, 'study item id');
    assertTimestamp(input.reviewedAt, 'review preview time');
    const scheduler = await this.scheduler;

    return this.database.transaction(
      'r',
      [this.database.studyItems, this.database.reviewStates],
      async () => {
        const studyItem = await this.database.studyItems.get(input.studyItemId);
        if (studyItem === undefined) {
          throw new ReviewSubmissionRejectedError('study-item-not-found');
        }
        if (studyItem.direction !== 'meaning-to-spelling') {
          throw new ReviewSubmissionRejectedError('unsupported-direction');
        }
        const existingState = await this.database.reviewStates.get(input.studyItemId);
        if (existingState !== undefined && !hasSameSchedulerConfig(existingState, scheduler)) {
          throw new ReviewStateVersionMismatchError();
        }
        if (
          existingState?.lastReviewAt !== null &&
          existingState?.lastReviewAt !== undefined &&
          input.reviewedAt < existingState.lastReviewAt
        ) {
          throw new ReviewSubmissionRejectedError('review-time-out-of-order');
        }

        const card = existingState === undefined
          ? scheduler.createCard(input.studyItemId, input.reviewedAt)
          : cardFromState(existingState);
        const ratings = ['again', 'hard', 'good', 'easy'] as const;
        return Object.fromEntries(
          ratings.map((rating) => {
            const result = scheduler.review({
              card,
              rating,
              now: input.reviewedAt,
            });
            return [
              rating,
              {
                rating,
                dueAt: result.card.dueAt,
                scheduledDays: result.card.scheduledDays,
              },
            ];
          }),
        ) as ReviewRatingPreviews;
      },
    );
  }

  async getReviewState(studyItemId: string): Promise<ReviewState | undefined> {
    await this.database.open();
    return this.database.reviewStates.get(studyItemId);
  }

  async getReviewLog(submissionId: string): Promise<ReviewLog | undefined> {
    await this.database.open();
    return this.database.reviewLogs.get(submissionId);
  }

  async listDueReviewItems(at: TimestampMs): Promise<DueReviewItem[]> {
    assertTimestamp(at, 'due query time');
    await this.database.open();

    return this.database.transaction(
      'r',
      [this.database.reviewStates, this.database.studyItems, this.database.words],
      async () => {
        const states = await this.database.reviewStates
          .where('dueAt')
          .belowOrEqual(at)
          .toArray();
        states.sort(
          (left, right) =>
            left.dueAt - right.dueAt || left.studyItemId.localeCompare(right.studyItemId),
        );

        const dueItems: DueReviewItem[] = [];
        for (const reviewState of states) {
          const studyItem = await this.database.studyItems.get(reviewState.studyItemId);
          if (studyItem === undefined) {
            throw new Error(`Review state ${reviewState.studyItemId} has no study item`);
          }
          const wordEntry = await this.database.words.get(studyItem.wordEntryId);
          if (wordEntry === undefined) {
            throw new Error(`Study item ${studyItem.id} has no word entry`);
          }
          dueItems.push({ reviewState, studyItem, wordEntry });
        }

        return dueItems;
      },
    );
  }

  async exportSnapshot(exportedAt: TimestampMs): Promise<StorageSnapshot> {
    assertTimestamp(exportedAt, 'snapshot export time');
    await this.database.open();

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
      async () => ({
        snapshotVersion: 1,
        schemaVersion: STORAGE_SCHEMA_VERSION,
        dataFormatVersion: DATA_FORMAT_VERSION,
        exportedAt,
        tables: {
          appMeta: await this.database.appMeta.toArray(),
          lexicons: await this.database.lexicons.toArray(),
          words: await this.database.words.toArray(),
          studyItems: await this.database.studyItems.toArray(),
          lexiconItems: await this.database.lexiconItems.toArray(),
          notes: await this.database.notes.toArray(),
          practiceAttempts: await this.database.practiceAttempts.toArray(),
          practiceSessions: await this.database.practiceSessions.toArray(),
          reviewStates: await this.database.reviewStates.toArray(),
          reviewLogs: await this.database.reviewLogs.toArray(),
        },
      }),
    );
  }

  async previewSnapshot(snapshot: unknown): Promise<StorageSnapshotPreview> {
    return snapshotPreview(parseAndValidateSnapshot(snapshot));
  }

  async importSnapshot(snapshot: unknown): Promise<StorageStatus> {
    const parsedSnapshot = parseAndValidateSnapshot(snapshot);
    await this.database.open();

    await this.database.transaction(
      'rw',
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
        await this.database.appMeta.clear();
        await this.database.lexicons.clear();
        await this.database.words.clear();
        await this.database.studyItems.clear();
        await this.database.lexiconItems.clear();
        await this.database.notes.clear();
        await this.database.practiceAttempts.clear();
        await this.database.practiceSessions.clear();
        await this.database.reviewStates.clear();
        await this.database.reviewLogs.clear();

        const { tables } = parsedSnapshot;
        await this.database.appMeta.bulkPut(tables.appMeta);
        await this.database.lexicons.bulkPut(tables.lexicons);
        await this.database.words.bulkPut(tables.words);
        await this.database.studyItems.bulkPut(tables.studyItems);
        await this.database.lexiconItems.bulkPut(tables.lexiconItems);
        await this.database.notes.bulkPut(tables.notes);
        await this.database.practiceAttempts.bulkPut(tables.practiceAttempts);
        await this.database.practiceSessions.bulkPut(tables.practiceSessions);
        await this.database.reviewStates.bulkPut(tables.reviewStates);
        await this.database.reviewLogs.bulkPut(tables.reviewLogs);
      },
    );

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
