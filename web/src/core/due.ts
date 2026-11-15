import type { TimestampMs } from './domain';

const DAY_MS = 24 * 60 * 60 * 1000;

interface LocalDateParts {
  year: number;
  month: number;
  day: number;
}

interface LocalDateTimeParts extends LocalDateParts {
  hour: number;
  minute: number;
}

function partsAt(timestamp: TimestampMs, timeZone: string): LocalDateParts {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(new Date(timestamp));
  const values = new Map(parts.map((part) => [part.type, part.value]));
  return {
    year: Number(values.get('year')),
    month: Number(values.get('month')),
    day: Number(values.get('day')),
  };
}

function wallClockToTimestamp(
  parts: LocalDateParts | LocalDateTimeParts,
  timeZone: string,
): TimestampMs {
  const hour = 'hour' in parts ? parts.hour : 0;
  const minute = 'minute' in parts ? parts.minute : 0;
  const target = Date.UTC(parts.year, parts.month - 1, parts.day, hour, minute);
  let guess = target;

  for (let attempt = 0; attempt < 3; attempt += 1) {
    const observedParts = new Intl.DateTimeFormat('en-CA', {
      timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    }).formatToParts(new Date(guess));
    const values = new Map(observedParts.map((part) => [part.type, part.value]));
    const observed = Date.UTC(
      Number(values.get('year')),
      Number(values.get('month')) - 1,
      Number(values.get('day')),
      Number(values.get('hour')),
      Number(values.get('minute')),
      Number(values.get('second')),
    );
    const difference = target - observed;
    guess += difference;
    if (difference === 0) {
      break;
    }
  }

  return guess;
}

export interface LocalDayRange {
  startAt: TimestampMs;
  endAt: TimestampMs;
}

export function getLocalDayRange(
  at: TimestampMs,
  timeZone: string,
): LocalDayRange {
  if (!Number.isFinite(at)) {
    throw new TypeError('Current timestamp must be finite');
  }
  if (timeZone.trim().length === 0) {
    throw new TypeError('Time zone must not be empty');
  }

  const current = partsAt(at, timeZone);
  const nextDate = new Date(Date.UTC(current.year, current.month - 1, current.day + 1));
  return {
    startAt: wallClockToTimestamp(current, timeZone),
    endAt: wallClockToTimestamp(
      {
        year: nextDate.getUTCFullYear(),
        month: nextDate.getUTCMonth() + 1,
        day: nextDate.getUTCDate(),
      },
      timeZone,
    ),
  };
}

export function getZonedTimeTimestamp(
  at: TimestampMs,
  timeZone: string,
  dayOffset: number,
  hour: number,
  minute: number,
): TimestampMs {
  if (!Number.isInteger(dayOffset) || !Number.isInteger(hour) || !Number.isInteger(minute)) {
    throw new TypeError('Day offset, hour and minute must be integers');
  }
  if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
    throw new RangeError('Hour and minute are out of range');
  }
  const current = partsAt(at, timeZone);
  const date = new Date(Date.UTC(current.year, current.month - 1, current.day + dayOffset));
  return wallClockToTimestamp(
    {
      year: date.getUTCFullYear(),
      month: date.getUTCMonth() + 1,
      day: date.getUTCDate(),
      hour,
      minute,
    },
    timeZone,
  );
}

function calendarDaySerial(timestamp: TimestampMs, timeZone: string): number {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date(timestamp));

  const values = new Map(parts.map((part) => [part.type, part.value]));
  const year = Number(values.get('year'));
  const month = Number(values.get('month'));
  const day = Number(values.get('day'));

  return Date.UTC(year, month - 1, day) / DAY_MS;
}

export function formatDueLabel(
  dueAt: TimestampMs,
  now: TimestampMs,
  timeZone: string,
): string {
  if (!Number.isFinite(dueAt) || !Number.isFinite(now)) {
    throw new TypeError('Due and current timestamps must be finite');
  }

  if (dueAt <= now) {
    return '待复习';
  }

  const dueDate = new Date(dueAt);
  const time = new Intl.DateTimeFormat('zh-CN', {
    timeZone,
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(dueDate);
  const dayDifference = calendarDaySerial(dueAt, timeZone) - calendarDaySerial(now, timeZone);

  if (dayDifference === 0) {
    return `今天 ${time}`;
  }
  if (dayDifference === 1) {
    return `明天 ${time}`;
  }

  const dateParts = new Intl.DateTimeFormat('zh-CN', {
    timeZone,
    month: 'numeric',
    day: 'numeric',
  }).formatToParts(dueDate);
  const month = dateParts.find((part) => part.type === 'month')?.value ?? '';
  const day = dateParts.find((part) => part.type === 'day')?.value ?? '';
  return `${month}月${day}日 ${time}`;
}
