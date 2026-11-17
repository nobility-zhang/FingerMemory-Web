<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import {
  Clock3,
  FlaskConical,
  Layers3,
  MapPinned,
  Plus,
  RefreshCw,
  RotateCcw,
  UserRoundPlus,
} from '@lucide/vue';
import {
  ApiClientError,
  type AccountClient,
  type GmSandbox,
  type GmSandboxCommandInput,
} from '../api/client';

type SandboxApi = Pick<
  AccountClient,
  'listGmSandboxes' | 'createGmSandbox' | 'executeGmSandboxCommand'
>;

const props = defineProps<{
  api: SandboxApi;
}>();

const emit = defineEmits<{
  changed: [];
}>();

const loading = ref(true);
const busy = ref(false);
const error = ref<string | null>(null);
const notice = ref<string | null>(null);
const sandboxes = ref<GmSandbox[]>([]);
const selectedId = ref('');
const reason = ref('GM sandbox verification');
const nodeId = ref('');
const cardDefinitionId = ref('');
const characterKey = ref('');
const clockMode = ref<'fixed' | 'real'>('fixed');
const clockLocal = ref(defaultClockInput());

const selected = computed(
  () => sandboxes.value.find((sandbox) => sandbox.id === selectedId.value),
);
const availableCharacters = computed(() => {
  if (selected.value === undefined) {
    return [];
  }
  return selected.value.state.availableCharacters.filter(
    (key) => !selected.value?.state.unlockedCharacters.includes(key),
  );
});
const deckSummary = computed(() => {
  const counts = new Map<string, number>();
  for (const card of selected.value?.state.deck ?? []) {
    counts.set(card, (counts.get(card) ?? 0) + 1);
  }
  return [...counts.entries()].sort(([left], [right]) => left.localeCompare(right));
});

function defaultClockInput(): string {
  const now = new Date(Date.now() + 60_000);
  return now.toISOString().slice(0, 16);
}

function newId(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function errorMessage(cause: unknown): string {
  if (cause instanceof ApiClientError) {
    return cause.message;
  }
  if (cause instanceof Error && cause.message.trim().length > 0) {
    return cause.message;
  }
  return 'sandbox 请求失败，请稍后重试。';
}

function selectDefaults(sandbox: GmSandbox): void {
  nodeId.value = sandbox.state.currentNodeId;
  cardDefinitionId.value = sandbox.state.availableCards[0] ?? '';
  characterKey.value = sandbox.state.availableCharacters.find(
    (key) => !sandbox.state.unlockedCharacters.includes(key),
  ) ?? '';
  clockMode.value = sandbox.state.clockMode;
  if (typeof sandbox.state.virtualNowMs === 'number') {
    clockLocal.value = new Date(sandbox.state.virtualNowMs).toISOString().slice(0, 16);
  }
}

function replaceSandbox(sandbox: GmSandbox): void {
  const index = sandboxes.value.findIndex((candidate) => candidate.id === sandbox.id);
  if (index < 0) {
    sandboxes.value = [...sandboxes.value, sandbox];
  } else {
    sandboxes.value = sandboxes.value.map((candidate) =>
      candidate.id === sandbox.id ? sandbox : candidate,
    );
  }
  selectedId.value = sandbox.id;
  selectDefaults(sandbox);
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    const page = await props.api.listGmSandboxes(100);
    sandboxes.value = page.items;
    const nextSelected = page.items.find((sandbox) => sandbox.id === selectedId.value)
      ?? page.items[0];
    if (nextSelected !== undefined) {
      replaceSandbox(nextSelected);
    } else {
      selectedId.value = '';
    }
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}

async function createSandbox(): Promise<void> {
  if (busy.value) {
    return;
  }
  busy.value = true;
  error.value = null;
  notice.value = null;
  try {
    const sandbox = await props.api.createGmSandbox(newId(), {
      templateId: 'card-battle-v1-tutorial',
      contentInstallationId: null,
      reason: reason.value,
    });
    replaceSandbox(sandbox);
    notice.value = 'sandbox 已创建';
    emit('changed');
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function execute(input: GmSandboxCommandInput): Promise<void> {
  if (busy.value) {
    return;
  }
  const sandbox = requireSelected();
  busy.value = true;
  error.value = null;
  notice.value = null;
  try {
    const receipt = await props.api.executeGmSandboxCommand(
      sandbox.id,
      input.actionId,
      input,
    );
    replaceSandbox(receipt.sandbox);
    notice.value = receipt.status === 'applied' ? '命令已应用' : '已返回幂等回执';
    emit('changed');
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function jumpNode(): Promise<void> {
  const sandbox = requireSelected();
  await execute({
    actionId: newId(),
    expectedVersion: sandbox.version,
    dataEpoch: sandbox.dataEpoch,
    reason: reason.value,
    command: 'jump-node',
    args: { nodeId: nodeId.value },
  });
}

async function grantCard(): Promise<void> {
  const sandbox = requireSelected();
  await execute({
    actionId: newId(),
    expectedVersion: sandbox.version,
    dataEpoch: sandbox.dataEpoch,
    reason: reason.value,
    command: 'grant-cards',
    args: { cardDefinitionIds: [cardDefinitionId.value] },
  });
}

async function unlockCharacter(): Promise<void> {
  const sandbox = requireSelected();
  await execute({
    actionId: newId(),
    expectedVersion: sandbox.version,
    dataEpoch: sandbox.dataEpoch,
    reason: reason.value,
    command: 'unlock-character',
    args: { characterKey: characterKey.value },
  });
}

async function setClock(): Promise<void> {
  const sandbox = requireSelected();
  if (clockMode.value === 'real') {
    await execute({
      actionId: newId(),
      expectedVersion: sandbox.version,
      dataEpoch: sandbox.dataEpoch,
      reason: reason.value,
      command: 'set-clock',
      args: { mode: 'real' },
    });
    return;
  }
  const nowMs = Date.parse(`${clockLocal.value}:00Z`);
  if (!Number.isFinite(nowMs)) {
    error.value = '请选择有效的 UTC 时间。';
    return;
  }
  await execute({
    actionId: newId(),
    expectedVersion: sandbox.version,
    dataEpoch: sandbox.dataEpoch,
    reason: reason.value,
    command: 'set-clock',
    args: { mode: 'fixed', nowMs },
  });
}

async function resetSandbox(): Promise<void> {
  const sandbox = requireSelected();
  await execute({
    actionId: newId(),
    expectedVersion: sandbox.version,
    dataEpoch: sandbox.dataEpoch,
    reason: reason.value,
    command: 'reset-sandbox',
    args: {},
  });
}

function requireSelected(): GmSandbox {
  if (selected.value === undefined) {
    throw new Error('sandbox 尚未选择');
  }
  return selected.value;
}

function formatEpoch(epoch: string): string {
  return epoch.slice(0, 8);
}

watch(selectedId, () => {
  if (selected.value !== undefined) {
    selectDefaults(selected.value);
  }
});

onMounted(() => {
  void load();
});
</script>

<template>
  <section
    class="gm-sandbox"
    aria-labelledby="gm-sandbox-title"
    data-testid="gm-sandbox-panel"
  >
    <div class="section-heading">
      <div>
        <p>隔离工作台</p>
        <h2 id="gm-sandbox-title">本人测试 sandbox</h2>
      </div>
      <div class="sandbox-heading-actions">
        <span class="sandbox-scope">TEST · SANDBOX</span>
        <button
          type="button"
          class="secondary-command"
          :disabled="busy || loading"
          aria-label="刷新 sandbox"
          title="刷新 sandbox"
          @click="load"
        >
          <RefreshCw :size="16" />
        </button>
      </div>
    </div>

    <p v-if="error" class="sandbox-message sandbox-message--error" role="alert">
      {{ error }}
    </p>
    <p v-if="notice" class="sandbox-message" role="status">{{ notice }}</p>

    <section v-if="loading" class="sandbox-empty" role="status">
      正在读取 sandbox...
    </section>

    <section v-else-if="sandboxes.length === 0" class="sandbox-empty">
      <FlaskConical :size="24" aria-hidden="true" />
      <h3>还没有隔离 sandbox</h3>
      <label>
        <span>操作理由</span>
        <input v-model="reason" maxlength="300" required />
      </label>
      <button
        type="button"
        class="primary-command"
        :disabled="busy"
        @click="createSandbox"
      >
        <Plus :size="17" />
        创建 sandbox
      </button>
    </section>

    <template v-else-if="selected">
      <div class="sandbox-selector">
        <label>
          <span>当前 sandbox</span>
          <select v-model="selectedId">
            <option v-for="sandbox in sandboxes" :key="sandbox.id" :value="sandbox.id">
              {{ sandbox.templateId }} · {{ formatEpoch(sandbox.dataEpoch) }}
            </option>
          </select>
        </label>
        <label>
          <span>操作理由</span>
          <input v-model="reason" maxlength="300" required />
        </label>
        <button
          type="button"
          class="secondary-command"
          :disabled="busy"
          @click="createSandbox"
        >
          <Plus :size="16" />
          新建
        </button>
      </div>

      <dl class="sandbox-summary" data-testid="gm-sandbox-summary">
        <div>
          <dt>节点</dt>
          <dd data-field="node">{{ selected.state.currentNodeId }}</dd>
        </div>
        <div>
          <dt>遭遇</dt>
          <dd data-field="encounter">{{ selected.state.encounterId ?? '无' }}</dd>
        </div>
        <div>
          <dt>版本</dt>
          <dd data-field="version">{{ selected.version }}</dd>
        </div>
        <div>
          <dt>Epoch</dt>
          <dd data-field="epoch">{{ formatEpoch(selected.dataEpoch) }}</dd>
        </div>
        <div>
          <dt>时钟</dt>
          <dd data-field="clock">
            {{ selected.state.clockMode === 'fixed'
              ? new Date(selected.state.virtualNowMs ?? 0).toISOString()
              : 'real' }}
          </dd>
        </div>
      </dl>

      <div class="sandbox-command-grid">
        <fieldset>
          <legend>
            <MapPinned :size="17" />
            跳关
          </legend>
          <select v-model="nodeId" aria-label="目标节点">
            <option v-for="node in selected.state.availableNodes" :key="node" :value="node">
              {{ node }}
            </option>
          </select>
          <button type="button" :disabled="busy" @click="jumpNode">跳转</button>
        </fieldset>

        <fieldset>
          <legend>
            <Layers3 :size="17" />
            发牌
          </legend>
          <select v-model="cardDefinitionId" aria-label="卡牌定义">
            <option
              v-for="card in selected.state.availableCards"
              :key="card"
              :value="card"
            >
              {{ card }}
            </option>
          </select>
          <button type="button" :disabled="busy" @click="grantCard">加入牌组</button>
        </fieldset>

        <fieldset>
          <legend>
            <UserRoundPlus :size="17" />
            解锁角色
          </legend>
          <select
            v-model="characterKey"
            aria-label="待解锁角色"
            :disabled="availableCharacters.length === 0"
          >
            <option
              v-for="character in availableCharacters"
              :key="character"
              :value="character"
            >
              {{ character }}
            </option>
          </select>
          <button
            type="button"
            :disabled="busy || availableCharacters.length === 0"
            @click="unlockCharacter"
          >
            解锁
          </button>
        </fieldset>

        <fieldset>
          <legend>
            <Clock3 :size="17" />
            虚拟时钟
          </legend>
          <div class="clock-controls">
            <select v-model="clockMode" aria-label="时钟模式">
              <option value="fixed">fixed</option>
              <option value="real">real</option>
            </select>
            <input
              v-if="clockMode === 'fixed'"
              v-model="clockLocal"
              type="datetime-local"
              aria-label="固定 UTC 时间"
            />
          </div>
          <button type="button" :disabled="busy" @click="setClock">设置时钟</button>
        </fieldset>
      </div>

      <div class="sandbox-inventory">
        <div>
          <h3>牌组 · {{ selected.state.deck.length }}</h3>
          <ul>
            <li v-for="[card, count] in deckSummary" :key="card">
              <span>{{ card }}</span>
              <strong>×{{ count }}</strong>
            </li>
          </ul>
        </div>
        <div>
          <h3>角色名单</h3>
          <ul>
            <li v-for="character in selected.state.unlockedCharacters" :key="character">
              <span>{{ character }}</span>
              <strong>UNLOCKED</strong>
            </li>
          </ul>
        </div>
      </div>

      <button
        type="button"
        class="reset-sandbox"
        :disabled="busy"
        @click="resetSandbox"
      >
        <RotateCcw :size="16" />
        重置并切换 epoch
      </button>
    </template>
  </section>
</template>

<style scoped>
.gm-sandbox {
  margin-bottom: 18px;
  padding: 20px;
  border: 1px solid rgb(210 168 94 / 36%);
  border-radius: 7px;
  background: rgb(26 37 34 / 92%);
}

.section-heading,
.sandbox-heading-actions,
.sandbox-selector,
.clock-controls,
fieldset,
legend,
.reset-sandbox {
  display: flex;
  align-items: center;
}

.section-heading {
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.section-heading p {
  margin: 0 0 5px;
  color: #d2a85e;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.section-heading h2 {
  margin: 0;
  font-size: 1.25rem;
  letter-spacing: 0;
}

.sandbox-heading-actions {
  gap: 9px;
}

.sandbox-scope {
  padding: 5px 8px;
  border: 1px solid #d2a85e;
  color: #f1ca7c;
  font-size: 0.68rem;
  font-weight: 800;
}

.sandbox-message {
  padding: 9px 11px;
  color: #a6e1c9;
}

.sandbox-message--error {
  border-left: 3px solid #e39b72;
  background: rgb(227 155 114 / 12%);
  color: #ffd8c2;
}

.sandbox-empty {
  display: grid;
  justify-items: start;
  gap: 14px;
  padding: 24px;
  border-top: 1px solid rgb(158 205 189 / 18%);
  color: #b8d2ca;
}

.sandbox-empty h3,
.sandbox-inventory h3 {
  margin: 0;
  font-size: 0.95rem;
}

.sandbox-selector {
  flex-wrap: wrap;
  gap: 12px;
  padding: 14px 0;
  border-top: 1px solid rgb(158 205 189 / 18%);
}

label {
  display: grid;
  gap: 6px;
  color: #bcd1ca;
  font-size: 0.73rem;
  font-weight: 700;
}

.sandbox-selector label {
  min-width: min(260px, 100%);
}

input,
select {
  min-height: 38px;
  padding: 0 10px;
  border: 1px solid rgb(210 168 94 / 38%);
  border-radius: 4px;
  background: #eef5f1;
  color: #172522;
}

.sandbox-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(100px, 1fr));
  gap: 1px;
  margin: 0 0 16px;
  background: rgb(210 168 94 / 22%);
}

.sandbox-summary div {
  display: grid;
  gap: 4px;
  padding: 12px;
  background: #142b2d;
}

.sandbox-summary dt {
  color: #9eb8b0;
  font-size: 0.68rem;
}

.sandbox-summary dd {
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 0.82rem;
  font-weight: 800;
}

.sandbox-command-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(240px, 1fr));
  gap: 12px;
}

fieldset {
  min-height: 102px;
  justify-content: flex-start;
  gap: 8px;
  margin: 0;
  padding: 14px;
  border: 1px solid rgb(158 205 189 / 18%);
}

legend {
  gap: 6px;
  padding: 0 5px;
  color: #d9e9e4;
  font-size: 0.76rem;
  font-weight: 800;
}

fieldset select {
  min-width: 0;
  flex: 1;
}

fieldset button,
.reset-sandbox {
  min-height: 36px;
  padding: 0 11px;
  border: 1px solid rgb(210 168 94 / 50%);
  border-radius: 4px;
  background: #b9863f;
  color: #161d19;
  font-weight: 800;
  cursor: pointer;
}

.clock-controls {
  min-width: 0;
  flex: 1;
  gap: 8px;
}

.clock-controls select {
  width: 90px;
}

.clock-controls input {
  min-width: 0;
  flex: 1;
}

.sandbox-inventory {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-top: 18px;
}

.sandbox-inventory ul {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 6px 16px;
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}

.sandbox-inventory li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 0;
  border-bottom: 1px solid rgb(158 205 189 / 12%);
  color: #b8d2ca;
  font-size: 0.78rem;
}

.sandbox-inventory li strong {
  color: #f1ca7c;
  font-size: 0.68rem;
}

.reset-sandbox {
  gap: 7px;
  margin-top: 20px;
  background: transparent;
  color: #f1ca7c;
}

button:disabled,
input:disabled,
select:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

button:focus-visible,
input:focus-visible,
select:focus-visible {
  outline: 3px solid rgb(210 168 94 / 55%);
  outline-offset: 2px;
}

@media (max-width: 880px) {
  .sandbox-summary,
  .sandbox-inventory {
    grid-template-columns: 1fr 1fr;
  }

  .sandbox-command-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .gm-sandbox {
    padding: 16px;
  }

  .section-heading,
  .sandbox-heading-actions,
  fieldset {
    align-items: stretch;
    flex-direction: column;
  }

  .sandbox-summary,
  .sandbox-inventory {
    grid-template-columns: 1fr;
  }
}
</style>
