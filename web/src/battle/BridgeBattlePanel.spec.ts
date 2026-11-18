import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type {
  AccountClient,
  BattleEnvelopeView,
  BattleStateView,
  PendingAnswerView,
  SessionEnvelope,
} from '../api/client';
import BridgeBattlePanel from './BridgeBattlePanel.vue';

type BridgeBattleApi = Pick<
  AccountClient,
  | 'createBattle'
  | 'getBattle'
  | 'getSession'
  | 'submitPracticeResponse'
  | 'previewReview'
  | 'submitReview'
  | 'claimBattleLearning'
  | 'playBattleCard'
  | 'useBattleCompanionSkill'
  | 'endBattleTurn'
>;

function sessionEnvelope(): SessionEnvelope {
  const item = {
    wordEntry: {
      id: 'word-bridge',
      term: 'lantern',
      meaning: '提灯',
      acceptedAnswers: ['lantern'],
      hints: ['照明工具'],
      normalizedTerm: 'lantern',
      createdAt: 1,
      updatedAt: 1,
    },
    studyItem: {
      id: 'study-bridge',
      wordEntryId: 'word-bridge',
      direction: 'meaning-to-spelling' as const,
      createdAt: 1,
      updatedAt: 1,
    },
    lexiconItem: null,
    reviewVersion: 0,
  };
  return {
    session: {
      id: 'session-bridge',
      modeId: 'keyboard-recall',
      status: 'active',
      queue: [{
        studyItemId: 'study-bridge',
        wordEntryId: 'word-bridge',
        status: 'pending',
        source: 'new',
      }],
      currentIndex: 0,
      currentStudyItemId: 'study-bridge',
      interactions: {},
      pendingAnswer: null,
      speed: 1,
      reduceMotion: false,
      createdAt: 1,
      updatedAt: 1,
      pausedAt: null,
      completedAt: null,
      version: 0,
    },
    items: [item],
    dataVersion: 1,
  };
}

function battleState(
  overrides: Partial<BattleStateView> = {},
): BattleEnvelopeView {
  const state: BattleStateView = {
    schemaVersion: 1,
    battleRulesVersion: 'card-battle-v1',
    battleId: 'battle-bridge',
    encounterId: 'bridge-guardian',
    turnId: 1,
    phase: 'learning',
    stateVersion: 0,
    rngState: 42,
    party: { hp: 30, maxHp: 30, block: 0 },
    enemies: [{
      id: 'bridge-guardian',
      hp: 36,
      maxHp: 36,
      block: 0,
      burn: null,
    }],
    energy: 0,
    drawPile: [],
    drawCursor: 0,
    hand: [],
    discardPile: [],
    exhaustPile: [],
    currentIntent: { enemyId: 'bridge-guardian', type: 'attack', value: 6 },
    intentCycle: [
      { type: 'attack', value: 6 },
      { type: 'defend', value: 6 },
      { type: 'attack', value: 8 },
    ],
    intentIndex: 0,
    terminal: null,
    learningTask: {
      taskId: 'task-bridge',
      sessionId: 'session-bridge',
      studyItemId: 'study-bridge',
      energyGranted: false,
      submissionId: null,
    },
    companion: { key: 'lia', skillUsed: false },
    ...overrides,
  };
  return { battle: state, events: [], stateVersion: state.stateVersion };
}

function pendingAnswer(): PendingAnswerView {
  return {
    studyItemId: 'study-bridge',
    response: 'lantern',
    answerWasHidden: true,
    hintUsed: false,
    answerRevealed: false,
    inputMatchedAnswer: true,
    assessmentType: 'typed-recall',
    assessmentPolicyVersion: 2,
    assessmentStage: 'attempt',
    priorExposure: false,
    explicitNotRemembered: false,
    practiceAttemptId: 'attempt-bridge',
    reviewSubmissionId: 'review-bridge',
    submittedAt: 2,
  };
}

function buttonByText(root: ParentNode, text: string): HTMLButtonElement {
  return [...root.querySelectorAll<HTMLButtonElement>('button')].find(
    (button) => button.textContent?.trim().includes(text),
  )!;
}

function pressKey(key: string, overrides: KeyboardEventInit = {}): void {
  const target = document.querySelector<HTMLElement>(
    '[data-testid="bridge-battle"]',
  ) ?? document.body;
  target.dispatchEvent(new KeyboardEvent('keydown', {
    key,
    bubbles: true,
    cancelable: true,
    ...overrides,
  }));
}

let mountedApp: ReturnType<typeof createApp> | null = null;

afterEach(() => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
});

describe('BridgeBattlePanel', () => {
  it('runs learning, claim, target selection, companion skill and turn end', async () => {
    const session = sessionEnvelope();
    const initial = battleState();
    const active = battleState({
      phase: 'player-action',
      stateVersion: 1,
      energy: 3,
      hand: [{ instanceId: 'card-01', definitionId: 'traveler-strike' }],
      learningTask: {
        taskId: 'task-bridge',
        sessionId: 'session-bridge',
        studyItemId: 'study-bridge',
        energyGranted: true,
        submissionId: 'review-bridge',
      },
    });
    const skilled = battleState({
      ...active.battle,
      stateVersion: 2,
      party: { hp: 30, maxHp: 30, block: 5 },
      companion: { key: 'lia', skillUsed: true },
    });
    const nextSession = sessionEnvelope();
    nextSession.session = {
      ...nextSession.session,
      currentIndex: 1,
      currentStudyItemId: null,
      queue: [{
        studyItemId: 'study-bridge',
        wordEntryId: 'word-bridge',
        status: 'completed',
        source: 'new',
      }],
      version: 2,
    };
    nextSession.items = [];
    nextSession.dataVersion = 3;
    const nextLearning = battleState({
      stateVersion: 3,
      hand: [],
      learningTask: {
        taskId: 'task-next',
        sessionId: 'session-bridge',
        studyItemId: 'study-next',
        energyGranted: false,
        submissionId: null,
      },
    });

    const api: BridgeBattleApi = {
      createBattle: vi.fn(async () => initial),
      getBattle: vi.fn(),
      getSession: vi.fn(async () => session),
      submitPracticeResponse: vi.fn(async () => ({
        attempt: {},
        pendingAnswer: pendingAnswer(),
        session: { ...session.session, pendingAnswer: pendingAnswer(), version: 1 },
        dataVersion: 2,
      })),
      previewReview: vi.fn(async () => ({
        previews: {
          again: { rating: 'again', dueAt: 3, scheduledDays: 0 },
          hard: { rating: 'hard', dueAt: 4, scheduledDays: 1 },
          good: { rating: 'good', dueAt: 5, scheduledDays: 2 },
          easy: { rating: 'easy', dueAt: 6, scheduledDays: 3 },
        },
        schedulerPackageVersion: '6.0.0',
        schedulerModelVersion: 'fsrs-7',
        schedulerConfigHash: 'hash',
        reviewedAtMs: 2,
        reviewVersion: 0,
        sessionVersion: 1,
        dataVersion: 2,
      })),
      submitReview: vi.fn(async () => ({
        outcome: 'created' as const,
        state: { dueAt: 5, version: 1 },
        log: { submissionId: 'review-bridge', reviewedAt: 2 },
        session: nextSession.session,
        dataVersion: 3,
        rewardEligible: true,
      })),
      claimBattleLearning: vi.fn(async () => active),
      playBattleCard: vi.fn(async () => active),
      useBattleCompanionSkill: vi.fn(async () => skilled),
      endBattleTurn: vi.fn(async () => nextLearning),
    };
    const root = document.createElement('div');
    document.body.append(root);
    mountedApp = createApp(BridgeBattlePanel, {
      api,
      session,
      enableAudio: false,
    });
    mountedApp.mount(root);

    await vi.waitFor(() => {
      expect(root.textContent).toContain('提灯');
      expect(root.querySelector('[data-field="party-hp"]')?.textContent)
        .toContain('30/30');
      expect(document.activeElement).toBe(
        root.querySelector('.battle-answer-row input'),
      );
    });

    const answer = root.querySelector<HTMLInputElement>('input');
    answer!.value = 'lantern';
    answer!.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    pressKey('Enter');
    await nextTick();
    expect(JSON.parse(
      root.querySelector<HTMLElement>('[data-testid="bridge-battle"]')
        ?.dataset.lastShortcutInput ?? '{}',
    )).toMatchObject({
      key: 'Enter',
      context: 'learning-input',
      action: 'submit-answer',
    });
    await vi.waitFor(() => {
      expect(buttonByText(root, 'GOOD')).toBeDefined();
      expect(root.querySelector('[data-shortcut-context="rating"]')).not.toBeNull();
    });
    pressKey('3');
    await vi.waitFor(() => {
      expect(root.querySelector('[data-field="energy"]')?.textContent).toContain('3/3');
      expect(root.querySelector('[data-shortcut-context="card-select"]')).not.toBeNull();
    });

    pressKey('?', { shiftKey: true });
    await vi.waitFor(() => {
      expect(root.textContent).toContain('结束回合组合键');
    });
    pressKey('Escape');
    await vi.waitFor(() => {
      expect(root.textContent).not.toContain('结束回合组合键');
    });

    pressKey('1');
    await vi.waitFor(() => {
      expect(buttonByText(root, 'bridge-guardian')).toBeDefined();
      expect(root.querySelector('[data-shortcut-context="target-select"]')).not.toBeNull();
      expect(document.activeElement?.textContent).toContain('bridge-guardian');
    });
    pressKey('Escape');
    await vi.waitFor(() => {
      expect(root.querySelector('[data-shortcut-context="card-select"]')).not.toBeNull();
    });
    expect(api.playBattleCard).not.toHaveBeenCalled();

    pressKey('1');
    await vi.waitFor(() => {
      expect(root.querySelector('[data-shortcut-context="target-select"]')).not.toBeNull();
    });
    pressKey('ArrowRight');
    pressKey('Enter');
    await vi.waitFor(() => {
      expect(api.playBattleCard).toHaveBeenCalledWith(
        expect.objectContaining({
          expectedStateVersion: 1,
          cardInstanceId: 'card-01',
          targetId: 'bridge-guardian',
        }),
      );
      expect(buttonByText(root, '协助').disabled).toBe(false);
      expect(root.querySelector('[data-shortcut-context="card-select"]')).not.toBeNull();
    });

    pressKey('Escape');
    await vi.waitFor(() => {
      expect(root.querySelector('[data-battle-paused="true"]')).not.toBeNull();
      expect(document.activeElement?.textContent).toContain('继续战斗');
    });
    pressKey('Enter');
    await vi.waitFor(() => {
      expect(root.querySelector('[data-battle-paused="false"]')).not.toBeNull();
    });

    await nextTick();
    buttonByText(root, '协助').click();
    await vi.waitFor(() => {
      expect(api.useBattleCompanionSkill).toHaveBeenCalled();
    });
    await nextTick();
    pressKey('Enter', { ctrlKey: true });
    await vi.waitFor(() => {
      expect(api.endBattleTurn).toHaveBeenCalled();
    });
  });

  it('keeps long learning text and native controls on the reduced-motion DOM stage', async () => {
    const longMeaning = [
      '在雾中的溪桥旁，旅团需要先辨认提灯照出的路标，再确认守卫下一次行动。',
      '这段释义刻意很长，用于验证窄屏不会裁掉问题、输入框、按钮或焦点轮廓。',
      '即使二维游戏表现被关闭，正式学习、能量获取和卡牌命令仍必须保持可用。',
    ].join('').repeat(4);
    const session = sessionEnvelope();
    session.items[0]!.wordEntry.meaning = longMeaning;
    const initial = battleState();
    const api: BridgeBattleApi = {
      createBattle: vi.fn(async () => initial),
      getBattle: vi.fn(),
      getSession: vi.fn(async () => session),
      submitPracticeResponse: vi.fn(),
      previewReview: vi.fn(),
      submitReview: vi.fn(),
      claimBattleLearning: vi.fn(),
      playBattleCard: vi.fn(),
      useBattleCompanionSkill: vi.fn(),
      endBattleTurn: vi.fn(),
    };
    const root = document.createElement('div');
    document.body.append(root);
    mountedApp = createApp(BridgeBattlePanel, {
      api,
      session,
      enableAudio: false,
      reduceMotion: true,
    });
    mountedApp.mount(root);

    await vi.waitFor(() => {
      const battle = root.querySelector<HTMLElement>('[data-testid="bridge-battle"]');
      expect(battle?.dataset.battleRenderer).toBe('dom');
      expect(battle?.dataset.presentationMode).toBe('reduced');
      expect(battle?.classList.contains('presentation-reduced')).toBe(true);
      expect(root.textContent).toContain(longMeaning);
      expect(root.querySelector('.battle-answer-row input')).not.toBeNull();
      expect(
        root.querySelector('.companion-portrait')?.classList.contains('reduce-motion'),
      ).toBe(true);
    });
  });

  it('restores the adventure battle instead of creating a second battle', async () => {
    const session = sessionEnvelope();
    const restored = battleState({
      phase: 'victory',
      terminal: 'victory',
      stateVersion: 8,
    });
    const finished = vi.fn();
    const getBattle = vi.fn(async () => restored);
    const createBattle = vi.fn(async () => restored);
    const api: BridgeBattleApi = {
      createBattle,
      getBattle,
      getSession: vi.fn(async () => session),
      submitPracticeResponse: vi.fn(),
      previewReview: vi.fn(),
      submitReview: vi.fn(),
      claimBattleLearning: vi.fn(),
      playBattleCard: vi.fn(),
      useBattleCompanionSkill: vi.fn(),
      endBattleTurn: vi.fn(),
    };
    const root = document.createElement('div');
    document.body.append(root);
    mountedApp = createApp(BridgeBattlePanel, {
      api,
      session,
      initialBattleId: 'battle-adventure',
      finishLabel: '继续剧情',
      enableAudio: false,
      onFinished: finished,
    });
    mountedApp.mount(root);

    await vi.waitFor(() => {
      expect(getBattle).toHaveBeenCalledWith('battle-adventure');
      expect(root.textContent).toContain('溪桥的路标重新亮了起来');
    });
    expect(createBattle).not.toHaveBeenCalled();
    buttonByText(root, '继续剧情').click();
    expect(finished).toHaveBeenCalledOnce();
  });
});
