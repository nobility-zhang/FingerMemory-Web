<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  toRef,
} from 'vue';
import { CircleHelp } from '@lucide/vue';
import {
  accountApi,
  ApiClientError,
  type AccountClient,
  type BattleEnvelopeView,
  type BattleStateView,
  type PendingAnswerView,
  type ReviewPreviewView,
  type SessionEnvelope,
  type SessionItemView,
} from '../api/client';
import CompanionPortrait from '../companions/CompanionPortrait.vue';
import {
  companionById,
  type CompanionExpression,
  type CompanionId,
} from '../companions/catalog';
import BattleShortcutHelp from './BattleShortcutHelp.vue';
import { CARD_DEFINITIONS } from './cards';
import { battleSoundCues } from './soundEvents';
import {
  endTurnShortcutAriaLabel,
  endTurnShortcutLabel,
  loadBattleShortcutPreferences,
  saveBattleShortcutPreferences,
} from './shortcutPreferences';
import {
  resolveBattleShortcut,
  type BattleShortcutAction,
  type BattleShortcutContext,
  type EndTurnShortcut,
} from './shortcutStateMachine';
import type { CardDefinition } from './types';
import PhaserGameHost from '../game-runtime/PhaserGameHost.vue';
import { usePhaserAudio } from '../game-runtime/usePhaserAudio';

type BattleApi = Pick<
  AccountClient,
  | 'createBattle'
  | 'getSession'
  | 'submitPracticeResponse'
  | 'previewReview'
  | 'submitReview'
  | 'claimBattleLearning'
  | 'playBattleCard'
  | 'useBattleCompanionSkill'
  | 'endBattleTurn'
>;

const props = withDefaults(
  defineProps<{
    api?: BattleApi;
    session: SessionEnvelope;
    companionKey?: CompanionId;
    enableAudio?: boolean;
  }>(),
  {
    api: () => accountApi,
    companionKey: 'lia',
    enableAudio: false,
  },
);

const emit = defineEmits<{
  sessionChanged: [session: SessionEnvelope];
  finished: [];
}>();

const battle = ref<BattleEnvelopeView>();
const learningSession = ref<SessionEnvelope>();
const loading = ref(true);
const busy = ref(false);
const error = ref<string | null>(null);
const notice = ref<string | null>(null);
const answer = ref('');
const pendingAnswer = ref<PendingAnswerView>();
const reviewPreviews = ref<ReviewPreviewView>();
const selectedCard = ref<BattleEnvelopeView['battle']['hand'][number]>();
const battleRoot = ref<HTMLElement>();
const selectedTargetIndex = ref(0);
const battlePaused = ref(false);
const shortcutHelpOpen = ref(false);
const shortcutPreferences = ref(loadBattleShortcutPreferences());
const resumeButton = ref<HTMLButtonElement>();
let focusBeforePause: HTMLElement | null = null;
let generation = 0;
let disposed = false;
let claimActionId = '';

const audio = usePhaserAudio(toRef(props, 'enableAudio'));
const {
  muted: audioMuted,
  volume: audioVolume,
  paused: audioPaused,
  status: audioStatus,
  rateLimitedCount: audioRateLimitedCount,
  setHost: setAudioHost,
  toggleMuted: toggleAudioMuted,
  playSound: playBattleSound,
  setVolume: setAudioVolume,
  setPaused: setAudioPaused,
  handleRuntimeEvent: handleAudioRuntimeEvent,
} = audio;

const currentItem = computed<SessionItemView | undefined>(() => {
  const task = battle.value?.battle.learningTask;
  if (task === null || task === undefined) {
    return undefined;
  }
  return learningSession.value?.items.find(
    (item) => item.studyItem.id === task.studyItemId,
  );
});
const currentEnemy = computed(() => battle.value?.battle.enemies[0]);
const handCards = computed(() =>
  (battle.value?.battle.hand ?? [])
    .map((card) => ({
      card,
      definition: CARD_DEFINITIONS[card.definitionId],
    }))
    .filter(
      (entry): entry is {
        card: BattleEnvelopeView['battle']['hand'][number];
        definition: CardDefinition;
      } => entry.definition !== undefined,
    ),
);
const legalTargets = computed(() =>
  (battle.value?.battle.enemies ?? []).filter((enemy) => enemy.hp > 0),
);
const selectedTarget = computed(() => {
  const targets = legalTargets.value;
  if (targets.length === 0) {
    return undefined;
  }
  return targets[selectedTargetIndex.value % targets.length];
});
const companion = computed(() => companionById[props.companionKey]);
const companionExpression = computed<CompanionExpression>(() => {
  if (battle.value?.battle.phase === 'victory') {
    return 'cheer';
  }
  if (battle.value?.battle.phase === 'defeat') {
    return 'concern';
  }
  if (battle.value?.battle.phase === 'learning') {
    return 'concern';
  }
  return 'relief';
});
const phaseLabel = computed(() => {
  switch (battle.value?.battle.phase) {
    case 'learning':
      return '学习行动';
    case 'player-action':
      return '玩家行动';
    case 'victory':
      return '迷雾散去';
    case 'defeat':
      return '需要救援';
    default:
      return '准备中';
  }
});
const shortcutContext = computed<BattleShortcutContext>(() => {
  if (battlePaused.value) {
    return 'paused';
  }
  if (loading.value || battle.value === undefined) {
    return 'blocked';
  }
  if (battle.value.battle.phase === 'learning') {
    return pendingAnswer.value === undefined ? 'learning-input' : 'rating';
  }
  if (battle.value.battle.phase === 'player-action') {
    return selectedCard.value === undefined ? 'card-select' : 'target-select';
  }
  if (
    battle.value.battle.phase === 'victory'
    || battle.value.battle.phase === 'defeat'
  ) {
    return 'result';
  }
  return 'blocked';
});

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
  return '战斗请求失败，请稍后重试。';
}

function formatDue(timestamp: number): string {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(timestamp));
}

function cardEffectText(definition: CardDefinition): string {
  return definition.effects
    .map((effect) => {
      switch (effect.type) {
        case 'damage':
          return `伤害 ${effect.amount}`;
        case 'block':
          return `格挡 ${effect.amount}`;
        case 'heal':
          return `回复 ${effect.amount}`;
        case 'draw':
          return `抽 ${effect.amount}`;
        case 'apply-burn':
          return `灼烧 ${effect.remainingTurns} 回合`;
        default:
          return '';
      }
    })
    .filter(Boolean)
    .join(' · ');
}

function intentText(state: BattleStateView): string {
  const intent = state.currentIntent;
  return intent.type === 'attack'
    ? `攻击 ${intent.value}`
    : `获得格挡 ${intent.value}`;
}

async function loadLearningSession(sessionId: string): Promise<void> {
  const next = await props.api.getSession(sessionId);
  learningSession.value = next;
  emit('sessionChanged', next);
}

function playBattleEvents(
  events: ReadonlyArray<Record<string, unknown>>,
  prefix: string,
): void {
  for (const cue of battleSoundCues(events, prefix)) {
    playBattleSound(cue.soundKey, cue.eventId, cue.volumeScale);
  }
}

async function startBattle(): Promise<void> {
  const initial = props.session.session;
  if (
    initial.status !== 'active'
    || initial.currentStudyItemId === null
    || initial.pendingAnswer !== null
  ) {
    error.value = '当前在线会话没有可开始战斗的学习目标。';
    loading.value = false;
    return;
  }
  const lifecycle = ++generation;
  loading.value = true;
  error.value = null;
  try {
    const created = await props.api.createBattle({
      battleId: newId(),
      encounterId: 'bridge-guardian',
      sessionId: initial.id,
      studyItemId: initial.currentStudyItemId,
      companionKey: props.companionKey,
      seed: 42,
      shuffle: false,
      actionId: newId(),
    });
    if (disposed || lifecycle !== generation) {
      return;
    }
    battle.value = created;
    playBattleEvents(created.events, `battle-start:${created.battle.battleId}`);
    await loadLearningSession(initial.id);
    notice.value = '溪桥迷雾守卫挡住了去路，先完成这一轮学习。';
  } catch (cause) {
    if (!disposed && lifecycle === generation) {
      error.value = errorMessage(cause);
    }
  } finally {
    if (!disposed && lifecycle === generation) {
      loading.value = false;
    }
  }
}

async function submitAnswer(): Promise<void> {
  const state = battle.value?.battle;
  const item = currentItem.value;
  const session = learningSession.value;
  if (
    busy.value
    || state === undefined
    || state.phase !== 'learning'
    || item === undefined
    || session === undefined
    || answer.value.trim().length === 0
  ) {
    return;
  }
  busy.value = true;
  error.value = null;
  notice.value = null;
  try {
    const result = await props.api.submitPracticeResponse({
      sessionId: session.session.id,
      practiceAttemptId: newId(),
      reviewSubmissionId: newId(),
      studyItemId: item.studyItem.id,
      response: answer.value,
      assessmentType: 'typed-recall',
      assessmentPolicyVersion: 2,
      assessmentStage: 'attempt',
      priorExposure: false,
      explicitNotRemembered: false,
      expectedDataVersion: session.dataVersion,
      expectedSessionVersion: session.session.version,
    });
    const nextSession = {
      ...session,
      session: result.session,
      dataVersion: result.dataVersion,
    };
    learningSession.value = nextSession;
    emit('sessionChanged', nextSession);
    pendingAnswer.value = result.pendingAnswer;
    answer.value = '';
    reviewPreviews.value = await props.api.previewReview({
      sessionId: nextSession.session.id,
      studyItemId: result.pendingAnswer.studyItemId,
      expectedDataVersion: nextSession.dataVersion,
      expectedSessionVersion: nextSession.session.version,
      expectedReviewVersion: item.reviewVersion,
    });
    notice.value = '答案已保存，请选择本次回忆评分。评分不会改变基础能量。';
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function submitRating(
  rating: 'again' | 'hard' | 'good' | 'easy',
): Promise<void> {
  const state = battle.value;
  const session = learningSession.value;
  const pending = pendingAnswer.value;
  const previews = reviewPreviews.value;
  if (
    busy.value
    || state === undefined
    || session === undefined
    || pending === undefined
    || previews === undefined
  ) {
    return;
  }
  busy.value = true;
  error.value = null;
  notice.value = null;
  claimActionId ||= newId();
  try {
    await props.api.submitReview({
      sessionId: session.session.id,
      submissionId: pending.reviewSubmissionId,
      rating,
      assessmentType: pending.assessmentType,
      assessmentPolicyVersion: pending.assessmentPolicyVersion,
      assessmentStage: pending.assessmentStage,
      priorExposure: pending.priorExposure,
      explicitNotRemembered: pending.explicitNotRemembered,
      answerWasHidden: pending.answerWasHidden,
      hintUsed: pending.hintUsed,
      answerRevealed: pending.answerRevealed,
      expectedDataVersion: previews.dataVersion,
      expectedReviewVersion: previews.reviewVersion,
      expectedSessionVersion: previews.sessionVersion,
    });
    const reviewedSession = await props.api.getSession(session.session.id);
    learningSession.value = reviewedSession;
    emit('sessionChanged', reviewedSession);
    const claimed = await props.api.claimBattleLearning({
      battleId: state.battle.battleId,
      actionId: claimActionId,
      expectedStateVersion: state.stateVersion,
      submissionId: pending.reviewSubmissionId,
    });
    battle.value = claimed;
    pendingAnswer.value = undefined;
    reviewPreviews.value = undefined;
    const completedClaimActionId = claimActionId;
    claimActionId = '';
    playBattleEvents(claimed.events, `learning-claim:${completedClaimActionId}`);
    notice.value = '学习行动完成，获得 3 点能量。现在选择你的战术牌。';
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

function chooseCard(card: BattleEnvelopeView['battle']['hand'][number]): void {
  const state = battle.value?.battle;
  if (busy.value || state === undefined || state.phase !== 'player-action') {
    return;
  }
  const definition = CARD_DEFINITIONS[card.definitionId];
  if (definition === undefined) {
    error.value = '未知战术牌。';
    return;
  }
  if (state.energy < definition.cost) {
    error.value = '当前能量不足。';
    return;
  }
  if (definition.target === 'enemy') {
    selectedCard.value = card;
    selectedTargetIndex.value = 0;
    notice.value = '选择目标后确认出牌。';
    return;
  }
  void playCard(card, undefined);
}

function cancelTargetSelection(): void {
  selectedCard.value = undefined;
  selectedTargetIndex.value = 0;
  notice.value = '已取消目标选择。';
}

function cycleTarget(direction: 'previous' | 'next'): void {
  const targets = legalTargets.value;
  if (targets.length === 0) {
    return;
  }
  const delta = direction === 'next' ? 1 : -1;
  selectedTargetIndex.value = (
    selectedTargetIndex.value + delta + targets.length
  ) % targets.length;
  const target = selectedTarget.value;
  if (target !== undefined) {
    notice.value = `目标：${target.id} · 生命 ${target.hp}`;
  }
}

async function playCard(
  card: BattleEnvelopeView['battle']['hand'][number],
  targetId: string | undefined,
): Promise<void> {
  const current = battle.value;
  if (busy.value || current === undefined || current.battle.phase !== 'player-action') {
    return;
  }
  busy.value = true;
  error.value = null;
  notice.value = null;
  try {
    const actionId = newId();
    const updated = await props.api.playBattleCard({
      battleId: current.battle.battleId,
      actionId,
      expectedStateVersion: current.stateVersion,
      cardInstanceId: card.instanceId,
      targetId,
    });
    battle.value = updated;
    selectedCard.value = undefined;
    selectedTargetIndex.value = 0;
    playBattleEvents(updated.events, `card:${actionId}`);
    notice.value = '战术牌已结算。';
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function playSelectedCard(): Promise<void> {
  const card = selectedCard.value;
  const target = selectedTarget.value;
  if (card === undefined || target === undefined) {
    return;
  }
  await playCard(card, target.id);
}

async function useCompanionSkill(): Promise<void> {
  const current = battle.value;
  if (busy.value || current === undefined || current.battle.phase !== 'player-action') {
    return;
  }
  busy.value = true;
  error.value = null;
  notice.value = null;
  try {
    const actionId = newId();
    battle.value = await props.api.useBattleCompanionSkill({
      battleId: current.battle.battleId,
      actionId,
      expectedStateVersion: current.stateVersion,
    });
    playBattleEvents(battle.value.events, `companion:${actionId}`);
    notice.value = `${companion.value.name}的协助已经生效。`;
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function endTurn(): Promise<void> {
  const current = battle.value;
  const session = learningSession.value;
  if (
    busy.value
    || current === undefined
    || current.battle.phase !== 'player-action'
    || selectedCard.value !== undefined
    || session === undefined
    || session.session.currentStudyItemId === null
  ) {
    if (session?.session.currentStudyItemId === null) {
      error.value = '当前会话没有下一学习目标，无法继续下一回合。';
    }
    return;
  }
  busy.value = true;
  error.value = null;
  notice.value = null;
  try {
    const actionId = newId();
    const updated = await props.api.endBattleTurn({
      battleId: current.battle.battleId,
      actionId,
      expectedStateVersion: current.stateVersion,
      nextSessionId: session.session.id,
      nextStudyItemId: session.session.currentStudyItemId,
    });
    battle.value = updated;
    selectedCard.value = undefined;
    selectedTargetIndex.value = 0;
    playBattleEvents(updated.events, `turn:${actionId}`);
    if (
      updated.battle.phase === 'learning'
      && updated.battle.learningTask !== null
    ) {
      await loadLearningSession(updated.battle.learningTask.sessionId);
      notice.value = '敌方行动结束，下一轮学习任务已经冻结。';
    }
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

function finish(): void {
  emit('finished');
}

function updateEndTurnShortcut(shortcut: EndTurnShortcut): void {
  shortcutPreferences.value = saveBattleShortcutPreferences({
    endTurn: shortcut,
  });
}

function pauseBattle(): void {
  if (
    battlePaused.value
    || loading.value
    || busy.value
    || battle.value === undefined
    || shortcutContext.value === 'result'
  ) {
    return;
  }
  shortcutHelpOpen.value = false;
  focusBeforePause = document.activeElement instanceof HTMLElement
    ? document.activeElement
    : null;
  battlePaused.value = true;
  void nextTick(() => resumeButton.value?.focus());
}

function resumeBattle(): void {
  if (!battlePaused.value) {
    return;
  }
  battlePaused.value = false;
  void nextTick(() => {
    if (
      focusBeforePause?.isConnected
      && battleRoot.value?.contains(focusBeforePause)
    ) {
      focusBeforePause.focus();
    } else {
      const fallback = battleRoot.value?.querySelector<HTMLElement>(
        [
          '.battle-answer-row input:not(:disabled)',
          '.battle-rating-grid button:not(:disabled)',
          '.card-button:not(:disabled)',
          '.battle-result button:not(:disabled)',
        ].join(', '),
      );
      fallback?.focus();
    }
    focusBeforePause = null;
  });
}

function performBattleShortcut(action: BattleShortcutAction): void {
  switch (action.type) {
    case 'submit-answer':
      void submitAnswer();
      return;
    case 'rate':
      void submitRating(action.rating);
      return;
    case 'select-card': {
      const entry = handCards.value[action.index - 1];
      if (entry !== undefined) {
        chooseCard(entry.card);
      }
      return;
    }
    case 'cycle-target':
      cycleTarget(action.direction);
      return;
    case 'confirm-target':
      void playSelectedCard();
      return;
    case 'cancel-target':
      cancelTargetSelection();
      return;
    case 'pause':
      pauseBattle();
      return;
    case 'resume':
      resumeBattle();
      return;
    case 'end-turn':
      void endTurn();
      return;
    case 'finish':
      finish();
      return;
    case 'toggle-help':
      shortcutHelpOpen.value = true;
      return;
    case 'close-help':
      shortcutHelpOpen.value = false;
  }
}

function handleBattleKeydown(event: KeyboardEvent): void {
  if (event.defaultPrevented) {
    return;
  }
  const target = event.target;
  const isEditable = target instanceof HTMLElement
    && (
      target.matches('input, textarea, select')
      || target.isContentEditable
    );
  const isInteractiveTarget = target instanceof HTMLElement
    && (
      isEditable
      || target.matches('button, a, summary, [role="button"]')
    );
  const action = resolveBattleShortcut({
    key: event.key,
    context: shortcutContext.value,
    endTurnShortcut: shortcutPreferences.value.endTurn,
    isComposing: event.isComposing,
    repeat: event.repeat,
    isEditable,
    isInteractiveTarget,
    helpOpen: shortcutHelpOpen.value,
    ctrlKey: event.ctrlKey,
    metaKey: event.metaKey,
    altKey: event.altKey,
    shiftKey: event.shiftKey,
  });
  if (action === null) {
    return;
  }
  event.preventDefault();
  performBattleShortcut(action);
}

onMounted(() => {
  window.addEventListener('keydown', handleBattleKeydown);
  void startBattle();
});

onBeforeUnmount(() => {
  disposed = true;
  generation += 1;
  window.removeEventListener('keydown', handleBattleKeydown);
});
</script>

<template>
  <section
    ref="battleRoot"
    class="bridge-battle"
    data-testid="bridge-battle"
    :data-shortcut-context="shortcutContext"
    :data-battle-paused="battlePaused"
    :data-end-turn-shortcut="shortcutPreferences.endTurn"
  >
    <header class="battle-heading">
      <div>
        <p class="battle-kicker">溪桥 · 教学遭遇</p>
        <h3>雾中的守卫</h3>
      </div>
      <div class="battle-status">
        <span data-field="battle-phase">{{ phaseLabel }}</span>
        <span data-field="battle-version">v{{ battle?.stateVersion ?? 0 }}</span>
        <span data-field="shortcut-context">{{ shortcutContext }}</span>
        <span v-if="enableAudio" data-field="audio-status">{{ audioStatus }}</span>
        <span
          v-if="enableAudio && audioRateLimitedCount > 0"
          data-field="audio-rate-limited"
        >
          限频 {{ audioRateLimitedCount }}
        </span>
        <button
          v-if="enableAudio"
          type="button"
          class="audio-command"
          :aria-label="audioMuted ? '打开音效' : '静音'"
          :title="audioMuted ? '打开音效' : '静音'"
          @click="toggleAudioMuted"
        >
          {{ audioMuted ? '静音中' : '音效' }}
        </button>
        <label v-if="enableAudio" class="audio-volume-control">
          <span>音量</span>
          <input
            type="range"
            min="0"
            max="1"
            step="0.05"
            :value="audioVolume"
            aria-label="音效音量"
            @input="setAudioVolume(Number(($event.target as HTMLInputElement).value))"
          />
        </label>
        <button
          v-if="enableAudio"
          type="button"
          class="audio-command"
          :aria-label="audioPaused ? '继续音效' : '暂停音效'"
          :title="audioPaused ? '继续音效' : '暂停音效'"
          @click="setAudioPaused(!audioPaused)"
        >
          {{ audioPaused ? '继续音效' : '暂停音效' }}
        </button>
        <button
          v-if="battle"
          type="button"
          class="audio-command"
          :aria-label="shortcutHelpOpen ? '关闭快捷键帮助' : '打开快捷键帮助'"
          :title="shortcutHelpOpen ? '关闭快捷键帮助' : '打开快捷键帮助'"
          @click="shortcutHelpOpen = !shortcutHelpOpen"
        >
          <CircleHelp :size="16" aria-hidden="true" />
          快捷键
        </button>
        <button
          v-if="battle && shortcutContext !== 'result'"
          type="button"
          class="audio-command"
          @click="battlePaused ? resumeBattle() : pauseBattle()"
        >
          {{ battlePaused ? '继续战斗' : '暂停战斗' }}
        </button>
      </div>
    </header>

    <section v-if="battlePaused" class="battle-pause-panel" aria-labelledby="battle-pause-title">
      <p class="section-label">战斗已暂停</p>
      <h4 id="battle-pause-title">溪桥与手牌仍保持当前状态。</h4>
      <p>服务端没有收到撤退或结束命令。按 Enter 或 Escape 继续。</p>
      <button ref="resumeButton" type="button" class="battle-primary" @click="resumeBattle">
        继续战斗
      </button>
    </section>

    <template v-else>
      <BattleShortcutHelp
        v-if="shortcutHelpOpen"
        :context="shortcutContext"
        :preferences="shortcutPreferences"
        @close="shortcutHelpOpen = false"
        @update-end-turn="updateEndTurnShortcut"
      />

      <p v-if="error" class="battle-message battle-message--error" role="alert">{{ error }}</p>
      <p v-if="notice" class="battle-message" role="status">{{ notice }}</p>

      <section v-if="loading" class="battle-loading" role="status">
        正在进入溪桥...
      </section>

      <template v-else-if="battle">
        <div class="battle-stage">
          <div class="mist mist--one"></div>
          <div class="mist mist--two"></div>
          <div class="bridge-line">
            <span></span><span></span><span></span><span></span>
          </div>
          <div class="enemy-figure" aria-label="溪桥迷雾守卫">
            <span class="enemy-eye"></span>
            <strong>桥雾守卫</strong>
            <small>{{ battle.battle.encounterId }}</small>
          </div>
          <div class="companion-figure">
            <CompanionPortrait
              :companion="companion"
              :expression="companionExpression"
            />
            <div class="companion-caption">
              <strong>{{ companion.name }}</strong>
              <span>{{ companion.epithet }}</span>
            </div>
          </div>
        </div>

        <div class="battle-hud">
          <div>
            <span>队伍生命</span>
            <strong data-field="party-hp">
              {{ battle.battle.party.hp }}/{{ battle.battle.party.maxHp }}
            </strong>
          </div>
          <div>
            <span>格挡</span>
            <strong data-field="party-block">{{ battle.battle.party.block }}</strong>
          </div>
          <div>
            <span>能量</span>
            <strong data-field="energy">{{ battle.battle.energy }}/3</strong>
          </div>
          <div>
            <span>敌方意图</span>
            <strong data-field="intent">{{ intentText(battle.battle) }}</strong>
          </div>
          <div>
            <span>守卫生命</span>
            <strong data-field="enemy-hp">
              {{ currentEnemy?.hp ?? 0 }}/{{ currentEnemy?.maxHp ?? 0 }}
            </strong>
          </div>
        </div>

        <section v-if="battle.battle.phase === 'learning'" class="learning-panel">
          <p class="section-label">本回合唯一学习任务</p>
          <strong v-if="currentItem" class="learning-meaning">
            {{ currentItem.wordEntry.meaning }}
          </strong>
          <p v-else class="battle-message battle-message--error">
            当前学习目标不可用，不能伪造战斗能量。
          </p>
          <div v-if="pendingAnswer === undefined" class="battle-answer-row">
            <label>
              <span>英文拼写</span>
              <input
                v-model="answer"
                type="text"
                autocomplete="off"
                :disabled="busy || currentItem === undefined"
                aria-keyshortcuts="Enter"
              />
            </label>
            <button
              type="button"
              class="battle-primary"
              :disabled="busy || answer.trim().length === 0"
              @click="submitAnswer"
            >
              提交并评分
            </button>
          </div>
          <div v-else class="battle-rating-grid" aria-label="战术学习评分">
            <button
              v-for="rating in (['again', 'hard', 'good', 'easy'] as const)"
              :key="rating"
              type="button"
              :disabled="busy || reviewPreviews === undefined"
              :aria-keyshortcuts="String(['again', 'hard', 'good', 'easy'].indexOf(rating) + 1)"
              @click="submitRating(rating)"
            >
              <strong>{{ rating.toUpperCase() }}</strong>
              <span v-if="reviewPreviews">
                {{ formatDue(reviewPreviews.previews[rating].dueAt) }}
              </span>
            </button>
          </div>
        </section>

        <section v-else-if="battle.battle.phase === 'player-action'" class="action-panel">
          <div class="action-toolbar">
            <div>
              <p class="section-label">战术行动</p>
              <strong>{{ companion.name }}与你并肩</strong>
            </div>
            <button
              type="button"
              :disabled="busy || battle.battle.companion?.skillUsed === true"
              @click="useCompanionSkill"
            >
              {{ battle.battle.companion?.skillUsed ? '协助已使用' : `${companion.name}协助` }}
            </button>
          </div>
          <div class="hand-grid" aria-label="手牌">
            <button
              v-for="(entry, index) in handCards"
              :key="entry.card.instanceId"
              type="button"
              class="card-button"
              :class="{ selected: selectedCard?.instanceId === entry.card.instanceId }"
              :disabled="busy || entry.definition.cost > battle.battle.energy"
              :aria-keyshortcuts="String(index + 1)"
              @click="chooseCard(entry.card)"
            >
              <span class="card-cost">{{ entry.definition.cost }}</span>
              <strong>{{ entry.definition.name }}</strong>
              <small>{{ cardEffectText(entry.definition) }}</small>
            </button>
          </div>
          <div v-if="selectedCard && legalTargets.length > 0" class="target-row">
            <span>选择目标</span>
            <button
              v-for="target in legalTargets"
              :key="target.id"
              type="button"
              :class="{ selected: selectedTarget?.id === target.id }"
              :disabled="busy"
              @click="playCard(selectedCard, target.id)"
            >
              {{ target.id }} · 生命 {{ target.hp }}
            </button>
            <button type="button" :disabled="busy" @click="cancelTargetSelection">
              取消
            </button>
          </div>
          <div class="turn-row">
            <span>抽牌 {{ battle.battle.drawCursor }}/{{ battle.battle.drawPile.length }}</span>
            <span>弃牌 {{ battle.battle.discardPile.length }}</span>
            <span>耗竭 {{ battle.battle.exhaustPile.length }}</span>
            <button
              type="button"
              class="battle-primary"
              :disabled="busy || selectedCard !== undefined"
              :aria-keyshortcuts="endTurnShortcutAriaLabel(shortcutPreferences.endTurn)"
              @click="endTurn"
            >
              结束回合
            </button>
            <kbd>{{ endTurnShortcutLabel(shortcutPreferences.endTurn) }}</kbd>
          </div>
        </section>

        <section v-else class="battle-result">
          <p class="section-label">{{ battle.battle.phase === 'victory' ? '桥面重见天日' : '救援信号' }}</p>
          <h4>
            {{ battle.battle.phase === 'victory' ? '溪桥的路标重新亮了起来' : '伙伴会把你带回营地' }}
          </h4>
          <p>
            {{ battle.battle.phase === 'victory'
              ? '学习记录、战斗状态和结算已经由服务端提交。'
              : '本次已提交的学习记录不会因为战斗失败而重算。' }}
          </p>
          <button type="button" class="battle-primary" @click="finish">返回在线学习</button>
        </section>
      </template>
    </template>

    <PhaserGameHost
      v-if="enableAudio"
      :ref="setAudioHost"
      :muted="audioMuted"
      :volume="audioVolume"
      :paused="audioPaused"
      @event="handleAudioRuntimeEvent"
    />
  </section>
</template>

<style scoped>
.bridge-battle {
  position: relative;
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid #bba979;
  border-radius: 8px;
  background: #142d2e;
  color: #edf3e9;
}

.battle-heading,
.battle-status,
.action-toolbar,
.turn-row,
.target-row,
.battle-answer-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.battle-heading,
.action-toolbar,
.turn-row {
  justify-content: space-between;
}

.battle-kicker,
.battle-status,
.battle-hud span,
.battle-hud strong + span,
.turn-row,
.target-row,
.card-button small,
.companion-caption span {
  font-size: 0.76rem;
}

.battle-kicker {
  margin: 0 0 4px;
  color: #d6bd78;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.battle-heading h3 {
  margin: 0;
  color: #f5ead1;
  font-family: Georgia, "Times New Roman", serif;
  font-size: clamp(1.4rem, 3vw, 2.2rem);
  font-weight: 500;
}

.battle-status {
  flex-wrap: wrap;
  justify-content: flex-end;
  color: #b9cec0;
}

.audio-command {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 28px;
  padding: 3px 8px;
  border: 1px solid rgb(214 189 120 / 45%);
  border-radius: 4px;
  background: transparent;
  color: #d6bd78;
  cursor: pointer;
}

.audio-volume-control {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #b9cec0;
  font-size: 0.7rem;
}

.audio-volume-control input {
  width: 74px;
  accent-color: #d6bd78;
}

.battle-stage {
  position: relative;
  min-height: 250px;
  overflow: hidden;
  border: 1px solid rgb(214 189 120 / 28%);
  background:
    linear-gradient(180deg, rgb(32 64 67 / 75%), rgb(14 35 38 / 96%)),
    radial-gradient(circle at 50% 20%, #729890, transparent 62%);
}

.bridge-line {
  position: absolute;
  right: -10%;
  bottom: 34px;
  left: -10%;
  display: flex;
  height: 18px;
  gap: 5px;
  transform: skewY(-2deg);
  opacity: 0.75;
}

.bridge-line span {
  flex: 1;
  border-top: 4px solid #826b4a;
  border-bottom: 2px solid #3d3427;
}

.bridge-line::before,
.bridge-line::after {
  position: absolute;
  top: 8px;
  width: 8px;
  height: 90px;
  background: #4d3b2a;
  content: "";
}

.bridge-line::before {
  left: 16%;
}

.bridge-line::after {
  right: 16%;
}

.mist {
  position: absolute;
  width: 56%;
  height: 45px;
  border-radius: 50%;
  background: rgb(198 222 205 / 16%);
  filter: blur(12px);
}

.mist--one {
  top: 34%;
  left: -12%;
}

.mist--two {
  top: 57%;
  right: -8%;
  animation: mist-drift 8s ease-in-out infinite alternate;
}

.enemy-figure {
  position: absolute;
  top: 38px;
  left: 50%;
  display: grid;
  width: 118px;
  height: 142px;
  place-items: center;
  border: 1px solid rgb(235 202 123 / 35%);
  border-radius: 48% 48% 38% 38%;
  background: linear-gradient(150deg, #415a51, #1a292d);
  box-shadow: 0 14px 28px rgb(0 0 0 / 35%);
  transform: translateX(-50%);
}

.enemy-eye {
  width: 34px;
  height: 8px;
  border-radius: 50%;
  background: #e5c879;
  box-shadow: 0 0 16px #e5c879;
}

.enemy-figure strong {
  align-self: end;
  color: #f0dfb3;
  font-size: 0.78rem;
}

.enemy-figure small {
  align-self: start;
  color: #9fb8a7;
  font-size: 0.6rem;
}

.companion-figure {
  position: absolute;
  bottom: -10px;
  left: 20px;
  display: flex;
  width: 190px;
  height: 240px;
  align-items: end;
  gap: 5px;
}

.companion-figure :deep(.companion-portrait) {
  width: 150px;
  height: 225px;
}

.companion-caption {
  display: grid;
  align-self: end;
  min-width: 0;
  padding-bottom: 20px;
  gap: 3px;
  color: #f0dfb3;
  font-size: 0.78rem;
}

.companion-caption span {
  color: #afc7b8;
  line-height: 1.3;
}

.battle-hud {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 1px;
  background: rgb(214 189 120 / 22%);
}

.battle-hud > div {
  display: grid;
  gap: 4px;
  padding: 10px;
  background: #102729;
}

.battle-hud span {
  color: #9db9ab;
}

.battle-hud strong {
  color: #f1d797;
  font-size: 0.95rem;
}

.learning-panel,
.action-panel,
.battle-pause-panel,
.battle-result {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid rgb(158 205 189 / 22%);
  background: #102729;
}

.battle-pause-panel {
  justify-items: start;
}

.battle-pause-panel h4 {
  margin: 0;
  color: #f0dfb3;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 1.35rem;
  font-weight: 500;
}

.battle-pause-panel p:not(.section-label) {
  margin: 0;
  color: #b9cec0;
}

.learning-meaning {
  color: #fff1cd;
  font-size: clamp(1.5rem, 4vw, 2.4rem);
}

.battle-answer-row {
  align-items: end;
}

.battle-answer-row label {
  display: grid;
  flex: 1;
  gap: 6px;
  color: #b9cec0;
  font-size: 0.75rem;
}

.battle-answer-row input {
  min-height: 40px;
  padding: 7px 10px;
  border: 1px solid rgb(214 189 120 / 45%);
  border-radius: 4px;
  background: #eef5f1;
  color: #142d2e;
}

.battle-rating-grid,
.hand-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.battle-rating-grid button,
.card-button,
.action-toolbar button,
.turn-row button,
.target-row button,
.battle-answer-row button,
.battle-primary {
  min-height: 38px;
  padding: 7px 10px;
  border: 1px solid rgb(214 189 120 / 42%);
  border-radius: 4px;
  background: transparent;
  color: #f0dfb3;
  cursor: pointer;
}

.battle-rating-grid button {
  display: grid;
  gap: 3px;
}

.battle-rating-grid button span {
  color: #aec5b7;
  font-size: 0.68rem;
}

.action-toolbar strong {
  color: #f0dfb3;
}

.hand-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.card-button {
  position: relative;
  display: grid;
  min-height: 96px;
  align-content: start;
  gap: 6px;
  padding: 12px;
  text-align: left;
}

.card-button.selected,
.target-row button.selected,
.battle-primary,
.battle-answer-row button {
  border-color: #e1c477;
  background: #d1ab5c;
  color: #1c2e2d;
}

.turn-row kbd {
  padding: 3px 6px;
  border: 1px solid rgb(214 189 120 / 32%);
  border-radius: 4px;
  background: #142d2e;
  color: #f0dfb3;
  font: 600 0.68rem/1.2 "SFMono-Regular", Consolas, monospace;
}

.card-cost {
  display: grid;
  width: 23px;
  height: 23px;
  place-items: center;
  border-radius: 50%;
  background: #d1ab5c;
  color: #1c2e2d;
  font-size: 0.73rem;
  font-weight: 800;
}

.card-button strong {
  font-size: 0.86rem;
}

.card-button small {
  color: #b7cbbb;
  line-height: 1.35;
}

.target-row {
  flex-wrap: wrap;
  padding: 9px 11px;
  background: rgb(209 171 92 / 15%);
  color: #f1d797;
}

.turn-row {
  flex-wrap: wrap;
  color: #aec5b7;
}

.battle-result {
  justify-items: start;
}

.battle-result h4 {
  margin: 0;
  color: #f0dfb3;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 1.35rem;
  font-weight: 500;
}

.battle-result p:not(.section-label) {
  margin: 0;
  color: #b9cec0;
}

.battle-message {
  margin: 0;
  padding: 9px 11px;
  border-left: 3px solid #d1ab5c;
  background: rgb(209 171 92 / 12%);
  color: #f0dfb3;
  font-size: 0.78rem;
}

.battle-message--error {
  border-left-color: #df936e;
  background: rgb(223 147 110 / 14%);
  color: #ffd6c2;
}

.battle-loading {
  color: #b9cec0;
}

button:disabled,
input:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

button:focus-visible,
input:focus-visible {
  outline: 3px solid rgb(225 196 119 / 64%);
  outline-offset: 2px;
}

.phaser-game-host {
  position: absolute;
  right: 0;
  bottom: 0;
  opacity: 0;
}

@keyframes mist-drift {
  from {
    transform: translateX(-7%);
  }

  to {
    transform: translateX(8%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .mist--two {
    animation: none;
  }
}

@media (max-width: 720px) {
  .battle-heading,
  .action-toolbar,
  .battle-answer-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .battle-status {
    justify-content: flex-start;
  }

  .battle-stage {
    min-height: 230px;
  }

  .companion-figure {
    left: 0;
    width: 155px;
  }

  .companion-figure :deep(.companion-portrait) {
    width: 120px;
    height: 190px;
  }

  .companion-caption {
    padding-bottom: 12px;
    font-size: 0.68rem;
  }

  .battle-hud {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hand-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 480px) {
  .bridge-battle {
    padding: 12px;
  }

  .battle-rating-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .companion-figure {
    display: none;
  }
}
</style>
