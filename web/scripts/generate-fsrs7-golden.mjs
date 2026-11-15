import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { DefaultScheduler, Rating } from 'ts-fsrs';

const options = {
  version: 'FSRS-7',
  desiredRetention: 0.9,
  enableFuzz: false,
  enableShortTerm: true,
  learningSteps: ['1m', '10m'],
  relearningSteps: ['10m'],
  graduatingInterval: 1,
  allowModelOverride: true,
};
const scheduler = await DefaultScheduler(options);
const base = Date.parse('2026-11-15T01:00:00.000Z');
const weights = Array.from(scheduler.config.weights);
if (scheduler.definition.model.name !== 'fsrs-7' || weights.length !== 34) {
  throw new Error(
    `Expected FSRS-7 with 34 weights, got ${scheduler.definition.model.name} with ${weights.length}`,
  );
}

function cardSnapshot(card) {
  return {
    cardId: card.cardId,
    stability: card.stability,
    stabilityFast: card.stabilityFast,
    difficulty: card.difficulty,
    dueAt: card.dueAt.getTime(),
    lastReviewAt: card.lastReviewAt?.getTime() ?? null,
    state: card.state,
    scheduleStatus: card.scheduleStatus,
    reps: card.reps,
    lapses: card.lapses,
    scheduledDays: card.scheduledDays,
    learningStep: card.learningStep,
  };
}

function revlogSnapshot(revlog) {
  return {
    cardId: revlog.cardId,
    rating: revlog.rating,
    stability: revlog.stability,
    stabilityFast: revlog.stabilityFast,
    difficulty: revlog.difficulty,
    dueAt: revlog.dueAt.getTime(),
    lastReviewAt: revlog.lastReviewAt?.getTime() ?? null,
    reviewTime: revlog.reviewTime.getTime(),
    state: revlog.state,
    scheduleStatus: revlog.scheduleStatus,
    scheduledDays: revlog.scheduledDays,
    learningStep: revlog.learningStep,
  };
}

const initial = scheduler.newCard({ cardId: 'fixture-card', now: new Date(base) });
const previews = ['Again', 'Hard', 'Good', 'Easy'].map((name) => {
  const result = scheduler.review({ card: initial, grade: Rating[name], now: new Date(base) });
  return {
    rating: name.toLowerCase(),
    afterCard: cardSnapshot(result.card),
    revlog: revlogSnapshot(result.revlog),
  };
});

const sequence = [];
let card = initial;
const steps = [
  { rating: 'Good', now: base + 5 * 60_000 },
  { rating: 'Good', now: base + 24 * 60 * 60_000 },
  { rating: 'Again', now: base + 4 * 24 * 60 * 60_000 },
  { rating: 'Hard', now: base + 4 * 24 * 60 * 60_000 + 15 * 60_000 },
  { rating: 'Easy', now: base + 10 * 24 * 60 * 60_000 },
];

for (const step of steps) {
  const before = cardSnapshot(card);
  const result = scheduler.review({
    card,
    grade: Rating[step.rating],
    now: new Date(step.now),
  });
  card = result.card;
  sequence.push({
    input: { rating: step.rating.toLowerCase(), now: step.now },
    beforeCard: before,
    afterCard: cardSnapshot(result.card),
    revlog: revlogSnapshot(result.revlog),
  });
}

const serialized = JSON.parse(JSON.stringify(initial));
const restored = {
  ...serialized,
  dueAt: new Date(serialized.dueAt),
  lastReviewAt: serialized.lastReviewAt === null ? null : new Date(serialized.lastReviewAt),
};
const restoredResult = scheduler.review({
  card: restored,
  grade: Rating.Good,
  now: new Date(base + 5 * 60_000),
});

const fixture = {
  fixtureVersion: 1,
  generatedAtUtc: '2026-11-15T00:00:00.000Z',
  source: {
    package: 'ts-fsrs',
    packageVersion: '6.0.0',
    modelVersion: scheduler.definition.model.name,
    options: {
      desiredRetention: options.desiredRetention,
      enableFuzz: options.enableFuzz,
      enableShortTerm: options.enableShortTerm,
      learningSteps: options.learningSteps,
      relearningSteps: options.relearningSteps,
      graduatingInterval: options.graduatingInterval,
      allowModelOverride: options.allowModelOverride,
    },
    weights,
  },
  initialCard: cardSnapshot(initial),
  sameCardRatingPreviews: previews,
  sequence,
  serializationRestore: {
    serializedCard: serialized,
    input: { rating: 'good', now: base + 5 * 60_000 },
    afterCard: cardSnapshot(restoredResult.card),
    revlog: revlogSnapshot(restoredResult.revlog),
  },
};

const output = resolve(process.cwd(), '../server/src/test/resources/fsrs/fsrs7-golden.json');
await mkdir(dirname(output), { recursive: true });
await writeFile(output, `${JSON.stringify(fixture, null, 2)}\n`, 'utf8');
console.log(output);
