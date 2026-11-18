import { describe, expect, it, vi } from 'vitest';
import type {
  AccountClient,
  AdventureRunView,
  SessionEnvelope,
} from '../api/client';
import { useAdventureJourney } from './useAdventureJourney';

function session(): SessionEnvelope {
  return {
    session: {
      id: 'session-adventure',
      modeId: 'keyboard-recall',
      status: 'active',
      queue: [],
      currentIndex: 0,
      currentStudyItemId: null,
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
    items: [],
    dataVersion: 1,
  };
}

function run(overrides: Partial<AdventureRunView> = {}): AdventureRunView {
  return {
    id: 'run-adventure',
    installationId: 'installation-adventure',
    namespace: 'fm.demo',
    bookId: 'warm-lantern',
    contentVersion: '1.0.0',
    contentHash: 'a'.repeat(64),
    title: '暖灯与失语之森',
    companionKey: 'lia',
    status: 'active',
    runVersion: 0,
    sessionId: 'session-adventure',
    currentNode: {
      nodeId: 'inn.arrival',
      chapterId: 'chapter-1',
      type: 'choice',
      title: '暖灯旅店',
      speaker: '',
      text: '莉娅把暖灯递给你。',
      choices: [{ choiceId: 'prepare', label: '准备出发' }],
    },
    flags: {},
    createdAtMs: 1,
    updatedAtMs: 1,
    ...overrides,
  };
}

function api(): Pick<
  AccountClient,
  | 'getSession'
  | 'startAdventure'
  | 'getAdventure'
  | 'startAdventureEncounter'
  | 'actAdventure'
> {
  return {
    getSession: vi.fn(async () => session()),
    startAdventure: vi.fn(async () => ({ outcome: 'created' as const, run: run() })),
    getAdventure: vi.fn(async () => run()),
    startAdventureEncounter: vi.fn(async () => ({
      outcome: 'started' as const,
      battleId: 'battle-adventure',
      runId: 'run-adventure',
      runVersion: 1,
    })),
    actAdventure: vi.fn(async (_runId, _key, input) => ({
      outcome: 'applied' as const,
      run: run({
        runVersion: input.expectedVersion + 1,
        currentNode: {
          nodeId: 'inn.preparation',
          chapterId: 'chapter-1',
          type: 'dialogue',
          title: '临行前的笔记',
          speaker: '莉娅',
          text: '带上提灯。',
          choices: [],
        },
      }),
    })),
  };
}

describe('useAdventureJourney', () => {
  it('starts from an installed package and keeps failed action keys for retry', async () => {
    const client = api();
    const replaceRun = run();
    vi.mocked(client.startAdventure).mockResolvedValueOnce({
      outcome: 'created',
      run: replaceRun,
    });
    const created = await useCreatedJourney(client);

    const started = await created.journey.start({
      installationId: 'installation-adventure',
      companionKey: 'lia',
      reduceMotion: true,
    });

    expect(started).toBe(true);
    expect(client.startAdventure).toHaveBeenCalledWith({
      installationId: 'installation-adventure',
      companionKey: 'lia',
      reduceMotion: true,
      actionId: 'action-1',
    });
    expect(created.journey.run.value?.id).toBe('run-adventure');
    expect(created.journey.session.value?.session.id).toBe('session-adventure');

    vi.mocked(client.actAdventure)
      .mockRejectedValueOnce(new Error('网络暂时中断'))
      .mockImplementationOnce(async (_runId, _key, input) => ({
        outcome: 'applied',
        run: run({
          runVersion: input.expectedVersion + 1,
          currentNode: {
            nodeId: 'inn.preparation',
            chapterId: 'chapter-1',
            type: 'dialogue',
            title: '临行前的笔记',
            speaker: '莉娅',
            text: '带上提灯。',
            choices: [],
          },
        }),
      }));
    await created.journey.choose('prepare');
    await created.journey.choose('prepare');

    expect(client.actAdventure).toHaveBeenNthCalledWith(
      1,
      'run-adventure',
      'action-2',
      {
        expectedVersion: 0,
        action: 'choose',
        choiceId: 'prepare',
        battleId: undefined,
      },
    );
    expect(client.actAdventure).toHaveBeenNthCalledWith(
      2,
      'run-adventure',
      'action-2',
      {
        expectedVersion: 0,
        action: 'choose',
        choiceId: 'prepare',
        battleId: undefined,
      },
    );
    expect(created.journey.run.value?.currentNode.nodeId).toBe('inn.preparation');
  });

  it('binds and resolves the encounter without inventing a second battle', async () => {
    const client = api();
    const journey = useAdventureJourney({
      api: () => client,
      describeError: () => '请求失败',
      createActionId: () => 'action-fixed',
    });
    vi.mocked(client.startAdventure).mockResolvedValueOnce({
      outcome: 'created',
      run: run({
        runVersion: 3,
        currentNode: {
          nodeId: 'bridge.guardian',
          chapterId: 'chapter-1',
          type: 'encounter',
          title: '雾中溪桥',
          speaker: '',
          text: '守卫挡住了路。',
          encounterId: 'bridge-guardian',
          choices: [],
        },
      }),
    });
    await journey.start({
      installationId: 'installation-adventure',
      companionKey: 'lia',
      reduceMotion: true,
    });

    vi.mocked(client.startAdventureEncounter).mockResolvedValueOnce({
      outcome: 'started',
      battleId: 'battle-adventure',
      runId: 'run-adventure',
      runVersion: 4,
    });
    await journey.startEncounter();
    expect(journey.run.value?.battleId).toBe('battle-adventure');
    expect(journey.run.value?.runVersion).toBe(4);

    vi.mocked(client.actAdventure).mockResolvedValueOnce({
      outcome: 'applied',
      run: run({
        runVersion: 5,
        currentNode: {
          nodeId: 'bridge.crossed',
          chapterId: 'chapter-1',
          type: 'dialogue',
          title: '桥后的灯影',
          speaker: '旅伴',
          text: '迷雾散开了。',
          choices: [],
        },
      }),
    });
    await journey.resolveEncounter();

    expect(client.actAdventure).toHaveBeenCalledWith(
      'run-adventure',
      'action-fixed',
      {
        expectedVersion: 4,
        action: 'resolve-encounter',
        choiceId: undefined,
        battleId: 'battle-adventure',
      },
    );
    expect(journey.run.value?.currentNode.nodeId).toBe('bridge.crossed');
    expect(journey.run.value?.battleId).toBeUndefined();
  });
});

async function useCreatedJourney(
  client: ReturnType<typeof api>,
): Promise<{ journey: ReturnType<typeof useAdventureJourney> }> {
  let sequence = 0;
  const journey = useAdventureJourney({
    api: () => client,
    describeError: () => '请求失败',
    createActionId: () => `action-${++sequence}`,
  });
  return { journey };
}
