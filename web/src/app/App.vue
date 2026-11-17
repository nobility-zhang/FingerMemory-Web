<script setup lang="ts">
import {
  computed,
  defineAsyncComponent,
  onBeforeUnmount,
  onMounted,
  ref,
} from 'vue';
import {
  Activity,
  Database,
  Home,
  Keyboard,
  LibraryBig,
  UserRound,
} from '@lucide/vue';
import type { FingerMemoryRepository, StorageStatus } from '../core/repository';
import GmWorkspace from '../admin/GmWorkspace.vue';
import AccountWorkspace from './AccountWorkspace.vue';
import DataWorkspace from './DataWorkspace.vue';
import LexiconManager from './LexiconManager.vue';
import PracticeWorkspace from './PracticeWorkspace.vue';
import ReminderBanner from './ReminderBanner.vue';
import ReviewWorkspace from './ReviewWorkspace.vue';

const GameWorkspace = defineAsyncComponent(() => import('../game/GameWorkspace.vue'));

const props = defineProps<{
  repository: FingerMemoryRepository;
}>();

type StorageState = 'initializing' | 'ready' | 'error';

const storageState = ref<StorageState>('initializing');
const storageStatus = ref<StorageStatus | null>(null);
const gameFocusMode = ref(false);
const isGmRoute = window.location.pathname === '/gm'
  || window.location.pathname === '/gm/';
const activeView = ref<
  'game' | 'practice' | 'library' | 'review' | 'data' | 'account'
>('game');
let disposed = false;

const stateLabel = computed(() => {
  if (storageState.value === 'ready') {
    return '已就绪';
  }
  if (storageState.value === 'error') {
    return '不可用';
  }
  return '初始化中';
});

const summaryText = computed(() => {
  if (storageState.value === 'error') {
    return '请检查浏览器是否允许使用 IndexedDB。';
  }
  if (storageStatus.value === null) {
    return '正在连接本地数据库。';
  }

  const { lexicons, studyItems } = storageStatus.value.counts;
  const model = storageStatus.value.scheduler.modelVersion.toUpperCase();
  const schemaVersion = storageStatus.value.schemaVersion;
  return `${model} · 本地数据 v${schemaVersion} · ${lexicons} 个词库 · ${studyItems} 个学习目标`;
});

const localBadgeText = computed(() => {
  if (storageStatus.value === null) {
    return '本地数据';
  }
  const model = storageStatus.value.scheduler.modelVersion.toUpperCase();
  return `${model} · 数据 v${storageStatus.value.schemaVersion}`;
});

const viewHeading = computed(() => {
  if (activeView.value === 'library') {
    return { label: '词库', title: '词库管理' };
  }
  if (activeView.value === 'data') {
    return { label: '数据', title: '数据管理' };
  }
  if (activeView.value === 'review') {
    return { label: '复习', title: '正式复习诊断' };
  }
  if (activeView.value === 'account') {
    return { label: '账号', title: '跨设备学习空间' };
  }
  if (activeView.value === 'practice') {
    return { label: '经典卡片', title: '经典学习卡片' };
  }
  return { label: '旅店', title: '暖灯旅店' };
});

function handleDataRestored(status: StorageStatus): void {
  storageStatus.value = status;
}

function openReminderPractice(): void {
  activeView.value = 'practice';
}

onMounted(async () => {
  if (isGmRoute) {
    return;
  }
  try {
    const status = await props.repository.initialize();
    if (!disposed) {
      storageStatus.value = status;
      storageState.value = 'ready';
    }
  } catch {
    if (!disposed) {
      storageState.value = 'error';
    }
  }
});

onBeforeUnmount(() => {
  disposed = true;
});
</script>

<template>
  <GmWorkspace v-if="isGmRoute" />
  <div v-else class="app-shell" :class="{ 'game-focus-mode': gameFocusMode }">
    <header class="topbar">
      <a class="brand" href="/" aria-label="FingerMemory 首页">
        <span class="brand-mark" aria-hidden="true">F</span>
        <span>FingerMemory</span>
      </a>
      <span class="stage-label">G1 · 暖灯旅店</span>
    </header>

    <div class="app-layout">
      <nav class="primary-nav" aria-label="主导航">
        <p class="nav-label">星灯旅团</p>
        <ul class="nav-list">
          <li>
            <button
              type="button"
              class="nav-item"
              :class="{ 'nav-item--active': activeView === 'game' }"
              :aria-current="activeView === 'game' ? 'page' : undefined"
              @click="activeView = 'game'"
            >
              <Home :size="17" aria-hidden="true" />
              <span>旅店</span>
            </button>
          </li>
          <li>
            <button
              type="button"
              class="nav-item"
              :class="{ 'nav-item--active': activeView === 'practice' }"
              :aria-current="activeView === 'practice' ? 'page' : undefined"
              @click="activeView = 'practice'"
            >
              <Keyboard :size="17" aria-hidden="true" />
              <span>经典卡片</span>
            </button>
          </li>
          <li>
            <button
              type="button"
              class="nav-item"
              :class="{ 'nav-item--active': activeView === 'library' }"
              :aria-current="activeView === 'library' ? 'page' : undefined"
              @click="activeView = 'library'"
            >
              <LibraryBig :size="17" aria-hidden="true" />
              <span>词库</span>
            </button>
          </li>
          <li>
            <button
              type="button"
              class="nav-item"
              :class="{ 'nav-item--active': activeView === 'review' }"
              :aria-current="activeView === 'review' ? 'page' : undefined"
              @click="activeView = 'review'"
            >
              <Activity :size="17" aria-hidden="true" />
              <span>复习诊断</span>
            </button>
          </li>
          <li>
            <button
              type="button"
              class="nav-item"
              :class="{ 'nav-item--active': activeView === 'data' }"
              :aria-current="activeView === 'data' ? 'page' : undefined"
              @click="activeView = 'data'"
            >
              <Database :size="17" aria-hidden="true" />
              <span>数据</span>
            </button>
          </li>
          <li>
            <button
              type="button"
              class="nav-item"
              :class="{ 'nav-item--active': activeView === 'account' }"
              :aria-current="activeView === 'account' ? 'page' : undefined"
              @click="activeView = 'account'"
            >
              <UserRound :size="17" aria-hidden="true" />
              <span>账号</span>
            </button>
          </li>
        </ul>
        <div class="storage-note">
          <span class="storage-dot" aria-hidden="true"></span>
          <span>本地优先</span>
        </div>
      </nav>

      <main class="workspace" :class="{ 'workspace--game': activeView === 'game' }">
        <header v-if="activeView !== 'game'" class="workspace-heading">
          <div>
            <p class="section-label">{{ viewHeading.label }}</p>
            <h1>{{ viewHeading.title }}</h1>
          </div>
          <span class="local-badge" data-testid="storage-summary">
            {{ localBadgeText }}
          </span>
        </header>

        <ReminderBanner
          v-if="storageState === 'ready' && activeView === 'practice'"
          :repository="repository"
          @open-practice="openReminderPractice"
        />

        <Suspense v-if="activeView === 'game'">
          <GameWorkspace
            @focus-mode="gameFocusMode = $event"
            @classic-study="activeView = 'practice'"
          />
          <template #fallback>
            <section class="empty-panel" aria-label="正在加载训练大厅">
              <span class="panel-index" aria-hidden="true">00</span>
              <div class="empty-copy">
                <p class="section-label">暖灯旅店</p>
                <h2>正在点亮旅店</h2>
                <p>正在连接账号和今日旅途笔记。</p>
              </div>
            </section>
          </template>
        </Suspense>
        <PracticeWorkspace
          v-else-if="storageState === 'ready' && activeView === 'practice'"
          :repository="repository"
          :enable-audio="true"
        />
        <LexiconManager
          v-else-if="storageState === 'ready' && activeView === 'library'"
          :repository="repository"
        />
        <ReviewWorkspace
          v-else-if="storageState === 'ready' && activeView === 'review'"
          :repository="repository"
        />
        <DataWorkspace
          v-else-if="storageState === 'ready' && activeView === 'data'"
          :repository="repository"
          @restored="handleDataRestored"
        />
        <AccountWorkspace
          v-else-if="storageState === 'ready' && activeView === 'account'"
          :enable-audio="true"
        />
        <section v-else class="empty-panel" aria-labelledby="empty-title">
          <span class="panel-index" aria-hidden="true">01</span>
          <div class="empty-copy">
            <p class="section-label">本地数据库</p>
            <h2 id="empty-title">{{ stateLabel }}</h2>
            <p data-testid="storage-message">{{ summaryText }}</p>
          </div>
          <span class="empty-state" data-testid="workspace-state">
            {{ stateLabel }}
          </span>
        </section>

        <footer v-if="activeView !== 'game'" class="workspace-footer">
          <span>FingerMemory v1.0.0</span>
          <span>Keyboard-first vocabulary practice</span>
        </footer>
      </main>
    </div>
  </div>
</template>
