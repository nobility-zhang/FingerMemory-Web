<script setup lang="ts">
import { onMounted, ref } from 'vue';
import {
  AlertTriangle,
  BookOpen,
  CheckCircle2,
  FileArchive,
  Upload,
} from '@lucide/vue';
import {
  accountApi,
  ApiClientError,
  type AccountClient,
  type ContentPackageInstallation,
  type ContentPackagePreview,
} from '../api/client';

const props = withDefaults(
  defineProps<{
    api?: AccountClient;
  }>(),
  {
    api: () => accountApi,
  },
);

const installations = ref<ContentPackageInstallation[]>([]);
const selectedFile = ref<File>();
const preview = ref<ContentPackagePreview>();
const loading = ref(true);
const busy = ref(false);
const error = ref<string | null>(null);
const notice = ref<string | null>(null);
const fileInput = ref<HTMLInputElement>();

function newId(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function errorMessage(cause: unknown): string {
  if (cause instanceof ApiClientError) {
    return cause.message;
  }
  if (cause instanceof Error && cause.message.trim().length > 0) {
    return cause.message;
  }
  return '内容包操作失败，请稍后重试。';
}

function formatDate(timestamp: number): string {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(timestamp));
}

async function loadInstallations(): Promise<void> {
  installations.value = await props.api.listContentPackages();
}

async function refresh(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    await loadInstallations();
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}

async function chooseFile(event: Event): Promise<void> {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (file === undefined) {
    return;
  }
  selectedFile.value = file;
  preview.value = undefined;
  error.value = null;
  notice.value = null;
  busy.value = true;
  try {
    preview.value = await props.api.previewContentPackage(file);
  } catch (cause) {
    selectedFile.value = undefined;
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function install(): Promise<void> {
  const file = selectedFile.value;
  if (file === undefined || preview.value === undefined || busy.value) {
    return;
  }
  busy.value = true;
  error.value = null;
  notice.value = null;
  try {
    const result = await props.api.installContentPackage(file, newId());
    notice.value = result.outcome === 'duplicate'
      ? `内容包 ${result.installation.contentVersion} 已安装，无需重复写入。`
      : `内容包 ${result.installation.contentVersion} 已安装。`;
    selectedFile.value = undefined;
    preview.value = undefined;
    if (fileInput.value !== undefined) {
      fileInput.value.value = '';
    }
    await loadInstallations();
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

function clearSelection(): void {
  selectedFile.value = undefined;
  preview.value = undefined;
  if (fileInput.value !== undefined) {
    fileInput.value.value = '';
  }
}

onMounted(() => {
  void refresh();
});
</script>

<template>
  <section class="content-library" aria-labelledby="content-library-title">
    <div class="content-heading">
      <div>
        <p class="section-label">冒险书</p>
        <h3 id="content-library-title">词库内容包</h3>
      </div>
      <span>{{ installations.length }} 个安装</span>
    </div>

    <p class="content-intro">
      先预览校验结果，再安装不可变的 `.fmbook` 版本。词条、章节、角色、卡牌、遭遇和媒体会一起校验。
    </p>

    <div class="content-actions">
      <input
        ref="fileInput"
        class="file-input"
        type="file"
        accept=".fmbook,application/zip"
        aria-label="选择 .fmbook 内容包"
        @change="chooseFile"
      />
      <button
        type="button"
        class="primary-action"
        :disabled="busy"
        @click="fileInput?.click()"
      >
        <Upload :size="17" aria-hidden="true" />
        选择内容包
      </button>
      <button type="button" :disabled="busy" @click="refresh">
        刷新安装
      </button>
    </div>

    <p v-if="loading" class="content-state" role="status">正在读取已安装内容...</p>
    <p v-if="error" class="content-message content-message--error" role="alert">
      <AlertTriangle :size="17" aria-hidden="true" />
      {{ error }}
    </p>
    <p v-if="notice" class="content-message" role="status">
      <CheckCircle2 :size="17" aria-hidden="true" />
      {{ notice }}
    </p>

    <section v-if="preview" class="content-preview" aria-labelledby="content-preview-title">
      <header>
        <FileArchive :size="22" aria-hidden="true" />
        <div>
          <p class="section-label">安装预览</p>
          <h4 id="content-preview-title">{{ preview.title }}</h4>
        </div>
      </header>
      <dl>
        <div><dt>身份</dt><dd>{{ preview.namespace }}:{{ preview.bookId }}</dd></div>
        <div><dt>系列</dt><dd>{{ preview.seriesId }}</dd></div>
        <div><dt>版本</dt><dd>{{ preview.contentVersion }}</dd></div>
        <div>
          <dt>前作要求</dt>
          <dd>
            {{ preview.requirements.length > 0
              ? `${preview.requirements.length} 条结局条件`
              : '无' }}
          </dd>
        </div>
        <div><dt>词条</dt><dd>{{ preview.wordCount }}</dd></div>
        <div><dt>章节 / 节点</dt><dd>{{ preview.chapterCount }} / {{ preview.nodeCount }}</dd></div>
        <div><dt>角色 / 卡牌</dt><dd>{{ preview.characterCount }} / {{ preview.cardCount }}</dd></div>
        <div><dt>遭遇 / 媒体</dt><dd>{{ preview.encounterCount }} / {{ preview.assetCount }}</dd></div>
      </dl>
      <code>{{ preview.contentHash }}</code>
      <div class="preview-actions">
        <button type="button" class="primary-action" :disabled="busy" @click="install">
          确认安装
        </button>
        <button type="button" :disabled="busy" @click="clearSelection">
          取消
        </button>
      </div>
    </section>

    <p v-if="!loading && installations.length === 0" class="content-state">
      当前账号还没有安装冒险内容包。
    </p>
    <ul v-else class="installation-list">
      <li v-for="installation in installations" :key="installation.id">
        <div class="installation-icon" aria-hidden="true">
          <BookOpen :size="20" />
        </div>
        <div>
          <strong>{{ installation.title }}</strong>
          <span>
            {{ installation.namespace }}:{{ installation.bookId }}
            · v{{ installation.contentVersion }}
          </span>
          <span v-if="installation.requirements.length > 0">
            需要前作结局 {{ installation.requirements.length }} 条
          </span>
          <small>
            {{ installation.wordCount }} 词 · {{ installation.chapterCount }} 章
            · {{ installation.assetCount }} 媒体
          </small>
        </div>
        <time :datetime="new Date(installation.installedAtMs).toISOString()">
          {{ formatDate(installation.installedAtMs) }}
        </time>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.content-library {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid rgb(102 128 108 / 28%);
  border-radius: 8px;
  background: #f4f1e8;
}

.content-heading,
.content-actions,
.content-preview header,
.preview-actions,
.installation-list li {
  display: flex;
  align-items: center;
  gap: 12px;
}

.content-heading,
.content-actions {
  justify-content: space-between;
}

.content-heading h3,
.content-preview h4 {
  margin: 0;
  color: #263d34;
}

.content-heading > span {
  color: #64746b;
  font-size: 0.78rem;
}

.section-label {
  margin: 0 0 3px;
  color: #6a775f;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.content-intro,
.content-state {
  margin: 0;
  color: #66756c;
  font-size: 0.82rem;
  line-height: 1.6;
}

.file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}

.content-actions {
  justify-content: flex-start;
  flex-wrap: wrap;
}

.content-actions button,
.preview-actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 40px;
  padding: 8px 13px;
  border: 1px solid #6f836f;
  border-radius: 4px;
  background: transparent;
  color: #304b3d;
  cursor: pointer;
}

.content-actions .primary-action,
.preview-actions .primary-action {
  border-color: #315e4d;
  background: #315e4d;
  color: #fffaf0;
}

.content-message {
  display: flex;
  align-items: start;
  gap: 8px;
  margin: 0;
  padding: 10px 12px;
  border-left: 3px solid #4f7963;
  background: #e8efe8;
  color: #315443;
  font-size: 0.8rem;
}

.content-message--error {
  border-left-color: #ad5d43;
  background: #f6e7e0;
  color: #793f2d;
}

.content-preview {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgb(69 103 80 / 25%);
  border-radius: 6px;
  background: #fffdf7;
}

.content-preview header {
  align-items: start;
}

.content-preview dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
}

.content-preview dl > div {
  display: grid;
  gap: 3px;
  padding: 8px;
  background: #edf1e9;
}

dt {
  color: #728077;
  font-size: 0.68rem;
}

dd {
  margin: 0;
  color: #29443a;
  font-size: 0.82rem;
  overflow-wrap: anywhere;
}

.content-preview code {
  padding: 8px 10px;
  overflow: auto;
  background: #26352f;
  color: #dcebd9;
  font-size: 0.7rem;
}

.preview-actions {
  flex-wrap: wrap;
}

.installation-list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.installation-list li {
  min-width: 0;
  padding: 12px;
  border: 1px solid rgb(75 104 83 / 22%);
  background: #fffdf8;
}

.installation-list li > div:nth-child(2) {
  display: grid;
  flex: 1;
  min-width: 0;
  gap: 3px;
}

.installation-list strong,
.installation-list span,
.installation-list small {
  overflow-wrap: anywhere;
}

.installation-list span,
.installation-list small,
.installation-list time {
  color: #6b776f;
  font-size: 0.74rem;
}

.installation-icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  background: #dfe9df;
  color: #3d6b57;
}

button:focus-visible,
.file-input:focus-visible + button {
  outline: 3px solid rgb(49 94 77 / 42%);
  outline-offset: 2px;
}

@media (max-width: 720px) {
  .content-heading,
  .installation-list li {
    align-items: flex-start;
    flex-direction: column;
  }

  .content-preview dl {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 480px) {
  .content-library {
    padding: 14px;
  }

  .content-preview dl {
    grid-template-columns: 1fr;
  }

  .content-actions button,
  .preview-actions button {
    width: 100%;
  }
}
</style>
