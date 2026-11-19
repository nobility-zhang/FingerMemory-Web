<script setup lang="ts">
import {
  computed,
  ref,
  watch,
} from 'vue';
import {
  ArrowRight,
  BookOpen,
  ChevronDown,
  CircleHelp,
  Clock3,
  Feather,
  Map,
  NotebookTabs,
  Play,
  RotateCcw,
  ScrollText,
  Target,
  TriangleAlert,
} from '@lucide/vue';
import type {
  AdventureCompletionView,
  AdventureRecollectionView,
  AdventureRunView,
  ContentPackageInstallation,
  ContentPackageRequirement,
  QueuePlanView,
  SessionEnvelope,
  UserView,
} from '../api/client';
import CompanionPortrait from '../companions/CompanionPortrait.vue';
import type {
  CompanionProfile,
} from '../companions/catalog';
import { companions as builtInCompanions } from '../companions/catalog';
import innBackground from '../assets/scenes/inn/runtime/inn-warm-lantern.jpg';

const props = withDefaults(
  defineProps<{
    companion: CompanionProfile;
    companionOptions?: CompanionProfile[];
    companionDensity: 'quiet' | 'standard' | 'rich';
    reduceMotion: boolean;
    user: UserView;
    plan?: QueuePlanView;
    lexiconsCount: number;
    activeSession?: SessionEnvelope;
    sessionCount: number;
    contentPackages: ContentPackageInstallation[];
    adventures: AdventureRunView[];
    completions?: AdventureCompletionView[];
    recollections?: AdventureRecollectionView[];
    unlockedCompanionIds?: string[];
    loading: boolean;
    error: string | null;
  }>(),
  {
    plan: undefined,
    activeSession: undefined,
    recollections: () => [],
    completions: () => [],
    companionOptions: () => [...builtInCompanions],
    unlockedCompanionIds: () => ['lia', 'serin'],
  },
);

const emit = defineEmits<{
  start: [];
  resume: [session: SessionEnvelope];
  help: [];
  'select-companion': [companionId: string];
  'select-density': [density: 'quiet' | 'standard' | 'rich'];
  'classic-study': [];
  'start-adventure': [input: {
    installationId: string;
    sourceCompletionId?: string;
  }];
  'upgrade-adventure': [runId: string, installationId: string];
  'resume-adventure': [runId: string];
  'content-settings': [];
}>();

const densities = ['quiet', 'standard', 'rich'] as const;
const selectedInstallationId = ref('');
const selectedSourceCompletionId = ref('');
const selectedUpgradeCompletionId = ref('');
const availableCompanions = computed(() =>
  props.companionOptions.filter((candidate) =>
    props.unlockedCompanionIds.includes(candidate.id),
  ),
);
const selectedCount = computed(() => props.plan?.selected.length ?? 0);
const selectedPackage = computed(() =>
  props.contentPackages.find(
    (contentPackage) => contentPackage.id === selectedInstallationId.value,
  ),
);
const requirements = computed(() => selectedPackage.value?.requirements ?? []);
const eligibleCompletions = computed(() => props.completions.filter(
  (completion) => requirements.value.some(
    (requirement) => matchesRequirement(completion, requirement),
  ),
));
const upgradeCompletions = computed(() => {
  const selected = selectedPackage.value;
  if (selected === undefined) {
    return [];
  }
  return props.completions.filter(
    (completion) =>
      completion.bookId === selected.bookId
      && completion.contentVersion !== selected.contentVersion
      && compareVersions(completion.contentVersion, selected.contentVersion) < 0,
  );
});
const activeAdventure = computed(() =>
  props.adventures.find(
    (run) => run.installationId === selectedInstallationId.value,
  ),
);
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

watch(
  () => props.contentPackages,
  (contentPackages) => {
    if (
      contentPackages.length > 0
      && !contentPackages.some(
        (contentPackage) => contentPackage.id === selectedInstallationId.value,
      )
    ) {
      selectedInstallationId.value = contentPackages[0]!.id;
    }
  },
  { immediate: true },
);

watch(
  eligibleCompletions,
  (completions) => {
    if (
      !completions.some(
        (completion) => completion.id === selectedSourceCompletionId.value,
      )
    ) {
      selectedSourceCompletionId.value = completions[0]?.id ?? '';
    }
  },
  { immediate: true },
);

watch(
  upgradeCompletions,
  (completions) => {
    if (
      !completions.some(
        (completion) => completion.id === selectedUpgradeCompletionId.value,
      )
    ) {
      selectedUpgradeCompletionId.value = completions[0]?.id ?? '';
    }
  },
  { immediate: true },
);

function startOrResumeAdventure(): void {
  const active = activeAdventure.value;
  if (active !== undefined) {
    emit('resume-adventure', active.id);
    return;
  }
  if (selectedPackage.value !== undefined) {
    if (
      requirements.value.length > 0
      && selectedSourceCompletionId.value.length === 0
    ) {
      return;
    }
    emit('start-adventure', {
      installationId: selectedPackage.value.id,
      sourceCompletionId:
        selectedSourceCompletionId.value.length > 0
          ? selectedSourceCompletionId.value
          : undefined,
    });
  }
}

function upgradeAdventure(): void {
  if (
    selectedPackage.value === undefined
    || selectedUpgradeCompletionId.value.length === 0
  ) {
    return;
  }
  const completion = upgradeCompletions.value.find(
    (item) => item.id === selectedUpgradeCompletionId.value,
  );
  if (completion !== undefined) {
    emit('upgrade-adventure', completion.runId, selectedPackage.value.id);
  }
}

function packageTitle(bookId: string): string {
  return props.contentPackages.find((item) => item.bookId === bookId)?.title
    ?? bookId;
}

function matchesRequirement(
  completion: AdventureCompletionView,
  requirement: ContentPackageRequirement,
): boolean {
  return completion.bookId === requirement.bookId
    && completion.endingId === requirement.endingId
    && (
      requirement.minContentVersion === undefined
      || compareVersions(
        completion.contentVersion,
        requirement.minContentVersion,
      ) >= 0
    )
    && (
      requirement.maxContentVersionExclusive === undefined
      || compareVersions(
        completion.contentVersion,
        requirement.maxContentVersionExclusive,
      ) < 0
    );
}

function compareVersions(left: string, right: string): number {
  const leftParts = left.split('.').map(Number);
  const rightParts = right.split('.').map(Number);
  for (let index = 0; index < 3; index += 1) {
    const leftPart = leftParts[index] ?? 0;
    const rightPart = rightParts[index] ?? 0;
    if (leftPart !== rightPart) {
      return leftPart - rightPart;
    }
  }
  return 0;
}

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
          v-for="candidate in availableCompanions"
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
            <small>{{ candidate.epithet }}</small>
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

      <section class="adventure-entry" aria-labelledby="adventure-entry-title">
        <div class="adventure-entry-heading">
          <span aria-hidden="true">
            <Map :size="18" />
          </span>
          <div>
            <p class="section-label">旅店中的词库冒险</p>
            <h2 id="adventure-entry-title">
              {{ selectedPackage?.title ?? '等待一本可冒险的词库' }}
            </h2>
          </div>
        </div>

        <div v-if="contentPackages.length > 0" class="adventure-entry-body">
          <label>
            <span>冒险书</span>
            <select v-model="selectedInstallationId" :disabled="loading">
              <option
                v-for="contentPackage in contentPackages"
                :key="contentPackage.id"
                :value="contentPackage.id"
              >
                {{ contentPackage.title }} · v{{ contentPackage.contentVersion }}
              </option>
            </select>
          </label>
          <p v-if="activeAdventure" class="adventure-version">
            <RotateCcw :size="15" aria-hidden="true" />
            已固定 v{{ activeAdventure.contentVersion }} ·
            {{ activeAdventure.contentHash.slice(0, 8) }}
          </p>
          <p v-else class="adventure-version">
            开局后将固定当前书版本，升级不会改写这段旅途。
          </p>
          <div
            v-if="requirements.length > 0"
            class="adventure-requirements"
          >
            <p class="section-label">前作结局</p>
            <label v-if="eligibleCompletions.length > 0">
              <span>继承完成回执</span>
              <select v-model="selectedSourceCompletionId">
                <option
                  v-for="completion in eligibleCompletions"
                  :key="completion.id"
                  :value="completion.id"
                >
                  {{ packageTitle(completion.bookId) }}
                  · {{ completion.endingTitle }}
                  · v{{ completion.contentVersion }}
                </option>
              </select>
            </label>
            <p v-else class="adventure-warning" role="status">
              <TriangleAlert :size="16" aria-hidden="true" />
              尚未满足前作条件：需要完成
              <template
                v-for="(requirement, index) in requirements"
                :key="`${requirement.bookId}:${requirement.endingId}`"
              >
                <span v-if="index > 0">，或</span>
                《{{ packageTitle(requirement.bookId) }}》的
                {{ requirement.endingTitle }} 结局
              </template>
            </p>
          </div>
          <div
            v-if="!activeAdventure && upgradeCompletions.length > 0"
            class="adventure-upgrade"
          >
            <p class="section-label">旧旅程新增章节</p>
            <label>
              <span>安全检查点</span>
              <select v-model="selectedUpgradeCompletionId">
                <option
                  v-for="completion in upgradeCompletions"
                  :key="completion.id"
                  :value="completion.id"
                >
                  {{ completion.endingTitle }}
                  · v{{ completion.contentVersion }}
                </option>
              </select>
            </label>
            <button type="button" :disabled="loading" @click="upgradeAdventure">
              继承检查点并开启新章
            </button>
          </div>
          <button
            type="button"
            class="adventure-cta"
            :disabled="loading
              || (requirements.length > 0 && eligibleCompletions.length === 0)"
            @click="startOrResumeAdventure"
          >
            <RotateCcw v-if="activeAdventure" :size="18" aria-hidden="true" />
            <Play v-else :size="18" aria-hidden="true" />
            <span>{{ activeAdventure ? '继续冒险' : '开始第一章' }}</span>
            <ArrowRight :size="18" aria-hidden="true" />
          </button>
        </div>

        <div v-else class="adventure-entry-body adventure-entry-body--empty">
          <p>旅店收到了新的暖灯，但书架里还没有可出发的词库冒险。</p>
          <button type="button" @click="emit('content-settings')">
            打开账号书架
          </button>
        </div>
      </section>

      <details v-if="recollections.length > 0" class="recollection-book">
        <summary>
          <ScrollText :size="17" aria-hidden="true" />
          <span>旅途回忆</span>
          <small>{{ recollections.length }}</small>
          <ChevronDown :size="16" aria-hidden="true" />
        </summary>
        <ul>
          <li
            v-for="recollection in recollections.slice(-6).reverse()"
            :key="recollection.key"
          >
            <strong>{{ recollection.title }}</strong>
            <span>{{ recollection.speaker }} · {{ recollection.text }}</span>
          </li>
        </ul>
      </details>

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

    <div class="chapter-footer" aria-label="第一章冒险状态">
      <span class="seal" aria-hidden="true">I</span>
      <div>
        <strong>{{ selectedPackage?.title ?? '旅店冒险' }}</strong>
        <span v-if="activeAdventure">
          固定内容 v{{ activeAdventure.contentVersion }} ·
          {{ activeAdventure.currentNode.title }}
        </span>
        <span v-else-if="selectedPackage">
          已装内容 v{{ selectedPackage.contentVersion }}，可在旅店整备后出发
        </span>
        <span v-else>在账号书架安装 .fmbook 后，从这张桌子出发</span>
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

.adventure-entry {
  display: grid;
  max-width: 610px;
  gap: 14px;
  margin-top: 18px;
  border: 1px solid rgb(217 183 121 / 24%);
  border-left: 3px solid #d1a55f;
  padding: 16px;
  background: rgb(29 20 20 / 52%);
  backdrop-filter: blur(10px);
}

.adventure-entry-heading {
  display: flex;
  align-items: center;
  gap: 12px;
}

.adventure-entry-heading > span {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid rgb(217 183 121 / 34%);
  border-radius: 50%;
  color: #e2bd78;
}

.adventure-entry-heading > div {
  min-width: 0;
}

.adventure-entry .section-label {
  margin: 0 0 3px;
  color: #c9a66d;
  font-size: 0.68rem;
}

.adventure-entry h2 {
  margin: 0;
  color: #f5e3c8;
  font-family: "Songti SC", "Noto Serif CJK SC", Georgia, serif;
  font-size: 1.23rem;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.adventure-entry-body {
  display: grid;
  gap: 10px;
}

.adventure-entry-body label {
  display: grid;
  gap: 5px;
  color: #bfa98d;
  font-size: 0.72rem;
}

.adventure-entry-body select {
  min-height: 38px;
  border: 1px solid rgb(217 183 121 / 36%);
  border-radius: 5px;
  padding: 6px 10px;
  background: #251a19;
  color: #f2e0c4;
}

.adventure-version {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: #aebdae;
  font-size: 0.7rem;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.adventure-requirements,
.adventure-upgrade {
  display: grid;
  gap: 8px;
  border: 1px solid rgb(217 183 121 / 24%);
  border-radius: 6px;
  padding: 10px;
  background: rgb(30 22 21 / 54%);
}

.adventure-warning {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin: 0;
  color: #e3b899;
  font-size: 0.72rem;
  line-height: 1.5;
}

.adventure-upgrade button {
  width: fit-content;
  min-height: 36px;
  border: 1px solid rgb(217 183 121 / 44%);
  border-radius: 5px;
  padding: 6px 11px;
  background: rgb(61 43 36 / 74%);
  color: #efd9b7;
  cursor: pointer;
}

.adventure-cta {
  display: inline-flex;
  width: fit-content;
  min-height: 43px;
  align-items: center;
  gap: 8px;
  border: 1px solid #e0bf7a;
  border-radius: 6px;
  padding: 8px 14px;
  background: linear-gradient(180deg, #f0d49d, #cfa869);
  color: #302017;
  font: inherit;
  font-size: 0.84rem;
  font-weight: 750;
  cursor: pointer;
}

.adventure-entry-body--empty {
  color: #cfbea6;
  font-size: 0.78rem;
  line-height: 1.55;
}

.adventure-entry-body--empty p {
  margin: 0;
}

.adventure-entry-body--empty button {
  width: fit-content;
  border: 0;
  border-bottom: 1px solid #d6b474;
  padding: 2px 0;
  background: transparent;
  color: #e6c582;
  font: inherit;
  cursor: pointer;
}

.adventure-entry button:focus-visible,
.adventure-entry select:focus-visible {
  outline: 3px solid rgb(246 214 158 / 44%);
  outline-offset: 2px;
}

.recollection-book {
  max-width: 610px;
  margin-top: 12px;
  border: 1px solid rgb(159 190 171 / 24%);
  background: rgb(24 24 23 / 48%);
}

.recollection-book summary {
  display: flex;
  min-height: 42px;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  color: #d7c698;
  cursor: pointer;
  list-style: none;
}

.recollection-book summary::-webkit-details-marker {
  display: none;
}

.recollection-book summary span {
  flex: 1;
  font-size: 0.78rem;
  font-weight: 700;
}

.recollection-book summary small {
  color: #9fb6a7;
}

.recollection-book[open] summary > svg:last-child {
  transform: rotate(180deg);
}

.recollection-book ul {
  display: grid;
  gap: 1px;
  margin: 0;
  padding: 0;
  background: rgb(159 190 171 / 14%);
  list-style: none;
}

.recollection-book li {
  display: grid;
  gap: 3px;
  padding: 10px 12px;
  background: rgb(25 23 23 / 82%);
}

.recollection-book strong {
  color: #efdfbd;
  font-size: 0.76rem;
}

.recollection-book li span {
  color: #aebeb1;
  font-size: 0.7rem;
  line-height: 1.5;
  overflow-wrap: anywhere;
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

  .adventure-entry {
    padding: 13px;
  }

  .adventure-cta {
    width: 100%;
    justify-content: center;
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
