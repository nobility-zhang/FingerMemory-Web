<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import type { StorageStatus } from '../core/repository';
import type { FingerMemoryRepository } from '../core/repository';
import { createBackupFile, downloadBackupFile } from './backupFile';

const props = defineProps<{
  repository: FingerMemoryRepository;
}>();

const status = ref<StorageStatus>();
const busy = ref(false);
const error = ref<string | null>(null);
const notice = ref<string | null>(null);

const countRows = computed(() => {
  const counts = status.value?.counts;
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
});

async function loadStatus(): Promise<void> {
  try {
    status.value = await props.repository.getStatus();
  } catch {
    error.value = '无法读取本地数据状态。';
  }
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

onMounted(() => {
  void loadStatus();
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
.counts-panel {
  padding: 20px;
  border: 1px solid #d0dad4;
  border-radius: 8px;
  background: #fff;
}

.export-panel {
  align-items: center;
}

.export-panel h3,
.counts-panel h3 {
  margin-bottom: 5px;
  color: #253a2e;
  font-size: 1rem;
}

.export-panel p,
.counts-panel p {
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
  .export-panel {
    align-items: stretch;
    flex-direction: column;
  }

  .data-heading > span,
  .export-panel button {
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
