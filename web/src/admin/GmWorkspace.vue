<script setup lang="ts">
import { onMounted, ref } from 'vue';
import {
  Ban,
  LogIn,
  LogOut,
  RefreshCw,
  RotateCcw,
  ScrollText,
  ShieldCheck,
  UserPlus,
} from '@lucide/vue';
import {
  accountApi,
  ApiClientError,
  type AccountClient,
  type GmAuditEvent,
  type GmContext,
  type GmUser,
  type UserView,
} from '../api/client';
import GmSandboxPanel from './GmSandboxPanel.vue';

const props = withDefaults(
  defineProps<{
    api?: AccountClient;
  }>(),
  {
    api: () => accountApi,
  },
);

const loading = ref(true);
const busy = ref(false);
const error = ref<string | null>(null);
const accessDenied = ref(false);
const user = ref<UserView | null>(null);
const context = ref<GmContext>();
const users = ref<GmUser[]>([]);
const audit = ref<GmAuditEvent[]>([]);

const username = ref('');
const password = ref('');
const currentPassword = ref('');
const newPassword = ref('');
const confirmPassword = ref('');

const newUsername = ref('');
const newDisplayName = ref('');
const newUserPassword = ref('');
const newAccountKind = ref<'standard' | 'test'>('test');
const newRole = ref<'USER' | 'ADMIN'>('USER');
const createReason = ref('Create an isolated test account');

function errorMessage(cause: unknown): string {
  if (cause instanceof ApiClientError) {
    return cause.message;
  }
  if (cause instanceof Error && cause.message.trim().length > 0) {
    return cause.message;
  }
  return '请求失败，请稍后重试。';
}

function newId(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function formatTime(timestamp: number): string {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(timestamp));
}

async function loadGm(): Promise<void> {
  if (user.value === null || user.value.mustChangePassword === true) {
    return;
  }
  busy.value = true;
  error.value = null;
  accessDenied.value = false;
  try {
    const [nextContext, nextUsers, nextAudit] = await Promise.all([
      props.api.loadGmContext(),
      props.api.listGmUsers(100),
      props.api.listGmAudit(30),
    ]);
    context.value = nextContext;
    users.value = nextUsers.items;
    audit.value = nextAudit.items;
  } catch (cause) {
    if (cause instanceof ApiClientError && cause.status === 403) {
      accessDenied.value = true;
      return;
    }
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function initialize(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    user.value = await props.api.loadCurrentUser();
    if (user.value !== null) {
      await loadGm();
    }
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}

async function login(): Promise<void> {
  if (busy.value) {
    return;
  }
  busy.value = true;
  error.value = null;
  try {
    user.value = await props.api.login(username.value, password.value);
    password.value = '';
    if (user.value.mustChangePassword !== true) {
      await loadGm();
    }
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function changePassword(): Promise<void> {
  if (busy.value) {
    return;
  }
  if (newPassword.value !== confirmPassword.value) {
    error.value = '两次输入的新密码不一致。';
    return;
  }
  busy.value = true;
  error.value = null;
  try {
    await props.api.changePassword(currentPassword.value, newPassword.value);
    currentPassword.value = '';
    newPassword.value = '';
    confirmPassword.value = '';
    user.value = await props.api.loadCurrentUser();
    await loadGm();
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function createUser(): Promise<void> {
  if (busy.value) {
    return;
  }
  busy.value = true;
  error.value = null;
  try {
    await props.api.createGmUser(newId(), {
      username: newUsername.value,
      displayName: newDisplayName.value,
      password: newUserPassword.value,
      role: newAccountKind.value === 'test' ? 'USER' : newRole.value,
      accountKind: newAccountKind.value,
      reason: createReason.value,
    });
    newUsername.value = '';
    newDisplayName.value = '';
    newUserPassword.value = '';
    await loadGm();
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function setUserStatus(target: GmUser): Promise<void> {
  if (busy.value) {
    return;
  }
  busy.value = true;
  error.value = null;
  try {
    await props.api.updateGmUser(target.id, newId(), {
      expectedVersion: target.version,
      status: target.status === 'active' ? 'disabled' : 'active',
      reason:
        target.status === 'active'
          ? 'Disable account from GM workspace'
          : 'Enable account from GM workspace',
    });
    await loadGm();
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function setUserRole(target: GmUser, role: 'USER' | 'ADMIN'): Promise<void> {
  if (busy.value || target.role === role) {
    return;
  }
  busy.value = true;
  error.value = null;
  try {
    await props.api.updateGmUser(target.id, newId(), {
      expectedVersion: target.version,
      role,
      reason: `Change account role to ${role} from GM workspace`,
    });
    await loadGm();
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

async function logout(): Promise<void> {
  if (busy.value) {
    return;
  }
  busy.value = true;
  error.value = null;
  try {
    await props.api.logout();
    user.value = null;
    context.value = undefined;
    users.value = [];
    audit.value = [];
    accessDenied.value = false;
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    busy.value = false;
  }
}

onMounted(() => {
  void initialize();
});
</script>

<template>
  <main class="gm-shell">
    <header class="gm-header">
      <div>
        <p class="gm-kicker">GM OPERATIONS</p>
        <h1>测试管理台</h1>
      </div>
      <div class="gm-header-actions">
        <span class="test-badge">TEST DATA ONLY</span>
        <a href="/">返回游戏</a>
        <button
          v-if="user"
          type="button"
          class="icon-command"
          aria-label="退出登录"
          title="退出登录"
          @click="logout"
        >
          <LogOut :size="18" />
        </button>
      </div>
    </header>

    <p v-if="error" class="gm-error" role="alert">{{ error }}</p>

    <section v-if="loading" class="gm-state" role="status">
      正在读取管理权限...
    </section>

    <section v-else-if="user === null" class="gm-login">
      <div class="gm-login-copy">
        <ShieldCheck :size="26" aria-hidden="true" />
        <h2>管理员登录</h2>
        <p>凭据由本地初始化文件生成，不在页面或仓库中公开。</p>
      </div>
      <form @submit.prevent="login">
        <label>
          <span>用户名</span>
          <input
            v-model="username"
            autocomplete="username"
            minlength="3"
            maxlength="32"
            required
          />
        </label>
        <label>
          <span>密码</span>
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            minlength="12"
            maxlength="128"
            required
          />
        </label>
        <button type="submit" class="primary-command" :disabled="busy">
          <LogIn :size="18" />
          登录
        </button>
      </form>
    </section>

    <section
      v-else-if="user.mustChangePassword === true"
      class="gm-password-change"
    >
      <div class="section-heading">
        <div>
          <p>首次登录</p>
          <h2>先设置自己的管理员密码</h2>
        </div>
        <ShieldCheck :size="25" aria-hidden="true" />
      </div>
      <form @submit.prevent="changePassword">
        <label>
          <span>当前密码</span>
          <input
            v-model="currentPassword"
            type="password"
            autocomplete="current-password"
            required
          />
        </label>
        <label>
          <span>新密码</span>
          <input
            v-model="newPassword"
            type="password"
            autocomplete="new-password"
            minlength="12"
            maxlength="128"
            required
          />
        </label>
        <label>
          <span>确认新密码</span>
          <input
            v-model="confirmPassword"
            type="password"
            autocomplete="new-password"
            minlength="12"
            maxlength="128"
            required
          />
        </label>
        <button type="submit" class="primary-command" :disabled="busy">
          保存并进入 GM
        </button>
      </form>
    </section>

    <section v-else-if="accessDenied" class="gm-state gm-denied">
      <Ban :size="28" aria-hidden="true" />
      <h2>当前账号没有 GM 权限</h2>
      <p>普通账号不能通过直接访问页面取得管理员能力。</p>
    </section>

    <template v-else-if="context">
      <section class="gm-overview" aria-label="GM 概览">
        <article>
          <span>角色</span>
          <strong>{{ context.role }}</strong>
        </article>
        <article>
          <span>账号</span>
          <strong>{{ users.length }}</strong>
        </article>
        <article>
          <span>能力</span>
          <strong>{{ context.capabilities.length }}</strong>
        </article>
        <button type="button" class="secondary-command" :disabled="busy" @click="loadGm">
          <RefreshCw :size="17" />
          刷新
        </button>
      </section>

      <GmSandboxPanel
        v-if="context.capabilities.includes('manage-sandboxes')"
        :api="props.api"
        @changed="loadGm"
      />

      <section class="gm-users" aria-labelledby="gm-users-title">
        <div class="section-heading">
          <div>
            <p>账号目录</p>
            <h2 id="gm-users-title">用户与测试账号</h2>
          </div>
        </div>
        <div class="gm-table-wrap">
          <table>
            <thead>
              <tr>
                <th>用户</th>
                <th>角色</th>
                <th>类型</th>
                <th>状态</th>
                <th>版本</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="managedUser in users" :key="managedUser.id">
                <td>
                  <strong>{{ managedUser.displayName }}</strong>
                  <span>@{{ managedUser.username }}</span>
                </td>
                <td>
                  <select
                    :value="managedUser.role"
                    :disabled="busy || managedUser.accountKind === 'test'"
                    :aria-label="`调整 ${managedUser.username} 的角色`"
                    @change="setUserRole(
                      managedUser,
                      ($event.target as HTMLSelectElement).value as 'USER' | 'ADMIN',
                    )"
                  >
                    <option value="USER">USER</option>
                    <option value="ADMIN">ADMIN</option>
                  </select>
                </td>
                <td>{{ managedUser.accountKind }}</td>
                <td>
                  <span
                    class="status-pill"
                    :class="`status-pill--${managedUser.status}`"
                  >
                    {{ managedUser.status }}
                  </span>
                </td>
                <td>{{ managedUser.version }}</td>
                <td>
                  <button
                    type="button"
                    class="table-command"
                    :disabled="busy"
                    :aria-label="`${managedUser.status === 'active' ? '停用' : '启用'} ${managedUser.username}`"
                    @click="setUserStatus(managedUser)"
                  >
                    <RotateCcw :size="15" />
                    {{ managedUser.status === 'active' ? '停用' : '启用' }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <div class="gm-columns">
        <section class="gm-create" aria-labelledby="gm-create-title">
          <div class="section-heading">
            <div>
              <p>账号创建</p>
              <h2 id="gm-create-title">新建普通或测试账号</h2>
            </div>
            <UserPlus :size="23" aria-hidden="true" />
          </div>
          <form @submit.prevent="createUser">
            <label>
              <span>用户名</span>
              <input v-model="newUsername" minlength="3" maxlength="32" required />
            </label>
            <label>
              <span>显示名称</span>
              <input v-model="newDisplayName" maxlength="80" />
            </label>
            <label>
              <span>初始密码</span>
              <input
                v-model="newUserPassword"
                type="password"
                autocomplete="new-password"
                minlength="12"
                maxlength="128"
                required
              />
            </label>
            <label>
              <span>账号类型</span>
              <select v-model="newAccountKind">
                <option value="test">test</option>
                <option value="standard">standard</option>
              </select>
            </label>
            <label v-if="newAccountKind === 'standard'">
              <span>角色</span>
              <select v-model="newRole">
                <option value="USER">USER</option>
                <option value="ADMIN">ADMIN</option>
              </select>
            </label>
            <label>
              <span>操作理由</span>
              <input v-model="createReason" maxlength="300" required />
            </label>
            <button type="submit" class="primary-command" :disabled="busy">
              创建账号
            </button>
          </form>
        </section>

        <section class="gm-audit" aria-labelledby="gm-audit-title">
          <div class="section-heading">
            <div>
              <p>审计</p>
              <h2 id="gm-audit-title">最近管理操作</h2>
            </div>
            <ScrollText :size="23" aria-hidden="true" />
          </div>
          <ol v-if="audit.length > 0">
            <li v-for="event in audit" :key="event.id">
              <strong>{{ event.action }}</strong>
              <span>{{ event.reason }}</span>
              <small>{{ formatTime(event.occurredAtMs) }} · {{ event.outcome }}</small>
            </li>
          </ol>
          <p v-else class="gm-empty">还没有管理审计记录。</p>
        </section>
      </div>
    </template>
  </main>
</template>

<style scoped>
.gm-shell {
  min-height: 100vh;
  padding: clamp(20px, 4vw, 52px);
  background:
    linear-gradient(120deg, rgb(9 25 30 / 92%), rgb(20 36 39 / 96%)),
    #0d1b1f;
  color: #e8f3ef;
}

.gm-header,
.gm-header-actions,
.section-heading,
.gm-overview,
.table-command,
.primary-command,
.secondary-command {
  display: flex;
  align-items: center;
}

.gm-header {
  justify-content: space-between;
  gap: 24px;
  max-width: 1280px;
  margin: 0 auto 26px;
  padding-bottom: 20px;
  border-bottom: 1px solid rgb(158 205 189 / 22%);
}

.gm-kicker,
.section-heading p {
  margin: 0 0 5px;
  color: #7fc6ad;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.gm-header h1,
.section-heading h2,
.gm-state h2,
.gm-login h2 {
  margin: 0;
  letter-spacing: 0;
}

.gm-header h1 {
  font-size: clamp(2rem, 4vw, 3.4rem);
}

.gm-header-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.gm-header-actions a {
  color: #c6ddd5;
  text-decoration: none;
}

.test-badge {
  padding: 6px 9px;
  border: 1px solid #d2a85e;
  color: #f1ca7c;
  font-size: 0.68rem;
  font-weight: 800;
}

.icon-command,
.table-command,
.primary-command,
.secondary-command {
  gap: 7px;
  border: 1px solid rgb(132 191 170 / 38%);
  border-radius: 5px;
  background: transparent;
  color: #dcece6;
  cursor: pointer;
}

.icon-command {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
}

.primary-command {
  min-height: 42px;
  justify-content: center;
  padding: 0 16px;
  background: #9ed7c1;
  color: #10231f;
  font-weight: 800;
}

.secondary-command {
  min-height: 38px;
  padding: 0 12px;
}

.gm-shell > :not(.gm-header) {
  max-width: 1280px;
  margin-right: auto;
  margin-left: auto;
}

.gm-error {
  padding: 12px 14px;
  border-left: 3px solid #e39b72;
  background: rgb(227 155 114 / 12%);
  color: #ffd8c2;
}

.gm-state,
.gm-login,
.gm-password-change,
.gm-users,
.gm-create,
.gm-audit {
  border: 1px solid rgb(158 205 189 / 20%);
  border-radius: 7px;
  background: rgb(19 38 41 / 82%);
}

.gm-state,
.gm-denied {
  padding: 48px;
  text-align: center;
}

.gm-login,
.gm-password-change {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) minmax(280px, 420px);
  gap: 32px;
  padding: 28px;
}

.gm-login-copy {
  display: grid;
  align-content: start;
  gap: 10px;
  color: #b8d2ca;
}

.gm-login-copy svg,
.section-heading > svg {
  color: #7fc6ad;
}

.gm-login form,
.gm-password-change form,
.gm-create form {
  display: grid;
  gap: 13px;
}

.gm-password-change {
  grid-template-columns: 1fr;
}

.gm-password-change form {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: end;
}

label {
  display: grid;
  gap: 6px;
  color: #bcd1ca;
  font-size: 0.73rem;
  font-weight: 700;
}

input,
select {
  min-height: 40px;
  padding: 0 10px;
  border: 1px solid rgb(152 195 181 / 35%);
  border-radius: 4px;
  background: #eef5f1;
  color: #172522;
}

.gm-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(120px, 1fr)) auto;
  gap: 1px;
  margin-bottom: 18px;
  border: 1px solid rgb(158 205 189 / 20%);
  background: rgb(158 205 189 / 20%);
}

.gm-overview article {
  display: grid;
  gap: 4px;
  padding: 16px;
  background: #12282b;
}

.gm-overview span {
  color: #91ada5;
  font-size: 0.7rem;
}

.gm-overview strong {
  font-size: 1.3rem;
}

.gm-overview .secondary-command {
  margin: 0;
  border: 0;
  background: #12282b;
}

.gm-users,
.gm-create,
.gm-audit {
  padding: 20px;
}

.section-heading {
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.section-heading h2 {
  font-size: 1.25rem;
}

.gm-table-wrap {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 760px;
}

th,
td {
  padding: 12px 10px;
  border-bottom: 1px solid rgb(158 205 189 / 16%);
  text-align: left;
}

th {
  color: #93b6ac;
  font-size: 0.68rem;
  text-transform: uppercase;
}

td {
  font-size: 0.82rem;
}

td:first-child {
  display: grid;
  gap: 3px;
}

td:first-child span {
  color: #8fa9a2;
}

.status-pill {
  padding: 3px 7px;
  border-radius: 999px;
  font-size: 0.68rem;
}

.status-pill--active {
  background: rgb(117 196 162 / 18%);
  color: #a6e1c9;
}

.status-pill--disabled {
  background: rgb(219 126 109 / 18%);
  color: #f1b5a9;
}

.table-command {
  min-height: 32px;
  padding: 0 9px;
  font-size: 0.72rem;
}

.gm-columns {
  display: grid;
  grid-template-columns: minmax(300px, 0.9fr) minmax(360px, 1.1fr);
  gap: 18px;
  margin-top: 18px;
}

.gm-audit ol {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.gm-audit li {
  display: grid;
  gap: 4px;
  padding: 12px 0;
  border-bottom: 1px solid rgb(158 205 189 / 14%);
}

.gm-audit li span,
.gm-audit li small,
.gm-empty {
  color: #9bb6ae;
  font-size: 0.76rem;
}

button:focus-visible,
input:focus-visible,
select:focus-visible,
a:focus-visible {
  outline: 3px solid rgb(127 198 173 / 55%);
  outline-offset: 2px;
}

@media (max-width: 880px) {
  .gm-login,
  .gm-password-change,
  .gm-columns {
    grid-template-columns: 1fr;
  }

  .gm-password-change form {
    grid-template-columns: 1fr;
  }

  .gm-overview {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }
}

@media (max-width: 560px) {
  .gm-shell {
    padding: 16px;
  }

  .gm-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .gm-header-actions {
    justify-content: flex-start;
  }

  .gm-state,
  .gm-denied {
    padding: 30px 18px;
  }
}
</style>
