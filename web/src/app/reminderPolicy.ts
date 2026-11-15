import type { TimestampMs } from '../core/domain';
import { getZonedTimeTimestamp } from '../core/due';
import type {
  InAppReminderRecord,
  InAppReminderSettings,
} from '../core/repository';

export type ReminderRandom = () => number;

function clockMinutes(value: string): number {
  const [hour, minute] = value.split(':').map(Number);
  return (hour ?? 0) * 60 + (minute ?? 0);
}

export function localDayKey(at: TimestampMs, timeZone: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date(at));
  const values = new Map(parts.map((part) => [part.type, part.value]));
  return `${values.get('year')}-${values.get('month')}-${values.get('day')}`;
}

function localMinuteOfDay(at: TimestampMs, timeZone: string): number {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(new Date(at));
  const values = new Map(parts.map((part) => [part.type, part.value]));
  return Number(values.get('hour')) * 60 + Number(values.get('minute'));
}

export function isWithinQuietHours(
  at: TimestampMs,
  settings: InAppReminderSettings,
  timeZone: string,
): boolean {
  const start = clockMinutes(settings.quietStart);
  const end = clockMinutes(settings.quietEnd);
  if (start === end) {
    return false;
  }
  const current = localMinuteOfDay(at, timeZone);
  return start < end
    ? current >= start && current < end
    : current >= start || current < end;
}

function quietHoursEnd(
  at: TimestampMs,
  settings: InAppReminderSettings,
  timeZone: string,
): TimestampMs | null {
  if (!isWithinQuietHours(at, settings, timeZone)) {
    return null;
  }
  const start = clockMinutes(settings.quietStart);
  const end = clockMinutes(settings.quietEnd);
  const current = localMinuteOfDay(at, timeZone);
  const dayOffset = start > end && current >= start ? 1 : 0;
  const [hour, minute] = settings.quietEnd.split(':').map(Number);
  return getZonedTimeTimestamp(at, timeZone, dayOffset, hour ?? 0, minute ?? 0);
}

export function nextRandomReminderAt(
  at: TimestampMs,
  settings: InAppReminderSettings,
  random: ReminderRandom = Math.random,
): TimestampMs {
  const sample = Math.min(0.999999, Math.max(0, random()));
  const span = settings.maxIntervalMinutes - settings.minIntervalMinutes;
  const minutes = settings.minIntervalMinutes + Math.floor(sample * (span + 1));
  return at + minutes * 60_000;
}

export function prepareReminderRecord(
  input: InAppReminderRecord,
  at: TimestampMs,
  timeZone: string,
  random: ReminderRandom = Math.random,
): InAppReminderRecord {
  const record = structuredClone(input);
  const dayKey = localDayKey(at, timeZone);
  if (record.state.dayKey !== dayKey) {
    record.state = {
      dayKey,
      shownToday: 0,
      lastShownAt: null,
      nextAt: null,
      snoozedUntil: null,
      dismissedForDay: false,
    };
  }

  if (!record.settings.enabled) {
    record.state.nextAt = null;
    record.state.snoozedUntil = null;
    return record;
  }
  if (
    record.state.dismissedForDay ||
    record.state.shownToday >= record.settings.dailyLimit
  ) {
    record.state.nextAt = null;
    return record;
  }

  const quietEnd = quietHoursEnd(at, record.settings, timeZone);
  if (quietEnd !== null) {
    record.state.nextAt = quietEnd;
    return record;
  }
  if (record.state.snoozedUntil !== null && record.state.snoozedUntil > at) {
    record.state.nextAt = record.state.snoozedUntil;
    return record;
  }
  if (record.state.nextAt === null) {
    record.state.nextAt = nextRandomReminderAt(at, record.settings, random);
  }
  return record;
}

export function isReminderDue(
  record: InAppReminderRecord,
  at: TimestampMs,
  timeZone: string,
): boolean {
  return (
    record.settings.enabled &&
    !record.state.dismissedForDay &&
    record.state.shownToday < record.settings.dailyLimit &&
    !isWithinQuietHours(at, record.settings, timeZone) &&
    record.state.nextAt !== null &&
    record.state.nextAt <= at
  );
}

export function markReminderShown(
  input: InAppReminderRecord,
  at: TimestampMs,
  random: ReminderRandom = Math.random,
): InAppReminderRecord {
  const record = structuredClone(input);
  record.state.shownToday += 1;
  record.state.lastShownAt = at;
  record.state.snoozedUntil = null;
  record.state.nextAt = nextRandomReminderAt(at, record.settings, random);
  return record;
}

export function deferReminder(
  input: InAppReminderRecord,
  at: TimestampMs,
  random: ReminderRandom = Math.random,
): InAppReminderRecord {
  const record = structuredClone(input);
  record.state.nextAt = nextRandomReminderAt(at, record.settings, random);
  return record;
}

export function snoozeReminder(
  input: InAppReminderRecord,
  at: TimestampMs,
): InAppReminderRecord {
  const record = structuredClone(input);
  const snoozedUntil = at + record.settings.snoozeMinutes * 60_000;
  record.state.snoozedUntil = snoozedUntil;
  record.state.nextAt = snoozedUntil;
  return record;
}

export function dismissReminderForDay(
  input: InAppReminderRecord,
): InAppReminderRecord {
  const record = structuredClone(input);
  record.state.dismissedForDay = true;
  record.state.nextAt = null;
  return record;
}

export function resetReminderSchedule(
  input: InAppReminderRecord,
  at: TimestampMs,
  random: ReminderRandom = Math.random,
): InAppReminderRecord {
  const record = structuredClone(input);
  record.state.shownToday = Math.min(
    record.state.shownToday,
    record.settings.dailyLimit,
  );
  record.state.snoozedUntil = null;
  record.state.nextAt = record.settings.enabled
    ? nextRandomReminderAt(at, record.settings, random)
    : null;
  return record;
}
