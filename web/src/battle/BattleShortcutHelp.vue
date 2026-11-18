<script setup lang="ts">
import { computed } from 'vue';
import {
  endTurnShortcutLabel,
  type BattleShortcutPreferences,
} from './shortcutPreferences';
import type {
  BattleShortcutContext,
  EndTurnShortcut,
} from './shortcutStateMachine';

const props = defineProps<{
  context: BattleShortcutContext;
  preferences: BattleShortcutPreferences;
}>();

const emit = defineEmits<{
  close: [];
  updateEndTurn: [shortcut: EndTurnShortcut];
}>();

const contextLabel = computed(() => {
  switch (props.context) {
    case 'learning-input':
      return '学习输入';
    case 'rating':
      return '学习评分';
    case 'card-select':
      return '选择手牌';
    case 'target-select':
      return '选择目标';
    case 'paused':
      return '战斗暂停';
    case 'result':
      return '战斗结算';
    case 'dialogue':
      return '剧情对话';
    case 'flashcard-front':
      return '闪卡正面';
    default:
      return '战斗载入';
  }
});

const shortcuts = computed<ReadonlyArray<readonly [string, string]>>(() => {
  switch (props.context) {
    case 'learning-input':
      return [
        ['Enter', '提交当前英文答案'],
        ['Escape', '暂停战斗'],
      ];
    case 'rating':
      return [
        ['1 / 2 / 3 / 4', 'Again / Hard / Good / Easy'],
        ['Escape', '暂停战斗'],
      ];
    case 'card-select':
      return [
        ['1 – 6', '选择对应手牌'],
        [endTurnShortcutLabel(props.preferences.endTurn), '结束当前回合'],
        ['Escape', '暂停战斗'],
        ['?', '打开快捷键帮助'],
      ];
    case 'target-select':
      return [
        ['方向键', '切换合法目标'],
        ['Enter', '确认目标并出牌'],
        ['Escape', '取消目标选择'],
      ];
    case 'paused':
      return [
        ['Enter / Escape', '继续战斗'],
      ];
    case 'result':
      return [
        ['Enter', '返回在线学习'],
        ['?', '打开快捷键帮助'],
      ];
    default:
      return [];
  }
});

function updateEndTurn(event: Event): void {
  const value = (event.target as HTMLSelectElement).value as EndTurnShortcut;
  emit('updateEndTurn', value);
}
</script>

<template>
  <section class="battle-shortcut-help" aria-labelledby="battle-shortcut-title">
    <header>
      <div>
        <p class="section-label">当前上下文</p>
        <h4 id="battle-shortcut-title">{{ contextLabel }}</h4>
      </div>
      <button type="button" class="shortcut-close" @click="emit('close')">
        关闭
      </button>
    </header>

    <dl>
      <div v-for="[keys, description] in shortcuts" :key="keys">
        <dt><kbd>{{ keys }}</kbd></dt>
        <dd>{{ description }}</dd>
      </div>
    </dl>

    <label class="end-turn-setting">
      <span>结束回合组合键</span>
      <select :value="preferences.endTurn" @change="updateEndTurn">
        <option value="ctrl+enter">Ctrl + Enter</option>
        <option value="alt+enter">Alt + Enter</option>
        <option value="ctrl+shift+enter">Ctrl + Shift + Enter</option>
      </select>
    </label>

    <p class="shortcut-note">
      输入法组合、Tab 焦点、复制粘贴和浏览器快捷键始终优先。
    </p>
  </section>
</template>

<style scoped>
.battle-shortcut-help {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgb(214 189 120 / 38%);
  border-radius: 6px;
  background: #0d2325;
}

header {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 12px;
}

.section-label {
  margin: 0 0 3px;
  color: #d6bd78;
  font-size: 0.7rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

h4 {
  margin: 0;
  color: #f5ead1;
  font-size: 1.05rem;
  font-weight: 600;
}

.shortcut-close {
  min-height: 32px;
  padding: 4px 10px;
  border: 1px solid rgb(214 189 120 / 42%);
  border-radius: 4px;
  background: transparent;
  color: #f0dfb3;
  cursor: pointer;
}

dl {
  display: grid;
  gap: 7px;
  margin: 0;
}

dl > div {
  display: grid;
  grid-template-columns: minmax(120px, 0.7fr) minmax(0, 1.3fr);
  align-items: center;
  gap: 12px;
}

dt,
dd {
  margin: 0;
}

dd {
  color: #b9cec0;
  font-size: 0.78rem;
}

kbd {
  display: inline-block;
  padding: 4px 7px;
  border: 1px solid rgb(214 189 120 / 32%);
  border-bottom-color: rgb(214 189 120 / 58%);
  border-radius: 4px;
  background: #142d2e;
  color: #f5ead1;
  font: 600 0.72rem/1.2 "SFMono-Regular", Consolas, monospace;
}

.end-turn-setting {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #d9e4db;
  font-size: 0.76rem;
}

select {
  min-height: 34px;
  padding: 4px 8px;
  border: 1px solid rgb(214 189 120 / 42%);
  border-radius: 4px;
  background: #eef5f1;
  color: #142d2e;
}

.shortcut-note {
  margin: 0;
  color: #9fb8a7;
  font-size: 0.72rem;
  line-height: 1.5;
}

button:focus-visible,
select:focus-visible {
  outline: 3px solid rgb(225 196 119 / 64%);
  outline-offset: 2px;
}

@media (max-width: 520px) {
  dl > div {
    grid-template-columns: 1fr;
    gap: 4px;
  }

  .end-turn-setting {
    align-items: start;
    flex-direction: column;
  }
}
</style>
