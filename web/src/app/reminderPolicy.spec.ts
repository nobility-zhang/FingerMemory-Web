import { describe, expect, it } from 'vitest';
import type { InAppReminderRecord } from '../core/repository';
import {
  deferReminder,
  dismissReminderForDay,
  isReminderDue,
  isWithinQuietHours,
  localDayKey,
  markReminderShown,
  prepareReminderRecord,
  snoozeReminder,
} from './reminderPolicy';

const timeZone = 'Asia/Shanghai';

function createRecord(): InAppReminderRecord {
  return {
    settings: {
      enabled: true,
      minIntervalMinutes: 30,
      maxIntervalMinutes: 90,
      dailyLimit: 2,
      quietStart: '22:00',
      quietEnd: '08:00',
      snoozeMinutes: 15,
    },
    state: {
      dayKey: '2026-11-15',
      shownToday: 0,
      lastShownAt: null,
      nextAt: null,
      snoozedUntil: null,
      dismissedForDay: false,
    },
  };
}

describe('in-app reminder policy', () => {
  it('detects quiet hours that wrap across local midnight', () => {
    const settings = createRecord().settings;
    expect(
      isWithinQuietHours(Date.parse('2026-11-15T14:30:00.000Z'), settings, timeZone),
    ).toBe(true);
    expect(
      isWithinQuietHours(Date.parse('2026-11-15T23:30:00.000Z'), settings, timeZone),
    ).toBe(true);
    expect(
      isWithinQuietHours(Date.parse('2026-11-15T04:00:00.000Z'), settings, timeZone),
    ).toBe(false);
  });

  it('schedules random intervals and defers quiet-hour reminders to morning', () => {
    const at = Date.parse('2026-11-15T14:30:00.000Z');
    const prepared = prepareReminderRecord(createRecord(), at, timeZone, () => 0.5);

    expect(localDayKey(at, timeZone)).toBe('2026-11-15');
    expect(prepared.state.nextAt).toBe(Date.parse('2026-11-16T00:00:00.000Z'));
  });

  it('enforces daily limits, snooze and dismiss-today state', () => {
    const at = Date.parse('2026-11-15T02:00:00.000Z');
    const due = prepareReminderRecord(
      { ...createRecord(), state: { ...createRecord().state, nextAt: at } },
      at,
      timeZone,
      () => 0,
    );
    expect(isReminderDue(due, at, timeZone)).toBe(true);

    const shown = markReminderShown(due, at, () => 0);
    expect(shown.state.shownToday).toBe(1);
    expect(shown.state.nextAt).toBe(at + 30 * 60_000);
    expect(snoozeReminder(shown, at).state.snoozedUntil).toBe(at + 15 * 60_000);
    expect(
      deferReminder(shown, at, () => 0.99).state.nextAt,
    ).toBe(at + 90 * 60_000);
    expect(dismissReminderForDay(shown)).toMatchObject({
      state: { dismissedForDay: true, nextAt: null },
    });

    const atLimit = { ...shown, state: { ...shown.state, shownToday: 2 } };
    expect(prepareReminderRecord(atLimit, at, timeZone).state.nextAt).toBeNull();
  });
});
