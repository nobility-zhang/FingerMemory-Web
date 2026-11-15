<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import type { FingerMemoryRepository, StorageStatus } from '../core/repository';
import LexiconManager from './LexiconManager.vue';
import PracticeWorkspace from './PracticeWorkspace.vue';

const props = defineProps<{
  repository: FingerMemoryRepository;
}>();

type StorageState = 'initializing' | 'ready' | 'error';

const storageState = ref<StorageState>('initializing');
const storageStatus = ref<StorageStatus | null>(null);
const activeView = ref<'practice' | 'library'>('practice');
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

onMounted(async () => {
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
  <div class="app-shell">
    <header class="topbar">
      <a class="brand" href="/" aria-label="FingerMemory 首页">
        <span class="brand-mark" aria-hidden="true">F</span>
        <span>FingerMemory</span>
      </a>
      <span class="stage-label">M4</span>
    </header>

    <div class="app-layout">
      <nav class="primary-nav" aria-label="主导航">
        <p class="nav-label">工作区</p>
        <ul class="nav-list">
          <li>
            <button
              type="button"
              class="nav-item"
              :class="{ 'nav-item--active': activeView === 'practice' }"
              :aria-current="activeView === 'practice' ? 'page' : undefined"
              @click="activeView = 'practice'"
            >
              学习
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
              词库
            </button>
          </li>
          <li>
            <button type="button" class="nav-item" disabled>复习</button>
          </li>
        </ul>
        <div class="storage-note">
          <span class="storage-dot" aria-hidden="true"></span>
          <span>本地优先</span>
        </div>
      </nav>

      <main class="workspace">
        <header class="workspace-heading">
          <div>
            <p class="section-label">{{ activeView === 'practice' ? '概览' : '词库' }}</p>
            <h1>{{ activeView === 'practice' ? '学习工作台' : '词库管理' }}</h1>
          </div>
          <span class="local-badge" data-testid="storage-summary">
            {{ localBadgeText }}
          </span>
        </header>

        <PracticeWorkspace
          v-if="storageState === 'ready' && activeView === 'practice'"
          :repository="repository"
        />
        <LexiconManager
          v-else-if="storageState === 'ready' && activeView === 'library'"
          :repository="repository"
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

        <footer class="workspace-footer">
          <span>FingerMemory</span>
          <span>Keyboard-first vocabulary practice</span>
        </footer>
      </main>
    </div>
  </div>
</template>
