import { describe, expect, it } from 'vitest';
import type { StorageSnapshot } from '../core/repository';
import { createBackupFile } from './backupFile';

describe('createBackupFile', () => {
  it('creates a versioned JSON download with a UTC timestamped name', () => {
    const snapshot: StorageSnapshot = {
      snapshotVersion: 1,
      schemaVersion: 3,
      dataFormatVersion: 3,
      exportedAt: Date.parse('2026-11-15T02:03:04.000Z'),
      tables: {
        appMeta: [],
        lexicons: [],
        words: [],
        studyItems: [],
        lexiconItems: [],
        notes: [],
        practiceAttempts: [],
        practiceSessions: [],
        reviewStates: [],
        reviewLogs: [],
      },
    };

    const file = createBackupFile(snapshot);

    expect(file.fileName).toBe('finger-memory-backup-20261115-020304.json');
    expect(file.content.endsWith('\n')).toBe(true);
    expect(JSON.parse(file.content)).toEqual(snapshot);
  });
});
