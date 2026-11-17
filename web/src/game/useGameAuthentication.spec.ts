import { describe, expect, it, vi } from 'vitest';
import type { UserView } from '../api/client';
import { gameErrorMessage } from './gameErrorMessage';
import { useGameAuthentication } from './useGameAuthentication';

const user: UserView = {
  id: 'user-auth',
  username: 'alice',
  displayName: 'Alice',
  preferences: {
    nickname: 'Alice',
    avatarKey: 'starter',
    timezone: 'UTC',
    dailyGoal: 20,
    theme: 'starlit',
    soundEnabled: true,
    shortcutsJson: '{}',
  },
};

function createAuth(overrides: {
  loadCurrentUser?: () => Promise<UserView | null>;
  login?: (username: string, password: string) => Promise<UserView>;
  register?: (
    username: string,
    password: string,
    displayName: string,
  ) => Promise<UserView>;
} = {}) {
  return {
    loadCurrentUser: overrides.loadCurrentUser ?? vi.fn(async () => null),
    login: overrides.login ?? vi.fn(async () => user),
    register: overrides.register ?? vi.fn(async () => user),
  };
}

describe('useGameAuthentication', () => {
  it('restores an existing account session', async () => {
    const api = createAuth({
      loadCurrentUser: vi.fn(async () => user),
    });
    const authentication = useGameAuthentication({
      api: () => api,
      describeError: gameErrorMessage,
    });

    await expect(authentication.initialize()).resolves.toBe(user);
    expect(authentication.user.value).toEqual(user);
    expect(authentication.initializing.value).toBe(false);
    expect(authentication.error.value).toBeNull();
  });

  it('registers and then loads the authenticated account', async () => {
    const register = vi.fn(async () => user);
    const loadCurrentUser = vi.fn(async () => user);
    const api = createAuth({ register, loadCurrentUser });
    const authentication = useGameAuthentication({
      api: () => api,
      describeError: gameErrorMessage,
    });
    authentication.mode.value = 'register';
    authentication.username.value = 'alice';
    authentication.displayName.value = 'Alice';
    authentication.password.value = 'correct horse battery';

    await expect(authentication.submit()).resolves.toBe(user);

    expect(register).toHaveBeenCalledWith(
      'alice',
      'correct horse battery',
      'Alice',
    );
    expect(loadCurrentUser).toHaveBeenCalledOnce();
    expect(authentication.password.value).toBe('');
    expect(authentication.authenticating.value).toBe(false);
  });

  it('ignores a session response that arrives after disposal', async () => {
    let resolveCurrentUser: ((value: UserView | null) => void) | undefined;
    const pendingCurrentUser = new Promise<UserView | null>((resolve) => {
      resolveCurrentUser = resolve;
    });
    const api = createAuth({
      loadCurrentUser: () => pendingCurrentUser,
    });
    const authentication = useGameAuthentication({
      api: () => api,
      describeError: gameErrorMessage,
    });

    const initialization = authentication.initialize();
    authentication.dispose();
    resolveCurrentUser?.(user);

    await expect(initialization).resolves.toBeNull();
    expect(authentication.user.value).toBeNull();
  });

  it('ignores a login response that arrives after disposal', async () => {
    let resolveLogin: ((value: UserView) => void) | undefined;
    const pendingLogin = new Promise<UserView>((resolve) => {
      resolveLogin = resolve;
    });
    const api = createAuth({
      login: () => pendingLogin,
    });
    const authentication = useGameAuthentication({
      api: () => api,
      describeError: gameErrorMessage,
    });
    authentication.username.value = 'alice';
    authentication.password.value = 'correct horse battery';

    const submission = authentication.submit();
    authentication.dispose();
    resolveLogin?.(user);

    await expect(submission).resolves.toBeNull();
    expect(authentication.user.value).toBeNull();
    expect(authentication.password.value).toBe('correct horse battery');
  });

  it('keeps the user on the login screen and exposes the error', async () => {
    const api = createAuth({
      login: vi.fn(async () => {
        throw new Error('用户名或密码不正确');
      }),
    });
    const authentication = useGameAuthentication({
      api: () => api,
      describeError: gameErrorMessage,
    });
    authentication.username.value = 'alice';
    authentication.password.value = 'wrong password';

    await expect(authentication.submit()).resolves.toBeNull();
    expect(authentication.user.value).toBeNull();
    expect(authentication.error.value).toBe('用户名或密码不正确');
    expect(authentication.password.value).toBe('wrong password');
  });
});
