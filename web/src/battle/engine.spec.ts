import { describe, expect, it } from 'vitest';
import {
  BattleRuleError,
  BattleVersionConflictError,
  createBattleState,
  endTurn,
  playCard,
} from './engine';
import {
  CARD_BATTLE_V1_TURN_FIXTURE,
  fixtureExpectationMatches,
  runCardBattleV1Fixture,
} from './fixtures/cardBattleV1';
import {
  BattleStateValidationError,
  parseBattleState,
  restoreBattleState,
  serializeBattleState,
} from './stateSchema';
import {
  BATTLE_BASE_ENERGY,
  BATTLE_RULES_VERSION,
  BATTLE_STATE_SCHEMA_VERSION,
  BATTLE_TURN_DRAW,
} from './types';

describe('card-battle-v1 deterministic fixture', () => {
  it('replays one complete turn from the stable 12-card teaching deck', () => {
    const initial = createBattleState({
      battleId: 'fixture-bridge-turn',
      encounterId: 'bridge-guardian',
      shuffle: false,
    });
    expect(initial).toMatchObject({
      schemaVersion: BATTLE_STATE_SCHEMA_VERSION,
      battleRulesVersion: BATTLE_RULES_VERSION,
      stateVersion: 0,
      turnId: 1,
      phase: 'player-action',
      energy: BATTLE_BASE_ENERGY,
    });
    expect(initial.drawPile).toHaveLength(12);
    expect(initial.drawCursor).toBe(BATTLE_TURN_DRAW);
    expect(initial.hand.map((card) => card.definitionId)).toEqual([
      'companion-guard',
      'traveler-strike',
      'ember-rune',
      'observe-signpost',
    ]);

    const { state, events } = runCardBattleV1Fixture();
    const expected = CARD_BATTLE_V1_TURN_FIXTURE.expected;
    expect(fixtureExpectationMatches(state, expected)).toBe(true);
    expect(state).toMatchObject({
      stateVersion: expected.stateVersion,
      turnId: expected.turnId,
      phase: expected.phase,
      energy: expected.energy,
      drawCursor: expected.drawCursor,
      terminal: expected.terminal,
    });
    expect(state.party).toMatchObject({
      hp: expected.partyHp,
      block: expected.partyBlock,
    });
    expect(state.enemies[0]).toMatchObject({
      hp: expected.enemyHp,
      block: expected.enemyBlock,
      burn: {
        remainingTurns: expected.burnRemainingTurns,
      },
    });
    expect(state.hand).toHaveLength(expected.handCount);
    expect(state.discardPile).toHaveLength(expected.discardCount);
    expect(state.exhaustPile).toHaveLength(expected.exhaustCount);
    expect(state.currentIntent).toMatchObject({
      type: expected.intentType,
      value: expected.intentValue,
    });
    expect(events.map((event) => event.type)).toEqual(expect.arrayContaining([
      'card-played',
      'block-gained',
      'damage',
      'burn-applied',
      'cards-drawn',
      'turn-ended',
      'burn-ticked',
      'intent-executed',
      'turn-started',
    ]));
  });

  it('produces identical seeded shuffles and changes the RNG stream', () => {
    const first = createBattleState({
      battleId: 'seed-a',
      encounterId: 'bridge-guardian',
      seed: 424242,
    });
    const second = createBattleState({
      battleId: 'seed-b',
      encounterId: 'bridge-guardian',
      seed: 424242,
    });
    const different = createBattleState({
      battleId: 'seed-c',
      encounterId: 'bridge-guardian',
      seed: 424243,
    });
    expect(first.drawPile).toEqual(second.drawPile);
    expect(first.hand).toEqual(second.hand);
    expect(different.hand).not.toEqual(first.hand);
  });
});

describe('battle command boundaries', () => {
  it('rejects stale state versions before mutating rules state', () => {
    const state = createBattleState({
      battleId: 'version-conflict',
      encounterId: 'bridge-guardian',
      shuffle: false,
    });
    expect(() => endTurn(state, { expectedVersion: 1 }))
      .toThrow(BattleVersionConflictError);
  });

  it('validates card ownership, energy and enemy target', () => {
    const state = createBattleState({
      battleId: 'command-validation',
      encounterId: 'bridge-guardian',
      shuffle: false,
    });
    expect(() => playCard(state, {
      expectedVersion: 0,
      cardInstanceId: 'card-09',
      targetId: 'bridge-guardian',
    })).toThrow(BattleRuleError);
    expect(() => playCard(state, {
      expectedVersion: 0,
      cardInstanceId: 'card-02',
    })).toThrow('valid enemy target required');
    const noEnergy = {
      ...state,
      energy: 0,
    };
    expect(() => playCard(noEnergy, {
      expectedVersion: 0,
      cardInstanceId: 'card-01',
    })).toThrow('not enough energy');
  });

  it('applies damage through shield before health', () => {
    const state = createBattleState({
      battleId: 'shield-check',
      encounterId: 'bridge-guardian',
      shuffle: false,
      enemies: [{
        id: 'bridge-guardian',
        hp: 36,
        maxHp: 36,
        block: 5,
        burn: null,
      }],
    });
    const transition = playCard(state, {
      expectedVersion: 0,
      cardInstanceId: 'card-02',
      targetId: 'bridge-guardian',
    });
    expect(transition.state.enemies[0]).toMatchObject({
      hp: 35,
      block: 0,
    });
    expect(transition.events).toContainEqual({
      type: 'damage',
      source: 'card',
      targetId: 'bridge-guardian',
      amount: 6,
      blocked: 5,
      hpLost: 1,
    });
  });
});

describe('draw, discard and exhaust boundaries', () => {
  it('moves an exhausted card out of all recyclable piles before draw', () => {
    const state = createBattleState({
      battleId: 'exhaust-check',
      encounterId: 'bridge-guardian',
      shuffle: false,
    });
    const observe = state.hand.find(
      (card) => card.definitionId === 'observe-signpost',
    )!;
    const transition = playCard(state, {
      expectedVersion: 0,
      cardInstanceId: observe.instanceId,
    });
    expect(transition.state.exhaustPile).toEqual([observe]);
    expect(transition.state.discardPile).toEqual([]);
    expect(transition.state.drawCursor).toBe(5);
    expect(transition.state.hand).toHaveLength(4);
    expect(transition.events).toContainEqual(expect.objectContaining({
      type: 'cards-drawn',
      recycled: false,
    }));
  });

  it('recycles discard in a deterministic shuffled order without reviving exhaust', () => {
    const base = createBattleState({
      battleId: 'recycle-check',
      encounterId: 'bridge-guardian',
      shuffle: false,
    });
    const observe = base.hand.find(
      (card) => card.definitionId === 'observe-signpost',
    )!;
    const recyclable = base.hand.filter(
      (card) => card.instanceId !== observe.instanceId,
    );
    const state = {
      ...base,
      hand: [observe],
      drawCursor: base.drawPile.length,
      discardPile: recyclable,
      exhaustPile: [],
    };
    const transition = playCard(state, {
      expectedVersion: 0,
      cardInstanceId: observe.instanceId,
    });
    expect(transition.state.exhaustPile).toEqual([observe]);
    expect(transition.state.discardPile).toEqual([]);
    expect(transition.state.drawCursor).toBe(1);
    expect(recyclable.map((card) => card.instanceId)).toContain(
      transition.state.hand[0]!.instanceId,
    );
    expect(transition.state.hand[0]!.instanceId).not.toBe(observe.instanceId);
    expect(transition.events).toContainEqual(expect.objectContaining({
      type: 'cards-drawn',
      recycled: true,
    }));
  });

  it('draws fewer cards without error when both recyclable piles are empty', () => {
    const base = createBattleState({
      battleId: 'empty-draw',
      encounterId: 'bridge-guardian',
      shuffle: false,
    });
    const observe = base.hand.find(
      (card) => card.definitionId === 'observe-signpost',
    )!;
    const state = {
      ...base,
      hand: [observe],
      drawCursor: base.drawPile.length,
      discardPile: [],
    };
    const transition = playCard(state, {
      expectedVersion: 0,
      cardInstanceId: observe.instanceId,
    });
    expect(transition.state.hand).toEqual([]);
    expect(transition.state.exhaustPile).toEqual([observe]);
    expect(transition.events.some((event) => event.type === 'cards-drawn'))
      .toBe(false);
  });
});

describe('burn, intents and terminal order', () => {
  it('refreshes burn duration instead of stacking damage', () => {
    const base = createBattleState({
      battleId: 'burn-refresh',
      encounterId: 'bridge-guardian',
      shuffle: false,
    });
    base.enemies[0]!.burn = {
      remainingTurns: 1,
      damagePerTurn: 2,
    };
    const ember = base.hand.find(
      (card) => card.definitionId === 'ember-rune',
    )!;
    const transition = playCard(base, {
      expectedVersion: 0,
      cardInstanceId: ember.instanceId,
      targetId: 'bridge-guardian',
    });
    expect(transition.state.enemies[0]!.burn).toEqual({
      remainingTurns: 2,
      damagePerTurn: 2,
    });
  });

  it('executes the visible defend intent after the first turn', () => {
    const afterFixture = runCardBattleV1Fixture().state;
    const transition = endTurn(afterFixture, {
      expectedVersion: afterFixture.stateVersion,
    });
    expect(transition.state.enemies[0]!.block).toBe(6);
    expect(transition.state.currentIntent.type).toBe('attack');
    expect(transition.events).toContainEqual({
      type: 'intent-executed',
      intent: {
        enemyId: 'bridge-guardian',
        type: 'defend',
        value: 6,
      },
    });
  });

  it('ends in victory from burn before the enemy intent can execute', () => {
    const base = createBattleState({
      battleId: 'burn-victory',
      encounterId: 'bridge-guardian',
      shuffle: false,
      enemies: [{
        id: 'bridge-guardian',
        hp: 1,
        maxHp: 36,
        block: 0,
        burn: { remainingTurns: 1, damagePerTurn: 2 },
      }],
    });
    const transition = endTurn(base, { expectedVersion: 0 });
    expect(transition.state).toMatchObject({
      phase: 'victory',
      terminal: 'victory',
      party: { hp: 30 },
    });
    expect(transition.events.some((event) => event.type === 'intent-executed'))
      .toBe(false);
  });

  it('prioritizes party defeat when enemy attack reduces hp to zero', () => {
    const base = createBattleState({
      battleId: 'party-defeat',
      encounterId: 'bridge-guardian',
      shuffle: false,
      party: { hp: 1, maxHp: 30, block: 0 },
      enemies: [{
        id: 'bridge-guardian',
        hp: 10,
        maxHp: 36,
        block: 0,
        burn: null,
      }],
    });
    const transition = endTurn(base, { expectedVersion: 0 });
    expect(transition.state).toMatchObject({
      phase: 'defeat',
      terminal: 'defeat',
      party: { hp: 0 },
    });
  });
});

describe('versioned battle snapshots', () => {
  it('serializes and restores an exact resumable state', () => {
    const state = runCardBattleV1Fixture().state;
    const serialized = serializeBattleState(state);
    expect(serialized.endsWith('\n')).toBe(true);
    expect(restoreBattleState(serialized)).toEqual(state);
    expect(parseBattleState(state).stateVersion).toBe(5);
  });

  it('rejects malformed, future or inconsistent save state', () => {
    const state = runCardBattleV1Fixture().state;
    expect(() => restoreBattleState('{bad json')).toThrow(
      BattleStateValidationError,
    );
    expect(() => parseBattleState({
      ...state,
      battleRulesVersion: 'card-battle-v2',
    })).toThrow(BattleStateValidationError);
    expect(() => parseBattleState({
      ...state,
      drawCursor: state.drawPile.length + 1,
    })).toThrow(BattleStateValidationError);
  });
});
