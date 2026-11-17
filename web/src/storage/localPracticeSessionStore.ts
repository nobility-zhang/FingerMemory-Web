import type {
  PracticeAttempt,
  PracticeModeId,
  PracticeSession,
} from '../core/domain';
import { assessmentMetadata } from '../core/assessmentPolicy';
import type {
  PracticeAttemptInput,
  PracticeAttemptResult,
} from '../core/repository';
import {
  PracticeAttemptConflictError,
  ReviewSubmissionRejectedError,
} from '../core/repository';
import {
  assertRepositoryIdentifier,
  assertRepositoryTimestamp,
  inputMatchesWord,
  practiceAttemptFingerprint,
} from './learningRecordInputs';
import type { FingerMemoryDatabase } from './database';

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

export class LocalPracticeSessionStore {
  constructor(private readonly database: FingerMemoryDatabase) {}

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

  async savePracticeSession(
    session: PracticeSession,
  ): Promise<PracticeSession> {
    const plainSession = plainPracticeSession(session);
    assertRepositoryIdentifier(plainSession.id, 'practice session id');
    assertRepositoryTimestamp(
      plainSession.createdAt,
      'practice session creation time',
    );
    assertRepositoryTimestamp(
      plainSession.updatedAt,
      'practice session update time',
    );
    if (plainSession.updatedAt < plainSession.createdAt) {
      throw new TypeError(
        'practice session update time cannot precede creation time',
      );
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
      [
        this.database.practiceSessions,
        this.database.studyItems,
        this.database.words,
      ],
      async () => {
        const studyItemIds = new Set<string>();
        for (const queueItem of plainSession.queue) {
          const studyItem = await this.database.studyItems.get(
            queueItem.studyItemId,
          );
          if (
            studyItem === undefined ||
            studyItem.wordEntryId !== queueItem.wordEntryId
          ) {
            throw new Error(
              'Practice session queue references invalid study data',
            );
          }
          studyItemIds.add(queueItem.studyItemId);
        }

        const currentQueueItem =
          plainSession.queue[plainSession.currentIndex];
        if (
          plainSession.status !== 'completed' &&
          (currentQueueItem === undefined ||
            currentQueueItem.studyItemId !==
              plainSession.currentStudyItemId)
        ) {
          throw new Error(
            'Practice session current item does not match its queue',
          );
        }
        for (const studyItemId of Object.keys(plainSession.interactions)) {
          if (!studyItemIds.has(studyItemId)) {
            throw new Error(
              'Practice session interaction references an invalid study item',
            );
          }
        }
        if (
          plainSession.pendingAnswer !== null &&
          !studyItemIds.has(plainSession.pendingAnswer.studyItemId)
        ) {
          throw new Error(
            'Practice session pending answer references invalid study data',
          );
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
    assertRepositoryIdentifier(input.id, 'practice attempt id');
    assertRepositoryIdentifier(input.studyItemId, 'study item id');
    assertRepositoryIdentifier(input.modeId, 'mode id');
    if (input.sessionId !== null) {
      assertRepositoryIdentifier(input.sessionId, 'practice session id');
    }
    assertRepositoryTimestamp(input.occurredAt, 'practice attempt time');
    const requestFingerprint = practiceAttemptFingerprint(input);
    const metadata = assessmentMetadata(input);

    return this.database.transaction(
      'rw',
      [
        this.database.studyItems,
        this.database.words,
        this.database.practiceAttempts,
      ],
      async () => {
        const existing = await this.database.practiceAttempts.get(input.id);
        if (existing !== undefined) {
          if (existing.requestFingerprint !== requestFingerprint) {
            throw new PracticeAttemptConflictError(input.id);
          }
          return { outcome: 'duplicate', attempt: existing };
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
          ...metadata,
          abandoned: input.abandoned,
          occurredAt: input.occurredAt,
          requestFingerprint,
        };

        await this.database.practiceAttempts.add(attempt);
        return { outcome: 'created', attempt };
      },
    );
  }
}
