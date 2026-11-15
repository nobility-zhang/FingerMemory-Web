import type { StorageSnapshot } from '../core/repository';

export interface BackupFile {
  fileName: string;
  content: string;
  mimeType: 'application/json;charset=utf-8';
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

export function createBackupFile(snapshot: StorageSnapshot): BackupFile {
  const date = new Date(snapshot.exportedAt);
  const fileName =
    `finger-memory-backup-${date.getUTCFullYear()}` +
    `${pad(date.getUTCMonth() + 1)}${pad(date.getUTCDate())}` +
    `-${pad(date.getUTCHours())}${pad(date.getUTCMinutes())}${pad(date.getUTCSeconds())}.json`;
  return {
    fileName,
    content: `${JSON.stringify(snapshot, null, 2)}\n`,
    mimeType: 'application/json;charset=utf-8',
  };
}

export function downloadBackupFile(file: BackupFile): void {
  const url = URL.createObjectURL(new Blob([file.content], { type: file.mimeType }));
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = file.fileName;
  anchor.click();
  URL.revokeObjectURL(url);
}
