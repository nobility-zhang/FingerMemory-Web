import type {
  ReviewCardSnapshot,
  ReviewLog,
  ReviewState,
} from '../core/domain';
import type { ReviewScheduler } from '../core/scheduler';

function arraysEqual<T>(left: readonly T[], right: readonly T[]): boolean {
  return (
    left.length === right.length &&
    left.every((value, index) => value === right[index])
  );
}

function recordsEqual(
  left: Record<string, number>,
  right: Record<string, number>,
): boolean {
  const leftEntries = Object.entries(left).sort(([a], [b]) =>
    a.localeCompare(b),
  );
  const rightEntries = Object.entries(right).sort(([a], [b]) =>
    a.localeCompare(b),
  );
  return (
    leftEntries.length === rightEntries.length &&
    leftEntries.every(
      ([key, value], index) =>
        rightEntries[index]?.[0] === key &&
        rightEntries[index]?.[1] === value,
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

export function reviewStateFromLog(log: ReviewLog): ReviewState {
  return {
    studyItemId: log.studyItemId,
    ...log.afterCard,
    schedulerConfig: log.schedulerConfig,
    schedulerPackageVersion: log.schedulerPackageVersion,
    schedulerModelVersion: log.schedulerModelVersion,
    updatedAt: log.reviewedAt,
  };
}

export function reviewCardFromState(state: ReviewState): ReviewCardSnapshot {
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

export function hasSameSchedulerConfig(
  state: ReviewState,
  scheduler: ReviewScheduler,
): boolean {
  return (
    state.schedulerPackageVersion === scheduler.packageVersion &&
    state.schedulerModelVersion === scheduler.modelVersion &&
    schedulerConfigsEqual(state.schedulerConfig, scheduler.config)
  );
}

export function cardsHaveSameSchedulingState(
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
