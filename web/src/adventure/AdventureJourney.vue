<script setup lang="ts">
import { computed } from 'vue';
import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  Flame,
  Map,
  Play,
  Sparkles,
  Swords,
} from '@lucide/vue';
import {
  accountApi,
  type AccountClient,
  type AdventureRunView,
  type SessionEnvelope,
} from '../api/client';
import BridgeBattlePanel from '../battle/BridgeBattlePanel.vue';
import CompanionPortrait from '../companions/CompanionPortrait.vue';
import forestBackground from '../assets/scenes/forest/runtime/forest-path.jpg';
import bridgeBackground from '../assets/scenes/bridge/runtime/bridge-guardian.jpg';
import campBackground from '../assets/scenes/camp/runtime/camp-fire.jpg';
import innBackground from '../assets/scenes/inn/runtime/inn-warm-lantern.jpg';
import type {
  CompanionExpression,
  CompanionId,
  CompanionProfile,
} from '../companions/catalog';

const props = withDefaults(
  defineProps<{
    api?: AccountClient;
    run: AdventureRunView;
    session?: SessionEnvelope;
    companion: CompanionProfile;
    loading: boolean;
    busy: boolean;
    error: string | null;
    notice: string | null;
    enableAudio?: boolean;
    reduceMotion?: boolean;
  }>(),
  {
    api: () => accountApi,
    session: undefined,
    enableAudio: false,
    reduceMotion: false,
  },
);

const emit = defineEmits<{
  choose: [choiceId: string];
  advance: [];
  'start-encounter': [];
  'resolve-encounter': [];
  complete: [];
  exit: [];
  'session-changed': [session: SessionEnvelope];
}>();

const node = computed(() => props.run.currentNode);
const fixedHash = computed(() => props.run.contentHash.slice(0, 8));
const sceneBackground = computed(() => {
  if (node.value.nodeId.startsWith('inn.')) {
    return innBackground;
  }
  if (node.value.type === 'encounter') {
    return bridgeBackground;
  }
  if (node.value.type === 'camp' || node.value.type === 'chapter-end') {
    return campBackground;
  }
  return forestBackground;
});
const speaker = computed(() => {
  const value = node.value.speaker.trim();
  if (value.length === 0 || value === '旅伴' || value === 'companion') {
    return props.companion.name;
  }
  return value;
});
const expression = computed<CompanionExpression>(() => {
  if (props.run.status === 'completed') {
    return 'relief';
  }
  switch (node.value.type) {
    case 'choice':
      return 'welcome';
    case 'encounter':
      return 'concern';
    case 'camp':
    case 'chapter-end':
      return 'relief';
    default:
      return 'cheer';
  }
});
const sceneLabel = computed(() => {
  switch (node.value.type) {
    case 'choice':
      return '旅途抉择';
    case 'encounter':
      return '溪桥遭遇';
    case 'camp':
      return '营火检查点';
    case 'chapter-end':
      return '章节结算';
    default:
      return '旅途对白';
  }
});
const canAdvance = computed(
  () => node.value.type === 'dialogue'
    || node.value.type === 'explore'
    || node.value.type === 'camp',
);
const battleReady = computed(
  () => props.run.battleId !== undefined && props.session !== undefined,
);
const battleCompanionKey = computed<CompanionId | undefined>(
  () => props.run.companionKey === 'fina' ? undefined : props.run.companionKey,
);
const primaryLabel = computed(() => {
  if (node.value.type === 'camp') {
    return '在营火旁收好笔记';
  }
  return '继续旅途';
});

function finishBattle(): void {
  emit('resolve-encounter');
}
</script>

<template>
  <section
    class="adventure-journey"
    :class="`scene-${node.type}`"
    :data-run-id="run.id"
    :data-node-id="node.nodeId"
    :data-content-version="run.contentVersion"
    :data-content-hash="run.contentHash"
    :style="{ '--scene-background': `url(${sceneBackground})` }"
    :aria-labelledby="`adventure-${node.nodeId}`"
  >
    <header class="journey-heading">
      <button type="button" class="exit-journey" :disabled="busy" @click="emit('exit')">
        <ArrowLeft :size="18" aria-hidden="true" />
        保存并离开
      </button>
      <div class="journey-identity">
        <p class="section-label">
          <Map :size="15" aria-hidden="true" />
          {{ run.title }}
        </p>
        <h2 :id="`adventure-${node.nodeId}`">{{ node.title }}</h2>
      </div>
      <div class="run-lock" aria-label="固定内容版本">
        <span>v{{ run.contentVersion }}</span>
        <code>{{ fixedHash }}</code>
      </div>
    </header>

    <div v-if="run.status === 'completed'" class="journey-complete" role="status">
      <Sparkles :size="22" aria-hidden="true" />
      <div>
        <strong>第一章已经写入冒险存档。</strong>
        <p>暖灯、营火和这次学习记录都会保留在同一个固定版本中。</p>
      </div>
      <button type="button" class="journey-primary" @click="emit('exit')">
        回到旅店
        <ArrowRight :size="18" aria-hidden="true" />
      </button>
    </div>

    <template v-else>
      <p v-if="error" class="journey-message journey-message--error" role="alert">
        {{ error }}
      </p>
      <p v-if="notice" class="journey-message" role="status">
        {{ notice }}
      </p>

      <div v-if="loading" class="journey-loading" role="status">
        正在读取冒险检查点...
      </div>

      <BridgeBattlePanel
        v-else-if="node.type === 'encounter' && battleReady && battleCompanionKey"
        :api="api"
        :session="session!"
        :companion-key="battleCompanionKey"
        :initial-battle-id="run.battleId"
        finish-label="继续剧情"
        :enable-audio="enableAudio"
        :reduce-motion="reduceMotion"
        @session-changed="emit('session-changed', $event)"
        @finished="finishBattle"
      />

      <div
        v-else-if="node.type === 'encounter' && run.companionKey === 'fina'"
        class="journey-loading"
        role="alert"
      >
        菲妮的战斗素材尚未接入，当前固定版本无法继续该遭遇。
      </div>

      <div
        v-else-if="node.type === 'encounter' && run.battleId"
        class="journey-loading"
        role="status"
      >
        已找到固定战斗，正在恢复学习会话...
      </div>

      <div v-else class="journey-stage">
        <div class="scene-atmosphere" aria-hidden="true">
          <span class="scene-light"></span>
        </div>

        <div class="journey-copy">
          <p class="scene-kicker">
            <Swords v-if="node.type === 'encounter'" :size="16" aria-hidden="true" />
            <Flame v-else-if="node.type === 'camp'" :size="16" aria-hidden="true" />
            <BookOpen v-else :size="16" aria-hidden="true" />
            {{ sceneLabel }}
          </p>
          <p v-if="node.speaker" class="speaker">{{ speaker }}</p>
          <p class="node-text">{{ node.text }}</p>

          <div v-if="node.type === 'choice'" class="choice-list" aria-label="剧情选项">
            <button
              v-for="choice in node.choices"
              :key="choice.choiceId"
              type="button"
              :disabled="busy"
              @click="emit('choose', choice.choiceId)"
            >
              <span>{{ choice.label }}</span>
              <ArrowRight :size="17" aria-hidden="true" />
            </button>
          </div>

          <div v-else-if="node.type === 'encounter'" class="encounter-actions">
            <p>
              雾中的守卫没有让路。先完成这一轮回忆，再决定如何出牌。
            </p>
            <button
              type="button"
              class="journey-primary"
              :disabled="busy"
              @click="emit('start-encounter')"
            >
              <Play :size="18" aria-hidden="true" />
              开始溪桥战斗
            </button>
          </div>

          <div v-else-if="node.type === 'chapter-end'" class="encounter-actions">
            <button
              type="button"
              class="journey-primary"
              :disabled="busy"
              @click="emit('complete')"
            >
              完成第一章
              <Sparkles :size="18" aria-hidden="true" />
            </button>
          </div>

          <button
            v-else-if="canAdvance"
            type="button"
            class="journey-primary"
            :disabled="busy"
            @click="emit('advance')"
          >
            {{ busy ? '正在保存检查点' : primaryLabel }}
            <ArrowRight :size="18" aria-hidden="true" />
          </button>
        </div>

        <div class="journey-companion">
          <span class="lamp-glow" aria-hidden="true"></span>
          <CompanionPortrait
            :companion="companion"
            :expression="expression"
            :reduce-motion="reduceMotion"
          />
          <div class="companion-line">
            <span>{{ companion.name }} · {{ companion.epithet }}</span>
            <p>{{ companion.dialogue.training }}</p>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.adventure-journey {
  --scene-deep: #14211d;
  --scene-middle: #31473b;
  --scene-warm: #bd8b4f;
  --scene-background: none;
  position: relative;
  display: grid;
  min-height: calc(100vh - 150px);
  gap: 18px;
  overflow: hidden;
  border: 1px solid rgb(217 183 121 / 36%);
  border-radius: 8px;
  padding: clamp(18px, 3vw, 34px);
  background:
    linear-gradient(90deg, rgb(17 12 13 / 90%) 0 38%, rgb(17 12 13 / 42%) 56%, transparent 82%),
    linear-gradient(180deg, transparent 52%, rgb(12 10 12 / 58%) 100%),
    var(--scene-background) center / cover no-repeat,
    linear-gradient(135deg, var(--scene-deep), var(--scene-middle));
  color: #f7ead5;
  isolation: isolate;
}

.adventure-journey.scene-choice,
.adventure-journey.scene-dialogue {
  --scene-deep: #261b18;
  --scene-middle: #594135;
  --scene-warm: #d0a063;
}

.adventure-journey.scene-encounter {
  --scene-deep: #11272a;
  --scene-middle: #3d6662;
  --scene-warm: #b7d3bd;
}

.adventure-journey.scene-camp,
.adventure-journey.scene-chapter-end {
  --scene-deep: #1b1521;
  --scene-middle: #5c372d;
  --scene-warm: #f0a95c;
}

.journey-heading {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
}

.journey-identity {
  min-width: 0;
  text-align: center;
}

.journey-identity .section-label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin: 0 0 5px;
  color: #d9b779;
  letter-spacing: 0.08em;
}

.journey-identity h2 {
  margin: 0;
  color: #fff0d7;
  font-family: "Songti SC", "Noto Serif CJK SC", Georgia, serif;
  font-size: clamp(1.55rem, 3vw, 2.5rem);
  font-weight: 600;
  line-height: 1.15;
  overflow-wrap: anywhere;
}

.exit-journey,
.journey-primary,
.choice-list button {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid rgb(234 205 145 / 46%);
  border-radius: 5px;
  padding: 8px 13px;
  background: rgb(22 18 18 / 44%);
  color: #f3dfb7;
  cursor: pointer;
}

.run-lock {
  display: grid;
  justify-items: end;
  gap: 3px;
  color: #cfb982;
  font-size: 0.72rem;
}

.run-lock code {
  color: #a8c7b6;
  font-size: 0.66rem;
}

.journey-message {
  position: relative;
  z-index: 2;
  margin: 0;
  border-left: 3px solid #d7b773;
  padding: 9px 12px;
  background: rgb(24 18 18 / 48%);
  color: #e9dcc4;
  overflow-wrap: anywhere;
}

.journey-message--error {
  border-left-color: #d78373;
  color: #ffd8ce;
}

.journey-loading {
  display: grid;
  min-height: 360px;
  place-items: center;
  color: #d7c79e;
}

.journey-stage {
  position: relative;
  display: grid;
  grid-template-columns: minmax(330px, 0.9fr) minmax(340px, 1.1fr);
  min-height: 560px;
  align-items: end;
  overflow: hidden;
  border: 1px solid rgb(234 205 145 / 20%);
  background:
    linear-gradient(90deg, rgb(12 10 12 / 84%) 0 38%, rgb(12 10 12 / 18%) 58%, transparent 84%),
    linear-gradient(180deg, transparent 40%, rgb(12 10 12 / 82%) 100%),
    var(--scene-background) center / cover no-repeat;
}

.scene-atmosphere {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.scene-atmosphere::before {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 76% 25%, rgb(255 214 151 / 8%), transparent 27%),
    linear-gradient(180deg, rgb(12 13 16 / 8%), rgb(11 10 12 / 30%));
  content: "";
}

.scene-light {
  position: absolute;
  top: 18%;
  right: 15%;
  width: 22%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: radial-gradient(circle, rgb(255 212 139 / 28%), transparent 68%);
  filter: blur(10px);
}

.journey-copy {
  position: relative;
  z-index: 2;
  display: flex;
  min-width: 0;
  align-self: stretch;
  flex-direction: column;
  justify-content: end;
  gap: 16px;
  padding: clamp(26px, 4vw, 54px);
}

.scene-kicker {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 7px;
  margin: 0;
  color: #d9b779;
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.speaker {
  margin: 0;
  color: #f0c980;
  font-size: 0.88rem;
  font-weight: 700;
}

.node-text {
  max-width: 38rem;
  margin: 0;
  color: #fff1d8;
  font-family: "Songti SC", "Noto Serif CJK SC", Georgia, serif;
  font-size: clamp(1.35rem, 2.6vw, 2.15rem);
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.choice-list,
.encounter-actions {
  display: grid;
  max-width: 38rem;
  gap: 9px;
}

.choice-list button {
  justify-content: space-between;
  text-align: left;
}

.choice-list button:hover,
.choice-list button:focus-visible {
  border-color: #e5c67f;
  background: rgb(209 171 92 / 24%);
}

.encounter-actions p {
  margin: 0;
  color: #c7d7cb;
  line-height: 1.6;
}

.encounter-actions code {
  color: #f0d89e;
}

.journey-primary {
  width: fit-content;
  border-color: #e0c278;
  background: #d1ab5c;
  color: #201d19;
  font-weight: 700;
}

.journey-companion {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 0;
  align-items: end;
  justify-content: center;
  gap: 0;
  padding: 20px 28px 0;
}

.journey-companion :deep(.companion-portrait) {
  width: min(390px, 94%);
  height: min(580px, 72vh);
}

.lamp-glow {
  position: absolute;
  right: 8%;
  bottom: 13%;
  width: 54%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: radial-gradient(circle, rgb(255 196 108 / 28%), transparent 70%);
  filter: blur(14px);
}

.companion-line {
  position: absolute;
  right: 24px;
  bottom: 24px;
  max-width: 310px;
  border-left: 2px solid #dcb772;
  padding: 8px 12px;
  background: rgb(17 13 14 / 72%);
}

.companion-line span {
  color: #e4c786;
  font-size: 0.75rem;
}

.companion-line p {
  margin: 4px 0 0;
  color: #fff0d7;
  line-height: 1.45;
}

.journey-complete {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
  border: 1px solid rgb(234 205 145 / 34%);
  padding: clamp(22px, 4vw, 42px);
  background: rgb(26 19 17 / 70%);
}

.journey-complete strong {
  color: #ffe8ba;
  font-family: "Songti SC", "Noto Serif CJK SC", Georgia, serif;
  font-size: 1.55rem;
}

.journey-complete p {
  margin: 6px 0 0;
  color: #d4c7ad;
}

button:disabled {
  cursor: wait;
  opacity: 0.55;
}

@media (max-width: 900px) {
  .adventure-journey {
    min-height: calc(100vh - 120px);
    background:
      linear-gradient(180deg, rgb(17 12 13 / 56%) 0 34%, rgb(14 11 13 / 82%) 72%),
      var(--scene-background) center / cover no-repeat,
      var(--scene-deep);
  }

  .journey-heading {
    grid-template-columns: 1fr auto;
  }

  .journey-identity {
    grid-row: 1;
    grid-column: 1 / -1;
    text-align: left;
  }

  .journey-stage {
    grid-template-columns: 1fr;
    min-height: auto;
    background:
      linear-gradient(180deg, rgb(12 10 12 / 28%) 0 30%, rgb(12 10 12 / 84%) 72%),
      var(--scene-background) center / cover no-repeat;
  }

  .journey-copy {
    min-height: 480px;
  }

  .journey-companion {
    min-height: 420px;
  }

  .journey-companion :deep(.companion-portrait) {
    width: min(320px, 84vw);
    height: 430px;
  }

  .journey-complete {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .journey-complete .journey-primary {
    grid-column: 1 / -1;
  }
}

@media (max-width: 520px) {
  .adventure-journey {
    margin-inline: -10px;
    padding: 14px;
  }

  .journey-heading {
    gap: 10px;
  }

  .exit-journey {
    padding-inline: 9px;
  }

  .run-lock {
    max-width: 92px;
    text-align: right;
    overflow-wrap: anywhere;
  }

  .journey-copy {
    min-height: 430px;
    padding: 22px 18px;
  }

  .node-text {
    font-size: 1.28rem;
  }

  .journey-companion {
    min-height: 350px;
    padding-inline: 10px;
  }

  .journey-companion :deep(.companion-portrait) {
    height: 360px;
  }

  .companion-line {
    right: 10px;
    bottom: 12px;
    left: 10px;
    max-width: none;
  }

  .journey-complete {
    grid-template-columns: 1fr;
  }
}
</style>
