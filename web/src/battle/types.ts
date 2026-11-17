export const BATTLE_RULES_VERSION = 'card-battle-v1' as const;
export const BATTLE_STATE_SCHEMA_VERSION = 1 as const;
export const BATTLE_BASE_ENERGY = 3 as const;
export const BATTLE_TURN_DRAW = 4 as const;
export const BATTLE_HAND_LIMIT = 6 as const;

export type CardDefinitionId =
  | 'traveler-strike'
  | 'companion-guard'
  | 'ember-rune'
  | 'herb-pouch'
  | 'observe-signpost'
  | 'party-strike';

export type CardTarget = 'none' | 'enemy';

export type CardEffect =
  | { type: 'damage'; amount: number }
  | { type: 'block'; amount: number }
  | { type: 'heal'; amount: number }
  | { type: 'draw'; amount: number }
  | {
      type: 'apply-burn';
      remainingTurns: number;
      damagePerTurn: number;
    };

export interface CardDefinition {
  id: CardDefinitionId;
  name: string;
  cost: number;
  target: CardTarget;
  exhaust: boolean;
  effects: readonly CardEffect[];
}

export interface CardInstance {
  instanceId: string;
  definitionId: CardDefinitionId;
}

export interface BurnStatus {
  remainingTurns: number;
  damagePerTurn: number;
}

export interface PartyState {
  hp: number;
  maxHp: number;
  block: number;
}

export interface EnemyState {
  id: string;
  hp: number;
  maxHp: number;
  block: number;
  burn: BurnStatus | null;
}

export interface EnemyIntentTemplate {
  type: 'attack' | 'defend';
  value: number;
}

export interface EnemyIntent extends EnemyIntentTemplate {
  enemyId: string;
}

export type BattlePhase =
  | 'player-action'
  | 'enemy-action'
  | 'victory'
  | 'defeat';

export interface BattleState {
  schemaVersion: typeof BATTLE_STATE_SCHEMA_VERSION;
  battleRulesVersion: typeof BATTLE_RULES_VERSION;
  battleId: string;
  encounterId: string;
  turnId: number;
  phase: BattlePhase;
  stateVersion: number;
  rngState: number;
  party: PartyState;
  enemies: EnemyState[];
  energy: number;
  drawPile: CardInstance[];
  drawCursor: number;
  hand: CardInstance[];
  discardPile: CardInstance[];
  exhaustPile: CardInstance[];
  currentIntent: EnemyIntent;
  intentCycle: EnemyIntentTemplate[];
  intentIndex: number;
  terminal: 'victory' | 'defeat' | null;
}

export type BattleEvent =
  | {
      type: 'battle-started';
      turnId: number;
      hand: CardInstance[];
    }
  | {
      type: 'cards-drawn';
      cards: CardInstance[];
      recycled: boolean;
    }
  | {
      type: 'card-played';
      card: CardInstance;
      definitionId: CardDefinitionId;
      energySpent: number;
    }
  | {
      type: 'damage';
      source: 'card' | 'burn' | 'enemy';
      targetId: string;
      amount: number;
      blocked: number;
      hpLost: number;
    }
  | {
      type: 'block-gained';
      targetId: string;
      amount: number;
      totalBlock: number;
    }
  | {
      type: 'healed';
      targetId: string;
      amount: number;
      totalHp: number;
    }
  | {
      type: 'burn-applied';
      targetId: string;
      remainingTurns: number;
      damagePerTurn: number;
    }
  | {
      type: 'burn-ticked';
      targetId: string;
      remainingTurns: number;
    }
  | {
      type: 'intent-executed';
      intent: EnemyIntent;
    }
  | {
      type: 'turn-ended';
      turnId: number;
      discarded: CardInstance[];
    }
  | {
      type: 'turn-started';
      turnId: number;
      intent: EnemyIntent;
    }
  | {
      type: 'battle-ended';
      result: 'victory' | 'defeat';
    };

export interface BattleTransition {
  state: BattleState;
  events: BattleEvent[];
}
