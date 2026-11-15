<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import type { Lexicon } from '../core/domain';
import type {
  FingerMemoryRepository,
  LexiconStudyItem,
} from '../core/repository';
import { StudyItemInUseError } from '../core/repository';
import TsvImportPanel from './TsvImportPanel.vue';

const props = defineProps<{
  repository: FingerMemoryRepository;
}>();

const lexicons = ref<Lexicon[]>([]);
const selectedId = ref<string>();
const items = ref<LexiconStudyItem[]>([]);
const loading = ref(true);
const busy = ref(false);
const error = ref<string | null>(null);
const editingLexicon = ref(false);
const confirmDeleteLexicon = ref(false);
const pendingRemoveId = ref<string>();
const editingStudyItemId = ref<string>();

const newLexicon = reactive({ name: '', description: '' });
const lexiconForm = reactive({ name: '', description: '' });
const wordForm = reactive({
  term: '',
  meaning: '',
  acceptedAnswers: '',
  hints: '',
});

const selectedLexicon = computed(() =>
  lexicons.value.find((lexicon) => lexicon.id === selectedId.value),
);

function splitLines(value: string): string[] {
  return [
    ...new Set(
      value
        .split(/\r?\n|,/)
        .map((item) => item.trim())
        .filter((item) => item.length > 0),
    ),
  ];
}

function resetWordForm(): void {
  editingStudyItemId.value = undefined;
  wordForm.term = '';
  wordForm.meaning = '';
  wordForm.acceptedAnswers = '';
  wordForm.hints = '';
}

async function loadLexicons(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    lexicons.value = await props.repository.listLexicons(true);
    if (
      selectedId.value === undefined ||
      !lexicons.value.some((lexicon) => lexicon.id === selectedId.value)
    ) {
      selectedId.value = lexicons.value[0]?.id;
    }
  } catch {
    error.value = '无法读取词库。';
  } finally {
    loading.value = false;
  }
}

async function loadItems(): Promise<void> {
  if (selectedId.value === undefined) {
    items.value = [];
    return;
  }
  try {
    items.value = await props.repository.listLexiconItems(selectedId.value);
  } catch {
    items.value = [];
    error.value = '无法读取词库内容。';
  }
}

async function createLexicon(): Promise<void> {
  if (newLexicon.name.trim().length === 0) {
    error.value = '词库名不能为空。';
    return;
  }
  busy.value = true;
  error.value = null;
  try {
    const lexicon = await props.repository.createLexicon({
      name: newLexicon.name,
      description: newLexicon.description,
      now: Date.now(),
    });
    newLexicon.name = '';
    newLexicon.description = '';
    await loadLexicons();
    selectedId.value = lexicon.id;
  } catch {
    error.value = '词库创建失败。';
  } finally {
    busy.value = false;
  }
}

function beginEditLexicon(): void {
  if (selectedLexicon.value === undefined) {
    return;
  }
  lexiconForm.name = selectedLexicon.value.name;
  lexiconForm.description = selectedLexicon.value.description;
  editingLexicon.value = true;
}

async function saveLexicon(): Promise<void> {
  if (selectedLexicon.value === undefined || lexiconForm.name.trim().length === 0) {
    error.value = '词库名不能为空。';
    return;
  }
  busy.value = true;
  error.value = null;
  try {
    await props.repository.updateLexicon({
      id: selectedLexicon.value.id,
      name: lexiconForm.name,
      description: lexiconForm.description,
      now: Date.now(),
    });
    editingLexicon.value = false;
    await loadLexicons();
  } catch {
    error.value = '词库更新失败。';
  } finally {
    busy.value = false;
  }
}

async function toggleArchive(): Promise<void> {
  if (selectedLexicon.value === undefined) {
    return;
  }
  busy.value = true;
  error.value = null;
  try {
    await props.repository.setLexiconArchived(
      selectedLexicon.value.id,
      selectedLexicon.value.archivedAt === null,
      Date.now(),
    );
    await loadLexicons();
  } catch {
    error.value = '词库归档状态更新失败。';
  } finally {
    busy.value = false;
  }
}

async function deleteSelectedLexicon(): Promise<void> {
  if (selectedLexicon.value === undefined) {
    return;
  }
  busy.value = true;
  error.value = null;
  try {
    await props.repository.deleteLexicon(selectedLexicon.value.id);
    confirmDeleteLexicon.value = false;
    selectedId.value = undefined;
    await loadLexicons();
  } catch {
    error.value = '词库删除失败，现有数据未改变。';
  } finally {
    busy.value = false;
  }
}

async function saveWord(): Promise<void> {
  if (selectedId.value === undefined) {
    error.value = '请先选择词库。';
    return;
  }
  if (wordForm.term.trim().length === 0 || wordForm.meaning.trim().length === 0) {
    error.value = '英文和中文释义都不能为空。';
    return;
  }

  busy.value = true;
  error.value = null;
  try {
    const payload = {
      lexiconId: selectedId.value,
      term: wordForm.term,
      meaning: wordForm.meaning,
      acceptedAnswers: splitLines(wordForm.acceptedAnswers),
      hints: splitLines(wordForm.hints),
      now: Date.now(),
    };
    if (editingStudyItemId.value === undefined) {
      await props.repository.createStudyItem(payload);
    } else {
      await props.repository.updateStudyItem({
        ...payload,
        studyItemId: editingStudyItemId.value,
      });
    }
    resetWordForm();
    await loadItems();
  } catch {
    error.value = '词条保存失败。';
  } finally {
    busy.value = false;
  }
}

function beginEditWord(entry: LexiconStudyItem): void {
  editingStudyItemId.value = entry.practiceItem.studyItem.id;
  wordForm.term = entry.practiceItem.wordEntry.term;
  wordForm.meaning = entry.practiceItem.wordEntry.meaning;
  wordForm.acceptedAnswers = entry.practiceItem.wordEntry.acceptedAnswers.join('\n');
  wordForm.hints = entry.practiceItem.wordEntry.hints.join('\n');
}

async function removeWord(entry: LexiconStudyItem): Promise<void> {
  if (selectedId.value === undefined) {
    return;
  }
  busy.value = true;
  error.value = null;
  try {
    await props.repository.removeStudyItemFromLexicon(
      selectedId.value,
      entry.practiceItem.studyItem.id,
    );
    pendingRemoveId.value = undefined;
    await loadItems();
  } catch (caughtError) {
    error.value =
      caughtError instanceof StudyItemInUseError
        ? '该学习目标仍被未完成会话引用，请先完成或退出该会话。'
        : '词条移除失败。';
  } finally {
    busy.value = false;
  }
}

async function handleImported(): Promise<void> {
  await loadItems();
}

watch(selectedId, () => {
  editingLexicon.value = false;
  confirmDeleteLexicon.value = false;
  pendingRemoveId.value = undefined;
  resetWordForm();
  void loadItems();
});

onMounted(() => {
  void loadLexicons();
});
</script>

<template>
  <section class="lexicon-manager" aria-labelledby="lexicon-manager-title">
    <div v-if="loading" class="manager-state" role="status">正在读取词库...</div>
    <div v-else class="manager-layout">
      <aside class="lexicon-sidebar" aria-label="词库列表">
        <div class="sidebar-heading">
          <div>
            <p class="manager-kicker">本地词库</p>
            <h2 id="lexicon-manager-title">词库</h2>
          </div>
          <span>{{ lexicons.length }}</span>
        </div>
        <ul class="lexicon-list">
          <li v-for="lexicon in lexicons" :key="lexicon.id">
            <button
              type="button"
              :class="{ selected: lexicon.id === selectedId }"
              :aria-pressed="lexicon.id === selectedId"
              @click="selectedId = lexicon.id"
            >
              <span>{{ lexicon.name }}</span>
              <small>{{ lexicon.archivedAt === null ? '使用中' : '已归档' }}</small>
            </button>
          </li>
        </ul>

        <form class="create-lexicon" @submit.prevent="createLexicon">
          <label for="new-lexicon-name">新词库</label>
          <input
            id="new-lexicon-name"
            v-model="newLexicon.name"
            type="text"
            placeholder="例如：TypeScript 文档"
          />
          <label for="new-lexicon-description">说明（可选）</label>
          <input
            id="new-lexicon-description"
            v-model="newLexicon.description"
            type="text"
            placeholder="说明（可选）"
          />
          <button type="submit" class="primary-action" :disabled="busy">创建词库</button>
        </form>
      </aside>

      <div v-if="selectedLexicon" class="lexicon-content">
        <header class="content-heading">
          <div>
            <p class="manager-kicker">当前词库</p>
            <h2>{{ selectedLexicon.name }}</h2>
            <p>{{ selectedLexicon.description || '暂无说明' }}</p>
          </div>
          <div class="content-actions">
            <button type="button" :disabled="busy" @click="beginEditLexicon">重命名</button>
            <button type="button" :disabled="busy" @click="toggleArchive">
              {{ selectedLexicon.archivedAt === null ? '归档' : '取消归档' }}
            </button>
            <button
              type="button"
              class="danger-action"
              :disabled="busy"
              @click="confirmDeleteLexicon = true"
            >
              删除词库
            </button>
          </div>
        </header>

        <form v-if="editingLexicon" class="edit-lexicon" @submit.prevent="saveLexicon">
          <label for="edit-lexicon-name">词库名</label>
          <input id="edit-lexicon-name" v-model="lexiconForm.name" type="text" />
          <label for="edit-lexicon-description">说明</label>
          <input id="edit-lexicon-description" v-model="lexiconForm.description" type="text" />
          <div class="form-actions">
            <button type="submit" class="primary-action" :disabled="busy">保存词库</button>
            <button type="button" :disabled="busy" @click="editingLexicon = false">取消</button>
          </div>
        </form>

        <section v-if="confirmDeleteLexicon" class="confirm-panel" role="alert">
          <strong>删除“{{ selectedLexicon.name }}”？</strong>
          <p>
            将移除该词库及 {{ items.length }} 个词库成员关系。学习目标、复习历史和日志会保留。
          </p>
          <div class="form-actions">
            <button type="button" class="danger-action" :disabled="busy" @click="deleteSelectedLexicon">
              确认删除
            </button>
            <button type="button" :disabled="busy" @click="confirmDeleteLexicon = false">取消</button>
          </div>
        </section>

        <div class="word-toolbar">
          <div>
            <p class="manager-kicker">词条与学习目标</p>
            <strong>{{ items.length }} 个目标</strong>
          </div>
          <button v-if="editingStudyItemId" type="button" @click="resetWordForm">取消编辑</button>
        </div>

        <details class="import-disclosure">
          <summary>从 TSV 批量导入</summary>
          <TsvImportPanel
            :key="selectedLexicon.id"
            :repository="repository"
            :lexicon-id="selectedLexicon.id"
            :existing-items="items"
            @imported="handleImported"
          />
        </details>

        <form class="word-form" @submit.prevent="saveWord">
          <div class="field-grid">
            <label for="word-term">
              英文拼写
              <input
                id="word-term"
                v-model="wordForm.term"
                type="text"
                autocomplete="off"
                spellcheck="false"
              />
            </label>
            <label for="word-meaning">
              中文释义
              <input id="word-meaning" v-model="wordForm.meaning" type="text" />
            </label>
          </div>
          <label for="word-accepted-answers">
            可接受拼写
            <textarea
              id="word-accepted-answers"
              v-model="wordForm.acceptedAnswers"
              rows="2"
              placeholder="每行一个，可留空"
            ></textarea>
          </label>
          <label for="word-hints">
            提示
            <textarea
              id="word-hints"
              v-model="wordForm.hints"
              rows="2"
              placeholder="每行一个，可留空"
            ></textarea>
          </label>
          <button type="submit" class="primary-action" :disabled="busy">
            {{ editingStudyItemId ? '保存词条' : '添加词条' }}
          </button>
        </form>

        <p v-if="error" class="manager-error" role="alert">{{ error }}</p>

        <ol v-if="items.length > 0" class="word-list">
          <li v-for="entry in items" :key="entry.practiceItem.studyItem.id">
            <div class="word-title">
              <div>
                <strong>{{ entry.practiceItem.wordEntry.term }}</strong>
                <span>{{ entry.practiceItem.wordEntry.meaning }}</span>
              </div>
              <div class="row-actions">
                <button type="button" :disabled="busy" @click="beginEditWord(entry)">编辑</button>
                <button
                  type="button"
                  class="danger-action"
                  :disabled="busy"
                  @click="pendingRemoveId = entry.practiceItem.studyItem.id"
                >
                  移除
                </button>
              </div>
            </div>
            <p v-if="entry.practiceItem.wordEntry.hints.length > 0">
              提示：{{ entry.practiceItem.wordEntry.hints.join(' / ') }}
            </p>
            <div
              v-if="pendingRemoveId === entry.practiceItem.studyItem.id"
              class="remove-confirm"
              role="alert"
            >
              <span>仅从当前词库移除，学习历史和复习日志保留。</span>
              <button type="button" class="danger-action" :disabled="busy" @click="removeWord(entry)">
                确认移除
              </button>
              <button type="button" :disabled="busy" @click="pendingRemoveId = undefined">取消</button>
            </div>
          </li>
        </ol>
        <p v-else class="empty-word-list">当前词库还没有词条。</p>
      </div>
      <div v-else class="no-lexicon">
        <p>还没有词库。先在左侧创建第一个词库。</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.lexicon-manager {
  width: 100%;
  min-width: 0;
}

.manager-layout {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 24px;
}

.lexicon-sidebar,
.lexicon-content,
.no-lexicon {
  border: 1px solid #d0dad4;
  border-radius: 8px;
  background: #fff;
}

.lexicon-sidebar {
  align-self: start;
  padding: 18px;
}

.sidebar-heading,
.content-heading,
.word-toolbar,
.word-title,
.form-actions,
.content-actions,
.row-actions,
.remove-confirm {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.sidebar-heading h2,
.content-heading h2 {
  margin: 0;
  color: #21352a;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 1.8rem;
  font-weight: 500;
}

.manager-kicker {
  margin: 0 0 4px;
  color: #74827a;
  font-size: 0.7rem;
  font-weight: 700;
}

.lexicon-list,
.word-list {
  display: grid;
  gap: 8px;
  margin: 18px 0;
  padding: 0;
  list-style: none;
}

.lexicon-list button {
  display: grid;
  width: 100%;
  gap: 4px;
  text-align: left;
}

.lexicon-list button.selected {
  border-color: #356b54;
  background: #e7f0ea;
}

.lexicon-list small {
  color: #7b8981;
  font-size: 0.68rem;
}

.create-lexicon,
.word-form,
.edit-lexicon {
  display: grid;
  gap: 8px;
}

.create-lexicon {
  padding-top: 16px;
  border-top: 1px solid #e0e6e2;
}

.lexicon-content {
  padding: 22px;
}

.content-heading {
  align-items: flex-start;
  margin-bottom: 18px;
}

.content-heading p:last-child {
  margin: 6px 0 0;
  color: #6d7b73;
}

.content-actions,
.row-actions,
.form-actions {
  flex-wrap: wrap;
}

.edit-lexicon,
.confirm-panel {
  margin-bottom: 18px;
  padding: 14px;
  border: 1px solid #d4ddd7;
  border-radius: 6px;
  background: #f7f9f8;
}

.confirm-panel {
  border-color: #d9b6aa;
  background: #fbf3f0;
}

.confirm-panel p {
  color: #745d54;
  line-height: 1.6;
}

.word-toolbar {
  margin: 24px 0 10px;
}

.word-form {
  padding: 16px;
  border: 1px solid #d4ddd7;
  border-radius: 6px;
  background: #f7f9f8;
}

.import-disclosure {
  margin-bottom: 16px;
  border: 1px solid #d4ddd7;
  border-radius: 6px;
  background: #fff;
}

.import-disclosure summary {
  padding: 12px 14px;
  color: #355044;
  font-size: 0.82rem;
  font-weight: 700;
  cursor: pointer;
}

.import-disclosure[open] summary {
  border-bottom: 1px solid #e0e6e2;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

label {
  display: grid;
  gap: 5px;
  color: #53655b;
  font-size: 0.75rem;
  font-weight: 700;
}

input,
textarea {
  width: 100%;
  padding: 9px 10px;
  border: 1px solid #b8c6be;
  border-radius: 5px;
  color: #24382d;
  font: inherit;
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

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

button:focus-visible,
input:focus-visible,
textarea:focus-visible {
  outline: 3px solid rgb(45 113 83 / 28%);
  outline-offset: 2px;
}

.primary-action {
  border-color: #244f3d;
  background: #244f3d;
  color: #fff;
}

.danger-action {
  color: #a14a34;
  border-color: #dfc2b9;
}

.manager-error {
  margin: 12px 0 0;
  color: #9a432f;
}

.word-list li {
  padding: 12px 14px;
  border: 1px solid #dbe2de;
  border-radius: 6px;
}

.word-title strong {
  color: #263b2f;
  font-size: 1rem;
}

.word-title span {
  margin-left: 10px;
  color: #6f7d75;
}

.word-list li > p {
  margin: 8px 0 0;
  color: #7b8780;
  font-size: 0.75rem;
}

.remove-confirm {
  justify-content: flex-start;
  flex-wrap: wrap;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #e4d6d1;
  color: #80584c;
  font-size: 0.75rem;
}

.empty-word-list,
.no-lexicon {
  padding: 24px;
  color: #6f7d75;
}

.manager-state {
  padding: 32px;
  color: #6f7d75;
}

@media (max-width: 900px) {
  .manager-layout {
    grid-template-columns: 1fr;
  }

  .content-heading {
    flex-direction: column;
  }

  .field-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .lexicon-content,
  .lexicon-sidebar {
    padding: 15px;
  }

  .word-title {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
