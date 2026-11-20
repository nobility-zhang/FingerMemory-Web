<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import type {
  PracticeItem,
  PracticeModeId,
  PracticeSession,
} from '../core/domain';
import type {
  FingerMemoryRepository,
  PracticeModePreferences,
  PracticeQueuePlan,
  PracticeQueueSettings,
} from '../core/repository';
import SessionWorkbench from '../modes/SessionWorkbench.vue';
import { TRUSTED_MODE_PLUGINS } from '../modes/registry';
import ReminderSettings from './ReminderSettings.vue';
import {
  abandonPracticeSession,
  createPracticeSession,
  switchSessionMode,
} from '../session/practiceSession';
import {
  isRetiredPracticeModeId,
  retiredPracticeModeLabel,
  type LaunchablePracticeModeId,
} from '../session/practiceModeCompatibility';

const props = defineProps<{
  repository: FingerMemoryRepository;
  enableAudio?: boolean;
}>();

const items = ref<PracticeItem[]>([]);
const queuePlan = ref<PracticeQueuePlan>();
const modePreferences = ref<PracticeModePreferences>({ disabledModeIds: [] });
const latestSession = ref<PracticeSession>();
const latestSessionItems = ref<PracticeItem[]>([]);
const activeSession = ref<PracticeSession>();
const loading = ref(true);
const busy = ref(false);
const error = ref<string | null>(null);
const migrationNotice = ref<string | null>(null);

const unfinishedSession = computed(() => {
  const session = latestSession.value;
  if (session === undefined || session.status === 'completed') {
    return undefined;
  }
  return session;
});
const retiredSession = computed(() => {
  const session = unfinishedSession.value;
  return session !== undefined && isRetiredPracticeModeId(session.modeId)
    ? session
    : undefined;
});
const retiredSessionLabel = computed(() => {
  const session = retiredSession.value;
  return session !== undefined && isRetiredPracticeModeId(session.modeId)
    ? retiredPracticeModeLabel(session.modeId)
    : '';
});
const resumableSession = computed(() => {
  const session = unfinishedSession.value;
  if (
    session === undefined ||
    isRetiredPracticeModeId(session.modeId)
  ) {
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
const selectedQueueCount = computed(() => queuePlan.value?.selected.length ?? 0);
const disabledModeIds = computed(() => new Set(modePreferences.value.disabledModeIds));
const enabledModePlugins = computed(() =>
  TRUSTED_MODE_PLUGINS.filter(
    (plugin) => !disabledModeIds.value.has(plugin.manifest.id),
  ),
);

function currentTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

async function loadQueuePlan(at = Date.now()): Promise<PracticeQueuePlan> {
  const plan = await props.repository.planPracticeQueue({
    at,
    timeZone: currentTimeZone(),
  });
  queuePlan.value = plan;
  return plan;
}

function preferredReducedMotion(): boolean {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
}

async function loadWorkspace(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    const at = Date.now();
    const [practiceItems, session, plan, preferences] = await Promise.all([
      props.repository.listPracticeItems(100),
      props.repository.getLatestPracticeSession(),
      props.repository.planPracticeQueue({ at, timeZone: currentTimeZone() }),
      props.repository.getPracticeModePreferences(),
    ]);
    items.value = practiceItems;
    queuePlan.value = plan;
    modePreferences.value = preferences;
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
    await loadQueuePlan();
  } catch {
    error.value = '示例词库未能保存。';
  } finally {
    busy.value = false;
  }
}

async function startSession(
  modeId: LaunchablePracticeModeId = 'flashcard',
): Promise<void> {
  busy.value = true;
  error.value = null;
  try {
    const now = Date.now();
    const plan = await loadQueuePlan(now);
    if (plan.selected.length === 0) {
      error.value = '今日队列为空，请调整上限、恢复队列或先添加词条。';
      return;
    }
    const planItems = plan.selected.map((entry) => entry.item);
    const sources = Object.fromEntries(
      plan.selected.map((entry) => [entry.item.studyItem.id, entry.source]),
    );
    const session = createPracticeSession({
      id: crypto.randomUUID(),
      modeId,
      items: planItems,
      sources,
      now,
      reduceMotion: preferredReducedMotion(),
    });
    const saved = await props.repository.savePracticeSession(session);
    latestSession.value = saved;
    latestSessionItems.value = planItems;
    activeSession.value = saved;
  } catch {
    error.value = '无法创建练习会话。';
  } finally {
    busy.value = false;
  }
}

async function updateQueueSettings(update: Partial<PracticeQueueSettings>): Promise<void> {
  const current = queuePlan.value?.settings;
  if (current === undefined) {
    return;
  }
  busy.value = true;
  error.value = null;
  try {
    await props.repository.savePracticeQueueSettings(
      { ...current, ...update },
      Date.now(),
    );
    await loadQueuePlan();
  } catch {
    error.value = '队列设置未能保存，现有计划未改变。';
  } finally {
    busy.value = false;
  }
}

async function updateModeEnabled(modeId: PracticeModeId, event: Event): Promise<void> {
  const enabled = (event.target as HTMLInputElement).checked;
  const disabled = new Set(modePreferences.value.disabledModeIds);
  if (enabled) {
    disabled.delete(modeId);
  } else {
    disabled.add(modeId);
  }
  busy.value = true;
  error.value = null;
  try {
    modePreferences.value = await props.repository.savePracticeModePreferences(
      { disabledModeIds: [...disabled] },
      Date.now(),
    );
  } catch {
    error.value = '模式启用状态未能保存。';
  } finally {
    busy.value = false;
  }
}

function updateQueueLimit(
  field: 'newLimit' | 'reviewLimit',
  event: Event,
): void {
  const value = Number((event.target as HTMLInputElement).value);
  void updateQueueSettings({ [field]: value });
}

function updateQueuePaused(event: Event): void {
  void updateQueueSettings({
    paused: (event.target as HTMLInputElement).checked,
  });
}

function continueSession(): void {
  const session = resumableSession.value;
  if (session === undefined || isRetiredPracticeModeId(session.modeId)) {
    return;
  }
  activeSession.value = session;
}

async function migrateRetiredSessionToClassic(): Promise<void> {
  const current = retiredSession.value;
  if (current === undefined) {
    return;
  }
  busy.value = true;
  error.value = null;
  migrationNotice.value = null;
  try {
    const migrated = switchSessionMode(current, 'flashcard', Date.now());
    latestSession.value = await props.repository.savePracticeSession(migrated);
    latestSessionItems.value =
      await props.repository.listPracticeItemsByStudyItemIds(
        migrated.queue.map((item) => item.studyItemId),
      );
    migrationNotice.value = '旧会话已显式转为经典闪卡，学习日志与 FSRS 状态未改变。';
  } catch {
    error.value = '旧会话转换失败，当前会话未改变。';
  } finally {
    busy.value = false;
  }
}

async function archiveRetiredSession(): Promise<void> {
  const current = retiredSession.value;
  if (current === undefined) {
    return;
  }
  busy.value = true;
  error.value = null;
  migrationNotice.value = null;
  try {
    const archived = abandonPracticeSession(current, Date.now());
    latestSession.value = await props.repository.savePracticeSession(archived);
    latestSessionItems.value = [];
    migrationNotice.value = '旧会话已归档，已有练习、复习日志和 FSRS 状态保持不变。';
  } catch {
    error.value = '旧会话归档失败，当前会话未改变。';
  } finally {
    busy.value = false;
  }
}

async function abandonResumableSession(): Promise<void> {
  const current = resumableSession.value;
  if (current === undefined) {
    return;
  }
  busy.value = true;
  error.value = null;
  try {
    const abandoned = abandonPracticeSession(current, Date.now());
    latestSession.value = await props.repository.savePracticeSession(abandoned);
    latestSessionItems.value = [];
  } catch {
    error.value = '无法放弃上次会话，现有会话未改变。';
  } finally {
    busy.value = false;
  }
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
  void startSession('flashcard');
}

onMounted(() => {
  void loadWorkspace();
});
</script>

<template>
  <div v-if="loading" class="workspace-state" role="status">正在读取练习数据...</div>
  <div v-else-if="error && queuePlan === undefined" class="workspace-state error" role="alert">
    <p>{{ error }}</p>
    <button type="button" @click="loadWorkspace">重试</button>
  </div>
  <SessionWorkbench
    v-else-if="activeSession !== undefined"
    :initial-session="activeSession"
    :items="workbenchItems"
    :repository="repository"
    :enable-audio="enableAudio === true"
    @updated="handleSessionUpdated"
    @exit="handleSessionExit"
    @new-session="startAnotherSession"
  />
  <section v-else class="practice-lobby" aria-labelledby="practice-lobby-title">
    <p v-if="error" class="lobby-error" role="alert">{{ error }}</p>
    <p v-if="migrationNotice" class="migration-notice" role="status">
      {{ migrationNotice }}
    </p>
    <div class="lobby-copy">
      <p class="lobby-kicker">经典闪卡</p>
      <h2 id="practice-lobby-title">先回忆，再翻面确认</h2>
      <p>
        正面只显示中文释义。空格翻面后检查英文，再用 Again、Hard、Good 或 Easy
        记录真实回忆。模板、动画和伙伴对话不会替你评分。
      </p>
    </div>

    <section
      v-if="retiredSession"
      class="legacy-session"
      aria-labelledby="legacy-session-title"
    >
      <div>
        <p class="lobby-kicker">旧模式迁移</p>
        <h3 id="legacy-session-title">
          {{ retiredSessionLabel }}会话不再启动
        </h3>
        <p>
          该未完成会话可以显式转为经典闪卡继续，或归档退出。转换不会重算 FSRS、
          删除练习记录或改写历史日志。
        </p>
      </div>
      <div class="legacy-session-actions">
        <button
          type="button"
          class="primary-action"
          :disabled="busy"
          @click="migrateRetiredSessionToClassic"
        >
          转为经典闪卡
        </button>
        <button
          type="button"
          class="danger-action"
          :disabled="busy"
          @click="archiveRetiredSession"
        >
          归档旧会话
        </button>
      </div>
    </section>

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
        <button
          v-if="resumableSession !== undefined"
          type="button"
          class="danger-action"
          :disabled="busy"
          @click="abandonResumableSession"
        >
          放弃上次会话
        </button>
      </div>
    </div>
    <div v-else class="start-actions">
      <div class="queue-overview">
        <p>{{ items.length }} 个学习目标已保存在本地。</p>
        <p v-if="queuePlan !== undefined" class="queue-counts">
          今日到期 {{ queuePlan.dueAvailableCount }} · 可学新词
          {{ queuePlan.newAvailableCount }} · 本次计划 {{ selectedQueueCount }}
        </p>
      </div>
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
        <button
          type="button"
          class="primary-action"
          :disabled="busy || selectedQueueCount === 0"
          @click="startSession('flashcard')"
        >
          开始经典闪卡
        </button>
        <button
          v-for="plugin in enabledModePlugins"
          :key="plugin.manifest.id"
          type="button"
          :disabled="busy || selectedQueueCount === 0"
          @click="startSession(plugin.manifest.id)"
        >
          开始{{ plugin.manifest.label }}
        </button>
        <button
          v-if="resumableSession !== undefined"
          type="button"
          class="danger-action"
          :disabled="busy"
          @click="abandonResumableSession"
        >
          放弃上次会话
        </button>
      </div>
    </div>

    <section
      v-if="items.length > 0 && queuePlan !== undefined"
      class="queue-policy"
      aria-labelledby="queue-policy-title"
    >
      <div class="queue-policy-copy">
        <p class="lobby-kicker">今日队列</p>
        <h3 id="queue-policy-title">控制本次取词范围</h3>
        <p>
          到期复习优先，新词随后加入。会话开始后队列固定；暂停不会删除已有会话或历史。
        </p>
      </div>
      <div class="queue-fields">
        <label>
          <span>新词上限</span>
          <input
            type="number"
            min="0"
            max="100"
            step="1"
            :value="queuePlan.settings.newLimit"
            :disabled="busy"
            @change="updateQueueLimit('newLimit', $event)"
          />
        </label>
        <label>
          <span>复习上限</span>
          <input
            type="number"
            min="0"
            max="500"
            step="1"
            :value="queuePlan.settings.reviewLimit"
            :disabled="busy"
            @change="updateQueueLimit('reviewLimit', $event)"
          />
        </label>
        <label class="queue-pause">
          <input
            type="checkbox"
            :checked="queuePlan.settings.paused"
            :disabled="busy"
            @change="updateQueuePaused"
          />
          <span>暂停加入新内容</span>
        </label>
      </div>
      <p v-if="selectedQueueCount === 0" class="queue-empty-note" role="status">
        当前计划为空，开始按钮已停用。调整上限或恢复队列后再开始。
      </p>
    </section>

    <section
      v-if="items.length > 0"
      class="mode-policy"
      aria-labelledby="mode-policy-title"
    >
      <div class="mode-policy-copy">
        <p class="lobby-kicker">练习模式</p>
        <h3 id="mode-policy-title">选择可启动的呈现方式</h3>
        <p>
          当前启用 {{ enabledModePlugins.length }} 个可选呈现模式；
          经典闪卡始终独立可用。
        </p>
      </div>
      <div class="mode-preferences">
        <label v-for="plugin in TRUSTED_MODE_PLUGINS" :key="plugin.manifest.id">
          <input
            type="checkbox"
            :checked="!disabledModeIds.has(plugin.manifest.id)"
            :disabled="busy"
            @change="updateModeEnabled(plugin.manifest.id, $event)"
          />
          <span>{{ plugin.manifest.label }}</span>
        </label>
      </div>
    </section>

    <ReminderSettings :repository="repository" />
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

.lobby-error {
  margin: 0;
  padding: 10px 12px;
  border-left: 3px solid #ad5b42;
  background: #fbf1ed;
  color: #8d4634;
  font-size: 0.8rem;
}

.migration-notice {
  margin: 0;
  padding: 10px 12px;
  border-left: 3px solid #3f765c;
  background: #edf6f0;
  color: #315443;
  font-size: 0.8rem;
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
.start-actions,
.queue-policy,
.mode-policy,
.legacy-session {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 22px;
  border-top: 1px solid #d9e0dc;
}

.legacy-session {
  align-items: flex-start;
  padding: 18px;
  border: 1px solid #d9b68d;
  border-radius: 8px;
  background: #fff7ec;
}

.legacy-session h3 {
  margin: 0 0 6px;
  color: #4c3827;
  font-size: 1.05rem;
}

.legacy-session p:last-child {
  max-width: 62ch;
  margin: 0;
  color: #705d4c;
  font-size: 0.78rem;
  line-height: 1.6;
}

.legacy-session-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.new-session-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.queue-overview {
  display: grid;
  gap: 5px;
}

.queue-counts {
  color: #465b50 !important;
  font-size: 0.8rem;
  font-weight: 700;
}

.queue-policy {
  align-items: flex-start;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 20px 28px;
  padding: 18px;
  border: 1px solid #d3dcd6;
  border-radius: 8px;
  background: #f8faf9;
}

.queue-policy-copy h3 {
  margin: 0 0 6px;
  color: #263c30;
  font-size: 1.05rem;
}

.queue-policy-copy p:last-child {
  max-width: 52ch;
  margin: 0;
  color: #6d7c73;
  font-size: 0.78rem;
  line-height: 1.6;
}

.queue-fields {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.queue-fields label {
  display: grid;
  gap: 5px;
  color: #586960;
  font-size: 0.72rem;
  font-weight: 700;
}

.queue-fields input[type="number"] {
  width: 92px;
  min-height: 38px;
  padding: 7px 9px;
  border: 1px solid #b8c5bd;
  border-radius: 6px;
  background: #fff;
  color: #263b30;
}

.queue-fields .queue-pause {
  display: flex;
  align-items: center;
  align-self: end;
  min-height: 38px;
  padding: 7px 10px;
  border: 1px solid #c7d2cb;
  border-radius: 6px;
  background: #fff;
  white-space: nowrap;
}

.queue-pause input {
  width: 16px;
  height: 16px;
}

.queue-empty-note {
  grid-column: 1 / -1;
  margin: 0;
  padding: 9px 11px;
  border-left: 3px solid #a06546;
  background: #f7f1ed;
  color: #7b4c37;
  font-size: 0.76rem;
}

.mode-policy {
  align-items: flex-start;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 20px 28px;
  padding: 18px;
  border: 1px solid #d3dcd6;
  border-radius: 8px;
  background: #fbfcfb;
}

.mode-policy-copy h3 {
  margin: 0 0 6px;
  color: #263c30;
  font-size: 1.05rem;
}

.mode-policy-copy p:last-child {
  margin: 0;
  color: #6d7c73;
  font-size: 0.78rem;
}

.mode-preferences {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.mode-preferences label {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 38px;
  padding: 7px 11px;
  border: 1px solid #c7d2cb;
  border-radius: 6px;
  background: #fff;
  color: #465b50;
  font-size: 0.76rem;
  font-weight: 700;
}

.mode-preferences input {
  width: 16px;
  height: 16px;
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

.danger-action {
  color: #9b4935;
  border-color: #dfc2b9;
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
  .start-actions,
  .queue-policy,
  .mode-policy,
  .legacy-session {
    align-items: flex-start;
    flex-direction: column;
  }

  .new-session-actions,
  .new-session-actions button,
  .empty-actions button,
  .continue-action {
    width: 100%;
  }

  .legacy-session-actions,
  .legacy-session-actions button {
    width: 100%;
  }

  .queue-policy {
    grid-template-columns: 1fr;
  }

  .mode-policy {
    grid-template-columns: 1fr;
  }

  .queue-fields {
    justify-content: flex-start;
    width: 100%;
  }

  .queue-fields label,
  .queue-fields input[type="number"] {
    width: 100%;
  }

  .mode-preferences {
    justify-content: flex-start;
    width: 100%;
  }
}
</style>
