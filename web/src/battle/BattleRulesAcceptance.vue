<script setup lang="ts">
import { computed, ref } from 'vue';
import { CARD_DEFINITIONS } from './cards';
import { createBattleState } from './engine';
import {
  CARD_BATTLE_V1_TURN_FIXTURE,
  fixtureExpectationMatches,
  runCardBattleV1Fixture,
} from './fixtures/cardBattleV1';
import type {
  BattleEvent,
  BattleState,
} from './types';

function initialState(): BattleState {
  return createBattleState({
    battleId: 'fixture-bridge-turn',
    encounterId: 'bridge-guardian',
    shuffle: false,
  });
}

const state = ref(initialState());
const events = ref<BattleEvent[]>([]);
const hasRun = ref(false);
const passed = computed(
  () =>
    hasRun.value
    && fixtureExpectationMatches(
      state.value,
      CARD_BATTLE_V1_TURN_FIXTURE.expected,
    ),
);
const enemy = computed(() => state.value.enemies[0]);
const handDefinitions = computed(() =>
  state.value.hand.map((card) => CARD_DEFINITIONS[card.definitionId]),
);

function runFixture(): void {
  const result = runCardBattleV1Fixture();
  state.value = result.state;
  events.value = result.events;
  hasRun.value = true;
}

function reset(): void {
  state.value = initialState();
  events.value = [];
  hasRun.value = false;
}

function eventLabel(event: BattleEvent): string {
  switch (event.type) {
    case 'battle-started':
      return `战斗开始 · 回合 ${event.turnId}`;
    case 'cards-drawn':
      return `抽取 ${event.cards.length} 张${event.recycled ? '，弃牌堆已洗回' : ''}`;
    case 'card-played':
      return `打出 ${CARD_DEFINITIONS[event.definitionId].name} · 费用 ${event.energySpent}`;
    case 'damage':
      return `${event.source} 伤害 ${event.amount} · 护盾吸收 ${event.blocked} · 生命损失 ${event.hpLost}`;
    case 'block-gained':
      return `${event.targetId} 获得 ${event.amount} 格挡 · 当前 ${event.totalBlock}`;
    case 'healed':
      return `${event.targetId} 回复 ${event.amount} · 当前 ${event.totalHp}`;
    case 'burn-applied':
      return `${event.targetId} 灼烧 ${event.damagePerTurn} × ${event.remainingTurns}`;
    case 'burn-ticked':
      return `${event.targetId} 灼烧结算 · 剩余 ${event.remainingTurns}`;
    case 'intent-executed':
      return `敌人意图 ${event.intent.type} ${event.intent.value}`;
    case 'turn-ended':
      return `回合 ${event.turnId} 结束 · 弃掉 ${event.discarded.length} 张`;
    case 'turn-started':
      return `回合 ${event.turnId} 开始 · 意图 ${event.intent.type} ${event.intent.value}`;
    case 'battle-ended':
      return event.result === 'victory' ? '战斗胜利' : '队伍进入救援';
  }
}
</script>

<template>
  <main
    class="battle-acceptance"
    data-rules-version="card-battle-v1"
    :data-state-version="state.stateVersion"
    :data-phase="state.phase"
    :data-enemy-hp="enemy?.hp"
    :data-party-hp="state.party.hp"
    :data-intent="`${state.currentIntent.type}:${state.currentIntent.value}`"
    :data-exhaust-count="state.exhaustPile.length"
    :data-fixture-passed="hasRun ? String(passed) : 'not-run'"
  >
    <header>
      <div>
        <p>规则验收 · 内部页面</p>
        <h1>卡牌回合 v1</h1>
      </div>
      <span>card-battle-v1 · state v{{ state.stateVersion }}</span>
    </header>

    <section class="battle-grid" aria-label="当前战斗状态">
      <article class="combatant">
        <p>星灯旅团</p>
        <strong>生命 {{ state.party.hp }} / {{ state.party.maxHp }}</strong>
        <span>格挡 {{ state.party.block }}</span>
      </article>
      <article class="combatant enemy">
        <p>溪桥迷雾守卫</p>
        <strong>生命 {{ enemy?.hp }} / {{ enemy?.maxHp }}</strong>
        <span>格挡 {{ enemy?.block }}</span>
        <span v-if="enemy?.burn">
          灼烧 {{ enemy.burn.damagePerTurn }} × {{ enemy.burn.remainingTurns }}
        </span>
      </article>
      <article class="intent">
        <p>当前意图</p>
        <strong>
          {{ state.currentIntent.type === 'attack' ? '攻击' : '防守' }}
          {{ state.currentIntent.value }}
        </strong>
        <span>能量 {{ state.energy }} / 3</span>
      </article>
    </section>

    <section class="table-layout">
      <article>
        <p class="section-label">手牌</p>
        <ul class="hand-list">
          <li v-for="(card, index) in handDefinitions" :key="state.hand[index]?.instanceId">
            <span>{{ card.name }}</span>
            <strong>{{ card.cost }} 能量</strong>
            <small>{{ card.effects.map((effect) => effect.type).join(' · ') }}</small>
          </li>
        </ul>
      </article>
      <article>
        <p class="section-label">牌堆状态</p>
        <dl class="zone-list">
          <div>
            <dt>抽牌堆</dt>
            <dd>{{ state.drawCursor }} / {{ state.drawPile.length }}</dd>
          </div>
          <div>
            <dt>弃牌堆</dt>
            <dd>{{ state.discardPile.length }}</dd>
          </div>
          <div>
            <dt>耗竭区</dt>
            <dd>{{ state.exhaustPile.length }}</dd>
          </div>
          <div>
            <dt>回合</dt>
            <dd>{{ state.turnId }}</dd>
          </div>
        </dl>
      </article>
    </section>

    <section class="acceptance-actions">
      <button type="button" class="primary-action" @click="runFixture">
        运行完整回合 fixture
      </button>
      <button type="button" @click="reset">重置</button>
      <strong v-if="hasRun" :class="{ passed, failed: !passed }">
        {{ passed ? 'fixture 通过' : 'fixture 不一致' }}
      </strong>
    </section>

    <section v-if="events.length > 0" class="event-log" aria-labelledby="event-log-title">
      <div>
        <p class="section-label">确定性事件</p>
        <h2 id="event-log-title">完整回合结算顺序</h2>
      </div>
      <ol>
        <li v-for="(event, index) in events" :key="`${index}-${event.type}`">
          <span>{{ String(index + 1).padStart(2, '0') }}</span>
          {{ eventLabel(event) }}
        </li>
      </ol>
    </section>
  </main>
</template>

<style scoped>
.battle-acceptance {
  display: grid;
  gap: 20px;
  min-height: 100vh;
  padding: clamp(22px, 4vw, 42px);
  background: #eef3ef;
  color: #1e3026;
}

.battle-acceptance > * {
  width: min(100%, 1120px);
  margin-inline: auto;
}

header,
.acceptance-actions,
.battle-grid,
.table-layout {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

header {
  align-items: flex-end;
  padding-bottom: 18px;
  border-bottom: 1px solid #d3ddd6;
}

header p,
.section-label {
  margin: 0 0 6px;
  color: #75847b;
  font-size: 0.7rem;
  font-weight: 700;
}

header h1,
.event-log h2 {
  margin: 0;
  font-family: Georgia, "Times New Roman", serif;
  font-weight: 500;
}

header h1 {
  color: #1e3026;
  font-size: clamp(2.2rem, 5vw, 4rem);
}

header > span {
  color: #687970;
  font-size: 0.76rem;
}

.battle-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.combatant,
.intent,
.table-layout article,
.event-log {
  padding: 18px;
  border: 1px solid #cbd7cf;
  border-radius: 8px;
  background: #fbfcfb;
}

.combatant,
.intent {
  display: grid;
  gap: 6px;
}

.combatant p,
.intent p {
  margin: 0;
  color: #75847b;
  font-size: 0.72rem;
}

.combatant strong,
.intent strong {
  font-size: 1.1rem;
}

.combatant span,
.intent span {
  color: #5e7066;
  font-size: 0.76rem;
}

.enemy {
  border-color: #d8c8b6;
  background: #fffaf3;
}

.table-layout {
  align-items: stretch;
}

.table-layout article {
  flex: 1;
}

.hand-list,
.event-log ol {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.hand-list li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 12px;
  padding: 10px 0;
  border-bottom: 1px solid #e0e7e2;
}

.hand-list li:last-child {
  border-bottom: 0;
}

.hand-list small {
  grid-column: 1 / -1;
  color: #75847b;
}

.zone-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin: 0;
}

.zone-list div {
  display: grid;
  gap: 4px;
}

.zone-list dt {
  color: #75847b;
  font-size: 0.7rem;
}

.zone-list dd {
  margin: 0;
  font-size: 1.1rem;
}

.acceptance-actions {
  align-items: center;
  justify-content: flex-start;
  padding: 14px;
  border: 1px solid #cbd7cf;
  border-radius: 8px;
  background: #f5f9f6;
}

.primary-action {
  min-height: 40px;
  padding: 0 16px;
  border: 1px solid #315e4d;
  border-radius: 6px;
  background: #315e4d;
  color: #fff;
  font-weight: 700;
}

.passed {
  color: #28634b;
}

.failed {
  color: #9d3e37;
}

.event-log {
  display: grid;
  grid-template-columns: minmax(220px, 0.35fr) minmax(0, 1fr);
  gap: 24px;
}

.event-log h2 {
  font-size: 1.5rem;
}

.event-log li {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid #e1e7e3;
  color: #52655a;
  font-size: 0.78rem;
}

.event-log li span {
  color: #8a9990;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

@media (max-width: 760px) {
  .battle-grid,
  .table-layout,
  .event-log {
    grid-template-columns: 1fr;
  }

  .battle-grid,
  .table-layout,
  .event-log {
    display: grid;
  }

  header,
  .acceptance-actions {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
