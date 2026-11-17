import type {
  ReviewLog,
  ReviewState,
  TimestampMs,
} from '../core/domain';
import type {
  DueReviewItem,
  ReviewHistoryEntry,
  ReviewRatingPreviews,
  ReviewSubmissionInput,
  ReviewSubmissionResult,
} from '../core/repository';
import {
  ReviewStateVersionMismatchError,
  ReviewSubmissionConflictError,
  ReviewSubmissionRejectedError,
} from '../core/repository';
import type { ReviewScheduler } from '../core/scheduler';
import type { FingerMemoryDatabase } from './database';
import {
  assertRepositoryIdentifier,
  assertRepositoryTimestamp,
  inputMatchesWord,
  reviewSubmissionFingerprint,
} from './learningRecordInputs';
import {
  cardsHaveSameSchedulingState,
  hasSameSchedulerConfig,
  reviewCardFromState,
  reviewStateFromLog,
} from './reviewStateRecords';

export class LocalReviewStore {
  constructor(
    private readonly database: FingerMemoryDatabase,
    private readonly scheduler: Promise<ReviewScheduler>,
  ) {}

  async submitReview(
    input: ReviewSubmissionInput,
  ): Promise<ReviewSubmissionResult> {
    assertRepositoryIdentifier(input.submissionId, 'review submission id');
    assertRepositoryIdentifier(input.studyItemId, 'study item id');
    assertRepositoryIdentifier(input.modeId, 'mode id');
    if (input.sessionId !== null) {
      assertRepositoryIdentifier(input.sessionId, 'practice session id');
    }
    assertRepositoryTimestamp(input.reviewedAt, 'review time');

    const scheduler = await this.scheduler;
    const requestFingerprint = reviewSubmissionFingerprint(input);

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
        const existingLog = await this.database.reviewLogs.get(
          input.submissionId,
        );
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
            state: reviewStateFromLog(existingLog),
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
          const session = await this.database.practiceSessions.get(
            input.sessionId,
          );
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
            throw new ReviewSubmissionRejectedError(
              'session-pending-mismatch',
            );
          }
          const sessionLogs = await this.database.reviewLogs.toArray();
          const alreadyReviewed = sessionLogs.some(
            (log) =>
              log.sessionId === input.sessionId &&
              log.studyItemId === input.studyItemId,
          );
          if (alreadyReviewed) {
            throw new ReviewSubmissionRejectedError(
              'session-review-limit',
            );
          }
        }

        const studyItem = await this.database.studyItems.get(
          input.studyItemId,
        );
        if (studyItem === undefined) {
          throw new ReviewSubmissionRejectedError('study-item-not-found');
        }
        if (studyItem.direction !== 'meaning-to-spelling') {
          throw new ReviewSubmissionRejectedError('unsupported-direction');
        }

        const wordEntry = await this.database.words.get(
          studyItem.wordEntryId,
        );
        if (wordEntry === undefined) {
          throw new Error(
            `Study item ${studyItem.id} references a missing word entry`,
          );
        }

        const existingState = await this.database.reviewStates.get(
          studyItem.id,
        );
        if (
          existingState !== undefined &&
          !hasSameSchedulerConfig(existingState, scheduler)
        ) {
          throw new ReviewStateVersionMismatchError();
        }
        if (
          existingState?.lastReviewAt !== null &&
          existingState?.lastReviewAt !== undefined &&
          input.reviewedAt < existingState.lastReviewAt
        ) {
          throw new ReviewSubmissionRejectedError(
            'review-time-out-of-order',
          );
        }

        const beforeCard =
          existingState === undefined
            ? null
            : reviewCardFromState(existingState);
        const card =
          beforeCard ??
          scheduler.createCard(studyItem.id, input.reviewedAt);
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
          inputMatchedAnswer: inputMatchesWord(
            wordEntry,
            input.response,
          ),
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
    assertRepositoryIdentifier(input.studyItemId, 'study item id');
    assertRepositoryTimestamp(input.reviewedAt, 'review preview time');
    const scheduler = await this.scheduler;

    return this.database.transaction(
      'r',
      [this.database.studyItems, this.database.reviewStates],
      async () => {
        const studyItem = await this.database.studyItems.get(
          input.studyItemId,
        );
        if (studyItem === undefined) {
          throw new ReviewSubmissionRejectedError('study-item-not-found');
        }
        if (studyItem.direction !== 'meaning-to-spelling') {
          throw new ReviewSubmissionRejectedError('unsupported-direction');
        }
        const existingState = await this.database.reviewStates.get(
          input.studyItemId,
        );
        if (
          existingState !== undefined &&
          !hasSameSchedulerConfig(existingState, scheduler)
        ) {
          throw new ReviewStateVersionMismatchError();
        }
        if (
          existingState?.lastReviewAt !== null &&
          existingState?.lastReviewAt !== undefined &&
          input.reviewedAt < existingState.lastReviewAt
        ) {
          throw new ReviewSubmissionRejectedError(
            'review-time-out-of-order',
          );
        }

        const card =
          existingState === undefined
            ? scheduler.createCard(input.studyItemId, input.reviewedAt)
            : reviewCardFromState(existingState);
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

  async getReviewState(
    studyItemId: string,
  ): Promise<ReviewState | undefined> {
    await this.database.open();
    return this.database.reviewStates.get(studyItemId);
  }

  async getReviewLog(submissionId: string): Promise<ReviewLog | undefined> {
    await this.database.open();
    return this.database.reviewLogs.get(submissionId);
  }

  async listReviewStates(studyItemIds?: string[]): Promise<ReviewState[]> {
    await this.database.open();
    const allowedIds =
      studyItemIds === undefined ? undefined : new Set(studyItemIds);
    const states = await this.database.reviewStates.toArray();
    return states
      .filter(
        (state) =>
          allowedIds === undefined || allowedIds.has(state.studyItemId),
      )
      .sort(
        (left, right) =>
          left.dueAt - right.dueAt ||
          left.studyItemId.localeCompare(right.studyItemId),
      );
  }

  async listReviewHistory(): Promise<ReviewHistoryEntry[]> {
    await this.database.open();

    return this.database.transaction(
      'r',
      [
        this.database.reviewStates,
        this.database.reviewLogs,
        this.database.studyItems,
        this.database.words,
      ],
      async () => {
        const states = await this.database.reviewStates.toArray();
        const logs = await this.database.reviewLogs.toArray();
        const logsByStudyItemId = new Map<string, ReviewLog[]>();
        for (const log of logs) {
          const itemLogs =
            logsByStudyItemId.get(log.studyItemId) ?? [];
          itemLogs.push(log);
          logsByStudyItemId.set(log.studyItemId, itemLogs);
        }

        const history: ReviewHistoryEntry[] = [];
        for (const state of states) {
          const studyItem = await this.database.studyItems.get(
            state.studyItemId,
          );
          if (studyItem === undefined) {
            throw new Error(
              `Review state ${state.studyItemId} has no study item`,
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
          const itemLogs = (
            logsByStudyItemId.get(state.studyItemId) ?? []
          ).sort(
            (left, right) =>
              left.reviewedAt - right.reviewedAt ||
              left.submissionId.localeCompare(right.submissionId),
          );
          let issue: string | null = null;
          if (itemLogs.length === 0) {
            issue = '当前计划没有对应的正式复习日志。';
          }
          for (
            let index = 1;
            index < itemLogs.length && issue === null;
            index += 1
          ) {
            const previous = itemLogs[index - 1];
            const current = itemLogs[index];
            if (
              previous === undefined ||
              current === undefined ||
              current.beforeCard === null ||
              !cardsHaveSameSchedulingState(
                previous.afterCard,
                current.beforeCard,
              )
            ) {
              issue = '相邻正式复习日志的前后卡片状态不连续。';
            }
          }
          const latestLog = itemLogs.at(-1);
          if (
            issue === null &&
            (latestLog === undefined ||
              !cardsHaveSameSchedulingState(
                latestLog.afterCard,
                reviewCardFromState(state),
              ))
          ) {
            issue = '当前计划与最新正式复习日志不一致。';
          }
          history.push({
            studyItem,
            wordEntry,
            state,
            logs: itemLogs,
            consistent: issue === null,
            issue,
          });
        }

        return history.sort(
          (left, right) =>
            left.state.dueAt - right.state.dueAt ||
            left.studyItem.id.localeCompare(right.studyItem.id),
        );
      },
    );
  }

  async listDueReviewItems(at: TimestampMs): Promise<DueReviewItem[]> {
    assertRepositoryTimestamp(at, 'due query time');
    await this.database.open();

    return this.database.transaction(
      'r',
      [
        this.database.reviewStates,
        this.database.studyItems,
        this.database.words,
      ],
      async () => {
        const states = await this.database.reviewStates
          .where('dueAt')
          .belowOrEqual(at)
          .toArray();
        states.sort(
          (left, right) =>
            left.dueAt - right.dueAt ||
            left.studyItemId.localeCompare(right.studyItemId),
        );

        const dueItems: DueReviewItem[] = [];
        for (const reviewState of states) {
          const studyItem = await this.database.studyItems.get(
            reviewState.studyItemId,
          );
          if (studyItem === undefined) {
            throw new Error(
              `Review state ${reviewState.studyItemId} has no study item`,
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
          dueItems.push({ reviewState, studyItem, wordEntry });
        }

        return dueItems;
      },
    );
  }
}
