import 'fake-indexeddb/auto';
import { createApp } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { InAppReminderRecord } from '../core/repository';
import { createDefaultReviewScheduler } from '../scheduler/fsrsScheduler';
import { FingerMemoryDatabase } from '../storage/database';
import { createFingerMemoryRepository } from '../storage/localRepository';
import ReminderBanner from './ReminderBanner.vue';
import { localDayKey } from './reminderPolicy';
import ReminderSettings from './ReminderSettings.vue';

const databaseName = 'finger-memory-reminder-test';
let database: FingerMemoryDatabase | null = null;
let mountedApp: ReturnType<typeof createApp> | null = null;

function createRecord(
  dayKey: string,
  nextAt: number | null,
): InAppReminderRecord {
  return {
    settings: {
      enabled: true,
      minIntervalMinutes: 30,
      maxIntervalMinutes: 90,
      dailyLimit: 3,
      quietStart: '00:00',
      quietEnd: '00:00',
      snoozeMinutes: 15,
    },
    state: {
      dayKey,
      shownToday: 0,
      lastShownAt: null,
      nextAt,
      snoozedUntil: null,
      dismissedForDay: false,
    },
  };
}

afterEach(async () => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
  database?.close();
  database = null;
  await FingerMemoryDatabase.delete(databaseName);
});

describe('in-app reminder UI', () => {
  it('shows an actionable reminder and persists snooze', async () => {
    await FingerMemoryDatabase.delete(databaseName);
    database = new FingerMemoryDatabase(databaseName);
    const now = Date.parse('2026-11-15T02:00:00.000Z');
    const repository = createFingerMemoryRepository(database, {
      scheduler: createDefaultReviewScheduler(),
      clock: () => now,
    });
    await repository.initialize();
    const lexicon = await repository.createLexicon({
      name: '提醒测试',
      description: '',
      now,
    });
    await repository.createStudyItem({
      lexiconId: lexicon.id,
      term: 'memory',
      meaning: '记忆',
      acceptedAnswers: [],
      hints: [],
      now: now + 1,
    });
    const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
    await repository.saveInAppReminderRecord(
      createRecord(localDayKey(now, timeZone), now - 1),
      now,
    );

    const root = document.createElement('div');
    document.body.append(root);
    const app = createApp(ReminderBanner, {
      repository,
      clock: () => now,
    });
    mountedApp = app;
    app.mount(root);

    await vi.waitFor(() => {
      expect(root.textContent).toContain('当前有 1 个目标可练习');
    });
    const snoozeButton = [...root.querySelectorAll('button')].find((button) =>
      button.textContent?.includes('稍后 15 分钟'),
    );
    snoozeButton?.click();

    await vi.waitFor(async () => {
      await expect(repository.getInAppReminderRecord()).resolves.toMatchObject({
        state: {
          shownToday: 1,
          snoozedUntil: now + 15 * 60_000,
          nextAt: now + 15 * 60_000,
        },
      });
      expect(root.textContent).not.toContain('当前有 1 个目标可练习');
    });

    const snoozed = await repository.getInAppReminderRecord();
    await repository.saveInAppReminderRecord(
      {
        ...snoozed,
        state: {
          ...snoozed.state,
          nextAt: now,
          snoozedUntil: null,
        },
      },
      now,
    );
    window.dispatchEvent(new Event('finger-memory:reminder-settings-changed'));
    await vi.waitFor(() => {
      expect(root.textContent).toContain('当前有 1 个目标可练习');
    });
    const dismissButton = [...root.querySelectorAll('button')].find((button) =>
      button.textContent?.includes('今天不再提醒'),
    );
    dismissButton?.click();
    await vi.waitFor(async () => {
      await expect(repository.getInAppReminderRecord()).resolves.toMatchObject({
        state: { dismissedForDay: true, nextAt: null },
      });
    });
  });

  it('saves reminder settings and notifies the live banner', async () => {
    await FingerMemoryDatabase.delete(databaseName);
    database = new FingerMemoryDatabase(databaseName);
    const repository = createFingerMemoryRepository(database, {
      scheduler: createDefaultReviewScheduler(),
      clock: () => Date.now(),
    });
    await repository.initialize();
    await expect(repository.getInAppReminderRecord()).resolves.toBeDefined();
    const dispatchSpy = vi.spyOn(window, 'dispatchEvent');
    const root = document.createElement('div');
    document.body.append(root);
    const app = createApp(ReminderSettings, { repository });
    mountedApp = app;
    app.mount(root);

    await vi.waitFor(() => {
      expect(root.querySelectorAll<HTMLInputElement>('input')).toHaveLength(7);
    });
    const inputs = root.querySelectorAll<HTMLInputElement>('input');
    inputs[1]!.value = '45';
    inputs[1]!.dispatchEvent(new Event('input', { bubbles: true }));
    inputs[2]!.value = '120';
    inputs[2]!.dispatchEvent(new Event('input', { bubbles: true }));
    root.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true }));

    await vi.waitFor(async () => {
      await expect(repository.getInAppReminderRecord()).resolves.toMatchObject({
        settings: { minIntervalMinutes: 45, maxIntervalMinutes: 120 },
      });
      expect(root.textContent).toContain('提醒设置已保存');
    });
    expect(
      dispatchSpy.mock.calls.some(
        ([event]) => (event as Event).type === 'finger-memory:reminder-settings-changed',
      ),
    ).toBe(true);
    dispatchSpy.mockRestore();
  });
});
