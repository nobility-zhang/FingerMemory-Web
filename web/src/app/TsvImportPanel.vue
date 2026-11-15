<script setup lang="ts">
import { computed, ref } from 'vue';
import type {
  FingerMemoryRepository,
  ImportStudyItemRow,
  ImportStudyItemsResult,
  LexiconStudyItem,
} from '../core/repository';
import {
  parseTsvImport,
  type TsvDuplicateRow,
  type TsvImportPreview,
} from '../import/tsvImport';

const props = defineProps<{
  repository: FingerMemoryRepository;
  lexiconId: string;
  existingItems: LexiconStudyItem[];
}>();

const emit = defineEmits<{
  imported: [result: ImportStudyItemsResult];
}>();

const tsvText = ref('');
const preview = ref<TsvImportPreview>();
const duplicateActions = ref<Record<number, 'skip' | 'create'>>({});
const batchId = ref<string>();
const busy = ref(false);
const error = ref<string | null>(null);
const notice = ref<string | null>(null);

const importableCount = computed(() => {
  const current = preview.value;
  if (current === undefined) {
    return 0;
  }
  return (
    current.validRows.length +
    current.duplicateRows.filter(
      (row) => duplicateActions.value[row.recordNumber] === 'create',
    ).length
  );
});

const canImport = computed(
  () =>
    preview.value !== undefined &&
    preview.value.invalidRows.length === 0 &&
    importableCount.value > 0 &&
    !busy.value,
);

function clearPreview(): void {
  preview.value = undefined;
  duplicateActions.value = {};
  batchId.value = undefined;
  error.value = null;
  notice.value = null;
}

function resetImport(): void {
  tsvText.value = '';
  clearPreview();
}

function previewImport(): void {
  clearPreview();
  if (tsvText.value.trim().length === 0) {
    error.value = '请先粘贴 TSV 内容或选择文件。';
    return;
  }

  const parsed = parseTsvImport(
    tsvText.value,
    props.existingItems.map((item) => item.practiceItem),
  );
  preview.value = parsed;
  duplicateActions.value = Object.fromEntries(
    parsed.duplicateRows.map((row) => [row.recordNumber, 'skip']),
  );
  batchId.value = crypto.randomUUID();
}

async function chooseFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (file === undefined) {
    return;
  }

  clearPreview();
  try {
    tsvText.value = await file.text();
    previewImport();
  } catch {
    error.value = '无法读取所选文件。';
  } finally {
    input.value = '';
  }
}

function sourceLabel(row: TsvDuplicateRow): string {
  const sources: string[] = [];
  if (row.sources.includes('existing')) {
    sources.push('当前词库已有');
  }
  if (row.sources.includes('file')) {
    sources.push(
      `文件内与第 ${row.matchingRecordNumbers.join('、')} 条重复`,
    );
  }
  return sources.join('；');
}

function importRows(): ImportStudyItemRow[] {
  const current = preview.value;
  if (current === undefined) {
    return [];
  }

  return [
    ...current.validRows.map((row) => ({
      term: row.term,
      meaning: row.meaning,
      hints: row.hints,
      duplicateAction: 'create' as const,
      recordNumber: row.recordNumber,
    })),
    ...current.duplicateRows.map((row) => ({
      term: row.term,
      meaning: row.meaning,
      hints: row.hints,
      duplicateAction:
        duplicateActions.value[row.recordNumber] ?? ('skip' as const),
      recordNumber: row.recordNumber,
    })),
  ]
    .sort((left, right) => left.recordNumber - right.recordNumber)
    .map((row) => ({
      term: row.term,
      meaning: row.meaning,
      hints: row.hints,
      duplicateAction: row.duplicateAction,
    }));
}

async function confirmImport(): Promise<void> {
  if (!canImport.value || batchId.value === undefined) {
    return;
  }

  busy.value = true;
  error.value = null;
  notice.value = null;
  try {
    const result = await props.repository.importStudyItems({
      batchId: batchId.value,
      lexiconId: props.lexiconId,
      rows: importRows(),
      now: Date.now(),
    });
    notice.value =
      result.outcome === 'duplicate'
        ? `该批导入已处理过：新增 ${result.importedCount} 条，跳过 ${result.skippedCount} 条。`
        : `已导入 ${result.importedCount} 条，跳过 ${result.skippedCount} 条。`;
    emit('imported', result);
    preview.value = undefined;
    duplicateActions.value = {};
    batchId.value = undefined;
    tsvText.value = '';
  } catch {
    error.value = '导入未保存，现有数据未改变。可以保留预览后重试。';
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <section class="tsv-import" aria-labelledby="tsv-import-title">
    <header class="import-heading">
      <div>
        <p>批量导入</p>
        <h3 id="tsv-import-title">TSV 预览</h3>
      </div>
      <span>仅提交一次</span>
    </header>

    <label class="file-picker">
      <span>选择 .tsv 文件</span>
      <input type="file" accept=".tsv,text/tab-separated-values,text/plain" @change="chooseFile" />
    </label>

    <label for="tsv-import-text">
      TSV 内容
      <textarea
        id="tsv-import-text"
        v-model="tsvText"
        rows="6"
        spellcheck="false"
        placeholder="memory&#9;记忆&#9;提示，每行一条"
        @input="clearPreview"
      ></textarea>
    </label>

    <div class="import-actions">
      <button type="button" class="primary-action" :disabled="busy" @click="previewImport">
        生成预览
      </button>
      <button type="button" :disabled="busy" @click="resetImport">取消</button>
    </div>

    <p v-if="error" class="import-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="import-notice" role="status">{{ notice }}</p>

    <div v-if="preview" class="import-preview">
      <dl class="preview-counts">
        <div>
          <dt>可新增</dt>
          <dd>{{ preview.validRows.length }}</dd>
        </div>
        <div>
          <dt>重复</dt>
          <dd>{{ preview.duplicateRows.length }}</dd>
        </div>
        <div>
          <dt>无效</dt>
          <dd>{{ preview.invalidRows.length }}</dd>
        </div>
      </dl>

      <section v-if="preview.invalidRows.length > 0" class="preview-block invalid-block">
        <h4>无效记录</h4>
        <p>存在无效记录，本次不会导入任何行。修正内容后请重新生成预览。</p>
        <ol>
          <li v-for="row in preview.invalidRows" :key="row.recordNumber">
            <strong>第 {{ row.recordNumber }} 条</strong>
            <span>{{ row.message }}</span>
          </li>
        </ol>
      </section>

      <section v-if="preview.validRows.length > 0" class="preview-block">
        <h4>将新增</h4>
        <ol>
          <li v-for="row in preview.validRows" :key="row.recordNumber">
            <strong>{{ row.term }}</strong>
            <span>{{ row.meaning }}</span>
            <small v-if="row.hints.length > 0">提示：{{ row.hints.join(' / ') }}</small>
          </li>
        </ol>
      </section>

      <section v-if="preview.duplicateRows.length > 0" class="preview-block">
        <h4>重复记录</h4>
        <p>默认跳过；选择“作为独立目标”会保留另一份学习目标与复习状态。</p>
        <ol>
          <li v-for="row in preview.duplicateRows" :key="row.recordNumber">
            <div class="duplicate-copy">
              <strong>{{ row.term }} · {{ row.meaning }}</strong>
              <small>{{ sourceLabel(row) }}</small>
            </div>
            <div class="duplicate-choice">
              <label>
                <input
                  v-model="duplicateActions[row.recordNumber]"
                  type="radio"
                  :name="`duplicate-${row.recordNumber}`"
                  value="skip"
                />
                跳过
              </label>
              <label>
                <input
                  v-model="duplicateActions[row.recordNumber]"
                  type="radio"
                  :name="`duplicate-${row.recordNumber}`"
                  value="create"
                />
                作为独立目标
              </label>
            </div>
          </li>
        </ol>
      </section>

      <div class="confirm-import">
        <p>
          确认后将新增 {{ importableCount }} 条学习目标；无效或选择跳过的记录不会写入。
        </p>
        <button type="button" class="primary-action" :disabled="!canImport" @click="confirmImport">
          确认导入
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.tsv-import {
  display: grid;
  gap: 13px;
  margin: 18px 0;
  padding: 18px;
  border: 1px solid #d4ddd7;
  border-radius: 8px;
  background: #f8faf9;
}

.import-heading,
.import-actions,
.confirm-import,
.duplicate-choice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.import-heading p,
.import-heading h3 {
  margin: 0;
}

.import-heading p {
  color: #75847b;
  font-size: 0.7rem;
  font-weight: 700;
}

.import-heading h3 {
  margin-top: 3px;
  color: #253a2e;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 1.35rem;
  font-weight: 500;
}

.import-heading > span {
  color: #8a968f;
  font-size: 0.7rem;
}

label {
  display: grid;
  gap: 5px;
  color: #53655b;
  font-size: 0.75rem;
  font-weight: 700;
}

textarea {
  width: 100%;
  min-height: 132px;
  padding: 10px;
  border: 1px solid #b8c6be;
  border-radius: 5px;
  background: #fff;
  color: #24382d;
  font: 0.82rem/1.6 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  resize: vertical;
}

.file-picker {
  padding: 11px;
  border: 1px dashed #b9c7bf;
  border-radius: 6px;
  background: #fff;
}

button {
  min-height: 36px;
  padding: 7px 11px;
  border: 1px solid #c1cdc6;
  border-radius: 5px;
  background: #fff;
  color: #344b3e;
  font-size: 0.76rem;
  font-weight: 650;
  cursor: pointer;
}

.primary-action {
  border-color: #244f3d;
  background: #244f3d;
  color: #fff;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

button:focus-visible,
textarea:focus-visible,
input:focus-visible {
  outline: 3px solid rgb(45 113 83 / 28%);
  outline-offset: 2px;
}

.import-error,
.import-notice,
.confirm-import p {
  margin: 0;
}

.import-error {
  color: #9a432f;
}

.import-notice {
  color: #246044;
}

.import-preview {
  display: grid;
  gap: 14px;
  padding-top: 14px;
  border-top: 1px solid #dce4df;
}

.preview-counts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
}

.preview-counts div {
  padding: 9px 11px;
  border: 1px solid #dce4df;
  border-radius: 6px;
  background: #fff;
}

.preview-counts dt {
  color: #75847b;
  font-size: 0.68rem;
}

.preview-counts dd {
  margin: 3px 0 0;
  color: #253a2e;
  font-size: 1.2rem;
  font-weight: 700;
}

.preview-block {
  padding: 12px;
  border: 1px solid #dce4df;
  border-radius: 6px;
  background: #fff;
}

.preview-block h4,
.preview-block p {
  margin: 0;
}

.preview-block p {
  margin-top: 4px;
  color: #6d7b73;
  font-size: 0.74rem;
  line-height: 1.55;
}

.preview-block ol {
  display: grid;
  gap: 7px;
  max-height: 280px;
  margin: 10px 0 0;
  padding-left: 22px;
  overflow: auto;
}

.preview-block li {
  padding: 7px 8px;
  color: #53655b;
  font-size: 0.76rem;
}

.preview-block li strong,
.preview-block li span,
.preview-block li small {
  margin-right: 8px;
}

.preview-block li small {
  color: #859188;
}

.invalid-block {
  border-color: #d9b6aa;
  background: #fbf3f0;
}

.duplicate-copy {
  display: grid;
  gap: 3px;
  margin-bottom: 7px;
}

.duplicate-choice {
  justify-content: flex-start;
}

.duplicate-choice label {
  display: flex;
  align-items: center;
  gap: 5px;
  font-weight: 600;
}

.confirm-import {
  padding-top: 5px;
}

.confirm-import p {
  max-width: 54ch;
  color: #68776f;
  font-size: 0.78rem;
  line-height: 1.55;
}

@media (max-width: 620px) {
  .preview-counts {
    grid-template-columns: 1fr;
  }

  .import-actions,
  .confirm-import {
    align-items: stretch;
    flex-direction: column;
  }

  .import-actions button,
  .confirm-import button {
    width: 100%;
  }
}
</style>
