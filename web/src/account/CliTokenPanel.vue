<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Clipboard, KeyRound, Trash2 } from '@lucide/vue';
import {
  ApiClientError,
  type AccessTokenApi,
  type AccountClient,
  type CreatedPersonalAccessTokenView,
  type PersonalAccessTokenView,
} from '../api/client';

const props = defineProps<{
  api: AccountClient & Partial<AccessTokenApi>;
}>();

const tokens = ref<PersonalAccessTokenView[]>([]);
const name = ref('CLI');
const expiryDays = ref('90');
const created = ref<CreatedPersonalAccessTokenView>();
const loading = ref(true);
const busy = ref(false);
const error = ref<string | null>(null);
const notice = ref<string | null>(null);

const activeCount = computed(
  () => tokens.value.filter((token) => token.revokedAtMs === null).length,
);

async function loadTokens(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    tokens.value = await requiredApi().listPersonalAccessTokens();
  } catch (cause) {
    error.value = describe(cause);
  } finally {
    loading.value = false;
  }
}

async function createToken(): Promise<void> {
  busy.value = true;
  error.value = null;
  notice.value = null;
  created.value = undefined;
  try {
    const days = Number(expiryDays.value);
    created.value = await requiredApi().createPersonalAccessToken({
      name: name.value.trim(),
      expiresAtMs: Date.now() + days * 24 * 60 * 60 * 1000,
    });
    notice.value = '令牌明文只显示这一次。请用 fm auth set-token 保存到受控配置。';
    await loadTokens();
  } catch (cause) {
    error.value = describe(cause);
  } finally {
    busy.value = false;
  }
}

async function revokeToken(token: PersonalAccessTokenView): Promise<void> {
  busy.value = true;
  error.value = null;
  notice.value = null;
  try {
    await requiredApi().revokePersonalAccessToken(token.id);
    if (created.value?.token.id === token.id) {
      created.value = undefined;
    }
    notice.value = `已撤销 ${token.name}。`;
    await loadTokens();
  } catch (cause) {
    error.value = describe(cause);
  } finally {
    busy.value = false;
  }
}

async function copyToken(): Promise<void> {
  const token = created.value?.plaintextToken;
  if (token === undefined) {
    return;
  }
  try {
    await navigator.clipboard.writeText(token);
    notice.value = '令牌已复制。';
  } catch {
    notice.value = '无法自动复制，请直接选择上方令牌。';
  }
}

function requiredApi(): AccessTokenApi {
  if (
    props.api.listPersonalAccessTokens === undefined
    || props.api.createPersonalAccessToken === undefined
    || props.api.revokePersonalAccessToken === undefined
  ) {
    throw new Error('当前接口未提供 CLI 令牌管理能力');
  }
  return props.api as AccountClient & AccessTokenApi;
}

function describe(cause: unknown): string {
  if (cause instanceof ApiClientError || cause instanceof Error) {
    return cause.message;
  }
  return 'CLI 令牌请求失败';
}

function formatTime(timestamp: number | null): string {
  if (timestamp === null) {
    return '未使用';
  }
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(timestamp));
}

onMounted(() => {
  void loadTokens();
});
</script>

<template>
  <section class="token-panel" aria-labelledby="cli-token-title">
    <header>
      <div>
        <p>CLI 访问</p>
        <h3 id="cli-token-title">个人访问令牌</h3>
      </div>
      <span>{{ activeCount }} 个有效</span>
    </header>

    <form @submit.prevent="createToken">
      <label>
        <span>令牌名称</span>
        <input v-model="name" type="text" maxlength="80" required />
      </label>
      <label>
        <span>有效期</span>
        <select v-model="expiryDays">
          <option value="30">30 天</option>
          <option value="90">90 天</option>
          <option value="365">365 天</option>
        </select>
      </label>
      <button type="submit" class="primary-action" :disabled="busy">
        <KeyRound :size="17" aria-hidden="true" />
        创建令牌
      </button>
    </form>

    <div v-if="created" class="created-token">
      <strong>只显示一次</strong>
      <code>{{ created.plaintextToken }}</code>
      <button type="button" :disabled="busy" @click="copyToken">
        <Clipboard :size="16" aria-hidden="true" />
        复制
      </button>
    </div>

    <p v-if="loading">正在读取令牌...</p>
    <p v-else-if="tokens.length === 0">还没有 CLI 令牌。</p>
    <ul v-else>
      <li v-for="token in tokens" :key="token.id">
        <div>
          <strong>{{ token.name }}</strong>
          <code>{{ token.prefix }}…</code>
        </div>
        <span>
          到期 {{ formatTime(token.expiresAtMs) }} ·
          {{ token.revokedAtMs === null ? `最近使用 ${formatTime(token.lastUsedAtMs)}` : '已撤销' }}
        </span>
        <button
          v-if="token.revokedAtMs === null"
          type="button"
          class="danger-action"
          :disabled="busy"
          @click="revokeToken(token)"
        >
          <Trash2 :size="16" aria-hidden="true" />
          撤销
        </button>
      </li>
    </ul>
    <p v-if="notice" class="inline-notice" role="status">{{ notice }}</p>
    <p v-if="error" class="account-error" role="alert">{{ error }}</p>
  </section>
</template>

<style scoped>
.token-panel {
  padding: 22px;
  border: 1px solid var(--fm-line, #ccd7d0);
  border-radius: 8px;
  background: var(--fm-surface, #fbfcfb);
}

header,
form,
li,
.created-token {
  display: flex;
  align-items: center;
  gap: 12px;
}

header {
  justify-content: space-between;
  margin-bottom: 18px;
}

header p {
  margin: 0 0 4px;
  color: var(--fm-muted, #69776f);
  font-size: 0.72rem;
  font-weight: 700;
}

h3 {
  margin: 0;
}

form {
  flex-wrap: wrap;
}

label {
  display: grid;
  min-width: 180px;
  flex: 1;
  gap: 6px;
}

input,
select {
  min-height: 40px;
  padding: 8px 10px;
  border: 1px solid var(--fm-line, #ccd7d0);
  border-radius: 6px;
}

.created-token,
ul {
  margin-top: 18px;
}

.created-token {
  align-items: flex-start;
  padding: 14px;
  border: 1px solid #c89d45;
  border-radius: 6px;
  background: #fff9e8;
}

.created-token code {
  min-width: 0;
  flex: 1;
  overflow-wrap: anywhere;
}

ul {
  display: grid;
  padding: 0;
  gap: 8px;
  list-style: none;
}

li {
  justify-content: space-between;
  padding-top: 8px;
  border-top: 1px solid var(--fm-line, #ccd7d0);
}

li div {
  display: flex;
  gap: 8px;
}

li span {
  color: var(--fm-muted, #69776f);
  font-size: 0.78rem;
}

button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

@media (max-width: 720px) {
  li,
  .created-token {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
