import type {
  PracticeItem,
  TimestampMs,
} from '../core/domain';
import type {
  PracticeQueuePlan,
} from '../core/repository';
import { getLocalDayRange } from '../core/due';
import {
  APP_META_KEYS,
  type FingerMemoryDatabase,
} from './database';
import {
  parsePracticeQueueSettings,
} from './practicePreferences';
import {
  assertRepositoryTimestamp,
} from './learningRecordInputs';

export class LocalPracticeQueueStore {
  constructor(private readonly database: FingerMemoryDatabase) {}

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
            .filter((membership) =>
              activeLexiconIds.has(membership.lexiconId),
            )
            .map((membership) => membership.studyItemId),
        );
        const practiceItems: PracticeItem[] = [];
        for (const studyItemId of studyItemIds) {
          const studyItem = await this.database.studyItems.get(
            studyItemId,
          );
          if (studyItem === undefined) {
            throw new Error(
              `Study item ${studyItemId} has no persisted record`,
            );
          }
          const wordEntry = await this.database.words.get(
            studyItem.wordEntryId,
          );
          if (wordEntry === undefined) {
            throw new Error(
              `Study item ${studyItem.id} has no word entry`,
            );
          }
          practiceItems.push({ studyItem, wordEntry });
        }
        practiceItems.sort(
          (left, right) =>
            left.wordEntry.term.localeCompare(
              right.wordEntry.term,
            ) ||
            left.studyItem.id.localeCompare(right.studyItem.id),
        );

        const states = (
          await this.database.reviewStates.toArray()
        ).filter((state) => studyItemIds.has(state.studyItemId));
        const stateByStudyItemId = new Map(
          states.map((state) => [state.studyItemId, state]),
        );
        const dueItems = practiceItems
          .filter((item) => {
            const state = stateByStudyItemId.get(
              item.studyItem.id,
            );
            return state !== undefined && state.dueAt < day.endAt;
          })
          .sort((left, right) => {
            const leftDue =
              stateByStudyItemId.get(left.studyItem.id)?.dueAt ?? 0;
            const rightDue =
              stateByStudyItemId.get(right.studyItem.id)?.dueAt ?? 0;
            return (
              leftDue - rightDue ||
              left.studyItem.id.localeCompare(right.studyItem.id)
            );
          });
        const newItems = practiceItems.filter(
          (item) => !stateByStudyItemId.has(item.studyItem.id),
        );

        const selected = settings.paused
          ? []
          : [
              ...dueItems
                .slice(0, settings.reviewLimit)
                .map((item) => ({
                  item,
                  source: 'review' as const,
                })),
              ...newItems
                .slice(0, settings.newLimit)
                .map((item) => ({
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
      throw new TypeError(
        'practice item limit must be a positive integer',
      );
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
        const memberships =
          await this.database.lexiconItems.toArray();
        const studyItemIds = new Set(
          memberships
            .filter((membership) =>
              activeLexiconIds.has(membership.lexiconId),
            )
            .map((membership) => membership.studyItemId),
        );
        const practiceItems: PracticeItem[] = [];
        for (const studyItemId of studyItemIds) {
          const studyItem = await this.database.studyItems.get(
            studyItemId,
          );
          if (studyItem === undefined) {
            throw new Error(
              `Study item ${studyItemId} has no persisted record`,
            );
          }
          const wordEntry = await this.database.words.get(
            studyItem.wordEntryId,
          );
          if (wordEntry === undefined) {
            throw new Error(
              `Study item ${studyItem.id} has no word entry`,
            );
          }
          practiceItems.push({ studyItem, wordEntry });
        }
        practiceItems.sort(
          (left, right) =>
            left.wordEntry.term.localeCompare(
              right.wordEntry.term,
            ) ||
            left.studyItem.id.localeCompare(right.studyItem.id),
        );
        return practiceItems.slice(0, limit);
      },
    );
  }

  async listPracticeItemsByStudyItemIds(
    studyItemIds: string[],
  ): Promise<PracticeItem[]> {
    await this.database.open();
    const uniqueIds = [...new Set(studyItemIds)];
    if (uniqueIds.some((id) => id.trim().length === 0)) {
      throw new TypeError(
        'study item ids must not contain empty values',
      );
    }

    return this.database.transaction(
      'r',
      [this.database.studyItems, this.database.words],
      async () => {
        const practiceItems: PracticeItem[] = [];
        for (const studyItemId of uniqueIds) {
          const studyItem = await this.database.studyItems.get(
            studyItemId,
          );
          if (studyItem === undefined) {
            continue;
          }
          const wordEntry = await this.database.words.get(
            studyItem.wordEntryId,
          );
          if (wordEntry === undefined) {
            continue;
          }
          practiceItems.push({ studyItem, wordEntry });
        }
        return practiceItems;
      },
    );
  }
}
