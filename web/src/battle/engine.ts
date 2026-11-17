import {
  CARD_DEFINITIONS,
  TEACHING_DECK_ORDER,
  validateInitialDeck,
} from './cards';
import { normalizeSeed, shuffleDeterministic } from './random';
import {
  BATTLE_BASE_ENERGY,
  BATTLE_RULES_VERSION,
  BATTLE_STATE_SCHEMA_VERSION,
  BATTLE_TURN_DRAW,
  BATTLE_HAND_LIMIT,
  type BattleEvent,
  type BattleState,
  type BattleTransition,
  type CardDefinitionId,
  type CardInstance,
  type EnemyState,
  type EnemyIntent,
  type EnemyIntentTemplate,
  type PartyState,
} from './types';

const DEFAULT_INTENT_CYCLE: EnemyIntentTemplate[] = [
  { type: 'attack', value: 6 },
  { type: 'defend', value: 6 },
  { type: 'attack', value: 8 },
];

export class BattleRuleError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'BattleRuleError';
  }
}

export class BattleVersionConflictError extends Error {
  constructor(
    readonly expectedVersion: number,
    readonly actualVersion: number,
  ) {
    super(
      `Battle state version conflict: expected ${expectedVersion}, actual ${actualVersion}`,
    );
    this.name = 'BattleVersionConflictError';
  }
}

export interface CreateBattleInput {
  battleId: string;
  encounterId: string;
  seed?: number;
  shuffle?: boolean;
  deck?: readonly CardDefinitionId[];
  party?: PartyState;
  enemies?: EnemyState[];
  intentCycle?: EnemyIntentTemplate[];
}

export interface BattleCommand {
  expectedVersion: number;
}

export interface PlayCardCommand extends BattleCommand {
  cardInstanceId: string;
  targetId?: string;
}

export function createBattleState(input: CreateBattleInput): BattleState {
  const deck = input.deck ?? TEACHING_DECK_ORDER;
  validateInitialDeck(deck);
  const cards: CardInstance[] = deck.map((definitionId, index) => ({
    instanceId: `card-${String(index + 1).padStart(2, '0')}`,
    definitionId,
  }));
  const initialSeed = normalizeSeed(input.seed ?? 0x5eed1234);
  const shuffled = input.shuffle === false
    ? { values: cards, rngState: initialSeed }
    : shuffleDeterministic(cards, initialSeed);
  const party = input.party ?? {
    hp: 30,
    maxHp: 30,
    block: 0,
  };
  const enemies = input.enemies ?? [{
    id: 'bridge-guardian',
    hp: 36,
    maxHp: 36,
    block: 0,
    burn: null,
  }];
  if (enemies.length === 0) {
    throw new BattleRuleError('battle requires at least one enemy');
  }
  const intentCycle = input.intentCycle ?? DEFAULT_INTENT_CYCLE;
  if (intentCycle.length === 0) {
    throw new BattleRuleError('enemy intent cycle must not be empty');
  }
  const initial: BattleState = {
    schemaVersion: BATTLE_STATE_SCHEMA_VERSION,
    battleRulesVersion: BATTLE_RULES_VERSION,
    battleId: input.battleId,
    encounterId: input.encounterId,
    turnId: 1,
    phase: 'player-action',
    stateVersion: 0,
    rngState: shuffled.rngState,
    party: { ...party },
    enemies: enemies.map((enemy) => ({
      ...enemy,
      burn: enemy.burn === null ? null : { ...enemy.burn },
    })),
    energy: BATTLE_BASE_ENERGY,
    drawPile: shuffled.values,
    drawCursor: 0,
    hand: [],
    discardPile: [],
    exhaustPile: [],
    currentIntent: intentFor(enemies[0]!.id, intentCycle, 0),
    intentCycle: intentCycle.map((intent) => ({ ...intent })),
    intentIndex: 0,
    terminal: null,
  };
  return drawCards(initial, BATTLE_TURN_DRAW).state;
}

export function playCard(
  state: BattleState,
  command: PlayCardCommand,
): BattleTransition {
  assertCommand(state, command, 'player-action');
  const handIndex = state.hand.findIndex(
    (card) => card.instanceId === command.cardInstanceId,
  );
  if (handIndex < 0) {
    throw new BattleRuleError('card is not in hand');
  }
  const card = state.hand[handIndex]!;
  const definition = CARD_DEFINITIONS[card.definitionId];
  if (definition === undefined) {
    throw new BattleRuleError(`unknown card definition: ${card.definitionId}`);
  }
  if (state.energy < definition.cost) {
    throw new BattleRuleError('not enough energy');
  }
  if (
    definition.target === 'enemy'
    && (
      command.targetId === undefined
      || !state.enemies.some(
        (enemy) => enemy.id === command.targetId && enemy.hp > 0,
      )
    )
  ) {
    throw new BattleRuleError('valid enemy target required');
  }

  const next = structuredClone(state);
  const events: BattleEvent[] = [];
  const played = next.hand.splice(handIndex, 1)[0]!;
  next.energy -= definition.cost;
  if (definition.exhaust) {
    next.exhaustPile.push(played);
  } else {
    next.discardPile.push(played);
  }
  events.push({
    type: 'card-played',
    card: { ...played },
    definitionId: played.definitionId,
    energySpent: definition.cost,
  });

  for (const effect of definition.effects) {
    if (effect.type === 'damage') {
      const enemy = findLivingEnemy(next, command.targetId);
      if (enemy !== null) {
        events.push(damageEnemy(enemy, effect.amount, 'card'));
      }
      continue;
    }
    if (effect.type === 'block') {
      next.party.block += effect.amount;
      events.push({
        type: 'block-gained',
        targetId: 'party',
        amount: effect.amount,
        totalBlock: next.party.block,
      });
      continue;
    }
    if (effect.type === 'heal') {
      const healed = Math.min(
        effect.amount,
        next.party.maxHp - next.party.hp,
      );
      next.party.hp += healed;
      events.push({
        type: 'healed',
        targetId: 'party',
        amount: healed,
        totalHp: next.party.hp,
      });
      continue;
    }
    if (effect.type === 'draw') {
      const draw = drawCards(next, effect.amount);
      replaceState(next, draw.state);
      events.push(...draw.events);
      continue;
    }
    if (effect.type === 'apply-burn') {
      const enemy = findLivingEnemy(next, command.targetId);
      if (enemy !== null) {
        enemy.burn = {
          remainingTurns: effect.remainingTurns,
          damagePerTurn: effect.damagePerTurn,
        };
        events.push({
          type: 'burn-applied',
          targetId: enemy.id,
          remainingTurns: effect.remainingTurns,
          damagePerTurn: effect.damagePerTurn,
        });
      }
    }
  }

  const terminal = terminalResult(next);
  if (terminal !== null) {
    setTerminal(next, terminal, events);
  }
  next.stateVersion = state.stateVersion + 1;
  return { state: next, events };
}

export function endTurn(
  state: BattleState,
  command: BattleCommand,
): BattleTransition {
  assertCommand(state, command, 'player-action');
  const next = structuredClone(state);
  const events: BattleEvent[] = [];
  const discarded = next.hand.splice(0);
  next.discardPile.push(...discarded);
  next.energy = 0;
  next.phase = 'enemy-action';
  events.push({
    type: 'turn-ended',
    turnId: next.turnId,
    discarded: discarded.map((card) => ({ ...card })),
  });

  for (const enemy of next.enemies) {
    if (enemy.hp <= 0 || enemy.burn === null) {
      continue;
    }
    events.push(damageEnemy(enemy, enemy.burn.damagePerTurn, 'burn'));
    enemy.burn.remainingTurns -= 1;
    events.push({
      type: 'burn-ticked',
      targetId: enemy.id,
      remainingTurns: enemy.burn.remainingTurns,
    });
    if (enemy.burn.remainingTurns <= 0) {
      enemy.burn = null;
    }
  }

  let terminal = terminalResult(next);
  if (terminal !== null) {
    setTerminal(next, terminal, events);
    next.stateVersion = state.stateVersion + 1;
    return { state: next, events };
  }

  const actingEnemy = next.enemies.find(
    (enemy) => enemy.id === next.currentIntent.enemyId && enemy.hp > 0,
  );
  if (actingEnemy !== undefined) {
    events.push({
      type: 'intent-executed',
      intent: { ...next.currentIntent },
    });
    if (next.currentIntent.type === 'attack') {
      const blocked = Math.min(
        next.party.block,
        next.currentIntent.value,
      );
      next.party.block -= blocked;
      const hpLost = Math.min(
        next.party.hp,
        next.currentIntent.value - blocked,
      );
      next.party.hp -= hpLost;
      events.push({
        type: 'damage',
        source: 'enemy',
        targetId: 'party',
        amount: next.currentIntent.value,
        blocked,
        hpLost,
      });
    } else {
      actingEnemy.block += next.currentIntent.value;
      events.push({
        type: 'block-gained',
        targetId: actingEnemy.id,
        amount: next.currentIntent.value,
        totalBlock: actingEnemy.block,
      });
    }
  }

  terminal = terminalResult(next);
  if (terminal !== null) {
    setTerminal(next, terminal, events);
    next.stateVersion = state.stateVersion + 1;
    return { state: next, events };
  }

  next.turnId += 1;
  next.party.block = 0;
  next.energy = BATTLE_BASE_ENERGY;
  next.intentIndex = (next.intentIndex + 1) % next.intentCycle.length;
  next.currentIntent = intentFor(
    next.enemies[0]!.id,
    next.intentCycle,
    next.intentIndex,
  );
  next.phase = 'player-action';
  events.push({
    type: 'turn-started',
    turnId: next.turnId,
    intent: { ...next.currentIntent },
  });
  const draw = drawCards(next, BATTLE_TURN_DRAW);
  replaceState(next, draw.state);
  events.push(...draw.events);
  next.stateVersion = state.stateVersion + 1;
  return { state: next, events };
}

function assertCommand(
  state: BattleState,
  command: BattleCommand,
  requiredPhase: BattleState['phase'],
): void {
  if (command.expectedVersion !== state.stateVersion) {
    throw new BattleVersionConflictError(
      command.expectedVersion,
      state.stateVersion,
    );
  }
  if (state.phase !== requiredPhase) {
    throw new BattleRuleError(
      `command requires phase ${requiredPhase}, current ${state.phase}`,
    );
  }
}

function drawCards(
  state: BattleState,
  amount: number,
): BattleTransition {
  const next = structuredClone(state);
  const events: BattleEvent[] = [];
  const drawn: CardInstance[] = [];
  let recycled = false;
  while (drawn.length < amount && next.hand.length < BATTLE_HAND_LIMIT) {
    if (next.drawCursor >= next.drawPile.length) {
      if (next.discardPile.length === 0) {
        break;
      }
      const shuffled = shuffleDeterministic(
        next.discardPile,
        next.rngState,
      );
      next.drawPile = shuffled.values;
      next.rngState = shuffled.rngState;
      next.drawCursor = 0;
      next.discardPile = [];
      recycled = true;
    }
    const card = next.drawPile[next.drawCursor];
    if (card === undefined) {
      break;
    }
    next.drawCursor += 1;
    next.hand.push(card);
    drawn.push(card);
  }
  if (drawn.length > 0) {
    events.push({
      type: 'cards-drawn',
      cards: drawn.map((card) => ({ ...card })),
      recycled,
    });
  }
  return { state: next, events };
}

function replaceState(target: BattleState, source: BattleState): void {
  Object.assign(target, source);
}

function findLivingEnemy(
  state: BattleState,
  targetId: string | undefined,
): EnemyState | null {
  if (targetId === undefined) {
    return null;
  }
  return state.enemies.find(
    (enemy) => enemy.id === targetId && enemy.hp > 0,
  ) ?? null;
}

function damageEnemy(
  enemy: EnemyState,
  amount: number,
  source: 'card' | 'burn',
): BattleEvent {
  const blocked = Math.min(enemy.block, amount);
  enemy.block -= blocked;
  const hpLost = Math.min(enemy.hp, amount - blocked);
  enemy.hp -= hpLost;
  return {
    type: 'damage',
    source,
    targetId: enemy.id,
    amount,
    blocked,
    hpLost,
  };
}

function intentFor(
  enemyId: string,
  cycle: readonly EnemyIntentTemplate[],
  index: number,
): EnemyIntent {
  const intent = cycle[index % cycle.length]!;
  return { enemyId, type: intent.type, value: intent.value };
}

function terminalResult(
  state: BattleState,
): 'victory' | 'defeat' | null {
  if (state.party.hp <= 0) {
    return 'defeat';
  }
  if (state.enemies.every((enemy) => enemy.hp <= 0)) {
    return 'victory';
  }
  return null;
}

function setTerminal(
  state: BattleState,
  result: 'victory' | 'defeat',
  events: BattleEvent[],
): void {
  if (state.terminal !== null) {
    return;
  }
  state.terminal = result;
  state.phase = result;
  events.push({ type: 'battle-ended', result });
}
