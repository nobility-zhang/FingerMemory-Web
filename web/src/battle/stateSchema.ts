import { z } from 'zod';
import {
  BATTLE_RULES_VERSION,
  BATTLE_STATE_SCHEMA_VERSION,
  type BattleState,
} from './types';

const cardDefinitionIdSchema = z.enum([
  'traveler-strike',
  'companion-guard',
  'ember-rune',
  'herb-pouch',
  'observe-signpost',
  'party-strike',
]);

const cardInstanceSchema = z
  .object({
    instanceId: z.string().min(1),
    definitionId: cardDefinitionIdSchema,
  })
  .strict();

const enemyStateSchema = z
  .object({
    id: z.string().min(1),
    hp: z.number().int().nonnegative(),
    maxHp: z.number().int().positive(),
    block: z.number().int().nonnegative(),
    burn: z
      .object({
        remainingTurns: z.number().int().nonnegative(),
        damagePerTurn: z.number().int().positive(),
      })
      .strict()
      .nullable(),
  })
  .strict();

const intentSchema = z
  .object({
    enemyId: z.string().min(1),
    type: z.enum(['attack', 'defend']),
    value: z.number().int().positive(),
  })
  .strict();

export const battleStateSchema = z
  .object({
    schemaVersion: z.literal(BATTLE_STATE_SCHEMA_VERSION),
    battleRulesVersion: z.literal(BATTLE_RULES_VERSION),
    battleId: z.string().min(1),
    encounterId: z.string().min(1),
    turnId: z.number().int().positive(),
    phase: z.enum(['player-action', 'enemy-action', 'victory', 'defeat']),
    stateVersion: z.number().int().nonnegative(),
    rngState: z.number().int(),
    party: z
      .object({
        hp: z.number().int().nonnegative(),
        maxHp: z.number().int().positive(),
        block: z.number().int().nonnegative(),
      })
      .strict(),
    enemies: z.array(enemyStateSchema).min(1),
    energy: z.number().int().nonnegative(),
    drawPile: z.array(cardInstanceSchema),
    drawCursor: z.number().int().nonnegative(),
    hand: z.array(cardInstanceSchema),
    discardPile: z.array(cardInstanceSchema),
    exhaustPile: z.array(cardInstanceSchema),
    currentIntent: intentSchema,
    intentCycle: z
      .array(
        z
          .object({
            type: z.enum(['attack', 'defend']),
            value: z.number().int().positive(),
          })
          .strict(),
      )
      .min(1),
    intentIndex: z.number().int().nonnegative(),
    terminal: z.enum(['victory', 'defeat']).nullable(),
  })
  .strict()
  .superRefine((state, context) => {
    if (state.drawCursor > state.drawPile.length) {
      context.addIssue({
        code: 'custom',
        path: ['drawCursor'],
        message: 'drawCursor must not exceed drawPile length',
      });
    }
    if (state.party.hp > state.party.maxHp) {
      context.addIssue({
        code: 'custom',
        path: ['party', 'hp'],
        message: 'party hp must not exceed maxHp',
      });
    }
    for (const [index, enemy] of state.enemies.entries()) {
      if (enemy.hp > enemy.maxHp) {
        context.addIssue({
          code: 'custom',
          path: ['enemies', index, 'hp'],
          message: 'enemy hp must not exceed maxHp',
        });
      }
    }
    if (state.phase === 'victory' && state.terminal !== 'victory') {
      context.addIssue({
        code: 'custom',
        path: ['terminal'],
        message: 'victory phase requires victory terminal state',
      });
    }
    if (state.phase === 'defeat' && state.terminal !== 'defeat') {
      context.addIssue({
        code: 'custom',
        path: ['terminal'],
        message: 'defeat phase requires defeat terminal state',
      });
    }
  }) satisfies z.ZodType<BattleState>;

export class BattleStateValidationError extends Error {
  constructor(message: string) {
    super(`Invalid battle state: ${message}`);
    this.name = 'BattleStateValidationError';
  }
}

export function parseBattleState(input: unknown): BattleState {
  const parsed = battleStateSchema.safeParse(input);
  if (!parsed.success) {
    throw new BattleStateValidationError(parsed.error.message);
  }
  return parsed.data;
}

export function serializeBattleState(state: BattleState): string {
  return `${JSON.stringify(parseBattleState(state), null, 2)}\n`;
}

export function restoreBattleState(serialized: string): BattleState {
  let input: unknown;
  try {
    input = JSON.parse(serialized);
  } catch {
    throw new BattleStateValidationError('state is not valid JSON');
  }
  return parseBattleState(input);
}
