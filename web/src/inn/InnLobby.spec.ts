import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { QueuePlanView, UserView } from '../api/client';
import {
  companionExpressions,
  companions,
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
    expect(root.textContent).toContain('第一章内容包接入后开启');
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
    expect(new Set(Object.values(lia.portraits)).size).toBe(6);
    expect(companions.map((companion) => companion.id)).toEqual(['lia', 'serin']);
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
});
