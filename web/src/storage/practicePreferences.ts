import type { PracticeModeId } from '../core/domain';
import type {
  InAppReminderRecord,
  PracticeModePreferences,
  PracticeQueueSettings,
} from '../core/repository';
import {
  isPracticeModeId,
  isRetiredPracticeModeId,
} from '../session/practiceModeCompatibility';
import { APP_META_KEYS, type FingerMemoryDatabase } from './database';

const DEFAULT_PRACTICE_QUEUE_SETTINGS: PracticeQueueSettings = {
  newLimit: 10,
  reviewLimit: 50,
  paused: false,
};

const DEFAULT_IN_APP_REMINDER_RECORD: InAppReminderRecord = {
  settings: {
    enabled: true,
    minIntervalMinutes: 30,
    maxIntervalMinutes: 90,
    dailyLimit: 3,
    quietStart: '22:00',
    quietEnd: '08:00',
    snoozeMinutes: 15,
  },
  state: {
    dayKey: '',
    shownToday: 0,
    lastShownAt: null,
    nextAt: null,
    snoozedUntil: null,
    dismissedForDay: false,
  },
};

const CONFIGURABLE_PRACTICE_MODE_IDS: readonly PracticeModeId[] = [
  'keyboard-recall',
];

function isClockTime(value: string): boolean {
  return /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value);
}

function assertTimestamp(value: number, label: string): void {
  if (!Number.isFinite(value) || value <= 0) {
    throw new TypeError(`${label} must be a positive finite timestamp`);
  }
}

function isConfigurablePracticeModeId(value: string): value is PracticeModeId {
  return CONFIGURABLE_PRACTICE_MODE_IDS.some((modeId) => modeId === value);
}

function assertInAppReminderRecord(record: InAppReminderRecord): void {
  const { settings, state } = record;
  if (typeof settings.enabled !== 'boolean') {
    throw new TypeError('reminder enabled flag must be a boolean');
  }
  if (
    !Number.isInteger(settings.minIntervalMinutes) ||
    !Number.isInteger(settings.maxIntervalMinutes) ||
    settings.minIntervalMinutes < 1 ||
    settings.maxIntervalMinutes > 720 ||
    settings.minIntervalMinutes > settings.maxIntervalMinutes
  ) {
    throw new TypeError(
      'reminder interval must be ordered and between 1 and 720 minutes',
    );
  }
  if (
    !Number.isInteger(settings.dailyLimit) ||
    settings.dailyLimit < 1 ||
    settings.dailyLimit > 20
  ) {
    throw new TypeError('reminder daily limit must be between 1 and 20');
  }
  if (!isClockTime(settings.quietStart) || !isClockTime(settings.quietEnd)) {
    throw new TypeError('reminder quiet hours must use HH:mm');
  }
  if (
    !Number.isInteger(settings.snoozeMinutes) ||
    settings.snoozeMinutes < 1 ||
    settings.snoozeMinutes > 240
  ) {
    throw new TypeError('reminder snooze minutes must be between 1 and 240');
  }
  if (
    !Number.isInteger(state.shownToday) ||
    state.shownToday < 0 ||
    state.shownToday > settings.dailyLimit
  ) {
    throw new TypeError('reminder shown count is out of range');
  }
  if (
    typeof state.dayKey !== 'string' ||
    typeof state.dismissedForDay !== 'boolean'
  ) {
    throw new TypeError('reminder state is invalid');
  }

  const timestamps = [
    ['lastShownAt', state.lastShownAt],
    ['nextAt', state.nextAt],
    ['snoozedUntil', state.snoozedUntil],
  ] as const;
  for (const [label, timestamp] of timestamps) {
    if (
      timestamp !== null &&
      (!Number.isFinite(timestamp) || timestamp <= 0)
    ) {
      throw new TypeError(
        `reminder ${label} must be null or a positive timestamp`,
      );
    }
  }
}

function readInAppReminderRecord(
  value: string | undefined,
): InAppReminderRecord {
  if (value === undefined) {
    return structuredClone(DEFAULT_IN_APP_REMINDER_RECORD);
  }

  try {
    const parsed: unknown = JSON.parse(value);
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      'settings' in parsed &&
      'state' in parsed
    ) {
      const record = parsed as InAppReminderRecord;
      assertInAppReminderRecord(record);
      return structuredClone(record);
    }
  } catch {
    return structuredClone(DEFAULT_IN_APP_REMINDER_RECORD);
  }
  return structuredClone(DEFAULT_IN_APP_REMINDER_RECORD);
}

function assertPracticeModePreferences(
  preferences: PracticeModePreferences,
): void {
  if (!Array.isArray(preferences.disabledModeIds)) {
    throw new TypeError('disabled mode ids must be an array');
  }
  if (
    new Set(preferences.disabledModeIds).size !==
    preferences.disabledModeIds.length
  ) {
    throw new TypeError('disabled mode ids must be unique');
  }
  for (const modeId of preferences.disabledModeIds) {
    if (!isConfigurablePracticeModeId(modeId)) {
      throw new TypeError(`unknown practice mode id: ${String(modeId)}`);
    }
  }
}

function readPracticeModePreferences(
  value: string | undefined,
): PracticeModePreferences {
  if (value === undefined) {
    return { disabledModeIds: [] };
  }

  try {
    const parsed: unknown = JSON.parse(value);
    if (
      typeof parsed !== 'object' ||
      parsed === null ||
      !('disabledModeIds' in parsed) ||
      !Array.isArray(parsed.disabledModeIds)
    ) {
      return { disabledModeIds: [] };
    }

    const disabledModeIds = new Set<PracticeModeId>();
    for (const modeId of parsed.disabledModeIds) {
      if (typeof modeId !== 'string' || !isPracticeModeId(modeId)) {
        return { disabledModeIds: [] };
      }
      if (!isRetiredPracticeModeId(modeId)) {
        disabledModeIds.add(modeId);
      }
    }

    const preferences: PracticeModePreferences = {
      disabledModeIds: [...disabledModeIds],
    };
    assertPracticeModePreferences(preferences);
    return preferences;
  } catch {
    return { disabledModeIds: [] };
  }
}

function assertPracticeQueueSettings(settings: PracticeQueueSettings): void {
  if (
    !Number.isInteger(settings.newLimit) ||
    settings.newLimit < 0 ||
    settings.newLimit > 100
  ) {
    throw new TypeError('new-item limit must be an integer between 0 and 100');
  }
  if (
    !Number.isInteger(settings.reviewLimit) ||
    settings.reviewLimit < 0 ||
    settings.reviewLimit > 500
  ) {
    throw new TypeError('review limit must be an integer between 0 and 500');
  }
  if (typeof settings.paused !== 'boolean') {
    throw new TypeError('queue paused flag must be a boolean');
  }
}

export function parsePracticeQueueSettings(
  value: string | undefined,
): PracticeQueueSettings {
  if (value === undefined) {
    return { ...DEFAULT_PRACTICE_QUEUE_SETTINGS };
  }

  try {
    const parsed: unknown = JSON.parse(value);
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      'newLimit' in parsed &&
      typeof parsed.newLimit === 'number' &&
      'reviewLimit' in parsed &&
      typeof parsed.reviewLimit === 'number' &&
      'paused' in parsed &&
      typeof parsed.paused === 'boolean'
    ) {
      const settings = {
        newLimit: parsed.newLimit,
        reviewLimit: parsed.reviewLimit,
        paused: parsed.paused,
      };
      assertPracticeQueueSettings(settings);
      return settings;
    }
  } catch {
    return { ...DEFAULT_PRACTICE_QUEUE_SETTINGS };
  }
  return { ...DEFAULT_PRACTICE_QUEUE_SETTINGS };
}

export async function loadPracticeQueueSettings(
  database: FingerMemoryDatabase,
): Promise<PracticeQueueSettings> {
  await database.open();
  const record = await database.appMeta.get(APP_META_KEYS.practiceQueueSettings);
  return parsePracticeQueueSettings(record?.value);
}

export async function storePracticeQueueSettings(
  database: FingerMemoryDatabase,
  settings: PracticeQueueSettings,
  now: number,
): Promise<PracticeQueueSettings> {
  assertPracticeQueueSettings(settings);
  assertTimestamp(now, 'queue settings update time');
  const normalized = { ...settings };
  await database.open();
  await database.appMeta.put({
    key: APP_META_KEYS.practiceQueueSettings,
    value: JSON.stringify(normalized),
    updatedAt: now,
  });
  return normalized;
}

export async function loadPracticeModePreferences(
  database: FingerMemoryDatabase,
): Promise<PracticeModePreferences> {
  await database.open();
  const record = await database.appMeta.get(
    APP_META_KEYS.practiceModePreferences,
  );
  return readPracticeModePreferences(record?.value);
}

export async function storePracticeModePreferences(
  database: FingerMemoryDatabase,
  preferences: PracticeModePreferences,
  now: number,
): Promise<PracticeModePreferences> {
  assertPracticeModePreferences(preferences);
  assertTimestamp(now, 'mode preferences update time');
  const normalized = {
    disabledModeIds: [...preferences.disabledModeIds].sort(),
  };
  await database.open();
  await database.appMeta.put({
    key: APP_META_KEYS.practiceModePreferences,
    value: JSON.stringify(normalized),
    updatedAt: now,
  });
  return normalized;
}

export async function loadInAppReminderRecord(
  database: FingerMemoryDatabase,
): Promise<InAppReminderRecord> {
  await database.open();
  const record = await database.appMeta.get(APP_META_KEYS.inAppReminder);
  return readInAppReminderRecord(record?.value);
}

export async function storeInAppReminderRecord(
  database: FingerMemoryDatabase,
  record: InAppReminderRecord,
  now: number,
): Promise<InAppReminderRecord> {
  assertInAppReminderRecord(record);
  assertTimestamp(now, 'reminder update time');
  const normalized = structuredClone(record);
  await database.open();
  await database.appMeta.put({
    key: APP_META_KEYS.inAppReminder,
    value: JSON.stringify(normalized),
    updatedAt: now,
  });
  return normalized;
}
