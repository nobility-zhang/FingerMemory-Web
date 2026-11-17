<script setup lang="ts">
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  toRef,
} from 'vue';
import {
  accountApi,
  ApiClientError,
  type AccountBackup,
  type AccountClient,
  type BackupPreview,
  type LexiconView,
  type MigrationPreview,
  type PendingAnswerView,
  type QueuePlanView,
  type ReviewPreviewView,
  type SessionEnvelope,
  type UserView,
} from '../api/client';
import FlashcardPanel from '../flashcards/FlashcardPanel.vue';
import PhaserGameHost from '../game-runtime/PhaserGameHost.vue';
import { usePhaserAudio } from '../game-runtime/usePhaserAudio';

const props = withDefaults(
  defineProps<{
    api?: AccountClient;
    enableAudio?: boolean;
  }>(),
  {
    api: () => accountApi,
    enableAudio: false,
  },
);

const user = ref<UserView | null>(null);
const lexicons = ref<LexiconView[]>([]);
const loading = ref(true);
const busy = ref(false);
const error = ref<string | null>(null);
const authMode = ref<'login' | 'register'>('login');
const username = ref('');
const displayName = ref('');
const password = ref('');
const lexiconName = ref('');
const lexiconDescription = ref('');
const onlinePlan = ref<QueuePlanView>();
const onlineSessions = ref<SessionEnvelope[]>([]);
const onlineSession = ref<SessionEnvelope>();
const onlineAnswer = ref('');
const pendingAnswer = ref<PendingAnswerView>();
const reviewPreviews = ref<ReviewPreviewView>();
const onlineBusy = ref(false);
const onlineNotice = ref<string | null>(null);
const onlineFlashcardNotRemembered = ref(false);
const {
  setHost: setAudioHost,
  muted: audioMuted,
  status: audioStatus,
  toggleMuted: toggleAudioMuted,
  playFlip: playFlipAudio,
  handleRuntimeEvent: handleAudioRuntimeEvent,
} = usePhaserAudio(toRef(props, 'enableAudio'));
const migrationFileName = ref('');
const migrationSnapshot = ref<unknown>();
const migrationPreview = ref<MigrationPreview>();
const migrationBatchId = ref('');
const migrationBackupDownloaded = ref(false);
const migrationConfirmed = ref(false);
const migrationBusy = ref(false);
const migrationNotice = ref<string | null>(null);
const accountBackupFileName = ref('');
const accountBackup = ref<AccountBackup>();
const accountBackupPreview = ref<BackupPreview>();
const accountBackupConfirmed = ref(false);
const accountBackupRestoreKey = ref('');
const accountBackupBusy = ref(false);
const accountBackupNotice = ref<string | null>(null);
let requestGeneration = 0;
let onlineGeneration = 0;

const isAuthenticated = computed(() => user.value !== null);
const currentSessionItem = computed(() => {
  const studyItemId = onlineSession.value?.session.currentStudyItemId;
  if (studyItemId === null || studyItemId === undefined) {
    return undefined;
  }
  return onlineSession.value?.items.find(
    (item) => item.studyItem.id === studyItemId,
  );
});
const onlineCompletedCount = computed(
  () =>
    onlineSession.value?.session.queue.filter(
      (item) => item.status === 'completed',
    ).length ?? 0,
);
const onlineQueueTotal = computed(
  () => onlineSession.value?.session.queue.length ?? 0,
);
const isOnlineFlashcardMode = computed(
  () => onlineSession.value?.session.modeId === 'flashcard',
);
const onlineRatingLabels = computed((): Partial<Record<
  'again' | 'hard' | 'good' | 'easy',
  string
>> => {
  const previews = reviewPreviews.value;
  if (previews === undefined) {
    return {};
  }
  return Object.fromEntries(
    Object.values(previews.previews).map((preview) => [
      preview.rating,
      formatDue(preview.dueAt),
    ]),
  );
});

function errorMessage(cause: unknown): string {
  if (cause instanceof ApiClientError) {
    return cause.message;
  }
  return '请求失败，请稍后重试。';
}

function newId(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function currentTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

function formatDue(timestamp: number): string {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(timestamp));
}

function downloadJson(fileName: string, value: unknown): void {
  const url = URL.createObjectURL(
    new Blob([`${JSON.stringify(value, null, 2)}\n`], {
      type: 'application/json;charset=utf-8',
    }),
  );
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(url);
}

async function refreshOnline(): Promise<void> {
  if (user.value === null) {
    return;
  }
  const generation = ++onlineGeneration;
  const [plan, sessions] = await Promise.all([
    props.api.loadQueuePlan({ timeZone: currentTimeZone() }),
    props.api.listActiveSessions(),
  ]);
  if (generation !== onlineGeneration) {
    return;
  }
  onlinePlan.value = plan;
  onlineSessions.value = sessions.sessions;
  const currentId = onlineSession.value?.session.id;
  const selected =
    sessions.sessions.find((item) => item.session.id === currentId) ??
    sessions.sessions[0];
  onlineSession.value = selected;
  if (onlineSession.value === undefined) {
    pendingAnswer.value = undefined;
    reviewPreviews.value = undefined;
    onlineFlashcardNotRemembered.value = false;
    return;
  }
  const selectedStudyItemId = onlineSession.value.session.currentStudyItemId;
  const selectedItem = onlineSession.value.items.find(
    (item) => item.studyItem.id === selectedStudyItemId,
  );
  const storedPending = onlineSession.value.session.pendingAnswer;
  if (storedPending !== null && selectedItem !== undefined) {
    pendingAnswer.value = storedPending;
    onlineFlashcardNotRemembered.value =
      storedPending.explicitNotRemembered === true;
    reviewPreviews.value = await props.api.previewReview({
      sessionId: onlineSession.value.session.id,
      studyItemId: storedPending.studyItemId,
      expectedDataVersion: onlineSession.value.dataVersion,
      expectedSessionVersion: onlineSession.value.session.version,
      expectedReviewVersion: selectedItem.reviewVersion,
    });
  } else {
    pendingAnswer.value = undefined;
    reviewPreviews.value = undefined;
    onlineFlashcardNotRemembered.value = false;
  }
}

async function refresh(): Promise<void> {
  const generation = ++requestGeneration;
  loading.value = true;
  error.value = null;
  try {
    const loadedUser = await props.api.loadCurrentUser();
    const loadedLexicons = loadedUser === null ? [] : await props.api.listLexicons();
    if (generation !== requestGeneration) {
      return;
    }
    user.value = loadedUser;
    lexicons.value = loadedLexicons;
    if (loadedUser !== null) {
      await refreshOnline();
    }
  } catch (cause) {
    if (generation === requestGeneration) {
      error.value = errorMessage(cause);
    }
  } finally {
    if (generation === requestGeneration) {
      loading.value = false;
    }
  }
}

async function submitAuth(): Promise<void> {
  if (busy.value) {
    return;
  }
  busy.value = true;
  error.value = null;
  try {
    if (authMode.value === 'register') {
      await props.api.register(username.value, password.value, displayName.value);
    } else {
      await props.api.login(username.value, password.value);
    }
    password.value = '';
    await refresh();
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function createLexicon(): Promise<void> {
  if (busy.value || lexiconName.value.trim().length === 0) {
    return;
  }
  busy.value = true;
  error.value = null;
  try {
    await props.api.createLexicon(lexiconName.value, lexiconDescription.value);
    lexiconName.value = '';
    lexiconDescription.value = '';
    lexicons.value = await props.api.listLexicons();
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function startOnlineSession(
  modeId: 'keyboard-recall' | 'flashcard' = 'keyboard-recall',
): Promise<void> {
  if (onlineBusy.value) {
    return;
  }
  onlineBusy.value = true;
  onlineNotice.value = null;
  try {
    onlineSession.value = await props.api.createSession({
      modeId,
      timeZone: currentTimeZone(),
      newLimit: onlinePlan.value?.settings.newLimit,
      reviewLimit: onlinePlan.value?.settings.reviewLimit,
      reduceMotion: false,
    });
    onlineAnswer.value = '';
    pendingAnswer.value = undefined;
    reviewPreviews.value = undefined;
    onlineFlashcardNotRemembered.value = false;
    onlineNotice.value = modeId === 'flashcard'
      ? '在线经典闪卡已创建。'
      : '在线拼写会话已创建。';
  } catch (cause) {
    onlineNotice.value = errorMessage(cause);
  } finally {
    onlineBusy.value = false;
  }
}

async function startOnlineFlashcardSession(): Promise<void> {
  await startOnlineSession('flashcard');
}

async function revealOnlineFlashcard(notRemembered = false): Promise<void> {
  const session = onlineSession.value;
  const item = currentSessionItem.value;
  if (
    onlineBusy.value ||
    session === undefined ||
    item === undefined ||
    pendingAnswer.value !== undefined ||
    session.session.status !== 'active'
  ) {
    return;
  }
  onlineBusy.value = true;
  onlineNotice.value = null;
  try {
    playFlipAudio(
      `flashcard:${session.session.id}:${item.studyItem.id}:${Date.now()}`,
    );
    const submissionAttemptId = newId();
    const reviewSubmissionId = newId();
    const result = await props.api.submitPracticeResponse({
      sessionId: session.session.id,
      practiceAttemptId: submissionAttemptId,
      reviewSubmissionId,
      studyItemId: item.studyItem.id,
      response: '',
      answerWasHidden: true,
      hintUsed: false,
      answerRevealed: true,
      assessmentType: 'flashcard-self-report',
      assessmentPolicyVersion: 2,
      assessmentStage: 'reveal-for-check',
      priorExposure: false,
      explicitNotRemembered: notRemembered,
      expectedDataVersion: session.dataVersion,
      expectedSessionVersion: session.session.version,
    });
    onlineSession.value = {
      ...session,
      session: result.session,
      dataVersion: result.dataVersion,
    };
    pendingAnswer.value = result.pendingAnswer;
    onlineFlashcardNotRemembered.value = notRemembered;
    reviewPreviews.value = await props.api.previewReview({
      sessionId: result.session.id,
      studyItemId: result.pendingAnswer.studyItemId,
      expectedDataVersion: result.dataVersion,
      expectedSessionVersion: result.session.version,
      expectedReviewVersion: item.reviewVersion,
    });
    onlineNotice.value = notRemembered
      ? '答案已揭晓。这条卡片只适合记为 Again。'
      : '翻面完成，请按真实回忆选择评分。';
  } catch (cause) {
    onlineNotice.value = errorMessage(cause);
  } finally {
    onlineBusy.value = false;
  }
}

async function skipOnlineFlashcard(): Promise<void> {
  const session = onlineSession.value;
  const item = currentSessionItem.value;
  if (
    onlineBusy.value ||
    session === undefined ||
    item === undefined ||
    session.session.status !== 'active'
  ) {
    return;
  }
  onlineBusy.value = true;
  onlineNotice.value = null;
  try {
    const result = await props.api.submitPracticeCompletion({
      sessionId: session.session.id,
      practiceAttemptId: newId(),
      studyItemId: item.studyItem.id,
      response: '',
      answerWasHidden: pendingAnswer.value?.answerWasHidden ?? true,
      hintUsed: false,
      answerRevealed: pendingAnswer.value?.answerRevealed ?? false,
      assessmentType: 'guided-practice',
      assessmentPolicyVersion: 2,
      assessmentStage: 'attempt',
      priorExposure: true,
      explicitNotRemembered: false,
      abandoned: true,
      outcome: 'skipped',
      expectedDataVersion: session.dataVersion,
      expectedSessionVersion: session.session.version,
    });
    onlineSession.value = {
      ...session,
      session: result.session,
      dataVersion: result.dataVersion,
    };
    pendingAnswer.value = undefined;
    reviewPreviews.value = undefined;
    onlineFlashcardNotRemembered.value = false;
    onlineNotice.value = '本张卡片已跳过，没有写入正式复习。';
  } catch (cause) {
    onlineNotice.value = errorMessage(cause);
  } finally {
    onlineBusy.value = false;
  }
}

async function submitOnlineAnswer(): Promise<void> {
  const session = onlineSession.value;
  const item = currentSessionItem.value;
  if (
    onlineBusy.value ||
    session === undefined ||
    item === undefined ||
    onlineAnswer.value.trim().length === 0
  ) {
    return;
  }
  onlineBusy.value = true;
  onlineNotice.value = null;
  try {
    let ready = session;
    if (session.session.status === 'paused') {
      ready = await props.api.updateSession({
        sessionId: session.session.id,
        expectedDataVersion: session.dataVersion,
        expectedVersion: session.session.version,
        status: 'active',
      });
    }
    const reviewSubmissionId = newId();
    const result = await props.api.submitPracticeResponse({
      sessionId: ready.session.id,
      practiceAttemptId: newId(),
      reviewSubmissionId,
      studyItemId: item.studyItem.id,
      response: onlineAnswer.value,
      assessmentType: 'typed-recall',
      assessmentPolicyVersion: 2,
      assessmentStage: 'attempt',
      priorExposure: false,
      explicitNotRemembered: false,
      expectedDataVersion: ready.dataVersion,
      expectedSessionVersion: ready.session.version,
    });
    onlineSession.value = {
      ...ready,
      session: result.session,
      dataVersion: result.dataVersion,
    };
    pendingAnswer.value = result.pendingAnswer;
    onlineAnswer.value = '';
    reviewPreviews.value = await props.api.previewReview({
      sessionId: result.session.id,
      studyItemId: result.pendingAnswer.studyItemId,
      expectedDataVersion: result.dataVersion,
      expectedSessionVersion: result.session.version,
      expectedReviewVersion: item.reviewVersion,
    });
    onlineNotice.value = '答案已提交，请根据主动回忆状态选择正式评分。';
  } catch (cause) {
    onlineNotice.value = errorMessage(cause);
  } finally {
    onlineBusy.value = false;
  }
}

async function submitOnlineRating(
  rating: 'again' | 'hard' | 'good' | 'easy',
): Promise<void> {
  const session = onlineSession.value;
  const previews = reviewPreviews.value;
  if (onlineBusy.value || session === undefined || previews === undefined) {
    return;
  }
  onlineBusy.value = true;
  onlineNotice.value = null;
  try {
    await props.api.submitReview({
      sessionId: session.session.id,
      submissionId:
        pendingAnswer.value?.reviewSubmissionId ?? newId(),
      rating,
      assessmentType: pendingAnswer.value?.assessmentType,
      assessmentPolicyVersion: pendingAnswer.value?.assessmentPolicyVersion,
      assessmentStage: pendingAnswer.value?.assessmentStage,
      priorExposure: pendingAnswer.value?.priorExposure,
      explicitNotRemembered: pendingAnswer.value?.explicitNotRemembered,
      answerWasHidden: pendingAnswer.value?.answerWasHidden,
      hintUsed: pendingAnswer.value?.hintUsed,
      answerRevealed: pendingAnswer.value?.answerRevealed,
      expectedDataVersion: previews.dataVersion,
      expectedReviewVersion: previews.reviewVersion,
      expectedSessionVersion: previews.sessionVersion,
    });
    onlineSession.value = await props.api.getSession(session.session.id);
    pendingAnswer.value = undefined;
    reviewPreviews.value = undefined;
    onlineFlashcardNotRemembered.value = false;
    onlineNotice.value = `正式复习已提交，下次到期 ${formatDue(
      previews.previews[rating].dueAt,
    )}。`;
    await refreshOnline();
  } catch (cause) {
    onlineNotice.value = errorMessage(cause);
  } finally {
    onlineBusy.value = false;
  }
}

async function toggleOnlinePause(): Promise<void> {
  const session = onlineSession.value;
  if (onlineBusy.value || session === undefined) {
    return;
  }
  onlineBusy.value = true;
  onlineNotice.value = null;
  try {
    onlineSession.value = await props.api.updateSession({
      sessionId: session.session.id,
      expectedDataVersion: session.dataVersion,
      expectedVersion: session.session.version,
      status: session.session.status === 'paused' ? 'active' : 'paused',
    });
  } catch (cause) {
    onlineNotice.value = errorMessage(cause);
  } finally {
    onlineBusy.value = false;
  }
}

async function handleMigrationFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (file === undefined) {
    return;
  }
  migrationBusy.value = true;
  migrationNotice.value = null;
  try {
    if (file.size > 20 * 1024 * 1024) {
      throw new Error('迁移文件超过 20 MiB 上限。');
    }
    const parsed: unknown = JSON.parse(await file.text());
    migrationSnapshot.value = parsed;
    migrationFileName.value = file.name;
    migrationBatchId.value = `local-${newId()}`;
    migrationBackupDownloaded.value = false;
    migrationConfirmed.value = false;
    migrationPreview.value = await props.api.previewLocalMigration(parsed);
  } catch (cause) {
    migrationPreview.value = undefined;
    migrationNotice.value = errorMessage(cause);
  } finally {
    migrationBusy.value = false;
  }
}

function downloadMigrationBackup(): void {
  if (migrationSnapshot.value === undefined) {
    return;
  }
  downloadJson(
    `finger-memory-local-before-migration-${Date.now()}.json`,
    migrationSnapshot.value,
  );
  migrationBackupDownloaded.value = true;
}

async function importMigration(): Promise<void> {
  if (
    migrationBusy.value ||
    migrationSnapshot.value === undefined ||
    !migrationBackupDownloaded.value ||
    !migrationConfirmed.value
  ) {
    return;
  }
  migrationBusy.value = true;
  migrationNotice.value = null;
  try {
    const result = await props.api.importLocalMigration(
      migrationBatchId.value,
      migrationSnapshot.value,
    );
    migrationNotice.value = `迁移完成：映射 ${result.idMappings} 个 ID，数据版本 ${result.dataVersion}。`;
    await refreshOnline();
  } catch (cause) {
    migrationNotice.value = errorMessage(cause);
  } finally {
    migrationBusy.value = false;
  }
}

async function exportAccountBackup(): Promise<void> {
  if (accountBackupBusy.value) {
    return;
  }
  accountBackupBusy.value = true;
  accountBackupNotice.value = null;
  try {
    const backup = await props.api.exportAccountBackup();
    downloadJson(
      `finger-memory-account-${new Date(backup.exportedAt)
        .toISOString()
        .replaceAll(':', '')
        .replaceAll('.', '')}.json`,
      backup,
    );
    accountBackupNotice.value = '账号备份已导出。';
  } catch (cause) {
    accountBackupNotice.value = errorMessage(cause);
  } finally {
    accountBackupBusy.value = false;
  }
}

async function handleAccountBackupFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (file === undefined) {
    return;
  }
  accountBackupBusy.value = true;
  accountBackupNotice.value = null;
  try {
    if (file.size > 20 * 1024 * 1024) {
      throw new Error('备份文件超过 20 MiB 上限。');
    }
    accountBackup.value = JSON.parse(await file.text()) as AccountBackup;
    accountBackupFileName.value = file.name;
    accountBackupRestoreKey.value = `restore-${newId()}`;
    accountBackupPreview.value = await props.api.previewAccountBackup(
      accountBackup.value,
    );
    accountBackupConfirmed.value = false;
  } catch (cause) {
    accountBackup.value = undefined;
    accountBackupPreview.value = undefined;
    accountBackupNotice.value = errorMessage(cause);
  } finally {
    accountBackupBusy.value = false;
  }
}

async function restoreAccountBackup(): Promise<void> {
  if (
    accountBackupBusy.value ||
    accountBackup.value === undefined ||
    !accountBackupConfirmed.value
  ) {
    return;
  }
  accountBackupBusy.value = true;
  accountBackupNotice.value = null;
  try {
    const result = await props.api.restoreAccountBackup(
      accountBackupRestoreKey.value,
      accountBackup.value,
    );
    accountBackupNotice.value = `账号备份已恢复，数据版本 ${result.dataVersion}。`;
    lexicons.value = await props.api.listLexicons();
    await refreshOnline();
  } catch (cause) {
    accountBackupNotice.value = errorMessage(cause);
  } finally {
    accountBackupBusy.value = false;
  }
}

async function logout(): Promise<void> {
  const generation = ++requestGeneration;
  busy.value = true;
  error.value = null;
  try {
    await props.api.logout();
    if (generation !== requestGeneration) {
      return;
    }
    user.value = null;
    lexicons.value = [];
    onlinePlan.value = undefined;
    onlineSessions.value = [];
    onlineSession.value = undefined;
    pendingAnswer.value = undefined;
    reviewPreviews.value = undefined;
    onlineFlashcardNotRemembered.value = false;
    onlineGeneration += 1;
    username.value = '';
    displayName.value = '';
    password.value = '';
  } catch (cause) {
    if (generation === requestGeneration) {
      error.value = errorMessage(cause);
    }
  } finally {
    if (generation === requestGeneration) {
      busy.value = false;
    }
  }
}

onMounted(() => {
  void refresh();
});

onBeforeUnmount(() => {
  requestGeneration += 1;
  onlineGeneration += 1;
});
</script>

<template>
  <section class="account-workspace" aria-labelledby="account-title">
    <div class="account-heading">
      <div>
        <p class="section-label">账号模式</p>
        <h2 id="account-title">跨设备学习空间</h2>
      </div>
      <span class="mode-badge">本地模式保持独立</span>
    </div>

    <div v-if="loading" class="account-state" role="status">正在读取账号状态...</div>
    <div v-else-if="!isAuthenticated" class="auth-panel">
      <div class="auth-tabs" role="tablist" aria-label="账号操作">
        <button
          type="button"
          role="tab"
          :aria-selected="authMode === 'login'"
          :class="{ selected: authMode === 'login' }"
          @click="authMode = 'login'"
        >
          登录
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="authMode === 'register'"
          :class="{ selected: authMode === 'register' }"
          @click="authMode = 'register'"
        >
          注册
        </button>
      </div>

      <form class="auth-form" @submit.prevent="submitAuth">
        <label>
          <span>用户名</span>
          <input
            v-model="username"
            type="text"
            autocomplete="username"
            minlength="3"
            maxlength="32"
            required
          />
        </label>
        <label v-if="authMode === 'register'">
          <span>显示名称</span>
          <input v-model="displayName" type="text" autocomplete="nickname" maxlength="80" />
        </label>
        <label>
          <span>密码</span>
          <input
            v-model="password"
            type="password"
            :autocomplete="authMode === 'register' ? 'new-password' : 'current-password'"
            minlength="12"
            maxlength="128"
            required
          />
        </label>
        <button type="submit" class="primary-action" :disabled="busy">
          {{ authMode === 'register' ? '创建账号' : '登录账号' }}
        </button>
      </form>
    </div>
    <template v-else>
      <div class="profile-strip">
        <div>
          <span class="profile-avatar" aria-hidden="true">
            {{ (user?.displayName ?? '?').slice(0, 1).toUpperCase() }}
          </span>
          <div>
            <strong>{{ user?.displayName }}</strong>
            <span>@{{ user?.username }}</span>
          </div>
        </div>
        <button type="button" :disabled="busy" @click="logout">退出登录</button>
      </div>

      <div class="cloud-lexicons">
        <div class="cloud-heading">
          <div>
            <p class="section-label">私人词库</p>
            <h3>当前账号的数据</h3>
          </div>
          <span>{{ lexicons.length }} 个词库</span>
        </div>

        <form class="new-lexicon-form" @submit.prevent="createLexicon">
          <label>
            <span>词库名称</span>
            <input v-model="lexiconName" type="text" maxlength="120" required />
          </label>
          <label>
            <span>说明</span>
            <input v-model="lexiconDescription" type="text" maxlength="2000" />
          </label>
          <button type="submit" class="primary-action" :disabled="busy">创建私人词库</button>
        </form>

        <p v-if="lexicons.length === 0" class="account-state">账号中还没有词库。</p>
        <ul v-else class="lexicon-list">
          <li v-for="lexicon in lexicons" :key="lexicon.id">
            <div>
              <strong>{{ lexicon.name }}</strong>
              <span>{{ lexicon.description || '暂无说明' }}</span>
            </div>
            <code>{{ lexicon.id }}</code>
          </li>
        </ul>
      </div>

      <section class="online-training" aria-labelledby="online-training-title">
        <div class="cloud-heading">
          <div>
            <p class="section-label">在线训练</p>
            <h3 id="online-training-title">服务端正式复习</h3>
          </div>
          <span v-if="onlinePlan">
            到期 {{ onlinePlan.dueAvailableCount }} · 新词
            {{ onlinePlan.newAvailableCount }}
          </span>
        </div>

        <div class="online-toolbar">
          <button
            type="button"
            :disabled="onlineBusy"
            @click="refreshOnline"
          >
            刷新队列
          </button>
          <button
            type="button"
            class="primary-action"
            :disabled="onlineBusy || onlinePlan?.selected.length === 0"
            @click="startOnlineFlashcardSession"
          >
            开始在线经典闪卡
          </button>
          <button
            type="button"
            class="secondary-action"
            :disabled="onlineBusy || onlinePlan?.selected.length === 0"
            @click="startOnlineSession()"
          >
            开始在线训练
          </button>
          <span v-if="onlineSessions.length > 0">
            可恢复会话 {{ onlineSessions.length }} 个
          </span>
        </div>

        <div v-if="onlineSession" class="online-session">
          <div class="online-session-meta">
            <span>
              进度 {{ onlineCompletedCount }}/{{ onlineQueueTotal }}
            </span>
            <span>会话 v{{ onlineSession.session.version }}</span>
            <button
              type="button"
              :disabled="onlineBusy || onlineSession.session.status === 'completed'"
              @click="toggleOnlinePause"
            >
              {{ onlineSession.session.status === 'paused' ? '继续' : '暂停' }}
            </button>
          </div>

          <div
            v-if="currentSessionItem && isOnlineFlashcardMode"
            class="online-card online-flashcard"
          >
            <FlashcardPanel
              :item="currentSessionItem"
              :position="(onlineSession?.session.currentIndex ?? 0) + 1"
              :total="onlineQueueTotal"
              :flipped="pendingAnswer !== undefined"
              :paused="onlineSession.session.status === 'paused'"
              :busy="onlineBusy"
              :not-remembered="onlineFlashcardNotRemembered"
              :reduce-motion="onlineSession.session.reduceMotion"
              :audio-control="enableAudio"
              :audio-muted="audioMuted"
              :audio-status="audioStatus"
              :rating-previews="onlineRatingLabels"
              :rating-preview-loading="
                pendingAnswer !== undefined && reviewPreviews === undefined
              "
              :rating-preview-error="null"
              @flip="revealOnlineFlashcard(false)"
              @not-remembered="revealOnlineFlashcard(true)"
              @rate="submitOnlineRating"
              @skip="skipOnlineFlashcard"
              @toggle-audio="toggleAudioMuted"
            >
              <template #audio-visual>
                <PhaserGameHost
                  v-if="enableAudio"
                  :ref="setAudioHost"
                  :muted="audioMuted"
                  @event="handleAudioRuntimeEvent"
                />
              </template>
            </FlashcardPanel>
          </div>
          <div v-else-if="currentSessionItem" class="online-card">
            <p class="section-label">中文释义</p>
            <strong>{{ currentSessionItem.wordEntry.meaning }}</strong>
            <div
              v-if="pendingAnswer === undefined"
              class="online-answer"
            >
              <label>
                <span>英文拼写</span>
                <input
                  v-model="onlineAnswer"
                  type="text"
                  autocomplete="off"
                  :disabled="onlineBusy || onlineSession.session.status === 'paused'"
                  @keydown.enter.prevent="submitOnlineAnswer"
                />
              </label>
              <button
                type="button"
                class="primary-action"
                :disabled="
                  onlineBusy ||
                    onlineSession.session.status === 'paused' ||
                    onlineAnswer.trim().length === 0
                "
                @click="submitOnlineAnswer"
              >
                提交答案
              </button>
            </div>
            <div v-else class="rating-panel" aria-label="正式复习评分">
              <p>输入已保存。答案在评分前保持隐藏，请选择本次记忆评分。</p>
              <div class="rating-grid">
                <button
                  v-for="rating in (['again', 'hard', 'good', 'easy'] as const)"
                  :key="rating"
                  type="button"
                  :disabled="onlineBusy || reviewPreviews === undefined"
                  @click="submitOnlineRating(rating)"
                >
                  <strong>
                    {{
                      {
                        again: 'Again',
                        hard: 'Hard',
                        good: 'Good',
                        easy: 'Easy',
                      }[rating]
                    }}
                  </strong>
                  <span v-if="reviewPreviews">
                    {{ formatDue(reviewPreviews.previews[rating].dueAt) }}
                  </span>
                </button>
              </div>
            </div>
          </div>
          <p v-else class="account-state">本次在线会话已完成。</p>
        </div>

        <p v-else-if="onlinePlan?.selected.length === 0" class="account-state">
          当前没有到期或新词。可先在私人词库中增加学习目标。
        </p>
        <p v-if="onlineNotice" class="inline-notice" role="status">
          {{ onlineNotice }}
        </p>
      </section>

      <section class="data-tools" aria-labelledby="migration-title">
        <div>
          <p class="section-label">本地迁移</p>
          <h3 id="migration-title">上传本地 v4 数据</h3>
        </div>
        <label class="file-picker">
          <span>选择本地备份 JSON</span>
          <input
            type="file"
            accept="application/json,.json"
            :disabled="migrationBusy"
            @change="handleMigrationFile"
          />
        </label>
        <div v-if="migrationPreview" class="migration-preview">
          <p>
            {{ migrationFileName }} · FSRS-7 ·
            {{ migrationPreview.schedulerWeights }} 权重
          </p>
          <ul>
            <li v-for="[name, count] in Object.entries(migrationPreview.counts)" :key="name">
              {{ name }}: {{ count }}
            </li>
          </ul>
          <button type="button" @click="downloadMigrationBackup">
            下载迁移前本地备份
          </button>
          <label class="confirm-check">
            <input v-model="migrationConfirmed" type="checkbox" />
            <span>确认追加到当前账号，不覆盖已有云端数据</span>
          </label>
          <button
            type="button"
            class="primary-action"
            :disabled="
              migrationBusy ||
                !migrationBackupDownloaded ||
                !migrationConfirmed
            "
            @click="importMigration"
          >
            提交本地迁移
          </button>
        </div>
        <p v-if="migrationNotice" class="inline-notice" role="status">
          {{ migrationNotice }}
        </p>
      </section>

      <section class="data-tools" aria-labelledby="account-backup-title">
        <div>
          <p class="section-label">账号备份</p>
          <h3 id="account-backup-title">当前用户数据备份与恢复</h3>
        </div>
        <div class="online-toolbar">
          <button
            type="button"
            :disabled="accountBackupBusy"
            @click="exportAccountBackup"
          >
            导出账号备份
          </button>
          <label class="file-picker">
            <span>选择账号备份</span>
            <input
              type="file"
              accept="application/json,.json"
              :disabled="accountBackupBusy"
              @change="handleAccountBackupFile"
            />
          </label>
        </div>
        <div v-if="accountBackupPreview" class="migration-preview">
          <p>
            {{ accountBackupFileName }} · 数据版本
            {{ accountBackupPreview.sourceDataVersion }}
          </p>
          <ul>
            <li v-for="[name, count] in Object.entries(accountBackupPreview.counts)" :key="name">
              {{ name }}: {{ count }}
            </li>
          </ul>
          <label class="confirm-check">
            <input v-model="accountBackupConfirmed" type="checkbox" />
            <span>确认替换当前账号业务数据，账号与偏好保持不变</span>
          </label>
          <button
            type="button"
            class="primary-action"
            :disabled="accountBackupBusy || !accountBackupConfirmed"
            @click="restoreAccountBackup"
          >
            恢复账号备份
          </button>
        </div>
        <p v-if="accountBackupNotice" class="inline-notice" role="status">
          {{ accountBackupNotice }}
        </p>
      </section>
    </template>

    <p v-if="error" class="account-error" role="alert">{{ error }}</p>
  </section>
</template>

<style scoped>
.account-workspace {
  display: grid;
  gap: 18px;
}

.account-heading,
.cloud-heading,
.profile-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.account-heading h2,
.cloud-heading h3,
.data-tools h3 {
  margin: 0;
  color: #20352a;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 2rem;
  font-weight: 500;
}

.cloud-heading h3 {
  font-size: 1.35rem;
}

.data-tools h3 {
  margin: 0;
  color: #20352a;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 1.35rem;
  font-weight: 500;
}

.mode-badge,
.cloud-heading > span {
  color: #65776d;
  font-size: 0.76rem;
}

.auth-panel,
.cloud-lexicons,
.account-state {
  padding: 20px;
  border: 1px solid #d2dcd6;
  border-radius: 8px;
  background: #fbfcfb;
}

.auth-panel {
  max-width: 560px;
}

.online-training,
.data-tools {
  display: grid;
  gap: 16px;
  padding-top: 20px;
  border-top: 1px solid #d2dcd6;
}

.online-toolbar,
.online-session-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.online-toolbar > span,
.online-session-meta > span {
  color: #718078;
  font-size: 0.76rem;
}

.online-session {
  display: grid;
  gap: 12px;
}

.online-card {
  display: grid;
  gap: 14px;
  padding: 20px;
  border: 1px solid #c7d5cd;
  border-radius: 8px;
  background: #f7faf8;
}

.online-card.online-flashcard {
  padding: 0;
  border: 0;
  background: transparent;
}

.online-card > strong {
  color: #1c3025;
  font-size: 1.65rem;
  font-weight: 600;
}

.online-answer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 12px;
}

.rating-panel {
  display: grid;
  gap: 10px;
}

.rating-panel p,
.migration-preview p {
  margin: 0;
  color: #65776d;
  font-size: 0.78rem;
}

.rating-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.rating-grid button {
  display: grid;
  gap: 3px;
}

.rating-grid span {
  color: #738178;
  font-size: 0.68rem;
}

.file-picker {
  display: grid;
  gap: 6px;
}

.file-picker input {
  max-width: 100%;
  color: #586960;
  font-size: 0.76rem;
}

.migration-preview {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid #d2dcd6;
  border-radius: 8px;
  background: #fbfcfb;
}

.migration-preview ul {
  display: flex;
  gap: 8px 16px;
  flex-wrap: wrap;
  margin: 0;
  padding: 0;
  color: #65776d;
  font-size: 0.74rem;
  list-style: none;
}

.confirm-check {
  display: flex;
  grid-template-columns: none;
  align-items: center;
  gap: 8px;
  color: #52645a;
}

.inline-notice {
  margin: 0;
  padding: 9px 11px;
  border-left: 3px solid #3c8061;
  background: #edf5f0;
  color: #315b47;
  font-size: 0.78rem;
}

.auth-tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 18px;
}

.auth-tabs button.selected {
  border-color: #315b47;
  background: #e6efe9;
}

.auth-form,
.new-lexicon-form {
  display: grid;
  gap: 12px;
}

.new-lexicon-form {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.5fr) auto;
  align-items: end;
  margin: 18px 0;
}

label {
  display: grid;
  gap: 6px;
  color: #586960;
  font-size: 0.74rem;
  font-weight: 700;
}

input {
  min-height: 42px;
  padding: 8px 11px;
  border: 1px solid #b9c6be;
  border-radius: 6px;
  background: #fff;
  color: #21362b;
}

button {
  min-height: 38px;
  padding: 8px 12px;
  border: 1px solid #bfcbc4;
  border-radius: 6px;
  background: #fff;
  color: #30453a;
  font-weight: 650;
  cursor: pointer;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

button:focus-visible,
input:focus-visible {
  outline: 3px solid rgb(45 113 83 / 28%);
  outline-offset: 2px;
}

.primary-action {
  border-color: #28513e;
  background: #28513e;
  color: #fff;
}

.profile-strip {
  padding: 14px 16px;
  border: 1px solid #cbd8d0;
  border-radius: 8px;
  background: #edf3ef;
}

.profile-strip > div {
  display: flex;
  align-items: center;
  gap: 12px;
}

.profile-strip div div {
  display: grid;
  gap: 2px;
}

.profile-strip span:not(.profile-avatar) {
  color: #718078;
  font-size: 0.75rem;
}

.profile-avatar {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border-radius: 8px;
  background: #17372a;
  color: #f6faf8;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 1.25rem;
}

.lexicon-list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.lexicon-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border: 1px solid #d8e0db;
  border-radius: 6px;
  background: #fff;
}

.lexicon-list li div {
  display: grid;
  gap: 3px;
}

.lexicon-list span,
.lexicon-list code {
  color: #74827a;
  font-size: 0.72rem;
}

.lexicon-list code {
  overflow-wrap: anywhere;
}

.account-state {
  color: #697970;
}

.account-error {
  margin: 0;
  padding: 10px 12px;
  border-left: 3px solid #ad5b42;
  background: #fbf1ed;
  color: #8d4634;
  font-size: 0.8rem;
}

@media (max-width: 720px) {
  .account-heading,
  .profile-strip,
  .lexicon-list li {
    align-items: flex-start;
    flex-direction: column;
  }

  .new-lexicon-form {
    grid-template-columns: 1fr;
  }

  .online-answer,
  .rating-grid {
    grid-template-columns: 1fr;
  }

  .new-lexicon-form button {
    width: 100%;
  }
}
</style>
