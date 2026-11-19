import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  ApiClientError,
  type AccountClient,
  type GmUser,
  type UserView,
} from '../api/client';
import GmWorkspace from './GmWorkspace.vue';

const adminUser: UserView = {
  id: 'admin-1',
  username: 'admin',
  displayName: '管理员',
  mustChangePassword: true,
  preferences: {
    nickname: '管理员',
    avatarKey: 'starter',
    timezone: 'UTC',
    dailyGoal: 20,
    theme: 'starlit',
    soundEnabled: true,
    shortcutsJson: '{}',
  },
};

const managedUser: GmUser = {
  id: 'admin-1',
  username: 'admin',
  displayName: '管理员',
  role: 'ADMIN',
  status: 'active',
  accountKind: 'standard',
  version: 1,
};

function baseApi(loadCurrentUser: () => Promise<UserView | null>): AccountClient {
  return {
    loadCurrentUser,
    login: vi.fn(),
    register: vi.fn(),
    changePassword: vi.fn(),
    logout: vi.fn(),
    listLexicons: vi.fn(),
    createLexicon: vi.fn(),
    loadQueuePlan: vi.fn(),
    listActiveSessions: vi.fn(),
    createSession: vi.fn(),
    getSession: vi.fn(),
    updateSession: vi.fn(),
    submitPracticeResponse: vi.fn(),
    submitPracticeCompletion: vi.fn(),
    previewReview: vi.fn(),
    submitReview: vi.fn(),
    createBattle: vi.fn(),
    getBattle: vi.fn(),
    claimBattleLearning: vi.fn(),
    playBattleCard: vi.fn(),
    useBattleCompanionSkill: vi.fn(),
    endBattleTurn: vi.fn(),
    listAdventures: vi.fn(async () => []),
    startAdventure: vi.fn(),
    getAdventure: vi.fn(),
    startAdventureEncounter: vi.fn(),
    actAdventure: vi.fn(),
    listUnlockedCompanions: vi.fn(async () =>
      ['lia', 'serin'] as Array<'lia' | 'serin' | 'fina'>),
    listCompanionDefinitions: vi.fn(async () => []),
    listAdventureCompletions: vi.fn(async () => []),
    listAdventureRecollections: vi.fn(async () => []),
    previewContentPackage: vi.fn(),
    installContentPackage: vi.fn(),
    listContentPackages: vi.fn(async () => []),
    previewLocalMigration: vi.fn(),
    importLocalMigration: vi.fn(),
    exportAccountBackup: vi.fn(),
    previewAccountBackup: vi.fn(),
    restoreAccountBackup: vi.fn(),
    loadGmContext: vi.fn(async () => ({
      role: 'ADMIN' as const,
      capabilities: ['manage-users', 'manage-sandboxes', 'read-audit'] as Array<
        'manage-users' | 'manage-sandboxes' | 'read-audit'
      >,
    })),
    listGmUsers: vi.fn(async () => ({ items: [managedUser], nextCursor: null })),
    createGmUser: vi.fn(),
    updateGmUser: vi.fn(),
    listGmAudit: vi.fn(async () => ({ items: [], nextCursor: null })),
    listGmSandboxes: vi.fn(async () => ({ items: [], nextCursor: null })),
    createGmSandbox: vi.fn(),
    executeGmSandboxCommand: vi.fn(),
    upgradeAdventure: vi.fn(),
  };
}

let mountedApp: ReturnType<typeof createApp> | null = null;

afterEach(() => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
});

describe('GmWorkspace', () => {
  it('forces first password change before loading administrator data', async () => {
    let currentUser: UserView | null = adminUser;
    const api = baseApi(async () => currentUser);
    api.changePassword = vi.fn(async () => {
      currentUser = { ...adminUser, mustChangePassword: false };
    });

    const root = document.createElement('div');
    document.body.append(root);
    mountedApp = createApp(GmWorkspace, { api });
    mountedApp.mount(root);

    await vi.waitFor(() => {
      expect(root.textContent).toContain('先设置自己的管理员密码');
    });
    expect(api.loadGmContext).not.toHaveBeenCalled();

    const inputs = [...root.querySelectorAll<HTMLInputElement>('input[type="password"]')];
    inputs[0]!.value = 'bootstrap horse battery';
    inputs[0]!.dispatchEvent(new Event('input', { bubbles: true }));
    inputs[1]!.value = 'changed horse battery';
    inputs[1]!.dispatchEvent(new Event('input', { bubbles: true }));
    inputs[2]!.value = 'changed horse battery';
    inputs[2]!.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    root.querySelector('form')?.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );

    await vi.waitFor(() => {
      expect(api.changePassword).toHaveBeenCalledWith(
        'bootstrap horse battery',
        'changed horse battery',
      );
      expect(root.textContent).toContain('ADMIN');
      expect(root.textContent).toContain('@admin');
      expect(root.textContent).toContain('TEST DATA ONLY');
    });
  });

  it('shows a permission denial for an ordinary account', async () => {
    const ordinaryUser: UserView = {
      ...adminUser,
      id: 'user-1',
      username: 'ordinary',
      displayName: 'Ordinary',
      mustChangePassword: false,
    };
    const api = baseApi(async () => ordinaryUser);
    api.loadGmContext = vi.fn(async () => {
      throw new ApiClientError(403, 'ACCESS_DENIED', '没有权限');
    });

    const root = document.createElement('div');
    document.body.append(root);
    mountedApp = createApp(GmWorkspace, { api });
    mountedApp.mount(root);

    await vi.waitFor(() => {
      expect(root.textContent).toContain('当前账号没有 GM 权限');
    });
    expect(root.textContent).not.toContain('用户与测试账号');
  });
});
