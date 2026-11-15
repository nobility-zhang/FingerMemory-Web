<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import type {
  FingerMemoryRepository,
  StorageCounts,
  StorageSnapshotPreview,
  StorageStatus,
} from '../core/repository';
import { createBackupFile, downloadBackupFile } from './backupFile';

const props = defineProps<{
  repository: FingerMemoryRepository;
}>();
const emit = defineEmits<{
  restored: [status: StorageStatus];
}>();

const status = ref<StorageStatus>();
const backupPreview = ref<StorageSnapshotPreview>();
const pendingSnapshot = ref<unknown>();
const backupFileName = ref('');
const confirmOverwrite = ref(false);
const storageUsage = ref<number>();
const storageQuota = ref<number>();
const storagePersisted = ref<boolean>();
const busy = ref(false);
const error = ref<string | null>(null);
const notice = ref<string | null>(null);

function countRowsFrom(counts: StorageCounts | undefined): Array<[string, number]> {
  if (counts === undefined) {
    return [];
  }
  return [
    ['词库', counts.lexicons],
    ['词条', counts.words],
    ['学习目标', counts.studyItems],
    ['笔记', counts.notes],
    ['练习记录', counts.practiceAttempts],
    ['练习会话', counts.practiceSessions],
    ['复习状态', counts.reviewStates],
    ['复习日志', counts.reviewLogs],
  ] satisfies Array<[string, number]>;
}

const countRows = computed(() => countRowsFrom(status.value?.counts));
const backupCountRows = computed(() => countRowsFrom(backupPreview.value?.counts));

async function loadStatus(): Promise<void> {
  try {
    status.value = await props.repository.getStatus();
  } catch {
    error.value = '无法读取本地数据状态。';
  }
}

async function loadStorageHealth(): Promise<void> {
  if (navigator.storage?.estimate === undefined) {
    return;
  }
  try {
    const estimate = await navigator.storage.estimate();
    storageUsage.value = estimate.usage;
    storageQuota.value = estimate.quota;
    storagePersisted.value = await navigator.storage.persisted?.();
  } catch {
    storageUsage.value = undefined;
    storageQuota.value = undefined;
  }
}

async function requestPersistentStorage(): Promise<void> {
  if (navigator.storage?.persist === undefined) {
    error.value = '当前浏览器不支持申请持久存储。';
    return;
  }
  busy.value = true;
  error.value = null;
  try {
    storagePersisted.value = await navigator.storage.persist();
    notice.value = storagePersisted.value
      ? '浏览器已授予持久存储。'
      : '浏览器未授予持久存储，请继续定期下载备份。';
    await loadStorageHealth();
  } catch {
    error.value = '无法申请持久存储。';
  } finally {
    busy.value = false;
  }
}

function formatBytes(value: number | undefined): string {
  if (value === undefined) {
    return '未知';
  }
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  if (value < 1024 * 1024 * 1024) {
    return `${(value / (1024 * 1024)).toFixed(1)} MB`;
  }
  return `${(value / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

async function exportBackup(): Promise<void> {
  busy.value = true;
  error.value = null;
  notice.value = null;
  try {
    const snapshot = await props.repository.exportSnapshot(Date.now());
    const file = createBackupFile(snapshot);
    downloadBackupFile(file);
    status.value = await props.repository.getStatus();
    notice.value = `备份文件 ${file.fileName} 已生成。`;
  } catch {
    error.value = '备份导出失败，本地数据未改变。';
  } finally {
    busy.value = false;
  }
}

function formatExportedAt(exportedAt: number): string {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(new Date(exportedAt));
}

async function chooseBackupFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (file === undefined) {
    return;
  }

  pendingSnapshot.value = undefined;
  backupPreview.value = undefined;
  backupFileName.value = file.name;
  confirmOverwrite.value = false;
  error.value = null;
  notice.value = null;
  busy.value = true;
  try {
    if (file.size > 25 * 1024 * 1024) {
      throw new Error('backup file is too large');
    }
    const text = await file.text();
    const parsed: unknown = JSON.parse(text);
    backupPreview.value = await props.repository.previewSnapshot(parsed);
    pendingSnapshot.value = parsed;
  } catch {
    error.value = '备份校验失败，现有数据未改变。';
  } finally {
    busy.value = false;
    input.value = '';
  }
}

async function restoreBackup(): Promise<void> {
  if (
    pendingSnapshot.value === undefined ||
    backupPreview.value === undefined ||
    !confirmOverwrite.value
  ) {
    return;
  }

  busy.value = true;
  error.value = null;
  notice.value = null;
  try {
    const restoredStatus = await props.repository.importSnapshot(pendingSnapshot.value);
    status.value = restoredStatus;
    notice.value = `已恢复 ${formatExportedAt(backupPreview.value.exportedAt)} 导出的备份。`;
    pendingSnapshot.value = undefined;
    backupPreview.value = undefined;
    backupFileName.value = '';
    confirmOverwrite.value = false;
    emit('restored', restoredStatus);
  } catch {
    error.value = '恢复失败，现有数据未改变。';
  } finally {
    busy.value = false;
  }
}

onMounted(() => {
  void loadStatus();
  void loadStorageHealth();
});
</script>

<template>
  <section class="data-workspace" aria-labelledby="data-title">
    <header class="data-heading">
      <div>
        <p>本地数据</p>
        <h2 id="data-title">备份与恢复</h2>
      </div>
      <span>JSON · 完整数据库</span>
    </header>

    <section class="export-panel" aria-labelledby="export-title">
      <div>
        <h3 id="export-title">导出备份</h3>
        <p>备份包含词库、笔记、练习会话、复习状态和日志。文件不加密，请自行妥善保存。</p>
      </div>
      <button type="button" class="primary-action" :disabled="busy" @click="exportBackup">
        下载完整备份
      </button>
    </section>

    <section class="import-panel" aria-labelledby="restore-title">
      <div>
        <h3 id="restore-title">恢复备份</h3>
        <p>恢复会校验完整备份并覆盖当前本地数据。确认前不会写入，失败时保留现有数据。</p>
      </div>
      <label class="file-picker">
        <span>选择备份 JSON · 最大 25 MB</span>
        <input
          type="file"
          accept=".json,application/json"
          :disabled="busy"
          @change="chooseBackupFile"
        />
      </label>

      <div v-if="backupPreview" class="restore-preview">
        <div class="preview-heading">
          <div>
            <strong>{{ backupFileName }}</strong>
            <p>
              导出于 {{ formatExportedAt(backupPreview.exportedAt) }} · 数据 v{{ backupPreview.schemaVersion }}
            </p>
          </div>
          <span>版本兼容 · 数据 v{{ backupPreview.dataFormatVersion }}</span>
        </div>
        <dl>
          <div v-for="[label, count] in backupCountRows" :key="label">
            <dt>{{ label }}</dt>
            <dd>{{ count }}</dd>
          </div>
        </dl>
        <label class="overwrite-confirm">
          <input v-model="confirmOverwrite" type="checkbox" :disabled="busy" />
          我确认使用该备份覆盖当前本地数据
        </label>
        <button
          type="button"
          class="danger-action"
          :disabled="busy || !confirmOverwrite"
          @click="restoreBackup"
        >
          覆盖并恢复
        </button>
      </div>
    </section>

    <section class="counts-panel" aria-labelledby="counts-title">
      <div class="counts-heading">
        <h3 id="counts-title">当前数据</h3>
        <span v-if="status">
          {{ status.scheduler.modelVersion.toUpperCase() }} · 数据 v{{ status.schemaVersion }}
        </span>
      </div>
      <dl v-if="countRows.length > 0">
        <div v-for="[label, count] in countRows" :key="label">
          <dt>{{ label }}</dt>
          <dd>{{ count }}</dd>
        </div>
      </dl>
      <p v-else>正在读取数据状态...</p>
    </section>

    <section class="storage-panel" aria-labelledby="storage-title">
      <div>
        <h3 id="storage-title">浏览器存储</h3>
        <p v-if="storageUsage !== undefined || storageQuota !== undefined">
          当前使用 {{ formatBytes(storageUsage) }}，浏览器配额
          {{ formatBytes(storageQuota) }}。
        </p>
        <p v-else>当前浏览器没有提供存储用量信息。</p>
        <p>
          持久存储状态：
          {{ storagePersisted === true ? '已授予' : storagePersisted === false ? '未授予' : '未知' }}
        </p>
      </div>
      <button
        type="button"
        :disabled="busy || storagePersisted === true"
        @click="requestPersistentStorage"
      >
        {{ storagePersisted === true ? '已启用持久存储' : '申请持久存储' }}
      </button>
    </section>

    <p v-if="error" class="data-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="data-notice" role="status">{{ notice }}</p>
  </section>
</template>

<style scoped>
.data-workspace {
  display: grid;
  gap: 18px;
  max-width: 920px;
  margin: 0 auto;
}

.data-heading,
.export-panel,
.import-panel,
.counts-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.data-heading p,
.data-heading h2,
.export-panel h3,
.export-panel p,
.import-panel h3,
.import-panel p,
.counts-panel h3,
.counts-panel p {
  margin: 0;
}

.data-heading p {
  color: #75847b;
  font-size: 0.7rem;
  font-weight: 700;
}

.data-heading h2 {
  margin-top: 4px;
  color: #1f3429;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 2.2rem;
  font-weight: 500;
}

.data-heading > span {
  padding: 6px 9px;
  border: 1px solid #cbd6cf;
  border-radius: 999px;
  color: #64746b;
  font-size: 0.7rem;
}

.export-panel,
.import-panel,
.counts-panel,
.storage-panel {
  padding: 20px;
  border: 1px solid #d0dad4;
  border-radius: 8px;
  background: #fff;
}

.export-panel {
  align-items: center;
}

.export-panel h3,
.import-panel h3,
.counts-panel h3,
.storage-panel h3 {
  margin-bottom: 5px;
  color: #253a2e;
  font-size: 1rem;
}

.export-panel p,
.import-panel p,
.counts-panel p,
.storage-panel p {
  color: #6d7b73;
  font-size: 0.8rem;
  line-height: 1.65;
}

button {
  min-height: 40px;
  padding: 8px 14px;
  border: 1px solid #244f3d;
  border-radius: 6px;
  background: #244f3d;
  color: #fff;
  font-size: 0.8rem;
  font-weight: 650;
  cursor: pointer;
  white-space: nowrap;
}

.danger-action {
  border-color: #b85d45;
  background: #a84d36;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

button:focus-visible {
  outline: 3px solid rgb(45 113 83 / 28%);
  outline-offset: 2px;
}

.counts-heading {
  align-items: center;
}

.counts-heading span {
  color: #74827a;
  font-size: 0.72rem;
}

dl {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin: 16px 0 0;
}

.import-panel {
  display: grid;
  gap: 16px;
}

.storage-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.storage-panel p + p {
  margin-top: 4px;
}

.file-picker {
  display: grid;
  gap: 5px;
  width: fit-content;
  color: #53655b;
  font-size: 0.75rem;
  font-weight: 700;
}

.file-picker input {
  max-width: 100%;
  color: #53655b;
  font: inherit;
}

.file-picker input:focus-visible,
.overwrite-confirm input:focus-visible {
  outline: 3px solid rgb(45 113 83 / 28%);
  outline-offset: 2px;
}

.restore-preview {
  display: grid;
  gap: 13px;
  padding-top: 14px;
  border-top: 1px solid #dce4df;
}

.preview-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.preview-heading strong {
  color: #253a2e;
}

.preview-heading p {
  margin-top: 3px;
}

.preview-heading > span {
  color: #246044;
  font-size: 0.72rem;
  font-weight: 700;
}

.overwrite-confirm {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #7f4231;
  font-size: 0.78rem;
  font-weight: 650;
}

.restore-preview > button {
  justify-self: start;
}

dl div {
  padding: 10px 12px;
  border: 1px solid #dce4df;
  border-radius: 6px;
  background: #f8faf9;
}

dt {
  color: #74827a;
  font-size: 0.68rem;
}

dd {
  margin: 4px 0 0;
  color: #253a2e;
  font-size: 1.2rem;
  font-weight: 700;
}

.data-error,
.data-notice {
  margin: 0;
  padding: 11px 13px;
  border-radius: 6px;
}

.data-error {
  border: 1px solid #dfc2b9;
  background: #fbf3f0;
  color: #9a432f;
}

.data-notice {
  border: 1px solid #c5d9cc;
  background: #f0f7f2;
  color: #246044;
}

@media (max-width: 760px) {
  .data-heading,
  .export-panel,
  .preview-heading,
  .storage-panel {
    align-items: stretch;
    flex-direction: column;
  }

  .data-heading > span,
  .export-panel button,
  .storage-panel button,
  .file-picker,
  .restore-preview > button {
    align-self: flex-start;
  }

  dl {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 440px) {
  dl {
    grid-template-columns: 1fr;
  }
}
</style>
