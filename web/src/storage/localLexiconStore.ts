import type {
  Lexicon,
  LexiconItem,
  StudyItem,
  TimestampMs,
  WordEntry,
} from '../core/domain';
import type {
  CreateLexiconInput,
  CreateStudyItemInput,
  ImportStudyItemsInput,
  ImportStudyItemsResult,
  LexiconStudyItem,
  UpdateLexiconInput,
  UpdateStudyItemInput,
} from '../core/repository';
import {
  ImportBatchConflictError,
  LexiconNotFoundError,
  StudyItemInUseError,
  StudyItemNotFoundError,
} from '../core/repository';
import {
  normalizeStudyTerm,
  normalizeTextList,
  studyTextKey,
} from '../core/studyText';
import type { FingerMemoryDatabase } from './database';
import {
  assertRepositoryIdentifier,
  assertRepositoryTimestamp,
} from './learningRecordInputs';

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

export class LocalLexiconStore {
  constructor(
    private readonly database: FingerMemoryDatabase,
    private readonly idFactory: () => string,
  ) {}

  async listLexicons(includeArchived = true): Promise<Lexicon[]> {
    await this.database.open();
    const lexicons = await this.database.lexicons.toArray();
    return lexicons
      .filter(
        (lexicon) =>
          includeArchived || lexicon.archivedAt === null,
      )
      .sort(
        (left, right) =>
          Number(left.archivedAt !== null) -
            Number(right.archivedAt !== null) ||
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

    return this.database.transaction(
      'rw',
      this.database.lexicons,
      async () => {
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
      },
    );
  }

  async setLexiconArchived(
    id: string,
    archived: boolean,
    now: TimestampMs,
  ): Promise<Lexicon> {
    assertRepositoryIdentifier(id, 'lexicon id');
    assertRepositoryTimestamp(now, 'lexicon archive time');
    await this.database.open();

    return this.database.transaction(
      'rw',
      this.database.lexicons,
      async () => {
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
      },
    );
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
        await this.database.lexiconItems
          .where('lexiconId')
          .equals(id)
          .delete();
        await this.database.lexicons.delete(id);
      },
    );
  }

  async listLexiconItems(
    lexiconId: string,
  ): Promise<LexiconStudyItem[]> {
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
        if (
          (await this.database.lexicons.get(lexiconId)) === undefined
        ) {
          throw new LexiconNotFoundError(lexiconId);
        }
        const memberships = await this.database.lexiconItems
          .where('lexiconId')
          .equals(lexiconId)
          .toArray();
        memberships.sort((left, right) => left.order - right.order);

        const result: LexiconStudyItem[] = [];
        for (const membership of memberships) {
          const studyItem = await this.database.studyItems.get(
            membership.studyItemId,
          );
          if (studyItem === undefined) {
            throw new StudyItemNotFoundError(membership.studyItemId);
          }
          const wordEntry = await this.database.words.get(
            studyItem.wordEntryId,
          );
          if (wordEntry === undefined) {
            throw new StudyItemNotFoundError(studyItem.id);
          }
          result.push({
            membership,
            practiceItem: { studyItem, wordEntry },
          });
        }
        return result;
      },
    );
  }

  async createStudyItem(
    input: CreateStudyItemInput,
  ): Promise<LexiconStudyItem> {
    assertRepositoryIdentifier(input.lexiconId, 'lexicon id');
    const term = input.term.trim();
    const meaning = input.meaning.trim();
    if (term.length === 0 || meaning.length === 0) {
      throw new TypeError(
        'study item term and meaning must not be empty',
      );
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
        if (
          (await this.database.lexicons.get(input.lexiconId)) ===
          undefined
        ) {
          throw new LexiconNotFoundError(input.lexiconId);
        }
        const memberships = await this.database.lexiconItems
          .where('lexiconId')
          .equals(input.lexiconId)
          .toArray();
        const order =
          memberships.reduce(
            (maximum, item) => Math.max(maximum, item.order),
            -1,
          ) + 1;
        const { wordEntry, studyItem, membership } =
          buildStudyItemEntities({
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
        return {
          membership,
          practiceItem: { studyItem, wordEntry },
        };
      },
    );
  }

  async updateStudyItem(
    input: UpdateStudyItemInput,
  ): Promise<LexiconStudyItem> {
    assertRepositoryIdentifier(input.lexiconId, 'lexicon id');
    assertRepositoryIdentifier(input.studyItemId, 'study item id');
    const term = input.term.trim();
    const meaning = input.meaning.trim();
    if (term.length === 0 || meaning.length === 0) {
      throw new TypeError(
        'study item term and meaning must not be empty',
      );
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
        const studyItem = await this.database.studyItems.get(
          input.studyItemId,
        );
        if (studyItem === undefined) {
          throw new StudyItemNotFoundError(input.studyItemId);
        }
        const wordEntry = await this.database.words.get(
          studyItem.wordEntryId,
        );
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
          acceptedAnswers: normalizeTextList(
            input.acceptedAnswers,
          ),
          hints: normalizeTextList(input.hints),
          normalizedTerm: normalizeStudyTerm(term),
          updatedAt: input.now,
        };
        const updatedStudyItem = {
          ...studyItem,
          updatedAt: input.now,
        };
        const updatedMembership = {
          ...membership,
          sourceHints: updatedWord.hints,
        };
        await this.database.words.put(updatedWord);
        await this.database.studyItems.put(updatedStudyItem);
        await this.database.lexiconItems.put(updatedMembership);
        return {
          membership: updatedMembership,
          practiceItem: {
            studyItem: updatedStudyItem,
            wordEntry: updatedWord,
          },
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
      [
        this.database.lexicons,
        this.database.lexiconItems,
        this.database.practiceSessions,
      ],
      async () => {
        if (
          (await this.database.lexicons.get(lexiconId)) === undefined
        ) {
          throw new LexiconNotFoundError(lexiconId);
        }
        const sessions = await this.database.practiceSessions.toArray();
        const inUse = sessions.some(
          (session) =>
            session.status !== 'completed' &&
            session.queue.some(
              (item) => item.studyItemId === studyItemId,
            ),
        );
        if (inUse) {
          throw new StudyItemInUseError(studyItemId);
        }
        await this.database.lexiconItems.delete([
          lexiconId,
          studyItemId,
        ]);
      },
    );
  }

  async importStudyItems(
    input: ImportStudyItemsInput,
  ): Promise<ImportStudyItemsResult> {
    assertRepositoryIdentifier(input.batchId, 'import batch id');
    assertRepositoryIdentifier(input.lexiconId, 'lexicon id');
    assertRepositoryTimestamp(input.now, 'import time');
    if (input.rows.length === 0) {
      throw new TypeError('import rows must not be empty');
    }
    for (const row of input.rows) {
      if (
        row.term.trim().length === 0 ||
        row.meaning.trim().length === 0
      ) {
        throw new TypeError(
          'import row term and meaning must not be empty',
        );
      }
      if (
        row.duplicateAction !== 'skip' &&
        row.duplicateAction !== 'create'
      ) {
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
        const existingReceiptRecord =
          await this.database.appMeta.get(receiptKey);
        if (existingReceiptRecord !== undefined) {
          const existingReceipt = readImportReceipt(
            existingReceiptRecord.value,
          );
          if (existingReceipt?.fingerprint !== fingerprint) {
            throw new ImportBatchConflictError(input.batchId);
          }
          return {
            outcome: 'duplicate',
            importedCount: existingReceipt.importedCount,
            skippedCount: existingReceipt.skippedCount,
          };
        }

        if (
          (await this.database.lexicons.get(input.lexiconId)) ===
          undefined
        ) {
          throw new LexiconNotFoundError(input.lexiconId);
        }

        const memberships = await this.database.lexiconItems
          .where('lexiconId')
          .equals(input.lexiconId)
          .toArray();
        let nextOrder =
          memberships.reduce(
            (maximum, membership) =>
              Math.max(maximum, membership.order),
            -1,
          ) + 1;
        const existingKeys = new Set<string>();
        for (const membership of memberships) {
          const studyItem = await this.database.studyItems.get(
            membership.studyItemId,
          );
          const wordEntry =
            studyItem === undefined
              ? undefined
              : await this.database.words.get(studyItem.wordEntryId);
          if (studyItem === undefined || wordEntry === undefined) {
            throw new StudyItemNotFoundError(
              membership.studyItemId,
            );
          }
          existingKeys.add(
            studyTextKey(wordEntry.term, wordEntry.meaning),
          );
        }

        const importedKeys = new Set<string>();
        let importedCount = 0;
        let skippedCount = 0;
        for (const row of input.rows) {
          const key = studyTextKey(row.term, row.meaning);
          const alreadyExists =
            existingKeys.has(key) || importedKeys.has(key);
          if (
            row.duplicateAction === 'skip' &&
            alreadyExists
          ) {
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
          await this.database.lexiconItems.add(
            entities.membership,
          );
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
        return {
          outcome: 'created',
          importedCount,
          skippedCount,
        };
      },
    );
  }
}
