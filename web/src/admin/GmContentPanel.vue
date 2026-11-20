<script setup lang="ts">
import {
  computed,
  onMounted,
  ref,
} from 'vue';
import {
  Archive,
  CheckCircle2,
  Eye,
  FileArchive,
  RefreshCw,
  Send,
  ShieldCheck,
  Upload,
  X,
} from '@lucide/vue';
import {
  accountApi,
  ApiClientError,
  type AccountClient,
  type GmContent,
  type GmContentNode,
  type GmContentPreview,
} from '../api/client';

const props = withDefaults(
  defineProps<{
    api?: AccountClient;
  }>(),
  {
    api: () => accountApi,
  },
);

const emit = defineEmits<{
  changed: [];
}>();

const items = ref<GmContent[]>([]);
const selectedFile = ref<File>();
const reason = ref('Validate and publish managed adventure content');
const loading = ref(true);
const busy = ref(false);
const error = ref<string | null>(null);
const notice = ref<string | null>(null);
const fileInput = ref<HTMLInputElement>();
const preview = ref<GmContentPreview>();
const selectedNodeId = ref('');
const previewLoading = ref(false);

const previewNodes = computed(
  () => preview.value?.chapters.flatMap((chapter) => chapter.nodes) ?? [],
);
const selectedNode = computed(
  () => previewNodes.value.find((node) => node.nodeId === selectedNodeId.value),
);
const selectedScene = computed(() => {
  const sceneId = selectedNode.value?.sceneId;
  return preview.value?.scenes.find((scene) => scene.sceneId === sceneId);
});
const selectedCharacter = computed(() => {
  const characterKey = selectedNode.value?.portraitCharacterId;
  return preview.value?.characters.find(
    (character) => character.characterKey === characterKey,
  );
});
const selectedPortrait = computed(() => {
  const character = selectedCharacter.value;
  if (character === undefined) {
    return undefined;
  }
  const costumeId = selectedNode.value?.costumeId ?? character.defaultCostumeId;
  const portraits = character.costumes[costumeId];
  return portraits?.welcome
    ?? portraits?.neutral
    ?? Object.values(portraits ?? {})[0];
});
const sceneStyle = computed(() => ({
  backgroundImage: selectedScene.value === undefined
    ? undefined
    : `url(${selectedScene.value.background})`,
}));

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
  return '内容管理请求失败，请稍后重试。';
}

function statusLabel(content: GmContent): string {
  switch (content.status) {
    case 'draft':
      return '沙盒草稿';
    case 'validated':
      return '校验通过';
    case 'published':
      return '已发布';
    case 'archived':
      return '已归档';
  }
}

function selectFile(event: Event): void {
  selectedFile.value = (event.target as HTMLInputElement).files?.[0];
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    items.value = (await props.api.listGmContent(100)).items;
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}

async function upload(): Promise<void> {
  const file = selectedFile.value;
  if (file === undefined || busy.value) {
    return;
  }
  busy.value = true;
  error.value = null;
  notice.value = null;
  try {
    const content = await props.api.uploadGmContent(
      file,
      newId(),
      reason.value,
    );
    selectedFile.value = undefined;
    if (fileInput.value !== undefined) {
      fileInput.value.value = '';
    }
    notice.value = `已保存沙盒草稿：${content.title} v${content.contentVersion}`;
    await load();
    emit('changed');
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function loadPreview(content: GmContent): Promise<void> {
  previewLoading.value = true;
  error.value = null;
  try {
    const nextPreview = await props.api.getGmContentPreview(content.id);
    preview.value = nextPreview;
    selectedNodeId.value = nextPreview.chapters[0]?.nodes[0]?.nodeId ?? '';
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    previewLoading.value = false;
  }
}

function closePreview(): void {
  preview.value = undefined;
  selectedNodeId.value = '';
}

function nodeMeta(node: GmContentNode): string {
  const status = node.type === 'chapter-end'
    ? node.endingTitle ?? node.endingId
    : node.encounterName ?? node.title;
  return `${node.chapterId} · ${status}`;
}

async function act(
  content: GmContent,
  action: 'validate' | 'publish' | 'archive',
): Promise<void> {
  if (busy.value) {
    return;
  }
  busy.value = true;
  error.value = null;
  notice.value = null;
  try {
    const result = await props.api.actGmContent(content.id, newId(), {
      action,
      expectedContentHash: content.contentHash,
      reason: reason.value,
    });
    notice.value = action === 'validate'
      ? `${result.title} 已通过同一内容校验器。`
      : action === 'publish'
        ? `${result.title} v${result.contentVersion} 已发布。`
        : `${result.title} v${result.contentVersion} 已归档，现有存档保留。`;
    await load();
    if (action === 'validate') {
      await loadPreview(result);
    } else if (preview.value?.content.id === result.id) {
      preview.value = {
        ...preview.value,
        content: result,
      };
    }
    emit('changed');
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="gm-content" aria-labelledby="gm-content-title" data-testid="gm-content-panel">
    <div class="content-heading">
      <div>
        <p>内容发布</p>
        <h2 id="gm-content-title">冒险内容与不可变版本</h2>
      </div>
      <button type="button" class="icon-command" :disabled="busy" @click="load">
        <RefreshCw :size="17" />
        <span class="sr-only">刷新内容列表</span>
      </button>
    </div>

    <p v-if="error" class="content-message content-message--error" role="alert">
      {{ error }}
    </p>
    <p v-if="notice" class="content-message" role="status">
      <CheckCircle2 :size="16" aria-hidden="true" />
      {{ notice }}
    </p>

    <form class="content-upload" @submit.prevent="upload">
      <input
        ref="fileInput"
        class="file-input"
        type="file"
        accept=".fmbook,application/zip"
        aria-label="选择 GM 内容包"
        @change="selectFile"
      />
      <button type="button" :disabled="busy" @click="fileInput?.click()">
        <FileArchive :size="17" />
        {{ selectedFile?.name ?? '选择 .fmbook' }}
      </button>
      <label>
        <span>操作理由</span>
        <input v-model="reason" maxlength="300" required />
      </label>
      <button type="submit" class="primary-command" :disabled="busy || selectedFile === undefined">
        <Upload :size="17" />
        保存沙盒草稿
      </button>
    </form>

    <p v-if="loading" class="content-state" role="status">正在读取管理内容...</p>
    <div v-else class="content-table-wrap">
      <table>
        <thead>
          <tr>
            <th>内容</th>
            <th>状态</th>
            <th>规模</th>
            <th>版本锁</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="content in items" :key="content.id">
            <td>
              <strong>{{ content.title }}</strong>
              <span>{{ content.bookId }} · v{{ content.contentVersion }}</span>
              <code>{{ content.contentHash.slice(0, 12) }}</code>
            </td>
            <td>
              <span class="content-status" :class="`content-status--${content.status}`">
                {{ statusLabel(content) }}
              </span>
              <small v-if="content.issues.length > 0">
                {{ content.issues.join('；') }}
              </small>
            </td>
            <td>
              {{ content.wordCount }} 词 · {{ content.nodeCount }} 节点
              <small>{{ content.characterCount }} 角色 · {{ content.assetCount }} 媒体</small>
            </td>
            <td>#{{ content.version }}</td>
            <td class="content-actions">
              <button
                v-if="content.status === 'draft'"
                type="button"
                :disabled="busy"
                @click="act(content, 'validate')"
              >
                <CheckCircle2 :size="15" />
                校验预览
              </button>
              <button
                v-if="content.status !== 'draft'"
                type="button"
                :disabled="busy || previewLoading"
                @click="loadPreview(content)"
              >
                <Eye :size="15" />
                沙盒预览
              </button>
              <button
                v-if="content.status === 'validated'"
                type="button"
                :disabled="busy"
                @click="act(content, 'publish')"
              >
                <Send :size="15" />
                发布
              </button>
              <button
                v-if="content.status === 'published'"
                type="button"
                :disabled="busy"
                @click="act(content, 'archive')"
              >
                <Archive :size="15" />
                归档
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <section
      v-if="preview"
      class="content-preview"
      aria-labelledby="gm-content-preview-title"
      data-testid="gm-content-preview"
    >
      <header class="preview-heading">
        <div>
          <p>
            <ShieldCheck :size="15" aria-hidden="true" />
            TEST · GM SANDBOX PREVIEW
          </p>
          <h3 id="gm-content-preview-title">
            {{ preview.content.title }} · v{{ preview.content.contentVersion }}
          </h3>
        </div>
        <button type="button" class="icon-command" aria-label="关闭预览" @click="closePreview">
          <X :size="17" />
        </button>
      </header>

      <p class="preview-scope">
        {{ preview.content.namespace }}:{{ preview.content.bookId }}
        · {{ preview.content.status }}
        · {{ preview.content.contentHash.slice(0, 12) }}
      </p>

      <label class="preview-selector">
        <span>剧情节点</span>
        <select v-model="selectedNodeId">
          <option
            v-for="node in previewNodes"
            :key="node.nodeId"
            :value="node.nodeId"
          >
            {{ node.chapterId }} · {{ node.nodeId }} · {{ nodeMeta(node) }}
          </option>
        </select>
      </label>

      <div v-if="selectedNode" class="preview-stage" :style="sceneStyle">
        <img
          v-if="selectedPortrait"
          class="preview-portrait"
          :src="selectedPortrait"
          :alt="selectedCharacter?.name ?? selectedNode.portraitCharacterId"
        />
        <article class="preview-dialogue">
          <p>{{ selectedNode.chapterId }} · {{ selectedNode.type }}</p>
          <h4>{{ selectedNode.title }}</h4>
          <strong v-if="selectedNode.speaker">{{ selectedNode.speaker }}</strong>
          <blockquote>{{ selectedNode.text }}</blockquote>
          <ul v-if="selectedNode.choices.length > 0">
            <li v-for="choice in selectedNode.choices" :key="choice.choiceId">
              {{ choice.label }}
              <code>{{ choice.targetNodeId }}</code>
            </li>
          </ul>
          <p v-if="selectedNode.endingTitle" class="preview-ending">
            结局：{{ selectedNode.endingTitle }}
            <code>{{ selectedNode.endingId }}</code>
          </p>
          <p v-if="selectedNode.encounterName" class="preview-encounter">
            遭遇：{{ selectedNode.encounterName }}
            <span>{{ selectedNode.encounterDescription }}</span>
          </p>
        </article>
      </div>

      <section
        v-if="preview.characters.length > 0"
        class="preview-section"
        aria-labelledby="gm-preview-characters"
      >
        <div class="preview-section-heading">
          <h4 id="gm-preview-characters">角色、服装与对白</h4>
          <span>{{ preview.characters.length }} 个定义</span>
        </div>
        <article
          v-for="character in preview.characters"
          :key="character.characterKey"
          class="character-review"
        >
          <header>
            <div>
              <strong>{{ character.name }}</strong>
              <span>{{ character.epithet }} · {{ character.characterKey }}</span>
            </div>
            <span v-if="character.age > 0">{{ character.age }} 岁</span>
          </header>
          <p>{{ character.description }}</p>
          <div class="costume-review">
            <section
              v-for="(portraits, costumeId) in character.costumes"
              :key="costumeId"
            >
              <h5>{{ costumeId }}</h5>
              <ul class="portrait-grid">
                <li v-for="(url, expression) in portraits" :key="expression">
                  <img :src="url" :alt="`${character.name} ${expression}`" loading="lazy" />
                  <span>{{ expression }}</span>
                </li>
              </ul>
            </section>
          </div>
          <dl class="dialogue-review">
            <div><dt>欢迎</dt><dd>{{ character.dialogue.welcome || '—' }}</dd></div>
            <div><dt>Again</dt><dd>{{ character.dialogue.again || '—' }}</dd></div>
            <div><dt>休息</dt><dd>{{ character.dialogue.rest || '—' }}</dd></div>
            <div><dt>练习</dt><dd>{{ character.dialogue.training || '—' }}</dd></div>
          </dl>
          <p v-if="character.skill" class="preview-skill">
            技能：{{ character.skill.name }} · {{ character.skill.type }}
            {{ character.skill.amount }} — {{ character.skill.description }}
          </p>
        </article>
      </section>

      <section
        v-if="preview.scenes.length > 0"
        class="preview-section"
        aria-labelledby="gm-preview-scenes"
      >
        <div class="preview-section-heading">
          <h4 id="gm-preview-scenes">场景与资源</h4>
          <span>{{ preview.assets.length }} 个资源</span>
        </div>
        <ul class="scene-review">
          <li v-for="scene in preview.scenes" :key="scene.sceneId">
            <img :src="scene.background" :alt="scene.name" loading="lazy" />
            <div>
              <strong>{{ scene.name }}</strong>
              <span>{{ scene.sceneId }} · {{ scene.battleTheme }}</span>
            </div>
          </li>
        </ul>
      </section>
    </section>
  </section>
</template>

<style scoped>
.gm-content {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid #d7ddd7;
  border-radius: 8px;
  background: #fffdf8;
}

.content-heading,
.content-upload,
.content-message {
  display: flex;
  align-items: center;
  gap: 10px;
}

.content-heading {
  justify-content: space-between;
}

.content-heading p {
  margin: 0 0 3px;
  color: #6c786e;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.content-heading h2 {
  margin: 0;
  color: #24372e;
  font-size: 1.15rem;
}

.content-message {
  margin: 0;
  padding: 10px 12px;
  background: #edf4ed;
  color: #315443;
}

.content-message--error {
  background: #f8e9e2;
  color: #7d3f2d;
}

.content-upload {
  flex-wrap: wrap;
  align-items: end;
  padding: 12px;
  background: #f3f1e9;
}

.content-upload label {
  display: grid;
  flex: 1;
  min-width: 220px;
  gap: 5px;
  color: #68736b;
  font-size: 0.74rem;
}

.content-upload input {
  min-height: 38px;
  border: 1px solid #aeb9b0;
  border-radius: 4px;
  padding: 7px 9px;
  background: #fff;
}

.content-upload button,
.content-actions button,
.icon-command {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px solid #9caa9f;
  border-radius: 4px;
  padding: 6px 10px;
  background: #fff;
  color: #304b3d;
  cursor: pointer;
}

.file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}

.content-table-wrap {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 10px;
  border-bottom: 1px solid #e4e7e1;
  text-align: left;
  vertical-align: top;
}

th {
  color: #66736a;
  font-size: 0.7rem;
}

td {
  color: #35483e;
  font-size: 0.78rem;
}

td strong,
td span,
td small,
td code {
  display: block;
}

td small,
td code {
  margin-top: 3px;
  color: #748078;
  font-size: 0.66rem;
}

.content-status {
  display: inline-flex;
  border-radius: 999px;
  padding: 3px 8px;
  background: #e8ece7;
  font-size: 0.68rem;
  font-weight: 700;
}

.content-status--validated {
  background: #e3efe5;
  color: #2e6747;
}

.content-status--published {
  background: #dcece6;
  color: #1f634d;
}

.content-status--archived {
  background: #ece8e2;
  color: #6d655a;
}

.content-actions {
  white-space: nowrap;
}

.content-preview {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 2px solid #b9863f;
  background: #f7f2e7;
}

.preview-heading,
.preview-section-heading,
.character-review header,
.scene-review li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.preview-heading p,
.preview-heading h3,
.preview-scope,
.preview-stage p,
.preview-stage h4,
.preview-stage blockquote,
.preview-section h4,
.preview-section p,
.preview-section h5,
.character-review header span,
.scene-review span {
  margin: 0;
}

.preview-heading p {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #8a5d24;
  font-size: 0.68rem;
  font-weight: 800;
}

.preview-heading h3 {
  margin-top: 4px;
  color: #24372e;
  font-size: 1.05rem;
}

.preview-scope {
  padding: 7px 10px;
  border-left: 3px solid #b9863f;
  background: #efe4ce;
  color: #5f5749;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.7rem;
}

.preview-selector {
  display: grid;
  gap: 5px;
  color: #68736b;
  font-size: 0.74rem;
  font-weight: 700;
}

.preview-selector select {
  min-height: 40px;
  border: 1px solid #aeb9b0;
  border-radius: 4px;
  padding: 7px 9px;
  background: #fff;
}

.preview-stage {
  position: relative;
  display: grid;
  min-height: 310px;
  align-items: end;
  overflow: hidden;
  border: 1px solid #7f8f82;
  background:
    linear-gradient(180deg, rgb(31 49 42 / 12%), rgb(20 32 27 / 88%)),
    #31473b center / cover no-repeat;
}

.preview-portrait {
  position: absolute;
  right: clamp(12px, 6vw, 80px);
  bottom: 0;
  width: min(34%, 260px);
  max-height: 92%;
  object-fit: contain;
  object-position: bottom;
}

.preview-dialogue {
  position: relative;
  z-index: 1;
  width: min(66%, 650px);
  margin: 18px;
  padding: 16px;
  background: rgb(255 253 248 / 94%);
  color: #304238;
}

.preview-dialogue > p:first-child {
  color: #8a5d24;
  font-size: 0.66rem;
  font-weight: 800;
  text-transform: uppercase;
}

.preview-dialogue h4 {
  margin: 4px 0 10px;
  font-size: 1rem;
}

.preview-dialogue strong {
  color: #8a5d24;
  font-size: 0.76rem;
}

.preview-dialogue blockquote {
  margin-top: 7px;
  line-height: 1.65;
}

.preview-dialogue ul {
  display: grid;
  gap: 5px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.preview-dialogue li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-top: 5px;
  border-top: 1px solid #e3ddcf;
}

.preview-dialogue code {
  color: #6c756e;
  font-size: 0.67rem;
}

.preview-ending,
.preview-encounter {
  margin-top: 10px !important;
  color: #62472b;
  font-size: 0.74rem;
  font-weight: 700;
}

.preview-encounter span {
  display: block;
  margin-top: 3px;
  font-weight: 400;
}

.preview-section {
  display: grid;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid #d8ccb8;
}

.preview-section-heading {
  color: #43594c;
}

.preview-section-heading h4 {
  font-size: 0.92rem;
}

.preview-section-heading span,
.character-review header span,
.scene-review span {
  color: #6e786f;
  font-size: 0.68rem;
}

.character-review {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid #d3c6b2;
  background: #fffdf8;
}

.character-review header strong,
.character-review header span,
.scene-review strong,
.scene-review span {
  display: block;
}

.character-review > p {
  color: #536158;
  font-size: 0.78rem;
  line-height: 1.55;
}

.costume-review {
  display: grid;
  gap: 10px;
}

.costume-review h5 {
  color: #315443;
  font-size: 0.75rem;
}

.portrait-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(80px, 1fr));
  gap: 7px;
  margin: 7px 0 0;
  padding: 0;
  list-style: none;
}

.portrait-grid li {
  overflow: hidden;
  border: 1px solid #d8d2c5;
  background: #e7ebe4;
}

.portrait-grid img {
  display: block;
  width: 100%;
  aspect-ratio: 3 / 4;
  object-fit: cover;
  object-position: top;
}

.portrait-grid span {
  display: block;
  padding: 5px;
  color: #667268;
  font-size: 0.63rem;
  text-align: center;
}

.dialogue-review {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 12px;
  margin: 0;
}

.dialogue-review div {
  padding-top: 6px;
  border-top: 1px solid #eee8dc;
}

.dialogue-review dt {
  color: #8a5d24;
  font-size: 0.66rem;
  font-weight: 800;
}

.dialogue-review dd {
  margin: 3px 0 0;
  color: #4d5b53;
  font-size: 0.72rem;
}

.preview-skill {
  color: #315443;
  font-size: 0.74rem;
}

.scene-review {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 9px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.scene-review li {
  align-items: stretch;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #d3c6b2;
  background: #fffdf8;
}

.scene-review img {
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
}

.scene-review div {
  padding: 8px;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}

@media (max-width: 760px) {
  .content-heading,
  .content-upload {
    align-items: stretch;
    flex-direction: column;
  }

  .content-upload label {
    min-width: 0;
  }

  .preview-stage {
    min-height: 420px;
  }

  .preview-portrait {
    right: 50%;
    width: min(62%, 220px);
    transform: translateX(50%);
  }

  .preview-dialogue {
    width: auto;
    margin-top: 220px;
  }

  .portrait-grid {
    grid-template-columns: repeat(3, minmax(70px, 1fr));
  }

  .dialogue-review,
  .scene-review {
    grid-template-columns: 1fr;
  }
}
</style>
