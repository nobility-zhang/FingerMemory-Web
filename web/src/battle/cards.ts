import type {
  CardDefinition,
  CardDefinitionId,
} from './types';

export const CARD_DEFINITIONS: Readonly<
  Record<CardDefinitionId, CardDefinition>
> = {
  'traveler-strike': {
    id: 'traveler-strike',
    name: '旅人突刺',
    cost: 1,
    target: 'enemy',
    exhaust: false,
    effects: [{ type: 'damage', amount: 6 }],
  },
  'companion-guard': {
    id: 'companion-guard',
    name: '同伴守护',
    cost: 1,
    target: 'none',
    exhaust: false,
    effects: [{ type: 'block', amount: 5 }],
  },
  'ember-rune': {
    id: 'ember-rune',
    name: '余烬符文',
    cost: 1,
    target: 'enemy',
    exhaust: false,
    effects: [
      { type: 'damage', amount: 3 },
      { type: 'apply-burn', remainingTurns: 2, damagePerTurn: 2 },
    ],
  },
  'herb-pouch': {
    id: 'herb-pouch',
    name: '采药包',
    cost: 1,
    target: 'none',
    exhaust: false,
    effects: [{ type: 'heal', amount: 4 }],
  },
  'observe-signpost': {
    id: 'observe-signpost',
    name: '观察路标',
    cost: 0,
    target: 'none',
    exhaust: true,
    effects: [{ type: 'draw', amount: 1 }],
  },
  'party-strike': {
    id: 'party-strike',
    name: '旅团合击',
    cost: 2,
    target: 'enemy',
    exhaust: false,
    effects: [{ type: 'damage', amount: 10 }],
  },
};

export const TEACHING_DECK_ORDER: readonly CardDefinitionId[] = [
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
];

export function validateInitialDeck(
  deck: readonly CardDefinitionId[],
): void {
  if (deck.length !== 12) {
    throw new TypeError('card-battle-v1 requires exactly 12 initial cards');
  }
  const counts = new Map<CardDefinitionId, number>();
  for (const definitionId of deck) {
    if (CARD_DEFINITIONS[definitionId] === undefined) {
      throw new TypeError(`Unknown card definition: ${definitionId}`);
    }
    counts.set(definitionId, (counts.get(definitionId) ?? 0) + 1);
  }
  const expected: Record<CardDefinitionId, number> = {
    'traveler-strike': 3,
    'companion-guard': 3,
    'ember-rune': 2,
    'herb-pouch': 1,
    'observe-signpost': 1,
    'party-strike': 2,
  };
  for (const [definitionId, count] of Object.entries(expected)) {
    if (counts.get(definitionId as CardDefinitionId) !== count) {
      throw new TypeError(
        `card-battle-v1 deck count mismatch for ${definitionId}`,
      );
    }
  }
}
