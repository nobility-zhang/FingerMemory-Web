import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type {
  AdventureRunView,
  ContentPackageInstallation,
  QueuePlanView,
  UserView,
} from '../api/client';
import {
  companionExpressions,
  companions,
  fina,
  lia,
  serin,
} from '../companions/catalog';
import InnLobby from './InnLobby.vue';

const user: UserView = {
  id: 'user-inn',
  username: 'traveler',
  displayName: 'Traveler',
  preferences: {
    nickname: '旅人',
    avatarKey: 'starter',
    timezone: 'Asia/Shanghai',
    dailyGoal: 20,
    theme: 'starlit',
    soundEnabled: false,
    shortcutsJson: '{}',
  },
};

const plan: QueuePlanView = {
  at: 1,
  timeZone: 'Asia/Shanghai',
  dayStartAt: 0,
  dayEndAt: 86_400_000,
  settings: { newLimit: 10, reviewLimit: 50, paused: false },
  dueAvailableCount: 2,
  newAvailableCount: 3,
  selected: [],
  dataVersion: 0,
};

const contentPackage: ContentPackageInstallation = {
  id: 'installation-inn',
  namespace: 'fm.demo',
  bookId: 'warm-lantern',
  contentVersion: '1.0.0',
  contentHash: 'a'.repeat(64),
  title: '暖灯与失语之森',
  entryNodeId: 'inn.arrival',
  status: 'installed',
  wordCount: 4,
  chapterCount: 1,
  nodeCount: 9,
  characterCount: 0,
  cardCount: 0,
  encounterCount: 1,
  assetCount: 0,
  installedAtMs: 1,
};

const adventure: AdventureRunView = {
  id: 'run-inn',
  installationId: contentPackage.id,
  namespace: contentPackage.namespace,
  bookId: contentPackage.bookId,
  contentVersion: contentPackage.contentVersion,
  contentHash: contentPackage.contentHash,
  title: contentPackage.title,
  companionKey: 'lia',
  status: 'active',
  runVersion: 2,
  sessionId: 'session-inn',
  currentNode: {
    nodeId: 'forest.signpost',
    chapterId: 'chapter-1',
    type: 'choice',
    title: '失语之森',
    speaker: '',
    text: '两道路痕都通向溪桥。',
    choices: [{ choiceId: 'cross', label: '沿旧路前进' }],
  },
  flags: {},
  createdAtMs: 1,
  updatedAtMs: 2,
};

let mountedApp: ReturnType<typeof createApp> | null = null;

afterEach(() => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
});

describe('InnLobby', () => {
  it('renders Lia and the real queue summary before a session starts', async () => {
    const root = document.createElement('div');
    document.body.append(root);
    mountedApp = createApp(InnLobby, {
      companion: lia,
      companionDensity: 'standard',
      reduceMotion: false,
      user,
      plan: { ...plan, selected: [] },
      lexiconsCount: 4,
      sessionCount: 0,
      contentPackages: [],
      adventures: [],
      loading: false,
      error: null,
    });
    mountedApp.mount(root);
    await nextTick();

    expect(root.querySelector('h1')?.textContent).toContain('今晚的火已经升起来了');
    expect(root.textContent).toContain('莉娅 · 暖灯旅店的看板娘');
    expect(root.textContent).toContain('等待回想');
    expect(root.textContent).toContain('2');
    expect(root.textContent).toContain('私人词库');
    expect(root.textContent).toContain('4');
    expect(root.textContent).toContain('打开账号书架');
    expect(root.querySelector('[data-companion="lia"]')).not.toBeNull();
    expect(root.querySelector('[data-expression="relief"]')).not.toBeNull();
  });

  it('offers a real queue action and emits start', async () => {
    const start = vi.fn();
    const root = document.createElement('div');
    document.body.append(root);
    mountedApp = createApp(InnLobby, {
      companion: lia,
      companionDensity: 'standard',
      reduceMotion: false,
      user,
      plan: {
        ...plan,
        selected: [
          {
            studyItem: {
              id: 'study-1',
              wordEntryId: 'word-1',
              direction: 'meaning-to-spelling',
              createdAt: 1,
              updatedAt: 1,
            },
            wordEntry: {
              id: 'word-1',
              term: 'lantern',
              meaning: '提灯',
              acceptedAnswers: ['lantern'],
              hints: [],
              normalizedTerm: 'lantern',
              createdAt: 1,
              updatedAt: 1,
            },
            source: 'new',
          },
        ],
      },
      lexiconsCount: 1,
      sessionCount: 0,
      contentPackages: [],
      adventures: [],
      loading: false,
      error: null,
      onStart: start,
    });
    mountedApp.mount(root);
    await nextTick();

    const button = [...root.querySelectorAll('button')].find(
      (item) => item.textContent?.includes('开始营地复习'),
    );
    expect(button).toBeDefined();
    expect(root.querySelector('[data-expression="cheer"]')).not.toBeNull();
    button?.click();
    expect(start).toHaveBeenCalledOnce();
  });

  it('ships the same six-expression contract for Lia', () => {
    expect(companionExpressions).toEqual([
      'neutral',
      'welcome',
      'cheer',
      'concern',
      'relief',
      'surprise',
    ]);
    expect(Object.keys(lia.portraits)).toEqual(companionExpressions);
    expect(Object.keys(serin.portraits)).toEqual(companionExpressions);
    expect(Object.keys(fina.portraits)).toEqual(companionExpressions);
    expect(new Set(Object.values(lia.portraits)).size).toBe(6);
    expect(new Set(Object.values(serin.portraits)).size).toBe(6);
    expect(new Set(Object.values(fina.portraits)).size).toBe(6);
    expect(companions.map((companion) => companion.id))
      .toEqual(['lia', 'serin', 'fina']);
  });

  it('switches the visible companion while keeping the same queue context', async () => {
    const selectCompanion = vi.fn();
    const root = document.createElement('div');
    document.body.append(root);
    mountedApp = createApp(InnLobby, {
      companion: serin,
      companionDensity: 'standard',
      reduceMotion: false,
      user,
      plan,
      lexiconsCount: 1,
      sessionCount: 0,
      contentPackages: [],
      adventures: [],
      loading: false,
      error: null,
      onSelectCompanion: selectCompanion,
    });
    mountedApp.mount(root);
    await nextTick();

    expect(root.textContent).toContain('瑟琳 · 古树契约的旅行祭司');
    expect(root.querySelector('[data-companion="serin"]')).not.toBeNull();
    const liaChoice = [...root.querySelectorAll('button')].find(
      (button) => button.textContent?.includes('莉娅'),
    );
    liaChoice?.click();
    expect(selectCompanion).toHaveBeenCalledWith('lia');
  });

  it('shows Fina only after the account unlocks her and switches to her portrait', async () => {
    const selectCompanion = vi.fn();
    const root = document.createElement('div');
    document.body.append(root);
    mountedApp = createApp(InnLobby, {
      companion: fina,
      companionDensity: 'standard',
      reduceMotion: false,
      user,
      plan,
      lexiconsCount: 1,
      sessionCount: 0,
      contentPackages: [],
      adventures: [],
      unlockedCompanionIds: ['lia', 'serin', 'fina'],
      loading: false,
      error: null,
      onSelectCompanion: selectCompanion,
    });
    mountedApp.mount(root);
    await nextTick();

    expect(root.textContent).toContain('菲妮 · 罗盘与远路的绘图师');
    expect(root.querySelector('[data-companion="fina"]')).not.toBeNull();
    const serinChoice = [...root.querySelectorAll('button')].find(
      (button) => button.textContent?.includes('瑟琳'),
    );
    serinChoice?.click();
    expect(selectCompanion).toHaveBeenCalledWith('serin');
  });

  it('exposes density and classic-card entries without hiding the queue', async () => {
    const selectDensity = vi.fn();
    const classicStudy = vi.fn();
    const root = document.createElement('div');
    document.body.append(root);
    mountedApp = createApp(InnLobby, {
      companion: lia,
      companionDensity: 'standard',
      reduceMotion: true,
      user,
      plan,
      lexiconsCount: 1,
      sessionCount: 0,
      contentPackages: [],
      adventures: [],
      loading: false,
      error: null,
      onSelectDensity: selectDensity,
      onClassicStudy: classicStudy,
    });
    mountedApp.mount(root);
    await nextTick();

    const quiet = [...root.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === '安静',
    );
    quiet?.click();
    expect(selectDensity).toHaveBeenCalledWith('quiet');

    const classic = [...root.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === '经典卡片',
    );
    classic?.click();
    expect(classicStudy).toHaveBeenCalledOnce();
    expect(root.textContent).toContain('等待回想');
    expect(root.querySelector('.companion-portrait')?.classList.contains('reduce-motion'))
      .toBe(true);
  });

  it('starts a new adventure or resumes the fixed installed run', async () => {
    const startAdventure = vi.fn();
    const resumeAdventure = vi.fn();
    const root = document.createElement('div');
    document.body.append(root);
    mountedApp = createApp(InnLobby, {
      companion: lia,
      companionDensity: 'standard',
      reduceMotion: false,
      user,
      plan,
      lexiconsCount: 1,
      sessionCount: 0,
      contentPackages: [contentPackage],
      adventures: [adventure],
      loading: false,
      error: null,
      onStartAdventure: startAdventure,
      onResumeAdventure: resumeAdventure,
    });
    mountedApp.mount(root);
    await nextTick();

    expect(root.textContent).toContain('已固定 v1.0.0');
    const resume = [...root.querySelectorAll('button')].find(
      (button) => button.textContent?.includes('继续冒险'),
    );
    resume?.click();
    expect(resumeAdventure).toHaveBeenCalledWith('run-inn');

    mountedApp.unmount();
    document.body.innerHTML = '';
    const newRoot = document.createElement('div');
    document.body.append(newRoot);
    mountedApp = createApp(InnLobby, {
      companion: lia,
      companionDensity: 'standard',
      reduceMotion: false,
      user,
      plan,
      lexiconsCount: 1,
      sessionCount: 0,
      contentPackages: [contentPackage],
      adventures: [],
      loading: false,
      error: null,
      onStartAdventure: startAdventure,
    });
    mountedApp.mount(newRoot);
    await nextTick();

    const start = [...newRoot.querySelectorAll('button')].find(
      (button) => button.textContent?.includes('开始第一章'),
    );
    start?.click();
    expect(startAdventure).toHaveBeenCalledWith('installation-inn');
  });
});
