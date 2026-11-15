import { z } from 'zod';
import type {
  AppMetaRecord,
  Lexicon,
  LexiconItem,
  PracticeAttempt,
  PracticeSession,
  ReviewCardSnapshot,
  ReviewLog,
  ReviewState,
  SchedulerConfigSnapshot,
  SchedulerRevlogSnapshot,
  StudyItem,
  StudyNote,
  WordEntry,
} from '../core/domain';
import type { StorageSnapshot } from '../core/repository';
import {
  DATA_FORMAT_VERSION,
  STORAGE_SCHEMA_VERSION,
} from './database';

const idSchema = z.string().min(1);
const timestampSchema = z.number().int().positive();
const finiteNumberSchema = z.number().finite();
const stringListSchema = z.array(z.string());

const appMetaSchema = z
  .object({
    key: idSchema,
    value: z.string(),
    updatedAt: timestampSchema,
  })
  .strict() satisfies z.ZodType<AppMetaRecord>;

const lexiconSchema = z
  .object({
    id: idSchema,
    name: z.string(),
    description: z.string(),
    createdAt: timestampSchema,
    updatedAt: timestampSchema,
    archivedAt: timestampSchema.nullable(),
  })
  .strict() satisfies z.ZodType<Lexicon>;

const wordEntrySchema = z
  .object({
    id: idSchema,
    term: z.string().min(1),
    meaning: z.string().min(1),
    acceptedAnswers: stringListSchema,
    hints: stringListSchema,
    normalizedTerm: z.string().min(1),
    createdAt: timestampSchema,
    updatedAt: timestampSchema,
  })
  .strict() satisfies z.ZodType<WordEntry>;

const studyItemSchema = z
  .object({
    id: idSchema,
    wordEntryId: idSchema,
    direction: z.literal('meaning-to-spelling'),
    createdAt: timestampSchema,
    updatedAt: timestampSchema,
  })
  .strict() satisfies z.ZodType<StudyItem>;

const lexiconItemSchema = z
  .object({
    lexiconId: idSchema,
    studyItemId: idSchema,
    order: z.number().int().nonnegative(),
    sourceHints: stringListSchema,
  })
  .strict() satisfies z.ZodType<LexiconItem>;

const studyNoteSchema = z
  .object({
    id: idSchema,
    studyItemId: idSchema,
    content: z.string(),
    createdAt: timestampSchema,
    updatedAt: timestampSchema,
  })
  .strict() satisfies z.ZodType<StudyNote>;

const practiceAttemptSchema = z
  .object({
    id: idSchema,
    studyItemId: idSchema,
    modeId: z.string().min(1),
    sessionId: idSchema.nullable(),
    direction: z.literal('meaning-to-spelling'),
    promptText: z.string().min(1),
    response: z.string(),
    inputMatchedAnswer: z.boolean(),
    answerWasHidden: z.boolean(),
    hintUsed: z.boolean(),
    answerRevealed: z.boolean(),
    abandoned: z.boolean(),
    occurredAt: timestampSchema,
    requestFingerprint: z.string().min(1),
  })
  .strict() satisfies z.ZodType<PracticeAttempt>;

const schedulerConfigSchema = z
  .object({
    packageVersion: z.string().min(1),
    modelVersion: z.string().min(1),
    weights: z.array(finiteNumberSchema),
    fractionalDays: z.boolean(),
    desiredRetention: z.record(z.string(), finiteNumberSchema),
    enableFuzz: z.boolean(),
    maximumInterval: finiteNumberSchema,
    clearStatsOnForget: z.boolean(),
    enableShortTerm: z.boolean(),
    learningSteps: stringListSchema,
    relearningSteps: stringListSchema,
    graduatingInterval: finiteNumberSchema,
    allowModelOverride: z.boolean(),
  })
  .strict() satisfies z.ZodType<SchedulerConfigSnapshot>;

const reviewCardSchema = z
  .object({
    cardId: idSchema,
    stability: finiteNumberSchema,
    stabilityFast: finiteNumberSchema,
    difficulty: finiteNumberSchema,
    dueAt: timestampSchema,
    lastReviewAt: timestampSchema.nullable(),
    state: z.enum(['new', 'learning', 'review', 'relearning']),
    scheduleStatus: z.enum(['new', 'learning', 'review']),
    reps: z.number().int().nonnegative(),
    lapses: z.number().int().nonnegative(),
    scheduledDays: finiteNumberSchema,
    learningStep: z.number().int().nonnegative(),
  })
  .strict() satisfies z.ZodType<ReviewCardSnapshot>;

const reviewStateSchema = reviewCardSchema
  .extend({
    studyItemId: idSchema,
    schedulerConfig: schedulerConfigSchema,
    schedulerPackageVersion: z.string().min(1),
    schedulerModelVersion: z.string().min(1),
    updatedAt: timestampSchema,
  })
  .strict() satisfies z.ZodType<ReviewState>;

const schedulerRevlogSchema = z
  .object({
    cardId: idSchema,
    rating: z.enum(['again', 'hard', 'good', 'easy']),
    stability: finiteNumberSchema,
    stabilityFast: finiteNumberSchema,
    difficulty: finiteNumberSchema,
    dueAt: timestampSchema,
    lastReviewAt: timestampSchema.nullable(),
    reviewTime: timestampSchema,
    state: z.enum(['new', 'learning', 'review', 'relearning']),
    scheduleStatus: z.enum(['new', 'learning', 'review']),
    scheduledDays: finiteNumberSchema,
    learningStep: z.number().int().nonnegative(),
  })
  .strict() satisfies z.ZodType<SchedulerRevlogSnapshot>;

const reviewLogSchema = z
  .object({
    submissionId: idSchema,
    studyItemId: idSchema,
    wordEntryId: idSchema,
    modeId: z.string().min(1),
    sessionId: idSchema.nullable(),
    direction: z.literal('meaning-to-spelling'),
    rating: z.enum(['again', 'hard', 'good', 'easy']),
    reviewedAt: timestampSchema,
    response: z.string(),
    inputMatchedAnswer: z.boolean(),
    answerWasHidden: z.boolean(),
    hintUsed: z.boolean(),
    answerRevealed: z.boolean(),
    abandoned: z.boolean(),
    beforeCard: reviewCardSchema.nullable(),
    afterCard: reviewCardSchema,
    srsRevlog: schedulerRevlogSchema,
    schedulerConfig: schedulerConfigSchema,
    schedulerPackageVersion: z.string().min(1),
    schedulerModelVersion: z.string().min(1),
    requestFingerprint: z.string().min(1),
  })
  .strict() satisfies z.ZodType<ReviewLog>;

const practiceSessionQueueItemSchema = z
  .object({
    studyItemId: idSchema,
    wordEntryId: idSchema,
    status: z.enum(['pending', 'completed', 'skipped']),
    source: z.enum(['new', 'review', 'retry']),
  })
  .strict();

const practiceInteractionSchema = z
  .object({
    draft: z.string(),
    revealedHintCount: z.number().int().nonnegative(),
    answerRevealed: z.boolean(),
  })
  .strict();

const pendingAnswerSchema = z
  .object({
    studyItemId: idSchema,
    response: z.string(),
    answerWasHidden: z.boolean(),
    hintUsed: z.boolean(),
    answerRevealed: z.boolean(),
    inputMatchedAnswer: z.boolean(),
    practiceAttemptId: idSchema,
    reviewSubmissionId: idSchema,
    submittedAt: timestampSchema,
  })
  .strict();

const practiceSessionSchema = z
  .object({
    id: idSchema,
    modeId: z.enum(['keyboard-recall', 'lyrics-scroll', 'danmaku-stream']),
    status: z.enum(['active', 'paused', 'completed']),
    queue: z.array(practiceSessionQueueItemSchema).min(1),
    currentIndex: z.number().int().nonnegative(),
    currentStudyItemId: idSchema.nullable(),
    interactions: z.record(idSchema, practiceInteractionSchema),
    pendingAnswer: pendingAnswerSchema.nullable(),
    speed: finiteNumberSchema.min(0.25).max(4),
    reduceMotion: z.boolean(),
    createdAt: timestampSchema,
    updatedAt: timestampSchema,
    pausedAt: timestampSchema.nullable(),
    completedAt: timestampSchema.nullable(),
    endedReason: z.enum(['completed', 'abandoned']).optional(),
  })
  .strict() satisfies z.ZodType<PracticeSession>;

export const storageSnapshotSchema = z
  .object({
    snapshotVersion: z.literal(1),
    schemaVersion: z.literal(STORAGE_SCHEMA_VERSION),
    dataFormatVersion: z.literal(DATA_FORMAT_VERSION),
    exportedAt: timestampSchema,
    tables: z
      .object({
        appMeta: z.array(appMetaSchema),
        lexicons: z.array(lexiconSchema),
        words: z.array(wordEntrySchema),
        studyItems: z.array(studyItemSchema),
        lexiconItems: z.array(lexiconItemSchema),
        notes: z.array(studyNoteSchema),
        practiceAttempts: z.array(practiceAttemptSchema),
        practiceSessions: z.array(practiceSessionSchema),
        reviewStates: z.array(reviewStateSchema),
        reviewLogs: z.array(reviewLogSchema),
      })
      .strict(),
  })
  .strict() satisfies z.ZodType<StorageSnapshot>;

export function parseStorageSnapshot(input: unknown): StorageSnapshot {
  return storageSnapshotSchema.parse(input);
}
