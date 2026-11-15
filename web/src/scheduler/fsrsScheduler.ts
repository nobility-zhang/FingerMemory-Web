import {
  DefaultScheduler,
  Rating,
  State,
  type DefaultSchedulerCard,
  type DefaultSchedulerCardInput,
  type DefaultSchedulerRevlog,
  type Grade,
} from 'ts-fsrs';
import type {
  ReviewCardSnapshot,
  ReviewLifecycleState,
  ReviewRating,
  ReviewScheduleStatus,
  SchedulerConfigSnapshot,
  SchedulerRevlogSnapshot,
  TimestampMs,
} from '../core/domain';
import type { ReviewScheduler, SchedulerReviewResult } from '../core/scheduler';

export const TS_FSRS_PACKAGE_VERSION = '6.0.0';

type FsrsScheduler = Awaited<ReturnType<typeof DefaultScheduler<'FSRS-7'>>>;

function assertTimestamp(timestamp: TimestampMs, label: string): Date {
  if (!Number.isFinite(timestamp) || timestamp <= 0) {
    throw new TypeError(`${label} must be a positive finite timestamp`);
  }
  return new Date(timestamp);
}

function toLifecycleState(state: State): ReviewLifecycleState {
  switch (state) {
    case State.New:
      return 'new';
    case State.Learning:
      return 'learning';
    case State.Review:
      return 'review';
    case State.Relearning:
      return 'relearning';
    default:
      throw new Error(`Unsupported FSRS state: ${state}`);
  }
}

function toLibraryState(state: ReviewLifecycleState): State {
  switch (state) {
    case 'new':
      return State.New;
    case 'learning':
      return State.Learning;
    case 'review':
      return State.Review;
    case 'relearning':
      return State.Relearning;
  }
}

function toLibraryRating(rating: ReviewRating): Grade {
  switch (rating) {
    case 'again':
      return Rating.Again;
    case 'hard':
      return Rating.Hard;
    case 'good':
      return Rating.Good;
    case 'easy':
      return Rating.Easy;
  }
}

function assertReviewRating(rating: ReviewRating): void {
  if (!['again', 'hard', 'good', 'easy'].includes(rating)) {
    throw new TypeError(`Unsupported review rating: ${String(rating)}`);
  }
}

function toReviewRating(rating: Rating): ReviewRating {
  switch (rating) {
    case Rating.Again:
      return 'again';
    case Rating.Hard:
      return 'hard';
    case Rating.Good:
      return 'good';
    case Rating.Easy:
      return 'easy';
    default:
      throw new Error(`Unsupported FSRS rating in revlog: ${rating}`);
  }
}

function toCardSnapshot(card: DefaultSchedulerCard): ReviewCardSnapshot {
  if (typeof card.cardId !== 'string') {
    throw new TypeError('FSRS cardId must be a string');
  }

  return {
    cardId: card.cardId,
    stability: card.stability,
    stabilityFast: card.stabilityFast,
    difficulty: card.difficulty,
    dueAt: card.dueAt.getTime(),
    lastReviewAt: card.lastReviewAt?.getTime() ?? null,
    state: toLifecycleState(card.state),
    scheduleStatus: card.scheduleStatus as ReviewScheduleStatus,
    reps: card.reps,
    lapses: card.lapses,
    scheduledDays: card.scheduledDays,
    learningStep: card.learningStep,
  };
}

function toLibraryCard(card: ReviewCardSnapshot): DefaultSchedulerCardInput {
  return {
    cardId: card.cardId,
    stability: card.stability,
    stabilityFast: card.stabilityFast,
    difficulty: card.difficulty,
    dueAt: new Date(card.dueAt),
    lastReviewAt: card.lastReviewAt === null ? null : new Date(card.lastReviewAt),
    state: toLibraryState(card.state),
    scheduleStatus: card.scheduleStatus,
    reps: card.reps,
    lapses: card.lapses,
    scheduledDays: card.scheduledDays,
    learningStep: card.learningStep,
  };
}

function toRevlogSnapshot(revlog: DefaultSchedulerRevlog): SchedulerRevlogSnapshot {
  if (typeof revlog.cardId !== 'string') {
    throw new TypeError('FSRS revlog cardId must be a string');
  }

  return {
    cardId: revlog.cardId,
    rating: toReviewRating(revlog.rating),
    stability: revlog.stability,
    stabilityFast: revlog.stabilityFast,
    difficulty: revlog.difficulty,
    dueAt: revlog.dueAt.getTime(),
    lastReviewAt: revlog.lastReviewAt?.getTime() ?? null,
    reviewTime: revlog.reviewTime.getTime(),
    state: toLifecycleState(revlog.state),
    scheduleStatus: revlog.scheduleStatus as ReviewScheduleStatus,
    scheduledDays: revlog.scheduledDays,
    learningStep: revlog.learningStep,
  };
}

function toConfigSnapshot(scheduler: FsrsScheduler): SchedulerConfigSnapshot {
  return {
    packageVersion: TS_FSRS_PACKAGE_VERSION,
    modelVersion: scheduler.definition.model.name,
    weights: Array.from(scheduler.config.weights),
    fractionalDays: scheduler.config.fractionalDays,
    desiredRetention: Object.fromEntries(
      Object.entries(scheduler.config.desiredRetention).map(([rating, value]) => [
        rating,
        Number(value),
      ]),
    ),
    enableFuzz: scheduler.config.enableFuzz,
    maximumInterval: scheduler.config.maximumInterval,
    clearStatsOnForget: scheduler.config.clearStatsOnForget,
    enableShortTerm: scheduler.config.enableShortTerm,
    learningSteps: Array.from(scheduler.config.learningSteps),
    relearningSteps: Array.from(scheduler.config.relearningSteps),
    graduatingInterval: scheduler.config.graduatingInterval,
    allowModelOverride: scheduler.config.allowModelOverride,
  };
}

class TsFsrsScheduler implements ReviewScheduler {
  constructor(private readonly scheduler: FsrsScheduler) {}

  readonly packageVersion = TS_FSRS_PACKAGE_VERSION;

  get modelVersion(): string {
    return this.scheduler.definition.model.name;
  }

  get config(): SchedulerConfigSnapshot {
    return toConfigSnapshot(this.scheduler);
  }

  createCard(cardId: string, now: TimestampMs): ReviewCardSnapshot {
    return toCardSnapshot(this.scheduler.newCard({ cardId, now: assertTimestamp(now, 'now') }));
  }

  review(input: {
    card: ReviewCardSnapshot;
    rating: ReviewRating;
    now: TimestampMs;
  }): SchedulerReviewResult {
    assertReviewRating(input.rating);
    const result = this.scheduler.review({
      card: toLibraryCard(input.card),
      grade: toLibraryRating(input.rating),
      now: assertTimestamp(input.now, 'now'),
    });

    return {
      card: toCardSnapshot(result.card),
      revlog: toRevlogSnapshot(result.revlog),
    };
  }
}

async function createScheduler(): Promise<ReviewScheduler> {
  const scheduler = await DefaultScheduler({
    version: 'FSRS-7',
    desiredRetention: 0.9,
    enableFuzz: false,
    enableShortTerm: true,
    learningSteps: ['1m', '10m'],
    relearningSteps: ['10m'],
    graduatingInterval: 1,
    allowModelOverride: true,
  });

  return new TsFsrsScheduler(scheduler);
}

let defaultSchedulerPromise: Promise<ReviewScheduler> | undefined;

export function createDefaultReviewScheduler(): Promise<ReviewScheduler> {
  if (defaultSchedulerPromise === undefined) {
    defaultSchedulerPromise = createScheduler().catch((error: unknown) => {
      defaultSchedulerPromise = undefined;
      throw error;
    });
  }

  return defaultSchedulerPromise;
}
