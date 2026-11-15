import type { TimestampMs } from './domain';

const DAY_MS = 24 * 60 * 60 * 1000;

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
