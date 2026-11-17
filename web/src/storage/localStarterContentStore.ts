import type {
  PracticeItem,
  TimestampMs,
} from '../core/domain';
import { normalizeStudyTerm } from '../core/studyText';
import type { FingerMemoryDatabase } from './database';
import { assertRepositoryTimestamp } from './learningRecordInputs';
import type { LocalPracticeQueueStore } from './localPracticeQueueStore';
import {
  STARTER_LEXICON_ID,
  STARTER_WORDS,
} from './starterContent';

export class LocalStarterContentStore {
  constructor(
    private readonly database: FingerMemoryDatabase,
    private readonly clock: () => TimestampMs,
    private readonly practiceQueueStore: LocalPracticeQueueStore,
  ) {}

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
        if (
          (await this.database.lexicons.get(STARTER_LEXICON_ID)) ===
          undefined
        ) {
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
          if (
            (await this.database.studyItems.get(studyItemId)) ===
            undefined
          ) {
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
}
