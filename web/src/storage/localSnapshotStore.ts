import type { ReviewLog } from '../core/domain';
import type {
  StorageSnapshot,
  StorageSnapshotPreview,
} from '../core/repository';
import { InvalidStorageSnapshotError } from '../core/repository';
import {
  APP_META_KEYS,
  DATA_FORMAT_VERSION,
  FingerMemoryDatabase,
  STORAGE_SCHEMA_VERSION,
} from './database';
import {
  cardsHaveSameSchedulingState,
  reviewCardFromState,
} from './reviewStateRecords';
import { parseStorageSnapshot } from './snapshotSchema';

function assertUniqueIds(values: string[], label: string): void {
  if (new Set(values).size !== values.length) {
    throw new InvalidStorageSnapshotError(`${label} contains duplicate ids`);
  }
}

function validateSnapshotReferences(snapshot: StorageSnapshot): void {
  const { tables } = snapshot;
  const dataFormatRecord = tables.appMeta.find(
    (record) => record.key === APP_META_KEYS.dataFormatVersion,
  );
  if (dataFormatRecord?.value !== String(DATA_FORMAT_VERSION)) {
    throw new InvalidStorageSnapshotError(
      'data format metadata is missing or incompatible',
    );
  }

  assertUniqueIds(
    tables.appMeta.map((record) => record.key),
    'appMeta',
  );
  assertUniqueIds(
    tables.lexicons.map((record) => record.id),
    'lexicons',
  );
  assertUniqueIds(
    tables.words.map((record) => record.id),
    'words',
  );
  assertUniqueIds(
    tables.studyItems.map((record) => record.id),
    'studyItems',
  );
  assertUniqueIds(
    tables.notes.map((record) => record.id),
    'notes',
  );
  assertUniqueIds(
    tables.practiceAttempts.map((record) => record.id),
    'practiceAttempts',
  );
  assertUniqueIds(
    tables.practiceSessions.map((record) => record.id),
    'practiceSessions',
  );
  assertUniqueIds(
    tables.reviewStates.map((record) => record.studyItemId),
    'reviewStates',
  );
  assertUniqueIds(
    tables.reviewLogs.map((record) => record.submissionId),
    'reviewLogs',
  );
  assertUniqueIds(
    tables.lexiconItems.map(
      (record) => `${record.lexiconId}\u0000${record.studyItemId}`,
    ),
    'lexiconItems',
  );

  const lexiconIds = new Set(tables.lexicons.map((record) => record.id));
  const wordIds = new Set(tables.words.map((record) => record.id));
  const studyItemsById = new Map(
    tables.studyItems.map((record) => [record.id, record]),
  );
  const studyItemIds = new Set(studyItemsById.keys());
  const practiceSessionIds = new Set(
    tables.practiceSessions.map((record) => record.id),
  );
  const logsByStudyItem = new Map<string, ReviewLog[]>();

  for (const item of tables.studyItems) {
    if (!wordIds.has(item.wordEntryId)) {
      throw new InvalidStorageSnapshotError(
        `study item ${item.id} references a missing word entry`,
      );
    }
  }
  for (const item of tables.lexiconItems) {
    if (
      !lexiconIds.has(item.lexiconId) ||
      !studyItemIds.has(item.studyItemId)
    ) {
      throw new InvalidStorageSnapshotError(
        'lexicon item references missing data',
      );
    }
  }

  const noteStudyItemIds = new Set<string>();
  for (const note of tables.notes) {
    if (!studyItemIds.has(note.studyItemId)) {
      throw new InvalidStorageSnapshotError(
        `note ${note.id} references a missing study item`,
      );
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
      (attempt.sessionId !== null &&
        !practiceSessionIds.has(attempt.sessionId))
    ) {
      throw new InvalidStorageSnapshotError(
        `practice attempt ${attempt.id} references a missing study item`,
      );
    }
  }

  for (const state of tables.reviewStates) {
    if (
      !studyItemIds.has(state.studyItemId) ||
      state.cardId !== state.studyItemId
    ) {
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
    if (
      session.queue.length === 0 ||
      session.currentIndex >= session.queue.length
    ) {
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
      if (
        studyItem === undefined ||
        studyItem.wordEntryId !== queueItem.wordEntryId
      ) {
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

    if (
      !cardsHaveSameSchedulingState(
        latestLog.afterCard,
        reviewCardFromState(state),
      )
    ) {
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
      error instanceof Error
        ? error.message
        : 'snapshot schema validation failed',
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

function validateSnapshotTimestamp(value: number): void {
  if (!Number.isFinite(value) || value <= 0) {
    throw new TypeError(
      'snapshot export time must be a positive finite timestamp',
    );
  }
}

export class LocalSnapshotStore {
  constructor(private readonly database: FingerMemoryDatabase) {}

  async exportSnapshot(exportedAt: number): Promise<StorageSnapshot> {
    validateSnapshotTimestamp(exportedAt);
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

  previewSnapshot(snapshot: unknown): StorageSnapshotPreview {
    return snapshotPreview(parseAndValidateSnapshot(snapshot));
  }

  async importSnapshot(snapshot: unknown): Promise<void> {
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
  }
}
