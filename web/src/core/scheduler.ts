import type {
  ReviewCardSnapshot,
  ReviewRating,
  SchedulerConfigSnapshot,
  SchedulerRevlogSnapshot,
  TimestampMs,
} from './domain';

export interface SchedulerReviewResult {
  card: ReviewCardSnapshot;
  revlog: SchedulerRevlogSnapshot;
}

export interface ReviewScheduler {
  readonly packageVersion: string;
  readonly modelVersion: string;
  readonly config: SchedulerConfigSnapshot;
  createCard(cardId: string, now: TimestampMs): ReviewCardSnapshot;
  review(input: {
    card: ReviewCardSnapshot;
    rating: ReviewRating;
    now: TimestampMs;
  }): SchedulerReviewResult;
}
