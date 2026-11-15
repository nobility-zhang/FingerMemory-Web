import { createHash } from 'node:crypto';
import { createServer, type IncomingMessage, type ServerResponse } from 'node:http';
import { z } from 'zod';
import {
  createDefaultReviewScheduler,
} from '../src/scheduler/fsrsScheduler';
import type {
  ReviewCardSnapshot,
  ReviewRating,
  SchedulerConfigSnapshot,
} from '../src/core/domain';

const PORT = Number(process.env.PORT ?? 8090);
const HOST = process.env.HOST ?? '0.0.0.0';
const MAX_BODY_BYTES = 256 * 1024;

const cardSchema = z
  .object({
    cardId: z.string().min(1),
    stability: z.number().finite(),
    stabilityFast: z.number().finite(),
    difficulty: z.number().finite(),
    dueAt: z.number().int().positive(),
    lastReviewAt: z.number().int().positive().nullable(),
    state: z.enum(['new', 'learning', 'review', 'relearning']),
    scheduleStatus: z.enum(['new', 'learning', 'review']),
    reps: z.number().int().nonnegative(),
    lapses: z.number().int().nonnegative(),
    scheduledDays: z.number().finite().nonnegative(),
    learningStep: z.number().int().nonnegative(),
  })
  .strict();

const ratingSchema = z.enum(['again', 'hard', 'good', 'easy']);

const reviewRequestSchema = z
  .object({
    studyItemId: z.string().min(1),
    card: cardSchema.nullable(),
    rating: ratingSchema,
    reviewedAt: z.number().int().positive(),
  })
  .strict();

const previewRequestSchema = z
  .object({
    studyItemId: z.string().min(1),
    card: cardSchema.nullable(),
    reviewedAt: z.number().int().positive(),
  })
  .strict();

type JsonObject = Record<string, unknown>;

function canonicalize(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(canonicalize);
  }
  if (typeof value === 'object' && value !== null) {
    return Object.fromEntries(
      Object.entries(value as JsonObject)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([key, child]) => [key, canonicalize(child)]),
    );
  }
  return value;
}

function configHash(config: SchedulerConfigSnapshot): string {
  return createHash('sha256')
    .update(JSON.stringify(canonicalize(config)))
    .digest('hex');
}

function sendJson(response: ServerResponse, status: number, body: unknown): void {
  const payload = JSON.stringify(body);
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
  });
  response.end(payload);
}

function sendError(
  response: ServerResponse,
  status: number,
  code: string,
  message: string,
): void {
  sendJson(response, status, { code, message });
}

async function readJson(request: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of request) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    size += buffer.length;
    if (size > MAX_BODY_BYTES) {
      throw new RangeError('request body is too large');
    }
    chunks.push(buffer);
  }
  if (chunks.length === 0) {
    throw new SyntaxError('request body is required');
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf8')) as unknown;
}

const scheduler = await createDefaultReviewScheduler();
const config = scheduler.config;
const workerMeta = {
  packageVersion: scheduler.packageVersion,
  modelVersion: scheduler.modelVersion,
  config,
  configHash: configHash(config),
};

const server = createServer(async (request, response) => {
  try {
    if (request.method === 'GET' && request.url === '/health') {
      sendJson(response, 200, { status: 'UP', ...workerMeta });
      return;
    }
    if (request.method === 'GET' && request.url === '/v1/config') {
      sendJson(response, 200, workerMeta);
      return;
    }
    if (request.method === 'POST' && request.url === '/v1/review') {
      const input = reviewRequestSchema.parse(await readJson(request));
      const inputCard =
        input.card ?? scheduler.createCard(input.studyItemId, input.reviewedAt);
      const result = scheduler.review({
        card: inputCard as ReviewCardSnapshot,
        rating: input.rating as ReviewRating,
        now: input.reviewedAt,
      });
      sendJson(response, 200, {
        ...workerMeta,
        card: result.card,
        revlog: result.revlog,
      });
      return;
    }
    if (request.method === 'POST' && request.url === '/v1/preview') {
      const input = previewRequestSchema.parse(await readJson(request));
      const card =
        input.card ?? scheduler.createCard(input.studyItemId, input.reviewedAt);
      const previews = Object.fromEntries(
        (['again', 'hard', 'good', 'easy'] as const).map((rating) => {
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
      );
      sendJson(response, 200, { ...workerMeta, previews });
      return;
    }
    sendError(response, 404, 'WORKER_NOT_FOUND', 'unknown worker route');
  } catch (error) {
    if (error instanceof RangeError) {
      sendError(response, 413, 'WORKER_BODY_TOO_LARGE', error.message);
      return;
    }
    if (error instanceof SyntaxError || error instanceof z.ZodError) {
      sendError(response, 400, 'WORKER_INVALID_REQUEST', 'request validation failed');
      return;
    }
    sendError(response, 500, 'WORKER_FAILED', 'scheduler computation failed');
  }
});

server.listen(PORT, HOST);

function shutdown(): void {
  server.close(() => process.exit(0));
}

process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
