import {
  createBattleState,
  endTurn,
  playCard,
} from '../engine';
import type {
  BattleEvent,
  BattleState,
} from '../types';
import fixtureJson from './card-battle-v1.turn.json';

export type CardBattleFixtureAction =
  | {
      type: 'play-card';
      expectedVersion: number;
      cardInstanceId: string;
      targetId?: string;
    }
  | {
      type: 'end-turn';
      expectedVersion: number;
    };

export interface CardBattleV1Fixture {
  fixtureVersion: 1;
  expected: {
    stateVersion: number;
    turnId: number;
    phase: BattleState['phase'];
    partyHp: number;
    partyBlock: number;
    enemyHp: number;
    enemyBlock: number;
    burnRemainingTurns: number | null;
    energy: number;
    drawCursor: number;
    handCount: number;
    discardCount: number;
    exhaustCount: number;
    intentType: 'attack' | 'defend';
    intentValue: number;
    terminal: BattleState['terminal'];
  };
  actions: CardBattleFixtureAction[];
}

export const CARD_BATTLE_V1_TURN_FIXTURE =
  fixtureJson as CardBattleV1Fixture;

export function runCardBattleV1Fixture(
  fixture: CardBattleV1Fixture = CARD_BATTLE_V1_TURN_FIXTURE,
): {
  state: BattleState;
  events: BattleEvent[];
} {
  let state = createBattleState({
    battleId: 'fixture-bridge-turn',
    encounterId: 'bridge-guardian',
    shuffle: false,
  });
  const events: BattleEvent[] = [];
  for (const action of fixture.actions) {
    const transition = action.type === 'play-card'
      ? playCard(state, {
          expectedVersion: action.expectedVersion,
          cardInstanceId: action.cardInstanceId,
          targetId: action.targetId,
        })
      : endTurn(state, { expectedVersion: action.expectedVersion });
    state = transition.state;
    events.push(...transition.events);
  }
  return { state, events };
}

export function fixtureExpectationMatches(
  state: BattleState,
  expected: CardBattleV1Fixture['expected'],
): boolean {
  const enemy = state.enemies[0];
  return (
    state.stateVersion === expected.stateVersion
    && state.turnId === expected.turnId
    && state.phase === expected.phase
    && state.party.hp === expected.partyHp
    && state.party.block === expected.partyBlock
    && enemy?.hp === expected.enemyHp
    && enemy.block === expected.enemyBlock
    && enemy.burn?.remainingTurns === expected.burnRemainingTurns
    && state.energy === expected.energy
    && state.drawCursor === expected.drawCursor
    && state.hand.length === expected.handCount
    && state.discardPile.length === expected.discardCount
    && state.exhaustPile.length === expected.exhaustCount
    && state.currentIntent.type === expected.intentType
    && state.currentIntent.value === expected.intentValue
    && state.terminal === expected.terminal
  );
}
