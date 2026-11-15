<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import type {
  PracticeItem,
  PracticeModeId,
  PracticeSession,
} from '../core/domain';
import type { FingerMemoryRepository } from '../core/repository';
import SessionWorkbench from '../modes/SessionWorkbench.vue';
import { createPracticeSession } from '../session/practiceSession';

const props = defineProps<{
  repository: FingerMemoryRepository;
}>();

const items = ref<PracticeItem[]>([]);
const latestSession = ref<PracticeSession>();
const latestSessionItems = ref<PracticeItem[]>([]);
const activeSession = ref<PracticeSession>();
const loading = ref(true);
const busy = ref(false);
const error = ref<string | null>(null);

const resumableSession = computed(() => {
  const session = latestSession.value;
  if (session === undefined || session.status === 'completed') {
    return undefined;
  }
  const availableIds = new Set(
    latestSessionItems.value.map((item) => item.studyItem.id),
  );
  return session.queue.every((item) => availableIds.has(item.studyItemId))
    ? session
    : undefined;
});
const workbenchItems = computed(() => {
  const merged = new Map<string, PracticeItem>();
  for (const item of [...items.value, ...latestSessionItems.value]) {
    merged.set(item.studyItem.id, item);
  }
  return [...merged.values()];
});

function preferredReducedMotion(): boolean {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
}

async function loadWorkspace(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    const [practiceItems, session] = await Promise.all([
      props.repository.listPracticeItems(100),
      props.repository.getLatestPracticeSession(),
    ]);
    items.value = practiceItems;
    latestSession.value = session;
    latestSessionItems.value =
      session === undefined || session.status === 'completed'
        ? []
        : await props.repository.listPracticeItemsByStudyItemIds(
            session.queue.map((item) => item.studyItemId),
          );
  } catch {
    error.value = '无法读取本地练习数据。';
  } finally {
    loading.value = false;
  }
}

async function loadStarterContent(): Promise<void> {
  busy.value = true;
  error.value = null;
  try {
    items.value = await props.repository.ensureStarterContent();
  } catch {
    error.value = '示例词库未能保存。';
  } finally {
    busy.value = false;
  }
}

async function startSession(modeId: PracticeModeId = 'lyrics-scroll'): Promise<void> {
  if (items.value.length === 0) {
    return;
  }

  busy.value = true;
  error.value = null;
  try {
    const now = Date.now();
    const session = createPracticeSession({
      id: crypto.randomUUID(),
      modeId,
      items: items.value,
      now,
      reduceMotion: preferredReducedMotion(),
    });
    const saved = await props.repository.savePracticeSession(session);
    latestSession.value = saved;
    latestSessionItems.value = items.value;
    activeSession.value = saved;
  } catch {
    error.value = '无法创建练习会话。';
  } finally {
    busy.value = false;
  }
}

function continueSession(): void {
  activeSession.value = resumableSession.value;
}

function handleSessionUpdated(session: PracticeSession): void {
  latestSession.value = session;
}

function handleSessionExit(session: PracticeSession): void {
  latestSession.value = session;
  activeSession.value = undefined;
}

function startAnotherSession(): void {
  activeSession.value = undefined;
  void startSession('lyrics-scroll');
}

onMounted(() => {
  void loadWorkspace();
});
</script>

<template>
  <div v-if="loading" class="workspace-state" role="status">正在读取练习数据...</div>
  <div v-else-if="error" class="workspace-state error" role="alert">
    <p>{{ error }}</p>
    <button type="button" @click="loadWorkspace">重试</button>
  </div>
  <SessionWorkbench
    v-else-if="activeSession !== undefined"
    :initial-session="activeSession"
    :items="workbenchItems"
    :repository="repository"
    @updated="handleSessionUpdated"
    @exit="handleSessionExit"
    @new-session="startAnotherSession"
  />
  <section v-else class="practice-lobby" aria-labelledby="practice-lobby-title">
    <div class="lobby-copy">
      <p class="lobby-kicker">歌词式滚动</p>
      <h2 id="practice-lobby-title">让词义沿着视线滚动</h2>
      <p>
        高亮行就是当前输入目标。滚动只负责呈现，提交、提示和跳过都需要你的明确操作。
      </p>
    </div>

    <div v-if="items.length === 0" class="empty-actions">
      <p>本地还没有可练习的词条。</p>
      <div class="new-session-actions">
        <button
          v-if="resumableSession !== undefined"
          type="button"
          class="continue-action"
          :disabled="busy"
          @click="continueSession"
        >
          打开上次会话 · {{ resumableSession.status === 'paused' ? '已暂停' : '进行中' }}
        </button>
        <button type="button" class="primary-action" :disabled="busy" @click="loadStarterContent">
          载入入门词库
        </button>
      </div>
    </div>
    <div v-else class="start-actions">
      <p>{{ items.length }} 个学习目标已保存在本地。</p>
      <button
        v-if="resumableSession !== undefined"
        type="button"
        class="continue-action"
        :disabled="busy"
        @click="continueSession"
      >
        打开上次会话 · {{ resumableSession.status === 'paused' ? '已暂停' : '进行中' }}
      </button>
      <div class="new-session-actions">
        <button type="button" class="primary-action" :disabled="busy" @click="startSession('lyrics-scroll')">
          开始歌词滚动
        </button>
        <button type="button" :disabled="busy" @click="startSession('keyboard-recall')">
          开始键盘对照
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.workspace-state,
.practice-lobby {
  width: 100%;
  max-width: 920px;
  margin: 0 auto;
  padding: clamp(24px, 5vw, 48px);
  border: 1px solid #ccd6d0;
  border-radius: 8px;
  background: rgb(252 253 252 / 90%);
}

.workspace-state {
  color: #66756d;
}

.workspace-state.error {
  color: #9a432f;
}

.practice-lobby {
  display: grid;
  gap: 28px;
}

.lobby-kicker {
  margin: 0 0 8px;
  color: #6e7f76;
  font-size: 0.72rem;
  font-weight: 700;
}

.lobby-copy h2 {
  max-width: 16ch;
  margin: 0 0 13px;
  color: #1f3429;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 2.5rem;
  font-weight: 500;
  line-height: 1.12;
}

.lobby-copy p:last-child,
.empty-actions p,
.start-actions p {
  max-width: 62ch;
  margin: 0;
  color: #68776f;
  line-height: 1.7;
}

.empty-actions,
.start-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 22px;
  border-top: 1px solid #d9e0dc;
}

.new-session-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

button {
  min-height: 40px;
  padding: 8px 14px;
  border: 1px solid #bac8c0;
  border-radius: 6px;
  background: #fff;
  color: #32483c;
  font-size: 0.82rem;
  font-weight: 650;
  cursor: pointer;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

button:focus-visible {
  outline: 3px solid rgb(45 113 83 / 28%);
  outline-offset: 2px;
}

.primary-action,
.continue-action {
  border-color: #244f3d;
  background: #244f3d;
  color: #fff;
}

.continue-action {
  background: #b96b41;
  border-color: #9f5835;
}

@media (max-width: 720px) {
  .practice-lobby {
    padding: 22px;
  }

  .lobby-copy h2 {
    font-size: 2rem;
  }

  .empty-actions,
  .start-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .new-session-actions,
  .new-session-actions button,
  .empty-actions button,
  .continue-action {
    width: 100%;
  }
}
</style>
