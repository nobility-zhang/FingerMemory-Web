import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AdventureRunView } from '../api/client';
import { lia, portraitsFor } from '../companions/catalog';
import AdventureJourney from './AdventureJourney.vue';

function run(overrides: Partial<AdventureRunView> = {}): AdventureRunView {
  return {
    id: 'run-journey',
    installationId: 'installation-journey',
    namespace: 'fm.demo',
    bookId: 'warm-lantern',
    contentVersion: '1.0.0',
    contentHash: 'abcdef12'.repeat(8),
    title: '暖灯与失语之森',
    companionKey: 'lia',
    status: 'active',
    runVersion: 2,
    sessionId: 'session-journey',
    currentNode: {
      nodeId: 'inn.arrival',
      chapterId: 'chapter-1',
      type: 'choice',
      title: '暖灯旅店',
      speaker: '',
      text: '信纸边缘又淡了一角。',
      choices: [{ choiceId: 'prepare', label: '拿起暖灯，准备出发' }],
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
    updatedAtMs: 2,
    ...overrides,
  };
}

let mountedApp: ReturnType<typeof createApp> | null = null;

afterEach(() => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
});

function mountJourney(
  adventure: AdventureRunView,
  handlers: Record<string, (...args: unknown[]) => void> = {},
): HTMLElement {
  const root = document.createElement('div');
  document.body.append(root);
  mountedApp = createApp(AdventureJourney, {
    run: adventure,
    companion: lia,
    loading: false,
    busy: false,
    error: null,
    notice: null,
    ...handlers,
  });
  mountedApp.mount(root);
  return root;
}

function buttonByText(root: ParentNode, text: string): HTMLButtonElement {
  return [...root.querySelectorAll<HTMLButtonElement>('button')].find(
    (button) => button.textContent?.includes(text),
  )!;
}

describe('AdventureJourney', () => {
  it('renders the fixed package version and emits a real choice', async () => {
    const choose = vi.fn();
    const root = mountJourney(run(), { onChoose: choose });
    await nextTick();

    const scene = root.querySelector<HTMLElement>('.adventure-journey');
    expect(scene?.dataset.contentVersion).toBe('1.0.0');
    expect(scene?.dataset.contentHash).toHaveLength(64);
    expect(root.textContent).toContain('abcdef12');
    buttonByText(root, '拿起暖灯').click();
    expect(choose).toHaveBeenCalledWith('prepare');
  });

  it('moves through camp and completes from the chapter-end checkpoint', async () => {
    const advance = vi.fn();
    const complete = vi.fn();
    const root = mountJourney(run({
      currentNode: {
        nodeId: 'camp.fire',
        chapterId: 'chapter-1',
        type: 'camp',
        title: '营火',
        speaker: '旅伴',
        text: '营火把路标照得清清楚楚。',
        choices: [],
      },
    }), {
      onAdvance: advance,
      onComplete: complete,
    });
    await nextTick();

    expect(root.textContent).toContain('莉娅');
    buttonByText(root, '在营火旁收好笔记').click();
    expect(advance).toHaveBeenCalledOnce();

    mountedApp?.unmount();
    document.body.innerHTML = '';
    const ending = mountJourney(run({
      runVersion: 8,
      currentNode: {
        nodeId: 'chapter1.end',
        chapterId: 'chapter-1',
        type: 'chapter-end',
        title: '第一章完成',
        speaker: '',
        text: '第一段旅途已经写入存档。',
        choices: [],
      },
    }), { onComplete: complete });
    await nextTick();
    buttonByText(ending, '完成当前章节').click();
    expect(complete).toHaveBeenCalledOnce();
  });

  it('uses dedicated chapter scene art outside the inn', async () => {
    const forest = mountJourney(run({
      currentNode: {
        nodeId: 'forest.signpost',
        chapterId: 'chapter-1',
        type: 'choice',
        title: '失语之森',
        speaker: '',
        text: '两道路痕都通向溪桥。',
        choices: [{ choiceId: 'cross', label: '沿旧路前进' }],
      },
    }));
    await nextTick();
    expect(
      forest.querySelector<HTMLElement>('.adventure-journey')?.style
        .getPropertyValue('--scene-background'),
    ).toContain('forest-path');

    mountedApp?.unmount();
    document.body.innerHTML = '';
    const camp = mountJourney(run({
      currentNode: {
        nodeId: 'camp.fire',
        chapterId: 'chapter-1',
        type: 'camp',
        title: '营火',
        speaker: '旅伴',
        text: '营火把路标照亮。',
        choices: [],
      },
    }));
    await nextTick();
    expect(
      camp.querySelector<HTMLElement>('.adventure-journey')?.style
        .getPropertyValue('--scene-background'),
    ).toContain('camp-fire');
  });

  it('renders the coast scene, swimwear and authored tide encounter copy', async () => {
    const root = mountJourney(run({
      currentNode: {
        nodeId: 'coast.tide-guardian',
        chapterId: 'coast-1',
        type: 'encounter',
        title: '潮汛石阶',
        speaker: '莉娅',
        text: '潮水拍过旧石阶。',
        encounterId: 'tide-guardian',
        sceneId: 'coast-tidepools',
        costumeId: 'beach-swimwear',
        choices: [],
      },
    }));
    await nextTick();

    const scene = root.querySelector<HTMLElement>('.adventure-journey');
    expect(scene?.dataset.sceneId).toBe('coast-tidepools');
    expect(scene?.dataset.costumeId).toBe('beach-swimwear');
    expect(scene?.style.getPropertyValue('--scene-background'))
      .toContain('coast-tidepools');
    expect(root.querySelector<HTMLImageElement>('.companion-portrait img')
      ?.getAttribute('src')).toBe(portraitsFor(lia, 'beach-swimwear').concern);
    expect(root.textContent).toContain('潮汐浅滩 · 遭遇');
    expect(root.textContent).toContain('开始潮汐浅滩战斗');
    expect(root.textContent).toContain('潮汐守卫');
  });

  it('renders authored Fina portraits, camp switching and a three-choice reward', async () => {
    const selectCompanion = vi.fn();
    const claimReward = vi.fn();
    const root = mountJourney(run({
      companionKey: 'serin',
      unlockedCharacters: ['lia', 'serin', 'fina'],
      currentNode: {
        nodeId: 'chapter2.arrival',
        chapterId: 'chapter-2',
        type: 'dialogue',
        title: '塔下的新同伴',
        speaker: '菲妮',
        portraitCharacterId: 'fina',
        text: '菲妮摊开地图。',
        choices: [],
      },
    }), {
      onSelectCompanion: selectCompanion,
      onClaimReward: claimReward,
    });
    await nextTick();

    expect(root.querySelector('[data-companion="fina"]')).not.toBeNull();
    expect(root.textContent).toContain('菲妮');

    mountedApp?.unmount();
    document.body.innerHTML = '';
    const reward = mountJourney(run({
      rewardOffer: {
        offerId: 'offer-1',
        nodeId: 'chapter2.bridge',
        choices: [
          {
            choiceId: 'herb-pouch',
            cardDefinitionId: 'herb-pouch',
            name: '采药包',
            description: '回复 4 点生命。',
          },
          {
            choiceId: 'observe-signpost',
            cardDefinitionId: 'observe-signpost',
            name: '观察路标',
            description: '抽 1 张牌。',
          },
          {
            choiceId: 'party-strike',
            cardDefinitionId: 'party-strike',
            name: '旅团合击',
            description: '造成 10 点伤害。',
          },
        ],
      },
      currentNode: {
        nodeId: 'chapter2.bridge',
        chapterId: 'chapter-2',
        type: 'encounter',
        title: '钟摆守卫',
        speaker: '',
        text: '战斗结束。',
        encounterId: 'clockwork-sentinel',
        choices: [],
      },
    }), { onClaimReward: claimReward });
    await nextTick();
    buttonByText(reward, '旅团合击').click();
    expect(claimReward).toHaveBeenCalledWith('party-strike');

    mountedApp?.unmount();
    document.body.innerHTML = '';
    const camp = mountJourney(run({
      companionKey: 'serin',
      unlockedCharacters: ['lia', 'serin', 'fina'],
      currentNode: {
        nodeId: 'chapter2.camp',
        chapterId: 'chapter-2',
        type: 'camp',
        title: '塔外营地',
        speaker: '旅伴',
        text: '选择带队伙伴。',
        choices: [],
      },
    }), { onSelectCompanion: selectCompanion });
    await nextTick();
    buttonByText(camp, '菲妮').click();
    expect(selectCompanion).toHaveBeenCalledWith('fina');
  });
});
