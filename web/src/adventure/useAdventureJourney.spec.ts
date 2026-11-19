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
    seriesId: 'warm-lantern',
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
    ],
    unlockedCharacters: ['lia', 'serin'],
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
  | 'upgradeAdventure'
  | 'startAdventureEncounter'
  | 'actAdventure'
> {
  return {
    getSession: vi.fn(async () => session()),
    startAdventure: vi.fn(async () => ({ outcome: 'created' as const, run: run() })),
    getAdventure: vi.fn(async () => run()),
    upgradeAdventure: vi.fn(async () => ({
      outcome: 'upgraded' as const,
      run: run({ runVersion: 1 }),
    })),
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
        companionKey: undefined,
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
        companionKey: undefined,
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
        companionKey: undefined,
      },
    );
    expect(journey.run.value?.currentNode.nodeId).toBe('bridge.crossed');
    expect(journey.run.value?.battleId).toBeUndefined();
  });

  it('upgrades a completed checkpoint into an explicitly installed chapter version', async () => {
    const client = api();
    vi.mocked(client.upgradeAdventure).mockResolvedValueOnce({
      outcome: 'upgraded',
      run: run({
        installationId: 'installation-next',
        contentVersion: '1.1.0',
        status: 'active',
        runVersion: 4,
      }),
    });
    const journey = useAdventureJourney({
      api: () => client,
      describeError: () => '请求失败',
      createActionId: () => 'upgrade-action',
    });

    await expect(journey.upgrade('run-old', 'installation-next'))
      .resolves.toBe(true);

    expect(client.upgradeAdventure).toHaveBeenCalledWith(
      'run-old',
      'upgrade-action',
      'installation-next',
    );
    expect(journey.run.value?.contentVersion).toBe('1.1.0');
    expect(journey.notice.value).toContain('安全开启');
  });

  it('claims a fixed reward and changes companion only at a camp', async () => {
    const client = api();
    const journey = useAdventureJourney({
      api: () => client,
      describeError: () => '请求失败',
      createActionId: () => 'action-fixed',
    });
    journey.run.value = run({
      runVersion: 4,
      currentNode: {
        nodeId: 'camp.reward',
        chapterId: 'chapter-1',
        type: 'camp',
        title: '营地',
        speaker: '旅伴',
        text: '选择下一步。',
        choices: [],
      },
      rewardOffer: {
        offerId: 'offer-1',
        nodeId: 'camp.reward',
        choices: [{
          choiceId: 'party-strike',
          cardDefinitionId: 'party-strike',
          name: '旅团合击',
          description: '造成 10 点伤害。',
        }],
      },
    });

    vi.mocked(client.actAdventure)
      .mockResolvedValueOnce({
        outcome: 'applied',
        run: run({
          runVersion: 5,
          deck: [...run().deck, 'party-strike'],
          currentNode: {
            nodeId: 'camp.reward',
            chapterId: 'chapter-1',
            type: 'camp',
            title: '营地',
            speaker: '旅伴',
            text: '选择下一步。',
            choices: [],
          },
        }),
      })
      .mockResolvedValueOnce({
        outcome: 'applied',
        run: run({
          runVersion: 6,
          companionKey: 'fina',
          unlockedCharacters: ['lia', 'serin', 'fina'],
          currentNode: {
            nodeId: 'camp.reward',
            chapterId: 'chapter-1',
            type: 'camp',
            title: '营地',
            speaker: '旅伴',
            text: '选择下一步。',
            choices: [],
          },
        }),
      });

    await journey.claimReward('party-strike');
    expect(journey.run.value?.deck).toContain('party-strike');
    expect(journey.run.value?.rewardOffer).toBeUndefined();

    await journey.selectCompanion('fina');
    expect(journey.run.value?.companionKey).toBe('fina');
    expect(client.actAdventure).toHaveBeenLastCalledWith(
      'run-adventure',
      'action-fixed',
      {
        expectedVersion: 5,
        action: 'select-companion',
        choiceId: undefined,
        battleId: undefined,
        companionKey: 'fina',
      },
    );
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
