import { describe, expect, it, vi } from 'vitest';
import type {
  AccountClient,
  AdventureRunView,
  QueuePlanView,
  SessionEnvelope,
} from '../api/client';
import { gameErrorMessage } from './gameErrorMessage';
import { useGameLobby } from './useGameLobby';

const session: SessionEnvelope = {
  session: {
    id: 'session-lobby',
    modeId: 'keyboard-recall',
    status: 'paused',
    queue: [],
    currentIndex: 0,
    currentStudyItemId: null,
    interactions: {},
    pendingAnswer: null,
    speed: 1,
    reduceMotion: false,
    createdAt: 1,
    updatedAt: 2,
    pausedAt: 2,
    completedAt: null,
    version: 1,
  },
  items: [],
  dataVersion: 2,
};

function createApi() {
  const loadQueuePlan = vi.fn(async (): Promise<QueuePlanView> => ({
    at: 1,
    timeZone: 'UTC',
    dayStartAt: 0,
    dayEndAt: 86_400_000,
    settings: { newLimit: 10, reviewLimit: 50, paused: false },
    dueAvailableCount: 0,
    newAvailableCount: 1,
    selected: [],
    dataVersion: 1,
  }));
  return {
    loadQueuePlan,
    listLexicons: vi.fn(async () => [{
      id: 'lexicon-lobby',
      name: '大厅词库',
      description: '',
      source: 'user',
      archivedAtMs: null,
      createdAtMs: 1,
      updatedAtMs: 1,
      version: 0,
    }]),
    listActiveSessions: vi.fn(async () => ({
      sessions: [session],
      dataVersion: 2,
    })),
    listContentPackages: vi.fn(async () => [{
      id: 'installation-lobby',
      namespace: 'fm.demo',
      bookId: 'warm-lantern',
      contentVersion: '1.0.0',
      contentHash: 'a'.repeat(64),
      title: '暖灯与失语之森',
      entryNodeId: 'inn.arrival',
      seriesId: 'warm-lantern',
      requirements: [],
      status: 'installed' as const,
      wordCount: 1,
      chapterCount: 1,
      nodeCount: 9,
      characterCount: 0,
      cardCount: 0,
      encounterCount: 1,
      assetCount: 0,
      installedAtMs: 1,
    }]),
    listAdventures: vi.fn(async () => [{
      id: 'run-lobby',
      installationId: 'installation-lobby',
      namespace: 'fm.demo',
      bookId: 'warm-lantern',
      contentVersion: '1.0.0',
      contentHash: 'a'.repeat(64),
      title: '暖灯与失语之森',
      seriesId: 'warm-lantern',
      companionKey: 'lia' as const,
      status: 'active' as const,
      runVersion: 2,
      sessionId: 'session-adventure',
      currentNode: {
        nodeId: 'inn.arrival',
        chapterId: 'chapter-1',
        type: 'choice' as const,
        title: '暖灯旅店',
        speaker: '',
        text: '莉娅把暖灯递给你。',
        choices: [{ choiceId: 'prepare', label: '准备出发' }],
      },
      deck: [
        'companion-guard',
        'traveler-strike',
        'ember-rune',
        'observe-signpost',
        'traveler-strike',
        'companion-guard',
        'herb-pouch',
        'traveler-strike',
        'ember-rune',
        'party-strike',
        'companion-guard',
        'party-strike',
      ] as AdventureRunView['deck'],
      unlockedCharacters: ['lia', 'serin'],
      characters: [],
      scenes: [],
      flags: {},
      createdAtMs: 1,
      updatedAtMs: 2,
    }]),
    listUnlockedCompanions: vi.fn(async () =>
      ['lia', 'serin']),
    listCompanionDefinitions: vi.fn(async () => []),
    listAdventureCompletions: vi.fn(async () => []),
    listAdventureRecollections: vi.fn(async () => []),
  } satisfies Pick<
    AccountClient,
    | 'loadQueuePlan'
    | 'listLexicons'
    | 'listActiveSessions'
    | 'listContentPackages'
    | 'listAdventures'
    | 'listUnlockedCompanions'
    | 'listCompanionDefinitions'
    | 'listAdventureCompletions'
    | 'listAdventureRecollections'
  >;
}

describe('useGameLobby', () => {
  it('loads the plan, lexicons, sessions and active session together', async () => {
    const api = createApi();
    const lobby = useGameLobby({
      api: () => api,
      describeError: gameErrorMessage,
      timeZone: () => 'UTC',
    });

    await lobby.load();

    expect(api.loadQueuePlan).toHaveBeenCalledWith({ timeZone: 'UTC' });
    expect(lobby.plan.value?.newAvailableCount).toBe(1);
    expect(lobby.lexicons.value).toHaveLength(1);
    expect(lobby.activeSession.value?.session.id).toBe('session-lobby');
    expect(lobby.contentPackages.value).toHaveLength(1);
    expect(lobby.adventures.value[0]?.id).toBe('run-lobby');
    expect(lobby.unlockedCompanions.value).toEqual(['lia', 'serin']);
    expect(lobby.recollections.value).toEqual([]);
    expect(lobby.loading.value).toBe(false);
    expect(lobby.error.value).toBeNull();
  });

  it('exposes a load error without partially overwriting the previous lobby', async () => {
    const api = createApi();
    const lobby = useGameLobby({
      api: () => api,
      describeError: gameErrorMessage,
      timeZone: () => 'UTC',
    });
    await lobby.load();
    api.listLexicons.mockRejectedValueOnce(new Error('词库服务不可用'));

    await lobby.load();

    expect(lobby.error.value).toBe('词库服务不可用');
    expect(lobby.lexicons.value[0]?.id).toBe('lexicon-lobby');
    expect(lobby.loading.value).toBe(false);
  });

  it('ignores a lobby response that arrives after disposal', async () => {
    let resolvePlan:
      | ((value: Awaited<ReturnType<AccountClient['loadQueuePlan']>>) => void)
      | undefined;
    const pendingPlan = new Promise<
      Awaited<ReturnType<AccountClient['loadQueuePlan']>>
    >((resolve) => {
      resolvePlan = resolve;
    });
    const api = createApi();
    api.loadQueuePlan.mockReturnValueOnce(pendingPlan);
    const lobby = useGameLobby({
      api: () => api,
      describeError: gameErrorMessage,
      timeZone: () => 'UTC',
    });

    const loading = lobby.load();
    lobby.dispose();
    resolvePlan?.({
      at: 1,
      timeZone: 'UTC',
      dayStartAt: 0,
      dayEndAt: 86_400_000,
      settings: { newLimit: 10, reviewLimit: 50, paused: false },
      dueAvailableCount: 0,
      newAvailableCount: 1,
      selected: [],
      dataVersion: 1,
    });

    await loading;
    expect(lobby.plan.value).toBeUndefined();
  });

  it('refreshes the active session list for explicit session reload', async () => {
    const api = createApi();
    const lobby = useGameLobby({
      api: () => api,
      describeError: gameErrorMessage,
      timeZone: () => 'UTC',
    });

    const sessions = await lobby.fetchActiveSessions();

    expect(sessions).toEqual([session]);
    expect(lobby.activeSession.value).toEqual(session);
  });
});
