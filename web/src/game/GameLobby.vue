<script setup lang="ts">
import { computed } from 'vue';
import {
  ArrowRight,
  BookOpen,
  CircleHelp,
  Clock3,
  Gamepad2,
  Keyboard,
  LockKeyhole,
  Sparkles,
  Target,
  TriangleAlert,
} from '@lucide/vue';
import companion from '../assets/game/companion-navigator.png';
import type {
  QueuePlanView,
  SessionEnvelope,
  UserView,
} from '../api/client';

const props = defineProps<{
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
}>();

const selectedCount = computed(() => props.plan?.selected.length ?? 0);
const completedInSession = computed(
  () => props.activeSession?.session.queue.filter(
    (item) => item.status === 'completed',
  ).length ?? 0,
);
const sessionTotal = computed(() => props.activeSession?.session.queue.length ?? 0);

const modes = [
  {
    name: '键盘回忆',
    description: '当前可用的主动回忆训练',
    icon: Keyboard,
    available: true,
  },
  {
    name: '歌词滚动',
    description: 'P4 接入在线游戏外壳',
    icon: BookOpen,
    available: false,
  },
  {
    name: '弹幕流',
    description: 'P4 接入在线游戏外壳',
    icon: Gamepad2,
    available: false,
  },
  {
    name: '卡片雨',
    description: 'P4 接入在线游戏外壳',
    icon: Sparkles,
    available: false,
  },
] as const;
</script>

<template>
  <div class="game-lobby">
    <section class="lobby-copy" aria-labelledby="lobby-title">
      <p class="eyebrow">Academy Training Deck</p>
      <h1 id="lobby-title">
        {{ user.displayName }}，<br />
        训练舱已就绪。
      </h1>
      <p class="lobby-lead">
        今天的正式复习由服务端队列生成。进入训练后，答案在评分前保持隐藏，
        每次评分都会被记录为可恢复的真实学习事件。
      </p>

      <div v-if="error" class="lobby-error" role="alert">
        <TriangleAlert :size="18" />
        <span>{{ error }}</span>
      </div>

      <div class="mission-strip" aria-label="今日训练数据">
        <div>
          <Clock3 :size="18" aria-hidden="true" />
          <span>今日到期</span>
          <strong>{{ plan?.dueAvailableCount ?? '—' }}</strong>
        </div>
        <div>
          <Target :size="18" aria-hidden="true" />
          <span>新词计划</span>
          <strong>{{ plan?.newAvailableCount ?? '—' }}</strong>
        </div>
        <div>
          <BookOpen :size="18" aria-hidden="true" />
          <span>私人词库</span>
          <strong>{{ lexiconsCount }}</strong>
        </div>
      </div>

      <div class="lobby-actions">
        <button
          v-if="activeSession"
          type="button"
          class="primary-cta"
          :disabled="loading"
          @click="emit('resume', activeSession)"
        >
          <span>继续训练</span>
          <ArrowRight :size="20" aria-hidden="true" />
        </button>
        <button
          v-else
          type="button"
          class="primary-cta"
          :disabled="loading || selectedCount === 0"
          @click="emit('start')"
        >
          <span>{{ selectedCount > 0 ? '开始训练' : '今日队列为空' }}</span>
          <ArrowRight :size="20" aria-hidden="true" />
        </button>
        <button
          type="button"
          class="help-cta"
          aria-label="打开快捷键帮助"
          @click="emit('help')"
        >
          <CircleHelp :size="20" aria-hidden="true" />
          <span>快捷键</span>
        </button>
      </div>

      <p class="goal-line">
        今日目标 {{ user.preferences.dailyGoal }} 项
        <span v-if="sessionCount > 0">· {{ sessionCount }} 个可恢复会话</span>
      </p>
    </section>

    <aside class="companion-panel" aria-label="训练导航员">
      <div class="companion-halo" aria-hidden="true"></div>
      <img :src="companion" alt="原创训练导航员半身像" />
      <div class="companion-dialogue">
        <span>导航员</span>
        <p>
          {{
            activeSession
              ? `上次训练已保存至 ${completedInSession}/${sessionTotal}，可以从断点继续。`
              : selectedCount > 0
                ? `本次队列已准备 ${selectedCount} 个目标，按 Enter 开始。`
                : '当前没有待训练目标，可以先到私人词库补充内容。'
          }}
        </p>
      </div>
    </aside>

    <section class="mode-dock" aria-labelledby="mode-dock-title">
      <div class="mode-heading">
        <div>
          <p class="eyebrow">Training Modules</p>
          <h2 id="mode-dock-title">训练模式</h2>
        </div>
        <span>当前阶段开放键盘回忆</span>
      </div>
      <div class="mode-grid">
        <article
          v-for="mode in modes"
          :key="mode.name"
          class="mode-tile"
          :class="{ locked: !mode.available }"
        >
          <component :is="mode.icon" :size="22" aria-hidden="true" />
          <div>
            <strong>{{ mode.name }}</strong>
            <span>{{ mode.description }}</span>
          </div>
          <LockKeyhole
            v-if="!mode.available"
            :size="16"
            aria-label="尚未开放"
          />
          <ArrowRight v-else :size="16" aria-hidden="true" />
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.game-lobby {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 26px 34px;
  align-items: stretch;
  min-height: calc(100vh - 164px);
}

.lobby-copy {
  align-self: center;
  max-width: 680px;
  padding: 20px 0;
}

.eyebrow {
  margin: 0 0 12px;
  color: #55d7e8;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  color: #f4fbff;
  font-family: "Songti SC", "Noto Serif CJK SC", Georgia, serif;
  font-size: clamp(2.7rem, 5vw, 5rem);
  font-weight: 600;
  letter-spacing: 0;
  line-height: 1.04;
  text-wrap: balance;
}

.lobby-lead {
  max-width: 58ch;
  margin: 20px 0 0;
  color: #b4c9d8;
  font-size: 0.94rem;
  line-height: 1.85;
}

.lobby-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  max-width: 600px;
  margin-top: 18px;
  padding: 10px 12px;
  border-left: 3px solid #f07878;
  background: rgb(240 120 120 / 10%);
  color: #ffd5d5;
  font-size: 0.82rem;
}

.mission-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1px;
  max-width: 650px;
  margin-top: 26px;
  overflow: hidden;
  border: 1px solid rgb(85 215 232 / 18%);
  border-radius: 8px;
  background: rgb(85 215 232 / 16%);
}

.mission-strip > div {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 14px;
  background: rgb(7 20 38 / 88%);
  color: #99b4c9;
}

.mission-strip span {
  font-size: 0.76rem;
}

.mission-strip strong {
  color: #f4fbff;
  font-size: 1.25rem;
}

.lobby-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 24px;
}

.primary-cta,
.help-cta {
  display: inline-flex;
  min-height: 48px;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 0 18px;
  border-radius: 7px;
  font-weight: 800;
  cursor: pointer;
}

.primary-cta {
  border: 1px solid #55d7e8;
  background: #55d7e8;
  color: #061426;
}

.primary-cta:disabled {
  border-color: #3a5267;
  background: #2d4357;
  color: #8ba1b5;
  cursor: not-allowed;
}

.help-cta {
  border: 1px solid rgb(158 181 200 / 34%);
  background: rgb(16 36 58 / 76%);
  color: #dcecf7;
  cursor: pointer;
}

.goal-line {
  margin: 14px 0 0;
  color: #89a2b7;
  font-size: 0.78rem;
}

.companion-panel {
  position: relative;
  display: flex;
  min-height: 520px;
  align-items: flex-end;
  justify-content: center;
  overflow: hidden;
  border: 1px solid rgb(85 215 232 / 18%);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgb(7 20 38 / 6%), rgb(7 20 38 / 82%)),
    rgb(9 24 42 / 48%);
}

.companion-panel img {
  position: relative;
  z-index: 1;
  width: min(100%, 470px);
  height: 100%;
  object-fit: contain;
  object-position: center bottom;
  filter: drop-shadow(0 22px 32px rgb(0 0 0 / 34%));
}

.companion-halo {
  position: absolute;
  top: 12%;
  width: 64%;
  aspect-ratio: 1;
  border: 1px solid rgb(85 215 232 / 20%);
  border-radius: 50%;
  box-shadow:
    0 0 0 28px rgb(85 215 232 / 3%),
    0 0 0 62px rgb(85 215 232 / 2%);
}

.companion-dialogue {
  position: absolute;
  z-index: 2;
  right: 14px;
  bottom: 14px;
  left: 14px;
  padding: 12px 14px;
  border: 1px solid rgb(241 154 182 / 28%);
  border-radius: 8px;
  background: rgb(7 20 38 / 88%);
  backdrop-filter: blur(12px);
}

.companion-dialogue span {
  color: #f19ab6;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.companion-dialogue p {
  margin: 5px 0 0;
  color: #d8e8f3;
  font-size: 0.78rem;
  line-height: 1.6;
}

.mode-dock {
  grid-column: 1 / -1;
  padding: 20px;
  border: 1px solid rgb(158 181 200 / 18%);
  border-radius: 8px;
  background: rgb(7 20 38 / 78%);
  backdrop-filter: blur(10px);
}

.mode-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 14px;
}

.mode-heading h2 {
  margin: 0;
  color: #edf8ff;
  font-size: 1.35rem;
}

.mode-heading > span {
  color: #8ca9c1;
  font-size: 0.76rem;
}

.mode-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.mode-tile {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  min-height: 76px;
  padding: 12px;
  border: 1px solid rgb(85 215 232 / 24%);
  border-radius: 7px;
  background: rgb(18 43 67 / 84%);
  color: #55d7e8;
}

.mode-tile div {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.mode-tile strong {
  color: #edf8ff;
  font-size: 0.85rem;
}

.mode-tile span {
  color: #8ca9c1;
  font-size: 0.68rem;
  line-height: 1.45;
}

.mode-tile.locked {
  border-color: rgb(158 181 200 / 12%);
  background: rgb(10 28 47 / 72%);
  color: #617b90;
}

.mode-tile.locked strong {
  color: #9eb5c8;
}

button:focus-visible {
  outline: 3px solid rgb(85 215 232 / 46%);
  outline-offset: 3px;
}

@media (max-width: 960px) {
  .game-lobby {
    grid-template-columns: 1fr;
  }

  .companion-panel {
    min-height: 430px;
  }

  .mode-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .game-lobby {
    min-height: auto;
  }

  .mission-strip,
  .mode-grid {
    grid-template-columns: 1fr;
  }

  .mission-strip > div {
    padding: 11px 12px;
  }

  .companion-panel {
    min-height: 380px;
  }

  .mode-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {
  .companion-halo {
    box-shadow: none;
  }
}
</style>
