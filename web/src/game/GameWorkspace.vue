<script setup lang="ts">
import {
  onBeforeUnmount,
  onMounted,
  ref,
} from 'vue';
import {
  CircleHelp,
  LogIn,
  RefreshCw,
  ShieldCheck,
  TriangleAlert,
  UserPlus,
} from '@lucide/vue';
import {
  accountApi,
  type AccountClient,
} from '../api/client';
import CompanionPortrait from '../companions/CompanionPortrait.vue';
import AdventureJourney from '../adventure/AdventureJourney.vue';
import { useAdventureJourney } from '../adventure/useAdventureJourney';
import innBackground from '../assets/scenes/inn/runtime/inn-warm-lantern.jpg';
import InnLobby from '../inn/InnLobby.vue';
import GameResult from './GameResult.vue';
import GameShortcutHelp from './GameShortcutHelp.vue';
import GameTrainingStage from './GameTrainingStage.vue';
import { gameErrorMessage } from './gameErrorMessage';
import { useGameAuthentication } from './useGameAuthentication';
import { useCompanionPreferences } from './useCompanionPreferences';
import { useGameLobby } from './useGameLobby';
import { useGamePracticeSession } from './useGamePracticeSession';
import {
  resolveGameShortcut,
  type GamePhase,
  type GameShortcutAction,
} from './shortcutStateMachine';

const props = withDefaults(
  defineProps<{
    api?: AccountClient;
  }>(),
  {
    api: () => accountApi,
  },
);

const emit = defineEmits<{
  'focus-mode': [enabled: boolean];
  'classic-study': [];
  'account-settings': [];
}>();

const {
  selectedCompanion,
  companionDensity,
  selectCompanion,
  selectCompanionDensity,
} = useCompanionPreferences();
const {
  user,
  initializing,
  authenticating,
  error: authError,
  mode: authMode,
  username,
  displayName,
  password,
  initialize: initializeAuthentication,
  submit: submitAuthentication,
  dispose: disposeAuthentication,
} = useGameAuthentication({
  api: () => props.api,
  describeError: gameErrorMessage,
});
const phase = ref<GamePhase>('login');
const helpOpen = ref(false);
const focusMode = ref(false);
const reduceMotion = ref(false);
const trainingStage = ref<InstanceType<typeof GameTrainingStage>>();
const {
  plan,
  lexicons,
  sessions,
  contentPackages,
  adventures,
  loading: lobbyLoading,
  error: lobbyError,
  activeSession,
  load: loadLobby,
  fetchActiveSessions,
  dispose: disposeLobby,
} = useGameLobby({
  api: () => props.api,
  describeError: gameErrorMessage,
  timeZone: currentTimeZone,
});
const {
  run: adventureRun,
  session: adventureSession,
  loading: adventureLoading,
  busy: adventureBusy,
  error: adventureError,
  notice: adventureNotice,
  start: startAdventureRecord,
  resume: resumeAdventureRecord,
  setSession: setAdventureSession,
  choose: chooseAdventure,
  advance: advanceAdventure,
  startEncounter: startAdventureEncounter,
  resolveEncounter: resolveAdventureEncounter,
  completeChapter: completeAdventureChapter,
  reset: resetAdventure,
  dispose: disposeAdventure,
} = useAdventureJourney({
  api: () => props.api,
  describeError: gameErrorMessage,
});
const {
  session,
  currentItem,
  currentHint,
  answer,
  pendingAnswer,
  reviewPreviews,
  hintUsed,
  answerRevealed,
  busy,
  error: sessionError,
  combo,
  reviewedCount,
  practiceCount,
  result,
  lastRating,
  enterSession,
  startSession,
  resumeSession,
  pauseSession,
  submitFormalAnswer,
  retryReviewPreview,
  rateReview,
  completePractice,
  showNextHint,
  revealAnswer,
  skipItem,
  reloadSession: reloadPracticeSession,
  pauseForExit,
  pauseForUnmount,
  resetSession,
  dispose: disposePracticeSession,
} = useGamePracticeSession({
  api: () => props.api,
  describeError: gameErrorMessage,
  phase,
  reduceMotion,
  activeSession: () => activeSession.value,
  scrollToTop: scrollPageToTop,
  requestAnswerFocus: requestAnswerFocus,
  onSessionCompleted: leaveFocusAfterSession,
});

function currentTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

function scrollPageToTop(): void {
  document.documentElement.scrollTop = 0;
  document.body.scrollTop = 0;
}

function requestAnswerFocus(): void {
  trainingStage.value?.focusAnswer();
}

function leaveFocusAfterSession(): void {
  if (focusMode.value) {
    setFocusMode(false);
  }
}

async function initializeGame(): Promise<void> {
  reduceMotion.value = window.matchMedia?.(
    '(prefers-reduced-motion: reduce)',
  ).matches ?? false;
  const authenticatedUser = await initializeAuthentication();
  if (authenticatedUser === null) {
    phase.value = 'login';
    return;
  }
  await loadLobby();
  phase.value = 'lobby';
}

async function submitAuth(): Promise<void> {
  const authenticatedUser = await submitAuthentication();
  if (authenticatedUser === null) {
    return;
  }
  await loadLobby();
  phase.value = 'lobby';
}

async function trainAgain(): Promise<void> {
  phase.value = 'lobby';
  resetSession();
  await loadLobby();
  if (plan.value?.selected.length) {
    await startTraining();
  }
}

async function startTraining(): Promise<void> {
  await startSession(plan.value);
}

async function startAdventure(installationId: string): Promise<void> {
  if (adventureBusy.value) {
    return;
  }
  const started = await startAdventureRecord({
    installationId,
    companionKey: selectedCompanion.value.id,
    reduceMotion: reduceMotion.value,
  });
  if (started) {
    helpOpen.value = false;
    phase.value = 'adventure';
    scrollPageToTop();
  }
}

async function resumeAdventure(runId: string): Promise<void> {
  if (adventureBusy.value) {
    return;
  }
  const resumed = await resumeAdventureRecord(runId);
  if (resumed) {
    helpOpen.value = false;
    phase.value = 'adventure';
    scrollPageToTop();
  }
}

async function leaveAdventureToLobby(): Promise<void> {
  if (adventureBusy.value) {
    return;
  }
  scrollPageToTop();
  resetAdventure();
  phase.value = 'lobby';
  await loadLobby();
}

async function reloadSession(): Promise<void> {
  const result = await reloadPracticeSession(fetchActiveSessions);
  if (result === 'missing') {
    await returnToLobby();
  }
}

async function returnToLobby(): Promise<void> {
  scrollPageToTop();
  leaveFocusAfterSession();
  resetSession();
  phase.value = 'lobby';
  await loadLobby();
}

async function leaveTrainingToLobby(): Promise<void> {
  if (session.value === undefined || busy.value) {
    return;
  }
  if (await pauseForExit()) {
    await returnToLobby();
  }
}

function toggleHelp(): void {
  helpOpen.value = !helpOpen.value;
}

function toggleFocus(): void {
  setFocusMode(!focusMode.value);
}

function setFocusMode(enabled: boolean): void {
  focusMode.value = enabled;
  emit('focus-mode', enabled);
}

function performShortcut(action: GameShortcutAction): void {
  switch (action) {
    case 'submit-answer':
      void submitFormalAnswer();
      return;
    case 'complete-practice':
      void completePractice('completed');
      return;
    case 'rate-again':
      void rateReview('again');
      return;
    case 'rate-hard':
      void rateReview('hard');
      return;
    case 'rate-good':
      void rateReview('good');
      return;
    case 'rate-easy':
      void rateReview('easy');
      return;
    case 'show-hint':
      showNextHint();
      return;
    case 'reveal-answer':
      revealAnswer();
      return;
    case 'skip-item':
      void skipItem();
      return;
    case 'pause':
      void pauseSession();
      return;
    case 'resume':
      void resumeSession();
      return;
    case 'toggle-help':
      toggleHelp();
      return;
    case 'close-help':
      helpOpen.value = false;
      return;
    case 'start-or-resume':
      void startTraining();
      return;
    case 'return-lobby':
      void returnToLobby();
      return;
    case 'toggle-focus':
      toggleFocus();
  }
}

function handleGlobalKeydown(event: KeyboardEvent): void {
  if (phase.value === 'adventure') {
    return;
  }
  const target = event.target;
  const isEditable = target instanceof HTMLElement
    && (
      target.matches('input, textarea, select')
      || target.isContentEditable
    );
  const action = resolveGameShortcut({
    key: event.key,
    phase: phase.value,
    isComposing: event.isComposing,
    repeat: event.repeat,
    isEditable,
    helpOpen: helpOpen.value,
    ctrlKey: event.ctrlKey,
    metaKey: event.metaKey,
    shiftKey: event.shiftKey,
    altKey: event.altKey,
  });
  if (action === null) {
    return;
  }
  event.preventDefault();
  performShortcut(action);
}

onMounted(() => {
  window.addEventListener('keydown', handleGlobalKeydown);
  void initializeGame();
});

onBeforeUnmount(() => {
  void pauseForUnmount();
  disposeLobby();
  disposeAdventure();
  disposePracticeSession();
  disposeAuthentication();
  window.removeEventListener('keydown', handleGlobalKeydown);
  if (focusMode.value) {
    emit('focus-mode', false);
  }
});
</script>

<template>
  <section
    class="game-workspace"
    :class="{ 'focus-background': focusMode }"
    :style="{ '--game-backdrop': `url(${innBackground})` }"
  >
    <div v-if="initializing" class="game-loading" role="status">
      <span aria-hidden="true"></span>
      <p>正在回到暖灯旅店...</p>
    </div>

    <div v-else-if="phase === 'login'" class="auth-stage">
      <div class="auth-copy">
        <p class="eyebrow">Warm Lantern Inn</p>
        <h1>登录后，回到暖灯旅店。</h1>
        <p>
          莉娅会把正式笔记、到期时间和会话书签收在账号里。本地模式仍保持独立。
        </p>
        <div class="auth-feature-list">
          <div>
            <ShieldCheck :size="18" aria-hidden="true" />
            <span>HttpOnly 会话与 CSRF 防护</span>
          </div>
          <div>
            <RefreshCw :size="18" aria-hidden="true" />
            <span>跨设备续接同一份旅途笔记</span>
          </div>
        </div>
      </div>
      <div class="auth-card">
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
        <form @submit.prevent="submitAuth">
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
            <input
              v-model="displayName"
              type="text"
              autocomplete="nickname"
              maxlength="80"
            />
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
          <p v-if="authError" class="auth-error" role="alert">
            <TriangleAlert :size="17" />
            {{ authError }}
          </p>
          <button type="submit" class="primary-action" :disabled="authenticating">
            <UserPlus v-if="authMode === 'register'" :size="18" />
            <LogIn v-else :size="18" />
            {{ authMode === 'register' ? '创建旅团账号' : '回到暖灯旅店' }}
          </button>
        </form>
      </div>
      <CompanionPortrait
        class="auth-companion"
        :companion="selectedCompanion"
        expression="welcome"
        :reduce-motion="reduceMotion"
      />
    </div>

    <InnLobby
      v-else-if="phase === 'lobby' && user"
      :companion="selectedCompanion"
      :user="user"
      :plan="plan"
      :lexicons-count="lexicons.length"
      :active-session="activeSession"
      :session-count="sessions.length"
      :content-packages="contentPackages"
      :adventures="adventures"
      :companion-density="companionDensity"
      :reduce-motion="reduceMotion"
      :loading="busy || lobbyLoading"
      :error="lobbyError ?? sessionError"
      @start="startTraining"
      @resume="enterSession"
      @help="toggleHelp"
      @select-companion="selectCompanion"
      @select-density="selectCompanionDensity"
      @classic-study="emit('classic-study')"
      @start-adventure="startAdventure"
      @resume-adventure="resumeAdventure"
      @content-settings="emit('account-settings')"
    />

    <AdventureJourney
      v-else-if="phase === 'adventure' && adventureRun"
      :api="api"
      :run="adventureRun"
      :session="adventureSession"
      :companion="selectedCompanion"
      :loading="adventureLoading"
      :busy="adventureBusy"
      :error="adventureError"
      :notice="adventureNotice"
      :enable-audio="true"
      :reduce-motion="reduceMotion"
      @choose="chooseAdventure"
      @advance="advanceAdventure"
      @start-encounter="startAdventureEncounter"
      @resolve-encounter="resolveAdventureEncounter"
      @complete="completeAdventureChapter"
      @exit="leaveAdventureToLobby"
      @session-changed="setAdventureSession"
    />

    <GameTrainingStage
      v-else-if="session && phase !== 'result'"
      ref="trainingStage"
      :session="session"
      :item="currentItem"
      :phase="phase"
      :answer="answer"
      :pending-answer="pendingAnswer"
      :review-previews="reviewPreviews"
      :hint-text="currentHint"
      :hint-used="hintUsed"
      :answer-revealed="answerRevealed"
      :busy="busy"
      :error="sessionError"
      :combo="combo"
      :reviewed-count="reviewedCount"
      :practice-count="practiceCount"
      :focus-mode="focusMode"
      :reduce-motion="reduceMotion"
      :companion="selectedCompanion"
      :last-rating="lastRating"
      @update:answer="answer = $event"
      @submit="submitFormalAnswer"
      @hint="showNextHint"
      @reveal="revealAnswer"
      @skip="skipItem"
      @rate="rateReview"
      @pause="pauseSession"
      @resume="resumeSession"
      @exit="leaveTrainingToLobby"
      @help="toggleHelp"
      @retry-preview="retryReviewPreview"
      @reload-session="reloadSession"
      @toggle-focus="toggleFocus"
    />

    <GameResult
      v-else-if="phase === 'result'"
      :companion="selectedCompanion"
      :result="result"
      :reduce-motion="reduceMotion"
      :last-rating="lastRating"
      @again="trainAgain"
      @lobby="returnToLobby"
    />

    <button
      v-if="phase === 'lobby' || phase === 'result'"
      type="button"
      class="global-help"
      aria-label="打开快捷键帮助"
      @click="toggleHelp"
    >
      <CircleHelp :size="19" />
    </button>

    <GameShortcutHelp :open="helpOpen" @close="helpOpen = false" />
  </section>
</template>

<style scoped>
.game-workspace {
  position: relative;
  min-height: calc(100vh - 130px);
  margin: calc(clamp(20px, 3vw, 34px) * -1);
  padding: clamp(20px, 3vw, 34px);
  overflow: hidden;
  border: 1px solid rgb(217 183 121 / 22%);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgb(24 15 15 / 28%), rgb(20 12 14 / 66%)),
    var(--game-backdrop) center / cover no-repeat,
    #171014;
}

.game-workspace::before {
  position: absolute;
  inset: 0;
  z-index: 0;
  background:
    radial-gradient(circle at 21% 18%, rgb(235 200 142 / 8%), transparent 32%),
    linear-gradient(90deg, rgb(21 13 14 / 22%), transparent 38% 72%, rgb(21 13 14 / 18%));
  content: "";
  pointer-events: none;
}

.game-workspace > :not(.global-help) {
  position: relative;
  z-index: 1;
}

.focus-background::before {
  opacity: 0.32;
}

.game-loading {
  display: grid;
  min-height: 560px;
  place-content: center;
  justify-items: center;
  gap: 14px;
  color: #c6b49d;
}

.game-loading span {
  width: 42px;
  height: 42px;
  border: 2px solid rgb(219 183 122 / 18%);
  border-top-color: #d9b779;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.game-loading p {
  margin: 0;
  font-size: 0.8rem;
}

.auth-stage {
  position: relative;
  display: grid;
  grid-template-columns: minmax(330px, 1fr) minmax(300px, 360px) minmax(210px, 280px);
  gap: clamp(20px, 3vw, 42px);
  min-height: calc(100vh - 210px);
  align-items: center;
  max-width: 1180px;
  margin: 0 auto;
}

.auth-copy {
  max-width: 680px;
}

.eyebrow {
  margin: 0 0 13px;
  color: #d9b779;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.auth-copy h1 {
  margin: 0;
  color: #fff0d7;
  font-family: "Songti SC", "Noto Serif CJK SC", Georgia, serif;
  font-size: clamp(2.8rem, 5.4vw, 5rem);
  font-weight: 600;
  letter-spacing: 0;
  line-height: 1.05;
  text-wrap: balance;
}

.auth-copy > p:not(.eyebrow) {
  max-width: 56ch;
  margin: 20px 0 0;
  color: #d2c0a8;
  line-height: 1.85;
}

.auth-feature-list {
  display: grid;
  gap: 9px;
  margin-top: 24px;
}

.auth-feature-list div {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #d9c9b4;
  font-size: 0.8rem;
}

.auth-feature-list svg {
  color: #d9b779;
}

.auth-card {
  padding: 22px;
  border: 1px solid rgb(217 183 121 / 30%);
  border-radius: 8px;
  background: rgb(35 24 22 / 90%);
  backdrop-filter: blur(14px);
  box-shadow: 0 24px 70px rgb(24 12 12 / 38%);
}

.auth-companion {
  width: min(100%, 280px);
  height: calc(100vh - 220px);
  align-self: end;
  justify-self: center;
  opacity: 0.9;
}

.auth-tabs {
  display: flex;
  gap: 5px;
  margin-bottom: 18px;
}

.auth-tabs button {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #b5a087;
  cursor: pointer;
}

.auth-tabs button.selected {
  border-color: rgb(217 183 121 / 34%);
  background: rgb(217 183 121 / 10%);
  color: #ffebca;
}

.auth-card form {
  display: grid;
  gap: 13px;
}

.auth-card label {
  display: grid;
  gap: 6px;
  color: #cbb79c;
  font-size: 0.72rem;
  font-weight: 700;
}

.auth-card input {
  min-height: 46px;
  padding: 0 12px;
  border: 1px solid rgb(195 165 119 / 32%);
  border-radius: 6px;
  background: #fff3df;
  color: #2d211c;
}

.auth-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0;
  padding: 9px 10px;
  border-left: 3px solid #f07878;
  background: rgb(240 120 120 / 10%);
  color: #ffd6d6;
  font-size: 0.76rem;
}

.primary-action {
  display: inline-flex;
  min-height: 46px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 16px;
  border: 1px solid #d9b779;
  border-radius: 6px;
  background: #d9b779;
  color: #302017;
  font-weight: 800;
  cursor: pointer;
}

.primary-action:disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

.global-help {
  position: fixed;
  z-index: 15;
  right: 20px;
  bottom: 20px;
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  padding: 0;
  border: 1px solid rgb(222 192 145 / 30%);
  border-radius: 50%;
  background: rgb(34 23 22 / 84%);
  color: #e4d0b2;
  cursor: pointer;
  backdrop-filter: blur(8px);
}

button:focus-visible,
input:focus-visible {
  outline: 3px solid rgb(231 195 132 / 54%);
  outline-offset: 2px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 860px) {
  .auth-stage {
    grid-template-columns: 1fr;
    align-content: start;
    padding: 30px 0;
  }

  .auth-card {
    width: min(100%, 540px);
  }

  .auth-companion {
    position: absolute;
    right: -18px;
    bottom: -18px;
    display: block;
    width: 130px;
    height: 190px;
    opacity: 0.45;
    pointer-events: none;
  }

  .auth-copy,
  .auth-card {
    position: relative;
    z-index: 1;
  }
}

@media (max-width: 620px) {
  .game-workspace {
    margin: -18px;
    min-height: calc(100vh - 110px);
    padding: 18px;
    border-radius: 0;
  }

  .auth-copy h1 {
    font-size: 2.55rem;
  }

  .auth-companion {
    right: -24px;
    width: 104px;
    height: 152px;
    opacity: 0.32;
  }
}

@media (prefers-reduced-motion: reduce) {
  .game-loading span {
    animation: none;
  }
}
</style>
