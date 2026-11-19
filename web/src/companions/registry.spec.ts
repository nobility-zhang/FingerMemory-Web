import { describe, expect, it } from 'vitest';
import type {
  AdventureCharacterView,
  AdventureRunView,
} from '../api/contracts';
import { lia } from './catalog';
import {
  companionsForRun,
  contentAssetUrl,
  mergeCompanions,
  profileFromCharacter,
} from './registry';

function nora(): AdventureCharacterView {
  const portraits = Object.fromEntries(
    ['neutral', 'welcome', 'cheer', 'concern', 'relief', 'surprise']
      .map((expression) => [
        expression,
        `assets/characters/nora/wind-courier/${expression}.png`,
      ]),
  );
  return {
    characterKey: 'nora',
    installationId: 'installation/windmill',
    name: '诺拉',
    epithet: '逆风邮路的年轻信使',
    description: '熟悉每一阵会改变路标方向的风。',
    age: 15,
    defaultCostumeId: 'wind-courier',
    costumes: {
      'wind-courier': portraits,
    },
    dialogue: {
      welcome: '风向正好。',
      again: '下一次再送。',
      rest: '邮站会等到明天。',
      training: '先看路标，再迈步。',
      practice: '这次算熟悉路线。',
      result: '信送到了。',
    },
    skill: {
      name: '顺风投递',
      description: '对首个敌人造成 6 点伤害。',
      type: 'damage',
      amount: 6,
    },
  };
}

function run(characters: AdventureCharacterView[]): AdventureRunView {
  return {
    id: 'run-windmill',
    installationId: 'installation-windmill',
    namespace: 'fm.demo',
    bookId: 'windmill-post',
    contentVersion: '1.0.0',
    contentHash: 'a'.repeat(64),
    title: '风车邮路',
    companionKey: 'lia',
    status: 'active',
    runVersion: 1,
    sessionId: 'session-windmill',
    currentNode: {
      nodeId: 'windmill.meet',
      chapterId: 'windmill-post-chapter',
      type: 'dialogue',
      title: '路中央的信使',
      speaker: '诺拉',
      text: '她从麦田边跑来。',
      choices: [],
    },
    deck: [
      'traveler-strike',
      'traveler-strike',
      'traveler-strike',
      'companion-guard',
      'companion-guard',
      'companion-guard',
      'ember-rune',
      'ember-rune',
      'herb-pouch',
      'observe-signpost',
      'party-strike',
      'party-strike',
    ],
    unlockedCharacters: ['lia', 'serin', 'nora'],
    characters,
    flags: {},
    createdAtMs: 1,
    updatedAtMs: 2,
  };
}

describe('content package companion registry', () => {
  it('maps a package character into the shared companion profile shape', () => {
    const profile = profileFromCharacter(nora());

    expect(profile.source).toBe('content-package');
    expect(profile.defaultCostumeId).toBe('wind-courier');
    expect(profile.portraits.cheer).toBe(
      '/api/v1/content-packages/installation%2Fwindmill/assets/'
        + 'assets/characters/nora/wind-courier/cheer.png',
    );
    expect(profile.skill).toEqual({
      name: '顺风投递',
      description: '对首个敌人造成 6 点伤害。',
      type: 'damage',
      amount: 6,
    });
  });

  it('keeps built-ins authoritative and adds only new package identities', () => {
    const profiles = mergeCompanions([lia], [nora()]);

    expect(profiles.map((profile) => profile.id)).toEqual(['lia', 'nora']);
    expect(profiles[0]).toBe(lia);
    expect(companionsForRun(run([nora()]), [lia]).map((item) => item.id))
      .toEqual(['lia', 'nora']);
  });

  it('encodes every asset path segment without changing path separators', () => {
    expect(contentAssetUrl('installation 1', 'assets/nora/a b.png')).toBe(
      '/api/v1/content-packages/installation%201/assets/assets/nora/a%20b.png',
    );
  });
});
