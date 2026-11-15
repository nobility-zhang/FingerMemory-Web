import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { createDefaultReviewScheduler, TS_FSRS_PACKAGE_VERSION } from './fsrsScheduler';

const packageJson = JSON.parse(
  readFileSync(resolve(process.cwd(), 'package.json'), 'utf8'),
) as { dependencies: Record<string, string> };

describe('ts-fsrs adapter', () => {
  it('uses the exact locked package and exposes its FSRS-7 configuration', async () => {
    const scheduler = await createDefaultReviewScheduler();

    expect(packageJson.dependencies['ts-fsrs']).toBe(TS_FSRS_PACKAGE_VERSION);
    expect(scheduler.packageVersion).toBe('6.0.0');
    expect(scheduler.modelVersion).toBe('fsrs-7');
    expect(scheduler.config).toMatchObject({
      packageVersion: '6.0.0',
      modelVersion: 'fsrs-7',
      fractionalDays: true,
      enableFuzz: false,
      enableShortTerm: true,
      learningSteps: ['1m', '10m'],
      relearningSteps: ['10m'],
      allowModelOverride: true,
    });
  });

  it('schedules a review without mutating the input card snapshot', async () => {
    const scheduler = await createDefaultReviewScheduler();
    const now = Date.parse('2026-11-15T01:00:00.000Z');
    const card = scheduler.createCard('study-memory', now);
    const originalCard = structuredClone(card);

    const result = scheduler.review({ card, rating: 'good', now });

    expect(card).toEqual(originalCard);
    expect(result.card.lastReviewAt).toBe(now);
    expect(result.card.dueAt).toBeGreaterThan(now);
    expect(result.revlog.reviewTime).toBe(now);
    expect(result.revlog.rating).toBe('good');
    expect(result.revlog.cardId).toBe('study-memory');
  });
});
