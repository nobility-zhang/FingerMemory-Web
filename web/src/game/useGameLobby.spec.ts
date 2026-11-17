import { describe, expect, it, vi } from 'vitest';
import type {
  AccountClient,
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
  } satisfies Pick<
    AccountClient,
    'loadQueuePlan' | 'listLexicons' | 'listActiveSessions'
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
