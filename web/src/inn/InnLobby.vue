<script setup lang="ts">
import { computed } from 'vue';
import {
  ArrowRight,
  BookOpen,
  ChevronDown,
  CircleHelp,
  Clock3,
  Feather,
  NotebookTabs,
  ScrollText,
  Target,
  TriangleAlert,
} from '@lucide/vue';
import type {
  QueuePlanView,
  SessionEnvelope,
  UserView,
} from '../api/client';
import CompanionPortrait from '../companions/CompanionPortrait.vue';
import {
  companions,
  type CompanionId,
  type CompanionProfile,
} from '../companions/catalog';
import innBackground from '../assets/scenes/inn/runtime/inn-warm-lantern.jpg';

const props = defineProps<{
  companion: CompanionProfile;
  companionDensity: 'quiet' | 'standard' | 'rich';
  reduceMotion: boolean;
  user: UserView;
  plan?: QueuePlanView;
  lexiconsCount: number;
  activeSession?: SessionEnvelope;
  sessionCount: number;
  loading: boolean;
  error: string | null;
}>();

const emit = defineEmits<{
  start: [];
  resume: [session: SessionEnvelope];
  help: [];
  'select-companion': [companionId: CompanionId];
  'select-density': [density: 'quiet' | 'standard' | 'rich'];
  'classic-study': [];
}>();

const densities = ['quiet', 'standard', 'rich'] as const;
const selectedCount = computed(() => props.plan?.selected.length ?? 0);
const completedInSession = computed(
  () => props.activeSession?.session.queue.filter(
    (item) => item.status === 'completed' || item.status === 'skipped',
  ).length ?? 0,
);
const sessionTotal = computed(
  () => props.activeSession?.session.queue.length ?? 0,
);
const expression = computed(() => {
  if (props.activeSession) {
    return 'welcome';
  }
  return selectedCount.value > 0 ? 'cheer' : 'relief';
});

const dialogue = computed(() => {
  if (props.activeSession) {
    return `${props.companion.dialogue.welcome} 上次的笔记已经整理到 ${completedInSession.value}/${sessionTotal.value}。`;
  }
  if (selectedCount.value > 0) {
    return `${props.companion.dialogue.welcome} 今天有 ${selectedCount.value} 个待回想的词。`;
  }
  return `${props.companion.dialogue.rest} 今晚没有待复习的词，也可以先整理词库。`;
});
const visibleDialogue = computed(
  () => props.companionDensity === 'quiet'
    ? `${props.companion.name}安静地陪着你。`
    : dialogue.value,
);

const actionLabel = computed(() => {
  if (props.activeSession) {
    return '续接旅途笔记';
  }
  return selectedCount.value > 0 ? '开始营地复习' : '等待新的旅途笔记';
});
</script>

<template>
  <section
    class="inn-lobby"
    :style="{ '--inn-background': `url(${innBackground})` }"
    aria-labelledby="inn-title"
  >
    <div class="night-wash" aria-hidden="true"></div>

    <div class="inn-copy">
      <p class="chapter-label">
        <ScrollText :size="16" aria-hidden="true" />
        第一章 · 暖灯旅店的委托
      </p>
      <h1 id="inn-title">
        <span>旅人，</span>
        <span>今晚的火</span>
        <span>已经升起来了。</span>
      </h1>
      <p class="scene-lead">
        信纸边缘又淡了一角。莉娅把暖灯放在桌沿，替你留出靠近炉火的位置。
      </p>

      <div class="companion-switcher" role="group" aria-label="选择带队伙伴">
        <button
          v-for="candidate in companions"
          :key="candidate.id"
          type="button"
          class="companion-choice"
          :class="{ selected: candidate.id === companion.id }"
          :aria-pressed="candidate.id === companion.id"
          @click="emit('select-companion', candidate.id)"
        >
          <img
            :src="candidate.portraits.neutral"
            alt=""
            aria-hidden="true"
          />
          <span>
            <strong>{{ candidate.name }}</strong>
            <small>{{ candidate.id === 'lia' ? '暖灯照料' : '记事与指引' }}</small>
          </span>
        </button>
      </div>

      <div class="companion-density">
        <span>陪伴密度</span>
        <div role="group" aria-label="选择陪伴密度">
          <button
            v-for="density in densities"
            :key="density"
            type="button"
            :class="{ selected: density === companionDensity }"
            :aria-pressed="density === companionDensity"
            @click="emit('select-density', density)"
          >
            {{ density === 'quiet' ? '安静' : density === 'standard' ? '标准' : '丰富' }}
          </button>
        </div>
      </div>

      <details class="mobile-companion">
        <summary>
          <img
            :src="companion.portraits[expression]"
            alt=""
          />
          <span class="mobile-companion-copy">
            <strong>{{ companion.name }}</strong>
            <span>{{ visibleDialogue }}</span>
          </span>
          <ChevronDown :size="18" aria-hidden="true" />
        </summary>
        <div class="mobile-companion-portrait">
          <CompanionPortrait
            :companion="companion"
            :expression="expression"
            :reduce-motion="reduceMotion"
          />
        </div>
      </details>

      <div v-if="error" class="inn-error" role="alert">
        <TriangleAlert :size="18" aria-hidden="true" />
        <span>{{ error }}</span>
      </div>

      <div class="travel-state" aria-label="今日旅途笔记">
        <div>
          <Clock3 :size="17" aria-hidden="true" />
          <span>等待回想</span>
          <strong>{{ plan?.dueAvailableCount ?? '—' }}</strong>
        </div>
        <div>
          <Target :size="17" aria-hidden="true" />
          <span>新路标</span>
          <strong>{{ plan?.newAvailableCount ?? '—' }}</strong>
        </div>
        <div>
          <NotebookTabs :size="17" aria-hidden="true" />
          <span>私人词库</span>
          <strong>{{ lexiconsCount }}</strong>
        </div>
      </div>

      <div class="inn-actions">
        <button
          v-if="activeSession"
          type="button"
          class="primary-cta"
          :disabled="loading"
          @click="emit('resume', activeSession)"
        >
          <BookOpen :size="19" aria-hidden="true" />
          <span>{{ actionLabel }}</span>
          <ArrowRight :size="18" aria-hidden="true" />
        </button>
        <button
          v-else
          type="button"
          class="primary-cta"
          :disabled="loading || selectedCount === 0"
          @click="emit('start')"
        >
          <BookOpen :size="19" aria-hidden="true" />
          <span>{{ loading ? '正在翻开笔记' : actionLabel }}</span>
          <ArrowRight :size="18" aria-hidden="true" />
        </button>
        <button
          type="button"
          class="quiet-cta"
          aria-label="打开快捷键帮助"
          @click="emit('help')"
        >
          <CircleHelp :size="19" aria-hidden="true" />
          <span>快捷键</span>
        </button>
        <button
          type="button"
          class="quiet-cta"
          @click="emit('classic-study')"
        >
          <Feather :size="19" aria-hidden="true" />
          <span>经典卡片</span>
        </button>
      </div>

      <p class="session-note">
        {{ user.preferences.nickname || user.displayName }} · 今日目标
        {{ user.preferences.dailyGoal }} 项
        <span v-if="sessionCount > 0">· {{ sessionCount }} 份笔记可续接</span>
      </p>
    </div>

    <div class="lia-stage">
      <div class="lamp-glow" aria-hidden="true"></div>
      <CompanionPortrait
        :companion="companion"
        :expression="expression"
        :reduce-motion="reduceMotion"
      />
      <div
        v-if="companionDensity !== 'quiet'"
        class="companion-dialogue"
        aria-live="polite"
      >
        <span>{{ companion.name }} · {{ companion.epithet }}</span>
        <p>{{ visibleDialogue }}</p>
        <small v-if="companionDensity === 'rich'">
          {{ companion.dialogue.training }}
        </small>
      </div>
    </div>

    <div class="chapter-footer" aria-label="第一章尚未开放">
      <span class="seal" aria-hidden="true">I</span>
      <div>
        <strong>暖灯与失语之森</strong>
        <span>探索与溪桥遭遇将在第一章内容包接入后开启</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.inn-lobby {
  position: relative;
  display: grid;
  grid-template-columns: minmax(360px, 0.92fr) minmax(380px, 1.08fr);
  grid-template-rows: minmax(560px, 1fr) auto;
  min-height: calc(100vh - 132px);
  overflow: hidden;
  border: 1px solid rgb(211 168 94 / 34%);
  border-radius: 8px;
  background:
    linear-gradient(90deg, rgb(22 14 14 / 89%) 0 44%, rgb(17 17 20 / 18%) 71%),
    var(--inn-background) center / cover no-repeat;
  box-shadow: 0 30px 80px rgb(25 13 12 / 28%);
  color: #f7ead5;
  isolation: isolate;
}

.night-wash {
  position: absolute;
  inset: 0;
  z-index: -1;
  background:
    radial-gradient(circle at 73% 46%, rgb(255 197 110 / 16%), transparent 28%),
    linear-gradient(180deg, transparent 64%, rgb(15 10 11 / 82%) 100%);
  pointer-events: none;
}

.inn-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-self: center;
  padding: clamp(34px, 4vw, 58px);
}

.chapter-label {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  width: fit-content;
  margin: 0 0 18px;
  color: #d9b779;
  font-size: 0.76rem;
  font-weight: 700;
  letter-spacing: 0.08em;
}

h1 {
  display: grid;
  max-width: 15ch;
  margin: 0;
  color: #fff0d7;
  font-family: "Songti SC", "Noto Serif CJK SC", Georgia, serif;
  font-size: clamp(2.45rem, 3.2vw, 3.4rem);
  font-weight: 600;
  letter-spacing: 0;
  line-height: 1.08;
  text-wrap: balance;
  text-shadow: 0 3px 24px rgb(17 9 8 / 54%);
}

h1 span {
  display: block;
  white-space: nowrap;
}

.scene-lead {
  max-width: 48ch;
  margin: 20px 0 0;
  color: #d4c2aa;
  font-size: 0.94rem;
  line-height: 1.85;
}

.companion-switcher {
  display: flex;
  gap: 8px;
  margin-top: 20px;
}

.companion-density {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.companion-density > span {
  color: #9f8a70;
  font-size: 0.69rem;
}

.companion-density > div {
  display: flex;
  gap: 2px;
  padding: 2px;
  border: 1px solid rgb(217 183 121 / 16%);
  border-radius: 6px;
  background: rgb(27 19 19 / 52%);
}

.companion-density button {
  min-height: 28px;
  padding: 0 9px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #a99479;
  font-size: 0.68rem;
  cursor: pointer;
}

.companion-density button.selected {
  background: rgb(207 168 105 / 20%);
  color: #f0d7ad;
}

.companion-choice {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  min-width: 164px;
  align-items: center;
  gap: 9px;
  padding: 7px 10px 7px 7px;
  border: 1px solid rgb(217 183 121 / 18%);
  border-radius: 7px;
  background: rgb(33 23 23 / 60%);
  color: #d8c5aa;
  text-align: left;
  cursor: pointer;
  backdrop-filter: blur(8px);
}

.companion-choice img {
  width: 42px;
  height: 42px;
  border: 1px solid rgb(217 183 121 / 26%);
  border-radius: 50%;
  background: #392926;
  object-fit: cover;
  object-position: 50% 10%;
}

.companion-choice > span {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.companion-choice strong {
  color: #f3e1c5;
  font-size: 0.78rem;
}

.companion-choice small {
  overflow: hidden;
  color: #9f8a70;
  font-size: 0.62rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.companion-choice.selected {
  border-color: rgb(233 196 133 / 56%);
  background: rgb(78 53 43 / 76%);
  box-shadow: inset 0 0 0 1px rgb(255 228 177 / 8%);
}

.companion-choice:focus-visible {
  outline: 3px solid rgb(246 214 158 / 44%);
  outline-offset: 2px;
}

.inn-error {
  display: flex;
  max-width: 540px;
  align-items: flex-start;
  gap: 9px;
  margin-top: 18px;
  padding: 10px 12px;
  border-left: 3px solid #c96c64;
  background: rgb(101 38 35 / 34%);
  color: #ffd9d2;
  font-size: 0.82rem;
}

.travel-state {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  max-width: 610px;
  margin-top: 30px;
  border-top: 1px solid rgb(217 183 121 / 26%);
  border-bottom: 1px solid rgb(217 183 121 / 26%);
}

.travel-state > div {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 15px 13px;
  color: #bda88e;
}

.travel-state > div + div {
  border-left: 1px solid rgb(217 183 121 / 18%);
}

.travel-state span {
  overflow: hidden;
  font-size: 0.73rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.travel-state strong {
  color: #f8e6c7;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 1.32rem;
  font-weight: 500;
}

.inn-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 26px;
}

.mobile-companion {
  display: none;
}

.primary-cta,
.quiet-cta {
  display: inline-flex;
  min-height: 48px;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 0 17px;
  border-radius: 7px;
  font: inherit;
  font-size: 0.87rem;
  font-weight: 750;
  cursor: pointer;
}

.primary-cta {
  border: 1px solid #d9b779;
  background: linear-gradient(180deg, #f0d49d, #cfa869);
  color: #302017;
  box-shadow: 0 9px 26px rgb(203 156 83 / 18%);
}

.primary-cta:disabled {
  border-color: #685c4d;
  background: #4a4037;
  color: #a99a87;
  box-shadow: none;
  cursor: not-allowed;
}

.quiet-cta {
  border: 1px solid rgb(230 204 165 / 32%);
  background: rgb(44 31 29 / 72%);
  color: #eadbc7;
  backdrop-filter: blur(10px);
}

.primary-cta:focus-visible,
.quiet-cta:focus-visible {
  outline: 3px solid rgb(246 214 158 / 44%);
  outline-offset: 2px;
}

.session-note {
  margin: 15px 0 0;
  color: #a89177;
  font-size: 0.76rem;
}

.lia-stage {
  position: relative;
  align-self: stretch;
  min-width: 0;
  min-height: 560px;
}

.lia-stage :deep(.companion-portrait) {
  position: absolute;
  right: clamp(-26px, -1vw, 4px);
  bottom: 94px;
  width: min(100%, 510px);
  height: calc(100% - 116px);
}

.lamp-glow {
  position: absolute;
  right: 24%;
  bottom: 21%;
  width: 42%;
  aspect-ratio: 0.64;
  border-radius: 50%;
  background: radial-gradient(ellipse, rgb(255 208 128 / 20%), transparent 68%);
  filter: blur(18px);
  pointer-events: none;
}

.companion-dialogue {
  position: absolute;
  z-index: 2;
  right: clamp(18px, 3vw, 42px);
  bottom: 24px;
  left: clamp(18px, 3vw, 42px);
  padding: 14px 17px;
  border: 1px solid rgb(228 193 139 / 35%);
  border-radius: 7px;
  background: rgb(31 21 21 / 88%);
  box-shadow: 0 14px 38px rgb(15 8 8 / 28%);
  backdrop-filter: blur(13px);
}

.companion-dialogue span {
  color: #ddb97e;
  font-size: 0.69rem;
  font-weight: 750;
  letter-spacing: 0.05em;
}

.companion-dialogue p {
  margin: 6px 0 0;
  color: #f1e1cd;
  font-size: 0.86rem;
  line-height: 1.7;
}

.companion-dialogue small {
  display: block;
  margin-top: 5px;
  color: #bda88e;
  font-size: 0.72rem;
  line-height: 1.55;
}

.chapter-footer {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 16px clamp(30px, 5vw, 72px) 18px;
  border-top: 1px solid rgb(217 183 121 / 18%);
  background: rgb(24 16 17 / 8%);
}

.seal {
  display: grid;
  width: 31px;
  height: 31px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid rgb(217 183 121 / 38%);
  border-radius: 50%;
  color: #d9b779;
  font-family: Georgia, "Times New Roman", serif;
}

.chapter-footer div {
  display: grid;
  gap: 3px;
}

.chapter-footer strong {
  color: #ead8bd;
  font-size: 0.8rem;
}

.chapter-footer div span {
  color: #9e8971;
  font-size: 0.7rem;
}

@media (max-width: 980px) {
  .inn-lobby {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto minmax(420px, 58vh) auto;
  }

  .inn-copy {
    padding-bottom: 20px;
  }

  .lia-stage {
    min-height: 420px;
  }

  .lia-stage :deep(.companion-portrait) {
    right: 50%;
    bottom: 96px;
    width: min(88%, 440px);
    transform: translateX(50%);
  }

  .chapter-footer {
    grid-column: 1;
  }
}

@media (max-width: 620px) {
  .inn-lobby {
    min-height: calc(100vh - 170px);
    grid-template-rows: auto auto;
    background:
      linear-gradient(180deg, rgb(22 14 14 / 91%) 0 48%, rgb(15 13 15 / 30%) 78%),
      var(--inn-background) 58% center / auto 72% no-repeat,
      #1e1516;
  }

  .inn-copy {
    padding: 28px 22px 18px;
  }

  h1 {
    font-size: clamp(2.3rem, 10vw, 3rem);
  }

  .scene-lead {
    font-size: 0.86rem;
  }

  .mobile-companion {
    display: block;
    margin-top: 18px;
    border: 1px solid rgb(228 193 139 / 28%);
    border-radius: 7px;
    background: rgb(31 21 21 / 78%);
    backdrop-filter: blur(10px);
  }

  .companion-switcher {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    margin-top: 16px;
  }

  .companion-choice {
    min-width: 0;
  }

  .companion-density {
    align-items: flex-start;
    justify-content: space-between;
  }

  .mobile-companion summary {
    display: grid;
    grid-template-columns: 48px minmax(0, 1fr) auto;
    align-items: center;
    gap: 11px;
    padding: 10px 12px;
    color: #eadbc7;
    cursor: pointer;
    list-style: none;
  }

  .mobile-companion summary::-webkit-details-marker {
    display: none;
  }

  .mobile-companion summary > img {
    width: 48px;
    height: 48px;
    border: 1px solid rgb(228 193 139 / 32%);
    border-radius: 50%;
    background: #3c2926;
    object-fit: cover;
    object-position: 50% 11%;
  }

  .mobile-companion-copy {
    display: grid;
    gap: 3px;
    min-width: 0;
  }

  .mobile-companion-copy strong {
    color: #ddb97e;
    font-size: 0.7rem;
  }

  .mobile-companion-copy > span {
    overflow: hidden;
    color: #f1e1cd;
    font-size: 0.73rem;
    line-height: 1.55;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-companion[open] summary > svg {
    transform: rotate(180deg);
  }

  .mobile-companion-portrait {
    height: 370px;
    overflow: hidden;
    border-top: 1px solid rgb(228 193 139 / 18%);
  }

  .mobile-companion-portrait :deep(.companion-portrait) {
    width: min(88%, 310px);
    height: 100%;
    margin: 0 auto;
  }

  .travel-state {
    margin-top: 22px;
  }

  .travel-state > div {
    grid-template-columns: auto auto;
    gap: 5px 7px;
    padding: 11px 8px;
  }

  .travel-state svg {
    display: none;
  }

  .travel-state span {
    grid-column: 1 / -1;
    font-size: 0.66rem;
  }

  .travel-state strong {
    grid-column: 1 / -1;
    font-size: 1.15rem;
  }

  .inn-actions {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .primary-cta {
    min-width: 0;
  }

  .quiet-cta span {
    display: none;
  }

  .quiet-cta {
    width: 48px;
    padding: 0;
  }

  .lia-stage {
    display: none;
  }

  .companion-dialogue {
    right: 16px;
    bottom: 14px;
    left: 16px;
    padding: 11px 13px;
  }

  .companion-dialogue p {
    font-size: 0.78rem;
  }

  .chapter-footer {
    padding: 13px 20px 15px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .primary-cta,
  .quiet-cta {
    scroll-behavior: auto;
  }
}
</style>
